package vn.unicom.thi360.feature.account

enum class AccountDetailPane {
    CHANGE_PASSWORD,
    LANGUAGE,
    THEME,
}

object AccountTestTags {
    const val PROFILE_CARD = "account_profile_card"
    const val PROFILE_LOADING = "account_profile_loading"
    const val PROFILE_ERROR = "account_profile_error"
    const val PROFILE_OFFLINE = "account_profile_offline"
    const val PROFILE_RETRY_BUTTON = "account_profile_retry_button"
    const val PROFILE_FULL_NAME = "account_profile_full_name"
    const val PROFILE_USERNAME = "account_profile_username"
    const val PROFILE_EMAIL = "account_profile_email"
    const val PROFILE_EMAIL_BADGE = "account_profile_email_badge"

    const val CHANGE_PASSWORD_ROW = "account_change_password_row"
    const val BIOMETRIC_ROW = "account_biometric_row"
    const val BIOMETRIC_SWITCH = "account_biometric_switch"
    const val BIOMETRIC_EXPLANATION = "account_biometric_explanation"
    const val BIOMETRIC_ERROR_BANNER = "account_biometric_error_banner"

    const val LANGUAGE_ROW = "account_language_row"
    const val LANGUAGE_DIALOG = "account_language_dialog"
    const val LANGUAGE_OPTION_VI = "account_language_option_vi"
    const val LANGUAGE_OPTION_EN = "account_language_option_en"

    const val THEME_ROW = "account_theme_row"
    const val THEME_OPTION_LIGHT = "account_theme_option_light"
    const val THEME_OPTION_DARK = "account_theme_option_dark"

    const val LOGOUT_BUTTON = "account_logout_button"
    const val LOGOUT_DIALOG = "account_logout_dialog"
    const val LOGOUT_CONFIRM_BUTTON = "account_logout_confirm_button"
    const val LOGOUT_CANCEL_BUTTON = "account_logout_cancel_button"

    const val DETAIL_PANE = "account_detail_pane"
}
