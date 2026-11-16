package vn.unicom.thi360.exam.domain;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlockExpectedScoring(
        String trueFalseMethod,
        List<BigDecimal> trueFalseTable,
        String multipleChoiceRule) {
    public BlockExpectedScoring {
        trueFalseTable = trueFalseTable == null ? null : List.copyOf(trueFalseTable);
    }
}
