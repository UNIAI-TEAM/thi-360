package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

class AttemptReviewIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;

    @BeforeEach
    void drainOutbox() {
        while (outboxRelay.relayBatch(500) > 0) {
            // drain backlog
        }
    }

    @Test
    void scoreViewForbidden() throws Exception {
        UUID student = createStudent("rev-score");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "SCORE", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        MvcResult review = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(review.getResponse().getStatus()).isEqualTo(403);
        String errorJson = review.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(errorJson);
        assertThat(objectMapper.readTree(errorJson).path("code").asText()).isEqualTo("REVIEW_NOT_INCLUDED");
    }

    @Test
    void rightWrongHidesSolution() throws Exception {
        UUID student = createStudent("rev-rw");
        UUID stranger = createStudent("rev-stranger");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "RIGHT_WRONG", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);

        // Before grading finished -> 409 REVIEW_NOT_AVAILABLE
        MvcResult earlyReview = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(earlyReview.getResponse().getStatus()).isEqualTo(409);

        UUID sessionId = claimSession(student, attempt.getId());
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        MvcResult review = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(review.getResponse().getStatus()).isEqualTo(200);
        String rawJson = review.getResponse().getContentAsString();
        // solution and internal grading rubric keys MUST NOT exist on RIGHT_WRONG
        ForbiddenKeysAssert.assertNoForbiddenKeys(
                objectMapper.readTree(rawJson),
                Set.of("solution", "gradingGuide", "rubric", "sampleAnswer", "commonMistakes"),
                "review-right-wrong");

        JsonNode json = objectMapper.readTree(rawJson);
        assertThat(json.path("resultView").asText()).isEqualTo("RIGHT_WRONG");
        assertThat(json.path("items")).isNotEmpty();
        assertThat(json.path("items").get(0).path("expectedAnswer").isNull()).isFalse();

        // Stranger gets 404
        assertThat(mockMvc.perform(
                                get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                        .with(AttemptTestFixtures.asExamUser(stranger, "stranger")))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void fullSolutionIncludesSolutionRewritesUrlsAndHidesRubric() throws Exception {
        UUID student = createStudent("rev-full");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "FULL_SOLUTION", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        MvcResult review = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(review.getResponse().getStatus()).isEqualTo(200);
        String rawJson = review.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoForbiddenKeys(
                objectMapper.readTree(rawJson),
                Set.of("gradingGuide", "rubric", "sampleAnswer", "commonMistakes"),
                "review-full-solution");
        JsonNode json = objectMapper.readTree(rawJson);
        assertThat(json.path("resultView").asText()).isEqualTo("FULL_SOLUTION");
        assertThat(json.path("items")).isNotEmpty();
    }

    @Test
    void pendingReviewFiltersOutPendingEssaysAndPartFilterWorks() throws Exception {
        UUID student = createStudent("rev-pending-part");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-BCA-FULL-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "FULL_SOLUTION", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(
                student, UUID.randomUUID().toString(), line.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        performFinishPart(student, attempt.getId(), parts.get(0).getId(), sessionId,
                new FinishPartRequest(List.of(new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài viết đang chấm</p>", 4)))));
        performFinishPart(student, attempt.getId(), parts.get(1).getId(), sessionId, new FinishPartRequest(List.of()));
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        // During PENDING_REVIEW, review returns ONLY non-pending items (PENDING_REVIEW essay excluded)
        MvcResult pendingReview = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(pendingReview.getResponse().getStatus()).isEqualTo(200);
        JsonNode pendingJson = objectMapper.readTree(pendingReview.getResponse().getContentAsString());
        assertThat(pendingJson.path("state").asText()).isEqualTo("PENDING_REVIEW");
        for (JsonNode item : pendingJson.path("items")) {
            assertThat(item.path("itemId").asText()).isNotEqualTo(essayItem.getId().toString());
            assertThat(item.path("outcome").asText()).isNotEqualTo("PENDING_REVIEW");
        }

        // ?part= filters to selected part, and invalid part yields 404
        String part1Code = parts.get(1).getPartCode();
        MvcResult partFiltered = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .param("part", part1Code)
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(partFiltered.getResponse().getStatus()).isEqualTo(200);
        JsonNode filteredJson = objectMapper.readTree(partFiltered.getResponse().getContentAsString());
        assertThat(filteredJson.path("selectedPartCode").asText()).isEqualTo(part1Code);
        assertThat(filteredJson.path("parts")).hasSize(1);
        for (JsonNode item : filteredJson.path("items")) {
            assertThat(item.path("partCode").asText()).isEqualTo(part1Code);
        }

        assertThat(mockMvc.perform(
                                get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                        .param("part", "NON_EXISTENT_PART")
                                        .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(404);

        // Once essay finalized & SCORED, FULL_SOLUTION review includes essayFeedback
        jdbc.update(
                """
                        UPDATE attempt.attempt_item
                        SET earned_points = ?,
                            outcome = 'PARTIAL',
                            grading_detail = '{"exactFraction":"15","comment":"Lập luận tốt","criteria":[{"id":"c1","points":15,"comment":"Rõ ràng"}]}'::jsonb
                        WHERE id = ?
                        """,
                new BigDecimal("15.00"),
                essayItem.getId());
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'SCORED', graded_at = now() WHERE id = ?",
                attempt.getId());

        MvcResult scoredReview = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(scoredReview.getResponse().getStatus()).isEqualTo(200);
        JsonNode scoredJson = objectMapper.readTree(scoredReview.getResponse().getContentAsString());
        JsonNode essayNode = null;
        for (JsonNode item : scoredJson.path("items")) {
            if (item.path("itemId").asText().equals(essayItem.getId().toString())) {
                essayNode = item;
                break;
            }
        }
        assertThat(essayNode).isNotNull();
        assertThat(essayNode.path("essayFeedback").path("points").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(essayNode.path("essayFeedback").path("comment").asText()).isEqualTo("Lập luận tốt");
        assertThat(essayNode.path("essayFeedback").path("criteria")).hasSize(1);
    }
}
