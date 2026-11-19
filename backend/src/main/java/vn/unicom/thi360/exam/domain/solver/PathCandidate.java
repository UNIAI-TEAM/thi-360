package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Một ứng viên cho nguồn của nhóm — đơn vị chọn nguyên vẹn (bản chiếu gọn của
 * {@code findCandidates}, spec 3).
 *
 * <p>Ứng viên là một câu độc lập hoặc nguyên một bộ câu hỏi / câu Điền khuyết (khi đó
 * {@code setId} khác null và {@code units} có một phần tử cho mỗi câu con / vị trí). Bộ giải chỉ
 * lấy trọn một ứng viên, không bao giờ lấy một phần.
 *
 * <p>Tính duy nhất trên đường: khóa của ứng viên gồm {@code questionId} và mọi {@code childId}
 * khác null của các đơn vị — một câu con vừa nằm trong bộ vừa có bản độc lập sẽ xung đột nhau.
 *
 * @param questionId   id câu (id bộ với bộ câu hỏi / Điền khuyết)
 * @param versionNo    số phiên bản đang phục vụ của câu
 * @param setId        id bộ khi ứng viên là bộ câu hỏi hoặc Điền khuyết; null với câu độc lập
 * @param scoringRule  mã quy tắc chấm của câu (để so với {@code expectedScoringRule} của nhóm); null nếu không có
 * @param units        các đơn vị đếm, ít nhất một
 */
public record PathCandidate(String questionId, int versionNo, String setId,
        String scoringRule, List<CountingUnit> units) {

    public PathCandidate {
        Objects.requireNonNull(questionId, "questionId must not be null");
        units = units == null ? List.of() : List.copyOf(units);
    }

    /** Câu độc lập một đơn vị. */
    public static PathCandidate standalone(String questionId, int versionNo, String answerType,
            String gradeCode, String difficultyCode, java.math.BigDecimal points) {
        return new PathCandidate(questionId, versionNo, null, null,
                List.of(CountingUnit.standalone(answerType, gradeCode, difficultyCode, points)));
    }
}
