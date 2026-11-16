package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer.Result;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;

/**
 * Bộ lọc HTML spec 4.2: {@code <script>}, thuộc tính sự kiện, {@code javascript:}, thẻ ngoài
 * danh sách bị loại và báo; nội dung trong danh sách đi qua nguyên trạng; {@code img} chỉ sống
 * qua {@code data-asset-id} tra được, {@code src} viết lại theo đường phục vụ thật.
 */
class QuestionHtmlSanitizerTests {

    private final QuestionHtmlSanitizer sanitizer = new QuestionHtmlSanitizer(QuestionAssetResolver.none());

    private final UUID assetId = UUID.randomUUID();
    private final String assetPath = "/api/v1/admin/question-assets/" + assetId;
    private final QuestionHtmlSanitizer withAssets = new QuestionHtmlSanitizer(
        id -> id.equals(assetId) ? Optional.of(assetPath) : Optional.empty());

    @Test
    void documentShellAndHeadMatterAreReportedAndCleanedInBothModes() {
        String[][] cases = {
            {"<script>alert(1)</script><p>x</p>", "script"},
            {"<html><head><style>p{display:none}</style></head><body><p>x</p></body></html>", "style"},
            {"<body onload=alert(1)><p>x</p></body>", "onload"},
            {"<title><img src=x onerror=alert(1)></title><p>x</p>", "title"}
        };
        for (boolean allowImages : new boolean[] {false, true}) {
            for (String[] example : cases) {
                Result result = sanitizer.clean(example[0], allowImages);
                assertFalse(result.unchanged(), example[0]);
                assertTrue(result.violations().stream().anyMatch(v ->
                    example[1].equals(v.tag()) || example[1].equals(v.attribute())), example[0]);
                var output = org.jsoup.Jsoup.parseBodyFragment(result.html());
                assertTrue(output.select("script, style, title, [onload], [onerror]").isEmpty(), result.html());
                assertEquals("x", output.selectFirst("p").text());
            }
        }
    }

    @Test
    void safeMarkupIsReturnedInCleanedForm() {
        Result result = sanitizer.clean("<P>x</P>");
        assertEquals("<p>x</p>", result.html());
    }

    @Test
    void cleanContentUnchanged() {
        String html = "<p>Đề bài <strong>đậm</strong> và <em>nghiêng</em></p><ul><li>ý 1</li></ul>";
        Result result = sanitizer.clean(html);
        assertTrue(result.unchanged(), () -> "violations: " + result.violations());
        assertEquals(html, result.html());
    }

    @Test
    void tableAndMathSpanAllowed() {
        String html = "<table><thead><tr><th colspan=\"2\">H</th></tr></thead>"
            + "<tbody><tr><td rowspan=\"2\">x</td><td><span class=\"math-inline\">\\(x^2\\)</span></td></tr></tbody></table>";
        Result result = sanitizer.clean(html);
        assertTrue(result.unchanged(), () -> "violations: " + result.violations());
    }

    @Test
    void scriptRemovedAndReported() {
        Result result = sanitizer.clean("<p>x</p><script>alert(1)</script>");
        assertFalse(result.unchanged());
        assertTrue(result.violations().stream().anyMatch(v -> "script".equals(v.tag())));
        assertFalse(result.html().contains("script"));
    }

    @Test
    void eventAttributeRemovedAndReported() {
        UUID id = UUID.randomUUID();
        QuestionHtmlSanitizer resolver = new QuestionHtmlSanitizer(
            a -> a.equals(id) ? Optional.of("/p/" + id) : Optional.empty());
        Result result = resolver.clean(
            "<img data-asset-id=\"" + id + "\" src=\"/p/" + id + "\" onerror=\"alert(1)\" alt=\"a\"/>");
        assertTrue(result.violations().stream().anyMatch(v -> "onerror".equals(v.attribute())));
        assertFalse(result.html().contains("onerror"));
    }

    @Test
    void javascriptHrefDroppedWithAnchor() {
        // Thẻ <a> không trong danh sách: bị loại cùng href javascript:.
        Result result = sanitizer.clean("<p>x</p><a href=\"javascript:alert(1)\">click</a>");
        assertFalse(result.unchanged());
        assertFalse(result.html().contains("javascript:"));
        assertTrue(result.html().contains("click"));
    }

    @Test
    void externalImgSrcRejected() {
        // src trỏ ngoài hệ thống: bị loại/báo ngay cả khi data-asset-id hợp lệ.
        Result result = withAssets.clean(
            "<img data-asset-id=\"" + assetId + "\" src=\"https://evil.example/x.png\"/>");
        assertFalse(result.unchanged());
        assertTrue(result.html().contains("src=\"" + assetPath + "\""));
    }

    @Test
    void imgSrcRewrittenFromAssetId() {
        Result result = withAssets.clean("<img data-asset-id=\"" + assetId + "\" alt=\"hình\"/>");
        assertTrue(result.unchanged(), () -> "violations: " + result.violations());
        assertTrue(result.html().contains("src=\"" + assetPath + "\""));
    }

    @Test
    void imgSrcMismatchReported() {
        Result result = withAssets.clean(
            "<img data-asset-id=\"" + assetId + "\" src=\"/api/v1/admin/question-assets/"
                + UUID.randomUUID() + "\"/>");
        assertTrue(result.violations().stream().anyMatch(v -> "src".equals(v.attribute())));
        assertTrue(result.html().contains("src=\"" + assetPath + "\""));
    }

    @Test
    void imgWithoutAssetIdRejected() {
        // img thiếu data-asset-id: vi phạm (img bị bỏ bởi cleaner vì thuộc tính cần thiết… vẫn giữ thẻ);
        // lời gọi map sang QUESTION_CONTENT_UNSAFE.
        Result result = sanitizer.clean("<img src=\"/api/v1/admin/question-assets/x\"/>");
        assertTrue(result.violations().stream().anyMatch(v -> "data-asset-id".equals(v.attribute())));
    }

    @Test
    void imgUnknownAssetRejected() {
        Result result = sanitizer.clean("<img data-asset-id=\"" + UUID.randomUUID() + "\"/>");
        assertTrue(result.violations().stream().anyMatch(v -> "data-asset-id".equals(v.attribute())));
    }

    @Test
    void styleAndUnknownTagsRemoved() {
        Result result = sanitizer.clean(
            "<p style=\"color:red\">x</p><iframe src=\"x\"></iframe><div>d</div>");
        assertTrue(result.violations().stream().anyMatch(v -> "style".equals(v.attribute())));
        assertTrue(result.violations().stream().anyMatch(v -> "iframe".equals(v.tag())));
        assertTrue(result.violations().stream().anyMatch(v -> "div".equals(v.tag())));
        assertFalse(result.html().contains("iframe"));
        // Chữ trong thẻ bị bỏ vẫn giữ (Cleaner giữ text node).
        assertTrue(result.html().contains("d"));
    }

    @Test
    void spanWithForeignClassRemoved() {
        Result result = sanitizer.clean("<p><span class=\"fancy\">x</span></p>");
        assertTrue(result.violations().stream().anyMatch(v -> "class".equals(v.attribute())));
        assertFalse(result.html().contains("fancy"));
    }

    @Test
    void nullAndBlankAreSafe() {
        assertTrue(sanitizer.clean(null).unchanged());
        assertTrue(sanitizer.clean("  ").unchanged());
    }

    @Test
    void textOfExtractsPlainText() {
        assertEquals("A cộng B", QuestionHtmlSanitizer.textOf("<p>A <strong>cộng</strong> B</p>"));
        assertEquals("", QuestionHtmlSanitizer.textOf(null));
    }
}
