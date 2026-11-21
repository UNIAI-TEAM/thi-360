package vn.unicom.thi360.attempt.audio;

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
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
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
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/** Shared DS + {@link MutableClock} fixture base for AT-6 Slice 2 audio integration tests. */
@Import(AttemptTestFixtures.MutableClockConfig.class)
abstract class AbstractAttemptAudioIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired protected ApplicationContext context;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected MutableClock mutableClock;
    @Autowired protected ObjectStorage objectStorage;
    @Autowired protected EntitlementGrantService grantService;
    @Autowired protected PublishedExamPort publishedExamPort;
    @Autowired protected AttemptRepository attemptRepository;
    @Autowired protected AttemptPartRepository attemptPartRepository;
    @Autowired protected AttemptAudioPlayRepository attemptAudioPlayRepository;

    @MockitoSpyBean protected PaperSolverPort paperSolverPort;
    @MockitoSpyBean protected QuestionAttachmentPort questionAttachmentPort;
    @MockitoSpyBean protected QuestionAssetContentPort questionAssetContentPort;

    protected final List<UUID> createdUserIds = new ArrayList<>();
    protected final List<UUID> createdQuestionIds = new ArrayList<>();
    protected AudioAssetFixture assetFixture;

    @BeforeEach
    void initAudioFixture() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        assetFixture = new AudioAssetFixture(jdbc, objectStorage);
    }

    @AfterEach
    void tearDownAudioFixture() {
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        reset(paperSolverPort, questionAttachmentPort, questionAssetContentPort);
        for (UUID userId : createdUserIds) {
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
        AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestionIds);
        createdQuestionIds.clear();
        if (assetFixture != null) {
            assetFixture.cleanup();
        }
    }

    protected UUID createStudent(String label) {
        UUID id =
                AttemptTestFixtures.createStudentAccount(
                        jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    protected UUID createManager(String label) {
        UUID id =
                AttemptTestFixtures.createManageAccount(
                        jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    protected GrantOrderResult.Line grant(UUID userId, String examCode, int attempts) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        return AttemptTestFixtures.grantExam(
                grantService,
                userId,
                examId,
                attempts,
                List.of(),
                "SCORE_AND_ANSWERS",
                serving.version().versionNo());
    }

    protected Attempt startAndAwaitOpen(UUID userId, String keyPrefix, UUID entitlementId)
            throws Exception {
        StartAttemptRequest payload = new StartAttemptRequest(entitlementId, null);
        MvcResult startRes =
                mockMvc.perform(
                                post("/api/v1/me/attempts")
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .header("Idempotency-Key", keyPrefix + "-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(payload)))
                        .andReturn();
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(startRes).path("attemptId").asText());
        return awaitState(attemptId, AttemptState.IN_PROGRESS);
    }

    protected UUID claimSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                        .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(json(res).path("sessionId").asText());
    }

    protected UUID takeoverSession(UUID userId, UUID attemptId) throws Exception {
        MvcResult res =
                mockMvc.perform(
                                post("/api/v1/me/attempts/{attemptId}/session", attemptId)
                                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new ClaimSessionRequest(null, true))))
                        .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(json(res).path("sessionId").asText());
    }

    protected MvcResult performGetState(UUID userId, UUID attemptId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/me/attempts/{attemptId}", attemptId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    protected MvcResult performFinishPart(
            UUID userId, UUID attemptId, UUID partId, UUID sessionId) throws Exception {
        var req =
                post("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", attemptId, partId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishPartRequest(List.of())));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult performSubmit(
            UUID userId, UUID attemptId, String idempotencyKey, UUID sessionId) throws Exception {
        var req =
                post("/api/v1/me/attempts/{attemptId}/submit", attemptId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(List.of())));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult performOpenPlay(
            UUID userId, UUID attemptId, UUID assetId, UUID sessionId, String requestId)
            throws Exception {
        var req =
                post("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays", attemptId, assetId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        new StartAudioPlayRequest(requestId)));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult performResumePlay(
            UUID userId, UUID attemptId, UUID assetId, UUID playId, UUID sessionId)
            throws Exception {
        var req =
                post(
                                "/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume",
                                attemptId,
                                assetId,
                                playId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult performProgress(
            UUID userId,
            UUID attemptId,
            UUID assetId,
            UUID playId,
            UUID sessionId,
            Long positionMs)
            throws Exception {
        var req =
                put(
                                "/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress",
                                attemptId,
                                assetId,
                                playId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        new AudioProgressRequest(positionMs)));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult performEndPlay(
            UUID userId, UUID attemptId, UUID assetId, UUID playId, UUID sessionId)
            throws Exception {
        var req =
                post(
                                "/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end",
                                attemptId,
                                assetId,
                                playId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"));
        if (sessionId != null) {
            req = req.header("X-Attempt-Session", sessionId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult performGetAudioAsset(
            UUID userId, UUID attemptId, UUID assetId, UUID playId) throws Exception {
        var req =
                get("/api/v1/me/attempts/{attemptId}/assets/{assetId}", attemptId, assetId)
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"));
        if (playId != null) {
            req = req.param("playId", playId.toString());
        }
        return mockMvc.perform(req).andReturn();
    }

    protected void stubListeningPaperWithQuestions(List<UUID> replacementQuestionIds) {
        doAnswer(
                        inv -> {
                            GeneratedPaper real = (GeneratedPaper) inv.callRealMethod();
                            List<GeneratedPart> parts = new ArrayList<>(real.parts().size());
                            GeneratedPart firstPart = real.parts().getFirst();
                            GeneratedSection firstSec = firstPart.sections().getFirst();
                            GeneratedGroup firstGroup = firstSec.groups().getFirst();
                            List<GeneratedQuestion> customQuestions =
                                    replacementQuestionIds.stream()
                                            .map(id -> new GeneratedQuestion(id, 1))
                                            .toList();
                            List<GeneratedGroup> updatedGroups =
                                    List.of(
                                            new GeneratedGroup(
                                                    firstGroup.groupId(),
                                                    customQuestions,
                                                    firstGroup.shuffleUnits()));
                            List<GeneratedSection> updatedSecs =
                                    List.of(
                                            new GeneratedSection(
                                                    firstSec.sectionId(),
                                                    firstSec.subjectCode(),
                                                    customQuestions.size(),
                                                    updatedGroups));
                            parts.add(new GeneratedPart(firstPart.partCode(), updatedSecs));
                            for (int i = 1; i < real.parts().size(); i++) {
                                parts.add(real.parts().get(i));
                            }
                            return new GeneratedPaper(parts, real.dropped());
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());
    }

    protected UUID createReadyListeningQuestion(UUID assetId, String prefix) throws Exception {
        UUID adminId = UUID.randomUUID();
        SaveQuestionRequest createReq =
                new SaveQuestionRequest(
                        QuestionType.SINGLE_CHOICE,
                        "ANH",
                        "G12",
                        "TH",
                        null,
                        null,
                        BigDecimal.ONE,
                        List.of(),
                        List.of(),
                        "<p>Audio test " + prefix + "</p>",
                        null,
                        List.of(),
                        List.of(assetId),
                        new QuestionBody.SingleChoiceBody(
                                QuestionType.SINGLE_CHOICE,
                                List.of(
                                        new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                        new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                                false),
                        prefix + "-" + UUID.randomUUID().toString().substring(0, 6));

        MvcResult qCreateRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions")
                                        .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "create-aud-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(createReq)))
                        .andReturn();
        assertThat(qCreateRes.getResponse().getStatus()).isBetween(200, 201);
        UUID questionId = UUID.fromString(json(qCreateRes).path("summary").path("id").asText());
        createdQuestionIds.add(questionId);

        MvcResult readyRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions/bulk/ready")
                                        .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "ready-aud-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new BulkQuestionStatusRequest(
                                                                List.of(questionId)))))
                        .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return questionId;
    }

    protected Attempt awaitState(UUID attemptId, AttemptState... targets)
            throws InterruptedException {
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
        throw new AssertionError(
                "Timed out waiting for attempt "
                        + attemptId
                        + " to reach "
                        + targetSet
                        + ", actual="
                        + (latest == null ? "MISSING" : latest.getState()));
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
