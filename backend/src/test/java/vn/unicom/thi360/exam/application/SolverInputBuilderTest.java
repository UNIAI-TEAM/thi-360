package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.*;
import vn.unicom.thi360.exam.domain.solver.*;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class SolverInputBuilderTest {
    @Test
    void expandsInheritedScopeAndPassesSetQuotasAndCandidateLimit() {
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        UUID root = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        CatalogItemView parentNode = mock(CatalogItemView.class);
        CatalogItemView childNode = mock(CatalogItemView.class);
        CatalogItemView subject = mock(CatalogItemView.class);
        when(parentNode.id()).thenReturn(root);
        when(parentNode.path()).thenReturn("/root/");
        when(childNode.id()).thenReturn(child);
        when(childNode.path()).thenReturn("/root/child/");
        when(subject.id()).thenReturn(subjectId);
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of(parentNode, childNode));
        when(catalog.findActiveItem("SUBJECT", "TOAN")).thenReturn(Optional.of(subject));
        when(source.findCandidates(any(), eq(8))).thenReturn(List.of());
        var scope = new KnowledgeScope(List.of(root), KnowledgeMatch.ALL, null, List.of("C1"));
        var matrix = new BlockMatrix(MatrixMode.COMBINED, MatrixUnit.PERCENT,
                List.of(new MatrixCell("G10", "NB", BigDecimal.valueOf(100))),
                List.of(), List.of(), List.of(new MatrixDimensionValue("C1", BigDecimal.valueOf(100))));
        var block = new QuestionBlock("set", "Set", QuestionType.QUESTION_SET, 2, 1, 2,
                null, matrix, null, null, null, false, BigDecimal.ONE, List.of("TAG"),
                new BlockExpectedScoring(null, null, "ALL_OR_NOTHING"));
        var section = new ExamSection("math", "TOAN", 2, scope, null, null, null, List.of(block));
        var part = new ExamPart("p", 1, "Part", 20, 2, 2, 2,
                new PartParticipation(ParticipationMode.ALL, null), null, List.of(section), List.of());
        var builder = new SolverInputBuilder(catalog, source, new SolverLimits(200, 7, Duration.ofSeconds(30), 8));
        var input = builder.build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        var fixed = (SolvePart.Fixed) input.parts().getFirst();
        GroupQuota group = fixed.sections().getFirst().groups().getFirst();
        assertThat(group.matrix()).isInstanceOf(MatrixQuota.Combined.class);
        assertThat(group.competencies()).containsExactly(new CompetencyQuota("C1", 100, QuotaUnit.PERCENT));
        assertThat(group.expectedScoringRule()).isNull();
        input.candidates().candidatesFor(group);
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).findCandidates(filter.capture(), eq(8));
        assertThat(filter.getValue().subjectId()).isEqualTo(subjectId);
        assertThat(filter.getValue().knowledgeMatch()).isEqualTo(QuestionSourcePort.Match.ALL);
        assertThat(filter.getValue().knowledgeGroups().getFirst()).containsExactlyInAnyOrder(root, child);
        assertThat(filter.getValue().tagCodes()).containsExactly("TAG");
        assertThat(filter.getValue().competencyCodes()).containsExactly("C1");
    }

    @Test
    void buildsChooseKAndSubjectChoiceWithoutResolvingSubjectCounts() {
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of());
        when(catalog.findActiveItem(eq("SUBJECT"), any())).thenReturn(Optional.empty());
        var a = new ExamSection("a", "LY", null, null, null, null, ChoiceGroup.NATURAL, List.of());
        var b = new ExamSection("b", "HOA", null, null, null, null, ChoiceGroup.NATURAL, List.of());
        var choice = new ExamPartOption("science", OptionKind.SUBJECT_CHOICE, "SCIENCE", "Science",
                1, List.of(1), List.of(a, b), List.of(), true);
        var branch = new ExamPartOption("branch", OptionKind.BRANCH, "BRANCH", "Branch",
                null, null, List.of(), List.of(), true);
        var part = new ExamPart("p", 1, "Part", 20, 1, 1, 1,
                new PartParticipation(ParticipationMode.CHOICE, 2), null, List.of(), List.of(choice, branch));
        var input = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        var solveChoice = (SolvePart.Choice) input.parts().getFirst();
        assertThat(solveChoice.choose()).isEqualTo(2);
        assertThat(solveChoice.options()).hasSize(2);
        var subjectChoice = (SubjectChoice) solveChoice.options().getFirst();
        assertThat(subjectChoice.choose()).isEqualTo(1);
        assertThat(subjectChoice.sections()).extracting(SectionOnPath::questionCount).containsOnlyNulls();
    }
}
