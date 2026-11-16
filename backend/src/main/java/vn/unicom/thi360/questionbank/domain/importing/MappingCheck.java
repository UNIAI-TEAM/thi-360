package vn.unicom.thi360.questionbank.domain.importing;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kết quả kiểm tra một {@link ColumnMapping} (mirror {@code MappingCheck} của web). Mapping
 * hợp lệ khi mọi trường bắt buộc đã có cột và không trường nào nhận từ hai cột trở lên; không
 * hợp lệ thì API trả 422 {@code IMPORT_MAPPING_INVALID} (spec 4.5).
 *
 * @param missingRequired trường bắt buộc chưa có cột nào ánh xạ vào
 * @param duplicateFields trường được hai cột trở lên ánh xạ vào (lỗi)
 * @param duplicateColumnKeys khóa các cột đang ánh xạ vào một trường bị trùng
 * @param mappedCount số cột có ánh xạ vào một trường
 * @param unmappedCount số cột chưa ánh xạ
 * @param ignoredCount số cột bị bỏ qua
 * @param columnByField trường đã có cột → khóa cột đầu tiên ánh xạ vào nó
 */
public record MappingCheck(
    List<ImportField> missingRequired,
    List<ImportField> duplicateFields,
    Set<String> duplicateColumnKeys,
    int mappedCount,
    int unmappedCount,
    int ignoredCount,
    Map<ImportField, String> columnByField) {

    public MappingCheck {
        missingRequired = List.copyOf(missingRequired);
        duplicateFields = List.copyOf(duplicateFields);
        duplicateColumnKeys = Set.copyOf(duplicateColumnKeys);
        columnByField = Map.copyOf(columnByField);
    }

    public boolean valid() {
        return missingRequired.isEmpty() && duplicateFields.isEmpty();
    }
}
