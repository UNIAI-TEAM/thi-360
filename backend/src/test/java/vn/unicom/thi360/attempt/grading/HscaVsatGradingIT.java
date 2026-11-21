package vn.unicom.thi360.attempt.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptGradingService;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.ExamListingFixture;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.ForbiddenKeysAssert;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Integration tests for H-SCA half-credit grading, V-SAT scoring rules, {@code displaySegments}
 * result attribution, preview-vs-real parity, regrade idempotency, and PostgreSQL scale-4 precision
 * (AC-4..AC-9, AC-11).
 */
@Import(AttemptTestFixtures.MutableClockConfig.class)
class HscaVsatGradingIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MutableClock mutableClock;
    @Autowired private EntityManager entityManager;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private QuestionSourcePort questionSourcePort;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;
    @Autowired private AttemptItemRepository attemptItemRepository;
    @Autowired private AttemptEventRepository attemptEventRepository;
    @Autowired private AttemptGradingService attemptGradingService;
    @Autowired private OutboxRelay outboxRelay;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdExamIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();

    @BeforeEach
    void initDsAndClock() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        drainAllOutbox();
    }

    @AfterEach
    void cleanup() {
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        for (UUID userId : createdUserIds) {
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
        for (UUID examId : createdExamIds) {
            cleanupSyntheticExam(examId);
        }
        createdExamIds.clear();
        AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestionIds);
        createdQuestionIds.clear();
        for (UUID subjectId : createdSubjectIds) {
            jdbc.update("DELETE FROM catalog.catalog_item WHERE id = ?", subjectId);
        }
        createdSubjectIds.clear();
    }

    @Test
    void hscaMathAttemptScores() throws Exception {
        UUID student = createStudent("hsca-math");
        GrantOrderResult.Line grant = grant(student, "DS-HSCA-TOAN");
        Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);

        List<AttemptItem> allItems = loadOrderedItems(attempt.getId(), parts);
        Map<VersionKey, JsonNode> snaps = loadSnapshots(allItems);

        List<AttemptItem> scItems = leafItems(allItems, QuestionType.SINGLE_CHOICE, false);
        List<AttemptItem> mcItems = leafItems(allItems, QuestionType.MULTIPLE_CHOICE, false);
        List<AttemptItem> setChildItems = leafItems(allItems, QuestionType.SINGLE_CHOICE, true);
        List<AttemptItem> saItems = leafItems(allItems, QuestionType.SHORT_ANSWER, false);

        assertThat(scItems).hasSize(20);
        assertThat(mcItems).hasSize(5);
        assertThat(setChildItems).hasSize(5);
        assertThat(saItems).hasSize(10);

        Map<UUID, AnswerValue> answers = new LinkedHashMap<>();

        // 20 SC (0.25 each): 10 correct (2.50), 5 wrong (0), 5 blank (0)
        for (int i = 0; i < 10; i++) {
            AttemptItem item = scItems.get(i);
            answers.put(item.getId(), correctSingleChoice(snaps.get(key(item))));
        }
        for (int i = 10; i < 15; i++) {
            AttemptItem item = scItems.get(i);
            answers.put(item.getId(), wrongSingleChoice(snaps.get(key(item))));
        }

        // 5 MC (0.25 each): pick up to 2 PARTIAL (>=2 correct options) items to test 0.125 half-credit
        List<AttemptItem> mcPartialCandidates = new ArrayList<>();
        List<AttemptItem> mcOther = new ArrayList<>();
        for (AttemptItem item : mcItems) {
            JsonNode snap = snaps.get(key(item));
            if ("PARTIAL".equals(snap.path("body").path("scoringRule").asText())
                    && countCorrectOptions(snap) >= 2) {
                mcPartialCandidates.add(item);
            } else {
                mcOther.add(item);
            }
        }
        List<AttemptItem> mcChosenPartial = new ArrayList<>();
        while (mcChosenPartial.size() < 2 && !mcPartialCandidates.isEmpty()) {
            mcChosenPartial.add(mcPartialCandidates.removeFirst());
        }
        List<AttemptItem> mcRemaining = new ArrayList<>();
        mcRemaining.addAll(mcOther);
        mcRemaining.addAll(mcPartialCandidates);
        while (mcChosenPartial.size() < 2) {
            mcChosenPartial.add(mcRemaining.removeFirst());
        }
        AttemptItem mcComplete = mcRemaining.get(0);
        AttemptItem mcPartialA = mcChosenPartial.get(0);
        AttemptItem mcPartialB = mcChosenPartial.get(1);
        AttemptItem mcWrong = mcRemaining.get(1);
        AttemptItem mcBlank = mcRemaining.get(2);

        answers.put(mcComplete.getId(), completeMultipleChoice(snaps.get(key(mcComplete))));
        answers.put(mcPartialA.getId(), partialMultipleChoice(snaps.get(key(mcPartialA))));
        answers.put(mcPartialB.getId(), partialMultipleChoice(snaps.get(key(mcPartialB))));
        answers.put(mcWrong.getId(), wrongMultipleChoice(snaps.get(key(mcWrong))));

        // 5 QUESTION_SET children (0.25 each): 3 correct (0.75), 1 wrong (0), 1 blank (0)
        for (int i = 0; i < 3; i++) {
            AttemptItem item = setChildItems.get(i);
            answers.put(item.getId(), correctSingleChoice(snaps.get(key(item))));
        }
        answers.put(setChildItems.get(3).getId(), wrongSingleChoice(snaps.get(key(setChildItems.get(3)))));

        // 10 SHORT_ANSWER (0.25 each): 4 correct (1.00), 3 wrong (0), 3 blank (0)
        for (int i = 0; i < 4; i++) {
            AttemptItem item = saItems.get(i);
            answers.put(item.getId(), correctShortAnswer(snaps.get(key(item))));
        }
        for (int i = 4; i < 7; i++) {
            answers.put(saItems.get(i).getId(), new AnswerValue.ShortAnswer("999999"));
        }

        completeSequentialAttempt(student, attempt.getId(), sessionId, parts, answers);

        BigDecimal expectedMcScore = new BigDecimal("0.2500");
        int expectedMcCorrect = 1;
        int expectedMcPartial = 0;
        int expectedMcWrong = 1;
        Map<UUID, ItemOutcome> expectedPartialOutcome = new HashMap<>();
        Map<UUID, BigDecimal> expectedPartialEarned = new HashMap<>();
        for (AttemptItem mc : List.of(mcPartialA, mcPartialB)) {
            JsonNode snap = snaps.get(key(mc));
            boolean isPartialRule = "PARTIAL".equals(snap.path("body").path("scoringRule").asText());
            int correctOptCount = countCorrectOptions(snap);
            if (isPartialRule && correctOptCount >= 2) {
                expectedPartialOutcome.put(mc.getId(), ItemOutcome.PARTIAL);
                expectedPartialEarned.put(mc.getId(), new BigDecimal("0.1250"));
                expectedMcScore = expectedMcScore.add(new BigDecimal("0.1250"));
                expectedMcPartial++;
            } else if (correctOptCount == 1) {
                expectedPartialOutcome.put(mc.getId(), ItemOutcome.CORRECT);
                expectedPartialEarned.put(mc.getId(), new BigDecimal("0.2500"));
                expectedMcScore = expectedMcScore.add(new BigDecimal("0.2500"));
                expectedMcCorrect++;
            } else {
                expectedPartialOutcome.put(mc.getId(), ItemOutcome.WRONG);
                expectedPartialEarned.put(mc.getId(), new BigDecimal("0.0000"));
                expectedMcWrong++;
            }
        }
        BigDecimal expectedTotal = new BigDecimal("4.25").add(expectedMcScore).setScale(2, RoundingMode.HALF_UP);

        Attempt scored = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(scored.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scored.getMaxScore()).isEqualByComparingTo("10.00");
        assertThat(scored.getRawScore()).isEqualByComparingTo(expectedTotal);
        assertThat(scored.getDisplayScore()).isEqualByComparingTo(expectedTotal);

        Map<UUID, AttemptItem> reloadedById = byId(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId()));
        assertThat(reloadedById.get(mcComplete.getId()).getOutcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(reloadedById.get(mcComplete.getId()).getEarnedPoints()).isEqualByComparingTo("0.2500");

        for (AttemptItem mc : List.of(mcPartialA, mcPartialB)) {
            AttemptItem reloaded = reloadedById.get(mc.getId());
            assertThat(reloaded.getOutcome()).isEqualTo(expectedPartialOutcome.get(mc.getId()));
            assertThat(reloaded.getEarnedPoints()).isEqualByComparingTo(expectedPartialEarned.get(mc.getId()));
        }
        assertThat(reloadedById.get(mcWrong.getId()).getOutcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(reloadedById.get(mcWrong.getId()).getEarnedPoints()).isEqualByComparingTo("0.0000");
        assertThat(reloadedById.get(mcBlank.getId()).getOutcome()).isEqualTo(ItemOutcome.BLANK);
        assertThat(reloadedById.get(mcBlank.getId()).getEarnedPoints()).isEqualByComparingTo("0.0000");

        JsonNode resultJson = getResultOk(student, attempt.getId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(resultJson);
        assertThat(resultJson.path("examKindCode").asText()).isEqualTo("HSCA");
        assertThat(resultJson.path("rawScore").decimalValue()).isEqualByComparingTo(expectedTotal);
        assertThat(resultJson.path("displayScore").decimalValue()).isEqualByComparingTo(expectedTotal);
        assertThat(resultJson.path("rawMaxScore").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(resultJson.path("scaleMax").decimalValue()).isEqualByComparingTo("10.00");
        // Statistics: SC(10/5/5) + MC(expected + 1 blank) + SET(3/1/1) + SA(4/3/3) = 40
        JsonNode stats = resultJson.path("statistics");
        assertThat(stats.path("correctCount").asInt()).isEqualTo(17 + expectedMcCorrect);
        assertThat(stats.path("partialCount").asInt()).isEqualTo(expectedMcPartial);
        assertThat(stats.path("wrongCount").asInt()).isEqualTo(9 + expectedMcWrong);
        assertThat(stats.path("blankCount").asInt()).isEqualTo(10);
        assertThat(stats.path("totalCount").asInt()).isEqualTo(40);
    }

    @Test
    void vsatRulesMatchPublishedTemplates() throws Exception {
        // Part A: DS-VSAT-TOAN (150 max: TF [0,1,2,3,6], SC 6, MATCHING 1.5/pair, SA 6)
        UUID mathStudent = createStudent("vsat-math");
        GrantOrderResult.Line mathGrant = grant(mathStudent, "DS-VSAT-TOAN");
        Attempt mathAttempt = startAndAwaitOpen(mathStudent, mathGrant.entitlementId());
        UUID mathSession = claimSession(mathStudent, mathAttempt.getId());

        List<AttemptPart> mathParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(mathAttempt.getId());
        assertThat(mathParts).hasSize(4);

        List<AttemptItem> mathItems = loadOrderedItems(mathAttempt.getId(), mathParts);
        Map<VersionKey, JsonNode> mathSnaps = loadSnapshots(mathItems);

        List<AttemptItem> tfItems = leafItems(mathItems, QuestionType.TRUE_FALSE, false);
        List<AttemptItem> scItems = leafItems(mathItems, QuestionType.SINGLE_CHOICE, true);
        List<AttemptItem> matchItems = leafItems(mathItems, QuestionType.MATCHING, false);
        List<AttemptItem> saItems = leafItems(mathItems, QuestionType.SHORT_ANSWER, false);

        assertThat(tfItems).hasSize(9);
        assertThat(scItems).hasSize(6);
        assertThat(matchItems).hasSize(5);
        assertThat(saItems).hasSize(5);

        Map<UUID, AnswerValue> mathAnswers = new LinkedHashMap<>();
        // TF: 0, 1, 2, 3, 4 matched statements across items 0..4 (rest blank) -> 0 + 1 + 2 + 3 + 6 = 12
        for (int matched = 0; matched <= 4; matched++) {
            AttemptItem tf = tfItems.get(matched);
            mathAnswers.put(tf.getId(), trueFalseWithMatchedCount(mathSnaps.get(key(tf)), matched));
        }
        // SC in QUESTION_SET: 3 correct (18), 1 wrong (0), 2 blank (0)
        for (int i = 0; i < 3; i++) {
            mathAnswers.put(scItems.get(i).getId(), correctSingleChoice(mathSnaps.get(key(scItems.get(i)))));
        }
        mathAnswers.put(scItems.get(3).getId(), wrongSingleChoice(mathSnaps.get(key(scItems.get(3)))));
        // MATCHING: 0, 1, 2, 3, 4 matched pairs -> 0 + 1.5 + 3.0 + 4.5 + 6.0 = 15.0
        for (int pairs = 0; pairs <= 4; pairs++) {
            AttemptItem m = matchItems.get(pairs);
            mathAnswers.put(m.getId(), matchingWithMatchedCount(mathSnaps.get(key(m)), pairs));
        }
        // SHORT_ANSWER: 2 correct (12), 1 wrong (0), 2 blank (0)
        mathAnswers.put(saItems.get(0).getId(), correctShortAnswer(mathSnaps.get(key(saItems.get(0)))));
        mathAnswers.put(saItems.get(1).getId(), correctShortAnswer(mathSnaps.get(key(saItems.get(1)))));
        mathAnswers.put(saItems.get(2).getId(), new AnswerValue.ShortAnswer("999999"));

        completeSequentialAttempt(mathStudent, mathAttempt.getId(), mathSession, mathParts, mathAnswers);

        Attempt scoredMath = attemptRepository.findById(mathAttempt.getId()).orElseThrow();
        assertThat(scoredMath.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scoredMath.getMaxScore()).isEqualByComparingTo("150.00");
        // 12 (TF) + 18 (SC) + 15 (MATCHING) + 12 (SA) = 57.00
        assertThat(scoredMath.getRawScore()).isEqualByComparingTo("57.00");

        Map<UUID, AttemptItem> mathReloaded = byId(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(mathAttempt.getId()));
        BigDecimal[] expectedTf = {new BigDecimal("0"), new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("6")};
        for (int i = 0; i <= 4; i++) {
            assertThat(mathReloaded.get(tfItems.get(i).getId()).getEarnedPoints()).isEqualByComparingTo(expectedTf[i]);
        }
        BigDecimal[] expectedMatch = {new BigDecimal("0"), new BigDecimal("1.5"), new BigDecimal("3.0"), new BigDecimal("4.5"), new BigDecimal("6.0")};
        for (int i = 0; i <= 4; i++) {
            assertThat(mathReloaded.get(matchItems.get(i).getId()).getEarnedPoints()).isEqualByComparingTo(expectedMatch[i]);
        }
        assertThat(mathReloaded.get(scItems.get(0).getId()).getEarnedPoints()).isEqualByComparingTo("6.00");
        assertThat(mathReloaded.get(saItems.get(0).getId()).getEarnedPoints()).isEqualByComparingTo("6.00");

        JsonNode mathResultJson = getResultOk(mathStudent, mathAttempt.getId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(mathResultJson);
        assertThat(mathResultJson.path("rawMaxScore").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(mathResultJson.path("scaleMax").decimalValue()).isEqualByComparingTo("150.00");

        // Part B: DS-VSAT-ANH (150 max: CLOZE 6/blank partial & full + TF + SC + MATCHING)
        UUID engStudent = createStudent("vsat-eng-rules");
        GrantOrderResult.Line engGrant = grant(engStudent, "DS-VSAT-ANH");
        Attempt engAttempt = startAndAwaitOpen(engStudent, engGrant.entitlementId());
        UUID engSession = claimSession(engStudent, engAttempt.getId());

        List<AttemptPart> engParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(engAttempt.getId());
        assertThat(engParts).hasSize(1);
        List<AttemptItem> engItems = loadOrderedItems(engAttempt.getId(), engParts);
        Map<VersionKey, JsonNode> engSnaps = loadSnapshots(engItems);

        AttemptItem cloze = engItems.stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.CLOZE)
                .findFirst()
                .orElseThrow();
        assertThat(cloze.getUnitCount()).isEqualTo(5);
        assertThat(cloze.getPointsMax()).isEqualByComparingTo("30.00");

        Map<UUID, AnswerValue> engAnswers = new LinkedHashMap<>();
        // Answer 3 of 5 blanks right, 1 wrong, 1 blank -> 18.00 PARTIAL
        engAnswers.put(cloze.getId(), clozeWithCorrectCount(engSnaps.get(key(cloze)), 3, 1));
        completeSequentialAttempt(engStudent, engAttempt.getId(), engSession, engParts, engAnswers);

        Attempt scoredEng = attemptRepository.findById(engAttempt.getId()).orElseThrow();
        assertThat(scoredEng.getMaxScore()).isEqualByComparingTo("150.00");
        assertThat(scoredEng.getRawScore()).isEqualByComparingTo("18.00");
        AttemptItem reloadedCloze = attemptItemRepository.findById(cloze.getId()).orElseThrow();
        assertThat(reloadedCloze.getOutcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(reloadedCloze.getEarnedPoints()).isEqualByComparingTo("18.00");
    }

    @Test
    void vsatLiteratureScaleIs120() throws Exception {
        UUID student = createStudent("vsat-van");
        GrantOrderResult.Line grant = grant(student, "DS-VSAT-VAN");
        Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(4);

        List<AttemptItem> allItems = loadOrderedItems(attempt.getId(), parts);
        List<AttemptItem> leaves = leafItems(allItems);
        assertThat(leaves).hasSize(20);
        assertThat(leaves).noneMatch(i -> i.getQuestionType() == QuestionType.ESSAY);
        assertThat(leaves).noneMatch(i -> i.getPartId().equals(parts.get(3).getId()));

        Map<VersionKey, JsonNode> snaps = loadSnapshots(allItems);
        Map<UUID, AnswerValue> allCorrect = new LinkedHashMap<>();
        for (AttemptItem item : leaves) {
            allCorrect.put(item.getId(), fullCorrectAnswer(item, snaps.get(key(item))));
        }

        completeSequentialAttempt(student, attempt.getId(), sessionId, parts, allCorrect);

        Attempt scored = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(scored.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scored.getMaxScore()).isEqualByComparingTo("120.00");
        assertThat(scored.getRawScore()).isEqualByComparingTo("120.00");
        assertThat(scored.getDisplayScore()).isEqualByComparingTo("120.00");

        JsonNode json = getResultOk(student, attempt.getId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json);
        assertThat(json.path("rawMaxScore").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(json.path("scaleMax").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(json.path("rawScore").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(json.path("displayScore").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(json.has("segments")).isFalse();
    }

    @Test
    void segmentsSumByRange() throws Exception {
        UUID student = createStudent("vsat-anh-segs");
        GrantOrderResult.Line grant = grant(student, "DS-VSAT-ANH");
        Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(1);
        List<AttemptItem> allItems = loadOrderedItems(attempt.getId(), parts);
        List<AttemptItem> leaves = leafItems(allItems);
        // 7 TF + 2 TF children + 6 SC children + 5 MATCHING + 1 CLOZE(5 units) = 21 leaf rows, 25 positions
        assertThat(leaves).hasSize(21);
        Map<VersionKey, JsonNode> snaps = loadSnapshots(allItems);

        Map<UUID, AnswerValue> answers = new LinkedHashMap<>();
        // P1 (pos 1..9): pos 1 (4/4 -> 6), pos 2 (2/4 -> 2), pos 8 (3/4 -> 3), pos 9 (1/4 -> 1) => 12.00
        answers.put(leaves.get(0).getId(), trueFalseWithMatchedCount(snaps.get(key(leaves.get(0))), 4));
        answers.put(leaves.get(1).getId(), trueFalseWithMatchedCount(snaps.get(key(leaves.get(1))), 2));
        answers.put(leaves.get(7).getId(), trueFalseWithMatchedCount(snaps.get(key(leaves.get(7))), 3));
        answers.put(leaves.get(8).getId(), trueFalseWithMatchedCount(snaps.get(key(leaves.get(8))), 1));
        // P2 (pos 10..15): 4 correct SC children => 24.00
        for (int idx = 9; idx <= 12; idx++) {
            answers.put(leaves.get(idx).getId(), correctSingleChoice(snaps.get(key(leaves.get(idx)))));
        }
        // P3 (pos 16..20): pos 16 (3 pairs -> 4.5), pos 17 (4 pairs -> 6.0) => 10.50
        answers.put(leaves.get(15).getId(), matchingWithMatchedCount(snaps.get(key(leaves.get(15))), 3));
        answers.put(leaves.get(16).getId(), matchingWithMatchedCount(snaps.get(key(leaves.get(16))), 4));
        // P4 (pos 21..25): CLOZE 3 correct, 1 wrong, 1 blank => 18.00
        AttemptItem cloze = leaves.get(20);
        assertThat(cloze.getQuestionType()).isEqualTo(QuestionType.CLOZE);
        answers.put(cloze.getId(), clozeWithCorrectCount(snaps.get(key(cloze)), 3, 1));

        completeSequentialAttempt(student, attempt.getId(), sessionId, parts, answers);

        JsonNode resultVi = getResultOk(student, attempt.getId(), Locale.forLanguageTag("vi"));
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(resultVi);

        assertThat(resultVi.path("rawScore").decimalValue()).isEqualByComparingTo("64.50");
        assertThat(resultVi.path("displayScore").decimalValue()).isEqualByComparingTo("64.50");
        assertThat(resultVi.path("rawMaxScore").decimalValue()).isEqualByComparingTo("150.00");

        JsonNode segments = resultVi.path("segments");
        assertThat(segments.isArray()).isTrue();
        assertThat(segments).hasSize(4);

        assertSegment(segments.get(0), "P1", "Phần I", 1, 9, "12.00", "54.00", 9);
        assertSegment(segments.get(1), "P2", "Phần II", 10, 15, "24.00", "36.00", 6);
        assertSegment(segments.get(2), "P3", "Phần III", 16, 20, "10.50", "30.00", 5);
        assertSegment(segments.get(3), "P4", "Phần IV", 21, 25, "18.00", "30.00", 5);

        BigDecimal segScoreSum = BigDecimal.ZERO;
        BigDecimal segMaxSum = BigDecimal.ZERO;
        for (JsonNode seg : segments) {
            segScoreSum = segScoreSum.add(seg.path("score").decimalValue());
            segMaxSum = segMaxSum.add(seg.path("maxPoints").decimalValue());
        }
        assertThat(segScoreSum).isEqualByComparingTo(resultVi.path("rawScore").decimalValue());
        assertThat(segScoreSum).isEqualByComparingTo(resultVi.path("displayScore").decimalValue());
        assertThat(segMaxSum).isEqualByComparingTo("150.00");

        // Segment names from snapshot stay verbatim even in English locale (BS §18)
        JsonNode resultEn = getResultOk(student, attempt.getId(), Locale.ENGLISH);
        assertThat(resultEn.path("segments").get(0).path("name").asText()).isEqualTo("Phần I");
        assertThat(resultEn.path("segments").get(3).path("name").asText()).isEqualTo("Phần IV");
    }

    @Test
    void splitClusterCountsByPosition() throws Exception {
        UUID student = createStudent("vsat-anh-split-cluster");
        GrantOrderResult.Line grant = grant(student, "DS-VSAT-ANH");
        Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> allItems = loadOrderedItems(attempt.getId(), parts);
        List<AttemptItem> leaves = leafItems(allItems);
        Map<VersionKey, JsonNode> snaps = loadSnapshots(allItems);

        // Positions 8..15 share ONE QUESTION_SET parent (pos 8..9 TF in P1, pos 10..15 SC in P2)
        List<AttemptItem> clusterChildren = leaves.subList(7, 15);
        assertThat(clusterChildren).hasSize(8);
        UUID sharedParentId = clusterChildren.getFirst().getParentItemId();
        assertThat(sharedParentId).isNotNull();
        assertThat(clusterChildren).allSatisfy(c -> assertThat(c.getParentItemId()).isEqualTo(sharedParentId));
        assertThat(clusterChildren.get(0).getQuestionType()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(clusterChildren.get(1).getQuestionType()).isEqualTo(QuestionType.TRUE_FALSE);
        for (int i = 2; i < 8; i++) {
            assertThat(clusterChildren.get(i).getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        }

        // Answer ONLY positions 8..15 (leaving pos 1..7, 16..20, 21..25 blank):
        // pos 8 -> 4/4 (6.00), pos 9 -> 2/4 (2.00) => P1 = 8.00
        // pos 10..14 -> 5 correct SC (30.00), pos 15 -> 1 wrong SC (0.00) => P2 = 30.00
        Map<UUID, AnswerValue> answers = new LinkedHashMap<>();
        answers.put(clusterChildren.get(0).getId(), trueFalseWithMatchedCount(snaps.get(key(clusterChildren.get(0))), 4));
        answers.put(clusterChildren.get(1).getId(), trueFalseWithMatchedCount(snaps.get(key(clusterChildren.get(1))), 2));
        for (int i = 2; i < 7; i++) {
            answers.put(clusterChildren.get(i).getId(), correctSingleChoice(snaps.get(key(clusterChildren.get(i)))));
        }
        answers.put(clusterChildren.get(7).getId(), wrongSingleChoice(snaps.get(key(clusterChildren.get(7)))));

        completeSequentialAttempt(student, attempt.getId(), sessionId, parts, answers);

        JsonNode json = getResultOk(student, attempt.getId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json);
        JsonNode segments = json.path("segments");
        assertThat(segments).hasSize(4);
        assertThat(segments.get(0).path("score").decimalValue()).isEqualByComparingTo("8.00");
        assertThat(segments.get(1).path("score").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(segments.get(2).path("score").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(segments.get(3).path("score").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(json.path("rawScore").decimalValue()).isEqualByComparingTo("38.00");
    }

    @Test
    void noSegmentsWhenAbsent() throws Exception {
        for (String examCode : List.of("DS-HSCA-TOAN", "DS-VSAT-TOAN")) {
            UUID student = createStudent("no-segs-" + examCode);
            GrantOrderResult.Line grant = grant(student, examCode);
            Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
            UUID sessionId = claimSession(student, attempt.getId());
            List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

            completeSequentialAttempt(student, attempt.getId(), sessionId, parts, Map.of());

            JsonNode json = getResultOk(student, attempt.getId());
            assertThat(json.has("segments"))
                    .as("Exam %s without displaySegments must omit segments field", examCode)
                    .isFalse();
        }
    }

    @Test
    void segmentsReadPublishedSnapshot() throws Exception {
        UUID student = createStudent("seg-snapshot");
        GrantOrderResult.Line grant = grant(student, "DS-VSAT-ANH");
        Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> leaves = leafItems(loadOrderedItems(attempt.getId(), parts));
        Map<VersionKey, JsonNode> snaps = loadSnapshots(leaves);

        Map<UUID, AnswerValue> answers = new LinkedHashMap<>();
        answers.put(leaves.get(0).getId(), trueFalseWithMatchedCount(snaps.get(key(leaves.get(0))), 4)); // +6 in P1
        answers.put(leaves.get(9).getId(), correctSingleChoice(snaps.get(key(leaves.get(9))))); // +6 in P2
        completeSequentialAttempt(student, attempt.getId(), sessionId, parts, answers);

        UUID examId = attempt.getExamId();
        String origTemplateStructure = jdbc.queryForObject(
                "SELECT structure::text FROM exam.exam_template WHERE code = 'VSAT_SUBJECT_ANH'",
                String.class);
        String origDraftStructure = jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?",
                String.class,
                examId);

        try {
            ObjectNode mutatedTemplate = (ObjectNode) objectMapper.readTree(origTemplateStructure);
            ArrayNode mutatedTemplateSegs = objectMapper.createArrayNode();
            mutatedTemplateSegs.add(objectMapper.createObjectNode()
                    .put("code", "MUTATED")
                    .put("name", "Mutated Template Segment")
                    .put("fromPosition", 1)
                    .put("toPosition", 25)
                    .put("commandCount", 67)
                    .put("maxPoints", 999));
            ((ObjectNode) mutatedTemplate.path("parts").get(0)).set("displaySegments", mutatedTemplateSegs);
            jdbc.update(
                    "UPDATE exam.exam_template SET structure = ?::jsonb WHERE code = 'VSAT_SUBJECT_ANH'",
                    objectMapper.writeValueAsString(mutatedTemplate));

            ObjectNode mutatedDraft = (ObjectNode) objectMapper.readTree(origDraftStructure);
            ArrayNode mutatedDraftSegs = objectMapper.createArrayNode();
            mutatedDraftSegs.add(objectMapper.createObjectNode()
                    .put("code", "DRAFT_CHG")
                    .put("name", "Mutated Draft Segment")
                    .put("fromPosition", 1)
                    .put("toPosition", 25)
                    .put("commandCount", 67)
                    .put("maxPoints", 777));
            ((ObjectNode) mutatedDraft.path("parts").get(0)).set("displaySegments", mutatedDraftSegs);
            jdbc.update(
                    "UPDATE exam.exam_structure SET parts = ?::jsonb WHERE exam_id = ?",
                    objectMapper.writeValueAsString(mutatedDraft),
                    examId);

            JsonNode resultAfterMutation = getResultOk(student, attempt.getId());
            JsonNode segments = resultAfterMutation.path("segments");
            assertThat(segments).hasSize(4);
            assertSegment(segments.get(0), "P1", "Phần I", 1, 9, "6.00", "54.00", 9);
            assertSegment(segments.get(1), "P2", "Phần II", 10, 15, "6.00", "36.00", 6);
            assertSegment(segments.get(2), "P3", "Phần III", 16, 20, "0.00", "30.00", 5);
            assertSegment(segments.get(3), "P4", "Phần IV", 21, 25, "0.00", "30.00", 5);
        } finally {
            jdbc.update(
                    "UPDATE exam.exam_template SET structure = ?::jsonb WHERE code = 'VSAT_SUBJECT_ANH'",
                    origTemplateStructure);
            jdbc.update(
                    "UPDATE exam.exam_structure SET parts = ?::jsonb WHERE exam_id = ?",
                    origDraftStructure,
                    examId);
        }
    }

    @Test
    void trialGradingUsesSamePolicy() throws Exception {
        // Create 5 distinct single-question subjects so preview & real attempt generate the EXACT same 5 questions
        UUID sSc = createSubject();
        UUID sMcHalf = createSubject();
        UUID sMcFull = createSubject();
        UUID sMcWrong = createSubject();
        UUID sSa = createSubject();

        UUID qSc = insertSingleChoice(sSc, new BigDecimal("0.25"));
        UUID qMcHalf = insertMultipleChoicePartial(sMcHalf, new BigDecimal("0.25"), 3, 1); // 2 of 3 -> 0.125
        UUID qMcFull = insertMultipleChoicePartial(sMcFull, new BigDecimal("0.25"), 2, 2); // 2 of 2 -> 0.25
        UUID qMcWrong = insertMultipleChoicePartial(sMcWrong, new BigDecimal("0.25"), 2, 2); // 1 right + 1 wrong -> 0
        UUID qSa = insertShortAnswer(sSa, new BigDecimal("0.25"), "42");

        String partsJson = """
                [{"id":"p1","order":1,"name":"Phần 1","questionCount":5,"questionCountOnPaper":5,"questionCountRequired":5,"maxPoints":1.25,
                  "participation":{"mode":"ALL","choose":null},
                  "sections":[
                    {"id":"sec1","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b1","name":"SC","questionType":"SINGLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]},
                    {"id":"sec2","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b2","name":"MC Half","questionType":"MULTIPLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]},
                    {"id":"sec3","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b3","name":"MC Full","questionType":"MULTIPLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]},
                    {"id":"sec4","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b4","name":"MC Wrong","questionType":"MULTIPLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]},
                    {"id":"sec5","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b5","name":"SA","questionType":"SHORT_ANSWER","questionCount":1,"requiredPointsPerQuestion":0.25}]}
                  ],"options":[]}]
                """.formatted(
                subjectCode(sSc), subjectCode(sMcHalf), subjectCode(sMcFull), subjectCode(sMcWrong), subjectCode(sSa));

        UUID examId = insertAndPublishCustomHscaExam(partsJson, 5, new BigDecimal("1.25"),
                List.of(
                        new BlockRef("b1", "p1", "sec1", sSc),
                        new BlockRef("b2", "p1", "sec2", sMcHalf),
                        new BlockRef("b3", "p1", "sec3", sMcFull),
                        new BlockRef("b4", "p1", "sec4", sMcWrong),
                        new BlockRef("b5", "p1", "sec5", sSa)));

        // 1. Preview trial grading
        JsonNode previewGen = json(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", examId)
                        .with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":20261122}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(previewGen.path("status").asText()).isEqualTo("GENERATED");
        UUID previewId = UUID.fromString(previewGen.path("previewId").asText());

        Map<String, Object> previewAnswers = new LinkedHashMap<>();
        previewAnswers.put(qSc.toString(), Map.of("type", "SINGLE_CHOICE", "optionId", "a")); // +0.25 CORRECT
        previewAnswers.put(qMcHalf.toString(), Map.of("type", "MULTIPLE_CHOICE", "optionIds", List.of("c1", "c2"))); // +0.125 PARTIAL
        previewAnswers.put(qMcFull.toString(), Map.of("type", "MULTIPLE_CHOICE", "optionIds", List.of("c1", "c2"))); // +0.25 CORRECT
        previewAnswers.put(qMcWrong.toString(), Map.of("type", "MULTIPLE_CHOICE", "optionIds", List.of("c1", "w1"))); // +0.00 WRONG
        previewAnswers.put(qSa.toString(), Map.of("type", "SHORT_ANSWER", "text", " 42 ")); // +0.25 CORRECT

        JsonNode previewGraded = json(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview/{previewId}/grade", examId, previewId)
                        .with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", previewAnswers))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(previewGraded.path("kindCode").asText()).isEqualTo("HSCA");
        // 0.25 + 0.125 + 0.25 + 0 + 0.25 = 0.875 -> 0.88 HALF_UP
        assertThat(previewGraded.path("rawScore").decimalValue()).isEqualByComparingTo("0.88");

        // 2. Real learner attempt on the exact same published HSCA exam
        UUID student = createStudent("hsca-trial-parity");
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line grant = AttemptTestFixtures.grantExam(
                grantService, student, examId, 2, List.of(), "SCORE_AND_ANSWERS", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> realItems = loadOrderedItems(attempt.getId(), parts);

        Map<UUID, AnswerValue> realAnswers = new LinkedHashMap<>();
        for (AttemptItem item : realItems) {
            if (item.getQuestionId().equals(qSc)) {
                realAnswers.put(item.getId(), new AnswerValue.SingleChoice("a"));
            } else if (item.getQuestionId().equals(qMcHalf)) {
                realAnswers.put(item.getId(), new AnswerValue.MultipleChoice(List.of("c1", "c2")));
            } else if (item.getQuestionId().equals(qMcFull)) {
                realAnswers.put(item.getId(), new AnswerValue.MultipleChoice(List.of("c1", "c2")));
            } else if (item.getQuestionId().equals(qMcWrong)) {
                realAnswers.put(item.getId(), new AnswerValue.MultipleChoice(List.of("c1", "w1")));
            } else if (item.getQuestionId().equals(qSa)) {
                realAnswers.put(item.getId(), new AnswerValue.ShortAnswer(" 42 "));
            }
        }
        completeSequentialAttempt(student, attempt.getId(), sessionId, parts, realAnswers);

        Attempt scoredReal = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(previewGraded.path("rawScore").decimalValue()).isEqualByComparingTo(scoredReal.getRawScore());
        assertThat(previewGraded.path("maxScore").decimalValue()).isEqualByComparingTo(scoredReal.getMaxScore());

        Map<UUID, AttemptItem> realByQuestion = new HashMap<>();
        for (AttemptItem item : attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())) {
            realByQuestion.put(item.getQuestionId(), item);
        }
        for (JsonNode previewItem : previewGraded.path("items")) {
            UUID qId = UUID.fromString(previewItem.path("questionId").asText());
            AttemptItem realItem = realByQuestion.get(qId);
            assertThat(realItem).isNotNull();
            assertThat(previewItem.path("outcome").asText()).isEqualTo(realItem.getOutcome().name());
            assertThat(previewItem.path("earnedPoints").decimalValue()).isEqualByComparingTo(realItem.getEarnedPoints());
        }
    }

    @Test
    void regradeKeepsPolicy() throws Exception {
        UUID examId = publishDeterministicPartialHscaExam();
        Attempt attempt = runHscaHalfCreditAttempt(examId, "hsca-regrade", 2);

        List<AttemptItem> mcItems = leafItems(
                attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId()),
                QuestionType.MULTIPLE_CHOICE,
                false);
        assertThat(mcItems).hasSizeGreaterThanOrEqualTo(2);
        AttemptItem partialMc1 = mcItems.get(0);
        AttemptItem partialMc2 = mcItems.get(1);

        assertThat(attempt.getRawScore()).isEqualByComparingTo("0.25");
        assertThat(attempt.getDisplayScore()).isEqualByComparingTo("0.25");

        // 1. Regrade attempt -> policy & scores stay identical
        attemptGradingService.regradeAttempt(attempt.getId());

        Attempt afterRegrade = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(afterRegrade.getRawScore()).isEqualByComparingTo("0.25");
        assertThat(afterRegrade.getDisplayScore()).isEqualByComparingTo("0.25");
        assertThat(attemptEventRepository.existsByAttemptIdAndType(attempt.getId(), AttemptEventType.REGRADED)).isTrue();

        for (UUID mcId : List.of(partialMc1.getId(), partialMc2.getId())) {
            AttemptItem item = attemptItemRepository.findById(mcId).orElseThrow();
            assertThat(item.getOutcome()).isEqualTo(ItemOutcome.PARTIAL);
            assertThat(item.getEarnedPoints()).isEqualByComparingTo("0.1250");
        }

        // 2. Direct call & outbox redelivery of AttemptFinished -> completely idempotent, keeps 0.1250 & 0.25
        attemptGradingService.gradeFinishedAttempt(attempt.getId());
        int updatedOutbox = jdbc.update(
                "UPDATE shared.outbox_event SET processed_at = NULL, attempts = 0, next_attempt_at = ? "
                        + "WHERE topic = 'attempt.finished' AND payload->>'attemptId' = ?",
                Timestamp.from(mutableClock.instant()),
                attempt.getId().toString());
        assertThat(updatedOutbox).isGreaterThanOrEqualTo(1);
        int redelivered = drainAllOutbox();
        assertThat(redelivered).isGreaterThanOrEqualTo(1);

        Attempt afterRedelivery = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(afterRedelivery.getRawScore()).isEqualByComparingTo("0.25");
        assertThat(afterRedelivery.getDisplayScore()).isEqualByComparingTo("0.25");
        for (UUID mcId : List.of(partialMc1.getId(), partialMc2.getId())) {
            AttemptItem item = attemptItemRepository.findById(mcId).orElseThrow();
            assertThat(item.getOutcome()).isEqualTo(ItemOutcome.PARTIAL);
            assertThat(item.getEarnedPoints()).isEqualByComparingTo("0.1250");
        }
    }

    @Test
    void eighthPointsSurviveDatabaseRoundTrip() throws Exception {
        UUID examId = publishDeterministicPartialHscaExam();
        // 2 items * 0.125 -> raw DB earned_points = 0.1250 (NOT truncated to 0.13/0.12), sum = 0.25
        Attempt twoItemsAttempt = runHscaHalfCreditAttempt(examId, "eighth-2", 2);
        entityManager.clear();

        List<AttemptItem> twoMc = leafItems(
                attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(twoItemsAttempt.getId()),
                QuestionType.MULTIPLE_CHOICE,
                false);
        for (int i = 0; i < 2; i++) {
            UUID itemId = twoMc.get(i).getId();
            BigDecimal dbEarned = jdbc.queryForObject(
                    "SELECT earned_points FROM attempt.attempt_item WHERE id = ?",
                    BigDecimal.class,
                    itemId);
            assertThat(dbEarned).isNotNull();
            assertThat(dbEarned.scale()).isGreaterThanOrEqualTo(3);
            assertThat(dbEarned).isEqualByComparingTo("0.1250");

            AttemptItem repoReloaded = attemptItemRepository.findById(itemId).orElseThrow();
            assertThat(repoReloaded.getOutcome()).isEqualTo(ItemOutcome.PARTIAL);
            assertThat(repoReloaded.getEarnedPoints()).isEqualByComparingTo("0.1250");
        }

        Attempt reloadedTwo = attemptRepository.findById(twoItemsAttempt.getId()).orElseThrow();
        assertThat(reloadedTwo.getRawScore()).isEqualByComparingTo("0.25");
        assertThat(reloadedTwo.getDisplayScore()).isEqualByComparingTo("0.25");

        // Regrade preserves both DB 0.1250 rows and total 0.25
        attemptGradingService.regradeAttempt(twoItemsAttempt.getId());
        entityManager.clear();
        for (int i = 0; i < 2; i++) {
            BigDecimal dbEarnedAfterRegrade = jdbc.queryForObject(
                    "SELECT earned_points FROM attempt.attempt_item WHERE id = ?",
                    BigDecimal.class,
                    twoMc.get(i).getId());
            assertThat(dbEarnedAfterRegrade).isEqualByComparingTo("0.1250");
        }
        Attempt regradedTwo = attemptRepository.findById(twoItemsAttempt.getId()).orElseThrow();
        assertThat(regradedTwo.getRawScore()).isEqualByComparingTo("0.25");
        assertThat(regradedTwo.getDisplayScore()).isEqualByComparingTo("0.25");

        // Also verify 1 item = 0.13 (HALF_UP) and 3 items = 0.38 (HALF_UP) through PostgreSQL
        Attempt oneItemAttempt = runHscaHalfCreditAttempt(examId, "eighth-1", 1);
        assertThat(oneItemAttempt.getRawScore()).isEqualByComparingTo("0.13");
        assertThat(oneItemAttempt.getDisplayScore()).isEqualByComparingTo("0.13");

        Attempt threeItemsAttempt = runHscaHalfCreditAttempt(examId, "eighth-3", 3);
        assertThat(threeItemsAttempt.getRawScore()).isEqualByComparingTo("0.38");
        assertThat(threeItemsAttempt.getDisplayScore()).isEqualByComparingTo("0.38");
    }

    // --- Support helpers -------------------------------------------------------------------------

    private UUID publishDeterministicPartialHscaExam() throws Exception {
        UUID s1 = createSubject();
        UUID s2 = createSubject();
        UUID s3 = createSubject();
        UUID s4 = createSubject();
        insertMultipleChoicePartial(s1, new BigDecimal("0.25"), 2, 2);
        insertMultipleChoicePartial(s2, new BigDecimal("0.25"), 2, 2);
        insertMultipleChoicePartial(s3, new BigDecimal("0.25"), 2, 2);
        insertSingleChoice(s4, new BigDecimal("0.25"));

        String partsJson = """
                [{"id":"p1","order":1,"name":"Phần 1","questionCount":4,"questionCountOnPaper":4,"questionCountRequired":4,"maxPoints":1.00,
                  "participation":{"mode":"ALL","choose":null},
                  "sections":[
                    {"id":"sec1","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b1","name":"MC1","questionType":"MULTIPLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]},
                    {"id":"sec2","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b2","name":"MC2","questionType":"MULTIPLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]},
                    {"id":"sec3","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b3","name":"MC3","questionType":"MULTIPLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]},
                    {"id":"sec4","subjectCode":"%s","questionCount":1,"blocks":[{"id":"b4","name":"SC4","questionType":"SINGLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":0.25}]}
                  ],"options":[]}]
                """.formatted(subjectCode(s1), subjectCode(s2), subjectCode(s3), subjectCode(s4));

        return insertAndPublishCustomHscaExam(partsJson, 4, new BigDecimal("1.00"),
                List.of(
                        new BlockRef("b1", "p1", "sec1", s1),
                        new BlockRef("b2", "p1", "sec2", s2),
                        new BlockRef("b3", "p1", "sec3", s3),
                        new BlockRef("b4", "p1", "sec4", s4)));
    }

    private Attempt runHscaHalfCreditAttempt(UUID examId, String label, int halfCreditMcCount) throws Exception {
        UUID student = createStudent(label);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        GrantOrderResult.Line grant = AttemptTestFixtures.grantExam(
                grantService, student, examId, 3, List.of(), "SCORE_AND_ANSWERS", serving.version().versionNo());
        Attempt attempt = startAndAwaitOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> allItems = loadOrderedItems(attempt.getId(), parts);
        Map<VersionKey, JsonNode> snaps = loadSnapshots(allItems);
        List<AttemptItem> mcItems = leafItems(allItems, QuestionType.MULTIPLE_CHOICE, false);
        assertThat(mcItems.size()).isGreaterThanOrEqualTo(halfCreditMcCount);

        Map<UUID, AnswerValue> answers = new LinkedHashMap<>();
        for (int i = 0; i < halfCreditMcCount; i++) {
            AttemptItem item = mcItems.get(i);
            answers.put(item.getId(), partialMultipleChoice(snaps.get(key(item))));
        }
        completeSequentialAttempt(student, attempt.getId(), sessionId, parts, answers);
        return attemptRepository.findById(attempt.getId()).orElseThrow();
    }

    private UUID createStudent(String label) {
        UUID id = AttemptTestFixtures.createStudentAccount(
                jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    private GrantOrderResult.Line grant(UUID userId, String examCode) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        return AttemptTestFixtures.grantExam(
                grantService, userId, examId, 3, List.of(), "SCORE_AND_ANSWERS", serving.version().versionNo());
    }

    private Attempt startAndAwaitOpen(UUID userId, UUID entitlementId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/me/attempts")
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAttemptRequest(entitlementId, null))))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Attempt current = attemptRepository.findById(attemptId).orElse(null);
            if (current != null && current.getState() == AttemptState.IN_PROGRESS) {
                return current;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for attempt " + attemptId + " to become IN_PROGRESS");
    }

    private UUID claimSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClaimSessionRequest(null, false))))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(json(res).path("sessionId").asText());
    }

    private void completeSequentialAttempt(
            UUID userId,
            UUID attemptId,
            UUID sessionId,
            List<AttemptPart> orderedParts,
            Map<UUID, AnswerValue> answersByItemId) throws Exception {
        List<AttemptItem> items = loadOrderedItems(attemptId, orderedParts);
        Map<UUID, List<FinalAnswerChange>> finalsByPartId = new LinkedHashMap<>();
        for (AttemptItem item : items) {
            AnswerValue ans = answersByItemId.get(item.getId());
            if (ans != null) {
                finalsByPartId
                        .computeIfAbsent(item.getPartId(), ignored -> new ArrayList<>())
                        .add(new FinalAnswerChange(item.getId(), 1L, ans));
            }
        }

        for (int idx = 0; idx < orderedParts.size() - 1; idx++) {
            AttemptPart part = orderedParts.get(idx);
            List<FinalAnswerChange> finals = finalsByPartId.getOrDefault(part.getId(), List.of());
            mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, part.getId())
                            .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                            .header("X-Attempt-Session", sessionId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new FinishPartRequest(finals))))
                    .andExpect(status().isOk());
        }

        AttemptPart lastPart = orderedParts.getLast();
        List<FinalAnswerChange> lastFinals = finalsByPartId.getOrDefault(lastPart.getId(), List.of());
        mockMvc.perform(post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(lastFinals))))
                .andExpect(status().isOk());

        drainAllOutbox();
    }

    private JsonNode getResultOk(UUID userId, UUID attemptId) throws Exception {
        return getResultOk(userId, attemptId, Locale.forLanguageTag("vi"));
    }

    private JsonNode getResultOk(UUID userId, UUID attemptId, Locale locale) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/result", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, locale.toLanguageTag()))
                .andExpect(status().isOk())
                .andReturn();
        return json(res);
    }

    private List<AttemptItem> loadOrderedItems(UUID attemptId, List<AttemptPart> orderedParts) {
        List<AttemptItem> items = new ArrayList<>(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId));
        Map<UUID, Integer> partOrd = new HashMap<>(orderedParts.size());
        for (AttemptPart part : orderedParts) {
            partOrd.put(part.getId(), part.getOrd());
        }
        items.sort(Comparator
                .comparingInt((AttemptItem i) -> partOrd.getOrDefault(i.getPartId(), Integer.MAX_VALUE))
                .thenComparingInt(AttemptItem::getOrd));
        return items;
    }

    private static List<AttemptItem> leafItems(List<AttemptItem> allItems) {
        return allItems.stream()
                .filter(i -> i.isInScope() && i.getQuestionType() != QuestionType.QUESTION_SET)
                .toList();
    }

    private static List<AttemptItem> leafItems(List<AttemptItem> allItems, QuestionType type, boolean childOnly) {
        return allItems.stream()
                .filter(i -> i.isInScope()
                        && i.getQuestionType() == type
                        && (childOnly ? i.getParentItemId() != null : i.getParentItemId() == null))
                .toList();
    }

    private Map<VersionKey, JsonNode> loadSnapshots(List<AttemptItem> items) {
        Set<VersionKey> keys = items.stream()
                .map(HscaVsatGradingIT::key)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<Version> loaded = questionSourcePort.loadVersions(List.copyOf(keys));
        Map<VersionKey, JsonNode> map = new HashMap<>(loaded.size() * 2);
        for (Version v : loaded) {
            map.put(v.key(), v.snapshot());
        }
        return map;
    }

    private static VersionKey key(AttemptItem item) {
        return new VersionKey(item.getQuestionId(), item.getQuestionVersionNo());
    }

    private static Map<UUID, AttemptItem> byId(List<AttemptItem> items) {
        Map<UUID, AttemptItem> map = new HashMap<>(items.size() * 2);
        for (AttemptItem item : items) {
            map.put(item.getId(), item);
        }
        return map;
    }

    private static AnswerValue.SingleChoice correctSingleChoice(JsonNode snapshot) {
        for (JsonNode opt : snapshot.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                return new AnswerValue.SingleChoice(opt.path("id").asText());
            }
        }
        throw new IllegalStateException("No correct option in snapshot");
    }

    private static AnswerValue.SingleChoice wrongSingleChoice(JsonNode snapshot) {
        for (JsonNode opt : snapshot.path("body").path("options")) {
            if (!opt.path("correct").asBoolean(false)) {
                return new AnswerValue.SingleChoice(opt.path("id").asText());
            }
        }
        throw new IllegalStateException("No wrong option in snapshot");
    }

    private static int countCorrectOptions(JsonNode snapshot) {
        int count = 0;
        for (JsonNode opt : snapshot.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                count++;
            }
        }
        return count;
    }

    private static AnswerValue.MultipleChoice completeMultipleChoice(JsonNode snapshot) {
        List<String> ids = new ArrayList<>();
        for (JsonNode opt : snapshot.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                ids.add(opt.path("id").asText());
            }
        }
        return new AnswerValue.MultipleChoice(ids);
    }

    private static AnswerValue.MultipleChoice partialMultipleChoice(JsonNode snapshot) {
        for (JsonNode opt : snapshot.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false)) {
                return new AnswerValue.MultipleChoice(List.of(opt.path("id").asText()));
            }
        }
        throw new IllegalStateException("No correct option in MC snapshot");
    }

    private static AnswerValue.MultipleChoice wrongMultipleChoice(JsonNode snapshot) {
        String oneCorrect = null;
        String oneWrong = null;
        for (JsonNode opt : snapshot.path("body").path("options")) {
            if (opt.path("correct").asBoolean(false) && oneCorrect == null) {
                oneCorrect = opt.path("id").asText();
            } else if (!opt.path("correct").asBoolean(false) && oneWrong == null) {
                oneWrong = opt.path("id").asText();
            }
        }
        return new AnswerValue.MultipleChoice(List.of(oneCorrect, oneWrong));
    }

    private static AnswerValue.ShortAnswer correctShortAnswer(JsonNode snapshot) {
        return new AnswerValue.ShortAnswer(snapshot.path("body").path("answers").get(0).asText());
    }

    private static AnswerValue.TrueFalse trueFalseWithMatchedCount(JsonNode snapshot, int matchedTarget) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        int idx = 0;
        for (JsonNode st : snapshot.path("body").path("statements")) {
            boolean expected = st.path("answer").asBoolean(false);
            map.put(st.path("id").asText(), idx < matchedTarget ? expected : !expected);
            idx++;
        }
        return new AnswerValue.TrueFalse(map);
    }

    private static AnswerValue.Matching matchingWithMatchedCount(JsonNode snapshot, int matchedTarget) {
        List<String> leftIds = new ArrayList<>();
        List<String> rightIds = new ArrayList<>();
        for (JsonNode p : snapshot.path("body").path("pairs")) {
            leftIds.add(p.path("leftId").asText());
            rightIds.add(p.path("rightId").asText());
        }
        Map<String, String> map = new LinkedHashMap<>();
        int size = leftIds.size();
        for (int i = 0; i < matchedTarget; i++) {
            map.put(leftIds.get(i), rightIds.get(i));
        }
        int remaining = size - matchedTarget;
        if (remaining >= 2) {
            // Derange the unmatched suffix [matchedTarget .. size-1] so zero duplicate rightIds occur
            for (int i = matchedTarget; i < size; i++) {
                int wrongIdx = matchedTarget + ((i - matchedTarget + 1) % remaining);
                map.put(leftIds.get(i), rightIds.get(wrongIdx));
            }
        }
        // When remaining == 1 (matchedTarget == size - 1), omit the last leftId so reuseRight=false stays valid
        return new AnswerValue.Matching(map);
    }

    private static AnswerValue.Cloze clozeWithCorrectCount(JsonNode snapshot, int correctCount, int wrongCount) {
        Map<String, String> blanks = new LinkedHashMap<>();
        int idx = 0;
        for (JsonNode blank : snapshot.path("body").path("blanks")) {
            String id = blank.path("id").asText();
            if (idx < correctCount) {
                blanks.put(id, blank.path("answers").get(0).asText());
            } else if (idx < correctCount + wrongCount) {
                blanks.put(id, "__wrong__");
            }
            idx++;
        }
        return new AnswerValue.Cloze(blanks);
    }

    private static AnswerValue fullCorrectAnswer(AttemptItem item, JsonNode snapshot) {
        return switch (item.getQuestionType()) {
            case SINGLE_CHOICE -> correctSingleChoice(snapshot);
            case MULTIPLE_CHOICE -> completeMultipleChoice(snapshot);
            case SHORT_ANSWER -> correctShortAnswer(snapshot);
            case TRUE_FALSE -> trueFalseWithMatchedCount(snapshot, snapshot.path("body").path("statements").size());
            case MATCHING -> matchingWithMatchedCount(snapshot, snapshot.path("body").path("pairs").size());
            case CLOZE -> clozeWithCorrectCount(snapshot, snapshot.path("body").path("blanks").size(), 0);
            default -> throw new IllegalStateException("Unexpected item type: " + item.getQuestionType());
        };
    }

    private static void assertSegment(
            JsonNode seg,
            String expectedCode,
            String expectedName,
            int expectedFrom,
            int expectedTo,
            String expectedScore,
            String expectedMax,
            int expectedTotalUnits) {
        assertThat(seg.path("code").asText()).isEqualTo(expectedCode);
        assertThat(seg.path("name").asText()).isEqualTo(expectedName);
        assertThat(seg.path("fromPosition").asInt()).isEqualTo(expectedFrom);
        assertThat(seg.path("toPosition").asInt()).isEqualTo(expectedTo);
        assertThat(seg.path("pendingReview").asBoolean()).isFalse();
        assertThat(seg.path("score").decimalValue()).isEqualByComparingTo(expectedScore);
        assertThat(seg.path("maxPoints").decimalValue()).isEqualByComparingTo(expectedMax);
        assertThat(seg.path("stats").path("totalCount").asInt()).isEqualTo(expectedTotalUnits);
    }

    private record BlockRef(String blockId, String partId, String sectionId, UUID subjectId) {}

    private UUID insertAndPublishCustomHscaExam(
            String partsJson, int questionCount, BigDecimal maxScore, List<BlockRef> refs) throws Exception {
        UUID examId = UUID.randomUUID();
        createdExamIds.add(examId);
        UUID kindItemId = jdbc.queryForObject(
                "SELECT id FROM catalog.catalog_item WHERE code = 'HSCA' LIMIT 1", UUID.class);
        jdbc.update(
                "INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, duration_minutes, question_count, max_score, has_unpublished_changes) "
                        + "VALUES (?, ?, ?, 'AT7 HSCA Parity Exam', ?, 'FULL', 'SHARED', 60, ?, ?, true)",
                examId,
                examId.toString(),
                examId.toString(),
                kindItemId,
                questionCount,
                maxScore);
        jdbc.update(
                "INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, cast(? as jsonb), 'at7-parity')",
                examId,
                "{\"parts\":" + partsJson + "}");
        for (BlockRef ref : refs) {
            jdbc.update(
                    "INSERT INTO exam.exam_block_ref(id, exam_id, block_id, part_id, section_id, subject_item_id) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    examId,
                    ref.blockId(),
                    ref.partId(),
                    ref.sectionId(),
                    ref.subjectId());
        }
        ExamListingFixture.fill(jdbc, examId);

        JsonNode checked = json(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", examId)
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk())
                .andReturn());
        ArrayNode validPaths = objectMapper.createArrayNode();
        checked.path("paths").forEach(p -> {
            if ("VALID".equals(p.path("result").asText())) {
                validPaths.add(p.path("pathId").asText());
            }
        });
        assertThat(validPaths).as(checked.toString()).isNotEmpty();

        long rev = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, examId);
        ObjectNode pubBody = objectMapper.createObjectNode().put("expectedRevision", rev);
        pubBody.set("pathIds", validPaths);
        mockMvc.perform(post("/api/v1/admin/exams/{id}/publish", examId)
                        .with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubBody.toString()))
                .andExpect(status().isOk());
        return examId;
    }

    private UUID createSubject() {
        UUID id = UUID.randomUUID();
        createdSubjectIds.add(id);
        UUID subjectGroup = jdbc.queryForObject(
                "SELECT group_id FROM catalog.catalog_item WHERE code = 'TOAN' LIMIT 1", UUID.class);
        String code = "AT7S_" + id.toString().replace("-", "");
        jdbc.update(
                """
                INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),
                    'AT7 Subject', 'AT7 Subject', 0, 1, false, 1, ?)
                """,
                id,
                subjectGroup,
                code,
                code.toLowerCase(Locale.ROOT),
                subjectGroup,
                "/" + id + "/");
        return id;
    }

    private String subjectCode(UUID subjectId) {
        return jdbc.queryForObject("SELECT code FROM catalog.catalog_item WHERE id = ?", String.class, subjectId);
    }

    private UUID insertSingleChoice(UUID subjectId, BigDecimal points) {
        UUID id = UUID.randomUUID();
        createdQuestionIds.add(id);
        jdbc.update(
                "INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "VALUES (?,?,'SINGLE_CHOICE','READY',?,1,?)",
                id,
                "AT7-SC-" + id,
                subjectId,
                points);
        String snapshot = """
                {
                  "content": "<p>AT7 SC</p>",
                  "points": %s,
                  "body": {
                    "type": "SINGLE_CHOICE",
                    "options": [
                      {"id": "a", "content": "A", "correct": true},
                      {"id": "b", "content": "B", "correct": false},
                      {"id": "c", "content": "C", "correct": false},
                      {"id": "d", "content": "D", "correct": false}
                    ]
                  }
                }
                """.formatted(points.toPlainString());
        jdbc.update(
                "INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertMultipleChoicePartial(UUID subjectId, BigDecimal points, int correctCount, int wrongCount) {
        UUID id = UUID.randomUUID();
        createdQuestionIds.add(id);
        jdbc.update(
                "INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "VALUES (?,?,'MULTIPLE_CHOICE','READY',?,1,?)",
                id,
                "AT7-MC-" + id,
                subjectId,
                points);
        List<Map<String, Object>> opts = new ArrayList<>();
        for (int i = 1; i <= correctCount; i++) {
            opts.add(Map.of("id", "c" + i, "content", "C" + i, "correct", true));
        }
        for (int i = 1; i <= wrongCount; i++) {
            opts.add(Map.of("id", "w" + i, "content", "W" + i, "correct", false));
        }
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(Map.of(
                    "content", "<p>AT7 MC</p>",
                    "points", points,
                    "body", Map.of(
                            "type", "MULTIPLE_CHOICE",
                            "scoringRule", "PARTIAL",
                            "options", opts)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        jdbc.update(
                "INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private UUID insertShortAnswer(UUID subjectId, BigDecimal points, String answer) {
        UUID id = UUID.randomUUID();
        createdQuestionIds.add(id);
        jdbc.update(
                "INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                        + "VALUES (?,?,'SHORT_ANSWER','READY',?,1,?)",
                id,
                "AT7-SA-" + id,
                subjectId,
                points);
        String snapshot = """
                {
                  "content": "<p>AT7 SA</p>",
                  "points": %s,
                  "body": {
                    "type": "SHORT_ANSWER",
                    "inputHint": "Integer",
                    "answers": ["%s"]
                  }
                }
                """.formatted(points.toPlainString(), answer);
        jdbc.update(
                "INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, cast(? as jsonb))",
                id,
                snapshot);
        return id;
    }

    private void cleanupSyntheticExam(UUID examId) {
        jdbc.update("DELETE FROM questionbank.question_usage_ledger WHERE exam_id = ?", examId);
        jdbc.update(
                "DELETE FROM shared.outbox_event WHERE payload->>'examId' = ? OR payload->>'targetId' = ?",
                examId.toString(),
                examId.toString());
        jdbc.update("DELETE FROM exam.exam_check_run WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_cover_asset WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_structure WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam WHERE id = ?", examId);
    }

    private int drainAllOutbox() {
        int total = 0;
        int batch;
        do {
            batch = outboxRelay.relayBatch(500);
            total += batch;
        } while (batch > 0);
        return total;
    }

    private JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }
}
