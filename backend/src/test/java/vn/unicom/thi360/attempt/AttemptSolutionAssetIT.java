package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.storage.ObjectStorage;

class AttemptSolutionAssetIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private QuestionAssetRepository assetRepository;
    @Autowired private ObjectStorage objectStorage;
    @MockitoSpyBean private QuestionAssetContentPort questionAssetContentPort;

    @BeforeEach
    void drainOutboxAndResetSpy() {
        Mockito.reset(questionAssetContentPort);
        while (outboxRelay.relayBatch(500) > 0) {
            // drain backlog
        }
    }

    @AfterEach
    void resetSpy() {
        Mockito.reset(questionAssetContentPort);
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
            byte[] adminUrlSolBytes = "ADMIN-URL-SOL-BYTES".getBytes(StandardCharsets.UTF_8);
            byte[] v2OnlySolBytes = "V2-ONLY-SOL-BYTES".getBytes(StandardCharsets.UTF_8);
            byte[] guideBytes = "GUIDE-SECRET-BYTES".getBytes(StandardCharsets.UTF_8);

            UUID stemAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "stem.png", "image/png", stemBytes, createdAssets);
            UUID solAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "sol.png", "image/png", solutionBytes, createdAssets);
            UUID adminUrlAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "sol-admin.png", "image/png", adminUrlSolBytes, createdAssets);
            UUID v2SolAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "sol-v2.png", "image/png", v2OnlySolBytes, createdAssets);
            UUID guideAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "guide.png", "image/png", guideBytes, createdAssets);

            UUID essayQ = createReadyEssayQuestion(
                    adminId,
                    "<p>Stem <img data-asset-id=\"" + stemAssetId + "\"></p>",
                    "<p>Solution <img data-asset-id=\"" + solAssetId + "\"></p>",
                    "<p>Guide <img data-asset-id=\"" + guideAssetId + "\"></p>",
                    createdQuestions);

            // Include legacy absolute admin URL inside v1 solution HTML for rewrite & extraction test (R1-18/20)
            String legacyV1Sol = "<p>Solution <img data-asset-id=\"" + solAssetId + "\"> "
                    + "<img src=\"https://admin.thi360.example/api/v1/admin/question-assets/" + adminUrlAssetId + "/content\"></p>";
            jdbc.update(
                    "UPDATE questionbank.question_version SET snapshot = jsonb_set(snapshot, '{solution}', to_jsonb(?::text)) WHERE question_id = ? AND version_no = 1",
                    legacyV1Sol,
                    essayQ);

            // R1-20: create v2 version of essayQ referencing v2SolAssetId; v1-issued attempt MUST NOT serve v2 asset
            String v1SnapJson = jdbc.queryForObject(
                    "SELECT snapshot::text FROM questionbank.question_version WHERE question_id = ? AND version_no = 1",
                    String.class,
                    essayQ);
            ObjectNode v2Snap = (ObjectNode) objectMapper.readTree(v1SnapJson);
            v2Snap.put("solution", "<p>V2 Solution <img src=\"/api/v1/admin/question-assets/" + v2SolAssetId + "\"></p>");
            jdbc.update(
                    "INSERT INTO questionbank.question_version (question_id, version_no, snapshot) VALUES (?, 2, ?::jsonb)",
                    essayQ,
                    objectMapper.writeValueAsString(v2Snap));

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

            // Non-regrading PENDING_REVIEW -> MUST 404
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

            // R1-12/13/14: Verify ObjectStorage I/O executes OUTSIDE any DB transaction
            AtomicBoolean txActiveDuringLoad = new AtomicBoolean(true);
            Mockito.doAnswer(inv -> {
                txActiveDuringLoad.set(TransactionSynchronizationManager.isActualTransactionActive());
                return inv.callRealMethod();
            }).when(questionAssetContentPort).loadContent(any());

            // SCORED + FULL_SOLUTION -> 200 with Cache-Control: private, no-store
            jdbc.update(
                    "UPDATE attempt.attempt SET state = 'SCORED', regrading = false, graded_at = now() WHERE id = ?",
                    attempt.getId());
            MvcResult scoredOk = downloadSolAsset(student, attempt.getId(), solAssetId);
            assertThat(scoredOk.getResponse().getStatus()).isEqualTo(200);
            assertThat(scoredOk.getResponse().getContentAsByteArray()).isEqualTo(solutionBytes);
            assertThat(scoredOk.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                    .isEqualTo("private, no-store");
            assertThat(txActiveDuringLoad.get()).isFalse();

            // Admin URL asset inside solution HTML also serves 200
            MvcResult adminUrlOk = downloadSolAsset(student, attempt.getId(), adminUrlAssetId);
            assertThat(adminUrlOk.getResponse().getStatus()).isEqualTo(200);
            assertThat(adminUrlOk.getResponse().getContentAsByteArray()).isEqualTo(adminUrlSolBytes);

            // R1-20: v2-only solution asset returns 404 for v1-issued attempt
            assertThat(downloadSolAsset(student, attempt.getId(), v2SolAssetId).getResponse().getStatus())
                    .isEqualTo(404);

            // Review endpoint rewrites ALL admin & data-asset-id URLs, leaving zero /api/v1/admin/ substrings
            MvcResult reviewRes = mockMvc.perform(
                            get("/api/v1/me/attempts/{id}/review", attempt.getId())
                                    .with(AttemptTestFixtures.asExamUser(student, "learner")))
                    .andReturn();
            assertThat(reviewRes.getResponse().getStatus()).isEqualTo(200);
            String reviewBody = reviewRes.getResponse().getContentAsString();
            assertThat(reviewBody).doesNotContain("/api/v1/admin/");
            JsonNode reviewJson = objectMapper.readTree(reviewBody);
            String solHtml = "";
            for (JsonNode item : reviewJson.path("items")) {
                if (item.path("itemId").asText().equals(targetItem.getId().toString())) {
                    solHtml = item.path("solution").asText("");
                }
            }
            assertThat(solHtml)
                    .contains("/api/v1/me/attempts/" + attempt.getId() + "/solution-assets/" + solAssetId)
                    .contains("/api/v1/me/attempts/" + attempt.getId() + "/solution-assets/" + adminUrlAssetId);

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

            // R1-52: Out-of-scope parent QUESTION_SET blocks child solution asset (404)
            UUID syntheticParentId = Ids.newId();
            jdbc.update(
                    """
                            INSERT INTO attempt.attempt_item (
                                id, attempt_id, part_id, section_id, block_id, ord, unit_count,
                                question_id, question_version_no, question_type, points_max,
                                in_scope, answer_seq, flagged, version
                            )
                            VALUES (?, ?, ?, ?, ?, -1, 0, ?, 1, 'QUESTION_SET', 0, false, 0, false, 0)
                            """,
                    syntheticParentId,
                    attempt.getId(),
                    targetItem.getPartId(),
                    targetItem.getSectionId(),
                    targetItem.getBlockId(),
                    Ids.newId());
            jdbc.update("UPDATE attempt.attempt_item SET parent_item_id = ? WHERE id = ?", syntheticParentId, targetItem.getId());
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);
            jdbc.update("UPDATE attempt.attempt_item SET parent_item_id = NULL WHERE id = ?", targetItem.getId());
            jdbc.update("DELETE FROM attempt.attempt_item WHERE id = ?", syntheticParentId);

            // Out-of-scope item asset -> 404
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE id = ?", targetItem.getId());
            assertThat(downloadSolAsset(student, attempt.getId(), solAssetId).getResponse().getStatus())
                    .isEqualTo(404);
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = true WHERE id = ?", targetItem.getId());

            // Real EntitlementPort grants for SCORE_AND_ANSWERS and SCORE MUST 404 even when SCORED (R1-8, R1-9)
            for (String lowerView : List.of("SCORE_AND_ANSWERS", "SCORE")) {
                UUID lowerStudent = createStudent("sol-asset-" + lowerView);
                GrantOrderResult.Line lowerGrant = AttemptTestFixtures.grantExam(
                        grantService, lowerStudent, examId, 2, List.of(), lowerView, serving.version().versionNo());
                Attempt lowerAttempt = startAndAwaitOpen(
                        lowerStudent, UUID.randomUUID().toString(), lowerGrant.entitlementId(), null);
                AttemptItem lowerTarget = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(lowerAttempt.getId())
                        .stream()
                        .filter(i -> i.getQuestionType() != QuestionType.QUESTION_SET)
                        .findFirst()
                        .orElseThrow();
                jdbc.update(
                        "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1 WHERE id = ?",
                        essayQ,
                        lowerTarget.getId());
                UUID lowerSession = claimSession(lowerStudent, lowerAttempt.getId());
                performSubmit(lowerStudent, lowerAttempt.getId(), UUID.randomUUID().toString(), lowerSession, new SubmitAttemptRequest(List.of()));
                outboxRelay.relayBatch(500);
                jdbc.update("UPDATE attempt.attempt SET state = 'SCORED', graded_at = now() WHERE id = ?", lowerAttempt.getId());

                assertThat(downloadSolAsset(lowerStudent, lowerAttempt.getId(), solAssetId).getResponse().getStatus())
                        .as("resultView=" + lowerView + " must return 404 for solution assets")
                        .isEqualTo(404);
            }

            // Stranger 404, anon 401, MANAGE 403
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
            for (UUID qId : createdQuestions) {
                jdbc.update("DELETE FROM questionbank.question_version WHERE question_id = ? AND version_no = 2", qId);
            }
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
                        post("/api/v1/admin/questions")
                                .with(asUser(adminId, "q-admin-at4", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "at4-sol-cr-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isBetween(200, 201);
        UUID qId = UUID.fromString(json(created).path("summary").path("id").asText());
        createdQuestions.add(qId);

        MvcResult readyRes = mockMvc.perform(
                        post("/api/v1/admin/questions/bulk/ready")
                                .with(asUser(adminId, "q-admin-at4", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "at4-sol-rd-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(qId)))))
                .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return qId;
    }
}
