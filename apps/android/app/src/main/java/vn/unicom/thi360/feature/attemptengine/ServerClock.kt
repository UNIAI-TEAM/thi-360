package vn.unicom.thi360.feature.attemptengine

import android.os.SystemClock
import java.time.OffsetDateTime
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse

interface EngineClock {
    fun elapsedRealtimeMs(): Long
    fun wallClockMs(): Long
}

object SystemEngineClock : EngineClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun wallClockMs(): Long = System.currentTimeMillis()
}

data class ServerClockAnchor(
    val serverNowEpochMs: Long,
    val elapsedRealtimeMs: Long,
)

class ServerClock(
    private val clock: EngineClock = SystemEngineClock,
) {
    @Volatile
    private var anchor: ServerClockAnchor? = null

    val isAnchored: Boolean
        get() = anchor != null

    fun currentAnchor(): ServerClockAnchor? = anchor

    fun reanchor(serverNow: OffsetDateTime) {
        anchor = ServerClockAnchor(
            serverNowEpochMs = serverNow.toInstant().toEpochMilli(),
            elapsedRealtimeMs = clock.elapsedRealtimeMs(),
        )
    }

    fun currentServerNowMs(): Long {
        val currentAnchor = anchor ?: return clock.wallClockMs()
        val elapsedDelta = (clock.elapsedRealtimeMs() - currentAnchor.elapsedRealtimeMs).coerceAtLeast(0L)
        return currentAnchor.serverNowEpochMs + elapsedDelta
    }

    fun remainingMs(deadline: OffsetDateTime?): Long? {
        if (deadline == null) return null
        val deadlineMs = deadline.toInstant().toEpochMilli()
        return (deadlineMs - currentServerNowMs()).coerceAtLeast(0L)
    }

    fun computeCountdown(
        attempt: AttemptStateResponse?,
        activePart: AttemptPartStateView?,
    ): Countdown {
        if (attempt == null || attempt.state != AttemptStateResponse.State.IN_PROGRESS) {
            return Countdown(CountdownTarget.None, 0L)
        }
        val activePartId = activePart?.partId
        if (activePart?.state == AttemptPartStateView.State.AWAITING_CHOICE &&
            activePartId != null &&
            activePart.choiceDeadlineAt != null
        ) {
            val remaining = remainingMs(activePart.choiceDeadlineAt) ?: 0L
            return Countdown(CountdownTarget.Choice(activePartId), remaining)
        }
        return when (attempt.timing) {
            AttemptStateResponse.Timing.SHARED -> {
                val deadline = attempt.deadlineAt
                if (deadline != null) {
                    Countdown(CountdownTarget.Attempt, remainingMs(deadline) ?: 0L)
                } else {
                    Countdown(CountdownTarget.None, 0L)
                }
            }
            AttemptStateResponse.Timing.PER_PART -> {
                val deadline = activePart?.deadlineAt
                if (activePart?.state == AttemptPartStateView.State.OPEN &&
                    activePartId != null &&
                    deadline != null
                ) {
                    Countdown(CountdownTarget.Part(activePartId), remainingMs(deadline) ?: 0L)
                } else {
                    Countdown(CountdownTarget.None, 0L)
                }
            }
            null -> Countdown(CountdownTarget.None, 0L)
        }
    }

    fun examDeadline(
        attempt: AttemptStateResponse?,
        activePart: AttemptPartStateView?,
    ): Pair<CountdownTarget, OffsetDateTime>? {
        if (attempt == null || attempt.state != AttemptStateResponse.State.IN_PROGRESS) {
            return null
        }
        return when (attempt.timing) {
            AttemptStateResponse.Timing.SHARED -> {
                attempt.deadlineAt?.let { CountdownTarget.Attempt to it }
            }
            AttemptStateResponse.Timing.PER_PART -> {
                val partId = activePart?.partId
                if (activePart?.state == AttemptPartStateView.State.OPEN && partId != null) {
                    activePart.deadlineAt?.let { CountdownTarget.Part(partId) to it }
                } else {
                    null
                }
            }
            null -> null
        }
    }
}
