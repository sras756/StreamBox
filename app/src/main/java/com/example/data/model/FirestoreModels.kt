package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val photoUrl: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WatchlistItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val subjectId: String = "",
  val title: String = "",
  val mediaType: String = "Movie",
  val coverUrl: String? = null,
  val imdbRating: String? = null,
  val lastSeason: Long = 0L,
  val lastEpisode: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WatchRoom(
  @DocumentId val id: String = "",
  val roomId: String = "",
  val subjectId: String = "",
  val title: String = "",
  val mediaType: String = "Movie",
  val season: Long = 0L,
  val episode: Long = 0L,
  val coverUrl: String? = null,
  val detailPath: String? = null,
  val isLive: Boolean = true,
  val createdBy: String = "",
  val hostName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val roomId: String = "",
  val userId: String = "",
  val senderName: String = "",
  val senderAvatarUrl: String? = null,
  val text: String = "",
  val streamQuality: String? = null,
  val messageType: String = "chat",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ViewerPresence(
  @DocumentId val id: String = "",
  val roomId: String = "",
  val userId: String = "",
  val displayName: String = "",
  val activeQuality: String? = null,
  @ServerTimestamp val lastSeenAt: Timestamp? = null,
)
