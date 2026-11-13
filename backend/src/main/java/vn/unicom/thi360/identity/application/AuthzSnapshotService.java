package vn.unicom.thi360.identity.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.shared.outbox.OutboxEvent;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.PermissionCatalog;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.SessionAuthzResolver;

@Service
public class AuthzSnapshotService implements SessionAuthzResolver, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AuthzSnapshotService.class);

    public static final String KEY_PREFIX = SessionAuthzResolver.AUTHZ_KEY_PREFIX;
    public static final String VER_PREFIX = "authz-ver:";
    public static final String OUTBOX_TOPIC_EVICT = "authz.evict";
    public static final Duration SNAPSHOT_TTL = Duration.ofDays(30);
    public static final Duration VERSION_TTL = Duration.ofDays(60);

    private static final String BUILD_SNAPSHOT_SQL =
            """
            WITH active_roles AS (
                SELECT r.id, r.access
                FROM identity.user_role ur
                JOIN identity.role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND r.status = 1
            ),
            has_manage AS (
                SELECT EXISTS (SELECT 1 FROM active_roles WHERE access = 1) AS is_manage
            ),
            effective_bits AS (
                SELECT p.bit_index
                FROM active_roles ar
                JOIN identity.role_permission rp ON rp.role_id = ar.id
                JOIN identity.permission p ON p.id = rp.permission_id
                UNION
                SELECT p.bit_index
                FROM identity.user_permission up
                JOIN identity.permission p ON p.id = up.permission_id
                CROSS JOIN has_manage hm
                WHERE up.user_id = ?
                  AND hm.is_manage = true
            )
            SELECT (SELECT is_manage FROM has_manage) AS is_manage,
                   eb.bit_index
            FROM (SELECT NULL::int AS bit_index) dummy
            LEFT JOIN effective_bits eb ON true
            """;

    private static final String FIND_USER_IDS_BY_ROLE_SQL =
            "SELECT user_id FROM identity.user_role WHERE role_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final PermissionCatalog permissionCatalog;
    private final OutboxPublisher outboxPublisher;
    private final OutboxRelay outboxRelay;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;
    private final Cache<SnapshotCacheKey, PermissionSet> decodedCache;

    public AuthzSnapshotService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            PermissionCatalog permissionCatalog,
            OutboxPublisher outboxPublisher,
            OutboxRelay outboxRelay,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.permissionCatalog = Objects.requireNonNull(permissionCatalog, "permissionCatalog must not be null");
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher, "outboxPublisher must not be null");
        this.outboxRelay = Objects.requireNonNull(outboxRelay, "outboxRelay must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.decodedCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
    }

    @Override
    public void afterSingletonsInstantiated() {
        outboxRelay.register(OUTBOX_TOPIC_EVICT, this::handleEvictOutbox);
    }

    @Override
    public AuthzSnapshot resolve(UUID userId, Realm realm) {
        return load(userId, realm);
    }

    @Override
    public AuthzSnapshot resolveFromCached(UUID userId, Realm realm, String rawCachedSnapshot) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(realm, "realm must not be null");
        if (realm == Realm.MASTER) {
            return platformAdminSnapshot();
        }
        AuthzSnapshot parsed = parseSnapshot(userId, rawCachedSnapshot);
        if (parsed != null) {
            return parsed;
        }
        return build(userId);
    }

    public AuthzSnapshot load(UUID userId) {
        return load(userId, Realm.APP);
    }

    public AuthzSnapshot load(UUID userId, Realm realm) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(realm, "realm must not be null");
        if (realm == Realm.MASTER) {
            return platformAdminSnapshot();
        }
        String raw = null;
        try {
            raw = redisTemplate.opsForValue().get(cacheKey(userId));
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("userId", userId.toString())
                    .log("Failed to read authz snapshot from Redis; rebuilding from database");
        }
        AuthzSnapshot parsed = parseSnapshot(userId, raw);
        if (parsed != null) {
            return parsed;
        }
        return build(userId);
    }

    public AuthzSnapshot build(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        DbAuthzResult dbResult = queryEffectiveAuthz(userId);
        PermissionSet permissions = permissionCatalog.decode(dbResult.bits());
        long version = nextVersion(userId);
        String encoded = encodeSnapshot(dbResult.access(), version, permissions);

        try {
            redisTemplate.opsForValue().set(cacheKey(userId), encoded, SNAPSHOT_TTL);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("userId", userId.toString())
                    .log("Failed to store authz snapshot in Redis");
        }

        decodedCache.put(new SnapshotCacheKey(userId, version), permissions);
        return new AuthzSnapshot(dbResult.access(), permissions, version);
    }

    public void evict(UUID userId) {
        if (userId == null) {
            return;
        }
        evictAll(List.of(userId));
    }

    public void evictByRole(UUID roleId) {
        if (roleId == null) {
            return;
        }
        List<UUID> userIds = jdbcTemplate.query(
                FIND_USER_IDS_BY_ROLE_SQL,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                roleId);
        evictAll(userIds);
    }

    public void evictAll(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        List<UUID> distinctIds = List.copyOf(unique);
        invalidateLocalCache(distinctIds);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidateLocalCache(distinctIds);
                    performRedisEvictWithOutboxFallback(distinctIds);
                }
            });
        } else {
            performRedisEvictWithOutboxFallback(distinctIds);
        }
    }

    private AuthzSnapshot platformAdminSnapshot() {
        return new AuthzSnapshot(Access.MANAGE, PermissionSet.all(permissionCatalog.allCodes()), 0L);
    }

    private DbAuthzResult queryEffectiveAuthz(UUID userId) {
        BitSet bits = new BitSet();
        boolean[] isManage = {false};
        jdbcTemplate.query(
                BUILD_SNAPSHOT_SQL,
                rs -> {
                    if (rs.getBoolean("is_manage")) {
                        isManage[0] = true;
                    }
                    Number bit = (Number) rs.getObject("bit_index");
                    if (bit != null && bit.intValue() >= 0) {
                        bits.set(bit.intValue());
                    }
                },
                userId,
                userId);
        Access access = isManage[0] ? Access.MANAGE : Access.EXAM;
        return new DbAuthzResult(access, bits);
    }

    private long nextVersion(UUID userId) {
        try {
            String verKey = VER_PREFIX + userId;
            Long incremented = redisTemplate.opsForValue().increment(verKey);
            if (incremented != null) {
                redisTemplate.expire(verKey, VERSION_TTL);
                return incremented;
            }
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("userId", userId.toString())
                    .log("Failed to increment authz version in Redis; using fallback version");
        }
        return 1L;
    }

    private AuthzSnapshot parseSnapshot(UUID userId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            Access access = Access.valueOf(parts[0]);
            long version = Long.parseLong(parts[1]);
            SnapshotCacheKey cacheKey = new SnapshotCacheKey(userId, version);
            PermissionSet cached = decodedCache.getIfPresent(cacheKey);
            if (cached != null) {
                return new AuthzSnapshot(access, cached, version);
            }
            byte[] bitBytes = parts[2].isEmpty() ? new byte[0] : Base64.getDecoder().decode(parts[2]);
            PermissionSet decoded = permissionCatalog.decode(bitBytes);
            decodedCache.put(cacheKey, decoded);
            return new AuthzSnapshot(access, decoded, version);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("userId", userId.toString())
                    .log("Corrupt authz snapshot in Redis; rebuilding");
            return null;
        }
    }

    private static String encodeSnapshot(Access access, long version, PermissionSet permissions) {
        byte[] bytes = permissions.toByteArray();
        String base64 = bytes.length == 0 ? "" : Base64.getEncoder().encodeToString(bytes);
        return access.name() + "|" + version + "|" + base64;
    }

    private void invalidateLocalCache(Collection<UUID> userIds) {
        Set<UUID> targetSet = Set.copyOf(userIds);
        decodedCache.asMap().keySet().removeIf(k -> targetSet.contains(k.userId()));
    }

    private void performRedisEvictWithOutboxFallback(List<UUID> userIds) {
        List<String> keys = userIds.stream().map(this::cacheKey).toList();
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("count", userIds.size())
                    .log("Redis authz snapshot eviction failed; enqueuing outbox fallback");
            requiresNewTx.executeWithoutResult(status ->
                    outboxPublisher.publish(OUTBOX_TOPIC_EVICT, new AuthzEvictPayload(userIds)));
        }
    }

    private void handleEvictOutbox(OutboxEvent event) throws JsonProcessingException {
        AuthzEvictPayload payload = objectMapper.readValue(event.getPayload(), AuthzEvictPayload.class);
        if (payload == null || payload.userIds() == null || payload.userIds().isEmpty()) {
            return;
        }
        invalidateLocalCache(payload.userIds());
        List<String> keys = payload.userIds().stream().map(this::cacheKey).toList();
        redisTemplate.delete(keys);
    }

    private record DbAuthzResult(Access access, BitSet bits) {
    }

    private record SnapshotCacheKey(UUID userId, long ver) {
    }

    public record AuthzEvictPayload(List<UUID> userIds) {
    }
}
