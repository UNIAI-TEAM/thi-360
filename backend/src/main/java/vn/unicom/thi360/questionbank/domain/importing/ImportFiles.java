package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Kiểm tra chung của tệp nhập trước khi giải mã (mirror {@code checkImportFile} của web):
 * đuôi {@code .xlsm} là macro bị chặn, loại khác {@code .xlsx}/CSV là tệp không hợp lệ, tệp
 * trống và quá cỡ là lỗi riêng.
 */
public final class ImportFiles {

    private ImportFiles() {}

    /** Nhận loại và kiểm tra cỡ; không đọc nội dung. */
    public static ImportFileType checkCommon(String fileName, byte[] fileBytes, ImportLimits limits)
        throws ImportFileException {
        if (ImportFileType.isMacroFileName(fileName)) {
            throw new ImportFileException(
                ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED, "Tệp Excel không được chứa macro");
        }
        ImportFileType type = ImportFileType.ofFileName(fileName);
        if (type == null) {
            throw new ImportFileException(
                ImportIssueCode.IMPORT_INVALID_FILE,
                "Tệp tải lên không phải .xlsx hoặc .csv hợp lệ");
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new ImportFileException(ImportIssueCode.IMPORT_EMPTY_FILE, "Tệp nhập trống");
        }
        if (fileBytes.length > limits.maxFileBytes()) {
            throw new ImportFileException(
                ImportIssueCode.IMPORT_FILE_TOO_LARGE,
                "Dung lượng tệp vượt quá giới hạn cho phép (tối đa "
                    + (limits.maxFileBytes() / (1024 * 1024)) + " MB)");
        }
        return type;
    }
}
