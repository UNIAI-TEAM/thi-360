package vn.unicom.thi360.questionbank.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;

/**
 * Bean Spring cho {@link HtmlContentSanitizer}: một lớp lọc duy nhất là
 * {@link QuestionHtmlSanitizer} (plan B8 — không có bộ lọc thứ hai); adapter chỉ chuyển
 * {@link Options} sang tham số {@code allowImages} và map kiểu domain sang kiểu spi.
 *
 * <p>{@link QuestionAssetResolver} chưa có bean (bản thật do BE-2b cài): khi vắng mặt dùng
 * {@link QuestionAssetResolver#none()} — mọi {@code data-asset-id} bị từ chối, giữ đúng ranh
 * giới của BE-2a.
 */
@Component
public class HtmlContentSanitizerAdapter implements HtmlContentSanitizer {

    private final QuestionHtmlSanitizer sanitizer;

    @Autowired
    public HtmlContentSanitizerAdapter(ObjectProvider<QuestionAssetResolver> assetResolver) {
        this(new QuestionHtmlSanitizer(assetResolver.getIfAvailable(QuestionAssetResolver::none)));
    }

    HtmlContentSanitizerAdapter(QuestionHtmlSanitizer sanitizer) {
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
