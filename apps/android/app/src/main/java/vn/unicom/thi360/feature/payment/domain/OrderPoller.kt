package vn.unicom.thi360.feature.payment.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PollDirective {
    CONTINUE,
    STOP,
}

class OrderPoller(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var startedAtMillis: Long? = null
    private var activeJob: Job? = null
    private var isInForeground: Boolean = true
    private var pendingScope: CoroutineScope? = null
    private var pendingOnPoll: (suspend () -> PollDirective)? = null
    private var pendingOnTimeout: (() -> Unit)? = null
    private var isPollingEnabled: Boolean = false
    private var callInFlight: Boolean = false

    val isRunning: Boolean
        get() = isPollingEnabled

    fun start(
        scope: CoroutineScope,
        onPoll: suspend () -> PollDirective,
        onTimeout: () -> Unit,
    ) {
        pendingScope = scope
        pendingOnPoll = onPoll
        pendingOnTimeout = onTimeout
        if (!isPollingEnabled) {
            startedAtMillis = clockMillis()
            isPollingEnabled = true
        }
        if (isInForeground) {
            scheduleLoop(immediateFirstPoll = false)
        }
    }

    fun restartWindow(
        scope: CoroutineScope,
        onPoll: suspend () -> PollDirective,
        onTimeout: () -> Unit,
    ) {
        activeJob?.cancel()
        activeJob = null
        pendingScope = scope
        pendingOnPoll = onPoll
        pendingOnTimeout = onTimeout
        startedAtMillis = clockMillis()
        isPollingEnabled = true
        if (isInForeground) {
            scheduleLoop(immediateFirstPoll = true)
        }
    }

    fun onPause() {
        isInForeground = false
        activeJob?.cancel()
        activeJob = null
    }

    fun onResume() {
        if (isInForeground) return
        isInForeground = true
        if (isPollingEnabled) {
            scheduleLoop(immediateFirstPoll = true)
        }
    }

    fun stop() {
        isPollingEnabled = false
        activeJob?.cancel()
        activeJob = null
        startedAtMillis = null
        pendingOnPoll = null
        pendingOnTimeout = null
        pendingScope = null
    }

    private fun scheduleLoop(immediateFirstPoll: Boolean) {
        val scope = pendingScope ?: return
        val onPoll = pendingOnPoll ?: return
        val onTimeout = pendingOnTimeout ?: return
        if (activeJob?.isActive == true) return

        activeJob = scope.launch {
            var doImmediate = immediateFirstPoll
            while (isActive && isPollingEnabled && isInForeground) {
                val started = startedAtMillis ?: clockMillis().also { startedAtMillis = it }
                if (!doImmediate) {
                    val elapsedBeforeWait = (clockMillis() - started).coerceAtLeast(0L)
                    if (elapsedBeforeWait >= windowMillis) {
                        finishTimedOut(onTimeout)
                        return@launch
                    }
                    val waitMs = minOf(intervalMillis, windowMillis - elapsedBeforeWait)
                    delay(waitMs)
                    if (!isActive || !isPollingEnabled || !isInForeground) return@launch
                    val elapsedAfterWait = (clockMillis() - started).coerceAtLeast(0L)
                    if (elapsedAfterWait >= windowMillis) {
                        finishTimedOut(onTimeout)
                        return@launch
                    }
                }
                doImmediate = false

                if (callInFlight) continue
                callInFlight = true
                val directive = try {
                    onPoll()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    callInFlight = false
                }

                if (directive == PollDirective.STOP || !isPollingEnabled) {
                    stop()
                    return@launch
                }

                val elapsedAfterCall = (clockMillis() - started).coerceAtLeast(0L)
                if (elapsedAfterCall >= windowMillis) {
                    finishTimedOut(onTimeout)
                    return@launch
                }
            }
        }
    }

    private fun finishTimedOut(onTimeout: () -> Unit) {
        isPollingEnabled = false
        activeJob = null
        onTimeout()
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 2_000L
        const val DEFAULT_WINDOW_MILLIS = 60_000L
    }
}
