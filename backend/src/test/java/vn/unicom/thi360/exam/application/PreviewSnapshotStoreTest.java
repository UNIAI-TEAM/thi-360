package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.TopicAssignment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class PreviewSnapshotStoreTest {
    @Test
    @SuppressWarnings("unchecked")
    void storesSelectionsForThirtyMinutesAndRestoresThem() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        PreviewSnapshotStore store = new PreviewSnapshotStore(redis, new ObjectMapper());
        UUID questionId = UUID.randomUUID();
        var unit = new QuestionSourcePort.Unit(questionId, "ref", QuestionType.SINGLE_CHOICE, "G12",
                "EASY", List.of(), List.of(), java.math.BigDecimal.ONE);
        var candidate = new QuestionSourcePort.Candidate(questionId, 3, null, List.of(unit), null, null);
        var version = new QuestionSourcePort.Version(new QuestionSourcePort.VersionKey(questionId, 3),
                new ObjectMapper().createObjectNode().put("content", "snapshot"), "CODE", Map.of());
        var part = new PreviewSnapshotStore.PartSnapshot(
                List.of(new GroupWitness("group", List.of(new QuestionPick(questionId.toString(), 3,
                        List.of(new TopicAssignment("SECTION", "section", "quota", 0)))))),
                List.of(candidate), List.of(version),
                List.of(new PreviewSnapshotStore.Dropped(new QuestionPick("dropped", 1), "PHYSICS", "physics-section")),
                new vn.unicom.thi360.exam.api.ExamPreviewController.PreviewPart(
                        "part-1", "Part", 1, null, 1, 1, java.math.BigDecimal.ONE, null, List.of()),
                Map.of("science", List.of(17, 16)));
        var snapshot = new PreviewSnapshotStore.Snapshot(UUID.randomUUID(), 2, 42L, Map.of("part-1", part));

        UUID id = store.save(snapshot);
        var serialized = ArgumentCaptor.forClass(String.class);
        verify(values).set(org.mockito.ArgumentMatchers.eq("exam:preview:" + id), serialized.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30)));
        when(values.get("exam:preview:" + id)).thenReturn(serialized.getValue());

        assertThat(store.find(id)).isEqualTo(snapshot);
        var legacy = new ObjectMapper().valueToTree(snapshot);
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy.path("parts").path("part-1"))
                .remove(List.of("presentation", "allocations"));
        UUID legacyId = UUID.randomUUID();
        when(values.get("exam:preview:" + legacyId)).thenReturn(legacy.toString());
        assertThat(store.find(legacyId)).isNull();
        var withoutAssignments = new ObjectMapper().valueToTree(snapshot);
        ((com.fasterxml.jackson.databind.node.ObjectNode) withoutAssignments.path("parts").path("part-1")
                .path("groups").get(0).path("picks").get(0)).remove("topicAssignments");
        UUID oldId = UUID.randomUUID();
        when(values.get("exam:preview:" + oldId)).thenReturn(withoutAssignments.toString());
        assertThat(store.find(oldId)).isNull();
        var withoutDroppedAssignments = new ObjectMapper().valueToTree(snapshot);
        ((com.fasterxml.jackson.databind.node.ObjectNode) withoutDroppedAssignments.path("parts").path("part-1")
                .path("droppedQuestions").get(0).path("pick")).remove("topicAssignments");
        UUID oldDroppedId = UUID.randomUUID();
        when(values.get("exam:preview:" + oldDroppedId)).thenReturn(withoutDroppedAssignments.toString());
        assertThat(store.find(oldDroppedId)).isNull();
        assertThat(store.find(UUID.randomUUID())).isNull();
    }
}
