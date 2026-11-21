package vn.unicom.thi360.attempt.application;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;

/**
 * Resolves and caches server-measured MP3 durations in {@code attempt.audio_asset_duration} by
 * walking MPEG frames directly from raw bytes without PCM decoding or temp files (BS §17.2, §17.4).
 */
@Service
public class AudioDurationReader {

    private static final int ID3V2_HEADER_BYTES = 10;
    private static final int ID3V1_TAG_BYTES = 128;
    private static final int APE_FOOTER_BYTES = 32;
    private static final int LYRICS3_V1_MAX_BODY_BYTES = 5100;
    private static final int[][] MPEG_BITRATES_KBPS = {
        // 0: MPEG1 Layer I
        {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
        // 1: MPEG1 Layer II
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
        // 2: MPEG1 Layer III
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
        // 3: MPEG2/2.5 Layer I
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
        // 4: MPEG2/2.5 Layer II & III
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160}
    };
    private static final int[] MPEG1_SAMPLE_RATES = {44100, 48000, 32000};

    private final JdbcTemplate jdbcTemplate;
    private final QuestionAssetContentPort questionAssetContentPort;
    private final Clock clock;

    public AudioDurationReader(
            JdbcTemplate jdbcTemplate,
            QuestionAssetContentPort questionAssetContentPort,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.questionAssetContentPort = Objects.requireNonNull(questionAssetContentPort);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Returns cached duration in milliseconds from {@code attempt.audio_asset_duration}, or loads
     * raw bytes from storage (outside any DB transaction), measures MPEG frame duration, and caches
     * the positive result.
     */
    public long resolveDurationMs(UUID assetId) {
        Objects.requireNonNull(assetId, "assetId must not be null");
        List<Long> cached =
                jdbcTemplate.query(
                        "SELECT duration_ms FROM attempt.audio_asset_duration WHERE asset_id = ?",
                        (rs, rowNum) -> rs.getLong("duration_ms"),
                        assetId);
        if (!cached.isEmpty() && cached.getFirst() != null && cached.getFirst() > 0) {
            return cached.getFirst();
        }

        AssetContent loaded =
                questionAssetContentPort
                        .loadContent(assetId)
                        .orElseThrow(
                                () ->
                                        new AudioAssetMissingException(
                                                "Audio asset content missing in storage: " + assetId));

        long durationMs = measureMp3DurationMs(loaded.content());

        jdbcTemplate.update(
                "INSERT INTO attempt.audio_asset_duration (asset_id, duration_ms, computed_at) "
                        + "VALUES (?, ?, ?) ON CONFLICT (asset_id) DO NOTHING",
                assetId,
                durationMs,
                Timestamp.from(clock.instant()));
        return durationMs;
    }

    /**
     * Measures exact MP3 duration in milliseconds by walking every MPEG frame via JLayer {@link
     * Bitstream}, skipping stacked ID3v2 headers and trailing ID3v1/APEv2/Lyrics3/null-padding
     * metadata before reading, rejecting truncated frames, and excluding any leading Xing/Info/VBRI
     * metadata header frame when audio frames follow.
     */
    public static long measureMp3DurationMs(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new AudioAssetInvalidException("Audio bytes empty or too short for MPEG header");
        }

        int audioStart = skipId3v2Tags(bytes);
        while (audioStart < bytes.length && bytes[audioStart] == 0) {
            audioStart++;
        }
        int audioEnd = stripTrailingMetadata(bytes, audioStart, bytes.length);
        audioEnd = stripTrailingNullPadding(bytes, audioStart, audioEnd);
        if (audioEnd - audioStart < 4 || !isFrameSyncAt(bytes, audioStart)) {
            throw new AudioAssetInvalidException("Missing MPEG audio frame sync after tags");
        }

        int cursor = audioStart;
        int totalFrames = 0;
        int audioFrames = 0;
        double totalDurationMs = 0.0d;

        Bitstream bitstream =
                new Bitstream(new ByteArrayInputStream(bytes, audioStart, audioEnd - audioStart));
        try {
            while (cursor < audioEnd) {
                if (audioEnd - cursor < 4) {
                    throw new AudioAssetInvalidException("Trailing truncated bytes in MPEG stream");
                }
                Header header = bitstream.readFrame();
                if (header == null) {
                    throw new AudioAssetInvalidException(
                            "Truncated or invalid MPEG frame at offset " + cursor);
                }
                if (header.getSyncHeader() != readIntBigEndian(bytes, cursor)) {
                    throw new AudioAssetInvalidException(
                            "Non-contiguous bytes before MPEG frame at offset " + cursor);
                }
                if (header.bitrate_index() <= 0
                        || header.bitrate_index() >= 15
                        || header.frequency() <= 0
                        || header.layer() < 1
                        || header.layer() > 3
                        || header.framesize < 0) {
                    throw new AudioAssetInvalidException("Unsupported or invalid MPEG frame header");
                }
                int frameBytes = 4 + header.framesize;
                if (cursor + frameBytes > audioEnd) {
                    throw new AudioAssetInvalidException(
                            "Truncated MPEG frame at offset " + cursor);
                }

                boolean metadataFrame =
                        totalFrames == 0
                                && (header.vbr()
                                        || isXingInfoOrVbriFrame(
                                                bytes, cursor, frameBytes, header));
                if (!metadataFrame) {
                    audioFrames++;
                    totalDurationMs += frameDurationMs(header);
                }
                totalFrames++;
                cursor += frameBytes;
                bitstream.closeFrame();
            }
        } catch (BitstreamException ex) {
            throw new AudioAssetInvalidException("Invalid MP3 bitstream: " + ex.getMessage(), ex);
        } finally {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
            }
        }

