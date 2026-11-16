package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.MediaItem
import com.example.data.model.UserProfile
import com.example.data.model.ViewerPresence
import com.example.data.model.WatchRoom
import com.example.data.model.WatchlistItem
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StreamRoomRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  suspend fun ensureUserProfile(displayName: String, photoUrl: String?): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val safeName = displayName.trim().ifBlank { "Viewer" }.take(60)
      val docRef = db.collection("users").document(uid)
      try {
        val snap = docRef.get().await()
        if (!snap.exists()) {
          val payload =
            mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "photoUrl" to photoUrl?.take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
              .filterValues { it != null }
          docRef.set(payload).await()
        } else {
          val updates =
            mapOf(
                "displayName" to safeName,
                "photoUrl" to photoUrl?.take(500),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
              .filterValues { it != null }
          docRef.update(updates).await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
        throw e
      }
    }

  suspend fun getUserProfile(): UserProfile? {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      docRef
        .get()
        .await()
        .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      throw e
    }
  }

  fun observeLiveWatchRooms(): Flow<List<WatchRoom>> = flow {
    requireUserId()
    val path = "rooms"
    emitAll(
      db.collection(path)
        .whereEqualTo("isLive", true)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(WatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun ensureAndJoinRoom(
    subjectId: String,
    title: String,
    mediaType: String,
    season: Int,
    episode: Int,
    coverUrl: String?,
    detailPath: String?,
    hostName: String,
    activeQuality: String?,
  ): Result<String> =
    runCatching {
      val uid = requireUserId()
      val roomId = buildRoomId(subjectId, season, episode)
      val roomRef = db.collection("rooms").document(roomId)
      val safeTitle = title.trim().ifBlank { "Live Stream" }.take(200)
      val safeType = if (mediaType == "Series") "Series" else "Movie"
      val safeHost = hostName.trim().ifBlank { "Viewer" }.take(60)

      try {
        val snap = roomRef.get().await()
        if (!snap.exists()) {
          val payload =
            mapOf(
                "roomId" to roomId,
                "subjectId" to subjectId.take(64),
                "title" to safeTitle,
                "mediaType" to safeType,
                "season" to season.coerceIn(0, 100),
                "episode" to episode.coerceIn(0, 2000),
                "coverUrl" to coverUrl?.take(800),
                "detailPath" to detailPath?.take(200),
                "isLive" to true,
                "createdBy" to uid,
                "hostName" to safeHost,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
              .filterValues { it != null }
          roomRef.set(payload).await()
        } else {
          roomRef.update(mapOf("updatedAt" to FieldValue.serverTimestamp())).await()
        }

        val presenceRef = roomRef.collection("presence").document(uid)
        val presencePayload =
          mapOf(
              "roomId" to roomId,
              "userId" to uid,
              "displayName" to safeHost,
              "activeQuality" to activeQuality?.take(30),
              "lastSeenAt" to FieldValue.serverTimestamp(),
            )
            .filterValues { it != null }
        presenceRef.set(presencePayload, SetOptions.merge()).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, roomRef.path, auth)
        throw e
      }

      roomId
    }

  suspend fun updatePresence(
    roomId: String,
    displayName: String,
    activeQuality: String?,
  ): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val presenceRef =
        db.collection("rooms").document(roomId).collection("presence").document(uid)
      val payload =
        mapOf(
            "roomId" to roomId,
            "userId" to uid,
            "displayName" to displayName.trim().ifBlank { "Viewer" }.take(60),
            "activeQuality" to activeQuality?.take(30),
            "lastSeenAt" to FieldValue.serverTimestamp(),
          )
          .filterValues { it != null }
      try {
        presenceRef.set(payload, SetOptions.merge()).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, presenceRef.path, auth)
        throw e
      }
    }

  suspend fun leaveRoomPresence(roomId: String) {
    val uid = auth.currentUser?.uid ?: return
    val presenceRef = db.collection("rooms").document(roomId).collection("presence").document(uid)
    try {
      presenceRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, presenceRef.path, auth)
    }
  }

  fun observeRoomMessages(roomId: String): Flow<List<ChatMessage>> = flow {
    requireUserId()
    val path = "rooms/$roomId/messages"
    emitAll(
      db.collection("rooms")
        .document(roomId)
        .collection("messages")
        .whereEqualTo("roomId", roomId)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  fun observeRoomPresence(roomId: String): Flow<List<ViewerPresence>> = flow {
    requireUserId()
    val path = "rooms/$roomId/presence"
    emitAll(
      db.collection("rooms")
        .document(roomId)
        .collection("presence")
        .whereEqualTo("roomId", roomId)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              ViewerPresence::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.lastSeenAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun sendChatMessage(
    roomId: String,
    senderName: String,
    senderAvatarUrl: String?,
    text: String,
    streamQuality: String?,
    messageType: String = "chat",
  ): Result<String> =
    runCatching {
      val uid = requireUserId()
      val trimmed = text.trim()
      require(trimmed.isNotEmpty()) { "Message cannot be empty" }
      val msgRef = db.collection("rooms").document(roomId).collection("messages").document()
      val payload =
        mapOf(
            "roomId" to roomId,
            "userId" to uid,
            "senderName" to senderName.trim().ifBlank { "Viewer" }.take(60),
            "senderAvatarUrl" to senderAvatarUrl?.take(500),
            "text" to trimmed.take(500),
            "streamQuality" to streamQuality?.take(30),
            "messageType" to if (messageType == "reaction") "reaction" else "chat",
            "createdAt" to FieldValue.serverTimestamp(),
          )
          .filterValues { it != null }
      try {
        msgRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, msgRef.path, auth)
        throw e
      }
      msgRef.id
    }

  suspend fun deleteChatMessage(roomId: String, messageId: String): Result<Unit> =
    runCatching {
      requireUserId()
      val msgRef =
        db.collection("rooms").document(roomId).collection("messages").document(messageId)
      try {
        msgRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, msgRef.path, auth)
        throw e
      }
    }

  fun observeWatchlist(): Flow<List<WatchlistItem>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/watchlist"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("watchlist")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(WatchlistItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun saveToWatchlist(
    item: MediaItem,
    season: Int = 0,
    episode: Int = 0,
  ): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val docId = sanitizeId(item.subjectId)
      val docRef = db.collection("users").document(uid).collection("watchlist").document(docId)
      val safeType = if (item.mediaType == "Series") "Series" else "Movie"
      try {
        val snap = docRef.get().await()
        if (!snap.exists()) {
          val payload =
            mapOf(
                "userId" to uid,
                "subjectId" to item.subjectId.take(64),
                "title" to item.title.take(200),
                "mediaType" to safeType,
                "coverUrl" to item.coverUrl?.take(800),
                "imdbRating" to item.imdbRating?.take(16),
                "lastSeason" to season.coerceIn(0, 100),
                "lastEpisode" to episode.coerceIn(0, 2000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
              .filterValues { it != null }
          docRef.set(payload).await()
        } else {
          val updates =
            mapOf(
                "title" to item.title.take(200),
                "mediaType" to safeType,
                "coverUrl" to item.coverUrl?.take(800),
                "imdbRating" to item.imdbRating?.take(16),
                "lastSeason" to season.coerceIn(0, 100),
                "lastEpisode" to episode.coerceIn(0, 2000),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
              .filterValues { it != null }
          docRef.update(updates).await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
        throw e
      }
    }

  suspend fun removeFromWatchlist(subjectId: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val docId = sanitizeId(subjectId)
      val docRef = db.collection("users").document(uid).collection("watchlist").document(docId)
      try {
        docRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
        throw e
      }
    }

  companion object {
    fun sanitizeId(raw: String): String =
      raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "item" }

    fun buildRoomId(subjectId: String, season: Int, episode: Int): String {
      val cleanSubject = sanitizeId(subjectId).take(80)
      return "subject_${cleanSubject}_s${season}_e${episode}"
    }
  }
}
