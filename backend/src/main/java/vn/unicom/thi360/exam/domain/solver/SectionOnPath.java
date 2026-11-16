package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Một môn trên đường lựa chọn (mock {@code ExamSection}, spec 5.4): mã môn, chỉ tiêu cấp môn
 * ({@link SubjectQuota}) và các nhóm lấy câu ({@link GroupQuota}) của môn đó.
 *
 * <p>{@code questionCount} là số câu của môn trên đường này. Nó để {@code null} khi môn nằm trong
 * một phương án {@link SubjectChoice} (số câu chỉ được tính khi học viên chọn môn, theo D25); trong
 * {@link SubjectPick} và trong mọi {@link SectionOnPath} của một đường đã mở nó luôn có giá trị.
 *
 * <p>Tổng số câu của môn được phân cho các nhóm: tổng số câu cố định của các nhóm (SINGLE có
 * {@code questionCount}, SET là setCount × setSize) không vượt số câu của môn; phần chênh thuộc về
 * nhóm SINGLE có {@code questionCount = null} ("lấy phần còn lại", tối đa một nhóm — spec 5.2).
 *
 * @param subjectCode   mã môn (mã môn trong danh mục)
 * @param sectionId     id của môn trong cấu trúc bài (hai môn cùng {@code subjectCode} vẫn phân biệt)
 * @param questionCount số câu của môn; null chỉ khi môn thuộc {@link SubjectChoice}
 * @param subjectQuota  chỉ tiêu cấp môn; {@link SubjectQuota#NONE} khi môn không khai chỉ tiêu
 * @param groups        các nhóm lấy câu của môn, theo thứ tự cấu trúc
 */
public record SectionOnPath(
        String subjectCode,
        String sectionId,
        Integer questionCount,
        SubjectQuota subjectQuota,
        List<GroupQuota> groups,
        String choiceGroup) {

    public SectionOnPath {
        Objects.requireNonNull(subjectCode, "subjectCode must not be null");
        Objects.requireNonNull(sectionId, "sectionId must not be null");
        if (questionCount != null && questionCount < 0) {
            throw new IllegalArgumentException("questionCount must not be negative");
        }
        if (subjectQuota == null) {
            subjectQuota = SubjectQuota.NONE;
        }
        groups = groups == null ? List.of() : List.copyOf(groups);
        int remainderGroups = 0;
        for (GroupQuota group : groups) {
            if (group.kind() == GroupQuota.GroupKind.SINGLE && group.questionCount() == null) {
                remainderGroups++;
            }
        }
        if (remainderGroups > 1) {
            throw new IllegalArgumentException(
                    "at most one remainder group (questionCount = null) per section");
        }
    }

    /** Môn không tham gia lựa chọn nhóm tự nhiên/xã hội. */
    public SectionOnPath(String subjectCode, String sectionId, Integer questionCount,
            SubjectQuota subjectQuota, List<GroupQuota> groups) {
        this(subjectCode, sectionId, questionCount, subjectQuota, groups, null);
    }

    /** Bản sao có số câu đã quyết (dùng khi mở {@link SubjectChoice} thành {@link SubjectPick}). */
    public SectionOnPath withQuestionCount(int resolvedCount) {
        return new SectionOnPath(subjectCode, sectionId, resolvedCount, subjectQuota, groups,
                choiceGroup);
    }
}
