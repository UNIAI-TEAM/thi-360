package vn.unicom.thi360.feature.attemptengine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse

internal class PartPolling(
    private val scope: CoroutineScope,
    private val serverClock: ServerClock,
    private val onReloadRequested: suspend () -> Unit,
    private val onChoiceDeadlineExpired: () -> Unit,
) {
    private var activeKey: String? = null
    private var pollJob: Job? = null

    fun sync(
        attempt: AttemptStateResponse?,
        activePart: AttemptPartStateView?,
        isForeground: Boolean,
        isStopped: Boolean,
    ) {
        if (isStopped || !isForeground || attempt == null || attempt.state != AttemptStateResponse.State.IN_PROGRESS) {
            stop()
            return
        }
        val partId = activePart?.partId
        when (activePart?.state) {
            AttemptPartStateView.State.GENERATING -> {
                val key = "GENERATING:$partId"
                if (activeKey == key && pollJob?.isActive == true) return
                stop()
                activeKey = key
                pollJob = scope.launch {
                    try {
                        while (isActive) {
                            delay(GENERATING_POLL_INTERVAL_MS)
                            onReloadRequested()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    }
                }
            }

            AttemptPartStateView.State.AWAITING_CHOICE -> {
                val deadline = activePart.choiceDeadlineAt
                if (deadline == null || !serverClock.isAnchored) {
                    stop()
                    return
                }
                val key = "AWAITING_CHOICE:$partId:$deadline"
                if (activeKey == key && pollJob?.isActive == true) return
                stop()
                activeKey = key
                pollJob = scope.launch {
                    try {
                        while (isActive) {
                            val remaining = serverClock.remainingMs(deadline) ?: break
                            if (remaining > 0L) {
                                delay(remaining.coerceAtMost(CLOCK_TICK_INTERVAL_MS))
                                continue
                            }
                            onChoiceDeadlineExpired()
                            delay(CHOICE_EXPIRED_INITIAL_POLL_MS)
                            onReloadRequested()
                            while (isActive) {
                                delay(CHOICE_EXPIRED_REPEAT_POLL_MS)
                                onReloadRequested()
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    }
                }
            }

            else -> stop()
        }
    }

    fun stop() {
        activeKey = null
        pollJob?.cancel()
        pollJob = null
    }
}
