package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.ExamListingFixture;
import vn.unicom.thi360.exam.spi.PreviewPaperPort;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for admin preview trial endpoints: {@code POST .../trial},
 * {@code GET .../paper}, and {@code POST .../grade} (AC-17..AC-21, AC-23..AC-24, BS §6/§9, F17/F20).
 */
class PreviewGradingIT extends AbstractAttemptDsIT {

    private static final String REDIS_KEY_PREFIX = "exam:preview:";
    private static final long SEED = 20261121L;

    @Autowired private StringRedisTemplate redis;
    @Autowired private PreviewPaperPort previewPaperPort;
    @Autowired private OutboxRelay outboxRelay;

    @Test
    void gradesPerQuestionAndStatement() throws Exception {
        Subject subject = createSubject();
        UUID qSingle = insertSingleChoice(subject, false);
        UUID qMultiple = insertMultipleChoicePartial(subject);
        UUID qTf = insertTrueFalsePerStatement(subject);
        UUID qCloze = insertClozeTwoBlanks(subject);
        UUID qShort = insertShortAnswer(subject);
        UUID qMatching = insertMatchingNoReuse(subject);
        SetIds set = insertQuestionSetTwoSingles(subject);

        UUID examId = insertCustomExam("BCA", "SHARED", 60, """
                [{"id":"p1","order":1,"name":"Part 1","questionCount":9,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":9,
                    "blocks":[
                      {"id":"b-sc","name":"SC","questionType":"SINGLE_CHOICE","questionCount":1},
                      {"id":"b-mc","name":"MC","questionType":"MULTIPLE_CHOICE","questionCount":1},
                      {"id":"b-tf","name":"TF","questionType":"TRUE_FALSE","questionCount":1},
                      {"id":"b-cl","name":"CL","questionType":"CLOZE","questionCount":2,"setCount":1,"setSize":2},
                      {"id":"b-sa","name":"SA","questionType":"SHORT_ANSWER","questionCount":1},
                      {"id":"b-ma","name":"MA","questionType":"MATCHING","questionCount":1},
                      {"id":"b-set","name":"SET","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2}
                    ]}]}]
                """.formatted(subject.code()), 9);

        UUID previewId = generatePreview(examId);

        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put(qSingle.toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a")); // +1.00 CORRECT
        answers.put(qMultiple.toString(), Map.of("type", "MULTIPLE_CHOICE", "optionIds", List.of("o1"))); // +0.50 PARTIAL (1/2 of 1.00)
        // 2 of 4 statements match -> 1.00 of 2.00 PARTIAL
        answers.put(
                qTf.toString(),
                Map.of("type", "TRUE_FALSE", "statements", Map.of("s1", true, "s2", false, "s3", false)));
        // b1 correct, b2 wrong -> 0.50 of 1.00 PARTIAL (2 blank units: 1 CORRECT, 1 WRONG)
        answers.put(qCloze.toString(), Map.of("type", "CLOZE", "blanks", Map.of("b1", "alpha", "b2", "nope")));
        answers.put(qShort.toString(), Map.of("type", "SHORT_ANSWER", "text", " Hà   Nội ")); // +1.00 CORRECT
        answers.put(qMatching.toString(), Map.of("type", "MATCHING", "pairs", Map.of("l1", "r1"))); // +0.50 PARTIAL (1/2)
        answers.put(set.child1().toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a")); // +1.00 CORRECT
        // child2 omitted -> 0.00 BLANK

        JsonNode res = gradeOk(examId, previewId, answers);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(res);

        assertThat(res.path("examId").asText()).isEqualTo(examId.toString());
        assertThat(res.path("previewId").asText()).isEqualTo(previewId.toString());
        assertThat(res.path("kindCode").asText()).isEqualTo("BCA");
        assertThat(res.path("hasPendingManualGrading").asBoolean()).isFalse();
        // Earned: 1.0 + 0.5 + 1.0 + 0.5 + 1.0 + 0.5 + 1.0 + 0.0 = 5.50 / 9.00
        assertThat(res.path("rawScore").decimalValue()).isEqualByComparingTo("5.50");
        assertThat(res.path("maxScore").decimalValue()).isEqualByComparingTo("9.00");

        JsonNode stats = res.path("statistics");
        // Units: SC(C), MC(P), TF(P), CLOZE(b1=C, b2=W), SA(C), MA(P), child1(C), child2(B)
        assertThat(stats.path("correctCount").asInt()).isEqualTo(4);
        assertThat(stats.path("partialCount").asInt()).isEqualTo(3);
        assertThat(stats.path("wrongCount").asInt()).isEqualTo(1);
        assertThat(stats.path("blankCount").asInt()).isEqualTo(1);
        assertThat(stats.path("pendingReviewCount").asInt()).isEqualTo(0);
        assertThat(stats.path("totalCount").asInt()).isEqualTo(9);

        JsonNode items = res.path("items");
        assertThat(items).hasSize(8);

        JsonNode tfItem = findGradedItem(items, qTf);
        assertThat(tfItem.path("outcome").asText()).isEqualTo("PARTIAL");
        assertThat(tfItem.path("earnedPoints").decimalValue()).isEqualByComparingTo("1.00");
        JsonNode stmtResults = tfItem.path("gradingDetail").path("statementResults");
        assertThat(stmtResults).hasSize(4);
        assertThat(stmtResults.get(0).path("matched").asBoolean()).isTrue();
        assertThat(stmtResults.get(1).path("matched").asBoolean()).isTrue();
        assertThat(stmtResults.get(2).path("matched").asBoolean()).isFalse();
        assertThat(stmtResults.get(3).path("answered").asBoolean()).isFalse();
        assertThat(stmtResults.get(3).path("matched").asBoolean()).isFalse();

        JsonNode clozeItem = findGradedItem(items, qCloze);
        assertThat(clozeItem.path("gradingDetail").path("blankResults")).hasSize(2);
        assertThat(clozeItem.path("gradingDetail").path("blankResults").get(0).path("outcome").asText())
                .isEqualTo("CORRECT");
        assertThat(clozeItem.path("gradingDetail").path("blankResults").get(1).path("outcome").asText())
                .isEqualTo("WRONG");

        // Verify paper preserves exact numbers 1..8 across standalone + set children
        JsonNode child2Item = findGradedItem(items, set.child2());
        assertThat(child2Item.path("outcome").asText()).isEqualTo("BLANK");
        assertThat(child2Item.path("earnedPoints").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void essayHasNoAutomaticScore() throws Exception {
        Subject subject = createSubject();
        UUID qSingle = insertSingleChoice(subject, false);
        UUID qEssayWritten = insertEssay(subject, 50, new BigDecimal("5.00"));
        UUID qEssayBlank = insertEssay(subject, 50, new BigDecimal("5.00"));

        UUID examId = insertCustomExam("SP2E", "SHARED", 60, """
                [{"id":"p1","order":1,"name":"Part 1","questionCount":3,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":3,
                    "blocks":[
                      {"id":"b-sc","name":"SC","questionType":"SINGLE_CHOICE","questionCount":1},
                      {"id":"b-es","name":"ES","questionType":"ESSAY","manualGrading":true,"questionCount":2}
                    ]}]}]
                """.formatted(subject.code()), 3);

        UUID previewId = generatePreview(examId);

        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put(qSingle.toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a"));
        answers.put(qEssayWritten.toString(), Map.of("type", "ESSAY", "html", "<p>Một hai ba bốn năm sáu</p>"));
        answers.put(qEssayBlank.toString(), Map.of("type", "ESSAY", "html", "   "));

        JsonNode res = gradeOk(examId, previewId, answers);
        assertThat(res.path("hasPendingManualGrading").asBoolean()).isTrue();
        assertThat(res.get("rawScore").isNull()).isTrue();
        assertThat(res.path("maxScore").decimalValue()).isEqualByComparingTo("11.00");
        assertThat(res.path("statistics").path("correctCount").asInt()).isEqualTo(1);
        assertThat(res.path("statistics").path("pendingReviewCount").asInt()).isEqualTo(1);
        assertThat(res.path("statistics").path("blankCount").asInt()).isEqualTo(1);

        JsonNode singleItem = findGradedItem(res.path("items"), qSingle);
        assertThat(singleItem.path("outcome").asText()).isEqualTo("CORRECT");
        assertThat(singleItem.path("earnedPoints").decimalValue()).isEqualByComparingTo("1.00");

        JsonNode writtenItem = findGradedItem(res.path("items"), qEssayWritten);
        assertThat(writtenItem.path("outcome").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(writtenItem.path("pendingReview").asBoolean()).isTrue();
        assertThat(writtenItem.get("earnedPoints").isNull()).isTrue();
        assertThat(writtenItem.path("gradingDetail").path("wordCount").asInt()).isEqualTo(6);

        JsonNode blankEssayItem = findGradedItem(res.path("items"), qEssayBlank);
        assertThat(blankEssayItem.path("outcome").asText()).isEqualTo("BLANK");
        assertThat(blankEssayItem.path("pendingReview").asBoolean()).isFalse();
        assertThat(blankEssayItem.path("earnedPoints").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void createsNoLearningRecords() throws Exception {
        Subject subject = createSubject();
        UUID qSingle = insertSingleChoice(subject, false);
        UUID examId = insertSingleQuestionExam("BCA", "SHARED", 60, subject);
        UUID previewId = generatePreview(examId);

        int attemptsBefore = countRows("attempt.attempt");
        int partsBefore = countRows("attempt.attempt_part");
        int itemsBefore = countRows("attempt.attempt_item");
        int eventsBefore = countRows("attempt.attempt_event");
        int essayGradesBefore = countRows("attempt.essay_grade");
        int ledgerBefore = countRows("questionbank.question_usage_ledger");
        int usageBefore = countRows("commerce.entitlement_usage");

        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
        gradeOk(examId, previewId, Map.of(qSingle.toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a")));

        assertThat(countRows("attempt.attempt")).isEqualTo(attemptsBefore);
        assertThat(countRows("attempt.attempt_part")).isEqualTo(partsBefore);
        assertThat(countRows("attempt.attempt_item")).isEqualTo(itemsBefore);
        assertThat(countRows("attempt.attempt_event")).isEqualTo(eventsBefore);
        assertThat(countRows("attempt.essay_grade")).isEqualTo(essayGradesBefore);
        assertThat(countRows("questionbank.question_usage_ledger")).isEqualTo(ledgerBefore);
        assertThat(countRows("commerce.entitlement_usage")).isEqualTo(usageBefore);
    }

    @Test
    void expiredPreviewIs410() throws Exception {
        Subject subject = createSubject();
        insertSingleChoice(subject, false);
        UUID examId = insertSingleQuestionExam("BCA", "SHARED", 60, subject);
        UUID previewId = generatePreview(examId);

        redis.delete(REDIS_KEY_PREFIX + previewId);

        JsonNode paperErr = json(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isGone())
                .andReturn());
        assertThat(paperErr.path("code").asText()).isEqualTo("PREVIEW_EXPIRED");

        JsonNode gradeErr = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":{}}"))
                .andExpect(status().isGone())
                .andReturn());
        assertThat(gradeErr.path("code").asText()).isEqualTo("PREVIEW_EXPIRED");

        UUID missing = UUID.randomUUID();
        JsonNode missingErr = json(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, missing)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isGone())
                .andReturn());
        assertThat(missingErr.path("code").asText()).isEqualTo("PREVIEW_EXPIRED");
    }

    @Test
    void otherExamPreviewIs410() throws Exception {
        Subject subject = createSubject();
        insertSingleChoice(subject, false);
        UUID examA = insertSingleQuestionExam("BCA", "SHARED", 60, subject);
        UUID examB = insertSingleQuestionExam("BCA", "SHARED", 60, subject);
        UUID previewA = generatePreview(examA);

        JsonNode paperErr = json(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examB, previewA)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isGone())
                .andReturn());
        assertThat(paperErr.path("code").asText()).isEqualTo("PREVIEW_EXPIRED");

        JsonNode gradeErr = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examB, previewA)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":{}}"))
                .andExpect(status().isGone())
                .andReturn());
        assertThat(gradeErr.path("code").asText()).isEqualTo("PREVIEW_EXPIRED");

        JsonNode trialErr = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examB, previewA)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isGone())
                .andReturn());
        assertThat(trialErr.path("code").asText()).isEqualTo("PREVIEW_EXPIRED");

        mockMvc.perform(get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examA, previewA)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidAnswerIs422() throws Exception {
        Subject subject = createSubject();
        UUID qSingle = insertSingleChoice(subject, false);
        UUID qEssay = insertEssay(subject, 5, BigDecimal.ONE);
        SetIds set = insertQuestionSetTwoSingles(subject);
        UUID examId = insertCustomExam("SP2E", "SHARED", 60, """
                [{"id":"p1","order":1,"name":"Part 1","questionCount":4,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":4,
                    "blocks":[
                      {"id":"b1","name":"SC","questionType":"SINGLE_CHOICE","questionCount":1},
                      {"id":"b2","name":"ES","questionType":"ESSAY","manualGrading":true,"questionCount":1},
                      {"id":"b3","name":"SET","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2}
                    ]}]}]
                """.formatted(subject.code()), 4);
        UUID previewId = generatePreview(examId);

        // 0. Malformed JSON syntax -> 400 VALIDATION_FAILED (R1-3)
        JsonNode malformedJson = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":{"))
                .andExpect(status().isBadRequest())
                .andReturn());
        assertThat(malformedJson.path("code").asText()).isEqualTo("VALIDATION_FAILED");

        // 1. Unknown AnswerValue discriminator -> HttpMessageNotReadableException -> 422 INVALID_ANSWER_FORMAT (vi + en)
        JsonNode badType = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":{\"" + qSingle + "\":{\"type\":\"NOT_A_TYPE\"}}}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn());
        assertThat(badType.path("code").asText()).isEqualTo("INVALID_ANSWER_FORMAT");
        assertThat(badType.path("message").asText()).isEqualTo("Định dạng câu trả lời không hợp lệ");

        JsonNode badTypeEn = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":{\"" + qSingle + "\":{\"type\":\"NOT_A_TYPE\"}}}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn());
        assertThat(badTypeEn.path("code").asText()).isEqualTo("INVALID_ANSWER_FORMAT");
        assertThat(badTypeEn.path("message").asText()).isEqualTo("Submitted answer format is invalid");

        // 2. Non-existent option ID -> AnswerValueValidator -> 422 INVALID_ANSWER_FORMAT (vi + en, R1-8/9)
        JsonNode badOption = gradeExpectStatus(
                examId, previewId, Map.of(qSingle.toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "non-existent")), 422);
        assertThat(badOption.path("code").asText()).isEqualTo("INVALID_ANSWER_FORMAT");
        assertThat(badOption.path("message").asText()).isEqualTo("Định dạng câu trả lời không hợp lệ");

        JsonNode badOptionEn = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "answers",
                                        Map.of(qSingle.toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "non-existent"))))))
                .andExpect(status().isUnprocessableEntity())
                .andReturn());
        assertThat(badOptionEn.path("code").asText()).isEqualTo("INVALID_ANSWER_FORMAT");
        assertThat(badOptionEn.path("message").asText()).isEqualTo("Submitted answer format is invalid");

        // 3. Wrong AnswerValue subtype for question type -> 422 INVALID_ANSWER_FORMAT
        JsonNode mismatchType = gradeExpectStatus(
                examId, previewId, Map.of(qSingle.toString(), Map.of("type", "SHORT_ANSWER", "text", "a")), 422);
        assertThat(mismatchType.path("code").asText()).isEqualTo("INVALID_ANSWER_FORMAT");

        // 4. Wrong AnswerValue subtype on ESSAY -> 422 INVALID_ANSWER_FORMAT
        JsonNode wrongEssayType = gradeExpectStatus(
                examId,
                previewId,
                Map.of(qEssay.toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a")),
                422);
        assertThat(wrongEssayType.path("code").asText()).isEqualTo("INVALID_ANSWER_FORMAT");

        // 5. Unknown questionId / parent QUESTION_SET root ID -> 422 PREVIEW_UNKNOWN_QUESTION (vi + en, R1-8/9)
        JsonNode unknownQ = gradeExpectStatus(
                examId,
                previewId,
                Map.of(UUID.randomUUID().toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a")),
                422);
        assertThat(unknownQ.path("code").asText()).isEqualTo("PREVIEW_UNKNOWN_QUESTION");
        assertThat(unknownQ.path("message").asText()).isEqualTo("Câu trả lời chứa mã câu hỏi không thuộc đề làm thử");

        JsonNode unknownQEn = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "answers",
                                        Map.of(UUID.randomUUID().toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a"))))))
                .andExpect(status().isUnprocessableEntity())
                .andReturn());
        assertThat(unknownQEn.path("code").asText()).isEqualTo("PREVIEW_UNKNOWN_QUESTION");
        assertThat(unknownQEn.path("message").asText())
                .isEqualTo("Submitted answer references a question that does not belong to this preview paper");

        JsonNode setRootRejected = gradeExpectStatus(
                examId,
                previewId,
                Map.of(set.parent().toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a")),
                422);
        assertThat(setRootRejected.path("code").asText()).isEqualTo("PREVIEW_UNKNOWN_QUESTION");
    }

    @Test
    void examViewAloneIsEnough() throws Exception {
        Subject subject = createSubject();
        insertSingleChoice(subject, false);
        UUID examId = insertSingleQuestionExam("BCA", "SHARED", 60, subject);
        UUID previewId = generatePreview(examId);

        // Only EXAM.VIEW succeeds across all three trial endpoints
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isOk());

        // Other permissions, student role, and anonymous are rejected
        for (String wrongPerm : List.of(Permissions.ATTEMPT_VIEW, Permissions.GRADING_VIEW)) {
            mockMvc.perform(get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, previewId)
                            .with(asUser(wrongPerm)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                            .with(asUser(wrongPerm))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answers\":{}}"))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, previewId)
                        .with(asStudent()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                        .with(asStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, previewId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paperKeepsOrderAndHasNoAnswerKeys() throws Exception {
        Subject subject = createSubject();
        UUID qSingle = insertSingleChoice(subject, true);
        UUID contentAssetId = UUID.randomUUID();
        UUID optionAssetId = UUID.randomUUID();
        String patchedScSnapshot = """
                {
                  "content": "<p>Single choice <img data-asset-id=\\"%s\\" alt=\\"prompt\\"></p>",
                  "points": 1.0,
                  "body": {
                    "type": "SINGLE_CHOICE",
                    "shuffleOptions": true,
                    "options": [
                      {"id": "a", "content": "<p>Opt A <img data-asset-id=\\"%s\\" alt=\\"opt\\"></p>", "correct": true},
                      {"id": "b", "content": "B", "correct": false},
                      {"id": "c", "content": "C", "correct": false},
                      {"id": "d", "content": "D", "correct": false},
                      {"id": "e", "content": "E", "correct": false},
                      {"id": "f", "content": "F", "correct": false}
                    ],
                    "solution": "Option A is right"
                  }
                }
                """.formatted(contentAssetId, optionAssetId);
        jdbc.update(
                "UPDATE questionbank.question_version SET snapshot = cast(? as jsonb) WHERE question_id = ? AND version_no = 1",
                patchedScSnapshot,
                qSingle);

        insertTrueFalsePerStatement(subject);
        insertClozeTwoBlanks(subject);
        insertMatchingNoReuse(subject);
        insertEssay(subject, 80, new BigDecimal("4.00"));
        insertQuestionSetTwoSingles(subject);

        UUID examId = insertCustomExam("BCA", "SHARED", 60, """
                [{"id":"p1","order":1,"name":"Part 1","questionCount":8,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":8,
                    "blocks":[
                      {"id":"b1","name":"SC","questionType":"SINGLE_CHOICE","questionCount":1},
                      {"id":"b2","name":"TF","questionType":"TRUE_FALSE","questionCount":1},
                      {"id":"b3","name":"CL","questionType":"CLOZE","questionCount":2,"setCount":1,"setSize":2},
                      {"id":"b4","name":"MA","questionType":"MATCHING","questionCount":1},
                      {"id":"b5","name":"ES","questionType":"ESSAY","manualGrading":true,"questionCount":1},
                      {"id":"b6","name":"SET","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2}
                    ]}]}]
                """.formatted(subject.code()), 8);

        UUID previewId = generatePreview(examId);
        PreviewPaperPort.PreviewPaper rawPaper = previewPaperPort.load(examId, previewId);

        MvcResult result = mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn();
        String rawJson = result.getResponse().getContentAsString();
        JsonNode paperJson = objectMapper.readTree(rawJson);

        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(paperJson);
        assertThat(paperJson.path("kindCode").asText()).isEqualTo("BCA");
        assertThat(paperJson.path("durationMinutes").asInt()).isEqualTo(60);

        List<String> expectedOutline = extractPortQuestionOutline(rawPaper);
        List<String> actualOutline = extractResponseQuestionOutline(paperJson);
        assertThat(actualOutline).containsExactlyElementsOf(expectedOutline);

        // Verify shuffled optionOrder is applied verbatim to body.options in SINGLE_CHOICE and asset src is populated (R1-30)
        JsonNode scNode = paperJson.path("parts").get(0).path("blocks").get(0).path("items").get(0).path("question");
        assertThat(scNode.path("content").asText())
                .contains("src=\"/api/v1/admin/question-assets/" + contentAssetId + "\"");
        List<String> displayedOptionIds = new ArrayList<>();
        String optAContent = null;
        for (JsonNode o : scNode.path("body").path("options")) {
            displayedOptionIds.add(o.path("id").asText());
            if ("a".equals(o.path("id").asText())) {
                optAContent = o.path("content").asText();
            }
        }
        assertThat(optAContent)
                .isNotNull()
                .contains("src=\"/api/v1/admin/question-assets/" + optionAssetId + "\"");
        List<String> expectedOrder = rawPaper.parts().getFirst().blocks().getFirst().items().getFirst().question().optionOrder();
        assertThat(displayedOptionIds).containsExactlyElementsOf(expectedOrder);

        // Verify essay wordLimit is exposed while rubric & sampleAnswer are absent
        JsonNode essayNode = paperJson.path("parts").get(0).path("blocks").get(4).path("items").get(0).path("question");
        assertThat(essayNode.path("body").path("wordLimit").asInt()).isEqualTo(80);
        assertThat(rawJson).doesNotContain("rubric").doesNotContain("sampleAnswer").doesNotContain("accepted");
    }

    @Test
    void trialExtendsByDurationPlusThirty() throws Exception {
        mutableClock.setAt(Instant.now());
        Subject subject = createSubject();
        insertSingleChoice(subject, false);
        insertSingleChoice(subject, false);

        // SHARED exam (60m): extends by 60m + 30m = 90m
        UUID sharedExamId = insertSingleQuestionExam(null, "SHARED", 60, subject);
        UUID sharedPreviewId = generatePreview(sharedExamId);
        assertSeconds(REDIS_KEY_PREFIX + sharedPreviewId, 30 * 60);

        Instant before = Instant.now();
        JsonNode sharedRes = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", sharedExamId, sharedPreviewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn());

        Instant sharedExpiresAt = Instant.parse(sharedRes.path("expiresAt").asText());
        assertSeconds(REDIS_KEY_PREFIX + sharedPreviewId, 90 * 60);
        assertThat(sharedExpiresAt).isBetween(
                before.plus(Duration.ofMinutes(90)).minusSeconds(10),
                Instant.now().plus(Duration.ofMinutes(90)).plusSeconds(5));
        assertThat(sharedExpiresAt.toEpochMilli()).isEqualTo(storedExpiry(REDIS_KEY_PREFIX + sharedPreviewId));

        // PER_PART exam (40m + 50m = 90m): extends by 90m + 30m = 120m
        UUID perPartExamId = insertCustomExam("BCA", "PER_PART", 0, """
                [{"id":"p1","order":1,"name":"Part 1","durationMinutes":40,"questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"b1","name":"SC1","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                 {"id":"p2","order":2,"name":"Part 2","durationMinutes":50,"questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s2","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"b2","name":"SC2","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]
                """.formatted(subject.code(), subject.code()), 2);
        UUID perPartPreviewId = generatePreview(perPartExamId);

        JsonNode perPartRes = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", perPartExamId, perPartPreviewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn());
        Instant perPartExpiresAt = Instant.parse(perPartRes.path("expiresAt").asText());
        assertSeconds(REDIS_KEY_PREFIX + perPartPreviewId, 120 * 60);
        assertThat(perPartExpiresAt.toEpochMilli()).isEqualTo(storedExpiry(REDIS_KEY_PREFIX + perPartPreviewId));
    }

    @Test
    void trialNeverShortens() throws Exception {
        mutableClock.setAt(Instant.now());
        Subject subject = createSubject();
        insertSingleChoice(subject, false);
        UUID examId = insertSingleQuestionExam(null, "SHARED", 60, subject);
        UUID previewId = generatePreview(examId);

        redis.expire(REDIS_KEY_PREFIX + previewId, Duration.ofMinutes(300));
        long longerStored = storedExpiry(REDIS_KEY_PREFIX + previewId);

        JsonNode res = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn());

        Instant returned = Instant.parse(res.path("expiresAt").asText());
        assertSeconds(REDIS_KEY_PREFIX + previewId, 300 * 60);
        assertThat(returned.toEpochMilli()).isEqualTo(longerStored);
    }

    @Test
    void trialOnExpiredPreviewIs410() throws Exception {
        Subject subject = createSubject();
        insertSingleChoice(subject, false);
        UUID examId = insertSingleQuestionExam("BCA", "SHARED", 60, subject);
        UUID previewId = generatePreview(examId);

        redis.delete(REDIS_KEY_PREFIX + previewId);

        JsonNode err = json(mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isGone())
                .andReturn());
        assertThat(err.path("code").asText()).isEqualTo("PREVIEW_EXPIRED");
        assertThat(redis.hasKey(REDIS_KEY_PREFIX + previewId)).isFalse();
    }

    @Test
    void trialNeedsExamView() throws Exception {
        Subject subject = createSubject();
        insertSingleChoice(subject, false);
        UUID examId = insertSingleQuestionExam("BCA", "SHARED", 60, subject);
        UUID previewId = generatePreview(examId);

        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId)
                        .with(asStudent()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId)
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/trial", examId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
    }

    @Test
    void paperKeepsAudioAndSegments() throws Exception {
        UUID hsca = createdExam(createExamViaApi("HSCA", "PART", "P1", "ANH", 40));
        UUID hscaPreviewId = generatePreview(hsca);
        JsonNode hscaPaper = json(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", hsca, hscaPreviewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(hscaPaper);

        List<JsonNode> audioAttachments = new ArrayList<>();
        for (JsonNode part : hscaPaper.path("parts")) {
            for (JsonNode block : part.path("blocks")) {
                for (JsonNode item : block.path("items")) {
                    if ("SET".equals(item.path("kind").asText())) {
                        for (JsonNode att : item.path("set").path("attachments")) {
                            if (att.path("mime").asText().startsWith("audio/")) {
                                audioAttachments.add(att);
                            }
                        }
                    }
                }
            }
        }
        assertThat(audioAttachments).as("listening preview paper keeps audio attachments on sets").isNotEmpty();
        assertThat(audioAttachments).allSatisfy(att -> {
            assertThat(att.path("id").asText()).isNotBlank();
            assertThat(att.path("name").asText()).isNotBlank();
            assertThat(att.path("downloadUrl").asText())
                    .isEqualTo("/api/v1/admin/question-assets/" + att.path("id").asText());
        });

        UUID vsat = createdExam(createExamViaApi("VSAT", "SUBJECT", null, "ANH", 60));
        UUID vsatPreviewId = generatePreview(vsat);
        JsonNode vsatPaper = json(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/preview/{previewId}/paper", vsat, vsatPreviewId)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(vsatPaper);

        JsonNode segments = vsatPaper.path("parts").get(0).path("displaySegments");
        assertThat(segments).hasSize(4);
        assertThat(segments).allSatisfy(seg -> {
            assertThat(seg.path("name").asText()).isNotBlank();
            assertThat(seg.path("fromPosition").asInt()).isPositive();
        });
    }

    @Test
    void gradePassesPreviewKind() throws Exception {
        Subject subject = createSubject();
        UUID mcQuestion = insertMultipleChoicePartial(subject);

        UUID bcaExam = insertCustomExam("BCA", "SHARED", 60, """
                [{"id":"p1","order":1,"name":"Part 1","questionCount":1,"questionCountOnPaper":1,"questionCountRequired":1,"maxPoints":1,"participation":{"mode":"ALL","choose":null},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"b1","name":"MC","questionType":"MULTIPLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":1}]}],"options":[]}]
                """.formatted(subject.code()), 1);
        jdbc.update("UPDATE exam.exam SET max_score = 1.0 WHERE id = ?", bcaExam);
        UUID bcaPreview = generatePreview(bcaExam);
        JsonNode bcaRes = gradeOk(
                bcaExam, bcaPreview, Map.of(mcQuestion.toString(), Map.of("type", "MULTIPLE_CHOICE", "optionIds", List.of("o1"))));
        assertThat(bcaRes.path("kindCode").asText()).isEqualTo("BCA");
        assertThat(bcaRes.path("rawScore").decimalValue()).isEqualByComparingTo("0.50");

        // Compare preview grading output against a real graded attempt on the exact same question & answer (R1-12)
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", bcaExam);
        jdbc.update(
                "INSERT INTO exam.exam_block_ref(id, exam_id, block_id, part_id, section_id, subject_item_id) VALUES (?, ?, 'b1', 'p1', 's1', ?)",
                UUID.randomUUID(),
                bcaExam,
                subject.id());
        ExamListingFixture.fill(jdbc, bcaExam);
        JsonNode checked = json(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", bcaExam)
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk())
                .andReturn());
        var validPaths = objectMapper.createArrayNode();
        checked.path("paths").forEach(p -> {
            if ("VALID".equals(p.path("result").asText())) {
                validPaths.add(p.path("pathId").asText());
            }
        });
        assertThat(validPaths).as(checked.toString()).isNotEmpty();
        long rev = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, bcaExam);
        ObjectNode pubBody = objectMapper.createObjectNode().put("expectedRevision", rev);
        pubBody.set("pathIds", validPaths);
        mockMvc.perform(post("/api/v1/admin/exams/{id}/publish", bcaExam)
                        .with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubBody.toString()))
                .andExpect(status().isOk());

        UUID student = createStudent("preview-compare-student");
        GrantOrderResult.Line grant = AttemptTestFixtures.grantExam(
                grantService, student, bcaExam, 1, List.of(), "SCORE_AND_ANSWERS", 1);
        Attempt realAttempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, realAttempt.getId());
        AttemptItem realMcItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(realAttempt.getId())
                .stream()
                .filter(i -> i.getQuestionId().equals(mcQuestion))
                .findFirst()
                .orElseThrow();
        assertThat(performSubmit(
                student,
                realAttempt.getId(),
                UUID.randomUUID().toString(),
                sessionId,
                new SubmitAttemptRequest(List.of(
                        new FinalAnswerChange(realMcItem.getId(), 1L, new AnswerValue.MultipleChoice(List.of("o1"))))))
                .getResponse().getStatus()).isEqualTo(200);
        int relayed;
        do {
            relayed = outboxRelay.relayBatch(500);
        } while (relayed > 0);

        Attempt scoredReal = attemptRepository.findById(realAttempt.getId()).orElseThrow();
        AttemptItem scoredRealItem = attemptItemRepository.findById(realMcItem.getId()).orElseThrow();
        assertThat(scoredReal.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(bcaRes.path("rawScore").decimalValue()).isEqualByComparingTo(scoredReal.getRawScore());
        assertThat(bcaRes.path("maxScore").decimalValue()).isEqualByComparingTo(scoredReal.getMaxScore());
        JsonNode previewItem = findGradedItem(bcaRes.path("items"), mcQuestion);
        assertThat(previewItem.path("earnedPoints").decimalValue()).isEqualByComparingTo(scoredRealItem.getEarnedPoints());
        assertThat(previewItem.path("outcome").asText()).isEqualTo(scoredRealItem.getOutcome().name());

        UUID hsaExam = insertCustomExam("HSA", "SHARED", 60, """
                [{"id":"p1","order":1,"name":"Part 1","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"b1","name":"MC","questionType":"MULTIPLE_CHOICE","questionCount":1}]}]}]
                """.formatted(subject.code()), 1);
        UUID hsaPreview = generatePreview(hsaExam);
        JsonNode hsaRes = gradeOk(
                hsaExam, hsaPreview, Map.of(mcQuestion.toString(), Map.of("type", "MULTIPLE_CHOICE", "optionIds", List.of("o1"))));
        assertThat(hsaRes.path("kindCode").asText()).isEqualTo("HSA");
        assertThat(hsaRes.path("rawScore").decimalValue()).isEqualByComparingTo("0.50");
    }

    // --- Support helpers --------------------------------------------------------------------------------

    private JsonNode gradeOk(UUID examId, UUID previewId, Map<String, Object> answers) throws Exception {
        return gradeExpectStatus(examId, previewId, answers, 200);
    }

    private JsonNode gradeExpectStatus(UUID examId, UUID previewId, Map<String, Object> answers, int statusCode)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("answers", answers));
        MvcResult result = mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                                .with(asUser(Permissions.EXAM_VIEW))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().is(statusCode))
                .andReturn();
        return json(result);
    }

    private UUID generatePreview(UUID examId) throws Exception {
        String body = "{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + SEED + "}";
        JsonNode result = json(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", examId)
                        .with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(result.path("status").asText()).as(result.path("issues").toString()).isEqualTo("GENERATED");
        return UUID.fromString(result.path("previewId").asText());
    }

    private ResultActions createExamViaApi(
            String kind, String scope, String partCode, String subjectCode, int minutes) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("kind", kind)
                .put("scope", scope)
                .put("name", "AT5 trial " + kind)
                .put("code", "AT5T-" + UUID.randomUUID().toString().substring(0, 8))
                .put("subjectCode", subjectCode)
                .put("durationMinutes", minutes);
        if (partCode != null) {
            body.put("partCode", partCode);
        }
        return mockMvc.perform(post("/api/v1/admin/exams")
                .with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private UUID createdExam(ResultActions result) throws Exception {
        return UUID.fromString(json(result.andExpect(status().isCreated()).andReturn())
                .path("id")
                .asText());
    }

    private UUID insertSingleQuestionExam(String kindCode, String timing, int durationMinutes, Subject subject) {
        return insertCustomExam(kindCode, timing, durationMinutes, """
                [{"id":"p1","order":1,"name":"Part 1","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"b1","name":"SC","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]
                """.formatted(subject.code()), 1);
    }

    private UUID insertCustomExam(
            String kindCode, String timing, int durationMinutes, String partsJson, int questionCount) {
        UUID examId = UUID.randomUUID();
        UUID kindItemId = kindCode == null
                ? null
                : jdbc.queryForObject(
                        "select id from catalog.catalog_item where code = ? limit 1", UUID.class, kindCode);
        jdbc.update(
                "insert into exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, duration_minutes, question_count) "
                        + "values (?, ?, ?, ?, ?, 'FULL', ?, ?, ?)",
                examId,
                examId.toString(),
                examId.toString(),
                "AT5 Trial Exam " + kindCode,
                kindItemId,
                timing,
                durationMinutes > 0 ? durationMinutes : null,
                questionCount);
        jdbc.update(
                "insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'at5-test')",
                examId,
                "{\"parts\":" + partsJson + "}");
        return examId;
    }

    private Subject createSubject() {
        UUID id = UUID.randomUUID();
        UUID subjectGroup = jdbc.queryForObject(
                "select group_id from catalog.catalog_item where code = 'TOAN' limit 1", UUID.class);
        String code = "AT5S_" + id.toString().replace("-", "");
        jdbc.update(
                """
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'AT5 Subject', 'AT5 Subject', 0, 1, false, 1, ?)
                """,
                id,
                subjectGroup,
                code,
                code.toLowerCase(Locale.ROOT),
                subjectGroup,
                "/" + id + "/");
        return new Subject(id, code);
    }

    private UUID insertSingleChoice(Subject subject, boolean shuffle) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'SINGLE_CHOICE','READY',?,1,1.0)",
                id,
                "AT5-SC-" + id,
                subject.id());
        String snapshot = """
                {
                  "content": "<p>Single choice question</p>",
                  "points": 1.0,
                  "body": {
                    "type": "SINGLE_CHOICE",
                    "shuffleOptions": %s,
                    "options": [
                      {"id": "a", "content": "A", "correct": true},
                      {"id": "b", "content": "B", "correct": false},
                      {"id": "c", "content": "C", "correct": false},
                      {"id": "d", "content": "D", "correct": false},
                      {"id": "e", "content": "E", "correct": false},
                      {"id": "f", "content": "F", "correct": false}
                    ],
                    "solution": "Option A is right"
                  }
                }
                """.formatted(shuffle);
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertMultipleChoicePartial(Subject subject) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'MULTIPLE_CHOICE','READY',?,1,1.0)",
                id,
                "AT5-MC-" + id,
                subject.id());
        String snapshot = """
                {
                  "content": "<p>Multiple choice</p>",
                  "points": 1.0,
                  "body": {
                    "type": "MULTIPLE_CHOICE",
                    "scoringRule": "PARTIAL",
                    "options": [
                      {"id": "o1", "content": "1", "correct": true},
                      {"id": "o2", "content": "2", "correct": true},
                      {"id": "o3", "content": "3", "correct": false}
                    ]
                  }
                }
                """;
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertTrueFalsePerStatement(Subject subject) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'TRUE_FALSE','READY',?,1,2.0)",
                id,
                "AT5-TF-" + id,
                subject.id());
        String snapshot = """
                {
                  "content": "<p>True False</p>",
                  "points": 2.0,
                  "body": {
                    "type": "TRUE_FALSE",
                    "scoringMethod": "PER_STATEMENT",
                    "statements": [
                      {"id": "s1", "content": "St 1", "answer": true, "points": 0.5},
                      {"id": "s2", "content": "St 2", "answer": false, "points": 0.5},
                      {"id": "s3", "content": "St 3", "answer": true, "points": 0.5},
                      {"id": "s4", "content": "St 4", "answer": false, "points": 0.5}
                    ]
                  }
                }
                """;
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertClozeTwoBlanks(Subject subject) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'CLOZE','READY',?,1,1.0)",
                id,
                "AT5-CL-" + id,
                subject.id());
        String snapshot = """
                {
                  "content": "<p>Cloze</p>",
                  "points": 1.0,
                  "body": {
                    "type": "CLOZE",
                    "passage": "<p>Fill {{b1}} and {{b2}}</p>",
                    "blanks": [
                      {"id": "b1", "points": 0.5, "answers": ["alpha"]},
                      {"id": "b2", "points": 0.5, "answers": ["beta"]}
                    ]
                  }
                }
                """;
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertShortAnswer(Subject subject) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'SHORT_ANSWER','READY',?,1,1.0)",
                id,
                "AT5-SA-" + id,
                subject.id());
        String snapshot = """
                {
                  "content": "<p>Short Answer</p>",
                  "points": 1.0,
                  "body": {
                    "type": "SHORT_ANSWER",
                    "inputHint": "Enter city",
                    "answers": ["Hà Nội"]
                  }
                }
                """;
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertMatchingNoReuse(Subject subject) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'MATCHING','READY',?,1,1.0)",
                id,
                "AT5-MA-" + id,
                subject.id());
        String snapshot = """
                {
                  "content": "<p>Matching</p>",
                  "points": 1.0,
                  "body": {
                    "type": "MATCHING",
                    "reuseRight": false,
                    "left": [{"id": "l1", "content": "L1"}, {"id": "l2", "content": "L2"}],
                    "right": [{"id": "r1", "content": "R1"}, {"id": "r2", "content": "R2"}],
                    "pairs": [
                      {"leftId": "l1", "rightId": "r1", "points": 0.5},
                      {"leftId": "l2", "rightId": "r2", "points": 0.5}
                    ]
                  }
                }
                """;
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertEssay(Subject subject, int wordLimit, BigDecimal points) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'ESSAY','READY',?,1,?)",
                id,
                "AT5-ES-" + id,
                subject.id(),
                points);
        String snapshot = """
                {
                  "content": "<p>Essay prompt</p>",
                  "points": %s,
                  "body": {
                    "type": "ESSAY",
                    "wordLimit": %d,
                    "rubric": [{"id": "c1", "description": "Criteria 1", "maxPoints": %s}],
                    "sampleAnswer": "Secret model essay"
                  }
                }
                """.formatted(points.toPlainString(), wordLimit, points.toPlainString());
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private SetIds insertQuestionSetTwoSingles(Subject subject) {
        UUID parent = UUID.randomUUID();
        UUID child1 = UUID.randomUUID();
        UUID child2 = UUID.randomUUID();
        jdbc.update(
                "insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "values (?,?,'QUESTION_SET','READY',?,1,2.0)",
                parent,
                "AT5-SET-" + parent,
                subject.id());
        int pos = 0;
        for (UUID child : List.of(child1, child2)) {
            jdbc.update(
                    "insert into questionbank.question(id, code, type, status, parent_id, position, points) "
                            + "values (?,?,'SINGLE_CHOICE','READY',?,?,1.0)",
                    child,
                    "AT5-CH-" + child,
                    parent,
                    pos++);
            String childSnap = """
                    {
                      "content": "<p>Child %s</p>",
                      "points": 1.0,
                      "body": {
                        "type": "SINGLE_CHOICE",
                        "options": [
                          {"id": "a", "content": "A", "correct": true},
                          {"id": "b", "content": "B", "correct": false}
                        ]
                      }
                    }
                    """.formatted(pos);
            jdbc.update(
                    "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                    child,
                    childSnap);
        }
        String parentSnap = """
                {
                  "content": "<p>Set header</p>",
                  "points": 2.0,
                  "body": {
                    "type": "QUESTION_SET",
                    "passage": "<p>Reading passage</p>",
                    "children": [{"id": "%s"}, {"id": "%s"}]
                  }
                }
                """.formatted(child1, child2);
        jdbc.update(
                "insert into questionbank.question_version(question_id, version_no, snapshot) values (?, 1, cast(? as jsonb))",
                parent,
                parentSnap);
        return new SetIds(parent, child1, child2);
    }

    private static JsonNode findGradedItem(JsonNode items, UUID questionId) {
        for (JsonNode item : items) {
            if (questionId.toString().equals(item.path("questionId").asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing graded item " + questionId);
    }

    private static List<String> extractPortQuestionOutline(PreviewPaperPort.PreviewPaper paper) {
        List<String> lines = new ArrayList<>();
        for (PreviewPaperPort.Part part : paper.parts()) {
            for (PreviewPaperPort.Block block : part.blocks()) {
                for (PreviewPaperPort.Item item : block.items()) {
                    if (item.kind() == PreviewPaperPort.Item.Kind.SET) {
                        lines.add("SET:" + item.set().questionId());
                        for (PreviewPaperPort.Question q : item.set().children()) {
                            lines.add(q.number() + ":" + q.questionId() + ":" + q.optionOrder());
                        }
                    } else {
                        PreviewPaperPort.Question q = item.question();
                        lines.add(q.number() + ":" + q.questionId() + ":" + q.optionOrder());
                    }
                }
            }
        }
        return lines;
    }

    private static List<String> extractResponseQuestionOutline(JsonNode paperJson) {
        List<String> lines = new ArrayList<>();
        for (JsonNode part : paperJson.path("parts")) {
            for (JsonNode block : part.path("blocks")) {
                for (JsonNode item : block.path("items")) {
                    if ("SET".equals(item.path("kind").asText())) {
                        JsonNode set = item.path("set");
                        lines.add("SET:" + set.path("questionId").asText());
                        for (JsonNode q : set.path("children")) {
                            lines.add(questionLine(q));
                        }
                    } else {
                        lines.add(questionLine(item.path("question")));
                    }
                }
            }
        }
        return lines;
    }

    private static String questionLine(JsonNode q) {
        List<String> order = null;
        if (q.has("optionOrder") && q.path("optionOrder").isArray()) {
            List<String> parsed = new ArrayList<>();
            for (JsonNode n : q.path("optionOrder")) {
                parsed.add(n.asText());
            }
            order = parsed;
        }
        return q.path("number").asInt() + ":" + q.path("questionId").asText() + ":" + order;
    }

    private int countRows(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count != null ? count : 0;
    }

    private void assertSeconds(String key, long expectedSeconds) {
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(expectedSeconds - 15, expectedSeconds);
    }

    private long storedExpiry(String key) {
        Long value = redis.execute(
                new DefaultRedisScript<>("return redis.call('PEXPIRETIME', KEYS[1])", Long.class),
                List.of(key));
        assertThat(value).isNotNull().isPositive();
        return value;
    }

    private record Subject(UUID id, String code) {}

    private record SetIds(UUID parent, UUID child1, UUID child2) {}
}
