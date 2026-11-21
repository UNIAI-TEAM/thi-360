package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptLifecycleService;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

class AttemptResultIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private QuestionSourcePort questionSourcePort;
    @Autowired private AttemptLifecycleService attemptLifecycleService;

    @BeforeEach
    void drainOutbox() {
        while (outboxRelay.relayBatch(500) > 0) {
            // drain prior seed events
        }
    }

    @Test
    void scoreViewDoesNotLeakAnswers() throws Exception {
        UUID student = createStudent("res-score");
        UUID stranger = createStudent("res-stranger");
        UUID manager = createManager("res-mgr");

        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "SCORE", serving.version().versionNo());

        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        // In-progress attempt cannot view result yet
        MvcResult inProgressRes = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(inProgressRes.getResponse().getStatus()).isEqualTo(409);

        MvcResult submit = performSubmit(
                student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        assertThat(submit.getResponse().getStatus()).isEqualTo(200);
        outboxRelay.relayBatch(500);

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        String body = res.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(body);

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("state").asText()).isEqualTo("SCORED");
        assertThat(json.path("reviewAvailable").asBoolean()).isFalse();
        assertThat(json.path("resultView").asText()).isEqualTo("SCORE");

        // Auth & ownership gates
        assertThat(mockMvc.perform(get("/api/v1/me/attempts/{id}/result", attempt.getId()))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mockMvc.perform(
                                get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(
                                get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                        .with(AttemptTestFixtures.asExamUser(stranger, "stranger")))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void pendingReviewShowsLabelNoTotal() throws Exception {
        UUID student = createStudent("res-pending");
        GrantOrderResult.Line grant = grant(student, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(
                student, UUID.randomUUID().toString(), grant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();
        AttemptItem part2Sc = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getPartId().equals(parts.get(1).getId()) && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        String correctOpt = firstCorrectOptionId(part2Sc);

        assertThat(performFinishPart(
                        student, attempt.getId(), parts.get(0).getId(), sessionId,
                        new FinishPartRequest(List.of(
                                new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài viết đủ ý</p>", 4)))))
                        .getResponse().getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(
                        student, attempt.getId(), parts.get(1).getId(), sessionId,
                        new FinishPartRequest(List.of(
                                new FinalAnswerChange(part2Sc.getId(), 1L, new AnswerValue.SingleChoice(correctOpt)))))
                        .getResponse().getStatus())
                .isEqualTo(200);
        assertThat(performSubmit(
                        student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);

        outboxRelay.relayBatch(500);

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(res.getResponse().getContentAsString());

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.path("state").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(json.path("pendingReviewLabel").asText()).isEqualTo("Chờ chấm");
        assertThat(json.path("rawScore").isMissingNode() || json.path("rawScore").isNull()).isTrue();
        assertThat(json.path("displayScore").isMissingNode() || json.path("displayScore").isNull()).isTrue();
        assertThat(json.path("choices").toString()).contains("CA2");

        // F16: non-pending Part 2 shows its own part score while Essay Part 1 shows pendingReview=true & null score
        JsonNode p0 = json.path("parts").get(0);
        JsonNode p1 = json.path("parts").get(1);
        assertThat(p0.path("pendingReview").asBoolean()).isTrue();
        assertThat(p0.path("score").isMissingNode() || p0.path("score").isNull()).isTrue();
        assertThat(p1.path("pendingReview").asBoolean()).isFalse();
        assertThat(p1.path("score").decimalValue()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void regradingShowsOldScoreAndLabel() throws Exception {
        UUID student = createStudent("res-regrading");
        GrantOrderResult.Line grant = grant(student, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(
                student, UUID.randomUUID().toString(), grant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        performFinishPart(student, attempt.getId(), parts.get(0).getId(), sessionId,
                new FinishPartRequest(List.of(new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài viết</p>", 2)))));
        performFinishPart(student, attempt.getId(), parts.get(1).getId(), sessionId, new FinishPartRequest(List.of()));
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        jdbc.update(
                """
                        UPDATE attempt.attempt
                        SET state = 'PENDING_REVIEW',
                            regrading = true,
                            raw_score = 45.50,
                            display_score = 45.50,
                            graded_at = ?
                        WHERE id = ?
                        """,
                java.sql.Timestamp.from(Instant.now()),
                attempt.getId());

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.path("state").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(json.path("regrading").asBoolean()).isTrue();
        assertThat(json.path("regradingLabel").asText()).isEqualTo("Đang chấm lại");
        assertThat(json.path("displayScore").decimalValue()).isEqualByComparingTo("45.50");
        assertThat(json.path("rawScore").decimalValue()).isEqualByComparingTo("45.50");
    }

    @Test
    void gradingErrorShowsStatusNoZero() throws Exception {
        UUID student = createStudent("res-err");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));

        jdbc.update(
                """
                        UPDATE attempt.attempt
                        SET state = 'GRADING_ERROR',
                            grading_error = 'Simulated failure: sensitive internal detail',
                            raw_score = NULL,
                            display_score = NULL
                        WHERE id = ?
                        """,
                attempt.getId());

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        String body = res.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(body);
        assertThat(body).doesNotContain("Simulated failure");
        assertThat(body).doesNotContain("sensitive internal detail");

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("state").asText()).isEqualTo("GRADING_ERROR");
        assertThat(json.has("gradingError")).isFalse();
        assertThat(json.path("statusMessage").asText())
                .isEqualTo("Bài làm đã được ghi nhận, kết quả đang được xử lý");
        assertThat(json.path("rawScore").isMissingNode() || json.path("rawScore").isNull()).isTrue();
        assertThat(json.path("displayScore").isMissingNode() || json.path("displayScore").isNull()).isTrue();
        assertThat(json.path("reviewAvailable").asBoolean()).isFalse();
    }

    @Test
    void vactResultShowsPracticeLabelFourComponentsAndSubsections() throws Exception {
        UUID student = createStudent("res-vact");
        GrantOrderResult.Line grant = grant(student, "DS-VACT-FULL", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);

        // Finish Parts 0 & 1 sequentially to open Part 2 (SCIENTIFIC_THINKING)
        assertThat(performFinishPart(student, attempt.getId(), parts.get(0).getId(), sessionId, new FinishPartRequest(List.of()))
                        .getResponse().getStatus()).isEqualTo(200);
        assertThat(performFinishPart(student, attempt.getId(), parts.get(1).getId(), sessionId, new FinishPartRequest(List.of()))
                        .getResponse().getStatus()).isEqualTo(200);

        List<AttemptItem> part2Items = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(2).getId());
        Map<String, List<AttemptItem>> bySection = new LinkedHashMap<>();
        for (AttemptItem item : part2Items) {
            if (item.isInScope() && item.getQuestionType() == QuestionType.SINGLE_CHOICE) {
                bySection.computeIfAbsent(item.getSectionId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        assertThat(bySection).hasSize(2);

        // Answer 2 questions in the 12-question section and 3 questions in the 18-question section correctly
        List<FinalAnswerChange> finals = new ArrayList<>();
        for (List<AttemptItem> secItems : bySection.values()) {
            int countToAnswer = secItems.size() == 12 ? 2 : 3;
            for (int i = 0; i < countToAnswer; i++) {
                AttemptItem item = secItems.get(i);
                finals.add(new FinalAnswerChange(
                        item.getId(), 1L, new AnswerValue.SingleChoice(firstCorrectOptionId(item))));
            }
        }
        assertThat(performSubmit(
                        student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(finals))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(res.getResponse().getContentAsString());

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.path("state").asText()).isEqualTo("SCORED");
        assertThat(json.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(json.path("scoreLabel").asText()).isEqualTo("Điểm luyện tập");
        assertThat(json.path("practiceScoreNote").asText()).isEqualTo("Không phải điểm V-ACT chính thức");
        assertThat(json.path("scaleMax").decimalValue()).isEqualByComparingTo("1200");
        assertThat(json.path("displayScore").decimalValue()).isEqualByComparingTo("50.00");
        // D1 (BS §3 / LT F15): subjects[] must NEVER expose numeric per-component scores on V-ACT
        assertThat(json.path("subjects")).isEmpty();

        JsonNode components = json.path("vactComponents");
        assertThat(components.isArray()).isTrue();
        assertThat(components).hasSize(4);

        JsonNode sci = null;
        for (JsonNode comp : components) {
            // Component score must NEVER be exposed (F15)
            assertThat(comp.has("score")).isFalse();
            assertThat(comp.has("maxScore")).isFalse();
            assertThat(comp.path("correctRate").isNumber()).isTrue();
            if ("SCIENTIFIC_THINKING".equals(comp.path("componentCode").asText())) {
                sci = comp;
            }
        }
        assertThat(sci).isNotNull();
        assertThat(sci.path("correctCount").asInt()).isEqualTo(5);
        assertThat(sci.path("correctRate").decimalValue()).isEqualByComparingTo("16.67");
        assertThat(sci.path("subsections")).hasSize(2);
        assertThat(sci.path("subsections").get(0).path("questionCount").asInt()).isEqualTo(12);
        assertThat(sci.path("subsections").get(0).path("label").asText()).isEqualTo("Logic và phân tích số liệu");
        assertThat(sci.path("subsections").get(0).path("correctCount").asInt()).isEqualTo(2);
        assertThat(sci.path("subsections").get(0).path("correctRate").decimalValue()).isEqualByComparingTo("16.67");
        assertThat(sci.path("subsections").get(1).path("questionCount").asInt()).isEqualTo(18);
        assertThat(sci.path("subsections").get(1).path("label").asText()).isEqualTo("Suy luận khoa học");
        assertThat(sci.path("subsections").get(1).path("correctCount").asInt()).isEqualTo(3);
        assertThat(sci.path("subsections").get(1).path("correctRate").decimalValue()).isEqualByComparingTo("16.67");
    }

    @Test
    void vactPartialScopesShowPathScale() throws Exception {
        // AC-14 + AC-36 (#noSubsectionsWhenNotInPaper) + PublishedExamPort regression:
        // DS-VACT-PART-LANGUAGE (scale 600) and DS-VACT-SUBJECT-TOAN (scale 300)
        UUID student = createStudent("res-vact-partial");
        GrantOrderResult.Line langGrant = grant(student, "DS-VACT-PART-LANGUAGE", 2, List.of());
        Attempt langAttempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), langGrant.entitlementId(), null);
        UUID langSession = claimSession(student, langAttempt.getId());

        List<AttemptItem> langItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(langAttempt.getId())
                .stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .toList();
        List<FinalAnswerChange> langFinals = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            AttemptItem item = langItems.get(i);
            langFinals.add(new FinalAnswerChange(item.getId(), 1L, new AnswerValue.SingleChoice(firstCorrectOptionId(item))));
        }
        assertThat(performSubmit(student, langAttempt.getId(), UUID.randomUUID().toString(), langSession, new SubmitAttemptRequest(langFinals))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);

        MvcResult langRes = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", langAttempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(langRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode langJson = objectMapper.readTree(langRes.getResponse().getContentAsString());
        assertThat(langJson.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(langJson.path("scaleMax").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(langJson.path("displayScore").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(langJson.path("practiceScoreNote").asText()).isEqualTo("Không phải điểm V-ACT chính thức");
        assertThat(langJson.path("subjects")).isEmpty();
        assertThat(langJson.path("vactComponents")).hasSize(2);
        for (JsonNode comp : langJson.path("vactComponents")) {
            assertThat(comp.has("subsections")).isFalse();
        }

        // DS-VACT-SUBJECT-TOAN (scale 300)
        GrantOrderResult.Line toanGrant = grant(student, "DS-VACT-SUBJECT-TOAN", 2, List.of());
        Attempt toanAttempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), toanGrant.entitlementId(), null);
        UUID toanSession = claimSession(student, toanAttempt.getId());

        List<AttemptItem> toanItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(toanAttempt.getId())
                .stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .toList();
        List<FinalAnswerChange> toanFinals = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AttemptItem item = toanItems.get(i);
            toanFinals.add(new FinalAnswerChange(item.getId(), 1L, new AnswerValue.SingleChoice(firstCorrectOptionId(item))));
        }
        assertThat(performSubmit(student, toanAttempt.getId(), UUID.randomUUID().toString(), toanSession, new SubmitAttemptRequest(toanFinals))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);

        MvcResult toanRes = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", toanAttempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(toanRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode toanJson = objectMapper.readTree(toanRes.getResponse().getContentAsString());
        assertThat(toanJson.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(toanJson.path("scaleMax").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(toanJson.path("displayScore").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(toanJson.path("subjects")).isEmpty();
        assertThat(toanJson.path("vactComponents")).hasSize(1);
        JsonNode toanComp = toanJson.path("vactComponents").get(0);
        assertThat(toanComp.path("componentCode").asText()).isEqualTo("TOAN");
        assertThat(toanComp.path("correctCount").asInt()).isEqualTo(3);
        assertThat(toanComp.path("correctRate").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(toanComp.has("subsections")).isFalse();
    }

    @Test
    void unchosenSubjectsExcluded() throws Exception {
        // AC-25: HSA FULL Khoa học choosing 3 of 5 subjects + explicit in_scope=false item exclusion
        UUID student = createStudent("res-unchosen");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-FULL-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line grant = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "FULL_SOLUTION", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);

        // Finish Part 0 & Part 1 sequentially to unlock Part 2 AWAITING_CHOICE
        assertThat(performFinishPart(student, attempt.getId(), parts.get(0).getId(), sessionId, new FinishPartRequest(List.of()))
                        .getResponse().getStatus()).isEqualTo(200);
        assertThat(performFinishPart(student, attempt.getId(), parts.get(1).getId(), sessionId, new FinishPartRequest(List.of()))
                        .getResponse().getStatus()).isEqualTo(200);

        MvcResult choiceRes = performChoice(
                student,
                attempt.getId(),
                parts.get(2).getId(),
                UUID.randomUUID().toString(),
                sessionId,
                new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SINH")));
        assertThat(choiceRes.getResponse().getStatus()).isEqualTo(202);
        awaitPartState(parts.get(2).getId(), PartState.OPEN);

        // Also mark one Part 0 leaf item as in_scope=false to prove in_scope=false is excluded everywhere
        AttemptItem excludedItem = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId())
                .stream()
                .filter(i -> i.isInScope() && i.getQuestionType() != QuestionType.QUESTION_SET)
                .findFirst()
                .orElseThrow();
        jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE id = ?", excludedItem.getId());

        assertThat(performSubmit(
                        student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);

        List<AttemptItem> allItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
        BigDecimal expectedMax = BigDecimal.ZERO;
        int expectedTotalUnits = 0;
        for (AttemptItem item : allItems) {
            if (item.isInScope() && item.getQuestionType() != QuestionType.QUESTION_SET) {
                expectedMax = expectedMax.add(item.getPointsMax());
                expectedTotalUnits += item.getQuestionType() == QuestionType.CLOZE ? item.getUnitCount() : 1;
            }
        }

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.path("rawMaxScore").decimalValue()).isEqualByComparingTo(expectedMax);
        assertThat(json.path("statistics").path("totalCount").asInt()).isEqualTo(expectedTotalUnits);
        assertThat(json.path("choices").path(parts.get(2).getPartCode()).path("optionCode").asText())
                .isEqualTo("SCIENCE");
        List<String> chosenSubjects = new ArrayList<>();
        json.path("choices").path(parts.get(2).getPartCode()).path("subjectCodes")
                .forEach(s -> chosenSubjects.add(s.asText()));
        assertThat(chosenSubjects).containsExactlyInAnyOrder("LY", "HOA", "SINH");

        List<String> subjectCodes = new ArrayList<>();
        json.path("subjects").forEach(s -> subjectCodes.add(s.path("code").asText()));
        assertThat(subjectCodes).containsExactlyInAnyOrder("TOAN", "VAN", "LY", "HOA", "SINH");
        assertThat(subjectCodes).doesNotContain("SU", "DIA");

        // /review items[] must also exclude the in_scope=false item
        MvcResult reviewRes = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(reviewRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode reviewJson = objectMapper.readTree(reviewRes.getResponse().getContentAsString());
        for (JsonNode item : reviewJson.path("items")) {
            assertThat(item.path("itemId").asText()).isNotEqualTo(excludedItem.getId().toString());
        }
    }

    @Test
    void overdueInProgressGetReviewPersistsLazyCloseAndReturns409() throws Exception {
        // R1-15: calling GET /review on overdue IN_PROGRESS attempt must commit lazy close to GRADING
        // even though /review throws 409 REVIEW_NOT_AVAILABLE
        UUID student = createStudent("res-overdue-rev");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "FULL_SOLUTION", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);

        mutableClock.advance(Duration.ofHours(5));

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(res.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("REVIEW_NOT_AVAILABLE");

        Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void compensatedFaultNoticeVisible() throws Exception {
        UUID student = createStudent("res-fault");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        jdbc.update(
                "UPDATE attempt.attempt SET fault_flag = 'SYSTEM_FAULT', fault_reason = 'DB hiccup' WHERE id = ?",
                attempt.getId());

        MvcResult uncompensated = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        JsonNode jsonBefore = objectMapper.readTree(uncompensated.getResponse().getContentAsString());
        assertThat(jsonBefore.path("compensated").asBoolean()).isFalse();
        assertThat(jsonBefore.path("faultFlag").asText()).isEqualTo("SYSTEM_FAULT");
        assertThat(jsonBefore.path("faultMessage").asText()).contains("Lượt kết thúc do lỗi hệ thống");

        attemptEventRepository.saveAndFlush(new AttemptEvent(
                Ids.newId(),
                attempt.getId(),
                AttemptEventType.COMPENSATED,
                null,
                null,
                Instant.now(),
                java.util.Map.of("reason", "Manual grant compensation")));

        MvcResult compensated = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        JsonNode jsonAfter = objectMapper.readTree(compensated.getResponse().getContentAsString());
        assertThat(jsonAfter.path("compensated").asBoolean()).isTrue();
        assertThat(jsonAfter.path("faultMessage").asText())
                .contains("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt");
    }

    private String firstCorrectOptionId(AttemptItem item) {
        var snap = questionSourcePort
                .loadVersions(List.of(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo())))
                .getFirst()
                .snapshot();
        for (JsonNode opt : snap.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                return opt.path("id").asText();
            }
        }
        throw new IllegalStateException("No correct option found for " + item.getQuestionId());
    }
}
