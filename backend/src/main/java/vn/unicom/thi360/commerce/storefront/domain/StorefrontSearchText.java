package vn.unicom.thi360.commerce.storefront.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises text for {@code storefront_product.search_text} and for the {@code q} of the public API exactly the way
 * BE spec D16 does for the question bank (lower case, accents removed, {@code đ} written {@code d}, runs of blanks
 * folded). It is a copy on purpose: {@code commerce} must not import {@code questionbank/domain} (RULES 4).
 */
public final class StorefrontSearchText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private StorefrontSearchText() { }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutD = text.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(withoutD, Normalizer.Form.NFD);
        String stripped = MARKS.matcher(decomposed).replaceAll("");
        String lowered = stripped.toLowerCase(Locale.ROOT);
        return WHITESPACE.matcher(lowered).replaceAll(" ").trim();
    }

    /** The search text of a product: its name and its code, normalised. */
    public static String of(String name, String code) {
        return normalize((name == null ? "" : name) + " " + (code == null ? "" : code));
    }

    /** A {@code LIKE} pattern that matches the normalised text anywhere; {@code %}, {@code _} and {@code \} are literal. */
    public static String containsPattern(String normalizedQuery) {
        String escaped = normalizedQuery.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
