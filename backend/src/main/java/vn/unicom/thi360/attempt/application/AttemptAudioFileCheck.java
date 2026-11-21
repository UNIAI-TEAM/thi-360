package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledItem;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledPart;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.Attachment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;

/**
 * Verifies audio attachments for an assembled paper before attempt start Tx2, skipping storage
 * entirely when no selected root question declares attachments (BS §17.4, AC-9, AC-14, AC-15).
 */
@Service
public class AttemptAudioFileCheck {

    private final AudioFileProbe audioFileProbe;
    private final AudioDurationReader audioDurationReader;

    public AttemptAudioFileCheck(
            AudioFileProbe audioFileProbe, AudioDurationReader audioDurationReader) {
        this.audioFileProbe = Objects.requireNonNull(audioFileProbe);
        this.audioDurationReader = Objects.requireNonNull(audioDurationReader);
    }

    /** Distinct audio asset verified and measured for one attempt paper. */
    public record CheckedAudioAsset(UUID assetId, String partCode, long durationMs) {}

    /** Result of pre-persist audio verification for an attempt paper. */
    public record CheckResult(List<CheckedAudioAsset> assets) {
        public static final CheckResult EMPTY = new CheckResult(List.of());

        public CheckResult {
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }

    /**
     * Checks all audio attachments referenced by assembled root questions outside any DB
     * transaction, propagating storage connection failures and throwing {@link
     * AudioFileCheckException} on missing or unreadable audio assets.
     */
    public CheckResult checkAndMeasure(
            UUID examId,
            String examCode,
            Map<String, AssembledPart> assembledByPartCode,
            Map<VersionKey, Version> versionMap) {
        if (assembledByPartCode == null || assembledByPartCode.isEmpty() || versionMap == null) {
            return CheckResult.EMPTY;
        }

        record RootRef(String partCode, VersionKey key) {}
        record AudioCandidate(String partCode, UUID questionId, UUID assetId) {}
        List<RootRef> candidateRoots = new java.util.ArrayList<>();
        Set<VersionKey> keysWithAttachments = new LinkedHashSet<>();

        for (Map.Entry<String, AssembledPart> entry : assembledByPartCode.entrySet()) {
            String partCode = entry.getKey();
            for (AssembledItem item : entry.getValue().items()) {
                if (item.parentIndex() != null) {
                    continue;
                }
                VersionKey key = new VersionKey(item.questionId(), item.questionVersionNo());
                Version version = versionMap.get(key);
                if (version == null) {
                    continue;
                }
                JsonNode attachmentIds = version.snapshot().path("attachmentIds");
                if (attachmentIds.isArray() && !attachmentIds.isEmpty()) {
                    keysWithAttachments.add(key);
                    candidateRoots.add(new RootRef(partCode, key));
                }
            }
        }

        if (keysWithAttachments.isEmpty()) {
            return CheckResult.EMPTY;
        }

        // Step 1: Metadata-only query (verifyStorage = false) so non-audio exams never touch object storage.
        Map<VersionKey, List<Attachment>> metadata =
                audioFileProbe.probe(examId, keysWithAttachments, false);
        List<RootRef> audioRoots = new java.util.ArrayList<>();
        Set<VersionKey> audioKeys = new LinkedHashSet<>();
        for (RootRef ref : candidateRoots) {
            List<Attachment> attachments = metadata.getOrDefault(ref.key(), List.of());
            boolean hasAudio = attachments.stream().anyMatch(att -> isAudioMime(att.mime()));
            if (hasAudio) {
                audioKeys.add(ref.key());
                audioRoots.add(ref);
            }
        }
        if (audioKeys.isEmpty()) {
            return CheckResult.EMPTY;
        }

        // Step 2: Verify storage existence only for keys carrying at least one audio attachment.
        Map<VersionKey, List<Attachment>> probed = audioFileProbe.probe(examId, audioKeys, true);

        // Step 3 (R1-21): Fail fast on any missing audio attachment before downloading/parsing uncached MP3s.
        List<AudioCandidate> audioCandidates = new java.util.ArrayList<>();
        for (RootRef ref : audioRoots) {
            List<Attachment> attachments = probed.getOrDefault(ref.key(), List.of());
            for (Attachment att : attachments) {
                if (!isAudioMime(att.mime())) {
                    continue;
                }
                if (!Boolean.TRUE.equals(att.readable())) {
                    throw new AudioFileCheckException(
                            AbortReason.AUDIO_ASSET_MISSING,
                            examCode,
                            ref.key().questionId(),
                            att.id(),
                            "Audio asset missing in storage");
                }
                audioCandidates.add(
                        new AudioCandidate(ref.partCode(), ref.key().questionId(), att.id()));
            }
        }

        // Step 4: Measure distinct readable audio durations (cached in attempt.audio_asset_duration).
        Map<UUID, CheckedAudioAsset> checkedByAssetId = new LinkedHashMap<>();
        for (AudioCandidate candidate : audioCandidates) {
            if (checkedByAssetId.containsKey(candidate.assetId())) {
                continue;
            }
            try {
                long durationMs = audioDurationReader.resolveDurationMs(candidate.assetId());
                checkedByAssetId.put(
                        candidate.assetId(),
                        new CheckedAudioAsset(
                                candidate.assetId(), candidate.partCode(), durationMs));
            } catch (AudioDurationReader.AudioAssetMissingException ex) {
                throw new AudioFileCheckException(
                        AbortReason.AUDIO_ASSET_MISSING,
                        examCode,
                        candidate.questionId(),
                        candidate.assetId(),
                        ex.getMessage(),
                        ex);
            } catch (AudioDurationReader.AudioAssetInvalidException ex) {
                throw new AudioFileCheckException(
                        AbortReason.AUDIO_ASSET_INVALID,
                        examCode,
                        candidate.questionId(),
                        candidate.assetId(),
                        ex.getMessage(),
                        ex);
            }
        }

        return checkedByAssetId.isEmpty()
                ? CheckResult.EMPTY
                : new CheckResult(List.copyOf(checkedByAssetId.values()));
    }

    private static boolean isAudioMime(String mime) {
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    /** Operational failure detailing which exam, question, and audio asset failed pre-start check. */
    public static final class AudioFileCheckException extends RuntimeException {

        private final AbortReason abortReason;
        private final String examCode;
        private final UUID questionId;
        private final UUID assetId;

        public AudioFileCheckException(
                AbortReason abortReason,
                String examCode,
                UUID questionId,
                UUID assetId,
                String message) {
            super(message);
            this.abortReason = Objects.requireNonNull(abortReason);
            this.examCode = examCode;
            this.questionId = Objects.requireNonNull(questionId);
            this.assetId = Objects.requireNonNull(assetId);
        }

        public AudioFileCheckException(
                AbortReason abortReason,
                String examCode,
                UUID questionId,
                UUID assetId,
                String message,
                Throwable cause) {
            super(message, cause);
            this.abortReason = Objects.requireNonNull(abortReason);
            this.examCode = examCode;
            this.questionId = Objects.requireNonNull(questionId);
            this.assetId = Objects.requireNonNull(assetId);
        }

        public AbortReason abortReason() {
            return abortReason;
        }

        public String examCode() {
            return examCode;
        }

        public UUID questionId() {
            return questionId;
        }

        public UUID assetId() {
            return assetId;
        }
    }
}
