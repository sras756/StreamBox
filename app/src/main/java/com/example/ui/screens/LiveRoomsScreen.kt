package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.WatchRoom
import com.example.data.model.WatchlistItem
import com.example.ui.components.LivePulseBadge
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.SurfaceCard
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun LiveRoomsScreen(
  viewModel: CatalogViewModel,
  onJoinRoom: (WatchRoom) -> Unit,
  onResumeWatchlist: (WatchlistItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val roomsState by viewModel.liveWatchRooms.collectAsStateWithLifecycle()
  val watchlistState by viewModel.watchlist.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.LiveTv,
          contentDescription = null,
          tint = ElectricCrimson,
        )
        Text(
          text = stringResource(R.string.rooms_live_header),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
    }

    when (val state = roomsState) {
      UiState.Loading -> {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = ElectricCrimson)
          }
        }
      }
      is UiState.Error -> {
        item {
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      is UiState.Success -> {
        if (state.data.isEmpty()) {
          item {
            EmptyStateCard(message = stringResource(R.string.rooms_empty_live))
          }
        } else {
          items(state.data, key = { it.roomId }) { room ->
            LiveRoomCard(room = room, onClick = { onJoinRoom(room) })
          }
        }
      }
    }

    item {
      Spacer(Modifier.height(6.dp))
      Text(
        text = stringResource(R.string.rooms_watchlist_header),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }

    when (val state = watchlistState) {
      UiState.Loading -> {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = BroadcastCyan)
          }
        }
      }
      is UiState.Error -> {
        item {
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      is UiState.Success -> {
        if (state.data.isEmpty()) {
          item {
            EmptyStateCard(message = stringResource(R.string.rooms_empty_watchlist))
          }
        } else {
          items(state.data, key = { it.subjectId }) { item ->
            WatchlistRowCard(
              item = item,
              onResume = { onResumeWatchlist(item) },
              onRemove = { viewModel.removeFromWatchlist(item.subjectId) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LiveRoomCard(room: WatchRoom, onClick: () -> Unit) {
  Surface(
    color = SurfaceCard,
    shape = MaterialTheme.shapes.medium,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag("live_room_card_${room.roomId}"),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(width = 72.dp, height = 96.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
      ) {
        if (!room.coverUrl.isNullOrBlank()) {
          AsyncImage(
            model = room.coverUrl,
            contentDescription = room.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        LivePulseBadge(label = "LIVE CHAT")
        Text(
          text = room.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val detailText =
          buildString {
            append(room.mediaType)
            if (room.season > 0L) append(" • S${room.season} E${room.episode}")
            append(" • Host: ${room.hostName}")
          }
        Text(
          text = detailText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Join", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun WatchlistRowCard(
  item: WatchlistItem,
  onResume: () -> Unit,
  onRemove: () -> Unit,
) {
  Surface(
    color = SurfaceCard,
    shape = MaterialTheme.shapes.medium,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onResume),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(width = 56.dp, height = 76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
      ) {
        if (!item.coverUrl.isNullOrBlank()) {
          AsyncImage(
            model = item.coverUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val meta =
          buildString {
            append(item.mediaType)
            if (item.lastSeason > 0L) append(" • S${item.lastSeason} E${item.lastEpisode}")
            if (!item.imdbRating.isNullOrBlank() && item.imdbRating != "0") {
              append(" • IMDb ${item.imdbRating}")
            }
          }
        Text(
          text = meta,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
        Icon(
          imageVector = Icons.Filled.BookmarkRemove,
          contentDescription = "Remove from watchlist",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun EmptyStateCard(message: String) {
  Surface(
    color = SurfaceCard.copy(alpha = 0.6f),
    shape = MaterialTheme.shapes.medium,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(18.dp),
    )
  }
}
