package vn.unicom.thi360.feature.myexams

import java.util.UUID

object MyExamsTestTags {
    const val LIST = "my_exams_list"
    const val SUBTITLE = "my_exams_subtitle"
    const val LOADING_SKELETON = "my_exams_loading_skeleton"
    const val SECTION_HEADER = "my_exams_section_header"
    const val SECTION_COUNT = "my_exams_section_count"
    const val EXPLORE_BUTTON = "my_exams_explore_button"
    const val EMPTY_STATE = "my_exams_empty_state"
    const val EMPTY_EXPLORE_BUTTON = "my_exams_empty_explore_button"
    const val ERROR_STATE = "my_exams_error_state"
    const val ERROR_RETRY_BUTTON = "my_exams_error_retry_button"
    const val OFFLINE_STATE = "my_exams_offline_state"
    const val OFFLINE_RETRY_BUTTON = "my_exams_offline_retry_button"
    const val STALE_BANNER = "my_exams_stale_banner"
    const val STALE_RETRY_BUTTON = "my_exams_stale_retry_button"
    const val CHECKING_SECTION = "my_exams_checking_section"
    const val STANDALONE_SECTION_HEADER = "my_exams_standalone_section_header"
    const val STANDALONE_SECTION_COUNT = "my_exams_standalone_section_count"

    fun checkingCard(code: String): String = "my_exams_checking_card_$code"
    fun checkingReloadButton(code: String): String = "my_exams_checking_reload_$code"
    fun checkingViewOrderButton(code: String): String = "my_exams_checking_view_order_$code"

    fun bundleGroup(bundleId: UUID): String = "my_exams_bundle_group_$bundleId"
    fun bundleHeading(bundleId: UUID): String = "my_exams_bundle_heading_$bundleId"
    fun bundleCount(bundleId: UUID): String = "my_exams_bundle_count_$bundleId"
    fun bundleViewButton(bundleId: UUID): String = "my_exams_bundle_view_$bundleId"
    fun bundleCrossRef(bundleId: UUID, examCode: String): String =
        "my_exams_bundle_cross_ref_${bundleId}_$examCode"
    fun bundleFootnote(bundleId: UUID): String = "my_exams_bundle_footnote_$bundleId"

    fun examCard(code: String): String = "my_exams_card_$code"
    fun examCardContinue(code: String): String = "my_exams_card_continue_$code"
    fun examCardStart(code: String): String = "my_exams_card_start_$code"
    fun examCardRebuy(code: String): String = "my_exams_card_rebuy_$code"
    fun examCardViewResult(code: String): String = "my_exams_card_view_result_$code"
    fun examCardHistory(code: String): String = "my_exams_card_history_$code"
    fun examCardBuyMore(code: String): String = "my_exams_card_buy_more_$code"
    fun examCardMiddleNote(code: String, note: CardNote): String =
        "my_exams_card_note_${code}_${note.name}"
    fun examCardPendingTopUpNote(code: String): String = "my_exams_card_pending_topup_$code"
}
