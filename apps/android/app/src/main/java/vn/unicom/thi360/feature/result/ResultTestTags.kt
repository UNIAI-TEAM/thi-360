package vn.unicom.thi360.feature.result

object ResultTestTags {
    const val SCREEN = "result_screen"
    fun screenFor(attemptId: String): String = "result_screen_$attemptId"
    const val CONTENT_LIST = "result_content_list"

    const val BACK_BUTTON = "result_back_button"
    const val LOADING = "result_loading"
    const val NOT_FOUND = "result_not_found"
    const val NOT_FINISHED = "result_not_finished"
    const val CONTINUE_ATTEMPT_BUTTON = "result_continue_attempt_button"

    const val HEADER = "result_header"
    const val EXAM_TITLE = "result_exam_title"
    const val STATUS_CHIP = "result_status_chip"
    const val EXAM_KIND_CHIP = "result_exam_kind_chip"
    const val SCOPE_CHIP = "result_scope_chip"
    const val ATTEMPT_NO_CHIP = "result_attempt_no_chip"

    const val FAULT_BANNER = "result_fault_banner"
    const val FAULT_REASON = "result_fault_reason"

    const val GRADING_CARD = "result_grading_card"
    const val GRADING_TIMEOUT_CARD = "result_grading_timeout_card"
    const val GRADING_RELOAD_BUTTON = "result_grading_reload_button"
    const val GRADING_ERROR_CARD = "result_grading_error_card"

    const val TOTAL_SCORE_CARD = "result_total_score_card"
    const val TOTAL_SCORE_VALUE = "result_total_score_value"
    const val VACT_PRACTICE_BADGE = "result_vact_practice_badge"
    const val VACT_PRACTICE_NOTE = "result_vact_practice_note"
    const val PENDING_TOTAL_BOX = "result_pending_total_box"
    const val REGRADING_NOTE = "result_regrading_note"

    const val STATS_CARD = "result_stats_card"
    const val STAT_CORRECT = "result_stat_correct"
    const val STAT_PARTIAL = "result_stat_partial"
    const val STAT_WRONG = "result_stat_wrong"
    const val STAT_BLANK = "result_stat_blank"
    const val STAT_PENDING = "result_stat_pending"

    const val REVIEW_BUTTON = "result_review_button"
    const val MY_EXAMS_BUTTON = "result_my_exams_button"
    const val HISTORY_BUTTON = "result_history_button"
    const val SCORE_ONLY_NOTE = "result_score_only_note"
    const val PENDING_REVIEW_NOTE = "result_pending_review_note"
    const val UNAVAILABLE_NOTE = "result_unavailable_note"

    const val INFO_CARD = "result_info_card"

    const val PARTS_SECTION = "result_parts_section"
    fun partCard(index: Int): String = "result_part_$index"

    const val SEGMENTS_SECTION = "result_segments_section"
    fun segmentCard(index: Int): String = "result_segment_$index"

    const val SUBJECTS_SECTION = "result_subjects_section"
    fun subjectCard(index: Int): String = "result_subject_$index"

    const val VACT_COMPONENTS_SECTION = "result_vact_components_section"
    fun vactComponentCard(code: String): String = "result_vact_component_$code"
    const val VACT_SUBSECTIONS_TOGGLE = "result_vact_subsections_toggle"
    const val VACT_SUBSECTIONS_LIST = "result_vact_subsections_list"
}
