package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

class AttemptReviewIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private QuestionAssetRepository assetRepository;
    @Autowired private ObjectStorage objectStorage;

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

        // R1-7/8: unrecognized or null resultView must fail closed with 403 REVIEW_NOT_INCLUDED
        jdbc.update("UPDATE commerce.entitlement_grant SET result_view = 'UNKNOWN_TIER' WHERE id = ?", line.grantId());
        assertThat(mockMvc.perform(
                                get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                        .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(403);
        MvcResult resultCheck = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(objectMapper.readTree(resultCheck.getResponse().getContentAsString()).path("reviewAvailable").asBoolean())
                .isFalse();
    }

    @Test
    void answersViewHasNoSolution() throws Exception {
        UUID student = createStudent("rev-answers");
        UUID stranger = createStudent("rev-stranger");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-BCA-FULL-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "SCORE_AND_ANSWERS", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(
                student, UUID.randomUUID().toString(), line.entitlementId(), new PreChoiceInput("CA2", null));

        // Before grading finished -> 409 REVIEW_NOT_AVAILABLE
        MvcResult earlyReview = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(earlyReview.getResponse().getStatus()).isEqualTo(409);

        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        performFinishPart(student, attempt.getId(), parts.get(0).getId(), sessionId,
                new FinishPartRequest(List.of(new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài viết tự luận</p>", 4)))));
        performFinishPart(student, attempt.getId(), parts.get(1).getId(), sessionId, new FinishPartRequest(List.of()));
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        // Simulate finalized essay with comment & criteria — SCORE_AND_ANSWERS must NEVER leak them (R1-10, R1-11)
        jdbc.update(
                """
                        UPDATE attempt.attempt_item
                        SET earned_points = 15.00,
                            outcome = 'PARTIAL',
                            grading_detail = '{"wordCount":4,"exactFraction":"15","comment":"Nhận xét mật","criteria":[{"id":"c1","points":15,"comment":"Tiêu chí mật"}]}'::jsonb
                        WHERE id = ?
                        """,
                essayItem.getId());
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'SCORED', graded_at = now() WHERE id = ?",
                attempt.getId());

        MvcResult review = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(review.getResponse().getStatus()).isEqualTo(200);
        String rawJson = review.getResponse().getContentAsString();
        assertThat(rawJson).doesNotContain("Nhận xét mật");
        assertThat(rawJson).doesNotContain("Tiêu chí mật");
        assertThat(rawJson).doesNotContain("exactFraction");

        // solution, essayFeedback, comment, criteria and internal rubric keys MUST NOT exist on SCORE_AND_ANSWERS
        ForbiddenKeysAssert.assertNoForbiddenKeys(
                objectMapper.readTree(rawJson),
                Set.of(
                        "solution",
                        "essayFeedback",
                        "comment",
                        "criteria",
                        "gradingGuide",
                        "rubric",
                        "sampleAnswer",
                        "commonMistakes"),
                "review-score-and-answers");

        JsonNode json = objectMapper.readTree(rawJson);
        assertThat(json.path("resultView").asText()).isEqualTo("SCORE_AND_ANSWERS");
        assertThat(json.path("items")).isNotEmpty();
        boolean foundNonEssayExpected = false;
        boolean foundFinalizedEssay = false;
        for (JsonNode item : json.path("items")) {
            if ("ESSAY".equals(item.path("questionType").asText())
                    && item.path("itemId").asText().equals(essayItem.getId().toString())) {
                foundFinalizedEssay = true;
                assertThat(item.path("gradingDetail").path("wordCount").asInt()).isEqualTo(4);
                assertThat(item.has("essayFeedback")).isFalse();
            } else if (!"QUESTION_SET".equals(item.path("questionType").asText())) {
                foundNonEssayExpected = true;
                assertThat(item.path("expectedAnswer").isNull()).isFalse();
            }
        }
        assertThat(foundNonEssayExpected).isTrue();
        assertThat(foundFinalizedEssay).isTrue();

        // Stranger gets 404
        assertThat(mockMvc.perform(
                                get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                        .with(AttemptTestFixtures.asExamUser(stranger, "stranger")))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void fullSolutionIncludesSolutionRewritesUrlsAttachmentsAndHidesRubric() throws Exception {
        UUID student = createStudent("rev-full");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] solBytes = "SOL-IMG".getBytes(StandardCharsets.UTF_8);
            byte[] attBytes = "%PDF-1.4\nReviewAttachment".getBytes(StandardCharsets.UTF_8);
            UUID solAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "sol.png", "image/png", solBytes, createdAssets);
            UUID attAssetId = saveStoredAsset(adminId, QuestionAssetKind.ATTACHMENT, "review-doc.pdf", "application/pdf", attBytes, createdAssets);

            UUID richQ = createReadySingleChoiceWithSolutionAndAttachment(
                    adminId,
                    "<p>Lời giải chi tiết <img data-asset-id=\"" + solAssetId + "\"></p>",
                    List.of(attAssetId),
                    createdQuestions);
            String legacyAdminSolution = "<p>Lời giải chi tiết <img src=\"https://admin.thi360.example/api/v1/admin/question-assets/" + solAssetId + "/content\"></p>";
            jdbc.update(
                    "UPDATE questionbank.question_version SET snapshot = jsonb_set(snapshot, '{solution}', to_jsonb(?::text)) WHERE question_id = ? AND version_no = 1",
                    legacyAdminSolution,
                    richQ);

            UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
            var serving = publishedExamPort.findServing(examId).orElseThrow();
            GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                    grantService, student, examId, 2, List.of(), "FULL_SOLUTION", serving.version().versionNo());
            Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);

            AttemptItem targetItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                    .stream()
                    .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                    .findFirst()
                    .orElseThrow();
            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, option_order = '[\"o2\",\"o1\"]'::jsonb WHERE id = ?",
                    richQ,
                    targetItem.getId());

            UUID sessionId = claimSession(student, attempt.getId());
            performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
            outboxRelay.relayBatch(500);

            MvcResult review = mockMvc.perform(
                            get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                    .with(AttemptTestFixtures.asExamUser(student, "learner")))
                    .andReturn();
            assertThat(review.getResponse().getStatus()).isEqualTo(200);
            String rawJson = review.getResponse().getContentAsString();
            assertThat(rawJson).doesNotContain("/api/v1/admin/");
            ForbiddenKeysAssert.assertNoForbiddenKeys(
                    objectMapper.readTree(rawJson),
                    Set.of("gradingGuide", "rubric", "sampleAnswer", "commonMistakes", "exactFraction"),
                    "review-full-solution");

            JsonNode json = objectMapper.readTree(rawJson);
            assertThat(json.path("resultView").asText()).isEqualTo("FULL_SOLUTION");
            assertThat(json.path("items")).isNotEmpty();

            JsonNode richNode = null;
            for (JsonNode item : json.path("items")) {
                if (item.path("itemId").asText().equals(targetItem.getId().toString())) {
                    richNode = item;
                    break;
                }
            }
            assertThat(richNode).isNotNull();
            assertThat(richNode.path("solution").asText())
                    .contains("Lời giải chi tiết")
                    .contains("/api/v1/me/attempts/" + attempt.getId() + "/solution-assets/" + solAssetId);
            assertThat(richNode.path("expectedAnswer").path("expectedOptionIds").get(0).asText()).isEqualTo("o1");
            // R1-21/22: attachments[] populated from QuestionAttachmentPort / attachmentIds
            assertThat(richNode.path("attachments")).hasSize(1);
            assertThat(richNode.path("attachments").get(0).path("id").asText()).isEqualTo(attAssetId.toString());
            assertThat(richNode.path("attachments").get(0).path("name").asText()).isEqualTo("review-doc.pdf");
            assertThat(richNode.path("attachments").get(0).path("downloadUrl").asText())
                    .isEqualTo("/api/v1/me/attempts/" + attempt.getId() + "/assets/" + attAssetId);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            for (UUID assetId : createdAssets) {
                jdbc.update("DELETE FROM questionbank.question_asset WHERE id = ?", assetId);
            }
        }
    }

    @Test
    void reviewKeepsOptionOrder() throws Exception {
        // AC-28 (#reviewKeepsOptionOrder): body.options[*].id & body.right[*].id follow persisted option_order
        UUID student = createStudent("rev-opt-order");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        try {
            UUID matchingQ = createReadyMatchingQuestion(adminId, createdQuestions);

            UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
            var serving = publishedExamPort.findServing(examId).orElseThrow();
            GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                    grantService, student, examId, 2, List.of(), "SCORE_AND_ANSWERS", serving.version().versionNo());
            Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);

            List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
            AttemptItem scItem = items.stream()
                    .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE && i.getOptionOrder() != null && i.getOptionOrder().size() >= 2)
                    .findFirst()
                    .orElseThrow();
            List<String> reversedScOrder = new ArrayList<>(scItem.getOptionOrder());
            java.util.Collections.reverse(reversedScOrder);
            jdbc.update(
                    "UPDATE attempt.attempt_item SET option_order = ?::jsonb WHERE id = ?",
                    objectMapper.writeValueAsString(reversedScOrder),
                    scItem.getId());

            AttemptItem matchingTarget = items.stream()
                    .filter(i -> !i.getId().equals(scItem.getId()) && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                    .findFirst()
                    .orElseThrow();
            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'MATCHING', option_order = '[\"r2\",\"r1\"]'::jsonb WHERE id = ?",
                    matchingQ,
                    matchingTarget.getId());

            UUID sessionId = claimSession(student, attempt.getId());
            performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
            outboxRelay.relayBatch(500);

            MvcResult review = mockMvc.perform(
                            get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                    .with(AttemptTestFixtures.asExamUser(student, "learner")))
                    .andReturn();
            assertThat(review.getResponse().getStatus()).isEqualTo(200);
            JsonNode json = objectMapper.readTree(review.getResponse().getContentAsString());

            JsonNode scNode = findItemNode(json, scItem.getId());
            List<String> actualScIds = new ArrayList<>();
            scNode.path("body").path("options").forEach(o -> actualScIds.add(o.path("id").asText()));
            assertThat(actualScIds).containsExactlyElementsOf(reversedScOrder);

            JsonNode matchNode = findItemNode(json, matchingTarget.getId());
            List<String> actualRightIds = new ArrayList<>();
            matchNode.path("body").path("right").forEach(r -> actualRightIds.add(r.path("id").asText()));
            assertThat(actualRightIds).containsExactly("r2", "r1");
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
        }
    }

    @Test
    void pendingReviewFiltersOutPendingEssaysAndMixedQuestionSetNeverEmitsPendingOutcome() throws Exception {
        UUID student = createStudent("rev-pending-part");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-BCA-FULL-MINI");
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "FULL_SOLUTION", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(
                student, UUID.randomUUID().toString(), line.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

        List<AttemptItem> allItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
        AttemptItem essayItem = allItems.stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        performFinishPart(student, attempt.getId(), parts.get(0).getId(), sessionId,
                new FinishPartRequest(List.of(new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài viết đang chấm</p>", 4)))));
        performFinishPart(student, attempt.getId(), parts.get(1).getId(), sessionId, new FinishPartRequest(List.of()));
        performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);

        // R1-42/43: Also test mixed QUESTION_SET (passage with graded MCQ child + pending Essay child)
        AttemptItem setParent = allItems.stream()
                .filter(i -> i.getQuestionType() == QuestionType.QUESTION_SET)
                .findFirst()
                .orElse(null);
        if (setParent != null) {
            jdbc.update("UPDATE attempt.attempt_item SET outcome = 'PENDING_REVIEW', earned_points = NULL WHERE id = ?", setParent.getId());
        }

        // During PENDING_REVIEW, review returns ONLY non-pending items and ZERO outcome=PENDING_REVIEW
        MvcResult pendingReview = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(pendingReview.getResponse().getStatus()).isEqualTo(200);
        JsonNode pendingJson = objectMapper.readTree(pendingReview.getResponse().getContentAsString());
        assertThat(pendingJson.path("state").asText()).isEqualTo("PENDING_REVIEW");
        for (JsonNode item : pendingJson.path("items")) {
            assertThat(item.path("itemId").asText()).isNotEqualTo(essayItem.getId().toString());
            assertThat(item.path("outcome").asText("")).isNotEqualTo("PENDING_REVIEW");
            if (setParent != null && item.path("itemId").asText().equals(setParent.getId().toString())) {
                assertThat(item.has("outcome")).isFalse();
                assertThat(item.has("earnedPoints")).isFalse();
            }
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
                            grading_detail = '{"wordCount":4,"exactFraction":"15","comment":"Lập luận tốt","criteria":[{"id":"c1","points":15,"comment":"Rõ ràng"}]}'::jsonb
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
        JsonNode essayNode = findItemNode(scoredJson, essayItem.getId());
        assertThat(essayNode.path("essayFeedback").path("points").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(essayNode.path("essayFeedback").path("comment").asText()).isEqualTo("Lập luận tốt");
        assertThat(essayNode.path("essayFeedback").path("criteria")).hasSize(1);
        assertThat(essayNode.path("gradingDetail").has("comment")).isFalse();
        assertThat(essayNode.path("gradingDetail").has("criteria")).isFalse();
    }

    @Test
    void mixedQuestionSetDuringPendingReviewIncludesPassageAndOmitsPendingOutcome() throws Exception {
        // R1-42/43: mixed QUESTION_SET (graded MCQ child + pending Essay child) during PENDING_REVIEW
        UUID student = createStudent("rev-mixed-set");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        try {
            String passage = "<p>Ngữ liệu chung câu chùm</p>";
            SaveQuestionRequest setReq = new SaveQuestionRequest(
                    QuestionType.QUESTION_SET,
                    "TOAN",
                    "G12",
                    "TH",
                    null,
                    null,
                    new BigDecimal("3.0"),
                    List.of(),
                    List.of(),
                    passage,
                    "<p>Lời giải chung câu chùm</p>",
                    List.of(),
                    List.of(),
                    new QuestionBody.QuestionSetBody(
                            QuestionType.QUESTION_SET,
                            passage,
                            1,
                            List.of(
                                    new QuestionBody.ChildQuestion(
                                            QuestionType.SINGLE_CHOICE,
                                            "<p>Câu con trắc nghiệm</p>",
                                            new QuestionBody.SingleChoiceBody(
                                                    QuestionType.SINGLE_CHOICE,
                                                    List.of(
                                                            new QuestionBody.ChoiceOption("c1", "<p>Đúng</p>", true),
                                                            new QuestionBody.ChoiceOption("c2", "<p>Sai</p>", false)),
                                                    true),
                                            "<p>Lời giải trắc nghiệm</p>",
                                            BigDecimal.ONE,
                                            null,
                                            null,
                                            null,
                                            null),
                                    new QuestionBody.ChildQuestion(
                                            QuestionType.ESSAY,
                                            "<p>Câu con tự luận</p>",
                                            new QuestionBody.EssayBody(
                                                    QuestionType.ESSAY, "<p>Hướng dẫn chấm</p>", List.of(), null, 50),
                                            "<p>Lời giải tự luận</p>",
                                            new BigDecimal("2.0"),
                                            null,
                                            null,
                                            null,
                                            null))),
                    "AT4-REV-SET-" + UUID.randomUUID().toString().substring(0, 8));
            UUID setQId = createAndReady(adminId, setReq, createdQuestions);

            List<UUID> childIds = jdbc.queryForList(
                    "SELECT id FROM questionbank.question WHERE parent_id = ? ORDER BY created_at ASC",
                    UUID.class,
                    setQId);
            assertThat(childIds).hasSize(2);
            UUID mcChildQId = childIds.get(0);
            UUID essayChildQId = childIds.get(1);

            UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
            var serving = publishedExamPort.findServing(examId).orElseThrow();
            GrantOrderResult.Line line = AttemptTestFixtures.grantExam(
                    grantService, student, examId, 2, List.of(), "FULL_SOLUTION", serving.version().versionNo());
            Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), line.entitlementId(), null);

            List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId());
            assertThat(items.size()).isGreaterThanOrEqualTo(3);
            AttemptItem pItem = items.get(0);
            AttemptItem cMc = items.get(1);
            AttemptItem cEssay = items.get(2);

            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'QUESTION_SET', display_no = NULL, unit_count = 0, points_max = 3.0 WHERE id = ?",
                    setQId,
                    pItem.getId());
            jdbc.update(
                    "UPDATE attempt.attempt_item SET parent_item_id = ?, question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', points_max = 1.0, option_order = '[\"c1\",\"c2\"]'::jsonb WHERE id = ?",
                    pItem.getId(),
                    mcChildQId,
                    cMc.getId());
            jdbc.update(
                    "UPDATE attempt.attempt_item SET parent_item_id = ?, question_id = ?, question_version_no = 1, question_type = 'ESSAY', points_max = 2.0 WHERE id = ?",
                    pItem.getId(),
                    essayChildQId,
                    cEssay.getId());

            UUID sessionId = claimSession(student, attempt.getId());
            performSubmit(
                    student,
                    attempt.getId(),
                    UUID.randomUUID().toString(),
                    sessionId,
                    new SubmitAttemptRequest(List.of(
                            new FinalAnswerChange(cMc.getId(), 1L, new AnswerValue.SingleChoice("c1")),
                            new FinalAnswerChange(cEssay.getId(), 1L, new AnswerValue.Essay("<p>Bài viết đang chờ chấm</p>", 5)))));
            outboxRelay.relayBatch(500);

            MvcResult review = mockMvc.perform(
                            get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                    .with(AttemptTestFixtures.asExamUser(student, "learner")))
                    .andReturn();
            assertThat(review.getResponse().getStatus()).isEqualTo(200);
            JsonNode json = objectMapper.readTree(review.getResponse().getContentAsString());
            assertThat(json.path("state").asText()).isEqualTo("PENDING_REVIEW");

            boolean sawParent = false;
            boolean sawMcChild = false;
            for (JsonNode item : json.path("items")) {
                assertThat(item.path("itemId").asText()).isNotEqualTo(cEssay.getId().toString());
                assertThat(item.path("outcome").asText("")).isNotEqualTo("PENDING_REVIEW");
                if (item.path("itemId").asText().equals(pItem.getId().toString())) {
                    sawParent = true;
                    assertThat(item.path("body").path("passage").asText()).contains("Ngữ liệu chung câu chùm");
                    assertThat(item.has("outcome")).isFalse();
                    assertThat(item.has("earnedPoints")).isFalse();
                    assertThat(item.has("gradingDetail")).isFalse();
                    assertThat(item.has("solution")).isFalse();
                } else if (item.path("itemId").asText().equals(cMc.getId().toString())) {
                    sawMcChild = true;
                    assertThat(item.path("outcome").asText()).isEqualTo("CORRECT");
                }
            }
            assertThat(sawParent).isTrue();
            assertThat(sawMcChild).isTrue();
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
        }
    }

    private static JsonNode findItemNode(JsonNode reviewJson, UUID itemId) {
        for (JsonNode item : reviewJson.path("items")) {
            if (item.path("itemId").asText().equals(itemId.toString())) {
                return item;
            }
        }
        throw new AssertionError("Missing item " + itemId + " in review response");
    }

    private UUID saveStoredAsset(
            UUID actor,
            QuestionAssetKind kind,
            String fileName,
            String mime,
            byte[] bytes,
            List<UUID> createdAssets) throws Exception {
        UUID id = Ids.newId();
        String objectKey = "questions/at4-rev-test/" + id + "-" + fileName;
        objectStorage.put(objectKey, bytes, mime);
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(bytes);
        assetRepository.saveAndFlush(
                new QuestionAsset(
                        id,
                        kind,
                        objectKey,
                        fileName,
                        mime,
                        bytes.length,
                        sha256,
                        kind == QuestionAssetKind.IMAGE ? 8 : null,
                        kind == QuestionAssetKind.IMAGE ? 8 : null,
                        actor,
                        Instant.now()));
        createdAssets.add(id);
        return id;
    }

    private UUID createReadySingleChoiceWithSolutionAndAttachment(
            UUID adminId,
            String solution,
            List<UUID> attachmentIds,
            List<UUID> createdQuestions) throws Exception {
        SaveQuestionRequest req = new SaveQuestionRequest(
                QuestionType.SINGLE_CHOICE,
                "TOAN",
                "G12",
                "NB",
                null,
                null,
                BigDecimal.ONE,
                List.of(),
                List.of(),
                "<p>Đề bài có file đính kèm</p>",
                solution,
                List.of(),
                attachmentIds,
                new QuestionBody.SingleChoiceBody(
                        QuestionType.SINGLE_CHOICE,
                        List.of(
                                new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                        true),
                "AT4-REV-ATT-" + UUID.randomUUID().toString().substring(0, 8));
        return createAndReady(adminId, req, createdQuestions);
    }

    private UUID createReadyMatchingQuestion(UUID adminId, List<UUID> createdQuestions) throws Exception {
        SaveQuestionRequest req = new SaveQuestionRequest(
                QuestionType.MATCHING,
                "TOAN",
                "G12",
                "TH",
                null,
                null,
                new BigDecimal("2.0"),
                List.of(),
                List.of(),
                "<p>Ghép cột trái với cột phải</p>",
                "<p>Lời giải ghép đôi</p>",
                List.of(),
                List.of(),
                new QuestionBody.MatchingBody(
                        QuestionType.MATCHING,
                        List.of(
                                new QuestionBody.MatchingSide("l1", "<p>L1</p>"),
                                new QuestionBody.MatchingSide("l2", "<p>L2</p>")),
                        List.of(
                                new QuestionBody.MatchingSide("r1", "<p>R1</p>"),
                                new QuestionBody.MatchingSide("r2", "<p>R2</p>")),
                        List.of(
                                new QuestionBody.MatchingPair("l1", "r1", BigDecimal.ONE),
                                new QuestionBody.MatchingPair("l2", "r2", BigDecimal.ONE)),
                        false),
                "AT4-REV-MAT-" + UUID.randomUUID().toString().substring(0, 8));
        return createAndReady(adminId, req, createdQuestions);
    }

    private UUID createAndReady(UUID adminId, SaveQuestionRequest req, List<UUID> createdQuestions) throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/api/v1/admin/questions")
                                .with(asUser(adminId, "q-admin-rev", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "at4-rev-cr-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isBetween(200, 201);
        UUID qId = UUID.fromString(json(created).path("summary").path("id").asText());
        createdQuestions.add(qId);

        MvcResult readyRes = mockMvc.perform(
                        post("/api/v1/admin/questions/bulk/ready")
                                .with(asUser(adminId, "q-admin-rev", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "at4-rev-rd-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(qId)))))
                .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return qId;
    }
}
