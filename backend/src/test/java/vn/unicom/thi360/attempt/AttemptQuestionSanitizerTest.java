package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AttemptQuestionSanitizer;

/** Unit tests for {@link AttemptQuestionSanitizer#rewriteAssetUrls} (R1-17, R1-18, AC-27). */
class AttemptQuestionSanitizerTest {

    private final AttemptQuestionSanitizer sanitizer = new AttemptQuestionSanitizer();
    private final UUID attemptId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID assetId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void rewritesUppercaseImgWithDataAssetIdWhenSrcIsMissingOrBlank() {
        String missingSrc = "<p>Prompt <IMG data-asset-id=\"" + assetId + "\"></p>";
        String blankSrc = "<p>Prompt <IMG src=\"   \" data-asset-id=\" " + assetId + " \"></p>";

        String expected = "/api/v1/me/attempts/" + attemptId + "/assets/" + assetId;
        assertThat(sanitizer.rewriteAssetUrls(attemptId, missingSrc)).contains("src=\"" + expected + "\"");
        assertThat(sanitizer.rewriteAssetUrls(attemptId, blankSrc)).contains("src=\"" + expected + "\"");
    }

    @Test
    void rewritesRelativeAndAbsoluteAdminUrlsWithWhitespaceTrimming() {
        String html = "<p>"
                + "<img src=\"  /api/v1/admin/question-assets/" + assetId + "  \">"
                + "<img src=\"https://admin.thi360.example/api/v1/admin/question-assets/" + assetId + "\">"
                + "<a href=\"http://localhost:8080/api/v1/admin/question-assets/" + assetId + "\">link</a>"
                + "</p>";

        String rewritten = sanitizer.rewriteAssetUrls(attemptId, html);
        String expected = "/api/v1/me/attempts/" + attemptId + "/assets/" + assetId;

        assertThat(rewritten).doesNotContain("/api/v1/admin/");
        assertThat(rewritten).doesNotContain("admin.thi360.example");
        assertThat(rewritten).doesNotContain("localhost:8080");
        assertThat(rewritten).contains("<img src=\"" + expected + "\">");
        assertThat(rewritten).contains("<a href=\"" + expected + "\">link</a>");
    }

    @Test
    void ignoresMalformedNonUuidDataAssetId() {
        String missingSrc = "<p><IMG data-asset-id=\"../etc/passwd\"><img data-asset-id=\"not-a-uuid\"></p>";
        String rewritten = sanitizer.rewriteAssetUrls(attemptId, missingSrc);
        assertThat(rewritten).doesNotContain("/api/v1/me/attempts/");
        assertThat(rewritten).doesNotContain("src=");

        String blankSrc = "<p><img src=\"\" data-asset-id=\"not-a-uuid\"></p>";
        assertThat(sanitizer.rewriteAssetUrls(attemptId, blankSrc)).isEqualTo(blankSrc);
    }

    @Test
    void preservesNonUrlProseOutsideAttributes() {
        String prose = "<p>Example admin endpoint /api/v1/admin/question-assets/" + assetId
                + " in explanation</p><img src=\"/api/v1/admin/question-assets/" + assetId + "\">";

        String rewritten = sanitizer.rewriteAssetUrls(attemptId, prose);
        String expected = "/api/v1/me/attempts/" + attemptId + "/assets/" + assetId;

        assertThat(rewritten).contains("Example admin endpoint /api/v1/admin/question-assets/" + assetId + " in explanation");
        assertThat(rewritten).contains("<img src=\"" + expected + "\">");
    }
}
