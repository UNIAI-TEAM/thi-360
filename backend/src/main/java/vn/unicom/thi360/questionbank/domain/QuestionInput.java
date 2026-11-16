package vn.unicom.thi360.questionbank.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Đầu vào kiểm tra của một câu hỏi (phần lớp domain nhìn thấy của lệnh lưu / đánh dấu Sẵn sàng;
 * spec mục 4.3). HTML trong {@code content}, {@code solution}, {@code commonMistakes} và trong
 * thân câu đã được lọc trước bởi {@link QuestionHtmlSanitizer}.
 *
 * @param points điểm tối đa của câu (mặc định 1, &gt; 0; với Bộ câu hỏi và Điền khuyết là tổng
 *               điểm thành phần). Dùng để so với tổng điểm mệnh đề, tổng điểm cặp, tổng tiêu chí.
 * @param content đề bài dạng HTML (đã lọc)
 * @param solution lời giải (HTML, nullable)
 * @param commonMistakes các lỗi sai thường gặp (HTML, có thể rỗng)
 * @param attachmentIds id tệp đính kèm của câu (spec 9.2); phải tra được qua resolver
 */
public record QuestionInput(
    QuestionType type,
    QuestionBody body,
    BigDecimal points,
    String content,
    String solution,
    List<String> commonMistakes,
    List<UUID> attachmentIds) {

    public QuestionInput {
        commonMistakes = commonMistakes == null ? List.of() : List.copyOf(commonMistakes);
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }
}
