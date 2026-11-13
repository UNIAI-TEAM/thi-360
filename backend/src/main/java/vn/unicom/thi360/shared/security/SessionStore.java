package vn.unicom.thi360.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {

    private static final String SESSION_KEY_PREFIX = "sess:";
    private static final String USER_SESSIONS_PREFIX = "user-sess:";
    private static final String USER_SESSION_SEQ_PREFIX = "user-sess-seq:";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_REALM = "realm";
    private static final String FIELD_CREATED_AT = "createdAt";

    private static final DefaultRedisScript<List> CREATE_SESSION_SCRIPT = createScript(
            """
            local sessKey = KEYS[1]
            local userSessKey = KEYS[2]
            local seqKey = KEYS[3]
            local sid = ARGV[1]
            local userId = ARGV[2]
            local realm = ARGV[3]
            local createdAt = ARGV[4]
            local ttlMs = tonumber(ARGV[5])
            local maxPerUser = tonumber(ARGV[6])
            local sessPrefix = ARGV[7]

            local seq = redis.call('INCR', seqKey)
            redis.call('PEXPIRE', seqKey, ttlMs)

            redis.call('HSET', sessKey, 'userId', userId, 'realm', realm, 'createdAt', createdAt)
            redis.call('PEXPIRE', sessKey, ttlMs)

            redis.call('ZADD', userSessKey, seq, sid)
            redis.call('PEXPIRE', userSessKey, ttlMs)

            local members = redis.call('ZRANGE', userSessKey, 0, -1)
            local liveCount = 0
            local live = {}
            for _, m in ipairs(members) do
              if redis.call('EXISTS', sessPrefix .. m) == 1 then
                liveCount = liveCount + 1
                live[liveCount] = m
              else
                redis.call('ZREM', userSessKey, m)
              end
            end

            local evicted = {}
            if liveCount > maxPerUser then
              local excess = liveCount - maxPerUser
              for i = 1, excess do
                local oldSid = live[i]
                redis.call('ZREM', userSessKey, oldSid)
                redis.call('DEL', sessPrefix .. oldSid)
                table.insert(evicted, oldSid)
              end
            end
            return evicted
            """,
            List.class);

    private static final DefaultRedisScript<List> FIND_SESSION_SCRIPT = createScript(
            """
            local vals = redis.call('HMGET', KEYS[1], 'userId', 'realm', 'createdAt')
            if not vals[1] or not vals[2] or not vals[3] then
              return {}
            end
            if not redis.call('ZSCORE', ARGV[2] .. vals[1], ARGV[1]) then
              return {}
            end
            return vals
            """,
            List.class);
    private static final DefaultRedisScript<List> FIND_SESSION_WITH_AUTHZ_SCRIPT = createScript(
            """
            local vals = redis.call('HMGET', KEYS[1], 'userId', 'realm', 'createdAt')
            if not vals[1] or not vals[2] or not vals[3] then
              return {}
            end
            if not redis.call('ZSCORE', ARGV[2] .. vals[1], ARGV[1]) then
              return {}
            end
            local authz = redis.call('GET', KEYS[2])
            return {vals[1], vals[2], vals[3], authz or ''}
            """,
            List.class);

    private static final DefaultRedisScript<Long> DELETE_SESSION_SCRIPT = createScript(
            """
            local userId = redis.call('HGET', KEYS[1], 'userId')
            redis.call('DEL', KEYS[1])
            if userId then
              redis.call('ZREM', ARGV[2] .. userId, ARGV[1])
              return 1
            end
            return 0
            """,
            Long.class);

    private static final DefaultRedisScript<Long> DELETE_SESSION_IF_OWNER_SCRIPT = createScript(
            """
            local userId = redis.call('HGET', KEYS[1], 'userId')
            local realm = redis.call('HGET', KEYS[1], 'realm')
            if userId == ARGV[2] and realm == ARGV[3] then
              redis.call('DEL', KEYS[1])
              redis.call('ZREM', KEYS[2], ARGV[1])
              return 1
            end
            return 0
            """,
            Long.class);

    private static final DefaultRedisScript<Long> DELETE_ALL_SESSIONS_SCRIPT = createScript(
            """
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, sid in ipairs(members) do
              redis.call('DEL', ARGV[1] .. sid)
            end
            redis.call('DEL', KEYS[1])
            return #members
            """,
            Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SESSION_SCRIPT = createScript(
            """
            local oldSessKey = KEYS[1]
            local newSessKey = KEYS[2]
            local userSessKey = KEYS[3]
            local seqKey = KEYS[4]
            local oldSid = ARGV[1]
            local newSid = ARGV[2]
            local expectedUserId = ARGV[3]
            local expectedRealm = ARGV[4]
            local createdAt = ARGV[5]
            local ttlMs = tonumber(ARGV[6])
            local maxPerUser = tonumber(ARGV[7])
            local sessPrefix = ARGV[8]

            local userId = redis.call('HGET', oldSessKey, 'userId')
            local realm = redis.call('HGET', oldSessKey, 'realm')
            if userId ~= expectedUserId or realm ~= expectedRealm then
              return 0
            end
            if not redis.call('ZSCORE', userSessKey, oldSid) then
              return 0
            end

            redis.call('DEL', oldSessKey)
            redis.call('ZREM', userSessKey, oldSid)

            local seq = redis.call('INCR', seqKey)
            redis.call('PEXPIRE', seqKey, ttlMs)

            redis.call('HSET', newSessKey, 'userId', expectedUserId, 'realm', expectedRealm, 'createdAt', createdAt)
            redis.call('PEXPIRE', newSessKey, ttlMs)

            redis.call('ZADD', userSessKey, seq, newSid)
            redis.call('PEXPIRE', userSessKey, ttlMs)

            local members = redis.call('ZRANGE', userSessKey, 0, -1)
            local liveCount = 0
            local live = {}
            for _, m in ipairs(members) do
              if redis.call('EXISTS', sessPrefix .. m) == 1 then
                liveCount = liveCount + 1
                live[liveCount] = m
              else
                redis.call('ZREM', userSessKey, m)
              end
            end
            if liveCount > maxPerUser then
              local excess = liveCount - maxPerUser
              for i = 1, excess do
                local evictSid = live[i]
                redis.call('ZREM', userSessKey, evictSid)
                redis.call('DEL', sessPrefix .. evictSid)
              end
            end
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final Clock clock;

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

        long ttlMs = Math.max(1L, securityProperties.session().ttl().toMillis());
        int maxPerUser = Math.max(1, securityProperties.session().maxPerUser());
        Instant now = Instant.now(clock);

        List<?> rawEvicted = redisTemplate.execute(
                CREATE_SESSION_SCRIPT,
                List.of(sessionKey(sessionId), userSessionsKey(userId), userSessionSeqKey(userId)),
                sessionId,
                userId.toString(),
                realm.name(),
                Long.toString(now.toEpochMilli()),
                Long.toString(ttlMs),
                Integer.toString(maxPerUser),
                SESSION_KEY_PREFIX);

        if (rawEvicted == null || rawEvicted.isEmpty()) {
            return List.of();
        }
        List<String> evicted = new ArrayList<>(rawEvicted.size());
        for (Object item : rawEvicted) {
            if (item != null) {
                evicted.add(String.valueOf(item));
            }
        }
        return List.copyOf(evicted);
    }

    public Optional<SessionData> findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        List<?> values = redisTemplate.execute(
                FIND_SESSION_SCRIPT,
                List.of(sessionKey(sessionId)),
                sessionId,
                USER_SESSIONS_PREFIX);
        if (values == null || values.size() < 3
                || values.get(0) == null || values.get(1) == null || values.get(2) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SessionData(
                    sessionId,
                    UUID.fromString(String.valueOf(values.get(0))),
                    Realm.valueOf(String.valueOf(values.get(1))),
                    Instant.ofEpochMilli(Long.parseLong(String.valueOf(values.get(2))))));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public Optional<SessionWithAuthz> findSessionWithAuthz(String sessionId, String authzKey) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        if (authzKey == null || authzKey.isBlank()) {
            return findSession(sessionId).map(session -> new SessionWithAuthz(session, null));
        }
        List<?> values = redisTemplate.execute(
                FIND_SESSION_WITH_AUTHZ_SCRIPT,
                List.of(sessionKey(sessionId), authzKey),
                sessionId,
                USER_SESSIONS_PREFIX);
        if (values == null || values.size() < 4
                || values.get(0) == null || values.get(1) == null || values.get(2) == null) {
            return Optional.empty();
        }
        try {
            SessionData session = new SessionData(
                    sessionId,
                    UUID.fromString(String.valueOf(values.get(0))),
                    Realm.valueOf(String.valueOf(values.get(1))),
                    Instant.ofEpochMilli(Long.parseLong(String.valueOf(values.get(2)))));
            Object rawAuthzObj = values.get(3);
            String rawAuthz = rawAuthzObj == null ? null : String.valueOf(rawAuthzObj);
            if (rawAuthz != null && rawAuthz.isBlank()) {
                rawAuthz = null;
            }
            return Optional.of(new SessionWithAuthz(session, rawAuthz));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public boolean exists(String sessionId) {
        return findSession(sessionId).isPresent();
    }

    public boolean existsForUser(String sessionId, UUID userId, Realm realm) {
        if (sessionId == null || sessionId.isBlank() || userId == null || realm == null) {
            return false;
        }
        return findSession(sessionId)
                .filter(data -> userId.equals(data.userId()) && realm == data.realm())
                .isPresent();
    }

    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        redisTemplate.execute(
                DELETE_SESSION_SCRIPT,
                List.of(sessionKey(sessionId)),
                sessionId,
                USER_SESSIONS_PREFIX);
    }

    public boolean deleteSessionIfOwner(String sessionId, UUID expectedUserId, Realm expectedRealm) {
        if (sessionId == null || sessionId.isBlank() || expectedUserId == null || expectedRealm == null) {
            return false;
        }
        Long deleted = redisTemplate.execute(
                DELETE_SESSION_IF_OWNER_SCRIPT,
                List.of(sessionKey(sessionId), userSessionsKey(expectedUserId)),
                sessionId,
                expectedUserId.toString(),
                expectedRealm.name());
        return deleted != null && deleted > 0L;
    }

    public boolean rotateSession(String oldSid, String newSid, UUID userId, Realm realm) {
        if (oldSid == null || oldSid.isBlank() || newSid == null || newSid.isBlank()
                || userId == null || realm == null) {
            return false;
        }
        long ttlMs = Math.max(1L, securityProperties.session().ttl().toMillis());
        int maxPerUser = Math.max(1, securityProperties.session().maxPerUser());
        Instant now = Instant.now(clock);
        Long rotated = redisTemplate.execute(
                ROTATE_SESSION_SCRIPT,
                List.of(sessionKey(oldSid), sessionKey(newSid), userSessionsKey(userId), userSessionSeqKey(userId)),
                oldSid,
                newSid,
                userId.toString(),
                realm.name(),
                Long.toString(now.toEpochMilli()),
                Long.toString(ttlMs),
                Integer.toString(maxPerUser),
                SESSION_KEY_PREFIX);
        return rotated != null && rotated > 0L;
    }

    public void deleteAllSessions(UUID userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.execute(
                DELETE_ALL_SESSIONS_SCRIPT,
                List.of(userSessionsKey(userId)),
                SESSION_KEY_PREFIX);
    }

    private static <T> DefaultRedisScript<T> createScript(String lua, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(resultType);
        return script;
    }

    private static String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private static String userSessionsKey(UUID userId) {
        return USER_SESSIONS_PREFIX + userId;
    }

    private static String userSessionSeqKey(UUID userId) {
        return USER_SESSION_SEQ_PREFIX + userId;
    }
    public record SessionData(
            String sessionId,
            UUID userId,
            Realm realm,
            Instant createdAt
    ) {
    }

    public record SessionWithAuthz(
            SessionData session,
            String rawAuthzSnapshot
    ) {
    }
}
