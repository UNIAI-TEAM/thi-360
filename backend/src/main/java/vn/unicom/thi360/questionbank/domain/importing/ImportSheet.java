package vn.unicom.thi360.questionbank.domain.importing;

import java.util.List;

/**
 * Kết quả đọc tệp: bảng đã tách cột/dòng, giữ số dòng gốc của tệp. {@code sheetName} là tên
 * trang tính với {@code .xlsx}, {@code null} với {@code .csv} (mirror {@code ImportUpload}).
 *
 * @param fileType loại tệp đã nhận diện
 * @param sheetName tên trang tính đầu tiên của XLSX; {@code null} với CSV
 * @param columns các cột theo dòng tiêu đề
 * @param rows các dòng dữ liệu không trống
 */
public record ImportSheet(
    ImportFileType fileType,
    String sheetName,
    List<ImportColumn> columns,
    List<ImportSheetRow> rows) {

    public ImportSheet {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    public int rowCount() {
        return rows.size();
    }
}
