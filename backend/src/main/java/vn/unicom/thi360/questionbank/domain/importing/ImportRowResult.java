package vn.unicom.thi360.questionbank.domain.importing;

import java.util.List;

/**
 * Kết quả đọc một dòng (mirror {@code ImportRowResult} của web). Bản nháp trên dòng bộ mang
 * cả {@link ImportQuestionDraft#children()}; các dòng câu con vẫn có kết quả riêng để bảng
 * lỗi chỉ đúng dòng cần sửa.
 *
 * @param rowNumber số dòng trong tệp
 * @param sourceCode mã nguồn thô của dòng ({@code null} khi trống)
 * @param status trạng thái cuối (lỗi chặn → {@code ERROR}; không lỗi mà trùng →
 *               {@code DUPLICATE}; còn lại {@code VALID})
 * @param issues các lỗi chặn và cảnh báo của dòng
 * @param draft bản nháp câu đọc được (kể cả khi dòng lỗi, để hiển thị phần đọc được)
 * @param duplicateOf câu đã có trong ngân hàng cùng Mã nguồn ({@code null} khi không trùng)
 * @param isSetPassage dòng là ngữ liệu của một chùm ({@code type = QUESTION_SET})
 * @param parentRowNumber số dòng ngữ liệu của chùm mà dòng này là câu con; {@code null} với
 *                        câu đơn và dòng ngữ liệu
 * @param dropped {@code true} khi dòng bị loại do bộ của nó bị loại (ngữ liệu lỗi hoặc một câu
 *                con lỗi chặn kéo cả bộ, spec 4.5) — dùng để đếm {@code errorRowCount}
 */
public record ImportRowResult(
    int rowNumber,
    String sourceCode,
    ImportRowStatus status,
    List<ImportIssue> issues,
    ImportQuestionDraft draft,
    ExistingQuestionRef duplicateOf,
    boolean isSetPassage,
    Integer parentRowNumber,
    boolean dropped) {

    public ImportRowResult {
        issues = List.copyOf(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(ImportIssue::blocking);
    }
}
