package vn.unicom.thi360.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

@Component
public class AppMessages {

    private static final MessageSource FALLBACK_SOURCE = createFallbackMessageSource();

    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public AppMessages(MessageSource messageSource, LocaleResolver localeResolver) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
        this.localeResolver = Objects.requireNonNull(localeResolver, "localeResolver must not be null");
    }

    public static AppMessages defaultInstance() {
        return new AppMessages(FALLBACK_SOURCE, LocaleConfig.createLocaleResolver());
    }

    public String get(String key, Object... args) {
        return get(currentLocale(), key, args);
    }

    public String get(Locale locale, String key, Object... args) {
        Locale target = LocaleConfig.normalizeSupported(locale);
        String resolved = messageSource.getMessage(key, args, null, target);
        return (resolved != null && !resolved.isBlank()) ? resolved : key;
    }

    public String getOrNull(Locale locale, String key, Object... args) {
        Locale target = LocaleConfig.normalizeSupported(locale);
        String resolved = messageSource.getMessage(key, args, null, target);
        return (resolved != null && !resolved.isBlank()) ? resolved : null;
    }

    public Locale currentLocale() {
        return LocaleConfig.currentLocale();
    }

    public Locale resolveLocale(HttpServletRequest request) {
        return localeResolver.resolveLocale(request);
    }

    private static MessageSource createFallbackMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(LocaleConfig.DEFAULT_LOCALE);
        return source;
    }
}
