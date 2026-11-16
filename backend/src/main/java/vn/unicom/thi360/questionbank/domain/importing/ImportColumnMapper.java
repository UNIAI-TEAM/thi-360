package vn.unicom.thi360.questionbank.domain.importing;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ánh xạ cột của tệp vào {@link ImportField} (mirror {@code mapping.ts} của web):
 * {@link #normalizeHeader} chuẩn hóa tên cột không phân biệt dấu/hoa thường/ký tự phân cách;
 * {@link #suggestMapping} gợi ý theo bí danh của {@link ImportDictionary} (mã trường tự là bí
 * danh, nên cột của tệp mẫu mới tự khớp); {@link #checkMapping} kiểm trường bắt buộc và trùng.
 *
 * <p>Thiếu đáp án chỉ là cảnh báo, nên không bắt buộc ánh xạ {@code correctAnswer}.
 */
public final class ImportColumnMapper {

    /** Trường bắt buộc phải có cột ánh xạ để tệp đọc được. */
    public static final Set<ImportField> REQUIRED_FIELDS =
        Set.of(ImportField.TYPE, ImportField.CONTENT, ImportField.SUBJECT, ImportField.GRADE, ImportField.DIFFICULTY);

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern D_STROKE = Pattern.compile("[Đđ]");
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private ImportColumnMapper() {}

    /**
     * Chuẩn hóa tên cột để so khớp: bỏ dấu tiếng Việt ({@code đ} thành {@code d}), chữ thường,
     * ký tự phân cách thành khoảng trắng: "Mã nguồn", "ma_nguon", "MA NGUON" → "ma nguon".
     */
    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String stripped = DIACRITICS.matcher(Normalizer.normalize(header, Normalizer.Form.NFD)).replaceAll("");
        stripped = D_STROKE.matcher(stripped).replaceAll("d");
        return SEPARATORS.matcher(stripped.toLowerCase(java.util.Locale.ROOT)).replaceAll(" ").trim();
    }

    /**
     * Ánh xạ gợi ý: tên cột khớp mã trường hoặc một bí danh; cột khớp đầu tiên nhận trường,
     * cột sau trùng trường thành {@link ColumnMapping#UNMAPPED}; tên trong
     * {@code ignoreHeaders} thành {@link ColumnMapping#IGNORE}.
     */
    public static ColumnMapping suggestMapping(List<ImportColumn> columns, ImportDictionary dictionary) {
        Map<String, ImportField> aliasToField = new LinkedHashMap<>();
        for (ImportField field : ImportField.values()) {
            List<String> names = new ArrayList<>(dictionary.aliasesOf(field).size() + 1);
            names.add(field.code());
            names.addAll(dictionary.aliasesOf(field));
            for (String alias : names) {
                String normalized = normalizeHeader(alias);
                if (!normalized.isEmpty()) {
                    aliasToField.putIfAbsent(normalized, field);
                }
            }
        }
        Set<String> ignored = new LinkedHashSet<>();
        for (String header : dictionary.ignoreHeaders()) {
            ignored.add(normalizeHeader(header));
        }
        Set<ImportField> taken = EnumSet.noneOf(ImportField.class);
        Map<String, String> mapping = new LinkedHashMap<>();
        for (ImportColumn column : columns) {
            String normalized = normalizeHeader(column.header());
            ImportField field = aliasToField.get(normalized);
            if (field != null && !taken.contains(field)) {
                taken.add(field);
                mapping.put(column.key(), field.code());
            } else if (ignored.contains(normalized)) {
                mapping.put(column.key(), ColumnMapping.IGNORE);
            } else {
                mapping.put(column.key(), ColumnMapping.UNMAPPED);
            }
        }
        return new ColumnMapping(mapping);
    }

    /**
     * Kiểm một mapping: trường bắt buộc chưa có cột, trường nhận từ hai cột trở lên, đếm số
     * cột từng trạng thái và bảng trường → khóa cột (mirror {@code checkMapping} của web).
     */
    public static MappingCheck checkMapping(List<ImportColumn> columns, ColumnMapping mapping) {
        Map<ImportField, List<String>> columnsByField = new LinkedHashMap<>();
        int unmapped = 0;
        int ignored = 0;
        for (ImportColumn column : columns) {
            String target = mapping.targetOf(column.key());
            if (ColumnMapping.UNMAPPED.equals(target)) {
                unmapped++;
            } else if (ColumnMapping.IGNORE.equals(target)) {
                ignored++;
            } else {
                ImportField field = ImportField.ofCode(target);
                if (field == null) {
                    unmapped++;
                } else {
                    columnsByField.computeIfAbsent(field, key -> new ArrayList<>()).add(column.key());
                }
            }
        }
        List<ImportField> duplicateFields = new ArrayList<>();
        Set<String> duplicateColumnKeys = new LinkedHashSet<>();
        Map<ImportField, String> columnByField = new LinkedHashMap<>();
        int mappedCount = 0;
        for (Map.Entry<ImportField, List<String>> entry : columnsByField.entrySet()) {
            List<String> keys = entry.getValue();
            mappedCount += keys.size();
            columnByField.put(entry.getKey(), keys.get(0));
            if (keys.size() > 1) {
                duplicateFields.add(entry.getKey());
                duplicateColumnKeys.addAll(keys);
            }
        }
        List<ImportField> missingRequired = new ArrayList<>();
        for (ImportField field : REQUIRED_FIELDS) {
            if (!columnsByField.containsKey(field)) {
                missingRequired.add(field);
            }
        }
        return new MappingCheck(
            missingRequired, duplicateFields, duplicateColumnKeys, mappedCount, unmapped, ignored, columnByField);
    }

    public static boolean isRequiredField(ImportField field) {
        return REQUIRED_FIELDS.contains(field);
    }
}
