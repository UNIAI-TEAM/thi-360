package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    public record Dropped(QuestionPick pick, String subjectCode, String sectionId) {}

    public record PartSnapshot(List<GroupWitness> groups, List<QuestionSourcePort.Candidate> candidates,
                               List<QuestionSourcePort.Version> versions, List<Dropped> droppedQuestions,
                               PreviewPart presentation, Map<String, List<Integer>> allocations) {}

    public record Snapshot(UUID examId, Integer version, long seed, Map<String, PartSnapshot> parts) {}

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
            Snapshot snapshot = mapper.treeToValue(tree, Snapshot.class);
            if (snapshot.parts() == null || snapshot.parts().values().stream()
                    .anyMatch(part -> part.presentation() == null || part.allocations() == null)) return null;
            return snapshot;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot deserialize preview snapshot", ex);
        }
    }
}
