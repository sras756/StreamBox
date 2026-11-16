package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.MediaDetail
import com.example.data.model.MediaDub
import com.example.data.model.MediaItem
import com.example.data.model.SeasonInfo
import com.example.data.model.StreamSource
import com.example.data.model.SubtitleTrack
import com.example.data.model.ViewerPresence
import com.example.data.remote.MovieBoxApiClient
import com.example.data.remote.MovieBoxSigner
import com.example.data.repository.StreamRoomRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WatchRoomViewModel(
  private val apiClient: MovieBoxApiClient,
  private val roomRepository: StreamRoomRepository,
  val subjectId: String,
  initialTitle: String,
  val mediaType: String,
  initialSeason: Int,
  initialEpisode: Int,
  private val initialCoverUrl: String?,
  private val initialDetailPath: String?,
  private val directStreamUrl: String? = null,
  private val directStreamReferer: String? = null,
  val currentUserId: String,
  private val displayName: String,
  private val photoUrl: String?,
) : ViewModel() {

  private val _activeSubjectId = MutableStateFlow(subjectId)
  val activeSubjectId: StateFlow<String> = _activeSubjectId.asStateFlow()

  private val _availableDubs = MutableStateFlow<List<MediaDub>>(emptyList())
  val availableDubs: StateFlow<List<MediaDub>> = _availableDubs.asStateFlow()

  private val _currentTitle = MutableStateFlow(initialTitle)
  val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

  private val _selectedSeason = MutableStateFlow(initialSeason)
  val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

  private val _selectedEpisode = MutableStateFlow(initialEpisode)
  val selectedEpisode: StateFlow<Int> = _selectedEpisode.asStateFlow()

  private val _detailState = MutableStateFlow<UiState<MediaDetail>>(UiState.Loading)
  val detailState: StateFlow<UiState<MediaDetail>> = _detailState.asStateFlow()

  private val _seasons = MutableStateFlow<List<SeasonInfo>>(emptyList())
  val seasons: StateFlow<List<SeasonInfo>> = _seasons.asStateFlow()

  private val _streamsState = MutableStateFlow<UiState<List<StreamSource>>>(UiState.Loading)
  val streamsState: StateFlow<UiState<List<StreamSource>>> = _streamsState.asStateFlow()

  private val _selectedStream = MutableStateFlow<StreamSource?>(null)
  val selectedStream: StateFlow<StreamSource?> = _selectedStream.asStateFlow()

  private val _subtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
  val subtitles: StateFlow<List<SubtitleTrack>> = _subtitles.asStateFlow()

  private val _selectedSubtitle = MutableStateFlow<SubtitleTrack?>(null)
  val selectedSubtitle: StateFlow<SubtitleTrack?> = _selectedSubtitle.asStateFlow()

  private val _activeRoomId = MutableStateFlow<String?>(null)
  val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

  private val _isSendingChat = MutableStateFlow(false)
  val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

  private val _chatError = MutableStateFlow<String?>(null)
  val chatError: StateFlow<String?> = _chatError.asStateFlow()

  val isBookmarked: StateFlow<Boolean> =
    roomRepository
      .observeWatchlist()
      .map { list -> list.any { it.subjectId == subjectId } }
      .catch { emit(false) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false,
      )

  val chatMessages: StateFlow<UiState<List<ChatMessage>>> =
    _activeRoomId
      .filterNotNull()
      .flatMapLatest { roomId ->
        roomRepository
          .observeRoomMessages(roomId)
          .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
          .catch { error ->
            val firestoreError =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chat messages", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load chat"))
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val activeViewers: StateFlow<List<ViewerPresence>> =
    _activeRoomId
      .filterNotNull()
      .flatMapLatest { roomId ->
        roomRepository.observeRoomPresence(roomId).catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          emit(emptyList())
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  private var resolvedDetailPath: String? = initialDetailPath
  private var resolvedCoverUrl: String? = initialCoverUrl

  private val failedStreamUrls = mutableSetOf<String>()

  init {
    if (!directStreamUrl.isNullOrBlank()) {
      initDirectWebsiteStream(directStreamUrl, directStreamReferer)
    } else {
      loadDetailsAndEpisodes()
      loadStreamsAndJoinRoom(initialSeason, initialEpisode)
    }
  }

  private fun initDirectWebsiteStream(streamUrl: String, referer: String?) {
    val cleanReferer = referer?.takeIf { it.isNotBlank() } ?: "https://moviebox.ph/"
    val format =
      when {
        streamUrl.contains(".m3u8", ignoreCase = true) -> "HLS"
        streamUrl.contains(".mpd", ignoreCase = true) -> "DASH"
        else -> "MP4"
      }
    val hostLabel =
      runCatching { java.net.URI(cleanReferer).host ?: cleanReferer }.getOrDefault(cleanReferer)
    val directSource =
      StreamSource(
        source = "Website Stream ($hostLabel)",
        format = format,
        quality = if (format == "MP4") "HD" else "Adaptive",
        qualityValue = 1080,
        resolutionTag = format,
        url = streamUrl,
        headers =
          mapOf(
            "User-Agent" to MovieBoxSigner.WEB_USER_AGENT,
            "Referer" to cleanReferer,
          ),
      )
    _detailState.value =
      UiState.Success(
        MediaDetail(
          subjectId = subjectId,
          title = _currentTitle.value,
          mediaType = mediaType,
          description = "Streaming live from $cleanReferer",
        )
      )
    _streamsState.value = UiState.Success(listOf(directSource))
    _selectedStream.value = directSource
    joinWatchRoom(_selectedSeason.value, _selectedEpisode.value, directSource.quality)
  }

  fun loadDetailsAndEpisodes() {
    if (!directStreamUrl.isNullOrBlank()) {
      initDirectWebsiteStream(directStreamUrl, directStreamReferer)
      return
    }
    val targetSubjectId = _activeSubjectId.value
    viewModelScope.launch {
      _detailState.value = UiState.Loading
      runCatching { apiClient.getDetails(targetSubjectId, detailPathHint = resolvedDetailPath) }
        .onSuccess { detail ->
          _detailState.value = UiState.Success(detail)
          if (detail.title.isNotBlank()) _currentTitle.value = detail.title
          if (!detail.detailPath.isNullOrBlank()) resolvedDetailPath = detail.detailPath
          if (!detail.coverUrl.isNullOrBlank()) resolvedCoverUrl = detail.coverUrl
          if (detail.dubs.isNotEmpty()) {
            val currentList = _availableDubs.value
            _availableDubs.value =
              (currentList + detail.dubs).distinctBy { it.subjectId }
          }
          if (detail.mediaType == "Series" || mediaType == "Series") {
            runCatching { apiClient.getEpisodes(targetSubjectId, detailPathHint = resolvedDetailPath) }
              .onSuccess { sList ->
                _seasons.value = sList
                if (_selectedSeason.value == 0 && sList.isNotEmpty()) {
                  val firstSeason = sList.first().season
                  _selectedSeason.value = firstSeason
                  _selectedEpisode.value = 1
                  loadStreamsAndJoinRoom(firstSeason, 1)
                }
              }
          }
        }
        .onFailure { err ->
          Log.e(TAG, "Failed to load details", err)
          _detailState.value = UiState.Error(err.localizedMessage ?: "Failed to load details")
        }
    }
  }

  fun switchDub(dub: MediaDub) {
    if (!directStreamUrl.isNullOrBlank()) return
    if (_activeSubjectId.value == dub.subjectId) return
    failedStreamUrls.clear()
    _activeSubjectId.value = dub.subjectId
    if (!dub.detailPath.isNullOrBlank()) {
      resolvedDetailPath = dub.detailPath
    }
    loadDetailsAndEpisodes()
    loadStreamsAndJoinRoom(_selectedSeason.value, _selectedEpisode.value)
  }

  fun selectEpisode(season: Int, episode: Int) {
    if (_selectedSeason.value == season && _selectedEpisode.value == episode) return
    val previousRoom = _activeRoomId.value
    if (previousRoom != null) {
      cleanupScope.launch { roomRepository.leaveRoomPresence(previousRoom) }
    }
    failedStreamUrls.clear()
    _selectedSeason.value = season
    _selectedEpisode.value = episode
    loadStreamsAndJoinRoom(season, episode)
  }

  fun reloadStreams() {
    if (!directStreamUrl.isNullOrBlank()) {
      initDirectWebsiteStream(directStreamUrl, directStreamReferer)
      return
    }
    failedStreamUrls.clear()
    if (_detailState.value is UiState.Error) {
      loadDetailsAndEpisodes()
    }
    loadStreamsAndJoinRoom(_selectedSeason.value, _selectedEpisode.value)
  }

  fun tryFallbackStream(): Boolean {
    val current = _selectedStream.value ?: return false
    failedStreamUrls.add(current.url)
    val available = (_streamsState.value as? UiState.Success)?.data.orEmpty()
    val next = available.firstOrNull { it.url !in failedStreamUrls } ?: return false
    _selectedStream.value = next
    val roomId = _activeRoomId.value
    if (roomId != null) {
      viewModelScope.launch {
        roomRepository.updatePresence(roomId, displayName, next.quality)
      }
    }
    return true
  }

  private fun loadStreamsAndJoinRoom(season: Int, episode: Int) {
    if (!directStreamUrl.isNullOrBlank()) {
      initDirectWebsiteStream(directStreamUrl, directStreamReferer)
      return
    }
    val targetSubjectId = _activeSubjectId.value
    viewModelScope.launch {
      _streamsState.value = UiState.Loading
      _selectedStream.value = null
      _selectedSubtitle.value = null

      val streamsResult = runCatching {
        apiClient.getStreams(
          subjectId = targetSubjectId,
          season = season,
          episode = episode,
          detailPath = resolvedDetailPath,
        )
      }

      streamsResult
        .onSuccess { sources ->
          if (sources.isEmpty()) {
            _streamsState.value = UiState.Error("No playable streams resolved for this selection")
            joinWatchRoom(season, episode, null)
          } else {
            _streamsState.value = UiState.Success(sources)
            val best = sources.first()
            _selectedStream.value = best
            joinWatchRoom(season, episode, best.quality)
          }
        }
        .onFailure { err ->
          Log.e(TAG, "Failed to resolve streams", err)
          _streamsState.value =
            UiState.Error(err.localizedMessage ?: "Failed to resolve HD streams")
          joinWatchRoom(season, episode, null)
        }

      runCatching {
          apiClient.getSubtitles(subjectId = targetSubjectId, season = season, episode = episode)
        }
        .onSuccess { subs -> _subtitles.value = subs }
    }
  }

  private fun joinWatchRoom(season: Int, episode: Int, quality: String?) {
    viewModelScope.launch {
      roomRepository
        .ensureAndJoinRoom(
          subjectId = subjectId,
          title = _currentTitle.value,
          mediaType = mediaType,
          season = season,
          episode = episode,
          coverUrl = resolvedCoverUrl,
          detailPath = resolvedDetailPath,
          hostName = displayName,
          activeQuality = quality,
        )
        .onSuccess { roomId -> _activeRoomId.value = roomId }
        .onFailure { err -> Log.e(TAG, "Failed to join watch room", err) }
    }
  }

  fun selectStreamSource(source: StreamSource) {
    failedStreamUrls.remove(source.url)
    _selectedStream.value = source
    val roomId = _activeRoomId.value ?: return
    viewModelScope.launch {
      roomRepository.updatePresence(roomId, displayName, source.quality)
    }
  }

  fun selectSubtitle(track: SubtitleTrack?) {
    _selectedSubtitle.value = track
  }

  fun sendChatMessage(text: String) {
    val roomId = _activeRoomId.value ?: return
    val trimmed = text.trim()
    if (trimmed.isEmpty() || _isSendingChat.value) return

    viewModelScope.launch {
      _isSendingChat.value = true
      _chatError.value = null
      roomRepository
        .sendChatMessage(
          roomId = roomId,
          senderName = displayName,
          senderAvatarUrl = photoUrl,
          text = trimmed,
          streamQuality = _selectedStream.value?.quality,
          messageType = "chat",
        )
        .onFailure { err ->
          _chatError.value = err.localizedMessage ?: "Failed to send message"
        }
      _isSendingChat.value = false
    }
  }

  fun sendReaction(emoji: String) {
    val roomId = _activeRoomId.value ?: return
    viewModelScope.launch {
      roomRepository.sendChatMessage(
        roomId = roomId,
        senderName = displayName,
        senderAvatarUrl = photoUrl,
        text = emoji,
        streamQuality = _selectedStream.value?.quality,
        messageType = "reaction",
      )
    }
  }

  fun deleteMessage(messageId: String) {
    val roomId = _activeRoomId.value ?: return
    viewModelScope.launch {
      roomRepository.deleteChatMessage(roomId, messageId)
    }
  }

  fun toggleBookmark() {
    val currentlyBookmarked = isBookmarked.value
    val detail = (_detailState.value as? UiState.Success)?.data
    val item =
      MediaItem(
        subjectId = subjectId,
        title = _currentTitle.value,
        mediaType = mediaType,
        releaseDate = detail?.releaseDate,
        imdbRating = detail?.imdbRating,
        genre = detail?.genre,
        coverUrl = resolvedCoverUrl,
        detailPath = resolvedDetailPath,
      )
    viewModelScope.launch {
      if (currentlyBookmarked) {
        roomRepository.removeFromWatchlist(subjectId)
      } else {
        roomRepository.saveToWatchlist(
          item = item,
          season = _selectedSeason.value,
          episode = _selectedEpisode.value,
        )
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    val roomId = _activeRoomId.value
    if (roomId != null) {
      cleanupScope.launch { roomRepository.leaveRoomPresence(roomId) }
    }
  }

  private companion object {
    const val TAG = "WatchRoomVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
    val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }
}
