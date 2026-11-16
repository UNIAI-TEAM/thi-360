package vn.unicom.thi360.exam.domain;

import java.util.List;

public record AnswerQuotas(MatrixUnit unit, List<AnswerQuota> items) {
    public AnswerQuotas {
        items = items == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
    }
}
