package com.example.data.model

data class MediaItem(
  val subjectId: String,
  val title: String,
  val mediaType: String,
  val releaseDate: String? = null,
  val imdbRating: String? = null,
  val genre: String? = null,
  val coverUrl: String? = null,
  val detailPath: String? = null,
  val language: String? = null,
)

data class MediaDub(
  val subjectId: String,
  val langName: String,
  val language: String,
)

data class MediaDetail(
  val subjectId: String,
  val title: String,
  val mediaType: String,
  val description: String? = null,
  val releaseDate: String? = null,
  val durationSeconds: Long? = null,
  val imdbRating: String? = null,
  val genre: String? = null,
  val country: String? = null,
  val language: String? = null,
  val coverUrl: String? = null,
  val detailPath: String? = null,
  val dubs: List<MediaDub> = emptyList(),
)

data class SeasonInfo(
  val season: Int,
  val episodeCount: Int,
  val resolutions: List<Int> = emptyList(),
)

data class StreamSource(
  val source: String,
  val format: String,
  val quality: String,
  val qualityValue: Int,
  val resolutionTag: String,
  val url: String,
  val sizeMb: Double = 0.0,
  val codec: String? = null,
  val durationSec: Long? = null,
  val cookie: String? = null,
  val headers: Map<String, String> = emptyMap(),
)

data class SubtitleTrack(
  val id: String,
  val language: String,
  val code: String,
  val url: String,
  val sizeBytes: Long? = null,
)

data class HomeSection(
  val title: String,
  val items: List<MediaItem>,
)

data class HomeFeedData(
  val banners: List<MediaItem>,
  val sections: List<HomeSection>,
)

data class SearchSuggestion(
  val title: String,
  val slug: String?,
  val subjectId: String,
)
