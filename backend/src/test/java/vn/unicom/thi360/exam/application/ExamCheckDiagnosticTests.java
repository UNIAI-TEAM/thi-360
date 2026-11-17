package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.BlockExpectedScoring;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MatrixUnit;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectQuota;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ScoringDetails;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ExamCheckDiagnosticTests {
    @Test
    void percentagesAreApportionedAtTheActualCountAndFractionsRejected() {
        assertThat(ExamCheckQuantities.resolve(List.of(new BigDecimal("50"), new BigDecimal("50")),
                MatrixUnit.PERCENT, 11)).containsExactly(6, 5);
        assertThat(ExamCheckQuantities.resolve(List.of(new BigDecimal("50.5"), new BigDecimal("49.5")),
                MatrixUnit.PERCENT, 11)).isNull();
    }

    @Test
    void servingScoringComparesMethodsTablesAndMultipleChoiceRules() {
        var expected = new BlockExpectedScoring("TABLE", List.of(new BigDecimal("0.50"), BigDecimal.ONE), "PARTIAL");
        assertThat(ExamCheckSources.mismatch(expected, unit(QuestionType.TRUE_FALSE,
                new ScoringDetails("TABLE", List.of(new BigDecimal("0.5"), new BigDecimal("1.0")), null)))).isFalse();
        assertThat(ExamCheckSources.mismatch(expected, unit(QuestionType.TRUE_FALSE,
                new ScoringDetails("TABLE", List.of(new BigDecimal("0.25"), BigDecimal.ONE), null)))).isTrue();
        assertThat(ExamCheckSources.mismatch(expected, unit(QuestionType.TRUE_FALSE, null))).isTrue();
        assertThat(ExamCheckSources.mismatch(expected, unit(QuestionType.MULTIPLE_CHOICE,
                new ScoringDetails(null, null, "ALL_OR_NOTHING")))).isTrue();
        assertThat(ExamCheckSources.mismatch(expected, unit(QuestionType.SINGLE_CHOICE, null))).isFalse();
    }

    @Test
    void jointFailureWithoutQuotaNamesTheSubjectGroupAndSelectedCount() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ExamStructureDocument doc = mapper.readValue("""
                {"parts":[{"id":"p","name":"Science","options":[{"id":"opt",
                "sections":[{"id":"s","subjectCode":"PHY","blocks":[{"id":"b","name":"Mechanics"}]}]}]}]}
                """, ExamStructureDocument.class);
        var section = new SectionOnPath("PHY", "s", null, SubjectQuota.NONE, List.of());
        var pick = new SubjectPick("opt", List.of(section), List.of(17));
        var issue = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), doc, 30)
                .failure(SolveReason.NO_JOINT_SOLUTION, "PHY", null, null, List.of(pick));
        assertThat(issue.path("code").asText()).isEqualTo("SUBJECT_QUOTA_INFEASIBLE");
        assertThat(issue.path("subjectCode").asText()).isEqualTo("PHY");
        assertThat(issue.path("subjectCount").asInt()).isEqualTo(17);
        assertThat(issue.path("blockId").asText()).isEqualTo("b");
        assertThat(issue.path("blockName").asText()).isEqualTo("Mechanics");
    }

    private static Unit unit(QuestionType type, ScoringDetails scoring) {
        return new Unit(null, null, type, null, null, List.of(), List.of(), null, scoring);
    }
}
