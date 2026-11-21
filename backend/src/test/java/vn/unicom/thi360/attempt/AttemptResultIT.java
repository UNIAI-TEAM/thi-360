package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
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
import vn.unicom.thi360.attempt.domain.EndReason;
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
        var part2Snap = questionSourcePort
                .loadVersions(List.of(new VersionKey(part2Sc.getQuestionId(), part2Sc.getQuestionVersionNo())))
                .getFirst()
                .snapshot();
        String correctOpt = null;
        for (JsonNode opt : part2Snap.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                correctOpt = opt.path("id").asText();
                break;
            }
        }
        assertThat(correctOpt).isNotBlank();

        // Finish Part 0 with written essay
        assertThat(performFinishPart(
                        student, attempt.getId(), parts.get(0).getId(), sessionId,
                        new FinishPartRequest(List.of(
                                new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài viết đủ ý</p>", 4)))))
                        .getResponse().getStatus())
                .isEqualTo(200);
        // Finish Part 1 answering one SC correctly
        assertThat(performFinishPart(
                        student, attempt.getId(), parts.get(1).getId(), sessionId,
                        new FinishPartRequest(List.of(
                                new FinalAnswerChange(part2Sc.getId(), 1L, new AnswerValue.SingleChoice(correctOpt)))))
                        .getResponse().getStatus())
                .isEqualTo(200);
        // Submit Part 2
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

        // Simulate reopened essay grading keeping old scores with regrading=true
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
                            grading_error = 'Simulated failure',
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
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(res.getResponse().getContentAsString());
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.path("state").asText()).isEqualTo("GRADING_ERROR");
        assertThat(json.path("statusMessage").asText()).contains("Hệ thống đang xử lý kết quả bài làm của bạn");
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

        // Close cleanly via shared close procedure then grade via outbox
        attemptLifecycleService.closeAttempt(
                attempt.getId(), EndReason.SUBMITTED, mutableClock.instant().plusSeconds(1), sessionId, List.of());
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
        assertThat(json.path("scaleMax").decimalValue()).isEqualByComparingTo("1200");

        JsonNode components = json.path("vactComponents");
        assertThat(components.isArray()).isTrue();
        assertThat(components).hasSize(4);

        JsonNode sci = null;
        for (JsonNode comp : components) {
            // Component score must NEVER be exposed (F15)
            assertThat(comp.has("score")).isFalse();
            if ("SCIENTIFIC_THINKING".equals(comp.path("componentCode").asText())) {
                sci = comp;
            }
        }
        assertThat(sci).isNotNull();
        assertThat(sci.path("subsections")).hasSize(2);
        assertThat(sci.path("subsections").get(0).path("questionCount").asInt()).isEqualTo(12);
        assertThat(sci.path("subsections").get(0).path("label").asText()).isEqualTo("Logic và phân tích số liệu");
        assertThat(sci.path("subsections").get(1).path("questionCount").asInt()).isEqualTo(18);
        assertThat(sci.path("subsections").get(1).path("label").asText()).isEqualTo("Suy luận khoa học");
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

        // Before compensation
        MvcResult uncompensated = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        JsonNode jsonBefore = objectMapper.readTree(uncompensated.getResponse().getContentAsString());
        assertThat(jsonBefore.path("compensated").asBoolean()).isFalse();
        assertThat(jsonBefore.path("faultFlag").asText()).isEqualTo("SYSTEM_FAULT");
        assertThat(jsonBefore.path("faultMessage").asText()).contains("Lượt kết thúc do lỗi hệ thống");

        // After compensation event recorded (F20 / AT-5)
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
}
