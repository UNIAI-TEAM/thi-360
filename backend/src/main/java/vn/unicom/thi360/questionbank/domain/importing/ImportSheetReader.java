package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Đọc một tệp nhập đã tải lên thành {@link ImportSheet} (cột, dòng, số dòng gốc). Cài đặt:
 * {@link CsvImportSheetReader} (UTF-8, BOM, RFC 4180, tự nhận dấu {@code ;}) và
 * {@link XlsxImportSheetReader} (POI, {@code ZipSecureFile}, chặn macro/công thức). Giới hạn
 * cỡ tệp và số dòng truyền qua {@link ImportLimits}.
 */
public interface ImportSheetReader {

    /**
     * Đọc toàn bộ tệp.
     *
     * @param fileName tên tệp tải lên (để nhận loại và báo lỗi đuôi {@code .xlsm})
     * @param fileBytes nội dung tệp
     * @param limits giới hạn cỡ tệp / số dòng
     * @return bảng đã tách cột/dòng; dòng trống hoàn toàn bị bỏ nhưng giữ số dòng gốc
     * @throws ImportFileException lỗi mức tệp (mã {@code IMPORT_*})
     */
    ImportSheet read(String fileName, byte[] fileBytes, ImportLimits limits) throws ImportFileException;
}
