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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.HomeFeedData
import com.example.data.model.MediaItem
import com.example.ui.components.ErrorRetryBox
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.MediaPosterCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.DiscoverFilter
import com.example.ui.viewmodel.UiState

@Composable
fun DiscoverScreen(
  viewModel: CatalogViewModel,
  userName: String,
  userPhotoUrl: String?,
  onOpenStream: (MediaItem) -> Unit,
  onSignOutClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
  val homeState by viewModel.homeState.collectAsStateWithLifecycle()
  val moviesState by viewModel.moviesState.collectAsStateWithLifecycle()
  val seriesState by viewModel.seriesState.collectAsStateWithLifecycle()
  val animeState by viewModel.animeState.collectAsStateWithLifecycle()

  Column(modifier = modifier.fillMaxSize()) {
    // Top Brand Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Brush.linearGradient(listOf(ElectricCrimson, BroadcastCyan))),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
        Spacer(Modifier.width(10.dp))
        Column {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "LIVE HD STREAMING",
            style = MaterialTheme.typography.labelSmall,
            color = BroadcastCyan,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          color = SurfaceCard,
          shape = CircleShape,
          modifier =
            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!userPhotoUrl.isNullOrBlank()) {
              AsyncImage(
                model = userPhotoUrl,
                contentDescription = userName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(22.dp).clip(CircleShape),
              )
              Spacer(Modifier.width(6.dp))
            }
            Text(
              text = userName.substringBefore(" "),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
            )
          }
        }

        IconButton(
          onClick = onSignOutClick,
          modifier = Modifier.size(48.dp).testTag("sign_out_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = stringResource(R.string.cd_sign_out),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Filter Chips
    LazyRow(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        FilterChipItem(
          selected = selectedFilter == DiscoverFilter.FEATURED,
          label = stringResource(R.string.filter_featured),
          tag = "filter_featured_chip",
          onClick = { viewModel.selectFilter(DiscoverFilter.FEATURED) },
        )
      }
      item {
        FilterChipItem(
          selected = selectedFilter == DiscoverFilter.MOVIES,
          label = stringResource(R.string.filter_movies),
          tag = "filter_movies_chip",
          onClick = { viewModel.selectFilter(DiscoverFilter.MOVIES) },
        )
      }
      item {
        FilterChipItem(
          selected = selectedFilter == DiscoverFilter.SERIES,
          label = stringResource(R.string.filter_series),
          tag = "filter_series_chip",
          onClick = { viewModel.selectFilter(DiscoverFilter.SERIES) },
        )
      }
      item {
        FilterChipItem(
          selected = selectedFilter == DiscoverFilter.ANIME,
          label = stringResource(R.string.filter_anime),
          tag = "filter_anime_chip",
          onClick = { viewModel.selectFilter(DiscoverFilter.ANIME) },
        )
      }
    }

    when (selectedFilter) {
      DiscoverFilter.FEATURED -> {
        when (val state = homeState) {
          UiState.Loading -> LoadingCatalogPlaceholder()
          is UiState.Error ->
            ErrorRetryBox(message = state.message, onRetry = { viewModel.loadHomeFeed() })
          is UiState.Success -> FeaturedFeedContent(feed = state.data, onOpenStream = onOpenStream)
        }
      }
      DiscoverFilter.MOVIES -> {
        when (val state = moviesState) {
          UiState.Loading -> LoadingCatalogPlaceholder()
          is UiState.Error ->
            ErrorRetryBox(message = state.message, onRetry = { viewModel.loadMovies() })
          is UiState.Success -> CatalogGrid(items = state.data, onOpenStream = onOpenStream)
        }
      }
      DiscoverFilter.SERIES -> {
        when (val state = seriesState) {
          UiState.Loading -> LoadingCatalogPlaceholder()
          is UiState.Error ->
            ErrorRetryBox(message = state.message, onRetry = { viewModel.loadSeries() })
          is UiState.Success -> CatalogGrid(items = state.data, onOpenStream = onOpenStream)
        }
      }
      DiscoverFilter.ANIME -> {
        when (val state = animeState) {
          UiState.Loading -> LoadingCatalogPlaceholder()
          is UiState.Error ->
            ErrorRetryBox(message = state.message, onRetry = { viewModel.loadAnime() })
          is UiState.Success -> CatalogGrid(items = state.data, onOpenStream = onOpenStream)
        }
      }
    }
  }
}

@Composable
private fun FilterChipItem(
  selected: Boolean,
  label: String,
  tag: String,
  onClick: () -> Unit,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = ElectricCrimson,
        selectedLabelColor = Color.White,
        containerColor = SurfaceCard,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
      ),
    border =
      FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = ElectricCrimson,
      ),
    modifier = Modifier.testTag(tag),
  )
}

@Composable
private fun FeaturedFeedContent(
  feed: HomeFeedData,
  onOpenStream: (MediaItem) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(22.dp),
  ) {
    if (feed.banners.isNotEmpty()) {
      item {
        val pagerState = rememberPagerState(pageCount = { feed.banners.size })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
          ) { page ->
            val banner = feed.banners[page]
            HeroBannerCard(item = banner, onWatchClick = { onOpenStream(banner) })
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            repeat(feed.banners.size.coerceAtMost(8)) { idx ->
              val active = pagerState.currentPage == idx
              Box(
                modifier =
                  Modifier.padding(horizontal = 3.dp)
                    .height(5.dp)
                    .width(if (active) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                      if (active) ElectricCrimson else MaterialTheme.colorScheme.outline
                    )
              )
            }
          }
        }
      }
    }

    items(feed.sections, key = { it.title }) { section ->
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${section.items.size} streams",
            style = MaterialTheme.typography.labelMedium,
            color = BroadcastCyan,
          )
        }

        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(section.items, key = { it.subjectId }) { item ->
            MediaPosterCard(
              item = item,
              onClick = { onOpenStream(item) },
              modifier = Modifier.width(136.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HeroBannerCard(
  item: MediaItem,
  onWatchClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .aspectRatio(16f / 10f)
        .clip(MaterialTheme.shapes.large)
        .background(SurfaceCard)
        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
        .clickable(onClick = onWatchClick)
  ) {
    AsyncImage(
      model = item.coverUrl,
      contentDescription = item.title,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(
                ObsidianBg.copy(alpha = 0.25f),
                ObsidianBg.copy(alpha = 0.50f),
                ObsidianBg.copy(alpha = 0.96f),
              )
            )
          )
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp).align(Alignment.TopStart),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LivePulseBadge(label = "1080P LIVE STREAM")

      if (!item.imdbRating.isNullOrBlank() && item.imdbRating != "0") {
        Surface(color = ObsidianBg.copy(alpha = 0.78f), shape = CircleShape) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Star,
              contentDescription = null,
              tint = AmberGold,
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = item.imdbRating,
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
            )
          }
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomStart),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = item.title,
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      val meta =
        listOfNotNull(
            item.mediaType,
            item.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
            item.genre?.takeIf { it.isNotBlank() },
          )
          .joinToString("  •  ")
      if (meta.isNotBlank()) {
        Text(
          text = meta,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Button(
        onClick = onWatchClick,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.testTag("hero_watch_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.action_join_room),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun CatalogGrid(
  items: List<MediaItem>,
  onOpenStream: (MediaItem) -> Unit,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 136.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(items, key = { it.subjectId }) { item ->
      MediaPosterCard(item = item, onClick = { onOpenStream(item) })
    }
  }
}

@Composable
private fun LoadingCatalogPlaceholder() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator(color = ElectricCrimson)
      Text(
        text = "Connecting to live stream catalog…",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
