package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.HomeFeedData
import com.example.data.model.MediaItem
import com.example.data.model.SearchSuggestion
import com.example.data.model.WatchRoom
import com.example.data.model.WatchlistItem
import com.example.data.remote.MovieBoxApiClient
import com.example.data.repository.StreamRoomRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

enum class DiscoverFilter {
  FEATURED,
  MOVIES,
  SERIES,
  ANIME,
}

class CatalogViewModel(
  private val apiClient: MovieBoxApiClient,
  private val roomRepository: StreamRoomRepository,
  private val currentUserId: String,
  private val displayName: String,
  private val photoUrl: String?,
) : ViewModel() {

  private val _selectedFilter = MutableStateFlow(DiscoverFilter.FEATURED)
  val selectedFilter: StateFlow<DiscoverFilter> = _selectedFilter.asStateFlow()

  private val _homeState = MutableStateFlow<UiState<HomeFeedData>>(UiState.Loading)
  val homeState: StateFlow<UiState<HomeFeedData>> = _homeState.asStateFlow()

  private val _moviesState = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
  val moviesState: StateFlow<UiState<List<MediaItem>>> = _moviesState.asStateFlow()

  private val _seriesState = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
  val seriesState: StateFlow<UiState<List<MediaItem>>> = _seriesState.asStateFlow()

  private val _animeState = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
  val animeState: StateFlow<UiState<List<MediaItem>>> = _animeState.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
  val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions.asStateFlow()

  private val _searchResultsState =
    MutableStateFlow<UiState<List<MediaItem>>>(UiState.Success(emptyList()))
  val searchResultsState: StateFlow<UiState<List<MediaItem>>> = _searchResultsState.asStateFlow()

  private var suggestJob: Job? = null

  val liveWatchRooms: StateFlow<UiState<List<WatchRoom>>> =
    roomRepository
      .observeLiveWatchRooms()
      .map<List<WatchRoom>, UiState<List<WatchRoom>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing live watch rooms", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load active rooms"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val watchlist: StateFlow<UiState<List<WatchlistItem>>> =
    roomRepository
      .observeWatchlist()
      .map<List<WatchlistItem>, UiState<List<WatchlistItem>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing watchlist", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load watchlist"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  init {
    viewModelScope.launch {
      roomRepository.ensureUserProfile(displayName, photoUrl)
    }
    loadHomeFeed()
    loadMovies()
    loadSeries()
    loadAnime()
  }

  fun selectFilter(filter: DiscoverFilter) {
    _selectedFilter.value = filter
  }

  fun loadHomeFeed() {
    viewModelScope.launch {
      _homeState.value = UiState.Loading
      runCatching { apiClient.getHomeFeed() }
        .onSuccess { feed -> _homeState.value = UiState.Success(feed) }
        .onFailure { err ->
          Log.e(TAG, "Home feed load failed", err)
          _homeState.value = UiState.Error(err.localizedMessage ?: "Failed to load live feed")
        }
    }
  }

  fun loadMovies() {
    viewModelScope.launch {
      _moviesState.value = UiState.Loading
      runCatching { apiClient.getMovies(page = 1, perPage = 30) }
        .onSuccess { items -> _moviesState.value = UiState.Success(items) }
        .onFailure { err ->
          Log.e(TAG, "Movies load failed", err)
          _moviesState.value = UiState.Error(err.localizedMessage ?: "Failed to load movies")
        }
    }
  }

  fun loadSeries() {
    viewModelScope.launch {
      _seriesState.value = UiState.Loading
      runCatching { apiClient.getTvSeries(page = 1, perPage = 30) }
        .onSuccess { items -> _seriesState.value = UiState.Success(items) }
        .onFailure { err ->
          Log.e(TAG, "Series load failed", err)
          _seriesState.value = UiState.Error(err.localizedMessage ?: "Failed to load series")
        }
    }
  }

  fun loadAnime() {
    viewModelScope.launch {
      _animeState.value = UiState.Loading
      runCatching { apiClient.getAnime(page = 1, perPage = 30) }
        .onSuccess { items -> _animeState.value = UiState.Success(items) }
        .onFailure { err ->
          Log.e(TAG, "Anime load failed", err)
          _animeState.value = UiState.Error(err.localizedMessage ?: "Failed to load anime")
        }
    }
  }

  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
    suggestJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.length < 2) {
      _suggestions.value = emptyList()
      if (trimmed.isEmpty()) {
        _searchResultsState.value = UiState.Success(emptyList())
      }
      return
    }
    suggestJob =
      viewModelScope.launch {
        delay(250)
        runCatching { apiClient.searchSuggest(trimmed) }
          .onSuccess { list -> _suggestions.value = list }
      }
  }

  fun submitSearch(queryOverride: String? = null) {
    val q = (queryOverride ?: _searchQuery.value).trim()
    if (q.isEmpty()) return
    _searchQuery.value = q
    _suggestions.value = emptyList()
    viewModelScope.launch {
      _searchResultsState.value = UiState.Loading
      runCatching {
          if (q.matches(Regex("""\d{15,}"""))) {
            val detail = apiClient.getDetails(q)
            listOf(
              MediaItem(
                subjectId = detail.subjectId,
                title = detail.title,
                mediaType = detail.mediaType,
                releaseDate = detail.releaseDate,
                imdbRating = detail.imdbRating,
                genre = detail.genre,
                coverUrl = detail.coverUrl,
                detailPath = detail.detailPath,
                language = detail.language,
              )
            )
          } else {
            apiClient.search(q)
          }
        }
        .onSuccess { items -> _searchResultsState.value = UiState.Success(items) }
        .onFailure { err ->
          Log.e(TAG, "Search failed", err)
          _searchResultsState.value = UiState.Error(err.localizedMessage ?: "Search failed")
        }
    }
  }

  fun removeFromWatchlist(subjectId: String) {
    viewModelScope.launch {
      roomRepository.removeFromWatchlist(subjectId)
    }
  }

  private companion object {
    const val TAG = "CatalogVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
