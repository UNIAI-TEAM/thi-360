package vn.unicom.thi360.core.session

sealed interface SignOutReason {
    data object None : SignOutReason
    data object Expired : SignOutReason
    data class StudentOnly(val username: String) : SignOutReason
    data object PasswordReset : SignOutReason
}

sealed interface SessionState {
    data class SignedOut(val reason: SignOutReason = SignOutReason.None) : SessionState

    data class MustChangePassword(
        val username: String? = null,
        val isOffline: Boolean = false,
    ) : SessionState

    data class Active(
        val username: String? = null,
        val isOffline: Boolean = false,
    ) : SessionState

    data class Locked(
        val username: String? = null,
    ) : SessionState
}
