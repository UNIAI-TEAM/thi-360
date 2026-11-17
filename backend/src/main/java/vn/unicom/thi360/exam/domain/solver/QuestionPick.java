package vn.unicom.thi360.exam.domain.solver;

/**
 * Một câu được chọn vào đề chứng minh: id câu và số phiên bản đang phục vụ.
 *
 * @param questionId id câu (id bộ với bộ câu hỏi / Điền khuyết)
 * @param versionNo  số phiên bản được chọn
 */
public record QuestionPick(String questionId, int versionNo) {
}
