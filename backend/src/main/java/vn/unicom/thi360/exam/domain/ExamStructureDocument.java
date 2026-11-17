package vn.unicom.thi360.exam.domain;

import java.util.List;

public record ExamStructureDocument(List<ExamPart> parts, List<ExamQuota> examQuotas) {
    public ExamStructureDocument {
        parts = parts == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parts));
        examQuotas = examQuotas == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(examQuotas));
    }

    public ExamStructureDocument(List<ExamPart> parts) {
        this(parts, List.of());
    }
}
