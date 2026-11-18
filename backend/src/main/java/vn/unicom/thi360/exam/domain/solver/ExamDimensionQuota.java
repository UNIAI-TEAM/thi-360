package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.List;

/** A required exam-level distribution over all sections of one subject. */
public record ExamDimensionQuota(String id, Dimension dimension, List<Bucket> buckets) {
    public enum Dimension { GRADE, DIFFICULTY }
    public record Bucket(List<String> codes, Integer count, BigDecimal percent) {
        public Bucket { codes = List.copyOf(codes); }
    }
    public ExamDimensionQuota { buckets = List.copyOf(buckets); }
}
