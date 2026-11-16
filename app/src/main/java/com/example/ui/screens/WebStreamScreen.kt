package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.remote.MovieBoxSigner
import com.example.ui.components.LivePulseBadge
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated

private data class SupportedSite(
  val name: String,
  val subtitle: String,
  val url: String,
)

private val SUPPORTED_STREAMING_SITES =
  listOf(
    SupportedSite(
      name = "MovieBox Official Web",
      subtitle = "Movies, Series, Anime & Dubs",
      url = "https://moviebox.ph/",
    ),
    SupportedSite(
      name = "123MoviesFree Mirror",
      subtitle = "High-Speed 1080p MP4 & DASH CDN",
      url = "https://123moviesfree.club/",
    ),
    SupportedSite(
      name = "Movie-Box Mirror",
      subtitle = "Alternative Web Streaming Portal",
      url = "https://movie-box.co/",
    ),
    SupportedSite(
      name = "MovieBox Online",
      subtitle = "Movies, Series & Global Mirrors",
      url = "https://movieboxonline.net/",
    ),
    SupportedSite(
      name = "SFlix Web Mirror",
      subtitle = "HD Streams & English Subtitles",
      url = "https://sflix.film/",
    ),
    SupportedSite(
      name = "MovieBoxHD Mirror",
      subtitle = "Fast Backup Web Streamer",
      url = "https://movieboxhd.net/",
    ),
  )

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebStreamScreen(
  onPlayDirectStream: (streamUrl: String, refererUrl: String, title: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var inputUrl by rememberSaveable { mutableStateOf("") }
  var activePageUrl by rememberSaveable { mutableStateOf<String?>(null) }
  var pageTitle by rememberSaveable { mutableStateOf("Streaming Websites") }
  var loadProgress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var customFullscreenView by remember { mutableStateOf<View?>(null) }
  var customFullscreenCallback by remember {
    mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
  }
  val detectedStreams = remember { mutableStateListOf<String>() }
  val keyboardController = LocalSoftwareKeyboardController.current

  fun openUrlOrDirectStream(rawInput: String) {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return
    val normalized =
      if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
      ) {
        trimmed
      } else {
        "https://$trimmed"
      }

    if (isDirectVideoUrl(normalized)) {
      val referer = activePageUrl ?: "https://moviebox.ph/"
      onPlayDirectStream(normalized, referer, pageTitle.ifBlank { "Web Stream" })
      return
    }

    inputUrl = normalized
    detectedStreams.clear()
    activePageUrl = normalized
    webViewRef?.loadUrl(normalized)
  }

  BackHandler(enabled = customFullscreenView != null || (activePageUrl != null && canGoBack)) {
    if (customFullscreenView != null) {
      customFullscreenCallback?.onCustomViewHidden()
      customFullscreenView = null
      customFullscreenCallback = null
    } else if (webViewRef?.canGoBack() == true) {
      webViewRef?.goBack()
    } else {
      activePageUrl = null
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      customFullscreenCallback?.onCustomViewHidden()
      webViewRef?.destroy()
      webViewRef = null
    }
  }

  Box(modifier = modifier.fillMaxSize().background(ObsidianBg)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top URL / Direct Stream Toolbar
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(SurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            placeholder = {
              Text(
                text = stringResource(R.string.websites_hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            singleLine = true,
            leadingIcon = {
              Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = BroadcastCyan,
              )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions =
              KeyboardActions(
                onGo = {
                  keyboardController?.hide()
                  openUrlOrDirectStream(inputUrl)
                }
              ),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BroadcastCyan,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
              ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("website_url_input"),
          )

          Button(
            onClick = {
              keyboardController?.hide()
              openUrlOrDirectStream(inputUrl)
            },
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            modifier = Modifier.testTag("website_go_button"),
          ) {
            Text("Go", style = MaterialTheme.typography.labelLarge)
          }
        }

        if (activePageUrl != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { activePageUrl = null },
                modifier = Modifier.size(40.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Home,
                  contentDescription = "Supported sites home",
                  tint = BroadcastCyan,
                )
              }
              IconButton(
                onClick = { webViewRef?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(40.dp),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(R.string.cd_back),
                  tint =
                    if (canGoBack) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
              }
              IconButton(
                onClick = { webViewRef?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(40.dp),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = "Forward",
                  tint =
                    if (canGoForward) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
              }
              IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier.size(40.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = "Reload page",
                  tint = Color.White,
                )
              }
            }

            if (detectedStreams.isNotEmpty()) {
              Surface(
                color = BroadcastCyan.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp),
                modifier =
                  Modifier.clickable {
                      val bestUrl = detectedStreams.firstOrNull() ?: return@clickable
                      onPlayDirectStream(
                        bestUrl,
                        activePageUrl ?: "https://moviebox.ph/",
                        pageTitle.ifBlank { "Web Stream" },
                      )
                    }
                    .testTag("play_detected_stream_button"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = null,
                    tint = BroadcastCyan,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(5.dp))
                  Text(
                    text = "Native HD (${detectedStreams.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = BroadcastCyan,
                  )
                }
              }
            }
          }

          if (loadProgress in 1..99) {
            LinearProgressIndicator(
              progress = { loadProgress / 100f },
              color = BroadcastCyan,
              trackColor = SurfaceCard,
              modifier = Modifier.fillMaxWidth().height(2.dp),
            )
          }
        }
      }

      if (activePageUrl == null) {
        // Supported Streaming Websites Hub
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              LivePulseBadge(label = "SUPPORTED STREAMING WEBSITES", color = BroadcastCyan)
              Text(
                text = "Browse Streaming Sites or Direct URLs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text =
                  "Open any supported streaming mirror below, enter your own streaming site URL, or paste a direct .mp4 / .m3u8 / .mpd link to stream in HD.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          items(SUPPORTED_STREAMING_SITES, key = { it.url }) { site ->
            Surface(
              color = SurfaceCard,
              shape = MaterialTheme.shapes.medium,
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                  .clip(MaterialTheme.shapes.medium)
                  .clickable { openUrlOrDirectStream(site.url) }
                  .testTag("supported_site_${site.name}"),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Surface(
                  color = ElectricCrimson.copy(alpha = 0.16f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.size(44.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Filled.Tv,
                      contentDescription = null,
                      tint = ElectricCrimson,
                    )
                  }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = site.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = site.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = site.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = BroadcastCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      } else {
        // Active Streaming Website WebView
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          AndroidView(
            factory = { ctx ->
              WebView(ctx).apply {
                layoutParams =
                  ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                  )
                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  mediaPlaybackRequiresUserGesture = false
                  useWideViewPort = true
                  loadWithOverviewMode = true
                  mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                  userAgentString = MovieBoxSigner.WEB_USER_AGENT
                }
                webViewClient =
                  object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                      super.onPageFinished(view, url)
                      if (!url.isNullOrBlank()) {
                        inputUrl = url
                      }
                      canGoBack = view?.canGoBack() == true
                      canGoForward = view?.canGoForward() == true
                    }

                    override fun shouldOverrideUrlLoading(
                      view: WebView?,
                      request: WebResourceRequest?,
                    ): Boolean {
                      val reqUrl = request?.url?.toString() ?: return false
                      if (isDirectVideoUrl(reqUrl)) {
                        if (reqUrl !in detectedStreams) {
                          detectedStreams.add(0, reqUrl)
                        }
                        onPlayDirectStream(
                          reqUrl,
                          activePageUrl ?: "https://moviebox.ph/",
                          pageTitle.ifBlank { "Web Stream" },
                        )
                        return true
                      }
                      // Allow normal user-initiated or same-frame navigation
                      return false
                    }

                    override fun shouldInterceptRequest(
                      view: WebView?,
                      request: WebResourceRequest?,
                    ): android.webkit.WebResourceResponse? {
                      val url = request?.url?.toString()
                      if (url != null && isDirectVideoUrl(url)) {
                        post {
                          if (url !in detectedStreams) {
                            detectedStreams.add(0, url)
                          }
                        }
                      }
                      return super.shouldInterceptRequest(view, request)
                    }
                  }

                webChromeClient =
                  object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                      loadProgress = newProgress
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                      if (!title.isNullOrBlank()) {
                        pageTitle = title
                      }
                    }

                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                      if (customFullscreenView != null) {
                        callback?.onCustomViewHidden()
                        return
                      }
                      customFullscreenView = view
                      customFullscreenCallback = callback
                    }

                    override fun onHideCustomView() {
                      customFullscreenView = null
                      customFullscreenCallback?.onCustomViewHidden()
                      customFullscreenCallback = null
                    }
                  }

                activePageUrl?.let { loadUrl(it) }
                webViewRef = this
              }
            },
            modifier = Modifier.fillMaxSize(),
          )

          if (detectedStreams.isNotEmpty()) {
            Surface(
              color = ObsidianBg.copy(alpha = 0.94f),
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .padding(12.dp)
                  .border(1.dp, BroadcastCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "Detected Playable HD Video Streams (${detectedStreams.size})",
                  style = MaterialTheme.typography.labelMedium,
                  color = LiveEmerald,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(detectedStreams.take(5), key = { it }) { streamUrl ->
                    val tag =
                      when {
                        streamUrl.contains(".m3u8", ignoreCase = true) -> "HLS Stream"
                        streamUrl.contains(".mpd", ignoreCase = true) -> "DASH Stream"
                        else -> "MP4 Stream"
                      }
                    Button(
                      onClick = {
                        onPlayDirectStream(
                          streamUrl,
                          activePageUrl ?: "https://moviebox.ph/",
                          pageTitle.ifBlank { "Web Stream" },
                        )
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.PlayCircleFilled,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(Modifier.width(5.dp))
                      Text("Play $tag", style = MaterialTheme.typography.labelMedium)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // HTML5 Video Fullscreen Overlay from WebView
    val fsView = customFullscreenView
    if (fsView != null) {
      AndroidView(
        factory = { ctx ->
          FrameLayout(ctx).apply {
            layoutParams =
              FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
              )
            (fsView.parent as? ViewGroup)?.removeView(fsView)
            addView(fsView)
          }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
      )
    }
  }
}

private fun isDirectVideoUrl(url: String): Boolean {
  val lower = url.lowercase()
  return lower.contains(".m3u8") ||
    lower.contains(".mpd") ||
    lower.contains(".mp4") && !lower.contains("thumb")
}
