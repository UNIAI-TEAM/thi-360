package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.ForbiddenKeysAssert;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Integration tests for {@code GET /api/v1/admin/users/{id}/learning} (LP / UNI-2743: AC-1, AC-2, OpenAPI schema).
 * Uses MockMvc + JsonNode assertions so this test compiles cleanly against {@code base_sha} during red-first checks.
 */
@Import(AttemptTestFixtures.MutableClockConfig.class)
class AdminUserLearningIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MutableClock mutableClock;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private EntitlementPort entitlementPort;
    @Autowired private StorefrontProjectionService storefrontProjectionService;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;
    @Autowired private AttemptItemRepository attemptItemRepository;
    @Autowired private QuestionSourcePort questionSourcePort;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private OutboxRelay outboxRelay;

    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> projectedExamIds = new ArrayList<>();

    @BeforeEach
    void setUpDsAndClock() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        while (outboxRelay.relayBatch(500) > 0) {
            // drain backlog from prior tests
        }
    }

    @AfterEach
    void cleanup() {
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUsers.clear();
        for (UUID examId : projectedExamIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        projectedExamIds.clear();
    }

    @Test
    void returnsEntitlementsAndAttempts() throws Exception {
        UUID studentA = createStudent("lp-a");
        UUID studentB = createStudent("lp-b");

        UUID hsaMathId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        UUID vactToanId = AttemptTestFixtures.dsExamId(jdbc, "DS-VACT-SUBJECT-TOAN-MINI");
        UUID thptqgMathId = AttemptTestFixtures.dsExamId(jdbc, "DS-THPTQG-MATH-P1-MINI");
        UUID hsaReviewId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-REVIEW-MINI");
        UUID bcaFullId = AttemptTestFixtures.dsExamId(jdbc, "DS-BCA-FULL-MINI");
        UUID hsaSubjToanId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-SUBJECT-TOAN-MINI");
        UUID thptqgInfoId = AttemptTestFixtures.dsExamId(jdbc, "DS-THPTQG-INFORMATICS-MINI");
        UUID sptInfoId = AttemptTestFixtures.dsExamId(jdbc, "DS-SPT-INFORMATICS-MINI");
        UUID vactLangId = AttemptTestFixtures.dsExamId(jdbc, "DS-VACT-PART-LANGUAGE-MINI");

        // Ensure hsaMathId & vactToanId stay projected in commerce.storefront_product;
        // thptqgMathId will be explicitly removed before calling /learning to verify live PublishedExamPort fallback.
        projectStorefrontIfAbsent(hsaMathId);
        projectStorefrontIfAbsent(vactToanId);

        // 1. ACTIVE entitlement (4 total: 2 used, 1 reserved, 1 left) + SCORED positive, SCORED 0, and IN_PROGRESS
        GrantOrderResult.Line activeEnt = grant(studentA, "DS-HSA-PART-MATH-MINI", 4);
        Attempt scoredPositive = startAndAwaitOpen(studentA, activeEnt.entitlementId(), null);
        UUID scoredPosSession = claimSession(studentA, scoredPositive.getId());
        AttemptItem scItem = attemptItemRepository
                .findByAttemptIdOrderByPartIdAscOrdAsc(scoredPositive.getId())
                .stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        String correctOpt = firstCorrectOptionId(scItem);
        submitAttempt(
                studentA,
                scoredPositive.getId(),
                scoredPosSession,
                List.of(new FinalAnswerChange(scItem.getId(), 1L, new AnswerValue.SingleChoice(correctOpt))));
        drainOutbox();
        Attempt scoredPosDb = attemptRepository.findById(scoredPositive.getId()).orElseThrow();
        assertThat(scoredPosDb.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scoredPosDb.getDisplayScore()).isGreaterThan(BigDecimal.ZERO);

        mutableClock.advance(Duration.ofMinutes(5));
        Attempt scoredZero = startAndAwaitOpen(studentA, activeEnt.entitlementId(), null);
        UUID scoredZeroSession = claimSession(studentA, scoredZero.getId());
        submitAttempt(studentA, scoredZero.getId(), scoredZeroSession, List.of());
        drainOutbox();
        Attempt scoredZeroDb = attemptRepository.findById(scoredZero.getId()).orElseThrow();
        assertThat(scoredZeroDb.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scoredZeroDb.getDisplayScore()).isEqualByComparingTo(BigDecimal.ZERO);

        mutableClock.advance(Duration.ofMinutes(5));
        Attempt inProgressHsa = startAndAwaitOpen(studentA, activeEnt.entitlementId(), null);

        // 2. IN_USE entitlement (2 total: 1 used SCORED LINEAR, 1 reserved IN_PROGRESS, 0 left)
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line inUseEnt = grant(studentA, "DS-VACT-SUBJECT-TOAN-MINI", 2);
        Attempt vactScored = startAndAwaitOpen(studentA, inUseEnt.entitlementId(), null);
        UUID vactSession = claimSession(studentA, vactScored.getId());
        AttemptItem vactScItem = attemptItemRepository
                .findByAttemptIdOrderByPartIdAscOrdAsc(vactScored.getId())
                .stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        submitAttempt(
                studentA,
                vactScored.getId(),
                vactSession,
                List.of(new FinalAnswerChange(
                        vactScItem.getId(), 1L, new AnswerValue.SingleChoice(firstCorrectOptionId(vactScItem)))));
        drainOutbox();
        Attempt vactScoredDb = attemptRepository.findById(vactScored.getId()).orElseThrow();
        assertThat(vactScoredDb.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(vactScoredDb.getPracticeScoring()).isEqualTo("LINEAR");

        mutableClock.advance(Duration.ofMinutes(5));
        Attempt vactInProgress = startAndAwaitOpen(studentA, inUseEnt.entitlementId(), null);

        // 3. EXHAUSTED entitlement (1 total, 1 used -> GRADING_ERROR)
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line exhaustedEnt = grant(studentA, "DS-THPTQG-MATH-P1-MINI", 1);
        Attempt gradingErrorAttempt = startAndAwaitOpen(studentA, exhaustedEnt.entitlementId(), null);
        UUID gradingErrorSession = claimSession(studentA, gradingErrorAttempt.getId());
        submitAttempt(studentA, gradingErrorAttempt.getId(), gradingErrorSession, List.of());
        drainOutbox();
        jdbc.update(
                """
                UPDATE attempt.attempt
                SET state = 'GRADING_ERROR',
                    grading_error = 'Internal stacktrace secret',
                    raw_score = 9.99,
                    display_score = 9.99
                WHERE id = ?
                """,
                gradingErrorAttempt.getId());

        // 4. REVOKED entitlement (2 total, 0 used, 2 revoked, 0 left)
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line revokedEnt = grant(studentA, "DS-HSA-REVIEW-MINI", 2);
        grantService.revokeUnused(revokedEnt.grantId());

        // 4b. Partial-refund EXHAUSTED entitlement (2 total: 1 used, 0 reserved, 1 revoked, 0 left -> EXHAUSTED)
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line partialRefundEnt = grant(studentA, "DS-THPTQG-INFORMATICS-MINI", 2);
        UUID partialRefundUsedAttempt = Ids.newId();
        entitlementPort.reserve(studentA, partialRefundEnt.entitlementId(), partialRefundUsedAttempt);
        entitlementPort.confirmUse(partialRefundUsedAttempt);
        grantService.revokeUnused(partialRefundEnt.grantId());

        // 5. PENDING_REVIEW (natural + regrading=true with residual DB scores)
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line bcaEnt = grant(studentA, "DS-BCA-FULL-MINI", 2);
        Attempt pendingNatural = startAndAwaitOpen(studentA, bcaEnt.entitlementId(), new PreChoiceInput("CA2", null));
        submitBcaWithEssay(studentA, pendingNatural.getId(), "<p>Bài luận chờ chấm tự nhiên</p>");
        drainOutbox();
        assertThat(attemptRepository.findById(pendingNatural.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.PENDING_REVIEW);

        mutableClock.advance(Duration.ofMinutes(5));
        Attempt pendingRegrading = startAndAwaitOpen(studentA, bcaEnt.entitlementId(), new PreChoiceInput("CA2", null));
        submitBcaWithEssay(studentA, pendingRegrading.getId(), "<p>Bài luận đang chấm lại</p>");
        drainOutbox();
        jdbc.update(
                """
                UPDATE attempt.attempt
                SET state = 'PENDING_REVIEW',
                    regrading = true,
                    raw_score = 45.50,
                    display_score = 45.50,
                    graded_at = now()
                WHERE id = ?
                """,
                pendingRegrading.getId());

        // 6. STARTING and ABORTED rows in DB for student A -> MUST be excluded from attempts[]
        UUID startingId = Ids.newId();
        UUID abortedId = Ids.newId();
        insertSyntheticTerminalAttempt(startingId, studentA, sptInfoId, "STARTING", null);
        insertSyntheticTerminalAttempt(abortedId, studentA, sptInfoId, "ABORTED", "GENERATION_FAILED");

        // 7. Student B's own entitlement & scored attempt -> MUST NOT appear in student A's response
        GrantOrderResult.Line studentBEnt = grant(studentB, "DS-VACT-PART-LANGUAGE-MINI", 2);
        Attempt studentBAttempt = startAndAwaitOpen(studentB, studentBEnt.entitlementId(), null);
        UUID studentBSession = claimSession(studentB, studentBAttempt.getId());
        submitAttempt(studentB, studentBAttempt.getId(), studentBSession, List.of());
        drainOutbox();

        // 8. GRADING attempt (submitted last after all drainOutbox calls, so it stays in GRADING)
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line gradingEnt = grant(studentA, "DS-HSA-SUBJECT-TOAN-MINI", 1);
        Attempt gradingAttempt = startAndAwaitOpen(studentA, gradingEnt.entitlementId(), null);
        UUID gradingSession = claimSession(studentA, gradingAttempt.getId());
        submitAttempt(studentA, gradingAttempt.getId(), gradingSession, List.of());
        assertThat(attemptRepository.findById(gradingAttempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.GRADING);

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM commerce.storefront_product WHERE exam_id = ?",
                        Integer.class,
                        hsaMathId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM commerce.storefront_product WHERE exam_id = ?",
                        Integer.class,
                        vactToanId))
                .isEqualTo(1);
        jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", thptqgMathId);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM commerce.storefront_product WHERE exam_id = ?",
                        Integer.class,
                        thptqgMathId))
                .isZero();

        String rawBody;
        try {
            MvcResult res = mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentA)
                            .with(asUser(Permissions.USER_LEARNING_VIEW)))
                    .andExpect(status().isOk())
                    .andReturn();
            rawBody = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } finally {
            storefrontProjectionService.project(thptqgMathId);
        }

        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(rawBody);
        JsonNode root = objectMapper.readTree(rawBody);

        assertThat(root.path("userId").asText()).isEqualTo(studentA.toString());
        assertThat(root.path("dataAvailable").asBoolean()).isTrue();
        assertThat(root.path("assessments").isArray()).isTrue();
        assertThat(root.path("assessments")).isEmpty();
        assertThat(root.path("practice").isArray()).isTrue();
        assertThat(root.path("practice")).isEmpty();
        assertThat(root.path("supportRequests").isArray()).isTrue();
        assertThat(root.path("supportRequests")).isEmpty();

        // Verify entitlements[] raw size and createdAt ASC, id ASC ordering before map indexing
        JsonNode entitlementsArray = root.path("entitlements");
        assertThat(entitlementsArray.isArray()).isTrue();
        assertThat(entitlementsArray).hasSize(7);
        List<UUID> actualEntitlementOrder = new ArrayList<>();
        Map<UUID, JsonNode> entsById = new LinkedHashMap<>();
        for (JsonNode entNode : entitlementsArray) {
            UUID entId = UUID.fromString(entNode.path("entitlementId").asText());
            actualEntitlementOrder.add(entId);
            entsById.put(entId, entNode);
        }
        assertThat(actualEntitlementOrder).containsExactly(
                activeEnt.entitlementId(),
                inUseEnt.entitlementId(),
                exhaustedEnt.entitlementId(),
                revokedEnt.entitlementId(),
                partialRefundEnt.entitlementId(),
                bcaEnt.entitlementId(),
                gradingEnt.entitlementId());
        assertThat(entsById).doesNotContainKey(studentBEnt.entitlementId());

        JsonNode activeNode = entsById.get(activeEnt.entitlementId());
        assertThat(activeNode.path("examId").asText()).isEqualTo(hsaMathId.toString());
        assertThat(activeNode.path("examCode").asText()).isEqualTo("DS-HSA-PART-MATH-MINI");
        assertThat(activeNode.path("examName").asText()).isEqualTo("HSA theo phần Toán (thu nhỏ)");
        assertThat(activeNode.path("source").asText()).isEqualTo("PURCHASE");
        assertThat(activeNode.path("attemptsTotal").asInt()).isEqualTo(4);
        assertThat(activeNode.path("attemptsUsed").asInt()).isEqualTo(2);
        assertThat(activeNode.path("attemptsReserved").asInt()).isEqualTo(1);
        assertThat(activeNode.path("attemptsRevoked").asInt()).isEqualTo(0);
        assertThat(activeNode.path("attemptsLeft").asInt()).isEqualTo(1);
        assertThat(activeNode.path("status").asText()).isEqualTo("ACTIVE");

        JsonNode inUseNode = entsById.get(inUseEnt.entitlementId());
        assertThat(inUseNode.path("examId").asText()).isEqualTo(vactToanId.toString());
        assertThat(inUseNode.path("examCode").asText()).isEqualTo("DS-VACT-SUBJECT-TOAN-MINI");
        assertThat(inUseNode.path("examName").asText()).isEqualTo("V-ACT theo môn Toán (thu nhỏ)");
        assertThat(inUseNode.path("source").asText()).isEqualTo("PURCHASE");
        assertThat(inUseNode.path("attemptsTotal").asInt()).isEqualTo(2);
        assertThat(inUseNode.path("attemptsUsed").asInt()).isEqualTo(1);
        assertThat(inUseNode.path("attemptsReserved").asInt()).isEqualTo(1);
        assertThat(inUseNode.path("attemptsRevoked").asInt()).isEqualTo(0);
        assertThat(inUseNode.path("attemptsLeft").asInt()).isEqualTo(0);
        assertThat(inUseNode.path("status").asText()).isEqualTo("IN_USE");

        JsonNode exhaustedNode = entsById.get(exhaustedEnt.entitlementId());
        assertThat(exhaustedNode.path("examId").asText()).isEqualTo(thptqgMathId.toString());
        assertThat(exhaustedNode.path("examCode").asText()).isEqualTo("DS-THPTQG-MATH-P1-MINI");
        assertThat(exhaustedNode.path("examName").asText()).isEqualTo("THPTQG Toán Phần I (thu nhỏ)");
        assertThat(exhaustedNode.path("attemptsTotal").asInt()).isEqualTo(1);
        assertThat(exhaustedNode.path("attemptsUsed").asInt()).isEqualTo(1);
        assertThat(exhaustedNode.path("attemptsReserved").asInt()).isEqualTo(0);
        assertThat(exhaustedNode.path("attemptsRevoked").asInt()).isEqualTo(0);
        assertThat(exhaustedNode.path("attemptsLeft").asInt()).isEqualTo(0);
        assertThat(exhaustedNode.path("status").asText()).isEqualTo("EXHAUSTED");

        JsonNode revokedNode = entsById.get(revokedEnt.entitlementId());
        assertThat(revokedNode.path("examId").asText()).isEqualTo(hsaReviewId.toString());
        assertThat(revokedNode.path("examCode").asText()).isEqualTo("DS-HSA-REVIEW-MINI");
        assertThat(revokedNode.path("examName").asText()).isEqualTo("HSA bài ôn Toán (thu nhỏ)");
        assertThat(revokedNode.path("attemptsTotal").asInt()).isEqualTo(2);
        assertThat(revokedNode.path("attemptsUsed").asInt()).isEqualTo(0);
        assertThat(revokedNode.path("attemptsReserved").asInt()).isEqualTo(0);
        assertThat(revokedNode.path("attemptsRevoked").asInt()).isEqualTo(2);
        assertThat(revokedNode.path("attemptsLeft").asInt()).isEqualTo(0);
        assertThat(revokedNode.path("status").asText()).isEqualTo("REVOKED");

        JsonNode partialRefundNode = entsById.get(partialRefundEnt.entitlementId());
        assertThat(partialRefundNode.path("examId").asText()).isEqualTo(thptqgInfoId.toString());
        assertThat(partialRefundNode.path("examCode").asText()).isEqualTo("DS-THPTQG-INFORMATICS-MINI");
        assertThat(partialRefundNode.path("examName").asText()).isEqualTo("THPTQG Tin học (thu nhỏ)");
        assertThat(partialRefundNode.path("source").asText()).isEqualTo("PURCHASE");
        assertThat(partialRefundNode.path("attemptsTotal").asInt()).isEqualTo(2);
        assertThat(partialRefundNode.path("attemptsUsed").asInt()).isEqualTo(1);
        assertThat(partialRefundNode.path("attemptsReserved").asInt()).isEqualTo(0);
        assertThat(partialRefundNode.path("attemptsRevoked").asInt()).isEqualTo(1);
        assertThat(partialRefundNode.path("attemptsLeft").asInt()).isEqualTo(0);
        assertThat(partialRefundNode.path("status").asText()).isEqualTo("EXHAUSTED");

        // Verify attempts[] raw size and startedAt DESC, createdAt DESC ordering before map indexing
        JsonNode attemptsArray = root.path("attempts");
        assertThat(attemptsArray.isArray()).isTrue();
        assertThat(attemptsArray).hasSize(9);
        List<UUID> actualAttemptOrder = new ArrayList<>();
        Map<UUID, JsonNode> attemptsById = new LinkedHashMap<>();
        for (JsonNode attNode : attemptsArray) {
            UUID attId = UUID.fromString(attNode.path("attemptId").asText());
            actualAttemptOrder.add(attId);
            attemptsById.put(attId, attNode);
        }
        assertThat(actualAttemptOrder).containsExactly(
                gradingAttempt.getId(),
                pendingRegrading.getId(),
                pendingNatural.getId(),
                gradingErrorAttempt.getId(),
                vactInProgress.getId(),
                vactScored.getId(),
                inProgressHsa.getId(),
                scoredZero.getId(),
                scoredPositive.getId());
        assertThat(attemptsById).doesNotContainKeys(startingId, abortedId, studentBAttempt.getId());

        JsonNode scoredPosNode = attemptsById.get(scoredPositive.getId());
        assertThat(scoredPosNode.path("examId").asText()).isEqualTo(hsaMathId.toString());
        assertThat(scoredPosNode.path("examName").asText()).isEqualTo("HSA theo phần Toán (thu nhỏ)");
        assertThat(scoredPosNode.path("examKindCode").asText()).isEqualTo("HSA");
        assertThat(scoredPosNode.path("scope").asText()).isEqualTo("PART");
        assertThat(scoredPosNode.path("attemptNo").asInt()).isEqualTo(1);
        assertThat(scoredPosNode.path("status").asText()).isEqualTo("SCORED");
        assertThat(scoredPosNode.path("startedAt").asText()).isEqualTo(scoredPosDb.getStartedAt().toString());
        assertThat(scoredPosNode.path("finishedAt").asText()).isEqualTo(scoredPosDb.getFinishedAt().toString());
        assertThat(scoredPosNode.path("score").decimalValue()).isEqualByComparingTo(scoredPosDb.getDisplayScore());
        assertThat(scoredPosNode.path("maxScore").decimalValue()).isEqualByComparingTo(scoredPosDb.getMaxScore());

        JsonNode scoredZeroNode = attemptsById.get(scoredZero.getId());
        assertThat(scoredZeroNode.path("status").asText()).isEqualTo("SCORED");
        assertThat(scoredZeroNode.path("score").isNumber()).isTrue();
        assertThat(scoredZeroNode.path("score").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(scoredZeroNode.path("maxScore").isNumber()).isTrue();
        assertThat(scoredZeroNode.path("maxScore").decimalValue()).isEqualByComparingTo(scoredZeroDb.getMaxScore());

        JsonNode vactScoredNode = attemptsById.get(vactScored.getId());
        assertThat(vactScoredNode.path("status").asText()).isEqualTo("SCORED");
        assertThat(vactScoredNode.path("score").decimalValue()).isEqualByComparingTo(vactScoredDb.getDisplayScore());
        assertThat(vactScoredNode.path("maxScore").decimalValue()).isEqualByComparingTo(vactScoredDb.getReferenceMax());

        JsonNode inProgNode = attemptsById.get(inProgressHsa.getId());
        assertThat(inProgNode.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(inProgNode.path("attemptNo").asInt()).isEqualTo(3);
        assertThat(inProgNode.path("finishedAt").isNull()).isTrue();
        assertThat(inProgNode.path("score").isNull()).isTrue();
        assertThat(inProgNode.path("maxScore").isNull()).isTrue();

        JsonNode pendingNatNode = attemptsById.get(pendingNatural.getId());
        assertThat(pendingNatNode.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(pendingNatNode.path("score").isNull()).isTrue();
        assertThat(pendingNatNode.path("maxScore").isNull()).isTrue();

        JsonNode pendingRegNode = attemptsById.get(pendingRegrading.getId());
        assertThat(pendingRegNode.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(pendingRegNode.path("score").isNull()).isTrue();
        assertThat(pendingRegNode.path("maxScore").isNull()).isTrue();

        JsonNode gradingErrNode = attemptsById.get(gradingErrorAttempt.getId());
        assertThat(gradingErrNode.path("status").asText()).isEqualTo("GRADING_ERROR");
        assertThat(gradingErrNode.path("score").isNull()).isTrue();
        assertThat(gradingErrNode.path("maxScore").isNull()).isTrue();

        JsonNode gradingNode = attemptsById.get(gradingAttempt.getId());
        assertThat(gradingNode.path("status").asText()).isEqualTo("GRADING");
        assertThat(gradingNode.path("score").isNull()).isTrue();
        assertThat(gradingNode.path("maxScore").isNull()).isTrue();

        assertThat(rawBody).doesNotContain(studentB.toString());
        assertThat(rawBody).doesNotContain(vactLangId.toString());
        assertThat(rawBody).doesNotContain("Internal stacktrace secret");
    }

    @Test
    void emptyStudentHasNoData() throws Exception {
        UUID emptyStudent = createStudent("lp-empty");
        UUID studentWithOnlyHiddenAttempts = createStudent("lp-hidden-only");
        UUID otherStudent = createStudent("lp-other-data");

        // Another student has both entitlement and scored attempt in DB
        GrantOrderResult.Line otherEnt = grant(otherStudent, "DS-HSA-PART-MATH-MINI", 2);
        Attempt otherAttempt = startAndAwaitOpen(otherStudent, otherEnt.entitlementId(), null);
        UUID otherSession = claimSession(otherStudent, otherAttempt.getId());
        submitAttempt(otherStudent, otherAttempt.getId(), otherSession, List.of());
        drainOutbox();

        // Second student has only STARTING and ABORTED rows and zero entitlements
        UUID hiddenExamId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        insertSyntheticTerminalAttempt(Ids.newId(), studentWithOnlyHiddenAttempts, hiddenExamId, "STARTING", null);
        insertSyntheticTerminalAttempt(
                Ids.newId(), studentWithOnlyHiddenAttempts, hiddenExamId, "ABORTED", "GENERATION_FAILED");

        for (UUID studentId : List.of(emptyStudent, studentWithOnlyHiddenAttempts)) {
            mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                            .with(asUser(Permissions.USER_LEARNING_VIEW)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(studentId.toString()))
                    .andExpect(jsonPath("$.dataAvailable").value(false))
                    .andExpect(jsonPath("$.entitlements").isEmpty())
                    .andExpect(jsonPath("$.attempts").isEmpty())
                    .andExpect(jsonPath("$.assessments").isEmpty())
                    .andExpect(jsonPath("$.practice").isEmpty())
                    .andExpect(jsonPath("$.supportRequests").isEmpty());
        }
    }

    @Test
    void permissionUnchanged() throws Exception {
        UUID studentId = createStudent("lp-perm");

        // Unauthenticated -> 401
        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId))
                .andExpect(status().isUnauthorized());

        // USER.LEARNING_VIEW alone -> 200
        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isOk());

        // Each other single permission alone -> 403
        for (String singlePermission : List.of(
                Permissions.USER_VIEW,
                Permissions.USER_MODIFY,
                Permissions.USER_ASSIGN,
                Permissions.ATTEMPT_VIEW,
                Permissions.ORDER_VIEW,
                Permissions.GRADING_VIEW,
                Permissions.PROMOTION_VIEW)) {
            mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                            .with(asUser(singlePermission)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        // STUDENT role -> 403
        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                        .with(asStudent()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Unknown UUID -> 404
        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", Ids.newId())
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // PROVISIONING user -> 404
        UUID provisioningId = Ids.newId();
        userAccountRepository.saveAndFlush(new UserAccount(
                provisioningId,
                "U9P" + suffix(),
                "lp.prov." + suffix(),
                "Provisioning Student",
                "lp.prov." + suffix() + "@example.test",
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.PROVISIONING,
                false,
                Instant.now(),
                null));
        createdUsers.add(provisioningId);

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", provisioningId)
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void noAnswerDataLeaks() throws Exception {
        String sfx = suffix();
        String studentAEmail = "lp.privacy.a." + sfx + "@example.test";
        String studentAPhone = "0987654" + sfx.substring(0, 3);
        String studentBEmail = "lp.privacy.b." + sfx + "@example.test";
        String learnerSecretAnswer = "SENTINEL_LEARNER_ESSAY_SECRET_" + sfx;
        String sensitiveGradingError = "SENTINEL_GRADING_ERR_" + sfx;

        UUID studentA = createStudentWithContact("lp-priv-a-" + sfx, studentAEmail, studentAPhone);
        UUID studentB = createStudentWithContact("lp-priv-b-" + sfx, studentBEmail, "0911111222");

        // Student A: submitted & scored MCQ attempt (real question versions with answer keys/solutions in DB)
        GrantOrderResult.Line hsaEnt = grant(studentA, "DS-HSA-PART-MATH-MINI", 3);
        Attempt scoredAttempt = startAndAwaitOpen(studentA, hsaEnt.entitlementId(), null);
        UUID scoredSession = claimSession(studentA, scoredAttempt.getId());
        AttemptItem scItem = attemptItemRepository
                .findByAttemptIdOrderByPartIdAscOrdAsc(scoredAttempt.getId())
                .stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        submitAttempt(
                studentA,
                scoredAttempt.getId(),
                scoredSession,
                List.of(new FinalAnswerChange(
                        scItem.getId(), 1L, new AnswerValue.SingleChoice(firstCorrectOptionId(scItem)))));
        drainOutbox();

        // Student A: IN_PROGRESS attempt
        mutableClock.advance(Duration.ofMinutes(5));
        startAndAwaitOpen(studentA, hsaEnt.entitlementId(), null);

        // Student A: BCA attempt with real submitted essay answer + rubric/solution in questionbank + regrading=true
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line bcaEnt = grant(studentA, "DS-BCA-FULL-MINI", 2);
        Attempt essayAttempt = startAndAwaitOpen(studentA, bcaEnt.entitlementId(), new PreChoiceInput("CA2", null));
        submitBcaWithEssay(studentA, essayAttempt.getId(), "<p>" + learnerSecretAnswer + "</p>");
        drainOutbox();
        assertThat(attemptRepository.findById(essayAttempt.getId()).orElseThrow().getState())
                .isEqualTo(AttemptState.PENDING_REVIEW);
        jdbc.update(
                """
                UPDATE attempt.attempt
                SET state = 'PENDING_REVIEW',
                    regrading = true,
                    raw_score = 42.50,
                    display_score = 42.50,
                    graded_at = now()
                WHERE id = ?
                """,
                essayAttempt.getId());

        // Student A: GRADING_ERROR attempt with sensitive grading_error string
        mutableClock.advance(Duration.ofMinutes(5));
        GrantOrderResult.Line errEnt = grant(studentA, "DS-THPTQG-MATH-P1-MINI", 1);
        Attempt errAttempt = startAndAwaitOpen(studentA, errEnt.entitlementId(), null);
        UUID errSession = claimSession(studentA, errAttempt.getId());
        submitAttempt(studentA, errAttempt.getId(), errSession, List.of());
        drainOutbox();
        jdbc.update(
                """
                UPDATE attempt.attempt
                SET state = 'GRADING_ERROR',
                    grading_error = ?,
                    raw_score = 77.00,
                    display_score = 77.00
                WHERE id = ?
                """,
                sensitiveGradingError,
                errAttempt.getId());

        // Student B: entitlement & scored attempt
        GrantOrderResult.Line bEnt = grant(studentB, "DS-VACT-PART-LANGUAGE-MINI", 2);
        Attempt bAttempt = startAndAwaitOpen(studentB, bEnt.entitlementId(), null);
        UUID bSession = claimSession(studentB, bAttempt.getId());
        submitAttempt(studentB, bAttempt.getId(), bSession, List.of());
        drainOutbox();

        MvcResult res = mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentA)
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode jsonNode = objectMapper.readTree(body);

        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(body);
        ForbiddenKeysAssert.assertNoForbiddenKeys(
                jsonNode,
                Set.of(
                        "answer",
                        "answers",
                        "finalAnswers",
                        "email",
                        "emailNormalized",
                        "gradingError",
                        "questionId",
                        "questionIds",
                        "body",
                        "snapshot",
                        "result",
                        "choices",
                        "phone"),
                "learning-profile-privacy");

        assertThat(body)
                .doesNotContain(studentAEmail)
                .doesNotContain(studentAPhone)
                .doesNotContain(studentBEmail)
                .doesNotContain(studentB.toString())
                .doesNotContain(bEnt.entitlementId().toString())
                .doesNotContain(bAttempt.getId().toString())
                .doesNotContain(learnerSecretAnswer)
                .doesNotContain(sensitiveGradingError);

        assertThat(jsonNode.path("attempts")).isNotEmpty();
        boolean sawRegradingEssayAttempt = false;
        for (JsonNode attemptNode : jsonNode.path("attempts")) {
            if (essayAttempt.getId().toString().equals(attemptNode.path("attemptId").asText())) {
                sawRegradingEssayAttempt = true;
                assertThat(attemptNode.path("status").asText()).isEqualTo("PENDING_REVIEW");
                assertThat(attemptNode.path("score").isNull()).isTrue();
                assertThat(attemptNode.path("maxScore").isNull()).isTrue();
            }
            if (!"SCORED".equals(attemptNode.path("status").asText())) {
                assertThat(attemptNode.path("score").isNull()).isTrue();
                assertThat(attemptNode.path("maxScore").isNull()).isTrue();
            }
        }
        assertThat(sawRegradingEssayAttempt).isTrue();
    }

    @Test
    void openApiSchemaUsesTypedLearningItems() throws Exception {
        Path openApiPath = resolveWorkspaceFile(Path.of("packages", "api-client", "openapi.json"));
        Path schemaDtsPath = resolveWorkspaceFile(Path.of("packages", "api-client", "src", "schema.d.ts"));

        JsonNode openApiRoot = objectMapper.readTree(Files.readString(openApiPath, StandardCharsets.UTF_8));
        JsonNode schemas = openApiRoot.path("components").path("schemas");

        String liveOpenApiRaw = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode liveSchemas = objectMapper.readTree(liveOpenApiRaw).path("components").path("schemas");
        for (String schemaName : List.of(
                "StudentLearningResponse", "StudentLearningEntitlement", "StudentLearningAttempt")) {
            assertThat(liveSchemas.path(schemaName))
                    .as("Live /v3/api-docs schema %s matches checked-in openapi.json", schemaName)
                    .isEqualTo(schemas.path(schemaName));
        }

        JsonNode learningRespSchema = schemas.path("StudentLearningResponse");
        assertThat(learningRespSchema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "userId",
                        "dataAvailable",
                        "entitlements",
                        "attempts",
                        "assessments",
                        "practice",
                        "supportRequests");
        JsonNode learningRespProps = learningRespSchema.path("properties");
        assertThat(learningRespProps.path("entitlements").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/StudentLearningEntitlement");
        assertThat(learningRespProps.path("attempts").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/StudentLearningAttempt");

        JsonNode entitlementSchema = schemas.path("StudentLearningEntitlement");
        assertThat(entitlementSchema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "entitlementId",
                        "examId",
                        "source",
                        "attemptsTotal",
                        "attemptsUsed",
                        "attemptsReserved",
                        "attemptsRevoked",
                        "attemptsLeft",
                        "status");
        JsonNode entitlementProps = entitlementSchema.path("properties");
        for (String field : List.of(
                "entitlementId",
                "examId",
                "examCode",
                "examName",
                "source",
                "attemptsTotal",
                "attemptsUsed",
                "attemptsReserved",
                "attemptsRevoked",
                "attemptsLeft",
                "status")) {
            assertThat(entitlementProps.has(field))
                    .as("StudentLearningEntitlement.%s in openapi.json", field)
                    .isTrue();
        }
        assertThat(entitlementProps.path("examCode").path("type"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("string", "null");
        assertThat(entitlementProps.path("examName").path("type"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("string", "null");
        assertThat(entitlementProps.path("source").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("PURCHASE", "CLASS");
        assertThat(entitlementProps.path("status").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("ACTIVE", "IN_USE", "EXHAUSTED", "REVOKED");

        JsonNode attemptSchema = schemas.path("StudentLearningAttempt");
        assertThat(attemptSchema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "attemptId", "examId", "examName", "examKindCode", "scope", "status");
        JsonNode attemptProps = attemptSchema.path("properties");
        for (String field : List.of(
                "attemptId",
                "examId",
                "examName",
                "examKindCode",
                "scope",
                "attemptNo",
                "status",
                "startedAt",
                "finishedAt",
                "score",
                "maxScore")) {
            assertThat(attemptProps.has(field))
                    .as("StudentLearningAttempt.%s in openapi.json", field)
                    .isTrue();
        }
        assertThat(attemptProps.path("attemptNo").path("type"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("integer", "null");
        assertThat(attemptProps.path("startedAt").path("type"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("string", "null");
        assertThat(attemptProps.path("startedAt").path("format").asText()).isEqualTo("date-time");
        assertThat(attemptProps.path("finishedAt").path("type"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("string", "null");
        assertThat(attemptProps.path("finishedAt").path("format").asText()).isEqualTo("date-time");
        assertThat(attemptProps.path("score").path("type"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("number", "null");
        assertThat(attemptProps.path("maxScore").path("type"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("number", "null");
        assertThat(attemptProps.path("status").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("IN_PROGRESS", "GRADING", "PENDING_REVIEW", "SCORED", "GRADING_ERROR");

        String schemaDts = Files.readString(schemaDtsPath, StandardCharsets.UTF_8);
        String responseBlock = extractComponentSchemaBlock(schemaDts, "StudentLearningResponse");
        assertThat(responseBlock)
                .contains("userId: string;")
                .contains("dataAvailable: boolean;")
                .contains("entitlements: components[\"schemas\"][\"StudentLearningEntitlement\"][];")
                .contains("attempts: components[\"schemas\"][\"StudentLearningAttempt\"][];")
                .doesNotContain("entitlements: Record<string, never>[]")
                .doesNotContain("attempts: Record<string, never>[]");

        String entitlementBlock = extractComponentSchemaBlock(schemaDts, "StudentLearningEntitlement");
        assertThat(entitlementBlock)
                .contains("entitlementId: string;")
                .contains("examId: string;")
                .contains("examCode?: string | null;")
                .contains("examName?: string | null;")
                .contains("source: \"PURCHASE\" | \"CLASS\";")
                .contains("status: \"ACTIVE\" | \"IN_USE\" | \"EXHAUSTED\" | \"REVOKED\";")
                .doesNotContain("Record<string, never>")
                .doesNotContain(": never");

        String attemptBlock = extractComponentSchemaBlock(schemaDts, "StudentLearningAttempt");
        assertThat(attemptBlock)
                .contains("attemptId: string;")
                .contains("examId: string;")
                .contains("examName: string;")
                .contains("examKindCode: string;")
                .contains("scope: string;")
                .contains("attemptNo?: number | null;")
                .contains("startedAt?: string | null;")
                .contains("finishedAt?: string | null;")
                .contains("score?: number | null;")
                .contains("maxScore?: number | null;")
                .contains("status: \"IN_PROGRESS\" | \"GRADING\" | \"PENDING_REVIEW\" | \"SCORED\" | \"GRADING_ERROR\";")
                .doesNotContain("Record<string, never>")
                .doesNotContain(": never");
    }

    private UUID createStudent(String label) {
        UUID userId = AttemptTestFixtures.createStudentAccount(jdbc, label + "-" + suffix());
        createdUsers.add(userId);
        return userId;
    }

    private UUID createStudentWithContact(String username, String email, String phone) {
        UUID userId = Ids.newId();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized, phone,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId,
                String.format("U%06d", seq),
                username,
                username.toLowerCase(),
                "LP Privacy Student",
                email,
                email.toLowerCase(),
                phone,
                now,
                userId,
                now,
                userId);
        createdUsers.add(userId);
        return userId;
    }

    private void projectStorefrontIfAbsent(UUID examId) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.storefront_product WHERE exam_id = ?", Integer.class, examId);
        if (existing == null || existing == 0) {
            storefrontProjectionService.project(examId);
            projectedExamIds.add(examId);
        }
    }

    private GrantOrderResult.Line grant(UUID userId, String examCode, int attempts) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        int versionNo = jdbc.queryForObject(
                "SELECT MAX(version_no) FROM exam.exam_version WHERE exam_id = ?", Integer.class, examId);
        return AttemptTestFixtures.grantExam(
                grantService, userId, examId, attempts, List.of(), "SCORE_AND_ANSWERS", versionNo);
    }

    private Attempt startAndAwaitOpen(UUID userId, UUID entitlementId, PreChoiceInput preChoice) throws Exception {
        MvcResult startRes = mockMvc.perform(post("/api/v1/me/attempts")
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("Idempotency-Key", "lp-start-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAttemptRequest(entitlementId, preChoice))))
                .andReturn();
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(
                objectMapper.readTree(startRes.getResponse().getContentAsString()).path("attemptId").asText());
        return awaitAttemptState(attemptId, AttemptState.IN_PROGRESS);
    }

    private UUID claimSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).path("sessionId").asText());
    }

    private void submitAttempt(
            UUID userId, UUID attemptId, UUID sessionId, List<FinalAnswerChange> finals) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("Idempotency-Key", "lp-submit-" + UUID.randomUUID())
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(finals))))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    private void submitBcaWithEssay(UUID userId, UUID attemptId, String essayHtml) throws Exception {
        UUID sessionId = claimSession(userId, attemptId);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId).stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();
        MvcResult fin0 = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                                attemptId, parts.get(0).getId())
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of(
                                new FinalAnswerChange(
                                        essayItem.getId(), 1L, new AnswerValue.Essay(essayHtml, 4)))))))
                .andReturn();
        assertThat(fin0.getResponse().getStatus()).isEqualTo(200);
        MvcResult fin1 = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
                                attemptId, parts.get(1).getId())
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andReturn();
        assertThat(fin1.getResponse().getStatus()).isEqualTo(200);
        submitAttempt(userId, attemptId, sessionId, List.of());
    }

    private void insertSyntheticTerminalAttempt(
            UUID attemptId, UUID userId, UUID examId, String state, String abortReason) {
        jdbc.update(
                """
                INSERT INTO attempt.attempt (
                    id, user_id, exam_id, exam_version_no, entitlement_id, grant_id,
                    exam_name, exam_kind_code, scope, timing, navigation_mode,
                    choices, seed, generation_token, state, regrading, abort_reason, result_view,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, 1, ?, ?, 'Hidden Attempt', 'SPT', 'SUBJECT', 'SHARED', 'SEQUENTIAL',
                        '{}'::jsonb, 1, gen_random_uuid(), ?, false, ?, 'SCORE', now(), now())
                """,
                attemptId,
                userId,
                examId,
                Ids.newId(),
                Ids.newId(),
                state,
                abortReason);
    }

    private String firstCorrectOptionId(AttemptItem item) {
        var snap = questionSourcePort
                .loadVersions(List.of(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo())))
                .getFirst()
                .snapshot();
        for (JsonNode opt : snap.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                return opt.path("id").asText();
            }
        }
        throw new IllegalStateException("No correct option found for " + item.getQuestionId());
    }

    private Attempt awaitAttemptState(UUID attemptId, AttemptState... targets) throws InterruptedException {
        Set<AttemptState> targetSet = EnumSet.noneOf(AttemptState.class);
        targetSet.addAll(List.of(targets));
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Attempt current = attemptRepository.findById(attemptId).orElse(null);
            if (current != null && targetSet.contains(current.getState())) {
                return current;
            }
            Thread.sleep(25);
        }
        Attempt latest = attemptRepository.findById(attemptId).orElse(null);
        throw new AssertionError("Timed out waiting for attempt " + attemptId + " to reach " + targetSet
                + ", actual=" + (latest == null ? "MISSING" : latest.getState()));
    }

    private void drainOutbox() {
        while (outboxRelay.relayBatch(500) > 0) {
            // drain
        }
    }

    private static String extractComponentSchemaBlock(String schemaDts, String schemaName) {
        Pattern header = Pattern.compile("(?m)^([ \\t]*)" + Pattern.quote(schemaName) + ":\\s*\\{");
        Matcher headerMatcher = header.matcher(schemaDts);
        assertThat(headerMatcher.find())
                .as("Found schema block header for %s in schema.d.ts", schemaName)
                .isTrue();
        String indent = headerMatcher.group(1);
        Pattern end = Pattern.compile("(?m)^" + Pattern.quote(indent) + "\\};");
        Matcher endMatcher = end.matcher(schemaDts);
        assertThat(endMatcher.find(headerMatcher.end()))
                .as("Found schema block end for %s in schema.d.ts", schemaName)
                .isTrue();
        return schemaDts.substring(headerMatcher.start(), endMatcher.end());
    }

    private static Path resolveWorkspaceFile(Path relativeFromRepoRoot) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativeFromRepoRoot);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "Could not locate " + relativeFromRepoRoot + " from " + Path.of("").toAbsolutePath());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
