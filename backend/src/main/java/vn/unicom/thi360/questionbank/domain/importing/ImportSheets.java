package vn.unicom.thi360.questionbank.domain.importing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuyển một bảng đã đọc (dòng 1 là tên cột) thành {@link ImportSheet}: khóa cột {@code c0},
 * {@code c1}…, bỏ dòng trống hoàn toàn nhưng giữ số dòng gốc (mirror {@code sheetFromTable}
 * của web).
 */
public final class ImportSheets {

    private ImportSheets() {}

    public static ImportSheet fromTable(
        ImportFileType fileType, String sheetName, List<List<String>> table, ImportLimits limits)
        throws ImportFileException {

        List<String> header = table.isEmpty() ? List.of() : table.get(0);
        if (header.isEmpty() || header.stream().allMatch(value -> value == null || value.isBlank())) {
            throw new ImportFileException(ImportIssueCode.IMPORT_INVALID_HEADER, "Thiếu dòng tiêu đề");
        }
        List<String> keys = new ArrayList<>(header.size());
        for (int i = 0; i < header.size(); i++) {
            keys.add("c" + i);
        }

        List<ImportSheetRow> rows = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            List<String> values = table.get(i);
            boolean blank = true;
            Map<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < keys.size(); c++) {
                String value = c < values.size() && values.get(c) != null ? values.get(c) : "";
                if (!value.isBlank()) {
                    blank = false;
                }
                cells.put(keys.get(c), value);
            }
            if (blank) {
                continue;
            }
            if (rows.size() >= limits.maxRows()) {
                throw new ImportFileException(
                    ImportIssueCode.IMPORT_ROW_LIMIT_EXCEEDED,
                    "Số dòng dữ liệu vượt quá giới hạn tối đa " + limits.maxRows() + " dòng");
            }
            rows.add(new ImportSheetRow(i + 1, cells));
        }
        if (rows.isEmpty()) {
            throw new ImportFileException(ImportIssueCode.IMPORT_EMPTY_FILE, "Tệp không có dòng dữ liệu");
        }

        List<ImportColumn> columns = new ArrayList<>(header.size());
        for (int c = 0; c < header.size(); c++) {
            String example = rows.isEmpty() ? "" : rows.get(0).cells().getOrDefault(keys.get(c), "").trim();
            columns.add(new ImportColumn(keys.get(c), header.get(c).trim(), example));
        }
        return new ImportSheet(fileType, sheetName, columns, rows);
    }
}
