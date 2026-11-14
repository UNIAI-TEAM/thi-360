package vn.unicom.thi360.catalog.application;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

/**
 * Single helper providing all Excel column headers, sheet names, status labels,
 * and row-error messages for catalog export/import (ready for Accept-Language localization in T19).
 */
@Component
public class CatalogExcelSchemaHelper {

    public static final Locale DEFAULT_LOCALE = Locale.of("vi");

    private final MessageSource messageSource;

    public CatalogExcelSchemaHelper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String sheetName() {
        return "DanhMuc";
    }

    public List<String> simpleHeaders() {
        return List.of("Mã*", "Tên tiếng Việt*", "Tên tiếng Anh", "Mô tả", "Trạng thái");
    }

    public List<String> combinationExportHeaders() {
        return List.of(
                "Mã",
                "Tên tiếng Việt",
                "Tên tiếng Anh",
                "Kỳ thi",
                "Các môn học",
                "Nguồn quy ước",
                "Phiên bản",
                "Trạng thái",
                "Mô tả");
    }

    public String treeLevelCodeHeader(String levelName) {
        return "Mã " + levelName;
    }

    public String treeLevelNameViHeader(String levelName) {
        return levelName + " (Tiếng Việt)";
    }

    public String treeLevelNameEnHeader(String levelName) {
        return levelName + " (Tiếng Anh)";
    }

    public String formatStatus(CatalogStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case ACTIVE -> "Hoạt động";
            case INACTIVE -> "Ngừng sử dụng";
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
        return messageSource.getMessage(code, null, fallbackMessage, DEFAULT_LOCALE);
    }

    public static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("*", "").trim().toLowerCase(Locale.ROOT);
    }
}
