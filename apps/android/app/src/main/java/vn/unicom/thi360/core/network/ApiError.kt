package vn.unicom.thi360.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApiFieldError(
    val field: String,
    val code: String,
    val message: String,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String = "",
    val fieldErrors: List<ApiFieldError> = emptyList(),
    val traceId: String = "",
    val retryAfter: Long? = null,
    val remainingAttempts: Int? = null,
)

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class HttpFailure(val statusCode: Int, val error: ApiError?) : ApiResult<Nothing>
    data class NetworkFailure(val cause: Throwable) : ApiResult<Nothing>
}

object ApiErrorParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun parse(rawBody: String?): ApiError? {
        if (rawBody.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(ApiError.serializer(), rawBody) }.getOrNull()
    }
}
