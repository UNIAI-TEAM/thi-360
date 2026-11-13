package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    void interleavedCreateAndBulkRevokeWithBarriers_neverLeavesOrphanAcceptedHash() throws Exception {
        SessionStore storeA = new SessionStore(redisTemplate, securityProperties, Clock.systemUTC());
        SessionStore storeB = new SessionStore(redisTemplate, securityProperties, Clock.systemUTC());
        UUID userId = UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<String> createdSids = new ArrayList<>();
            for (int round = 0; round < 30; round++) {
                String sid = "interleave-" + round + "-" + UUID.randomUUID();
                createdSids.add(sid);
                CyclicBarrier barrier = new CyclicBarrier(2);

                Future<List<String>> createFuture = pool.submit(() -> {
                    awaitBarrier(barrier);
                    return storeA.createSession(sid, userId, Realm.APP);
                });
                Future<Void> revokeFuture = pool.submit(() -> {
                    awaitBarrier(barrier);
                    storeB.deleteAllSessions(userId);
                    return null;
                });

                createFuture.get();
                revokeFuture.get();

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
            }

            storeB.deleteAllSessions(userId);
            for (String s : createdSids) {
                assertThat(storeA.findSession(s)).isEmpty();
                assertThat(redisTemplate.hasKey("sess:" + s)).isFalse();
            }
            assertThat(redisTemplate.hasKey("user-sess:" + userId)).isFalse();
        } finally {
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
}
