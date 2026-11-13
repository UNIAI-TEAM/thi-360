package vn.unicom.thi360.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {

    private static final String SESSION_KEY_PREFIX = "sess:";
    private static final String USER_SESSIONS_PREFIX = "user-sess:";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_REALM = "realm";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public SessionStore(
            StringRedisTemplate redisTemplate,
            SecurityProperties securityProperties,
            Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public List<String> createSession(String sessionId, UUID userId, Realm realm) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(realm, "realm must not be null");

        Duration ttl = securityProperties.session().ttl();
        int maxPerUser = Math.max(1, securityProperties.session().maxPerUser());
        Instant now = Instant.now(clock);

        String sessKey = sessionKey(sessionId);
        String userSessKey = userSessionsKey(userId);

        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.putAll(sessKey, Map.of(
                FIELD_USER_ID, userId.toString(),
                FIELD_REALM, realm.name(),
                FIELD_CREATED_AT, Long.toString(now.toEpochMilli())));
        redisTemplate.expire(sessKey, ttl);

        ZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();
        double score = now.toEpochMilli() * 1_000_000.0 + (sequence.incrementAndGet() % 1_000_000L);
        zsetOps.add(userSessKey, sessionId, score);
        redisTemplate.expire(userSessKey, ttl);

        List<String> evicted = new ArrayList<>();
        Long size = zsetOps.zCard(userSessKey);
        if (size != null && size > maxPerUser) {
            long excess = size - maxPerUser;
            Set<String> oldest = zsetOps.range(userSessKey, 0, excess - 1);
            if (oldest != null && !oldest.isEmpty()) {
                for (String oldSid : oldest) {
                    zsetOps.remove(userSessKey, oldSid);
                    redisTemplate.delete(sessionKey(oldSid));
                    evicted.add(oldSid);
                }
            }
        }
        return List.copyOf(evicted);
    }

    public Optional<SessionData> findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(sessionKey(sessionId));
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        Object userIdObj = raw.get(FIELD_USER_ID);
        Object realmObj = raw.get(FIELD_REALM);
        Object createdAtObj = raw.get(FIELD_CREATED_AT);
        if (userIdObj == null || realmObj == null || createdAtObj == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SessionData(
                    sessionId,
                    UUID.fromString(String.valueOf(userIdObj)),
                    Realm.valueOf(String.valueOf(realmObj)),
                    Instant.ofEpochMilli(Long.parseLong(String.valueOf(createdAtObj)))));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public boolean exists(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(sessionId)));
    }

    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Optional<SessionData> existing = findSession(sessionId);
        redisTemplate.delete(sessionKey(sessionId));
        existing.ifPresent(data -> redisTemplate.opsForZSet().remove(userSessionsKey(data.userId()), sessionId));
    }

    public void deleteAllSessions(UUID userId) {
        if (userId == null) {
            return;
        }
        String userSessKey = userSessionsKey(userId);
        Set<String> sessionIds = redisTemplate.opsForZSet().range(userSessKey, 0, -1);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            List<String> keysToDelete = new ArrayList<>(sessionIds.size() + 1);
            for (String sid : sessionIds) {
                keysToDelete.add(sessionKey(sid));
            }
            keysToDelete.add(userSessKey);
            redisTemplate.delete(keysToDelete);
        } else {
            redisTemplate.delete(userSessKey);
        }
    }

    private static String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private static String userSessionsKey(UUID userId) {
        return USER_SESSIONS_PREFIX + userId;
    }

    public record SessionData(
            String sessionId,
            UUID userId,
            Realm realm,
            Instant createdAt
    ) {
    }
}
