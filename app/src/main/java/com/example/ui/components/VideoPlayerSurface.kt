package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.StreamSource
import com.example.data.model.SubtitleTrack
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.ObsidianBg
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(
  title: String,
  stream: StreamSource?,
  subtitle: SubtitleTrack?,
  viewerCount: Int,
  isFullscreen: Boolean,
  isResolvingStream: Boolean,
  streamErrorMessage: String?,
  httpClient: OkHttpClient,
  onToggleFullscreen: () -> Unit,
  onBack: () -> Unit,
  onRetryStream: () -> Unit,
  onAutoFallbackStream: () -> Boolean = { false },
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var showControls by remember { mutableStateOf(true) }
  var isPlaying by remember { mutableStateOf(false) }
  var isBuffering by remember { mutableStateOf(false) }
  var playbackError by remember { mutableStateOf<String?>(null) }
  var currentPositionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var isDraggingSlider by remember { mutableStateOf(false) }
  var sliderDragPositionMs by remember { mutableLongStateOf(0L) }

  val currentOnAutoFallback by rememberUpdatedState(onAutoFallbackStream)

  val dataSourceFactory =
    remember(context, httpClient, stream) {
      val okHttpFactory =
        OkHttpDataSource.Factory(httpClient).apply {
          val headers = stream?.headers.orEmpty()
          val userAgent =
            headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
          if (!userAgent.isNullOrBlank()) {
            setUserAgent(userAgent)
          }
          val extraHeaders = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
          if (extraHeaders.isNotEmpty()) {
            setDefaultRequestProperties(extraHeaders)
          }
        }
      DefaultDataSource.Factory(context, okHttpFactory)
    }

  val exoPlayer =
    remember(context) {
      ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(10_000L)
        .setSeekForwardIncrementMs(10_000L)
        .build()
        .apply { playWhenReady = true }
    }

  DisposableEffect(exoPlayer) {
    val listener =
      object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
          isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
          isBuffering = state == Player.STATE_BUFFERING
          if (state == Player.STATE_READY) {
            playbackError = null
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          isBuffering = false
          if (currentOnAutoFallback()) {
            playbackError = null
            return
          }
          playbackError = formatPlaybackError(error)
        }
      }
    exoPlayer.addListener(listener)
    onDispose {
      exoPlayer.removeListener(listener)
      exoPlayer.release()
    }
  }

  DisposableEffect(lifecycleOwner, exoPlayer) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
        Lifecycle.Event.ON_STOP -> exoPlayer.pause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(stream, subtitle) {
    if (stream == null) {
      exoPlayer.stop()
      exoPlayer.clearMediaItems()
      return@LaunchedEffect
    }
    playbackError = null
    val previousPos = exoPlayer.currentPosition.coerceAtLeast(0L)
    val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(stream.url))
    if (stream.format.equals("DASH", ignoreCase = true) || stream.url.contains(".mpd")) {
      mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
    } else if (stream.url.contains(".m3u8")) {
      mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }

    if (subtitle != null && subtitle.url.isNotBlank()) {
      val subMime =
        if (subtitle.url.contains(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT
        else MimeTypes.APPLICATION_SUBRIP
      val subConfig =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
          .setMimeType(subMime)
          .setLanguage(subtitle.code.ifBlank { "en" })
          .setLabel(subtitle.language)
          .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
          .build()
      mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
    }

    val mediaSource =
      DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItemBuilder.build())
    exoPlayer.setMediaSource(mediaSource, previousPos)
    exoPlayer.prepare()
    exoPlayer.playWhenReady = true
  }

  LaunchedEffect(exoPlayer, isPlaying, isDraggingSlider) {
    while (true) {
      if (!isDraggingSlider) {
        currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val d = exoPlayer.duration
        if (d > 0L) durationMs = d
      }
      delay(400)
    }
  }

  LaunchedEffect(showControls, isPlaying) {
    if (showControls && isPlaying) {
      delay(4000)
      showControls = false
    }
  }

  Box(
    modifier =
      modifier
        .background(Color.Black)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
        ) {
          showControls = !showControls
        }
  ) {
    AndroidView(
      factory = { ctx ->
        PlayerView(ctx).apply {
          player = exoPlayer
          useController = false
          resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
          layoutParams =
            FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
      },
      update = { view -> view.player = exoPlayer },
      modifier = Modifier.fillMaxSize(),
    )

    val displayError = streamErrorMessage ?: playbackError

    if (isResolvingStream || (isBuffering && displayError == null)) {
      Box(
        modifier = Modifier.fillMaxSize().padding(top = 56.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CircularProgressIndicator(
            color = ElectricCrimson,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp),
          )
          Text(
            text = if (isResolvingStream) "Resolving 1080p HD stream…" else "Buffering HD feed…",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
          )
        }
      }
    }

    if (displayError != null && !isResolvingStream) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(ObsidianBg.copy(alpha = 0.90f))
            .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = displayError,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
          Button(
            onClick = {
              playbackError = null
              onRetryStream()
            },
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
            modifier = Modifier.testTag("retry_stream_button"),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry Stream")
          }
        }
      }
    }

    val showTopBar = showControls || !isPlaying || displayError != null || isResolvingStream
    val showPlaybackControls =
      (showControls || !isPlaying) && displayError == null && !isResolvingStream && stream != null

    AnimatedVisibility(
      visible = showTopBar,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(
              Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
              )
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("player_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = Color.White,
            )
          }
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (stream != null) {
            Surface(
              color = BroadcastCyan.copy(alpha = 0.22f),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = stream.quality,
                style = MaterialTheme.typography.labelSmall,
                color = BroadcastCyan,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
          Surface(
            color = ElectricCrimson.copy(alpha = 0.26f),
            shape = CircleShape,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = viewerCount.coerceAtLeast(1).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
              )
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = showPlaybackControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(
                  Color.Transparent,
                  Color.Black.copy(alpha = 0.25f),
                  Color.Black.copy(alpha = 0.82f),
                )
              )
            )
      ) {
        // Center playback controls
        Row(
          modifier = Modifier.align(Alignment.Center),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = { exoPlayer.seekBack() },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Replay10,
              contentDescription = stringResource(R.string.cd_seek_back),
              tint = Color.White,
              modifier = Modifier.size(30.dp),
            )
          }

          Surface(
            color = ElectricCrimson,
            shape = CircleShape,
            modifier =
              Modifier.size(58.dp).testTag("player_play_pause_button").clickable {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
              },
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(34.dp),
              )
            }
          }

          IconButton(
            onClick = { exoPlayer.seekForward() },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Forward10,
              contentDescription = stringResource(R.string.cd_seek_forward),
              tint = Color.White,
              modifier = Modifier.size(30.dp),
            )
          }
        }

        // Bottom scrub bar & fullscreen
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp)
              .align(Alignment.BottomCenter)
        ) {
          val effectivePos = if (isDraggingSlider) sliderDragPositionMs else currentPositionMs
          val safeDuration = durationMs.coerceAtLeast(1L)
          Slider(
            value = (effectivePos.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f),
            onValueChange = { frac ->
              isDraggingSlider = true
              sliderDragPositionMs = (frac * safeDuration).toLong()
            },
            onValueChangeFinished = {
              exoPlayer.seekTo(sliderDragPositionMs)
              currentPositionMs = sliderDragPositionMs
              isDraggingSlider = false
            },
            colors =
              SliderDefaults.colors(
                thumbColor = ElectricCrimson,
                activeTrackColor = ElectricCrimson,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
              ),
            modifier = Modifier.fillMaxWidth(),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${formatDuration(effectivePos)} / ${formatDuration(durationMs)}",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
              if (subtitle != null) {
                Surface(
                  color = Color.White.copy(alpha = 0.18f),
                  shape = RoundedCornerShape(4.dp),
                  modifier = Modifier.padding(end = 6.dp),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ClosedCaption,
                      contentDescription = null,
                      tint = BroadcastCyan,
                      modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                      text = subtitle.code.uppercase().ifBlank { "CC" },
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                    )
                  }
                }
              }

              IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.size(48.dp).testTag("fullscreen_toggle_button"),
              ) {
                Icon(
                  imageVector =
                    if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                  contentDescription = stringResource(R.string.cd_fullscreen),
                  tint = Color.White,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun formatPlaybackError(error: PlaybackException): String {
  var cause: Throwable? = error.cause
  while (cause != null) {
    if (cause is HttpDataSource.InvalidResponseCodeException) {
      return "Stream CDN returned HTTP ${cause.responseCode} (${error.errorCodeName})"
    }
    if (cause is HttpDataSource.HttpDataSourceException) {
      val detail = cause.cause?.message ?: cause.message
      if (!detail.isNullOrBlank()) {
        return "$detail (${error.errorCodeName})"
      }
    }
    if (cause.cause == null) {
      val msg = cause.message
      if (!msg.isNullOrBlank()) {
        return "$msg (${error.errorCodeName})"
      }
    }
    cause = cause.cause
  }
  return "${error.localizedMessage ?: "Playback failed"} (${error.errorCodeName})"
}

private fun formatDuration(ms: Long): String {
  if (ms <= 0L) return "00:00"
  val totalSec = ms / 1000L
  val hours = totalSec / 3600L
  val mins = (totalSec % 3600L) / 60L
  val secs = totalSec % 60L
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, mins, secs)
  } else {
    "%02d:%02d".format(mins, secs)
  }
}
