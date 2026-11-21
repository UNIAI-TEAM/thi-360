package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.FinalizeEssayRequest;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.ReopenEssayRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

class EssayGradingConcurrencyIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;

    @BeforeEach
    void drainBefore() {
        drainAllOutbox();
    }

    @Test
    void twoGradersClaimOneWins() throws Exception {
        UUID student = createStudent("conc-claim-student");
        UUID graderA = createGrader("conc-claim-a", "Giám khảo Đua A");
        UUID graderB = createGrader("conc-claim-b", "Giám khảo Đua B");

        SubmittedEssay sub = submitBcaEssay(student);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<MvcResult> fA = pool.submit(() -> {
                ready.countDown();
                go.await();
                return performClaim(graderA, sub.essayItemId());
            });
            Future<MvcResult> fB = pool.submit(() -> {
                ready.countDown();
                go.await();
                return performClaim(graderB, sub.essayItemId());
            });
            ready.await();
            go.countDown();

            MvcResult rA = fA.get();
            MvcResult rB = fB.get();
            List<MvcResult> results = List.of(rA, rB);
            List<MvcResult> ok = results.stream().filter(r -> r.getResponse().getStatus() == 200).toList();
            List<MvcResult> conflicts = results.stream().filter(r -> r.getResponse().getStatus() == 409).toList();

            assertThat(ok).hasSize(1);
            assertThat(conflicts).hasSize(1);

            JsonNode winner = json(ok.getFirst());
            JsonNode loser = json(conflicts.getFirst());
            assertThat(loser.path("code").asText()).isEqualTo("GRADING_CLAIMED");
            assertThat(loser.path("claimedById").asText()).isEqualTo(winner.path("graderId").asText());
            assertThat(loser.path("claimedByName").asText()).isEqualTo(winner.path("graderName").asText());

            Integer activeRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM attempt.essay_grade WHERE attempt_item_id = ? AND superseded_by IS NULL",
                    Integer.class,
                    sub.essayItemId());
            assertThat(activeRows).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelFinalizeSumsCorrectly() throws Exception {
        UUID student = createStudent("conc-fin-student");
        UUID graderA = createGrader("conc-fin-a", "Giám khảo Chấm 1");
        UUID graderB = createGrader("conc-fin-b", "Giám khảo Chấm 2");

        SubmittedEssay sub = submitBcaEssay(student);
        AttemptItem essay1 = attemptItemRepository.findById(sub.essayItemId()).orElseThrow();
        UUID essay2Id = addSecondEssayItem(sub.attemptId(), essay1);

        assertThat(performClaim(graderA, essay1.getId()).getResponse().getStatus()).isEqualTo(200);
        assertThat(performClaim(graderB, essay2Id).getResponse().getStatus()).isEqualTo(200);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<MvcResult> f1 = pool.submit(() -> {
                ready.countDown();
                go.await();
                return performFinalize(graderA, essay1.getId(),
                        new FinalizeEssayRequest(null, null, new BigDecimal("11.25"), "Câu 1"));
            });
            Future<MvcResult> f2 = pool.submit(() -> {
                ready.countDown();
                go.await();
                return performFinalize(graderB, essay2Id,
                        new FinalizeEssayRequest(null, null, new BigDecimal("6.75"), "Câu 2"));
            });
            ready.await();
            go.countDown();

            assertThat(f1.get().getResponse().getStatus()).isEqualTo(200);
            assertThat(f2.get().getResponse().getStatus()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        Attempt finalAttempt = attemptRepository.findById(sub.attemptId()).orElseThrow();
        assertThat(finalAttempt.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(finalAttempt.getRawScore()).isEqualByComparingTo("18.00");
        assertThat(finalAttempt.getGradedAt()).isNotNull();

        long scoredGradedEvents = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(sub.attemptId())
                .stream()
                .filter(e -> e.getType() == AttemptEventType.GRADED
                        && "SCORED".equals(e.getData().get("state")))
                .count();
        assertThat(scoredGradedEvents).isEqualTo(1L);
    }

    @Test
    void duplicateFinalizeAndReopenRaceSerializedCleanly() throws Exception {
        UUID student = createStudent("conc-dup-student");
        UUID grader = createGrader("conc-dup-grader", "Giám khảo Trùng");

        SubmittedEssay sub = submitBcaEssay(student);
        assertThat(performClaim(grader, sub.essayItemId()).getResponse().getStatus()).isEqualTo(200);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<MvcResult> f1 = pool.submit(() -> {
                ready.countDown();
                go.await();
                return performFinalize(grader, sub.essayItemId(),
                        new FinalizeEssayRequest(null, null, new BigDecimal("14.00"), "Lần 1"));
            });
            Future<MvcResult> f2 = pool.submit(() -> {
                ready.countDown();
                go.await();
                return performFinalize(grader, sub.essayItemId(),
                        new FinalizeEssayRequest(null, null, new BigDecimal("14.00"), "Lần 2"));
            });
            ready.await();
            go.countDown();

            List<MvcResult> results = List.of(f1.get(), f2.get());
            assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 200)).hasSize(1);
            List<MvcResult> conflict = results.stream().filter(r -> r.getResponse().getStatus() == 409).toList();
            assertThat(conflict).hasSize(1);
            assertThat(json(conflict.getFirst()).path("code").asText()).isEqualTo("ESSAY_ALREADY_FINALIZED");

            // Race reopen against another finalize on the now-FINAL item
            CountDownLatch ready2 = new CountDownLatch(2);
            CountDownLatch go2 = new CountDownLatch(1);
            Future<MvcResult> reopenFut = pool.submit(() -> {
                ready2.countDown();
                go2.await();
                return mockMvc.perform(
                                post("/api/v1/admin/grading/items/{itemId}/reopen", sub.essayItemId())
                                        .with(asUser(grader, "grader", Permissions.GRADING_MODIFY))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new ReopenEssayRequest("Đua reopen"))))
                        .andReturn();
            });
            Future<MvcResult> finFut = pool.submit(() -> {
                ready2.countDown();
                go2.await();
                return performFinalize(grader, sub.essayItemId(),
                        new FinalizeEssayRequest(null, null, new BigDecimal("15.00"), "Đua finalize"));
            });
            ready2.await();
            go2.countDown();

            MvcResult rReopen = reopenFut.get();
            MvcResult rFin = finFut.get();
            // Reopen acquires the lock either before finalize (reopen 200 -> finalize 200) or after finalize (finalize 409 -> reopen 200)
            assertThat(rReopen.getResponse().getStatus()).isEqualTo(200);
            assertThat(rFin.getResponse().getStatus()).isIn(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    private record SubmittedEssay(UUID attemptId, UUID essayItemId) {}

    private SubmittedEssay submitBcaEssay(UUID studentId) throws Exception {
        GrantOrderResult.Line grant = grant(studentId, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(studentId, UUID.randomUUID().toString(), grant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(studentId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        List<FinalAnswerChange> essayAnswer = List.of(
                new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài đua đồng thời</p>", 4)));
        for (int idx = 0; idx < parts.size() - 1; idx++) {
            AttemptPart p = parts.get(idx);
            List<FinalAnswerChange> partFinals = p.getId().equals(essayItem.getPartId()) ? essayAnswer : List.of();
            assertThat(performFinishPart(studentId, attempt.getId(), p.getId(), sessionId,
                    new FinishPartRequest(partFinals)).getResponse().getStatus()).isEqualTo(200);
        }
        AttemptPart lastPart = parts.getLast();
        List<FinalAnswerChange> submitFinals = lastPart.getId().equals(essayItem.getPartId()) ? essayAnswer : List.of();
        assertThat(performSubmit(studentId, attempt.getId(), UUID.randomUUID().toString(), sessionId,
                new SubmitAttemptRequest(submitFinals))
                .getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();
        return new SubmittedEssay(attempt.getId(), essayItem.getId());
    }

    private UUID addSecondEssayItem(UUID attemptId, AttemptItem template) throws Exception {
        UUID newItemId = Ids.newId();
        jdbc.update(
                """
                INSERT INTO attempt.attempt_item (
                    id, attempt_id, part_id, subject_item_id, section_id, component_code, block_id,
                    ord, display_no, unit_count, question_id, question_version_no, question_type,
                    points_max, in_scope, answer, answer_seq, flagged, outcome, grading_detail, version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'ESSAY', ?, true, ?::jsonb, 1, false, 'PENDING_REVIEW', '{"wordCount":3}'::jsonb, 0)
                """,
                newItemId,
                attemptId,
                template.getPartId(),
                template.getSubjectItemId(),
                template.getSectionId(),
                template.getComponentCode(),
                template.getBlockId(),
                template.getOrd() + 100,
                template.getDisplayNo() != null ? template.getDisplayNo() + 1 : 99,
                template.getQuestionId(),
                template.getQuestionVersionNo(),
                new BigDecimal("10.00"),
                objectMapper.writeValueAsString(new AnswerValue.Essay("<p>Bài tự luận thứ 2</p>", 4)));
        return newItemId;
    }

    private UUID createGrader(String label, String fullName) {
        UUID id = createManager(label);
        jdbc.update("UPDATE identity.user_account SET full_name = ? WHERE id = ?", fullName, id);
        return id;
    }

    private MvcResult performClaim(UUID graderId, UUID itemId) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/grading/items/{itemId}/claim", itemId)
                                .with(asUser(graderId, "grader", Permissions.GRADING_MODIFY)))
                .andReturn();
    }

    private MvcResult performFinalize(UUID graderId, UUID itemId, FinalizeEssayRequest req) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/grading/items/{itemId}/finalize", itemId)
                                .with(asUser(graderId, "grader", Permissions.GRADING_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
    }

    private void drainAllOutbox() {
        int batch;
        do {
            batch = outboxRelay.relayBatch(500);
        } while (batch > 0);
    }
}
