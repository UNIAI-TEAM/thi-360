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

class PreviewSnapshotStoreTest {
    @Test
    @SuppressWarnings("unchecked")
    void storesSelectionsForThirtyMinutesAndRestoresThem() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        PreviewSnapshotStore store = new PreviewSnapshotStore(redis, new ObjectMapper());
        var snapshot = new PreviewSnapshotStore.Snapshot(UUID.randomUUID(), 2, 42L,
                Map.of("part-1", List.of(new GroupWitness("group", List.of(new QuestionPick("q", 3))))),
                List.of(new QuestionPick("dropped", 1)));

        UUID id = store.save(snapshot);
        var serialized = ArgumentCaptor.forClass(String.class);
        verify(values).set(org.mockito.ArgumentMatchers.eq("exam:preview:" + id), serialized.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30)));
        when(values.get("exam:preview:" + id)).thenReturn(serialized.getValue());

        assertThat(store.find(id)).isEqualTo(snapshot);
        assertThat(store.find(UUID.randomUUID())).isNull();
    }
}
