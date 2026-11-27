package vn.unicom.thi360.feature.account

enum class AccountDetailPane {
    PROFILE,
    ORDERS,
    CHANGE_PASSWORD,
    LANGUAGE,
    THEME,
    LEGAL_TERMS,
    LEGAL_PRIVACY,
    DELETE_ACCOUNT,
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
    const val PROFILE_AVATAR_IMAGE = "account_profile_avatar_image"
    const val PROFILE_AVATAR_INITIALS = "account_profile_avatar_initials"

    const val PROFILE_ROW = "account_profile_row"
    const val ORDERS_ROW = "account_orders_row"
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

    const val LEGAL_TERMS_ROW = "account_legal_terms_row"
    const val LEGAL_PRIVACY_ROW = "account_legal_privacy_row"

    const val LOGOUT_BUTTON = "account_logout_button"
    const val LOGOUT_DIALOG = "account_logout_dialog"
    const val LOGOUT_CONFIRM_BUTTON = "account_logout_confirm_button"
    const val LOGOUT_CANCEL_BUTTON = "account_logout_cancel_button"

    const val DELETE_ACCOUNT_ROW = "account_delete_account_row"
    const val LIST_COLUMN = "account_list_column"
    const val DETAIL_PANE = "account_detail_pane"
}

object ProfileTestTags {
    const val SCREEN = "profile_screen"
    const val BACK_BUTTON = "profile_back_button"
    const val TITLE = "profile_title"
    const val INTRO = "profile_intro"
    const val REFRESH_ERROR_BANNER = "profile_refresh_error_banner"
    const val REFRESH_RETRY_BUTTON = "profile_refresh_retry_button"
    const val UNVERIFIED_BANNER = "profile_unverified_banner"
    const val VERIFY_EMAIL_BUTTON = "profile_verify_email_button"
    const val SNACKBAR = "profile_snackbar"

    const val AVATAR_SECTION = "profile_avatar_section"
    const val AVATAR_IMAGE = "profile_avatar_image"
    const val AVATAR_INITIALS = "profile_avatar_initials"
    const val AVATAR_UPLOAD_BUTTON = "profile_avatar_upload_button"
    const val AVATAR_REMOVE_BUTTON = "profile_avatar_remove_button"
    const val AVATAR_HINT = "profile_avatar_hint"
    const val AVATAR_ERROR = "profile_avatar_error"
    const val AVATAR_SOURCE_SHEET = "profile_avatar_source_sheet"
    const val AVATAR_SOURCE_GALLERY = "profile_avatar_source_gallery"
    const val AVATAR_SOURCE_CAMERA = "profile_avatar_source_camera"
    const val CAMERA_DENIED_BANNER = "profile_camera_denied_banner"
    const val CAMERA_OPEN_SETTINGS_BUTTON = "profile_camera_open_settings_button"

    const val INFO_SECTION = "profile_info_section"
    const val INFO_ERROR_BANNER = "profile_info_error_banner"
    const val USERNAME_FIELD = "profile_username_field"
    const val FULL_NAME_FIELD = "profile_full_name_field"
    const val FULL_NAME_ERROR = "profile_full_name_error"
    const val PHONE_FIELD = "profile_phone_field"
    const val PHONE_ERROR = "profile_phone_error"
    const val PHONE_EMPTY_HINT = "profile_phone_empty_hint"
    const val INFO_CANCEL_BUTTON = "profile_info_cancel_button"
    const val INFO_SAVE_BUTTON = "profile_info_save_button"

    const val EMAIL_SECTION = "profile_email_section"
    const val EMAIL_VALUE = "profile_email_value"
    const val EMAIL_BADGE = "profile_email_badge"
    const val EMAIL_NONE = "profile_email_none"
    const val CHANGE_EMAIL_TOGGLE = "profile_change_email_toggle"
    const val CHANGE_EMAIL_FORM = "profile_change_email_form"
    const val CHANGE_EMAIL_ALERT = "profile_change_email_alert"
    const val NEW_EMAIL_FIELD = "profile_new_email_field"
    const val NEW_EMAIL_ERROR = "profile_new_email_error"
    const val CURRENT_PASSWORD_FIELD = "profile_current_password_field"
    const val CURRENT_PASSWORD_TOGGLE = "profile_current_password_toggle"
    const val CURRENT_PASSWORD_ERROR = "profile_current_password_error"
    const val CHANGE_EMAIL_CANCEL = "profile_change_email_cancel"
    const val CHANGE_EMAIL_SEND = "profile_change_email_send"
    const val CHANGE_EMAIL_COOLDOWN = "profile_change_email_cooldown"

