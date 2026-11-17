package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("perf")
class QuotaPerfTest {
    @Test void hsaLiteratureSlots() {
        List<GroupQuota> groups = new ArrayList<>();
        Map<String, List<PathCandidate>> pools = new HashMap<>();
        for (int g = 0; g < 5; g++) {
            GroupQuota group = GroupQuota.set("slot" + g, 1, 5, null);
            groups.add(group);
            List<PathCandidate> candidates = new ArrayList<>();
            for (int c = 0; c < 50; c++) {
                String id = "s" + g + "-" + c;
                List<CountingUnit> units = new ArrayList<>();
                for (int u = 0; u < 5; u++) units.add(new CountingUnit(id + "-" + u,
                        "SINGLE_CHOICE", null, null, List.of(), List.of(), null));
                candidates.add(new PathCandidate(id, 1, id, null, units));
            }
            pools.put(group.groupId(), candidates);
        }
        SectionOnPath section = new SectionOnPath("LIT", "lit", 25, SubjectQuota.NONE, groups);
        timed("HSA-literature-5-slots", () -> new PaperSolver().solvePath(
                SolverTestFixtures.path(section), g -> pools.get(g.groupId()), SolveMode.check()).status());
    }

    @Test void hsaMathThreeTopics() { mathTopics(3); }
    @Test void hsaMathEightTopics() { mathTopics(8); }

    private void mathTopics(int count) {
        List<TopicQuota> quotas = new ArrayList<>();
        for (int i = 0; i < count; i++) quotas.add(new TopicQuota("topic" + i,
                List.of("node" + i), 4, List.of(), List.of(), List.of()));
        GroupQuota group = GroupQuota.single("math", 50);
        SectionOnPath section = SolverTestFixtures.section("MATH", 50,
                new SubjectQuota(null, null, quotas), group);
        List<PathCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            List<String> nodes = new ArrayList<>();
            for (int bit = 0; bit < count; bit++) if (((i + 1) & (1 << bit)) != 0) nodes.add("node" + bit);
            candidates.add(new PathCandidate("q" + i, 1, null, null,
                    List.of(new CountingUnit(null, "SINGLE_CHOICE", "G12", "HARD", nodes, List.of(), null))));
        }
        timed("HSA-math-" + count + "-topics", () -> new PaperSolver().solvePath(
                SolverTestFixtures.path(section), ignored -> candidates, SolveMode.check()).status());
    }

    @Test void qdaFiveCodes() {
        List<SectionOnPath> subjects = new ArrayList<>();
        Map<String, List<PathCandidate>> pools = new HashMap<>();
        for (int s = 0; s < 6; s++) {
            GroupQuota group = GroupQuota.single("group" + s, 25);
            subjects.add(SolverTestFixtures.section("SUB" + s, 25, group));
            List<PathCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < 50; i++) candidates.add(PathCandidate.standalone(
                    "q" + s + "-" + i, 1, "SINGLE_CHOICE", null, null, null));
            pools.put(group.groupId(), candidates);
        }
        int[][] codes = {{0,1}, {1,2}, {0,2}, {3,4}, {5}};
        List<SolveOption> options = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            List<SectionOnPath> sections = new ArrayList<>();
            for (int subject : codes[i]) sections.add(subjects.get(subject));
            options.add(new SolveOption.Sections("QD" + i, sections));
        }
        SolvePart part = new SolvePart.Choice("QDA", List.of(), options, 1);
        timed("QDA-five-codes", () -> new PaperSolver().solveAll(List.of(part),
                group -> pools.get(group.groupId()), SolveMode.check()).status());
    }

    private void timed(String name, java.util.function.Supplier<SolveStatus> solve) {
        long start = System.nanoTime();
        SolveStatus status = solve.get();
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("PERF " + name + " ms=" + millis + " status=" + status);
        assertThat(millis).isLessThan(30_000);
        assertThat(status).isEqualTo(SolveStatus.VALID);
    }
}
