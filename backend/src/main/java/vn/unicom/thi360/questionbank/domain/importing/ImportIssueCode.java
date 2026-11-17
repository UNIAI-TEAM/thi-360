package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Mã lỗi/cảnh báo của một dòng nhập. Tên khớp mã {@code IMPORT_ISSUE_CODES} của mock web
 * ({@code features/question-import/types.ts}) cộng các mã mới cho cú pháp spec 4.6 và các mã
 * mức tệp của {@code files.ts} ({@code IMPORT_*}). Lỗi chặn khiến dòng (và cả bộ câu hỏi nếu
 * lỗi ở câu con) không nhập; cảnh báo cho nhập ở trạng thái Nháp (spec 4.5).
 */
public enum ImportIssueCode {
    // Mức tệp (reader ném {@link ImportFileException} mang một mã này)
    IMPORT_INVALID_FILE,
    IMPORT_MACRO_NOT_ALLOWED,
    IMPORT_EMPTY_FILE,
    IMPORT_FILE_TOO_LARGE,
    IMPORT_ROW_LIMIT_EXCEEDED,
    IMPORT_INVALID_HEADER,
    IMPORT_FORMULA_NOT_ALLOWED,

    // Ánh xạ
    IMPORT_MAPPING_INVALID,

    // Lỗi chặn theo dòng
    REQUIRED,
    TYPE_UNKNOWN,
    SUBJECT_UNKNOWN,
    GRADE_UNKNOWN,
    DIFFICULTY_UNKNOWN,
    TASK_TYPE_UNKNOWN,
    KNOWLEDGE_UNKNOWN,
    KNOWLEDGE_AMBIGUOUS,
    KNOWLEDGE_NOT_LEAF,
    TAG_UNKNOWN,
    CELL_SYNTAX,
    ANSWER_INVALID,
    FORMULA_INVALID,
    POINTS_INVALID,
    RUBRIC_INVALID,
    DUPLICATE_IN_FILE,
    IMPORT_SOURCE_TYPE_MISMATCH,
    CLOZE_MARKER_MISMATCH,
    BLANK_UNKNOWN,
    SET_PASSAGE_MISSING,
    SET_PASSAGE_INVALID,
    SET_CODE_REPEATED,
    SET_CHILD_INVALID,
    CHILD_TYPE_NOT_ALLOWED,
    UNSAFE_CONTENT,
    SAVE_VALIDATION,

    // Thiếu dữ liệu: dòng vẫn nhập ở Nháp (spec 4.5)
    ANSWER_MISSING,
    OPTIONS_MISSING,
    GRADING_GUIDE_MISSING,
    SET_NO_CHILDREN,
    KNOWLEDGE_MISSING,
    CHILD_FIELD_IGNORED,
    CHILD_SOURCE_CODE_IGNORED,
    KEEP_OPTION_ORDER_IGNORED;

    /** Mã mà API trả trong {@code ImportRowIssue.code}; giữ đúng tên enum như mock. */
    public String code() {
        return name();
    }
}
