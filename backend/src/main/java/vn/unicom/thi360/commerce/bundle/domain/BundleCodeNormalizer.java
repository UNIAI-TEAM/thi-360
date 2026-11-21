package vn.unicom.thi360.commerce.bundle.domain;

import java.util.Locale;

/** Normalizes bundle codes for case-insensitive uniqueness comparisons (AC-5). */
public final class BundleCodeNormalizer {

    private BundleCodeNormalizer() {
    }

    public static String normalize(String code) {
        return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
    }
}
