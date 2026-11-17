package vn.unicom.thi360.questionbank.domain;

/**
 * Một lỗi kiểm tra phần thân câu hỏi: mã lỗi và đường dẫn JSON tới trường gây lỗi
 * (ví dụ {@code body.options[0].content}). Lớp ứng dụng gói các lỗi này trong một
 * {@code BusinessRuleException} 422 (spec 4.5); {@code message} là mã khóa dịch trong
 * {@code messages*.properties}.
 */
public record QuestionFieldError(String code, String path, String message) {

    static QuestionFieldError of(String code, String path) {
        return new QuestionFieldError(code, path, code);
    }
}
