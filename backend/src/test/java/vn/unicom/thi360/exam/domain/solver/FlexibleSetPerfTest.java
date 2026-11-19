package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("perf")
class FlexibleSetPerfTest {
    @Test
    void solves120QuestionsWith18FlexibleChildren() {
        List<SectionOnPath> sections = new ArrayList<>();
        Map<String, List<PathCandidate>> pools = new HashMap<>();
        for (int s = 0; s < 4; s++) {
            String id = "single" + s;
            int count = s == 0 ? 12 : 30;
            sections.add(section("subject" + s, s == 0 ? 30 : 30,
                    s == 0 ? new GroupQuota[]{group(id, count), new GroupQuota("sets", GroupQuota.GroupKind.FLEXIBLE_SET,
                            18, null, null, null, SC, null, List.of(), List.of(), null, null)}
                            : new GroupQuota[]{group(id, count)}));
            List<PathCandidate> singles = new ArrayList<>();
            for (int i = 0; i < 300; i++) singles.add(cand(id + i, SC, G10, NB));
            pools.put(id, singles);
        }
        List<PathCandidate> sets = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            List<CountingUnit> children = new ArrayList<>();
            for (int j = 0; j < 3 + i % 3; j++) children.add(unit("c" + i + ":" + j, SC, G10, NB));
            sets.add(new PathCandidate("set" + i, 1, "set" + i, null, children));
        }
        pools.put("sets", sets);
        long start = System.nanoTime();
        var result = new PaperSolver().solvePath(path(sections.toArray(SectionOnPath[]::new)), source(pools), SolveMode.check());
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("PERF flexible 120 ms=" + millis);
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(millis).isLessThan(30_000);
    }
}
