package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.BlockExpectedScoring;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MatrixUnit;
import vn.unicom.thi360.exam.domain.solver.QuotaRef;
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

    @Test
    void cellQuotaFailureReportsCellShortage() {
        ObjectMapper mapper = new ObjectMapper();
        var issues = new ExamCheckSolverIssues(new ExamCheckIssues(mapper),
                new ExamStructureDocument(List.of()), 30);
        var cell = new QuotaRef("MATH", "math", "CELL", "G12/VDC");
        var issue = issues.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "block", cell, List.of());
        assertThat(issue.path("code").asText()).isEqualTo("CELL_SHORTAGE");
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G12");
        assertThat(issue.path("difficultyCode").asText()).isEqualTo("VDC");
        var grade = new QuotaRef("MATH", "math", "GRADE", "G12");
        assertThat(issues.failure(SolveReason.SUBJECT_QUOTA_INFEASIBLE, "MATH", "block", grade, List.of())
                .path("code").asText()).isEqualTo("SUBJECT_QUOTA_INFEASIBLE");
    }

    @Test
    void slotShortageIdentifiesParentBlockAndSlot() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ExamStructureDocument doc = mapper.readValue("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"PHY",
                "blocks":[{"id":"b","name":"Passages","questionType":"QUESTION_SET"}]}]}]}
                """, ExamStructureDocument.class);
        var issue = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), doc, 30)
                .failure(SolveReason.INSUFFICIENT_SOURCE, "PHY", "b:slot:4", null, List.of());
        assertThat(issue.path("code").asText()).isEqualTo("SLOT_SOURCE_SHORTAGE");
        assertThat(issue.path("location").path("partId").asText()).isEqualTo("p");
        assertThat(issue.path("location").path("sectionId").asText()).isEqualTo("s");
        assertThat(issue.path("location").path("blockId").asText()).isEqualTo("b");
        assertThat(issue.path("location").path("slotIndex").asInt()).isEqualTo(4);
    }

    @Test
    void missingCombinationSubjectNamesOnlyAffectedCodes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ExamStructureDocument doc = mapper.readValue("""
                {"parts":[{"id":"p","codeSource":{"source":"GLOBAL_LIST"},"subjectPool":[
                {"id":"physics","subjectCode":"PHY","blocks":[]},
                {"id":"biology","subjectCode":"BIO","blocks":[]}]}]}
                """, ExamStructureDocument.class);
        var definitions = java.util.Map.of("p", java.util.Map.of(
                "QD01", List.of("PHY", "CHEM"),
                "QD02", List.of("CHEM", "BIO"),
                "QD03", List.of("PHY", "BIO")));
        var problems = new ExamCheckRules(mapper, null, doc, definitions).combinationIssues();
        assertThat(problems).hasSize(1);
        var issue = problems.getFirst();
        assertThat(issue.path("code").asText()).isEqualTo("COMBINATION_SUBJECT_MISSING");
        assertThat(issue.path("subjectCode").asText()).isEqualTo("CHEM");
        assertThat(issue.path("location").path("partId").asText()).isEqualTo("p");
        assertThat(issue.path("affectedCodes")).extracting(node -> node.asText())
                .containsExactlyInAnyOrder("QD01", "QD02");
    }

    private static Unit unit(QuestionType type, ScoringDetails scoring) {
        return new Unit(null, null, type, null, null, List.of(), List.of(), null, scoring);
    }
}
