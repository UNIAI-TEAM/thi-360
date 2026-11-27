package vn.unicom.thi360.feature.prepare.waiting

import java.util.UUID
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.PreChoiceInput
import vn.unicom.thi360.feature.prepare.PreChoiceSelection
import vn.unicom.thi360.feature.prepare.StartCommand
import vn.unicom.thi360.feature.prepare.StartContext
import vn.unicom.thi360.feature.prepare.StartContextStore
import vn.unicom.thi360.feature.prepare.StartOutcome
import vn.unicom.thi360.feature.prepare.StartRefusal
import vn.unicom.thi360.feature.prepare.WaitingOrigin

sealed interface RetryPreChoiceResolution {
    data class Direct(val preChoice: PreChoiceInput?) : RetryPreChoiceResolution
    data object MustReturnToPrepare : RetryPreChoiceResolution
}

sealed interface WaitingRetryOutcome {
    data class Restarted(
        val newAttemptId: UUID,
        val state: StartOutcome.OpenedState,
        val preChoice: PreChoiceInput?,
        val context: StartContext?,
    ) : WaitingRetryOutcome

    data class Refused(val refusal: StartRefusal) : WaitingRetryOutcome
    data object ReloadPrepare : WaitingRetryOutcome
    data class OpenPrepare(val entitlementId: String) : WaitingRetryOutcome
    data object GoToMyExams : WaitingRetryOutcome
    data object AlreadyInFlight : WaitingRetryOutcome
}

class WaitingRetry(
    private val startCommand: StartCommand,
    private val startContextStore: StartContextStore,
) {
    suspend fun retry(
        attemptId: UUID,
        origin: WaitingOrigin,
        entitlementId: UUID? = null,
        prepareResponse: AttemptPrepareResponse? = null,
        inMemoryPreChoice: PreChoiceInput? = null,
        inMemoryContext: StartContext? = null,
        fallbackContextBuilder: ((PreChoiceInput?) -> StartContext?)? = null,
    ): WaitingRetryOutcome = when (origin) {
        WaitingOrigin.Prepare -> retryFromPrepare(
            attemptId = attemptId,
            entitlementId = entitlementId,
            prepareResponse = prepareResponse,
            inMemoryPreChoice = inMemoryPreChoice,
            inMemoryContext = inMemoryContext,
            fallbackContextBuilder = fallbackContextBuilder,
        )

        WaitingOrigin.Player -> retryFromPlayer(
            attemptId = attemptId,
            inMemoryContext = inMemoryContext,
        )
    }

    private suspend fun retryFromPrepare(
        attemptId: UUID,
        entitlementId: UUID?,
        prepareResponse: AttemptPrepareResponse?,
        inMemoryPreChoice: PreChoiceInput?,
        inMemoryContext: StartContext?,
        fallbackContextBuilder: ((PreChoiceInput?) -> StartContext?)?,
    ): WaitingRetryOutcome {
        val savedContext = startContextStore.read(attemptId)
        val resolvedEntitlementId = entitlementId
            ?: prepareResponse?.entitlementId
            ?: savedContext?.entitlementId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return WaitingRetryOutcome.ReloadPrepare

        val resolution = resolvePreparePreChoice(
            inMemoryPreChoice = inMemoryPreChoice,
            savedPreChoice = savedContext?.preChoice ?: inMemoryContext?.preChoice,
            prepareResponse = prepareResponse,
        )
        val preChoicePayload = when (resolution) {
            is RetryPreChoiceResolution.Direct -> resolution.preChoice
            RetryPreChoiceResolution.MustReturnToPrepare -> return WaitingRetryOutcome.ReloadPrepare
        }

        val outcome = startCommand.start(
            entitlementId = resolvedEntitlementId,
            preChoice = preChoicePayload,
        ) ?: return WaitingRetryOutcome.AlreadyInFlight

        return when (outcome) {
            is StartOutcome.Opened -> {
                val baseContext = inMemoryContext ?: savedContext ?: fallbackContextBuilder?.invoke(preChoicePayload)
                val updatedContext = baseContext?.copy(preChoice = preChoicePayload)
                if (updatedContext != null) {
                    startContextStore.moveTo(
                        oldAttemptId = attemptId,
                        newAttemptId = outcome.attemptId,
                        updatedContext = updatedContext,
                    )
                }
                WaitingRetryOutcome.Restarted(
                    newAttemptId = outcome.attemptId,
                    state = outcome.state,
                    preChoice = preChoicePayload,
                    context = updatedContext,
                )
            }

            is StartOutcome.Refused -> WaitingRetryOutcome.Refused(outcome.refusal)
        }
    }

    private suspend fun retryFromPlayer(
        attemptId: UUID,
        inMemoryContext: StartContext?,
    ): WaitingRetryOutcome {
        val context = inMemoryContext ?: startContextStore.read(attemptId)
            ?: return WaitingRetryOutcome.GoToMyExams
        val entitlementUuid = runCatching { UUID.fromString(context.entitlementId) }.getOrNull()
            ?: return WaitingRetryOutcome.GoToMyExams

        val outcome = startCommand.start(
            entitlementId = entitlementUuid,
            preChoice = context.preChoice,
        ) ?: return WaitingRetryOutcome.AlreadyInFlight

        return when (outcome) {
            is StartOutcome.Opened -> {
                startContextStore.moveTo(
                    oldAttemptId = attemptId,
                    newAttemptId = outcome.attemptId,
                    updatedContext = context,
                )
                WaitingRetryOutcome.Restarted(
                    newAttemptId = outcome.attemptId,
                    state = outcome.state,
                    preChoice = context.preChoice,
                    context = context,
                )
            }

            is StartOutcome.Refused -> when (outcome.refusal) {
                StartRefusal.ChoiceNotAllowed,
                StartRefusal.SubjectsInvalid,
                -> WaitingRetryOutcome.OpenPrepare(context.entitlementId)

                else -> WaitingRetryOutcome.Refused(outcome.refusal)
            }
        }
    }

    companion object {
        fun resolvePreparePreChoice(
            inMemoryPreChoice: PreChoiceInput?,
            savedPreChoice: PreChoiceInput?,
            prepareResponse: AttemptPrepareResponse?,
        ): RetryPreChoiceResolution {
            if (inMemoryPreChoice != null) {
                return RetryPreChoiceResolution.Direct(inMemoryPreChoice)
            }
            if (savedPreChoice != null) {
                return RetryPreChoiceResolution.Direct(savedPreChoice)
            }
            val response = prepareResponse ?: return RetryPreChoiceResolution.MustReturnToPrepare
            val preChoice = response.preChoice
                ?: return RetryPreChoiceResolution.Direct(null)
            val options = preChoice.options.orEmpty()
            if (options.size == 1) {
                val single = options.first()
                val code = single.code?.takeIf { it.isNotBlank() }
                if (code != null && PreChoiceSelection.requiredSubjectCount(single) <= 0) {
                    return RetryPreChoiceResolution.Direct(
                        PreChoiceInput(optionCode = code, subjectCodes = null),
                    )
                }
            }
            return RetryPreChoiceResolution.MustReturnToPrepare
        }
    }
}
