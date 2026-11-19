package vn.unicom.thi360.shared.mail;

import java.util.List;
import java.util.Locale;

/** Ngôn ngữ của thư: chỉ {@code vi} (mặc định) và {@code en}. */
public final class MailLocale {

    public static final String DEFAULT = "vi";
    public static final List<String> SUPPORTED = List.of("vi", "en");

    private MailLocale() {
    }

    /** {@code en-US}, {@code EN} → {@code en}; rỗng hoặc ngôn ngữ khác → {@code vi}. */
    public static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT;
        }
        String language = Locale.forLanguageTag(locale.trim().replace('_', '-')).getLanguage();
        return SUPPORTED.contains(language) ? language : DEFAULT;
    }
}
