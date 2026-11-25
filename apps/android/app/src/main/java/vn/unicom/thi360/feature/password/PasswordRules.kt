package vn.unicom.thi360.feature.password

data class PasswordRuleEvaluation(
    val hasValidLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecial: Boolean,
) {
    val allSatisfied: Boolean
        get() = hasValidLength && hasUppercase && hasLowercase && hasDigit && hasSpecial
}

enum class PasswordValidationError {
    REQUIRED,
    WEAK,
    SAME_AS_USERNAME,
}

object PasswordRules {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    fun evaluate(password: String): PasswordRuleEvaluation {
        var hasUpper = false
        var hasLower = false
        var hasDigit = false
        var hasSpecial = false

        for (ch in password) {
            when {
                Character.isUpperCase(ch) -> hasUpper = true
                Character.isLowerCase(ch) -> hasLower = true
                Character.isDigit(ch) -> hasDigit = true
                !Character.isWhitespace(ch) -> hasSpecial = true
            }
        }

        return PasswordRuleEvaluation(
            hasValidLength = password.length in MIN_LENGTH..MAX_LENGTH,
            hasUppercase = hasUpper,
            hasLowercase = hasLower,
            hasDigit = hasDigit,
            hasSpecial = hasSpecial,
        )
    }

    fun isStrong(password: String): Boolean = evaluate(password).allSatisfied

    fun matchesUsername(password: String, username: String?): Boolean {
        val trimmed = username?.trim().orEmpty()
        return trimmed.isNotEmpty() && password.equals(trimmed, ignoreCase = true)
    }

    fun validate(password: String, username: String? = null): PasswordValidationError? = when {
        password.isEmpty() -> PasswordValidationError.REQUIRED
        matchesUsername(password, username) -> PasswordValidationError.SAME_AS_USERNAME
        !isStrong(password) -> PasswordValidationError.WEAK
        else -> null
    }
}
