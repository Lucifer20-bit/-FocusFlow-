package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.UserProfile
import com.example.data.repository.FocusRepository
import com.example.util.AmbientSoundPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TimerMode(val title: String, val defaultMinutes: Int) {
    WORK("Focus", 25),
    SHORT_BREAK("Short Break", 5),
    LONG_BREAK("Long Break", 15)
}

data class TimerUiState(
    val mode: TimerMode = TimerMode.WORK,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val selectedTag: String = "Deep Work",
    val completedSessionsToday: Int = 0,
    val ambientSound: String = "none",
    val ambientVolume: Float = 0.5f,
    val showCompletionDialog: Boolean = false,
    val lastCompletedMinutes: Int = 25
)

class FocusViewModel(
    private val repository: FocusRepository,
    val userId: String
) : ViewModel() {

    private val ambientPlayer = AmbientSoundPlayer()

    private val _timerState = MutableStateFlow(TimerUiState())
    val timerState: StateFlow<TimerUiState> = _timerState.asStateFlow()

    private var timerJob: Job? = null

    // Habits stream from Firestore
    val habits: StateFlow<List<Habit>> = repository.observeHabits(userId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Focus Sessions stream from Firestore
    val sessions: StateFlow<List<FocusSession>> = repository.observeSessions(userId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Profile settings stream from Firestore
    val profile: StateFlow<UserProfile?> = repository.observeProfile(userId)
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        // Collect profile preferences to configure initial timer durations
        viewModelScope.launch {
            profile.collect { userProfile ->
                if (userProfile != null && !_timerState.value.isRunning) {
                    val workMins = userProfile.workDurationMinutes.coerceIn(1, 180)
                    if (_timerState.value.mode == TimerMode.WORK) {
                        _timerState.update {
                            it.copy(
                                totalSeconds = workMins * 60,
                                remainingSeconds = workMins * 60,
                                ambientSound = userProfile.soundPreset
                            )
                        }
                    }
                }
            }
        }
    }

    fun startTimer() {
        if (_timerState.value.isRunning) return

        _timerState.update { it.copy(isRunning = true) }
        if (_timerState.value.ambientSound != "none") {
            ambientPlayer.play(_timerState.value.ambientSound, _timerState.value.ambientVolume)
        }

        timerJob = viewModelScope.launch {
            while (isActive && _timerState.value.remainingSeconds > 0) {
                delay(1000)
                _timerState.update { current ->
                    val newRemaining = current.remainingSeconds - 1
                    current.copy(remainingSeconds = newRemaining)
                }
            }

            if (_timerState.value.remainingSeconds <= 0) {
                onTimerFinished()
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        ambientPlayer.stop()
        _timerState.update { it.copy(isRunning = false) }
    }

    fun resetTimer() {
        pauseTimer()
        _timerState.update { it.copy(remainingSeconds = it.totalSeconds) }
    }

    fun skipTimer() {
        pauseTimer()
        val nextMode = when (_timerState.value.mode) {
            TimerMode.WORK -> TimerMode.SHORT_BREAK
            TimerMode.SHORT_BREAK -> TimerMode.WORK
            TimerMode.LONG_BREAK -> TimerMode.WORK
        }
        setMode(nextMode)
    }

    fun setMode(mode: TimerMode) {
        pauseTimer()
        val p = profile.value
        val mins = when (mode) {
            TimerMode.WORK -> p?.workDurationMinutes ?: mode.defaultMinutes
            TimerMode.SHORT_BREAK -> p?.shortBreakMinutes ?: mode.defaultMinutes
            TimerMode.LONG_BREAK -> p?.longBreakMinutes ?: mode.defaultMinutes
        }
        val totalSecs = mins * 60
        _timerState.update {
            it.copy(
                mode = mode,
                totalSeconds = totalSecs,
                remainingSeconds = totalSecs
            )
        }
    }

    fun setSelectedTag(tag: String) {
        _timerState.update { it.copy(selectedTag = tag) }
    }

    fun setAmbientSound(sound: String) {
        _timerState.update { it.copy(ambientSound = sound) }
        if (_timerState.value.isRunning) {
            ambientPlayer.play(sound, _timerState.value.ambientVolume)
        }
    }

    fun setAmbientVolume(vol: Float) {
        _timerState.update { it.copy(ambientVolume = vol) }
        ambientPlayer.setVolume(vol)
    }

    fun dismissCompletionDialog() {
        _timerState.update { it.copy(showCompletionDialog = false) }
    }

    private fun onTimerFinished() {
        pauseTimer()
        val completedDuration = _timerState.value.totalSeconds / 60
        val sessionTag = _timerState.value.selectedTag
        val isWork = _timerState.value.mode == TimerMode.WORK

        if (isWork) {
            viewModelScope.launch {
                repository.logSession(
                    userId = userId,
                    durationMinutes = completedDuration,
                    tag = sessionTag,
                    sessionType = "pomodoro",
                    completed = true
                )
            }
        }

        _timerState.update {
            it.copy(
                completedSessionsToday = it.completedSessionsToday + if (isWork) 1 else 0,
                showCompletionDialog = true,
                lastCompletedMinutes = completedDuration
            )
        }
    }

    // Habit operations
    fun addHabit(title: String, category: String, targetDays: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.createHabit(userId, title, category, targetDays)
        }
    }

    fun toggleHabitToday(habit: Habit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val isDone = habit.completedDates.contains(today)
        val updatedList = if (isDone) {
            habit.completedDates.filter { it != today }
        } else {
            habit.completedDates + today
        }
        val newStreak = if (isDone) {
            (habit.currentStreak - 1).coerceAtLeast(0)
        } else {
            habit.currentStreak + 1
        }
        val best = maxOf(newStreak, habit.bestStreak)

        viewModelScope.launch {
            repository.updateHabitProgress(userId, habit.id, newStreak, best, updatedList)
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            repository.deleteHabit(userId, habitId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(userId, sessionId)
        }
    }

    fun saveSettings(
        workMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        soundPreset: String
    ) {
        val current = profile.value ?: UserProfile(userId = userId)
        val updated = current.copy(
            workDurationMinutes = workMinutes,
            shortBreakMinutes = shortBreakMinutes,
            longBreakMinutes = longBreakMinutes,
            soundPreset = soundPreset
        )
        viewModelScope.launch {
            repository.saveProfile(userId, updated)
            if (!_timerState.value.isRunning && _timerState.value.mode == TimerMode.WORK) {
                _timerState.update {
                    it.copy(
                        totalSeconds = workMinutes * 60,
                        remainingSeconds = workMinutes * 60,
                        ambientSound = soundPreset
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ambientPlayer.stop()
    }
}
