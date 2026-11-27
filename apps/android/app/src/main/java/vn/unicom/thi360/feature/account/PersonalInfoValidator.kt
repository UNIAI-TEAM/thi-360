package vn.unicom.thi360.feature.account

enum class FullNameValidationError {
    REQUIRED,
    TOO_LONG,
}

enum class PhoneValidationError {
    INVALID,
}

data class PersonalInfoValidationResult(
    val fullNameError: FullNameValidationError? = null,
    val phoneError: PhoneValidationError? = null,
) {
    val isValid: Boolean
        get() = fullNameError == null && phoneError == null
}

data class PersonalInfoPatch(
    val fullName: String,
    val phone: String?,
)

object PersonalInfoValidator {
    const val FULL_NAME_MAX_CHARS = 200
    private val PHONE_PATTERN = Regex("^0\\d{9}$")
    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val OTP_PATTERN = Regex("^\\d{6}$")

    fun clampFullNameInput(raw: String): String {
        if (raw.codePointCount(0, raw.length) <= FULL_NAME_MAX_CHARS) return raw
        val endIndex = raw.offsetByCodePoints(0, FULL_NAME_MAX_CHARS)
        return raw.substring(0, endIndex)
    }

    fun isDirty(fullName: String, phone: String, saved: UserProfile): Boolean =
        fullName != saved.fullName || phone != saved.phone.orEmpty()

    fun validate(fullName: String, phone: String, savedPhone: String?): PersonalInfoValidationResult {
        val trimmedName = fullName.trim()
        val nameError = when {
            trimmedName.isEmpty() -> FullNameValidationError.REQUIRED
            trimmedName.codePointCount(0, trimmedName.length) > FULL_NAME_MAX_CHARS -> FullNameValidationError.TOO_LONG
            else -> null
        }

        val trimmedPhone = phone.trim()
        val savedTrimmed = savedPhone.orEmpty().trim()
        val phoneError = if (
            trimmedPhone == savedTrimmed ||
            trimmedPhone.isEmpty() ||
            PHONE_PATTERN.matches(trimmedPhone)
        ) {
            null
        } else {
            PhoneValidationError.INVALID
        }

        return PersonalInfoValidationResult(
            fullNameError = nameError,
            phoneError = phoneError,
        )
    }

    fun buildPatch(fullName: String, phone: String, savedPhone: String?): PersonalInfoPatch {
        val trimmedName = fullName.trim()
        val trimmedPhone = phone.trim()
        val savedTrimmed = savedPhone.orEmpty().trim()
        return PersonalInfoPatch(
            fullName = trimmedName,
            phone = if (trimmedPhone != savedTrimmed) trimmedPhone else null,
        )
    }

    fun isValidEmail(raw: String): Boolean = EMAIL_PATTERN.matches(raw.trim())

    fun isValidVerificationCode(raw: String): Boolean = OTP_PATTERN.matches(raw)
}
