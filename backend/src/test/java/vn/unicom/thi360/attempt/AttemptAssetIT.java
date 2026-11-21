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
import vn.unicom.thi360.attempt.domain.PartState;
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
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', in_scope = true WHERE id = ?",
                    oosQ,
                    item.getId());

            MvcResult inScopeRes = performGetAsset(userId, attempt.getId(), oosAssetId);
            assertThat(inScopeRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(inScopeRes.getResponse().getContentAsByteArray())
                    .isEqualTo("OOS-BYTES".getBytes(StandardCharsets.UTF_8));

            jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE id = ?", item.getId());

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

    @Test
    void outOfBoundsRangeReturns416WithEmptyBody() throws Exception {
        UUID userId = createStudent("asset-416");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] promptBytes = "PNG-RANGE-BYTES".getBytes(StandardCharsets.UTF_8);
            UUID promptAssetId =
                    saveStoredAsset(
                            adminId, QuestionAssetKind.IMAGE, "range.png", "image/png", promptBytes, createdAssets);
            UUID qId =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>Range <img data-asset-id=\"" + promptAssetId + "\"></p>",
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
            Attempt attempt = startAndAwaitOpen(userId, "asset-416", line.entitlementId(), null);
            AttemptPart p0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).getFirst();
            AttemptItem item = attemptItemRepository.findByPartIdOrderByOrdAsc(p0.getId()).getFirst();
            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                    qId,
                    item.getId());

            MvcResult range416 =
                    mockMvc.perform(
                                    get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", attempt.getId(), promptAssetId)
                                            .header(HttpHeaders.RANGE, "bytes=999999-")
                                            .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                            .andReturn();
            assertThat(range416.getResponse().getStatus()).isEqualTo(416);
            assertThat(range416.getResponse().getContentAsByteArray()).isEmpty();
        } finally {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
            cleanupSyntheticAssets(createdAssets);
        }
    }

    @Test
    void fullAc16QuestionTypesVisibilityAndExclusions() throws Exception {
        UUID userId = createStudent("asset-ac16");
        UUID adminId = UUID.randomUUID();
        List<UUID> createdQuestions = new ArrayList<>();
        List<UUID> createdAssets = new ArrayList<>();

        try {
            byte[] setPassageBytes = "SET-PASSAGE".getBytes(StandardCharsets.UTF_8);
            byte[] childStemBytes = "CHILD-STEM".getBytes(StandardCharsets.UTF_8);
            byte[] childOptBytes = "CHILD-OPT".getBytes(StandardCharsets.UTF_8);
            byte[] childSolBytes = "CHILD-SOL-SECRET".getBytes(StandardCharsets.UTF_8);
            byte[] clozePassageBytes = "CLOZE-PASSAGE".getBytes(StandardCharsets.UTF_8);
            byte[] clozeAnsBytes = "CLOZE-ANS-SECRET".getBytes(StandardCharsets.UTF_8);
            byte[] tfStmtBytes = "TF-STMT".getBytes(StandardCharsets.UTF_8);
            byte[] matchLeftBytes = "MATCH-LEFT".getBytes(StandardCharsets.UTF_8);
            byte[] matchRightBytes = "MATCH-RIGHT".getBytes(StandardCharsets.UTF_8);
            byte[] rubricBytes = "RUBRIC-SECRET".getBytes(StandardCharsets.UTF_8);
            byte[] mistakeBytes = "MISTAKE-SECRET".getBytes(StandardCharsets.UTF_8);
            byte[] skippedBytes = "SKIPPED-PART-SECRET".getBytes(StandardCharsets.UTF_8);

            UUID setPassageId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "set-p.png", "image/png", setPassageBytes, createdAssets);
            UUID childStemId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "c-stem.png", "image/png", childStemBytes, createdAssets);
            UUID childOptId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "c-opt.png", "image/png", childOptBytes, createdAssets);
            UUID childSolId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "c-sol.png", "image/png", childSolBytes, createdAssets);
            UUID clozePassageId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "clz-p.png", "image/png", clozePassageBytes, createdAssets);
            UUID clozeAnsId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "clz-a.png", "image/png", clozeAnsBytes, createdAssets);
            UUID tfStmtId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "tf-s.png", "image/png", tfStmtBytes, createdAssets);
            UUID matchLeftId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "m-l.png", "image/png", matchLeftBytes, createdAssets);
            UUID matchRightId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "m-r.png", "image/png", matchRightBytes, createdAssets);
            UUID rubricId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "rubric.png", "image/png", rubricBytes, createdAssets);
            UUID mistakeId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "mistake.png", "image/png", mistakeBytes, createdAssets);
            UUID skippedId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "skipped.png", "image/png", skippedBytes, createdAssets);

            UUID setQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.QUESTION_SET,
                            new BigDecimal("2"),
                            "<p>Set stem</p>",
                            null,
                            List.of(),
                            List.of(),
                            new QuestionBody.QuestionSetBody(
                                    QuestionType.QUESTION_SET,
                                    "<p>Ngữ liệu <img data-asset-id=\"" + setPassageId + "\"></p>",
                                    1,
                                    List.of(
                                            new QuestionBody.ChildQuestion(
                                                    QuestionType.SINGLE_CHOICE,
                                                    "<p>Child 1 <img data-asset-id=\"" + childStemId + "\"></p>",
                                                    new QuestionBody.SingleChoiceBody(
                                                            QuestionType.SINGLE_CHOICE,
                                                            List.of(
                                                                    new QuestionBody.ChoiceOption(
                                                                            "c1a",
                                                                            "<p>Opt <img data-asset-id=\"" + childOptId + "\"></p>",
                                                                            true),
                                                                    new QuestionBody.ChoiceOption("c1b", "<p>B</p>", false)),
                                                            true),
                                                    "<p>Child sol <img data-asset-id=\"" + childSolId + "\"></p>",
                                                    BigDecimal.ONE,
                                                    null,
                                                    null,
                                                    List.of(),
                                                    null),
                                            new QuestionBody.ChildQuestion(
                                                    QuestionType.SHORT_ANSWER,
                                                    "<p>Child 2</p>",
                                                    new QuestionBody.ShortAnswerBody(
                                                            QuestionType.SHORT_ANSWER, List.of("9"), null, null),
                                                    null,
                                                    BigDecimal.ONE,
                                                    null,
                                                    null,
                                                    List.of(),
                                                    null))),
                            createdQuestions);
            UUID child1QId =
                    jdbc.queryForObject(
                            "SELECT id FROM questionbank.question WHERE parent_id = ? AND type = 'SINGLE_CHOICE'",
                            UUID.class,
                            setQ);
            UUID child2QId =
                    jdbc.queryForObject(
                            "SELECT id FROM questionbank.question WHERE parent_id = ? AND type = 'SHORT_ANSWER'",
                            UUID.class,
                            setQ);

            byte[] mixedCaseBytes = "MIXED-CASE-BYTES".getBytes(StandardCharsets.UTF_8);
            UUID mixedCaseId = saveStoredAsset(adminId, QuestionAssetKind.IMAGE, "mixed.png", "image/png", mixedCaseBytes, createdAssets);

            UUID clozeQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.CLOZE,
                            BigDecimal.ONE,
                            "<p>Cloze</p>",
                            null,
                            List.of(),
                            List.of(),
                            new QuestionBody.ClozeBody(
                                    QuestionType.CLOZE,
                                    "<p>Ngữ liệu <img data-asset-id=\"" + clozePassageId + "\"> [[b1]]</p>",
                                    List.of(
                                            new QuestionBody.ClozeBlank(
                                                    "b1",
                                                    List.of("ans <img data-asset-id=\"" + clozeAnsId + "\">"),
                                                    BigDecimal.ONE))),
                            createdQuestions);

            // R1-29: also test mixed-case admin asset URL without data-asset-id, with entity-encoded query separator
            UUID mixedCaseQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SHORT_ANSWER,
                            BigDecimal.ONE,
                            "<p>Mixed</p>",
                            null,
                            List.of(),
                            List.of(),
                            new QuestionBody.ShortAnswerBody(
                                    QuestionType.SHORT_ANSWER, List.of("42"), null, null),
                            createdQuestions);
            String legacyHtml =
                    "<p><img src=\"/API/V1/ADMIN/QUESTION-ASSETS/"
                            + mixedCaseId.toString().toUpperCase(java.util.Locale.ROOT)
                            + "?v=1&amp;w=2\"></p>";
            jdbc.update(
                    "UPDATE questionbank.question_version SET snapshot = jsonb_set(snapshot, '{content}', to_jsonb(?::text)) WHERE question_id = ?",
                    legacyHtml,
                    mixedCaseQ);

            UUID tfQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.TRUE_FALSE,
                            BigDecimal.ONE,
                            "<p>TF</p>",
                            null,
                            List.of(),
                            List.of(),
                            new QuestionBody.TrueFalseBody(
                                    QuestionType.TRUE_FALSE,
                                    List.of(
                                            new QuestionBody.TrueFalseStatement(
                                                    "s1",
                                                    "<p>Stmt <img data-asset-id=\"" + tfStmtId + "\"></p>",
                                                    true,
                                                    new BigDecimal("0.5")),
                                            new QuestionBody.TrueFalseStatement(
                                                    "s2", "<p>S2</p>", false, new BigDecimal("0.5"))),
                                    QuestionBody.TrueFalseScoringMethod.PER_STATEMENT,
                                    null),
                            createdQuestions);

            UUID matchQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.MATCHING,
                            BigDecimal.ONE,
                            "<p>Match</p>",
                            null,
                            List.of(),
                            List.of(),
                            new QuestionBody.MatchingBody(
                                    QuestionType.MATCHING,
                                    List.of(
                                            new QuestionBody.MatchingSide(
                                                    "l1", "<p>L1 <img data-asset-id=\"" + matchLeftId + "\"></p>"),
                                            new QuestionBody.MatchingSide("l2", "<p>L2</p>")),
                                    List.of(
                                            new QuestionBody.MatchingSide(
                                                    "r1", "<p>R1 <img data-asset-id=\"" + matchRightId + "\"></p>"),
                                            new QuestionBody.MatchingSide("r2", "<p>R2</p>")),
                                    List.of(
                                            new QuestionBody.MatchingPair("l1", "r1", new BigDecimal("0.5")),
                                            new QuestionBody.MatchingPair("l2", "r2", new BigDecimal("0.5"))),
                                    false),
                            createdQuestions);

            UUID essayQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.ESSAY,
                            BigDecimal.ONE,
                            "<p>Essay prompt</p>",
                            null,
                            List.of("<p>Mistake <img data-asset-id=\"" + mistakeId + "\"></p>"),
                            List.of(),
                            new QuestionBody.EssayBody(
                                    QuestionType.ESSAY,
                                    "<p>Guide</p>",
                                    List.of(
                                            new QuestionBody.EssayCriterion(
                                                    "c1",
                                                    "<p>Rubric <img data-asset-id=\"" + rubricId + "\"></p>",
                                                    BigDecimal.ONE)),
                                    null,
                                    200),
                            createdQuestions);

            UUID skippedQ =
                    createAndReadyQuestion(
                            adminId,
                            QuestionType.SINGLE_CHOICE,
                            BigDecimal.ONE,
                            "<p>Skipped <img data-asset-id=\"" + skippedId + "\"></p>",
                            null,
                            List.of(),
                            List.of(),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                            new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                                    true),
                            createdQuestions);

            // Use DS-BCA-FULL-MINI (SHARED, 3 parts) so Part 0 is OPEN and Parts 1..2 become SKIPPED when deadline expires
            GrantOrderResult.Line line = grant(userId, "DS-BCA-FULL-MINI", 2, List.of());
            Attempt attempt =
                    startAndAwaitOpen(
                            userId,
                            "ac16",
                            line.entitlementId(),
                            new vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput("CA2", null));
            List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
            AttemptPart p0 = parts.get(0);
            AttemptPart p1 = parts.get(1);

            jdbc.update("DELETE FROM attempt.attempt_item WHERE part_id = ?", p0.getId());
            UUID setItemId = vn.unicom.thi360.shared.ids.Ids.newId();
            UUID child1ItemId = vn.unicom.thi360.shared.ids.Ids.newId();
            UUID child2ItemId = vn.unicom.thi360.shared.ids.Ids.newId();
            attemptItemRepository.saveAllAndFlush(
                    List.of(
                            AttemptItem.create(setItemId, attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", null, 0, null, 0, setQ, 1, QuestionType.QUESTION_SET, new BigDecimal("2"), null),
                            AttemptItem.create(child1ItemId, attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", setItemId, 1, 1, 1, child1QId, 1, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, List.of("c1a", "c1b")),
                            AttemptItem.create(child2ItemId, attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", setItemId, 2, 2, 1, child2QId, 1, QuestionType.SHORT_ANSWER, BigDecimal.ONE, null),
                            AttemptItem.create(vn.unicom.thi360.shared.ids.Ids.newId(), attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", null, 3, 3, 1, clozeQ, 1, QuestionType.CLOZE, BigDecimal.ONE, null),
                            AttemptItem.create(vn.unicom.thi360.shared.ids.Ids.newId(), attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", null, 4, 4, 1, tfQ, 1, QuestionType.TRUE_FALSE, BigDecimal.ONE, null),
                            AttemptItem.create(vn.unicom.thi360.shared.ids.Ids.newId(), attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", null, 5, 5, 1, matchQ, 1, QuestionType.MATCHING, BigDecimal.ONE, List.of("r1", "r2")),
                            AttemptItem.create(vn.unicom.thi360.shared.ids.Ids.newId(), attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", null, 6, 6, 1, essayQ, 1, QuestionType.ESSAY, BigDecimal.ONE, null),
                            AttemptItem.create(vn.unicom.thi360.shared.ids.Ids.newId(), attempt.getId(), p0.getId(), null, "SEC", "TOAN", "B", null, 7, 7, 1, mixedCaseQ, 1, QuestionType.SHORT_ANSWER, BigDecimal.ONE, null)));
            AttemptItem p1First = attemptItemRepository.findByPartIdOrderByOrdAsc(p1.getId()).getFirst();
            jdbc.update(
                    "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, question_type = 'SINGLE_CHOICE', option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                    skippedQ,
                    p1First.getId());

            // 1 & 2. Passage, child stem/option, CLOZE passage (mixed-case URL), TF statement, MATCHING left/right -> 200 & rewritten /state URLs serve exact bytes
            MvcResult stateRes = performGetState(userId, attempt.getId());
            assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
            String stateRaw = stateRes.getResponse().getContentAsString();
            ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(stateRaw);

            for (var entry :
                    List.of(
                            java.util.Map.entry(setPassageId, setPassageBytes),
                            java.util.Map.entry(childStemId, childStemBytes),
                            java.util.Map.entry(childOptId, childOptBytes),
                            java.util.Map.entry(clozePassageId, clozePassageBytes),
                            java.util.Map.entry(tfStmtId, tfStmtBytes),
                            java.util.Map.entry(matchLeftId, matchLeftBytes),
                            java.util.Map.entry(matchRightId, matchRightBytes))) {
                String expectedUrl = "/api/v1/me/attempts/" + attempt.getId() + "/assets/" + entry.getKey();
                assertThat(stateRaw).contains(expectedUrl);
                MvcResult dl = performGetAsset(userId, attempt.getId(), entry.getKey());
                assertThat(dl.getResponse().getStatus()).isEqualTo(200);
                assertThat(dl.getResponse().getContentAsByteArray()).isEqualTo(entry.getValue());
            }
            String expectedMixedUrl = "/api/v1/me/attempts/" + attempt.getId() + "/assets/" + mixedCaseId;
            assertThat(stateRaw).containsIgnoringCase(expectedMixedUrl);
            MvcResult mixedDl = performGetAsset(userId, attempt.getId(), mixedCaseId);
            assertThat(mixedDl.getResponse().getStatus()).isEqualTo(200);
            assertThat(mixedDl.getResponse().getContentAsByteArray()).isEqualTo(mixedCaseBytes);

            // 4. Confidential fields (children[].solution, rubric, commonMistakes, blanks[].answers) -> 404
            for (UUID secretId : List.of(childSolId, rubricId, mistakeId, clozeAnsId)) {
                assertThat(performGetAsset(userId, attempt.getId(), secretId).getResponse().getStatus())
                        .isEqualTo(404);
            }

            // 3. QUESTION_SET parent/child in_scope visibility
            // Flip all children in_scope=false -> parent passage becomes 404
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE parent_item_id = ?", setItemId);
            assertThat(performGetAsset(userId, attempt.getId(), setPassageId).getResponse().getStatus()).isEqualTo(404);
            assertThat(performGetAsset(userId, attempt.getId(), childStemId).getResponse().getStatus()).isEqualTo(404);

            // Restore children in_scope=true and flip parent QUESTION_SET in_scope=false -> child assets become 404
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = true WHERE parent_item_id = ?", setItemId);
            jdbc.update("UPDATE attempt.attempt_item SET in_scope = false WHERE id = ?", setItemId);
            assertThat(performGetAsset(userId, attempt.getId(), setPassageId).getResponse().getStatus()).isEqualTo(404);
            assertThat(performGetAsset(userId, attempt.getId(), childStemId).getResponse().getStatus()).isEqualTo(404);
            assertThat(performGetAsset(userId, attempt.getId(), childOptId).getResponse().getStatus()).isEqualTo(404);

            // 5. Close SHARED attempt at deadline -> Part 1 becomes PartState.SKIPPED -> skipped asset returns 404
            mutableClock.setAt(attempt.getDeadlineAt().plusSeconds(5));
            assertThat(performGetState(userId, attempt.getId()).getResponse().getStatus()).isEqualTo(200);
            assertThat(attemptPartRepository.findById(p1.getId()).orElseThrow().getState())
                    .isEqualTo(PartState.SKIPPED);
            assertThat(performGetAsset(userId, attempt.getId(), skippedId).getResponse().getStatus())
                    .isEqualTo(404);
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
        return createAndReadyQuestion(
                adminId, type, points, content, solution, List.of(), attachmentIds, body, createdQuestions);
    }

    private UUID createAndReadyQuestion(
            UUID adminId,
            QuestionType type,
            BigDecimal points,
            String content,
            String solution,
            List<String> commonMistakes,
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
                        commonMistakes,
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
