package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamQuota(
        String id,
        String name,
        Dimension dimension,
        List<Bucket> buckets,
        QuotaBasis basis,
        QuotaEnforcement enforcement,
        List<String> subjectCodes,
        List<String> excludeSubjectCodes) {
    public enum Dimension {
        GRADE,
        DIFFICULTY
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Bucket(List<String> codes, Integer count, java.math.BigDecimal percent) {
        public Bucket {
            codes = codes == null ? List.of() : List.copyOf(codes);
        }
    }

    public ExamQuota {
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
        subjectCodes = subjectCodes == null ? List.of() : List.copyOf(subjectCodes);
        excludeSubjectCodes = excludeSubjectCodes == null ? List.of() : List.copyOf(excludeSubjectCodes);
    }
}
