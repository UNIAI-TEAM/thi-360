package vn.unicom.thi360.questionbank.infrastructure;


/**
 * Một dòng của danh sách lỗi lưu trong {@code question_import.errors} (mirror
 * {@code ImportErrorLine} của web): số dòng, mã nguồn của dòng và lỗi chặn kèm trường/cột.
 */
public record QuestionImportErrorLine(int rowNumber, String sourceCode, Issue issue) {
    public record Issue(String code, String severity, String field, String column, String value,
            Integer relatedRow, String questionType, String subjectCode) {}
}
