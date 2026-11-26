package vn.unicom.thi360.feature.register

import vn.unicom.thi360.feature.password.PasswordRules
import vn.unicom.thi360.feature.password.PasswordValidationError

enum class FullNameError {
    REQUIRED,
    TOO_LONG,
}

enum class UsernameError {
    REQUIRED,
    PATTERN,
    TAKEN,
}

enum class EmailError {
    REQUIRED,
    INVALID,
    TAKEN,
}

data class RegistrationFieldErrors(
    val fullName: FullNameError? = null,
    val username: UsernameError? = null,
    val email: EmailError? = null,
    val password: PasswordValidationError? = null,
    val acceptTermsRequired: Boolean = false,
) {
    val hasErrors: Boolean
        get() = fullName != null ||
            username != null ||
            email != null ||
            password != null ||
            acceptTermsRequired
}

object RegistrationValidation {
    private const val MAX_FULL_NAME_LENGTH = 200
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9._@-]{3,29}$")
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val OTP_REGEX = Regex("^\\d{6}$")

    fun validateEmail(rawEmail: String): EmailError? {
        val trimmed = rawEmail.trim()
        return when {
            trimmed.isEmpty() -> EmailError.REQUIRED
            !EMAIL_REGEX.matches(trimmed) -> EmailError.INVALID
            else -> null
        }
    }

    fun validateOtpCode(code: String): Boolean = OTP_REGEX.matches(code)

    fun validateForm(
        data: RegistrationFormData,
        password: String,
    ): RegistrationFieldErrors {
        val trimmedFullName = data.fullName.trim()
        val trimmedUsername = data.username.trim()
        val fullNameError = when {
            trimmedFullName.isEmpty() -> FullNameError.REQUIRED
            trimmedFullName.length > MAX_FULL_NAME_LENGTH -> FullNameError.TOO_LONG
            else -> null
        }
        val usernameError = when {
            trimmedUsername.isEmpty() -> UsernameError.REQUIRED
            !USERNAME_REGEX.matches(trimmedUsername) -> UsernameError.PATTERN
            else -> null
        }
        return RegistrationFieldErrors(
            fullName = fullNameError,
            username = usernameError,
            email = validateEmail(data.email),
            password = PasswordRules.validate(password, trimmedUsername),
            acceptTermsRequired = !data.acceptTerms,
        )
    }

    fun formatCountdown(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val minutes = clamped / 60L
        val seconds = clamped % 60L
        return "%02d:%02d".format(java.util.Locale.US, minutes, seconds)
    }
}
