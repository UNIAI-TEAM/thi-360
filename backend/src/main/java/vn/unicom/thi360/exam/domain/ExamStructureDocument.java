package vn.unicom.thi360.exam.domain;

import java.util.List;

public record ExamStructureDocument(List<ExamPart> parts) {
    public ExamStructureDocument {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }
}
