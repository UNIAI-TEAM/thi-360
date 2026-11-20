package vn.unicom.thi360.attempt.application;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Rewrites admin asset URLs in student-facing question HTML fragments to attempt-scoped asset URLs
 * (AC-25, AC-27).
 */
@Component
public class AttemptQuestionSanitizer {

    public static final String ADMIN_ASSET_PREFIX = "/api/v1/admin/question-assets/";

    private static final Pattern ADMIN_ASSET_URL_PATTERN =
            Pattern.compile(
                    "(?:https?://[^/\"'\\s<>]+)?/api/v1/admin/question-assets/([0-9a-fA-F-]+)",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Rewrites relative or absolute {@code /api/v1/admin/question-assets/{assetId}} URLs in HTML
     * attributes and {@code <img data-asset-id="{uuid}">} tags to
     * {@code /api/v1/me/attempts/{attemptId}/assets/{assetId}}.
     */
    public String rewriteAssetUrls(UUID attemptId, String html) {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        if (html == null || html.isEmpty()) {
            return html;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains("<img") && !lower.contains(ADMIN_ASSET_PREFIX)) {
            return html;
        }
        String targetPrefix = "/api/v1/me/attempts/" + attemptId + "/assets/";
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        for (Element img : doc.select("img")) {
            String rawSrc = img.attr("src");
            String src = rawSrc.trim();
            if (!src.isEmpty()) {
                String rewrittenSrc = rewriteAdminUrlValue(src, targetPrefix);
                if (!rewrittenSrc.equals(rawSrc)) {
                    img.attr("src", rewrittenSrc);
                }
            } else if (img.hasAttr("data-asset-id")) {
                UUID assetId = parseUuidOrNull(img.attr("data-asset-id").trim());
                if (assetId != null) {
                    img.attr("src", targetPrefix + assetId);
                }
            }
        }
        for (Element element : doc.body().getAllElements()) {
            for (Attribute attr : element.attributes()) {
                String trimmed = attr.getValue().trim();
                if (trimmed.toLowerCase(Locale.ROOT).contains(ADMIN_ASSET_PREFIX)) {
                    String rewritten = rewriteAdminUrlValue(trimmed, targetPrefix);
                    if (!rewritten.equals(trimmed)) {
                        element.attr(attr.getKey(), rewritten);
                    }
                }
            }
        }
        return doc.body().html();
    }

    private static String rewriteAdminUrlValue(String trimmedUrl, String targetPrefix) {
        Matcher matcher = ADMIN_ASSET_URL_PATTERN.matcher(trimmedUrl);
        if (!matcher.lookingAt()) {
            return trimmedUrl;
        }
        return targetPrefix + matcher.group(1) + trimmedUrl.substring(matcher.end());
    }

    private static UUID parseUuidOrNull(String candidate) {
        if (candidate.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
