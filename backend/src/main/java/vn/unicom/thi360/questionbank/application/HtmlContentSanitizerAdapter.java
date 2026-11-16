package vn.unicom.thi360.questionbank.application;

import org.springframework.stereotype.Component;

import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;

/**
 * Bean Spring cho {@link HtmlContentSanitizer}: một lớp lọc duy nhất là
 * {@link QuestionHtmlSanitizer} (plan B8 — không có bộ lọc thứ hai); adapter chỉ chuyển
 * {@link Options} sang tham số {@code allowImages} và map kiểu domain sang kiểu spi.
 */
@Component
public class HtmlContentSanitizerAdapter implements HtmlContentSanitizer {

    private final QuestionHtmlSanitizer sanitizer;

    public HtmlContentSanitizerAdapter(QuestionHtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public Result sanitize(String html, Options options) {
        boolean allowImages = options != null && options.allowImages();
        QuestionHtmlSanitizer.Result cleaned = sanitizer.clean(html, allowImages);
        return new Result(
            cleaned.html(),
            cleaned.violations().stream()
                .map(v -> new Violation(v.tag(), v.attribute(), v.value()))
                .toList());
    }
}
