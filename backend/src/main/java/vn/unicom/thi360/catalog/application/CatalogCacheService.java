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
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
    public static final String GEN_KEY_PREFIX = "catalog:gen:";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration GEN_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheService.class);

    private static final TypeReference<List<CatalogOptionResponse>> OPTIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<CatalogTreeNodeResponse>> TREE_NODES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<CatalogDescendantNodeResponse>> DESCENDANTS_TYPE = new TypeReference<>() {};

    private static final DefaultRedisScript<Long> EVICT_GROUP_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            redis.call('DEL', KEYS[2], KEYS[3])
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> PUT_VALUE_IF_GEN_SCRIPT = new DefaultRedisScript<>(
            """
            local cur = redis.call('GET', KEYS[1])
            if cur == false then cur = '0' end
            if cur ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[3]))
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> PUT_HASH_IF_GEN_SCRIPT = new DefaultRedisScript<>(
            """
            local cur = redis.call('GET', KEYS[1])
            if cur == false then cur = '0' end
            if cur ~= ARGV[1] then return 0 end
            redis.call('HSET', KEYS[2], ARGV[2], ARGV[3])
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4]))
            return 1
            """,
            Long.class);

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

    public static String genKey(String groupCodeNormalized) {
        return GEN_KEY_PREFIX + groupCodeNormalized;
    }

    public Long readGeneration(String groupCodeNormalized) {
        if (groupCodeNormalized == null || groupCodeNormalized.isBlank()) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(genKey(groupCodeNormalized));
            if (raw == null || raw.isBlank()) {
                return 0L;
            }
            return Long.parseLong(raw);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("group", groupCodeNormalized)
                    .log("Failed to read catalog cache generation from Redis");
            return null;
        }
    }

    public List<CatalogOptionResponse> getOptions(String groupCodeNormalized) {
        return readValue(optionsKey(groupCodeNormalized), OPTIONS_TYPE);
    }

    public void putOptions(
            String groupCodeNormalized, Long expectedGen, List<CatalogOptionResponse> options) {
        if (expectedGen == null) {
            return;
        }
        writeValueIfGen(
                genKey(groupCodeNormalized),
                optionsKey(groupCodeNormalized),
                expectedGen,
                options);
    }

    public List<CatalogTreeNodeResponse> getTreeNodes(String groupCodeNormalized, String field) {
        return readHashValue(treeKey(groupCodeNormalized), field, TREE_NODES_TYPE);
    }

    public void putTreeNodes(
            String groupCodeNormalized,
            String field,
            Long expectedGen,
            List<CatalogTreeNodeResponse> nodes) {
        if (expectedGen == null) {
            return;
        }
        writeHashValueIfGen(
                genKey(groupCodeNormalized),
                treeKey(groupCodeNormalized),
                field,
                expectedGen,
                nodes);
    }

    public List<CatalogDescendantNodeResponse> getDescendants(String groupCodeNormalized, String field) {
        return readHashValue(treeKey(groupCodeNormalized), field, DESCENDANTS_TYPE);
    }

    public void putDescendants(
            String groupCodeNormalized,
            String field,
            Long expectedGen,
            List<CatalogDescendantNodeResponse> nodes) {
        if (expectedGen == null) {
            return;
        }
        writeHashValueIfGen(
                genKey(groupCodeNormalized),
                treeKey(groupCodeNormalized),
                field,
                expectedGen,
                nodes);
    }

    public void evictGroupAndDependents(UUID groupId, String... groupCodesNormalized) {
        Set<String> codesToEvict = new LinkedHashSet<>();
        if (groupCodesNormalized != null) {
            for (String code : groupCodesNormalized) {
                if (code != null && !code.isBlank()) {
                    codesToEvict.add(code);
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
                    codesToEvict.add(treeCode);
                }
            }
        }
        evictCodesAfterCommit(codesToEvict);
    }

    private void evictCodesAfterCommit(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        List<String> copy = List.copyOf(codes);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictCodes(copy);
                }
            });
        } else {
            evictCodes(copy);
        }
    }

    private void evictCodes(List<String> codes) {
        String genTtlSeconds = String.valueOf(GEN_TTL.toSeconds());
        for (String code : codes) {
            try {
                redisTemplate.execute(
                        EVICT_GROUP_SCRIPT,
                        List.of(genKey(code), optionsKey(code), treeKey(code)),
                        genTtlSeconds);
            } catch (RuntimeException ex) {
                log.atWarn()
                        .setCause(ex)
                        .addKeyValue("group", code)
                        .log("Failed to evict catalog cache keys from Redis");
            }
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

    private void writeValueIfGen(String genKey, String key, long expectedGen, Object value) {
        try {
            String encoded = objectMapper.writeValueAsString(Objects.requireNonNull(value));
            redisTemplate.execute(
                    PUT_VALUE_IF_GEN_SCRIPT,
                    List.of(genKey, key),
                    String.valueOf(expectedGen),
                    encoded,
                    String.valueOf(CACHE_TTL.toSeconds()));
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

    private void writeHashValueIfGen(
            String genKey, String key, String field, long expectedGen, Object value) {
        try {
            String encoded = objectMapper.writeValueAsString(Objects.requireNonNull(value));
            redisTemplate.execute(
                    PUT_HASH_IF_GEN_SCRIPT,
                    List.of(genKey, key),
                    String.valueOf(expectedGen),
                    field,
                    encoded,
                    String.valueOf(CACHE_TTL.toSeconds()));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("key", key)
                    .addKeyValue("field", field)
                    .log("Failed to write catalog tree cache to Redis");
        }
    }
}
