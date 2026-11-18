package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

class ExamOverviewTest {
    @Test
    void equivalentDecimalScalesDoNotRequireOverviewConfirmation() {
        var baseline = new ExamStructureDocument(List.of(part(new BigDecimal("4"))));
        var current = new ExamStructureDocument(List.of(part(new BigDecimal("4.00"))));
        assertThat(ExamOverview.changes(current, baseline)).isEmpty();
    }

    @Test
    void unresolvedPartIsNotTreatedAsUneditedOrSilentlyKept() {
        var baseline = new ExamStructureDocument(List.of(part(new BigDecimal("4"))));
        var unknown = new ExamPart("regenerated", null, "Part", null, 10, null, null, null,
                new BigDecimal("4"), List.of(), List.of());
        var changes = ExamOverview.changes(new ExamStructureDocument(List.of(unknown)), baseline);
        assertThat(changes).isNotEmpty();
        assertThatThrownBy(() -> ExamOverview.presentIn(baseline, changes))
                .isInstanceOf(vn.unicom.thi360.shared.api.BusinessRuleException.class);
    }

    private ExamPart part(BigDecimal points) {
        return new ExamPart("part", 1, "Part", null, 10, null, null, null, points, List.of(), List.of());
    }
}
