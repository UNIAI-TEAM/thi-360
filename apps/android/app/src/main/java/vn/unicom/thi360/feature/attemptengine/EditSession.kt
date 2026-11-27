package vn.unicom.thi360.feature.attemptengine

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.ClaimSessionRequest
import vn.unicom.thi360.core.network.generated.model.ClaimSessionResponse

internal interface EditSessionCallbacks {
    fun isOnline(): Boolean
    fun onSessionStateChanged(state: EditSessionState, claimFailureSaveStatus: SaveStatus?)
    fun onServerNow(serverNow: OffsetDateTime)
    suspend fun reloadBeforeTakeoverCompletion(): Boolean
    fun onClaimed(response: ClaimSessionResponse, fromTakeover: Boolean)
    fun onAttemptClosedFromSession(reason: AttemptClosedReason)
    fun onEvicted(fromSave: Boolean)
}

internal class EditSession(
    private val attemptId: UUID,
    private val api: AttemptApi,
    private val draftStore: DraftStore,
    private val scope: CoroutineScope,
    private val callbacks: EditSessionCallbacks,
    private val uuidGenerator: () -> UUID = UUID::randomUUID,
    private val backoff: Backoff = Backoff(),
) {
    private var state: EditSessionState = EditSessionState.Acquiring
    private var currentSessionId: UUID? = null
    private var claimJob: Job? = null
    private var retryJob: Job? = null
    private var takeoverJob: Job? = null
    private var closed = false
    private var lastClaimFailure: SaveStatus? = null
    private var evictionGeneration = 0L

    val currentState: EditSessionState
        get() = state

    val activeSessionId: UUID?
        get() = (state as? EditSessionState.Editing)?.sessionId

    val persistedOrCurrentSessionId: UUID?
        get() = currentSessionId ?: draftStore.readSessionId(attemptId)

    val isTakeoverInFlight: Boolean
        get() = takeoverJob?.isActive == true

    fun startClaim() {
        if (closed) return
        if (state is EditSessionState.Editing || state is EditSessionState.OpenElsewhere || state is EditSessionState.Evicted) {
            return
        }
        val sid = ensureSessionIdPersisted()
        if (claimJob?.isActive == true) return
        retryJob?.cancel()
        retryJob = null
        state = EditSessionState.Acquiring
        callbacks.onSessionStateChanged(state, lastClaimFailure)
        launchClaim(sid)
    }

    fun onOnline() {
        if (closed) return
        backoff.reset()
        if (state is EditSessionState.Acquiring) {
            retryJob?.cancel()
            retryJob = null
            lastClaimFailure = null
            val sid = ensureSessionIdPersisted()
            if (claimJob?.isActive != true) {
                launchClaim(sid)
            }
        }
    }

    fun onOffline() {
        if (closed) return
        if (state is EditSessionState.Acquiring) {
            retryJob?.cancel()
            retryJob = null
            lastClaimFailure = SaveStatus.Offline
            callbacks.onSessionStateChanged(state, lastClaimFailure)
        }
    }

    fun requestTakeover(
        onStarted: () -> Unit,
        onFinished: (succeeded: Boolean, failureCode: String?) -> Unit,
    ) {
        if (closed) return
        if (state !is EditSessionState.OpenElsewhere && state !is EditSessionState.Evicted) return
        if (takeoverJob?.isActive == true) return

        val previousState = state
        val takeoverEvictionGen = evictionGeneration
        val sid = ensureSessionIdPersisted()
        retryJob?.cancel()
        retryJob = null
        claimJob?.cancel()
        claimJob = null

        onStarted()
        takeoverJob = scope.launch {
            try {
                when (
                    val result = api.claimSession(
                        attemptId = attemptId,
                        request = ClaimSessionRequest(sessionId = sid, takeover = true),
                    )
                ) {
                    is ClaimSessionApiResult.Claimed -> {
                        if (closed) return@launch
                        currentSessionId = result.response.sessionId
                        draftStore.writeSessionId(attemptId, result.response.sessionId)
                        backoff.reset()
                        lastClaimFailure = null
                        callbacks.onServerNow(result.response.serverNow)
                        val stillOpen = callbacks.reloadBeforeTakeoverCompletion()
                        if (closed) return@launch
                        if (stillOpen &&
                            state !is EditSessionState.ReadOnly &&
                            evictionGeneration == takeoverEvictionGen
                        ) {
                            state = EditSessionState.Editing(result.response.sessionId)
                            callbacks.onSessionStateChanged(state, null)
                            callbacks.onClaimed(result.response, fromTakeover = true)
                            onFinished(true, null)
                        } else {
                            onFinished(false, null)
                        }
                    }

                    is ClaimSessionApiResult.OpenElsewhere -> {
                        if (closed) return@launch
                        state = EditSessionState.OpenElsewhere(result.lastSeenAt)
                        callbacks.onSessionStateChanged(state, null)
                        onFinished(false, result.code)
                    }

                    is ClaimSessionApiResult.HttpFailure -> {
                        if (closed) return@launch
                        val code = result.failure.error?.code
                        if (result.failure.statusCode == HTTP_CONFLICT &&
                            (code == OpenApiAttemptApi.CODE_TIME_UP || code == OpenApiAttemptApi.CODE_ATTEMPT_NOT_OPEN)
                        ) {
                            val reason = if (code == OpenApiAttemptApi.CODE_TIME_UP) {
                                AttemptClosedReason.TimeUp
                            } else {
                                AttemptClosedReason.NotOpen
                            }
                            markReadOnly(ReadOnlyReason.AttemptClosed)
                            callbacks.onAttemptClosedFromSession(reason)
                            onFinished(false, code)
                        } else {
                            state = previousState
                            callbacks.onSessionStateChanged(state, null)
                            onFinished(false, code)
                        }
                    }

                    is ClaimSessionApiResult.NetworkFailure -> {
                        if (closed) return@launch
                        state = previousState
                        callbacks.onSessionStateChanged(state, null)
                        onFinished(false, null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                takeoverJob = null
            }
        }
    }

    fun markEvicted(fromSave: Boolean = false) {
        if (closed) return
        evictionGeneration += 1L
        val wasEvicted = state is EditSessionState.Evicted
        claimJob?.cancel()
        claimJob = null
        retryJob?.cancel()
        retryJob = null
        state = EditSessionState.Evicted
        lastClaimFailure = null
        callbacks.onSessionStateChanged(state, null)
        if (!wasEvicted) {
            callbacks.onEvicted(fromSave)
        }
    }

    fun markReadOnly(reason: ReadOnlyReason, cancelTakeover: Boolean = true) {
        claimJob?.cancel()
        claimJob = null
        retryJob?.cancel()
        retryJob = null
        if (cancelTakeover) {
            takeoverJob?.cancel()
            takeoverJob = null
        }
        state = EditSessionState.ReadOnly(reason)
        lastClaimFailure = null
        callbacks.onSessionStateChanged(state, null)
    }

    fun stop() {
        closed = true
        claimJob?.cancel()
        claimJob = null
        retryJob?.cancel()
        retryJob = null
        takeoverJob?.cancel()
        takeoverJob = null
    }

    private fun ensureSessionIdPersisted(): UUID {
        val existing = currentSessionId ?: draftStore.readSessionId(attemptId)
        if (existing != null) {
            currentSessionId = existing
            return existing
        }
        val generated = uuidGenerator()
        currentSessionId = generated
        draftStore.writeSessionId(attemptId, generated)
        return generated
    }

    private fun launchClaim(sessionId: UUID) {
        if (!callbacks.isOnline()) {
            lastClaimFailure = SaveStatus.Offline
            callbacks.onSessionStateChanged(EditSessionState.Acquiring, lastClaimFailure)
            return
        }
        claimJob = scope.launch {
            try {
                when (
                    val result = api.claimSession(
                        attemptId = attemptId,
                        request = ClaimSessionRequest(sessionId = sessionId, takeover = null),
                    )
                ) {
                    is ClaimSessionApiResult.Claimed -> {
                        if (closed) return@launch
                        currentSessionId = result.response.sessionId
                        draftStore.writeSessionId(attemptId, result.response.sessionId)
                        backoff.reset()
                        lastClaimFailure = null
                        callbacks.onServerNow(result.response.serverNow)
                        state = EditSessionState.Editing(result.response.sessionId)
                        callbacks.onSessionStateChanged(state, null)
                        callbacks.onClaimed(result.response, fromTakeover = false)
                    }

                    is ClaimSessionApiResult.OpenElsewhere -> {
                        if (closed) return@launch
                        backoff.reset()
                        lastClaimFailure = null
                        state = EditSessionState.OpenElsewhere(result.lastSeenAt)
                        callbacks.onSessionStateChanged(state, null)
                    }

                    is ClaimSessionApiResult.HttpFailure -> {
                        if (closed) return@launch
                        handleClaimHttpFailure(sessionId, result.failure)
                    }

                    is ClaimSessionApiResult.NetworkFailure -> {
                        if (closed) return@launch
                        handleClaimRetryableFailure(
                            sessionId = sessionId,
                            saveStatus = if (!callbacks.isOnline()) {
                                SaveStatus.Offline
                            } else {
                                SaveStatus.Unsaved(UnsavedReason.NetworkError)
                            },
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                claimJob = null
            }
        }
    }

    private fun handleClaimHttpFailure(sessionId: UUID, failure: ApiResult.HttpFailure) {
        val code = failure.error?.code
        when {
            failure.statusCode == HTTP_UNAUTHORIZED -> {
                lastClaimFailure = null
                callbacks.onSessionStateChanged(state, null)
            }

            failure.statusCode == HTTP_CONFLICT &&
                (code == OpenApiAttemptApi.CODE_TIME_UP || code == OpenApiAttemptApi.CODE_ATTEMPT_NOT_OPEN) -> {
                val reason = if (code == OpenApiAttemptApi.CODE_TIME_UP) {
                    AttemptClosedReason.TimeUp
                } else {
                    AttemptClosedReason.NotOpen
                }
                markReadOnly(ReadOnlyReason.AttemptClosed)
                callbacks.onAttemptClosedFromSession(reason)
            }

            failure.statusCode in 500..599 -> {
                handleClaimRetryableFailure(
                    sessionId = sessionId,
                    saveStatus = if (!callbacks.isOnline()) {
                        SaveStatus.Offline
                    } else {
                        SaveStatus.Unsaved(UnsavedReason.ServerError)
                    },
                )
            }

            else -> {
                handleClaimRetryableFailure(
                    sessionId = sessionId,
                    saveStatus = if (!callbacks.isOnline()) {
                        SaveStatus.Offline
                    } else {
                        SaveStatus.Unsaved(UnsavedReason.NetworkError)
                    },
                )
            }
        }
    }

    private fun handleClaimRetryableFailure(sessionId: UUID, saveStatus: SaveStatus) {
        state = EditSessionState.Acquiring
        lastClaimFailure = saveStatus
        callbacks.onSessionStateChanged(state, lastClaimFailure)
        if (!callbacks.isOnline()) {
            return
        }
        val delayMs = backoff.nextDelayMs()
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            retryJob = null
            if (!closed && state is EditSessionState.Acquiring && callbacks.isOnline()) {
                launchClaim(sessionId)
            }
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_CONFLICT = 409
    }
}
