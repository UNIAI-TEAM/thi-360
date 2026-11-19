package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.api.ExamPreviewController.PreviewPart;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Short-lived solver selections used when regenerating only some parts of a preview. */
@Component
public class PreviewSnapshotStore {
    private static final String KEY_PREFIX = "exam:preview:";
    private static final Duration TTL = Duration.ofMinutes(30);
    /** Reads the remaining life and raises it to at least {@code ARGV[1]} ms in one step; -1 when the key is gone. */
    private static final DefaultRedisScript<Long> EXTEND = new DefaultRedisScript<>("""
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then return -1 end
            local want = tonumber(ARGV[1])
            if want > ttl then
                redis.call('PEXPIRE', KEYS[1], want)
                return want
            end
            return ttl
            """, Long.class);

    public record Dropped(QuestionPick pick, String subjectCode, String sectionId) {}

    public record PartSnapshot(List<GroupWitness> groups, List<QuestionSourcePort.Candidate> candidates,
                               List<QuestionSourcePort.Version> versions, List<Dropped> droppedQuestions,
                               PreviewPart presentation, Map<String, List<Integer>> allocations) {}

    /**
     * {@code kindCode}, {@code timing} and {@code durationMinutes} are captured at generation (BS 9) so a later
     * trial extension never rereads the draft: {@code durationMinutes} is the exam duration for {@code SHARED} and
     * the sum of the generated path's parts for {@code PER_PART}. {@code kindCode} is null for an exam without a
     * kind and is always written; a snapshot saved before these fields existed lacks the three keys.
     */
    public record Snapshot(UUID examId, Integer version, long seed, Map<String, PartSnapshot> parts,
                           @JsonInclude(JsonInclude.Include.ALWAYS) String kindCode, String timing,
                           Integer durationMinutes) {}

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public PreviewSnapshotStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public UUID save(Snapshot snapshot) {
        UUID id = UUID.randomUUID();
        try {
            redis.opsForValue().set(KEY_PREFIX + id, mapper.writeValueAsString(snapshot), TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize preview snapshot", ex);
        }
        return id;
    }

    public Snapshot find(UUID id) {
        String value = redis.opsForValue().get(KEY_PREFIX + id);
        if (value == null) return null;
        try {
            JsonNode tree = mapper.readTree(value);
            for (JsonNode part : tree.path("parts")) {
                for (JsonNode group : part.path("groups")) {
                    for (JsonNode pick : group.path("picks")) {
                        if (!pick.path("topicAssignments").isArray()) return null;
                    }
                }
                for (JsonNode dropped : part.path("droppedQuestions")) {
                    if (!dropped.path("pick").path("topicAssignments").isArray()) return null;
                }
            }
            // Snapshots saved before BS 9 lack these fields; kindCode may be an explicit null (exam without a kind).
            if (!tree.has("kindCode") || !tree.hasNonNull("timing") || !tree.hasNonNull("durationMinutes")) return null;
            Snapshot snapshot = mapper.treeToValue(tree, Snapshot.class);
            if (snapshot.parts() == null || snapshot.parts().values().stream()
                    .anyMatch(part -> part.presentation() == null || part.allocations() == null)) return null;
            return snapshot;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot deserialize preview snapshot", ex);
        }
    }

    /**
     * Raises the remaining life of a stored preview to at least {@code minimum}, never lowers it. The read of the
     * current life and the write are one Redis script, so concurrent calls cannot overwrite a longer expiry with a
     * shorter one. Returns the life now left, or null when the preview is gone.
     */
    public Duration extendAtLeast(UUID id, Duration minimum) {
        Long millis = redis.execute(EXTEND, List.of(KEY_PREFIX + id), Long.toString(minimum.toMillis()));
        return millis == null || millis < 0 ? null : Duration.ofMillis(millis);
    }
}
