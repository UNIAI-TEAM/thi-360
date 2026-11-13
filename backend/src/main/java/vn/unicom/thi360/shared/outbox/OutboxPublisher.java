package vn.unicom.thi360.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.shared.ids.Ids;

@Service
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxPublisher(
            OutboxEventRepository repository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publish(String topic, Object payload) {
        return publish(Ids.newId(), topic, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publish(UUID id, String topic, Object payload) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        String json = serializePayload(payload);
        OutboxEvent event = new OutboxEvent(id, topic, json, Instant.now(clock));
        return repository.save(event);
    }

    private String serializePayload(Object payload) {
        if (payload instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize outbox payload", ex);
        }
    }
}