    const val PENDING_EMAIL_CARD = "profile_pending_email_card"
    const val PENDING_EMAIL_TITLE = "profile_pending_email_title"
    const val PENDING_EMAIL_ALERT = "profile_pending_email_alert"
    const val PENDING_CODE_FIELD = "profile_pending_code_field"
    const val PENDING_CODE_ERROR = "profile_pending_code_error"
    const val PENDING_CONFIRM_BUTTON = "profile_pending_confirm_button"
    const val PENDING_RESEND_BUTTON = "profile_pending_resend_button"
    const val PENDING_COOLDOWN = "profile_pending_cooldown"
    const val COOLDOWN_LIVE_REGION = "profile_cooldown_live_region"
}

object VerifyEmailTestTags {
    const val SCREEN = "verify_email_screen"
    const val BACK_BUTTON = "verify_email_back_button"
    const val TITLE = "verify_email_title"
    const val SENT_TO = "verify_email_sent_to"
    const val ALERT = "verify_email_alert"
    const val DAILY_LIMIT_BANNER = "verify_email_daily_limit_banner"
    const val CODE_FIELD = "verify_email_code_field"
    const val CODE_ERROR = "verify_email_code_error"
    const val SUBMIT_BUTTON = "verify_email_submit_button"
    const val RESEND_BUTTON = "verify_email_resend_button"
    const val RESEND_COOLDOWN = "verify_email_resend_cooldown"
    const val CODE_RESENT_NOTE = "verify_email_code_resent_note"
    const val LATER_BUTTON = "verify_email_later_button"
    const val FOOTNOTE = "verify_email_footnote"
    const val ALREADY_VERIFIED_BANNER = "verify_email_already_verified_banner"
    const val CONTINUE_BUTTON = "verify_email_continue_button"
    const val COOLDOWN_LIVE_REGION = "verify_email_cooldown_live_region"
}

object DeleteAccountTestTags {
    const val SCREEN = "delete_account_screen"
    const val BACK_BUTTON = "delete_account_back_button"
    const val TITLE = "delete_account_title"
    const val EXPLANATION = "delete_account_explanation"
    const val UNUSED_ATTEMPTS_WARNING = "delete_account_unused_attempts_warning"
    const val PENDING_GRADING_WARNING = "delete_account_pending_grading_warning"
    const val BLOCKED_CARD = "delete_account_blocked_card"
    const val BLOCKED_ATTEMPT_MESSAGE = "delete_account_blocked_attempt"
    const val OPEN_MY_EXAMS_BUTTON = "delete_account_open_my_exams"
    const val BLOCKED_ORDER_MESSAGE = "delete_account_blocked_order"
    const val BLOCKED_PENDING_PAYMENT_MESSAGE = "delete_account_blocked_pending_payment"
    const val OPEN_MY_ORDERS_BUTTON = "delete_account_open_my_orders"
    const val FORM_ERROR_BANNER = "delete_account_form_error"
    const val PASSWORD_FIELD = "delete_account_password_field"
    const val PASSWORD_TOGGLE = "delete_account_password_toggle"
    const val PASSWORD_ERROR = "delete_account_password_error"
    const val ACKNOWLEDGE_ROW = "delete_account_acknowledge_row"
    const val ACKNOWLEDGE_CHECKBOX = "delete_account_acknowledge_checkbox"
    const val SUBMIT_BUTTON = "delete_account_submit_button"
    const val CONFIRM_DIALOG = "delete_account_confirm_dialog"
    const val CONFIRM_ACTION_BUTTON = "delete_account_confirm_action"
    const val CONFIRM_CANCEL_BUTTON = "delete_account_confirm_cancel"
}
