package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.MediaDetail
import com.example.data.model.MediaDub
import com.example.data.model.SeasonInfo
import com.example.data.model.StreamSource
import com.example.data.model.SubtitleTrack
import com.example.data.model.ViewerPresence
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.VideoPlayerSurface
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WatchRoomViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient

@Composable
fun WatchRoomScreen(
  viewModel: WatchRoomViewModel,
  httpClient: OkHttpClient,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val activity = remember(context) { context.findActivity() }

  var isFullscreen by rememberSaveable { mutableStateOf(false) }
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }

  val activeSubjectId by viewModel.activeSubjectId.collectAsStateWithLifecycle()
  val availableDubs by viewModel.availableDubs.collectAsStateWithLifecycle()
  val title by viewModel.currentTitle.collectAsStateWithLifecycle()
  val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
  val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
  val detailState by viewModel.detailState.collectAsStateWithLifecycle()
  val seasons by viewModel.seasons.collectAsStateWithLifecycle()
  val streamsState by viewModel.streamsState.collectAsStateWithLifecycle()
  val selectedStream by viewModel.selectedStream.collectAsStateWithLifecycle()
  val subtitles by viewModel.subtitles.collectAsStateWithLifecycle()
  val selectedSubtitle by viewModel.selectedSubtitle.collectAsStateWithLifecycle()
  val chatMessagesState by viewModel.chatMessages.collectAsStateWithLifecycle()
  val activeViewers by viewModel.activeViewers.collectAsStateWithLifecycle()
  val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
  val isSendingChat by viewModel.isSendingChat.collectAsStateWithLifecycle()
  val chatError by viewModel.chatError.collectAsStateWithLifecycle()

  LaunchedEffect(isFullscreen) {
    val window = activity?.window ?: return@LaunchedEffect
    val controller = WindowCompat.getInsetsController(window, view)
    if (isFullscreen) {
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      val window = activity?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
      }
      activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }

  BackHandler(enabled = isFullscreen) { isFullscreen = false }

  val displayTitle =
    if (selectedSeason > 0) "$title • S${selectedSeason} E${selectedEpisode}" else title
  val isResolvingStream = streamsState is UiState.Loading
  val streamErrorMsg = (streamsState as? UiState.Error)?.message
  val availableStreams = (streamsState as? UiState.Success)?.data.orEmpty()

  if (isFullscreen) {
    VideoPlayerSurface(
      title = displayTitle,
      stream = selectedStream,
      availableStreams = availableStreams,
      subtitle = selectedSubtitle,
      availableSubtitles = subtitles,
      viewerCount = activeViewers.size,
      isFullscreen = true,
      isResolvingStream = isResolvingStream,
      streamErrorMessage = streamErrorMsg,
      httpClient = httpClient,
      onToggleFullscreen = { isFullscreen = false },
      onBack = { isFullscreen = false },
      onRetryStream = { viewModel.reloadStreams() },
      onSelectStream = { viewModel.selectStreamSource(it) },
      onSelectSubtitle = { viewModel.selectSubtitle(it) },
      onAutoFallbackStream = { viewModel.tryFallbackStream() },
      modifier = Modifier.fillMaxSize(),
    )
    return
  }

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(ObsidianBg)
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding()
  ) {
    val isWideLayout = maxWidth >= 720.dp

    if (isWideLayout) {
      Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(0.62f).fillMaxHeight()) {
          VideoPlayerSurface(
            title = displayTitle,
            stream = selectedStream,
            availableStreams = availableStreams,
            subtitle = selectedSubtitle,
            availableSubtitles = subtitles,
            viewerCount = activeViewers.size,
            isFullscreen = false,
            isResolvingStream = isResolvingStream,
            streamErrorMessage = streamErrorMsg,
            httpClient = httpClient,
            onToggleFullscreen = { isFullscreen = true },
            onBack = onBack,
            onRetryStream = { viewModel.reloadStreams() },
            onSelectStream = { viewModel.selectStreamSource(it) },
            onSelectSubtitle = { viewModel.selectSubtitle(it) },
            onAutoFallbackStream = { viewModel.tryFallbackStream() },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
          )

          RoomTabsAndContent(
            selectedTab = if (selectedTab == 0) 1 else selectedTab,
            onTabSelected = { selectedTab = it },
            includeChatTab = false,
            title = displayTitle,
            isBookmarked = isBookmarked,
            onToggleBookmark = { viewModel.toggleBookmark() },
            streamsState = streamsState,
            selectedStream = selectedStream,
            onSelectStream = { viewModel.selectStreamSource(it) },
            availableDubs = availableDubs,
            activeSubjectId = activeSubjectId,
            onSelectDub = { viewModel.switchDub(it) },
            subtitles = subtitles,
            selectedSubtitle = selectedSubtitle,
            onSelectSubtitle = { viewModel.selectSubtitle(it) },
            detailState = detailState,
            seasons = seasons,
            selectedSeason = selectedSeason,
            selectedEpisode = selectedEpisode,
            onSelectEpisode = { s, e -> viewModel.selectEpisode(s, e) },
            onRetryStreams = { viewModel.reloadStreams() },
            onRetryDetails = { viewModel.loadDetailsAndEpisodes() },
            chatPanel = {},
            modifier = Modifier.weight(1f),
          )
        }

        Surface(
          color = SurfaceElevated,
          modifier =
            Modifier.weight(0.38f)
              .fillMaxHeight()
              .border(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          RealTimeChatPane(
            messagesState = chatMessagesState,
            viewers = activeViewers,
            currentUserId = viewModel.currentUserId,
            isSending = isSendingChat,
            chatError = chatError,
            onSendMessage = { viewModel.sendChatMessage(it) },
            onSendReaction = { viewModel.sendReaction(it) },
            onDeleteMessage = { viewModel.deleteMessage(it) },
          )
        }
      }
    } else {
      Column(modifier = Modifier.fillMaxSize()) {
        VideoPlayerSurface(
          title = displayTitle,
          stream = selectedStream,
          availableStreams = availableStreams,
          subtitle = selectedSubtitle,
          availableSubtitles = subtitles,
          viewerCount = activeViewers.size,
          isFullscreen = false,
          isResolvingStream = isResolvingStream,
          streamErrorMessage = streamErrorMsg,
          httpClient = httpClient,
          onToggleFullscreen = { isFullscreen = true },
          onBack = onBack,
          onRetryStream = { viewModel.reloadStreams() },
          onSelectStream = { viewModel.selectStreamSource(it) },
          onSelectSubtitle = { viewModel.selectSubtitle(it) },
          onAutoFallbackStream = { viewModel.tryFallbackStream() },
          modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )

        RoomTabsAndContent(
          selectedTab = selectedTab,
          onTabSelected = { selectedTab = it },
          includeChatTab = true,
          title = displayTitle,
          isBookmarked = isBookmarked,
          onToggleBookmark = { viewModel.toggleBookmark() },
          streamsState = streamsState,
          selectedStream = selectedStream,
          onSelectStream = { viewModel.selectStreamSource(it) },
          availableDubs = availableDubs,
          activeSubjectId = activeSubjectId,
          onSelectDub = { viewModel.switchDub(it) },
          subtitles = subtitles,
          selectedSubtitle = selectedSubtitle,
          onSelectSubtitle = { viewModel.selectSubtitle(it) },
          detailState = detailState,
          seasons = seasons,
          selectedSeason = selectedSeason,
          selectedEpisode = selectedEpisode,
          onSelectEpisode = { s, e -> viewModel.selectEpisode(s, e) },
          onRetryStreams = { viewModel.reloadStreams() },
          onRetryDetails = { viewModel.loadDetailsAndEpisodes() },
          chatPanel = {
            RealTimeChatPane(
              messagesState = chatMessagesState,
              viewers = activeViewers,
              currentUserId = viewModel.currentUserId,
              isSending = isSendingChat,
              chatError = chatError,
              onSendMessage = { viewModel.sendChatMessage(it) },
              onSendReaction = { viewModel.sendReaction(it) },
              onDeleteMessage = { viewModel.deleteMessage(it) },
            )
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomTabsAndContent(
  selectedTab: Int,
  onTabSelected: (Int) -> Unit,
  includeChatTab: Boolean,
  title: String,
  isBookmarked: Boolean,
  onToggleBookmark: () -> Unit,
  streamsState: UiState<List<StreamSource>>,
  selectedStream: StreamSource?,
  onSelectStream: (StreamSource) -> Unit,
  availableDubs: List<MediaDub>,
  activeSubjectId: String,
  onSelectDub: (MediaDub) -> Unit,
  subtitles: List<SubtitleTrack>,
  selectedSubtitle: SubtitleTrack?,
  onSelectSubtitle: (SubtitleTrack?) -> Unit,
  detailState: UiState<MediaDetail>,
  seasons: List<SeasonInfo>,
  selectedSeason: Int,
  selectedEpisode: Int,
  onSelectEpisode: (Int, Int) -> Unit,
  onRetryStreams: () -> Unit,
  onRetryDetails: () -> Unit,
  chatPanel: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tabs =
    if (includeChatTab) {
      listOf(
        stringResource(R.string.tab_live_chat),
        stringResource(R.string.tab_streams),
        stringResource(R.string.tab_episodes),
      )
    } else {
      listOf(
        stringResource(R.string.tab_streams),
        stringResource(R.string.tab_episodes),
      )
    }

  val activeIndex =
    if (includeChatTab) selectedTab.coerceIn(0, 2) else (selectedTab - 1).coerceIn(0, 1)

  Column(modifier = modifier.fillMaxWidth()) {
    // Compact room title + bookmark strip
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(SurfaceElevated)
          .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (selectedStream != null) {
          Text(
            text = "${selectedStream.quality} • ${selectedStream.format} • ${selectedStream.source}",
            style = MaterialTheme.typography.labelSmall,
            color = BroadcastCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      IconButton(
        onClick = onToggleBookmark,
        modifier = Modifier.size(48.dp).testTag("bookmark_button"),
      ) {
        Icon(
          imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
          contentDescription = stringResource(R.string.cd_bookmark),
          tint = if (isBookmarked) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    SecondaryTabRow(
      selectedTabIndex = activeIndex,
      containerColor = SurfaceElevated,
      contentColor = ElectricCrimson,
      indicator = {
        TabRowDefaults.SecondaryIndicator(
          modifier = Modifier.tabIndicatorOffset(activeIndex),
          color = ElectricCrimson,
        )
      },
    ) {
      tabs.forEachIndexed { index, label ->
        Tab(
          selected = activeIndex == index,
          onClick = { onTabSelected(if (includeChatTab) index else index + 1) },
          text = {
            Text(
              text = label,
              style = MaterialTheme.typography.labelLarge,
              color =
                if (activeIndex == index) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
        )
      }
    }

    val effectiveTab = if (includeChatTab) activeIndex else activeIndex + 1
    when (effectiveTab) {
      0 -> chatPanel()
      1 ->
        StreamsAndSubtitlesPane(
          streamsState = streamsState,
          selectedStream = selectedStream,
          onSelectStream = onSelectStream,
          onRetryStreams = onRetryStreams,
          availableDubs = availableDubs,
          activeSubjectId = activeSubjectId,
          onSelectDub = onSelectDub,
          subtitles = subtitles,
          selectedSubtitle = selectedSubtitle,
          onSelectSubtitle = onSelectSubtitle,
        )
      else ->
        DetailsAndEpisodesPane(
          detailState = detailState,
          seasons = seasons,
          selectedSeason = selectedSeason,
          selectedEpisode = selectedEpisode,
          onSelectEpisode = onSelectEpisode,
          onRetryDetails = onRetryDetails,
        )
    }
  }
}

@Composable
private fun RealTimeChatPane(
  messagesState: UiState<List<ChatMessage>>,
  viewers: List<ViewerPresence>,
  currentUserId: String,
  isSending: Boolean,
  chatError: String?,
  onSendMessage: (String) -> Unit,
  onSendReaction: (String) -> Unit,
  onDeleteMessage: (String) -> Unit,
) {
  var messageText by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()
  val messages = (messagesState as? UiState.Success)?.data.orEmpty()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
    // Active Viewers Header
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(SurfaceElevated.copy(alpha = 0.7f))
          .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      LivePulseBadge(
        label = "${viewers.size.coerceAtLeast(1)} WATCHING LIVE",
        color = LiveEmerald,
      )

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("🔥", "👏", "❤️", "🤯").forEach { emoji ->
          Surface(
            color = SurfaceCard,
            shape = CircleShape,
            modifier =
              Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clip(CircleShape)
                .clickable { onSendReaction(emoji) }
                .testTag("reaction_$emoji"),
          ) {
            Text(
              text = emoji,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

    // Chat Messages Feed
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (messagesState) {
        UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BroadcastCyan, modifier = Modifier.size(28.dp))
          }
        }
        is UiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = messagesState.message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        is UiState.Success -> {
          if (messages.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(R.string.chat_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(messages, key = { it.id.ifBlank { "${it.userId}_${it.text}_${it.createdAt}" } }) { msg ->
                ChatMessageRow(
                  message = msg,
                  isOwn = msg.userId == currentUserId,
                  onDelete = { if (msg.id.isNotBlank()) onDeleteMessage(msg.id) },
                )
              }
            }
          }
        }
      }
    }

    if (chatError != null) {
      Text(
        text = chatError,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
      )
    }

    // Sticky Bottom Input
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(SurfaceElevated)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = messageText,
        onValueChange = { messageText = it },
        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions =
          KeyboardActions(
            onSend = {
              if (messageText.isNotBlank()) {
                onSendMessage(messageText)
                messageText = ""
              }
            }
          ),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BroadcastCyan,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = SurfaceCard,
            unfocusedContainerColor = SurfaceCard,
          ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.weight(1f).testTag("chat_input"),
      )

      Surface(
        color = if (messageText.isNotBlank() && !isSending) ElectricCrimson else SurfaceCard,
        shape = CircleShape,
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = messageText.isNotBlank() && !isSending) {
              onSendMessage(messageText)
              messageText = ""
            }
            .testTag("send_chat_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          if (isSending) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp),
            )
          } else {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = stringResource(R.string.cd_send_message),
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChatMessageRow(
  message: ChatMessage,
  isOwn: Boolean,
  onDelete: () -> Unit,
) {
  val isReaction = message.messageType == "reaction"
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
  val timeStr = message.createdAt?.toDate()?.let { timeFormat.format(it) } ?: ""

  Surface(
    color = if (isOwn) ElectricCrimson.copy(alpha = 0.14f) else SurfaceCard,
    shape = MaterialTheme.shapes.small,
    modifier =
      Modifier.fillMaxWidth()
        .border(
          1.dp,
          if (isOwn) ElectricCrimson.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
          MaterialTheme.shapes.small,
        ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (!message.senderAvatarUrl.isNullOrBlank()) {
        AsyncImage(
          model = message.senderAvatarUrl,
          contentDescription = message.senderName,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(28.dp).clip(CircleShape),
        )
      } else {
        Box(
          modifier = Modifier.size(28.dp).clip(CircleShape).background(BroadcastCyan.copy(alpha = 0.25f)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = message.senderName.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = BroadcastCyan,
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = message.senderName,
            style = MaterialTheme.typography.labelLarge,
            color = if (isOwn) ElectricCrimson else BroadcastCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (!message.streamQuality.isNullOrBlank()) {
            Surface(color = ObsidianBg.copy(alpha = 0.65f), shape = RoundedCornerShape(4.dp)) {
              Text(
                text = message.streamQuality,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
              )
            }
          }
          if (timeStr.isNotBlank()) {
            Text(
              text = timeStr,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
          }
        }

        Text(
          text = if (isReaction) "reacted ${message.text}" else message.text,
          style = if (isReaction) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      if (isOwn && message.id.isNotBlank()) {
        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_message),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun StreamsAndSubtitlesPane(
  streamsState: UiState<List<StreamSource>>,
  selectedStream: StreamSource?,
  onSelectStream: (StreamSource) -> Unit,
  onRetryStreams: () -> Unit,
  availableDubs: List<MediaDub>,
  activeSubjectId: String,
  onSelectDub: (MediaDub) -> Unit,
  subtitles: List<SubtitleTrack>,
  selectedSubtitle: SubtitleTrack?,
  onSelectSubtitle: (SubtitleTrack?) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Hd, contentDescription = null, tint = BroadcastCyan)
        Text(
          text = "Available Video Feeds",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
    }

    when (streamsState) {
      UiState.Loading -> {
        item { CircularProgressIndicator(color = ElectricCrimson) }
      }
      is UiState.Error -> {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = streamsState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
            Button(
              onClick = onRetryStreams,
              colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Retry Streams")
            }
          }
        }
      }
      is UiState.Success -> {
        if (streamsState.data.isEmpty()) {
          item {
            Text(
              text = "No streams resolved for this selection.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          items(streamsState.data, key = { it.url }) { stream ->
            val isActive = selectedStream?.url == stream.url
            Surface(
              color = if (isActive) BroadcastCyan.copy(alpha = 0.14f) else SurfaceCard,
              shape = MaterialTheme.shapes.small,
              modifier =
                Modifier.fillMaxWidth()
                  .border(
                    1.dp,
                    if (isActive) BroadcastCyan else MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.small,
                  )
                  .clip(MaterialTheme.shapes.small)
                  .clickable { onSelectStream(stream) },
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Surface(
                      color =
                        if (stream.qualityValue >= 1080) LiveEmerald.copy(alpha = 0.2f)
                        else BroadcastCyan.copy(alpha = 0.16f),
                      shape = RoundedCornerShape(4.dp),
                    ) {
                      Text(
                        text = stream.quality,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (stream.qualityValue >= 1080) LiveEmerald else BroadcastCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                    Text(
                      text = stream.format,
                      style = MaterialTheme.typography.titleSmall,
                      color = Color.White,
                    )
                    if (stream.sizeMb > 0.0) {
                      Text(
                        text = "${stream.sizeMb} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  Text(
                    text = listOfNotNull(stream.source, stream.codec).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                if (isActive) {
                  Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected stream",
                    tint = BroadcastCyan,
                  )
                }
              }
            }
          }
        }
      }
    }

    if (availableDubs.size > 1) {
      item {
        Spacer(Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Filled.Language, contentDescription = null, tint = ElectricCrimson)
          Text(
            text = stringResource(R.string.audio_dubs_header),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
      }

      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(availableDubs, key = { it.subjectId }) { dub ->
            val isActiveDub = dub.subjectId == activeSubjectId
            FilterChip(
              selected = isActiveDub,
              onClick = { onSelectDub(dub) },
              label = { Text(dub.langName.ifBlank { dub.language }) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = ElectricCrimson,
                  selectedLabelColor = Color.White,
                ),
            )
          }
        }
      }
    }

    item {
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.ClosedCaption, contentDescription = null, tint = BroadcastCyan)
        Text(
          text = "Subtitles & Captions",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(
            selected = selectedSubtitle == null,
            onClick = { onSelectSubtitle(null) },
            label = { Text(stringResource(R.string.subtitles_off)) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = ElectricCrimson,
                selectedLabelColor = Color.White,
              ),
          )
        }
        items(subtitles, key = { it.url }) { sub ->
          FilterChip(
            selected = selectedSubtitle?.url == sub.url,
            onClick = { onSelectSubtitle(sub) },
            label = { Text(sub.language) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = BroadcastCyan,
                selectedLabelColor = ObsidianBg,
              ),
          )
        }
      }
    }
  }
}

@Composable
private fun DetailsAndEpisodesPane(
  detailState: UiState<MediaDetail>,
  seasons: List<SeasonInfo>,
  selectedSeason: Int,
  selectedEpisode: Int,
  onSelectEpisode: (Int, Int) -> Unit,
  onRetryDetails: () -> Unit,
) {
  var activeSeasonTab by rememberSaveable(selectedSeason) {
    mutableIntStateOf(if (selectedSeason > 0) selectedSeason else 1)
  }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    when (detailState) {
      UiState.Loading -> {
        item { CircularProgressIndicator(color = ElectricCrimson) }
      }
      is UiState.Error -> {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = detailState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
            Button(
              onClick = onRetryDetails,
              colors = ButtonDefaults.buttonColors(containerColor = ElectricCrimson),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Retry Details")
            }
          }
        }
      }
      is UiState.Success -> {
        val d = detailState.data
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (!d.imdbRating.isNullOrBlank() && d.imdbRating != "0") {
                Surface(color = SurfaceCard, shape = RoundedCornerShape(6.dp)) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      Icons.Filled.Star,
                      contentDescription = null,
                      tint = AmberGold,
                      modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                      text = "IMDb ${d.imdbRating}",
                      style = MaterialTheme.typography.labelMedium,
                      color = Color.White,
                    )
                  }
                }
              }
              val meta =
                listOfNotNull(d.releaseDate?.take(4), d.country, d.language).joinToString(" • ")
              if (meta.isNotBlank()) {
                Text(
                  text = meta,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            if (!d.genre.isNullOrBlank()) {
              Text(
                text = d.genre,
                style = MaterialTheme.typography.labelLarge,
                color = BroadcastCyan,
              )
            }

            if (!d.description.isNullOrBlank()) {
              Text(
                text = d.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }

    if (seasons.isNotEmpty()) {
      item {
        Spacer(Modifier.height(4.dp))
        Text(
          text = "Seasons & Episodes",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(seasons, key = { it.season }) { seasonInfo ->
            FilterChip(
              selected = activeSeasonTab == seasonInfo.season,
              onClick = { activeSeasonTab = seasonInfo.season },
              label = { Text("Season ${seasonInfo.season}") },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = ElectricCrimson,
                  selectedLabelColor = Color.White,
                ),
            )
          }
        }
      }

      val currentSeasonObj = seasons.firstOrNull { it.season == activeSeasonTab } ?: seasons.first()
      val episodeNumbers = (1..currentSeasonObj.episodeCount.coerceAtLeast(1)).toList()
      items(episodeNumbers, key = { "${currentSeasonObj.season}_$it" }) { epNum ->
        val isPlayingEp =
          selectedSeason == currentSeasonObj.season && selectedEpisode == epNum
        Surface(
          color = if (isPlayingEp) ElectricCrimson.copy(alpha = 0.16f) else SurfaceCard,
          shape = MaterialTheme.shapes.small,
          modifier =
            Modifier.fillMaxWidth()
              .border(
                1.dp,
                if (isPlayingEp) ElectricCrimson else MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small,
              )
              .clip(MaterialTheme.shapes.small)
              .clickable { onSelectEpisode(currentSeasonObj.season, epNum) },
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Season ${currentSeasonObj.season} • Episode $epNum",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = if (isPlayingEp) FontWeight.Bold else FontWeight.Medium,
              color = Color.White,
            )
            if (isPlayingEp) {
              LivePulseBadge(label = "NOW PLAYING")
            } else {
              Text(
                text = "Stream HD",
                style = MaterialTheme.typography.labelMedium,
                color = BroadcastCyan,
              )
            }
          }
        }
      }
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
