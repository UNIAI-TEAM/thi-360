package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-2: đối chiếu {@link PaperSolver} với bộ duyệt hết trên các ca nhỏ sinh ngẫu nhiên có hạt giống.
 *
 * <p>Bộ oracle dưới đây cài đặt lại độc lập ngữ nghĩa ràng buộc (khớp chỉ tiêu, lấy nguyên vẹn, khóa duy
 * nhất) và duyệt mọi tập con ứng viên theo khối. Ca nào vượt ngân sách nút duyệt thì bỏ qua và đếm riêng.
 */
class PaperSolverOracleTest {

    private static final int CASES = 600;
    private static final long NODE_BUDGET = 3_000_000;

    private static final String[] GRADES = {"G1", "G2"};
    private static final String[] DIFFS = {"D1", "D2"};
    private static final String[] COMPS = {"C1", "C2"};
    private static final String[] NODES = {"N1", "N2", "N3"};
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final PaperSolver solver = new PaperSolver();

    @Test
    @DisplayName("500+ ca ngẫu nhiên nhỏ: VALID/INVALID của bộ giải khớp duyệt hết; đề chứng minh qua kiểm chứng độc lập")
    void oracleComparison() {
        Random rng = new Random(20261116L);
        int valid = 0, invalid = 0, unconfirmed = 0, skipped = 0;
        for (int c = 0; c < CASES; c++) {
            Case cs = genCase(rng, c);
            Oracle oracle = new Oracle(cs);
            Boolean oracleSolvable = oracle.solvable();
            if (oracleSolvable == null) {
                skipped++;
                continue;
            }
            SolveMode mode = (c % 4 == 3) ? SolveMode.generate(1000 + c) : SolveMode.check();
            PathResult r = solver.solve(cs.path(), cs.candidates(), mode);
            if (r.status() == SolveStatus.UNCONFIRMED) {
                unconfirmed++;
                continue;
            }
            if (oracleSolvable) {
                assertThat(r.status()).as("case %d expected VALID:\n%s", c, describe(cs))
                        .isEqualTo(SolveStatus.VALID);
                validateWitness(cs, r);
                valid++;
            } else {
                assertThat(r.status()).as("case %d expected INVALID", c).isEqualTo(SolveStatus.INVALID);
                invalid++;
            }
        }
        assertThat(valid + invalid).as("compared cases").isGreaterThanOrEqualTo(500);
        assertThat(unconfirmed).as("UNCONFIRMED in small oracle cases").isZero();
        assertThat(valid).as("VALID cases").isPositive();
        assertThat(invalid).as("INVALID cases").isPositive();
        assertThat(skipped).as("cases skipped by oracle budget").isLessThan(CASES / 10);
    }

    // ---------- sinh ca ----------

    private record Case(SolvePath path, List<List<PathCandidate>> candidates) {
    }

