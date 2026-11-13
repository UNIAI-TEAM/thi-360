package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;

class SessionStoreIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void fixedClockSameMillisecond_reverseLexicalSidsAcrossInstances_evictsOnlyFirstCreated() {
        Instant fixedInstant = Instant.parse("2026-11-13T12:00:00.123Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        SessionStore storeA = new SessionStore(redisTemplate, securityProperties, fixedClock);
        SessionStore storeB = new SessionStore(redisTemplate, securityProperties, fixedClock);

        UUID userId = UUID.randomUUID();
        List<String> reverseLexicalSids = List.of("sid-z", "sid-y", "sid-x", "sid-w", "sid-v", "sid-a");

        try {
            for (int i = 0; i < reverseLexicalSids.size(); i++) {
                SessionStore targetStore = (i % 2 == 0) ? storeA : storeB;
                List<String> evicted = targetStore.createSession(reverseLexicalSids.get(i), userId, Realm.APP);
                if (i < 5) {
                    assertThat(evicted).isEmpty();
                } else {
                    assertThat(evicted).containsExactly("sid-z");
                }
            }

            assertThat(storeA.findSession("sid-z")).isEmpty();
            for (int i = 1; i < reverseLexicalSids.size(); i++) {
                String sid = reverseLexicalSids.get(i);
                assertThat(storeB.findSession(sid))
                        .isPresent()
                        .get()
                        .satisfies(data -> assertThat(data.createdAt()).isEqualTo(fixedInstant));
            }
            assertThat(redisTemplate.opsForZSet().zCard("user-sess:" + userId)).isEqualTo(5L);
        } finally {
            storeA.deleteAllSessions(userId);
            redisTemplate.delete("user-sess-seq:" + userId);
        }
    }

