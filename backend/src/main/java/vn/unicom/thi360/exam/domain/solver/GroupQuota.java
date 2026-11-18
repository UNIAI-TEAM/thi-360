package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Một nhóm lấy câu trong một môn (B9, D21: mock {@code QuestionBlock}; thay thế {@code BlockQuota}
 * của bản trước). Nhóm thuộc một trong hai {@link GroupKind}:
 *
 * <ul>
 *   <li>{@link GroupKind#SINGLE} — nhóm câu đơn: lấy đúng {@code questionCount} đơn vị đếm;
 *       {@code questionCount = null} nghĩa là "lấy phần còn lại" của môn (tối đa một nhóm như vậy
 *       trên một {@link SectionOnPath}, spec 5.2). {@code answerType} là dạng trả lời mà nhóm nhận
 *       (mock {@code QuestionBlock.questionType}); {@code null} nghĩa là nhóm lấy theo chỉ tiêu
 *       dạng trả lời của môn (Toán HSA).
 *   <li>{@link GroupKind#SET} — nhóm câu chùm: lấy đúng {@code setCount} bộ, mỗi bộ đúng
 *       {@code setSize} đơn vị đếm, luôn nguyên chùm. {@code childAnswerType} giới hạn dạng của
 *       câu con; {@code null} là không giới hạn.
 * </ul>
 *
 * <p>{@code matrix}, {@code competencies}, {@code mapidGroups} là chỉ tiêu riêng của nhóm (tùy
 * chọn, spec 5.2): câu của nhóm vẫn đóng góp vào chỉ tiêu cấp môn. {@code requiredPointsPerUnit}
 * và {@code expectedScoringRule} là điều kiện lọc nguồn (B5): chỉ đơn vị có điểm bằng giá trị yêu
 * cầu / ứng viên có quy tắc chấm khớp mới được chọn; {@code null} = không lọc.
 *
 * <p>{@code groupId} phải duy nhất trên toàn đường (kiểm tra ở {@link SolvePath}); bộ giải nhớ
 * nguồn ứng viên theo toàn bộ giá trị của record này, không theo riêng {@code groupId}.
 *
 * @param groupId               mã nhóm, duy nhất trên đường
 * @param kind                  SINGLE hoặc SET
 * @param questionCount         số đơn vị đếm của nhóm đơn; null = lấy phần còn lại (chỉ SINGLE)
 * @param answerType            dạng trả lời của nhóm đơn (D22); null = theo chỉ tiêu của môn
 * @param setCount              số chùm cần chọn (chỉ SET)
 * @param setSize               số đơn vị mỗi chùm (chỉ SET)
 * @param childAnswerType       dạng trả lời bắt buộc của câu con (chỉ SET); null = không giới hạn
 * @param matrix                chỉ tiêu lớp × độ khó riêng của nhóm; null = không có
 * @param competencies          chỉ tiêu năng lực bổ sung; rỗng = không có
 * @param mapidGroups           chỉ tiêu nhóm MAPID bổ sung; rỗng = không có
 * @param requiredPointsPerUnit điểm bắt buộc của từng đơn vị; null = không lọc
 * @param expectedScoringRule   quy tắc chấm mong đợi; null = không so
 * @param slotParent            chỉ tiêu dùng chung của các slot cùng nhóm cha; null nếu không phải slot
 */
public record GroupQuota(
        String groupId,
        GroupKind kind,
        Integer questionCount,
        String answerType,
        Integer setCount,
        Integer setSize,
        String childAnswerType,
        MatrixQuota matrix,
        List<CompetencyQuota> competencies,
        List<MapidGroupQuota> mapidGroups,
        BigDecimal requiredPointsPerUnit,
        String expectedScoringRule,
        SlotParentQuota slotParent,
        List<TopicQuota> topicQuotas) {

    public GroupQuota(String groupId, GroupKind kind, Integer questionCount, String answerType,
            Integer setCount, Integer setSize, String childAnswerType, MatrixQuota matrix,
            List<CompetencyQuota> competencies, List<MapidGroupQuota> mapidGroups,
            BigDecimal requiredPointsPerUnit, String expectedScoringRule, SlotParentQuota slotParent) {
        this(groupId, kind, questionCount, answerType, setCount, setSize, childAnswerType, matrix,
                competencies, mapidGroups, requiredPointsPerUnit, expectedScoringRule, slotParent, List.of());
    }

    /** A group without shared slot-parent quotas. */
    public GroupQuota(String groupId, GroupKind kind, Integer questionCount, String answerType,
            Integer setCount, Integer setSize, String childAnswerType, MatrixQuota matrix,
            List<CompetencyQuota> competencies, List<MapidGroupQuota> mapidGroups,
            BigDecimal requiredPointsPerUnit, String expectedScoringRule) {
        this(groupId, kind, questionCount, answerType, setCount, setSize, childAnswerType, matrix,
                competencies, mapidGroups, requiredPointsPerUnit, expectedScoringRule, null);
    }

    /** Loại nhóm lấy câu (spec 5.2): nhóm câu đơn hay nhóm câu chùm. */
    public enum GroupKind {
        /** Nhóm câu đơn: khai số câu (hoặc lấy phần còn lại) và phạm vi nguồn. */
        SINGLE,
        /** Nhóm câu chùm: khai số chùm và số câu con mỗi chùm, luôn lấy nguyên chùm. */
        SET
    }

    public GroupQuota {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        switch (kind) {
            case SINGLE -> {
                if (setCount != null || setSize != null || childAnswerType != null) {
                    throw new IllegalArgumentException(
                            "setCount/setSize/childAnswerType are only for SET groups");
                }
                if (questionCount != null && questionCount < 0) {
                    throw new IllegalArgumentException("questionCount must not be negative");
                }
            }
            case SET -> {
                if (questionCount != null || answerType != null) {
                    throw new IllegalArgumentException(
                            "questionCount/answerType are only for SINGLE groups");
                }
                Objects.requireNonNull(setCount, "setCount must not be null for SET groups");
                Objects.requireNonNull(setSize, "setSize must not be null for SET groups");
                if (setCount <= 0 || setSize <= 0) {
                    throw new IllegalArgumentException("setCount and setSize must be positive");
                }
            }
        }
        if (requiredPointsPerUnit != null && requiredPointsPerUnit.signum() <= 0) {
            throw new IllegalArgumentException("requiredPointsPerUnit must be positive");
        }
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
        mapidGroups = mapidGroups == null ? List.of() : List.copyOf(mapidGroups);
        topicQuotas = topicQuotas == null ? List.of() : List.copyOf(topicQuotas);
        Set<String> seen = new HashSet<>();
        QuotaUnit competencyUnit = competencies.isEmpty() ? null : competencies.getFirst().unit();
        for (CompetencyQuota c : competencies) {
            if (c.unit() != competencyUnit) {
                throw new IllegalArgumentException("competency quotas must use the same unit");
            }
            if (!seen.add("C" + c.competencyCode())) {
                throw new IllegalArgumentException("duplicate competency quota " + c.competencyCode());
            }
        }
        for (MapidGroupQuota g : mapidGroups) {
            if (!seen.add("M" + g.groupKey())) {
                throw new IllegalArgumentException("duplicate MAPID group quota " + g.groupKey());
            }
        }
    }

    /** Số đơn vị đếm mà nhóm chiếm trong môn khi đã biết số câu: SINGLE = questionCount, SET = setCount × setSize. */
    public int resolvedUnitCount(int remainder) {
        return switch (kind) {
            case SINGLE -> questionCount != null ? questionCount : remainder;
            case SET -> setCount * setSize;
        };
    }

    /** Nhóm câu đơn số câu cố định, không chỉ tiêu riêng. */
    public static GroupQuota single(String groupId, int questionCount) {
        return new GroupQuota(groupId, GroupKind.SINGLE, questionCount, null, null, null, null,
                null, List.of(), List.of(), null, null);
    }

    /** Nhóm câu đơn theo chỉ tiêu dạng trả lời của môn, lấy phần còn lại. */
    public static GroupQuota remainder(String groupId) {
        return new GroupQuota(groupId, GroupKind.SINGLE, null, null, null, null, null,
                null, List.of(), List.of(), null, null);
    }

    /** Nhóm câu chùm không chỉ tiêu riêng. */
    public static GroupQuota set(String groupId, int setCount, int setSize, String childAnswerType) {
        return new GroupQuota(groupId, GroupKind.SET, null, null, setCount, setSize, childAnswerType,
                null, List.of(), List.of(), null, null);
    }
}
