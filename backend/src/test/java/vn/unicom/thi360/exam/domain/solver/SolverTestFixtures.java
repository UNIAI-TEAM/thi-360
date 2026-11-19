package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Dựng dữ liệu và đồng hồ cho test của {@link PaperSolver} (mô hình ma trận theo môn, B9).
 */
final class SolverTestFixtures {

    static final String G10 = "GRADE_10";
    static final String G11 = "GRADE_11";
    static final String G12 = "GRADE_12";
    static final String NB = "RECOGNITION";
    static final String TH = "COMPREHENSION";
    static final String VD = "APPLICATION";
    static final String SC = "SINGLE_CHOICE";
    static final String SA = "SHORT_ANSWER";

    private SolverTestFixtures() {
    }

    // ---- đơn vị đếm và ứng viên ----

    static CountingUnit unit(String answerType, String grade, String difficulty) {
        return new CountingUnit(null, answerType, grade, difficulty, List.of(), List.of(), null);
    }

    static CountingUnit unit(String childId, String answerType, String grade, String difficulty) {
        return new CountingUnit(childId, answerType, grade, difficulty, List.of(), List.of(), null);
    }

    /** Câu độc lập một đơn vị. */
    static PathCandidate cand(String id, String answerType, String grade, String difficulty) {
        return new PathCandidate(id, 1, null, null, List.of(unit(answerType, grade, difficulty)));
    }

    /** Bộ câu hỏi / Điền khuyết nguyên vẹn: {@code units} phần tử, setId = id. */
    static PathCandidate set(String id, CountingUnit... units) {
        return new PathCandidate(id, 1, id, null, List.of(units));
    }

    // ---- chỉ tiêu ----

    static CellQuota cell(String grade, String difficulty, int quota) {
        return new CellQuota(grade, difficulty, quota);
    }

    static DimensionQuota dim(String code, int quota) {
        return new DimensionQuota(code, quota);
    }

    /** Chỉ tiêu dạng trả lời của môn, đơn vị số câu. */
    static SubjectQuota.AnswerQuotas answerQuotas(DimensionQuota... items) {
        return new SubjectQuota.AnswerQuotas(List.of(items), QuotaUnit.COUNT);
    }

    /** Chỉ tiêu dạng trả lời của môn, đơn vị phần trăm. */
    static SubjectQuota.AnswerQuotas answerQuotaPercents(DimensionQuota... items) {
        return new SubjectQuota.AnswerQuotas(List.of(items), QuotaUnit.PERCENT);
    }

    // ---- nhóm ----

    /** Nhóm câu đơn số câu cố định, theo chỉ tiêu của môn, không chỉ tiêu riêng. */
    static GroupQuota group(String groupId, int questionCount) {
        return GroupQuota.single(groupId, questionCount);
    }

    /** Nhóm câu đơn với ma trận ô kết hợp riêng. */
    static GroupQuota group(String groupId, int questionCount, MatrixQuota matrix) {
        return new GroupQuota(groupId, GroupQuota.GroupKind.SINGLE, questionCount, null, null, null,
                null, matrix, List.of(), List.of(), null, null);
    }

    // ---- môn, phần, đường ----

    /** Môn không chỉ tiêu cấp môn, số câu cho trước. */
    static SectionOnPath section(String subjectCode, int questionCount, GroupQuota... groups) {
        return new SectionOnPath(subjectCode, subjectCode + "-S", questionCount,
                SubjectQuota.NONE, List.of(groups));
    }

    static SectionOnPath section(String subjectCode, int questionCount, SubjectQuota quota,
            GroupQuota... groups) {
        return new SectionOnPath(subjectCode, subjectCode + "-S", questionCount, quota,
                List.of(groups));
    }

    /** Môn của một SUBJECT_CHOICE: questionCount = null. */
    static SectionOnPath choiceSection(String subjectCode, SubjectQuota quota,
            GroupQuota... groups) {
        return new SectionOnPath(subjectCode, subjectCode + "-S", null, quota, List.of(groups));
    }

    /** Phần FIXED gói các môn. */
    static SolvePart part(String partId, SectionOnPath... sections) {
        return new SolvePart.Fixed(partId, List.of(sections));
    }

    /** Đường một môn cố định. */
    static SolvePath path(SectionOnPath... sections) {
        return SolvePath.of(0, List.of(sections));
    }

    // ---- nguồn ứng viên ----

    /** Nguồn tra theo groupId. */
    static CandidateSource source(Map<String, List<PathCandidate>> byGroupId) {
        return group -> byGroupId.getOrDefault(group.groupId(), List.of());
    }

    /** Một nguồn duy nhất cho mọi nhóm. */
    static CandidateSource source(List<PathCandidate> pool) {
        return group -> pool;
    }

    // ---- đồng hồ ----

    /**
     * Đồng hồ bước: {@code instant()} trả {@code base} cho {@code callsBeforeExpiry} lần gọi đầu,
     * sau đó nhảy vọt qua mọi hạn. Dùng để ép DEADLINE_EXCEEDED trong khi giải.
     */
    static final class SteppingClock extends Clock {

        private final Instant base = Instant.parse("2026-11-16T00:00:00Z");
        private final int callsBeforeExpiry;
        private int calls;

        SteppingClock(int callsBeforeExpiry) {
            this.callsBeforeExpiry = callsBeforeExpiry;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            calls++;
            return calls <= callsBeforeExpiry ? base : base.plus(Duration.ofDays(1));
        }
    }
}
