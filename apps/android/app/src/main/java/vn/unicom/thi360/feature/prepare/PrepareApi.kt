package vn.unicom.thi360.feature.prepare

import java.util.UUID
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.StartAttemptRequest
import vn.unicom.thi360.core.network.generated.model.StartAttemptResponse
import vn.unicom.thi360.core.network.safeApiCall

interface PrepareApi {
    suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse>

    suspend fun start(
        idempotencyKey: String,
        request: StartAttemptRequest,
    ): ApiResult<StartAttemptResponse>

    suspend fun getAttemptState(attemptId: UUID): ApiResult<AttemptStateResponse>
}

class OpenApiPrepareApi(
    private val attemptsApi: AttemptsApi,
) : PrepareApi {
    override suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse> =
        safeApiCall { attemptsApi.prepare(entitlementId) }

    override suspend fun start(
        idempotencyKey: String,
        request: StartAttemptRequest,
    ): ApiResult<StartAttemptResponse> =
        safeApiCall { attemptsApi.start(idempotencyKey = idempotencyKey, startAttemptRequest = request) }

    override suspend fun getAttemptState(attemptId: UUID): ApiResult<AttemptStateResponse> =
        safeApiCall { attemptsApi.getState(attemptId) }
}
