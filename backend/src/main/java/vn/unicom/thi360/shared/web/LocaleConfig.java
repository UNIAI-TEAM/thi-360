package vn.unicom.thi360.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfig {

    public static final Locale DEFAULT_LOCALE = Locale.of("vi");
    public static final Locale ENGLISH_LOCALE = Locale.of("en");
    public static final List<Locale> SUPPORTED_LOCALES = List.of(DEFAULT_LOCALE, ENGLISH_LOCALE);

    @Bean
    public LocaleResolver localeResolver() {
        return createLocaleResolver();
    }

    public static AcceptHeaderLocaleResolver createLocaleResolver() {
        SupportedAcceptHeaderLocaleResolver resolver = new SupportedAcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        return resolver;
    }

    public static Locale resolveLocale(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_LOCALE;
        }
        Enumeration<String> headerValues = request.getHeaders(HttpHeaders.ACCEPT_LANGUAGE);
        if (headerValues == null || !headerValues.hasMoreElements()) {
            return DEFAULT_LOCALE;
        }
        StringBuilder combined = new StringBuilder();
        while (headerValues.hasMoreElements()) {
            String value = headerValues.nextElement();
            if (value != null && !value.isBlank()) {
                if (!combined.isEmpty()) {
                    combined.append(',');
                }
                combined.append(value);
            }
        }
        if (combined.isEmpty()) {
            return DEFAULT_LOCALE;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(combined.toString()).stream()
                    .filter(range -> range.getWeight() > 0.0)
                    .toList();
            if (ranges.isEmpty()) {
                return DEFAULT_LOCALE;
            }
            Locale matched = Locale.lookup(ranges, SUPPORTED_LOCALES);
            return matched != null ? matched : DEFAULT_LOCALE;
        } catch (IllegalArgumentException ex) {
            return DEFAULT_LOCALE;
        }
    }

    public static Locale currentLocale() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return resolveLocale(attrs.getRequest());
        }
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        if (context != null && context.getLocale() != null) {
            return normalizeSupported(context.getLocale());
        }
        return DEFAULT_LOCALE;
    }

    public static Locale normalizeSupported(Locale candidate) {
        if (candidate != null && ENGLISH_LOCALE.getLanguage().equalsIgnoreCase(candidate.getLanguage())) {
            return ENGLISH_LOCALE;
        }
        return DEFAULT_LOCALE;
    }

    private static final class SupportedAcceptHeaderLocaleResolver extends AcceptHeaderLocaleResolver {
        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            return LocaleConfig.resolveLocale(request);
        }
    }
}
