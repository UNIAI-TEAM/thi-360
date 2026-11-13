package vn.unicom.thi360.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyKeyRepository repository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public <T> T executeOnce(String key, String scope, Supplier<T> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        acquireAdvisoryXactLock(scope, key);

        IdempotencyKeyId id = new IdempotencyKeyId(scope, key);
        Optional<IdempotencyKey> existing = repository.findById(id);
        if (existing.isPresent()) {
            return (T) deserializeStoredEnvelope(existing.get().getResponse());
        }

        T result = supplier.get();
        String json = serializeStoredEnvelope(result);
        repository.saveAndFlush(new IdempotencyKey(id, json, Instant.now(clock)));
        return result;
    }

    @Transactional
    public <T> T executeOnce(String key, String scope, Class<T> resultType, Supplier<T> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        acquireAdvisoryXactLock(scope, key);

        IdempotencyKeyId id = new IdempotencyKeyId(scope, key);
        Optional<IdempotencyKey> existing = repository.findById(id);
        if (existing.isPresent()) {
            return deserializeTyped(existing.get().getResponse(), objectMapper.constructType(resultType));
        }

        T result = supplier.get();
        String json = serializeStoredEnvelope(result);
        repository.saveAndFlush(new IdempotencyKey(id, json, Instant.now(clock)));
        return result;
    }

    @Transactional
    public <T> T executeOnce(String key, String scope, TypeReference<T> resultType, Supplier<T> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        acquireAdvisoryXactLock(scope, key);

        IdempotencyKeyId id = new IdempotencyKeyId(scope, key);
        Optional<IdempotencyKey> existing = repository.findById(id);
        if (existing.isPresent()) {
            return deserializeTyped(existing.get().getResponse(), objectMapper.constructType(resultType.getType()));
        }

        T result = supplier.get();
        String json = serializeStoredEnvelope(result);
        repository.saveAndFlush(new IdempotencyKey(id, json, Instant.now(clock)));
        return result;
    }

    private void acquireAdvisoryXactLock(String scope, String key) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                Object.class,
                scope,
                key);
    }

    private String serializeStoredEnvelope(Object value) {
        try {
            String typeName = value == null ? null : value.getClass().getName();
            StoredEnvelope envelope = new StoredEnvelope(typeName, objectMapper.valueToTree(value));
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize idempotency response", ex);
        }
    }

    private Object deserializeStoredEnvelope(String json) {
        try {
            StoredEnvelope envelope = objectMapper.readValue(json, StoredEnvelope.class);
            if (envelope == null || envelope.value() == null || envelope.value().isNull()) {
                return null;
            }
            if (envelope.type() != null) {
                try {
                    Class<?> clazz = Class.forName(envelope.type());
                    return objectMapper.treeToValue(envelope.value(), clazz);
                } catch (ClassNotFoundException ignored) {
                    // Fallback to generic conversion
                }
            }
            return objectMapper.treeToValue(envelope.value(), Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize idempotency response", ex);
        }
    }

    private <T> T deserializeTyped(String json, JavaType javaType) {
        try {
            StoredEnvelope envelope = objectMapper.readValue(json, StoredEnvelope.class);
            if (envelope != null && envelope.value() != null) {
                if (envelope.value().isNull()) {
                    return null;
                }
                return objectMapper.convertValue(envelope.value(), javaType);
            }
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize idempotency response", ex);
        }
    }

    private record StoredEnvelope(String type, com.fasterxml.jackson.databind.JsonNode value) {
    }
}
