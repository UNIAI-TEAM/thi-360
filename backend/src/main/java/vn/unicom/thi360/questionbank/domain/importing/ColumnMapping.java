package vn.unicom.thi360.questionbank.domain.importing;

import java.util.Map;

/**
 * Ánh xạ cột của tệp vào trường hệ thống (mirror {@code ColumnMapping} của web): khóa cột
 * ({@code c0}…) → mã trường camelCase ({@link ImportField#code()}), {@link #IGNORE} hoặc
 * {@link #UNMAPPED}. Giữ ở dạng chuỗi để khớp trực tiếp DTO của API.
 */
public record ColumnMapping(Map<String, String> byColumnKey) {

    public static final String IGNORE = "IGNORE";
    public static final String UNMAPPED = "UNMAPPED";

    public ColumnMapping {
        byColumnKey = byColumnKey == null ? Map.of() : Map.copyOf(byColumnKey);
    }

    public static ColumnMapping of(Map<String, String> byColumnKey) {
        return new ColumnMapping(byColumnKey);
    }

    /** Giá trị ánh xạ của một cột; {@link #UNMAPPED} khi khóa không có trong bảng. */
    public String targetOf(String columnKey) {
        return byColumnKey.getOrDefault(columnKey, UNMAPPED);
    }

    /** Trường mà cột ánh xạ vào; {@code null} khi bỏ qua hoặc chưa ánh xạ/giá trị lạ. */
    public ImportField fieldOf(String columnKey) {
        return ImportField.ofCode(targetOf(columnKey));
    }
}
