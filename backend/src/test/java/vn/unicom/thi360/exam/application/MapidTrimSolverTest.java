package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import vn.unicom.thi360.exam.domain.ChoiceGroup;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MapidComparator;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.MapidRuleType;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.PartParticipation;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.QuotaBasis;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.TopicQuota;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * The HSA Khoa hoc paper (17/17/16): the subject that gets 16 is solved at 17 and one single question is dropped
 * (B10). The MAPID minimums and caps of that subject must still hold for the paper that is handed out (review R1-1),
 * through the real {@code solvePick} path with rules compiled by {@link SolverInputBuilder}.
 */
class MapidTrimSolverTest {
    private static final UUID A = UUID.randomUUID();
    private final CatalogLookup catalog = mock(CatalogLookup.class);
    private final QuestionSourcePort source = mock(QuestionSourcePort.class);
    private final Map<String, UUID> subjectIds = new HashMap<>();
    private final Map<UUID, List<Candidate>> pools = new HashMap<>();
    private SolverInputBuilder.SolverInput input;

    @BeforeEach
    void stubCatalogAndSource() {
        CatalogItemView node = mock(CatalogItemView.class);
        when(node.id()).thenReturn(A);
        when(node.path()).thenReturn("/a/");
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of(node));
        for (String code : List.of("LY", "HOA", "SINH")) {
            CatalogItemView subject = mock(CatalogItemView.class);
            UUID id = UUID.randomUUID();
            when(subject.id()).thenReturn(id);
            when(catalog.findActiveItem("SUBJECT", code)).thenReturn(Optional.of(subject));
            subjectIds.put(code, id);
        }
        when(source.findCandidates(any(), anyInt())).thenAnswer(call -> {
            QuestionSourcePort.Filter filter = call.getArgument(0);
            return pools.getOrDefault(filter.subjectId(), List.of());
        });
    }

    @Test
    void aMinimumThatOnlyTheTrimmedQuestionsCouldKeepNeverReachesThePaper() {
        // LY: exactly 17 questions exist and all carry A; EXACT 17 A needs every one of them.
        for (int i = 0; i < 17; i++) add("LY", "ly" + i, A);
        for (int i = 0; i < 17; i++) add("HOA", "hoa" + i);
        for (int i = 0; i < 17; i++) add("SINH", "sinh" + i);
        SubjectChoice choice = choice(section("LY", exact("lyA", 17)), section("HOA"), section("SINH"));
        int valid = 0;
        int invalid = 0;
        for (long seed = 1; seed <= 40; seed++) {
            PathResult result = new PaperSolver().solvePick(choice, List.of("LY", "HOA", "SINH"), seed,
                    input.candidates(), Set.of());
            if (result.status() == SolveStatus.VALID) {
                valid++;
                var ly = result.witness().stream().filter(w -> w.subjectCode().equals("LY")).findFirst().orElseThrow();
                assertThat(ly.resolvedCount()).as("seed %d", seed).isEqualTo(17);
                assertThat(ly.groups().getFirst().picks()).as("seed %d", seed).hasSize(17);
            } else {
                invalid++;
                assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
                assertThat(result.subjectCode()).isEqualTo("LY");
                assertThat(result.failingQuota()).as("seed %d", seed).isNotNull();
                assertThat(result.failingQuota().key()).isEqualTo("lyA");
            }
        }
        assertThat(valid).as("LY keeps 17 when another subject is the low one").isPositive();
        assertThat(invalid).as("LY as the low subject cannot keep EXACT 17").isPositive();
    }

    @Test
    void aLegacyTopicQuotaKeepsTheH3FallbackAndTheLowSubjectStillGetsItsPaper() {
        // Review R2-1: a quota without mapidMode (here grade filtered AT_LEAST 17 A) may lose one contributor when
        // no other drop exists (spec H3); only MAPID rules are hard.
        for (int i = 0; i < 17; i++) add("LY", "ly" + i, "G12", A);
        for (int i = 0; i < 17; i++) add("HOA", "hoa" + i);
        for (int i = 0; i < 17; i++) add("SINH", "sinh" + i);
        SubjectChoice choice = choice(legacySection("LY", 17), section("HOA"), section("SINH"));
        int lowLy = 0;
        for (long seed = 1; seed <= 40; seed++) {
            PathResult result = new PaperSolver().solvePick(choice, List.of("LY", "HOA", "SINH"), seed,
                    input.candidates(), Set.of());
            assertThat(result.status()).as("seed %d", seed).isEqualTo(SolveStatus.VALID);
            var ly = result.witness().stream().filter(w -> w.subjectCode().equals("LY")).findFirst().orElseThrow();
            if (ly.resolvedCount() == 16) lowLy++;
            assertThat(ly.groups().getFirst().picks()).as("seed %d", seed).hasSize(ly.resolvedCount());
        }
        assertThat(lowLy).as("some seed gives LY the 16-question paper").isPositive();
    }

    @Test
    void aStrictAtLeastRuleCannotLoseAContributorToTheTrim() {
        for (int i = 0; i < 17; i++) add("LY", "ly" + i, A);
        for (int i = 0; i < 17; i++) add("HOA", "hoa" + i);
        for (int i = 0; i < 17; i++) add("SINH", "sinh" + i);
        MapidRule atLeast = new MapidRule("lyA", MapidRuleType.COUNT, null, List.of(A), MapidComparator.AT_LEAST,
                17, null);
        SubjectChoice choice = choice(section("LY", atLeast), section("HOA"), section("SINH"));
        int invalid = 0;
        for (long seed = 1; seed <= 40; seed++) {
            PathResult result = new PaperSolver().solvePick(choice, List.of("LY", "HOA", "SINH"), seed,
                    input.candidates(), Set.of());
            if (result.status() == SolveStatus.VALID) {
                var ly = result.witness().stream().filter(w -> w.subjectCode().equals("LY")).findFirst().orElseThrow();
                assertThat(ly.resolvedCount()).as("seed %d", seed).isEqualTo(17);
            } else {
                invalid++;
                assertThat(result.failingQuota().key()).as("seed %d", seed).isEqualTo("lyA");
            }
        }
        assertThat(invalid).isPositive();
    }

    @Test
    void theSectionPreviewTrimsALegacyQuotaButRefusesAStrictOne() {
        for (int i = 0; i < 17; i++) add("LY", "ly" + i, "G12", A);
        for (int i = 0; i < 17; i++) add("HOA", "hoa" + i);
        for (int i = 0; i < 17; i++) add("SINH", "sinh" + i);
        var legacy = choice(legacySection("LY", 17), section("HOA"), section("SINH")).sections().getFirst();
        var legacySource = input.candidates();
        var strict = choice(section("LY", exact("lyA", 17)), section("HOA"), section("SINH")).sections().getFirst();
        var strictSource = input.candidates();
        for (long seed = 1; seed <= 20; seed++) {
            var kept = PreviewPathSolver.solveSection(new PaperSolver(),
                    SolvePath.of(0, List.of(legacy.withQuestionCount(17))), legacySource, seed, true);
            assertThat(kept.path().status()).as("seed %d", seed).isEqualTo(SolveStatus.VALID);
            assertThat(kept.dropped()).hasSize(1);
        }
        for (long seed = 1; seed <= 20; seed++) {
            var refused = PreviewPathSolver.solveSection(new PaperSolver(),
                    SolvePath.of(0, List.of(strict.withQuestionCount(17))), strictSource, seed, true);
            assertThat(refused.path().status()).as("seed %d", seed).isEqualTo(SolveStatus.INVALID);
            assertThat(refused.path().failingQuota().key()).isEqualTo("lyA");
        }
    }

    // ---------------------------------------------------------------- fixtures

    private SubjectChoice choice(ExamSection... sections) {
        ExamPartOption option = new ExamPartOption("science", OptionKind.SUBJECT_CHOICE, "SCIENCE", "Science",
                3, List.of(17, 17, 16), List.of(sections), List.of(), true);
        var part = new ExamPart("p", 1, "Part", 20, 1, 1, 1, new PartParticipation(ParticipationMode.CHOICE, 1),
                null, List.of(), List.of(option));
        input = new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
        var choicePart = (SolvePart.Choice) input.parts().getFirst();
        return (SubjectChoice) choicePart.options().getFirst();
    }

    private static ExamSection section(String code, MapidRule... rules) {
        QuestionBlock block = new QuestionBlock(code + "-g", code, QuestionType.SHORT_ANSWER, 17, null, null, null,
                null, null, null, false, null, List.of(), null, true, false, List.of(), List.of(), null, List.of(),
                false, List.of(), null);
        return new ExamSection(code.toLowerCase(), code, null, null, null, null, ChoiceGroup.NATURAL,
                List.of(block), List.of(), null, rules.length == 0 ? null : MapidMode.MATRIX, List.of(rules));
    }

    private static ExamSection legacySection(String code, int count) {
        QuestionBlock block = new QuestionBlock(code + "-g", code, QuestionType.SHORT_ANSWER, 17, null, null, null,
                null, null, null, false, null, List.of(), null, true, false, List.of(), List.of(), null, List.of(),
                false, List.of(), null);
        TopicQuota quota = new TopicQuota("legacy", "legacy", List.of(A), count, QuotaBasis.QUESTION,
                QuotaEnforcement.REQUIRED, List.of("G12"), List.of(), List.of());
        return new ExamSection(code.toLowerCase(), code, null, null, null, null, ChoiceGroup.NATURAL,
                List.of(block), List.of(quota), null, null, List.of());
    }

    private static MapidRule exact(String id, int count) {
        return new MapidRule(id, MapidRuleType.COUNT, null, List.of(A), MapidComparator.EXACT, count, null);
    }

    private void add(String subject, String name, UUID... nodes) {
        add(subject, name, null, nodes);
    }

    private void add(String subject, String name, String grade, UUID... nodes) {
        UUID id = UUID.nameUUIDFromBytes((subject + name).getBytes());
        Candidate candidate = new Candidate(id, 1, null, List.of(new Unit(id, null, QuestionType.SHORT_ANSWER, grade,
                null, List.of(nodes), List.of(), null)), null, null);
        pools.computeIfAbsent(subjectIds.get(subject), key -> new ArrayList<>()).add(candidate);
    }
}
