package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.api.AttemptPosition;
import vn.unicom.thi360.attempt.api.FlagChange;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for {@code PUT /api/v1/me/attempts/{id}/answers}: seq ordering, clear/deselect,
 * partial saves, idempotency, part/deadline checks, per-change scope/validation, Essay sanitization,
 * 64 KB limit, 50-change limit, rate limit, flags/position persistence, forbidden-key sanitization,
 * ownership/access, and save concurrency (AC-1..AC-10, AC-17, AC-19).
 */
class AttemptAnswerIT extends AbstractAttemptDsIT {

    private record EightTypeBundle(
            List<UUID> createdQuestionIds,
            AttemptItem singleChoice,
            AttemptItem multipleChoice,
            AttemptItem shortAnswer,
            AttemptItem cloze,
            AttemptItem trueFalse,
            AttemptItem matching,
            AttemptItem essay,
            AttemptItem questionSet,
            AttemptItem setChildChoice) {}

    @Test
    void outOfOrderSeqKeepsLatest() throws Exception {
        UUID userId = createStudent("ans-ooo");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "ooo", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart part0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        AttemptItem item = singleChoiceItems(part0.getId()).getFirst();
        String opt0 = item.getOptionOrder().get(0);
        String opt1 = item.getOptionOrder().get(1);

        // Send seq=3 first
        MvcResult r3 =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(
                                                item.getId(), 3L, new AnswerValue.SingleChoice(opt1))),
                                null,
                                null));
        assertThat(r3.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(r3).path("results").get(0).path("status").asText()).isEqualTo("SAVED");

        // Late seq=1 arrives after seq=3 -> STALE, value stays opt1
        MvcResult r1 =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(
                                                item.getId(), 1L, new AnswerValue.SingleChoice(opt0))),
                                null,
                                null));
        assertThat(r1.getResponse().getStatus()).isEqualTo(200);
        JsonNode r1Json = json(r1);
        assertThat(r1Json.path("results").get(0).path("status").asText()).isEqualTo("STALE");
        assertThat(r1Json.path("results").get(0).path("reason").isNull()).isTrue();

        AttemptItem reloaded = attemptItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getAnswerSeq()).isEqualTo(3L);
        assertThat(((AnswerValue.SingleChoice) reloaded.getAnswer()).optionId()).isEqualTo(opt1);
    }

    @Test
    void clearAndDeselectSaved() throws Exception {
        UUID userId = createStudent("ans-clear");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "clear", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart part0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();

        EightTypeBundle bundle = replaceWithAllEightTypes(attempt.getId(), part0.getId());
        try {
            // Save initial answers for all 7 answerable types
            MvcResult initial =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(bundle.singleChoice().getId(), 1L, new AnswerValue.SingleChoice("o1")),
                                            new AnswerChange(bundle.multipleChoice().getId(), 1L, new AnswerValue.MultipleChoice(List.of("m1", "m3"))),
                                            new AnswerChange(bundle.shortAnswer().getId(), 1L, new AnswerValue.ShortAnswer("42")),
                                            new AnswerChange(bundle.cloze().getId(), 1L, new AnswerValue.Cloze(Map.of("b1", "hi", "b2", "there"))),
                                            new AnswerChange(bundle.trueFalse().getId(), 1L, new AnswerValue.TrueFalse(Map.of("s1", true, "s2", false))),
                                            new AnswerChange(bundle.matching().getId(), 1L, new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r2"))),
                                            new AnswerChange(bundle.essay().getId(), 1L, new AnswerValue.Essay("<p>Initial essay</p>", 2))),
                                    null,
                                    null));
            assertThat(initial.getResponse().getStatus()).isEqualTo(200);
            for (JsonNode res : json(initial).path("results")) {
                assertThat(res.path("status").asText()).isEqualTo("SAVED");
            }

            // Deselect/clear via type-specific empty/null values (seq=2)
            MvcResult deselect =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(bundle.singleChoice().getId(), 2L, new AnswerValue.SingleChoice(null)),
                                            new AnswerChange(bundle.multipleChoice().getId(), 2L, new AnswerValue.MultipleChoice(List.of())),
                                            new AnswerChange(bundle.shortAnswer().getId(), 2L, new AnswerValue.ShortAnswer("")),
                                            new AnswerChange(bundle.cloze().getId(), 2L, new AnswerValue.Cloze(Map.of())),
                                            new AnswerChange(bundle.trueFalse().getId(), 2L, new AnswerValue.TrueFalse(Map.of())),
                                            new AnswerChange(bundle.matching().getId(), 2L, new AnswerValue.Matching(Map.of())),
                                            new AnswerChange(bundle.essay().getId(), 2L, new AnswerValue.Essay("", 0))),
                                    null,
                                    null));
            assertThat(deselect.getResponse().getStatus()).isEqualTo(200);
            for (JsonNode res : json(deselect).path("results")) {
                assertThat(res.path("status").asText()).isEqualTo("SAVED");
            }

            assertThat(((AnswerValue.SingleChoice) attemptItemRepository.findById(bundle.singleChoice().getId()).orElseThrow().getAnswer()).optionId()).isNull();
            assertThat(((AnswerValue.MultipleChoice) attemptItemRepository.findById(bundle.multipleChoice().getId()).orElseThrow().getAnswer()).optionIds()).isEmpty();
            assertThat(((AnswerValue.ShortAnswer) attemptItemRepository.findById(bundle.shortAnswer().getId()).orElseThrow().getAnswer()).text()).isEmpty();
            assertThat(((AnswerValue.Cloze) attemptItemRepository.findById(bundle.cloze().getId()).orElseThrow().getAnswer()).blanks()).isEmpty();
            assertThat(((AnswerValue.TrueFalse) attemptItemRepository.findById(bundle.trueFalse().getId()).orElseThrow().getAnswer()).statements()).isEmpty();
            assertThat(((AnswerValue.Matching) attemptItemRepository.findById(bundle.matching().getId()).orElseThrow().getAnswer()).pairs()).isEmpty();
            AnswerValue.Essay clearedEssay = (AnswerValue.Essay) attemptItemRepository.findById(bundle.essay().getId()).orElseThrow().getAnswer();
            assertThat(clearedEssay.html()).isEmpty();
            assertThat(clearedEssay.wordCount()).isZero();

            // Clear entirely with answer=null (seq=3) across all 7 types
            MvcResult clearNull =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(bundle.singleChoice().getId(), 3L, null),
                                            new AnswerChange(bundle.multipleChoice().getId(), 3L, null),
                                            new AnswerChange(bundle.shortAnswer().getId(), 3L, null),
                                            new AnswerChange(bundle.cloze().getId(), 3L, null),
                                            new AnswerChange(bundle.trueFalse().getId(), 3L, null),
                                            new AnswerChange(bundle.matching().getId(), 3L, null),
                                            new AnswerChange(bundle.essay().getId(), 3L, null)),
                                    null,
                                    null));
            assertThat(clearNull.getResponse().getStatus()).isEqualTo(200);
            for (AttemptItem item :
                    List.of(
                            bundle.singleChoice(),
                            bundle.multipleChoice(),
                            bundle.shortAnswer(),
                            bundle.cloze(),
                            bundle.trueFalse(),
                            bundle.matching(),
                            bundle.essay())) {
                AttemptItem reloaded = attemptItemRepository.findById(item.getId()).orElseThrow();
                assertThat(reloaded.getAnswerSeq()).isEqualTo(3L);
                assertThat(reloaded.getAnswer()).isNull();
            }
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, bundle.createdQuestionIds());
        }
    }

    @Test
    void partialAnswersSaved() throws Exception {
        UUID userId = createStudent("ans-partial");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "partial", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart part0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();

        EightTypeBundle bundle = replaceWithAllEightTypes(attempt.getId(), part0.getId());
        try {
            Map<String, Boolean> partialTf = new LinkedHashMap<>();
            partialTf.put("s1", true);
            partialTf.put("s2", null);

            Map<String, String> partialCloze = new LinkedHashMap<>();
            partialCloze.put("b1", "only-b1");
            partialCloze.put("b2", null);

            Map<String, String> partialMatch = new LinkedHashMap<>();
            partialMatch.put("l1", "r2");
            partialMatch.put("l2", null);

            MvcResult res =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(bundle.trueFalse().getId(), 1L, new AnswerValue.TrueFalse(partialTf)),
                                            new AnswerChange(bundle.cloze().getId(), 1L, new AnswerValue.Cloze(partialCloze)),
                                            new AnswerChange(bundle.matching().getId(), 1L, new AnswerValue.Matching(partialMatch))),
                                    null,
                                    null));
            assertThat(res.getResponse().getStatus()).isEqualTo(200);
            for (JsonNode itemRes : json(res).path("results")) {
                assertThat(itemRes.path("status").asText()).isEqualTo("SAVED");
            }

            AnswerValue.TrueFalse savedTf =
                    (AnswerValue.TrueFalse) attemptItemRepository.findById(bundle.trueFalse().getId()).orElseThrow().getAnswer();
            assertThat(savedTf.statements()).containsEntry("s1", true).containsEntry("s2", null);

            AnswerValue.Cloze savedCloze =
                    (AnswerValue.Cloze) attemptItemRepository.findById(bundle.cloze().getId()).orElseThrow().getAnswer();
            assertThat(savedCloze.blanks()).containsEntry("b1", "only-b1").containsEntry("b2", null);

            AnswerValue.Matching savedMatch =
                    (AnswerValue.Matching) attemptItemRepository.findById(bundle.matching().getId()).orElseThrow().getAnswer();
            assertThat(savedMatch.pairs()).containsEntry("l1", "r2").containsEntry("l2", null);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, bundle.createdQuestionIds());
        }
    }

    @Test
    void resendSameSeqIsIdempotent() throws Exception {
        UUID userId = createStudent("ans-idem");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "idem", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart part0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        AttemptItem item = attemptItemRepository.findByPartIdOrderByOrdAsc(part0.getId()).getFirst();

        MvcResult first =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(
                                                item.getId(), 4L, new AnswerValue.SingleChoice("b"))),
                                null,
                                null));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(first).path("results").get(0).path("status").asText()).isEqualTo("SAVED");
        AttemptItem afterFirst = attemptItemRepository.findById(item.getId()).orElseThrow();
        Instant originalReceivedAt = afterFirst.getAnswerReceivedAt();

        mutableClock.advance(Duration.ofSeconds(15));
        MvcResult resend =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(
                                                item.getId(), 4L, new AnswerValue.SingleChoice("a"))),
                                null,
                                null));
        assertThat(resend.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resend).path("results").get(0).path("status").asText()).isEqualTo("SAVED");

        AttemptItem afterResend = attemptItemRepository.findById(item.getId()).orElseThrow();
        assertThat(((AnswerValue.SingleChoice) afterResend.getAnswer()).optionId()).isEqualTo("b");
        assertThat(afterResend.getAnswerReceivedAt()).isEqualTo(originalReceivedAt);
    }

    @Test
    void closedPartRejected() throws Exception {
        UUID userId = createStudent("ans-closed");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "cls", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem p0Item = singleChoiceItems(parts.get(0).getId()).getFirst();
        AttemptItem p1Item = singleChoiceItems(parts.get(1).getId()).getFirst();
        String p0Opt0 = p0Item.getOptionOrder().get(0);
        String p1Opt1 = p1Item.getOptionOrder().get(1);

        // Finish Part 0 -> Part 0 CLOSED, Part 1 OPEN
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sid, null)
                .getResponse().getStatus()).isEqualTo(200);

        // Save on closed Part 0 -> REJECTED PART_CLOSED; save on open Part 1 -> SAVED
        MvcResult mixedRes =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(p0Item.getId(), 1L, new AnswerValue.SingleChoice(p0Opt0)),
                                        new AnswerChange(p1Item.getId(), 1L, new AnswerValue.SingleChoice(p1Opt1))),
                                null,
                                null));
        assertThat(mixedRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode mixedJson = json(mixedRes);
        assertThat(mixedJson.path("results").get(0).path("status").asText()).isEqualTo("REJECTED");
        assertThat(mixedJson.path("results").get(0).path("reason").asText()).isEqualTo("PART_CLOSED");
        assertThat(mixedJson.path("results").get(1).path("status").asText()).isEqualTo("SAVED");
    }

    @Test
    void afterDeadlineRejected() throws Exception {
        UUID userId = createStudent("ans-deadline");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "dl", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem p1Item = singleChoiceItems(parts.get(1).getId()).getFirst();
        String p1Opt0 = p1Item.getOptionOrder().get(0);
        String p1Opt1 = p1Item.getOptionOrder().get(1);

        // Finish Part 0 -> Part 1 OPEN with its deadline set
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sid, null)
                .getResponse().getStatus()).isEqualTo(200);

        // Boundary: exact (p1.deadlineAt - 1s) is accepted; exact p1.deadlineAt is TIME_UP
        Instant p1Deadline = attemptPartRepository.findById(parts.get(1).getId()).orElseThrow().getDeadlineAt();
        mutableClock.setAt(p1Deadline.minusSeconds(1));
        MvcResult beforeDeadline =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p1Item.getId(), 2L, new AnswerValue.SingleChoice(p1Opt0))),
                                null,
                                null));
        assertThat(beforeDeadline.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(beforeDeadline).path("results").get(0).path("status").asText()).isEqualTo("SAVED");

        mutableClock.setAt(p1Deadline);
        MvcResult atExactDeadline =
                performPutAnswers(
                        userId,
                        attempt.getId(),
                        sid,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p1Item.getId(), 3L, new AnswerValue.SingleChoice(p1Opt1))),
                                null,
                                null));
        assertThat(atExactDeadline.getResponse().getStatus()).isEqualTo(200);
        JsonNode dlJson = json(atExactDeadline);
        assertThat(dlJson.path("results").get(0).path("status").asText()).isEqualTo("REJECTED");
        assertThat(dlJson.path("results").get(0).path("reason").asText()).isEqualTo("TIME_UP");
        assertThat(attemptItemRepository.findById(p1Item.getId()).orElseThrow().getAnswerSeq()).isEqualTo(2L);
    }

    @Test
    void outOfScopeRejectedPerChange() throws Exception {
        UUID userId = createStudent("ans-scope");
        UUID userId2 = createStudent("ans-scope-2");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line line2 = grant(userId2, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt1 = startAndAwaitOpen(userId, "scope-1", line.entitlementId(), null);
        Attempt attempt2 = startAndAwaitOpen(userId2, "scope-2", line2.entitlementId(), null);
        UUID sid1 = claimSession(userId, attempt1.getId());

        AttemptPart p1 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt1.getId()).getFirst();
        AttemptPart p2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt2.getId()).getFirst();
        EightTypeBundle bundle = replaceWithAllEightTypes(attempt1.getId(), p1.getId());

        try {
            // Mark shortAnswer item out of scope (in_scope = false)
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE id = ?", bundle.shortAnswer().getId());
            UUID foreignItemId = attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId()).getFirst().getId();
            UUID missingItemId = UUID.randomUUID();

            MvcResult res =
                    performPutAnswers(
                            userId,
                            attempt1.getId(),
                            sid1,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(foreignItemId, 1L, new AnswerValue.SingleChoice("a")),
                                            new AnswerChange(missingItemId, 1L, new AnswerValue.SingleChoice("a")),
                                            new AnswerChange(bundle.shortAnswer().getId(), 1L, new AnswerValue.ShortAnswer("42")),
                                            new AnswerChange(bundle.questionSet().getId(), 1L, new AnswerValue.SingleChoice("o1")),
                                            new AnswerChange(bundle.singleChoice().getId(), 1L, new AnswerValue.SingleChoice("unknown")),
                                            new AnswerChange(bundle.setChildChoice().getId(), 1L, new AnswerValue.SingleChoice("c1a"))),
                                    null,
                                    null));
            assertThat(res.getResponse().getStatus()).isEqualTo(200);
            JsonNode results = json(res).path("results");
            assertThat(results).hasSize(6);
            assertThat(results.get(0).path("status").asText()).isEqualTo("REJECTED");
            assertThat(results.get(0).path("reason").asText()).isEqualTo("NOT_IN_SCOPE");
            assertThat(results.get(1).path("status").asText()).isEqualTo("REJECTED");
            assertThat(results.get(1).path("reason").asText()).isEqualTo("NOT_IN_SCOPE");
            assertThat(results.get(2).path("status").asText()).isEqualTo("REJECTED");
            assertThat(results.get(2).path("reason").asText()).isEqualTo("NOT_IN_SCOPE");
            assertThat(results.get(3).path("status").asText()).isEqualTo("REJECTED");
            assertThat(results.get(3).path("reason").asText()).isEqualTo("INVALID_ANSWER");
            assertThat(results.get(4).path("status").asText()).isEqualTo("REJECTED");
            assertThat(results.get(4).path("reason").asText()).isEqualTo("INVALID_ANSWER");
            // Valid child item of QUESTION_SET in same batch STILL succeeded!
            assertThat(results.get(5).path("status").asText()).isEqualTo("SAVED");

            AttemptItem savedChild = attemptItemRepository.findById(bundle.setChildChoice().getId()).orElseThrow();
            assertThat(savedChild.getAnswerSeq()).isEqualTo(1L);
            assertThat(((AnswerValue.SingleChoice) savedChild.getAnswer()).optionId()).isEqualTo("c1a");

            List<AttemptEvent> rejectEvents =
                    attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt1.getId()).stream()
                            .filter(e -> e.getType() == AttemptEventType.SAVE_REJECTED)
                            .toList();
            assertThat(rejectEvents).hasSize(5);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, bundle.createdQuestionIds());
        }
    }

    @Test
    void essayHtmlSanitized() throws Exception {
        UUID userId = createStudent("ans-essay-sanitize");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "essay-san", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        EightTypeBundle bundle = replaceWithAllEightTypes(attempt.getId(), p0.getId());

        try {
            // Essay with <img>, <script>, onerror stripped + BS §6 wordCount persisted
            String dirtyHtml =
                    "<p>B\u00e0i l\u00e0m\u00A0c\u1ee7a em</p><script>alert(1)</script>"
                            + "<img src=\"/api/v1/admin/question-assets/11111111-1111-1111-1111-111111111111\" onerror=\"x()\">";
            MvcResult cleanRes =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(
                                                    bundle.essay().getId(),
                                                    1L,
                                                    new AnswerValue.Essay(dirtyHtml, 999))),
                                    null,
                                    null));
            assertThat(cleanRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(cleanRes).path("results").get(0).path("status").asText()).isEqualTo("SAVED");

            AnswerValue.Essay savedEssay =
                    (AnswerValue.Essay) attemptItemRepository.findById(bundle.essay().getId()).orElseThrow().getAnswer();
            assertThat(savedEssay.html())
                    .doesNotContain("<img")
                    .doesNotContain("<script")
                    .doesNotContain("onerror")
                    .contains("Bài làm");
            assertThat(savedEssay.wordCount()).isEqualTo(4);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, bundle.createdQuestionIds());
        }
    }

    @Test
    void answerOver64KbRejected() throws Exception {
        UUID userId = createStudent("ans-64kb");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "ans64", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());
        AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        EightTypeBundle bundle = replaceWithAllEightTypes(attempt.getId(), p0.getId());

        try {
            // Exact 65,536 bytes accepted vs 65,537 bytes rejected (ShortAnswer + Essay)
            String exact64Kb = "a".repeat(65_536);
            String over64Kb = "a".repeat(65_537);
            assertThat(exact64Kb.getBytes(StandardCharsets.UTF_8)).hasSize(65_536);
            assertThat(over64Kb.getBytes(StandardCharsets.UTF_8)).hasSize(65_537);

            MvcResult boundaryRes =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(bundle.shortAnswer().getId(), 1L, new AnswerValue.ShortAnswer(exact64Kb)),
                                            new AnswerChange(bundle.shortAnswer().getId(), 2L, new AnswerValue.ShortAnswer(over64Kb)),
                                            new AnswerChange(bundle.essay().getId(), 2L, new AnswerValue.Essay(exact64Kb, 1)),
                                            new AnswerChange(bundle.essay().getId(), 3L, new AnswerValue.Essay(over64Kb, 1))),
                                    null,
                                    null));
            assertThat(boundaryRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode bResults = json(boundaryRes).path("results");
            assertThat(bResults.get(0).path("status").asText()).isEqualTo("SAVED");
            assertThat(bResults.get(1).path("status").asText()).isEqualTo("REJECTED");
            assertThat(bResults.get(1).path("reason").asText()).isEqualTo("INVALID_ANSWER");
            assertThat(bResults.get(2).path("status").asText()).isEqualTo("SAVED");
            assertThat(bResults.get(3).path("status").asText()).isEqualTo("REJECTED");
            assertThat(bResults.get(3).path("reason").asText()).isEqualTo("INVALID_ANSWER");
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, bundle.createdQuestionIds());
        }
    }

    @Test
    void changeLimitEnforced() throws Exception {
        UUID userId = createStudent("ans-change-limit");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attemptA = startAndAwaitOpen(userId, "lim-chg", line.entitlementId(), null);
        UUID sidA = claimSession(userId, attemptA.getId());

        AttemptItem itemA =
                singleChoiceItems(
                                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptA.getId()).getFirst().getId())
                        .getFirst();
        String optA0 = itemA.getOptionOrder().get(0);
        String optA1 = itemA.getOptionOrder().get(1);

        // 50 changes accepted, 51 changes -> 400 VALIDATION_FAILED (changes limit)
        List<AnswerChange> fifty = new ArrayList<>(50);
        for (int i = 1; i <= 50; i++) {
            fifty.add(new AnswerChange(itemA.getId(), i, new AnswerValue.SingleChoice(optA0)));
        }
        MvcResult ok50 =
                performPutAnswers(userId, attemptA.getId(), sidA, new SaveAnswersRequest(fifty, null, null));
        assertThat(ok50.getResponse().getStatus()).isEqualTo(200);

        List<AnswerChange> fiftyOne = new ArrayList<>(fifty);
        fiftyOne.add(new AnswerChange(itemA.getId(), 51L, new AnswerValue.SingleChoice(optA1)));
        MvcResult bad51 =
                performPutAnswers(userId, attemptA.getId(), sidA, new SaveAnswersRequest(fiftyOne, null, null));
        assertThat(bad51.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(bad51).path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void rateLimitPerAttempt() throws Exception {
        UUID userId = createStudent("ans-rate-limit");
        UUID userIdB = createStudent("ans-rate-limit-b");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line lineB = grant(userIdB, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attemptA = startAndAwaitOpen(userId, "lim-a", line.entitlementId(), null);
        Attempt attemptB = startAndAwaitOpen(userIdB, "lim-b", lineB.entitlementId(), null);
        UUID sidA = claimSession(userId, attemptA.getId());
        UUID sidB = claimSession(userIdB, attemptB.getId());

        AttemptItem itemA =
                singleChoiceItems(
                                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptA.getId()).getFirst().getId())
                        .getFirst();
        AttemptItem itemB =
                singleChoiceItems(
                                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptB.getId()).getFirst().getId())
                        .getFirst();
        String optA0 = itemA.getOptionOrder().get(0);
        String optA1 = itemA.getOptionOrder().get(1);
        String optB0 = itemB.getOptionOrder().get(0);

        // Consume tokens 1..20 -> all 200; request 21 -> 429 RATE_LIMITED + Retry-After
        for (int i = 1; i <= 20; i++) {
            MvcResult r =
                    performPutAnswers(
                            userId,
                            attemptA.getId(),
                            sidA,
                            new SaveAnswersRequest(
                                    List.of(new AnswerChange(itemA.getId(), 100L + i, new AnswerValue.SingleChoice(optA0))),
                                    null,
                                    null));
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
        }

        MvcResult limited =
                performPutAnswers(
                        userId,
                        attemptA.getId(),
                        sidA,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(itemA.getId(), 200L, new AnswerValue.SingleChoice(optA1))),
                                null,
                                null));
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(json(limited).path("code").asText()).isEqualTo("RATE_LIMITED");
        String retryAfter = limited.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1L);

        // Separate attempt B has its own rate limit bucket -> still 200
        MvcResult otherAttemptOk =
                performPutAnswers(
                        userIdB,
                        attemptB.getId(),
                        sidB,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(itemB.getId(), 1L, new AnswerValue.SingleChoice(optB0))),
                                null,
                                null));
        assertThat(otherAttemptOk.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void positionAndFlagsPersist() throws Exception {
        UUID userId = createStudent("ans-pos-flags");
        UUID otherUser = createStudent("ans-pos-other");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line otherLine = grant(otherUser, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "pos-flg", line.entitlementId(), null);
        Attempt otherAttempt = startAndAwaitOpen(otherUser, "pos-oth", otherLine.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart part0 = parts.get(0);
        EightTypeBundle bundle = replaceWithAllEightTypes(attempt.getId(), part0.getId());

        AttemptPart otherPart = attemptPartRepository.findByAttemptIdOrderByOrdAsc(otherAttempt.getId()).getFirst();
        AttemptItem otherItem = attemptItemRepository.findByPartIdOrderByOrdAsc(otherPart.getId()).getFirst();

        try {
            // Save MATCHING answer (with pairs), flag=true, and valid position
            MvcResult putRes =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(
                                                    bundle.matching().getId(),
                                                    1L,
                                                    new AnswerValue.Matching(Map.of("l1", "r2")))),
                                    List.of(
                                            new FlagChange(bundle.singleChoice().getId(), true),
                                            new FlagChange(otherItem.getId(), true)),
                                    new AttemptPosition(part0.getId(), bundle.matching().getId())));
            assertThat(putRes.getResponse().getStatus()).isEqualTo(200);

            // Read back via GET /api/v1/me/attempts/{id}
            MvcResult stateRes = performGetState(userId, attempt.getId());
            assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode stateJson = objectMapper.readTree(stateRes.getResponse().getContentAsString());
            assertThat(stateJson.path("lastPosition").path("partId").asText())
                    .isEqualTo(part0.getId().toString());
            assertThat(stateJson.path("lastPosition").path("itemId").asText())
                    .isEqualTo(bundle.matching().getId().toString());

            boolean foundFlagged = false;
            boolean foundMatchingPairs = false;
            for (JsonNode itemNode : stateJson.path("parts").get(0).path("items")) {
                if (bundle.singleChoice().getId().toString().equals(itemNode.path("itemId").asText())) {
                    assertThat(itemNode.path("flagged").asBoolean()).isTrue();
                    foundFlagged = true;
                }
                if (bundle.matching().getId().toString().equals(itemNode.path("itemId").asText())) {
                    assertThat(itemNode.path("answer").path("pairs").path("l1").asText()).isEqualTo("r2");
                    foundMatchingPairs = true;
                }
            }
            assertThat(foundFlagged).isTrue();
            assertThat(foundMatchingPairs).isTrue();

            // Foreign partId/itemId in position must NOT overwrite lastPosition
            MvcResult foreignPosRes =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(),
                                    List.of(),
                                    new AttemptPosition(otherPart.getId(), otherItem.getId())));
            assertThat(foreignPosRes.getResponse().getStatus()).isEqualTo(200);
            Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
            assertThat(reloaded.getLastPosition()).containsEntry("partId", part0.getId().toString());
            assertThat(reloaded.getLastPosition()).containsEntry("itemId", bundle.matching().getId().toString());

            // Unflag works
            performPutAnswers(
                    userId,
                    attempt.getId(),
                    sid,
                    new SaveAnswersRequest(null, List.of(new FlagChange(bundle.singleChoice().getId(), false)), null));
            assertThat(attemptItemRepository.findById(bundle.singleChoice().getId()).orElseThrow().isFlagged()).isFalse();
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, bundle.createdQuestionIds());
        }
    }

    @Test
    void forbiddenKeysAbsent() throws Exception {
        UUID userId = createStudent("ans-forbidden");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(userId, "forbid", line.entitlementId(), null);
        UUID sid = claimSession(userId, attempt.getId());

        AttemptPart part0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
        EightTypeBundle bundle = replaceWithAllEightTypes(attempt.getId(), part0.getId());

        try {
            // Save MATCHING answer (with pairs), flag=true, and valid position -> verify ForbiddenKeysAssert on PUT
            MvcResult putRes =
                    performPutAnswers(
                            userId,
                            attempt.getId(),
                            sid,
                            new SaveAnswersRequest(
                                    List.of(
                                            new AnswerChange(
                                                    bundle.matching().getId(),
                                                    1L,
                                                    new AnswerValue.Matching(Map.of("l1", "r2")))),
                                    List.of(new FlagChange(bundle.singleChoice().getId(), true)),
                                    new AttemptPosition(part0.getId(), bundle.matching().getId())));
            assertThat(putRes.getResponse().getStatus()).isEqualTo(200);
            ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(putRes.getResponse().getContentAsString());

            // Read back via GET /api/v1/me/attempts/{id} and run ForbiddenKeysAssert (AC-10, AC-17)
            MvcResult stateRes = performGetState(userId, attempt.getId());
            assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
            String stateRaw = stateRes.getResponse().getContentAsString();
            ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(stateRaw);

            JsonNode stateJson = objectMapper.readTree(stateRaw);
            boolean foundMatchingPairs = false;
            for (JsonNode itemNode : stateJson.path("parts").get(0).path("items")) {
                if (bundle.matching().getId().toString().equals(itemNode.path("itemId").asText())) {
                    assertThat(itemNode.path("answer").path("pairs").path("l1").asText()).isEqualTo("r2");
                    foundMatchingPairs = true;
                }
            }
            assertThat(foundMatchingPairs).isTrue();
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, bundle.createdQuestionIds());
        }
    }

    @Test
    void ownershipAndAccess() throws Exception {
        UUID owner = createStudent("ans-owner");
        UUID stranger = createStudent("ans-stranger");
        UUID manager = createManager("ans-manager");
        GrantOrderResult.Line line = grant(owner, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(owner, "ans-auth", line.entitlementId(), null);

        // Anonymous -> 401
        assertThat(mockMvc.perform(put("/api/v1/me/attempts/{id}/answers", attempt.getId()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);

        // MANAGE -> 403 ATTEMPT_ACCESS_FORBIDDEN
        MvcResult mgrRes =
                mockMvc.perform(
                                put("/api/v1/me/attempts/{id}/answers", attempt.getId())
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(mgrRes.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrRes).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        // Other student -> 404
        MvcResult strRes = performPutAnswers(stranger, attempt.getId(), UUID.randomUUID(), new SaveAnswersRequest(null, null, null));
        assertThat(strRes.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(strRes).path("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void concurrentSaveHigherSeqWinsAndFinishRaceNeverWritesAfterClosedAt() throws Exception {
        UUID userId = createStudent("ans-race");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 3, List.of());

        // 1. Two concurrent PUT /answers on same item with seq=1 vs seq=2 -> higher seq ALWAYS wins
        Attempt a1 = startAndAwaitOpen(userId, "race-seq", line.entitlementId(), null);
        UUID sid1 = claimSession(userId, a1.getId());
        AttemptPart a1P0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).getFirst();
        AttemptItem item1 = singleChoiceItems(a1P0.getId()).getFirst();
        String item1Opt0 = item1.getOptionOrder().get(0);
        String item1Opt1 = item1.getOptionOrder().get(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 3; round++) {
                long lowSeq = round * 10L + 1;
                long highSeq = round * 10L + 2;
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Callable<MvcResult> lowCall = () -> {
                    ready.countDown();
                    assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                    return performPutAnswers(
                            userId, a1.getId(), sid1,
                            new SaveAnswersRequest(List.of(new AnswerChange(item1.getId(), lowSeq, new AnswerValue.SingleChoice(item1Opt0))), null, null));
                };
                Callable<MvcResult> highCall = () -> {
                    ready.countDown();
                    assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                    return performPutAnswers(
                            userId, a1.getId(), sid1,
                            new SaveAnswersRequest(List.of(new AnswerChange(item1.getId(), highSeq, new AnswerValue.SingleChoice(item1Opt1))), null, null));
                };
                Future<MvcResult> fLow = pool.submit(lowCall);
                Future<MvcResult> fHigh = pool.submit(highCall);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();

                assertThat(fLow.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
                assertThat(fHigh.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);

                AttemptItem after = attemptItemRepository.findById(item1.getId()).orElseThrow();
                assertThat(after.getAnswerSeq()).isEqualTo(highSeq);
                assertThat(((AnswerValue.SingleChoice) after.getAnswer()).optionId()).isEqualTo(item1Opt1);
            }

            // 2. PUT /answers racing with POST /finish -> no answer ever written with answer_received_at > closed_at
            UUID userId2 = createStudent("ans-race-fin");
            GrantOrderResult.Line line2 = grant(userId2, "DS-HSA-FULL-MINI", 2, List.of());
            Attempt a2 = startAndAwaitOpen(userId2, "race-fin", line2.entitlementId(), null);
            UUID sid2 = claimSession(userId2, a2.getId());
            List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
            AttemptPart p0 = a2Parts.get(0);
            AttemptItem p0Item = singleChoiceItems(p0.getId()).getFirst();
            String p0Opt0 = p0Item.getOptionOrder().get(0);

            CountDownLatch ready2 = new CountDownLatch(2);
            CountDownLatch go2 = new CountDownLatch(1);
            Future<MvcResult> putFuture = pool.submit(() -> {
                ready2.countDown();
                assertThat(go2.await(10, TimeUnit.SECONDS)).isTrue();
                return performPutAnswers(
                        userId2, a2.getId(), sid2,
                        new SaveAnswersRequest(List.of(new AnswerChange(p0Item.getId(), 1L, new AnswerValue.SingleChoice(p0Opt0))), null, null));
            });
            Future<MvcResult> finFuture = pool.submit(() -> {
                ready2.countDown();
                assertThat(go2.await(10, TimeUnit.SECONDS)).isTrue();
                mutableClock.advance(Duration.ofSeconds(1));
                return performFinishPart(userId2, a2.getId(), p0.getId(), sid2, null);
            });
            assertThat(ready2.await(10, TimeUnit.SECONDS)).isTrue();
            go2.countDown();

            MvcResult putRes = putFuture.get(15, TimeUnit.SECONDS);
            MvcResult finRes = finFuture.get(15, TimeUnit.SECONDS);
            assertThat(putRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(finRes.getResponse().getStatus()).isEqualTo(200);

            AttemptPart closedP0 = attemptPartRepository.findById(p0.getId()).orElseThrow();
            AttemptItem p0ItemAfter = attemptItemRepository.findById(p0Item.getId()).orElseThrow();
            assertThat(closedP0.getClosedAt()).isNotNull();
            if (p0ItemAfter.getAnswerReceivedAt() != null) {
                assertThat(p0ItemAfter.getAnswerReceivedAt()).isBeforeOrEqualTo(closedP0.getClosedAt());
            } else {
                assertThat(json(putRes).path("results").get(0).path("status").asText()).isEqualTo("REJECTED");
                assertThat(json(putRes).path("results").get(0).path("reason").asText()).isEqualTo("PART_CLOSED");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private EightTypeBundle replaceWithAllEightTypes(UUID attemptId, UUID partId) throws Exception {
        UUID adminId = UUID.randomUUID();
        List<UUID> createdIds = new ArrayList<>();

        UUID scId = createAndReadyQuestion(adminId, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, "<p>SC</p>",
                new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
                        new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                        new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)), true));
        createdIds.add(scId);

        UUID mcId = createAndReadyQuestion(adminId, QuestionType.MULTIPLE_CHOICE, BigDecimal.ONE, "<p>MC</p>",
                new QuestionBody.MultipleChoiceBody(QuestionType.MULTIPLE_CHOICE, List.of(
                        new QuestionBody.ChoiceOption("m1", "<p>1</p>", true),
                        new QuestionBody.ChoiceOption("m2", "<p>2</p>", false),
                        new QuestionBody.ChoiceOption("m3", "<p>3</p>", true)),
                        QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING, true));
        createdIds.add(mcId);

        UUID saId = createAndReadyQuestion(adminId, QuestionType.SHORT_ANSWER, BigDecimal.ONE, "<p>SA</p>",
                new QuestionBody.ShortAnswerBody(QuestionType.SHORT_ANSWER, List.of("42"), "hint", null));
        createdIds.add(saId);

        UUID clozeId = createAndReadyQuestion(adminId, QuestionType.CLOZE, BigDecimal.ONE, "<p>Cloze</p>",
                new QuestionBody.ClozeBody(QuestionType.CLOZE, "<p>[[b1]] [[b2]]</p>", List.of(
                        new QuestionBody.ClozeBlank("b1", List.of("a"), BigDecimal.ONE),
                        new QuestionBody.ClozeBlank("b2", List.of("b"), BigDecimal.ONE))));
        createdIds.add(clozeId);

        UUID tfId = createAndReadyQuestion(adminId, QuestionType.TRUE_FALSE, BigDecimal.ONE, "<p>TF</p>",
                new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
                        new QuestionBody.TrueFalseStatement("s1", "<p>S1</p>", true, new BigDecimal("0.5")),
                        new QuestionBody.TrueFalseStatement("s2", "<p>S2</p>", false, new BigDecimal("0.5"))),
                        QuestionBody.TrueFalseScoringMethod.PER_STATEMENT, null));
        createdIds.add(tfId);

        UUID matchId = createAndReadyQuestion(adminId, QuestionType.MATCHING, BigDecimal.ONE, "<p>Match</p>",
                new QuestionBody.MatchingBody(QuestionType.MATCHING,
                        List.of(new QuestionBody.MatchingSide("l1", "<p>L1</p>"), new QuestionBody.MatchingSide("l2", "<p>L2</p>")),
                        List.of(new QuestionBody.MatchingSide("r1", "<p>R1</p>"), new QuestionBody.MatchingSide("r2", "<p>R2</p>")),
                        List.of(new QuestionBody.MatchingPair("l1", "r1", new BigDecimal("0.5")), new QuestionBody.MatchingPair("l2", "r2", new BigDecimal("0.5"))),
                        false));
        createdIds.add(matchId);

        UUID essayId = createAndReadyQuestion(adminId, QuestionType.ESSAY, BigDecimal.ONE, "<p>Essay</p>",
                new QuestionBody.EssayBody(QuestionType.ESSAY, "<p>Guide</p>", List.of(), null, 100));
        createdIds.add(essayId);

        UUID child1Id = UUID.randomUUID();
        UUID child2Id = UUID.randomUUID();
        UUID setId = createAndReadyQuestion(adminId, QuestionType.QUESTION_SET, new BigDecimal("2"), "<p>Set</p>",
                new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET, "<p>Passage</p>", 1, List.of(
                        new QuestionBody.ChildQuestion(QuestionType.SINGLE_CHOICE, "<p>C1</p>",
                                new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
                                        new QuestionBody.ChoiceOption("c1a", "<p>A</p>", true),
                                        new QuestionBody.ChoiceOption("c1b", "<p>B</p>", false)), true),
                                null, BigDecimal.ONE, null, null, List.of(), child1Id),
                        new QuestionBody.ChildQuestion(QuestionType.SHORT_ANSWER, "<p>C2</p>",
                                new QuestionBody.ShortAnswerBody(QuestionType.SHORT_ANSWER, List.of("9"), null, null),
                                null, BigDecimal.ONE, null, null, List.of(), child2Id))));
        createdIds.add(setId);

        jdbc.update("DELETE FROM attempt.attempt_item WHERE part_id = ?", partId);
        int ord = 0;
        UUID setItemId = Ids.newId();
        AttemptItem scItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, 1, 1, scId, 1, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, List.of("o1", "o2"));
        AttemptItem mcItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, 2, 1, mcId, 1, QuestionType.MULTIPLE_CHOICE, BigDecimal.ONE, List.of("m1", "m2", "m3"));
        AttemptItem saItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, 3, 1, saId, 1, QuestionType.SHORT_ANSWER, BigDecimal.ONE, null);
        AttemptItem clozeItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, 4, 2, clozeId, 1, QuestionType.CLOZE, new BigDecimal("2"), null);
        AttemptItem tfItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, 6, 1, tfId, 1, QuestionType.TRUE_FALSE, BigDecimal.ONE, null);
        AttemptItem matchItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, 7, 1, matchId, 1, QuestionType.MATCHING, BigDecimal.ONE, List.of("r1", "r2"));
        AttemptItem essayItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, 8, 1, essayId, 1, QuestionType.ESSAY, BigDecimal.ONE, null);
        AttemptItem setItem = AttemptItem.create(setItemId, attemptId, partId, null, "SEC", "TOAN", "B", null, ord++, null, 0, setId, 1, QuestionType.QUESTION_SET, new BigDecimal("2"), null);
        attemptItemRepository.saveAllAndFlush(List.of(scItem, mcItem, saItem, clozeItem, tfItem, matchItem, essayItem, setItem));

        AttemptItem childItem = AttemptItem.create(Ids.newId(), attemptId, partId, null, "SEC", "TOAN", "B", setItemId, ord, 9, 1, child1Id, 1, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, List.of("c1a", "c1b"));
        attemptItemRepository.saveAndFlush(childItem);

        return new EightTypeBundle(createdIds, scItem, mcItem, saItem, clozeItem, tfItem, matchItem, essayItem, setItem, childItem);
    }

    private UUID createAndReadyQuestion(
            UUID adminId, QuestionType type, BigDecimal points, String content, QuestionBody body)
            throws Exception {
        SaveQuestionRequest req =
                new SaveQuestionRequest(
                        type, "TOAN", "G12", "TH", null, null, points,
                        List.of(), List.of(), content, null, List.of(), List.of(), body,
                        "AT3-8T-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult createRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "at3-cr-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
        assertThat(createRes.getResponse().getStatus()).isBetween(200, 201);
        UUID id = UUID.fromString(json(createRes).path("summary").path("id").asText());

        MvcResult readyRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions/bulk/ready")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "at3-rd-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(id)))))
                        .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return id;
    }
}
