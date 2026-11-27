package vn.unicom.thi360.feature.account

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.generated.api.AvatarApi
import vn.unicom.thi360.core.network.generated.api.MeApi
import vn.unicom.thi360.core.network.generated.api.ProfileApi
import vn.unicom.thi360.core.network.generated.infrastructure.ApiClient
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AccountDeletionBlockedResponse
import vn.unicom.thi360.core.network.generated.model.AccountDeletionCheckResponse
import vn.unicom.thi360.core.network.generated.model.AccountDeletionRequest
import vn.unicom.thi360.core.network.generated.model.EmailCodeChangeRequest
import vn.unicom.thi360.core.network.generated.model.EmailCodeVerifyRequest
import vn.unicom.thi360.core.network.generated.model.ProfileResponse
import vn.unicom.thi360.core.network.generated.model.ProfileUpdateRequest
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.network.safeEmptyApiCall

interface AccountProfileGateway {
    suspend fun fetchProfile(): ApiResult<UserProfile>
}

interface ProfileGateway : AccountProfileGateway {
    suspend fun fetch(): ApiResult<UserProfile>

    override suspend fun fetchProfile(): ApiResult<UserProfile> = fetch()

    suspend fun update(fullName: String, phone: String?): ProfileOperationResult<UserProfile>

    suspend fun uploadAvatar(avatar: PreparedAvatar): ProfileOperationResult<UserProfile>

    suspend fun removeAvatar(): ProfileOperationResult<Unit>

    suspend fun sendVerification(): ProfileOperationResult<Unit>

    suspend fun verify(code: String): ProfileOperationResult<Unit>

    suspend fun requestEmailChange(newEmail: String, currentPassword: String): ProfileOperationResult<Unit>

    suspend fun confirmEmailChange(code: String): ProfileOperationResult<Unit>
}

interface AccountDeletionGateway {
    suspend fun check(): ApiResult<DeletionCheck>

    suspend fun delete(currentPassword: String): AccountDeletionOutcome
}

internal fun ProfileResponse.toUserProfile(): UserProfile = UserProfile(
    username = username,
    fullName = fullName,
    email = email?.trim()?.takeIf { it.isNotEmpty() },
    emailVerified = emailVerified,
    phone = phone?.trim()?.takeIf { it.isNotEmpty() },
    avatarAssetId = avatarAssetId?.toString(),
    avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
    pendingEmail = pendingEmail?.trim()?.takeIf { it.isNotEmpty() },
)

internal fun AccountDeletionCheckResponse.Blockers.toDomain(): DeletionBlocker = when (this) {
    AccountDeletionCheckResponse.Blockers.ATTEMPT_IN_PROGRESS -> DeletionBlocker.ATTEMPT_IN_PROGRESS
    AccountDeletionCheckResponse.Blockers.ORDER_BEING_CHECKED -> DeletionBlocker.ORDER_BEING_CHECKED
    AccountDeletionCheckResponse.Blockers.ORDER_PENDING_PAYMENT -> DeletionBlocker.ORDER_PENDING_PAYMENT
}

internal fun AccountDeletionBlockedResponse.Blockers.toDomain(): DeletionBlocker = when (this) {
    AccountDeletionBlockedResponse.Blockers.ATTEMPT_IN_PROGRESS -> DeletionBlocker.ATTEMPT_IN_PROGRESS
    AccountDeletionBlockedResponse.Blockers.ORDER_BEING_CHECKED -> DeletionBlocker.ORDER_BEING_CHECKED
    AccountDeletionBlockedResponse.Blockers.ORDER_PENDING_PAYMENT -> DeletionBlocker.ORDER_PENDING_PAYMENT
}

@OptIn(ExperimentalSerializationApi::class)
private val omitNullPatchJson = Json {
    serializersModule = Serializer.kotlinxSerializationAdapters
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
    isLenient = true
}

