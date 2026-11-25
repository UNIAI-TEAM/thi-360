package vn.unicom.thi360.feature.account

import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.api.ProfileApi
import vn.unicom.thi360.core.network.safeApiCall

data class UserProfile(
    val username: String,
    val fullName: String,
    val email: String?,
    val emailVerified: Boolean,
)

sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data class Loaded(val profile: UserProfile) : ProfileLoadState
    data object Error : ProfileLoadState
    data object Offline : ProfileLoadState
}

interface AccountProfileGateway {
    suspend fun fetchProfile(): ApiResult<UserProfile>
}

class OpenApiAccountProfileGateway(
    private val profileApi: ProfileApi,
) : AccountProfileGateway {

    override suspend fun fetchProfile(): ApiResult<UserProfile> =
        when (val result = safeApiCall { profileApi.getMyProfile() }) {
            is ApiResult.Success -> {
                val body = result.data
                ApiResult.Success(
                    UserProfile(
                        username = body.username,
                        fullName = body.fullName,
                        email = body.email?.trim()?.takeIf { it.isNotEmpty() },
                        emailVerified = body.emailVerified,
                    ),
                )
            }

            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }
}

internal fun formatAvatarInitials(fullName: String?, username: String?): String {
    val words = fullName.orEmpty()
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
    if (words.size >= 2) {
        val lastTwo = words.takeLast(2)
        return "${lastTwo[0].first().uppercaseChar()}${lastTwo[1].first().uppercaseChar()}"
    }
    val single = words.firstOrNull() ?: username.orEmpty().trim()
    val alphaNum = single.filter { it.isLetterOrDigit() }
    return when {
        alphaNum.length >= 2 -> alphaNum.take(2).uppercase()
        alphaNum.length == 1 -> alphaNum.uppercase()
        else -> "T3"
    }
}
