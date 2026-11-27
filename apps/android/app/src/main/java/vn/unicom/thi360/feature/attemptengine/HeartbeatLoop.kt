package vn.unicom.thi360.feature.attemptengine

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.HeartbeatResponse

internal sealed interface HeartbeatOutcome {
    data object Active : HeartbeatOutcome
    data class AttemptNotInProgress(val state: HeartbeatResponse.State) : HeartbeatOutcome
    data object Evicted : HeartbeatOutcome
    data class ClosedConflict(val reason: AttemptClosedReason) : HeartbeatOutcome
    data object Unauthorized : HeartbeatOutcome
    data object IgnoredTransientError : HeartbeatOutcome
}

internal interface HeartbeatCallbacks {
    fun activeSessionId(): UUID?
    fun isForeground(): Boolean
    fun isOnline(): Boolean
    fun onServerNow(serverNow: OffsetDateTime)
    fun onAttemptClosedFromHeartbeat(reason: AttemptClosedReason?)
    fun onSessionRevokedFromHeartbeat()
}

internal class HeartbeatLoop(
    private val attemptId: UUID,
    private val api: AttemptApi,
    private val scope: CoroutineScope,
    private val callbacks: HeartbeatCallbacks,
    private val intervalMs: Long = HEARTBEAT_INTERVAL_MS,
) {
    private var loopJob: Job? = null

    fun start() {
        if (loopJob?.isActive == true) return
        if (!callbacks.isForeground() || callbacks.activeSessionId() == null) return
        loopJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (!callbacks.isForeground()) break
                val sessionId = callbacks.activeSessionId() ?: break
                val outcome = sendOnce(sessionId)
                if (outcome is HeartbeatOutcome.Evicted ||
                    outcome is HeartbeatOutcome.AttemptNotInProgress ||
                    outcome is HeartbeatOutcome.ClosedConflict ||
                    outcome is HeartbeatOutcome.Unauthorized
                ) {
                    break
                }
            }
        }
    }

    fun restart() {
        stop()
        start()
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    suspend fun sendImmediate(sessionId: UUID): HeartbeatOutcome = sendOnce(sessionId)

    private suspend fun sendOnce(sessionId: UUID): HeartbeatOutcome {
        return try {
            when (val result = api.heartbeat(attemptId = attemptId, sessionId = sessionId)) {
                is ApiResult.Success -> {
                    if (callbacks.activeSessionId() != sessionId) {
                        return HeartbeatOutcome.IgnoredTransientError
                    }
                    callbacks.onServerNow(result.data.serverNow)
                    if (result.data.state != HeartbeatResponse.State.IN_PROGRESS) {
                        stop()
                        callbacks.onAttemptClosedFromHeartbeat(null)
                        HeartbeatOutcome.AttemptNotInProgress(result.data.state)
                    } else {
                        HeartbeatOutcome.Active
                    }
                }

                is ApiResult.HttpFailure -> {
                    val code = result.error?.code
                    when {
                        result.statusCode == HTTP_UNAUTHORIZED -> {
                            stop()
                            HeartbeatOutcome.Unauthorized
                        }

                        result.statusCode == HTTP_CONFLICT &&
                            (code == OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED ||
                                code == OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE) -> {
                            stop()
                            callbacks.onSessionRevokedFromHeartbeat()
                            HeartbeatOutcome.Evicted
                        }

                        result.statusCode == HTTP_CONFLICT &&
                            (code == OpenApiAttemptApi.CODE_TIME_UP ||
                                code == OpenApiAttemptApi.CODE_ATTEMPT_NOT_OPEN) -> {
                            stop()
                            val reason = if (code == OpenApiAttemptApi.CODE_TIME_UP) {
                                AttemptClosedReason.TimeUp
                            } else {
                                AttemptClosedReason.NotOpen
                            }
                            callbacks.onAttemptClosedFromHeartbeat(reason)
                            HeartbeatOutcome.ClosedConflict(reason)
                        }

                        else -> HeartbeatOutcome.IgnoredTransientError
                    }
                }

                is ApiResult.NetworkFailure -> HeartbeatOutcome.IgnoredTransientError
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_CONFLICT = 409
    }
}
