package vn.unicom.thi360.attempt.application;

import java.util.Locale;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Shared HTML helper populating admin asset {@code src} attributes on {@code img[data-asset-id]} tags. */
final class AdminHtmlAssetHelper {

    static final String ADMIN_ASSET_PREFIX = "/api/v1/admin/question-assets/";

    private AdminHtmlAssetHelper() {}

    static String ensureAdminImgSrc(String html) {
        if (html == null || html.isEmpty() || !html.toLowerCase(Locale.ROOT).contains("data-asset-id")) {
            return html;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        for (Element img : doc.select("img[data-asset-id]")) {
            if (img.attr("src").trim().isEmpty()) {
                UUID assetId = parseUuidOrNull(img.attr("data-asset-id").trim());
                if (assetId != null) {
                    img.attr("src", ADMIN_ASSET_PREFIX + assetId);
                }
            }
        }
        return doc.body().html();
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
