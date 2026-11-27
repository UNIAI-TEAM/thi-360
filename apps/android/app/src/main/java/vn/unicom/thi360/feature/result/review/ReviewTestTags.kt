package vn.unicom.thi360.feature.result.review

object ReviewTestTags {
    const val SCREEN = "review_screen"
    fun screenFor(attemptId: String): String = "review_screen_$attemptId"

    const val BACK_BUTTON = "review_back_button"
    const val HEADER_SUBTITLE = "review_header_subtitle"
    const val LOADING = "review_loading"
    const val NOT_INCLUDED = "review_not_included"
    const val NOT_AVAILABLE = "review_not_available"
    const val NOT_FOUND = "review_not_found"
    const val BACK_TO_RESULT_BUTTON = "review_back_to_result_button"
    const val MY_EXAMS_BUTTON = "review_my_exams_button"
    const val HISTORY_BUTTON = "review_history_button"

    const val SCORE_ONLY_BANNER = "review_score_only_banner"
    const val PENDING_BANNER = "review_pending_banner"

    const val PARTS_BAR = "review_parts_bar"
    fun partTab(index: Int): String = "review_part_tab_$index"

    const val SKIPPED_PART_CARD = "review_skipped_part_card"
    const val EMPTY_PART_CARD = "review_empty_part_card"

    const val PALETTE_STRIP = "review_palette_strip"
    const val PALETTE_SIDE_PANE = "review_palette_side_pane"
    const val PALETTE_SUMMARY = "review_palette_summary"
    const val PALETTE_LEGEND = "review_palette_legend"
    fun paletteCell(index: Int): String = "review_palette_cell_$index"
    const val PREV_BUTTON = "review_prev_button"
    const val NEXT_BUTTON = "review_next_button"

    const val QUESTION_LIST = "review_question_list"
    fun questionCard(index: Int): String = "review_question_card_$index"
    fun essayCriteriaBlock(id: String): String = "review_essay_criteria_$id"
}