class OpenApiProfileGateway(
    private val profileApi: ProfileApi,
    private val patchProfileApi: ProfileApi,
    private val avatarApi: AvatarApi,
    private val meApi: MeApi,
) : ProfileGateway {

    override suspend fun fetch(): ApiResult<UserProfile> =
        when (val result = safeApiCall { profileApi.getMyProfile() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toUserProfile())
            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }

    override suspend fun update(fullName: String, phone: String?): ProfileOperationResult<UserProfile> =
        when (val result = safeApiCall { patchProfileApi.updateMyProfile(ProfileUpdateRequest(fullName = fullName, phone = phone)) }) {
            is ApiResult.Success -> ProfileOperationResult.Success(result.data.toUserProfile())
            is ApiResult.NetworkFailure -> ProfileOperationResult.Failure(ProfileError.Offline)
            is ApiResult.HttpFailure -> ProfileOperationResult.Failure(mapProfileUpdateError(result))
        }

    override suspend fun uploadAvatar(avatar: PreparedAvatar): ProfileOperationResult<UserProfile> {
        val body = avatar.bytes.toRequestBody(avatar.mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", avatar.fileName, body)
        return when (val result = safeApiCall { avatarApi.replaceMyAvatar(part) }) {
            is ApiResult.Success -> ProfileOperationResult.Success(result.data.toUserProfile())
            is ApiResult.NetworkFailure -> ProfileOperationResult.Failure(ProfileError.Offline)
            is ApiResult.HttpFailure -> ProfileOperationResult.Failure(mapAvatarUploadError(result))
        }
    }

    override suspend fun removeAvatar(): ProfileOperationResult<Unit> =
        when (safeEmptyApiCall { avatarApi.removeMyAvatar() }) {
            is ApiResult.Success -> ProfileOperationResult.Success(Unit)
            is ApiResult.NetworkFailure -> ProfileOperationResult.Failure(ProfileError.Offline)
            is ApiResult.HttpFailure -> ProfileOperationResult.Failure(ProfileError.Other)
        }

    override suspend fun sendVerification(): ProfileOperationResult<Unit> =
        when (val result = safeEmptyApiCall { meApi.sendVerification() }) {
            is ApiResult.Success -> ProfileOperationResult.Success(Unit)
            is ApiResult.NetworkFailure -> ProfileOperationResult.Failure(ProfileError.Offline)
            is ApiResult.HttpFailure -> ProfileOperationResult.Failure(mapSendVerificationError(result))
        }

    override suspend fun verify(code: String): ProfileOperationResult<Unit> =
        when (val result = safeEmptyApiCall { meApi.verify(EmailCodeVerifyRequest(code = code)) }) {
            is ApiResult.Success -> ProfileOperationResult.Success(Unit)
            is ApiResult.NetworkFailure -> ProfileOperationResult.Failure(ProfileError.Offline)
            is ApiResult.HttpFailure -> ProfileOperationResult.Failure(mapVerifyEmailError(result))
        }

    override suspend fun requestEmailChange(newEmail: String, currentPassword: String): ProfileOperationResult<Unit> =
        when (
            val result = safeEmptyApiCall {
                meApi.requestChange(
                    EmailCodeChangeRequest(
                        currentPassword = currentPassword,
                        newEmail = newEmail,
                    ),
                )
            }
        ) {
            is ApiResult.Success -> ProfileOperationResult.Success(Unit)
            is ApiResult.NetworkFailure -> ProfileOperationResult.Failure(ProfileError.Offline)
            is ApiResult.HttpFailure -> ProfileOperationResult.Failure(mapRequestEmailChangeError(result))
        }

    override suspend fun confirmEmailChange(code: String): ProfileOperationResult<Unit> =
        when (val result = safeEmptyApiCall { meApi.confirmChange(EmailCodeVerifyRequest(code = code)) }) {
            is ApiResult.Success -> ProfileOperationResult.Success(Unit)
            is ApiResult.NetworkFailure -> ProfileOperationResult.Failure(ProfileError.Offline)
            is ApiResult.HttpFailure -> ProfileOperationResult.Failure(mapConfirmEmailChangeError(result))
        }

    companion object {
        fun fromSessionStack(
            stack: Thi360SessionStack,
            baseUrl: String,
        ): OpenApiProfileGateway {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val patchClient = ApiClient(
                baseUrl = normalizedBaseUrl,
                callFactory = stack.authenticatedOkHttpClient,
                converterFactories = listOf(
                    ScalarsConverterFactory.create(),
                    omitNullPatchJson.asConverterFactory("application/json".toMediaType()),
                ),
            )
            return OpenApiProfileGateway(
                profileApi = stack.profileApi,
                patchProfileApi = patchClient.createService(ProfileApi::class.java),
                avatarApi = stack.authenticatedService(AvatarApi::class.java),
                meApi = stack.authenticatedService(MeApi::class.java),
            )
        }

        internal fun mapProfileUpdateError(failure: ApiResult.HttpFailure): ProfileError {
            val fieldErrors = failure.error?.fieldErrors.orEmpty()
            val relevantField = fieldErrors.firstOrNull { it.field == "phone" }
                ?: fieldErrors.firstOrNull { it.field == "fullName" }
            if (relevantField != null) {
                return ProfileError.Field(field = relevantField.field, code = relevantField.code)
            }
            return ProfileError.Other
        }

        internal fun mapAvatarUploadError(failure: ApiResult.HttpFailure): ProfileError {
            val code = failure.error?.code.orEmpty()
            return when {
                failure.statusCode == 413 || code == "AVATAR_TOO_LARGE" || code == "UPLOAD_TOO_LARGE" ->
                    ProfileError.AvatarTooLarge
                code == "AVATAR_DIMENSIONS_EXCEEDED" -> ProfileError.AvatarDimensions
                code == "AVATAR_INVALID_IMAGE" -> ProfileError.AvatarInvalid
                else -> ProfileError.Other
            }
        }

        internal fun mapSendVerificationError(failure: ApiResult.HttpFailure): ProfileError =
            if (failure.statusCode == 429) {
                ProfileError.RateLimited(failure.retryAfterSeconds ?: failure.error?.retryAfter)
            } else {
                ProfileError.Other
            }

        internal fun mapVerifyEmailError(failure: ApiResult.HttpFailure): ProfileError =
            when (failure.error?.code) {
                "EMAIL_CODE_INVALID" -> ProfileError.CodeInvalid(failure.error.remainingAttempts)
                "EMAIL_CODE_EXPIRED" -> ProfileError.CodeExpired
                else -> ProfileError.Other
            }

        internal fun mapRequestEmailChangeError(failure: ApiResult.HttpFailure): ProfileError {
            val error = failure.error
            val code = error?.code.orEmpty()
            if (failure.statusCode == 429) {
                return if (code == "LOGIN_THROTTLED") {
                    ProfileError.Throttled
                } else {
                    ProfileError.RateLimited(failure.retryAfterSeconds ?: error?.retryAfter)
                }
            }
            if (failure.statusCode == 409 && code == "EMAIL_TAKEN") {
                return ProfileError.EmailTaken
            }
            val passwordField = error?.fieldErrors?.firstOrNull { it.field == "currentPassword" }
            if (passwordField != null || code == "WRONG_OLD_PASSWORD") {
                return ProfileError.Field("currentPassword", passwordField?.code ?: "WRONG_OLD_PASSWORD")
            }
            val emailField = error?.fieldErrors?.firstOrNull { it.field == "newEmail" }
            if (emailField != null) {
                return ProfileError.Field("newEmail", emailField.code)
            }
            return ProfileError.Other
        }

        internal fun mapConfirmEmailChangeError(failure: ApiResult.HttpFailure): ProfileError {
            val error = failure.error
            return when {
                error?.code == "EMAIL_CODE_INVALID" -> ProfileError.CodeInvalid(error.remainingAttempts)
                error?.code == "EMAIL_CODE_EXPIRED" -> ProfileError.CodeExpired
                failure.statusCode == 409 && error?.code == "EMAIL_TAKEN" -> ProfileError.EmailTaken
                failure.statusCode == 503 -> ProfileError.SyncFailed
                else -> ProfileError.Other
            }
        }
    }
}

internal class ReadOnlyProfileGatewayAdapter(
    private val reader: AccountProfileGateway,
    private val fallback: ProfileGateway,
) : ProfileGateway {
    override suspend fun fetch(): ApiResult<UserProfile> = reader.fetchProfile()
    override suspend fun update(fullName: String, phone: String?): ProfileOperationResult<UserProfile> =
        fallback.update(fullName, phone)
    override suspend fun uploadAvatar(avatar: PreparedAvatar): ProfileOperationResult<UserProfile> =
        fallback.uploadAvatar(avatar)
    override suspend fun removeAvatar(): ProfileOperationResult<Unit> = fallback.removeAvatar()
    override suspend fun sendVerification(): ProfileOperationResult<Unit> = fallback.sendVerification()
    override suspend fun verify(code: String): ProfileOperationResult<Unit> = fallback.verify(code)
    override suspend fun requestEmailChange(newEmail: String, currentPassword: String): ProfileOperationResult<Unit> =
        fallback.requestEmailChange(newEmail, currentPassword)
    override suspend fun confirmEmailChange(code: String): ProfileOperationResult<Unit> =
        fallback.confirmEmailChange(code)
}

class OpenApiAccountDeletionGateway(
    private val checkApi: MeApi,
    private val deleteApi: MeApi,
) : AccountDeletionGateway {

    override suspend fun check(): ApiResult<DeletionCheck> =
        when (val result = safeApiCall { checkApi.checkMyAccountDeletion() }) {
            is ApiResult.Success -> {
                val data = result.data
                ApiResult.Success(
                    DeletionCheck(
                        canDelete = data.canDelete,
                        blockers = data.blockers.map { it.toDomain() },
                        unusedAttempts = data.unusedAttempts,
                        pendingGrading = data.pendingGrading,
                    ),
                )
            }
            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }

    override suspend fun delete(currentPassword: String): AccountDeletionOutcome = try {
        val response: Response<Unit> = deleteApi.deleteMyAccount(
            AccountDeletionRequest(currentPassword = currentPassword),
        )
        when {
            response.isSuccessful -> AccountDeletionOutcome.Deleted
            response.code() == HTTP_UNAUTHORIZED -> AccountDeletionOutcome.Deleted
            else -> mapDeleteError(response.code(), response.errorBody()?.string())
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        AccountDeletionOutcome.Failed(AccountDeletionFailureKind.Network)
    } catch (_: Throwable) {
        AccountDeletionOutcome.Failed(AccountDeletionFailureKind.Network)
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_CONFLICT = 409
        private const val HTTP_TOO_MANY_REQUESTS = 429

        fun fromSessionStack(
            stack: Thi360SessionStack,
            baseUrl: String,
        ): OpenApiAccountDeletionGateway {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            // Bypass TokenAuthenticator on POST /deletion so a 401 retry after a lost 204 response
            // returns directly to the deletion flow and signs out with AccountDeleted instead of Expired.
            val noAuthRetryClient: OkHttpClient = stack.authenticatedOkHttpClient.newBuilder()
                .authenticator(Authenticator.NONE)
                .build()
            val deleteApiClient = ApiClient(
                baseUrl = normalizedBaseUrl,
                callFactory = noAuthRetryClient,
            )
            return OpenApiAccountDeletionGateway(
                checkApi = stack.authenticatedService(MeApi::class.java),
                deleteApi = deleteApiClient.createService(MeApi::class.java),
            )
        }

        internal fun mapDeleteError(statusCode: Int, rawBody: String?): AccountDeletionOutcome {
            if (statusCode == HTTP_CONFLICT) {
                val blocked = rawBody?.let {
                    runCatching {
                        Serializer.kotlinxSerializationJson.decodeFromString(
                            AccountDeletionBlockedResponse.serializer(),
                            it,
                        )
                    }.getOrNull()
                }
                if (blocked != null && blocked.code == "ACCOUNT_DELETION_BLOCKED") {
                    return AccountDeletionOutcome.Blocked(blocked.blockers.map { it.toDomain() })
                }
            }
            val parsed = ApiErrorParser.parse(rawBody)
            return when {
                statusCode == HTTP_BAD_REQUEST && (
                    parsed?.code == "WRONG_OLD_PASSWORD" ||
                        parsed?.fieldErrors?.any { it.field == "currentPassword" } == true
                    ) -> AccountDeletionOutcome.Failed(AccountDeletionFailureKind.WrongPassword)

                statusCode == HTTP_TOO_MANY_REQUESTS ->
                    AccountDeletionOutcome.Failed(AccountDeletionFailureKind.Throttled)

                else -> AccountDeletionOutcome.Failed(AccountDeletionFailureKind.Server)
            }
        }
    }
}
