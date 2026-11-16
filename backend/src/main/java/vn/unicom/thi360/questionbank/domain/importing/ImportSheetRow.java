package vn.unicom.thi360.questionbank.domain.importing;

import java.util.Map;

/**
 * Một dòng dữ liệu của tệp nhập (mirror {@code ImportSheetRow} của web).
 *
 * @param rowNumber số dòng trong tệp (dòng 1 là tên cột, dữ liệu bắt đầu từ dòng 2)
 * @param cells giá trị ô theo khóa cột ({@code c0}, {@code c1}…)
 */
public record ImportSheetRow(int rowNumber, Map<String, String> cells) {

    public String cell(String columnKey) {
        return cells.getOrDefault(columnKey, "");
    }
}