        if (audioFrames <= 0) {
            throw new AudioAssetInvalidException("MP3 stream contains zero complete audio frames");
        }
        long durationMs = Math.round(totalDurationMs);
        if (durationMs <= 0) {
            throw new AudioAssetInvalidException("Computed MP3 duration must be positive");
        }
        return durationMs;
    }

    private static int skipId3v2Tags(byte[] bytes) {
        int start = 0;
        while (start + ID3V2_HEADER_BYTES <= bytes.length
                && bytes[start] == 'I'
                && bytes[start + 1] == 'D'
                && bytes[start + 2] == '3') {
            if ((bytes[start + 3] & 0xFF) == 0xFF || (bytes[start + 4] & 0xFF) == 0xFF) {
                throw new AudioAssetInvalidException("Invalid ID3v2 version");
            }
            int size = 0;
            for (int i = 6; i < 10; i++) {
                int b = bytes[start + i] & 0xFF;
                if ((b & 0x80) != 0) {
                    throw new AudioAssetInvalidException("Invalid synchsafe size in ID3v2 header");
                }
                size = (size << 7) | b;
            }
            int footerBytes = (bytes[start + 5] & 0x10) != 0 ? ID3V2_HEADER_BYTES : 0;
            long endLong = (long) start + ID3V2_HEADER_BYTES + size + footerBytes;
            if (endLong < start + ID3V2_HEADER_BYTES || endLong >= bytes.length) {
                throw new AudioAssetInvalidException("Truncated ID3v2 tag with no MPEG audio frames");
            }
            start = (int) endLong;
        }
        return start;
    }

    private static int stripTrailingMetadata(byte[] bytes, int audioStart, int initialEnd) {
        int end = initialEnd;
        while (end > audioStart) {
            int nextEnd = stripOneTrailingTag(bytes, audioStart, end);
            if (nextEnd == end) {
                break;
            }
            end = nextEnd;
        }
        return end;
    }

    private static int stripOneTrailingTag(byte[] bytes, int audioStart, int end) {
        // ID3v1: trailing 128-byte "TAG" block must be stripped before JLayer Bitstream lookahead.
        if (end - audioStart >= ID3V1_TAG_BYTES && isId3v1TagAt(bytes, end - ID3V1_TAG_BYTES)) {
            return end - ID3V1_TAG_BYTES;
        }
        // APEv1/APEv2 footer (32 bytes at end): APETAGEX + version(1000|2000) + tagSize + flags(footer).
        if (end - audioStart >= APE_FOOTER_BYTES) {
            int footerStart = end - APE_FOOTER_BYTES;
            if (matchesTag8(bytes, footerStart, end, "APETAGEX")) {
                long version = readUint32LittleEndian(bytes, footerStart + 8);
                long tagSize = readUint32LittleEndian(bytes, footerStart + 12);
                long flags = readUint32LittleEndian(bytes, footerStart + 20);
                boolean isFooter = (flags & 0x2000_0000L) == 0L;
                boolean hasHeader = (flags & 0x8000_0000L) != 0L;
                long totalApeBytes = tagSize + (hasHeader ? APE_FOOTER_BYTES : 0L);
                if ((version == 1000L || version == 2000L)
                        && isFooter
                        && tagSize >= APE_FOOTER_BYTES
                        && totalApeBytes <= end - audioStart) {
                    int apeStart = end - (int) totalApeBytes;
                    if (!hasHeader || matchesTag8(bytes, apeStart, end, "APETAGEX")) {
                        return apeStart;
                    }
                }
            }
        }
        // Lyrics3 v2: 6-digit ASCII body length + "LYRICS200" (15 bytes total trailer), starting with "LYRICSBEGIN".
        if (end - audioStart >= 15 && matchesAscii(bytes, end - 9, end, "LYRICS200")) {
            int bodySize = parseSixDigitSize(bytes, end - 15);
            if (bodySize >= 11) {
                long totalLyricsBytes = (long) bodySize + 15L;
                if (totalLyricsBytes <= end - audioStart) {
                    int lyricsStart = end - (int) totalLyricsBytes;
                    if (matchesAscii(bytes, lyricsStart, end, "LYRICSBEGIN")) {
                        return lyricsStart;
                    }
                }
            }
        }
        // Lyrics3 v1: "LYRICSBEGIN" ... "LYRICSEND" within 5100 bytes.
        if (end - audioStart >= 20 && matchesAscii(bytes, end - 9, end, "LYRICSEND")) {
            int searchLimit = Math.max(audioStart, end - 9 - LYRICS3_V1_MAX_BODY_BYTES);
            for (int pos = end - 20; pos >= searchLimit; pos--) {
                if (matchesAscii(bytes, pos, end - 9, "LYRICSBEGIN")) {
                    return pos;
                }
            }
        }
        return end;
    }

    private static int stripTrailingNullPadding(byte[] bytes, int audioStart, int audioEnd) {
        if (audioEnd <= audioStart || bytes[audioEnd - 1] != 0) {
            return audioEnd;
        }
        // Walk MPEG frame lengths from audioStart so 0x00 bytes inside the final silent frame are never clipped.
        int pos = audioStart;
        while (pos + 4 <= audioEnd) {
            int frameBytes = mpegFrameBytes(bytes, pos);
            if (frameBytes < 4 || pos + frameBytes > audioEnd) {
                break;
            }
            pos += frameBytes;
        }
        if (pos > audioStart && pos < audioEnd && isAllZeroBytes(bytes, pos, audioEnd)) {
            return pos;
        }
        return audioEnd;
    }

    private static int mpegFrameBytes(byte[] bytes, int offset) {
        if (!isFrameSyncAt(bytes, offset) || offset + 4 > bytes.length) {
            return -1;
        }
        int h = readIntBigEndian(bytes, offset);
        int versionId = (h >>> 19) & 0x3;
        int layerBits = (h >>> 17) & 0x3;
        int bitrateIdx = (h >>> 12) & 0xF;
        int freqIdx = (h >>> 10) & 0x3;
        int padding = (h >>> 9) & 0x1;
        if (versionId == 1
                || layerBits == 0
                || bitrateIdx == 0
                || bitrateIdx == 15
                || freqIdx == 3) {
            return -1;
        }
        boolean mpeg1 = versionId == 3;
        int sampleRate = MPEG1_SAMPLE_RATES[freqIdx];
        if (versionId == 2) {
            sampleRate /= 2;
        } else if (versionId == 0) {
            sampleRate /= 4;
        }
        int row =
                switch (layerBits) {
                    case 3 -> mpeg1 ? 0 : 3;
                    case 2 -> mpeg1 ? 1 : 4;
                    case 1 -> mpeg1 ? 2 : 4;
                    default -> -1;
                };
        if (row < 0) {
            return -1;
        }
        int bitrateBps = MPEG_BITRATES_KBPS[row][bitrateIdx] * 1000;
        if (layerBits == 3) {
            return ((12 * bitrateBps) / sampleRate + padding) * 4;
        }
        int coeff = (layerBits == 1 && !mpeg1) ? 72 : 144;
        return (coeff * bitrateBps) / sampleRate + padding;
    }

    private static boolean isAllZeroBytes(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static long readUint32LittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }

    private static boolean matchesTag8(byte[] bytes, int offset, int limit, String tag) {
        return matchesAscii(bytes, offset, limit, tag);
    }

    private static boolean matchesAscii(byte[] bytes, int offset, int limit, String ascii) {
        if (offset < 0 || offset + ascii.length() > limit) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if ((bytes[offset + i] & 0xFF) != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int parseSixDigitSize(byte[] bytes, int offset) {
        int val = 0;
        for (int i = 0; i < 6; i++) {
            int ch = bytes[offset + i] & 0xFF;
            if (ch < '0' || ch > '9') {
                return -1;
            }
            val = val * 10 + (ch - '0');
        }
        return val;
    }

    private static boolean isId3v1TagAt(byte[] bytes, int offset) {
        return offset + 3 <= bytes.length
                && bytes[offset] == 'T'
                && bytes[offset + 1] == 'A'
                && bytes[offset + 2] == 'G';
    }

    private static boolean isFrameSyncAt(byte[] bytes, int offset) {
        return offset + 2 <= bytes.length
                && (bytes[offset] & 0xFF) == 0xFF
                && (bytes[offset + 1] & 0xE0) == 0xE0;
    }

    private static int readIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static boolean isXingInfoOrVbriFrame(
            byte[] bytes, int frameStart, int frameBytes, Header header) {
        int frameEnd = frameStart + frameBytes;
        int sideInfo =
                header.version() == Header.MPEG1
                        ? (header.mode() == Header.SINGLE_CHANNEL ? 17 : 32)
                        : (header.mode() == Header.SINGLE_CHANNEL ? 9 : 17);
        int xingOffset = frameStart + 4 + sideInfo;
        if (matchesTag(bytes, xingOffset, frameEnd, "Xing")
                || matchesTag(bytes, xingOffset, frameEnd, "Info")) {
            return true;
        }
        if (header.checksums()) {
            int crcXingOffset = xingOffset + 2;
            if (matchesTag(bytes, crcXingOffset, frameEnd, "Xing")
                    || matchesTag(bytes, crcXingOffset, frameEnd, "Info")) {
                return true;
            }
        }
        int vbriOffset = frameStart + 4 + 32;
        return matchesTag(bytes, vbriOffset, frameEnd, "VBRI");
    }

    private static boolean matchesTag(byte[] bytes, int offset, int limit, String tag) {
        if (offset < 0 || offset + 4 > limit) {
            return false;
        }
        byte[] expected = tag.getBytes(StandardCharsets.US_ASCII);
        return bytes[offset] == expected[0]
                && bytes[offset + 1] == expected[1]
                && bytes[offset + 2] == expected[2]
                && bytes[offset + 3] == expected[3];
    }

    private static double frameDurationMs(Header header) {
        int samplesPerFrame =
                switch (header.layer()) {
                    case 1 -> 384;
                    case 2 -> 1152;
                    case 3 -> header.version() == Header.MPEG1 ? 1152 : 576;
                    default -> throw new AudioAssetInvalidException(
                            "Unsupported MPEG layer: " + header.layer());
                };
        return (samplesPerFrame * 1000.0d) / header.frequency();
    }

    /** Thrown when an audio asset object is missing in storage. */
    public static final class AudioAssetMissingException extends RuntimeException {
        public AudioAssetMissingException(String message) {
            super(message);
        }
    }

    /** Thrown when an audio asset exists but is not a valid readable MP3 stream. */
    public static final class AudioAssetInvalidException extends RuntimeException {
        public AudioAssetInvalidException(String message) {
            super(message);
        }

        public AudioAssetInvalidException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
