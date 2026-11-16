package com.example.data.remote

import com.example.data.model.HomeFeedData
import com.example.data.model.HomeSection
import com.example.data.model.MediaDetail
import com.example.data.model.MediaDub
import com.example.data.model.MediaItem
import com.example.data.model.SearchSuggestion
import com.example.data.model.SeasonInfo
import com.example.data.model.StreamSource
import com.example.data.model.SubtitleTrack
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MovieBoxApiClient {
  private val deviceId = MovieBoxSigner.generateDeviceId()
  private val tokenMutex = Mutex()
  private val detailPathCache = java.util.concurrent.ConcurrentHashMap<String, String>()

  @Volatile private var bearerToken: String? = null

  val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private fun captureTokenFromHeader(xUser: String?): String? {
    if (xUser.isNullOrBlank()) return null
    return runCatching {
        val token = JSONObject(xUser).optString("token")
        if (token.isNotBlank()) {
          bearerToken = token
          token
        } else {
          null
        }
      }
      .getOrNull()
  }

  suspend fun bootstrapToken(force: Boolean = false): String =
    tokenMutex.withLock {
      val cached = bearerToken
      if (!force && !cached.isNullOrBlank()) return cached

      withContext(Dispatchers.IO) {
        var lastError = "No bootstrap endpoint succeeded"

        for (signedUrl in SIGNED_BOOTSTRAP_URLS) {
          val attempt =
            runCatching {
              val headers = MovieBoxSigner.buildAuthHeaders("GET", signedUrl, deviceId = deviceId)
              val reqBuilder = Request.Builder().url(signedUrl).get()
              headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
              httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val token = captureTokenFromHeader(resp.header("x-user"))
                if (token != null) {
                  return@runCatching token
                }
                lastError = "Signed bootstrap $signedUrl returned HTTP ${resp.code}"
                null
              }
            }
          attempt.getOrNull()?.let { return@withContext it }
          attempt.exceptionOrNull()?.let { ex ->
            lastError = ex.message ?: lastError
          }
        }

        for (webUrl in WEB_BOOTSTRAP_URLS) {
          val attempt =
            runCatching {
              val req =
                Request.Builder()
                  .url(webUrl)
                  .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
                  .addHeader("Referer", "https://moviebox.ph/")
                  .addHeader("Accept", "application/json")
                  .get()
                  .build()
              httpClient.newCall(req).execute().use { resp ->
                val token = captureTokenFromHeader(resp.header("x-user"))
                if (token != null) {
                  return@runCatching token
                }
                lastError = "Web bootstrap $webUrl returned HTTP ${resp.code}"
                null
              }
            }
          attempt.getOrNull()?.let { return@withContext it }
          attempt.exceptionOrNull()?.let { ex ->
            lastError = ex.message ?: lastError
          }
        }

        throw IOException("Failed to bootstrap MovieBox token ($lastError)")
      }
    }

  private suspend fun apiRequest(
    method: String,
    pathOrUrl: String,
    bodyDict: JSONObject? = null,
    useToken: Boolean = true,
    retryAuth: Boolean = true,
  ): JSONObject {
    if (useToken && bearerToken.isNullOrBlank()) {
      bootstrapToken()
    }

    val targetUrls =
      if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
        listOf(pathOrUrl)
      } else {
        listOf("$NATIVE_API_BASE$pathOrUrl", "$NATIVE_API_FALLBACK$pathOrUrl")
      }

    val bodyStr = bodyDict?.toString()
    var lastCode = 0
    var lastException: Exception? = null

    for (fullUrl in targetUrls) {
      val headers =
        MovieBoxSigner.buildAuthHeaders(
          method = method,
          url = fullUrl,
          body = bodyStr,
          token = if (useToken) bearerToken else null,
          deviceId = deviceId,
        )

      val callResult =
        runCatching {
          withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(fullUrl)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            if (method.equals("POST", ignoreCase = true)) {
              builder.post((bodyStr ?: "{}").toRequestBody(jsonMediaType))
            } else {
              builder.get()
            }
            httpClient.newCall(builder.build()).execute().use { resp ->
              captureTokenFromHeader(resp.header("x-user"))
              resp.code to (resp.body?.string().orEmpty())
            }
          }
        }

      val pair = callResult.getOrNull()
      if (pair == null) {
        lastException = callResult.exceptionOrNull() as? Exception
        continue
      }

      val (code, bodyText) = pair
      lastCode = code
      if ((code == 401 || code == 441) && retryAuth) {
        bootstrapToken(force = true)
        return apiRequest(method, pathOrUrl, bodyDict, useToken, retryAuth = false)
      }
      if (code == 200) {
        return JSONObject(bodyText)
      }
    }

    if (lastCode != 0) {
      throw IOException("Upstream API returned HTTP $lastCode")
    }
    throw (lastException ?: IOException("Upstream API request failed"))
  }

  suspend fun getHomeFeed(): HomeFeedData =
    withContext(Dispatchers.IO) {
      val req =
        Request.Builder()
          .url("$API_BASE/home?host=moviebox.ph")
          .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
          .addHeader("Referer", "https://moviebox.ph/")
          .get()
          .build()

      val rawJson =
        httpClient.newCall(req).execute().use { resp ->
          captureTokenFromHeader(resp.header("x-user"))
          if (!resp.isSuccessful) throw IOException("Home feed HTTP ${resp.code}")
          JSONObject(resp.body?.string().orEmpty())
        }

      val data = rawJson.optJSONObject("data") ?: JSONObject()
      val operatingList = data.optJSONArray("operatingList") ?: JSONArray()
      val banners = mutableListOf<MediaItem>()
      val sections = mutableListOf<HomeSection>()

      for (i in 0 until operatingList.length()) {
        val op = operatingList.optJSONObject(i) ?: continue
        val title = op.optString("title").ifBlank { "Featured" }
        val bannerObj = op.optJSONObject("banner")
        if (bannerObj != null) {
          val bannerItems = bannerObj.optJSONArray("items") ?: JSONArray()
          for (j in 0 until bannerItems.length()) {
            val bItem = bannerItems.optJSONObject(j) ?: continue
            val sub = bItem.optJSONObject("subject") ?: bItem
            parseMediaItem(sub, fallbackCover = bItem.optJSONObject("image")?.optString("url"))
              ?.let { banners.add(it) }
          }
        }

        val subjects = op.optJSONArray("subjects") ?: JSONArray()
        val sectionItems = mutableListOf<MediaItem>()
        for (j in 0 until subjects.length()) {
          val sub = subjects.optJSONObject(j) ?: continue
          parseMediaItem(sub)?.let { sectionItems.add(it) }
        }
        if (sectionItems.isNotEmpty()) {
          sections.add(HomeSection(title = title, items = sectionItems))
        }
      }

      if (banners.isEmpty() && sections.isNotEmpty()) {
        banners.addAll(sections.first().items.take(5))
      }

      HomeFeedData(banners = banners.distinctBy { it.subjectId }, sections = sections)
    }

  suspend fun getMovies(page: Int = 1, perPage: Int = 24, sort: String = "RECOMMEND"): List<MediaItem> =
    fetchFilteredCatalog(tabId = 2, defaultType = "Movie", page = page, perPage = perPage, sort = sort)

  suspend fun getTvSeries(page: Int = 1, perPage: Int = 24, sort: String = "RECOMMEND"): List<MediaItem> =
    fetchFilteredCatalog(tabId = 5, defaultType = "Series", page = page, perPage = perPage, sort = sort)

  suspend fun getAnime(page: Int = 1, perPage: Int = 30): List<MediaItem> {
    val homeAnime =
      runCatching {
          getHomeFeed()
            .sections
            .filter { it.title.contains("Anime", ignoreCase = true) }
            .flatMap { it.items }
        }
        .getOrDefault(emptyList())

    val filteredAnime =
      runCatching {
          fetchFilteredCatalog(
            tabId = null,
            channelId = 1006,
            genre = "Anime",
            defaultType = null,
            page = page,
            perPage = perPage,
            sort = "Hottest",
            allowedSubjectTypes = setOf(1, 2),
          )
        }
        .getOrDefault(emptyList())

    val allChannelAnimation =
      if (homeAnime.size + filteredAnime.size < 12) {
        runCatching {
            fetchFilteredCatalog(
              tabId = null,
              channelId = 1006,
              genre = "ALL",
              defaultType = null,
              page = page,
              perPage = perPage,
              sort = "RECOMMEND",
              allowedSubjectTypes = setOf(1, 2),
            )
          }
          .getOrDefault(emptyList())
      } else {
        emptyList()
      }

    val combined = (homeAnime + filteredAnime + allChannelAnimation).distinctBy { it.subjectId }
    if (combined.isEmpty()) {
      throw IOException("Failed to load live anime catalog")
    }
    return combined
  }

  private suspend fun fetchFilteredCatalog(
    tabId: Int? = null,
    channelId: Int? = null,
    genre: String = "ALL",
    defaultType: String?,
    page: Int,
    perPage: Int,
    sort: String,
    allowedSubjectTypes: Set<Int>? = null,
  ): List<MediaItem> =
    withContext(Dispatchers.IO) {
      val payload =
        JSONObject().apply {
          if (tabId != null) put("tabId", tabId)
          if (channelId != null) put("channelId", channelId)
          put(
            "filter",
            JSONObject().apply {
              put("sort", sort)
              put("genre", genre)
              put("country", "ALL")
              put("year", "ALL")
              put("language", "ALL")
            },
          )
          put("page", page)
          put("perPage", perPage)
        }
      val token = bearerToken
      val reqBuilder =
        Request.Builder()
          .url("$API_BASE/subject/filter")
          .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
          .addHeader("Referer", "https://moviebox.ph/")
          .post(payload.toString().toRequestBody(jsonMediaType))
      if (!token.isNullOrBlank()) {
        reqBuilder.addHeader("Authorization", "Bearer $token")
      }
      val req = reqBuilder.build()

      val rawJson =
        httpClient.newCall(req).execute().use { resp ->
          captureTokenFromHeader(resp.header("x-user"))
          if (!resp.isSuccessful) throw IOException("Catalog HTTP ${resp.code}")
          JSONObject(resp.body?.string().orEmpty())
        }

      val data = rawJson.optJSONObject("data") ?: JSONObject()
      val arr = data.optJSONArray("items") ?: data.optJSONArray("subjects") ?: JSONArray()
      val results = mutableListOf<MediaItem>()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val st = obj.optInt("subjectType", 1)
        if (allowedSubjectTypes != null && st !in allowedSubjectTypes) continue
        parseMediaItem(obj, defaultType = defaultType)?.let { results.add(it) }
      }
      results
    }

  suspend fun search(query: String, page: Int = 1, perPage: Int = 20): List<MediaItem> {
    val payload =
      JSONObject().apply {
        put("keyword", query)
        put("page", page)
        put("perPage", perPage)
      }

    val nativeAttempt =
      runCatching {
        val data =
          apiRequest("POST", "/wefeed-mobile-bff/subject-api/search", bodyDict = payload)
            .optJSONObject("data") ?: JSONObject()
        parseSearchResults(data)
      }
    if (nativeAttempt.isSuccess) {
      return nativeAttempt.getOrThrow()
    }

    val token = runCatching { bootstrapToken() }.getOrNull()
    return withContext(Dispatchers.IO) {
      var lastError: Throwable? = nativeAttempt.exceptionOrNull()
      for (domain in WEB_DETAIL_DOMAINS) {
        val attempt =
          runCatching {
            val reqBuilder =
              Request.Builder()
                .url("$domain/wefeed-h5api-bff/subject/search")
                .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
                .addHeader("Referer", "https://moviebox.ph/")
                .addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
            if (!token.isNullOrBlank()) {
              reqBuilder.addHeader("Authorization", "Bearer $token")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
              captureTokenFromHeader(resp.header("x-user"))
              if (!resp.isSuccessful) throw IOException("Web search HTTP ${resp.code}")
              val data = JSONObject(resp.body?.string().orEmpty()).optJSONObject("data") ?: JSONObject()
              parseSearchResults(data)
            }
          }
        if (attempt.isSuccess) return@withContext attempt.getOrThrow()
        lastError = attempt.exceptionOrNull() ?: lastError
      }
      throw (lastError ?: IOException("Search request failed"))
    }
  }

  private fun parseSearchResults(data: JSONObject): List<MediaItem> {
    val rawItems = data.optJSONArray("items") ?: data.optJSONArray("list") ?: JSONArray()
    val results = mutableListOf<MediaItem>()
    for (i in 0 until rawItems.length()) {
      val item = rawItems.optJSONObject(i) ?: continue
      val sub = item.optJSONObject("subject") ?: item
      val st = sub.optInt("subjectType", 1)
      if (st == 6) continue
      parseMediaItem(sub)?.let { results.add(it) }
    }
    return results
  }

  suspend fun searchSuggest(query: String): List<SearchSuggestion> =
    withContext(Dispatchers.IO) {
      val payload =
        JSONObject().apply {
          put("keyword", query)
          put("perPage", 10)
        }
      val req =
        Request.Builder()
          .url("$API_BASE/subject/search-suggest")
          .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
          .addHeader("Referer", "https://moviebox.ph/")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

      val raw =
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
              captureTokenFromHeader(resp.header("x-user"))
              JSONObject(resp.body?.string().orEmpty())
                .optJSONObject("data")
                ?.optJSONArray("items") ?: JSONArray()
            }
          }
          .getOrElse { JSONArray() }

      val suggestions = mutableListOf<SearchSuggestion>()
      for (i in 0 until raw.length()) {
        val item = raw.optJSONObject(i) ?: continue
        val sub = item.optJSONObject("subject") ?: JSONObject()
        val title =
          sub.optString("title").ifBlank { item.optString("word").ifBlank { item.optString("title") } }
        val subjectId =
          sub.optString("subjectId").ifBlank { item.optString("subjectId") }
        val slug = sub.optString("detailPath").ifBlank { item.optString("detailPath") }.ifBlank { null }
        if (title.isNotBlank() && subjectId.isNotBlank()) {
          if (!slug.isNullOrBlank()) detailPathCache[subjectId] = slug
          suggestions.add(SearchSuggestion(title = title, slug = slug, subjectId = subjectId))
        }
      }
      suggestions
    }

  private suspend fun fetchWebDetailJson(detailPath: String): JSONObject? {
    if (detailPath.isBlank()) return null
    val token = runCatching { bootstrapToken() }.getOrNull()
    return withContext(Dispatchers.IO) {
      for (domain in WEB_DETAIL_DOMAINS) {
        val res =
          runCatching {
            val reqBuilder =
              Request.Builder()
                .url("$domain/wefeed-h5api-bff/detail?detailPath=${encode(detailPath)}&host=moviebox.ph")
                .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
                .addHeader("Referer", "https://moviebox.ph/")
                .addHeader("Accept", "application/json")
                .get()
            if (!token.isNullOrBlank()) {
              reqBuilder.addHeader("Authorization", "Bearer $token")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
              captureTokenFromHeader(resp.header("x-user"))
              if (!resp.isSuccessful) return@use null
              val data = JSONObject(resp.body?.string().orEmpty()).optJSONObject("data")
              if (data?.optJSONObject("subject") != null) data else null
            }
          }.getOrNull()
        if (res != null) return@withContext res
      }
      null
    }
  }

  suspend fun getDetails(subjectId: String, detailPathHint: String? = null): MediaDetail {
    val nativeAttempt =
      runCatching {
        val url = "/wefeed-mobile-bff/subject-api/get?subjectId=${encode(subjectId)}"
        val data = apiRequest("GET", url).optJSONObject("data") ?: JSONObject()
        parseMediaDetail(subjectId, data)
      }
    if (nativeAttempt.isSuccess && nativeAttempt.getOrThrow().title.isNotBlank()) {
      return nativeAttempt.getOrThrow()
    }

    val resolvedSlug =
      detailPathHint?.takeIf { it.isNotBlank() } ?: detailPathCache[subjectId]
    if (resolvedSlug != null) {
      val webData = fetchWebDetailJson(resolvedSlug)
      val sub = webData?.optJSONObject("subject")
      if (sub != null) {
        return parseMediaDetail(subjectId, sub, fallbackDetailPath = resolvedSlug)
      }
    }

    throw (nativeAttempt.exceptionOrNull() ?: IOException("Failed to load title details"))
  }

  private fun parseMediaDetail(
    subjectId: String,
    data: JSONObject,
    fallbackDetailPath: String? = null,
  ): MediaDetail {
    val detailUrlStr = data.optString("detailUrl")
    val detailPath =
      when {
        "/" in detailUrlStr -> detailUrlStr.substringAfterLast('/')
        detailUrlStr.isNotBlank() -> detailUrlStr
        else -> data.optString("detailPath").ifBlank { fallbackDetailPath }
      }
    val resolvedSid = data.optString("subjectId").ifBlank { subjectId }
    if (!detailPath.isNullOrBlank() && resolvedSid.isNotBlank()) {
      detailPathCache[resolvedSid] = detailPath
    }

    val dubs = mutableListOf<MediaDub>()
    val dubArr = data.optJSONArray("dubs") ?: JSONArray()
    for (i in 0 until dubArr.length()) {
      val d = dubArr.optJSONObject(i) ?: continue
      val sid = d.optString("subjectId")
      if (sid.isNotBlank()) {
        dubs.add(
          MediaDub(
            subjectId = sid,
            langName = d.optString("lanName").ifBlank { d.optString("language") },
            language = d.optString("language"),
          )
        )
      }
    }

    val subjectType = data.optInt("subjectType", 1)
    return MediaDetail(
      subjectId = data.optString("subjectId").ifBlank { subjectId },
      title = data.optString("title"),
      mediaType = if (subjectType == 1) "Movie" else "Series",
      description = data.optString("description").ifBlank { null },
      releaseDate = data.optString("releaseDate").ifBlank { null },
      durationSeconds = data.optLong("duration", 0L).takeIf { it > 0L },
      imdbRating = data.optString("imdbRatingValue").ifBlank { null },
      genre = data.optString("genre").ifBlank { null },
      country = data.optString("countryName").ifBlank { null },
      language = data.optString("language").ifBlank { null },
      coverUrl = data.optJSONObject("cover")?.optString("url")?.ifBlank { null },
      detailPath = detailPath,
      dubs = dubs,
    )
  }

  suspend fun getEpisodes(subjectId: String, detailPathHint: String? = null): List<SeasonInfo> {
    val nativeAttempt =
      runCatching {
        val url = "/wefeed-mobile-bff/subject-api/season-info?subjectId=${encode(subjectId)}"
        val seasons =
          apiRequest("GET", url).optJSONObject("data")?.optJSONArray("seasons") ?: JSONArray()
        parseSeasonsArray(seasons)
      }
    if (nativeAttempt.isSuccess) {
      return nativeAttempt.getOrThrow()
    }

    val slug = detailPathHint?.takeIf { it.isNotBlank() } ?: detailPathCache[subjectId]
    if (slug != null) {
      val webData = fetchWebDetailJson(slug)
      val seasons = webData?.optJSONObject("resource")?.optJSONArray("seasons")
      if (seasons != null) {
        return parseSeasonsArray(seasons)
      }
    }

    return emptyList()
  }

  private fun parseSeasonsArray(seasons: JSONArray): List<SeasonInfo> {
    val list = mutableListOf<SeasonInfo>()
    for (i in 0 until seasons.length()) {
      val s = seasons.optJSONObject(i) ?: continue
      val resArr = s.optJSONArray("resolutions") ?: JSONArray()
      val resList = mutableListOf<Int>()
      for (j in 0 until resArr.length()) {
        val rObj = resArr.opt(j)
        val rInt =
          when (rObj) {
            is Number -> rObj.toInt()
            is JSONObject -> rObj.optInt("resolution", 0)
            else -> rObj?.toString()?.toIntOrNull() ?: 0
          }
        if (rInt > 0) resList.add(rInt)
      }
      list.add(
        SeasonInfo(
          season = s.optInt("se", 1),
          episodeCount = s.optInt("maxEp", 1),
          resolutions = resList.distinct().sortedDescending(),
        )
      )
    }
    return list
  }

  suspend fun getStreams(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
    detailPath: String? = null,
  ): List<StreamSource> {
    val resolvedDetailPath =
      detailPath?.takeIf { it.isNotBlank() }
        ?: detailPathCache[subjectId]
        ?: runCatching { getDetails(subjectId).detailPath }.getOrNull().orEmpty()
    val token = runCatching { bootstrapToken() }.getOrNull()
    val streams = mutableListOf<StreamSource>()
    val seenUrls = mutableSetOf<String>()

    withContext(Dispatchers.IO) {
      for (domain in WEB_DOMAINS) {
        val playUrl =
          "$domain/wefeed-h5api-bff/subject/play?" +
            "subjectId=${encode(subjectId)}&se=$season&ep=$episode&detailPath=${encode(resolvedDetailPath)}&" +
            "streamSignType=1&supportCodecs%5Bhevc%5D=1&supportCodecs%5Bh264%5D=1"
        val referer =
          "$domain/movies/$resolvedDetailPath?id=$subjectId&type=/movie/detail&detailSe=&detailEp=&lang=en"
        val reqBuilder =
          Request.Builder()
            .url(playUrl)
            .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
            .addHeader("Referer", referer)
            .addHeader("Accept", "application/json")
            .addHeader("x-client-info", """{"timezone":"Asia/Calcutta"}""")
            .addHeader("x-request-lang", "en")
            .addHeader("x-vip-restrict", "0")
            .addHeader("x-no-high-risk-restrict", "0")
            .addHeader("x-source", "moviebox")
        if (!token.isNullOrBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer $token")
        }
        val req = reqBuilder.get().build()

        val foundOnDomain =
          runCatching {
              httpClient.newCall(req).execute().use { resp ->
                captureTokenFromHeader(resp.header("x-user"))
                if (resp.code != 200) return@use false
                val data = JSONObject(resp.body?.string().orEmpty()).optJSONObject("data") ?: return@use false
                val rawStreams = data.optJSONArray("streams") ?: JSONArray()
                val dashItems = data.optJSONArray("dash") ?: JSONArray()
                val hlsItems = data.optJSONArray("hls") ?: JSONArray()
                if (rawStreams.length() == 0 && dashItems.length() == 0 && hlsItems.length() == 0) {
                  return@use false
                }

                for (i in 0 until rawStreams.length()) {
                  val st = rawStreams.optJSONObject(i) ?: continue
                  val url = st.optString("url")
                  if (url.isBlank() || !seenUrls.add(url)) continue
                  val resStr = st.optString("resolutions", "unknown")
                  val sizeBytes = st.optLong("size", 0L)
                  val sizeMb = if (sizeBytes > 0L) (sizeBytes / (1024.0 * 1024.0)) else 0.0
                  val qualityVal = QUALITY_LADDER.firstOrNull { resStr.contains(it.toString()) } ?: 0
                  streams.add(
                    StreamSource(
                      source = "Web CDN (${domain.removePrefix("https://")})",
                      format = st.optString("format", "MP4").ifBlank { "MP4" },
                      quality = if (qualityVal > 0) "${qualityVal}p" else resStr,
                      qualityValue = qualityVal,
                      resolutionTag = resStr,
                      url = url,
                      sizeMb = (sizeMb * 10).toInt() / 10.0,
                      codec = st.optString("codecName").ifBlank { null },
                      durationSec = st.optLong("duration", 0L).takeIf { it > 0L },
                      headers =
                        mapOf(
                          "User-Agent" to MovieBoxSigner.WEB_USER_AGENT,
                          "Referer" to "$domain/",
                        ),
                    )
                  )
                }

                for (i in 0 until dashItems.length()) {
                  val d = dashItems.optJSONObject(i) ?: continue
                  val url = d.optString("url")
                  val cookie = d.optString("signCookie").ifBlank { null }
                  val signHeaderKey = d.optString("signHeaderKey").ifBlank { null }
                  val extractedMpd = MovieBoxSigner.extractPolicyResource(cookie)
                  val finalUrl = extractedMpd ?: url
                  if (finalUrl.isNotBlank() && seenUrls.add(finalUrl)) {
                    val resTag = d.optString("resolutions", "adaptive")
                    val headers =
                      buildMap {
                        put("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
                        put("Referer", "$domain/")
                        if (cookie != null) {
                          put("Cookie", cookie)
                          if (signHeaderKey != null) put(signHeaderKey, cookie)
                        }
                      }
                    streams.add(
                      StreamSource(
                        source = "Web DASH (${domain.removePrefix("https://")})",
                        format = "DASH",
                        quality = if ("1080" in resTag) "1080p (Adaptive)" else "Adaptive",
                        qualityValue = if ("1080" in resTag) 1080 else 999,
                        resolutionTag = resTag,
                        url = finalUrl,
                        sizeMb = 0.0,
                        codec = d.optString("codecName").ifBlank { "hevc/h264" },
                        cookie = cookie,
                        headers = headers,
                      )
                    )
                  }
                }

                for (i in 0 until hlsItems.length()) {
                  val h = hlsItems.optJSONObject(i) ?: continue
                  val url = h.optString("url")
                  if (url.isNotBlank() && seenUrls.add(url)) {
                    val resTag = h.optString("resolutions", "adaptive")
                    val qualityVal = QUALITY_LADDER.firstOrNull { resTag.contains(it.toString()) } ?: 990
                    streams.add(
                      StreamSource(
                        source = "Web HLS (${domain.removePrefix("https://")})",
                        format = "HLS",
                        quality = if (qualityVal in QUALITY_LADDER) "${qualityVal}p (HLS)" else "Adaptive HLS",
                        qualityValue = qualityVal,
                        resolutionTag = resTag,
                        url = url,
                        sizeMb = 0.0,
                        codec = h.optString("codecName").ifBlank { null },
                        headers =
                          mapOf(
                            "User-Agent" to MovieBoxSigner.WEB_USER_AGENT,
                            "Referer" to "$domain/",
                          ),
                      )
                    )
                  }
                }
                true
              }
            }
            .getOrDefault(false)

        if (foundOnDomain) break
      }
    }

    runCatching {
      val nativeUrl =
        "/wefeed-mobile-bff/subject-api/play-info?subjectId=${encode(subjectId)}&se=$season&ep=$episode"
      val nativeData = apiRequest("GET", nativeUrl).optJSONObject("data") ?: JSONObject()
      val nativeStreams = nativeData.optJSONArray("streams") ?: JSONArray()
      for (i in 0 until nativeStreams.length()) {
        val st = nativeStreams.optJSONObject(i) ?: continue
        val rawUrl = st.optString("url")
        if (rawUrl.isBlank() || "b164fbfb4347792950bdfbfb563d39d9" in rawUrl) continue
        val cookie = st.optString("signCookie").ifBlank { null }
        val signHeaderKey = st.optString("signHeaderKey").ifBlank { null }
        val policyUrl = MovieBoxSigner.extractPolicyResource(cookie)
        val finalUrl = policyUrl ?: rawUrl
        if (!seenUrls.add(finalUrl)) continue
        val resStr = st.optString("resolutions", "")
        val qualityVal = QUALITY_LADDER.firstOrNull { resStr.contains(it.toString()) } ?: 0
        val sizeBytes = st.optLong("size", 0L)
        val sizeMb = if (sizeBytes > 0L) (sizeBytes / (1024.0 * 1024.0)) else 0.0
        val isDash = policyUrl != null || finalUrl.contains(".mpd")
        val headers =
          buildMap {
            put("User-Agent", MovieBoxSigner.MODERN_USER_AGENT)
            put("Referer", "https://api3.aoneroom.com/")
            if (cookie != null) {
              put("Cookie", cookie)
              if (signHeaderKey != null) put(signHeaderKey, cookie)
            }
          }
        streams.add(
          StreamSource(
            source = "Mobile Native CDN",
            format = if (isDash) "DASH" else st.optString("format", "MP4").ifBlank { "MP4" },
            quality = if (qualityVal > 0) "${qualityVal}p" else resStr.ifBlank { "Default" },
            qualityValue = qualityVal,
            resolutionTag = resStr,
            url = finalUrl,
            sizeMb = (sizeMb * 10).toInt() / 10.0,
            codec = st.optString("codecName").ifBlank { null },
            durationSec = st.optLong("duration", 0L).takeIf { it > 0L },
            cookie = cookie,
            headers = headers,
          )
        )
      }
    }

    return streams.sortedWith(
      compareByDescending<StreamSource> { it.qualityValue }
        .thenBy { if (it.format.equals("MP4", ignoreCase = true) && !it.codec.equals("hevc", ignoreCase = true)) 0 else 1 }
    )
  }

  suspend fun getSubtitles(
    subjectId: String,
    streamId: String? = null,
    season: Int = 0,
    episode: Int = 0,
  ): List<SubtitleTrack> {
    var resolvedStreamId = streamId
    if (resolvedStreamId.isNullOrBlank()) {
      runCatching {
        val nativeUrl =
          "/wefeed-mobile-bff/subject-api/play-info?subjectId=${encode(subjectId)}&se=$season&ep=$episode"
        val rawStreams =
          apiRequest("GET", nativeUrl).optJSONObject("data")?.optJSONArray("streams")
        if (rawStreams != null && rawStreams.length() > 0) {
          resolvedStreamId = rawStreams.optJSONObject(0)?.optString("id")?.ifBlank { null }
        }
      }
    }

    val subs = mutableListOf<SubtitleTrack>()
    val seenUrls = mutableSetOf<String>()

    fun addSub(item: JSONObject) {
      val u = item.optString("url")
      if (u.isNotBlank() && seenUrls.add(u)) {
        subs.add(
          SubtitleTrack(
            id = item.optString("id"),
            language =
              item.optString("lanName").ifBlank { item.optString("language").ifBlank { "English" } },
            code = item.optString("lan"),
            url = u,
            sizeBytes = item.optLong("size", 0L).takeIf { it > 0L },
          )
        )
      }
    }

    if (!resolvedStreamId.isNullOrBlank()) {
      runCatching {
        val url =
          "/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=${encode(subjectId)}&streamId=${encode(resolvedStreamId!!)}"
        val resp = apiRequest("GET", url)
        extractCaptionsArray(resp).forEach { addSub(it) }
      }
    }

    runCatching {
      val resId = resolvedStreamId ?: "0"
      val url =
        "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=${encode(subjectId)}&resourceId=${encode(resId)}&episode=$episode"
      val resp = apiRequest("GET", url)
      extractCaptionsArray(resp).forEach { addSub(it) }
    }

    if (subs.isEmpty()) {
      val token = runCatching { bootstrapToken() }.getOrNull()
      withContext(Dispatchers.IO) {
        val capId = resolvedStreamId ?: subjectId
        for (domain in WEB_DETAIL_DOMAINS) {
          runCatching {
            val url =
              "$domain/wefeed-h5api-bff/subject/caption?format=MP4&id=${encode(capId)}&subjectId=${encode(subjectId)}"
            val reqBuilder =
              Request.Builder()
                .url(url)
                .addHeader("User-Agent", MovieBoxSigner.WEB_USER_AGENT)
                .addHeader("Referer", "https://moviebox.ph/")
                .addHeader("Accept", "application/json")
                .get()
            if (!token.isNullOrBlank()) {
              reqBuilder.addHeader("Authorization", "Bearer $token")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
              captureTokenFromHeader(resp.header("x-user"))
              if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                extractCaptionsArray(json).forEach { addSub(it) }
              }
            }
          }
          if (subs.isNotEmpty()) break
        }
      }
    }

    return subs
  }

  private fun extractCaptionsArray(resp: JSONObject): List<JSONObject> {
    val out = mutableListOf<JSONObject>()
    val dataObj = resp.optJSONObject("data")
    val arr =
      if (dataObj != null) {
        dataObj.optJSONArray("extCaptions") ?: dataObj.optJSONArray("captions") ?: JSONArray()
      } else {
        resp.optJSONArray("data") ?: JSONArray()
      }
    for (i in 0 until arr.length()) {
      arr.optJSONObject(i)?.let { out.add(it) }
    }
    return out
  }

  private fun parseMediaItem(obj: JSONObject, defaultType: String? = null, fallbackCover: String? = null): MediaItem? {
    val subjectId = obj.optString("subjectId")
    val title = obj.optString("title")
    if (subjectId.isBlank() || title.isBlank()) return null
    val subjectType = obj.optInt("subjectType", if (defaultType == "Series") 2 else 1)
    val coverObj = obj.optJSONObject("cover")
    val coverUrl = coverObj?.optString("url")?.ifBlank { null } ?: fallbackCover?.ifBlank { null }
    val mediaType =
      defaultType ?: if (subjectType == 1) "Movie" else "Series"
    val resolvedDetailPath = obj.optString("detailPath").ifBlank { null }
    if (!resolvedDetailPath.isNullOrBlank()) {
      detailPathCache[subjectId] = resolvedDetailPath
    }
    return MediaItem(
      subjectId = subjectId,
      title = title,
      mediaType = mediaType,
      releaseDate = obj.optString("releaseDate").ifBlank { null },
      imdbRating = obj.optString("imdbRatingValue").ifBlank { null },
      genre = obj.optString("genre").ifBlank { null },
      coverUrl = coverUrl,
      detailPath = resolvedDetailPath,
      language = obj.optString("language").ifBlank { null },
    )
  }

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  companion object {
    private const val API_BASE = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    private const val NATIVE_API_BASE = "https://api3.aoneroom.com"
    private const val NATIVE_API_FALLBACK = "https://apig.inmoviebox.com"
    private val SIGNED_BOOTSTRAP_URLS =
      listOf(
        "https://apig.inmoviebox.com/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1",
        "https://api3.aoneroom.com/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1",
      )
    private val WEB_BOOTSTRAP_URLS =
      listOf(
        "https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=moviebox.ph",
        "https://movie-box.co/wefeed-h5api-bff/home?host=moviebox.ph",
        "https://123moviesfree.club/wefeed-h5api-bff/home?host=moviebox.ph",
      )

    private val QUALITY_LADDER = listOf(2160, 1440, 1080, 720, 480, 360, 240)

    private val WEB_DETAIL_DOMAINS =
      listOf(
        "https://h5-api.aoneroom.com",
        "https://123moviesfree.club",
        "https://movie-box.co",
      )

    private val WEB_DOMAINS =
      listOf(
        "https://123moviesfree.club",
        "https://movie-box.co",
        "https://movieboxonline.net",
        "https://sflix.film",
        "https://h5-api.aoneroom.com",
        "https://netnaija.film",
        "https://movieboxhd.net",
      )
  }
}
