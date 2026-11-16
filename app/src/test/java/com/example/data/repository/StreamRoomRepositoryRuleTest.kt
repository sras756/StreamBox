package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MediaItem
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreamRoomRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureAndJoinRoom_andSendMessage_emitsRealtimeChatAndPresence() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = StreamRoomRepository(firestore, auth)
    val uniqueSubject = "8906247916_${UUID.randomUUID().toString().take(8)}"

    val joinResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.ensureAndJoinRoom(
          subjectId = uniqueSubject,
          title = "Avatar HD Stream",
          mediaType = "Movie",
          season = 0,
          episode = 0,
          coverUrl = null,
          detailPath = "avatar",
          hostName = "Alice",
          activeQuality = "1080p",
        )
      }
    assertTrue(joinResult.isSuccess)
    val roomId = joinResult.getOrThrow()

    val chatText = "Live 1080p stream looks crystal clear! ${UUID.randomUUID()}"
    val sendResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.sendChatMessage(
          roomId = roomId,
          senderName = "Alice",
          senderAvatarUrl = null,
          text = chatText,
          streamQuality = "1080p",
          messageType = "chat",
        )
      }
    assertTrue(sendResult.isSuccess)
    val msgId = sendResult.getOrThrow()
    assertTrue(msgId.isNotBlank())

    val messages =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeRoomMessages(roomId).first { list -> list.any { it.id == msgId } }
      }
    val savedMsg = messages.first { it.id == msgId }
    assertEquals(chatText, savedMsg.text)
    assertEquals(aliceUid, savedMsg.userId)
    assertEquals("1080p", savedMsg.streamQuality)
    assertNotNull(savedMsg.createdAt)

    val presenceList =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeRoomPresence(roomId).first { list -> list.any { it.userId == aliceUid } }
      }
    assertTrue(presenceList.any { it.userId == aliceUid && it.activeQuality == "1080p" })
  }

  @Test
  fun watchlist_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = StreamRoomRepository(firestore, auth)
    val uniqueSubject = "subj_${UUID.randomUUID().toString().take(8)}"

    val saveResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.saveToWatchlist(
          MediaItem(
            subjectId = uniqueSubject,
            title = "Breaking Bad",
            mediaType = "Series",
          ),
          season = 1,
          episode = 2,
        )
      }
    assertTrue(saveResult.isSuccess)

    val aliceWatchlist =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeWatchlist().first { list -> list.any { it.subjectId == uniqueSubject } }
      }
    assertEquals(1, aliceWatchlist.count { it.subjectId == uniqueSubject })

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore
          .collection("users")
          .document(aliceUid)
          .collection("watchlist")
          .document(uniqueSubject)
          .get()
          .await()
      }
      fail("Expected Bob reading Alice's watchlist to fail with PERMISSION_DENIED")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  @Test
  fun deleteChatMessage_crossUserAttempt_failsWithPermissionDenied() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val aliceRepo = StreamRoomRepository(firestore, auth)
    val uniqueSubject = "subj_${UUID.randomUUID().toString().take(8)}"
    val roomId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .ensureAndJoinRoom(
            subjectId = uniqueSubject,
            title = "Live Stream",
            mediaType = "Movie",
            season = 0,
            episode = 0,
            coverUrl = null,
            detailPath = null,
            hostName = "Alice",
            activeQuality = "1080p",
          )
          .getOrThrow()
      }
    val msgId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .sendChatMessage(
            roomId = roomId,
            senderName = "Alice",
            senderAvatarUrl = null,
            text = "Alice's message",
            streamQuality = "1080p",
          )
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    val bobRepo = StreamRoomRepository(firestore, auth)
    val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.deleteChatMessage(roomId, msgId) }
    assertTrue(deleteResult.isFailure)
    val firestoreErr = deleteResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(firestoreErr)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr!!.code)
  }

  @Test
  fun unauthenticatedQuery_failsBeforeTouchingFirestore() = runBlocking {
    auth.signOut()
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("rooms").whereEqualTo("isLive", true).get().await()
      }
      fail("Expected unauthenticated rooms query to fail with PERMISSION_DENIED")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_stream@test.com"
    const val BOB_EMAIL = "bob_stream@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
