package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AttemptAudioFileCheck;
import vn.unicom.thi360.attempt.application.AttemptAudioFileCheck.AudioFileCheckException;
import vn.unicom.thi360.attempt.application.AttemptAudioFileCheck.CheckResult;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledItem;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledPart;
import vn.unicom.thi360.attempt.application.AudioDurationReader;
import vn.unicom.thi360.attempt.application.AudioDurationReader.AudioAssetMissingException;
import vn.unicom.thi360.attempt.application.AudioFileProbe;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.Attachment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class AttemptAudioFileCheckTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void nonAudioAttachmentsNeverTriggerStorageProbe() {
        AudioFileProbe probe = mock(AudioFileProbe.class);
        AudioDurationReader durationReader = mock(AudioDurationReader.class);
        AttemptAudioFileCheck check = new AttemptAudioFileCheck(probe, durationReader);

        UUID examId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID pdfId = UUID.randomUUID();
        VersionKey key = new VersionKey(qId, 1);

        when(probe.probe(eq(examId), any(), eq(false)))
                .thenReturn(
                        Map.of(
                                key,
                                List.of(new Attachment(pdfId, "doc.pdf", "application/pdf", null))));

        CheckResult result =
                check.checkAndMeasure(
                        examId,
                        "EX-PDF",
                        Map.of("P1", assembledPart(List.of(qId))),
                        Map.of(key, versionWithAttachments(key, List.of(pdfId))));

        assertThat(result.assets()).isEmpty();
        verify(probe).probe(eq(examId), any(), eq(false));
        verify(probe, never()).probe(any(), any(), eq(true));
        verifyNoInteractions(durationReader);
    }

    @Test
    void validatesAllAudioReadabilityBeforeMeasuringDurations() {
        AudioFileProbe probe = mock(AudioFileProbe.class);
        AudioDurationReader durationReader = mock(AudioDurationReader.class);
        AttemptAudioFileCheck check = new AttemptAudioFileCheck(probe, durationReader);

        UUID examId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID readableAudioId = UUID.randomUUID();
        UUID missingAudioId = UUID.randomUUID();
        VersionKey k1 = new VersionKey(q1, 1);
        VersionKey k2 = new VersionKey(q2, 1);

        when(probe.probe(eq(examId), any(), eq(false)))
                .thenReturn(
                        Map.of(
                                k1, List.of(new Attachment(readableAudioId, "ok.mp3", "audio/mpeg", null)),
                                k2, List.of(new Attachment(missingAudioId, "missing.mp3", "audio/mpeg", null))));
        when(probe.probe(eq(examId), any(), eq(true)))
                .thenReturn(
                        Map.of(
                                k1, List.of(new Attachment(readableAudioId, "ok.mp3", "audio/mpeg", true)),
                                k2, List.of(new Attachment(missingAudioId, "missing.mp3", "audio/mpeg", false))));

        assertThatThrownBy(
                        () ->
                                check.checkAndMeasure(
                                        examId,
                                        "EX-AUD",
                                        Map.of("P1", assembledPart(List.of(q1, q2))),
                                        Map.of(
                                                k1, versionWithAttachments(k1, List.of(readableAudioId)),
                                                k2, versionWithAttachments(k2, List.of(missingAudioId)))))
                .isInstanceOf(AudioFileCheckException.class)
                .satisfies(
                        ex -> {
                            AudioFileCheckException checkEx = (AudioFileCheckException) ex;
                            assertThat(checkEx.abortReason()).isEqualTo(AbortReason.AUDIO_ASSET_MISSING);
                            assertThat(checkEx.assetId()).isEqualTo(missingAudioId);
                            assertThat(checkEx.questionId()).isEqualTo(q2);
                        });

        verifyNoInteractions(durationReader);
    }

    @Test
    void mapsDurationReaderMissingExceptionToAudioAssetMissing() {
        AudioFileProbe probe = mock(AudioFileProbe.class);
        AudioDurationReader durationReader = mock(AudioDurationReader.class);
        AttemptAudioFileCheck check = new AttemptAudioFileCheck(probe, durationReader);

        UUID examId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID audioId = UUID.randomUUID();
        VersionKey key = new VersionKey(qId, 1);

        when(probe.probe(eq(examId), any(), eq(false)))
                .thenReturn(Map.of(key, List.of(new Attachment(audioId, "a.mp3", "audio/mpeg", null))));
        when(probe.probe(eq(examId), any(), eq(true)))
                .thenReturn(Map.of(key, List.of(new Attachment(audioId, "a.mp3", "audio/mpeg", true))));
        when(durationReader.resolveDurationMs(audioId))
                .thenThrow(new AudioAssetMissingException("vanished between probe and read"));

        assertThatThrownBy(
                        () ->
                                check.checkAndMeasure(
                                        examId,
                                        "EX-RACE",
                                        Map.of("P1", assembledPart(List.of(qId))),
                                        Map.of(key, versionWithAttachments(key, List.of(audioId)))))
                .isInstanceOf(AudioFileCheckException.class)
                .satisfies(
                        ex -> {
                            AudioFileCheckException checkEx = (AudioFileCheckException) ex;
                            assertThat(checkEx.abortReason()).isEqualTo(AbortReason.AUDIO_ASSET_MISSING);
                            assertThat(checkEx.assetId()).isEqualTo(audioId);
                            assertThat(checkEx.questionId()).isEqualTo(qId);
                        });
    }

    private static AssembledPart assembledPart(List<UUID> questionIds) {
        List<AssembledItem> items = new java.util.ArrayList<>(questionIds.size());
        for (int i = 0; i < questionIds.size(); i++) {
            items.add(
                    new AssembledItem(
                            null,
                            i,
                            i + 1,
                            1,
                            questionIds.get(i),
                            1,
                            QuestionType.SINGLE_CHOICE,
                            null,
                            "sec-1",
                            "ANH",
                            "blk-1",
                            BigDecimal.ONE,
                            List.of("o1", "o2")));
        }
        return new AssembledPart(
                items, questionIds.size() + 1, BigDecimal.valueOf(questionIds.size()), questionIds.size());
    }

    private static Version versionWithAttachments(VersionKey key, List<UUID> attachmentIds) {
        ObjectNode snapshot = MAPPER.createObjectNode();
        var arr = snapshot.putArray("attachmentIds");
        attachmentIds.forEach(id -> arr.add(id.toString()));
        return new Version(key, snapshot);
    }
}
