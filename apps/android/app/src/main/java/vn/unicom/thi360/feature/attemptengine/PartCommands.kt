package vn.unicom.thi360.feature.attemptengine

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.IdempotentCall
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ConfirmPartChoiceRequest
import vn.unicom.thi360.core.network.generated.model.FinalAnswerChange
import vn.unicom.thi360.core.network.generated.model.FinalAnswerResult
import vn.unicom.thi360.core.network.generated.model.FinishPartRequest
import vn.unicom.thi360.core.network.generated.model.FinishPartResponse
import vn.unicom.thi360.core.network.generated.model.RetryPartRequest
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptRequest
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptResponse

internal interface PartCommandsCallbacks {
    fun currentAttempt(): AttemptStateResponse?
    fun activeSessionId(): UUID?
    fun isClosed(): Boolean
    fun setCommandState(state: CommandState)
    fun onBeforeClose(target: CloseTarget)
    fun cancelPendingSaveTimers()
    fun buildFinalAnswers(partId: PartId?): List<FinalAnswerChange>
    fun applyFinalAnswerResults(results: List<FinalAnswerResult>)
    fun onServerNow(serverNow: OffsetDateTime)
    fun onChoiceWindowClosedHint()
    fun onClosedHint(reason: AttemptClosedReason)
    fun clearAllLocalDataForSubmittedAttempt()
    suspend fun reloadState(): AttemptStateResponse?
    fun onPartClosedSuccess(partId: PartId)
    fun onAttemptSubmittedSuccess(reason: AttemptClosedReason)
    fun onSessionRevoked()
    fun onFlushRemainingAfterReconcile()
}

