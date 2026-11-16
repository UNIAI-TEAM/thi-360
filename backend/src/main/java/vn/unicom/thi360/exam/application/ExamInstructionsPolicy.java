package vn.unicom.thi360.exam.application;

import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer.Options;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Điểm đi qua duy nhất cho nội dung HTML {@code instructions} của bài thi (spec 5.2, D19).
 *
 * <p>Quyết định của Advisor (2026-11-16): module exam không chứa mã sanitizer — chính sách này
 * inject {@code questionbank.spi.HtmlContentSanitizer} (BE-2a phần 1, bean
 * {@code HtmlContentSanitizerAdapter}) và gọi {@code sanitize} với ảnh bị cấm
 * ({@link Options#textOnly()}). {@code <img>} hoặc bất kỳ vi phạm nào → 422
 * {@code EXAM_INSTRUCTIONS_UNSAFE}; HTML đã lọc được lưu.
 */
@Component
public class ExamInstructionsPolicy {

    private final HtmlContentSanitizer sanitizer;
    private final AppMessages messages;

    public ExamInstructionsPolicy(HtmlContentSanitizer sanitizer, AppMessages messages) {
        this.sanitizer = sanitizer;
        this.messages = messages;
    }

    /**
     * Lọc nội dung hướng dẫn trước khi lưu: chuỗi trống chỉ khoảng trắng → {@code null};
     * có vi phạm → 422 {@code EXAM_INSTRUCTIONS_UNSAFE}; ngược lại trả HTML đã lọc.
     */
    public String check(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        HtmlContentSanitizer.Result result = sanitizer.sanitize(html, Options.textOnly());
        if (!result.isClean()) {
            throw new BusinessRuleException(ExamErrorCodes.INSTRUCTIONS_UNSAFE,
                    messages.get(ExamErrorCodes.INSTRUCTIONS_UNSAFE));
        }
        return result.html();
    }
}
