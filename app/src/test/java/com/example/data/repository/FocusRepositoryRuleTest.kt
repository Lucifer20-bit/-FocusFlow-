package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createHabit_authenticatedOwner_createsDocument() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = FocusRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createHabit(uid, "Deep Coding", "Work", 5)
    }
    assertTrue(result.isSuccess)
    val habitId = result.getOrThrow()
    assertNotNull(habitId)
  }

  @Test
  fun observeHabits_authenticatedOwner_emitsCreatedHabit() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = FocusRepository(firestore)

    val habitId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createHabit(uid, "Daily Meditation", "Mindfulness", 7).getOrThrow()
    }

    val habits = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeHabits(uid).first { list -> list.any { it.id == habitId } }
    }
    assertTrue(habits.any { it.id == habitId })
  }

  @Test
  fun logSession_authenticatedOwner_createsDocument() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = FocusRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.logSession(uid, 25, "Algorithm Study", "pomodoro", true)
    }
    assertTrue(result.isSuccess)
    val sessionId = result.getOrThrow()
    assertNotNull(sessionId)
  }

  @Test
  fun observeHabits_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = FocusRepository(firestore)

    var caughtException: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeHabits("unauth_user_id").first()
      }
    } catch (e: Exception) {
      caughtException = e
    }
    assertNotNull("Expected exception on unauthenticated query", caughtException)
    val firestoreException = checkNotNull(caughtException!!.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $caughtException"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun createHabit_crossUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val bobUid = signInTestUser(BOB_EMAIL)
    val repository = FocusRepository(firestore)

    // Bob tries to create a habit under Alice's path
    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createHabit(aliceUid, "Intrusion Habit", "Work", 5)
    }
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception is FirebaseFirestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (exception as FirebaseFirestoreException).code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@example.com"
    const val BOB_EMAIL = "bob@example.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
