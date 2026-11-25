package vn.unicom.thi360.identity.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.shared.web.LocaleConfig;

final class RequestLocaleResolver {

    private RequestLocaleResolver() {
    }

    static String readCookie(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Resolves locale from cookie {@code thi360_locale}; falls back to {@code Accept-Language} only when the cookie
     * is absent. Returns {@code null} when neither provides a supported language ({@code vi} or {@code en}) so callers
     * can preserve an already-stored preference.
     */
    static String resolveCookieOrAcceptLanguage(HttpServletRequest request) {
        String cookie = readCookie(request, AuthService.LOCALE_COOKIE);
        if (cookie != null) {
            return isSupported(cookie) ? cookie : null;
        }
        return parseSupportedAcceptLanguage(request);
    }

    private static boolean isSupported(String value) {
        return "vi".equals(value) || "en".equals(value);
    }

    private static String parseSupportedAcceptLanguage(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Enumeration<String> headerValues = request.getHeaders(HttpHeaders.ACCEPT_LANGUAGE);
        if (headerValues == null || !headerValues.hasMoreElements()) {
            return null;
        }
        StringBuilder combined = new StringBuilder();
        while (headerValues.hasMoreElements()) {
            String value = headerValues.nextElement();
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String segment : value.split(",")) {
                String trimmed = segment.trim();
                if (!trimmed.isEmpty()) {
                    if (!combined.isEmpty()) {
                        combined.append(',');
                    }
                    combined.append(trimmed);
                }
            }
        }
        if (combined.isEmpty()) {
            return null;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(combined.toString()).stream()
                    .filter(range -> range.getWeight() > 0.0 && !"*".equals(range.getRange()))
                    .toList();
            if (ranges.isEmpty()) {
                return null;
            }
            Locale matched = Locale.lookup(ranges, LocaleConfig.SUPPORTED_LOCALES);
            return matched != null ? matched.getLanguage() : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
