package vn.unicom.thi360.catalog.application;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.api.CatalogGroupRefResponse;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.shared.web.LocaleConfig;

/**
 * Single helper providing all Excel column headers, sheet names, status labels,
 * and row-error messages for catalog export/import localized by request locale.
 */
@Component
public class CatalogExcelSchemaHelper {

    public static final Locale DEFAULT_LOCALE = LocaleConfig.DEFAULT_LOCALE;

    private final MessageSource messageSource;

    public CatalogExcelSchemaHelper(MessageSource messageSource) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
    }

    public static CatalogExcelSchemaHelper defaultInstance() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(LocaleConfig.DEFAULT_LOCALE);
        return new CatalogExcelSchemaHelper(source);
    }

    public String sheetName() {
        return sheetName(currentLocale());
    }

    public String sheetName(Locale locale) {
        return message(locale, "export.catalog.sheetName");
    }

    public List<String> simpleHeaders() {
        return simpleHeaders(currentLocale());
    }

    public List<String> simpleHeaders(Locale locale) {
        return List.of(
                message(locale, "export.catalog.simple.header.code"),
                message(locale, "export.catalog.simple.header.nameVi"),
                message(locale, "export.catalog.simple.header.nameEn"),
                message(locale, "export.catalog.simple.header.description"),
                message(locale, "export.catalog.simple.header.status"));
    }

    public List<String> combinationExportHeaders() {
        return combinationExportHeaders(currentLocale());
    }

    public List<String> combinationExportHeaders(Locale locale) {
        return List.of(
                message(locale, "export.catalog.combination.header.code"),
                message(locale, "export.catalog.combination.header.nameVi"),
                message(locale, "export.catalog.combination.header.nameEn"),
                message(locale, "export.catalog.combination.header.exam"),
                message(locale, "export.catalog.combination.header.subjects"),
                message(locale, "export.catalog.combination.header.sourceNote"),
                message(locale, "export.catalog.combination.header.version"),
                message(locale, "export.catalog.combination.header.status"),
                message(locale, "export.catalog.combination.header.description"));
    }

    public String treeLevelCodeHeader(String levelName) {
        return treeLevelCodeHeader(levelName, currentLocale());
    }

    public String treeLevelCodeHeader(String levelName, Locale locale) {
        return message(locale, "export.catalog.tree.header.levelCode", levelName);
    }

    public String treeLevelNameViHeader(String levelName) {
        return treeLevelNameViHeader(levelName, currentLocale());
    }

    public String treeLevelNameViHeader(String levelName, Locale locale) {
        return message(locale, "export.catalog.tree.header.levelNameVi", levelName);
    }

    public String treeLevelNameEnHeader(String levelName) {
        return treeLevelNameEnHeader(levelName, currentLocale());
    }

    public String treeLevelNameEnHeader(String levelName, Locale locale) {
        return message(locale, "export.catalog.tree.header.levelNameEn", levelName);
    }

    public String refGroupHeader(CatalogGroupRefResponse refGroup) {
        return refGroupHeader(refGroup, currentLocale());
    }

    public String refGroupHeader(CatalogGroupRefResponse refGroup, Locale locale) {
        Locale target = LocaleConfig.normalizeSupported(locale);
        if (LocaleConfig.ENGLISH_LOCALE.getLanguage().equals(target.getLanguage())
                && refGroup.nameEn() != null
                && !refGroup.nameEn().isBlank()) {
            return refGroup.nameEn();
        }
        return refGroup.nameVi();
    }

    public String formatStatus(CatalogStatus status) {
        return formatStatus(status, currentLocale());
    }

    public String formatStatus(CatalogStatus status, Locale locale) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case ACTIVE -> message(locale, "export.catalog.status.ACTIVE");
            case INACTIVE -> message(locale, "export.catalog.status.INACTIVE");
        };
    }

    public CatalogStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return CatalogStatus.ACTIVE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "active", "hoạt động", "hoat dong" -> CatalogStatus.ACTIVE;
            case "2", "inactive", "ngừng sử dụng", "ngung su dung" -> CatalogStatus.INACTIVE;
            default -> null;
        };
    }

    public String rowMessage(String code, String fallbackMessage) {
        Locale target = currentLocale();
        String resolved = messageSource.getMessage(code, null, null, target);
        return (resolved != null && !resolved.isBlank()) ? resolved : fallbackMessage;
    }

    public String message(String key, Object... args) {
        return message(currentLocale(), key, args);
    }

    public String message(Locale locale, String key, Object... args) {
        Locale target = LocaleConfig.normalizeSupported(locale);
        String resolved = messageSource.getMessage(key, args, null, target);
        return (resolved != null && !resolved.isBlank()) ? resolved : key;
    }

    public Locale currentLocale() {
        return LocaleConfig.currentLocale();
    }

    public static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("*", "").trim().toLowerCase(Locale.ROOT);
    }
}
