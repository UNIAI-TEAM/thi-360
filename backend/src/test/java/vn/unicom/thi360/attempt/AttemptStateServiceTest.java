package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.api.AttemptStateResponse;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptDisplaySegmentView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptPartStateView;
import vn.unicom.thi360.attempt.application.AttemptAccessGate;
import vn.unicom.thi360.attempt.application.AttemptGuard;
import vn.unicom.thi360.attempt.application.AttemptQuestionSanitizer;
import vn.unicom.thi360.attempt.application.AttemptStateService;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Segment;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Unit tests for {@link AttemptStateService} {@code displaySegments} projection, visibility gating,
 * Jackson {@code NON_EMPTY} omission, and {@link ForbiddenKeysAssert} compliance (BS §17.3, AD-174).
 */
class AttemptStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void visiblePartsReceiveSortedDisplaySegmentsAndPassForbiddenKeysAssert() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        int versionNo = 3;
        UUID attemptId = UUID.randomUUID();
        Attempt attempt = openAttempt(attemptId, userId, examId, versionNo);

        AttemptPart closedPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P1", "Phần 1", 0, 30, 15, BigDecimal.TEN, 1L);
        closedPart.open(NOW.minusSeconds(1800), NOW.minusSeconds(600));
        closedPart.close(PartCloseReason.FINISHED, NOW.minusSeconds(600));

        AttemptPart openPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P2", "Phần 2", 1, 30, 15, BigDecimal.TEN, 2L);
        openPart.open(NOW.minusSeconds(300), NOW.plusSeconds(1500));

        PublishedVersion frozenVersion =
                publishedVersion(
                        examId,
                        versionNo,
                        List.of(
                                partWithSegments(
                                        "P1",
                                        Arrays.asList(
                                                new Segment("S2", "Đoạn 2", 6, 15, 2, new BigDecimal("5.0")),
                                                null,
                                                new Segment("S1", "Đoạn 1", 1, 5, 1, new BigDecimal("5.0")))),
                                partWithSegments(
                                        "P2",
                                        List.of(
                                                new Segment("B", "Đoạn B", 1, 10, 1, new BigDecimal("4.0")),
                                                new Segment("A", "Đoạn A", 1, 5, 1, new BigDecimal("3.0")),
                                                new Segment("C", "Đoạn C", null, null, 1, new BigDecimal("3.0"))))));

        PublishedExamPort publishedExamPort = mock(PublishedExamPort.class);
        when(publishedExamPort.loadVersion(examId, versionNo)).thenReturn(Optional.of(frozenVersion));

        AttemptStateService service =
                buildService(
                        userId,
                        attempt,
                        List.of(closedPart, openPart),
                        publishedExamPort);

        AttemptStateResponse response = service.getState(attemptId);

        verify(publishedExamPort, times(1)).loadVersion(examId, versionNo);
        assertThat(response.parts()).hasSize(2);

        AttemptPartStateView p1View = response.parts().get(0);
        assertThat(p1View.state()).isEqualTo(PartState.CLOSED);
        assertThat(p1View.displaySegments())
                .containsExactly(
                        new AttemptDisplaySegmentView("S1", "Đoạn 1", 1, 5, 1, new BigDecimal("5.0")),
                        new AttemptDisplaySegmentView("S2", "Đoạn 2", 6, 15, 2, new BigDecimal("5.0")));

        AttemptPartStateView p2View = response.parts().get(1);
        assertThat(p2View.state()).isEqualTo(PartState.OPEN);
        assertThat(p2View.displaySegments())
                .containsExactly(
                        new AttemptDisplaySegmentView("A", "Đoạn A", 1, 5, 1, new BigDecimal("3.0")),
                        new AttemptDisplaySegmentView("B", "Đoạn B", 1, 10, 1, new BigDecimal("4.0")),
                        new AttemptDisplaySegmentView("C", "Đoạn C", null, null, 1, new BigDecimal("3.0")));

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(root);
        assertThat(root.path("parts").get(0).path("displaySegments")).hasSize(2);
        assertThat(root.path("parts").get(1).path("displaySegments")).hasSize(3);
    }

    @Test
    void nonVisiblePartsNeverLeakDisplaySegments() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        int versionNo = 1;
        UUID attemptId = UUID.randomUUID();
        Attempt attempt = openAttempt(attemptId, userId, examId, versionNo);

        AttemptPart openPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P1", "Phần 1", 0, 20, 10, BigDecimal.TEN, 1L);
        openPart.open(NOW.minusSeconds(60), NOW.plusSeconds(1140));

        AttemptPart pendingPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P2", "Phần 2", 1, 20, 10, BigDecimal.TEN, 2L);

        AttemptPart awaitingChoicePart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P3", "Phần 3", 2, 20, 10, BigDecimal.TEN, 3L);
        awaitingChoicePart.awaitChoice(NOW.plusSeconds(900));

        AttemptPart generatingPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P4", "Phần 4", 3, 20, 10, BigDecimal.TEN, 4L);
        generatingPart.awaitChoice(NOW.plusSeconds(900));
        generatingPart.confirmChoiceAndStartGenerating(
                Map.of("optionCode", "OPT_1"), NOW, UUID.randomUUID(), NOW);

        AttemptPart skippedPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P5", "Phần 5", 4, 20, 10, BigDecimal.TEN, 5L);
        skippedPart.skip(PartCloseReason.CHOICE_TIMEOUT, NOW);

        AttemptPart failedPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P6", "Phần 6", 5, 20, 10, BigDecimal.TEN, 6L);
        failedPart.awaitChoice(NOW.plusSeconds(900));
        failedPart.confirmChoiceAndStartGenerating(
                Map.of("optionCode", "OPT_2"), NOW, UUID.randomUUID(), NOW);
        failedPart.failGeneration("err");

        PublishedVersion frozenVersion =
                publishedVersion(
                        examId,
                        versionNo,
                        List.of(
                                partWithSegments("P1", List.of(new Segment("S1", "Visible", 1, 10, 1, BigDecimal.TEN))),
                                partWithSegments("P2", List.of(new Segment("S2", "Secret P2", 1, 10, 1, BigDecimal.TEN))),
                                partWithSegments("P3", List.of(new Segment("S3", "Secret P3", 1, 10, 1, BigDecimal.TEN))),
                                partWithSegments("P4", List.of(new Segment("S4", "Secret P4", 1, 10, 1, BigDecimal.TEN))),
                                partWithSegments("P5", List.of(new Segment("S5", "Secret P5", 1, 10, 1, BigDecimal.TEN))),
                                partWithSegments("P6", List.of(new Segment("S6", "Secret P6", 1, 10, 1, BigDecimal.TEN)))));

        PublishedExamPort publishedExamPort = mock(PublishedExamPort.class);
        when(publishedExamPort.loadVersion(examId, versionNo)).thenReturn(Optional.of(frozenVersion));

        AttemptStateService service =
                buildService(
                        userId,
                        attempt,
                        List.of(openPart, pendingPart, awaitingChoicePart, generatingPart, skippedPart, failedPart),
                        publishedExamPort);

        AttemptStateResponse response = service.getState(attemptId);

        assertThat(response.parts()).hasSize(6);
        assertThat(response.parts().get(0).displaySegments()).hasSize(1);
        for (int i = 1; i < response.parts().size(); i++) {
            assertThat(response.parts().get(i).displaySegments())
                    .as("Part %s must hide displaySegments", response.parts().get(i).partCode())
                    .isEmpty();
        }

        String rawJson = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(rawJson);
        assertThat(root.path("parts").get(0).has("displaySegments")).isTrue();
        for (int i = 1; i < 6; i++) {
            assertThat(root.path("parts").get(i).has("displaySegments"))
                    .as("Empty displaySegments on part %d must be omitted by NON_EMPTY", i)
                    .isFalse();
        }
        assertThat(rawJson).doesNotContain("Secret P");
    }

    @Test
    void skipsPortLookupWhenNoPartsVisibleAndHandlesMissingSegmentsOrVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        int versionNo = 2;
        UUID attemptId = UUID.randomUUID();
        Attempt attempt = openAttempt(attemptId, userId, examId, versionNo);

        AttemptPart pendingOnly =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P1", "Phần 1", 0, 20, 10, BigDecimal.TEN, 1L);
        PublishedExamPort noCallPort = mock(PublishedExamPort.class);

        AttemptStateService noVisibleService =
                buildService(userId, attempt, List.of(pendingOnly), noCallPort);
        AttemptStateResponse hiddenResponse = noVisibleService.getState(attemptId);
        verifyNoInteractions(noCallPort);
        assertThat(hiddenResponse.parts().getFirst().displaySegments()).isEmpty();
        JsonNode hiddenJson = objectMapper.readTree(objectMapper.writeValueAsString(hiddenResponse));
        assertThat(hiddenJson.path("parts").get(0).has("displaySegments")).isFalse();

        AttemptPart openPart =
                AttemptPart.createPending(
                        UUID.randomUUID(), attemptId, "P1", "Phần 1", 0, 20, 10, BigDecimal.TEN, 1L);
        openPart.open(NOW, NOW.plusSeconds(1200));

        PublishedExamPort missingVersionPort = mock(PublishedExamPort.class);
        when(missingVersionPort.loadVersion(examId, versionNo)).thenReturn(Optional.empty());
        AttemptStateService missingVersionService =
                buildService(userId, attempt, List.of(openPart), missingVersionPort);
        AttemptStateResponse missingVersionResp = missingVersionService.getState(attemptId);
        assertThat(missingVersionResp.parts().getFirst().displaySegments()).isEmpty();
        JsonNode missingVersionJson =
                objectMapper.readTree(objectMapper.writeValueAsString(missingVersionResp));
        assertThat(missingVersionJson.path("parts").get(0).has("displaySegments")).isFalse();

        PublishedExamPort emptySegmentsPort = mock(PublishedExamPort.class);
        when(emptySegmentsPort.loadVersion(examId, versionNo))
                .thenReturn(Optional.of(publishedVersion(examId, versionNo, List.of(partWithSegments("P1", List.of())))));
        AttemptStateService emptySegmentsService =
                buildService(userId, attempt, List.of(openPart), emptySegmentsPort);
        AttemptStateResponse emptySegmentsResp = emptySegmentsService.getState(attemptId);
        assertThat(emptySegmentsResp.parts().getFirst().displaySegments()).isEmpty();
        JsonNode emptySegmentsJson =
                objectMapper.readTree(objectMapper.writeValueAsString(emptySegmentsResp));
        assertThat(emptySegmentsJson.path("parts").get(0).has("displaySegments")).isFalse();
    }

    @Test
    void autowiredConstructorRequiresPublishedExamPort() {
        assertThatThrownBy(
                        () ->
                                new AttemptStateService(
                                        mock(AttemptAccessGate.class),
                                        mock(AttemptGuard.class),
                                        mock(AttemptPartRepository.class),
                                        mock(AttemptItemRepository.class),
                                        mock(AttemptAudioPlayRepository.class),
                                        mock(QuestionSourcePort.class),
                                        mock(QuestionAttachmentPort.class),
                                        null,
                                        mock(AttemptQuestionSanitizer.class),
                                        mock(EntityManager.class),
                                        Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(NullPointerException.class);
    }

    private AttemptStateService buildService(
            UUID userId,
            Attempt attempt,
            List<AttemptPart> parts,
            PublishedExamPort publishedExamPort) {
        AttemptAccessGate accessGate = mock(AttemptAccessGate.class);
        AttemptGuard guard = mock(AttemptGuard.class);
        AttemptPartRepository partRepo = mock(AttemptPartRepository.class);
        AttemptItemRepository itemRepo = mock(AttemptItemRepository.class);
        AttemptAudioPlayRepository audioRepo = mock(AttemptAudioPlayRepository.class);
        QuestionSourcePort sourcePort = mock(QuestionSourcePort.class);
        QuestionAttachmentPort attachmentPort = mock(QuestionAttachmentPort.class);
        EntityManager entityManager = mock(EntityManager.class);

        when(accessGate.requireLearner()).thenReturn(userId);
        when(guard.requireOwnedAttempt(attempt.getId(), userId)).thenReturn(attempt);
        when(partRepo.findByAttemptIdOrderByOrdAsc(attempt.getId())).thenReturn(parts);
        when(audioRepo.findByAttemptIdOrderByAssetIdAsc(attempt.getId())).thenReturn(List.of());
        when(itemRepo.findByAttemptIdAndPartIdInOrderByPartIdAscOrdAsc(any(), any())).thenReturn(List.of());

        return new AttemptStateService(
                accessGate,
                guard,
                partRepo,
                itemRepo,
                audioRepo,
                sourcePort,
                attachmentPort,
                publishedExamPort,
                new AttemptQuestionSanitizer(),
                entityManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Attempt openAttempt(UUID attemptId, UUID userId, UUID examId, int versionNo) {
        Attempt attempt =
                Attempt.start(
                        attemptId,
                        userId,
                        examId,
                        versionNo,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "V-SAT English",
                        UUID.randomUUID(),
                        "VSAT",
                        "SUBJECT",
                        null,
                        UUID.randomUUID(),
                        AttemptTiming.PER_PART,
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        1L,
                        UUID.randomUUID(),
                        "SCORE_AND_ANSWERS",
                        "RAW",
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        "k-state-seg",
                        NOW.minusSeconds(3600));
        attempt.open(1, NOW.minusSeconds(3600), null);
        return attempt;
    }

    private static PublishedVersion publishedVersion(UUID examId, int versionNo, List<Part> parts) {
        return new PublishedVersion(
                examId,
                versionNo,
                "SERVING",
                "PUBLISHED",
                NOW.minusSeconds(7200),
                "EX-VSAT",
                "V-SAT English",
                UUID.randomUUID(),
                "VSAT",
                "SUBJECT",
                null,
                UUID.randomUUID(),
                "STANDARD",
                "PER_PART",
                60,
                BigDecimal.TEN,
                BigDecimal.TEN,
                "RAW",
                null,
                parts,
                List.of(),
                List.of(),
                List.of("default"),
                List.of("default"),
                null);
    }

    private static Part partWithSegments(String partCode, List<Segment> segments) {
        return new Part(
                partCode,
                1,
                partCode,
                60,
                10,
                10,
                10,
                "FIXED",
                null,
                BigDecimal.TEN,
                segments,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
