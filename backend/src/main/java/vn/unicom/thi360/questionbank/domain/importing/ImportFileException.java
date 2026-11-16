package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Lỗi mức tệp khi đọc tệp nhập (không phải lỗi từng dòng): sai định dạng, macro, zip bomb,
 * quá cỡ, thiếu tiêu đề, công thức. Lớp ứng dụng bắt và trả {@code code} cho client.
 */
public class ImportFileException extends Exception {

    private final ImportIssueCode code;

    public ImportFileException(ImportIssueCode code, String message) {
        super(message);
        this.code = code;
    }

    public ImportFileException(ImportIssueCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Mã lỗi của tệp ({@code IMPORT_*}, spec 9.2). */
    public ImportIssueCode code() {
        return code;
    }
}
