package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MapidComparator;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.MapidRuleType;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.PartParticipation;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SolveMode;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Enforcement of the compiled MAPID caps (UNI-2758 rules 2 and 3-exact) end to end through the solver: rules on an
 * {@link ExamSection} are compiled by {@link SolverInputBuilder}, the candidate pools are served by a stubbed
 * source and the shared {@link PaperSolver} picks the questions. A unit counts for a cap when it carries the
 * cap's node or a descendant of it (Q1); a set counts one unit per child.
 */
class MapidCapSolverTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID A1 = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private final CatalogLookup catalog = mock(CatalogLookup.class);
    private final QuestionSourcePort source = mock(QuestionSourcePort.class);
    private final Map<String, List<Candidate>> pools = new HashMap<>();
    private final Map<String, Candidate> byId = new HashMap<>();

    @BeforeEach
    void stubCatalogAndSource() {
        List<CatalogItemView> knowledge = List.of(node(A, "/a/"), node(A1, "/a/a1/"), node(B, "/b/"));
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(knowledge);
        CatalogItemView subject = mock(CatalogItemView.class);
        when(subject.id()).thenReturn(UUID.randomUUID());
        when(catalog.findActiveItem("SUBJECT", "MATH")).thenReturn(Optional.of(subject));
        when(source.findCandidates(any(), anyInt())).thenAnswer(call -> {
            QuestionSourcePort.Filter filter = call.getArgument(0);
            return pools.getOrDefault(filter.questionType().name(), List.of());
        });
    }

    @Test
    void exactCountInSubjectLimitsRandomPicksAndDescendantsCount() {
        for (int i = 0; i < 8; i++) {
            add(QuestionType.SHORT_ANSWER, candidate("c" + i, QuestionType.SHORT_ANSWER, i % 2 == 0 ? A : A1));
        }
        for (int i = 0; i < 4; i++) add(QuestionType.SHORT_ANSWER, candidate("n" + i, QuestionType.SHORT_ANSWER));
        ExamSection section = section(6, List.of(single("g1", QuestionType.SHORT_ANSWER, 6)),
                exact("capA", null, 2, A));
        for (SolveMode mode : modes()) {
            PathResult result = solve(section, mode);
            assertThat(result.status()).as("%s", mode).isEqualTo(SolveStatus.VALID);
            assertThat(picked(result, null)).hasSize(6);
            assertThat(carriers(result, null, A, A1)).as("%s", mode).isEqualTo(2);
        }
    }

    @Test
    void exactCountInOneGroupLeavesOtherGroupsFree() {
        for (int i = 0; i < 6; i++) {
            add(QuestionType.SHORT_ANSWER, candidate("sa" + i, QuestionType.SHORT_ANSWER, A));
            add(QuestionType.SINGLE_CHOICE, candidate("sc" + i, QuestionType.SINGLE_CHOICE, A));
        }
        for (int i = 6; i < 12; i++) {
            add(QuestionType.SHORT_ANSWER, candidate("sa" + i, QuestionType.SHORT_ANSWER));
            add(QuestionType.SINGLE_CHOICE, candidate("sc" + i, QuestionType.SINGLE_CHOICE));
        }
        ExamSection section = section(8, List.of(single("g1", QuestionType.SHORT_ANSWER, 4),
                single("g2", QuestionType.SINGLE_CHOICE, 4)), exact("capG1", "g1", 2, A));
        PathResult result = solve(section, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(carriers(result, "g1", A, A1)).isEqualTo(2);
        assertThat(carriers(result, "g2", A, A1)).isEqualTo(4);
        for (long seed = 1; seed <= 10; seed++) {
            PathResult generated = solve(section, SolveMode.generate(seed));
            assertThat(generated.status()).isEqualTo(SolveStatus.VALID);
            assertThat(carriers(generated, "g1", A, A1)).as("seed %d", seed).isEqualTo(2);
        }
    }

    @Test
    void aQuestionWithSeveralMapidsCountsForEveryCapButFillsOneSlot() {
        add(QuestionType.SHORT_ANSWER, candidate("x", QuestionType.SHORT_ANSWER, A, B));
        add(QuestionType.SHORT_ANSWER, candidate("y", QuestionType.SHORT_ANSWER, A));
        add(QuestionType.SHORT_ANSWER, candidate("z", QuestionType.SHORT_ANSWER, B));
        add(QuestionType.SHORT_ANSWER, candidate("w", QuestionType.SHORT_ANSWER));
        ExamSection section = section(2, List.of(single("g1", QuestionType.SHORT_ANSWER, 2)),
                exact("capA", null, 1, A), new MapidRule("minB", MapidRuleType.PRESENT, null, List.of(B), null, null, null));
        for (SolveMode mode : modes()) {
            PathResult result = solve(section, mode);
            assertThat(result.status()).as("%s", mode).isEqualTo(SolveStatus.VALID);
            assertThat(carriers(result, null, A, A1)).as("%s", mode).isEqualTo(1);
            assertThat(carriers(result, null, B)).isGreaterThanOrEqualTo(1);
            assertThat(Set.copyOf(picked(result, null))).isIn(Set.of("x", "z"), Set.of("y", "z"));
        }
    }

    @Test
    void twoRulesNeedTwoDifferentQuestions() {
        add(QuestionType.SHORT_ANSWER, candidate("x", QuestionType.SHORT_ANSWER, A, B));
        add(QuestionType.SHORT_ANSWER, candidate("w", QuestionType.SHORT_ANSWER));
        ExamSection section = section(2, List.of(single("g1", QuestionType.SHORT_ANSWER, 2)),
                new MapidRule("minA", MapidRuleType.PRESENT, null, List.of(A), null, null, null),
                new MapidRule("minB", MapidRuleType.PRESENT, null, List.of(B), null, null, null));
        assertThat(solve(section, SolveMode.check()).status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    void everyChildOfASetCountsAsAUnit() {
        add(QuestionType.QUESTION_SET, set("s1", List.of(A), List.of(A), List.of()));
        add(QuestionType.QUESTION_SET, set("s2", List.of(A), List.of(), List.of()));
        add(QuestionType.QUESTION_SET, set("s3", List.of(), List.of(), List.of()));
        add(QuestionType.QUESTION_SET, set("s4", List.of(), List.of(), List.of()));
        ExamSection section = section(6, List.of(setBlock("g1", 2, 3)), exact("capA", null, 2, A));
        PathResult result = solve(section, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(carriers(result, null, A, A1)).isEqualTo(2);
        assertThat(picked(result, null)).hasSize(2).contains("s1").doesNotContain("s2");
    }

    @Test
    void minimumsThatSumPastAnIntCannotWrapToZeroAndPassAnUnmappedPaper() {
        // 4 x 2^30 wraps an int sum to 0; the single unmapped question carries none of them (UNI-2758 R1-7).
        add(QuestionType.SHORT_ANSWER, candidate("unmapped", QuestionType.SHORT_ANSWER));
        List<MapidRule> rules = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rules.add(new MapidRule("huge" + i, MapidRuleType.COUNT, null, List.of(A), MapidComparator.AT_LEAST,
                    1_073_741_824, null));
        }
        ExamSection atLeast = section(1, List.of(single("g1", QuestionType.SHORT_ANSWER, 1)),
                rules.toArray(MapidRule[]::new));
        for (SolveMode mode : modes()) {
            assertThat(solve(atLeast, mode).status()).as("%s", mode).isEqualTo(SolveStatus.INVALID);
        }
        List<MapidRule> exact = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            exact.add(new MapidRule("exact" + i, MapidRuleType.COUNT, null, List.of(A), MapidComparator.EXACT,
                    1_073_741_824, null));
        }
        ExamSection exactly = section(1, List.of(single("g1", QuestionType.SHORT_ANSWER, 1)),
                exact.toArray(MapidRule[]::new));
        assertThat(solve(exactly, SolveMode.check()).status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    void capZeroDropsEverySingleAndEverySetThatCarriesTheNode() {
        add(QuestionType.SHORT_ANSWER, candidate("carrier", QuestionType.SHORT_ANSWER, A1));
        for (int i = 0; i < 3; i++) add(QuestionType.SHORT_ANSWER, candidate("n" + i, QuestionType.SHORT_ANSWER));
        add(QuestionType.QUESTION_SET, set("badSet", List.of(), List.of(A), List.of()));
        add(QuestionType.QUESTION_SET, set("goodSet", List.of(), List.of(), List.of()));
        ExamSection section = section(6, List.of(single("g1", QuestionType.SHORT_ANSWER, 3), setBlock("g2", 1, 3)),
                exact("none", null, 0, A));
        for (SolveMode mode : modes()) {
            PathResult result = solve(section, mode);
            assertThat(result.status()).as("%s", mode).isEqualTo(SolveStatus.VALID);
            assertThat(carriers(result, null, A, A1)).isZero();
            assertThat(picked(result, "g1")).containsExactlyInAnyOrder("n0", "n1", "n2");
            assertThat(picked(result, "g2")).containsExactly("goodSet");
        }
    }

    @Test
    void anInfeasibleCapNamesTheRule() {
        for (int i = 0; i < 3; i++) add(QuestionType.SHORT_ANSWER, candidate("ab" + i, QuestionType.SHORT_ANSWER, A, B));
        for (int i = 0; i < 3; i++) add(QuestionType.SHORT_ANSWER, candidate("w" + i, QuestionType.SHORT_ANSWER));
        ExamSection section = section(3, List.of(single("g1", QuestionType.SHORT_ANSWER, 3)),
                exact("capA", null, 0, A),
                new MapidRule("minB", MapidRuleType.COUNT, null, List.of(B), MapidComparator.AT_LEAST, 3, null));
        PathResult result = solve(section, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.failingQuota()).isNotNull();
        assertThat(result.failingQuota().kind()).isEqualTo("TOPIC_CAP");
        assertThat(result.failingQuota().key()).isEqualTo("capA");
    }

    @Test
    void aGroupShortOfCapFreeSourceNamesTheRule() {
        for (int i = 0; i < 3; i++) add(QuestionType.SHORT_ANSWER, candidate("a" + i, QuestionType.SHORT_ANSWER, A));
        for (int i = 0; i < 2; i++) add(QuestionType.SHORT_ANSWER, candidate("w" + i, QuestionType.SHORT_ANSWER));
        ExamSection section = section(3, List.of(single("g1", QuestionType.SHORT_ANSWER, 3)),
                exact("capA", null, 0, A));
        PathResult result = solve(section, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.groupId()).isEqualTo("g1");
        assertThat(result.failingQuota().kind()).isEqualTo("TOPIC_CAP");
        assertThat(result.failingQuota().key()).isEqualTo("capA");
    }

    @Test
    void aCapIsBlamedOnlyWhenRelaxingItAloneClosesTheGap() {
        // The three carriers sit over BOTH caps: relaxing either one leaves them out, so no single rule explains the
        // shortage, whatever order the rules are declared in (UNI-2758 R1-9).
        for (int i = 0; i < 3; i++) add(QuestionType.SHORT_ANSWER, candidate("d" + i, QuestionType.SHORT_ANSWER, A, B));
        for (int i = 0; i < 2; i++) add(QuestionType.SHORT_ANSWER, candidate("w" + i, QuestionType.SHORT_ANSWER));
        for (MapidRule[] order : new MapidRule[][] {
                {exact("capA", null, 0, A), exact("capB", null, 0, B)},
                {exact("capB", null, 0, B), exact("capA", null, 0, A)}}) {
            PathResult result = solve(section(3, List.of(single("g1", QuestionType.SHORT_ANSWER, 3)), order),
                    SolveMode.check());
            assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
            assertThat(result.groupId()).isEqualTo("g1");
            assertThat(result.failingQuota()).as("%s first", order[0].id()).isNull();
        }
    }

    @Test
    void aShortageIsNotBlamedOnACapThatNeedsTheOtherCapRelaxedToo() {
        // Two A+B singles fill the group of two. Each is individually over capA only (capB = 1 holds one), but
        // relaxing capA alone leaves the pair over capB: neither cap alone explains the shortage (UNI-2758 R2-3).
        for (int i = 0; i < 2; i++) add(QuestionType.SHORT_ANSWER, candidate("d" + i, QuestionType.SHORT_ANSWER, A, B));
        for (MapidRule[] order : new MapidRule[][] {
                {exact("capA", null, 0, A), exact("capB", null, 1, B)},
                {exact("capB", null, 1, B), exact("capA", null, 0, A)}}) {
            PathResult result = solve(section(2, List.of(single("g1", QuestionType.SHORT_ANSWER, 2)), order),
                    SolveMode.check());
            assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
            assertThat(result.groupId()).isEqualTo("g1");
            assertThat(result.failingQuota()).as("%s first", order[0].id()).isNull();
        }
    }

    @Test
    void aCapThatAloneKeepsEnoughCandidatesOutIsStillNamedInEitherOrder() {
        // a0/a1 are over capA only, d0 over both: relaxing capA frees a0 and a1, which closes the gap.
        for (int i = 0; i < 2; i++) add(QuestionType.SHORT_ANSWER, candidate("a" + i, QuestionType.SHORT_ANSWER, A));
        add(QuestionType.SHORT_ANSWER, candidate("d0", QuestionType.SHORT_ANSWER, A, B));
        add(QuestionType.SHORT_ANSWER, candidate("w0", QuestionType.SHORT_ANSWER));
        for (MapidRule[] order : new MapidRule[][] {
                {exact("capA", null, 0, A), exact("capB", null, 0, B)},
                {exact("capB", null, 0, B), exact("capA", null, 0, A)}}) {
            PathResult result = solve(section(3, List.of(single("g1", QuestionType.SHORT_ANSWER, 3)), order),
                    SolveMode.check());
            assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
            assertThat(result.failingQuota().key()).as("%s first", order[0].id()).isEqualTo("capA");
        }
    }

    @Test
    void aPercentOnTheTakeTheRemainderGroupIsHeldByTheSolver() {
        for (int i = 0; i < 8; i++) add(QuestionType.SHORT_ANSWER, candidate("a" + i, QuestionType.SHORT_ANSWER, A));
        for (int i = 0; i < 8; i++) add(QuestionType.SHORT_ANSWER, candidate("w" + i, QuestionType.SHORT_ANSWER));
        for (int i = 0; i < 6; i++) add(QuestionType.SINGLE_CHOICE, candidate("c" + i, QuestionType.SINGLE_CHOICE));
        QuestionBlock remainder = new QuestionBlock("g1", "g1", QuestionType.SHORT_ANSWER, null, null, null, null,
                null, null, null, false, null, List.of(), null, true, false, List.of(), List.of(), null, List.of(),
                false, List.of(), null);
        // 10 questions, g2 draws 4: the remainder group g1 draws 6 and 50% of it is exactly 3 carriers of A.
        ExamSection section = section(10, List.of(remainder, single("g2", QuestionType.SINGLE_CHOICE, 4)),
                new MapidRule("half", MapidRuleType.COUNT, "g1", List.of(A), MapidComparator.EXACT, null,
                        BigDecimal.valueOf(50)));
        for (SolveMode mode : modes()) {
            PathResult result = solve(section, mode);
            assertThat(result.status()).as("%s", mode).isEqualTo(SolveStatus.VALID);
            assertThat(picked(result, "g1")).hasSize(6);
            assertThat(carriers(result, "g1", A, A1)).as("%s", mode).isEqualTo(3);
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static List<SolveMode> modes() {
        List<SolveMode> modes = new ArrayList<>();
        modes.add(SolveMode.check());
        for (long seed = 1; seed <= 20; seed++) modes.add(SolveMode.generate(seed));
        return modes;
    }

    private PathResult solve(ExamSection section, SolveMode mode) {
        var part = new ExamPart("p", 1, "Part", 20, 4, 4, 4, new PartParticipation(ParticipationMode.ALL, null),
                null, List.of(section), List.of());
        var input = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        var outcome = new PaperSolver().solveAll(input.parts(), input.candidates(), mode);
        assertThat(outcome.results()).hasSize(1);
        return outcome.results().getFirst();
    }

    /** Question ids picked for one group, or for every group when {@code groupId} is null. */
    private List<String> picked(PathResult result, String groupId) {
        return rawPicked(result, groupId).stream().map(id -> NAMES.get(UUID.fromString(id))).toList();
    }

    private static List<String> rawPicked(PathResult result, String groupId) {
        return result.witness().stream().flatMap(w -> w.groups().stream())
                .filter(g -> groupId == null || g.groupId().equals(groupId))
                .flatMap(g -> g.picks().stream()).map(pick -> pick.questionId()).toList();
    }

    /** Selected counting units, in the given group or everywhere, that carry any of {@code nodes}. */
    private int carriers(PathResult result, String groupId, UUID... nodes) {
        Set<UUID> wanted = Set.of(nodes);
        int count = 0;
        for (String id : rawPicked(result, groupId)) {
            for (Unit unit : byId.get(id).units()) {
                if (unit.mapidIds().stream().anyMatch(wanted::contains)) count++;
            }
        }
        return count;
    }

    private void add(QuestionType type, Candidate candidate) {
        pools.computeIfAbsent(type.name(), key -> new ArrayList<>()).add(candidate);
        byId.put(candidate.questionId().toString(), candidate);
    }

    /** The candidate keeps its own id as the name so the tests can read the witness. */
    private static Candidate candidate(String name, QuestionType type, UUID... nodes) {
        UUID id = named(name);
        return new Candidate(id, 1, null, List.of(unit(id, type, List.of(nodes))), null, null);
    }

    private static Candidate set(String name, List<UUID> first, List<UUID> second, List<UUID> third) {
        UUID id = named(name);
        List<Unit> units = new ArrayList<>();
        for (List<UUID> nodes : List.of(first, second, third)) {
            units.add(unit(UUID.randomUUID(), QuestionType.SHORT_ANSWER, nodes));
        }
        return new Candidate(id, 1, id, units, null, null);
    }

    private static Unit unit(UUID id, QuestionType type, List<UUID> nodes) {
        return new Unit(id, null, type, null, null, nodes, List.of(), null);
    }

    private static final Map<UUID, String> NAMES = new HashMap<>();

    private static UUID named(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        NAMES.put(id, name);
        return id;
    }

    private static QuestionBlock single(String id, QuestionType type, int count) {
        return new QuestionBlock(id, id, type, count, null, null, null, null, null, null, false, null,
                List.of(), null, true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
    }

    private static QuestionBlock setBlock(String id, int setCount, int setSize) {
        return new QuestionBlock(id, id, QuestionType.QUESTION_SET, null, setCount, setSize, null, null, null, null,
                false, null, List.of(), null, true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
    }

    private static ExamSection section(int count, List<QuestionBlock> blocks, MapidRule... rules) {
        return new ExamSection("s", "MATH", count, null, null, null, null, blocks, List.of(), null,
                MapidMode.MATRIX, List.of(rules));
    }

    private static MapidRule exact(String id, String groupId, int count, UUID node) {
        return new MapidRule(id, MapidRuleType.COUNT, groupId, List.of(node), MapidComparator.EXACT, count, null);
    }

    private static CatalogItemView node(UUID id, String path) {
        CatalogItemView item = mock(CatalogItemView.class);
        when(item.id()).thenReturn(id);
        when(item.path()).thenReturn(path);
        return item;
    }
}
