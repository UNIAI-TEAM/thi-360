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
    public <T> T executeOnce(String key, String scope, Class<T> resultType, Supplier<T> supplier) {
        Objects.requireNonNull(resultType, "resultType must not be null");
        return executeOnceWithJavaType(key, scope, objectMapper.constructType(resultType), supplier);
    }

    @Transactional
    public <T> T executeOnce(String key, String scope, TypeReference<T> resultType, Supplier<T> supplier) {
        Objects.requireNonNull(resultType, "resultType must not be null");
        return executeOnceWithJavaType(key, scope, objectMapper.constructType(resultType.getType()), supplier);
    }

    private <T> T executeOnceWithJavaType(
            String key, String scope, JavaType javaType, Supplier<T> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(javaType, "javaType must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        acquireAdvisoryXactLock(scope, key);

        IdempotencyKeyId id = new IdempotencyKeyId(scope, key);
        Optional<IdempotencyKey> existing = repository.findById(id);
        if (existing.isPresent()) {
            return deserializeJson(existing.get().getResponse(), javaType);
        }

        T result = supplier.get();
        String json = serializeJson(result);
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

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize idempotency response", ex);
        }
    }

    private <T> T deserializeJson(String json, JavaType javaType) {
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize idempotency response", ex);
        }
    }
}
