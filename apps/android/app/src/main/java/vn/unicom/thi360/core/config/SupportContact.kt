package vn.unicom.thi360.core.config

import vn.unicom.thi360.BuildConfig

data class SupportContact(
    val phone: String = "",
    val email: String = "",
    val hours: String = "",
) {
    val normalizedPhone: String? = phone.trim().takeIf { it.isNotEmpty() }
    val normalizedEmail: String? = email.trim().takeIf { it.isNotEmpty() }
    val normalizedHours: String? = hours.trim().takeIf { it.isNotEmpty() }

    val hasAnyChannel: Boolean
        get() = normalizedPhone != null || normalizedEmail != null || normalizedHours != null

    fun formattedSummary(): String? {
        val parts = listOfNotNull(normalizedPhone, normalizedEmail, normalizedHours)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    companion object {
        fun fromBuildConfig(): SupportContact = SupportContact(
            phone = BuildConfig.SUPPORT_PHONE,
            email = BuildConfig.SUPPORT_EMAIL,
            hours = BuildConfig.SUPPORT_HOURS,
        )
    }
}
