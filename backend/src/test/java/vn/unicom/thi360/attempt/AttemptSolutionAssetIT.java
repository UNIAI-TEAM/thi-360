package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.storage.ObjectStorage;

class AttemptSolutionAssetIT extends AbstractAttemptDsIT {

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
    void servesOnlySolutionAssetsUnderFullSolutionScoredOrRegrading() throws Exception {
        UUID student = createStudent("sol-asset-owner");
        UUID stranger = createStudent("sol-asset-stranger");
        UUID manager = createManager("sol-asset-mgr");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] stemBytes = "STEM-ASSET-BYTES".getBytes(StandardCharsets.UTF_8);
            byte[] solutionBytes = "SOLUTION-PNG-BYTES".getBytes(StandardCharsets.UTF_8);
            byte[] guideBytes = "GUIDE-SECRET-BYTES".getBytes(StandardCharsets.UTF_8);

            UUID stemAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "stem.png", "image/png", stemBytes, createdAssets);
            UUID solAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "sol.png", "image/png", solutionBytes, createdAssets);
            UUID guideAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "guide.png", "image/png", guideBytes, createdAssets);

            UUID essayQ = createReadyEssayQuestion(
                    adminId,
                    "<p>Stem <img data-asset-id=\"" + stemAssetId + "\"></p>",
                    "<p>Solution <img data-asset-id=\"" + solAssetId + "\"></p>",
                    "<p>Guide <img data-asset-id=\"" + guideAssetId + "\"></p>",
                    createdQuestions);

            // Start an attempt and point an in-scope item at essayQ
            UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
            var serving = publishedExamPort.findServing(examId).orElseThrow();
            GrantOrderResult.Line fullSolGrant = AttemptTestFixtures.grantExam(
                    grantService, student, examId, 3, List.of(), "FULL_SOLUTION", serving.version().versionNo());
            Attempt attempt = startAndAwaitOpen(
                    student, UUID.randomUUID().toString(), fullSolGrant.entitlementId(), null);

            AttemptItem targetItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                    .stream()
                    .filter(i -> i.getQuestionType() != QuestionType.QUESTION_SET)
                    .findFirst()
                    .orElseThrow();
            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1 WHERE id = ?",
                    essayQ,
                    targetItem.getId());

            // 1. IN_PROGRESS -> solution-asset MUST 404 (BS §15)
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);

            // Submit -> GRADING -> solution-asset MUST 404
            UUID sessionId = claimSession(student, attempt.getId());
            performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);

            outboxRelay.relayBatch(500);

            // Wait: simulate non-regrading PENDING_REVIEW first -> MUST 404
            jdbc.update(
                    "UPDATE attempt.attempt SET state = 'PENDING_REVIEW', regrading = false WHERE id = ?",
                    attempt.getId());
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);

            // PENDING_REVIEW with regrading=true -> MUST 200 (BS §15)
            jdbc.update(
                    "UPDATE attempt.attempt SET state = 'PENDING_REVIEW', regrading = true WHERE id = ?",
                    attempt.getId());
            MvcResult regradingOk = downloadSolAsset(student, attempt.getId(), solAssetId);
            assertThat(regradingOk.getResponse().getStatus()).isEqualTo(200);
            assertThat(regradingOk.getResponse().getContentAsByteArray()).isEqualTo(solutionBytes);

            // SCORED + FULL_SOLUTION -> solutionAssetId = 200 with Cache-Control: private, no-store
            jdbc.update(
                    "UPDATE attempt.attempt SET state = 'SCORED', regrading = false, graded_at = now() WHERE id = ?",
                    attempt.getId());
            MvcResult scoredOk = downloadSolAsset(student, attempt.getId(), solAssetId);
            assertThat(scoredOk.getResponse().getStatus()).isEqualTo(200);
            assertThat(scoredOk.getResponse().getContentAsByteArray()).isEqualTo(solutionBytes);
            assertThat(scoredOk.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                    .isEqualTo("private, no-store");

            // Review endpoint rewrites data-asset-id in solution HTML to /solution-assets/
            MvcResult reviewRes = mockMvc.perform(
                            get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                    .with(AttemptTestFixtures.asExamUser(student, "learner")))
                    .andReturn();
            assertThat(reviewRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode reviewJson = objectMapper.readTree(reviewRes.getResponse().getContentAsString());
            boolean foundRewrittenSolUrl = false;
            for (JsonNode item : reviewJson.path("items")) {
                if (item.path("itemId").asText().equals(targetItem.getId().toString())) {
                    foundRewrittenSolUrl = item.path("solution").asText("")
                            .contains("/api/v1/me/attempts/" + attempt.getId() + "/solution-assets/" + solAssetId);
                }
            }
            assertThat(foundRewrittenSolUrl).isTrue();

            // Guide asset or stem-only asset MUST 404 on /solution-assets/
            assertThat(downloadSolAsset(student, attempt.getId(), guideAssetId).getResponse().getStatus())
                    .isEqualTo(404);
            assertThat(downloadSolAsset(student, attempt.getId(), stemAssetId).getResponse().getStatus())
                    .isEqualTo(404);

            // Solution-only asset stays 404 on /assets/
            assertThat(mockMvc.perform(
                                    get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", attempt.getId(), solAssetId)
                                            .with(AttemptTestFixtures.asExamUser(student, "learner")))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(404);

            // Out-of-scope item asset -> 404
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE id = ?", targetItem.getId());
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = true WHERE id = ?", targetItem.getId());

            // RIGHT_WRONG and SCORE gates MUST 404 even when SCORED (BS §15)
            jdbc.update("UPDATE attempt.attempt SET result_view = 'RIGHT_WRONG' WHERE id = ?", attempt.getId());
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);
            jdbc.update("UPDATE attempt.attempt SET result_view = 'SCORE' WHERE id = ?", attempt.getId());
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);

            // Restore FULL_SOLUTION & verify stranger 404, anon 401, MANAGE 403
            jdbc.update("UPDATE attempt.attempt SET result_view = 'FULL_SOLUTION' WHERE id = ?", attempt.getId());
            assertThat(downloadSolAsset(stranger, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);
            assertThat(mockMvc.perform(get("/api/v1/me/attempts/{id}/solution-assets/{assetId}", attempt.getId(), solAssetId))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(401);
            assertThat(mockMvc.perform(
                                    get("/api/v1/me/attempts/{id}/solution-assets/{assetId}", attempt.getId(), solAssetId)
                                            .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(403);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            for (UUID assetId : createdAssets) {
                jdbc.update("DELETE FROM questionbank.question_asset WHERE id = ?", assetId);
            }
        }
    }

    private MvcResult downloadSolAsset(UUID userId, UUID attemptId, UUID assetId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}", attemptId, assetId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    private UUID saveStoredAsset(
            UUID actor,
            QuestionAssetKind kind,
            String fileName,
            String mime,
            byte[] bytes,
            List<UUID> createdAssets) throws Exception {
        UUID id = UUID.randomUUID();
        String objectKey = "questions/at4-test/" + id + "-" + fileName;
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
                        8,
                        8,
                        actor,
                        Instant.now()));
        createdAssets.add(id);
        return id;
    }

    private UUID createReadyEssayQuestion(
            UUID adminId,
            String content,
            String solution,
            String gradingGuide,
            List<UUID> createdQuestions) throws Exception {
        SaveQuestionRequest req = new SaveQuestionRequest(
                QuestionType.ESSAY,
                "TOAN",
                "G12",
                "TH",
                null,
                null,
                BigDecimal.ONE,
                List.of(),
                List.of(),
                content,
                solution,
                List.of(),
                List.of(),
                new QuestionBody.EssayBody(QuestionType.ESSAY, gradingGuide, List.of(), null, 100),
                "AT4-SOL-" + UUID.randomUUID().toString().substring(0, 8));

        MvcResult created = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/questions")
                                .with(asUser(adminId, "q-admin-at4", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "at4-sol-cr-" + UUID.randomUUID())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isBetween(200, 201);
        UUID qId = UUID.fromString(json(created).path("summary").path("id").asText());
        createdQuestions.add(qId);

        MvcResult readyRes = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/questions/bulk/ready")
                                .with(asUser(adminId, "q-admin-at4", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "at4-sol-rd-" + UUID.randomUUID())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(qId)))))
                .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return qId;
    }
}