    @Test
    void interleavedCreateAndBulkRevokeAtRedisCommandBoundary_neverLeavesOrphanAcceptedHash() throws Exception {
        AtomicBoolean armed = new AtomicBoolean(false);
        CountDownLatch snapshotCompleted = new CountDownLatch(1);
        CountDownLatch createCompleted = new CountDownLatch(1);
        StringRedisTemplate interceptedTemplate = templateWithPostSnapshotPause(
                redisTemplate.getConnectionFactory(), armed, snapshotCompleted, createCompleted);

        SessionStore storeA = new SessionStore(redisTemplate, securityProperties, Clock.systemUTC());
        SessionStore storeB = new SessionStore(interceptedTemplate, securityProperties, Clock.systemUTC());
        UUID userId = UUID.randomUUID();
        String seedSid = "interleave-seed-" + UUID.randomUUID();
        String newSid = "interleave-create-" + UUID.randomUUID();
        List<String> createdSids = List.of(seedSid, newSid);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            storeA.createSession(seedSid, userId, Realm.APP);
            armed.set(true);

            Future<Void> revokeFuture = pool.submit(() -> {
                try {
                    storeB.deleteAllSessions(userId);
                    return null;
                } finally {
                    snapshotCompleted.countDown();
                }
            });
            Future<List<String>> createFuture = pool.submit(() -> {
                try {
                    assertThat(snapshotCompleted.await(10, TimeUnit.SECONDS))
                            .as("deleteAllSessions must complete its snapshot or atomic script command on Redis")
                            .isTrue();
                    return storeA.createSession(newSid, userId, Realm.APP);
                } finally {
                    createCompleted.countDown();
                }
            });

            createFuture.get();
            revokeFuture.get();
            assertThat(armed.get())
                    .as("Redis command boundary hook must have intercepted deleteAllSessions")
                    .isFalse();

            Set<String> indexed = redisTemplate.opsForZSet().range("user-sess:" + userId, 0, -1);
            for (String s : createdSids) {
                boolean hashExists = Boolean.TRUE.equals(redisTemplate.hasKey("sess:" + s));
                boolean inIndex = indexed != null && indexed.contains(s);
                assertThat(hashExists)
                        .as("Surviving session hash %s must match user index membership", s)
                        .isEqualTo(inIndex);
                if (!inIndex) {
                    assertThat(storeA.findSession(s)).isEmpty();
                }
            }
            assertThat(storeA.findSession(newSid)).isPresent();

            storeB.deleteAllSessions(userId);
            for (String s : createdSids) {
                assertThat(storeA.findSession(s)).isEmpty();
                assertThat(redisTemplate.hasKey("sess:" + s)).isFalse();
            }
            assertThat(redisTemplate.hasKey("user-sess:" + userId)).isFalse();
        } finally {
            armed.set(false);
            createCompleted.countDown();
            snapshotCompleted.countDown();
            pool.shutdownNow();
            storeA.deleteAllSessions(userId);
            redisTemplate.delete("user-sess-seq:" + userId);
        }
    }

    @Test
    void concurrentSessionCreation_retainsExactlyPermittedNewestSessions() throws Exception {
        int totalSessions = 12;
        int maxPerUser = securityProperties.session().maxPerUser();
        UUID userId = UUID.randomUUID();
        SessionStore store = new SessionStore(redisTemplate, securityProperties, Clock.systemUTC());

        ExecutorService pool = Executors.newFixedThreadPool(totalSessions);
        CyclicBarrier barrier = new CyclicBarrier(totalSessions);
        List<String> sids = new ArrayList<>(totalSessions);
        for (int i = 0; i < totalSessions; i++) {
            sids.add("concurrent-" + i + "-" + UUID.randomUUID());
        }

        try {
            List<Future<List<String>>> futures = new ArrayList<>(totalSessions);
            for (String sid : sids) {
                futures.add(pool.submit(() -> {
                    awaitBarrier(barrier);
                    return store.createSession(sid, userId, Realm.APP);
                }));
            }
            for (Future<List<String>> future : futures) {
                future.get();
            }

            Set<String> indexed = redisTemplate.opsForZSet().range("user-sess:" + userId, 0, -1);
            assertThat(indexed).hasSize(maxPerUser);

            Double minSurvivingScore = null;
            for (String s : indexed) {
                Double score = redisTemplate.opsForZSet().score("user-sess:" + userId, s);
                assertThat(score).isNotNull();
                assertThat(store.findSession(s)).isPresent();
                if (minSurvivingScore == null || score < minSurvivingScore) {
                    minSurvivingScore = score;
                }
            }
            assertThat(minSurvivingScore).isNotNull();
            // Since Redis INCR assigns 1..12, retaining the newest 5 means scores must be 8..12.
            assertThat(minSurvivingScore).isEqualTo((double) (totalSessions - maxPerUser + 1));

            for (String s : sids) {
                if (!indexed.contains(s)) {
                    assertThat(store.findSession(s)).isEmpty();
                    assertThat(redisTemplate.hasKey("sess:" + s)).isFalse();
                }
            }
        } finally {
            pool.shutdownNow();
            store.deleteAllSessions(userId);
            redisTemplate.delete("user-sess-seq:" + userId);
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } catch (BrokenBarrierException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static StringRedisTemplate templateWithPostSnapshotPause(
            RedisConnectionFactory delegateFactory,
            AtomicBoolean armed,
            CountDownLatch snapshotCompleted,
            CountDownLatch createCompleted) {
        RedisConnectionFactory interceptingFactory = new RedisConnectionFactory() {
            @Override
            public RedisConnection getConnection() {
                RedisConnection delegate = delegateFactory.getConnection();
                return wrapConnection(delegate, armed, snapshotCompleted, createCompleted);
            }

            @Override
            public RedisClusterConnection getClusterConnection() {
                return delegateFactory.getClusterConnection();
            }

            @Override
            public boolean getConvertPipelineAndTxResults() {
                return delegateFactory.getConvertPipelineAndTxResults();
            }

            @Override
            public RedisSentinelConnection getSentinelConnection() {
                return delegateFactory.getSentinelConnection();
            }

            @Override
            public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
                return delegateFactory.translateExceptionIfPossible(ex);
            }
        };
        return new StringRedisTemplate(interceptingFactory);
    }

    private static RedisConnection wrapConnection(
            RedisConnection delegate,
            AtomicBoolean armed,
            CountDownLatch snapshotCompleted,
            CountDownLatch createCompleted) {
        Class<?>[] interfaces = delegate instanceof StringRedisConnection
                ? new Class<?>[] {StringRedisConnection.class, RedisConnection.class}
                : new Class<?>[] {RedisConnection.class};
        return (RedisConnection) Proxy.newProxyInstance(
                SessionStoreIT.class.getClassLoader(),
                interfaces,
                new PostSnapshotPauseHandler(delegate, armed, snapshotCompleted, createCompleted));
    }

    private static final class PostSnapshotPauseHandler implements InvocationHandler {

        private static final Set<String> SUB_COMMAND_PROVIDERS = Set.of(
                "commands",
                "geoCommands",
                "hashCommands",
                "hyperLogLogCommands",
                "keyCommands",
                "listCommands",
                "setCommands",
                "scriptingCommands",
                "serverCommands",
                "streamCommands",
                "stringCommands",
                "zSetCommands");

        private static final Set<String> SNAPSHOT_OR_SCRIPT_COMMANDS = Set.of(
                "zRange",
                "zRangeByScore",
                "zRangeWithScores",
                "zRangeByScoreWithScores",
                "zRevRange",
                "zRevRangeWithScores",
                "zRevRangeByScore",
                "zRevRangeByScoreWithScores",
                "zScan",
                "keys",
                "scan",
                "sMembers",
                "sScan",
                "hGetAll",
                "hKeys",
                "hScan",
                "eval",
                "evalSha");

        private final Object delegate;
        private final AtomicBoolean armed;
        private final CountDownLatch snapshotCompleted;
        private final CountDownLatch createCompleted;

        private PostSnapshotPauseHandler(
                Object delegate,
                AtomicBoolean armed,
                CountDownLatch snapshotCompleted,
                CountDownLatch createCompleted) {
            this.delegate = delegate;
            this.armed = armed;
            this.snapshotCompleted = snapshotCompleted;
            this.createCompleted = createCompleted;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
            if (method.getDeclaringClass() == Object.class) {
                return result;
            }
            String name = method.getName();
            if (SUB_COMMAND_PROVIDERS.contains(name)
                    && result != null
                    && method.getReturnType().isInterface()) {
                return Proxy.newProxyInstance(
                        SessionStoreIT.class.getClassLoader(),
                        new Class<?>[] {method.getReturnType()},
                        new PostSnapshotPauseHandler(result, armed, snapshotCompleted, createCompleted));
            }
            if (SNAPSHOT_OR_SCRIPT_COMMANDS.contains(name) && armed.compareAndSet(true, false)) {
                snapshotCompleted.countDown();
                if (!createCompleted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent createSession");
                }
            }
            return result;
        }
    }
}
