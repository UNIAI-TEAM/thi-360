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
        var scope = new KnowledgeScope(List.of(root), List.of("C1"));
        var matrix = new BlockMatrix(MatrixMode.COMBINED, MatrixUnit.PERCENT,
                List.of(new MatrixCell("G10", "NB", BigDecimal.valueOf(100))),
                List.of(), List.of(), List.of(new MatrixDimensionValue("C1", BigDecimal.valueOf(100))));
        var block = new QuestionBlock("set", "Set", QuestionType.QUESTION_SET, 2, 1, 2,
                null, matrix, null, null, false, BigDecimal.ONE, List.of("TAG"),
                new BlockExpectedScoring(null, null, "ALL_OR_NOTHING"), true, false, List.of(),
                List.of(), null, List.of(), false, List.of(), null);
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
    @Test
    void buildsCodePathsInCatalogSubjectOrderAndReusesSubproblems() {
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        UUID kind = UUID.randomUUID();
        List<ExamSection> pool = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new ExamSection("s" + i, "S" + i, 0, null, null, null, null, List.of()))
                .toList();
        int[][] members = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {5}};
        var combinations = new java.util.ArrayList<vn.unicom.thi360.catalog.spi.CatalogCombinationView>();
        for (int i = 0; i < members.length; i++) {
            var combination = mock(vn.unicom.thi360.catalog.spi.CatalogCombinationView.class);
            when(combination.itemCode()).thenReturn("QD0" + (i + 1));
            when(combination.subjectCodes()).thenReturn(java.util.Arrays.stream(members[i]).mapToObj(n -> "S" + n).toList());
            combinations.add(combination);
        }
        when(catalog.listCombinations(kind)).thenReturn(combinations);
        var part = new ExamPart("p3", 1, "Part", null, null, null, null, null, null,
                List.of(), List.of(), pool, new CodeSource("GLOBAL_LIST", null));
        var input = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(kind, new ExamStructureDocument(List.of(part)));
        var choice = (SolvePart.Choice) input.parts().getFirst();
        assertThat(choice.options()).hasSize(5);
        var second = (SolveOption.Sections) choice.options().get(1);
        assertThat(second.optionId()).isEqualTo("p3:QD02");
        assertThat(second.sections()).extracting(SectionOnPath::subjectCode).containsExactly("S1", "S2");
        var outcome = new PaperSolver().solveAll(input.parts(), input.candidates(), SolveMode.check());
        assertThat(outcome.results()).hasSize(5);
        assertThat(outcome.stats().subproblemsSolved()).isEqualTo(6);
    }

    @Test
    void excludesOmittedBlocksAndReferenceGradeButPassesNewFilters() {
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var subject = mock(CatalogItemView.class);
        when(subject.id()).thenReturn(UUID.randomUUID());
        when(catalog.findActiveItem("SUBJECT", "EN")).thenReturn(Optional.of(subject));
        var matrix = new BlockMatrix(MatrixMode.SEPARATE, MatrixUnit.COUNT, List.of(),
                List.of(new MatrixDimensionValue("G12", BigDecimal.ONE)),
                List.of(new MatrixDimensionValue("NB", BigDecimal.ONE)), List.of(),
                QuotaEnforcement.REFERENCE, QuotaEnforcement.REQUIRED, QuotaBasis.QUESTION);
        var block = new QuestionBlock("b", "B", QuestionType.SHORT_ANSWER, 1, null, null,
                null, null, null, null, false, null, List.of("A", "B"), null,
                true, false, List.of(), List.of(), new ItemShape(null, null, null),
                List.of("INTEGER"), false, List.of(), null);
        var omitted = new QuestionBlock("o", "O", QuestionType.ESSAY, 1, null, null,
                null, null, null, null, false, null, List.of(), null,
                true, false, List.of(), List.of(), null, List.of(), true, List.of(), null);
        var section = new ExamSection("s", "EN", 1, null, null, matrix, null, List.of(block, omitted));
        var part = new ExamPart("p", 1, "Part", null, null, null, null, null, null, List.of(section), List.of());
        var input = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        var result = ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst();
        assertThat(result.groups()).extracting(GroupQuota::groupId).containsExactly("b");
        var quota = (MatrixQuota.Separate) result.subjectQuota().matrix();
        assertThat(quota.grades()).isEmpty();
        assertThat(quota.difficulties()).containsExactly(new DimensionQuota("NB", 1));
        input.candidates().candidatesFor(result.groups().getFirst());
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).findCandidates(filter.capture(), eq(20001));
        assertThat(filter.getValue().taskTypeCode()).isNull();
        assertThat(filter.getValue().tagCodes()).containsExactly("A", "B");
        assertThat(filter.getValue().answerFormats()).containsExactly(vn.unicom.thi360.questionbank.spi.AnswerFormat.INTEGER);
        UUID id = UUID.randomUUID();
        var unit = new QuestionSourcePort.Unit(id, null, QuestionType.SHORT_ANSWER, null, "NB",
                List.of(), List.of(), BigDecimal.ONE);
        when(source.findCandidates(any(), eq(20001))).thenReturn(List.of(
                new QuestionSourcePort.Candidate(id, 1, null, List.of(unit), null, null)));
        var solveInput = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        var outcome = new PaperSolver().solveAll(solveInput.parts(), solveInput.candidates(), SolveMode.check());
        assertThat(outcome.status()).isEqualTo(SolveStatus.VALID);
    }

    @Test
    void slotsHaveIndependentFiltersAndShareParentQuota() {
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var subject = mock(CatalogItemView.class);
        when(subject.id()).thenReturn(UUID.randomUUID());
        when(catalog.findActiveItem("SUBJECT", "LIT")).thenReturn(Optional.of(subject));
        UUID parentNode = UUID.randomUUID();
        UUID slotNode = UUID.randomUUID();
        var parentItem = mock(CatalogItemView.class);
        var slotItem = mock(CatalogItemView.class);
        when(parentItem.id()).thenReturn(parentNode);
        when(parentItem.path()).thenReturn("/parent/");
        when(slotItem.id()).thenReturn(slotNode);
        when(slotItem.path()).thenReturn("/slot/");
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of(parentItem, slotItem));
        var matrix = new BlockMatrix(MatrixMode.SEPARATE, MatrixUnit.COUNT, List.of(),
                List.of(new MatrixDimensionValue("G12", BigDecimal.valueOf(10))), List.of(), List.of());
        var slots = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new BlockSlot(i, new KnowledgeScope(List.of(slotNode), List.of()), List.of("TAG" + i))).toList();
        var block = new QuestionBlock("literature", "Literature", QuestionType.QUESTION_SET, 10, 5, 2,
                new KnowledgeScope(List.of(parentNode), List.of()), matrix, QuestionType.SINGLE_CHOICE, null, false, null, List.of("PARENT"), null,
                true, false, slots, List.of(), null, List.of(), false, List.of(), null);
        var section = new ExamSection("s", "LIT", 10, null, null, null, null, List.of(block));
        var part = new ExamPart("p", 1, "Part", null, null, null, null, null, null, List.of(section), List.of());
        var input = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        var groups = ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst().groups();
        assertThat(groups).hasSize(5);
        assertThat(groups).extracting(GroupQuota::slotParent).containsOnly(groups.getFirst().slotParent());
        assertThat(groups.getFirst().slotParent().matrix()).isNotNull();
        assertThat(groups).allSatisfy(g -> assertThat(g.matrix()).isNull());
        for (var group : groups) input.candidates().candidatesFor(group);
        var filters = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source, org.mockito.Mockito.times(5)).findCandidates(filters.capture(), eq(20001));
        assertThat(filters.getAllValues()).allSatisfy(f -> assertThat(f.tagCodes()).containsExactly("PARENT"));
        assertThat(filters.getAllValues()).allSatisfy(f -> {
            assertThat(f.knowledgeGroups()).containsExactly(List.of(parentNode));
            assertThat(f.additionalKnowledgeGroups()).containsExactly(List.of(List.of(slotNode)));
        });
        assertThat(filters.getAllValues()).extracting(QuestionSourcePort.Filter::additionalTagGroups)
                .containsExactly(List.of(List.of("TAG1")), List.of(List.of("TAG2")), List.of(List.of("TAG3")),
                        List.of(List.of("TAG4")), List.of(List.of("TAG5")));
    }

    @Test
    void pronunciationAndStressGroupsStaySeparateAcrossSeeds() {
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var subject = mock(CatalogItemView.class);
        when(subject.id()).thenReturn(UUID.randomUUID());
        when(catalog.findActiveItem("SUBJECT", "EN")).thenReturn(Optional.of(subject));
        UUID pronunciation = UUID.randomUUID();
        UUID stress = UUID.randomUUID();
        var first = new QuestionSourcePort.Candidate(pronunciation, 1, null, List.of(
                new QuestionSourcePort.Unit(pronunciation, null, QuestionType.SINGLE_CHOICE, null, null,
                        List.of(), List.of(), BigDecimal.ONE)), null, null);
        var second = new QuestionSourcePort.Candidate(stress, 1, null, List.of(
                new QuestionSourcePort.Unit(stress, null, QuestionType.SINGLE_CHOICE, null, null,
                        List.of(), List.of(), BigDecimal.ONE)), null, null);
        when(source.findCandidates(any(), eq(20001))).thenAnswer(call -> {
            QuestionSourcePort.Filter filter = call.getArgument(0);
            if (filter.tagCodes().equals(List.of("PRONUNCIATION"))) return List.of(first);
            if (filter.tagCodes().equals(List.of("STRESS"))) return List.of(second);
            return List.of(first, second);
        });
        var b46 = new QuestionBlock("q46", "46", QuestionType.SINGLE_CHOICE, 1, null, null,
                null, null, null, null, false, null, List.of("PRONUNCIATION"), null,
                true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
        var b47 = new QuestionBlock("q47", "47", QuestionType.SINGLE_CHOICE, 1, null, null,
                null, null, null, null, false, null, List.of("STRESS"), null,
                true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
        var section = new ExamSection("s", "EN", 2, null, null, null, null, List.of(b46, b47));
        var part = new ExamPart("p", 1, "Part", null, null, null, null, null, null, List.of(section), List.of());
        var input = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        for (int seed = 0; seed < 200; seed++) {
            var outcome = new PaperSolver().solveAll(input.parts(), input.candidates(), SolveMode.generate(seed));
            assertThat(outcome.status()).isEqualTo(SolveStatus.VALID);
            var groups = outcome.results().getFirst().witness().getFirst().groups();
            assertThat(groups).filteredOn(g -> g.groupId().equals("q46")).singleElement()
                    .satisfies(g -> assertThat(g.picks()).extracting(QuestionPick::questionId).containsExactly(pronunciation.toString()));
            assertThat(groups).filteredOn(g -> g.groupId().equals("q47")).singleElement()
                    .satisfies(g -> assertThat(g.picks()).extracting(QuestionPick::questionId).containsExactly(stress.toString()));
        }
    }
}
