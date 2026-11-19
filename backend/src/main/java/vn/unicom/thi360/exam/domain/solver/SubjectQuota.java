package vn.unicom.thi360.exam.domain.solver;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chỉ tiêu cấp môn (B9, spec 5.2): tính trên mọi đơn vị đếm của mọi nhóm trong môn, kể cả câu con
 * của chùm. Mỗi thành phần để {@code null} nghĩa là môn không kiểm soát thành phần đó; đã khai thì
 * tổng quy ra số câu phải bằng số câu của môn.
 *
 * @param answerQuotas chỉ tiêu theo dạng trả lời (D22), ví dụ Toán HSA SINGLE_CHOICE 35 +
 *                     SHORT_ANSWER 15; null khi môn không kiểm soát dạng trả lời
 * @param matrix       chỉ tiêu lớp × độ khó của môn; null khi không kiểm soát
 * @param caps         cận trên số đơn vị mang một MAPID (UNI-2758), của cả môn hoặc của một nhóm
 */
public record SubjectQuota(AnswerQuotas answerQuotas, MatrixQuota matrix, List<TopicQuota> topicQuotas,
        List<ExamDimensionQuota> examQuotas, List<TopicCap> caps) {

    public SubjectQuota {
        topicQuotas = topicQuotas == null ? List.of() : List.copyOf(topicQuotas);
        examQuotas = examQuotas == null ? List.of() : List.copyOf(examQuotas);
        caps = caps == null ? List.of() : List.copyOf(caps);
    }

    public SubjectQuota(AnswerQuotas answerQuotas, MatrixQuota matrix, List<TopicQuota> topicQuotas,
            List<ExamDimensionQuota> examQuotas) {
        this(answerQuotas, matrix, topicQuotas, examQuotas, List.of());
    }

    public SubjectQuota(AnswerQuotas answerQuotas, MatrixQuota matrix, List<TopicQuota> topicQuotas) {
        this(answerQuotas, matrix, topicQuotas, List.of());
    }

    public SubjectQuota(AnswerQuotas answerQuotas, MatrixQuota matrix) {
        this(answerQuotas, matrix, List.of());
    }

    /** Môn không có chỉ tiêu cấp môn nào. */
    public static final SubjectQuota NONE = new SubjectQuota(null, null);

    /**
     * Chỉ tiêu dạng trả lời của môn: dãy (mã dạng trả lời theo D22, giá trị) theo thứ tự khai báo —
     * thứ tự này quyết định xử lý hòa của phần dư lớn nhất khi {@code unit} là
     * {@link QuotaUnit#PERCENT}.
     *
     * @param items (answerType, giá trị) theo thứ tự khai báo; {@code code} của
     *              {@link DimensionQuota} mang mã dạng trả lời
     * @param unit  đơn vị của mọi giá trị
     */
    public record AnswerQuotas(List<DimensionQuota> items, QuotaUnit unit) {

        public AnswerQuotas {
            Objects.requireNonNull(items, "items must not be null");
            items = List.copyOf(items);
            Objects.requireNonNull(unit, "unit must not be null");
            Set<String> seen = new HashSet<>();
            for (DimensionQuota item : items) {
                if (!seen.add(item.code())) {
                    throw new IllegalArgumentException("duplicate answer type quota " + item.code());
                }
            }
        }
    }
}
