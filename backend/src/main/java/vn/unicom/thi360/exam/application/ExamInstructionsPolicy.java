package vn.unicom.thi360.exam.application;

import org.springframework.stereotype.Component;

/**
 * Điểm đi qua duy nhất cho nội dung HTML {@code instructions} của bài thi (spec 5.2, D19).
 *
 * <p>Quyết định của Advisor (2026-11-16): chưa có mã sanitizer trong module exam — lưu nguyên
 * chuỗi cho tới khi {@code questionbank.spi.HtmlContentSanitizer} (BE-2a phần 1) được merge vào
 * nhánh tích hợp. Khi đó lát cắt cuối của BE-4 inject sanitizer vào đây, gọi
 * {@code sanitize(html, Options.allowImages(false))} và ném 422 {@code EXAM_INSTRUCTIONS_UNSAFE}
 * khi có {@code <img>} hoặc vi phạm; test {@code ExamApiIT#instructionsImg422} đi kèm.
 */
@Component
public class ExamInstructionsPolicy {

    /**
     * Chuẩn hóa nội dung hướng dẫn trước khi lưu. Hiện tại: lưu nguyên trạng (pass-through),
     * chuỗi trống chỉ khoảng trắng → {@code null}.
     */
    public String check(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        return html;
    }
}
