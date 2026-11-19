package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.dao.QueryTimeoutException;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.infrastructure.ExamGenerationSolverProperties;
import vn.unicom.thi360.exam.infrastructure.ExamSolverProperties;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class PaperGenerationLimitsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID EXAM = UUID.randomUUID();
    private static final String PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"P1","questionCount":1,
              "participation":{"mode":"ALL","choose":null},
              "sections":[{"id":"s1","subjectCode":"TOAN","questionCount":1,
                "blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":1}]}],
              "options":[]}]}
            """;
    private static final String TRIM_PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"P1","questionCount":3,
              "participation":{"mode":"CHOICE","choose":1},"sections":[],
              "options":[{"id":"sc","kind":"SUBJECT_CHOICE","code":"SC","chooseSubjects":2,"countsByPick":[2,1],
                "sections":[
                  {"id":"sa","subjectCode":"TOAN","choiceGroup":"NATURAL",
                   "blocks":[{"id":"ba","questionType":"SINGLE_CHOICE","questionCount":2}]},
                  {"id":"sb","subjectCode":"LY","choiceGroup":"NATURAL",
                   "blocks":[{"id":"bb","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}]}
            """;
    private static final Instant START = Instant.parse("2026-11-19T00:00:00Z");
    private static final ExamGenerationSolverProperties THIRTY =
            new ExamGenerationSolverProperties(Duration.ofSeconds(30), 200, 20_000, 8);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deadlineMapsToGenerationTimeout() throws Exception {
        QuestionSourcePort source = source(1);
        PaperSolverPort port = service(source, new ExamGenerationSolverProperties(Duration.ZERO, 200, 20_000, 8));

        assertThatThrownBy(() -> port.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_TIMEOUT));
        // The deadline hits before any source read: nothing was fetched.
        verify(source, never()).findCandidates(any(), anyInt());
    }

    @Test
    void shortageIsAFailureNotATimeoutAndAFullSourceGeneratesWithinTheDeadline() throws Exception {
        ExamGenerationSolverProperties limits = new ExamGenerationSolverProperties(Duration.ofSeconds(30), 200, 20_000, 8);

        assertThatThrownBy(() -> service(source(0), limits)
                .generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_FAILED));

        PaperSolverPort.GeneratedPaper paper = service(source(1), limits)
                .generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of());
        assertThat(paper.parts()).hasSize(1);
        assertThat(paper.parts().getFirst().sections().getFirst().groups().getFirst().questions()).hasSize(1);
    }

    @Test
    void generationLimitsAreSeparate() {
        // Defaults: generation has its own 30 s deadline and the check limits keep theirs.
        Binder defaults = new Binder(new MapConfigurationPropertySource(Map.of()));
        assertThat(defaults.bindOrCreate("thi360.exam.solver.generate", ExamGenerationSolverProperties.class).deadline())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.bindOrCreate("thi360.exam.solver", ExamSolverProperties.class).toLimits().timeout())
                .isEqualTo(Duration.ofSeconds(30));

        // Each prefix moves only its own limits; the generate keys under the check prefix are not read by it.
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "thi360.exam.solver.timeout", "45s",
                "thi360.exam.solver.max-paths", "50",
                "thi360.exam.solver.generate.deadline", "7s",
                "thi360.exam.solver.generate.max-candidates-per-block", "5000")));
        var check = binder.bindOrCreate("thi360.exam.solver", ExamSolverProperties.class).toLimits();
        var generation = binder.bindOrCreate("thi360.exam.solver.generate", ExamGenerationSolverProperties.class);
        assertThat(check.timeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(check.maxPaths()).isEqualTo(50);
        assertThat(check.maxCandidatesPerBlock()).isEqualTo(20_000);
        assertThat(generation.deadline()).isEqualTo(Duration.ofSeconds(7));
        assertThat(generation.maxCandidatesPerBlock()).isEqualTo(5_000);
        assertThat(generation.maxPaths()).isEqualTo(200);
        assertThat(generation.limits(Duration.ofSeconds(2)).timeout()).isEqualTo(Duration.ofSeconds(2));
    }

    // ---- one absolute deadline across the phases (review R1-6) ---------------------------------------------

    /** Setup (catalog reads) takes 20 s and the one source read 15 s: past a 30 s deadline, whichever phase notices. */
    @Test
    void aShortageThatSurfacesPastTheDeadlineIsATimeoutNotAFailure() throws Exception {
        SteppingClock stepping = new SteppingClock(START);
        PaperSolverPort port = service(source(stepping, Duration.ofSeconds(15), "TOAN", 0, 0), THIRTY, stepping, PARTS,
                Duration.ofSeconds(20), "TOAN");

        assertThatThrownBy(() -> port.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_TIMEOUT));
    }

    @Test
    void aPaperFoundPastTheDeadlineIsATimeoutAndOneWithinItIsReturned() throws Exception {
        SteppingClock late = new SteppingClock(START);
        PaperSolverPort slow = service(source(late, Duration.ofSeconds(15), "TOAN", 1, 1), THIRTY, late, PARTS,
                Duration.ofSeconds(20), "TOAN");
        assertThatThrownBy(() -> slow.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_TIMEOUT));

        // 10 s of setup and 10 s of reading are 20 s of a 30 s deadline: each phase fits and so does the whole.
        SteppingClock inTime = new SteppingClock(START);
        PaperSolverPort fast = service(source(inTime, Duration.ofSeconds(10), "TOAN", 1, 1), THIRTY, inTime, PARTS,
                Duration.ofSeconds(10), "TOAN");
        assertThat(fast.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()).parts()).hasSize(1);
    }

    /** The trim solve (HSA science) starts after the setup took 20 s: it gets what is left, not a fresh 30 s. */
    @Test
    void aTrimSolveFailingPastTheDeadlineIsATimeoutNotAFailure() throws Exception {
        SteppingClock stepping = new SteppingClock(START);
        PaperSolverPort port = service(source(stepping, Duration.ofSeconds(15), "LY", 0, 3), THIRTY, stepping,
                TRIM_PARTS, Duration.ofSeconds(20), "TOAN", "LY");

        assertThatThrownBy(() -> port.generate(EXAM, 1, List.of("p1"),
                List.of(PaperSolverPort.Selection.subjects("p1", "SC", List.of("TOAN", "LY"))), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_TIMEOUT));
    }

    /** A read that crosses the deadline and then fails (a JDBC query timeout) is the deadline's doing. */
    @Test
    void aReadThatFailsPastTheDeadlineIsATimeoutAndOneWithinItAFailure() throws Exception {
        SteppingClock late = new SteppingClock(START);
        QuestionSourcePort slow = failingSource(late, Duration.ofSeconds(40));
        PaperSolverPort pastDeadline = service(slow, THIRTY, late, PARTS, Duration.ZERO, "TOAN");
        assertThatThrownBy(() -> pastDeadline.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_TIMEOUT));
        verify(slow).findCandidates(any(), anyInt());

        // The same failure with 10 s of the 30 s spent is an ordinary failure.
        SteppingClock inTime = new SteppingClock(START);
        PaperSolverPort withinBudget = service(failingSource(inTime, Duration.ofSeconds(10)), THIRTY, inTime, PARTS,
                Duration.ZERO, "TOAN");
        assertThatThrownBy(() -> withinBudget.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_FAILED));
    }

    /** The solve returns VALID in time; building the paper from it is what takes too long. */
    @Test
    void aPaperThatLeavesTheAssemblyPastTheDeadlineIsATimeoutAndOneWithinItIsReturned() throws Exception {
        SteppingClock late = new SteppingClock(START);
        AtomicBoolean lateAssembled = new AtomicBoolean();
        PaperSolverPort slow = service(assemblySource(late, Duration.ofSeconds(40), lateAssembled), THIRTY, late, PARTS,
                Duration.ZERO, "TOAN");
        assertThatThrownBy(() -> slow.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_TIMEOUT));
        assertThat(lateAssembled).isTrue();

        // Identical, but the assembly costs 5 s of the 30 s: the paper is returned.
        SteppingClock inTime = new SteppingClock(START);
        AtomicBoolean assembled = new AtomicBoolean();
        PaperSolverPort fast = service(assemblySource(inTime, Duration.ofSeconds(5), assembled), THIRTY, inTime, PARTS,
                Duration.ZERO, "TOAN");
        assertThat(fast.generate(EXAM, 1, List.of("p1"), List.of(), 7L, Set.of()).parts()).hasSize(1);
        assertThat(assembled).isTrue();
    }

    private PaperSolverPort service(QuestionSourcePort source, ExamGenerationSolverProperties limits) throws Exception {
        return service(source, limits, clock, PARTS, Duration.ZERO, "TOAN");
    }

    /** {@code setup} is what the catalog reads of the build cost on {@code time}. */
    private PaperSolverPort service(QuestionSourcePort source, ExamGenerationSolverProperties limits, Clock time,
            String structure, Duration setup, String... subjects) throws Exception {
        CatalogLookup catalog = mock(CatalogLookup.class);
        for (String code : subjects) {
            CatalogItemView subject = new CatalogItemView(subjectId(code), UUID.randomUUID(), "SUBJECT", code,
                    code.toLowerCase(), 1, code, code, CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, false, null,
                    (short) 1, "/x/");
            when(catalog.findActiveItem("SUBJECT", code)).thenReturn(Optional.of(subject));
        }
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenAnswer(call -> {
            if (time instanceof SteppingClock stepping) stepping.advance(setup);
            return List.of();
        });
        ExamPreviewStore store = mock(ExamPreviewStore.class);
        JsonNode parts = MAPPER.readTree(structure);
        when(store.loadVersion(EXAM, 1)).thenReturn(new ExamPreviewStore.Snapshot(EXAM, "E", "SHARED", 60,
                "RAW", BigDecimal.ONE, parts, mock(Exam.class), MAPPER.createObjectNode(), List.of("default", "sc")));
        SolverInputBuilder builder = new SolverInputBuilder(catalog, source,
                new vn.unicom.thi360.exam.domain.solver.SolverLimits(200, 20_000, Duration.ofSeconds(30), 8));
        return new PaperGenerationService(store, builder, limits, MAPPER, time);
    }

    private static UUID subjectId(String code) {
        return UUID.nameUUIDFromBytes(("subject:" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static QuestionSourcePort source(int candidates) {
        QuestionSourcePort source = mock(QuestionSourcePort.class);
        when(source.findCandidates(any(), anyInt())).thenReturn(candidates(candidates));
        return source;
    }

    /**
     * A source where reading {@code slowSubject} costs {@code delay} on the clock and holds {@code slowCount}
     * candidates; every other subject answers at once with {@code otherCount}.
     */
    private static QuestionSourcePort source(SteppingClock clock, Duration delay, String slowSubject, int slowCount,
            int otherCount) {
        QuestionSourcePort source = mock(QuestionSourcePort.class);
        when(source.findCandidates(any(), anyInt())).thenAnswer(call -> {
            QuestionSourcePort.Filter filter = call.getArgument(0);
            if (subjectId(slowSubject).equals(filter.subjectId())) {
                clock.advance(delay);
                return candidates(slowCount);
            }
            return candidates(otherCount);
        });
        return source;
    }

    /** A source whose read costs {@code delay} on the clock and then fails as a JDBC query timeout would. */
    private static QuestionSourcePort failingSource(SteppingClock clock, Duration delay) {
        QuestionSourcePort source = mock(QuestionSourcePort.class);
        when(source.findCandidates(any(), anyInt())).thenAnswer(call -> {
            clock.advance(delay);
            throw new QueryTimeoutException("statement timed out");
        });
        return source;
    }

    /**
     * A source that answers at once with one valid candidate whose units cost {@code cost} on the clock the first
     * time the assembler reads them: the solve is in time, only the paper's materialization is slow.
     */
    private static QuestionSourcePort assemblySource(SteppingClock clock, Duration cost, AtomicBoolean touched) {
        QuestionSourcePort source = mock(QuestionSourcePort.class);
        UUID id = UUID.randomUUID();
        QuestionSourcePort.Unit unit = new QuestionSourcePort.Unit(id, null, QuestionType.SINGLE_CHOICE, null, null,
                List.of(), List.of(), BigDecimal.ONE);
        List<QuestionSourcePort.Unit> units = new java.util.AbstractList<>() {
            @Override public QuestionSourcePort.Unit get(int index) { return unit; }

            @Override public int size() {
                boolean assembling = StackWalker.getInstance().walk(frames -> frames
                        .anyMatch(frame -> frame.getClassName().endsWith("PaperGenerationAssembler")));
                if (assembling && touched.compareAndSet(false, true)) clock.advance(cost);
                return 1;
            }
        };
        QuestionSourcePort.Candidate candidate = new QuestionSourcePort.Candidate(id, 1, null, units, null, null);
        when(source.findCandidates(any(), anyInt())).thenReturn(List.of(candidate));
        return source;
    }

    private static List<QuestionSourcePort.Candidate> candidates(int count) {
        List<QuestionSourcePort.Candidate> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            list.add(new QuestionSourcePort.Candidate(id, 1, null, List.of(new QuestionSourcePort.Unit(id, null,
                    QuestionType.SINGLE_CHOICE, null, null, List.of(), List.of(), BigDecimal.ONE)), null, null));
        }
        return list;
    }

    /** A clock that only moves when a test moves it, so a phase can cost time without sleeping. */
    private static final class SteppingClock extends Clock {
        private Instant now;

        SteppingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
