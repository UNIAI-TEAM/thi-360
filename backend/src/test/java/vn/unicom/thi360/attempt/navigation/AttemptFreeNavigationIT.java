package vn.unicom.thi360.attempt.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import vn.unicom.thi360.attempt.api.AttemptPosition;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.api.ClaimSessionResponse;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerStatus;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.PartSummaryView;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.api.SaveAnswersResponse;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.api.SubmitAttemptResponse;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.application.OverdueAttemptCloser;
import vn.unicom.thi360.attempt.audio.AudioAssetFixture;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
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
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.api.QuestionDetail;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.application.QuestionService;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.ForbiddenKeysAssert;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * End-to-end integration tests for AT-8 (UNI-2729) free navigation in THPTQG attempts covering
 * AC-1 through AC-10, AD-153 prepare contract, and risk scenarios.
 */
@Import(AttemptTestFixtures.MutableClockConfig.class)
class AttemptFreeNavigationIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void registerDsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    private static final AtomicInteger CODE_SEQ = new AtomicInteger(1);

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
    @Autowired private AttemptAudioPlayRepository attemptAudioPlayRepository;
    @Autowired private AttemptDeadlineWorker deadlineWorker;
    @Autowired private ExamService examService;
    @Autowired private ExamListingService examListingService;
    @Autowired private ExamCheckOrchestrator examCheckOrchestrator;
    @Autowired private ExamPublishService examPublishService;
    @Autowired private QuestionService questionService;
    @Autowired private UserDirectory userDirectory;
    @Autowired private ObjectStorage objectStorage;

    @MockitoSpyBean private EntitlementPort entitlementPort;
    @MockitoSpyBean private OverdueAttemptCloser overdueCloser;
    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdExamIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private final List<UUID> createdAudioAssetIds = new ArrayList<>();
    private AudioAssetFixture audioAssetFixture;

    @BeforeEach
    void setUp() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Mockito.reset(entitlementPort, overdueCloser, paperSolverPort);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Mockito.reset(entitlementPort, overdueCloser, paperSolverPort);

        for (UUID userId : createdUsers) {
            jdbc.update(
                    "DELETE FROM attempt.attempt_audio_play WHERE attempt_id IN "
                            + "(SELECT id FROM attempt.attempt WHERE user_id = ?)",
                    userId);
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUsers.clear();

        if (!createdQuestionIds.isEmpty()) {
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestionIds);
            createdQuestionIds.clear();
        }
        for (UUID assetId : createdAudioAssetIds) {
            jdbc.update("DELETE FROM attempt.audio_asset_duration WHERE asset_id = ?", assetId);
        }
        createdAudioAssetIds.clear();
        if (audioAssetFixture != null) {
            audioAssetFixture.cleanup();
            audioAssetFixture = null;
        }
        for (UUID examId : createdExamIds) {
            cleanupSyntheticExam(examId);
        }
        createdExamIds.clear();
    }

    @Test
    void allPartsOpenWithSharedDeadline() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID userId = createStudent("ac1-all-open");
        GrantOrderResult.Line grant = grant(userId, exam.examId(), exam.versionNo(), 2, List.of());

        AttemptPrepareResponse prepare = getPrepare(userId, grant.entitlementId());
        assertThat(prepare.navigationMode()).isEqualTo(NavigationMode.FREE);

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "ac1-start", null);
        Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
        List<AttemptEvent> events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);

        assertThat(attempt.getState()).isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(attempt.getExamKindCode()).isEqualTo("THPTQG");
        assertThat(attempt.getTiming()).isEqualTo(AttemptTiming.SHARED);
        assertThat(attempt.getNavigationMode()).isEqualTo(NavigationMode.FREE);
        assertThat(attempt.getDeadlineAt()).isEqualTo(attempt.getStartedAt().plus(Duration.ofMinutes(90)));

        assertThat(parts).hasSize(3);
        assertThat(parts).extracting(AttemptPart::getOrd).containsExactly(0, 1, 2);
        assertThat(parts).extracting(AttemptPart::getState).containsOnly(PartState.OPEN);
        assertThat(parts).extracting(AttemptPart::getStartedAt).containsOnly(attempt.getStartedAt());
        assertThat(parts).extracting(AttemptPart::getDeadlineAt).containsOnly(attempt.getDeadlineAt());

        assertThat(items).hasSize(22);
        List<AttemptEvent> partOpenedEvents =
                events.stream().filter(e -> e.getType() == AttemptEventType.PART_OPENED).toList();
        assertThat(partOpenedEvents).hasSize(3);
        assertThat(partOpenedEvents)
                .extracting(AttemptEvent::getPartId)
                .containsExactlyElementsOf(parts.stream().map(AttemptPart::getId).toList());

        JsonNode state = getState(userId, attemptId);
        assertThat(state.path("examKindCode").asText()).isEqualTo("THPTQG");
        assertThat(state.path("timing").asText()).isEqualTo("SHARED");
        assertThat(state.path("navigationMode").asText()).isEqualTo("FREE");
        assertThat(Instant.parse(state.path("deadlineAt").asText())).isEqualTo(attempt.getDeadlineAt());
        JsonNode partsJson = state.path("parts");
        assertThat(partsJson).hasSize(3);
        List<Integer> displayNos = new ArrayList<>();
        for (JsonNode p : partsJson) {
            assertThat(p.path("state").asText()).isEqualTo("OPEN");
            assertThat(p.path("items")).isNotEmpty();
            assertThat(Instant.parse(p.path("deadlineAt").asText())).isEqualTo(attempt.getDeadlineAt());
            for (JsonNode item : p.path("items")) {
                displayNos.add(item.path("displayNo").asInt());
            }
        }
        assertThat(displayNos).hasSize(22);
        for (int i = 0; i < 22; i++) {
            assertThat(displayNos.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void answersSavedInAnyPart() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID userId = createStudent("ac2-any-part");
        GrantOrderResult.Line grant = grant(userId, exam.examId(), exam.versionNo(), 2, List.of());

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "ac2-start", null);
        JsonNode initialState = getState(userId, attemptId);
        JsonNode p0 = initialState.path("parts").get(0);
        JsonNode p1 = initialState.path("parts").get(1);
        JsonNode p2 = initialState.path("parts").get(2);

        JsonNode p0Item = p0.path("items").get(0);
        JsonNode p1Item = p1.path("items").get(0);
        JsonNode p2Item = p2.path("items").get(0);
        UUID p0ItemId = uuid(p0Item, "itemId");
        UUID p1ItemId = uuid(p1Item, "itemId");
        UUID p2ItemId = uuid(p2Item, "itemId");

        AnswerValue p0Answer = answerForItem(p0Item);
        AnswerValue p1Answer = answerForItem(p1Item);
        AnswerValue p2Answer = answerForItem(p2Item);

        UUID sessionId = claimSession(userId, attemptId, null, false);

        // Save Part 2 first, then Part 0, then batch across all three parts.
        SaveAnswersResponse respP2 =
                putAnswers(
                        userId,
                        attemptId,
                        sessionId,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p2ItemId, 1L, p2Answer)),
                                List.of(),
                                new AttemptPosition(uuid(p2, "partId"), p2ItemId)));
        assertThat(respP2.results()).singleElement().satisfies(r ->
                assertThat(r.status()).isEqualTo(FinalAnswerStatus.SAVED));

        SaveAnswersResponse respP0 =
                putAnswers(
                        userId,
                        attemptId,
                        sessionId,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p0ItemId, 1L, p0Answer)),
                                List.of(),
                                new AttemptPosition(uuid(p0, "partId"), p0ItemId)));
        assertThat(respP0.results()).singleElement().satisfies(r ->
                assertThat(r.status()).isEqualTo(FinalAnswerStatus.SAVED));

        AnswerValue p0Updated = new AnswerValue.SingleChoice(p0Item.path("optionOrder").get(1).asText());
        SaveAnswersResponse batchAllThree =
                putAnswers(
                        userId,
                        attemptId,
                        sessionId,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(p0ItemId, 2L, p0Updated),
                                        new AnswerChange(p1ItemId, 1L, p1Answer),
                                        new AnswerChange(p2ItemId, 2L, new AnswerValue.ShortAnswer("2"))),
                                List.of(),
                                new AttemptPosition(uuid(p1, "partId"), p1ItemId)));
        assertThat(batchAllThree.results()).hasSize(3);
        assertThat(batchAllThree.results()).allMatch(r -> r.status() == FinalAnswerStatus.SAVED);

        JsonNode afterState = getState(userId, attemptId);
        JsonNode p0After = findItem(afterState, p0ItemId);
        assertThat(readAnswer(p0After)).isEqualTo(p0Updated);
        assertThat(p0After.path("answerSeq").asLong()).isEqualTo(2L);
        assertThat(readAnswer(findItem(afterState, p1ItemId))).isEqualTo(p1Answer);
        assertThat(readAnswer(findItem(afterState, p2ItemId))).isEqualTo(new AnswerValue.ShortAnswer("2"));
        assertThat(attemptItemRepository.findById(p0ItemId).orElseThrow().getAnswer()).isEqualTo(p0Updated);
        assertThat(attemptItemRepository.findById(p1ItemId).orElseThrow().getAnswer()).isEqualTo(p1Answer);
    }

    @Test
    void positionRestored() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID userId = createStudent("ac2-position");
        GrantOrderResult.Line grant = grant(userId, exam.examId(), exam.versionNo(), 2, List.of());

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "ac2-pos-start", null);
        JsonNode initialState = getState(userId, attemptId);
        JsonNode p0 = initialState.path("parts").get(0);
        JsonNode p2 = initialState.path("parts").get(2);
        JsonNode p2Item = p2.path("items").get(2);
        JsonNode p0Item = p0.path("items").get(1);
        UUID p0PartId = uuid(p0, "partId");
        UUID p2PartId = uuid(p2, "partId");
        UUID p0ItemId = uuid(p0Item, "itemId");
        UUID p2ItemId = uuid(p2Item, "itemId");

        UUID s1 = claimSession(userId, attemptId, null, false);
        putAnswers(
                userId,
                attemptId,
                s1,
                new SaveAnswersRequest(
                        List.of(new AnswerChange(p2ItemId, 1L, answerForItem(p2Item))),
                        List.of(),
                        new AttemptPosition(p2PartId, p2ItemId)));

        JsonNode midState = getState(userId, attemptId);
        assertThat(midState.path("lastPosition").path("partId").asText()).isEqualTo(p2PartId.toString());
        assertThat(midState.path("lastPosition").path("itemId").asText()).isEqualTo(p2ItemId.toString());

        // Move back to Part 0 and reload state.
        putAnswers(
                userId,
                attemptId,
                s1,
                new SaveAnswersRequest(
                        List.of(new AnswerChange(p0ItemId, 1L, answerForItem(p0Item))),
                        List.of(),
                        new AttemptPosition(p0PartId, p0ItemId)));

        claimSession(userId, attemptId, null, true);
        JsonNode restored = getState(userId, attemptId);
        assertThat(restored.path("lastPosition").path("partId").asText()).isEqualTo(p0PartId.toString());
        assertThat(restored.path("lastPosition").path("itemId").asText()).isEqualTo(p0ItemId.toString());
        assertThat(readAnswer(findItem(restored, p2ItemId))).isNotNull();
        assertThat(readAnswer(findItem(restored, p0ItemId))).isNotNull();
    }

    @Test
    void finishIsRejected() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID userId = createStudent("ac3-finish-rej");
        GrantOrderResult.Line grant = grant(userId, exam.examId(), exam.versionNo(), 2, List.of());

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "ac3-start", null);
        JsonNode state = getState(userId, attemptId);
        UUID sessionId = claimSession(userId, attemptId, null, false);

        UUID p0Id = uuid(state.path("parts").get(0), "partId");
        UUID p1Id = uuid(state.path("parts").get(1), "partId");
        UUID p2Id = uuid(state.path("parts").get(2), "partId");
        JsonNode p0Item = state.path("parts").get(0).path("items").get(0);
        UUID p0ItemId = uuid(p0Item, "itemId");
        FinishPartRequest finishWithFinalAnswer =
                new FinishPartRequest(
                        List.of(new FinalAnswerChange(p0ItemId, 1L, answerForItem(p0Item))));

        MvcResult viRes = postFinishPart(userId, attemptId, p0Id, sessionId, finishWithFinalAnswer, "vi");
        assertThat(viRes.getResponse().getStatus()).isEqualTo(409);
        String viRaw = viRes.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(viRaw);
        JsonNode viBody = objectMapper.readTree(viRaw);
        assertThat(viBody.path("code").asText()).isEqualTo("PART_FINISH_NOT_ALLOWED");
        assertThat(viBody.path("message").asText()).isNotBlank().doesNotContain("PART_FINISH_NOT_ALLOWED");

        MvcResult repeatRes = postFinishPart(userId, attemptId, p0Id, sessionId, finishWithFinalAnswer, "vi");
        assertThat(repeatRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(repeatRes.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("PART_FINISH_NOT_ALLOWED");

        MvcResult enResMid = postFinishPart(userId, attemptId, p1Id, sessionId, finishWithFinalAnswer, "en");
        assertThat(enResMid.getResponse().getStatus()).isEqualTo(409);
        JsonNode enMidBody = objectMapper.readTree(enResMid.getResponse().getContentAsString());
        assertThat(enMidBody.path("code").asText()).isEqualTo("PART_FINISH_NOT_ALLOWED");
        assertThat(enMidBody.path("message").asText())
                .isNotBlank()
                .isNotEqualTo(viBody.path("message").asText());

        MvcResult enResLast = postFinishPart(userId, attemptId, p2Id, sessionId, finishWithFinalAnswer, "en");
        assertThat(enResLast.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(enResLast.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("PART_FINISH_NOT_ALLOWED");

        Attempt attemptAfter = attemptRepository.findById(attemptId).orElseThrow();
        List<AttemptPart> partsAfter = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        AttemptItem itemAfter = attemptItemRepository.findById(p0ItemId).orElseThrow();
        List<AttemptEvent> events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);

        assertThat(attemptAfter.getState()).isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(partsAfter).extracting(AttemptPart::getState).containsOnly(PartState.OPEN);
        assertThat(partsAfter).extracting(AttemptPart::getClosedAt).containsOnlyNulls();
        assertThat(itemAfter.getAnswerSeq()).isZero();
        assertThat(itemAfter.getAnswer()).isNull();
        assertThat(events).noneMatch(e -> e.getType() == AttemptEventType.PART_CLOSED);
        assertThat(usageState(attemptId)).isEqualTo("RESERVED");
    }

    @Test
    void submitFromAnyPartClosesAll() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID userId = createStudent("ac4-submit-any");
        GrantOrderResult.Line grant = grant(userId, exam.examId(), exam.versionNo(), 2, List.of());

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "ac4-start", null);
        JsonNode state = getState(userId, attemptId);
        UUID sessionId = claimSession(userId, attemptId, null, false);

        JsonNode p0 = state.path("parts").get(0);
        JsonNode p0Item = p0.path("items").get(0);
        UUID p0ItemId = uuid(p0Item, "itemId");
        putAnswers(
                userId,
                attemptId,
                sessionId,
                new SaveAnswersRequest(
                        List.of(new AnswerChange(p0ItemId, 1L, answerForItem(p0Item))),
                        List.of(),
                        new AttemptPosition(uuid(p0, "partId"), p0ItemId)));

        SubmitAttemptResponse submitResp =
                postSubmit(userId, attemptId, sessionId, "ac4-sub-1", new SubmitAttemptRequest(List.of()));
        assertThat(submitResp.state()).isEqualTo(AttemptState.GRADING);
        assertThat(submitResp.endReason()).isEqualTo(EndReason.SUBMITTED);
        assertThat(submitResp.finishedAt()).isNotNull();
        assertThat(submitResp.parts()).hasSize(3);
        submitResp.parts().forEach(p -> {
            assertThat(p.state()).isEqualTo(PartState.CLOSED);
            assertThat(p.closeReason()).isEqualTo(PartCloseReason.ATTEMPT_SUBMITTED);
            assertThat(p.closedAt()).isEqualTo(submitResp.finishedAt());
        });

        Attempt persistedAttempt = attemptRepository.findById(attemptId).orElseThrow();
        List<AttemptPart> persistedParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        assertThat(persistedAttempt.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(persistedAttempt.getEndReason()).isEqualTo(EndReason.SUBMITTED);
        assertThat(persistedParts).hasSize(3);
        assertThat(persistedParts).extracting(AttemptPart::getState).containsOnly(PartState.CLOSED);
        assertThat(persistedParts)
                .extracting(AttemptPart::getCloseReason)
                .containsOnly(PartCloseReason.ATTEMPT_SUBMITTED);
        assertThat(usageState(attemptId)).isEqualTo("USED");
        assertThat(countOutboxEvents(attemptId, "attempt.finished")).isEqualTo(1);
        verify(entitlementPort, times(1)).confirmUse(attemptId);

        SubmitAttemptResponse replayResp =
                postSubmit(userId, attemptId, sessionId, "ac4-sub-1", new SubmitAttemptRequest(List.of()));
        assertThat(replayResp.state()).isEqualTo(AttemptState.GRADING);
        assertThat(replayResp.finishedAt()).isEqualTo(submitResp.finishedAt());
        assertThat(countOutboxEvents(attemptId, "attempt.finished")).isEqualTo(1);
        verify(entitlementPort, times(1)).confirmUse(attemptId);
    }

    @Test
    void deadlineClosesAllParts() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();

        // 1) Worker sweep at overdue time
        UUID uWorker = createStudent("ac5-deadline-wrk");
        GrantOrderResult.Line gWorker = grant(uWorker, exam.examId(), exam.versionNo(), 2, List.of());
        UUID aWorker = startAndAwaitInProgress(uWorker, gWorker.entitlementId(), "ac5-wrk-start", null);
        Instant workerDeadline = attemptRepository.findById(aWorker).orElseThrow().getDeadlineAt();

        mutableClock.setAt(workerDeadline.plusSeconds(1));
        assertThat(deadlineWorker.sweepOverdue()).isEqualTo(1);

        Attempt closedByWorker = attemptRepository.findById(aWorker).orElseThrow();
        List<AttemptPart> partsByWorker = attemptPartRepository.findByAttemptIdOrderByOrdAsc(aWorker);
        assertThat(closedByWorker.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(closedByWorker.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(closedByWorker.getFinishedAt()).isEqualTo(workerDeadline);
        assertThat(partsByWorker).hasSize(3);
        assertThat(partsByWorker).extracting(AttemptPart::getState).containsOnly(PartState.CLOSED);
        assertThat(partsByWorker).extracting(AttemptPart::getCloseReason).containsOnly(PartCloseReason.TIME_UP);
        assertThat(partsByWorker).extracting(AttemptPart::getClosedAt).containsOnly(workerDeadline);
        assertThat(countOutboxEvents(aWorker, "attempt.finished")).isEqualTo(1);
        verify(entitlementPort, times(1)).confirmUse(aWorker);

        // 2) Lazy close on GET /state without running worker
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID uLazy = createStudent("ac5-deadline-lazy");
        GrantOrderResult.Line gLazy = grant(uLazy, exam.examId(), exam.versionNo(), 2, List.of());
        UUID aLazy = startAndAwaitInProgress(uLazy, gLazy.entitlementId(), "ac5-lazy-start", null);
        Instant lazyDeadline = attemptRepository.findById(aLazy).orElseThrow().getDeadlineAt();

        mutableClock.setAt(lazyDeadline);
        JsonNode stateAfter = getState(uLazy, aLazy);
        assertThat(stateAfter.path("state").asText()).isEqualTo("GRADING");
        for (JsonNode p : stateAfter.path("parts")) {
            assertThat(p.path("state").asText()).isEqualTo("CLOSED");
            assertThat(Instant.parse(p.path("closedAt").asText())).isEqualTo(lazyDeadline);
        }
        Attempt lazyDb = attemptRepository.findById(aLazy).orElseThrow();
        assertThat(lazyDb.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(lazyDb.getFinishedAt()).isEqualTo(lazyDeadline);
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(aLazy))
                .extracting(AttemptPart::getCloseReason)
                .containsOnly(PartCloseReason.TIME_UP);
        assertThat(countOutboxEvents(aLazy, "attempt.finished")).isEqualTo(1);
        verify(entitlementPort, times(1)).confirmUse(aLazy);
    }

    @Test
    void saveAfterDeadlineRejected() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();

        // Attempt 1: 1ms before deadline -> SAVED; exact deadlineAt -> REJECTED(TIME_UP)
        UUID u1 = createStudent("ac5-boundary-1");
        GrantOrderResult.Line g1 = grant(u1, exam.examId(), exam.versionNo(), 2, List.of());
        UUID a1 = startAndAwaitInProgress(u1, g1.entitlementId(), "ac5-b1-start", null);
        JsonNode s1 = getState(u1, a1);
        Instant d1 = Instant.parse(s1.path("deadlineAt").asText());
        JsonNode p0ItemNode1 = s1.path("parts").get(0).path("items").get(0);
        JsonNode p2ItemNode1 = s1.path("parts").get(2).path("items").get(0);
        UUID p0Id1 = uuid(s1.path("parts").get(0), "partId");
        UUID p2Id1 = uuid(s1.path("parts").get(2), "partId");
        UUID p0Item1 = uuid(p0ItemNode1, "itemId");
        UUID p2Item1 = uuid(p2ItemNode1, "itemId");
        UUID sid1 = claimSession(u1, a1, null, false);

        mutableClock.setAt(d1.minusMillis(1));
        SaveAnswersResponse onTime =
                putAnswers(
                        u1,
                        a1,
                        sid1,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(p0Item1, 1L, answerForItem(p0ItemNode1)),
                                        new AnswerChange(p2Item1, 1L, answerForItem(p2ItemNode1))),
                                List.of(),
                                new AttemptPosition(p2Id1, p2Item1)));
        assertThat(onTime.results()).hasSize(2);
        assertThat(onTime.results()).extracting(r -> r.status()).containsOnly(FinalAnswerStatus.SAVED);

        mutableClock.setAt(d1);
        SaveAnswersResponse atExactDeadline =
                putAnswers(
                        u1,
                        a1,
                        sid1,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p0Item1, 2L, answerForItem(p0ItemNode1))),
                                List.of(),
                                new AttemptPosition(p0Id1, p0Item1)));
        assertThat(atExactDeadline.results()).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(FinalAnswerStatus.REJECTED);
            assertThat(r.reason()).isEqualTo("TIME_UP");
        });
        Attempt a1Db = attemptRepository.findById(a1).orElseThrow();
        assertThat(a1Db.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a1Db.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(a1Db.getFinishedAt()).isEqualTo(d1);
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1))
                .extracting(AttemptPart::getClosedAt)
                .containsOnly(d1);
        assertThat(attemptItemRepository.findById(p0Item1).orElseThrow().getAnswerSeq()).isEqualTo(1L);

        // Attempt 2: 1ms after deadline -> REJECTED(TIME_UP), zero writes, closedAt = deadlineAt
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u2 = createStudent("ac5-boundary-2");
        GrantOrderResult.Line g2 = grant(u2, exam.examId(), exam.versionNo(), 2, List.of());
        UUID a2 = startAndAwaitInProgress(u2, g2.entitlementId(), "ac5-b2-start", null);
        JsonNode s2 = getState(u2, a2);
        Instant d2 = Instant.parse(s2.path("deadlineAt").asText());
        UUID p1Id2 = uuid(s2.path("parts").get(1), "partId");
        JsonNode p1ItemNode2 = s2.path("parts").get(1).path("items").get(0);
        UUID p1Item2 = uuid(p1ItemNode2, "itemId");
        UUID sid2 = claimSession(u2, a2, null, false);

        mutableClock.setAt(d2.plusMillis(1));
        SaveAnswersResponse afterDeadline =
                putAnswers(
                        u2,
                        a2,
                        sid2,
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p1Item2, 1L, answerForItem(p1ItemNode2))),
                                List.of(),
                                new AttemptPosition(p1Id2, p1Item2)));
        assertThat(afterDeadline.results()).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(FinalAnswerStatus.REJECTED);
            assertThat(r.reason()).isEqualTo("TIME_UP");
        });
        Attempt a2Db = attemptRepository.findById(a2).orElseThrow();
        assertThat(a2Db.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(a2Db.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(a2Db.getFinishedAt()).isEqualTo(d2);
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2))
                .extracting(AttemptPart::getClosedAt)
                .containsOnly(d2);
        assertThat(attemptItemRepository.findById(p1Item2).orElseThrow().getAnswer()).isNull();
    }

    @Test
    void submitRacingDeadlineClosesOnce() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();

        // Direction A: submit holds lock in confirmUse before deadline; clock passes deadlineAt;
        // worker sweeps overdue -> returns 0; submit finishes as SUBMITTED.
        UUID userA = createStudent("ac6-race-a");
        GrantOrderResult.Line grantA = grant(userA, exam.examId(), exam.versionNo(), 2, List.of());
        UUID attemptA = startAndAwaitInProgress(userA, grantA.entitlementId(), "race-a-start", null);
        Attempt startedA = attemptRepository.findById(attemptA).orElseThrow();
        UUID sessionA = claimSession(userA, attemptA, null, false);

        mutableClock.setAt(startedA.getDeadlineAt().minusSeconds(1));
        CountDownLatch enteredA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        AtomicBoolean heldA = new AtomicBoolean(false);
        doAnswer(
                        inv -> {
                            if (heldA.compareAndSet(false, true)) {
                                enteredA.countDown();
                                assertThat(releaseA.await(10, TimeUnit.SECONDS)).isTrue();
                            }
                            return inv.callRealMethod();
                        })
                .when(entitlementPort)
                .confirmUse(attemptA);

        try {
            CompletableFuture<SubmitAttemptResponse> submitFutureA =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return postSubmit(
                                            userA,
                                            attemptA,
                                            sessionA,
                                            "race-a-sub",
                                            new SubmitAttemptRequest(List.of()));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

            assertThat(enteredA.await(10, TimeUnit.SECONDS)).isTrue();
            mutableClock.setAt(startedA.getDeadlineAt().plusSeconds(1));
            int workerDuringSubmit = deadlineWorker.sweepOverdue();
            assertThat(workerDuringSubmit).isZero();
            releaseA.countDown();

            SubmitAttemptResponse subRespA = submitFutureA.get(15, TimeUnit.SECONDS);
            assertThat(subRespA.state()).isEqualTo(AttemptState.GRADING);
            assertThat(subRespA.endReason()).isEqualTo(EndReason.SUBMITTED);
            verify(entitlementPort, times(1)).confirmUse(attemptA);
            assertThat(countOutboxEvents(attemptA, "attempt.finished")).isEqualTo(1);
            assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptA))
                    .extracting(AttemptPart::getCloseReason)
                    .containsOnly(PartCloseReason.ATTEMPT_SUBMITTED);
        } finally {
            enteredA.countDown();
            releaseA.countDown();
            clearInvocations(entitlementPort);
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        }

        // Direction B: worker holds lock in confirmUse after deadlineAt; concurrent submit blocks then reads TIME_UP.
        UUID userB = createStudent("ac6-race-b");
        GrantOrderResult.Line grantB = grant(userB, exam.examId(), exam.versionNo(), 2, List.of());
        UUID attemptB = startAndAwaitInProgress(userB, grantB.entitlementId(), "race-b-start", null);
        JsonNode stateB = getState(userB, attemptB);
        Instant deadlineB = Instant.parse(stateB.path("deadlineAt").asText());
        JsonNode itemNodeB = stateB.path("parts").get(0).path("items").get(0);
        UUID itemB = uuid(itemNodeB, "itemId");
        UUID sessionB = claimSession(userB, attemptB, null, false);

        mutableClock.setAt(deadlineB.plusSeconds(1));
        CountDownLatch enteredB = new CountDownLatch(1);
        CountDownLatch releaseB = new CountDownLatch(1);
        AtomicBoolean heldB = new AtomicBoolean(false);
        doAnswer(
                        inv -> {
                            if (heldB.compareAndSet(false, true)) {
                                enteredB.countDown();
                                assertThat(releaseB.await(10, TimeUnit.SECONDS)).isTrue();
                            }
                            return inv.callRealMethod();
                        })
                .when(entitlementPort)
                .confirmUse(attemptB);

        try {
            CompletableFuture<Integer> workerFutureB =
                    CompletableFuture.supplyAsync(() -> deadlineWorker.sweepOverdue());

            assertThat(enteredB.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<SubmitAttemptResponse> submitFutureB =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return postSubmit(
                                            userB,
                                            attemptB,
                                            sessionB,
                                            "race-b-sub",
                                            new SubmitAttemptRequest(
                                                    List.of(
                                                            new FinalAnswerChange(
                                                                    itemB, 1L, answerForItem(itemNodeB)))));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

            awaitAttemptRowLockWait();
            releaseB.countDown();

            assertThat(workerFutureB.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            SubmitAttemptResponse subRespB = submitFutureB.get(15, TimeUnit.SECONDS);

            assertThat(subRespB.state()).isEqualTo(AttemptState.GRADING);
            assertThat(subRespB.endReason()).isEqualTo(EndReason.TIME_UP);
            assertThat(subRespB.results()).isEmpty();
            assertThat(attemptItemRepository.findById(itemB).orElseThrow().getAnswer()).isNull();
            verify(entitlementPort, times(1)).confirmUse(attemptB);
            assertThat(countOutboxEvents(attemptB, "attempt.finished")).isEqualTo(1);
            assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptB))
                    .extracting(AttemptPart::getCloseReason)
                    .containsOnly(PartCloseReason.TIME_UP);
        } finally {
            enteredB.countDown();
            releaseB.countDown();
        }
    }

    @Test
    void otherKindsStaySequential() throws Exception {
        verifySequentialKind("DS-HSA-FULL-MINI", "HSA", 3, null);
        verifySequentialKind("DS-QDA-FULL-MINI", "QDA", 3, null);
        verifySequentialKind("DS-BCA-FULL-MINI", "BCA", 3, new PreChoiceInput("CA2", null));
        verifySequentialKind("DS-VACT-FULL", "VACT", 3, null);
        verifySequentialKind("DS-SP2E-MATH-2025-MINI", "SP2E", 3, null);
        verifySequentialKind("DS-SPT-INFORMATICS-MINI", "SPT", 2, new PreChoiceInput("CS", null));
        verifySequentialKind("DS-HSCA-TOAN", "HSCA", 3, null);
        verifySequentialKind("DS-VSAT-TOAN", "VSAT", 4, null);

        // Contrast with THPTQG mini exam opening as FREE with all parts immediately OPEN.
        UUID infMiniId = AttemptTestFixtures.dsExamId(jdbc, "DS-THPTQG-INFORMATICS-MINI");
        int infMiniVer = publishedExamPort.findServing(infMiniId).orElseThrow().version().versionNo();
        UUID uThptqg = createStudent("ac7-thptqg-contrast");
        GrantOrderResult.Line gThptqg = grant(uThptqg, infMiniId, infMiniVer, 2, List.of());
        assertThat(getPrepare(uThptqg, gThptqg.entitlementId()).navigationMode())
                .isEqualTo(NavigationMode.FREE);
        UUID aThptqg =
                startAndAwaitInProgress(
                        uThptqg, gThptqg.entitlementId(), "ac7-thptqg-start", new PreChoiceInput("CS", null));
        assertThat(attemptRepository.findById(aThptqg).orElseThrow().getNavigationMode())
                .isEqualTo(NavigationMode.FREE);
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(aThptqg))
                .extracting(AttemptPart::getState)
                .containsOnly(PartState.OPEN);
    }

    @Test
    void informaticsBranchIsFree() throws Exception {
        verifyInformaticsBranch("CS");
        verifyInformaticsBranch("ICT");
    }

    @Test
    void submitFlushesFinalAnswersAcrossParts() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID userId = createStudent("ac9-final-flush");
        GrantOrderResult.Line grant = grant(userId, exam.examId(), exam.versionNo(), 2, List.of());

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "ac9-flush-start", null);
        JsonNode state = getState(userId, attemptId);
        UUID sessionId = claimSession(userId, attemptId, null, false);

        JsonNode itemANode = state.path("parts").get(0).path("items").get(0);
        JsonNode itemBNode = state.path("parts").get(0).path("items").get(1);
        JsonNode itemCNode = state.path("parts").get(2).path("items").get(0);
        JsonNode itemDNode = state.path("parts").get(2).path("items").get(1);
        UUID itemA = uuid(itemANode, "itemId");
        UUID itemB = uuid(itemBNode, "itemId");
        UUID itemC = uuid(itemCNode, "itemId");
        UUID itemD = uuid(itemDNode, "itemId");

        AnswerValue savedA = answerForItem(itemANode);
        putAnswers(
                userId,
                attemptId,
                sessionId,
                new SaveAnswersRequest(
                        List.of(new AnswerChange(itemA, 5L, savedA)),
                        List.of(),
                        new AttemptPosition(uuid(state.path("parts").get(0), "partId"), itemA)));

        AnswerValue altA = new AnswerValue.SingleChoice(itemANode.path("optionOrder").get(1).asText());
        AnswerValue finalB = answerForItem(itemBNode);
        AnswerValue finalC = answerForItem(itemCNode);
        AnswerValue invalidD = new AnswerValue.SingleChoice("not-a-short-answer");

        SubmitAttemptResponse submitResp =
                postSubmit(
                        userId,
                        attemptId,
                        sessionId,
                        "ac9-flush-sub",
                        new SubmitAttemptRequest(
                                List.of(
                                        new FinalAnswerChange(itemA, 3L, altA),
                                        new FinalAnswerChange(itemB, 1L, finalB),
                                        new FinalAnswerChange(itemC, 2L, finalC),
                                        new FinalAnswerChange(itemD, 1L, invalidD))));

        assertThat(submitResp.state()).isEqualTo(AttemptState.GRADING);
        assertThat(submitResp.endReason()).isEqualTo(EndReason.SUBMITTED);
        assertThat(submitResp.results()).hasSize(4);
        assertThat(submitResp.results().get(0).status()).isEqualTo(FinalAnswerStatus.STALE);
        assertThat(submitResp.results().get(1).status()).isEqualTo(FinalAnswerStatus.SAVED);
        assertThat(submitResp.results().get(2).status()).isEqualTo(FinalAnswerStatus.SAVED);
        assertThat(submitResp.results().get(3).status()).isEqualTo(FinalAnswerStatus.REJECTED);
        assertThat(submitResp.results().get(3).reason()).isEqualTo("INVALID_ANSWER");
        assertThat(submitResp.parts())
                .extracting(PartSummaryView::closeReason)
                .containsOnly(PartCloseReason.ATTEMPT_SUBMITTED);

        AttemptItem dbA = attemptItemRepository.findById(itemA).orElseThrow();
        AttemptItem dbB = attemptItemRepository.findById(itemB).orElseThrow();
        AttemptItem dbC = attemptItemRepository.findById(itemC).orElseThrow();
        AttemptItem dbD = attemptItemRepository.findById(itemD).orElseThrow();
        assertThat(dbA.getAnswer()).isEqualTo(savedA);
        assertThat(dbA.getAnswerSeq()).isEqualTo(5L);
        assertThat(dbB.getAnswer()).isEqualTo(finalB);
        assertThat(dbB.getAnswerSeq()).isEqualTo(1L);
        assertThat(dbC.getAnswer()).isEqualTo(finalC);
        assertThat(dbC.getAnswerSeq()).isEqualTo(2L);
        assertThat(dbD.getAnswer()).isNull();
        assertThat(usageState(attemptId)).isEqualTo("USED");
        assertThat(countOutboxEvents(attemptId, "attempt.finished")).isEqualTo(1);
        verify(entitlementPort, times(1)).confirmUse(attemptId);
    }

    @Test
    void lateOrRevokedSubmitWritesNothing() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();

        // Case 1: Revoked session submit writes nothing and leaves entitlement RESERVED.
        UUID u1 = createStudent("ac9-revoked");
        GrantOrderResult.Line g1 = grant(u1, exam.examId(), exam.versionNo(), 2, List.of());
        UUID a1 = startAndAwaitInProgress(u1, g1.entitlementId(), "ac9-rev-start", null);
        JsonNode s1State = getState(u1, a1);
        JsonNode s1P0Node = s1State.path("parts").get(0).path("items").get(0);
        JsonNode s1P2Node = s1State.path("parts").get(2).path("items").get(0);
        UUID s1P0 = uuid(s1P0Node, "itemId");
        UUID s1P2 = uuid(s1P2Node, "itemId");

        UUID sid1 = claimSession(u1, a1, null, false);
        UUID sid2 = claimSession(u1, a1, null, true);

        MvcResult revokedRes =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/submit", a1)
                                        .with(AttemptTestFixtures.asExamUser(u1, "learner"))
                                        .header("Idempotency-Key", "ac9-rev-sub")
                                        .header("X-Attempt-Session", sid1.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new SubmitAttemptRequest(
                                                                List.of(
                                                                        new FinalAnswerChange(
                                                                                s1P0, 1L, answerForItem(s1P0Node)),
                                                                        new FinalAnswerChange(
                                                                                s1P2, 1L, answerForItem(s1P2Node)))))))
                        .andReturn();
        assertThat(revokedRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(revokedRes.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptRepository.findById(a1).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(attemptItemRepository.findById(s1P0).orElseThrow().getAnswer()).isNull();
        assertThat(attemptItemRepository.findById(s1P2).orElseThrow().getAnswer()).isNull();
        assertThat(usageState(a1)).isEqualTo("RESERVED");

        SubmitAttemptResponse validSub =
                postSubmit(u1, a1, sid2, "ac9-active-sub", new SubmitAttemptRequest(List.of()));
        assertThat(validSub.endReason()).isEqualTo(EndReason.SUBMITTED);
        verify(entitlementPort, times(1)).confirmUse(a1);

        // Case 2: Late submit past deadline writes nothing, closes all parts TIME_UP at deadlineAt, results=[].
        UUID u2 = createStudent("ac9-late");
        GrantOrderResult.Line g2 = grant(u2, exam.examId(), exam.versionNo(), 2, List.of());
        UUID a2 = startAndAwaitInProgress(u2, g2.entitlementId(), "ac9-late-start", null);
        JsonNode s2State = getState(u2, a2);
        JsonNode s2P0Node = s2State.path("parts").get(0).path("items").get(0);
        JsonNode s2P2Node = s2State.path("parts").get(2).path("items").get(0);
        UUID s2P0 = uuid(s2P0Node, "itemId");
        UUID s2P2 = uuid(s2P2Node, "itemId");
        UUID activeSid2 = claimSession(u2, a2, null, false);

        Instant deadline2 = Instant.parse(s2State.path("deadlineAt").asText());
        mutableClock.setAt(deadline2.plusMillis(1));
        SubmitAttemptResponse lateSubmit =
                postSubmit(
                        u2,
                        a2,
                        activeSid2,
                        "ac9-late-sub",
                        new SubmitAttemptRequest(
                                List.of(
                                        new FinalAnswerChange(s2P0, 1L, answerForItem(s2P0Node)),
                                        new FinalAnswerChange(s2P2, 1L, answerForItem(s2P2Node)))));
        assertThat(lateSubmit.state()).isEqualTo(AttemptState.GRADING);
        assertThat(lateSubmit.endReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(lateSubmit.finishedAt()).isEqualTo(deadline2);
        assertThat(lateSubmit.results()).isEmpty();
        assertThat(attemptItemRepository.findById(s2P0).orElseThrow().getAnswer()).isNull();
        assertThat(attemptItemRepository.findById(s2P2).orElseThrow().getAnswer()).isNull();
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2))
                .extracting(AttemptPart::getCloseReason)
                .containsOnly(PartCloseReason.TIME_UP);
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2))
                .extracting(AttemptPart::getClosedAt)
                .containsOnly(deadline2);
        verify(entitlementPort, times(1)).confirmUse(a2);
    }

    @Test
    void audioCheckRunsBeforeOpeningFreeParts() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        audioAssetFixture = new AudioAssetFixture(jdbc, objectStorage);

        // 1) Missing audio file in storage: attempt aborts BEFORE opening any free parts & releases entitlement.
        var missingAsset = audioAssetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID missingAssetId = missingAsset.assetId();
        createdAudioAssetIds.add(missingAssetId);
        UUID missingAudioQId = createReadyShortAnswerAudioQuestion(missingAssetId);
        audioAssetFixture.deleteObjectOnly(missingAssetId);

        stubPart2AudioQuestion(exam.examId(), exam.versionNo(), missingAudioQId);

        UUID uFail = createStudent("ac10-audio-miss");
        GrantOrderResult.Line gFail = grant(uFail, exam.examId(), exam.versionNo(), 1, List.of());
        MvcResult startFailRes =
                mockMvc.perform(
                                post("/api/v1/me/attempts")
                                        .with(AttemptTestFixtures.asExamUser(uFail, "learner"))
                                        .header("Idempotency-Key", "audio-miss-start")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new StartAttemptRequest(gFail.entitlementId(), null))))
                        .andExpect(status().isAccepted())
                        .andReturn();
        UUID aFailId =
                UUID.fromString(
                        objectMapper.readTree(startFailRes.getResponse().getContentAsString()).path("attemptId").asText());
        Attempt aborted = awaitTerminalStartState(aFailId);

        assertThat(aborted.getState()).isEqualTo(AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.AUDIO_ASSET_MISSING);
        assertThat(aborted.getStartedAt()).isNull();
        assertThat(aborted.getDeadlineAt()).isNull();
        assertThat(aborted.getAttemptNo()).isNull();
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(aFailId)).isEmpty();
        assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(aFailId)).isEmpty();
        assertThat(attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(aFailId)).isEmpty();
        assertThat(usageState(aFailId)).isEqualTo("RELEASED");
        assertThat(entitlementPort.describe(uFail, gFail.entitlementId()).attemptsLeft()).isEqualTo(1);
        JsonNode abortedState = getState(uFail, aFailId);
        assertThat(abortedState.path("retryable").asBoolean()).isTrue();

        // 2) Valid audio file: opens ALL 3 FREE parts with shared deadline and IDLE play row linked to Part 2.
        Mockito.reset(paperSolverPort);
        var validAsset = audioAssetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID validAssetId = validAsset.assetId();
        createdAudioAssetIds.add(validAssetId);
        UUID validAudioQId = createReadyShortAnswerAudioQuestion(validAssetId);
        stubPart2AudioQuestion(exam.examId(), exam.versionNo(), validAudioQId);

        UUID uOk = createStudent("ac10-audio-ok");
        GrantOrderResult.Line gOk = grant(uOk, exam.examId(), exam.versionNo(), 1, List.of());
        UUID aOkId = startAndAwaitInProgress(uOk, gOk.entitlementId(), "audio-ok-start", null);
        Attempt startedOk = attemptRepository.findById(aOkId).orElseThrow();
        List<AttemptPart> okParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(aOkId);
        List<AttemptAudioPlay> plays = attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(aOkId);

        assertThat(startedOk.getNavigationMode()).isEqualTo(NavigationMode.FREE);
        assertThat(okParts).hasSize(3);
        assertThat(okParts).extracting(AttemptPart::getState).containsOnly(PartState.OPEN);
        assertThat(okParts).extracting(AttemptPart::getDeadlineAt).containsOnly(startedOk.getDeadlineAt());
        assertThat(plays).singleElement().satisfies(play -> {
            assertThat(play.getAssetId()).isEqualTo(validAssetId);
            assertThat(play.getPartId()).isEqualTo(okParts.get(2).getId());
            assertThat(play.getState()).isEqualTo(AudioPlayState.IDLE);
            assertThat(play.getDurationMs()).isEqualTo(30_000L);
        });
    }

    @Test
    void twoDevicesSavingDifferentParts() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID userId = createStudent("risk-2devices");
        GrantOrderResult.Line grant = grant(userId, exam.examId(), exam.versionNo(), 2, List.of());

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "risk-2dev-start", null);
        JsonNode state = getState(userId, attemptId);
        JsonNode p0 = state.path("parts").get(0);
        JsonNode p2 = state.path("parts").get(2);
        JsonNode p0ItemNodeA = p0.path("items").get(0);
        JsonNode p0ItemNodeB = p0.path("items").get(1);
        JsonNode p2ItemNodeA = p2.path("items").get(0);
        JsonNode p2ItemNodeB = p2.path("items").get(1);
        UUID p0PartId = uuid(p0, "partId");
        UUID p2PartId = uuid(p2, "partId");
        UUID p0ItemA = uuid(p0ItemNodeA, "itemId");
        UUID p0ItemB = uuid(p0ItemNodeB, "itemId");
        UUID p2ItemA = uuid(p2ItemNodeA, "itemId");
        UUID p2ItemB = uuid(p2ItemNodeB, "itemId");

        AnswerValue p0AnsA = answerForItem(p0ItemNodeA);
        AnswerValue p2AnsA = answerForItem(p2ItemNodeA);
        AnswerValue p0AnsB = answerForItem(p0ItemNodeB);
        AnswerValue p2AnsB = new AnswerValue.ShortAnswer("7");

        UUID session1 = claimSession(userId, attemptId, null, false);
        UUID session2 = claimSession(userId, attemptId, null, true);

        MvcResult staleWrite =
                mockMvc.perform(
                                put("/api/v1/me/attempts/{attemptId}/answers", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .header("X-Attempt-Session", session1.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new SaveAnswersRequest(
                                                                List.of(new AnswerChange(p0ItemA, 1L, p0AnsA)),
                                                                List.of(),
                                                                new AttemptPosition(p0PartId, p0ItemA)))))
                        .andReturn();
        assertThat(staleWrite.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(staleWrite.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("ATTEMPT_SESSION_REVOKED");
        assertThat(attemptItemRepository.findById(p0ItemA).orElseThrow().getAnswer()).isNull();

        putAnswers(
                userId,
                attemptId,
                session2,
                new SaveAnswersRequest(
                        List.of(new AnswerChange(p2ItemA, 1L, p2AnsA)),
                        List.of(),
                        new AttemptPosition(p2PartId, p2ItemA)));
        putAnswers(
                userId,
                attemptId,
                session2,
                new SaveAnswersRequest(
                        List.of(new AnswerChange(p0ItemA, 1L, p0AnsA)),
                        List.of(),
                        new AttemptPosition(p0PartId, p0ItemA)));

        CompletableFuture<SaveAnswersResponse> concurrentP0 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return putAnswers(
                                        userId,
                                        attemptId,
                                        session2,
                                        new SaveAnswersRequest(
                                                List.of(new AnswerChange(p0ItemB, 1L, p0AnsB)),
                                                List.of(),
                                                new AttemptPosition(p0PartId, p0ItemB)));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
        CompletableFuture<SaveAnswersResponse> concurrentP2 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return putAnswers(
                                        userId,
                                        attemptId,
                                        session2,
                                        new SaveAnswersRequest(
                                                List.of(new AnswerChange(p2ItemB, 1L, p2AnsB)),
                                                List.of(),
                                                new AttemptPosition(p2PartId, p2ItemB)));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

        assertThat(concurrentP0.get(10, TimeUnit.SECONDS).results().getFirst().status())
                .isEqualTo(FinalAnswerStatus.SAVED);
        assertThat(concurrentP2.get(10, TimeUnit.SECONDS).results().getFirst().status())
                .isEqualTo(FinalAnswerStatus.SAVED);

        JsonNode finalState = getState(userId, attemptId);
        assertThat(readAnswer(findItem(finalState, p0ItemA))).isEqualTo(p0AnsA);
        assertThat(readAnswer(findItem(finalState, p2ItemA))).isEqualTo(p2AnsA);
        assertThat(readAnswer(findItem(finalState, p0ItemB))).isEqualTo(p0AnsB);
        assertThat(readAnswer(findItem(finalState, p2ItemB))).isEqualTo(p2AnsB);
    }

    @Test
    void singlePartThptqgFreeNavigation() throws Exception {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-THPTQG-MATH-P1-MINI");
        int versionNo = publishedExamPort.findServing(examId).orElseThrow().version().versionNo();
        UUID userId = createStudent("risk-single-thptqg");
        GrantOrderResult.Line grant = grant(userId, examId, versionNo, 2, List.of());

        assertThat(getPrepare(userId, grant.entitlementId()).navigationMode())
                .isEqualTo(NavigationMode.FREE);

        UUID attemptId = startAndAwaitInProgress(userId, grant.entitlementId(), "risk-1p-start", null);
        Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);

        assertThat(attempt.getExamKindCode()).isEqualTo("THPTQG");
        assertThat(attempt.getNavigationMode()).isEqualTo(NavigationMode.FREE);
        assertThat(parts).singleElement().satisfies(p -> assertThat(p.getState()).isEqualTo(PartState.OPEN));

        UUID sessionId = claimSession(userId, attemptId, null, false);
        MvcResult finishRes =
                postFinishPart(
                        userId, attemptId, parts.getFirst().getId(), sessionId, new FinishPartRequest(List.of()), "vi");
        assertThat(finishRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(finishRes.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("PART_FINISH_NOT_ALLOWED");

        SubmitAttemptResponse subResp =
                postSubmit(userId, attemptId, sessionId, "risk-1p-sub", new SubmitAttemptRequest(List.of()));
        assertThat(subResp.state()).isEqualTo(AttemptState.GRADING);
        assertThat(subResp.parts()).singleElement().satisfies(p -> {
            assertThat(p.state()).isEqualTo(PartState.CLOSED);
            assertThat(p.closeReason()).isEqualTo(PartCloseReason.ATTEMPT_SUBMITTED);
        });

        UUID attempt2Id = startAndAwaitInProgress(userId, grant.entitlementId(), "risk-1p-exp", null);
        Instant deadline2 = attemptRepository.findById(attempt2Id).orElseThrow().getDeadlineAt();
        mutableClock.setAt(deadline2.plusSeconds(1));
        assertThat(deadlineWorker.sweepOverdue()).isEqualTo(1);
        Attempt expired2 = attemptRepository.findById(attempt2Id).orElseThrow();
        assertThat(expired2.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt2Id))
                .singleElement()
                .satisfies(p -> assertThat(p.getCloseReason()).isEqualTo(PartCloseReason.TIME_UP));
    }

    @Test
    void ownershipAndAccessGuards() throws Exception {
        PublishedExam exam = publishThptqgMath2025Exam();
        UUID ownerId = createStudent("risk-owner");
        UUID intruderId = createStudent("risk-intruder");
        UUID managerId = AttemptTestFixtures.createManageAccount(jdbc, "risk-mgr-" + CODE_SEQ.getAndIncrement());
        createdUsers.add(managerId);
        GrantOrderResult.Line grant = grant(ownerId, exam.examId(), exam.versionNo(), 2, List.of());

        UUID attemptId = startAndAwaitInProgress(ownerId, grant.entitlementId(), "risk-own-start", null);
        JsonNode ownerState = getState(ownerId, attemptId);
        JsonNode p0 = ownerState.path("parts").get(0);
        JsonNode p0Item = p0.path("items").get(0);
        UUID p0Id = uuid(p0, "partId");
        UUID p0ItemId = uuid(p0Item, "itemId");
        UUID ownerSession = claimSession(ownerId, attemptId, null, false);

        String savePayload =
                objectMapper.writeValueAsString(
                        new SaveAnswersRequest(
                                List.of(new AnswerChange(p0ItemId, 1L, answerForItem(p0Item))),
                                List.of(),
                                new AttemptPosition(p0Id, p0ItemId)));
        String finishPayload = objectMapper.writeValueAsString(new FinishPartRequest(List.of()));
        String submitPayload = objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of()));

        mockMvc.perform(get("/api/v1/me/attempts/{attemptId}", attemptId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        put("/api/v1/me/attempts/{attemptId}/answers", attemptId)
                                .header("X-Attempt-Session", ownerSession.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(savePayload))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, p0Id)
                                .header("X-Attempt-Session", ownerSession.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(finishPayload))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                                .header("Idempotency-Key", "anon-sub")
                                .header("X-Attempt-Session", ownerSession.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(submitPayload))
                .andExpect(status().isUnauthorized());

        MvcResult mgrGet =
                mockMvc.perform(
                                get("/api/v1/me/attempts/{attemptId}", attemptId)
                                        .with(AttemptTestFixtures.asManageUser(managerId, "mgr")))
                        .andExpect(status().isForbidden())
                        .andReturn();
        assertThat(objectMapper.readTree(mgrGet.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");
        MvcResult mgrFinish =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, p0Id)
                                        .with(AttemptTestFixtures.asManageUser(managerId, "mgr"))
                                        .header("X-Attempt-Session", ownerSession.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(finishPayload))
                        .andExpect(status().isForbidden())
                        .andReturn();
        assertThat(objectMapper.readTree(mgrFinish.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        mockMvc.perform(
                        get("/api/v1/me/attempts/{attemptId}", attemptId)
                                .with(AttemptTestFixtures.asExamUser(intruderId, "intruder")))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        put("/api/v1/me/attempts/{attemptId}/answers", attemptId)
                                .with(AttemptTestFixtures.asExamUser(intruderId, "intruder"))
                                .header("X-Attempt-Session", ownerSession.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(savePayload))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, p0Id)
                                .with(AttemptTestFixtures.asExamUser(intruderId, "intruder"))
                                .header("X-Attempt-Session", ownerSession.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(finishPayload))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                                .with(AttemptTestFixtures.asExamUser(intruderId, "intruder"))
                                .header("Idempotency-Key", "intruder-sub")
                                .header("X-Attempt-Session", ownerSession.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(submitPayload))
                .andExpect(status().isNotFound());

        assertThat(attemptRepository.findById(attemptId).orElseThrow().getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(attemptItemRepository.findById(p0ItemId).orElseThrow().getAnswer()).isNull();
    }

    // ---------------------------------------------------------------- helpers

    private void verifySequentialKind(
            String examCode, String expectedKindCode, int expectedPartCount, PreChoiceInput preChoice)
            throws Exception {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        int versionNo = publishedExamPort.findServing(examId).orElseThrow().version().versionNo();
        UUID userId = createStudent("seq-" + expectedKindCode.toLowerCase());
        GrantOrderResult.Line grant = grant(userId, examId, versionNo, 2, List.of());

        assertThat(getPrepare(userId, grant.entitlementId()).navigationMode())
                .isEqualTo(NavigationMode.SEQUENTIAL);

        UUID attemptId =
                startAndAwaitInProgress(
                        userId, grant.entitlementId(), "seq-" + expectedKindCode + "-start", preChoice);
        Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);

        assertThat(attempt.getExamKindCode()).isEqualTo(expectedKindCode);
        assertThat(attempt.getNavigationMode()).isEqualTo(NavigationMode.SEQUENTIAL);
        assertThat(parts).hasSize(expectedPartCount);
        assertThat(parts.getFirst().getState()).isEqualTo(PartState.OPEN);
        for (int i = 1; i < expectedPartCount; i++) {
            assertThat(parts.get(i).getState()).isEqualTo(PartState.PENDING);
        }

        JsonNode initialState = getState(userId, attemptId);
        assertThat(initialState.path("navigationMode").asText()).isEqualTo("SEQUENTIAL");
        assertThat(initialState.path("parts").get(1).path("items")).isEmpty();

        UUID sessionId = claimSession(userId, attemptId, null, false);
        MvcResult earlySubmit =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .header("Idempotency-Key", "early-" + expectedKindCode)
                                        .header("X-Attempt-Session", sessionId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of()))))
                        .andReturn();
        assertThat(earlySubmit.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(earlySubmit.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("PART_NOT_LAST");

        MvcResult finishP0 =
                postFinishPart(
                        userId,
                        attemptId,
                        parts.getFirst().getId(),
                        sessionId,
                        new FinishPartRequest(List.of()),
                        "vi");
        assertThat(finishP0.getResponse().getStatus()).isEqualTo(200);

        List<AttemptPart> updated = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        assertThat(updated.get(0).getState()).isEqualTo(PartState.CLOSED);
        assertThat(updated.get(0).getCloseReason()).isEqualTo(PartCloseReason.FINISHED);
        assertThat(updated.get(1).getState()).isEqualTo(PartState.OPEN);
    }

    private void verifyInformaticsBranch(String branchCode) throws Exception {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-THPTQG-INFORMATICS");
        PublishedVersion version = publishedExamPort.findServing(examId).orElseThrow().version();
        String otherCode = "CS".equals(branchCode) ? "ICT" : "CS";
        var part2Def = version.parts().get(1);
        Set<String> otherOnlySections =
                part2Def.options().stream()
                        .filter(o -> otherCode.equals(o.code()))
                        .flatMap(o -> o.sections().stream())
                        .map(Section::id)
                        .collect(Collectors.toSet());
        Set<String> chosenSections =
                part2Def.options().stream()
                        .filter(o -> branchCode.equals(o.code()))
                        .flatMap(o -> o.sections().stream())
                        .map(Section::id)
                        .collect(Collectors.toSet());

        UUID userId = createStudent("ac8-inf-" + branchCode.toLowerCase());
        GrantOrderResult.Line grant = grant(userId, examId, version.versionNo(), 2, List.of());
        assertThat(getPrepare(userId, grant.entitlementId()).navigationMode())
                .isEqualTo(NavigationMode.FREE);

        UUID attemptId =
                startAndAwaitInProgress(
                        userId,
                        grant.entitlementId(),
                        "ac8-" + branchCode + "-start",
                        new PreChoiceInput(branchCode, null));
        Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);

        assertThat(attempt.getExamKindCode()).isEqualTo("THPTQG");
        assertThat(attempt.getNavigationMode()).isEqualTo(NavigationMode.FREE);
        assertThat(parts).hasSize(2);
        assertThat(parts).extracting(AttemptPart::getState).containsOnly(PartState.OPEN);
        assertThat(parts).extracting(AttemptPart::getDeadlineAt).containsOnly(attempt.getDeadlineAt());
        assertThat(items.stream().mapToInt(AttemptItem::getUnitCount).sum()).isEqualTo(28);

        JsonNode state = getState(userId, attemptId);
        JsonNode p0 = state.path("parts").get(0);
        JsonNode p1 = state.path("parts").get(1);
        assertThat(p0.path("items")).isNotEmpty();
        assertThat(p1.path("items")).isNotEmpty();
        Set<String> p1SectionIds = new java.util.HashSet<>();
        for (JsonNode item : p1.path("items")) {
            p1SectionIds.add(item.path("sectionId").asText());
        }
        assertThat(p1SectionIds).containsAll(chosenSections);
        assertThat(p1SectionIds).doesNotContainAnyElementsOf(otherOnlySections);

        UUID sessionId = claimSession(userId, attemptId, null, false);
        JsonNode p0Item = p0.path("items").get(0);
        JsonNode p1Item = p1.path("items").get(0);
        UUID p0Id = uuid(p0, "partId");
        UUID p0ItemId = uuid(p0Item, "itemId");
        UUID p1ItemId = uuid(p1Item, "itemId");
        SaveAnswersResponse saveResp =
                putAnswers(
                        userId,
                        attemptId,
                        sessionId,
                        new SaveAnswersRequest(
                                List.of(
                                        new AnswerChange(p1ItemId, 1L, answerForItem(p1Item)),
                                        new AnswerChange(p0ItemId, 1L, answerForItem(p0Item))),
                                List.of(),
                                new AttemptPosition(p0Id, p0ItemId)));
        assertThat(saveResp.results()).allMatch(r -> r.status() == FinalAnswerStatus.SAVED);

        MvcResult finishRes =
                postFinishPart(
                        userId, attemptId, p0Id, sessionId, new FinishPartRequest(List.of()), "vi");
        assertThat(finishRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(finishRes.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("PART_FINISH_NOT_ALLOWED");

        SubmitAttemptResponse submitResp =
                postSubmit(
                        userId,
                        attemptId,
                        sessionId,
                        "ac8-" + branchCode + "-sub",
                        new SubmitAttemptRequest(List.of()));
        assertThat(submitResp.state()).isEqualTo(AttemptState.GRADING);
        assertThat(submitResp.parts())
                .extracting(PartSummaryView::closeReason)
                .containsOnly(PartCloseReason.ATTEMPT_SUBMITTED);
    }

    private void stubPart2AudioQuestion(UUID examId, int versionNo, UUID audioQuestionId) {
        doAnswer(
                        inv -> {
                            GeneratedPaper real = (GeneratedPaper) inv.callRealMethod();
                            List<GeneratedPart> updatedParts = new ArrayList<>(real.parts());
                            GeneratedPart lastPart = updatedParts.get(2);
                            GeneratedSection firstSection = lastPart.sections().getFirst();
                            GeneratedGroup firstGroup = firstSection.groups().getFirst();
                            List<GeneratedQuestion> replacedQs = new ArrayList<>(firstGroup.questions());
                            replacedQs.set(0, new GeneratedQuestion(audioQuestionId, 1));
                            List<GeneratedGroup> updatedGroups = new ArrayList<>(firstSection.groups());
                            updatedGroups.set(
                                    0,
                                    new GeneratedGroup(
                                            firstGroup.groupId(), replacedQs, firstGroup.shuffleUnits()));
                            List<GeneratedSection> updatedSections = new ArrayList<>(lastPart.sections());
                            updatedSections.set(
                                    0,
                                    new GeneratedSection(
                                            firstSection.sectionId(),
                                            firstSection.subjectCode(),
                                            firstSection.questionCount(),
                                            updatedGroups));
                            updatedParts.set(2, new GeneratedPart(lastPart.partCode(), updatedSections));
                            return new GeneratedPaper(updatedParts, real.dropped());
                        })
                .when(paperSolverPort)
                .generate(eq(examId), eq(versionNo), anyList(), anyList(), anyLong(), anySet());
    }

    private UUID createStudent(String label) {
        UUID userId = AttemptTestFixtures.createStudentAccount(jdbc, label + "-" + CODE_SEQ.getAndIncrement());
        createdUsers.add(userId);
        return userId;
    }

    private GrantOrderResult.Line grant(
            UUID userId, UUID examId, Integer versionNo, int attempts, List<String> optionCodes) {
        return AttemptTestFixtures.grantExam(
                grantService, userId, examId, attempts, optionCodes, "SCORE_AND_ANSWERS", versionNo);
    }

    private AttemptPrepareResponse getPrepare(UUID userId, UUID entitlementId) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                get("/api/v1/me/entitlements/{entitlementId}/prepare", entitlementId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                        .andExpect(status().isOk())
                        .andReturn();
        String raw = res.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(raw);
        return objectMapper.readValue(raw, AttemptPrepareResponse.class);
    }

    private UUID startAndAwaitInProgress(
            UUID userId, UUID entitlementId, String idempotencyKey, PreChoiceInput preChoice)
            throws Exception {
        MvcResult startRes =
                mockMvc.perform(
                                post("/api/v1/me/attempts")
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .header("Idempotency-Key", idempotencyKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new StartAttemptRequest(entitlementId, preChoice))))
                        .andExpect(status().isAccepted())
                        .andReturn();
        String raw = startRes.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(raw);
        UUID attemptId = UUID.fromString(objectMapper.readTree(raw).path("attemptId").asText());
        Attempt terminal = awaitTerminalStartState(attemptId);
        assertThat(terminal.getState())
                .withFailMessage(
                        "Attempt %s ended in %s (%s)", attemptId, terminal.getState(), terminal.getAbortReason())
                .isEqualTo(AttemptState.IN_PROGRESS);
        return attemptId;
    }

    private Attempt awaitTerminalStartState(UUID attemptId) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadlineNanos) {
            Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();
            if (attempt.getState() != AttemptState.STARTING) {
                return attempt;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Attempt stayed in STARTING past 15s: " + attemptId);
    }

    private JsonNode getState(UUID userId, UUID attemptId) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                get("/api/v1/me/attempts/{attemptId}", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                        .andExpect(status().isOk())
                        .andReturn();
        String raw = res.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(raw);
        return objectMapper.readTree(raw);
    }

    private UUID claimSession(UUID userId, UUID attemptId, UUID sessionId, boolean takeover)
            throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new ClaimSessionRequest(sessionId, takeover))))
                        .andExpect(status().isOk())
                        .andReturn();
        ClaimSessionResponse parsed =
                objectMapper.readValue(res.getResponse().getContentAsString(), ClaimSessionResponse.class);
        return parsed.sessionId();
    }

    private SaveAnswersResponse putAnswers(
            UUID userId, UUID attemptId, UUID sessionId, SaveAnswersRequest req) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                put("/api/v1/me/attempts/{attemptId}/answers", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .header("X-Attempt-Session", sessionId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk())
                        .andReturn();
        String raw = res.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(raw);
        return objectMapper.readValue(raw, SaveAnswersResponse.class);
    }

    private MvcResult postFinishPart(
            UUID userId,
            UUID attemptId,
            UUID partId,
            UUID sessionId,
            FinishPartRequest req,
            String language)
            throws Exception {
        return mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, partId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("X-Attempt-Session", sessionId.toString())
                                .header(HttpHeaders.ACCEPT_LANGUAGE, language)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
    }

    private SubmitAttemptResponse postSubmit(
            UUID userId, UUID attemptId, UUID sessionId, String idempotencyKey, SubmitAttemptRequest req)
            throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .header("Idempotency-Key", idempotencyKey)
                                        .header("X-Attempt-Session", sessionId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk())
                        .andReturn();
        String raw = res.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(raw);
        return objectMapper.readValue(raw, SubmitAttemptResponse.class);
    }

    private String usageState(UUID attemptId) {
        return jdbc.queryForObject(
                "SELECT state FROM commerce.entitlement_usage WHERE attempt_id = ?",
                String.class,
                attemptId);
    }

    private int countOutboxEvents(UUID attemptId, String topic) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM shared.outbox_event "
                                + "WHERE topic = ? AND payload->>'attemptId' = ?",
                        Integer.class,
                        topic,
                        attemptId.toString());
        return count == null ? 0 : count;
    }

    private AnswerValue readAnswer(JsonNode itemNode) throws Exception {
        JsonNode ans = itemNode.path("answer");
        if (ans.isMissingNode() || ans.isNull()) {
            return null;
        }
        return objectMapper.treeToValue(ans, AnswerValue.class);
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private static JsonNode findItem(JsonNode state, UUID itemId) {
        for (JsonNode part : state.path("parts")) {
            for (JsonNode item : part.path("items")) {
                if (itemId.toString().equals(item.path("itemId").asText())) {
                    return item;
                }
            }
        }
        throw new AssertionError("Item not found in state JSON: " + itemId);
    }

    private static AnswerValue answerForItem(JsonNode item) {
        QuestionType qType = QuestionType.valueOf(item.path("questionType").asText());
        return switch (qType) {
            case SINGLE_CHOICE -> new AnswerValue.SingleChoice(item.path("optionOrder").get(0).asText());
            case TRUE_FALSE -> {
                Map<String, Boolean> map = new LinkedHashMap<>();
                for (JsonNode st : item.path("questionBody").path("statements")) {
                    map.put(st.path("id").asText(), Boolean.TRUE);
                }
                yield new AnswerValue.TrueFalse(map);
            }
            case SHORT_ANSWER -> new AnswerValue.ShortAnswer("1");
            default -> throw new IllegalStateException("Unsupported test item type: " + qType);
        };
    }

    private void awaitAttemptRowLockWait() throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadlineNanos) {
            Integer waiting =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM pg_stat_activity "
                                    + "WHERE pid <> pg_backend_pid() "
                                    + "AND wait_event_type = 'Lock' "
                                    + "AND state = 'active' "
                                    + "AND query ILIKE '%attempt%'",
                            Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for second transaction to block on attempt row lock");
    }

    private UUID createReadyShortAnswerAudioQuestion(UUID assetId) {
        UserDirectory.UserSummary actor = userDirectory.findActiveByUsername("sys.admin").orElseThrow();
        SecurityContext prev = SecurityContextHolder.getContext();
        try {
            setAdminSecurityContext(actor);
            QuestionDetail created =
                    questionService.create(
                            "q-audio-" + UUID.randomUUID(),
                            new SaveQuestionRequest(
                                    QuestionType.SHORT_ANSWER,
                                    "TOAN",
                                    "G12",
                                    "NB",
                                    null,
                                    null,
                                    BigDecimal.ONE,
                                    List.of(),
                                    List.of(),
                                    "<p>AT-8 audio question</p>",
                                    "<p>Sol</p>",
                                    List.of(),
                                    List.of(assetId),
                                    new QuestionBody.ShortAnswerBody(
                                            QuestionType.SHORT_ANSWER, List.of("1"), "", null),
                                    null));
            questionService.markReady(created.summary().id(), created.summary().revision());
            UUID qId = created.summary().id();
            createdQuestionIds.add(qId);
            return qId;
        } finally {
            SecurityContextHolder.setContext(prev);
        }
    }

    private PublishedExam publishThptqgMath2025Exam() {
        UserDirectory.UserSummary actor = userDirectory.findActiveByUsername("sys.admin").orElseThrow();
        SecurityContext prev = SecurityContextHolder.getContext();
        String code = "THPTQG-FN-" + CODE_SEQ.getAndIncrement();
        try {
            setAdminSecurityContext(actor);
            ExamDetailResponse created =
                    examService.createExam(
                            "create-" + code,
                            new CreateExamRequest(
                                    "THPTQG",
                                    "SUBJECT",
                                    null,
                                    "TOAN",
                                    code,
                                    "THPTQG Toan 3 parts Free Nav IT",
                                    90,
                                    null,
                                    "THPTQG Math 2025 IT",
                                    null,
                                    "STANDARD",
                                    null));
            if (!"THPTQG_MATH_2025".equals(created.templateCode())) {
                examService.loadTemplate(
                        created.id(),
                        new LoadExamTemplateRequest(
                                "THPTQG_MATH_2025", Boolean.TRUE, "USE_TEMPLATE", created.revision()));
            }
            Long revForListing =
                    jdbc.queryForObject(
                            "SELECT revision FROM exam.exam WHERE id = ?", Long.class, created.id());
            examListingService.update(
                    created.id(),
                    new UpdateExamListingRequest(
                            "Summary",
                            "Objectives",
                            "Students",
                            50000L,
                            3,
                            true,
                            true,
                            true,
                            null,
                            "SCORE_AND_ANSWERS",
                            "Description",
                            null,
                            revForListing));

            var checkResult =
                    examCheckOrchestrator.check(
                            created.id(),
                            ExamCheckScope.from(null, null, null),
                            new ExamCheckCompletion.Actor(actor.id(), actor.username(), AuditActorType.USER),
                            null);
            List<String> validPathIds = new ArrayList<>();
            checkResult
                    .path("paths")
                    .forEach(
                            p -> {
                                if ("VALID".equals(p.path("result").asText())) {
                                    validPathIds.add(p.path("pathId").asText());
                                }
                            });
            assertThat(validPathIds).isNotEmpty();

            Long revForPublish =
                    jdbc.queryForObject(
                            "SELECT revision FROM exam.exam WHERE id = ?", Long.class, created.id());
            ExamPublishResponse published =
                    examPublishService.publish(
                            created.id(),
                            "pub-" + code,
                            new ExamPublishRequest(validPathIds, "AT-8 free nav", revForPublish));
            createdExamIds.add(created.id());
            return new PublishedExam(created.id(), published.version());
        } finally {
            SecurityContextHolder.setContext(prev);
        }
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
        jdbc.update("DELETE FROM exam.exam WHERE id = ?", examId);
    }

    private static void setAdminSecurityContext(UserDirectory.UserSummary actor) {
        CurrentUser admin =
                new CurrentUser(
                        actor.id(),
                        actor.username(),
                        Realm.MASTER,
                        Access.MANAGE,
                        PermissionSet.all());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new CurrentUserAuthenticationToken(admin, "it-admin-session"));
        SecurityContextHolder.setContext(ctx);
    }

    private record PublishedExam(UUID examId, int versionNo) {}
}
