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
     * Bitstream}, skipping ID3v2/ID3v1 tags, rejecting truncated frames, and excluding any leading
     * Xing/Info/VBRI metadata header frame when audio frames follow.
     */
    public static long measureMp3DurationMs(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new AudioAssetInvalidException("Audio bytes empty or too short for MPEG header");
        }

        int audioStart = skipId3v2(bytes);
        while (audioStart < bytes.length && bytes[audioStart] == 0) {
            audioStart++;
        }
        int audioEnd = bytes.length;
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
                if (audioEnd - cursor == ID3V1_TAG_BYTES && isId3v1TagAt(bytes, cursor)) {
                    break;
                }
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

    private static int skipId3v2(byte[] bytes) {
        if (bytes.length < ID3V2_HEADER_BYTES
                || bytes[0] != 'I'
                || bytes[1] != 'D'
                || bytes[2] != '3') {
            return 0;
        }
        if ((bytes[3] & 0xFF) == 0xFF || (bytes[4] & 0xFF) == 0xFF) {
            throw new AudioAssetInvalidException("Invalid ID3v2 version");
        }
        int size = 0;
        for (int i = 6; i < 10; i++) {
            int b = bytes[i] & 0xFF;
            if ((b & 0x80) != 0) {
                throw new AudioAssetInvalidException("Invalid synchsafe size in ID3v2 header");
            }
            size = (size << 7) | b;
        }
        int footerBytes = (bytes[5] & 0x10) != 0 ? ID3V2_HEADER_BYTES : 0;
        int end = ID3V2_HEADER_BYTES + size + footerBytes;
        if (end < ID3V2_HEADER_BYTES || end >= bytes.length) {
            throw new AudioAssetInvalidException("Truncated ID3v2 tag with no MPEG audio frames");
        }
        return end;
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
