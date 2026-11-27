package vn.unicom.thi360.feature.question.ui

object QuestionTestTags {
    const val FRAME_ROOT = "question_frame_root"
    const val FRAME_TITLE = "question_frame_title"
    const val FRAME_TYPE_CHIP = "question_frame_type_chip"
    const val FRAME_MANUAL_CHIP = "question_frame_manual_chip"
    const val FRAME_OUTCOME_CHIP = "question_frame_outcome_chip"
    const val FRAME_POINTS = "question_frame_points"
    const val FRAME_FLAG_BUTTON = "question_frame_flag_button"
    const val FRAME_SOLUTION_BLOCK = "question_frame_solution_block"
    const val FRAME_ATTACHMENTS = "question_frame_attachments"

    fun choiceOption(optionId: String): String = "question_choice_option_$optionId"
    const val SHORT_ANSWER_INPUT = "question_short_answer_input"
    const val SHORT_ANSWER_REVIEW_VALUE = "question_short_answer_review_value"
    const val NUMERIC_KEY_ROW = "question_numeric_key_row"
    fun numericKey(id: String): String = "question_numeric_key_$id"
    const val SYMBOL_BAR = "question_symbol_bar"
    fun symbolKey(id: String): String = "question_symbol_key_$id"

    fun clozeBlank(blankId: String): String = "question_cloze_blank_$blankId"
    const val CLOZE_COUNTER = "question_cloze_counter"

    fun tfRow(statementId: String): String = "question_tf_row_$statementId"
    fun tfTrue(statementId: String): String = "question_tf_true_$statementId"
    fun tfFalse(statementId: String): String = "question_tf_false_$statementId"

    const val MATCH_COUNTER = "question_match_counter"
    fun matchSelector(leftId: String): String = "question_match_selector_$leftId"
    fun matchClear(leftId: String): String = "question_match_clear_$leftId"
    const val MATCH_PICKER_DIALOG = "question_match_picker_dialog"
    fun matchPickerOption(rightId: String): String = "question_match_picker_option_$rightId"

    const val ESSAY_TOOLBAR = "question_essay_toolbar"
    const val ESSAY_BTN_BOLD = "question_essay_btn_bold"
    const val ESSAY_BTN_ITALIC = "question_essay_btn_italic"
    const val ESSAY_BTN_BULLET = "question_essay_btn_bullet"
    const val ESSAY_BTN_ORDERED = "question_essay_btn_ordered"
    const val ESSAY_BTN_UNDO = "question_essay_btn_undo"
    const val ESSAY_BTN_REDO = "question_essay_btn_redo"
    const val ESSAY_EDITOR = "question_essay_editor"
    const val ESSAY_LOADING = "question_essay_loading"
    const val ESSAY_WORD_COUNT = "question_essay_word_count"
    const val ESSAY_WORD_STATUS = "question_essay_word_status"
    const val ESSAY_REVIEW_BODY = "question_essay_review_body"
    const val ESSAY_REVIEW_TOGGLE = "question_essay_review_toggle"
    const val ESSAY_CRITERIA_TABLE = "question_essay_criteria_table"
    const val ESSAY_PENDING_NOTE = "question_essay_pending_note"

    const val SET_ROOT = "question_set_root"
    const val SET_SINGLE_COLUMN = "question_set_single_column"
    const val SET_TWO_COLUMN = "question_set_two_column"
    const val SET_PASSAGE_PANE = "question_set_passage_pane"
    const val SET_CHILDREN_PANE = "question_set_children_pane"

    const val REVIEW_TABLE_STACKED = "question_review_table_stacked"
    const val REVIEW_TABLE_WIDE = "question_review_table_wide"
}
