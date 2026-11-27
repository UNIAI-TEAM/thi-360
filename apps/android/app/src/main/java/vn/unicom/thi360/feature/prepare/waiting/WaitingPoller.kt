package vn.unicom.thi360.feature.prepare.waiting

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.feature.prepare.PrepareApi
import vn.unicom.thi360.feature.prepare.WaitingPhase

const val WAITING_FAST_POLL_MS: Long = 1_000L
const val WAITING_SLOW_POLL_MS: Long = 5_000L
const val WAITING_BUSY_THRESHOLD_MS: Long = 90_000L

class WaitingPoller(
    val attemptId: UUID,
    private val clockMillis: () -> Long,
    private val api: PrepareApi,
    private val initialElapsedMs: Long = 0L,
    private val onReady: (UUID) -> Unit = {},
    private val onAborted: (String?) -> Unit = {},
    private val onPhaseChanged: (WaitingPhase) -> Unit = {},
) {
    private val _phase = MutableStateFlow<WaitingPhase>(
        if (initialElapsedMs >= WAITING_BUSY_THRESHOLD_MS) {
            WaitingPhase.Busy
        } else {
            WaitingPhase.Polling(initialElapsedMs.coerceAtLeast(0L))
        },
    )
    val phase: StateFlow<WaitingPhase> = _phase.asStateFlow()

    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null
    private var busyJob: Job? = null
    private var startedAtMs: Long = 0L
    private var started: Boolean = false
    private var foreground: Boolean = true
    private var terminated: Boolean = false

    fun currentElapsedMs(): Long {
        if (!started) return initialElapsedMs.coerceAtLeast(0L)
        return (initialElapsedMs + (clockMillis() - startedAtMs)).coerceAtLeast(0L)
    }

    fun start(
        coroutineScope: CoroutineScope,
        initiallyForeground: Boolean = true,
    ) {
        if (started || terminated) return
        started = true
        scope = coroutineScope
        startedAtMs = clockMillis()
        foreground = initiallyForeground
        emitPhase(_phase.value)
        if (foreground) {
            launchLoops(immediateFirstPoll = false)
        }
    }

    fun setForeground(isForeground: Boolean) {
        if (terminated || !started) {
            foreground = isForeground
            return
        }
        if (foreground == isForeground) return
        foreground = isForeground
        if (isForeground) {
            launchLoops(immediateFirstPoll = true)
        } else {
            cancelLoops()
        }
    }

    fun stop() {
        terminated = true
        cancelLoops()
    }

    private fun cancelLoops() {
        pollJob?.cancel()
        pollJob = null
        busyJob?.cancel()
        busyJob = null
    }

    private fun launchLoops(immediateFirstPoll: Boolean) {
        val activeScope = scope ?: return
        cancelLoops()

        val elapsedNow = currentElapsedMs()
        if (elapsedNow >= WAITING_BUSY_THRESHOLD_MS) {
            emitPhase(WaitingPhase.Busy)
        } else {
            val remaining = (WAITING_BUSY_THRESHOLD_MS - elapsedNow).coerceAtLeast(0L)
            busyJob = activeScope.launch {
                delay(remaining)
                if (!terminated && foreground) {
                    emitPhase(WaitingPhase.Busy)
                }
            }
        }

        pollJob = activeScope.launch {
            var nextDelayMs = if (immediateFirstPoll) {
                0L
            } else if (currentElapsedMs() >= WAITING_BUSY_THRESHOLD_MS) {
                WAITING_SLOW_POLL_MS
            } else {
                WAITING_FAST_POLL_MS
            }

            while (isActive && !terminated && foreground) {
                if (nextDelayMs > 0L) {
                    delay(nextDelayMs)
                }
                if (!isActive || terminated || !foreground) break

                val result = try {
                    api.getAttemptState(attemptId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }

                if (!isActive || terminated || !foreground) break

                if (result is ApiResult.Success) {
                    val state = result.data.state
                    when {
                        state == AttemptStateResponse.State.ABORTED -> {
                            terminated = true
                            busyJob?.cancel()
                            busyJob = null
                            val reason = result.data.abortReason?.value
                            emitPhase(WaitingPhase.Failed(reason))
                            onAborted(reason)
                            return@launch
                        }

                        state != AttemptStateResponse.State.STARTING -> {
                            terminated = true
                            busyJob?.cancel()
                            busyJob = null
                            onReady(attemptId)
                            return@launch
                        }
                    }
                }

                val elapsed = currentElapsedMs()
                if (elapsed >= WAITING_BUSY_THRESHOLD_MS) {
                    emitPhase(WaitingPhase.Busy)
                    nextDelayMs = WAITING_SLOW_POLL_MS
                } else {
                    emitPhase(WaitingPhase.Polling(elapsed))
                    nextDelayMs = WAITING_FAST_POLL_MS
                }
            }
        }
    }

    private fun emitPhase(next: WaitingPhase) {
        _phase.value = next
        onPhaseChanged(next)
    }
}
