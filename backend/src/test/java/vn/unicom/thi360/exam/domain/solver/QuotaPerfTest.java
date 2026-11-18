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

    @Test void qdaFullFiveCodes() {
        // BE4B-R1-04 / 2B 6.3: QDA FULL — phần bắt buộc Toán (50: 35 TN + 15 điền) và Văn (50)
        // cộng phần 3 năm mã trên subjectPool sáu môn; mỗi môn pool là một bài toán con dùng chung
        // cho mọi mã chứa nó, nên giải 2 + 6 bài toán con, không phải 2 + 9 lượt môn.
        Map<String, List<PathCandidate>> pools = new HashMap<>();
        SectionOnPath math = new SectionOnPath("MATH", "qda-math", 50,
                new SubjectQuota(SolverTestFixtures.answerQuotas(
                        SolverTestFixtures.dim(SolverTestFixtures.SC, 35),
                        SolverTestFixtures.dim(SolverTestFixtures.SA, 15)), null,
                        List.of(), List.of()),
                List.of(GroupQuota.single("math", 50)));
        List<PathCandidate> mathPool = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            mathPool.add(PathCandidate.standalone("mq" + i, 1,
                    i < 40 ? SolverTestFixtures.SC : SolverTestFixtures.SA, null, null, null));
        }
        pools.put("math", mathPool);
        SectionOnPath lit = SolverTestFixtures.section("LIT", 50, GroupQuota.single("lit", 50));
        List<PathCandidate> litPool = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            litPool.add(PathCandidate.standalone("lq" + i, 1,
                    SolverTestFixtures.SC, null, null, null));
        }
        pools.put("lit", litPool);
        List<SectionOnPath> subjects = new ArrayList<>();
        for (int s = 0; s < 6; s++) {
            // BE4B-R2-02: QĐ05 Tiếng Anh (môn thứ sáu) 50 câu theo R-26/R-41 — mọi mã đạt FULL 150.
            int count = s == 5 ? 50 : 25;
            GroupQuota group = GroupQuota.single("group" + s, count);
            subjects.add(SolverTestFixtures.section("SUB" + s, count, group));
            List<PathCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < count + 10; i++) candidates.add(PathCandidate.standalone(
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
        List<SolvePart> parts = List.of(
                SolverTestFixtures.part("P1", math),
                SolverTestFixtures.part("P2", lit),
                new SolvePart.Choice("QDA-P3", List.of(), options, 1));
        long start = System.nanoTime();
        SolveOutcome outcome = new PaperSolver().solveAll(parts,
                group -> pools.get(group.groupId()), SolveMode.check());
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("PERF QDA-full-five-codes ms=" + millis + " status=" + outcome.status());
        assertThat(millis).isLessThan(30_000);
        assertThat(outcome.status()).isEqualTo(SolveStatus.VALID);
        assertThat(outcome.results()).hasSize(5);
        // 5 mã = 5 đường; mỗi môn pool giải đúng một lần + 2 phần bắt buộc = 8 bài toán con,
        // phần còn lại của 9 lượt môn qua mã lấy từ bộ nhớ
        assertThat(outcome.stats().pathsEnumerated()).isEqualTo(5);
        assertThat(outcome.stats().subproblemsSolved()).isEqualTo(8);
        assertThat(outcome.stats().subproblemsMemoHit()).isGreaterThanOrEqualTo(5);
        // R-41: mọi mã FULL 150 câu — Toán 50 + Văn 50 + (25 + 25 khoa học | 50 Tiếng Anh).
        for (PathResult result : outcome.results()) {
            assertThat(result.witness().stream().mapToInt(SubjectWitness::resolvedCount).sum())
                    .as("path=%s witness=%s", result.path().chosen(), result.witness())
                    .isEqualTo(150);
        }
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
