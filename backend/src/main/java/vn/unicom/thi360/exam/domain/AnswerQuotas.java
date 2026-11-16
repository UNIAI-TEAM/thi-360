package vn.unicom.thi360.exam.domain;

import java.util.List;

public record AnswerQuotas(MatrixUnit unit, List<AnswerQuota> items) {
    public AnswerQuotas {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
