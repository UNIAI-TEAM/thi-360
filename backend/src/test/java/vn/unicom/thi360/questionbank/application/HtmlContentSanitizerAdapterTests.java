package vn.unicom.thi360.questionbank.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer.Options;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer.Result;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;

/**
 * {@link HtmlContentSanitizer} qua spi: {@code allowImages=false} coi mọi {@code img} là vi
 * phạm (hướng dẫn làm bài, spec 5.2); {@code allowImages=true} giữ quy tắc câu hỏi (img chỉ
 * sống qua {@code data-asset-id} tra được, {@code src} viết lại); {@code script}, thuộc tính
 * sự kiện và {@code javascript:} vẫn được báo ở cả hai chế độ.
 */
class HtmlContentSanitizerAdapterTests {

    private final HtmlContentSanitizer textOnly =
        new HtmlContentSanitizerAdapter(new QuestionHtmlSanitizer(QuestionAssetResolver.none()));

    private final UUID assetId = UUID.randomUUID();
    private final String assetPath = "/api/v1/admin/question-assets/" + assetId;
    private final HtmlContentSanitizer withAssets = new HtmlContentSanitizerAdapter(
        new QuestionHtmlSanitizer(
            id -> id.equals(assetId) ? Optional.of(assetPath) : Optional.empty()));

    @Test
    void productionConstructorUsesAvailableResolverOrRejectsUnknownImages() {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        HtmlContentSanitizer absent = new HtmlContentSanitizerAdapter(
            factory.getBeanProvider(QuestionAssetResolver.class));
        String image = "<img data-asset-id=\"" + assetId + "\">";
        Result rejected = absent.sanitize(image, Options.withImages());
        assertFalse(rejected.isClean());
        assertTrue(rejected.violations().stream().anyMatch(v -> "data-asset-id".equals(v.attribute())));

        QuestionAssetResolver resolver = id -> id.equals(assetId) ? Optional.of(assetPath) : Optional.empty();
        factory.addBean("resolver", resolver);
        HtmlContentSanitizer present = new HtmlContentSanitizerAdapter(
            factory.getBeanProvider(QuestionAssetResolver.class));
        Result accepted = present.sanitize(image, Options.withImages());
        assertTrue(accepted.isClean(), () -> accepted.violations().toString());
        assertTrue(accepted.html().contains("src=\"" + assetPath + "\""));
    }

    @Test
    void documentShellAndHeadMatterAreReportedAndCleanedInBothModes() {
        String[][] cases = {
            {"<script>alert(1)</script><p>x</p>", "script"},
            {"<html><head><style>p{display:none}</style></head><body><p>x</p></body></html>", "style"},
            {"<body onload=alert(1)><p>x</p></body>", "onload"},
            {"<title><img src=x onerror=alert(1)></title><p>x</p>", "title"}
        };
        for (Options options : new Options[] {Options.textOnly(), Options.withImages()}) {
            for (String[] example : cases) {
                Result result = textOnly.sanitize(example[0], options);
                assertFalse(result.isClean(), example[0]);
                assertTrue(result.violations().stream().anyMatch(v ->
                    example[1].equals(v.tag()) || example[1].equals(v.attribute())), example[0]);
                var output = org.jsoup.Jsoup.parseBodyFragment(result.html());
                assertTrue(output.select("script, style, title, [onload], [onerror]").isEmpty(), result.html());
                assertTrue(output.select("p").stream().anyMatch(p -> p.text().equals("x")));
            }
        }
    }

    @Test
    void textOnlyRejectsImg() {
        Result result = textOnly.sanitize(
            "<p>Đọc kỹ</p><img src=\"/api/v1/admin/question-assets/x\"/>", Options.textOnly());
        assertFalse(result.isClean());
        assertTrue(result.violations().stream().anyMatch(v -> "img".equals(v.tag())));
        assertFalse(result.html().contains("img"));
        assertTrue(result.html().contains("Đọc kỹ"));
    }

    @Test
    void imgInsideAllowedTagStillViolation() {
        Result result = textOnly.sanitize(
            "<p>x <img data-asset-id=\"" + assetId + "\"/></p>", Options.textOnly());
        assertFalse(result.isClean());
        assertTrue(result.violations().stream().anyMatch(v -> "img".equals(v.tag())));
        assertFalse(result.html().contains("img"));
        assertTrue(result.html().contains("x"));
    }

    @Test
    void allowImagesKeepsResolverBehaviour() {
        Result result = withAssets.sanitize(
            "<img data-asset-id=\"" + assetId + "\" alt=\"hình\"/>", Options.withImages());
        assertTrue(result.isClean(), () -> "violations: " + result.violations());
        assertTrue(result.html().contains("src=\"" + assetPath + "\""));
    }

    @Test
    void allowImagesStillRejectsUnresolvableAsset() {
        Result result = withAssets.sanitize(
            "<img data-asset-id=\"" + UUID.randomUUID() + "\"/>", Options.withImages());
        assertFalse(result.isClean());
        assertTrue(result.violations().stream()
            .anyMatch(v -> "data-asset-id".equals(v.attribute())));
    }

    @Test
    void scriptReportedInBothModes() {
        String html = "<p>x</p><script>alert(1)</script>";
        for (Options options : new Options[] {Options.textOnly(), Options.withImages()}) {
            Result result = withAssets.sanitize(html, options);
            assertFalse(result.isClean());
            assertTrue(result.violations().stream().anyMatch(v -> "script".equals(v.tag())));
            assertFalse(result.html().contains("script"));
        }
    }

    @Test
    void eventAttributeReportedThroughSpi() {
        Result result = textOnly.sanitize(
            "<p onclick=\"alert(1)\">x</p>", Options.textOnly());
        assertFalse(result.isClean());
        assertTrue(result.violations().stream().anyMatch(v -> "onclick".equals(v.attribute())));
        assertFalse(result.html().contains("onclick"));
    }

    @Test
    void javascriptAnchorReportedThroughSpi() {
        Result result = textOnly.sanitize(
            "<p>x</p><a href=\"javascript:alert(1)\">click</a>", Options.textOnly());
        assertFalse(result.isClean());
        assertFalse(result.html().contains("javascript:"));
        assertTrue(result.html().contains("click"));
    }

    @Test
    void nullOptionsMeansTextOnly() {
        Result result = textOnly.sanitize("<img src=\"/x.png\"/>", null);
        assertFalse(result.isClean());
        assertTrue(result.violations().stream().anyMatch(v -> "img".equals(v.tag())));
    }

    @Test
    void cleanContentIsClean() {
        Result result = withAssets.sanitize(
            "<p>Đề bài <strong>đậm</strong></p>", Options.withImages());
        assertTrue(result.isClean());
    }
}
