package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DiscoverRoute

@Serializable
data object LiveRoomsRoute

@Serializable
data object SearchRoute

@Serializable
data object WebSitesRoute

@Serializable
data class WatchRoomRoute(
  val subjectId: String,
  val title: String,
  val mediaType: String,
  val season: Int = 0,
  val episode: Int = 0,
  val coverUrl: String? = null,
  val detailPath: String? = null,
  val directStreamUrl: String? = null,
  val directStreamReferer: String? = null,
)
