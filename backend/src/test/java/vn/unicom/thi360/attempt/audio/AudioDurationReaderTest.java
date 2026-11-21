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

        byte[] truncatedWithId3v1 = concat(truncatedLastFrame, id3v1Tag());
        assertThatThrownBy(() -> AudioDurationReader.measureMp3DurationMs(truncatedWithId3v1))
                .isInstanceOf(AudioAssetInvalidException.class);

        byte[] trailingGarbage = concat(cbr, new byte[] {0, 0, 1, 2});
        assertThatThrownBy(() -> AudioDurationReader.measureMp3DurationMs(trailingGarbage))
                .isInstanceOf(AudioAssetInvalidException.class);

        byte[] headerOnly = AudioAssetFixture.readResource("attempt/audio/header-only.mp3");
        assertThatThrownBy(
                        () ->
                                AudioDurationReader.measureMp3DurationMs(
                                        concat(headerOnly, new byte[64])))
                .isInstanceOf(AudioAssetInvalidException.class);
    }

    @Test
    void stripsTrailingId3v1TagOnAllRepoSamples() {
        byte[] cbr = AudioAssetFixture.readResource("attempt/audio/silence-30s-cbr.mp3");
        byte[] vbr = AudioAssetFixture.readResource("attempt/audio/silence-30s-vbr-xing.mp3");
        byte[] twoB =
                AudioAssetFixture.readResource("db/devdata/assets/hsca-en-listening-single-01.mp3");

        byte[] tag = id3v1Tag();
        assertThat(AudioDurationReader.measureMp3DurationMs(concat(cbr, tag))).isEqualTo(30_000L);
        assertThat(AudioDurationReader.measureMp3DurationMs(concat(vbr, tag))).isEqualTo(30_000L);
        assertThat(AudioDurationReader.measureMp3DurationMs(concat(twoB, tag))).isEqualTo(2_088L);
    }

    @Test
    void handlesStackedId3v2HeadersApeV2FooterLyrics3AndTrailingNullPadding() {
        byte[] cbr = AudioAssetFixture.readResource("attempt/audio/silence-30s-cbr.mp3");
        byte[] twoB =
                AudioAssetFixture.readResource("db/devdata/assets/hsca-en-listening-single-01.mp3");

        // Stacked ID3v2 headers + trailing APEv2 (with header & footer) + ID3v1 + null padding
        byte[] stackedId3v2 = concat(id3v2Tag(24), id3v2Tag(32));
        byte[] apeWithHeader = apeV2Tag(true, 16);
        byte[] apeFooterOnly = apeV2Tag(false, 20);
        byte[] trailingZeros = new byte[48];

        assertThat(
                        AudioDurationReader.measureMp3DurationMs(
                                concat(stackedId3v2, cbr, trailingZeros, apeWithHeader, id3v1Tag())))
                .isEqualTo(30_000L);
        assertThat(
                        AudioDurationReader.measureMp3DurationMs(
                                concat(id3v2Tag(12), twoB, trailingZeros, apeFooterOnly)))
                .isEqualTo(2_088L);
        assertThat(
                        AudioDurationReader.measureMp3DurationMs(
                                concat(cbr, lyrics3v2Tag("Test lyrics"), id3v1Tag())))
                .isEqualTo(30_000L);
    }

    private static byte[] id3v1Tag() {
        byte[] tag = new byte[128];
        tag[0] = 'T';
        tag[1] = 'A';
        tag[2] = 'G';
        byte[] title = "Sample Title".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, tag, 3, title.length);
        return tag;
    }

    private static byte[] id3v2Tag(int bodySize) {
        byte[] tag = new byte[10 + bodySize];
        tag[0] = 'I';
        tag[1] = 'D';
        tag[2] = '3';
        tag[3] = 3; // ID3v2.3
        tag[4] = 0;
        tag[5] = 0;
        tag[6] = (byte) ((bodySize >> 21) & 0x7F);
        tag[7] = (byte) ((bodySize >> 14) & 0x7F);
        tag[8] = (byte) ((bodySize >> 7) & 0x7F);
        tag[9] = (byte) (bodySize & 0x7F);
        return tag;
    }

    private static byte[] apeV2Tag(boolean withHeader, int itemsSize) {
        int tagSize = itemsSize + 32; // items + footer (excludes optional header)
        int totalSize = tagSize + (withHeader ? 32 : 0);
        byte[] ape = new byte[totalSize];
        long flagsBase = withHeader ? 0x8000_0000L : 0L;
        if (withHeader) {
            writeApeHeaderOrFooter(ape, 0, 2000L, tagSize, 1, flagsBase | 0x2000_0000L);
        }
        writeApeHeaderOrFooter(ape, totalSize - 32, 2000L, tagSize, 1, flagsBase);
        return ape;
    }

    private static void writeApeHeaderOrFooter(
            byte[] buf, int offset, long version, int tagSize, int itemCount, long flags) {
        byte[] magic = "APETAGEX".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, buf, offset, 8);
        writeUint32Le(buf, offset + 8, version);
        writeUint32Le(buf, offset + 12, tagSize);
        writeUint32Le(buf, offset + 16, itemCount);
        writeUint32Le(buf, offset + 20, flags);
    }

    private static void writeUint32Le(byte[] buf, int offset, long val) {
        buf[offset] = (byte) (val & 0xFF);
        buf[offset + 1] = (byte) ((val >>> 8) & 0xFF);
        buf[offset + 2] = (byte) ((val >>> 16) & 0xFF);
        buf[offset + 3] = (byte) ((val >>> 24) & 0xFF);
    }

    private static byte[] lyrics3v2Tag(String text) {
        byte[] begin = ("LYRICSBEGIN" + text).getBytes(StandardCharsets.US_ASCII);
        String sizeStr = String.format(java.util.Locale.ROOT, "%06d", begin.length);
        byte[] trailer = (sizeStr + "LYRICS200").getBytes(StandardCharsets.US_ASCII);
        return concat(begin, trailer);
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
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
