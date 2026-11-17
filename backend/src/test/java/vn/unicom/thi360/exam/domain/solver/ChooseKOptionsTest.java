package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.SolverInputBuilder;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamStructureValidator;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

class ChooseKOptionsTest {
    @Test
    void weightedChooseTwoMatchesDocumentPathCount() throws Exception {
        String json = """
                {"parts":[{"id":"p","questionCount":2,"participation":{"mode":"CHOICE","choose":2},"options":[
                {"id":"first","kind":"SUBJECT_CHOICE","code":"F","chooseSubjects":1,"countsByPick":[1],
                 "sections":[{"id":"a","subjectCode":"A","choiceGroup":"NATURAL","blocks":[{"id":"ga","questionType":"SINGLE_CHOICE"}]},
                             {"id":"b","subjectCode":"B","choiceGroup":"NATURAL","blocks":[{"id":"gb","questionType":"SINGLE_CHOICE"}]}]},
                {"id":"second","kind":"SUBJECT_CHOICE","code":"S","chooseSubjects":1,"countsByPick":[1],
                 "sections":[{"id":"c","subjectCode":"C","choiceGroup":"NATURAL","blocks":[{"id":"gc","questionType":"SINGLE_CHOICE"}]},
                             {"id":"d","subjectCode":"D","choiceGroup":"NATURAL","blocks":[{"id":"gd","questionType":"SINGLE_CHOICE"}]},
                             {"id":"e","subjectCode":"E","choiceGroup":"NATURAL","blocks":[{"id":"ge","questionType":"SINGLE_CHOICE"}]}]},
                {"id":"fixed","kind":"SUBJECT","code":"X","sections":[{"id":"x","subjectCode":"X","questionCount":1,
                 "blocks":[{"id":"gx","questionType":"SINGLE_CHOICE","questionCount":1}]}]}
                ]}]}
                """;
        var document = new ObjectMapper().readValue(json, ExamStructureDocument.class);
        ExamStructureValidator.validate(document);
        var builder = new SolverInputBuilder(mock(CatalogLookup.class), mock(QuestionSourcePort.class), SolverLimits.defaults());
        var input = builder.build(UUID.randomUUID(), document);
        SolveOutcome outcome = new PaperSolver().solveAll(input.parts(), ChooseKOptionsTest::source, SolveMode.check());
        assertThat(outcome.stats().pathsEnumerated()).isEqualTo(ExamStructureValidator.countPaths(document));
    }

    @Test
    void choosesTwoOptionsInLexicographicOrderIncludingSubjectChoice() {
        SolveOption a = regular("a");
        SolveOption b = regular("b");
        SubjectChoice science = choice("science", 2);
        SolvePart part = SolvePart.Choice.of("p", List.of(a, b, science), 2);

        SolveOutcome result = new PaperSolver().solveAll(List.of(part), ChooseKOptionsTest::source, SolveMode.check());

        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.stats().pathsEnumerated()).isEqualTo(5);
        assertThat(result.results()).hasSize(3);
        assertThat(result.results().stream().map(path -> path.path().chosen().stream()
                .map(ChosenOption::optionId).toList()).toList())
                .containsExactly(List.of("a", "b"), List.of("a", "science"), List.of("b", "science"));
        assertThat(result.results().get(1).path().picks().getFirst()).hasSize(2);
        assertThat(result.results().get(2).path().picks().getFirst()).hasSize(2);
    }
    @Test
    void chosenSubjectChoicesExpandTogether() {
        SolvePart part = SolvePart.Choice.of("p", List.of(choice("first", 2), choice("second", 3)), 2);
        SolveOutcome result = new PaperSolver().solveAll(List.of(part), ChooseKOptionsTest::source, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.stats().pathsEnumerated()).isEqualTo(6);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().getFirst().path().picks()).hasSize(2);
        assertThat(result.results().getFirst().witness()).hasSize(5);
    }


    @Test
    void selectedSubjectChoicesMultiplyWeightsAndEnforcePathLimit() {
        SubjectChoice first = choice("first", 2);
        SubjectChoice second = choice("second", 3);
        SolvePart part = SolvePart.Choice.of("p", List.of(first, second, regular("fixed")), 2);
        AtomicInteger calls = new AtomicInteger();
        PaperSolver solver = new PaperSolver(new SolverLimits(10, 20000, Duration.ofSeconds(30), 8), Clock.systemUTC());

        SolveOutcome result = solver.solveAll(List.of(part), group -> {
            calls.incrementAndGet();
            return source(group);
        }, SolveMode.check());

        // first+second = 2*3, first+fixed = 2, second+fixed = 3.
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.PATH_LIMIT_EXCEEDED);
        assertThat(result.stats().pathsEnumerated()).isEqualTo(11);
        assertThat(calls).hasValue(0);
    }

    @Test
    void stridesUseCombinationCountsAcrossParts() {
        SolvePart first = SolvePart.Choice.of("p1", List.of(regular("a"), regular("b"), regular("c")), 2);
        SolvePart second = SolvePart.Choice.of("p2", List.of(regular("x"), regular("y")));
        SolveOutcome result = new PaperSolver().solveAll(List.of(first, second), ChooseKOptionsTest::source, SolveMode.check());

        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.results().stream().map(path -> path.path().chosen().stream()
                .map(ChosenOption::optionId).toList()).toList()).containsExactly(
                        List.of("a", "b", "x"), List.of("a", "b", "y"),
                        List.of("a", "c", "x"), List.of("a", "c", "y"),
                        List.of("b", "c", "x"), List.of("b", "c", "y"));
    }

    @Test
    void twentyOneChooseTwoExceedsTwoHundredPaths() {
        List<SolveOption> options = IntStream.range(0, 21).mapToObj(i -> regular("o" + i)).toList();
        SolveOutcome result = new PaperSolver().solveAll(
                List.of(SolvePart.Choice.of("p", options, 2)), ChooseKOptionsTest::source, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.PATH_LIMIT_EXCEEDED);
        assertThat(result.stats().pathsEnumerated()).isEqualTo(210);
    }

    private static SolveOption regular(String id) {
        return new SolveOption.Sections(id, List.of(new SectionOnPath(id, "section-" + id, 1,
                SubjectQuota.NONE, List.of(GroupQuota.single("group-" + id, 1)))));
    }

    private static SubjectChoice choice(String id, int subjects) {
        List<SectionOnPath> sections = IntStream.range(0, subjects).mapToObj(i ->
                new SectionOnPath(id + i, id + "-section-" + i, null, SubjectQuota.NONE,
                        List.of(GroupQuota.remainder(id + "-group-" + i)))).toList();
        return new SubjectChoice(id, sections, 1, List.of(1));
    }

    private static List<PathCandidate> source(GroupQuota group) {
        return List.of(PathCandidate.standalone(group.groupId(), 1, "SINGLE_CHOICE", "G10", "NB", null));
    }
}
