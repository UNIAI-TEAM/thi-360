package vn.unicom.thi360.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.api.AttemptAudioController.AudioProgressRequest;
import vn.unicom.thi360.attempt.api.AttemptAudioController.StartAudioPlayRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
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
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataBuilder;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
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
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.ForbiddenKeysAssert;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * AC-8: Scans every public storefront API, student commerce API, student attempt API, and admin
 * trial preview paper API with {@link ForbiddenKeysAssert} on non-empty, real DS responses.
 */
class StudentApiForbiddenKeysIT extends AbstractIntegrationTest {

    private static final String PUBLIC_CLIENT_IP = "203.0.113.220";
    private static final Set<String> REVIEW_SCORE_AND_ANSWERS_FORBIDDEN_KEYS = Set.of(
            "solution", "essayFeedback", "comment", "criteria",
            "gradingGuide", "rubric", "sampleAnswer", "commonMistakes",
            "scoreTable", "trueFalseTable",
            "knowledge", "knowledgeItemIds", "mapId", "mapIds", "mapidIds",
            "levelItemId", "difficulty", "difficultyCode");
    private static final Set<String> REVIEW_FULL_SOLUTION_FORBIDDEN_KEYS = Set.of(
            "gradingGuide", "rubric", "sampleAnswer", "commonMistakes",
            "scoreTable", "trueFalseTable", "exactFraction",
            "knowledge", "knowledgeItemIds", "mapId", "mapIds", "mapidIds",
            "levelItemId", "difficulty", "difficultyCode");

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private BundleDevDataBuilder bundleDevDataBuilder;
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
    void setUpDsAndStorefront() throws Exception {
        AttemptTestFixtures.ensureDsBuilt(context);
        bundleDevDataBuilder.build();
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
                            try {
                                jdbc.update("DELETE FROM shared.idempotency_key WHERE key LIKE 'fk-sol-%'");
                            } finally {
                                redisTemplate.delete(RateLimitFilter.PUBLIC_KEY_PREFIX + PUBLIC_CLIENT_IP);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void publicStorefrontAndStudentCommerceResponsesHaveNoForbiddenKeys() throws Exception {
        // 1. Public storefront filters & products (exams + bundle)
        String filtersJson = publicGet("/api/v1/public/storefront/filters");
        JsonNode filtersNode = objectMapper.readTree(filtersJson);
        assertThat(filtersNode.path("kinds")).isNotEmpty();
        assertThat(filtersNode.path("formats")).isNotEmpty();
        assertThat(filtersNode.path("subjects")).isNotEmpty();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(filtersJson);

        String productsJson = publicGet("/api/v1/public/storefront/products?size=48");
        JsonNode productsNode = objectMapper.readTree(productsJson);
        assertThat(productsNode.path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(productsJson);

        String bundleProductsJson = publicGet("/api/v1/public/storefront/products?format=BUNDLE");
        JsonNode bundleProductsNode = objectMapper.readTree(bundleProductsJson);
        assertThat(bundleProductsNode.path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(bundleProductsJson);

        // 2. Public storefront product detail for published DS exams and DS-BO-SP2E-3MON bundle
        for (String examCode : List.of(
                "DS-HSA-FULL",
                "DS-BCA-FULL-MINI",
                "DS-VSAT-ANH",
                "DS-HSCA-ANH-MINI",
                "DS-THPTQG-MATH-P1-MINI",
                "DS-SPT-INFORMATICS-MINI")) {
            String detailJson = publicGet("/api/v1/public/storefront/products/" + examCode);
            JsonNode detailNode = objectMapper.readTree(detailJson);
            assertThat(detailNode.path("parts")).as("parts of %s", examCode).isNotEmpty();
            ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(detailJson);
        }

        String bundleDetailJson = publicGet("/api/v1/public/storefront/products/DS-BO-SP2E-3MON");
        JsonNode bundleDetailNode = objectMapper.readTree(bundleDetailJson);
        assertThat(bundleDetailNode.path("productType").asText()).isEqualTo("BUNDLE");
        assertThat(bundleDetailNode.path("items")).hasSize(3);
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(bundleDetailJson);

        // 3. Student commerce: create order, pay order, list orders, order detail, exam-access/query, my exams
        StudentAccount student = createVerifiedStudent("fk-commerce");
        grantExam(student.id(), "DS-HSA-FULL-MINI", 3, "SCORE_AND_ANSWERS");

        String accessQueryJson = mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("codes", List.of("DS-HSA-FULL-MINI", "DS-BO-SP2E-3MON", "DS-BCA-FULL-MINI")))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode accessQueryNode = objectMapper.readTree(accessQueryJson);
        assertThat(accessQueryNode.path("items")).hasSize(3);
        assertThat(accessQueryNode.path("items").get(0).path("state").asText()).isEqualTo("CAN_START");
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(accessQueryJson);

        String myExamsJson = mockMvc.perform(get("/api/v1/me/exams")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode myExamsNode = objectMapper.readTree(myExamsJson);
        assertThat(myExamsNode.path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(myExamsJson);

        JsonNode productForOrder = objectMapper.readTree(publicGet("/api/v1/public/storefront/products/DS-HSA-PART-MATH-MINI"));
        String priceQuoteJson = mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "DS-HSA-PART-MATH-MINI"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(priceQuoteJson).path("productCode").asText())
                .isEqualTo("DS-HSA-PART-MATH-MINI");
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(priceQuoteJson);

        String createOrderRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("Idempotency-Key", "fk-ord-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "DS-HSA-PART-MATH-MINI",
                                "expectedAccountId", student.id(),
                                "expectedVersionNo", productForOrder.path("versionNo").asInt(),
                                "expectedAmountDue", productForOrder.path("price").asLong()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode createdOrderNode = objectMapper.readTree(createOrderRaw);
        UUID orderId = UUID.fromString(createdOrderNode.path("order").path("id").asText());
        createdOrderIds.add(orderId);
        String orderCode = createdOrderNode.path("order").path("code").asText();
        assertThat(createdOrderNode.path("order").path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(createOrderRaw);

        String listOrdersJson = mockMvc.perform(get("/api/v1/me/orders")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(listOrdersJson).path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(listOrdersJson);

        String orderDetailJson = mockMvc.perform(get("/api/v1/me/orders/{code}", orderCode)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(orderDetailJson).path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(orderDetailJson);

        String payOrderJson = mockMvc.perform(post("/api/v1/me/orders/{code}/pay", orderCode)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(payOrderJson).path("paymentUrl").asText()).isNotBlank();
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(payOrderJson);

        String cancelOrderJson = mockMvc.perform(post("/api/v1/me/orders/{code}/cancel", orderCode)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(cancelOrderJson).path("state").asText()).isEqualTo("CANCELLED");
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(cancelOrderJson);
    }

    @Test
    void allAttemptApisAcrossAllQuestionTypesAndAdminPreviewHaveNoForbiddenKeys() throws Exception {
        StudentAccount student = createVerifiedStudent("fk-attempt");
        Set<QuestionType> observedTypes = new LinkedHashSet<>();

        // Non-vacuous solution-asset fixture: real stored image linked to a ready question's solution HTML
        byte[] solutionBytes = "FK-SOLUTION-PNG".getBytes(StandardCharsets.UTF_8);
        UUID linkedSolAssetId = saveSolutionAsset("fk-sol.png", "image/png", solutionBytes);
        UUID solQuestionId = createReadyQuestionWithSolutionAsset(linkedSolAssetId);

        // A. Multi-part DS-HSA-FULL-MINI (SCORE_AND_ANSWERS): prepare, start, state, finish, choice, retry, submit, result, review, policy gate
        GrantOrderResult.Line hsaGrant = grantExam(student.id(), "DS-HSA-FULL-MINI", 3, "SCORE_AND_ANSWERS");
        String prepareJson = mockMvc.perform(get("/api/v1/me/entitlements/{entitlementId}/prepare", hsaGrant.entitlementId())
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(prepareJson).path("parts")).isNotEmpty();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(prepareJson);

        MvcResult startHsaRes = mockMvc.perform(post("/api/v1/me/attempts")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("Idempotency-Key", "fk-hsa-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAttemptRequest(hsaGrant.entitlementId(), null))))
                .andExpect(status().isAccepted())
                .andReturn();
        String startHsaJson = startHsaRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(startHsaJson);
        UUID hsaAttemptId = UUID.fromString(objectMapper.readTree(startHsaJson).path("attemptId").asText());
        awaitAttemptState(hsaAttemptId, AttemptState.IN_PROGRESS);

        String hsaStateJson = getAttemptState(student, hsaAttemptId);
        JsonNode hsaStateNode = objectMapper.readTree(hsaStateJson);
        assertThat(hsaStateNode.path("parts").get(0).path("items")).isNotEmpty();
        collectObservedQuestionTypes(hsaStateNode, observedTypes);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(hsaStateJson);

        UUID hsaSessionId = claimSession(student, hsaAttemptId);
        String heartbeatJson = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/heartbeat", hsaAttemptId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("X-Attempt-Session", hsaSessionId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(heartbeatJson).path("state").asText()).isEqualTo("IN_PROGRESS");
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(heartbeatJson);

        List<AttemptPart> hsaParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsaAttemptId);
        assertThat(hsaParts).hasSize(3);
        List<AttemptItem> hsaPart0Items = attemptItemRepository.findByPartIdOrderByOrdAsc(hsaParts.get(0).getId());
        AttemptItem hsaLinkedTarget = hsaPart0Items.stream()
                .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        jdbc.update(
                "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                solQuestionId,
                hsaLinkedTarget.getId());

        // Save ShortAnswer on Part 0
        AttemptItem hsaShort = hsaPart0Items.stream()
                .filter(i -> i.getQuestionType() == QuestionType.SHORT_ANSWER)
                .findFirst()
                .orElseThrow();
        String putShortJson = putAnswers(student, hsaAttemptId, hsaSessionId,
                List.of(new AnswerChange(hsaShort.getId(), 1L, new AnswerValue.ShortAnswer("42"))));
        assertThat(objectMapper.readTree(putShortJson).path("results").get(0).path("status").asText()).isEqualTo("SAVED");
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(putShortJson);

        // Finish Part 0 & Part 1 -> Part 2 becomes AWAITING_CHOICE
        String finish0Json = finishPart(student, hsaAttemptId, hsaParts.get(0).getId(), hsaSessionId);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(finish0Json);
        String finish1Json = finishPart(student, hsaAttemptId, hsaParts.get(1).getId(), hsaSessionId);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(finish1Json);

        // Confirm Part 3 choice
        String choiceJson = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/choice",
                        hsaAttemptId, hsaParts.get(2).getId())
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("Idempotency-Key", "fk-ch-" + UUID.randomUUID())
                        .header("X-Attempt-Session", hsaSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SINH")))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(choiceJson);
        awaitPartState(hsaParts.get(2).getId(), PartState.OPEN);

        // Also exercise /parts/{partId}/retry on a GENERATION_FAILED part and scan its 202 response
        jdbc.update("DELETE FROM attempt.attempt_item WHERE part_id = ?", hsaParts.get(2).getId());
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'GENERATION_FAILED', generation_tries = 1, generation_error = 'GENERATION_FAILED' WHERE id = ?",
                hsaParts.get(2).getId());
        String retryJson = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/retry",
                        hsaAttemptId, hsaParts.get(2).getId())
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("Idempotency-Key", "fk-ret-" + UUID.randomUUID())
                        .header("X-Attempt-Session", hsaSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetryPartRequest("qa security check"))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(retryJson);
        awaitPartState(hsaParts.get(2).getId(), PartState.OPEN);

        String hsaAfterPart3Json = getAttemptState(student, hsaAttemptId);
        JsonNode hsaAfterPart3Node = objectMapper.readTree(hsaAfterPart3Json);
        assertThat(hsaAfterPart3Node.path("parts").get(2).path("items")).isNotEmpty();
        collectObservedQuestionTypes(hsaAfterPart3Node, observedTypes);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(hsaAfterPart3Json);

        // Submit, relay, result, review (SCORE_AND_ANSWERS)
        String submitJson = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/submit", hsaAttemptId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("Idempotency-Key", "fk-sub-" + UUID.randomUUID())
                        .header("X-Attempt-Session", hsaSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(submitJson);
        outboxRelay.relayBatch(500);

        String resultJson = mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/result", hsaAttemptId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(resultJson).path("state").asText()).isEqualTo("SCORED");
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(resultJson);

        String reviewJson = mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/review", hsaAttemptId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode reviewNode = strictJsonMapper().readTree(reviewJson);
        assertThat(reviewNode.path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoForbiddenKeys(
                reviewNode,
                REVIEW_SCORE_AND_ANSWERS_FORBIDDEN_KEYS,
                ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_SCOPED_KEYS,
                "review-score-and-answers");

        // SCORE_AND_ANSWERS policy gate: even after SCORED and even for a genuinely linked solution asset, /solution-assets MUST 404
        mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}",
                        hsaAttemptId, linkedSolAssetId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isNotFound());

        // A2. Non-vacuous IN_PROGRESS -> 404 vs SCORED -> 200 proof on FULL_SOLUTION attempt (DS-HSA-PART-MATH-MINI)
        Attempt fullSolAttempt = startAttemptForExam(student, "DS-HSA-PART-MATH-MINI", "FULL_SOLUTION");
        AttemptItem fullSolTarget = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(fullSolAttempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        jdbc.update(
                "UPDATE attempt.attempt_item SET question_id = ?, question_version_no = 1, option_order = '[\"o1\",\"o2\"]'::jsonb WHERE id = ?",
                solQuestionId,
                fullSolTarget.getId());

        // While IN_PROGRESS, linked solution asset MUST return 404
        mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}",
                        fullSolAttempt.getId(), linkedSolAssetId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isNotFound());

        UUID fullSolSessionId = claimSession(student, fullSolAttempt.getId());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/submit", fullSolAttempt.getId())
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("Idempotency-Key", "fk-full-sub-" + UUID.randomUUID())
                        .header("X-Attempt-Session", fullSolSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of()))))
                .andExpect(status().isOk());
        outboxRelay.relayBatch(500);

        String fullSolResultJson = mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/result", fullSolAttempt.getId())
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(fullSolResultJson).path("state").asText()).isEqualTo("SCORED");
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(fullSolResultJson);

        // Same attempt & linked solution asset after SCORED MUST return 200 with exact stored bytes
        assertThat(mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}",
                        fullSolAttempt.getId(), linkedSolAssetId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .isEqualTo(solutionBytes);

        String fullSolReviewJson = mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/review", fullSolAttempt.getId())
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode fullSolReviewNode = strictJsonMapper().readTree(fullSolReviewJson);
        assertThat(fullSolReviewNode.path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoForbiddenKeys(
                fullSolReviewNode,
                REVIEW_FULL_SOLUTION_FORBIDDEN_KEYS,
                ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_SCOPED_KEYS,
                "review-full-solution");

        // B. DS-VSAT-ANH: TRUE_FALSE (statements[]), MATCHING (save answer.pairs), CLOZE, QUESTION_SET
        Attempt vsatAttempt = startAttemptForExam(student, "DS-VSAT-ANH", "SCORE_AND_ANSWERS");
        String vsatStateJson = getAttemptState(student, vsatAttempt.getId());
        JsonNode vsatStateNode = objectMapper.readTree(vsatStateJson);
        assertThat(vsatStateNode.path("parts").get(0).path("items")).isNotEmpty();
        collectObservedQuestionTypes(vsatStateNode, observedTypes);
        verifyTrueFalseStatementsSanitized(vsatStateNode);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(vsatStateJson);

        UUID vsatSessionId = claimSession(student, vsatAttempt.getId());
        List<AttemptItem> vsatItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(vsatAttempt.getId());
        AttemptItem matchingItem = vsatItems.stream()
                .filter(i -> i.getQuestionType() == QuestionType.MATCHING)
                .findFirst()
                .orElseThrow();
        AttemptItem tfItem = vsatItems.stream()
                .filter(i -> i.getQuestionType() == QuestionType.TRUE_FALSE)
                .findFirst()
                .orElseThrow();
        AttemptItem clozeItem = vsatItems.stream()
                .filter(i -> i.getQuestionType() == QuestionType.CLOZE)
                .findFirst()
                .orElseThrow();

        // Find first left/right IDs from sanitized state response so pairs are valid
        JsonNode matchingStateNode = findItemInState(vsatStateNode, matchingItem.getId());
        String leftId = matchingStateNode.path("questionBody").path("left").get(0).path("id").asText();
        String rightId = matchingStateNode.path("questionBody").path("right").get(0).path("id").asText();
        String tfStmtId = findItemInState(vsatStateNode, tfItem.getId())
                .path("questionBody").path("statements").get(0).path("id").asText();
        String clozeBlankId = findItemInState(vsatStateNode, clozeItem.getId())
                .path("questionBody").path("blanks").get(0).path("id").asText();

        String putVsatJson = putAnswers(student, vsatAttempt.getId(), vsatSessionId, List.of(
                new AnswerChange(matchingItem.getId(), 1L, new AnswerValue.Matching(Map.of(leftId, rightId))),
                new AnswerChange(tfItem.getId(), 1L, new AnswerValue.TrueFalse(Map.of(tfStmtId, true))),
                new AnswerChange(clozeItem.getId(), 1L, new AnswerValue.Cloze(Map.of(clozeBlankId, "word")))));
        for (JsonNode r : objectMapper.readTree(putVsatJson).path("results")) {
            assertThat(r.path("status").asText()).isEqualTo("SAVED");
        }
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(putVsatJson);

        // Re-read GET /attempts/{id} and verify answer.pairs is present AND permitted by assertNoAttemptForbiddenKeys
        String vsatAfterSaveJson = getAttemptState(student, vsatAttempt.getId());
        JsonNode savedMatchNode = findItemInState(objectMapper.readTree(vsatAfterSaveJson), matchingItem.getId());
        assertThat(savedMatchNode.path("answer").path("pairs").path(leftId).asText()).isEqualTo(rightId);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(vsatAfterSaveJson);

        // C. DS-HSCA-TOAN: MULTIPLE_CHOICE
        Attempt hscaToanAttempt = startAttemptForExam(student, "DS-HSCA-TOAN", "SCORE_AND_ANSWERS");
        String hscaToanStateJson = getAttemptState(student, hscaToanAttempt.getId());
        JsonNode hscaToanNode = objectMapper.readTree(hscaToanStateJson);
        assertThat(hscaToanNode.path("parts").get(0).path("items")).isNotEmpty();
        collectObservedQuestionTypes(hscaToanNode, observedTypes);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(hscaToanStateJson);

        // D. DS-BCA-FULL-MINI: ESSAY (requires preChoice subject CA2)
        Attempt bcaAttempt = startAttemptForExam(
                student, "DS-BCA-FULL-MINI", "SCORE_AND_ANSWERS", new StartAttemptRequest.PreChoiceInput("CA2", null));
        String bcaStateJson = getAttemptState(student, bcaAttempt.getId());
        JsonNode bcaNode = objectMapper.readTree(bcaStateJson);
        assertThat(bcaNode.path("parts").get(0).path("items")).isNotEmpty();
        collectObservedQuestionTypes(bcaNode, observedTypes);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(bcaStateJson);

        // E. DS-HSCA-ANH-MINI: Audio attempt state & audio play lifecycle (/plays, /resume, /progress, /end)
        Attempt audioAttempt = startAttemptForExam(student, "DS-HSCA-ANH-MINI", "SCORE_AND_ANSWERS");
        String audioStateJson = getAttemptState(student, audioAttempt.getId());
        JsonNode audioNode = objectMapper.readTree(audioStateJson);
        assertThat(audioNode.path("audioPolicy").path("maxPlays").asInt()).isPositive();
        assertThat(audioNode.path("audioPlays")).isNotEmpty();
        List<AttemptAudioPlay> plays = attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(audioAttempt.getId());
        assertThat(plays).isNotEmpty();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(audioStateJson);

        UUID audioSessionId = claimSession(student, audioAttempt.getId());
        UUID audioAssetId = plays.getFirst().getAssetId();
        String openPlayJson = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays",
                        audioAttempt.getId(), audioAssetId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("X-Attempt-Session", audioSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAudioPlayRequest("fk-audio-req-1"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        UUID audioPlayId = UUID.fromString(objectMapper.readTree(openPlayJson).path("playId").asText());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(openPlayJson);

        String resumePlayJson = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume",
                        audioAttempt.getId(), audioAssetId, audioPlayId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("X-Attempt-Session", audioSessionId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(resumePlayJson);

        String progressPlayJson = mockMvc.perform(put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress",
                        audioAttempt.getId(), audioAssetId, audioPlayId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("X-Attempt-Session", audioSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AudioProgressRequest(0L))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(progressPlayJson);

        String endPlayJson = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end",
                        audioAttempt.getId(), audioAssetId, audioPlayId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("X-Attempt-Session", audioSessionId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(endPlayJson);

        // Assert ALL 8 QuestionTypes were genuinely observed and scanned (non-vacuous coverage)
        assertThat(observedTypes).containsExactlyInAnyOrder(QuestionType.values());

        // F. Admin trial preview endpoints: POST .../trial, GET .../paper, and POST .../grade
        // (Note: POST /api/v1/admin/exams/{id}/preview is the exam-authoring preview that intentionally includes
        // answer keys, solutions, rubric, and difficulty/knowledge metadata for EXAM_VIEW editors.)
        UUID vsatExamId = AttemptTestFixtures.dsExamId(jdbc, "DS-VSAT-ANH");
        String previewCreateRaw = mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", vsatExamId)
                        .with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":20261122}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode previewCreateNode = objectMapper.readTree(previewCreateRaw);
        assertThat(previewCreateNode.path("status").asText()).isEqualTo("GENERATED");
        UUID previewId = UUID.fromString(previewCreateNode.path("previewId").asText());

        String previewTrialJson = mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/trial",
                        vsatExamId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(previewTrialJson).path("expiresAt").asText()).isNotBlank();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(previewTrialJson);

        String previewPaperJson = mockMvc.perform(get("/api/v1/admin/exams/{id}/preview/{previewId}/paper",
                        vsatExamId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode previewPaperNode = objectMapper.readTree(previewPaperJson);
        assertThat(previewPaperNode.path("parts")).isNotEmpty();
        assertThat(previewPaperNode.path("parts").get(0).path("blocks")).isNotEmpty();
        assertThat(previewPaperNode.path("parts").get(0).path("blocks").get(0).path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(previewPaperJson);

        String previewGradeJson = mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/grade",
                        vsatExamId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(previewGradeJson).path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(previewGradeJson);

        // Also verify GET /api/v1/me/attempts history contains no attempt-forbidden keys
        String historyJson = mockMvc.perform(get("/api/v1/me/attempts")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(historyJson).path("items")).isNotEmpty();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(historyJson);
    }

    private String publicGet(String path) throws Exception {
        return mockMvc.perform(get(path).with(req -> {
                    req.setRemoteAddr(PUBLIC_CLIENT_IP);
                    return req;
                }))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private StudentAccount createVerifiedStudent(String label) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = label + "." + userId.toString().substring(0, 8);
        String email = username + "@qa-test.local";
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
                "QA Student " + label,
                email,
                email.toLowerCase(),
                now,
                now,
                userId,
                now,
                userId);
        createdUserIds.add(userId);
        return new StudentAccount(userId, username);
    }

    private GrantOrderResult.Line grantExam(UUID userId, String examCode, int attempts, String resultView) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        var serving = publishedExamPort.findServing(examId).orElseThrow();
        AttemptTestFixtures.bindJdbc(jdbc);
        return AttemptTestFixtures.grantExam(
                grantService, userId, examId, attempts, List.of(), resultView, serving.version().versionNo());
    }

    private Attempt startAttemptForExam(StudentAccount student, String examCode, String resultView) throws Exception {
        return startAttemptForExam(student, examCode, resultView, null);
    }

    private Attempt startAttemptForExam(
            StudentAccount student, String examCode, String resultView, StartAttemptRequest.PreChoiceInput preChoice)
            throws Exception {
        GrantOrderResult.Line grant = grantExam(student.id(), examCode, 2, resultView);
        MvcResult startRes = mockMvc.perform(post("/api/v1/me/attempts")
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("Idempotency-Key", "fk-" + examCode + "-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAttemptRequest(grant.entitlementId(), preChoice))))
                .andExpect(status().isAccepted())
                .andReturn();
        String startJson = startRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(startJson);
        UUID attemptId = UUID.fromString(objectMapper.readTree(startJson).path("attemptId").asText());
        return awaitAttemptState(attemptId, AttemptState.IN_PROGRESS);
    }

    private String getAttemptState(StudentAccount student, UUID attemptId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/attempts/{attemptId}", attemptId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private UUID claimSession(StudentAccount student, UUID attemptId) throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(raw);
        return UUID.fromString(objectMapper.readTree(raw).path("sessionId").asText());
    }

    private String putAnswers(
            StudentAccount student, UUID attemptId, UUID sessionId, List<AnswerChange> changes) throws Exception {
        return mockMvc.perform(put("/api/v1/me/attempts/{attemptId}/answers", attemptId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAnswersRequest(changes, null, null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String finishPart(StudentAccount student, UUID attemptId, UUID partId, UUID sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, partId)
                        .with(AttemptTestFixtures.asExamUser(student.id(), student.username()))
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
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

    private ObjectMapper strictJsonMapper() {
        return objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private UUID saveSolutionAsset(String fileName, String mime, byte[] bytes) throws Exception {
        UUID id = Ids.newId();
        String objectKey = "questions/qa-fk/" + id + "-" + fileName;
        objectStorage.put(objectKey, bytes, mime);
        createdObjectKeys.add(objectKey);
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(bytes);
        questionAssetRepository.saveAndFlush(new QuestionAsset(
                id, QuestionAssetKind.IMAGE, objectKey, fileName, mime, bytes.length, sha256, 8, 8, Ids.newId(), Instant.now()));
        createdAssets.add(id);
        return id;
    }

    private UUID createReadyQuestionWithSolutionAsset(UUID solutionAssetId) throws Exception {
        SaveQuestionRequest req = new SaveQuestionRequest(
                QuestionType.SINGLE_CHOICE,
                "TOAN",
                "G12",
                "NB",
                null,
                null,
                java.math.BigDecimal.ONE,
                List.of(),
                List.of(),
                "<p>Prompt</p>",
                "<p>Solution <img data-asset-id=\"" + solutionAssetId + "\"></p>",
                List.of(),
                List.of(),
                new QuestionBody.SingleChoiceBody(
                        QuestionType.SINGLE_CHOICE,
                        List.of(
                                new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                        false),
                "QA-FK-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult created = mockMvc.perform(post("/api/v1/admin/questions")
                        .with(asUser(Ids.newId(), "q-author-fk", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", "fk-sol-cr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isBetween(200, 201);
        UUID qId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("summary").path("id").asText());
        createdQuestions.add(qId);

        mockMvc.perform(post("/api/v1/admin/questions/bulk/ready")
                        .with(asUser(Ids.newId(), "q-author-fk", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", "fk-sol-rd-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(qId)))))
                .andExpect(status().isOk());
        return qId;
    }

    private static void collectObservedQuestionTypes(JsonNode stateNode, Set<QuestionType> observedTypes) {
        for (JsonNode part : stateNode.path("parts")) {
            for (JsonNode item : part.path("items")) {
                String qt = item.path("questionType").asText(null);
                if (qt != null) {
                    observedTypes.add(QuestionType.valueOf(qt));
                }
            }
        }
    }

    private static void verifyTrueFalseStatementsSanitized(JsonNode stateNode) {
        boolean sawStatements = false;
        for (JsonNode part : stateNode.path("parts")) {
            for (JsonNode item : part.path("items")) {
                if ("TRUE_FALSE".equals(item.path("questionType").asText())) {
                    JsonNode statements = item.path("questionBody").path("statements");
                    assertThat(statements).isNotEmpty();
                    sawStatements = true;
                    for (JsonNode st : statements) {
                        assertThat(st.has("id")).isTrue();
                        assertThat(st.has("content")).isTrue();
                        assertThat(st.has("answer")).isFalse();
                        assertThat(st.has("points")).isFalse();
                    }
                }
            }
        }
        assertThat(sawStatements).as("DS-VSAT-ANH must contain TRUE_FALSE statements[]").isTrue();
    }

    private static JsonNode findItemInState(JsonNode stateNode, UUID itemId) {
        for (JsonNode part : stateNode.path("parts")) {
            for (JsonNode item : part.path("items")) {
                if (itemId.toString().equals(item.path("itemId").asText())) {
                    return item;
                }
            }
        }
        throw new AssertionError("Missing item " + itemId + " in AttemptStateResponse");
    }

    private record StudentAccount(UUID id, String username) {}
}
