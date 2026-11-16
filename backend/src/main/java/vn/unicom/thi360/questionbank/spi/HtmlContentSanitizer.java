package vn.unicom.thi360.questionbank.spi;

import java.util.List;

/**
 * Lọc HTML do người dùng nhập theo danh sách cho phép của spec mục 4.2 (plan B6: jsoup).
 * Dùng chung cho các module ngoài {@code questionbank} (ví dụ {@code exam} lọc hướng dẫn làm
 * bài với {@link Options#textOnly()}, spec 5.2); một triển khai duy nhất nằm trong
 * {@code questionbank} (plan B8).
 *
 * <p>Không ném lỗi: mọi vấn đề nằm trong {@link Result#violations()}; lời gọi quyết định cách
 * xử lý (questionbank map sang 422 {@code QUESTION_CONTENT_UNSAFE}, exam map sang 422
 * {@code EXAM_INSTRUCTIONS_UNSAFE}). Các kiểu trả về là kiểu của spi, không lộ kiểu domain.
 */
public interface HtmlContentSanitizer {

    /**
     * Tùy chọn lọc một trường HTML.
     *
     * @param allowImages {@code true} giữ {@code img} theo quy tắc câu hỏi (bắt buộc
     *                    {@code data-asset-id} tra được, {@code src} viết lại theo đường phục
     *                    vụ thật); {@code false} coi mọi {@code img} là vi phạm
     */
    record Options(boolean allowImages) {

        /** Nội dung chỉ chữ: mọi {@code img} là vi phạm (hướng dẫn làm bài của bài thi). */
        public static Options textOnly() {
            return new Options(false);
        }

        /** Nội dung câu hỏi: {@code img} được giữ theo quy tắc tài nguyên. */
        public static Options withImages() {
            return new Options(true);
        }
    }

    /**
     * Phần nội dung bị loại hoặc viết lại trong một lần lọc.
     *
     * @param tag tên thẻ liên quan (chữ thường)
     * @param attribute tên thuộc tính bị loại/sửa; {@code null} khi cả thẻ bị loại
     * @param value giá trị đầu vào của thuộc tính; {@code null} khi không áp dụng
     */
    record Violation(String tag, String attribute, String value) {
    }

    /**
     * Kết quả lọc một trường HTML.
     *
     * @param html HTML sau khi lọc (và viết lại {@code src} của {@code img} khi được phép)
     * @param violations phần bị bỏ/sửa; rỗng nghĩa là nội dung đã an toàn nguyên trạng
     */
    record Result(String html, List<Violation> violations) {

        public Result {
            violations = List.copyOf(violations);
        }

        /** {@code true} khi nội dung an toàn nguyên trạng (spec: lưu như cũ). */
        public boolean isClean() {
            return violations.isEmpty();
        }
    }

    /**
     * Lọc một trường HTML. {@code options == null} được hiểu là {@link Options#textOnly()}
     * (hạn chế hơn).
     */
    Result sanitize(String html, Options options);
}
