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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
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

    private PaperSolverPort service(QuestionSourcePort source, ExamGenerationSolverProperties limits) throws Exception {
        CatalogLookup catalog = mock(CatalogLookup.class);
        CatalogItemView subject = new CatalogItemView(UUID.randomUUID(), UUID.randomUUID(), "SUBJECT", "TOAN", "toan",
                1, "Toan", "Math", CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, false, null, (short) 1, "/x/");
        when(catalog.findActiveItem("SUBJECT", "TOAN")).thenReturn(Optional.of(subject));
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of());
        ExamPreviewStore store = mock(ExamPreviewStore.class);
        JsonNode parts = MAPPER.readTree(PARTS);
        when(store.loadVersion(EXAM, 1)).thenReturn(new ExamPreviewStore.Snapshot(EXAM, "E", "SHARED", 60,
                "RAW", BigDecimal.ONE, parts, mock(Exam.class), MAPPER.createObjectNode()));
        SolverInputBuilder builder = new SolverInputBuilder(catalog, source,
                new vn.unicom.thi360.exam.domain.solver.SolverLimits(200, 20_000, Duration.ofSeconds(30), 8));
        return new PaperGenerationService(store, builder, limits, MAPPER, clock);
    }

    private static QuestionSourcePort source(int candidates) {
        QuestionSourcePort source = mock(QuestionSourcePort.class);
        List<QuestionSourcePort.Candidate> list = new java.util.ArrayList<>();
        for (int i = 0; i < candidates; i++) {
            UUID id = UUID.randomUUID();
            list.add(new QuestionSourcePort.Candidate(id, 1, null, List.of(new QuestionSourcePort.Unit(id, null,
                    QuestionType.SINGLE_CHOICE, null, null, List.of(), List.of(), BigDecimal.ONE)), null, null));
        }
        when(source.findCandidates(any(), anyInt())).thenReturn(list);
        return source;
    }
}
