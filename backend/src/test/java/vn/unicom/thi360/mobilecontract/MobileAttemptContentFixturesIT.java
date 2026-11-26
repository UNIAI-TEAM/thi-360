package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
import vn.unicom.thi360.attempt.api.AttemptAudioController.AudioProgressRequest;
import vn.unicom.thi360.attempt.api.AttemptAudioController.StartAudioPlayRequest;
import vn.unicom.thi360.attempt.api.ClaimSessionRequest;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.audio.AudioAssetFixture;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.PublishedExamSetSupport;

@Import(AttemptTestFixtures.MutableClockConfig.class)
public class MobileAttemptContentFixturesIT extends AbstractIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/attempts-asset-404-not-found.json",
                "api/attempts-asset-401-unauthenticated.json",
                "api/attempts-solution-asset-404-not-found.json",
                "api/attempts-solution-asset-401-unauthenticated.json",
                "api/audio-plays-start-200.json",
                "api/audio-plays-start-409-audio-play-in-progress.json",
                "api/audio-plays-start-409-audio-plays-exhausted.json",
                "api/audio-plays-progress-200.json",
                "api/audio-plays-progress-409-audio-seek-rejected.json",
                "api/audio-plays-progress-409-audio-play-stale.json",
                "api/audio-plays-progress-409-attempt-session-revoked.json",
                "api/audio-plays-resume-200.json",
                "api/audio-plays-resume-409-part-closed.json",
                "api/audio-plays-end-200.json",
                "api/audio-plays-end-200-closed.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

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
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private AudioAssetFixture assetFixture;
    private MobileFixtureCertifier certifier;

    @BeforeEach
    void setUp() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        assetFixture = new AudioAssetFixture(jdbc, objectStorage);
        certifier = new MobileFixtureCertifier(objectMapper);
    }

    @AfterEach
    void tearDown() {
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        reset(paperSolverPort);
        for (UUID userId : createdUserIds) {
            jdbc.update(
                    "DELETE FROM attempt.attempt_audio_play WHERE attempt_id IN "
                            + "(SELECT id FROM attempt.attempt WHERE user_id = ?)",
                    userId);
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
        AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestionIds);
        createdQuestionIds.clear();
        if (assetFixture != null) {
            assetFixture.cleanup();
        }
    }

    @Test
    void allAttemptContentAndAudioContractFixturesMatchLiveResponses() throws Exception {
        UUID userId = createStudent("mc-aud-main");
        var mp3 = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID qId = createReadyListeningQuestion(mp3.assetId(), "MC-AUD");
        stubListeningPaperWithQuestions(List.of(qId));

        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 3);
        Attempt attempt1 = startAndAwaitOpen(userId, "mc-aud-1", line.entitlementId());
        UUID sid1 = claimSession(userId, attempt1.getId());
        UUID unknownAssetId = UUID.randomUUID();

        certifier.certify("api/attempts-asset-404-not-found.json", 404,
                mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", attempt1.getId(), unknownAssetId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/attempts-asset-401-unauthenticated.json", 401,
                mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", attempt1.getId(), mp3.assetId()))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/attempts-solution-asset-404-not-found.json", 404,
                mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}", attempt1.getId(), unknownAssetId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/attempts-solution-asset-401-unauthenticated.json", 401,
                mockMvc.perform(get("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}", attempt1.getId(), unknownAssetId))
                        .andReturn(),
                Set.of("traceId"));

        MvcResult startRes1 = performOpenPlay(userId, attempt1.getId(), mp3.assetId(), sid1, "play-req-1");
        certifier.certify("api/audio-plays-start-200.json", 200, startRes1, Set.of("playId"));
        UUID play1Id = UUID.fromString(json(startRes1).path("playId").asText());

        MvcResult inProgRes = performOpenPlay(userId, attempt1.getId(), mp3.assetId(), sid1, "play-req-conflict");
        assertThat(json(inProgRes).path("playId").asText()).isEqualTo(play1Id.toString());
        certifier.certify("api/audio-plays-start-409-audio-play-in-progress.json", 409, inProgRes, Set.of("playId", "traceId"));

        mutableClock.advance(Duration.ofSeconds(10));
        MvcResult progressRes = performProgress(userId, attempt1.getId(), mp3.assetId(), play1Id, sid1, 10_000L);
        certifier.certify("api/audio-plays-progress-200.json", 200, progressRes, Set.of("playId"));

        MvcResult seekRejectedRes = performProgress(userId, attempt1.getId(), mp3.assetId(), play1Id, sid1, 5_000L);
        assertThat(json(seekRejectedRes).path("positionMs").asLong()).isEqualTo(10_000L);
        certifier.certify("api/audio-plays-progress-409-audio-seek-rejected.json", 409, seekRejectedRes, Set.of("traceId"));

        MvcResult staleRes = performProgress(userId, attempt1.getId(), mp3.assetId(), UUID.randomUUID(), sid1, 10_000L);
        certifier.certify("api/audio-plays-progress-409-audio-play-stale.json", 409, staleRes, Set.of("traceId"));

        UUID newSid1 = takeoverSession(userId, attempt1.getId());
        MvcResult revokedRes = performProgress(userId, attempt1.getId(), mp3.assetId(), play1Id, sid1, 10_000L);
        certifier.certify("api/audio-plays-progress-409-attempt-session-revoked.json", 409, revokedRes, Set.of("traceId"));

        MvcResult resumeRes = performResumePlay(userId, attempt1.getId(), mp3.assetId(), play1Id, newSid1);
        certifier.certify("api/audio-plays-resume-200.json", 200, resumeRes, Set.of("playId"));

        MvcResult end1Res = performEndPlay(userId, attempt1.getId(), mp3.assetId(), play1Id, newSid1);
        certifier.certify("api/audio-plays-end-200.json", 200, end1Res, Set.of("playId"));

        MvcResult startRes2 = performOpenPlay(userId, attempt1.getId(), mp3.assetId(), newSid1, "play-req-2");
        assertThat(startRes2.getResponse().getStatus()).isEqualTo(200);
        UUID play2Id = UUID.fromString(json(startRes2).path("playId").asText());
        assertThat(performEndPlay(userId, attempt1.getId(), mp3.assetId(), play2Id, newSid1).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult exhaustedRes = performOpenPlay(userId, attempt1.getId(), mp3.assetId(), newSid1, "play-req-3");
        certifier.certify("api/audio-plays-start-409-audio-plays-exhausted.json", 409, exhaustedRes, Set.of("traceId"));

        UUID user2 = createStudent("mc-aud-closed");
        GrantOrderResult.Line line2 = grant(user2, "DS-HSCA-ANH-MINI", 2);
        Attempt attempt2 = startAndAwaitOpen(user2, "mc-aud-2", line2.entitlementId());
        UUID sid2 = claimSession(user2, attempt2.getId());
        List<AttemptPart> parts2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt2.getId());

        MvcResult openForClose = performOpenPlay(user2, attempt2.getId(), mp3.assetId(), sid2, "play-close-1");
        assertThat(openForClose.getResponse().getStatus()).isEqualTo(200);
        UUID closedPlayId = UUID.fromString(json(openForClose).path("playId").asText());
        mutableClock.advance(Duration.ofSeconds(5));
        assertThat(performProgress(user2, attempt2.getId(), mp3.assetId(), closedPlayId, sid2, 5_000L)
                        .getResponse().getStatus())
                .isEqualTo(200);

        assertThat(performFinishPart(user2, attempt2.getId(), parts2.getFirst().getId(), sid2)
                        .getResponse().getStatus())
                .isEqualTo(200);

        MvcResult resumeClosed = performResumePlay(user2, attempt2.getId(), mp3.assetId(), closedPlayId, sid2);
        certifier.certify("api/audio-plays-resume-409-part-closed.json", 409, resumeClosed, Set.of("traceId"));

        MvcResult endClosed = performEndPlay(user2, attempt2.getId(), mp3.assetId(), closedPlayId, sid2);
        assertThat(json(endClosed).path("endReason").asText()).isEqualTo("CLOSED");
        certifier.certify("api/audio-plays-end-200-closed.json", 200, endClosed, Set.of("playId"));

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private UUID createStudent(String label) {
        UUID id = AttemptTestFixtures.createStudentAccount(jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    private GrantOrderResult.Line grant(UUID userId, String examCode, int attempts) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        return AttemptTestFixtures.grantExam(
                grantService, userId, examId, attempts, List.of(), "SCORE_AND_ANSWERS", serving.version().versionNo());
    }

    private Attempt startAndAwaitOpen(UUID userId, String keyPrefix, UUID entitlementId) throws Exception {
        MvcResult startRes = mockMvc.perform(
                        post("/api/v1/me/attempts")
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("Idempotency-Key", keyPrefix + "-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new StartAttemptRequest(entitlementId, null))))
                .andReturn();
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(startRes).path("attemptId").asText());
        return awaitState(attemptId, AttemptState.IN_PROGRESS);
    }

    private UUID claimSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res = mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(json(res).path("sessionId").asText());
    }

    private UUID takeoverSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res = mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ClaimSessionRequest(null, true))))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(json(res).path("sessionId").asText());
    }

    private MvcResult performFinishPart(UUID userId, UUID attemptId, UUID partId, UUID sessionId) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, partId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("X-Attempt-Session", sessionId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of()))))
                .andReturn();
    }

    private MvcResult performOpenPlay(UUID userId, UUID attemptId, UUID assetId, UUID sessionId, String requestId)
            throws Exception {
        return mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays", attemptId, assetId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("X-Attempt-Session", sessionId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new StartAudioPlayRequest(requestId))))
                .andReturn();
    }

    private MvcResult performResumePlay(UUID userId, UUID attemptId, UUID assetId, UUID playId, UUID sessionId)
            throws Exception {
        return mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume", attemptId, assetId, playId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("X-Attempt-Session", sessionId.toString()))
                .andReturn();
    }

    private MvcResult performProgress(UUID userId, UUID attemptId, UUID assetId, UUID playId, UUID sessionId, Long positionMs)
            throws Exception {
        return mockMvc.perform(
                        put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress", attemptId, assetId, playId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("X-Attempt-Session", sessionId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AudioProgressRequest(positionMs))))
                .andReturn();
    }

    private MvcResult performEndPlay(UUID userId, UUID attemptId, UUID assetId, UUID playId, UUID sessionId)
            throws Exception {
        return mockMvc.perform(
                        post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end", attemptId, assetId, playId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("X-Attempt-Session", sessionId.toString()))
                .andReturn();
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

        MvcResult qCreateRes = mockMvc.perform(
                        post("/api/v1/admin/questions")
                                .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "create-aud-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                .andReturn();
        assertThat(qCreateRes.getResponse().getStatus()).isBetween(200, 201);
        UUID questionId = UUID.fromString(json(qCreateRes).path("summary").path("id").asText());
        createdQuestionIds.add(questionId);

        MvcResult readyRes = mockMvc.perform(
                        post("/api/v1/admin/questions/bulk/ready")
                                .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "ready-aud-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new BulkQuestionStatusRequest(List.of(questionId)))))
                .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return questionId;
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
