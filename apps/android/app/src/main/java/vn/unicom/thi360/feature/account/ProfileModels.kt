package vn.unicom.thi360.feature.account

import vn.unicom.thi360.core.ui.LoadState

data class UserProfile(
    val username: String,
    val fullName: String,
    val email: String?,
    val emailVerified: Boolean,
    val phone: String? = null,
    val avatarAssetId: String? = null,
    val avatarUrl: String? = null,
    val pendingEmail: String? = null,
)

class PreparedAvatar(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String = "avatar.jpg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreparedAvatar) return false
        return mimeType == other.mimeType && fileName == other.fileName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }

    override fun toString(): String = "PreparedAvatar(mimeType=$mimeType, size=${bytes.size})"
}

sealed interface AvatarPrepareResult {
    data class Prepared(val avatar: PreparedAvatar) : AvatarPrepareResult
    data object Unreadable : AvatarPrepareResult
}

sealed interface ProfileError {
    data class Field(val field: String, val code: String) : ProfileError
    data class CodeInvalid(val remainingAttempts: Int?) : ProfileError
    data object CodeExpired : ProfileError
    data object EmailTaken : ProfileError
    data class RateLimited(val retryAfterSeconds: Long?) : ProfileError
    data object Throttled : ProfileError
    data object SyncFailed : ProfileError
    data object AvatarTooLarge : ProfileError
    data object AvatarDimensions : ProfileError
    data object AvatarInvalid : ProfileError
    data object Offline : ProfileError
    data object Other : ProfileError
}

sealed interface ProfileOperationResult<out T> {
    data class Success<T>(val value: T) : ProfileOperationResult<T>
    data class Failure(val error: ProfileError) : ProfileOperationResult<Nothing>
}

enum class DeletionBlocker {
    ATTEMPT_IN_PROGRESS,
    ORDER_BEING_CHECKED,
    ORDER_PENDING_PAYMENT,
}

data class DeletionCheck(
    val canDelete: Boolean,
    val blockers: List<DeletionBlocker>,
    val unusedAttempts: Int,
    val pendingGrading: Boolean,
)

enum class AccountDeletionFailureKind {
    WrongPassword,
    Throttled,
    Network,
    Server,
}

sealed interface AccountDeletionOutcome {
    data object Deleted : AccountDeletionOutcome
    data class Blocked(val blockers: List<DeletionBlocker>) : AccountDeletionOutcome
    data class Failed(val kind: AccountDeletionFailureKind) : AccountDeletionOutcome
}

sealed interface AccountDeletionState {
    data class Checking(val loadState: LoadState<DeletionCheck>) : AccountDeletionState
    data class Blocked(val blockers: List<DeletionBlocker>) : AccountDeletionState
    data class Ready(val unusedAttempts: Int, val pendingGrading: Boolean) : AccountDeletionState
    data class Confirming(val unusedAttempts: Int, val pendingGrading: Boolean) : AccountDeletionState
    data class Deleting(val unusedAttempts: Int, val pendingGrading: Boolean) : AccountDeletionState
    data object Deleted : AccountDeletionState
    data class Failed(
        val kind: AccountDeletionFailureKind,
        val unusedAttempts: Int,
        val pendingGrading: Boolean,
    ) : AccountDeletionState
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
