package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Trường của hệ thống mà một cột trong tệp nhập có thể ánh xạ vào. Tên trường là mã camelCase
 * theo cột của spec 4.6; {@code OPTION_A}..{@code OPTION_D} là bốn cột phương án riêng của tệp
 * mẫu cũ (tương đương cột {@code options} nhiều dòng).
 */
public enum ImportField {
    SOURCE_CODE("sourceCode"),
    TYPE("type"),
    CONTENT("content"),
    OPTION_A("optionA"),
    OPTION_B("optionB"),
    OPTION_C("optionC"),
    OPTION_D("optionD"),
    CORRECT_ANSWER("correctAnswer"),
    SOLUTION("solution"),
    SUBJECT("subject"),
    GRADE("grade"),
    DIFFICULTY("difficulty"),
    KNOWLEDGE("knowledge"),
    TAGS("tags"),
    SET_CODE("setCode"),
    RUBRIC("rubric"),
    POINTS("points"),
    OPTIONS("options"),
    MATCH_RIGHT("matchRight"),
    SCORING("scoring"),
    INPUT_HINT("inputHint"),
    GRADING_GUIDE("gradingGuide"),
    COMMON_MISTAKES("commonMistakes"),
    TASK_TYPE("taskType"),
    KEEP_OPTION_ORDER("keepOptionOrder");

    private final String code;

    ImportField(String code) {
        this.code = code;
    }

    /** Mã trường đúng như spec 4.6 / mock {@code IMPORT_FIELDS} (giá trị lưu trong mapping). */
    public String code() {
        return code;
    }

    /** Tra ngược từ mã trường; {@code null} khi không có. */
    public static ImportField ofCode(String code) {
        for (ImportField field : values()) {
            if (field.code.equals(code)) {
                return field;
            }
        }
        return null;
    }
}
