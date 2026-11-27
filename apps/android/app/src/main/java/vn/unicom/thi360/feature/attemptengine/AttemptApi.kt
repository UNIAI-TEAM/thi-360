package vn.unicom.thi360.feature.attemptengine

import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptSessionConflictResponse
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ClaimSessionRequest
import vn.unicom.thi360.core.network.generated.model.ClaimSessionResponse
import vn.unicom.thi360.core.network.generated.model.ConfirmPartChoiceRequest
import vn.unicom.thi360.core.network.generated.model.FinishPartRequest
import vn.unicom.thi360.core.network.generated.model.FinishPartResponse
import vn.unicom.thi360.core.network.generated.model.HeartbeatResponse
import vn.unicom.thi360.core.network.generated.model.PartChoiceResponse
import vn.unicom.thi360.core.network.generated.model.RetryPartRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersResponse
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptRequest
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptResponse

sealed interface ClaimSessionApiResult {
    data class Claimed(val response: ClaimSessionResponse) : ClaimSessionApiResult
    data class OpenElsewhere(
        val lastSeenAt: OffsetDateTime?,
        val code: String,
    ) : ClaimSessionApiResult
    data class HttpFailure(val failure: ApiResult.HttpFailure) : ClaimSessionApiResult
    data class NetworkFailure(val cause: Throwable) : ClaimSessionApiResult
}

interface AttemptApi {
    suspend fun getState(attemptId: UUID): ApiResult<AttemptStateResponse>

    suspend fun claimSession(
        attemptId: UUID,
        request: ClaimSessionRequest,
    ): ClaimSessionApiResult

    suspend fun heartbeat(
        attemptId: UUID,
        sessionId: UUID,
    ): ApiResult<HeartbeatResponse>

    suspend fun saveAnswers(
        attemptId: UUID,
        sessionId: UUID,
        request: SaveAnswersRequest,
    ): ApiResult<SaveAnswersResponse>

    suspend fun finishPart(
        attemptId: UUID,
        partId: UUID,
        sessionId: UUID,
        request: FinishPartRequest,
    ): ApiResult<FinishPartResponse>

    suspend fun submit(
        attemptId: UUID,
        sessionId: UUID,
        idempotencyKey: String,
        request: SubmitAttemptRequest,
    ): ApiResult<SubmitAttemptResponse>

    suspend fun confirmPartChoice(
        attemptId: UUID,
        partId: UUID,
        sessionId: UUID,
        idempotencyKey: String,
        request: ConfirmPartChoiceRequest,
    ): ApiResult<PartChoiceResponse>

    suspend fun retryPartGeneration(
        attemptId: UUID,
        partId: UUID,
        sessionId: UUID,
        idempotencyKey: String,
        request: RetryPartRequest = RetryPartRequest(),
    ): ApiResult<PartChoiceResponse>
}

