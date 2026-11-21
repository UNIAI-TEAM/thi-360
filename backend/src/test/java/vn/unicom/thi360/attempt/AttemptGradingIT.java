package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptGradingService;
import vn.unicom.thi360.attempt.application.AttemptLifecycleService;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

class AttemptGradingIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private AttemptGradingService attemptGradingService;
    @Autowired private AttemptLifecycleService attemptLifecycleService;
    @Autowired private QuestionSourcePort questionSourcePort;

    @BeforeEach
    void drainPendingOutbox() {
        drainAllOutbox();
    }

    @Test
    void handlerRegisteredForTopic() throws Exception {
        UUID student = createStudent("grading-handler");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        submitOk(student, attempt.getId(), sessionId, List.of());
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);

        int relayed = drainAllOutbox();
        assertThat(relayed).isGreaterThanOrEqualTo(1);

        Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(AttemptState.SCORED);
    }

    @Test
    void noEssayGoesToScored() throws Exception {
        UUID student = createStudent("grading-no-essay");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
        List<VersionKey> keys = items.stream()
                .map(i -> new VersionKey(i.getQuestionId(), i.getQuestionVersionNo()))
                .toList();
        var versions = questionSourcePort.loadVersions(keys);
        Map<VersionKey, JsonNode> snapMap = new HashMap<>();
        versions.forEach(v -> snapMap.put(v.key(), v.snapshot()));

        AttemptItem scItem = items.stream()
                .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        JsonNode snap = snapMap.get(new VersionKey(scItem.getQuestionId(), scItem.getQuestionVersionNo()));
        String correctOptId = null;
        for (JsonNode opt : snap.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                correctOptId = opt.path("id").asText();
                break;
            }
        }
        assertThat(correctOptId).isNotBlank();

        submitOk(
                student,
                attempt.getId(),
                sessionId,
                List.of(new FinalAnswerChange(scItem.getId(), 1L, new AnswerValue.SingleChoice(correctOptId))));

        drainAllOutbox();

        Attempt scored = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(scored.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scored.getGradedAt()).isNotNull();
        assertThat(scored.getRawScore()).isEqualByComparingTo(scItem.getPointsMax());
        assertThat(scored.getDisplayScore()).isEqualByComparingTo(scItem.getPointsMax());
        assertThat(scored.getResult()).isNotNull();
        assertThat(attemptEventRepository.existsByAttemptIdAndType(attempt.getId(), AttemptEventType.GRADED))
                .isTrue();
    }

    @Test
    void blankEssayNotQueued() throws Exception {
        UUID studentBlank = createStudent("grading-blank-essay");
        GrantOrderResult.Line grantBlank = grant(studentBlank, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt blankAttempt = startOpen(studentBlank, grantBlank.entitlementId(), new PreChoiceInput("CA2", null));
        UUID blankSession = claimSession(studentBlank, blankAttempt.getId());
        List<AttemptPart> blankParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(blankAttempt.getId());

        AttemptItem blankEssayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(blankAttempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        finishAllSequentialPartsThroughLastAndSubmit(
                studentBlank,
                blankAttempt.getId(),
                blankSession,
                blankParts,
                List.of(new FinalAnswerChange(blankEssayItem.getId(), 1L, new AnswerValue.Essay("<p>&nbsp;</p>", 0))));

        drainAllOutbox();

        Attempt reloadedBlank = attemptRepository.findById(blankAttempt.getId()).orElseThrow();
        assertThat(reloadedBlank.getState()).isEqualTo(AttemptState.SCORED);
        AttemptItem reloadedBlankItem = attemptItemRepository.findById(blankEssayItem.getId()).orElseThrow();
        assertThat(reloadedBlankItem.getOutcome()).isEqualTo(ItemOutcome.BLANK);
        assertThat(reloadedBlankItem.getEarnedPoints()).isEqualByComparingTo("0");

        // Non-blank essay enters PENDING_REVIEW with null total score
        UUID studentText = createStudent("grading-text-essay");
        GrantOrderResult.Line grantText = grant(studentText, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt textAttempt = startOpen(studentText, grantText.entitlementId(), new PreChoiceInput("CA2", null));
        UUID textSession = claimSession(studentText, textAttempt.getId());
        List<AttemptPart> textParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(textAttempt.getId());
        AttemptItem textEssayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(textAttempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        finishAllSequentialPartsThroughLastAndSubmit(
                studentText,
                textAttempt.getId(),
                textSession,
                textParts,
                List.of(new FinalAnswerChange(
                        textEssayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài luận hoàn chỉnh</p>", 4))));

        drainAllOutbox();

        Attempt reloadedPending = attemptRepository.findById(textAttempt.getId()).orElseThrow();
        assertThat(reloadedPending.getState()).isEqualTo(AttemptState.PENDING_REVIEW);
        assertThat(reloadedPending.getRawScore()).isNull();
        assertThat(reloadedPending.getDisplayScore()).isNull();
        AttemptItem reloadedTextItem = attemptItemRepository.findById(textEssayItem.getId()).orElseThrow();
        assertThat(reloadedTextItem.getOutcome()).isEqualTo(ItemOutcome.PENDING_REVIEW);
        assertThat(reloadedTextItem.getEarnedPoints()).isNull();
    }

    @Test
    void regradeIgnoresSourceEdits() throws Exception {
        UUID student = createStudent("grading-regrade-snap");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
        AttemptItem scItem = items.stream()
                .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        var v1 = questionSourcePort
                .loadVersions(List.of(new VersionKey(scItem.getQuestionId(), scItem.getQuestionVersionNo())))
                .getFirst();

        String v1CorrectOptionId = null;
        for (JsonNode opt : v1.snapshot().path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                v1CorrectOptionId = opt.path("id").asText();
                break;
            }
        }
        assertThat(v1CorrectOptionId).isNotBlank();

        submitOk(
                student,
                attempt.getId(),
                sessionId,
                List.of(new FinalAnswerChange(scItem.getId(), 1L, new AnswerValue.SingleChoice(v1CorrectOptionId))));

        int v1No = scItem.getQuestionVersionNo();
        int v2No = v1No + 1;
        ObjectNode mutatedV2 = v1.snapshot().deepCopy();
        mutatedV2.put("points", 9.0);
        for (JsonNode opt : mutatedV2.path("body").path("options")) {
            ((ObjectNode) opt).put("correct", !opt.path("correct").asBoolean(false));
        }

        try {
            jdbc.update(
                    """
                            INSERT INTO questionbank.question_version (question_id, version_no, snapshot)
                            VALUES (?, ?, ?::jsonb)
                            ON CONFLICT (question_id, version_no) DO UPDATE SET snapshot = EXCLUDED.snapshot
                            """,
                    scItem.getQuestionId(),
                    v2No,
                    objectMapper.writeValueAsString(mutatedV2));
            jdbc.update(
                    "UPDATE questionbank.question SET current_version_no = ? WHERE id = ?",
                    v2No,
                    scItem.getQuestionId());

            drainAllOutbox();
            attemptGradingService.regradeAttempt(attempt.getId());

            Attempt regraded = attemptRepository.findById(attempt.getId()).orElseThrow();
            assertThat(regraded.getState()).isEqualTo(AttemptState.SCORED);
            assertThat(regraded.getRawScore()).isEqualByComparingTo(scItem.getPointsMax());
            AttemptItem gradedItem = attemptItemRepository.findById(scItem.getId()).orElseThrow();
            assertThat(gradedItem.getOutcome()).isEqualTo(ItemOutcome.CORRECT);
            assertThat(gradedItem.getEarnedPoints()).isEqualByComparingTo(scItem.getPointsMax());
            assertThat(attemptEventRepository.existsByAttemptIdAndType(attempt.getId(), AttemptEventType.REGRADED))
                    .isTrue();
        } finally {
            jdbc.update(
                    "UPDATE questionbank.question SET current_version_no = ? WHERE id = ?",
                    v1No,
                    scItem.getQuestionId());
            jdbc.update(
                    "DELETE FROM questionbank.question_version WHERE question_id = ? AND version_no = ?",
                    scItem.getQuestionId(),
                    v2No);
        }

        // Verify finalized manual essay score is preserved on regradeAttempt (AC-18)
        UUID essayUser = createStudent("grading-regrade-essay");
        GrantOrderResult.Line essayGrant = grant(essayUser, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt essayAttempt = startOpen(essayUser, essayGrant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID essaySession = claimSession(essayUser, essayAttempt.getId());
        List<AttemptPart> essayParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(essayAttempt.getId());
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(essayAttempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        finishAllSequentialPartsThroughLastAndSubmit(
                essayUser,
                essayAttempt.getId(),
                essaySession,
                essayParts,
                List.of(new FinalAnswerChange(
                        essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài làm đầy đủ</p>", 4))));
        drainAllOutbox();

        jdbc.update(
                """
                        UPDATE attempt.attempt_item
                        SET earned_points = 18.50,
                            outcome = 'PARTIAL',
                            grading_detail = '{"exactFraction":"37/2"}'::jsonb
                        WHERE id = ?
                        """,
                essayItem.getId());

        attemptGradingService.regradeAttempt(essayAttempt.getId());

        AttemptItem afterRegrade = attemptItemRepository.findById(essayItem.getId()).orElseThrow();
        assertThat(afterRegrade.getOutcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(afterRegrade.getEarnedPoints()).isEqualByComparingTo("18.50");
        Attempt essayAfterRegrade = attemptRepository.findById(essayAttempt.getId()).orElseThrow();
        assertThat(essayAfterRegrade.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(essayAfterRegrade.getRawScore()).isEqualByComparingTo("18.50");
    }

    @Test
    void redeliveryIsIdempotent() throws Exception {
        UUID student = createStudent("grading-idempotent");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        submitOk(student, attempt.getId(), sessionId, List.of());
        drainAllOutbox();

        Attempt afterFirst = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(afterFirst.getState()).isEqualTo(AttemptState.SCORED);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch ready = new CountDownLatch(4);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    attemptGradingService.gradeFinishedAttempt(attempt.getId());
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        Attempt afterRedelivery = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(afterRedelivery.getRawScore()).isEqualByComparingTo(afterFirst.getRawScore());
        long gradedEvents = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId())
                .stream()
                .filter(e -> e.getType() == AttemptEventType.GRADED)
                .count();
        assertThat(gradedEvents).isEqualTo(1);
    }

    @Test
    void skippedPartWithoutItems() throws Exception {
        UUID student = createStudent("grading-skip-no-items");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        // Timeout closes open Part 1 and marks ungenerated CHOICE Part 3 as SKIPPED without items (AC-21)
        attemptLifecycleService.closeAttempt(
                attempt.getId(), EndReason.TIME_UP, mutableClock.instant().plusSeconds(1), sessionId, List.of());
        drainAllOutbox();

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
        AttemptPart skippedEmptyPart = parts.stream()
                .filter(p -> p.getState() == PartState.SKIPPED
                        && items.stream().noneMatch(i -> i.getPartId().equals(p.getId())))
                .findFirst()
                .orElseThrow();

        Attempt scored = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(scored.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scored.getRawScore()).isEqualByComparingTo("0.00");

        BigDecimal expectedTotalMax = BigDecimal.ZERO;
        int expectedBlankUnits = 0;
        for (AttemptPart p : parts) {
            List<AttemptItem> partItems = items.stream()
                    .filter(i -> i.getPartId().equals(p.getId())
                            && i.isInScope()
                            && i.getQuestionType() != QuestionType.QUESTION_SET)
                    .toList();
            if (p.getState() == PartState.SKIPPED && partItems.isEmpty()) {
                if (p.getMaxPoints() != null) {
                    expectedTotalMax = expectedTotalMax.add(p.getMaxPoints());
                }
                if (p.getQuestionCountRequired() != null) {
                    expectedBlankUnits += p.getQuestionCountRequired();
                }
            } else {
                for (AttemptItem item : partItems) {
                    expectedTotalMax = expectedTotalMax.add(item.getPointsMax());
                    expectedBlankUnits += item.getQuestionType() == QuestionType.CLOZE ? item.getUnitCount() : 1;
                }
            }
        }
        assertThat(skippedEmptyPart.getMaxPoints()).isGreaterThan(BigDecimal.ZERO);
        assertThat(scored.getMaxScore()).isEqualByComparingTo(expectedTotalMax);
        assertThat(scored.getResult()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) scored.getResult().get("statistics");
        assertThat(((Number) stats.get("blankCount")).intValue()).isEqualTo(expectedBlankUnits);
    }

    @Test
    void skippedPartWithItemsNotDoubleCounted() throws Exception {
        UUID student = createStudent("grading-skip-with-items");
        GrantOrderResult.Line grant = grant(student, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(student, attempt.getId());

        // Timeout in Part 1 of SHARED exam -> Part 2 & Part 3 (already generated with items) become SKIPPED (AC-22)
        attemptLifecycleService.closeAttempt(
                attempt.getId(), EndReason.TIME_UP, mutableClock.instant().plusSeconds(1), sessionId, List.of());
        drainAllOutbox();

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> allItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
        assertThat(parts.stream().filter(p -> p.getState() == PartState.SKIPPED)).isNotEmpty();

        BigDecimal expectedMax = BigDecimal.ZERO;
        for (AttemptItem item : allItems) {
            if (item.isInScope() && item.getQuestionType() != QuestionType.QUESTION_SET) {
                expectedMax = expectedMax.add(item.getPointsMax());
            }
        }

        Attempt scored = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(scored.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scored.getMaxScore()).isEqualByComparingTo(expectedMax);
    }

    private void finishAllSequentialPartsThroughLastAndSubmit(
            UUID userId,
            UUID attemptId,
            UUID sessionId,
            List<AttemptPart> parts,
            List<FinalAnswerChange> part0Finals) throws Exception {
        for (int idx = 0; idx < parts.size() - 1; idx++) {
            AttemptPart p = parts.get(idx);
            List<FinalAnswerChange> finals = idx == 0 ? part0Finals : List.of();
            MvcResult fin = performFinishPart(userId, attemptId, p.getId(), sessionId, new FinishPartRequest(finals));
            assertThat(fin.getResponse().getStatus()).isEqualTo(200);
        }
        submitOk(userId, attemptId, sessionId, List.of());
    }

    private void submitOk(UUID userId, UUID attemptId, UUID sessionId, List<FinalAnswerChange> finals)
            throws Exception {
        MvcResult result = performSubmit(
                userId, attemptId, UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(finals));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private Attempt startOpen(UUID userId, UUID entitlementId, PreChoiceInput preChoice) throws Exception {
        return startAndAwaitOpen(userId, UUID.randomUUID().toString(), entitlementId, preChoice);
    }

    private int drainAllOutbox() {
        int total = 0;
        int batch;
        do {
            batch = outboxRelay.relayBatch(500);
            total += batch;
        } while (batch > 0);
        return total;
    }
}
