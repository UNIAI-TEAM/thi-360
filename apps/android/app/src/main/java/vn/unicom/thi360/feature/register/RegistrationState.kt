package vn.unicom.thi360.feature.register

import java.util.UUID

const val TERMS_VERSION = "2026-11"

data class RegistrationFormData(
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val acceptTerms: Boolean = false,
    val pendingRegistrationId: UUID? = null,
)

sealed interface RegistrationState {
    data class Form(val data: RegistrationFormData = RegistrationFormData()) : RegistrationState

    data class Otp(
        val registrationId: UUID,
        val email: String,
        val resendAt: Long,
        val formData: RegistrationFormData,
    ) : RegistrationState

    data object Done : RegistrationState
}
