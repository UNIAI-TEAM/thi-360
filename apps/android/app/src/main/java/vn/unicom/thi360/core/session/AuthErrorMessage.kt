package vn.unicom.thi360.core.session

import androidx.annotation.StringRes
import vn.unicom.thi360.R

enum class LoginErrorKind(@param:StringRes val messageRes: Int) {
    INVALID_CREDENTIALS(R.string.login_error_invalid_credentials),
    ACCOUNT_INACTIVE(R.string.login_error_account_inactive),
    LOGIN_THROTTLED(R.string.login_error_throttled),
    RATE_LIMITED(R.string.login_error_rate_limited),
    NETWORK(R.string.login_error_network),
    GENERIC(R.string.login_error_generic),
}

object AuthErrorMessage {
    fun fromApiErrorCode(code: String?): LoginErrorKind = when (code?.trim()?.uppercase()) {
        "INVALID_CREDENTIALS" -> LoginErrorKind.INVALID_CREDENTIALS
        "ACCOUNT_INACTIVE" -> LoginErrorKind.ACCOUNT_INACTIVE
        "LOGIN_THROTTLED" -> LoginErrorKind.LOGIN_THROTTLED
        "RATE_LIMITED" -> LoginErrorKind.RATE_LIMITED
        else -> LoginErrorKind.GENERIC
    }
}
