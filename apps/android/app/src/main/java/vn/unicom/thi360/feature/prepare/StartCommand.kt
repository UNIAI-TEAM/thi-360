package vn.unicom.thi360.feature.prepare

import java.util.UUID
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.IdempotentCall
import vn.unicom.thi360.core.network.generated.model.PreChoiceInput
import vn.unicom.thi360.core.network.generated.model.StartAttemptRequest
import vn.unicom.thi360.core.network.generated.model.StartAttemptResponse

class StartCommand(
    private val api: PrepareApi,
    private val idempotentCall: IdempotentCall = IdempotentCall(),
) {
    val currentKey: String?
        get() = idempotentCall.currentKey

    val isInFlight: Boolean
        get() = idempotentCall.isInFlight

    fun resetKey() {
        idempotentCall.reset()
    }

    suspend fun start(
        entitlementId: UUID,
        preChoice: PreChoiceInput? = null,
    ): StartOutcome? {
        val request = StartAttemptRequest(
            entitlementId = entitlementId,
            preChoice = preChoice,
        )
        val result = idempotentCall.execute { key ->
            api.start(idempotencyKey = key, request = request)
        } ?: return null

        return when (result) {
            is ApiResult.Success -> mapOpened(result.data)
            is ApiResult.HttpFailure,
            is ApiResult.NetworkFailure,
            -> StartOutcome.Refused(StartErrorMessage.fromApiFailure(result))
        }
    }

    private fun mapOpened(response: StartAttemptResponse): StartOutcome.Opened {
        val openedState = when (response.state) {
            StartAttemptResponse.State.STARTING -> StartOutcome.OpenedState.Starting
            StartAttemptResponse.State.IN_PROGRESS -> StartOutcome.OpenedState.InProgress
            StartAttemptResponse.State.ABORTED ->
                StartOutcome.OpenedState.Aborted(response.abortReason?.value)
            StartAttemptResponse.State.GRADING,
            StartAttemptResponse.State.PENDING_REVIEW,
            StartAttemptResponse.State.SCORED,
            StartAttemptResponse.State.GRADING_ERROR,
            -> StartOutcome.OpenedState.Closed
        }
        return StartOutcome.Opened(attemptId = response.attemptId, state = openedState)
    }
}
