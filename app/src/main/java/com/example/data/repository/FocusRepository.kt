package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FocusRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun saveProfile(userId: String, profile: UserProfile): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val data = hashMapOf(
                    "userId" to userId,
                    "displayName" to profile.displayName,
                    "workDurationMinutes" to profile.workDurationMinutes,
                    "shortBreakMinutes" to profile.shortBreakMinutes,
                    "longBreakMinutes" to profile.longBreakMinutes,
                    "longBreakInterval" to profile.longBreakInterval,
                    "soundPreset" to profile.soundPreset,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).await()
            } else {
                val updates = hashMapOf<String, Any>(
                    "displayName" to profile.displayName,
                    "workDurationMinutes" to profile.workDurationMinutes,
                    "shortBreakMinutes" to profile.shortBreakMinutes,
                    "longBreakMinutes" to profile.longBreakMinutes,
                    "longBreakInterval" to profile.longBreakInterval,
                    "soundPreset" to profile.soundPreset,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeHabits(userId: String): Flow<List<Habit>> {
        val colRef = db.collection("users").document(userId).collection("habits")
        return colRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(Habit::class.java)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    suspend fun createHabit(userId: String, title: String, category: String, targetDays: Int = 5): Result<String> {
        val colRef = db.collection("users").document(userId).collection("habits")
        val docRef = colRef.document()
        return try {
            val payload = hashMapOf(
                "userId" to userId,
                "title" to title.trim(),
                "category" to category.trim(),
                "targetDaysPerWeek" to targetDays,
                "currentStreak" to 0,
                "bestStreak" to 0,
                "completedDates" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateHabitProgress(
        userId: String,
        habitId: String,
        currentStreak: Int,
        bestStreak: Int,
        completedDates: List<String>
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("habits").document(habitId)
        return try {
            val updates = hashMapOf<String, Any>(
                "currentStreak" to currentStreak,
                "bestStreak" to bestStreak,
                "completedDates" to completedDates,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteHabit(userId: String, habitId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("habits").document(habitId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSessions(userId: String): Flow<List<FocusSession>> {
        val colRef = db.collection("users").document(userId).collection("sessions")
        return colRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(FocusSession::class.java)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    suspend fun logSession(
        userId: String,
        durationMinutes: Int,
        tag: String,
        sessionType: String,
        completed: Boolean = true
    ): Result<String> {
        val colRef = db.collection("users").document(userId).collection("sessions")
        val docRef = colRef.document()
        return try {
            val payload = hashMapOf(
                "userId" to userId,
                "durationMinutes" to durationMinutes,
                "tag" to tag,
                "sessionType" to sessionType,
                "completed" to completed,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("sessions").document(sessionId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
