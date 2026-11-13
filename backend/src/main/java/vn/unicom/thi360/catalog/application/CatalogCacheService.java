package vn.unicom.thi360.catalog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.catalog.api.CatalogDescendantNodeResponse;
import vn.unicom.thi360.catalog.api.CatalogOptionResponse;
import vn.unicom.thi360.catalog.api.CatalogTreeNodeResponse;

@Service
public class CatalogCacheService {

    public static final String OPT_KEY_PREFIX = "catalog:opt:";
    public static final String TREE_KEY_PREFIX = "catalog:tree:";
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheService.class);

    private static final TypeReference<List<CatalogOptionResponse>> OPTIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<CatalogTreeNodeResponse>> TREE_NODES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<CatalogDescendantNodeResponse>> DESCENDANTS_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public CatalogCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static String optionsKey(String groupCodeNormalized) {
        return OPT_KEY_PREFIX + groupCodeNormalized;
    }

    public static String treeKey(String groupCodeNormalized) {
        return TREE_KEY_PREFIX + groupCodeNormalized;
    }

    public List<CatalogOptionResponse> getOptions(String groupCodeNormalized) {
        return readValue(optionsKey(groupCodeNormalized), OPTIONS_TYPE);
    }

    public void putOptions(String groupCodeNormalized, List<CatalogOptionResponse> options) {
        writeValue(optionsKey(groupCodeNormalized), options);
    }

    public List<CatalogTreeNodeResponse> getTreeNodes(String groupCodeNormalized, String field) {
        return readHashValue(treeKey(groupCodeNormalized), field, TREE_NODES_TYPE);
    }

    public void putTreeNodes(
            String groupCodeNormalized, String field, List<CatalogTreeNodeResponse> nodes) {
        writeHashValue(treeKey(groupCodeNormalized), field, nodes);
    }

    public List<CatalogDescendantNodeResponse> getDescendants(String groupCodeNormalized, String field) {
        return readHashValue(treeKey(groupCodeNormalized), field, DESCENDANTS_TYPE);
    }

    public void putDescendants(
            String groupCodeNormalized, String field, List<CatalogDescendantNodeResponse> nodes) {
        writeHashValue(treeKey(groupCodeNormalized), field, nodes);
    }

    public void evictGroupAndDependents(UUID groupId, String... groupCodesNormalized) {
        Set<String> keysToDelete = new LinkedHashSet<>();
        if (groupCodesNormalized != null) {
            for (String code : groupCodesNormalized) {
                if (code != null && !code.isBlank()) {
                    keysToDelete.add(optionsKey(code));
                    keysToDelete.add(treeKey(code));
                }
            }
        }
        if (groupId != null) {
            List<String> referencingTreeCodes = jdbcTemplate.query(
                    """
                    SELECT DISTINCT g.code_normalized
                    FROM catalog.catalog_group_ref r
                    JOIN catalog.catalog_group g ON g.id = r.group_id
                    WHERE r.ref_group_id = ?
                    """,
                    (rs, rowNum) -> rs.getString(1),
                    groupId);
            for (String treeCode : referencingTreeCodes) {
                if (treeCode != null && !treeCode.isBlank()) {
                    keysToDelete.add(treeKey(treeCode));
                }
            }
        }
        evictKeysAfterCommit(keysToDelete);
    }

    private void evictKeysAfterCommit(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> copy = List.copyOf(keys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteKeys(copy);
                }
            });
        } else {
            deleteKeys(copy);
        }
    }

    private void deleteKeys(List<String> keys) {
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("keys", String.join(",", keys))
                    .log("Failed to evict catalog cache keys from Redis");
        }
    }

    private <T> T readValue(String key, TypeReference<T> typeRef) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, typeRef);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("key", key)
                    .log("Failed to read catalog cache from Redis; falling back to database");
            return null;
        }
    }

    private void writeValue(String key, Object value) {
        try {
            String encoded = objectMapper.writeValueAsString(Objects.requireNonNull(value));
            redisTemplate.opsForValue().set(key, encoded, CACHE_TTL);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("key", key)
                    .log("Failed to write catalog cache to Redis");
        }
    }

    private <T> T readHashValue(String key, String field, TypeReference<T> typeRef) {
        try {
            Object raw = redisTemplate.opsForHash().get(key, field);
            if (!(raw instanceof String str) || str.isBlank()) {
                return null;
            }
            return objectMapper.readValue(str, typeRef);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("key", key)
                    .addKeyValue("field", field)
                    .log("Failed to read catalog tree cache from Redis; falling back to database");
            return null;
        }
    }

    private void writeHashValue(String key, String field, Object value) {
        try {
            String encoded = objectMapper.writeValueAsString(Objects.requireNonNull(value));
            redisTemplate.opsForHash().put(key, field, encoded);
            redisTemplate.expire(key, CACHE_TTL);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("key", key)
                    .addKeyValue("field", field)
                    .log("Failed to write catalog tree cache to Redis");
        }
    }
}
