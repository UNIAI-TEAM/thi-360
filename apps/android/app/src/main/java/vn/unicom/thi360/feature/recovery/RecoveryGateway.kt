package vn.unicom.thi360.feature.recovery

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.api.AuthApi
import vn.unicom.thi360.core.network.generated.model.ChangePasswordRequest
import vn.unicom.thi360.core.network.generated.model.MobileTokenResponse
import vn.unicom.thi360.core.network.generated.model.PasswordForgotRequest
import vn.unicom.thi360.core.network.generated.model.PasswordResetRequest
import vn.unicom.thi360.core.network.generated.model.RegisterRequest
import vn.unicom.thi360.core.network.generated.model.RegisterResendRequest
import vn.unicom.thi360.core.network.generated.model.RegisterVerifyRequest
import vn.unicom.thi360.core.network.safeEmptyApiCall
import vn.unicom.thi360.core.storage.Tokens

data class RegistrationStartData(
    val registrationId: UUID,
    val email: String,
    val resendAfterSeconds: Long,
)

interface RecoveryGateway {
    suspend fun startRegistration(
        fullName: String,
        username: String,
        email: String,
        password: String,
        termsVersion: String,
        registrationId: UUID? = null,
    ): ApiResult<RegistrationStartData>

    suspend fun resendRegistrationCode(registrationId: UUID): ApiResult<RegistrationStartData>

    suspend fun verifyRegistration(
        registrationId: UUID,
        code: String,
        username: String?,
    ): ApiResult<Tokens>

    suspend fun forgotPassword(email: String): ApiResult<Unit>

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit>

    suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit>
}

class OpenApiRecoveryGateway(
    private val publicAuthApi: AuthApi,
    private val authenticatedAuthApi: AuthApi,
) : RecoveryGateway {

    override suspend fun startRegistration(
        fullName: String,
        username: String,
        email: String,
        password: String,
        termsVersion: String,
        registrationId: UUID?,
    ): ApiResult<RegistrationStartData> = executeWithRetryAfter {
        publicAuthApi.startRegistration(
            RegisterRequest(
                acceptTerms = true,
                email = email,
                fullName = fullName,
                password = password,
                termsVersion = termsVersion,
                username = username,
                registrationId = registrationId,
            ),
        )
    }.mapSuccess { body ->
        RegistrationStartData(
            registrationId = body.registrationId,
            email = body.email,
            resendAfterSeconds = body.resendAfterSeconds,
        )
    }

    override suspend fun resendRegistrationCode(registrationId: UUID): ApiResult<RegistrationStartData> =
        executeWithRetryAfter {
            publicAuthApi.resendRegistrationCode(RegisterResendRequest(registrationId = registrationId))
        }.mapSuccess { body ->
            RegistrationStartData(
                registrationId = body.registrationId,
                email = body.email,
                resendAfterSeconds = body.resendAfterSeconds,
            )
        }

    override suspend fun verifyRegistration(
        registrationId: UUID,
        code: String,
        username: String?,
    ): ApiResult<Tokens> = executeWithRetryAfter {
        publicAuthApi.mobileVerifyRegistration(
            RegisterVerifyRequest(
                code = code,
                registrationId = registrationId,
            ),
        )
    }.mapSuccess { body ->
        body.toDomainTokens(username = username)
    }

    override suspend fun forgotPassword(email: String): ApiResult<Unit> =
        executeWithRetryAfter {
            publicAuthApi.forgotPassword(PasswordForgotRequest(email = email))
        }.mapSuccess { }

    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        safeEmptyApiCall {
            publicAuthApi.resetPasswordByToken(
                PasswordResetRequest(
                    newPassword = newPassword,
                    token = token,
                ),
            )
        }

    override suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit> =
        safeEmptyApiCall {
            authenticatedAuthApi.changePassword(
                ChangePasswordRequest(
                    newPassword = newPassword,
                    oldPassword = oldPassword,
                ),
            )
        }

    private suspend fun <T> executeWithRetryAfter(
        block: suspend () -> Response<T>,
    ): ApiResult<T> = try {
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
            val parsed = ApiErrorParser.parse(rawError)
            val retryAfterHeader = response.headers()["Retry-After"]?.trim()?.toLongOrNull()
            val enrichedError = when {
                parsed != null && parsed.retryAfter == null && retryAfterHeader != null ->
                    parsed.copy(retryAfter = retryAfterHeader)
                parsed == null && retryAfterHeader != null ->
                    ApiError(code = "RATE_LIMITED", retryAfter = retryAfterHeader)
                else -> parsed
            }
            ApiResult.HttpFailure(
                statusCode = response.code(),
                error = enrichedError,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        ApiResult.NetworkFailure(io)
    } catch (other: Throwable) {
        ApiResult.NetworkFailure(other)
    }

    private inline fun <T, R> ApiResult<T>.mapSuccess(transform: (T) -> R): ApiResult<R> = when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.HttpFailure -> this
        is ApiResult.NetworkFailure -> this
    }

    private fun MobileTokenResponse.toDomainTokens(username: String?): Tokens = Tokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessExpiresIn = accessExpiresIn,
        refreshExpiresIn = refreshExpiresIn,
        mustChangePassword = mustChangePassword,
        username = username,
    )
}