internal class PartCommands(
    private val attemptId: UUID,
    private val api: AttemptApi,
    private val scope: CoroutineScope,
    private val callbacks: PartCommandsCallbacks,
    uuidGenerator: () -> UUID = UUID::randomUUID,
) {
    private val choiceIdempotentCall = IdempotentCall { uuidGenerator().toString() }
    private var choiceSignature: String? = null

    private val retryIdempotentCall = IdempotentCall { uuidGenerator().toString() }
    private var retryPartId: PartId? = null

    private val submitIdempotentCall = IdempotentCall { uuidGenerator().toString() }

    private var activeCommandJob: Job? = null
    private var unconfirmedKind: UnconfirmedCommandKind? = null
    private var unconfirmedFinishPartId: PartId? = null
    private var stopped = false

    val isUnconfirmed: Boolean
        get() = unconfirmedKind != null

    val isSending: Boolean
        get() = activeCommandJob?.isActive == true

    fun choosePart(
        partId: PartId,
        optionCode: String,
        subjectCodes: List<String>? = null,
    ) {
        if (stopped || isSending || callbacks.isClosed()) return
        val attempt = callbacks.currentAttempt() ?: return
        if (attempt.state != AttemptStateResponse.State.IN_PROGRESS) return
        val sessionId = callbacks.activeSessionId() ?: return
        val part = attempt.parts.orEmpty().firstOrNull { it.partId == partId }
        if (part != null && part.state != AttemptPartStateView.State.AWAITING_CHOICE) return

        val normalizedSubjects = subjectCodes?.takeIf { it.isNotEmpty() }
        val signature = "$partId:$optionCode:${normalizedSubjects.orEmpty().joinToString(",")}"
        if (choiceSignature != signature) {
            choiceIdempotentCall.reset()
            choiceSignature = signature
        }

        callbacks.setCommandState(CommandState.Sending(AttemptCommandKind.Choice))
        activeCommandJob = scope.launch {
            try {
                val result = choiceIdempotentCall.execute { key ->
                    api.confirmPartChoice(
                        attemptId = attemptId,
                        partId = partId,
                        sessionId = sessionId,
                        idempotencyKey = key,
                        request = ConfirmPartChoiceRequest(
                            optionCode = optionCode,
                            subjectCodes = normalizedSubjects,
                        ),
                    )
                } ?: return@launch
                if (stopped) return@launch
                handleChoiceOrRetryResult(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                activeCommandJob = null
            }
        }
    }

    fun retryGeneration(partId: PartId) {
        if (stopped || isSending || callbacks.isClosed()) return
        val attempt = callbacks.currentAttempt() ?: return
        if (attempt.state != AttemptStateResponse.State.IN_PROGRESS) return
        val sessionId = callbacks.activeSessionId() ?: return

        if (retryPartId != partId) {
            retryIdempotentCall.reset()
            retryPartId = partId
        }

        callbacks.setCommandState(CommandState.Sending(AttemptCommandKind.Retry))
        activeCommandJob = scope.launch {
            try {
                val result = retryIdempotentCall.execute { key ->
                    api.retryPartGeneration(
                        attemptId = attemptId,
                        partId = partId,
                        sessionId = sessionId,
                        idempotencyKey = key,
                        request = RetryPartRequest(),
                    )
                } ?: return@launch
                if (stopped) return@launch
                handleChoiceOrRetryResult(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                activeCommandJob = null
            }
        }
    }

    fun finishPart(partId: PartId) {
        if (stopped || isSending || callbacks.isClosed()) return
        val attempt = callbacks.currentAttempt() ?: return
        if (attempt.state != AttemptStateResponse.State.IN_PROGRESS) return
        val sessionId = callbacks.activeSessionId() ?: return

        callbacks.onBeforeClose(CloseTarget.Part(partId))
        callbacks.cancelPendingSaveTimers()
        val finalAnswers = callbacks.buildFinalAnswers(partId)
        unconfirmedKind = null
        unconfirmedFinishPartId = null
        callbacks.setCommandState(CommandState.Sending(AttemptCommandKind.FinishPart))

        activeCommandJob = scope.launch {
            try {
                when (
                    val result = api.finishPart(
                        attemptId = attemptId,
                        partId = partId,
                        sessionId = sessionId,
                        request = FinishPartRequest(finalAnswers = finalAnswers),
                    )
                ) {
                    is ApiResult.Success -> {
                        if (stopped) return@launch
                        callbacks.onServerNow(result.data.serverNow)
                        callbacks.applyFinalAnswerResults(result.data.results)
                        mapFinishEndReason(result.data.endReason)?.let { callbacks.onClosedHint(it) }
                        callbacks.reloadState()
                        callbacks.setCommandState(CommandState.Idle)
                        callbacks.onPartClosedSuccess(partId)
                    }

                    is ApiResult.HttpFailure -> {
                        if (stopped) return@launch
                        val code = result.error?.code
                        when {
                            result.statusCode == HTTP_UNAUTHORIZED -> {
                                callbacks.setCommandState(CommandState.Idle)
                            }

                            result.statusCode == HTTP_CONFLICT &&
                                (code == OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED ||
                                    code == OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE) -> {
                                callbacks.onSessionRevoked()
                            }

                            result.statusCode in 400..499 -> {
                                if (code == OpenApiAttemptApi.CODE_TIME_UP) {
                                    callbacks.onClosedHint(AttemptClosedReason.TimeUp)
                                }
                                callbacks.reloadState()
                                callbacks.setCommandState(
                                    CommandState.Refused(
                                        code = code ?: "HTTP_${result.statusCode}",
                                        retryAfterSeconds = result.retryAfterSeconds,
                                    ),
                                )
                            }

                            else -> {
                                unconfirmedKind = UnconfirmedCommandKind.FinishPart
                                unconfirmedFinishPartId = partId
                                callbacks.setCommandState(
                                    CommandState.Unconfirmed(UnconfirmedCommandKind.FinishPart),
                                )
                            }
                        }
                    }

                    is ApiResult.NetworkFailure -> {
                        if (stopped) return@launch
                        unconfirmedKind = UnconfirmedCommandKind.FinishPart
                        unconfirmedFinishPartId = partId
                        callbacks.setCommandState(
                            CommandState.Unconfirmed(UnconfirmedCommandKind.FinishPart),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                activeCommandJob = null
            }
        }
    }

    fun submit() {
        if (stopped || isSending || callbacks.isClosed()) return
        val attempt = callbacks.currentAttempt() ?: return
        if (attempt.state != AttemptStateResponse.State.IN_PROGRESS) return
        val sessionId = callbacks.activeSessionId() ?: return

        callbacks.onBeforeClose(CloseTarget.Attempt)
        callbacks.cancelPendingSaveTimers()
        val finalAnswers = callbacks.buildFinalAnswers(partId = null)
        unconfirmedKind = null
        callbacks.setCommandState(CommandState.Sending(AttemptCommandKind.Submit))

        activeCommandJob = scope.launch {
            try {
                val result = submitIdempotentCall.execute { key ->
                    api.submit(
                        attemptId = attemptId,
                        sessionId = sessionId,
                        idempotencyKey = key,
                        request = SubmitAttemptRequest(finalAnswers = finalAnswers),
                    )
                } ?: return@launch
                if (stopped) return@launch

                when (result) {
                    is ApiResult.Success -> {
                        callbacks.onServerNow(result.data.serverNow)
                        callbacks.applyFinalAnswerResults(result.data.results)
                        val reason = mapSubmitEndReason(result.data.endReason) ?: AttemptClosedReason.Submitted
                        callbacks.onClosedHint(reason)
                        callbacks.clearAllLocalDataForSubmittedAttempt()
                        callbacks.reloadState()
                        callbacks.setCommandState(CommandState.Idle)
                        callbacks.onAttemptSubmittedSuccess(reason)
                    }

                    is ApiResult.HttpFailure -> {
                        val code = result.error?.code
                        when {
                            result.statusCode == HTTP_UNAUTHORIZED -> {
                                callbacks.setCommandState(CommandState.Idle)
                            }

                            result.statusCode == HTTP_CONFLICT &&
                                (code == OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED ||
                                    code == OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE) -> {
                                callbacks.onSessionRevoked()
                            }

                            result.statusCode == HTTP_CONFLICT -> {
                                if (code == OpenApiAttemptApi.CODE_TIME_UP) {
                                    callbacks.onClosedHint(AttemptClosedReason.TimeUp)
                                }
                                callbacks.reloadState()
                                callbacks.setCommandState(
                                    CommandState.Refused(
                                        code = code ?: "CONFLICT",
                                        retryAfterSeconds = result.retryAfterSeconds,
                                    ),
                                )
                            }

                            else -> {
                                unconfirmedKind = UnconfirmedCommandKind.Submit
                                callbacks.setCommandState(
                                    CommandState.Unconfirmed(UnconfirmedCommandKind.Submit),
                                )
                            }
                        }
                    }

                    is ApiResult.NetworkFailure -> {
                        unconfirmedKind = UnconfirmedCommandKind.Submit
                        callbacks.setCommandState(
                            CommandState.Unconfirmed(UnconfirmedCommandKind.Submit),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                activeCommandJob = null
            }
        }
    }

    fun reconcile() {
        val pendingKind = unconfirmedKind ?: return
        if (stopped || isSending) return
        val finishPartId = unconfirmedFinishPartId

        activeCommandJob = scope.launch {
            try {
                val fresh = callbacks.reloadState()
                if (stopped) return@launch
                if (fresh == null) {
                    callbacks.setCommandState(CommandState.Unconfirmed(pendingKind))
                    return@launch
                }
                when (pendingKind) {
                    UnconfirmedCommandKind.FinishPart -> {
                        val targetPart = fresh.parts.orEmpty().firstOrNull { it.partId == finishPartId }
                        val partClosed = fresh.state != AttemptStateResponse.State.IN_PROGRESS ||
                            targetPart == null ||
                            targetPart.state != AttemptPartStateView.State.OPEN
                        unconfirmedKind = null
                        unconfirmedFinishPartId = null
                        callbacks.setCommandState(CommandState.Idle)
                        if (partClosed && finishPartId != null) {
                            callbacks.onPartClosedSuccess(finishPartId)
                        } else if (fresh.state == AttemptStateResponse.State.IN_PROGRESS) {
                            callbacks.onFlushRemainingAfterReconcile()
                        }
                    }

                    UnconfirmedCommandKind.Submit -> {
                        unconfirmedKind = null
                        if (fresh.state != AttemptStateResponse.State.IN_PROGRESS) {
                            submitIdempotentCall.reset()
                            callbacks.clearAllLocalDataForSubmittedAttempt()
                            val reason = PartSelection.inferClosedReason(fresh) ?: AttemptClosedReason.Submitted
                            callbacks.setCommandState(CommandState.Idle)
                            callbacks.onAttemptSubmittedSuccess(reason)
                        } else {
                            callbacks.setCommandState(CommandState.Idle)
                            callbacks.onFlushRemainingAfterReconcile()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                activeCommandJob = null
            }
        }
    }

    fun clearOnEviction() {
        activeCommandJob?.cancel()
        activeCommandJob = null
        unconfirmedKind = null
        unconfirmedFinishPartId = null
    }

    fun stop() {
        stopped = true
        activeCommandJob?.cancel()
        activeCommandJob = null
    }

    private suspend fun handleChoiceOrRetryResult(
        result: ApiResult<vn.unicom.thi360.core.network.generated.model.PartChoiceResponse>,
    ) {
        when (result) {
            is ApiResult.Success -> {
                callbacks.onServerNow(result.data.serverNow)
                callbacks.reloadState()
                callbacks.setCommandState(CommandState.Idle)
            }

            is ApiResult.HttpFailure -> {
                val code = result.error?.code
                when {
                    result.statusCode == HTTP_UNAUTHORIZED -> {
                        callbacks.setCommandState(CommandState.Idle)
                    }

                    result.statusCode == HTTP_CONFLICT &&
                        (code == OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED ||
                            code == OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE) -> {
                        callbacks.onSessionRevoked()
                    }

                    result.statusCode == HTTP_TOO_MANY_REQUESTS -> {
                        callbacks.setCommandState(
                            CommandState.Refused(
                                code = OpenApiAttemptApi.CODE_RATE_LIMITED,
                                retryAfterSeconds = result.retryAfterSeconds,
                            ),
                        )
                    }

                    code in RELOAD_ON_CHOICE_ERROR_CODES -> {
                        if (code == OpenApiAttemptApi.CODE_CHOICE_WINDOW_CLOSED) {
                            callbacks.onChoiceWindowClosedHint()
                        }
                        callbacks.reloadState()
                        callbacks.setCommandState(CommandState.Refused(code = code!!))
                    }

                    else -> {
                        callbacks.setCommandState(
                            CommandState.Refused(
                                code = code ?: "generic",
                                retryAfterSeconds = result.retryAfterSeconds,
                            ),
                        )
                    }
                }
            }

            is ApiResult.NetworkFailure -> {
                callbacks.setCommandState(CommandState.Refused(code = "generic"))
            }
        }
    }

    private fun mapFinishEndReason(reason: FinishPartResponse.EndReason?): AttemptClosedReason? =
        when (reason) {
            FinishPartResponse.EndReason.SUBMITTED -> AttemptClosedReason.Submitted
            FinishPartResponse.EndReason.TIME_UP -> AttemptClosedReason.TimeUp
            FinishPartResponse.EndReason.CHOICE_TIMEOUT -> AttemptClosedReason.ChoiceTimeout
            FinishPartResponse.EndReason.FAULT_CLOSED -> AttemptClosedReason.NotOpen
            null -> null
        }

    private fun mapSubmitEndReason(reason: SubmitAttemptResponse.EndReason?): AttemptClosedReason? =
        when (reason) {
            SubmitAttemptResponse.EndReason.SUBMITTED -> AttemptClosedReason.Submitted
            SubmitAttemptResponse.EndReason.TIME_UP -> AttemptClosedReason.TimeUp
            SubmitAttemptResponse.EndReason.CHOICE_TIMEOUT -> AttemptClosedReason.ChoiceTimeout
            SubmitAttemptResponse.EndReason.FAULT_CLOSED -> AttemptClosedReason.NotOpen
            null -> null
        }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_CONFLICT = 409
        private const val HTTP_TOO_MANY_REQUESTS = 429

        private val RELOAD_ON_CHOICE_ERROR_CODES = setOf(
            OpenApiAttemptApi.CODE_CHOICE_WINDOW_CLOSED,
            OpenApiAttemptApi.CODE_CHOICE_LOCKED,
            OpenApiAttemptApi.CODE_PART_NOT_AWAITING,
            OpenApiAttemptApi.CODE_PART_GENERATING,
        )
    }
}
