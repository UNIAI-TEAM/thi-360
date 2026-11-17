package vn.unicom.thi360.questionbank.domain.importing;

import java.util.Locale;

/** Loại tệp nhập được chấp nhận (spec 2, 4.5: Excel và CSV). */
public enum ImportFileType {
    XLSX,
    CSV;

    /**
     * Nhận loại tệp theo đuôi tên; {@code .xlsm} và {@code .xls} không phải XLSX dùng được
     * (macro bị chặn, định dạng nhị phân cũ không hỗ trợ).
     */
    public static ImportFileType ofFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return XLSX;
        }
        if (lower.endsWith(".csv")) {
            return CSV;
        }
        return null;
    }

    /** {@code true} khi tên tệp chỉ rõ là tệp Excel có macro (lỗi riêng, không phải "sai đuôi"). */
    public static boolean isMacroFileName(String fileName) {
        return fileName != null && fileName.trim().toLowerCase(Locale.ROOT).endsWith(".xlsm");
    }
}
