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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for {@code GET /api/v1/me/attempts/{id}/assets/{assetId}}: allowed
 * student-visible fields (prompt/option/attachment), solution & essay-guide/sample exclusion,
 * unopened part gating, out-of-scope gating, cross-user & role isolation, no session header
 * required, and rewritten {@code /state} URLs serving exact bytes (AC-14, AC-15, AC-19).
 */
class AttemptAssetIT extends AbstractAttemptDsIT {

    @Autowired private QuestionAssetRepository assetRepository;
    @Autowired private ObjectStorage objectStorage;

    @Test
    void allowedAssetIsServed() throws Exception {
        UUID userId = createStudent("asset-ok");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] promptBytes = "PNG-PROMPT-BYTES-1".getBytes(StandardCharsets.UTF_8);
            byte[] optionBytes = "PNG-OPTION-BYTES-2".getBytes(StandardCharsets.UTF_8);
            byte[] attBytes = "%PDF-1.4\nAttachment".getBytes(StandardCharsets.UTF_8);

            UUID promptAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "prompt.png", "image/png", promptBytes, createdAssets);
            UUID optionAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "option.png", "image/png", optionBytes, createdAssets);
            UUID attAssetId = saveStoredAsset(adminId, QuestionAssetKind.ATTACHMENT, "tài-liệu.pdf", "application/pdf", attBytes, createdAssets);

            UUID qId =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>Prompt <img data-asset-id=\"" + promptAssetId + "\"></p>",
                            null,
                            List.of(attAssetId),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption(
                                                    "o1",
                                                    "<p>Opt 1 <img data-asset-id=\"" + optionAssetId + "\"></p>",
                                                    true),
                                            new QuestionBody.ChoiceOption("o2", "<p>Opt 2</p>", false)),
                                    true),
                            createdQuestions);

            GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "asset-ok", line.entitlementId(), null);
            List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            AttemptItem firstItem = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId()).getFirst();

            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                    qId,
                    firstItem.getId());

            // Prompt image served with inline disposition, no-store cache, nosniff, and NO X-Attempt-Session header
            MvcResult promptRes = performGetAsset(userId, attempt.getId(), promptAssetId);
            assertThat(promptRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(promptRes.getResponse().getContentType()).isEqualTo("image/png");
            assertThat(promptRes.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(promptRes.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
            assertThat(promptRes.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
            assertThat(promptRes.getResponse().getContentAsByteArray()).isEqualTo(promptBytes);

            // Option image served similarly
            MvcResult optionRes = performGetAsset(userId, attempt.getId(), optionAssetId);
            assertThat(optionRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(optionRes.getResponse().getContentType()).isEqualTo("image/png");
            assertThat(optionRes.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
            assertThat(optionRes.getResponse().getContentAsByteArray()).isEqualTo(optionBytes);

            // Attachment served with attachment disposition
            MvcResult attRes = performGetAsset(userId, attempt.getId(), attAssetId);
            assertThat(attRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(attRes.getResponse().getContentType()).isEqualTo("application/pdf");
            assertThat(attRes.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(attRes.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
            assertThat(attRes.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
            assertThat(attRes.getResponse().getContentAsByteArray()).isEqualTo(attBytes);

            // Closing the part (and submitting/grading) still keeps opened/closed part assets readable
            UUID sid = claimSession(userId, attempt.getId());
            assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sid, null)
                    .getResponse().getStatus()).isEqualTo(200);
            assertThat(performGetAsset(userId, attempt.getId(), promptAssetId).getResponse().getStatus()).isEqualTo(200);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            cleanupSyntheticAssets(createdAssets);
        }
    }

    @Test
    void solutionAssetIs404() throws Exception {
        UUID userId = createStudent("asset-sol");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] promptBytes = "PROMPT-1".getBytes(StandardCharsets.UTF_8);
            byte[] solBytes = "SECRET-SOLUTION-IMG".getBytes(StandardCharsets.UTF_8);
            byte[] guideBytes = "SECRET-GUIDE-IMG".getBytes(StandardCharsets.UTF_8);
            byte[] sampleBytes = "SECRET-SAMPLE-IMG".getBytes(StandardCharsets.UTF_8);

            UUID promptAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "p.png", "image/png", promptBytes, createdAssets);
            UUID solAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "sol.png", "image/png", solBytes, createdAssets);
            UUID guideAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "guide.png", "image/png", guideBytes, createdAssets);
            UUID sampleAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "sample.png", "image/png", sampleBytes, createdAssets);

            UUID scQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>Visible <img data-asset-id=\"" + promptAssetId + "\"></p>",
                            "<p>Secret <img data-asset-id=\"" + solAssetId + "\"></p>",
                            List.of(),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                            new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                                    true),
                            createdQuestions);

            UUID essayQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.ESSAY,
                            BigDecimal.ONE,
                            "<p>Write essay</p>",
                            null,
                            List.of(),
                            new QuestionBody.EssayBody(
                                    QuestionType.ESSAY,
                                    "<p>Guide <img data-asset-id=\"" + guideAssetId + "\"></p>",
                                    List.of(),
                                    "<p>Sample <img data-asset-id=\"" + sampleAssetId + "\"></p>",
                                    200),
                            createdQuestions);

            GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "sol-404", line.entitlementId(), null);
            AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
            List<AttemptItem> items = attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId());

            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                    scQ,
                    items.get(0).getId());
            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'ESSAY', option_order = NULL WHERE id = ?",
                    essayQ,
                    items.get(1).getId());

            assertThat(performGetAsset(userId, attempt.getId(), promptAssetId).getResponse().getStatus()).isEqualTo(200);

            for (UUID confidentialId : List.of(solAssetId, guideAssetId, sampleAssetId, UUID.randomUUID())) {
                MvcResult secretRes = performGetAsset(userId, attempt.getId(), confidentialId);
                assertThat(secretRes.getResponse().getStatus()).isEqualTo(404);
                assertThat(json(secretRes).path("code").asText()).isEqualTo("NOT_FOUND");
            }
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            cleanupSyntheticAssets(createdAssets);
        }
    }

    @Test
    void unopenedPartAssetIs404() throws Exception {
        UUID userId = createStudent("asset-unopened");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] part1Bytes = "PART-1-IMG".getBytes(StandardCharsets.UTF_8);
            UUID part1AssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "p1.png", "image/png", part1Bytes, createdAssets);

            UUID p1Q =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>Part 1 img <img data-asset-id=\"" + part1AssetId + "\"></p>",
                            null,
                            List.of(),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                            new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                                    true),
                            createdQuestions);

            GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "unopened-part", line.entitlementId(), null);
            UUID sid = claimSession(userId, attempt.getId());
            List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            AttemptItem part1Item = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(1).getId()).getFirst();

            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                    p1Q,
                    part1Item.getId());

            // While Part 0 is OPEN and Part 1 is UNOPENED -> 404
            MvcResult blocked = performGetAsset(userId, attempt.getId(), part1AssetId);
            assertThat(blocked.getResponse().getStatus()).isEqualTo(404);
            assertThat(json(blocked).path("code").asText()).isEqualTo("NOT_FOUND");

            // After finishing Part 0 -> Part 1 becomes OPEN -> 200
            assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), sid, null)
                    .getResponse().getStatus()).isEqualTo(200);
            MvcResult opened = performGetAsset(userId, attempt.getId(), part1AssetId);
            assertThat(opened.getResponse().getStatus()).isEqualTo(200);
            assertThat(opened.getResponse().getContentAsByteArray()).isEqualTo(part1Bytes);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            cleanupSyntheticAssets(createdAssets);
        }
    }

    @Test
    void otherUserAssetIs404() throws Exception {
        UUID owner = createStudent("asset-owner");
        UUID stranger = createStudent("asset-stranger");
        UUID manager = createManager("asset-mgr");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            UUID assetId =
                    saveStoredAsset(
                            adminId,
                            QuestionAssetKind.IMAGE,
                            "own.png",
                            "image/png",
                            "OWNER-BYTES".getBytes(StandardCharsets.UTF_8),
                            createdAssets);
            UUID qId =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>Owner <img data-asset-id=\"" + assetId + "\"></p>",
                            null,
                            List.of(),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                            new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                                    true),
                            createdQuestions);

            GrantOrderResult.Line line = grant(owner, "DS-HSA-PART-MATH-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(owner, "other-user", line.entitlementId(), null);
            AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
            AttemptItem item = attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId()).getFirst();
            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                    qId,
                    item.getId());

            // Unauthenticated -> 401
            MvcResult unauth =
                    mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", attempt.getId(), assetId))
                            .andReturn();
            assertThat(unauth.getResponse().getStatus()).isEqualTo(401);

            // Non-learner role -> 403
            MvcResult forbidden =
                    mockMvc.perform(
                                    get(
                                                    "/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                                                    attempt.getId(),
                                                    assetId)
                                            .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                            .andReturn();
            assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
            assertThat(json(forbidden).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

            // Another student -> 404
            MvcResult strRes = performGetAsset(stranger, attempt.getId(), assetId);
            assertThat(strRes.getResponse().getStatus()).isEqualTo(404);
            assertThat(json(strRes).path("code").asText()).isEqualTo("NOT_FOUND");
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            cleanupSyntheticAssets(createdAssets);
        }
    }

    @Test
    void outOfScopeItemAssetIs404() throws Exception {
        UUID userId = createStudent("asset-oos");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            UUID oosAssetId =
                    saveStoredAsset(
                            adminId,
                            QuestionAssetKind.IMAGE,
                            "oos.png",
                            "image/png",
                            "OOS-BYTES".getBytes(StandardCharsets.UTF_8),
                            createdAssets);
            UUID oosQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>OOS <img data-asset-id=\"" + oosAssetId + "\"></p>",
                            null,
                            List.of(),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                            new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                                    true),
                            createdQuestions);

            GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "oos-asset", line.entitlementId(), null);
            AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
            AttemptItem item = attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId()).getFirst();

            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', in_scope = false WHERE id = ?",
                    oosQ,
                    item.getId());

            MvcResult oosRes = performGetAsset(userId, attempt.getId(), oosAssetId);
            assertThat(oosRes.getResponse().getStatus()).isEqualTo(404);
            assertThat(json(oosRes).path("code").asText()).isEqualTo("NOT_FOUND");
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            cleanupSyntheticAssets(createdAssets);
        }
    }

    @Test
    void stateImageSourceIsServed() throws Exception {
        UUID userId = createStudent("asset-state-url");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] stemBytes = "STEM-IMAGE-BYTES".getBytes(StandardCharsets.UTF_8);
            byte[] optBytes = "OPTION-IMAGE-BYTES".getBytes(StandardCharsets.UTF_8);
            UUID stemAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "stem.png", "image/png", stemBytes, createdAssets);
            UUID optAssetId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "opt.png", "image/png", optBytes, createdAssets);

            UUID imgQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>Stem <img data-asset-id=\"" + stemAssetId + "\"></p>",
                            null,
                            List.of(),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption(
                                                    "o1",
                                                    "<p>Option 1 <img data-asset-id=\"" + optAssetId + "\"></p>",
                                                    true),
                                            new QuestionBody.ChoiceOption("o2", "<p>Option 2</p>", false)),
                                    true),
                            createdQuestions);

            GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
            Attempt attempt = startAndAwaitOpen(userId, "state-img", line.entitlementId(), null);
            AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
            AttemptItem item = attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId()).getFirst();

            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                    imgQ,
                    item.getId());

            MvcResult stateRes = performGetState(userId, attempt.getId());
            assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
            String rawState = stateRes.getResponse().getContentAsString();
            ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(rawState);

            JsonNode firstItemJson = objectMapper.readTree(rawState).path("parts").get(0).path("items").get(0);
            String expectedStemUrl = "/api/v1/me/attempts/" + attempt.getId() + "/assets/" + stemAssetId;
            String expectedOptUrl = "/api/v1/me/attempts/" + attempt.getId() + "/assets/" + optAssetId;

            assertThat(firstItemJson.path("content").asText()).contains(expectedStemUrl);
            assertThat(firstItemJson.path("questionBody").path("options").get(0).path("content").asText())
                    .contains(expectedOptUrl);

            MvcResult stemDownload =
                    mockMvc.perform(get(expectedStemUrl).with(AttemptTestFixtures.asExamUser(userId, "learner")))
                            .andReturn();
            assertThat(stemDownload.getResponse().getStatus()).isEqualTo(200);
            assertThat(stemDownload.getResponse().getContentAsByteArray()).isEqualTo(stemBytes);

            MvcResult optDownload =
                    mockMvc.perform(get(expectedOptUrl).with(AttemptTestFixtures.asExamUser(userId, "learner")))
                            .andReturn();
            assertThat(optDownload.getResponse().getStatus()).isEqualTo(200);
            assertThat(optDownload.getResponse().getContentAsByteArray()).isEqualTo(optBytes);
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            cleanupSyntheticAssets(createdAssets);
        }
    }

    private MvcResult performGetAsset(UUID userId, UUID attemptId, UUID assetId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", attemptId, assetId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    private UUID saveStoredAsset(
            UUID actor,
            QuestionAssetKind kind,
            String fileName,
            String mime,
            byte[] bytes,
            List<UUID> createdAssets)
            throws Exception {
        UUID id = UUID.randomUUID();
        String objectKey = "questions/at3-test/" + id + "-" + fileName;
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

    private UUID createAndReadyQuestion(
            UUID adminId,
            QuestionType type,
            BigDecimal points,
            String content,
            String solution,
            List<UUID> attachmentIds,
            QuestionBody body,
            List<UUID> createdQuestions)
            throws Exception {
        SaveQuestionRequest req =
                new SaveQuestionRequest(
                        type,
                        "TOAN",
                        "G12",
                        "TH",
                        null,
                        null,
                        points,
                        List.of(),
                        List.of(),
                        content,
                        solution,
                        List.of(),
                        attachmentIds,
                        body,
                        "AT3-AST-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult createRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "at3-ast-cr-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
        assertThat(createRes.getResponse().getStatus()).isBetween(200, 201);
        UUID id = UUID.fromString(json(createRes).path("summary").path("id").asText());
        createdQuestions.add(id);

        MvcResult readyRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions/bulk/ready")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "at3-ast-rd-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(id)))))
                        .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return id;
    }

    private void cleanupSyntheticAssets(List<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return;
        }
        for (UUID id : assetIds) {
            jdbc.update("DELETE FROM questionbank.question_asset WHERE id = ?", id);
        }
    }
}
