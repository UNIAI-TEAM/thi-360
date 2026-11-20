package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Verifies attempt Flyway schema against full DB + DS data: partial unique index for open attempts
 * (AC-3), {@code attempt_no} uniqueness, JPA mappings, and {@code numeric(10,4)} precision (BS §17.5).
 */
class AttemptSchemaIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired AttemptRepository attemptRepository;
    @Autowired AttemptPartRepository attemptPartRepository;
    @Autowired AttemptItemRepository attemptItemRepository;
    @Autowired AttemptEventRepository attemptEventRepository;

    private final List<UUID> createdUsers = new ArrayList<>();
    private UUID examId;

    @BeforeEach
    void setUp() {
        AttemptTestFixtures.ensureDsBuilt(context);
        examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-FULL");
    }

    @AfterEach
    void tearDown() {
        createdUsers.forEach(userId -> AttemptTestFixtures.cleanupUser(jdbc, userId));
    }

    @Test
    void partialUniqueIndexAllowsOneOpenAttempt() {
        UUID userId = student();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt first = newAttempt(UUID.randomUUID(), userId, examId, now);
        attemptRepository.saveAndFlush(first);

        // Second STARTING attempt for same (user, exam) is rejected at DB level.
        Attempt secondStarting = newAttempt(UUID.randomUUID(), userId, examId, now);
        assertThatThrownBy(() -> attemptRepository.saveAndFlush(secondStarting))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_attempt_open_user_exam");

        // Second IN_PROGRESS attempt for same (user, exam) is also rejected.
        Attempt secondOpen = newAttempt(UUID.randomUUID(), userId, examId, now);
        secondOpen.open(1, now.plusSeconds(1), null);
        assertThatThrownBy(() -> attemptRepository.saveAndFlush(secondOpen))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_attempt_open_user_exam");

        // Aborting first (no attempt_no) unblocks creating a new open attempt; multiple ABORTED rows are allowed.
        first.abort(AbortReason.STALE_STARTING, now.plusSeconds(5));
        attemptRepository.saveAndFlush(first);

        Attempt secondAborted = newAttempt(UUID.randomUUID(), userId, examId, now.plusSeconds(6));
        secondAborted.abort(AbortReason.GENERATION_FAILED, now.plusSeconds(7));
        attemptRepository.saveAndFlush(secondAborted);

        Attempt reopened = newAttempt(UUID.randomUUID(), userId, examId, now.plusSeconds(10));
        reopened.open(1, now.plusSeconds(12), null);
        attemptRepository.saveAndFlush(reopened);

        // Another IN_PROGRESS row for same (user, exam) fails while reopened is IN_PROGRESS.
        Attempt thirdOpen = newAttempt(UUID.randomUUID(), userId, examId, now.plusSeconds(15));
        assertThatThrownBy(() -> attemptRepository.saveAndFlush(thirdOpen))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_attempt_open_user_exam");

        // Different user on same exam is allowed.
        UUID otherUser = student();
        Attempt otherAttempt = newAttempt(UUID.randomUUID(), otherUser, examId, now.plusSeconds(20));
        attemptRepository.saveAndFlush(otherAttempt);
        assertThat(attemptRepository.findById(otherAttempt.getId())).isPresent();
    }

    @Test
    void numericPrecisionPreservesFourDecimalsAndEntitiesRoundTrip() {
        UUID userId = student();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            Attempt attempt = newAttempt(attemptId, userId, examId, now);
            attempt.open(1, now.plusSeconds(2), null);
            attemptRepository.saveAndFlush(attempt);

            AttemptPart part = AttemptPart.createPending(
                    partId, attemptId, "P1", "Phần 1", 1, 75, 50, new BigDecimal("50.0000"), 123L);
            part.open(now.plusSeconds(2), now.plusSeconds(4502));
            attemptPartRepository.saveAndFlush(part);

            AttemptItem item = AttemptItem.create(
                    itemId,
                    attemptId,
                    partId,
                    UUID.randomUUID(),
                    "SEC_1",
                    "TOAN",
                    "BLK_1",
                    null,
                    1,
                    1,
                    1,
                    UUID.randomUUID(),
                    1,
                    QuestionType.SINGLE_CHOICE,
                    new BigDecimal("0.1250"),
                    List.of("opt-b", "opt-a"));
            item.recordAnswer(new AnswerValue.SingleChoice("opt-b"), 1L, now.plusSeconds(10));
            attemptItemRepository.saveAndFlush(item);

            AttemptEvent event = new AttemptEvent(
                    eventId,
                    attemptId,
                    AttemptEventType.STARTED,
                    null,
                    partId,
                    now.plusSeconds(2),
                    Map.of("attemptNo", 1));
            attemptEventRepository.saveAndFlush(event);
        });

        Attempt loadedAttempt = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(loadedAttempt.getState()).isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(loadedAttempt.getAttemptNo()).isEqualTo(1);

        AttemptItem loadedItem = attemptItemRepository.findByPartIdOrderByOrdAsc(partId).getFirst();
        assertThat(loadedItem.getPointsMax()).isEqualByComparingTo("0.1250");
        assertThat(loadedItem.getOptionOrder()).containsExactly("opt-b", "opt-a");
        assertThat(loadedItem.getAnswer()).isEqualTo(new AnswerValue.SingleChoice("opt-b"));
        assertThat(loadedItem.getAnswerSeq()).isEqualTo(1L);

        // Verify duplicate non-null attempt_no is rejected by uq_attempt_no_user_exam.
        Attempt dupNo = newAttempt(UUID.randomUUID(), userId, examId, now.plusSeconds(30));
        dupNo.open(1, now.plusSeconds(31), null);
        // Mark first finished in DB so uq_attempt_open_user_exam is not what rejects dupNo.
        jdbc.update("UPDATE attempt.attempt SET state = 'SCORED' WHERE id = ?", attemptId);
        assertThatThrownBy(() -> attemptRepository.saveAndFlush(dupNo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_attempt_no_user_exam");
    }

    private UUID student() {
        UUID id = AttemptTestFixtures.createStudentAccount(jdbc, "schema.it");
        createdUsers.add(id);
        return id;
    }

    private static Attempt newAttempt(UUID id, UUID userId, UUID examId, Instant now) {
        return Attempt.start(
                id,
                userId,
                examId,
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HSA Full",
                UUID.randomUUID(),
                "HSA",
                "FULL",
                null,
                null,
                AttemptTiming.PER_PART,
                NavigationMode.SEQUENTIAL,
                Map.of(),
                987L,
                UUID.randomUUID(),
                "FULL_SOLUTION",
                "RAW",
                new BigDecimal("150.0000"),
                new BigDecimal("150.0000"),
                "idem-" + id,
                now);
    }
}
