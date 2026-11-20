package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.attempt.api.ChoiceTiming;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.StartAttemptResponse;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.PlannedPart;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.ResolvedStartPlan;
import vn.unicom.thi360.attempt.application.AttemptStartService.StartCommandResult;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.spi.GrantSource;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;

/**
 * Unit tests for {@link AttemptStartService} HTTP status/state determinism when background
 * generation transitions an attempt before {@code startAttempt} builds its response (R1-1, R1-2).
 */
class AttemptStartServiceTest {

    @Test
    void freshStartReturns202AndStartingEvenIfAttemptFlipsToInProgressImmediately() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        AttemptAccessGate accessGate = mock(AttemptAccessGate.class);
        EntitlementPort entitlementPort = mock(EntitlementPort.class);
        PublishedExamPort publishedExamPort = mock(PublishedExamPort.class);
        PaperSolverPort paperSolverPort = mock(PaperSolverPort.class);
        QuestionSourcePort questionSourcePort = mock(QuestionSourcePort.class);
        AttemptPathResolver pathResolver = mock(AttemptPathResolver.class);
        AttemptPaperAssembler paperAssembler = mock(AttemptPaperAssembler.class);
        AttemptRepository attemptRepository = mock(AttemptRepository.class);
        AttemptPartRepository attemptPartRepository = mock(AttemptPartRepository.class);
        AttemptItemRepository attemptItemRepository = mock(AttemptItemRepository.class);
        AttemptEventRepository attemptEventRepository = mock(AttemptEventRepository.class);
        GenerationExecutor generationExecutor = mock(GenerationExecutor.class);
        IdempotentCommand idempotentCommand = mock(IdempotentCommand.class);
        OverdueAttemptCloser overdueAttemptCloser = mock(OverdueAttemptCloser.class);
        EntityManager entityManager = mock(EntityManager.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

        AttemptStartService service =
                new AttemptStartService(
                        accessGate,
                        entitlementPort,
                        publishedExamPort,
                        paperSolverPort,
                        questionSourcePort,
                        pathResolver,
                        paperAssembler,
                        attemptRepository,
                        attemptPartRepository,
                        attemptItemRepository,
                        attemptEventRepository,
                        generationExecutor,
                        idempotentCommand,
                        overdueAttemptCloser,
                        entityManager,
                        jdbcTemplate,
                        txManager,
                        clock);

        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        UUID entitlementId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();

        when(accessGate.requireLearner()).thenReturn(userId);
        when(entitlementPort.describe(userId, entitlementId))
                .thenReturn(
                        new EntitlementDescription(
                                examId,
                                2,
                                grantId,
                                GrantSource.PURCHASE,
                                "ORD-1",
                                now,
                                List.of()));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(1);
        when(attemptRepository.findByUserIdAndExamIdAndStateIn(any(), any(), any()))
                .thenReturn(Optional.empty());

        PublishedVersion version =
                new PublishedVersion(
                        examId,
                        1,
                        "SERVING",
                        "PUBLISHED",
                        now,
                        "EX-1",
                        "Exam",
                        UUID.randomUUID(),
                        "HSA",
                        "FULL",
                        null,
                        null,
                        "STANDARD",
                        "SHARED",
                        60,
                        BigDecimal.TEN,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("default"),
                        List.of("default"));
        ServingPath path =
                new ServingPath("default", 1, List.of(), 5, BigDecimal.TEN, 60, List.of(), List.of());
        ServingExam serving = new ServingExam(examId, "PUBLISHED", version, List.of(path));
        when(publishedExamPort.findServing(examId)).thenReturn(Optional.of(serving));

        ResolvedStartPlan plan =
                new ResolvedStartPlan(
                        version,
                        path,
                        60,
                        BigDecimal.TEN,
                        Map.of(),
                        List.of("p1"),
                        List.of(),
                        List.of(
                                new PlannedPart(
                                        "p1",
                                        "Part 1",
                                        0,
                                        ChoiceTiming.NONE,
                                        60,
                                        5,
                                        BigDecimal.TEN)),
                        "path-1");
        when(pathResolver.resolveStartPlan(serving, List.of(), null)).thenReturn(plan);
        when(entitlementPort.reserve(any(), any(), any()))
                .thenReturn(
                        new EntitlementBenefits(
                                grantId, examId, Map.of(), "SCORE_AND_ANSWERS", List.of(), 1));

        AtomicReference<Attempt> savedAttempt = new AtomicReference<>();
        when(attemptRepository.saveAndFlush(any(Attempt.class)))
                .thenAnswer(
                        inv -> {
                            Attempt a = inv.getArgument(0);
                            savedAttempt.set(a);
                            return a;
                        });

        when(idempotentCommand.execute(any(), any(), any(), eq(StartCommandResult.class), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Supplier<StartCommandResult> supplier = inv.getArgument(4);
                            StartCommandResult r = supplier.get();
                            // Simulate background worker flipping attempt to IN_PROGRESS BEFORE startAttempt formats response
                            savedAttempt.get().updateMaxScore(BigDecimal.TEN);
                            savedAttempt.get().open(1, now, now.plusSeconds(3600));
                            when(attemptRepository.findById(r.attemptId()))
                                    .thenReturn(Optional.of(savedAttempt.get()));
                            return r;
                        });

        // 1. Fresh execution returns 202 ACCEPTED + STARTING + resumed=false despite DB already IN_PROGRESS
        ResponseEntity<StartAttemptResponse> fresh =
                service.startAttempt("k-1", new StartAttemptRequest(entitlementId, null));
        assertThat(fresh.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(fresh.getBody()).isNotNull();
        assertThat(fresh.getBody().state()).isEqualTo(AttemptState.STARTING);
        assertThat(fresh.getBody().resumed()).isFalse();
        assertThat(fresh.getBody().retryable()).isFalse();

        // 2. Idempotent replay (supplier does not run) returns 200 OK with current DB state
        when(idempotentCommand.execute(any(), any(), any(), eq(StartCommandResult.class), any()))
                .thenReturn(new StartCommandResult(savedAttempt.get().getId(), false));
        ResponseEntity<StartAttemptResponse> replay =
                service.startAttempt("k-1", new StartAttemptRequest(entitlementId, null));
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isNotNull();
        assertThat(replay.getBody().state()).isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(replay.getBody().resumed()).isFalse();

        // 3. Resumed open attempt (supplier runs and sets resumed=true) returns 200 OK + resumed=true
        when(idempotentCommand.execute(any(), any(), any(), eq(StartCommandResult.class), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Supplier<StartCommandResult> supplier = inv.getArgument(4);
                            when(attemptRepository.findByUserIdAndExamIdAndStateIn(
                                            any(), any(), any()))
                                    .thenReturn(Optional.of(savedAttempt.get()));
                            return supplier.get();
                        });
        ResponseEntity<StartAttemptResponse> resumed =
                service.startAttempt("k-2", new StartAttemptRequest(entitlementId, null));
        assertThat(resumed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resumed.getBody()).isNotNull();
        assertThat(resumed.getBody().state()).isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(resumed.getBody().resumed()).isTrue();
    }

    private static Integer eq(Integer val) {
        return org.mockito.ArgumentMatchers.eq(val);
    }

    private static <T> Class<T> eq(Class<T> cls) {
        return org.mockito.ArgumentMatchers.eq(cls);
    }
}
