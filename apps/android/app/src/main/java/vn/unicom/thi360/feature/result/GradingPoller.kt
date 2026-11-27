package vn.unicom.thi360.feature.result

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse

@Immutable
sealed interface PollPhase {
    data object Idle : PollPhase
    data class Polling(val deadlineMillis: Long) : PollPhase
    data class Paused(val deadlineMillis: Long) : PollPhase
    data object TimedOut : PollPhase
}

class GradingPoller(
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    private val _phase = MutableStateFlow<PollPhase>(PollPhase.Idle)
    val phase: StateFlow<PollPhase> = _phase.asStateFlow()

    private val _isReloading = MutableStateFlow(false)
    val isReloading: StateFlow<Boolean> = _isReloading.asStateFlow()

    private var pollJob: Job? = null
    private var reloadJob: Job? = null
    private var isForeground: Boolean = true
    private var latestState: AttemptResultResponse.State? = null

    fun onResultState(
        state: AttemptResultResponse.State?,
        scope: CoroutineScope,
        pollAction: suspend () -> AttemptResultResponse.State?,
    ) {
        latestState = state
        if (state != AttemptResultResponse.State.GRADING) {
            cancelPollJob()
            _phase.value = PollPhase.Idle
            return
        }

        when (_phase.value) {
            PollPhase.Idle -> {
                val deadline = clockMillis() + windowMillis
                if (isForeground) {
                    _phase.value = PollPhase.Polling(deadline)
                    startLoop(deadline, scope, pollAction)
                } else {
                    _phase.value = PollPhase.Paused(deadline)
                }
            }
            is PollPhase.Polling -> {
                if (pollJob?.isActive != true && isForeground) {
                    val deadline = (_phase.value as PollPhase.Polling).deadlineMillis
                    startLoop(deadline, scope, pollAction)
                }
            }
            is PollPhase.Paused,
            PollPhase.TimedOut,
            -> Unit
        }
    }

    fun onBackground() {
        isForeground = false
        cancelPollJob()
        val current = _phase.value
        if (current is PollPhase.Polling) {
            _phase.value = PollPhase.Paused(current.deadlineMillis)
        }
    }

    fun onForeground(
        scope: CoroutineScope,
        pollAction: suspend () -> AttemptResultResponse.State?,
    ) {
        isForeground = true
        val current = _phase.value
        if (current !is PollPhase.Paused) return

        val now = clockMillis()
        if (now >= current.deadlineMillis) {
            _phase.value = PollPhase.TimedOut
            return
        }

        _phase.value = PollPhase.Polling(current.deadlineMillis)
        cancelPollJob()
        pollJob = scope.launch {
            try {
                val updated = pollAction()
                if (updated != null) {
                    latestState = updated
                    if (updated != AttemptResultResponse.State.GRADING) {
                        _phase.value = PollPhase.Idle
                        return@launch
                    }
                }
                if (_phase.value is PollPhase.Polling && latestState == AttemptResultResponse.State.GRADING) {
                    runPollLoop(current.deadlineMillis, pollAction)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun reloadFromTimeout(
        scope: CoroutineScope,
        pollAction: suspend () -> AttemptResultResponse.State?,
    ): Boolean {
        if (_isReloading.value) return false
        _isReloading.value = true
        cancelPollJob()
        reloadJob = scope.launch {
            try {
                val updatedState = pollAction()
                if (updatedState != null) {
                    latestState = updatedState
                }
                if (updatedState == AttemptResultResponse.State.GRADING) {
                    val freshDeadline = clockMillis() + windowMillis
                    if (isForeground) {
                        _phase.value = PollPhase.Polling(freshDeadline)
                        startLoop(freshDeadline, scope) {
                            pollAction()
                        }
                    } else {
                        _phase.value = PollPhase.Paused(freshDeadline)
                    }
                } else if (updatedState != null) {
                    _phase.value = PollPhase.Idle
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                _isReloading.value = false
            }
        }
        return true
    }

    fun stop() {
        cancelPollJob()
        reloadJob?.cancel()
        reloadJob = null
        _isReloading.value = false
        latestState = null
        _phase.value = PollPhase.Idle
    }

    private fun startLoop(
        deadlineMillis: Long,
        scope: CoroutineScope,
        pollAction: suspend () -> AttemptResultResponse.State?,
    ) {
        cancelPollJob()
        pollJob = scope.launch {
            runPollLoop(deadlineMillis, pollAction)
        }
    }

    private suspend fun runPollLoop(
        deadlineMillis: Long,
        pollAction: suspend () -> AttemptResultResponse.State?,
    ) {
        while (true) {
            val remaining = deadlineMillis - clockMillis()
            if (remaining <= 0L) {
                if (_phase.value is PollPhase.Polling) {
                    _phase.value = PollPhase.TimedOut
                }
                return
            }
            val sleepMs = minOf(intervalMillis, remaining)
            delay(sleepMs)
            if (_phase.value !is PollPhase.Polling) {
                return
            }
            if (clockMillis() >= deadlineMillis) {
                _phase.value = PollPhase.TimedOut
                return
            }
            val updated = pollAction()
            if (updated != null) {
                latestState = updated
                if (updated != AttemptResultResponse.State.GRADING) {
                    _phase.value = PollPhase.Idle
                    return
                }
            }
            if (_phase.value !is PollPhase.Polling || latestState != AttemptResultResponse.State.GRADING) {
                return
            }
            if (clockMillis() >= deadlineMillis) {
                _phase.value = PollPhase.TimedOut
                return
            }
        }
    }

    private fun cancelPollJob() {
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS: Long = 3_000L
        const val DEFAULT_WINDOW_MILLIS: Long = 120_000L
    }
}
