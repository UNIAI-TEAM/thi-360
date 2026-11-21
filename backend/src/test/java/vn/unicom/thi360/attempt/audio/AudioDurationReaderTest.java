package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import vn.unicom.thi360.attempt.application.AudioDurationReader;
import vn.unicom.thi360.attempt.application.AudioDurationReader.AudioAssetInvalidException;
import vn.unicom.thi360.attempt.application.AudioDurationReader.AudioAssetMissingException;
import vn.unicom.thi360.questionbank.domain.QuestionAssetSignature;
import vn.unicom.thi360.questionbank.domain.QuestionAssetSignature.AttachmentSignature;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetKind;
import vn.unicom.thi360.shared.api.ExternalServiceException;

class AudioDurationReaderTest {

    @Test
    void measuresCbr30SecondsExact() {
        byte[] bytes = AudioAssetFixture.readResource("attempt/audio/silence-30s-cbr.mp3");
        assertThat(AudioDurationReader.measureMp3DurationMs(bytes)).isEqualTo(30_000L);
    }

    @Test
    void measuresVbrXing30SecondsWithoutDoubleCountingHeaderFrame() {
        byte[] bytes = AudioAssetFixture.readResource("attempt/audio/silence-30s-vbr-xing.mp3");
        assertThat(AudioDurationReader.measureMp3DurationMs(bytes)).isEqualTo(30_000L);
    }

    @Test
    void measuresTwoBSampleHscaListeningExact2088Ms() {
        byte[] bytes =
                AudioAssetFixture.readResource("db/devdata/assets/hsca-en-listening-single-01.mp3");
        assertThat(AudioDurationReader.measureMp3DurationMs(bytes)).isEqualTo(2_088L);
    }

    @Test
    void rejectsHeaderOnlyMp3EvenThoughItPassesUploadSignature() {
        byte[] bytes = AudioAssetFixture.readResource("attempt/audio/header-only.mp3");
        assertThat(QuestionAssetSignature.detectAttachment(bytes, "header-only.mp3"))
                .contains(AttachmentSignature.MP3);
        assertThatThrownBy(() -> AudioDurationReader.measureMp3DurationMs(bytes))
                .isInstanceOf(AudioAssetInvalidException.class);
    }

    @Test
    void rejectsEmptyNullAndNonMp3Bytes() {
        assertThatThrownBy(() -> AudioDurationReader.measureMp3DurationMs(null))
                .isInstanceOf(AudioAssetInvalidException.class);
        assertThatThrownBy(() -> AudioDurationReader.measureMp3DurationMs(new byte[0]))
                .isInstanceOf(AudioAssetInvalidException.class);
        assertThatThrownBy(
                        () ->
                                AudioDurationReader.measureMp3DurationMs(
                                        "not an mp3 file at all".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AudioAssetInvalidException.class);

        byte[] cbr = AudioAssetFixture.readResource("attempt/audio/silence-30s-cbr.mp3");
        byte[] truncatedLastFrame = new byte[cbr.length - 10];
        System.arraycopy(cbr, 0, truncatedLastFrame, 0, truncatedLastFrame.length);
        assertThatThrownBy(() -> AudioDurationReader.measureMp3DurationMs(truncatedLastFrame))
                .isInstanceOf(AudioAssetInvalidException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cacheHitSkipsStorageAndCacheMissPersists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        QuestionAssetContentPort contentPort = mock(QuestionAssetContentPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
        AudioDurationReader reader = new AudioDurationReader(jdbc, contentPort, clock);

        UUID cachedId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(cachedId)))
                .thenReturn(List.of(12_345L));
        assertThat(reader.resolveDurationMs(cachedId)).isEqualTo(12_345L);
        verify(contentPort, never()).loadContent(any());

        UUID missId = UUID.randomUUID();
        byte[] sample = AudioAssetFixture.readResource("attempt/audio/silence-30s-cbr.mp3");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(missId))).thenReturn(List.of());
        when(contentPort.loadContent(missId))
                .thenReturn(
                        Optional.of(
                                new AssetContent(
                                        missId,
                                        AssetKind.ATTACHMENT,
                                        "silence-30s-cbr.mp3",
                                        "audio/mpeg",
                                        sample)));

        assertThat(reader.resolveDurationMs(missId)).isEqualTo(30_000L);
        verify(jdbc)
                .update(
                        anyString(),
                        eq(missId),
                        eq(30_000L),
                        any(java.sql.Timestamp.class));

        UUID missingId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(missingId))).thenReturn(List.of());
        when(contentPort.loadContent(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reader.resolveDurationMs(missingId))
                .isInstanceOf(AudioAssetMissingException.class);

        UUID downId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(downId))).thenReturn(List.of());
        when(contentPort.loadContent(downId))
                .thenThrow(new ExternalServiceException("STORAGE_DOWN"));
        assertThatThrownBy(() -> reader.resolveDurationMs(downId))
                .isInstanceOf(ExternalServiceException.class);
    }
}
