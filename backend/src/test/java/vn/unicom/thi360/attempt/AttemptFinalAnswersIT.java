package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Integration tests verifying the production {@code FinalAnswerWriter} implementation inside
 * {@code finish} and {@code submit} (LT §6.3, AC-11.08, AC-11).
 */
class AttemptFinalAnswersIT extends AbstractAttemptDsIT {

    @Test
    void submitWritesFinalAnswers() throws Exception {
        UUID userId = createStudent("fin-ans-sub");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "sub-fa", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        List<AttemptItem> items = singleChoiceItems(p0.getId());
        AttemptItem item0 = items.get(0);
        AttemptItem item1 = items.get(1);
        String validOpt = item0.getOptionOrder().get(0);

        SubmitAttemptRequest req =
                new SubmitAttemptRequest(
                        List.of(
                                new FinalAnswerChange(item0.getId(), 1L, new AnswerValue.SingleChoice(validOpt)),
                                new FinalAnswerChange(item1.getId(), 1L, new AnswerValue.SingleChoice("bad-id"))));
        MvcResult subRes = performSubmit(userId, attempt.getId(), "fa-sub-k", sid, req);
        assertThat(subRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode subJson = json(subRes);
        assertThat(subJson.path("state").asText()).isEqualTo("GRADING");
        assertThat(subJson.path("endReason").asText()).isEqualTo("SUBMITTED");
        JsonNode results = subJson.path("results");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).path("status").asText()).isEqualTo("SAVED");
        assertThat(results.get(1).path("status").asText()).isEqualTo("REJECTED");
        assertThat(results.get(1).path("reason").asText()).isEqualTo("INVALID_ANSWER");

