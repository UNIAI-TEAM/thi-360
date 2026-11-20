package vn.unicom.thi360.commerce.promotion.domain;

import java.util.Locale;

/** Normalizes voucher codes for case-insensitive trimmed uniqueness and lookup (RULES §4, TM 7.1). */
public final class PromotionCodeNormalizer {

    private PromotionCodeNormalizer() {
    }

    public static String normalize(String code) {
        return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
    }
}
