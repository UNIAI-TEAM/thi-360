package vn.unicom.thi360.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.api.AttemptAudioController.AudioProgressRequest;
import vn.unicom.thi360.attempt.api.AttemptAudioController.StartAudioPlayRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
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
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * AC-9: Cross-tenant IDOR verification across all {@code /api/v1/me/**} endpoints and collections.
 * Every Alice resource returns 200/202 for Alice and strictly 404 (never 200 or 403) without data
 * leakage for Bob.
 */
class MeApiIdorIT extends AbstractIntegrationTest {

    private static final String ALICE_SENTINEL_ANSWER = "ALICE_PRIVATE_SHORT_ANSWER_991";

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;
    @Autowired private AttemptItemRepository attemptItemRepository;
    @Autowired private AttemptAudioPlayRepository attemptAudioPlayRepository;
    @Autowired private QuestionAssetRepository questionAssetRepository;
    @Autowired private ObjectStorage objectStorage;
    @Autowired private OutboxRelay outboxRelay;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdQuestions = new ArrayList<>();
    private final List<UUID> createdAssets = new ArrayList<>();
    private final List<String> createdObjectKeys = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        AttemptTestFixtures.ensureDsBuilt(context);
        Integer projectedCount = jdbc.queryForObject(
                "SELECT count(*) FROM commerce.storefront_product WHERE code LIKE 'DS-%'", Integer.class);
        if (projectedCount == null || projectedCount < 10) {
            mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(asPlatformAdmin()))
                    .andExpect(status().isOk());
        }
        while (outboxRelay.relayBatch(500) > 0) {
            // drain backlog
        }
    }

    @AfterEach
    void tearDown() {
        try {
            for (UUID orderId : createdOrderIds) {
                CommerceTestFixtures.deleteStubOrder(jdbc, orderId);
            }
            createdOrderIds.clear();
        } finally {
            try {
                for (UUID userId : createdUserIds) {
                    jdbc.update(
                            "DELETE FROM attempt.attempt_audio_play WHERE attempt_id IN (SELECT id FROM attempt.attempt WHERE user_id = ?)",
                            userId);
                    AttemptTestFixtures.cleanupUser(jdbc, userId);
                }
                createdUserIds.clear();
            } finally {
                try {
                    AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestions);
                    createdQuestions.clear();
                } finally {
                    try {
                        for (UUID assetId : createdAssets) {
                            jdbc.update("DELETE FROM questionbank.question_asset WHERE id = ?", assetId);
                        }
                        createdAssets.clear();
                    } finally {
                        try {
                            for (String key : createdObjectKeys) {
                                objectStorage.delete(key);
                            }
                            createdObjectKeys.clear();
                        } finally {
                            jdbc.update("DELETE FROM shared.idempotency_key WHERE key LIKE 'qa-idor-%'");
                        }
                    }
                }
            }
        }
    }

    @Test
    void bobReceives404AndZeroLeaksAcrossEveryAliceResourceAndCollection() throws Exception {
        Student alice = createVerifiedStudent("alice");
        Student bob = createVerifiedStudent("bob");

        // 1. Alice fulfilled order + entitlement on DS-HSA-FULL-MINI (FULL_SOLUTION)
        UUID hsaFullMiniId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-FULL-MINI");
        var hsaServing = publishedExamPort.findServing(hsaFullMiniId).orElseThrow();
        UUID aliceOrderId = Ids.newId();
        String aliceOrderCode = "DH261122" + aliceOrderId.toString().replace("-", "").substring(0, 6).toUpperCase();
        CommerceTestFixtures.insertMinimalStubOrder(
                jdbc, aliceOrderId, alice.id(), hsaFullMiniId, aliceOrderCode, OrderState.FULFILLED);
        jdbc.update(
                "UPDATE commerce.purchase_order SET buyer_username = ?, buyer_full_name = ? WHERE id = ?",
                alice.username(),
                alice.fullName(),
                aliceOrderId);
        createdOrderIds.add(aliceOrderId);

        GrantOrderResult.Line aliceGrant = grantService.grantForOrder(new GrantOrderCommand(
                        aliceOrderId,
                        aliceOrderCode,
                        alice.id(),
                        List.of(new GrantLine(
                                hsaFullMiniId,
                                hsaServing.version().versionNo(),
                                5,
                                List.of(),
                                Map.of("aiExplanation", true),
                                "FULL_SOLUTION"))))
                .lines()
                .getFirst();
        UUID aliceEntitlementId = aliceGrant.entitlementId();

        // 2. Alice pending order via real POST /api/v1/me/orders
        String pendingExamCode = "DS-HSA-PART-MATH-MINI";
        String productRaw = mockMvc.perform(get("/api/v1/public/storefront/products/{code}", pendingExamCode)
                        .with(req -> {
                            req.setRemoteAddr("203.0.113.225");
                            return req;
                        }))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode productNode = objectMapper.readTree(productRaw);

        String alicePendingRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(asStudent(alice))
                        .header("Idempotency-Key", "idor-pend-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", pendingExamCode,
                                "expectedAccountId", alice.id(),
                                "expectedVersionNo", productNode.path("versionNo").asInt(),
                                "expectedAmountDue", productNode.path("price").asLong()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode pendingOrderNode = objectMapper.readTree(alicePendingRaw).path("order");
        UUID alicePendingOrderId = UUID.fromString(pendingOrderNode.path("id").asText());
        createdOrderIds.add(alicePendingOrderId);
        String alicePendingOrderCode = pendingOrderNode.path("code").asText();

        // 3. Create synthetic image asset (prompt + solution) so /assets and /solution-assets are non-vacuous
        byte[] promptBytes = "ALICE-PROMPT-PNG".getBytes(StandardCharsets.UTF_8);
        byte[] solutionBytes = "ALICE-SOLUTION-PNG".getBytes(StandardCharsets.UTF_8);
        UUID promptAssetId = saveAsset("alice-prompt.png", "image/png", promptBytes);
        UUID solutionAssetId = saveAsset("alice-sol.png", "image/png", solutionBytes);
        UUID richQuestionId = createReadyQuestionWithAssets(promptAssetId, solutionAssetId);

        // 4. Alice SCORED attempt FIRST (so DS-HSA-FULL-MINI has no open attempt when starting step 5)
        Attempt aliceScoredAttempt = startAttempt(alice, aliceEntitlementId, null);
        UUID aliceScoredAttemptId = aliceScoredAttempt.getId();
        UUID scoredSessionId = claimSession(alice, aliceScoredAttemptId, null, false);
        List<AttemptPart> scoredParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(aliceScoredAttemptId);
        AttemptItem scoredFirstItem = attemptItemRepository.findByPartIdOrderByOrdAsc(scoredParts.get(0).getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        jdbc.update(
                "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                richQuestionId,
                scoredFirstItem.getId());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                        aliceScoredAttemptId, scoredParts.get(0).getId())
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", scoredSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                        aliceScoredAttemptId, scoredParts.get(1).getId())
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", scoredSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/choice",
                        aliceScoredAttemptId, scoredParts.get(2).getId())
                        .with(asStudent(alice))
                        .header("Idempotency-Key", "idor-sc-ch-" + UUID.randomUUID())
                        .header("X-Attempt-Session", scoredSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPartChoiceRequest("ANH", null))))
                .andExpect(status().isAccepted());
        awaitPartState(scoredParts.get(2).getId(), PartState.OPEN);
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/submit", aliceScoredAttemptId)
                        .with(asStudent(alice))
                        .header("Idempotency-Key", "idor-sub-scored-" + UUID.randomUUID())
                        .header("X-Attempt-Session", scoredSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of()))))
                .andExpect(status().isOk());
        outboxRelay.relayBatch(500);

        // Verify Alice's scored attempt endpoints succeed (200)
        mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/result", aliceScoredAttemptId).with(asStudent(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/review", aliceScoredAttemptId).with(asStudent(alice)))
                .andExpect(status().isOk());
        assertThat(mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}",
                        aliceScoredAttemptId, solutionAssetId)
                        .with(asStudent(alice)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .isEqualTo(solutionBytes);

        // 5. Alice active IN_PROGRESS attempt (DS-HSA-FULL-MINI)
        Attempt aliceActiveAttempt = startAttempt(alice, aliceEntitlementId, null);
        UUID aliceActiveAttemptId = aliceActiveAttempt.getId();
        UUID aliceSessionId = claimSession(alice, aliceActiveAttemptId, null, false);
        List<AttemptPart> activeParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(aliceActiveAttemptId);
        AttemptPart openPart = activeParts.get(0);
        AttemptPart secondPart = activeParts.get(1);
        AttemptPart choicePart = activeParts.get(2);

        List<AttemptItem> part0Items = attemptItemRepository.findByPartIdOrderByOrdAsc(openPart.getId());
        AttemptItem assetTargetItem = part0Items.stream()
                .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        AttemptItem shortAnswerItem = part0Items.stream()
                .filter(i -> i.getQuestionType() == QuestionType.SHORT_ANSWER)
                .findFirst()
                .orElseThrow();
        jdbc.update(
                "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                richQuestionId,
                assetTargetItem.getId());

        // Alice saves private answer & fetches her asset (200)
        mockMvc.perform(put("/api/v1/me/attempts/{attemptId}/answers", aliceActiveAttemptId)
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAnswersRequest(
                                List.of(new AnswerChange(
                                        shortAnswerItem.getId(), 1L, new AnswerValue.ShortAnswer(ALICE_SENTINEL_ANSWER))),
                                null,
                                null))))
                .andExpect(status().isOk());
        assertThat(mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                        aliceActiveAttemptId, promptAssetId)
                        .with(asStudent(alice)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .isEqualTo(promptBytes);

        // 6. Alice audio attempt (DS-HSCA-ANH-MINI) with real active play
        UUID hscaAnhId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSCA-ANH-MINI");
        var anhServing = publishedExamPort.findServing(hscaAnhId).orElseThrow();
        AttemptTestFixtures.bindJdbc(jdbc);
        GrantOrderResult.Line anhGrant = AttemptTestFixtures.grantExam(
                grantService, alice.id(), hscaAnhId, 2, List.of(), "FULL_SOLUTION", anhServing.version().versionNo());
        Attempt aliceAudioAttempt = startAttempt(alice, anhGrant.entitlementId(), null);
        UUID aliceAudioAttemptId = aliceAudioAttempt.getId();
        UUID aliceAudioSessionId = claimSession(alice, aliceAudioAttemptId, null, false);
        AttemptAudioPlay audioPlayRow = attemptAudioPlayRepository
                .findByAttemptIdOrderByAssetIdAsc(aliceAudioAttemptId)
                .getFirst();
        UUID aliceAudioAssetId = audioPlayRow.getAssetId();

        String openPlayRaw = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays",
                        aliceAudioAttemptId, aliceAudioAssetId)
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceAudioSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAudioPlayRequest("alice-req-1"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        UUID alicePlayId = UUID.fromString(objectMapper.readTree(openPlayRaw).path("playId").asText());

        // ================= IDOR ASSERTIONS: Bob calling every Alice /api/v1/me/** endpoint -> 404 =================
        List<String> forbiddenSubstrings = List.of(
                aliceOrderCode,
                alicePendingOrderCode,
                alice.username(),
                alice.fullName(),
                ALICE_SENTINEL_ANSWER,
                aliceActiveAttemptId.toString(),
                aliceScoredAttemptId.toString(),
                aliceAudioAttemptId.toString(),
                aliceEntitlementId.toString(),
                anhGrant.entitlementId().toString(),
                aliceAudioAssetId.toString(),
                alicePlayId.toString(),
                promptAssetId.toString(),
                solutionAssetId.toString(),
                scoredSessionId.toString(),
                aliceSessionId.toString(),
                aliceAudioSessionId.toString());

        // Parity baselines against random nonexistent IDs/codes
        String randomOrder404Code = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/me/orders/{code}", "DH000000000000").with(asStudent(bob)))
                                .andExpect(status().isNotFound())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8))
                .path("code").asText();
        assertThat(randomOrder404Code).isEqualTo("ORDER_NOT_FOUND");
        String randomAttempt404Code = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/me/attempts/{attemptId}", UUID.randomUUID()).with(asStudent(bob)))
                                .andExpect(status().isNotFound())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8))
                .path("code").asText();
        assertThat(randomAttempt404Code).isEqualTo("NOT_FOUND");

        // Orders
        assertBobGets404NoLeak(get("/api/v1/me/orders/{code}", aliceOrderCode), bob, randomOrder404Code, forbiddenSubstrings);
        assertBobGets404NoLeak(get("/api/v1/me/orders/{code}", alicePendingOrderCode), bob, randomOrder404Code, forbiddenSubstrings);
        assertBobGets404NoLeak(post("/api/v1/me/orders/{code}/pay", alicePendingOrderCode), bob, randomOrder404Code, forbiddenSubstrings);
        assertBobGets404NoLeak(post("/api/v1/me/orders/{code}/cancel", alicePendingOrderCode), bob, randomOrder404Code, forbiddenSubstrings);

        // Entitlement prepare & start
        assertBobGets404NoLeak(
                get("/api/v1/me/entitlements/{entitlementId}/prepare", aliceEntitlementId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts")
                        .header("Idempotency-Key", "bob-st-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAttemptRequest(aliceEntitlementId, null))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Active attempt read, save answers, session takeover & heartbeat
        assertBobGets404NoLeak(get("/api/v1/me/attempts/{attemptId}", aliceActiveAttemptId), bob, randomAttempt404Code, forbiddenSubstrings);
        assertBobGets404NoLeak(
                put("/api/v1/me/attempts/{attemptId}/answers", aliceActiveAttemptId)
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAnswersRequest(
                                List.of(new AnswerChange(
                                        shortAnswerItem.getId(), 99L, new AnswerValue.ShortAnswer("bob-tamper"))),
                                null,
                                null))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/session", aliceActiveAttemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClaimSessionRequest(UUID.randomUUID(), true))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/heartbeat", aliceActiveAttemptId)
                        .header("X-Attempt-Session", aliceSessionId.toString()),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Part finish (while openPart is OPEN)
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", aliceActiveAttemptId, openPart.getId())
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Advance Alice's active attempt so choicePart enters AWAITING_CHOICE, then check Bob -> 404 on /choice & /retry
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                        aliceActiveAttemptId, openPart.getId())
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                        aliceActiveAttemptId, secondPart.getId())
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andExpect(status().isOk());

        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/choice",
                        aliceActiveAttemptId, choicePart.getId())
                        .header("Idempotency-Key", "bob-ch-" + UUID.randomUUID())
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPartChoiceRequest("ANH", null))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/retry",
                        aliceActiveAttemptId, choicePart.getId())
                        .header("Idempotency-Key", "bob-ret-" + UUID.randomUUID())
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetryPartRequest("bob-retry"))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Submit, result, review, assets (including audio asset), solution-assets
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/submit", aliceActiveAttemptId)
                        .header("Idempotency-Key", "bob-sub-" + UUID.randomUUID())
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of()))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/result", aliceScoredAttemptId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/review", aliceScoredAttemptId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", aliceActiveAttemptId, promptAssetId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", aliceAudioAttemptId, aliceAudioAssetId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", aliceAudioAttemptId, aliceAudioAssetId)
                        .param("playId", alicePlayId.toString()),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}",
                        aliceScoredAttemptId, solutionAssetId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Audio endpoints (plays, resume, progress, end)
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays",
                        aliceAudioAttemptId, aliceAudioAssetId)
                        .header("X-Attempt-Session", aliceAudioSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAudioPlayRequest("bob-req"))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume",
                        aliceAudioAttemptId, aliceAudioAssetId, alicePlayId)
                        .header("X-Attempt-Session", aliceAudioSessionId.toString()),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress",
                        aliceAudioAttemptId, aliceAudioAssetId, alicePlayId)
                        .header("X-Attempt-Session", aliceAudioSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AudioProgressRequest(1_000L))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end",
                        aliceAudioAttemptId, aliceAudioAssetId, alicePlayId)
                        .header("X-Attempt-Session", aliceAudioSessionId.toString()),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Verify post-attack integrity of Alice's active attempt: ALICE_SENTINEL_ANSWER & aliceSessionId remained intact
        assertAliceAttemptIntegrity(alice, aliceActiveAttemptId, shortAnswerItem.getId(), aliceSessionId);

        // Prove Alice's own calls on those exact pending order, choice, audio, heartbeat, and takeover resources succeed
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/heartbeat", aliceActiveAttemptId)
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceSessionId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/orders/{code}/pay", alicePendingOrderCode).with(asStudent(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/choice",
                        aliceActiveAttemptId, choicePart.getId())
                        .with(asStudent(alice))
                        .header("Idempotency-Key", "alice-ch-" + UUID.randomUUID())
                        .header("X-Attempt-Session", aliceSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPartChoiceRequest("ANH", null))))
                .andExpect(status().isAccepted());
        awaitPartState(choicePart.getId(), PartState.OPEN);
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume",
                        aliceAudioAttemptId, aliceAudioAssetId, alicePlayId)
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceAudioSessionId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress",
                        aliceAudioAttemptId, aliceAudioAssetId, alicePlayId)
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceAudioSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AudioProgressRequest(0L))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end",
                        aliceAudioAttemptId, aliceAudioAssetId, alicePlayId)
                        .with(asStudent(alice))
                        .header("X-Attempt-Session", aliceAudioSessionId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/session", aliceActiveAttemptId)
                        .with(asStudent(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClaimSessionRequest(UUID.randomUUID(), true))))
                .andExpect(status().isOk());

        // ================= COLLECTION ISOLATION: Bob vs Alice =================
        String aliceOrdersRaw = mockMvc.perform(get("/api/v1/me/orders").with(asStudent(alice)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(aliceOrdersRaw).contains(aliceOrderCode, alicePendingOrderCode);

        String bobOrdersRaw = mockMvc.perform(get("/api/v1/me/orders").with(asStudent(bob)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(bobOrdersRaw).path("items")).isEmpty();
        assertThat(bobOrdersRaw).doesNotContain(aliceOrderCode, alicePendingOrderCode, alice.username());

        String aliceExamsRaw = mockMvc.perform(get("/api/v1/me/exams").with(asStudent(alice)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(aliceExamsRaw).contains(aliceEntitlementId.toString());

        String bobExamsRaw = mockMvc.perform(get("/api/v1/me/exams").with(asStudent(bob)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(bobExamsRaw).path("items")).isEmpty();
        assertThat(bobExamsRaw).doesNotContain(aliceEntitlementId.toString(), aliceOrderCode);

        String aliceAttemptsRaw = mockMvc.perform(get("/api/v1/me/attempts").with(asStudent(alice)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(aliceAttemptsRaw).contains(
                aliceActiveAttemptId.toString(), aliceScoredAttemptId.toString(), aliceAudioAttemptId.toString());

        String bobAttemptsRaw = mockMvc.perform(get("/api/v1/me/attempts").with(asStudent(bob)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(bobAttemptsRaw).path("items")).isEmpty();
        assertThat(bobAttemptsRaw).doesNotContain(
                aliceActiveAttemptId.toString(), aliceScoredAttemptId.toString(), aliceAudioAttemptId.toString());

        String accessQueryBody = "{\"codes\":[\"DS-HSA-FULL-MINI\"]}";
        String aliceAccessRaw = mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(asStudent(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessQueryBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String bobAccessRaw = mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(asStudent(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessQueryBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(bobAccessRaw).path("items").get(0).path("state").asText())
                .isNotEqualTo(objectMapper.readTree(aliceAccessRaw).path("items").get(0).path("state").asText());
        assertThat(bobAccessRaw).doesNotContain(
                aliceEntitlementId.toString(),
                aliceActiveAttemptId.toString(),
                aliceScoredAttemptId.toString(),
                aliceOrderCode,
                alice.username());

        // ================= MIXED-TENANT IDOR: Bob owns his own attempt, supplies Alice's sub-resource IDs =================
        AttemptTestFixtures.bindJdbc(jdbc);
        GrantOrderResult.Line bobGrant = AttemptTestFixtures.grantExam(
                grantService, bob.id(), hsaFullMiniId, 2, List.of(), "FULL_SOLUTION", hsaServing.version().versionNo());
        Attempt bobAttempt = startAttempt(bob, bobGrant.entitlementId(), null);
        UUID bobAttemptId = bobAttempt.getId();
        UUID bobSessionId = claimSession(bob, bobAttemptId, null, false);
        List<AttemptPart> bobParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(bobAttemptId);

        // Bob's attemptId + Alice's itemId in PUT /answers -> REJECTED NOT_IN_SCOPE (never mutates Alice's item)
        String mixedSaveRaw = mockMvc.perform(put("/api/v1/me/attempts/{attemptId}/answers", bobAttemptId)
                        .with(asStudent(bob))
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAnswersRequest(
                                List.of(new AnswerChange(
                                        shortAnswerItem.getId(), 99L, new AnswerValue.ShortAnswer("bob-mixed-tamper"))),
                                null,
                                null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode mixedSaveNode = objectMapper.readTree(mixedSaveRaw);
        assertThat(mixedSaveNode.path("results").get(0).path("status").asText()).isEqualTo("REJECTED");
        assertThat(mixedSaveNode.path("results").get(0).path("reason").asText()).isEqualTo("NOT_IN_SCOPE");
        for (String secret : forbiddenSubstrings) {
            assertThat(mixedSaveRaw).doesNotContain(secret);
        }

        // Bob's attemptId + Alice's partIds (/finish, /choice, /retry) -> 404 NOT_FOUND
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", bobAttemptId, openPart.getId())
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/choice", bobAttemptId, choicePart.getId())
                        .header("Idempotency-Key", "bob-mx-ch-" + UUID.randomUUID())
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPartChoiceRequest("ANH", null))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/retry", bobAttemptId, choicePart.getId())
                        .header("Idempotency-Key", "bob-mx-ret-" + UUID.randomUUID())
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetryPartRequest("bob-mixed-retry"))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Bob's attemptId + Alice's prompt/audio asset IDs & audio playId -> 404 NOT_FOUND
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", bobAttemptId, promptAssetId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", bobAttemptId, aliceAudioAssetId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays", bobAttemptId, aliceAudioAssetId)
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAudioPlayRequest("bob-mx-play"))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume",
                        bobAttemptId, aliceAudioAssetId, alicePlayId)
                        .header("X-Attempt-Session", bobSessionId.toString()),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress",
                        bobAttemptId, aliceAudioAssetId, alicePlayId)
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AudioProgressRequest(500L))),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);
        assertBobGets404NoLeak(
                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end",
                        bobAttemptId, aliceAudioAssetId, alicePlayId)
                        .header("X-Attempt-Session", bobSessionId.toString()),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Score Bob's attempt (FULL_SOLUTION) and verify Bob's SCORED attemptId + Alice's solutionAssetId -> 404 NOT_FOUND
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                        bobAttemptId, bobParts.get(0).getId())
                        .with(asStudent(bob))
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                        bobAttemptId, bobParts.get(1).getId())
                        .with(asStudent(bob))
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/choice",
                        bobAttemptId, bobParts.get(2).getId())
                        .with(asStudent(bob))
                        .header("Idempotency-Key", "bob-fin-ch-" + UUID.randomUUID())
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPartChoiceRequest("ANH", null))))
                .andExpect(status().isAccepted());
        awaitPartState(bobParts.get(2).getId(), PartState.OPEN);
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/submit", bobAttemptId)
                        .with(asStudent(bob))
                        .header("Idempotency-Key", "bob-fin-sub-" + UUID.randomUUID())
                        .header("X-Attempt-Session", bobSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of()))))
                .andExpect(status().isOk());
        outboxRelay.relayBatch(500);

        assertBobGets404NoLeak(
                get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}", bobAttemptId, solutionAssetId),
                bob,
                randomAttempt404Code,
                forbiddenSubstrings);

        // Re-confirm Alice's sentinel answer was never overwritten by Bob's mixed-tenant save probe
        UUID currentAliceSession = attemptRepository.findById(aliceActiveAttemptId).orElseThrow().getEditSessionId();
        assertAliceAttemptIntegrity(alice, aliceActiveAttemptId, shortAnswerItem.getId(), currentAliceSession);
    }

    private void assertAliceAttemptIntegrity(
            Student alice, UUID attemptId, UUID shortAnswerItemId, UUID expectedSessionId) throws Exception {
        String aliceStateRaw = mockMvc.perform(get("/api/v1/me/attempts/{attemptId}", attemptId)
                        .with(asStudent(alice)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode aliceStateNode = objectMapper.readTree(aliceStateRaw);
        String savedText = null;
        for (JsonNode part : aliceStateNode.path("parts")) {
            for (JsonNode item : part.path("items")) {
                if (shortAnswerItemId.toString().equals(item.path("itemId").asText())) {
                    savedText = item.path("answer").path("text").asText(null);
                }
            }
        }
        assertThat(savedText).isEqualTo(ALICE_SENTINEL_ANSWER);
        assertThat(attemptRepository.findById(attemptId).orElseThrow().getEditSessionId())
                .isEqualTo(expectedSessionId);
    }

    private void assertBobGets404NoLeak(
            MockHttpServletRequestBuilder request,
            Student bob,
            String expectedCode,
            List<String> forbiddenSubstrings) throws Exception {
        MvcResult res = mockMvc.perform(request.with(asStudent(bob)))
                .andExpect(status().isNotFound())
                .andReturn();
        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(body).path("code").asText()).isEqualTo(expectedCode);
        for (String secret : forbiddenSubstrings) {
            assertThat(body).as("Bob 404 response must not leak %s", secret).doesNotContain(secret);
        }
    }

    private Student createVerifiedStudent(String label) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = label + "." + userId.toString().substring(0, 8);
        String fullName = "Student " + username;
        String email = username + "@qa-idor.local";
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     email_verified_at, preferred_locale, source, status, must_change_password,
                     created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'vi', 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId,
                String.format("U%06d", seq),
                username,
                username.toLowerCase(),
                fullName,
                email,
                email.toLowerCase(),
                now,
                now,
                userId,
                now,
                userId);
        createdUserIds.add(userId);
        return new Student(userId, username, fullName);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asStudent(Student student) {
        return AttemptTestFixtures.asExamUser(student.id(), student.username());
    }

    private Attempt startAttempt(Student student, UUID entitlementId, StartAttemptRequest.PreChoiceInput preChoice)
            throws Exception {
        MvcResult startRes = mockMvc.perform(post("/api/v1/me/attempts")
                        .with(asStudent(student))
                        .header("Idempotency-Key", "idor-st-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAttemptRequest(entitlementId, preChoice))))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID attemptId = UUID.fromString(
                objectMapper.readTree(startRes.getResponse().getContentAsString()).path("attemptId").asText());
        return awaitAttemptState(attemptId, AttemptState.IN_PROGRESS);
    }

    private UUID claimSession(Student student, UUID attemptId, UUID sessionId, boolean takeover) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                        .with(asStudent(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClaimSessionRequest(sessionId, takeover))))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).path("sessionId").asText());
    }

    private UUID saveAsset(String fileName, String mime, byte[] bytes) throws Exception {
        UUID id = Ids.newId();
        String objectKey = "questions/qa-idor/" + id + "-" + fileName;
        objectStorage.put(objectKey, bytes, mime);
        createdObjectKeys.add(objectKey);
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(bytes);
        questionAssetRepository.saveAndFlush(new QuestionAsset(
                id, QuestionAssetKind.IMAGE, objectKey, fileName, mime, bytes.length, sha256, 8, 8, Ids.newId(), Instant.now()));
        createdAssets.add(id);
        return id;
    }

    private UUID createReadyQuestionWithAssets(UUID promptAssetId, UUID solutionAssetId) throws Exception {
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
                "<p>Prompt <img data-asset-id=\"" + promptAssetId + "\"></p>",
                "<p>Solution <img data-asset-id=\"" + solutionAssetId + "\"></p>",
                List.of(),
                List.of(),
                new QuestionBody.SingleChoiceBody(
                        QuestionType.SINGLE_CHOICE,
                        List.of(
                                new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                        false),
                "QA-IDOR-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult created = mockMvc.perform(post("/api/v1/admin/questions")
                        .with(asUser(Ids.newId(), "q-author-idor", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", "qa-idor-cr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isBetween(200, 201);
        UUID qId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("summary").path("id").asText());
        createdQuestions.add(qId);

        mockMvc.perform(post("/api/v1/admin/questions/bulk/ready")
                        .with(asUser(Ids.newId(), "q-author-idor", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", "qa-idor-rd-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(qId)))))
                .andExpect(status().isOk());
        return qId;
    }

    private Attempt awaitAttemptState(UUID attemptId, AttemptState target) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            Attempt attempt = attemptRepository.findById(attemptId).orElse(null);
            if (attempt != null) {
                if (attempt.getState() == target) {
                    return attempt;
                }
                if (attempt.getState() == AttemptState.ABORTED || attempt.getState() == AttemptState.GRADING_ERROR) {
                    throw new AssertionError("Attempt " + attemptId + " entered terminal failure state "
                            + attempt.getState() + " while waiting for " + target
                            + " (abortReason=" + attempt.getAbortReason()
                            + ", gradingError=" + attempt.getGradingError()
                            + ", faultFlag=" + attempt.getFaultFlag()
                            + ", faultReason=" + attempt.getFaultReason() + ")");
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for attempt " + attemptId + " to reach " + target);
    }

    private AttemptPart awaitPartState(UUID partId, PartState target) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            AttemptPart part = attemptPartRepository.findById(partId).orElse(null);
            if (part != null) {
                if (part.getState() == target) {
                    return part;
                }
                if (part.getState() == PartState.GENERATION_FAILED) {
                    throw new AssertionError("Part " + partId + " entered terminal failure state "
                            + part.getState() + " while waiting for " + target
                            + " (generationTries=" + part.getGenerationTries()
                            + ", generationError=" + part.getGenerationError() + ")");
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for part " + partId + " to reach " + target);
    }

    private record Student(UUID id, String username, String fullName) {}
}
