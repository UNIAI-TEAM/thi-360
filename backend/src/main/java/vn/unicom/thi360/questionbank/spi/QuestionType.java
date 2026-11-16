package vn.unicom.thi360.questionbank.spi;

/**
 * Tám dạng câu hỏi cố định theo quyết định B6 (spec mục 1). Mã là bất biến; tên mặc định chỉ
 * dùng khi nhóm danh mục {@code QUESTION_FORMAT} chưa có giá trị cho mã đó (spec D1).
 */
public enum QuestionType {
    SINGLE_CHOICE("Trắc nghiệm một đáp án", "Single choice"),
    MULTIPLE_CHOICE("Trắc nghiệm nhiều đáp án", "Multiple choice"),
    SHORT_ANSWER("Trả lời ngắn", "Short answer"),
    CLOZE("Điền khuyết", "Cloze"),
    QUESTION_SET("Bộ câu hỏi", "Question set"),
    TRUE_FALSE("Đúng/Sai", "True/False"),
    MATCHING("Ghép đôi", "Matching"),
    ESSAY("Tự luận", "Essay");

    private final String defaultNameVi;
    private final String defaultNameEn;

    QuestionType(String defaultNameVi, String defaultNameEn) {
        this.defaultNameVi = defaultNameVi;
        this.defaultNameEn = defaultNameEn;
    }

    public String code() {
        return name();
    }

    public String defaultNameVi() {
        return defaultNameVi;
    }

    public String defaultNameEn() {
        return defaultNameEn;
    }
}