    private String describe(Case cs) {
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < cs.path().blocks().size(); b++) {
            sb.append("block ").append(cs.path().blocks().get(b)).append('\n');
            for (PathCandidate c : cs.candidates().get(b)) {
                sb.append("  cand ").append(c).append('\n');
            }
        }
        return sb.toString();
    }

    private Case genCase(Random rng, int idx) {
        int blockCount = 1 + rng.nextInt(4);
        List<BlockQuota> blocks = new ArrayList<>();
        List<List<PathCandidate>> candLists = new ArrayList<>();
        Map<String, PathCandidate> sharedPool = new HashMap<>();
        for (int b = 0; b < blockCount; b++) {
            BlockQuota block = genBlock(rng, "B" + b);
            blocks.add(block);
            candLists.add(genCandidates(rng, block, sharedPool, idx * 1000 + b * 100));
        }
        return new Case(new SolvePath(0, List.of(), blocks), candLists);
    }

    private BlockQuota genBlock(Random rng, String id) {
        int qc = 1 + rng.nextInt(6);
        boolean setBlock = rng.nextDouble() < 0.25;
        Integer setCount = null, setSize = null;
        if (setBlock) {
            setSize = 1 + rng.nextInt(3);
            int divisors = qc / setSize;
            if (divisors == 0) {
                setSize = qc;
                divisors = 1;
            }
            setCount = divisors;
            qc = setCount * setSize;
        }
        MatrixQuota matrix;
        if (rng.nextDouble() < 0.6) {
            List<CellQuota> cells = new ArrayList<>();
            for (String g : GRADES) {
                for (String d : DIFFS) {
                    if (rng.nextDouble() < 0.8) {
                        cells.add(new CellQuota(g, d, 0));
                    }
                }
            }
            if (cells.isEmpty()) {
                cells.add(new CellQuota("G1", "D1", 0));
            }
            distributeQuotas(rng, cells, CellQuota::quota, qc,
                    (i, q) -> cells.set(i, new CellQuota(cells.get(i).gradeCode(),
                            cells.get(i).difficultyCode(), q)));
            maybePerturb(rng, cells, CellQuota::quota,
                    (i, q) -> cells.set(i, new CellQuota(cells.get(i).gradeCode(),
                            cells.get(i).difficultyCode(), q)));
            matrix = new MatrixQuota.Combined(cells);
        } else {
            List<DimensionQuota> gs = new ArrayList<>();
            List<DimensionQuota> ds = new ArrayList<>();
            for (String g : GRADES) {
                if (rng.nextDouble() < 0.9) {
                    gs.add(new DimensionQuota(g, 0));
                }
            }
            for (String d : DIFFS) {
                if (rng.nextDouble() < 0.9) {
                    ds.add(new DimensionQuota(d, 0));
                }
            }
            distributeQuotas(rng, gs, DimensionQuota::quota, qc,
                    (i, q) -> gs.set(i, new DimensionQuota(gs.get(i).code(), q)));
            distributeQuotas(rng, ds, DimensionQuota::quota, qc,
                    (i, q) -> ds.set(i, new DimensionQuota(ds.get(i).code(), q)));
            maybePerturb(rng, gs, DimensionQuota::quota,
                    (i, q) -> gs.set(i, new DimensionQuota(gs.get(i).code(), q)));
            matrix = new MatrixQuota.Separate(gs, ds);
        }
        List<CompetencyQuota> comps = new ArrayList<>();
        if (rng.nextDouble() < 0.3) {
            for (String c : COMPS) {
                if (rng.nextDouble() < 0.6) {
                    comps.add(new CompetencyQuota(c, rng.nextInt(qc + 1)));
                }
            }
        }
        List<MapidGroupQuota> groups = new ArrayList<>();
        if (rng.nextDouble() < 0.2) {
            groups.add(new MapidGroupQuota("M1", List.of("N1", "N2"), rng.nextInt(qc + 1)));
            if (rng.nextDouble() < 0.4) {
                groups.add(new MapidGroupQuota("M2", List.of("N2", "N3"), rng.nextInt(qc + 1)));
            }
        }
        BigDecimal requiredPoints = rng.nextDouble() < 0.15 ? ONE : null;
        String scoring = rng.nextDouble() < 0.15 ? "RULE_A" : null;
        return new BlockQuota(id, qc, setCount, setSize, matrix, comps, groups, requiredPoints, scoring);
    }

    private interface Setter<T> {
        void set(int index, int quota);
    }

    private <T> void distributeQuotas(Random rng, List<T> list,
            java.util.function.ToIntFunction<T> get, int total, Setter<T> set) {
        if (list.isEmpty()) {
            return;
        }
        // phân bổ ngẫu nhiên 'total' vào các phần tử
        for (int i = 0; i < total; i++) {
            int idx = rng.nextInt(list.size());
            set.set(idx, get.applyAsInt(list.get(idx)) + 1);
        }
    }

    private <T> void maybePerturb(Random rng, List<T> list,
            java.util.function.ToIntFunction<T> get, Setter<T> set) {
        // 25% số ca: cộng/trừ 1 để tổng lệch số câu (ca INVALID cấu hình)
        if (list.isEmpty() || rng.nextDouble() >= 0.25) {
            return;
        }
        int idx = rng.nextInt(list.size());
        int q = get.applyAsInt(list.get(idx));
        set.set(idx, Math.max(0, q + (rng.nextBoolean() ? 1 : -1)));
    }

    private List<PathCandidate> genCandidates(Random rng, BlockQuota block,
            Map<String, PathCandidate> sharedPool, int idBase) {
        int n = rng.nextInt(13);
        List<PathCandidate> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // đôi khi tái dùng một ứng viên của khối trước (cùng đối tượng → chung khóa)
            if (!sharedPool.isEmpty() && rng.nextDouble() < 0.12) {
                List<PathCandidate> pool = new ArrayList<>(sharedPool.values());
                out.add(pool.get(rng.nextInt(pool.size())));
                continue;
            }
            String qid = "c" + (idBase + i);
            PathCandidate c;
            double kind = rng.nextDouble();
            if (kind < 0.6) {
                c = new PathCandidate(qid, 1, null, scoringRule(rng, block),
                        List.of(genUnit(rng, null, block)));
            } else if (kind < 0.85) {
                int u = 1 + rng.nextInt(3);
                List<CountingUnit> units = new ArrayList<>(u);
                for (int k = 0; k < u; k++) {
                    units.add(genUnit(rng, qid + "-k" + k, block));
                }
                c = new PathCandidate(qid, 1, qid, scoringRule(rng, block), units);
            } else {
                int u = 2 + rng.nextInt(2);
                List<CountingUnit> units = new ArrayList<>(u);
                for (int k = 0; k < u; k++) {
                    units.add(genUnit(rng, null, block));
                }
                c = new PathCandidate(qid, 1, qid, scoringRule(rng, block), units);
            }
            out.add(c);
            sharedPool.put(qid, c);
        }
        return out;
    }

    private String scoringRule(Random rng, BlockQuota block) {
        if (block.expectedScoringRule() != null) {
            return rng.nextDouble() < 0.7 ? block.expectedScoringRule() : "OTHER";
        }
        return "RULE_A";
    }

    private CountingUnit genUnit(Random rng, String childId, BlockQuota block) {
        String g = GRADES[rng.nextInt(GRADES.length)];
        String d = DIFFS[rng.nextInt(DIFFS.length)];
        List<String> comps = new ArrayList<>();
        if (rng.nextDouble() < 0.5) {
            comps.add(COMPS[rng.nextInt(COMPS.length)]);
            if (rng.nextDouble() < 0.3) {
                comps.add(COMPS[rng.nextInt(COMPS.length)]);
            }
        }
        List<String> mapids = new ArrayList<>();
        if (rng.nextDouble() < 0.4) {
            mapids.add(NODES[rng.nextInt(NODES.length)]);
        }
        BigDecimal points = rng.nextDouble() < 0.8 ? ONE : new BigDecimal("0.5");
        return new CountingUnit(childId, g, d, mapids, comps, points);
    }

    // ---------- oracle độc lập ----------

    /** Một chỉ tiêu tái cài độc lập từ BlockQuota. */
    private record OReq(int quota, Predicate<CountingUnit> matcher) {
    }

    private static final class Oracle {
        private final BlockQuota[] blocks;
        private final List<List<PathCandidate>> cands;
        private final OReq[][] reqs;
        private final boolean[][] usable;
        private final int[][][] contrib;
        private final String[][][] keys;
        private long nodes;
        private boolean budgetOut;

        Oracle(Case cs) {
            blocks = cs.path().blocks().toArray(BlockQuota[]::new);
            cands = cs.candidates();
            reqs = new OReq[blocks.length][];
            usable = new boolean[blocks.length][];
            contrib = new int[blocks.length][][];
            keys = new String[blocks.length][][];
            for (int b = 0; b < blocks.length; b++) {
                reqs[b] = reqsOf(blocks[b]);
                int n = cands.get(b).size();
                usable[b] = new boolean[n];
                contrib[b] = new int[n][reqs[b].length];
                keys[b] = new String[n][];
                for (int i = 0; i < n; i++) {
                    PathCandidate c = cands.get(b).get(i);
                    keys[b][i] = keysOf(c);
                    usable[b][i] = computeUsable(blocks[b], reqs[b], c, contrib[b][i]);
                }
            }
        }

        /** @return solvable, hoặc null khi vượt ngân sách duyệt */
        Boolean solvable() {
            int bCount = blocks.length;
            List<int[]> flat = new ArrayList<>();
            for (int b = 0; b < bCount; b++) {
                for (int i = 0; i < cands.get(b).size(); i++) {
                    flat.add(new int[]{b, i});
                }
            }
            int[][] rem = new int[bCount][];
            for (int b = 0; b < bCount; b++) {
                rem[b] = new int[reqs[b].length];
                for (int r = 0; r < reqs[b].length; r++) {
                    rem[b][r] = reqs[b][r].quota();
                }
            }
            Set<String> usedKeys = new HashSet<>();
            int[] unitsSel = new int[bCount];
            boolean result = dfs(0, flat, rem, usedKeys, unitsSel);
            return budgetOut ? null : result;
        }

        private boolean dfs(int idx, List<int[]> flat, int[][] rem, Set<String> usedKeys,
                int[] unitsSel) {
            if (++nodes > NODE_BUDGET) {
                budgetOut = true;
                return false;
            }
            if (idx == flat.size()) {
                for (int b = 0; b < blocks.length; b++) {
                    if (unitsSel[b] != blocks[b].questionCount()) {
                        return false;
                    }
                    for (int r = 0; r < rem[b].length; r++) {
                        if (rem[b][r] != 0) {
                            return false;
                        }
                    }
                }
                return true;
            }
            int b = flat.get(idx)[0];
            int i = flat.get(idx)[1];
            if (isSatisfied(rem[b]) && unitsSel[b] == blocks[b].questionCount()) {
                // khối đã đủ chỉ tiêu và đủ đơn vị: các ứng viên còn lại không được chọn nữa
                return dfs(idx + 1, flat, rem, usedKeys, unitsSel);
            }
            int units = cands.get(b).get(i).units().size();
            if (usable[b][i] && unitsSel[b] + units <= blocks[b].questionCount()
                    && fitsAndMark(rem[b], contrib[b][i], keys[b][i], usedKeys, true)) {
                unitsSel[b] += units;
                if (dfs(idx + 1, flat, rem, usedKeys, unitsSel)) {
                    return true;
                }
                unitsSel[b] -= units;
                unmark(rem[b], contrib[b][i], keys[b][i], usedKeys);
            }
            return dfs(idx + 1, flat, rem, usedKeys, unitsSel);
        }

        private boolean isSatisfied(int[] rem) {
            for (int v : rem) {
                if (v != 0) {
                    return false;
                }
            }
            return true;
        }

        private boolean fitsAndMark(int[] rem, int[] vec, String[] ks, Set<String> usedKeys,
                boolean mark) {
            for (int r = 0; r < vec.length; r++) {
                if (vec[r] > rem[r]) {
                    return false;
                }
            }
            for (String k : ks) {
                if (usedKeys.contains(k)) {
                    return false;
                }
            }
            if (mark) {
                for (int r = 0; r < vec.length; r++) {
                    rem[r] -= vec[r];
                }
                for (String k : ks) {
                    usedKeys.add(k);
                }
            }
            return true;
        }

        private void unmark(int[] rem, int[] vec, String[] ks, Set<String> usedKeys) {
            for (int r = 0; r < vec.length; r++) {
                rem[r] += vec[r];
            }
            for (String k : ks) {
                usedKeys.remove(k);
            }
        }

        private OReq[] reqsOf(BlockQuota block) {
            List<OReq> out = new ArrayList<>();
            switch (block.matrix()) {
                case MatrixQuota.Combined combined -> {
                    for (CellQuota cell : combined.cells()) {
                        out.add(new OReq(cell.quota(), u -> cell.gradeCode().equals(u.gradeCode())
                                && cell.difficultyCode().equals(u.difficultyCode())));
                    }
                }
                case MatrixQuota.Separate separate -> {
                    for (DimensionQuota g : separate.grades()) {
                        out.add(new OReq(g.quota(), u -> g.code().equals(u.gradeCode())));
                    }
                    for (DimensionQuota d : separate.difficulties()) {
                        out.add(new OReq(d.quota(), u -> d.code().equals(u.difficultyCode())));
                    }
                }
            }
            for (CompetencyQuota c : block.competencies()) {
                out.add(new OReq(c.quota(), u -> u.competencyCodes().contains(c.competencyCode())));
            }
            for (MapidGroupQuota gq : block.mapidGroups()) {
                Set<String> nodes = Set.copyOf(gq.nodeIds());
                out.add(new OReq(gq.quota(), u -> {
                    for (String id : u.mapidIds()) {
                        if (nodes.contains(id)) {
                            return true;
                        }
                    }
                    return false;
                }));
            }
            return out.toArray(OReq[]::new);
        }

        private String[] keysOf(PathCandidate c) {
            List<String> out = new ArrayList<>();
            out.add(c.questionId());
            for (CountingUnit u : c.units()) {
                if (u.childId() != null) {
                    out.add(u.childId());
                }
            }
            return out.toArray(String[]::new);
        }

        private boolean computeUsable(BlockQuota block, OReq[] rs, PathCandidate c, int[] vec) {
            if (c.units().isEmpty()) {
                return false;
            }
            if (block.setCount() != null && c.units().size() != block.setSize()) {
                return false;
            }
            if (block.expectedScoringRule() != null
                    && !block.expectedScoringRule().equals(c.scoringRule())) {
                return false;
            }
            if (block.requiredPointsPerUnit() != null) {
                for (CountingUnit u : c.units()) {
                    if (u.points() == null
                            || u.points().compareTo(block.requiredPointsPerUnit()) != 0) {
                        return false;
                    }
                }
            }
            Set<String> seen = new HashSet<>();
            for (String k : keysOf(c)) {
                if (!seen.add(k)) {
                    return false;
                }
            }
            int[][] groups = requiredGroups(block, rs);
            for (CountingUnit u : c.units()) {
                for (int r = 0; r < rs.length; r++) {
                    if (rs[r].matcher().test(u)) {
                        vec[r]++;
                    }
                }
                for (int[] group : groups) {
                    boolean covered = false;
                    for (int r : group) {
                        if (rs[r].matcher().test(u)) {
                            covered = true;
                            break;
                        }
                    }
                    if (!covered) {
                        return false;
                    }
                }
            }
            for (int r = 0; r < rs.length; r++) {
                if (vec[r] > 0 && rs[r].quota() == 0) {
                    return false;
                }
            }
            return true;
        }

        private int[][] requiredGroups(BlockQuota block, OReq[] rs) {
            List<int[]> groups = new ArrayList<>(2);
            switch (block.matrix()) {
                case MatrixQuota.Combined combined -> {
                    if (!combined.cells().isEmpty()) {
                        groups.add(positive(rs, 0, combined.cells().size()));
                    }
                }
                case MatrixQuota.Separate separate -> {
                    int g = separate.grades().size();
                    int d = separate.difficulties().size();
                    if (g > 0) {
                        groups.add(positive(rs, 0, g));
                    }
                    if (d > 0) {
                        groups.add(positive(rs, g, g + d));
                    }
                }
            }
            if (groups.isEmpty() && rs.length > 0) {
                groups.add(positive(rs, 0, rs.length));
            }
            return groups.toArray(int[][]::new);
        }

        private static int[] positive(OReq[] rs, int from, int to) {
            List<Integer> out = new ArrayList<>(to - from);
            for (int r = from; r < to; r++) {
                if (rs[r].quota() > 0) {
                    out.add(r);
                }
            }
            int[] res = new int[out.size()];
            for (int i = 0; i < res.length; i++) {
                res[i] = out.get(i);
            }
            return res;
        }
    }

    // ---------- kiểm chứng đề độc lập ----------

    /**
     * Kiểm chứng lại đề chứng minh: mọi câu chọn thuộc danh sách đầu vào của đúng khối, chỉ tiêu khớp đúng,
     * khóa không trùng trên cả đường, mỗi đơn vị được tính.
     */
    private void validateWitness(Case cs, PathResult r) {
        assertThat(r.witness()).hasSize(cs.path().blocks().size());
        Set<String> globalKeys = new HashSet<>();
        int[] totalUnits = {0};
        for (int b = 0; b < cs.path().blocks().size(); b++) {
            BlockQuota block = cs.path().blocks().get(b);
            BlockWitness w = r.witness().get(b);
            assertThat(w.blockId()).isEqualTo(block.blockId());
            OReq[] rs = new Oracle(cs).reqsOf(block);
            int[] sum = new int[rs.length];
            int units = 0;
            Set<String> seenIds = new HashSet<>();
            for (QuestionPick pick : w.picks()) {
                PathCandidate c = findCandidate(cs.candidates().get(b), pick);
                assertThat(c).as("pick %s must come from block %s input", pick, block.blockId())
                        .isNotNull();
                for (String k : new Oracle(cs).keysOf(c)) {
                    assertThat(globalKeys.add(k))
                            .as("key %s reused on path", k).isTrue();
                }
                for (CountingUnit u : c.units()) {
                    int hit = 0;
                    for (int i = 0; i < rs.length; i++) {
                        if (rs[i].matcher().test(u)) {
                            sum[i]++;
                            hit++;
                        }
                    }
                    assertThat(hit).as("unit %s of %s hit no requirement", u, c.questionId())
                            .isGreaterThan(0);
                    units++;
                }
                seenIds.add(c.questionId());
            }
            for (int i = 0; i < rs.length; i++) {
                assertThat(sum[i]).as("block %s req %d sum", block.blockId(), i)
                        .isEqualTo(rs[i].quota());
            }
            assertThat(units).as("block %s counting units", block.blockId())
                    .isGreaterThanOrEqualTo(block.questionCount() > 0 ? 1 : 0);
            totalUnits[0] += units;
        }
    }

    private PathCandidate findCandidate(List<PathCandidate> list, QuestionPick pick) {
        for (PathCandidate c : list) {
            if (c.questionId().equals(pick.questionId()) && c.versionNo() == pick.versionNo()) {
                return c;
            }
        }
        return null;
    }
}
