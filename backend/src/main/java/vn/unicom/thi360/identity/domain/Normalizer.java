package vn.unicom.thi360.identity.domain;

import java.util.Locale;

public final class Normalizer {

    private Normalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
