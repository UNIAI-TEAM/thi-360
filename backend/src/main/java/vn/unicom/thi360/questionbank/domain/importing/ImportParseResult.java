package vn.unicom.thi360.questionbank.domain.importing;

import java.util.List;

/**
 * Kết quả đọc + kiểm tra một tệp đã ánh xạ: kết quả từng dòng và tổng hợp (mirror
 * {@code ImportValidation} của web; bản nháp các dòng hợp lệ chính là đầu vào của lệnh nhập).
 */
public record ImportParseResult(List<ImportRowResult> rows, ImportSummary summary) {

    public ImportParseResult {
        rows = List.copyOf(rows);
    }

    /** Còn lỗi chặn nào (commit với {@code skipErrors = false} thì máy chủ từ chối). */
    public boolean hasErrors() {
        return summary.error() > 0;
    }
}
