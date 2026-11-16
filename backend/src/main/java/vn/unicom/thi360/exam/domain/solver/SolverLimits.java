package vn.unicom.thi360.exam.domain.solver;

import java.time.Duration;
import java.util.Objects;

/**
 * Giới hạn của bộ giải (spec 5.4, plan B5).
 *
 * <p>Giá trị thuần, không đọc cấu hình: BE-5b buộc {@code thi360.exam.solver.*} vào record này.
 *
 * @param maxPaths              số đường lựa chọn tối đa được liệt kê — mỗi cách chọn môn của một
 *                              {@link SubjectChoice} tính một đường (vượt thì UNCONFIRMED/PATH_LIMIT_EXCEEDED)
 * @param maxCandidatesPerBlock số ứng viên tối đa của một nhóm (vượt thì UNCONFIRMED/CANDIDATE_LIMIT_EXCEEDED)
 * @param timeout               hạn thời gian cho cả một lời gọi solve, tính cả đọc ứng viên và liệt kê đường
 * @param maxChoiceSubjects     số môn tối đa của một {@link SubjectChoice} (spec 5.2: 8; vượt thì
 *                              UNCONFIRMED/CHOICE_SUBJECT_LIMIT_EXCEEDED)
 */
public record SolverLimits(int maxPaths, int maxCandidatesPerBlock, Duration timeout,
        int maxChoiceSubjects) {

    public SolverLimits {
        if (maxPaths <= 0) {
            throw new IllegalArgumentException("maxPaths must be positive");
        }
        if (maxCandidatesPerBlock <= 0) {
            throw new IllegalArgumentException("maxCandidatesPerBlock must be positive");
        }
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (maxChoiceSubjects <= 0) {
            throw new IllegalArgumentException("maxChoiceSubjects must be positive");
        }
    }

    /** Mặc định của spec 5.4 / 5.2: 200 đường, 20.000 ứng viên mỗi nhóm, 30 giây, 8 môn mỗi SUBJECT_CHOICE. */
    public static SolverLimits defaults() {
        return new SolverLimits(200, 20_000, Duration.ofSeconds(30), 8);
    }
}
