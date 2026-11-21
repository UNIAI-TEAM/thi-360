package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.attempt.testsupport.E2eAudioFaultController;
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
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Integration tests for pre-persist audio verification, MP3 frame duration measurement, caching,
 * failure aborts, and e2e audio fault injection (BS §17.4, AC-9, AC-14, AC-15, AC-16).
 */
@ExtendWith(OutputCaptureExtension.class)
class AttemptAudioFileCheckIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ObjectStorage objectStorage;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptPartRepository attemptPartRepository;
    @Autowired private AttemptItemRepository attemptItemRepository;
    @Autowired private AttemptEventRepository attemptEventRepository;
    @Autowired private AttemptAudioPlayRepository attemptAudioPlayRepository;

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @MockitoSpyBean private QuestionAttachmentPort questionAttachmentPort;
    @MockitoSpyBean private QuestionAssetContentPort questionAssetContentPort;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private AudioAssetFixture assetFixture;

    @BeforeEach
    void setUp() {
        AttemptTestFixtures.ensureDsBuilt(context);
        assetFixture = new AudioAssetFixture(jdbc, objectStorage);
    }

    @AfterEach
    void tearDown() {
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

    @Test
    void serverReadsMp3Duration() throws Exception {
        UUID userId = createStudent("audio-cbr-vbr");
        var cbr = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        var vbr = assetFixture.createFromAudioResource("silence-30s-vbr-xing.mp3");
        UUID qCbr = createReadyListeningQuestion(cbr.assetId(), "CBR-30S");
        UUID qVbr = createReadyListeningQuestion(vbr.assetId(), "VBR-30S");

        stubListeningPaperWithQuestions(List.of(qCbr, qVbr));

        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);
        MvcResult res = performStart(userId, "mp3-dur-" + UUID.randomUUID(), line.entitlementId());
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        Attempt opened = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
        assertThat(opened.getStartedAt()).isNotNull();

        List<AttemptAudioPlay> plays = attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attemptId);
        assertThat(plays).hasSize(2);
        assertThat(plays).allSatisfy(play -> {
            assertThat(play.getState()).isEqualTo(AudioPlayState.IDLE);
            assertThat(play.getPlayCount()).isZero();
            assertThat(play.getDurationMs()).isEqualTo(30_000L);
        });
        assertThat(plays).extracting(AttemptAudioPlay::getAssetId)
                .containsExactlyInAnyOrder(cbr.assetId(), vbr.assetId());

        Long cachedCbr = jdbc.queryForObject(
                "SELECT duration_ms FROM attempt.audio_asset_duration WHERE asset_id = ?",
                Long.class, cbr.assetId());
        Long cachedVbr = jdbc.queryForObject(
                "SELECT duration_ms FROM attempt.audio_asset_duration WHERE asset_id = ?",
                Long.class, vbr.assetId());
        assertThat(cachedCbr).isEqualTo(30_000L);
        assertThat(cachedVbr).isEqualTo(30_000L);
    }

    @Test
    void readsTwoBSample() throws Exception {
        UUID userId = createStudent("audio-2b-sample");
        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);
        MvcResult res = performStart(userId, "2b-sample-" + UUID.randomUUID(), line.entitlementId());
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
        List<AttemptAudioPlay> plays = attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attemptId);
        assertThat(plays).isNotEmpty();
        assertThat(plays).allSatisfy(play -> {
            assertThat(play.getState()).isEqualTo(AudioPlayState.IDLE);
            assertThat(play.getPlayCount()).isZero();
            assertThat(play.getDurationMs()).isEqualTo(2_088L);
        });
    }

    @Test
    void unreadableDurationAbortsAndReleases() throws Exception {
        UUID userId = createStudent("audio-invalid");
        var headerOnly = assetFixture.createFromAudioResource("header-only.mp3");
        UUID qInvalid = createReadyListeningQuestion(headerOnly.assetId(), "HEADER-ONLY");
        stubListeningPaperWithQuestions(List.of(qInvalid));

        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);
        String key = "invalid-audio-" + UUID.randomUUID();
        MvcResult res = performStart(userId, key, line.entitlementId());
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        Attempt aborted = awaitTerminalStartState(attemptId, AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.AUDIO_ASSET_INVALID);
        assertThat(aborted.getAttemptNo()).isNull();
        assertThat(aborted.getStartedAt()).isNull();
        assertThat(aborted.getDeadlineAt()).isNull();
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).isEmpty();
        assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
        assertThat(attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attemptId)).isEmpty();
        assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
        assertThat(countEntitlementUsage(attemptId, "RESERVED")).isZero();

        MvcResult replay = performStart(userId, key, line.entitlementId());
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        JsonNode replayBody = json(replay);
        assertThat(replayBody.path("state").asText()).isEqualTo("ABORTED");
        assertThat(replayBody.path("abortReason").asText()).isEqualTo("AUDIO_ASSET_INVALID");
        assertThat(replayBody.path("retryable").asBoolean()).isTrue();

        var events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);
        assertThat(events).anySatisfy(ev -> {
            assertThat(ev.getType()).isEqualTo(AttemptEventType.GENERATION_FAILED);
            assertThat(ev.getData().get("examCode")).isEqualTo("DS-HSCA-ANH-MINI");
            assertThat(ev.getData().get("questionId")).isEqualTo(qInvalid.toString());
            assertThat(ev.getData().get("assetId")).isEqualTo(headerOnly.assetId().toString());
            assertThat(ev.getData().get("reason")).isEqualTo("AUDIO_ASSET_INVALID");
        });
    }

    @Test
    void durationIsCached() throws Exception {
        UUID userId = createStudent("audio-cached");
        var cbr = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID qCbr = createReadyListeningQuestion(cbr.assetId(), "CACHE-30S");
        stubListeningPaperWithQuestions(List.of(qCbr));

        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 3);

        MvcResult firstRes = performStart(userId, "cache-1-" + UUID.randomUUID(), line.entitlementId());
        assertThat(firstRes.getResponse().getStatus()).isEqualTo(202);
        UUID firstAttemptId = UUID.fromString(json(firstRes).path("attemptId").asText());
        awaitTerminalStartState(firstAttemptId, AttemptState.IN_PROGRESS);
        verify(questionAssetContentPort, times(1)).loadContent(cbr.assetId());

        jdbc.update(
                "UPDATE attempt.attempt SET state = 'SCORED', finished_at = now() WHERE id = ?",
                firstAttemptId);
        clearInvocations(questionAssetContentPort);

        MvcResult secondRes = performStart(userId, "cache-2-" + UUID.randomUUID(), line.entitlementId());
        assertThat(secondRes.getResponse().getStatus()).isEqualTo(202);
        UUID secondAttemptId = UUID.fromString(json(secondRes).path("attemptId").asText());
        awaitTerminalStartState(secondAttemptId, AttemptState.IN_PROGRESS);

        verify(questionAssetContentPort, never()).loadContent(cbr.assetId());
        List<AttemptAudioPlay> plays = attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(secondAttemptId);
        assertThat(plays).hasSize(1);
        assertThat(plays.getFirst().getDurationMs()).isEqualTo(30_000L);
    }

    @Test
    void missingAudioAbortsAndReleases(CapturedOutput output) throws Exception {
        UUID userId = createStudent("audio-missing");
        var cbr = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID qMissing = createReadyListeningQuestion(cbr.assetId(), "MISSING-AUDIO");
        assetFixture.deleteObjectOnly(cbr.assetId());
        stubListeningPaperWithQuestions(List.of(qMissing));

        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);
        String key = "missing-audio-" + UUID.randomUUID();
        MvcResult res = performStart(userId, key, line.entitlementId());
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        Attempt aborted = awaitTerminalStartState(attemptId, AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.AUDIO_ASSET_MISSING);
        assertThat(aborted.getAttemptNo()).isNull();
        assertThat(aborted.getStartedAt()).isNull();
        assertThat(aborted.getDeadlineAt()).isNull();
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).isEmpty();
        assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
        assertThat(attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attemptId)).isEmpty();
        assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
        assertThat(countEntitlementUsage(attemptId, "RESERVED")).isZero();

        MvcResult replay = performStart(userId, key, line.entitlementId());
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        JsonNode replayBody = json(replay);
        assertThat(replayBody.path("abortReason").asText()).isEqualTo("AUDIO_ASSET_MISSING");
        assertThat(replayBody.path("retryable").asBoolean()).isTrue();

        var events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);
        assertThat(events).anySatisfy(ev -> {
            assertThat(ev.getType()).isEqualTo(AttemptEventType.GENERATION_FAILED);
            assertThat(ev.getData().get("examCode")).isEqualTo("DS-HSCA-ANH-MINI");
            assertThat(ev.getData().get("questionId")).isEqualTo(qMissing.toString());
            assertThat(ev.getData().get("assetId")).isEqualTo(cbr.assetId().toString());
            assertThat(ev.getData().get("reason")).isEqualTo("AUDIO_ASSET_MISSING");
        });
        assertThat(output.getOut())
                .contains("DS-HSCA-ANH-MINI")
                .contains(qMissing.toString())
                .contains(cbr.assetId().toString())
                .contains("AUDIO_ASSET_MISSING");
    }

    @Test
    void storageFailureAbortsAndReleases() throws Exception {
        UUID userId = createStudent("audio-storage-fail");
        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);

        doThrow(new ExternalServiceException("STORAGE_UNAVAILABLE"))
                .when(questionAttachmentPort)
                .attachments(any(), anyBoolean());

        MvcResult res = performStart(userId, "storage-fail-" + UUID.randomUUID(), line.entitlementId());
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

        Attempt aborted = awaitTerminalStartState(attemptId, AttemptState.ABORTED);
        assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.GENERATION_FAILED);
        assertThat(aborted.getAttemptNo()).isNull();
        assertThat(aborted.getStartedAt()).isNull();
        assertThat(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).isEmpty();
        assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).isEmpty();
        assertThat(attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attemptId)).isEmpty();
        assertThat(countEntitlementUsage(attemptId, "RELEASED")).isEqualTo(1);
        assertThat(countEntitlementUsage(attemptId, "RESERVED")).isZero();
    }

    @Test
    void paperWithoutAudioSkipsStorage() throws Exception {
        UUID userId = createStudent("paper-no-audio");
        for (String examCode : List.of("DS-HSCA-TOAN", "DS-VSAT-TOAN")) {
            clearInvocations(questionAttachmentPort, questionAssetContentPort);
            GrantOrderResult.Line line = grant(userId, examCode, 2);
            MvcResult res = performStart(userId, "no-audio-" + examCode + "-" + UUID.randomUUID(), line.entitlementId());
            assertThat(res.getResponse().getStatus()).isEqualTo(202);
            UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());

            awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
            verifyNoInteractions(questionAttachmentPort);
            verifyNoInteractions(questionAssetContentPort);
            assertThat(attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attemptId)).isEmpty();
        }
    }

    @Test
    void faultEndpointOnlyInE2eProfile() throws Exception {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSCA-ANH-MINI");
        assertThat(context.getBeanNamesForType(E2eAudioFaultController.class)).isEmpty();

        mockMvc.perform(post("/api/v1/test/audio-faults/{examId}", examId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/test/audio-faults/{examId}", examId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Nested
    @ActiveProfiles("e2e")
    @TestPropertySource(properties = "thi360.devdata.published-exams.enabled=false")
    class UnderE2e {

        @Autowired private MockMvc e2eMockMvc;
        @Autowired private JdbcTemplate e2eJdbc;
        @Autowired private ApplicationContext e2eContext;
        @Autowired private AttemptRepository e2eAttemptRepository;
        @Autowired private AttemptAudioPlayRepository e2eAudioPlayRepository;

        private final List<UUID> e2eUsers = new ArrayList<>();

        @BeforeEach
        void ensureDs() {
            AttemptTestFixtures.ensureDsBuilt(e2eContext);
        }

        @AfterEach
        void cleanE2e() {
            UUID examId = AttemptTestFixtures.dsExamId(e2eJdbc, "DS-HSCA-ANH-MINI");
            try {
                e2eMockMvc.perform(delete("/api/v1/test/audio-faults/{examId}", examId));
            } catch (Exception ignored) {
            }
            for (UUID u : e2eUsers) {
                AttemptTestFixtures.cleanupUser(e2eJdbc, u);
            }
            e2eUsers.clear();
        }

        @Test
        void faultEndpointTogglesMissingAudioAndRecovers() throws Exception {
            assertThat(e2eContext.getBeanNamesForType(E2eAudioFaultController.class)).hasSize(1);
            UUID examId = AttemptTestFixtures.dsExamId(e2eJdbc, "DS-HSCA-ANH-MINI");
            UUID userId = AttemptTestFixtures.createStudentAccount(
                    e2eJdbc, "e2e-audio-" + UUID.randomUUID().toString().substring(0, 6));
            e2eUsers.add(userId);
            GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);

            e2eMockMvc.perform(post("/api/v1/test/audio-faults/{examId}", examId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.examId").value(examId.toString()))
                    .andExpect(jsonPath("$.active").value(true));

            MvcResult faultedStart = performStart(
                    e2eMockMvc, userId, "e2e-fault-" + UUID.randomUUID(), line.entitlementId());
            assertThat(faultedStart.getResponse().getStatus()).isEqualTo(202);
            UUID faultedId = UUID.fromString(json(faultedStart).path("attemptId").asText());

            Attempt aborted = awaitState(e2eAttemptRepository, faultedId, AttemptState.ABORTED);
            assertThat(aborted.getAbortReason()).isEqualTo(AbortReason.AUDIO_ASSET_MISSING);
            assertThat(countUsage(e2eJdbc, faultedId, "RELEASED")).isEqualTo(1);
            assertThat(e2eAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(faultedId)).isEmpty();

            e2eMockMvc.perform(delete("/api/v1/test/audio-faults/{examId}", examId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.examId").value(examId.toString()))
                    .andExpect(jsonPath("$.active").value(false));

            MvcResult okStart = performStart(
                    e2eMockMvc, userId, "e2e-ok-" + UUID.randomUUID(), line.entitlementId());
            assertThat(okStart.getResponse().getStatus()).isEqualTo(202);
            UUID okId = UUID.fromString(json(okStart).path("attemptId").asText());

            Attempt opened = awaitState(e2eAttemptRepository, okId, AttemptState.IN_PROGRESS);
            assertThat(opened.getStartedAt()).isNotNull();
            assertThat(e2eAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(okId)).isNotEmpty();
        }
    }

    private void stubListeningPaperWithQuestions(List<UUID> replacementQuestionIds) {
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

    private UUID createReadyListeningQuestion(UUID assetId, String prefix) throws Exception {
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

    private UUID createStudent(String label) {
        UUID id = AttemptTestFixtures.createStudentAccount(
                jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    private GrantOrderResult.Line grant(UUID userId, String examCode, int attempts) {
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

    private MvcResult performStart(UUID userId, String idempotencyKey, UUID entitlementId)
            throws Exception {
        return performStart(mockMvc, userId, idempotencyKey, entitlementId);
    }

    private MvcResult performStart(
            MockMvc mvc, UUID userId, String idempotencyKey, UUID entitlementId) throws Exception {
        StartAttemptRequest payload = new StartAttemptRequest(entitlementId, null);
        return mvc.perform(
                        post("/api/v1/me/attempts")
                                .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                .andReturn();
    }

    private Attempt awaitTerminalStartState(UUID attemptId, AttemptState... targets)
            throws InterruptedException {
        return awaitState(attemptRepository, attemptId, targets);
    }

    private static Attempt awaitState(
            AttemptRepository repo, UUID attemptId, AttemptState... targets)
            throws InterruptedException {
        Set<AttemptState> targetSet = EnumSet.noneOf(AttemptState.class);
        targetSet.addAll(List.of(targets));
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Attempt current = repo.findById(attemptId).orElse(null);
            if (current != null && targetSet.contains(current.getState())) {
                return current;
            }
            Thread.sleep(25);
        }
        Attempt latest = repo.findById(attemptId).orElse(null);
        throw new AssertionError(
                "Timed out waiting for attempt "
                        + attemptId
                        + " to reach "
                        + targetSet
                        + ", actual="
                        + (latest == null ? "MISSING" : latest.getState()));
    }

    private int countEntitlementUsage(UUID attemptId, String state) {
        return countUsage(jdbc, attemptId, state);
    }

    private static int countUsage(JdbcTemplate jdbcTemplate, UUID attemptId, String state) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM commerce.entitlement_usage WHERE attempt_id = ? AND state = ?",
                        Integer.class,
                        attemptId,
                        state);
        assertThat(count).isNotNull();
        return count;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
