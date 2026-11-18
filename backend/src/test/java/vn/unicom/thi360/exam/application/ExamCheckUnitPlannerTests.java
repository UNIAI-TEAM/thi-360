package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;

class ExamCheckUnitPlannerTests {
    @Test
    void groupsSameSubjectAcrossPartsAndReusesASectionAcrossCodes() {
        var math1 = section("MATH", "m1", 10);
        var math2 = section("MATH", "m2", 10);
        var physics = section("PHYSICS", "p", 5);
        var chemistry = section("CHEMISTRY", "c", 5);
        var biology = section("BIOLOGY", "b", 5);
        var parts = List.<SolvePart>of(SolvePart.Fixed.of("one", math1), SolvePart.Fixed.of("two", math2),
                SolvePart.Choice.of("three", List.of(
                        new SolveOption.Sections("code1", List.of(physics, chemistry)),
                        new SolveOption.Sections("code2", List.of(physics, biology)))));
        var units = ExamCheckUnitPlanner.plan(parts, 200, 8).units();
        assertThat(units).extracting(unit -> unit.key().subjectCode()).containsExactly("MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY");
        assertThat(units.getFirst().key().sectionIds()).containsExactly("m1", "m2");
    }

    @Test
    void mutuallyExclusiveSectionsOfSameSubjectAreNotSolvedTogether() {
        var first = section("MATH", "a", 10);
        var second = section("MATH", "b", 10);
        var units = ExamCheckUnitPlanner.plan(List.of(SolvePart.Choice.of("p", List.of(
                SolveOption.Sections.of("a", first), SolveOption.Sections.of("b", second)))), 200, 8).units();
        assertThat(units).extracting(unit -> unit.key().sectionIds()).containsExactly(List.of("a"), List.of("b"));
    }

    @Test
    void subjectChoiceUsesFullQuestionCountAndLimitsPaths() {
        var choice = new SubjectChoice("science", List.of(section("A", "a", null), section("B", "b", null),
                section("C", "c", null), section("D", "d", null)), 3, List.of(17, 17, 16));
        var parts = List.<SolvePart>of(SolvePart.Choice.of("p", List.of(choice)));
        var plan = ExamCheckUnitPlanner.plan(parts, 4, 8);
        var units = plan.units();
        assertThat(units).hasSize(4).allSatisfy(unit ->
                assertThat(unit.sections().getFirst().questionCount()).isEqualTo(17));
        assertThat(plan.paths()).hasSize(1);
        assertThat(plan.paths().getFirst().tuples()).hasSize(4);
        assertThat(plan.paths().getFirst().path().picks().getFirst()).hasSize(4);
        assertThatThrownBy(() -> ExamCheckUnitPlanner.plan(parts, 3, 8))
                .isInstanceOf(ExamCheckUnitPlanner.TooManyPaths.class);
        assertThatThrownBy(() -> ExamCheckUnitPlanner.plan(parts, 20, 3))
                .isInstanceOf(ExamCheckUnitPlanner.TooManyChoiceSubjects.class);
    }

    private static SectionOnPath section(String subject, String id, Integer count) {
        return new SectionOnPath(subject, id, count, null, List.of());
    }
}
