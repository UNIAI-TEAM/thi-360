package vn.unicom.thi360.attempt.domain.grading;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import vn.unicom.thi360.attempt.domain.ItemOutcome;

/**
 * Pure grading output for a single {@code AttemptItem}.
 *
 * @param exactEarnedPoints exact fractional earned score for lossless aggregation; {@code null} when pending review
 * @param earnedPoints rounded decimal (scale 4) persisted to {@code attempt_item.earned_points}; {@code null} when pending review
 * @param pointsMax item maximum score
 * @param outcome graded outcome enum
 * @param gradingDetail structured breakdown (without forbidden secret keys) stored in {@code grading_detail}
 * @param pendingReview true when manual grading is required before final score is known
 */
public record ItemGradeResult(
        ExactRational exactEarnedPoints,
        BigDecimal earnedPoints,
        BigDecimal pointsMax,
        ItemOutcome outcome,
        Map<String, Object> gradingDetail,
        boolean pendingReview) {

    public ItemGradeResult {
        Objects.requireNonNull(pointsMax, "pointsMax must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        gradingDetail = sanitizeDetail(gradingDetail);
    }

    private static Map<String, Object> sanitizeDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new LinkedHashMap<>(detail.size());
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered.isEmpty() ? Map.of() : Map.copyOf(filtered);
    }

    public static ItemGradeResult pendingReview(BigDecimal pointsMax, Map<String, Object> detail) {
        return new ItemGradeResult(null, null, pointsMax, ItemOutcome.PENDING_REVIEW, detail, true);
    }

    public static ItemGradeResult graded(
            ExactRational exactEarned,
            BigDecimal pointsMax,
            ItemOutcome outcome,
            Map<String, Object> detail) {
        Objects.requireNonNull(exactEarned, "exactEarned must not be null");
        return new ItemGradeResult(
                exactEarned,
                exactEarned.toBigDecimal(4),
                pointsMax,
                outcome,
                detail,
                false);
    }
}
