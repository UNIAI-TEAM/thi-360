package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.PublishedExamSetSupport;

@Import(AttemptTestFixtures.MutableClockConfig.class)
public class MobileResultFixturesIT extends AbstractIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/attempts-result-200-scored.json",
                "api/attempts-result-200-pending-review.json",
                "api/attempts-result-200-grading.json",
                "api/attempts-result-200-grading-error.json",
                "api/attempts-result-200-regrading.json",
                "api/attempts-result-200-vact.json",
                "api/attempts-result-200-system-fault.json",
                "api/attempts-result-200-compensated.json",
                "api/attempts-result-200-zero-score.json",
                "api/attempts-result-404-attempt-not-found.json",
                "api/attempts-result-409-attempt-not-finished.json",
                "api/attempts-review-200-score-and-answers.json",
                "api/attempts-review-200-full-solution.json",
                "api/attempts-review-200-pending-review.json",
                "api/attempts-review-403-review-not-included.json",
                "api/attempts-review-404-attempt-not-found.json",
                "api/attempts-review-409-review-not-available.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    private static final Set<String> RESULT_DYNAMIC_FIELDS = Set.of(
            "attemptId", "examId", "partId", "startedAt", "finishedAt", "gradedAt");

    private static final Set<String> REVIEW_DYNAMIC_FIELDS = Set.of(
            "attemptId",
            "examId",
            "partId",
            "itemId",
            "parentItemId",
            "optionOrder",
            "content",
            "stem",
            "passage",
            "solution",
            "$.items[*].body.options[*].id",
            "$.items[*].body.options[*].content",
            "$.items[*].body.statements[*].id",
            "$.items[*].body.statements[*].content",
            "$.items[*].body.left[*].id",
            "$.items[*].body.left[*].content",
            "$.items[*].body.right[*].id",
            "$.items[*].body.right[*].content",
            "$.items[*].studentAnswer.optionId",
            "$.items[*].gradingDetail.selectedOptionId",
            "$.items[*].expectedAnswer.expectedOptionIds",
            "$.items[*].expectedAnswer.acceptedTexts",
            "$.items[*].expectedAnswer.clozeKeys",
            "$.items[*].expectedAnswer.trueFalseKeys",
            "$.items[*].expectedAnswer.matchingKeys",
            "$.items[*].attachments[*].id",
            "$.items[*].attachments[*].downloadUrl");

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MutableClock mutableClock;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;
    @Autowired private AttemptItemRepository attemptItemRepository;
    @Autowired private AttemptEventRepository attemptEventRepository;
    @Autowired private OutboxRelay outboxRelay;
    @MockitoSpyBean private QuestionSourcePort questionSourcePort;
    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @MockitoSpyBean private AttemptPaperAssembler attemptPaperAssembler;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private MobileFixtureCertifier certifier;

    @BeforeEach
    void setUp() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        certifier = new MobileFixtureCertifier(objectMapper);
        reset(paperSolverPort, attemptPaperAssembler, questionSourcePort);
        pinCandidateOrder();
        pinSolverSeed();
        pinAssemblerSeed();
        while (outboxRelay.relayBatch(500) > 0) {
        }
    }

    @AfterEach
    void tearDown() {
        reset(paperSolverPort, attemptPaperAssembler, questionSourcePort);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        for (UUID userId : createdUserIds) {
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
    }

    private void pinSolverSeed() {
        doAnswer(inv -> {
            long seed = inv.getArgument(4);
            if (seed != 42L) {
                return paperSolverPort.generate(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3), 42L, inv.getArgument(5));
            }
            return inv.callRealMethod();
        }).when(paperSolverPort).generate(any(), anyInt(), any(), any(), anyLong(), any());
    }

    private void pinAssemblerSeed() {
        doAnswer(inv -> {
            long partSeed = inv.getArgument(3);
            if (partSeed != 42L) {
                return attemptPaperAssembler.assemble(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        42L, inv.getArgument(4));
            }
            return inv.callRealMethod();
        }).when(attemptPaperAssembler).assemble(any(), any(), any(), anyLong(), anyInt());
    }

    private void pinCandidateOrder() {
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<QuestionSourcePort.Candidate> real = (List<QuestionSourcePort.Candidate>) inv.callRealMethod();
            return real.stream()
                    .sorted(Comparator.comparing(
                            c -> c.code() == null ? "" : c.code(),
                            Comparator.naturalOrder()))
                    .toList();
        }).when(questionSourcePort).findCandidates(any(), anyInt());
    }

    @Test
    void allResultAndReviewContractFixturesMatchLiveResponses() throws Exception {
        UUID student = createStudent("mc-res-main");

        certifier.certify("api/attempts-result-404-attempt-not-found.json", 404,
                performGetResult(student, UUID.randomUUID()), Set.of("traceId"));
        certifier.certify("api/attempts-review-404-attempt-not-found.json", 404,
                performGetReview(student, UUID.randomUUID()), Set.of("traceId"));

        var scoredLine = grant(student, "DS-HSA-PART-MATH-MINI", 3, "SCORE_AND_ANSWERS");
        Attempt scoredAttempt = startAndAwaitOpen(student, "mc-res-sc", scoredLine.entitlementId(), null);
        UUID scoredSid = claimSession(student, scoredAttempt.getId());

        certifier.certify("api/attempts-result-409-attempt-not-finished.json", 409,
                performGetResult(student, scoredAttempt.getId()), Set.of("traceId"));
        certifier.certify("api/attempts-review-409-review-not-available.json", 409,
                performGetReview(student, scoredAttempt.getId()), Set.of("traceId"));

        AttemptItem firstSc = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(scoredAttempt.getId()).stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        String correctOpt = firstCorrectOptionId(firstSc);
        assertThat(performSubmit(student, scoredAttempt.getId(), "mc-sub-sc", scoredSid,
                        new SubmitAttemptRequest(List.of(new FinalAnswerChange(firstSc.getId(), 1L, new AnswerValue.SingleChoice(correctOpt)))))
                        .getResponse().getStatus())
                .isEqualTo(200);

        certifier.certify("api/attempts-result-200-grading.json", 200,
                performGetResult(student, scoredAttempt.getId()), RESULT_DYNAMIC_FIELDS);

        outboxRelay.relayBatch(500);

        certifier.certify("api/attempts-result-200-scored.json", 200,
                performGetResult(student, scoredAttempt.getId()), RESULT_DYNAMIC_FIELDS);
        certifier.certify("api/attempts-review-200-score-and-answers.json", 200,
                performGetReview(student, scoredAttempt.getId()), REVIEW_DYNAMIC_FIELDS);

        UUID uZero = createStudent("mc-res-zero");
        var scoreOnlyLine = grant(uZero, "DS-HSA-PART-MATH-MINI", 3, "SCORE");
        Attempt zeroAttempt = startAndAwaitOpen(uZero, "mc-res-z", scoreOnlyLine.entitlementId(), null);
        UUID zeroSid = claimSession(uZero, zeroAttempt.getId());
        assertThat(performSubmit(uZero, zeroAttempt.getId(), "mc-sub-z", zeroSid, new SubmitAttemptRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);

        certifier.certify("api/attempts-result-200-zero-score.json", 200,
                performGetResult(uZero, zeroAttempt.getId()), RESULT_DYNAMIC_FIELDS);
        certifier.certify("api/attempts-review-403-review-not-included.json", 403,
                performGetReview(uZero, zeroAttempt.getId()), Set.of("traceId"));

        jdbc.update("UPDATE attempt.attempt SET fault_flag = 'SYSTEM_FAULT', fault_reason = 'Transient worker fault' WHERE id = ?",
                zeroAttempt.getId());
        certifier.certify("api/attempts-result-200-system-fault.json", 200,
                performGetResult(uZero, zeroAttempt.getId()), RESULT_DYNAMIC_FIELDS);

        attemptEventRepository.saveAndFlush(new AttemptEvent(
                Ids.newId(), zeroAttempt.getId(), AttemptEventType.COMPENSATED,
                null, null, mutableClock.instant(), Map.of("reason", "System fault compensation")));
        certifier.certify("api/attempts-result-200-compensated.json", 200,
                performGetResult(uZero, zeroAttempt.getId()), RESULT_DYNAMIC_FIELDS);

        Attempt errAttempt = startAndAwaitOpen(uZero, "mc-res-err", scoreOnlyLine.entitlementId(), null);
        UUID errSid = claimSession(uZero, errAttempt.getId());
        assertThat(performSubmit(uZero, errAttempt.getId(), "mc-sub-err", errSid, new SubmitAttemptRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);
        jdbc.update("""
                UPDATE attempt.attempt
                SET state = 'GRADING_ERROR', grading_error = 'Internal error detail', raw_score = NULL, display_score = NULL
                WHERE id = ?
                """, errAttempt.getId());
        certifier.certify("api/attempts-result-200-grading-error.json", 200,
                performGetResult(uZero, errAttempt.getId()), RESULT_DYNAMIC_FIELDS);

        UUID uFull = createStudent("mc-res-full");
        var fullSolLine = grant(uFull, "DS-HSA-PART-MATH-MINI", 2, "FULL_SOLUTION");
        Attempt fullAttempt = startAndAwaitOpen(uFull, "mc-res-fs", fullSolLine.entitlementId(), null);
        UUID fullSid = claimSession(uFull, fullAttempt.getId());
        assertThat(performSubmit(uFull, fullAttempt.getId(), "mc-sub-fs", fullSid, new SubmitAttemptRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);
        certifier.certify("api/attempts-review-200-full-solution.json", 200,
                performGetReview(uFull, fullAttempt.getId()), REVIEW_DYNAMIC_FIELDS);

        UUID uPending = createStudent("mc-res-pend");
        var bcaLine = grant(uPending, "DS-BCA-FULL-MINI", 2, "FULL_SOLUTION");
        Attempt bcaAttempt = startAndAwaitOpen(uPending, "mc-res-bca", bcaLine.entitlementId(), new PreChoiceInput("CA2", null));
        UUID bcaSid = claimSession(uPending, bcaAttempt.getId());
        List<AttemptPart> bcaParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(bcaAttempt.getId());
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(bcaAttempt.getId()).stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();
        AttemptItem part2Sc = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(bcaAttempt.getId()).stream()
                .filter(i -> i.getPartId().equals(bcaParts.get(1).getId()) && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();
        String bcaCorrect = firstCorrectOptionId(part2Sc);

        assertThat(performFinishPart(uPending, bcaAttempt.getId(), bcaParts.get(0).getId(), bcaSid,
                        new FinishPartRequest(List.of(new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài viết tự luận</p>", 4)))))
                        .getResponse().getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(uPending, bcaAttempt.getId(), bcaParts.get(1).getId(), bcaSid,
                        new FinishPartRequest(List.of(new FinalAnswerChange(part2Sc.getId(), 1L, new AnswerValue.SingleChoice(bcaCorrect)))))
                        .getResponse().getStatus())
                .isEqualTo(200);
        assertThat(performSubmit(uPending, bcaAttempt.getId(), "mc-sub-bca", bcaSid, new SubmitAttemptRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);

        certifier.certify("api/attempts-result-200-pending-review.json", 200,
                performGetResult(uPending, bcaAttempt.getId()), RESULT_DYNAMIC_FIELDS);
        certifier.certify("api/attempts-review-200-pending-review.json", 200,
                performGetReview(uPending, bcaAttempt.getId()), REVIEW_DYNAMIC_FIELDS);

        jdbc.update("""
                UPDATE attempt.attempt
                SET state = 'PENDING_REVIEW', regrading = true, raw_score = 45.50, display_score = 45.50, graded_at = ?
                WHERE id = ?
                """, Timestamp.from(mutableClock.instant()), bcaAttempt.getId());
        certifier.certify("api/attempts-result-200-regrading.json", 200,
                performGetResult(uPending, bcaAttempt.getId()), RESULT_DYNAMIC_FIELDS);

        UUID uVact = createStudent("mc-res-vact");
        var vactLine = grant(uVact, "DS-VACT-FULL", 2, "SCORE_AND_ANSWERS");
        Attempt vactAttempt = startAndAwaitOpen(uVact, "mc-res-vact", vactLine.entitlementId(), null);
        UUID vactSid = claimSession(uVact, vactAttempt.getId());
        List<AttemptPart> vactParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(vactAttempt.getId());
        assertThat(performFinishPart(uVact, vactAttempt.getId(), vactParts.get(0).getId(), vactSid, new FinishPartRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(uVact, vactAttempt.getId(), vactParts.get(1).getId(), vactSid, new FinishPartRequest(List.of()))
                        .getResponse().getStatus())
                .isEqualTo(200);
        Map<String, List<AttemptItem>> bySection = new LinkedHashMap<>();
        for (AttemptItem item : attemptItemRepository.findByPartIdOrderByOrdAsc(vactParts.get(2).getId())) {
            if (item.isInScope() && item.getQuestionType() == QuestionType.SINGLE_CHOICE) {
                bySection.computeIfAbsent(item.getSectionId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        List<FinalAnswerChange> vactFinals = new ArrayList<>();
        for (List<AttemptItem> secItems : bySection.values()) {
            int count = secItems.size() == 12 ? 2 : 3;
            for (int i = 0; i < count; i++) {
                AttemptItem item = secItems.get(i);
                vactFinals.add(new FinalAnswerChange(item.getId(), 1L, new AnswerValue.SingleChoice(firstCorrectOptionId(item))));
            }
        }
        assertThat(performSubmit(uVact, vactAttempt.getId(), "mc-sub-vact", vactSid, new SubmitAttemptRequest(vactFinals))
                        .getResponse().getStatus())
                .isEqualTo(200);
        outboxRelay.relayBatch(500);

        certifier.certify("api/attempts-result-200-vact.json", 200,
                performGetResult(uVact, vactAttempt.getId()), RESULT_DYNAMIC_FIELDS);

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private UUID createStudent(String label) {
        UUID id = AttemptTestFixtures.createStudentAccount(jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    private GrantOrderResult.Line grant(UUID userId, String examCode, int attempts, String resultView) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        return AttemptTestFixtures.grantExam(
                grantService, userId, examId, attempts, List.of(), resultView, serving.version().versionNo());
    }

    private Attempt startAndAwaitOpen(UUID userId, String keyPrefix, UUID entitlementId, PreChoiceInput preChoice)
            throws Exception {
        MvcResult startRes = mockMvc.perform(post("/api/v1/me/attempts")
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("Idempotency-Key", keyPrefix + "-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAttemptRequest(entitlementId, preChoice))))
                .andReturn();
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        return awaitState(UUID.fromString(json(startRes).path("attemptId").asText()), AttemptState.IN_PROGRESS);
    }

    private UUID claimSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/me/attempts/{id}/session", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(json(res).path("sessionId").asText());
    }

    private MvcResult performFinishPart(UUID userId, UUID attemptId, UUID partId, UUID sessionId, FinishPartRequest req)
            throws Exception {
        return mockMvc.perform(post("/api/v1/me/attempts/{id}/parts/{partId}/finish", attemptId, partId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
    }

    private MvcResult performSubmit(UUID userId, UUID attemptId, String key, UUID sessionId, SubmitAttemptRequest req)
            throws Exception {
        return mockMvc.perform(post("/api/v1/me/attempts/{id}/submit", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("Idempotency-Key", key)
                        .header("X-Attempt-Session", sessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
    }

    private MvcResult performGetResult(UUID userId, UUID attemptId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/attempts/{id}/result", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    private MvcResult performGetReview(UUID userId, UUID attemptId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/attempts/{id}/review", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
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

    private Attempt awaitState(UUID attemptId, AttemptState... targets) throws InterruptedException {
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
        throw new AssertionError("Timed out waiting for attempt " + attemptId + " -> " + targetSet
                + ", actual=" + (latest == null ? "MISSING" : latest.getState()));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
