package com.example.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MovieBoxSignerTest {

  @Test
  fun generateXClientToken_matchesReversedTimestampMd5Format() {
    val ts = 1700000000123L
    val token = MovieBoxSigner.generateXClientToken(ts)
    val parts = token.split(",")
    assertEquals(2, parts.size)
    assertEquals("1700000000123", parts[0])
    assertEquals(32, parts[1].length)
    assertEquals(MovieBoxSigner.md5Hex("3210000000071".toByteArray()), parts[1])
  }

  @Test
  fun buildCanonicalString_sortsQueryParameters() {
    val canonical =
      MovieBoxSigner.buildCanonicalString(
        method = "get",
        accept = "application/json",
        contentType = "application/json",
        url = "https://api3.aoneroom.com/wefeed-mobile-bff/subject-api/play-info?se=1&ep=2&subjectId=123",
        body = null,
        timestamp = 1700000000000L,
      )
    val lines = canonical.split("\n")
    assertEquals("GET", lines[0])
    assertEquals("/wefeed-mobile-bff/subject-api/play-info?ep=2&se=1&subjectId=123", lines[6])
  }

  @Test
  fun generateXTrSignature_formatsTimestampVersionAndBase64() {
    val sig =
      MovieBoxSigner.generateXTrSignature(
        method = "GET",
        accept = "application/json",
        contentType = "application/json",
        url = "https://apig.inmoviebox.com/wefeed-mobile-bff/tab/ranking-list?tabId=0",
        body = null,
        timestamp = 1700000000000L,
      )
    val parts = sig.split("|")
    assertEquals(3, parts.size)
    assertEquals("1700000000000", parts[0])
    assertEquals("2", parts[1])
    assertTrue(parts[2].isNotBlank())
  }

  @Test
  fun buildAuthHeaders_usesUnescapedTimezoneAndOmitsHttp2ConnectionHeader() {
    val headers =
      MovieBoxSigner.buildAuthHeaders(
        method = "GET",
        url = "https://apig.inmoviebox.com/wefeed-mobile-bff/tab/ranking-list?tabId=0",
      )
    val clientInfo = headers.getValue("x-client-info")
    assertTrue(clientInfo.contains("\"timezone\":\"Asia/Calcutta\""))
    assertFalse(clientInfo.contains("\\/"))
    assertFalse(headers.containsKey("connection"))
  }

  @Test
  fun extractPolicyResource_parsesEdgeCacheCookie() {
    val cookie = "Edge-Cache-Cookie=urlprefix=aHR0cHM6Ly9jZG4uZXhhbXBsZS5jb20vaGxzLzEyMy8:Expires=123"
    val result = MovieBoxSigner.extractPolicyResource(cookie)
    assertEquals("https://cdn.example.com/hls/123/index.mpd", result)
  }

  @Test
  fun apiClient_bootstrapsTokenAndResolvesDetailsAndStreamsWithOkHttp() = runBlocking {
    val client = MovieBoxApiClient()
    val token = client.bootstrapToken(force = true)
    assertTrue("Expected non-blank token", token.isNotBlank())

    val detail = client.getDetails("1824699943735411040", detailPathHint = "love-in-disguise-g88MCLZ8Na2")
    assertEquals("Love in Disguise", detail.title)

    val seasons = client.getEpisodes("1824699943735411040", detailPathHint = "love-in-disguise-g88MCLZ8Na2")
    assertTrue("Expected seasons list", seasons.isNotEmpty())

    val streams =
      client.getStreams(
        subjectId = "1824699943735411040",
        season = 1,
        episode = 1,
        detailPath = detail.detailPath,
      )
    assertTrue("Expected at least 1 stream, got ${streams.size}", streams.isNotEmpty())
  }

  @Test
  fun apiClient_loadsLiveAnimeAndStreamsReturnHttp200Or206() = runBlocking {
    val client = MovieBoxApiClient()
    val anime = client.getAnime()
    assertTrue("Expected live anime catalog entries", anime.isNotEmpty())

    val beastDetail = client.getDetails("568566274385663264", detailPathHint = "the-beast-C7GYk6s20G")
    assertTrue("Expected Beast detail title", beastDetail.title.isNotBlank())

    val beastStreams =
      client.getStreams(
        subjectId = "568566274385663264",
        season = 0,
        episode = 0,
        detailPath = "the-beast-C7GYk6s20G",
      )
    assertTrue("Expected streams for The Beast", beastStreams.isNotEmpty())

    var playableCount = 0
    for (stream in beastStreams.take(3)) {
      val reqBuilder = okhttp3.Request.Builder().url(stream.url).get()
      if (!stream.format.equals("DASH", ignoreCase = true)) {
        reqBuilder.addHeader("Range", "bytes=0-1023")
      }
      stream.headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
      client.httpClient.newCall(reqBuilder.build()).execute().use { resp ->
        if (resp.code in listOf(200, 206)) {
          playableCount++
        }
      }
    }
    assertTrue("Expected at least 1 playable CDN stream (200/206), got $playableCount", playableCount > 0)
  }
}
