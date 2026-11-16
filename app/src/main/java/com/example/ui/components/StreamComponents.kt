package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.model.MediaItem
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard

@Composable
fun LivePulseBadge(
  label: String,
  color: Color = ElectricCrimson,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "live_pulse")
  val pulseAlpha by
    transition.animateFloat(
      initialValue = 0.45f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
      label = "pulse_alpha",
    )

  Surface(
    color = color.copy(alpha = 0.2f),
    shape = CircleShape,
    modifier = modifier.border(1.dp, color.copy(alpha = 0.5f), CircleShape),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(7.dp)
            .alpha(pulseAlpha)
            .clip(CircleShape)
            .background(color)
      )
      Spacer(Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun MediaPosterCard(
  item: MediaItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag("media_card_${item.subjectId}"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(2f / 3f)
          .clip(MaterialTheme.shapes.medium)
          .background(SurfaceCard)
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
    ) {
      SubcomposeAsyncImage(
        model = item.coverUrl,
        contentDescription = item.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = {
          Box(
            modifier = Modifier.fillMaxSize().background(SurfaceCard),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Movie,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
              modifier = Modifier.size(32.dp),
            )
          }
        },
        error = {
          Box(
            modifier = Modifier.fillMaxSize().background(SurfaceCard),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Movie,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(32.dp),
            )
          }
        },
      )

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(72.dp)
            .align(Alignment.BottomCenter)
            .background(
              Brush.verticalGradient(
                listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.92f))
              )
            )
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopStart),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = ObsidianBg.copy(alpha = 0.78f),
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = if (item.mediaType == "Series") "SERIES" else "HD",
            style = MaterialTheme.typography.labelSmall,
            color = if (item.mediaType == "Series") BroadcastCyan else LiveEmerald,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
          )
        }

        if (!item.imdbRating.isNullOrBlank() && item.imdbRating != "0" && item.imdbRating != "0.0") {
          Surface(
            color = ObsidianBg.copy(alpha = 0.78f),
            shape = RoundedCornerShape(6.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = AmberGold,
                modifier = Modifier.size(11.dp),
              )
              Spacer(Modifier.width(3.dp))
              Text(
                text = item.imdbRating,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
              )
            }
          }
        }
      }
    }

    Text(
      text = item.title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    val subLabel =
      listOfNotNull(
          item.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
          item.genre?.substringBefore(",")?.trim()?.takeIf { it.isNotBlank() },
        )
        .joinToString(" • ")
    if (subLabel.isNotBlank()) {
      Text(
        text = subLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun ErrorRetryBox(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.CloudOff,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(40.dp),
    )
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      onClick = onRetry,
      colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
      modifier = Modifier.testTag("retry_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text(text = stringResource(R.string.action_retry))
    }
  }
}
