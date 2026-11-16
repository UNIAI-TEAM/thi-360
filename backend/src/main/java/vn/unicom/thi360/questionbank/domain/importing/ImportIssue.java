package vn.unicom.thi360.questionbank.domain.importing;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Một lỗi chặn hoặc cảnh báo của một dòng (spec 4.5: "Lỗi chỉ rõ dòng, câu hoặc ý và trường
 * cần sửa"; mô hình mirror {@code ImportRowIssue} của web).
 *
 * @param rowNumber số dòng trong tệp (dòng 1 là tên cột)
 * @param code mã vấn đề
 * @param severity chặn hay cảnh báo
 * @param field trường liên quan; {@code null} khi lỗi thuộc cả dòng
 * @param column tên cột trong tệp của trường đó (hiển thị trong bảng lỗi)
 * @param value giá trị gây lỗi, nếu có
 * @param relatedRow số dòng liên quan (dòng trùng Mã nguồn, dòng ngữ liệu của chùm)
 * @param questionType dạng câu của dòng, để gợi ý cách sửa đúng dạng
 * @param subjectCode mã môn đã nhận diện của dòng (để nêu trong lỗi kiến thức)
 */
public record ImportIssue(
    int rowNumber,
    ImportIssueCode code,
    ImportIssueSeverity severity,
    ImportField field,
    String column,
    String value,
    Integer relatedRow,
    QuestionType questionType,
    String subjectCode) {

    public boolean blocking() {
        return severity == ImportIssueSeverity.ERROR;
    }
}
