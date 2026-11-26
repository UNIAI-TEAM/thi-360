package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.api.AttemptAudioController.StartAudioPlayRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler;
import vn.unicom.thi360.attempt.audio.AudioAssetFixture;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.api.CreateExamRequest;
import vn.unicom.thi360.exam.api.ExamDetailResponse;
import vn.unicom.thi360.exam.api.ExamPublishRequest;
import vn.unicom.thi360.exam.api.ExamPublishResponse;
import vn.unicom.thi360.exam.api.LoadExamTemplateRequest;
import vn.unicom.thi360.exam.api.UpdateExamListingRequest;
import vn.unicom.thi360.exam.application.ExamCheckCompletion;
import vn.unicom.thi360.exam.application.ExamCheckOrchestrator;
import vn.unicom.thi360.exam.application.ExamCheckScope;
import vn.unicom.thi360.exam.application.ExamListingService;
import vn.unicom.thi360.exam.application.ExamPublishService;
import vn.unicom.thi360.exam.application.ExamService;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.PublishedExamSetSupport;

@Import(AttemptTestFixtures.MutableClockConfig.class)
public class MobileAttemptFixturesIT extends AbstractIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/entitlements-prepare-200-per-part.json",
                "api/entitlements-prepare-200-shared.json",
                "api/entitlements-prepare-200-free-navigation.json",
                "api/entitlements-prepare-200-pre-choice.json",
                "api/entitlements-prepare-200-exhausted-purchase.json",
                "api/entitlements-prepare-200-exhausted-compensation.json",
                "api/entitlements-prepare-200-exhausted-class.json",
                "api/entitlements-prepare-200-open-in-progress.json",
                "api/entitlements-prepare-200-open-starting.json",
                "api/entitlements-prepare-200-class.json",
                "api/entitlements-prepare-403-attempt-access-forbidden.json",
                "api/entitlements-prepare-404-not-found.json",
                "api/attempts-start-202-starting.json",
                "api/attempts-start-200-resumed.json",
                "api/attempts-start-200-aborted.json",
                "api/attempts-start-400-idempotency-key-required.json",
                "api/attempts-start-409-no-attempts-left.json",
                "api/attempts-start-409-no-serving-option.json",
                "api/attempts-start-422-choice-not-allowed.json",
                "api/attempts-start-422-subjects-invalid.json",
                "api/attempts-detail-200-starting.json",
                "api/attempts-detail-200-aborted.json",
                "api/attempts-detail-200-in-progress-shared.json",
                "api/attempts-detail-200-in-progress-per-part.json",
                "api/attempts-detail-200-awaiting-choice.json",
                "api/attempts-detail-200-generating.json",
                "api/attempts-detail-200-generation-failed.json",
                "api/attempts-detail-200-audio-playing.json",
                "api/attempts-detail-200-closed-submitted.json",
                "api/attempts-detail-200-closed-time-up.json",
                "api/attempts-detail-200-closed-choice-timeout.json",
                "api/attempts-detail-404-attempt-not-found.json",
                "api/attempts-session-200.json",
                "api/attempts-session-409-attempt-open-elsewhere.json",
                "api/attempts-session-409-attempt-not-open.json",
                "api/attempts-session-409-time-up.json",
                "api/attempts-heartbeat-200.json",
                "api/attempts-heartbeat-409-attempt-session-revoked.json",
                "api/attempts-answers-200-saved.json",
                "api/attempts-answers-200-stale.json",
                "api/attempts-answers-200-rejected-invalid-answer.json",
                "api/attempts-answers-200-rejected-not-in-scope.json",
                "api/attempts-answers-200-rejected-time-up.json",
                "api/attempts-answers-200-rejected-part-closed.json",
                "api/attempts-answers-409-attempt-session-revoked.json",
                "api/attempts-answers-429-rate-limited.json",
                "api/attempts-finish-200.json",
                "api/attempts-finish-409-time-up.json",
                "api/attempts-finish-409-part-closed.json",
                "api/attempts-finish-409-attempt-part-is-last.json",
                "api/attempts-finish-409-part-finish-not-allowed.json",
                "api/attempts-finish-409-attempt-session-revoked.json",
                "api/attempts-submit-200.json",
                "api/attempts-submit-409-part-not-last.json",
                "api/attempts-submit-409-attempt-session-revoked.json",
                "api/attempts-choice-202.json",
                "api/attempts-choice-200-replayed.json",
                "api/attempts-choice-409-choice-window-closed.json",
                "api/attempts-choice-409-choice-locked.json",
                "api/attempts-choice-409-part-not-awaiting.json",
                "api/attempts-choice-422-choice-not-allowed.json",
                "api/attempts-choice-422-subjects-invalid.json",
                "api/attempts-retry-202.json",
                "api/attempts-retry-200-replayed.json",
                "api/attempts-retry-409-part-not-awaiting.json",
                "api/attempts-retry-409-part-generating.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    private static final Set<String> DETAIL_DYNAMIC_FIELDS = Set.of(
            "attemptId",
            "examId",
            "entitlementId",
            "partId",
            "itemId",
            "parentItemId",
            "assetId",
            "playId",
            "serverNow",
            "startedAt",
            "finishedAt",
            "deadlineAt",
            "closedAt",
            "choiceDeadlineAt",
            "optionOrder",
            "stem",
            "passage",
            "content",
            "$.parts[*].items[*].questionBody.passage",
            "$.parts[*].items[*].questionBody.options[*].id",
            "$.parts[*].items[*].questionBody.options[*].content",
            "$.parts[*].items[*].questionBody.statements[*].id",
            "$.parts[*].items[*].questionBody.statements[*].content",
            "$.parts[*].items[*].questionBody.left[*].id",
            "$.parts[*].items[*].questionBody.left[*].content",
            "$.parts[*].items[*].questionBody.right[*].id",
            "$.parts[*].items[*].questionBody.right[*].content",
            "$.parts[*].items[*].attachments[*].id",
            "$.parts[*].items[*].attachments[*].url",
            "$.parts[*].choice.seed",
            "$.parts[*].choice.subjectCounts.LY",
            "$.parts[*].choice.subjectCounts.HOA",
            "$.parts[*].choice.subjectCounts.SU");

    private static final Set<String> CHOICE_DYNAMIC_FIELDS = Set.of(
            "attemptId",
            "partId",
            "serverNow",
            "choiceConfirmedAt",
            "choiceDeadlineAt",
            "startedAt",
            "deadlineAt",
            "$.choice.subjectCounts.LY",
            "$.choice.subjectCounts.HOA",
            "$.choice.subjectCounts.SU");

    private static final AtomicInteger CODE_SEQ = new AtomicInteger(1);

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MutableClock mutableClock;
    @Autowired private ObjectStorage objectStorage;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private EntitlementPort entitlementPort;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;
    @Autowired private AttemptItemRepository attemptItemRepository;
    @Autowired private ExamService examService;
    @Autowired private ExamListingService examListingService;
    @Autowired private ExamCheckOrchestrator examCheckOrchestrator;
    @Autowired private ExamPublishService examPublishService;
    @Autowired private UserDirectory userDirectory;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @MockitoSpyBean private AttemptPaperAssembler attemptPaperAssembler;
    @MockitoSpyBean private QuestionSourcePort questionSourcePort;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<UUID> createdAssignmentIds = new ArrayList<>();
    private final List<UUID> createdExamIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private AudioAssetFixture audioAssetFixture;
    private MobileFixtureCertifier certifier;

    @BeforeEach
    void setUp() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        audioAssetFixture = new AudioAssetFixture(jdbc, objectStorage);
        certifier = new MobileFixtureCertifier(objectMapper);
        reset(paperSolverPort, attemptPaperAssembler, questionSourcePort);
        pinCandidateOrder();
        pinSolverSeed();
        pinAssemblerSeed();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        reset(paperSolverPort, attemptPaperAssembler, questionSourcePort);
        for (UUID userId : createdUserIds) {
            jdbc.update(
                    "DELETE FROM attempt.attempt_audio_play WHERE attempt_id IN "
                            + "(SELECT id FROM attempt.attempt WHERE user_id = ?)",
                    userId);
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
        for (UUID assignmentId : createdAssignmentIds) {
            jdbc.update("DELETE FROM commerce.class_exam_assignment WHERE id = ?", assignmentId);
        }
        createdAssignmentIds.clear();
        for (UUID classId : createdClassIds) {
            jdbc.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbc.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        createdClassIds.clear();
        AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestionIds);
        createdQuestionIds.clear();
        if (audioAssetFixture != null) {
            audioAssetFixture.cleanup();
        }
        for (UUID examId : createdExamIds) {
            cleanupSyntheticExam(examId);
        }
        createdExamIds.clear();
    }

    @Test
    void allPrepareStartStateSessionAnswerFinishSubmitChoiceAndRetryFixturesMatchLiveResponses() throws Exception {
        UUID student = createStudent("mc-att-main");
        UUID stranger = createStudent("mc-att-stranger");
        UUID manager = createManager("mc-att-mgr");

        certifyPrepareFixtures(student, stranger, manager);
        certifyStartAndDetailFixtures();
        certifySessionHeartbeatAnswersFinishSubmitFixtures();
        certifyChoiceAndRetryFixtures();

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private void certifyPrepareFixtures(UUID student, UUID stranger, UUID manager) throws Exception {
        var hsaFull = grant(student, "DS-HSA-FULL-MINI", 3, List.of());
        certifier.certify("api/entitlements-prepare-200-per-part.json", 200,
                performPrepare(student, hsaFull.entitlementId()),
                Set.of("entitlementId", "examId", "orderCode", "grantedAt"));

        var hscaShared = grant(student, "DS-HSCA-TOAN", 2, List.of());
        certifier.certify("api/entitlements-prepare-200-shared.json", 200,
                performPrepare(student, hscaShared.entitlementId()),
                Set.of("entitlementId", "examId", "orderCode", "grantedAt"));

        var freeNavExam = publishThptqgMath2025Exam();
        var freeNavLine = AttemptTestFixtures.grantExam(
                grantService, student, freeNavExam.examId(), 2, List.of(), "SCORE_AND_ANSWERS", freeNavExam.versionNo());
        certifier.certify("api/entitlements-prepare-200-free-navigation.json", 200,
                performPrepare(student, freeNavLine.entitlementId()),
                Set.of("entitlementId", "examId", "examCode", "orderCode", "grantedAt"));

        var sptPreChoice = grant(student, "DS-SPT-INFORMATICS-MINI", 2, List.of("CS"));
        certifier.certify("api/entitlements-prepare-200-pre-choice.json", 200,
                performPrepare(student, sptPreChoice.entitlementId()),
                Set.of("entitlementId", "examId", "orderCode", "grantedAt"));

        UUID exPurchaseUser = createStudent("mc-prep-ex-pur");
        var exPurchase = grant(exPurchaseUser, "DS-HSA-PART-MATH-MINI", 1, List.of());
        UUID used1 = Ids.newId();
        entitlementPort.reserve(exPurchaseUser, exPurchase.entitlementId(), used1);
        entitlementPort.confirmUse(used1);
        certifier.certify("api/entitlements-prepare-200-exhausted-purchase.json", 200,
                performPrepare(exPurchaseUser, exPurchase.entitlementId()),
                Set.of("entitlementId", "examId"));

        UUID exCompUser = createStudent("mc-prep-ex-comp");
        UUID mathExamId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        UUID compEntId = insertCustomEntitlement(exCompUser, mathExamId, "PURCHASE", "COMPENSATION", null, 1, 1);
        certifier.certify("api/entitlements-prepare-200-exhausted-compensation.json", 200,
                performPrepare(exCompUser, compEntId),
                Set.of("entitlementId", "examId"));

        UUID exClassUser = createStudent("mc-prep-ex-cls");
        UUID exClassId = createClassRoom(exClassUser);
        UUID exClassEntId = insertCustomEntitlement(exClassUser, mathExamId, "CLASS", "CLASS", exClassId, 1, 1);
        certifier.certify("api/entitlements-prepare-200-exhausted-class.json", 200,
                performPrepare(exClassUser, exClassEntId),
                Set.of("entitlementId", "examId"));

        UUID activeClassUser = createStudent("mc-prep-cls");
        UUID activeClassId = createClassRoom(activeClassUser);
        UUID activeClassEntId = insertCustomEntitlement(activeClassUser, mathExamId, "CLASS", "CLASS", activeClassId, 2, 0);
        certifier.certify("api/entitlements-prepare-200-class.json", 200,
                performPrepare(activeClassUser, activeClassEntId),
                Set.of("entitlementId", "examId", "classId", "className", "grantedAt"));

        UUID openInProgUser = createStudent("mc-prep-open-ip");
        var openInProgLine = grant(openInProgUser, "DS-HSA-PART-MATH-MINI", 2, List.of());
        startAndAwaitOpen(openInProgUser, "prep-open-ip", openInProgLine.entitlementId(), null);
        certifier.certify("api/entitlements-prepare-200-open-in-progress.json", 200,
                performPrepare(openInProgUser, openInProgLine.entitlementId()),
                Set.of("entitlementId", "examId", "orderCode", "grantedAt", "attemptId", "startedAt"));

        UUID openStartingUser = createStudent("mc-prep-open-st");
        var openStartingLine = grant(openStartingUser, "DS-HSA-PART-MATH-MINI", 2, List.of());
        createStartingAttemptRow(openStartingUser, openStartingLine);
        certifier.certify("api/entitlements-prepare-200-open-starting.json", 200,
                performPrepare(openStartingUser, openStartingLine.entitlementId()),
                Set.of("entitlementId", "examId", "orderCode", "grantedAt", "attemptId", "startedAt"));

        certifier.certify("api/entitlements-prepare-403-attempt-access-forbidden.json", 403,
                mockMvc.perform(get("/api/v1/me/entitlements/{id}/prepare", hsaFull.entitlementId())
                                .with(AttemptTestFixtures.asManageUser(manager, "manager")))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/entitlements-prepare-404-not-found.json", 404,
                performPrepare(stranger, hsaFull.entitlementId()),
                Set.of("traceId"));
    }

    private void certifyStartAndDetailFixtures() throws Exception {
        UUID uStart = createStudent("mc-start-ok");
        var startLine = grant(uStart, "DS-HSA-PART-MATH-MINI", 2, List.of());

        MvcResult missingKeyRes = performStart(uStart, null, startLine.entitlementId(), null);
        certifier.certify("api/attempts-start-400-idempotency-key-required.json", 400, missingKeyRes, Set.of("traceId"));

        MvcResult start202 = performStart(uStart, "mc-start-k1", startLine.entitlementId(), null);
        certifier.certify("api/attempts-start-202-starting.json", 202, start202, Set.of("attemptId"));
        UUID startedId = UUID.fromString(json(start202).path("attemptId").asText());
        awaitAttemptState(startedId, AttemptState.IN_PROGRESS);

        MvcResult resumed200 = performStart(uStart, "mc-start-k2", startLine.entitlementId(), null);
        certifier.certify("api/attempts-start-200-resumed.json", 200, resumed200, Set.of("attemptId"));

        UUID sidSubmitted = claimSession(uStart, startedId);
        assertThat(performSubmit(uStart, startedId, "mc-sub-closed", sidSubmitted, null).getResponse().getStatus())
                .isEqualTo(200);
        certifier.certify("api/attempts-detail-200-closed-submitted.json", 200,
                performGetState(uStart, startedId), DETAIL_DYNAMIC_FIELDS);

        UUID uAbort = createStudent("mc-start-abort");
        var abortLine = grant(uAbort, "DS-HSA-PART-MATH-MINI", 2, List.of());
        doThrow(new PaperGenerationException(PaperGenerationException.Reason.GENERATION_FAILED, "contract fail"))
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());
        MvcResult failStart = performStart(uAbort, "mc-abort-key", abortLine.entitlementId(), null);
        assertThat(failStart.getResponse().getStatus()).isEqualTo(202);
        UUID abortedId = UUID.fromString(json(failStart).path("attemptId").asText());
        awaitAttemptState(abortedId, AttemptState.ABORTED);
        reset(paperSolverPort);
        pinSolverSeed();

        MvcResult abortedReplay = performStart(uAbort, "mc-abort-key", abortLine.entitlementId(), null);
        certifier.certify("api/attempts-start-200-aborted.json", 200, abortedReplay, Set.of("attemptId"));

        jdbc.update("UPDATE attempt.attempt SET abort_reason = 'STALE_STARTING' WHERE id = ?", abortedId);
        certifier.certify("api/attempts-detail-200-aborted.json", 200,
                performGetState(uAbort, abortedId), DETAIL_DYNAMIC_FIELDS);

        UUID uExhausted = createStudent("mc-start-noleft");
        var oneAttempt = grant(uExhausted, "DS-HSA-PART-MATH-MINI", 1, List.of());
        UUID usedAtt = Ids.newId();
        entitlementPort.reserve(uExhausted, oneAttempt.entitlementId(), usedAtt);
        entitlementPort.confirmUse(usedAtt);
        certifier.certify("api/attempts-start-409-no-attempts-left.json", 409,
                performStart(uExhausted, "mc-noleft", oneAttempt.entitlementId(), null), Set.of("traceId"));

        UUID uNoOpt = createStudent("mc-start-noopt");
        var noOptLine = grant(uNoOpt, "DS-SPT-INFORMATICS-MINI", 2, List.of("NONEXISTENT"));
        certifier.certify("api/attempts-start-409-no-serving-option.json", 409,
                performStart(uNoOpt, "mc-noopt", noOptLine.entitlementId(), new PreChoiceInput("CS", null)),
                Set.of("traceId"));

        UUID uChoiceErr = createStudent("mc-start-cherr");
        var sptLine = grant(uChoiceErr, "DS-SPT-INFORMATICS-MINI", 2, List.of());
        certifier.certify("api/attempts-start-422-choice-not-allowed.json", 422,
                performStart(uChoiceErr, "mc-bad-ch", sptLine.entitlementId(), new PreChoiceInput("NOPE", null)),
                Set.of("traceId"));
        certifier.certify("api/attempts-start-422-subjects-invalid.json", 422,
                performStart(uChoiceErr, "mc-bad-subj", sptLine.entitlementId(), new PreChoiceInput("CS", List.of("TOAN"))),
                Set.of("traceId"));

        UUID uStarting = createStudent("mc-det-starting");
        var startingLine = grant(uStarting, "DS-HSA-PART-MATH-MINI", 2, List.of());
        UUID startingAttemptId = createStartingAttemptRow(uStarting, startingLine);
        certifier.certify("api/attempts-detail-200-starting.json", 200,
                performGetState(uStarting, startingAttemptId), DETAIL_DYNAMIC_FIELDS);

        UUID uShared = createStudent("mc-det-shared");
        var sharedLine = grant(uShared, "DS-HSCA-TOAN", 2, List.of());
        Attempt sharedAttempt = startAndAwaitOpen(uShared, "mc-shared", sharedLine.entitlementId(), null);
        certifier.certify("api/attempts-detail-200-in-progress-shared.json", 200,
                performGetState(uShared, sharedAttempt.getId()), DETAIL_DYNAMIC_FIELDS);

        UUID uPerPart = createStudent("mc-det-perpart");
        var perPartLine = grant(uPerPart, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt perPartAttempt = startAndAwaitOpen(uPerPart, "mc-perpart", perPartLine.entitlementId(), null);
        certifier.certify("api/attempts-detail-200-in-progress-per-part.json", 200,
                performGetState(uPerPart, perPartAttempt.getId()), DETAIL_DYNAMIC_FIELDS);

        UUID uAudio = createStudent("mc-det-audio");
        var mp3 = audioAssetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID audioQ = createReadyListeningQuestion(mp3.assetId(), "MC-DET-AUD");
        stubListeningPaperWithQuestions(List.of(audioQ));
        var audioLine = grant(uAudio, "DS-HSCA-ANH-MINI", 2, List.of());
        Attempt audioAttempt = startAndAwaitOpen(uAudio, "mc-det-aud", audioLine.entitlementId(), null);
        reset(paperSolverPort);
        pinSolverSeed();
        UUID audioSid = claimSession(uAudio, audioAttempt.getId());
        assertThat(mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays",
                                        audioAttempt.getId(), mp3.assetId())
                                        .with(AttemptTestFixtures.asExamUser(uAudio, "learner"))
                                        .header("X-Attempt-Session", audioSid.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new StartAudioPlayRequest("det-aud-1"))))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(200);
        certifier.certify("api/attempts-detail-200-audio-playing.json", 200,
                performGetState(uAudio, audioAttempt.getId()), DETAIL_DYNAMIC_FIELDS);

        UUID uTimeUp = createStudent("mc-det-timeup");
        var timeUpLine = grant(uTimeUp, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt timeUpAttempt = startAndAwaitOpen(uTimeUp, "mc-det-tu", timeUpLine.entitlementId(), null);
        Instant tuDeadline = attemptPartRepository.findByAttemptIdOrderByOrdAsc(timeUpAttempt.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(tuDeadline.plusSeconds(10));
        certifier.certify("api/attempts-detail-200-closed-time-up.json", 200,
                performGetState(uTimeUp, timeUpAttempt.getId()), DETAIL_DYNAMIC_FIELDS);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);

        certifier.certify("api/attempts-detail-404-attempt-not-found.json", 404,
                performGetState(uStart, UUID.randomUUID()), Set.of("traceId"));
    }

    private void certifySessionHeartbeatAnswersFinishSubmitFixtures() throws Exception {
        UUID student = createStudent("mc-sess-ans");
        var fullLine = grant(student, "DS-HSA-FULL-MINI", 5, List.of());
        Attempt fullAttempt = startAndAwaitOpen(student, "mc-full-1", fullLine.entitlementId(), null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(fullAttempt.getId());

        UUID sidA = UUID.randomUUID();
        MvcResult claim200 = performClaimSession(student, fullAttempt.getId(), new ClaimSessionRequest(sidA, false));
        certifier.certify("api/attempts-session-200.json", 200, claim200, Set.of("sessionId", "serverNow"));

        MvcResult elsewhere = performClaimSession(student, fullAttempt.getId(), new ClaimSessionRequest(UUID.randomUUID(), false));
        certifier.certify("api/attempts-session-409-attempt-open-elsewhere.json", 409, elsewhere, Set.of("traceId", "lastSeenAt"));

        MvcResult hb200 = performHeartbeat(student, fullAttempt.getId(), sidA);
        certifier.certify("api/attempts-heartbeat-200.json", 200, hb200, Set.of("serverNow", "deadlineAt"));

        UUID sidB = UUID.randomUUID();
        assertThat(performClaimSession(student, fullAttempt.getId(), new ClaimSessionRequest(sidB, true))
                        .getResponse().getStatus())
                .isEqualTo(200);

        certifier.certify("api/attempts-heartbeat-409-attempt-session-revoked.json", 409,
                performHeartbeat(student, fullAttempt.getId(), sidA), Set.of("traceId"));

        AttemptItem p0Item = singleChoiceItems(parts.get(0).getId()).getFirst();
        String opt0 = p0Item.getOptionOrder().get(0);
        String opt1 = p0Item.getOptionOrder().get(1);

        certifier.certify("api/attempts-answers-409-attempt-session-revoked.json", 409,
                performPutAnswers(student, fullAttempt.getId(), sidA,
                        new SaveAnswersRequest(List.of(new AnswerChange(p0Item.getId(), 1L, new AnswerValue.SingleChoice(opt0))), null, null)),
                Set.of("traceId"));

        certifier.certify("api/attempts-answers-200-saved.json", 200,
                performPutAnswers(student, fullAttempt.getId(), sidB,
                        new SaveAnswersRequest(List.of(new AnswerChange(p0Item.getId(), 3L, new AnswerValue.SingleChoice(opt1))), null, null)),
                Set.of("itemId", "serverNow"));

        certifier.certify("api/attempts-answers-200-stale.json", 200,
                performPutAnswers(student, fullAttempt.getId(), sidB,
                        new SaveAnswersRequest(List.of(new AnswerChange(p0Item.getId(), 1L, new AnswerValue.SingleChoice(opt0))), null, null)),
                Set.of("itemId", "serverNow"));

        certifier.certify("api/attempts-answers-200-rejected-invalid-answer.json", 200,
                performPutAnswers(student, fullAttempt.getId(), sidB,
                        new SaveAnswersRequest(List.of(new AnswerChange(p0Item.getId(), 4L, new AnswerValue.SingleChoice("UNKNOWN-OPT"))), null, null)),
                Set.of("itemId", "serverNow"));

        certifier.certify("api/attempts-answers-200-rejected-not-in-scope.json", 200,
                performPutAnswers(student, fullAttempt.getId(), sidB,
                        new SaveAnswersRequest(List.of(new AnswerChange(UUID.randomUUID(), 1L, new AnswerValue.SingleChoice(opt0))), null, null)),
                Set.of("itemId", "serverNow"));

        certifier.certify("api/attempts-finish-409-attempt-session-revoked.json", 409,
                performFinishPart(student, fullAttempt.getId(), parts.get(0).getId(), sidA, null),
                Set.of("traceId"));

        certifier.certify("api/attempts-finish-409-part-closed.json", 409,
                performFinishPart(student, fullAttempt.getId(), parts.get(1).getId(), sidB, null),
                Set.of("traceId"));

        certifier.certify("api/attempts-submit-409-attempt-session-revoked.json", 409,
                performSubmit(student, fullAttempt.getId(), "mc-sub-rev", sidA, null),
                Set.of("traceId"));

        certifier.certify("api/attempts-submit-409-part-not-last.json", 409,
                performSubmit(student, fullAttempt.getId(), "mc-sub-notlast", sidB, null),
                Set.of("traceId"));

        certifier.certify("api/attempts-finish-200.json", 200,
                performFinishPart(student, fullAttempt.getId(), parts.get(0).getId(), sidB, null),
                Set.of("attemptId", "partId", "serverNow", "closedAt", "startedAt", "deadlineAt"));

        certifier.certify("api/attempts-answers-200-rejected-part-closed.json", 200,
                performPutAnswers(student, fullAttempt.getId(), sidB,
                        new SaveAnswersRequest(List.of(new AnswerChange(p0Item.getId(), 5L, new AnswerValue.SingleChoice(opt0))), null, null)),
                Set.of("itemId", "serverNow"));

        AttemptItem p1Item = singleChoiceItems(parts.get(1).getId()).getFirst();
        Instant p1Deadline = attemptPartRepository.findById(parts.get(1).getId()).orElseThrow().getDeadlineAt();
        mutableClock.setAt(p1Deadline);
        certifier.certify("api/attempts-answers-200-rejected-time-up.json", 200,
                performPutAnswers(student, fullAttempt.getId(), sidB,
                        new SaveAnswersRequest(List.of(new AnswerChange(p1Item.getId(), 1L, new AnswerValue.SingleChoice(p1Item.getOptionOrder().get(0)))), null, null)),
                Set.of("itemId", "serverNow"));

        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID uRate = createStudent("mc-ans-rl");
        var rlLine = grant(uRate, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt rlAttempt = startAndAwaitOpen(uRate, "mc-rl", rlLine.entitlementId(), null);
        UUID rlSid = claimSession(uRate, rlAttempt.getId());
        AttemptItem rlItem = singleChoiceItems(attemptPartRepository.findByAttemptIdOrderByOrdAsc(rlAttempt.getId()).getFirst().getId()).getFirst();
        String rlOpt = rlItem.getOptionOrder().get(0);
        for (int i = 1; i <= 20; i++) {
            assertThat(performPutAnswers(uRate, rlAttempt.getId(), rlSid,
                            new SaveAnswersRequest(List.of(new AnswerChange(rlItem.getId(), (long) i, new AnswerValue.SingleChoice(rlOpt))), null, null))
                            .getResponse().getStatus())
                    .isEqualTo(200);
        }
        MvcResult saveRlRes = performPutAnswers(uRate, rlAttempt.getId(), rlSid,
                new SaveAnswersRequest(List.of(new AnswerChange(rlItem.getId(), 21L, new AnswerValue.SingleChoice(rlOpt))), null, null));
        String retryAfter = saveRlRes.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).as("Retry-After header on 429 answer save").isNotNull();
        assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1L);
        certifier.certify("api/attempts-answers-429-rate-limited.json", 429, saveRlRes, Set.of("traceId"));

        UUID uSingle = createStudent("mc-fin-sub");
        var singleLine = grant(uSingle, "DS-HSA-PART-MATH-MINI", 3, List.of());
        Attempt singleAttempt = startAndAwaitOpen(uSingle, "mc-single-1", singleLine.entitlementId(), null);
        UUID singleSid = claimSession(uSingle, singleAttempt.getId());
        AttemptPart onlyPart = attemptPartRepository.findByAttemptIdOrderByOrdAsc(singleAttempt.getId()).getFirst();

        certifier.certify("api/attempts-finish-409-attempt-part-is-last.json", 409,
                performFinishPart(uSingle, singleAttempt.getId(), onlyPart.getId(), singleSid, null),
                Set.of("traceId"));

        certifier.certify("api/attempts-submit-200.json", 200,
                performSubmit(uSingle, singleAttempt.getId(), "mc-sub-ok", singleSid, null),
                Set.of("attemptId", "partId", "serverNow", "finishedAt", "startedAt", "deadlineAt", "closedAt"));

        certifier.certify("api/attempts-session-409-attempt-not-open.json", 409,
                performClaimSession(uSingle, singleAttempt.getId(), new ClaimSessionRequest(singleSid, false)),
                Set.of("traceId"));

        Attempt expiredForSession = startAndAwaitOpen(uSingle, "mc-sess-tu", singleLine.entitlementId(), null);
        UUID expSid = claimSession(uSingle, expiredForSession.getId());
        Instant expDeadline = attemptPartRepository.findByAttemptIdOrderByOrdAsc(expiredForSession.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(expDeadline.plusSeconds(5));
        certifier.certify("api/attempts-session-409-time-up.json", 409,
                performClaimSession(uSingle, expiredForSession.getId(), new ClaimSessionRequest(expSid, false)),
                Set.of("traceId"));
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);

        UUID uFinTu = createStudent("mc-fin-tu");
        var finTuLine = grant(uFinTu, "DS-VACT-FULL", 2, List.of());
        Attempt finTuAttempt = startAndAwaitOpen(uFinTu, "mc-fin-tu", finTuLine.entitlementId(), null);
        UUID finTuSid = claimSession(uFinTu, finTuAttempt.getId());
        List<AttemptPart> finTuParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(finTuAttempt.getId());
        mutableClock.advance(Duration.ofHours(6));
        certifier.certify("api/attempts-finish-409-time-up.json", 409,
                performFinishPart(uFinTu, finTuAttempt.getId(), finTuParts.get(1).getId(), finTuSid, null),
                Set.of("traceId"));
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);

        UUID uFree = createStudent("mc-fin-free");
        var freeExam = publishThptqgMath2025Exam();
        var freeLine = AttemptTestFixtures.grantExam(
                grantService, uFree, freeExam.examId(), 2, List.of(), "SCORE_AND_ANSWERS", freeExam.versionNo());
        Attempt freeAttempt = startAndAwaitOpen(uFree, "mc-free", freeLine.entitlementId(), null);
        UUID freeSid = claimSession(uFree, freeAttempt.getId());
        AttemptPart freePart0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(freeAttempt.getId()).getFirst();
        certifier.certify("api/attempts-finish-409-part-finish-not-allowed.json", 409,
                performFinishPart(uFree, freeAttempt.getId(), freePart0.getId(), freeSid, null),
                Set.of("traceId"));
    }

    private void certifyChoiceAndRetryFixtures() throws Exception {
        UUID student = createStudent("mc-choice-main");
        var hsaLine = grant(student, "DS-HSA-FULL-MINI", 5, List.of());

        Attempt a1 = startAndAwaitOpen(student, "mc-ch-1", hsaLine.entitlementId(), null);
        UUID sid1 = claimSession(student, a1.getId());
        List<AttemptPart> a1Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId());

        certifier.certify("api/attempts-choice-409-part-not-awaiting.json", 409,
                performChoice(student, a1.getId(), a1Parts.get(0).getId(), "mc-ch-na", sid1, new ConfirmPartChoiceRequest("ANH", null)),
                Set.of("traceId"));

        assertThat(performFinishPart(student, a1.getId(), a1Parts.get(0).getId(), sid1, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(performFinishPart(student, a1.getId(), a1Parts.get(1).getId(), sid1, null).getResponse().getStatus()).isEqualTo(200);
        AttemptPart a1P2 = attemptPartRepository.findById(a1Parts.get(2).getId()).orElseThrow();

        certifier.certify("api/attempts-detail-200-awaiting-choice.json", 200,
                performGetState(student, a1.getId()), DETAIL_DYNAMIC_FIELDS);

        certifier.certify("api/attempts-retry-409-part-not-awaiting.json", 409,
                performRetry(student, a1.getId(), a1P2.getId(), "mc-ret-na", sid1, null),
                Set.of("traceId"));

        certifier.certify("api/attempts-choice-422-choice-not-allowed.json", 422,
                performChoice(student, a1.getId(), a1P2.getId(), "mc-ch-badopt", sid1, new ConfirmPartChoiceRequest("NOPE", null)),
                Set.of("traceId"));

        certifier.certify("api/attempts-choice-422-subjects-invalid.json", 422,
                performChoice(student, a1.getId(), a1P2.getId(), "mc-ch-badsub", sid1, new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA"))),
                Set.of("traceId"));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            List<String> partCodes = inv.getArgument(2);
            if (partCodes.contains(a1P2.getPartCode())) {
                entered.countDown();
                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                throw new PaperGenerationException(PaperGenerationException.Reason.GENERATION_FAILED, "fail-for-retry");
            }
            return inv.callRealMethod();
        }).when(paperSolverPort).generate(any(), anyInt(), any(), any(), anyLong(), any());

        try {
            MvcResult choice202 = performChoice(student, a1.getId(), a1P2.getId(), "mc-ch-ok", sid1,
                    new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SU")));
            certifier.certify("api/attempts-choice-202.json", 202, choice202, CHOICE_DYNAMIC_FIELDS);

            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            certifier.certify("api/attempts-detail-200-generating.json", 200,
                    performGetState(student, a1.getId()), DETAIL_DYNAMIC_FIELDS);

            certifier.certify("api/attempts-retry-409-part-generating.json", 409,
                    performRetry(student, a1.getId(), a1P2.getId(), "mc-ret-gen", sid1, null),
                    Set.of("traceId"));

            certifier.certify("api/attempts-choice-409-choice-locked.json", 409,
                    performChoice(student, a1.getId(), a1P2.getId(), "mc-ch-lock", sid1, new ConfirmPartChoiceRequest("ANH", null)),
                    Set.of("traceId"));

            release.countDown();
            awaitPartState(a1P2.getId(), PartState.GENERATION_FAILED);
        } finally {
            release.countDown();
            reset(paperSolverPort);
            pinSolverSeed();
        }

        certifier.certify("api/attempts-detail-200-generation-failed.json", 200,
                performGetState(student, a1.getId()), DETAIL_DYNAMIC_FIELDS);

        certifier.certify("api/attempts-choice-200-replayed.json", 200,
                performChoice(student, a1.getId(), a1P2.getId(), "mc-ch-ok", sid1,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SU"))),
                CHOICE_DYNAMIC_FIELDS);

        MvcResult retry202 = performRetry(student, a1.getId(), a1P2.getId(), "mc-ret-ok", sid1, new RetryPartRequest("retry"));
        certifier.certify("api/attempts-retry-202.json", 202, retry202, CHOICE_DYNAMIC_FIELDS);
        awaitPartState(a1P2.getId(), PartState.OPEN);

        certifier.certify("api/attempts-retry-200-replayed.json", 200,
                performRetry(student, a1.getId(), a1P2.getId(), "mc-ret-ok", sid1, new RetryPartRequest("retry")),
                CHOICE_DYNAMIC_FIELDS);

        assertThat(performSubmit(student, a1.getId(), "mc-sub-a1", sid1, null).getResponse().getStatus()).isEqualTo(200);

        Attempt a2 = startAndAwaitOpen(student, "mc-ch-2", hsaLine.entitlementId(), null);
        UUID sid2 = claimSession(student, a2.getId());
        List<AttemptPart> a2Parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId());
        assertThat(performFinishPart(student, a2.getId(), a2Parts.get(0).getId(), sid2, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(performFinishPart(student, a2.getId(), a2Parts.get(1).getId(), sid2, null).getResponse().getStatus()).isEqualTo(200);
        AttemptPart a2P2 = attemptPartRepository.findById(a2Parts.get(2).getId()).orElseThrow();
        mutableClock.setAt(a2P2.getChoiceDeadlineAt().plusSeconds(5));

        certifier.certify("api/attempts-choice-409-choice-window-closed.json", 409,
                performChoice(student, a2.getId(), a2P2.getId(), "mc-ch-winclosed", sid2, new ConfirmPartChoiceRequest("ANH", null)),
                Set.of("traceId"));

        certifier.certify("api/attempts-detail-200-closed-choice-timeout.json", 200,
                performGetState(student, a2.getId()), DETAIL_DYNAMIC_FIELDS);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
    }

    private UUID createStudent(String label) {
        UUID id = AttemptTestFixtures.createStudentAccount(jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    private UUID createManager(String label) {
        UUID id = AttemptTestFixtures.createManageAccount(jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    private UUID createClassRoom(UUID memberId) {
        UUID classId = UUID.randomUUID();
        String code = "CLSM" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO classroom.class_room (id, code, code_normalized, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, now(), now(), 0)
                """, classId, code, code.toLowerCase(), "Lớp ôn thi " + code);
        jdbc.update("""
                INSERT INTO classroom.class_member (class_id, user_id, joined_at, added_by)
                VALUES (?, ?, now(), ?)
                """, classId, memberId, memberId);
        createdClassIds.add(classId);
        return classId;
    }

    private UUID insertCustomEntitlement(
            UUID userId, UUID examId, String entitlementSource, String grantKind, UUID sourceRef, int total, int used) {
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        UUID entId = Ids.newId();
        UUID grantId = Ids.newId();
        Timestamp now = Timestamp.from(mutableClock.instant());
        UUID compensationAttemptId = "COMPENSATION".equals(grantKind) ? Ids.newId() : null;
        UUID classAssignmentId = null;
        if ("CLASS".equals(grantKind)) {
            classAssignmentId = Ids.newId();
            jdbc.update("""
                    INSERT INTO commerce.class_exam_assignment
                        (id, class_id, exam_id, exam_version_no, exam_code, exam_name, attempts_granted,
                         result_view, assigned_by, assigned_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'SCORE_AND_ANSWERS', ?, ?, ?, 0)
                    """,
                    classAssignmentId, sourceRef, examId, serving.version().versionNo(),
                    serving.version().code(), serving.version().name(), total, userId, now, now);
            createdAssignmentIds.add(classAssignmentId);
        }
        jdbc.update("""
                INSERT INTO commerce.entitlement
                    (id, user_id, exam_id, source, source_ref, attempts_total, attempts_used, attempts_reserved,
                     attempts_revoked, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, 0)
                """, entId, userId, examId, entitlementSource, sourceRef, total, used, now, now);
        jdbc.update("""
                INSERT INTO commerce.entitlement_grant
                    (id, entitlement_id, kind, exam_id, compensation_attempt_id, class_assignment_id,
                     option_codes, attempts, attempts_used, attempts_reserved, attempts_revoked,
                     benefits, result_view, exam_version_no, granted_at, granted_by, version)
                VALUES (?, ?, ?, ?, ?, ?, '{}'::text[], ?, ?, 0, 0, '{"aiExplanation":true}'::jsonb,
                        'SCORE_AND_ANSWERS', ?, ?, 'SYSTEM', 0)
                """, grantId, entId, grantKind, examId, compensationAttemptId, classAssignmentId,
                total, used, serving.version().versionNo(), now);
        return entId;
    }

    private UUID createStartingAttemptRow(UUID userId, GrantOrderResult.Line line) {
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();
        UUID attemptId = Ids.newId();
        entitlementPort.reserve(userId, line.entitlementId(), attemptId);
        attemptRepository.saveAndFlush(Attempt.start(
                attemptId, userId, line.examId(), v.versionNo(), line.entitlementId(), line.grantId(),
                v.name(), v.kindItemId(), v.kindCode(), v.scope(), v.partCode(), v.subjectItemId(),
                AttemptTiming.valueOf(v.timing()), NavigationMode.SEQUENTIAL, Map.of(), 42L,
                Ids.newId(), "SCORE_AND_ANSWERS", v.practiceScoring(), v.referenceMax(), v.maxScore(),
                "seed-starting-" + attemptId, mutableClock.instant()));
        return attemptId;
    }

    private GrantOrderResult.Line grant(UUID userId, String examCode, int attempts, List<String> optionCodes) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        return AttemptTestFixtures.grantExam(
                grantService, userId, examId, attempts, optionCodes, "SCORE_AND_ANSWERS", serving.version().versionNo());
    }

    private MvcResult performPrepare(UUID userId, UUID entitlementId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/entitlements/{id}/prepare", entitlementId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    private MvcResult performStart(UUID userId, String idempotencyKey, UUID entitlementId, PreChoiceInput preChoice)
            throws Exception {
        var req = post("/api/v1/me/attempts")
                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartAttemptRequest(entitlementId, preChoice)));
        if (idempotencyKey != null) {
            req = req.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(req).andReturn();
    }

    private Attempt startAndAwaitOpen(UUID userId, String keyPrefix, UUID entitlementId, PreChoiceInput preChoice)
            throws Exception {
        MvcResult res = performStart(userId, keyPrefix + "-" + UUID.randomUUID(), entitlementId, preChoice);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        return awaitAttemptState(UUID.fromString(json(res).path("attemptId").asText()), AttemptState.IN_PROGRESS);
    }

    private MvcResult performGetState(UUID userId, UUID attemptId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/attempts/{id}", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    private MvcResult performClaimSession(UUID userId, UUID attemptId, ClaimSessionRequest request) throws Exception {
        var req = post("/api/v1/me/attempts/{id}/session", attemptId)
                .with(AttemptTestFixtures.asExamUser(userId, "learner"));
        if (request != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request));
        }
        return mockMvc.perform(req).andReturn();
    }

    private UUID claimSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res = performClaimSession(userId, attemptId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(json(res).path("sessionId").asText());
    }

    private MvcResult performHeartbeat(UUID userId, UUID attemptId, UUID sessionId) throws Exception {
        var req = post("/api/v1/me/attempts/{id}/heartbeat", attemptId)
                .with(AttemptTestFixtures.asExamUser(userId, "learner"));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    private MvcResult performPutAnswers(UUID userId, UUID attemptId, UUID sessionId, SaveAnswersRequest request)
            throws Exception {
        var req = put("/api/v1/me/attempts/{id}/answers", attemptId)
                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    private MvcResult performFinishPart(UUID userId, UUID attemptId, UUID partId, UUID sessionId, FinishPartRequest request)
            throws Exception {
        var req = post("/api/v1/me/attempts/{id}/parts/{partId}/finish", attemptId, partId)
                .with(AttemptTestFixtures.asExamUser(userId, "learner"));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        if (request != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request));
        }
        return mockMvc.perform(req).andReturn();
    }

    private MvcResult performSubmit(UUID userId, UUID attemptId, String idempotencyKey, UUID sessionId, SubmitAttemptRequest request)
            throws Exception {
        var req = post("/api/v1/me/attempts/{id}/submit", attemptId)
                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                .header("Idempotency-Key", idempotencyKey);
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        if (request != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request));
        }
        return mockMvc.perform(req).andReturn();
    }

    private MvcResult performChoice(
            UUID userId, UUID attemptId, UUID partId, String idempotencyKey, UUID sessionId, ConfirmPartChoiceRequest request)
            throws Exception {
        var req = post("/api/v1/me/attempts/{id}/parts/{partId}/choice", attemptId, partId)
                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    private MvcResult performRetry(
            UUID userId, UUID attemptId, UUID partId, String idempotencyKey, UUID sessionId, RetryPartRequest request)
            throws Exception {
        var req = post("/api/v1/me/attempts/{id}/parts/{partId}/retry", attemptId, partId)
                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                .header("Idempotency-Key", idempotencyKey);
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        if (request != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request));
        }
        return mockMvc.perform(req).andReturn();
    }

    private List<AttemptItem> singleChoiceItems(UUID partId) {
        return attemptItemRepository.findByPartIdOrderByOrdAsc(partId).stream()
                .filter(i -> i.isInScope()
                        && i.getQuestionType() == QuestionType.SINGLE_CHOICE
                        && i.getOptionOrder() != null
                        && i.getOptionOrder().size() >= 2)
                .toList();
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
        throw new AssertionError("Timed out waiting for attempt " + attemptId + " -> " + targetSet
                + ", actual=" + (latest == null ? "MISSING" : latest.getState()));
    }

    private AttemptPart awaitPartState(UUID partId, PartState... targets) throws InterruptedException {
        Set<PartState> targetSet = EnumSet.noneOf(PartState.class);
        targetSet.addAll(List.of(targets));
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            AttemptPart current = attemptPartRepository.findById(partId).orElse(null);
            if (current != null && targetSet.contains(current.getState())) {
                return current;
            }
            Thread.sleep(25);
        }
        AttemptPart latest = attemptPartRepository.findById(partId).orElse(null);
        throw new AssertionError("Timed out waiting for part " + partId + " -> " + targetSet
                + ", actual=" + (latest == null ? "MISSING" : latest.getState()));
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

    private void stubListeningPaperWithQuestions(List<UUID> replacementQuestionIds) {
        doAnswer(inv -> {
            GeneratedPaper real = (GeneratedPaper) inv.callRealMethod();
            List<GeneratedPart> parts = new ArrayList<>(real.parts().size());
            GeneratedPart firstPart = real.parts().getFirst();
            GeneratedSection firstSec = firstPart.sections().getFirst();
            GeneratedGroup firstGroup = firstSec.groups().getFirst();
            List<GeneratedQuestion> customQuestions = replacementQuestionIds.stream()
                    .map(id -> new GeneratedQuestion(id, 1))
                    .toList();
            List<GeneratedGroup> updatedGroups = List.of(
                    new GeneratedGroup(firstGroup.groupId(), customQuestions, firstGroup.shuffleUnits()));
            List<GeneratedSection> updatedSecs = List.of(
                    new GeneratedSection(firstSec.sectionId(), firstSec.subjectCode(), customQuestions.size(), updatedGroups));
            parts.add(new GeneratedPart(firstPart.partCode(), updatedSecs));
            for (int i = 1; i < real.parts().size(); i++) {
                parts.add(real.parts().get(i));
            }
            return new GeneratedPaper(parts, real.dropped());
        }).when(paperSolverPort).generate(any(), anyInt(), any(), any(), anyLong(), any());
    }

    private UUID createReadyListeningQuestion(UUID assetId, String prefix) throws Exception {
        UUID adminId = UUID.randomUUID();
        SaveQuestionRequest createReq = new SaveQuestionRequest(
                QuestionType.SINGLE_CHOICE,
                "ANH",
                "G12",
                "TH",
                null,
                null,
                BigDecimal.ONE,
                List.of(),
                List.of(),
                "<p>Nghe đoạn hội thoại và chọn đáp án đúng.</p>",
                null,
                List.of(),
                List.of(assetId),
                new QuestionBody.SingleChoiceBody(
                        QuestionType.SINGLE_CHOICE,
                        List.of(
                                new QuestionBody.ChoiceOption("o1", "<p>Đáp án A</p>", true),
                                new QuestionBody.ChoiceOption("o2", "<p>Đáp án B</p>", false)),
                        false),
                prefix + "-" + UUID.randomUUID().toString().substring(0, 6));

        MvcResult created = mockMvc.perform(post("/api/v1/admin/questions")
                        .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", "mc-aud-q-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isBetween(200, 201);
        UUID questionId = UUID.fromString(json(created).path("summary").path("id").asText());
        createdQuestionIds.add(questionId);

        assertThat(mockMvc.perform(post("/api/v1/admin/questions/bulk/ready")
                                .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "mc-aud-r-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(questionId)))))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(200);
        return questionId;
    }

    private record PublishedExam(UUID examId, int versionNo) {}

    private PublishedExam publishThptqgMath2025Exam() {
        UserDirectory.UserSummary actor = userDirectory.findActiveByUsername("sys.admin").orElseThrow();
        SecurityContext prev = SecurityContextHolder.getContext();
        String code = "THPTQG-MC-" + CODE_SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        try {
            CurrentUser admin = new CurrentUser(actor.id(), actor.username(), Realm.MASTER, Access.MANAGE, PermissionSet.all());
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(new CurrentUserAuthenticationToken(admin, "it-admin-session"));
            SecurityContextHolder.setContext(ctx);

            ExamDetailResponse created = examService.createExam(
                    "create-" + code,
                    new CreateExamRequest(
                            "THPTQG", "SUBJECT", null, "TOAN", code, "Đề thi THPTQG Toán",
                            90, null, "Đề thi THPTQG môn Toán", null, "STANDARD", null));
            createdExamIds.add(created.id());
            if (!"THPTQG_MATH_2025".equals(created.templateCode())) {
                examService.loadTemplate(
                        created.id(),
                        new LoadExamTemplateRequest("THPTQG_MATH_2025", Boolean.TRUE, "USE_TEMPLATE", created.revision()));
            }
            Long revForListing = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, created.id());
            examListingService.update(
                    created.id(),
                    new UpdateExamListingRequest(
                            "Tóm tắt", "Mục tiêu", "Học sinh lớp 12", 50_000L, 3,
                            true, true, true, null, "SCORE_AND_ANSWERS", "Mô tả", null, revForListing));
            var checkResult = examCheckOrchestrator.check(
                    created.id(),
                    ExamCheckScope.from(null, null, null),
                    new ExamCheckCompletion.Actor(actor.id(), actor.username(), AuditActorType.USER),
                    null);
            List<String> validPathIds = new ArrayList<>();
            checkResult.path("paths").forEach(p -> {
                if ("VALID".equals(p.path("result").asText())) {
                    validPathIds.add(p.path("pathId").asText());
                }
            });
            assertThat(validPathIds).isNotEmpty();
            Long revForPublish = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, created.id());
            ExamPublishResponse published = examPublishService.publish(
                    created.id(), "pub-" + code, new ExamPublishRequest(validPathIds, "mobile contract", revForPublish));
            return new PublishedExam(created.id(), published.version());
        } finally {
            SecurityContextHolder.setContext(prev);
        }
    }

    private void cleanupSyntheticExam(UUID examId) {
        jdbc.update("DELETE FROM questionbank.question_usage_ledger WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM shared.outbox_event WHERE payload->>'examId' = ? OR payload->>'targetId' = ?",
                examId.toString(), examId.toString());
        jdbc.update("DELETE FROM exam.exam_check_run WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam_cover_asset WHERE exam_id = ?", examId);
        jdbc.update("DELETE FROM exam.exam WHERE id = ?", examId);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