        AttemptItem reloaded0 = attemptItemRepository.findById(item0.getId()).orElseThrow();
        assertThat(reloaded0.getAnswerSeq()).isEqualTo(1L);
        assertThat(((AnswerValue.SingleChoice) reloaded0.getAnswer()).optionId()).isEqualTo(validOpt);
        assertThat(attemptItemRepository.findById(item1.getId()).orElseThrow().getAnswer()).isNull();
    }

    @Test
    void finishWritesFinalAnswers() throws Exception {
        UUID userId = createStudent("fin-ans-part");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "fin-fa", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p0 = parts.get(0);
        AttemptPart p1 = parts.get(1);
        AttemptItem p0Item = singleChoiceItems(p0.getId()).getFirst();
        AttemptItem p1Item = singleChoiceItems(p1.getId()).getFirst();
        String p0Opt = p0Item.getOptionOrder().get(0);
        String p1Opt = p1Item.getOptionOrder().get(0);

        FinishPartRequest req =
                new FinishPartRequest(
                        List.of(
                                new FinalAnswerChange(p0Item.getId(), 2L, new AnswerValue.SingleChoice(p0Opt)),
                                new FinalAnswerChange(p1Item.getId(), 1L, new AnswerValue.SingleChoice(p1Opt))));
        MvcResult finRes = performFinishPart(userId, attempt.getId(), p0.getId(), sid, req);
        assertThat(finRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode finJson = json(finRes);
        assertThat(finJson.path("closedPart").path("state").asText()).isEqualTo("CLOSED");
        assertThat(finJson.path("nextPart").path("state").asText()).isEqualTo("OPEN");
        JsonNode results = finJson.path("results");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).path("status").asText()).isEqualTo("SAVED");
        assertThat(results.get(1).path("status").asText()).isEqualTo("REJECTED");
        assertThat(results.get(1).path("reason").asText()).isEqualTo("PART_CLOSED");

        AttemptItem reloadedP0 = attemptItemRepository.findById(p0Item.getId()).orElseThrow();
        assertThat(reloadedP0.getAnswerSeq()).isEqualTo(2L);
        assertThat(((AnswerValue.SingleChoice) reloadedP0.getAnswer()).optionId()).isEqualTo(p0Opt);
        assertThat(attemptItemRepository.findById(p1Item.getId()).orElseThrow().getAnswerSeq()).isZero();
    }

    @Test
    void staleSeqInFinalAnswersIgnored() throws Exception {
        UUID userId = createStudent("fin-ans-stale");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "stale-fa", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        List<AttemptItem> items = singleChoiceItems(p0.getId());
        AttemptItem item0 = items.get(0);
        AttemptItem item1 = items.get(1);
        AttemptItem item2 = items.get(2);
        String i0Opt0 = item0.getOptionOrder().get(0);
        String i0Opt1 = item0.getOptionOrder().get(1);
        String i1Opt0 = item1.getOptionOrder().get(0);
        String i1Opt1 = item1.getOptionOrder().get(1);
        String i2Opt1 = item2.getOptionOrder().get(1);

        // Pre-save seq=5 on item0 and item1
        assertThat(
                        performPutAnswers(
                                        userId,
                                        attempt.getId(),
                                        sid,
                                        new SaveAnswersRequest(
                                                List.of(
                                                        new AnswerChange(item0.getId(), 5L, new AnswerValue.SingleChoice(i0Opt1)),
                                                        new AnswerChange(item1.getId(), 5L, new AnswerValue.SingleChoice(i1Opt0))),
                                                null,
                                                null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        // Submit with stale seq=3 on item0, same seq=5 on item1, newer seq=6 on item2
        MvcResult subRes =
                performSubmit(
                        userId,
                        attempt.getId(),
                        "stale-fa-sub",
                        sid,
                        new SubmitAttemptRequest(
                                List.of(
                                        new FinalAnswerChange(item0.getId(), 3L, new AnswerValue.SingleChoice(i0Opt0)),
                                        new FinalAnswerChange(item1.getId(), 5L, new AnswerValue.SingleChoice(i1Opt1)),
                                        new FinalAnswerChange(item2.getId(), 6L, new AnswerValue.SingleChoice(i2Opt1)))));
        assertThat(subRes.getResponse().getStatus()).isEqualTo(200);

        JsonNode results = json(subRes).path("results");
        assertThat(results).hasSize(3);
        assertThat(results.get(0).path("status").asText()).isEqualTo("STALE");
        assertThat(results.get(1).path("status").asText()).isEqualTo("SAVED");
        assertThat(results.get(2).path("status").asText()).isEqualTo("SAVED");

        AttemptItem after0 = attemptItemRepository.findById(item0.getId()).orElseThrow();
        assertThat(after0.getAnswerSeq()).isEqualTo(5L);
        assertThat(((AnswerValue.SingleChoice) after0.getAnswer()).optionId()).isEqualTo(i0Opt1);

        AttemptItem after1 = attemptItemRepository.findById(item1.getId()).orElseThrow();
        assertThat(after1.getAnswerSeq()).isEqualTo(5L);
        assertThat(((AnswerValue.SingleChoice) after1.getAnswer()).optionId()).isEqualTo(i1Opt0);

        AttemptItem after2 = attemptItemRepository.findById(item2.getId()).orElseThrow();
        assertThat(after2.getAnswerSeq()).isEqualTo(6L);
        assertThat(((AnswerValue.SingleChoice) after2.getAnswer()).optionId()).isEqualTo(i2Opt1);
    }

    @Test
    void afterDeadlineNotWritten() throws Exception {
        UUID userId = createStudent("fin-ans-late");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 3, List.of());

        // 1. Late finishPart at exact deadline -> TIME_UP, finalAnswers NOT written
        Attempt a1 = startAndAwaitOpen(userId, "late-fa-fin", line.entitlementId(), null);
        UUID sid1 = claimSession(userId, a1.getId());
        AttemptPart a1P0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).getFirst();
        AttemptItem a1Item = attemptItemRepository.findByPartIdOrderByOrdAsc(a1P0.getId()).getFirst();

        mutableClock.setAt(a1P0.getDeadlineAt());
        MvcResult lateFin =
                performFinishPart(
                        userId,
                        a1.getId(),
                        a1P0.getId(),
                        sid1,
                        new FinishPartRequest(
                                List.of(new FinalAnswerChange(a1Item.getId(), 1L, new AnswerValue.SingleChoice("a")))));
        assertThat(lateFin.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(lateFin).path("closedPart").path("closeReason").asText()).isEqualTo("TIME_UP");
        assertThat(json(lateFin).path("results")).isEmpty();
        assertThat(attemptItemRepository.findById(a1Item.getId()).orElseThrow().getAnswerSeq()).isZero();

        // 2. Late submit at exact deadline -> TIME_UP, finalAnswers NOT written
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        GrantOrderResult.Line singleLine = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt a2 = startAndAwaitOpen(userId, "late-fa-sub", singleLine.entitlementId(), null);
        UUID sid2 = claimSession(userId, a2.getId());
        AttemptPart a2P0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).getFirst();
        AttemptItem a2Item = attemptItemRepository.findByPartIdOrderByOrdAsc(a2P0.getId()).getFirst();

        mutableClock.setAt(a2P0.getDeadlineAt().plusSeconds(5));
        MvcResult lateSub =
                performSubmit(
                        userId,
                        a2.getId(),
                        "late-fa-sub-k",
                        sid2,
                        new SubmitAttemptRequest(
                                List.of(new FinalAnswerChange(a2Item.getId(), 1L, new AnswerValue.SingleChoice("a")))));
        assertThat(lateSub.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(lateSub).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(json(lateSub).path("results")).isEmpty();
        assertThat(attemptItemRepository.findById(a2Item.getId()).orElseThrow().getAnswerSeq()).isZero();
    }

    @Test
    void submitFinalAnswersRacingPutAnswersKeepsHigherSeqOrRejectsClosed() throws Exception {
        UUID userId = createStudent("fin-ans-race");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Case A: submit(seq=10) races put(seq=5) -> final saved seq is ALWAYS 10
            Attempt a1 = startAndAwaitOpen(userId, "fa-race-1", line.entitlementId(), null);
            UUID sid1 = claimSession(userId, a1.getId());
            AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).getFirst();
            AttemptItem item1 = singleChoiceItems(p1.getId()).getFirst();
            String i1Opt0 = item1.getOptionOrder().get(0);
            String i1Opt1 = item1.getOptionOrder().get(1);

            CountDownLatch ready1 = new CountDownLatch(2);
            CountDownLatch go1 = new CountDownLatch(1);
            Future<MvcResult> put1 = pool.submit(() -> {
                ready1.countDown();
                assertThat(go1.await(10, TimeUnit.SECONDS)).isTrue();
                return performPutAnswers(
                        userId, a1.getId(), sid1,
                        new SaveAnswersRequest(List.of(new AnswerChange(item1.getId(), 5L, new AnswerValue.SingleChoice(i1Opt0))), null, null));
            });
            Future<MvcResult> sub1 = pool.submit(() -> {
                ready1.countDown();
                assertThat(go1.await(10, TimeUnit.SECONDS)).isTrue();
                return performSubmit(
                        userId, a1.getId(), "fa-race-sub-1", sid1,
                        new SubmitAttemptRequest(List.of(new FinalAnswerChange(item1.getId(), 10L, new AnswerValue.SingleChoice(i1Opt1)))));
            });
            assertThat(ready1.await(10, TimeUnit.SECONDS)).isTrue();
            go1.countDown();
            assertThat(put1.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(sub1.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);

            AttemptItem after1 = attemptItemRepository.findById(item1.getId()).orElseThrow();
            assertThat(after1.getAnswerSeq()).isEqualTo(10L);
            assertThat(((AnswerValue.SingleChoice) after1.getAnswer()).optionId()).isEqualTo(i1Opt1);

            // Case B: submit(seq=10) races put(seq=15) -> either put ran first (seq=15 wins, submit sees STALE)
            // or submit ran first (seq=10 wins, put gets PART_CLOSED). Never 10 overwriting 15!
            Attempt a2 = startAndAwaitOpen(userId, "fa-race-2", line.entitlementId(), null);
            UUID sid2 = claimSession(userId, a2.getId());
            AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).getFirst();
            AttemptItem item2 = singleChoiceItems(p2.getId()).getFirst();
            String i2Opt0 = item2.getOptionOrder().get(0);
            String i2Opt1 = item2.getOptionOrder().get(1);

            CountDownLatch ready2 = new CountDownLatch(2);
            CountDownLatch go2 = new CountDownLatch(1);
            Future<MvcResult> put2 = pool.submit(() -> {
                ready2.countDown();
                assertThat(go2.await(10, TimeUnit.SECONDS)).isTrue();
                return performPutAnswers(
                        userId, a2.getId(), sid2,
                        new SaveAnswersRequest(List.of(new AnswerChange(item2.getId(), 15L, new AnswerValue.SingleChoice(i2Opt1))), null, null));
            });
            Future<MvcResult> sub2 = pool.submit(() -> {
                ready2.countDown();
                assertThat(go2.await(10, TimeUnit.SECONDS)).isTrue();
                return performSubmit(
                        userId, a2.getId(), "fa-race-sub-2", sid2,
                        new SubmitAttemptRequest(List.of(new FinalAnswerChange(item2.getId(), 10L, new AnswerValue.SingleChoice(i2Opt0)))));
            });
            assertThat(ready2.await(10, TimeUnit.SECONDS)).isTrue();
            go2.countDown();
            MvcResult putRes2 = put2.get(15, TimeUnit.SECONDS);
            MvcResult subRes2 = sub2.get(15, TimeUnit.SECONDS);
            assertThat(putRes2.getResponse().getStatus()).isEqualTo(200);
            assertThat(subRes2.getResponse().getStatus()).isEqualTo(200);

            AttemptItem after2 = attemptItemRepository.findById(item2.getId()).orElseThrow();
            String putStatus = json(putRes2).path("results").get(0).path("status").asText();
            String subStatus = json(subRes2).path("results").get(0).path("status").asText();
            if ("SAVED".equals(putStatus)) {
                assertThat(subStatus).isEqualTo("STALE");
                assertThat(after2.getAnswerSeq()).isEqualTo(15L);
                assertThat(((AnswerValue.SingleChoice) after2.getAnswer()).optionId()).isEqualTo(i2Opt1);
            } else {
                assertThat(putStatus).isEqualTo("REJECTED");
                assertThat(json(putRes2).path("results").get(0).path("reason").asText()).isEqualTo("PART_CLOSED");
                assertThat(subStatus).isEqualTo("SAVED");
                assertThat(after2.getAnswerSeq()).isEqualTo(10L);
                assertThat(((AnswerValue.SingleChoice) after2.getAnswer()).optionId()).isEqualTo(i2Opt0);
            }
            assertThat(attemptRepository.findById(a2.getId()).orElseThrow().getState()).isEqualTo(AttemptState.GRADING);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void outOfScopeQuestionSetParentRejectsChildFinalAnswer() throws Exception {
        UUID userId = createStudent("fin-ans-set-oos");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "fa-set-oos", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        AttemptItem validSibling = singleChoiceItems(p0.getId()).getFirst();
        String validSiblingOpt = validSibling.getOptionOrder().getFirst();

        UUID adminId = UUID.randomUUID();
        UUID childQId = UUID.randomUUID();
        UUID setQId = createAndReadyQuestionSet(adminId, childQId);
        List<UUID> createdQuestions = List.of(setQId);

        try {
            int baseOrd =
                    attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId()).stream()
                            .mapToInt(AttemptItem::getOrd)
                            .max()
                            .orElse(0)
                            + 1;
            UUID setItemId = Ids.newId();
            AttemptItem setItem =
                    AttemptItem.create(
                            setItemId,
                            attempt.getId(),
                            p0.getId(),
                            null,
                            "SEC",
                            "TOAN",
                            "B",
                            null,
                            baseOrd,
                            null,
                            0,
                            setQId,
                            1,
                            QuestionType.QUESTION_SET,
                            BigDecimal.ONE,
                            null);
            AttemptItem childItem =
                    AttemptItem.create(
                            Ids.newId(),
                            attempt.getId(),
                            p0.getId(),
                            null,
                            "SEC",
                            "TOAN",
                            "B",
                            setItemId,
                            baseOrd + 1,
                            99,
                            1,
                            childQId,
                            1,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            List.of("c1a", "c1b"));
            attemptItemRepository.saveAllAndFlush(List.of(setItem, childItem));
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE id = ?", setItemId);

            // Only pass childItem (NOT setItem) so AnswerService.writeFinalAnswers must load parent via loadParentItems (R2-11)
            FinishPartRequest req =
                    new FinishPartRequest(
                            List.of(
                                    new FinalAnswerChange(
                                            childItem.getId(), 1L, new AnswerValue.SingleChoice("c1a")),
                                    new FinalAnswerChange(
                                            validSibling.getId(),
                                            1L,
                                            new AnswerValue.SingleChoice(validSiblingOpt))));
            MvcResult finRes = performFinishPart(userId, attempt.getId(), p0.getId(), sid, req);
            assertThat(finRes.getResponse().getStatus()).isEqualTo(200);

            JsonNode results = json(finRes).path("results");
            assertThat(results).hasSize(2);
            assertThat(results.get(0).path("itemId").asText()).isEqualTo(childItem.getId().toString());
            assertThat(results.get(0).path("status").asText()).isEqualTo("REJECTED");
            assertThat(results.get(0).path("reason").asText()).isEqualTo("NOT_IN_SCOPE");
            assertThat(results.get(1).path("itemId").asText()).isEqualTo(validSibling.getId().toString());
            assertThat(results.get(1).path("status").asText()).isEqualTo("SAVED");

            AttemptItem reloadedChild = attemptItemRepository.findById(childItem.getId()).orElseThrow();
            assertThat(reloadedChild.getAnswer()).isNull();
            assertThat(reloadedChild.getAnswerSeq()).isZero();
            AttemptItem reloadedSibling = attemptItemRepository.findById(validSibling.getId()).orElseThrow();
            assertThat(reloadedSibling.getAnswerSeq()).isEqualTo(1L);
            assertThat(((AnswerValue.SingleChoice) reloadedSibling.getAnswer()).optionId())
                    .isEqualTo(validSiblingOpt);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
        }
    }

    private UUID createAndReadyQuestionSet(UUID adminId, UUID childQId) throws Exception {
        QuestionBody.QuestionSetBody body =
                new QuestionBody.QuestionSetBody(
                        QuestionType.QUESTION_SET,
                        "<p>Passage</p>",
                        1,
                        List.of(
                                new QuestionBody.ChildQuestion(
                                        QuestionType.SINGLE_CHOICE,
                                        "<p>Child 1</p>",
                                        new QuestionBody.SingleChoiceBody(
                                                QuestionType.SINGLE_CHOICE,
                                                List.of(
                                                        new QuestionBody.ChoiceOption("c1a", "<p>A</p>", true),
                                                        new QuestionBody.ChoiceOption("c1b", "<p>B</p>", false)),
                                                true),
                                        null,
                                        BigDecimal.ONE,
                                        null,
                                        null,
                                        List.of(),
                                        childQId)));
        SaveQuestionRequest req =
                new SaveQuestionRequest(
                        QuestionType.QUESTION_SET,
                        "TOAN",
                        "G12",
                        "TH",
                        null,
                        null,
                        BigDecimal.ONE,
                        List.of(),
                        List.of(),
                        "<p>Set</p>",
                        null,
                        List.of(),
                        List.of(),
                        body,
                        "AT3-FA-SET-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult createRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "at3-fa-cr-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
        assertThat(createRes.getResponse().getStatus()).isBetween(200, 201);
        UUID id = UUID.fromString(json(createRes).path("summary").path("id").asText());

        MvcResult readyRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions/bulk/ready")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "at3-fa-rd-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new BulkQuestionStatusRequest(List.of(id)))))
                        .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return id;
    }
}
