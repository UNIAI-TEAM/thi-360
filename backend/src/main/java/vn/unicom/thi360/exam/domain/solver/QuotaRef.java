package vn.unicom.thi360.exam.domain.solver;

/**
 * Định danh có cấu trúc của một chỉ tiêu cấp môn liên quan tới thất bại (spec 5.4: INVALID kèm
 * môn và chỉ tiêu; R2-23). Chỉ mô tả chỉ tiêu phía môn; thất bại phía nhóm vẫn mang ở
 * {@link PathResult#groupId()}.
 *
 * @param subjectCode mã môn của bài toán con chứa chỉ tiêu
 * @param sectionId   id của môn ({@link SectionOnPath#sectionId()}) khai báo chỉ tiêu; với môn
 *                    gộp từ nhiều ExamSection cùng mã, đây là section sở hữu chỉ tiêu
 * @param kind        loại chỉ tiêu: {@code ANSWER} (dạng trả lời), {@code CELL} (ô lớp × độ khó),
 *                    {@code GRADE} (chiều khối lớp), {@code DIFFICULTY} (chiều độ khó)
 * @param key         mã của mục chỉ tiêu: mã dạng trả lời, mã khối lớp / độ khó, hoặc
 *                    {@code "grade/difficulty"} với ô
 */
public record QuotaRef(String subjectCode, String sectionId, String kind, String key) {
}
