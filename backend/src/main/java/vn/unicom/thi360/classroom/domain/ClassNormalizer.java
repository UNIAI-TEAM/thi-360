package vn.unicom.thi360.classroom.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ClassNormalizer {

    public static final String CODE_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]{1,29}$";
    public static final Pattern CODE_PATTERN = Pattern.compile(CODE_REGEX);

    private ClassNormalizer() {
    }

    public static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
