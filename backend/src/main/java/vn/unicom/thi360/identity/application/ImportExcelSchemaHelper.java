package vn.unicom.thi360.identity.application;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.domain.ImportRowStatus;

/**
 * Single locale-aware helper for bulk account import/export Excel headers, labels,
 * and row validation messages.
 */
@Component
public class ImportExcelSchemaHelper {

    public static final Locale DEFAULT_LOCALE = Locale.of("vi");

    public enum ImportColumn {
        USERNAME("IMPORT_EXCEL.HEADER_USERNAME", "Tên đăng nhập", "Tên đăng nhập*"),
        PASSWORD("IMPORT_EXCEL.HEADER_PASSWORD", "Mật khẩu", "Mật khẩu"),
        FULL_NAME("IMPORT_EXCEL.HEADER_FULL_NAME", "Họ và tên", "Họ và tên*"),
        EMAIL("IMPORT_EXCEL.HEADER_EMAIL", "Email", "Email"),
        PHONE("IMPORT_EXCEL.HEADER_PHONE", "Số điện thoại", "Số điện thoại"),
        CLASS_CODE("IMPORT_EXCEL.HEADER_CLASS_CODE", "Mã lớp", "Mã lớp");

        private final String headerKey;
        private final String fallbackLabel;
        private final String fallbackHeader;

        ImportColumn(String headerKey, String fallbackLabel, String fallbackHeader) {
            this.headerKey = headerKey;
            this.fallbackLabel = fallbackLabel;
            this.fallbackHeader = fallbackHeader;
        }

        public String headerKey() {
            return headerKey;
        }

        public String fallbackLabel() {
            return fallbackLabel;
        }

        public String fallbackHeader() {
            return fallbackHeader;
        }
    }

    private final MessageSource messageSource;

    public ImportExcelSchemaHelper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String templateSheetName(Locale locale) {
        return message("IMPORT_EXCEL.SHEET_TEMPLATE", "DanhSachTaiKhoan", locale);
    }

    public String resultSheetName(Locale locale) {
        return message("IMPORT_EXCEL.SHEET_RESULT", "KetQuaCapTaiKhoan", locale);
    }

    public List<String> templateHeaders(Locale locale) {
        return List.of(
                message(ImportColumn.USERNAME.headerKey(), ImportColumn.USERNAME.fallbackHeader(), locale),
                message(ImportColumn.PASSWORD.headerKey(), ImportColumn.PASSWORD.fallbackHeader(), locale),
                message(ImportColumn.FULL_NAME.headerKey(), ImportColumn.FULL_NAME.fallbackHeader(), locale),
                message(ImportColumn.EMAIL.headerKey(), ImportColumn.EMAIL.fallbackHeader(), locale),
                message(ImportColumn.PHONE.headerKey(), ImportColumn.PHONE.fallbackHeader(), locale),
                message(ImportColumn.CLASS_CODE.headerKey(), ImportColumn.CLASS_CODE.fallbackHeader(), locale));
    }

    public String exampleNoteHeader(Locale locale) {
        return message("IMPORT_EXCEL.HEADER_EXAMPLE_NOTE", "Ghi chú", locale);
    }

    public String exampleNoteText(Locale locale) {
        return message(
                "IMPORT_EXCEL.EXAMPLE_NOTE",
                "Dòng ví dụ (có thể xóa). Mật khẩu: để trống thì hệ thống sinh tự động; nếu tự nhập cần dài 8-64 ký tự gồm chữ hoa, thường, số, ký tự đặc biệt.",
                locale);
    }

    public String errorColumnHeader(Locale locale) {
        return message("IMPORT_EXCEL.HEADER_ERROR", "Lỗi", locale);
    }

    public List<String> resultHeaders(Locale locale) {
        return List.of(
                columnLabel(ImportColumn.USERNAME, locale),
                columnLabel(ImportColumn.FULL_NAME, locale),
                columnLabel(ImportColumn.EMAIL, locale),
                columnLabel(ImportColumn.PHONE, locale),
                columnLabel(ImportColumn.CLASS_CODE, locale),
                generatedPasswordHeader(locale),
                statusHeader(locale),
                failureReasonHeader(locale));
    }

    public String generatedPasswordHeader(Locale locale) {
        return message("IMPORT_EXCEL.HEADER_GENERATED_PASSWORD", "Mật khẩu được cấp", locale);
    }

    public String statusHeader(Locale locale) {
        return message("IMPORT_EXCEL.HEADER_STATUS", "Trạng thái", locale);
    }

    public String failureReasonHeader(Locale locale) {
        return message("IMPORT_EXCEL.HEADER_FAILURE_REASON", "Nguyên nhân", locale);
    }

    public String columnLabel(ImportColumn column, Locale locale) {
        String rawHeader = message(column.headerKey(), column.fallbackHeader(), locale);
        return rawHeader.replace("*", "").trim();
    }

    public String statusLabel(ImportRowStatus status, Locale locale) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CREATED -> message("IMPORT_EXCEL.STATUS_CREATED", "Thành công", locale);
            case FAILED -> message("IMPORT_EXCEL.STATUS_FAILED", "Thất bại", locale);
            case VALID -> message("IMPORT_EXCEL.STATUS_VALID", "Hợp lệ", locale);
            case INVALID -> message("IMPORT_EXCEL.STATUS_INVALID", "Lỗi", locale);
        };
    }

    public String rowErrorMessage(String errorCode, String columnLabel, Locale locale) {
        String key = "IMPORT_ROW." + errorCode;
        Object[] args = columnLabel == null ? null : new Object[] {columnLabel};
        return messageSource.getMessage(key, args, errorCode, effectiveLocale(locale));
    }

    public String message(String key, String fallback, Locale locale) {
        return messageSource.getMessage(key, null, fallback, effectiveLocale(locale));
    }

    public static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("*", "").trim().toLowerCase(Locale.ROOT);
    }

    public ImportColumn matchColumn(String rawHeader) {
        String norm = normalizeHeader(rawHeader);
        if (norm.isEmpty()) {
            return null;
        }
        return switch (norm) {
            case "tên đăng nhập", "ten dang nhap", "username" -> ImportColumn.USERNAME;
            case "mật khẩu", "mat khau", "password" -> ImportColumn.PASSWORD;
            case "họ và tên", "ho va ten", "họ tên", "ho ten", "full name", "fullname" -> ImportColumn.FULL_NAME;
            case "email" -> ImportColumn.EMAIL;
            case "số điện thoại", "so dien thoai", "điện thoại", "dien thoai", "phone" -> ImportColumn.PHONE;
            case "mã lớp", "ma lop", "class code", "classcode" -> ImportColumn.CLASS_CODE;
            default -> null;
        };
    }

    public boolean isErrorColumnHeader(String rawHeader) {
        String norm = normalizeHeader(rawHeader);
        return "lỗi".equals(norm) || "loi".equals(norm) || "error".equals(norm) || "errors".equals(norm);
    }

    private static Locale effectiveLocale(Locale locale) {
        return locale == null ? DEFAULT_LOCALE : locale;
    }
}