class OpenApiAttemptApi(
    private val service: AttemptsApi,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) : AttemptApi {

    override suspend fun getState(attemptId: UUID): ApiResult<AttemptStateResponse> =
        executeCall { service.getState(attemptId) }

    override suspend fun claimSession(
        attemptId: UUID,
        request: ClaimSessionRequest,
    ): ClaimSessionApiResult = try {
        val response = service.claimSession(attemptId = attemptId, claimSessionRequest = request)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ClaimSessionApiResult.Claimed(body)
            } else {
                ClaimSessionApiResult.HttpFailure(
                    ApiResult.HttpFailure(statusCode = response.code(), error = null),
                )
            }
        } else {
            val rawError = response.errorBody()?.string()
            if (response.code() == HTTP_CONFLICT) {
                val conflict = parseSessionConflict(rawError)
                if (conflict?.code == CODE_ATTEMPT_OPEN_ELSEWHERE) {
                    return ClaimSessionApiResult.OpenElsewhere(
                        lastSeenAt = conflict.lastSeenAt,
                        code = conflict.code,
                    )
                }
            }
            val parsedError = ApiErrorParser.parse(rawError)
            ClaimSessionApiResult.HttpFailure(
                ApiResult.HttpFailure(
                    statusCode = response.code(),
                    error = parsedError,
                    retryAfterSeconds = parseRetryAfter(response, parsedError?.retryAfter),
                ),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        ClaimSessionApiResult.NetworkFailure(io)
    } catch (other: Throwable) {
        ClaimSessionApiResult.NetworkFailure(other)
    }

    override suspend fun heartbeat(
        attemptId: UUID,
        sessionId: UUID,
    ): ApiResult<HeartbeatResponse> = executeCall {
        service.heartbeat(attemptId = attemptId, xAttemptSession = sessionId)
    }

    override suspend fun saveAnswers(
        attemptId: UUID,
        sessionId: UUID,
        request: SaveAnswersRequest,
    ): ApiResult<SaveAnswersResponse> = executeCall {
        service.saveAnswers(
            attemptId = attemptId,
            xAttemptSession = sessionId,
            saveAnswersRequest = request,
        )
    }

    override suspend fun finishPart(
        attemptId: UUID,
        partId: UUID,
        sessionId: UUID,
        request: FinishPartRequest,
    ): ApiResult<FinishPartResponse> = executeCall {
        service.finishPart(
            attemptId = attemptId,
            partId = partId,
            xAttemptSession = sessionId,
            finishPartRequest = request,
        )
    }

    override suspend fun submit(
        attemptId: UUID,
        sessionId: UUID,
        idempotencyKey: String,
        request: SubmitAttemptRequest,
    ): ApiResult<SubmitAttemptResponse> = executeCall {
        service.submit(
            attemptId = attemptId,
            idempotencyKey = idempotencyKey,
            xAttemptSession = sessionId,
            submitAttemptRequest = request,
        )
    }

    override suspend fun confirmPartChoice(
        attemptId: UUID,
        partId: UUID,
        sessionId: UUID,
        idempotencyKey: String,
        request: ConfirmPartChoiceRequest,
    ): ApiResult<PartChoiceResponse> = executeCall {
        service.confirmPartChoice(
            attemptId = attemptId,
            partId = partId,
            idempotencyKey = idempotencyKey,
            xAttemptSession = sessionId,
            confirmPartChoiceRequest = request,
        )
    }

    override suspend fun retryPartGeneration(
        attemptId: UUID,
        partId: UUID,
        sessionId: UUID,
        idempotencyKey: String,
        request: RetryPartRequest,
    ): ApiResult<PartChoiceResponse> = executeCall {
        service.retryPartGeneration(
            attemptId = attemptId,
            partId = partId,
            idempotencyKey = idempotencyKey,
            xAttemptSession = sessionId,
            retryPartRequest = request,
        )
    }

    private suspend fun <T> executeCall(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.HttpFailure(statusCode = response.code(), error = null)
            }
        } else {
            val rawError = response.errorBody()?.string()
            val parsedError = ApiErrorParser.parse(rawError)
            ApiResult.HttpFailure(
                statusCode = response.code(),
                error = parsedError,
                retryAfterSeconds = parseRetryAfter(response, parsedError?.retryAfter),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        ApiResult.NetworkFailure(io)
    } catch (other: Throwable) {
        ApiResult.NetworkFailure(other)
    }

    private fun parseRetryAfter(response: Response<*>, bodyRetryAfter: Long?): Long? {
        val header = response.headers()["Retry-After"]
        val parsedHeader = parseAttemptRetryAfterSeconds(header, wallClockMs())
        if (parsedHeader != null) return parsedHeader
        return bodyRetryAfter?.takeIf { it >= 0L }?.coerceAtLeast(1L)
    }

    private fun parseSessionConflict(rawBody: String?): AttemptSessionConflictResponse? {
        if (rawBody.isNullOrBlank()) return null
        return runCatching {
            Serializer.kotlinxSerializationJson.decodeFromString(
                AttemptSessionConflictResponse.serializer(),
                rawBody,
            )
        }.getOrNull()
    }

    companion object {
        const val CODE_ATTEMPT_OPEN_ELSEWHERE = "ATTEMPT_OPEN_ELSEWHERE"
        const val CODE_ATTEMPT_SESSION_REVOKED = "ATTEMPT_SESSION_REVOKED"
        const val CODE_TIME_UP = "TIME_UP"
        const val CODE_ATTEMPT_NOT_OPEN = "ATTEMPT_NOT_OPEN"
        const val CODE_PART_CLOSED = "PART_CLOSED"
        const val CODE_CHOICE_WINDOW_CLOSED = "CHOICE_WINDOW_CLOSED"
        const val CODE_CHOICE_LOCKED = "CHOICE_LOCKED"
        const val CODE_PART_NOT_AWAITING = "PART_NOT_AWAITING"
        const val CODE_PART_GENERATING = "PART_GENERATING"
        const val CODE_RATE_LIMITED = "RATE_LIMITED"
        private const val HTTP_CONFLICT = 409

        // Core RetryAfter.parse drops 0; AC-10 requires Retry-After: 0 to clamp to 1s instead of falling back to backoff.
        fun parseAttemptRetryAfterSeconds(headerValue: String?, nowMs: Long = System.currentTimeMillis()): Long? {
            val trimmed = headerValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val numeric = trimmed.toDoubleOrNull()
            if (numeric != null && numeric.isFinite() && numeric >= 0.0) {
                return ceil(numeric).toLong().coerceAtLeast(1L)
            }
            val parsedDateMs = runCatching {
                ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull() ?: return null
            val deltaSeconds = ceil((parsedDateMs - nowMs) / 1_000.0).toLong()
            return deltaSeconds.coerceAtLeast(1L)
        }
    }
}
