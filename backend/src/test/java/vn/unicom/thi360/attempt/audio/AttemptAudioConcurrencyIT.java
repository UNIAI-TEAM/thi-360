package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

/**
 * Concurrency integration tests proving single-play open under parallel requests, single-banking
 * of seek tolerance on parallel resumes, safe open-vs-deadline-expiry races, and safe
 * stale-end-vs-new-open races (AC-11, risk concurrency suite).
 */
class AttemptAudioConcurrencyIT extends AbstractAttemptAudioIT {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AttemptDeadlineWorker deadlineWorker;

    private record ConcCtx(UUID userId, Attempt attempt, UUID sessionId, UUID assetId) {}

    private ConcCtx startSingleAudio(String label) throws Exception {
        UUID userId = createStudent(label);
        var cbr = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID qId = createReadyListeningQuestion(cbr.assetId(), label.toUpperCase());
        stubListeningPaperWithQuestions(List.of(qId));
        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);
        Attempt attempt = startAndAwaitOpen(userId, label, line.entitlementId());
        UUID sid = claimSession(userId, attempt.getId());
        return new ConcCtx(userId, attempt, sid, cbr.assetId());
    }

    @Test
    void parallelOpenCountsOnce() throws Exception {
        var ctx = startSingleAudio("aud-conc-open");

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<Void> holder =
                    holdAudioRowLock(pool, ctx.attempt().getId(), ctx.assetId(), lockHeld, releaseLock);
            assertThat(lockHeld.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> f1 =
                    pool.submit(
                            () ->
                                    performOpenPlay(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            ctx.sessionId(),
                                            "par-req-1"));
            Future<MvcResult> f2 =
                    pool.submit(
                            () ->
                                    performOpenPlay(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            ctx.sessionId(),
                                            "par-req-2"));

            awaitAudioRowLockWaiters(2);
            releaseLock.countDown();
            holder.get(15, TimeUnit.SECONDS);

            MvcResult r1 = f1.get(15, TimeUnit.SECONDS);
            MvcResult r2 = f2.get(15, TimeUnit.SECONDS);

            List<MvcResult> all = List.of(r1, r2);
            MvcResult winner =
                    all.stream()
                            .filter(r -> r.getResponse().getStatus() == 200)
                            .findFirst()
                            .orElseThrow();
            MvcResult loser =
                    all.stream()
                            .filter(r -> r.getResponse().getStatus() == 409)
                            .findFirst()
                            .orElseThrow();

            String winnerPlayId = json(winner).path("playId").asText();
            assertThat(json(winner).path("playsUsed").asInt()).isEqualTo(1);
            assertThat(json(winner).path("playsLeft").asInt()).isEqualTo(1);

            JsonNode loserJson = json(loser);
            assertThat(loserJson.path("code").asText()).isEqualTo("AUDIO_PLAY_IN_PROGRESS");
            assertThat(loserJson.path("playId").asText()).isEqualTo(winnerPlayId);

            AttemptAudioPlay saved =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                            .orElseThrow();
            assertThat(saved.getPlayCount()).isEqualTo(1);
            assertThat(saved.getState()).isEqualTo(AudioPlayState.PLAYING);
            assertThat(saved.getPlayId()).isEqualTo(UUID.fromString(winnerPlayId));
        } finally {
            releaseLock.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void parallelResumeKeepsTolerance() throws Exception {
        var ctx = startSingleAudio("aud-conc-res");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "pr-open"))
                                .path("playId")
                                .asText());
        // Consume 2_000ms out of 3_000ms tolerance while clock is frozen
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        2_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<Void> holder =
                    holdAudioRowLock(pool, ctx.attempt().getId(), ctx.assetId(), lockHeld, releaseLock);
            assertThat(lockHeld.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> f1 =
                    pool.submit(
                            () ->
                                    performResumePlay(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            playId,
                                            ctx.sessionId()));
            Future<MvcResult> f2 =
                    pool.submit(
                            () ->
                                    performResumePlay(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            playId,
                                            ctx.sessionId()));

            awaitAudioRowLockWaiters(2);
            releaseLock.countDown();
            holder.get(15, TimeUnit.SECONDS);

            MvcResult r1 = f1.get(15, TimeUnit.SECONDS);
            MvcResult r2 = f2.get(15, TimeUnit.SECONDS);
            assertThat(r1.getResponse().getStatus()).isEqualTo(200);
            assertThat(r2.getResponse().getStatus()).isEqualTo(200);

            AttemptAudioPlay saved =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                            .orElseThrow();
            assertThat(saved.getToleranceUsedMs()).isEqualTo(2_000L);
            assertThat(saved.getSegmentExcessMs()).isZero();

            // Only 1_000ms remaining tolerance left at frozen clock -> 3_000 accepted, 3_001 rejected
            assertThat(
                            performProgress(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            playId,
                                            ctx.sessionId(),
                                            3_000L)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(200);
            MvcResult beyond =
                    performProgress(
                            ctx.userId(),
                            ctx.attempt().getId(),
                            ctx.assetId(),
                            playId,
                            ctx.sessionId(),
                            3_001L);
            assertThat(beyond.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(beyond).path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
        } finally {
            releaseLock.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void openRacesWithDeadlineExpiry() throws Exception {
        // Case A: openPlay blocks on audio row lock before deadline while clock crosses deadline
        var ctxA = startSingleAudio("aud-conc-dl-a");
        mutableClock.setAt(ctxA.attempt().getDeadlineAt().minusSeconds(1));

        CountDownLatch lockHeldA = new CountDownLatch(1);
        CountDownLatch releaseLockA = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holderA =
                    holdAudioRowLock(
                            pool, ctxA.attempt().getId(), ctxA.assetId(), lockHeldA, releaseLockA);
            assertThat(lockHeldA.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> openFutureA =
                    pool.submit(
                            () ->
                                    performOpenPlay(
                                            ctxA.userId(),
                                            ctxA.attempt().getId(),
                                            ctxA.assetId(),
                                            ctxA.sessionId(),
                                            "dl-race-a"));
            awaitAudioRowLockWaiters(1);

            mutableClock.setAt(ctxA.attempt().getDeadlineAt().plusSeconds(2));
            releaseLockA.countDown();
            holderA.get(15, TimeUnit.SECONDS);

            MvcResult openResA = openFutureA.get(15, TimeUnit.SECONDS);
            assertThat(openResA.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(openResA).path("code").asText()).isEqualTo("TIME_UP");

            Attempt closedA = attemptRepository.findById(ctxA.attempt().getId()).orElseThrow();
            assertThat(closedA.getState()).isEqualTo(AttemptState.GRADING);
            assertThat(closedA.getEndReason()).isEqualTo(EndReason.TIME_UP);

            AttemptAudioPlay playA =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctxA.attempt().getId(), ctxA.assetId())
                            .orElseThrow();
            assertThat(playA.getState()).isEqualTo(AudioPlayState.IDLE);
            assertThat(playA.getPlayCount()).isZero();

            // Case B: openPlay racing simultaneously with deadlineWorker.sweepOverdue() never deadlocks
            // and never leaves an open PLAYING row after the attempt closes.
            var ctxB = startSingleAudio("aud-conc-dl-b");
            mutableClock.setAt(ctxB.attempt().getDeadlineAt().plusSeconds(1));

            CountDownLatch readyB = new CountDownLatch(2);
            CountDownLatch fireB = new CountDownLatch(1);
            Future<MvcResult> openFutureB =
                    pool.submit(
                            () -> {
                                readyB.countDown();
                                assertThat(fireB.await(10, TimeUnit.SECONDS)).isTrue();
                                return performOpenPlay(
                                        ctxB.userId(),
                                        ctxB.attempt().getId(),
                                        ctxB.assetId(),
                                        ctxB.sessionId(),
                                        "dl-race-b");
                            });
            Future<Integer> sweepFutureB =
                    pool.submit(
                            () -> {
                                readyB.countDown();
                                assertThat(fireB.await(10, TimeUnit.SECONDS)).isTrue();
                                return deadlineWorker.sweepOverdue();
                            });

            assertThat(readyB.await(10, TimeUnit.SECONDS)).isTrue();
            fireB.countDown();

            MvcResult openResB = openFutureB.get(15, TimeUnit.SECONDS);
            sweepFutureB.get(15, TimeUnit.SECONDS);

            assertThat(openResB.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(openResB).path("code").asText()).isEqualTo("TIME_UP");
            assertThat(
                            attemptAudioPlayRepository
                                    .findByAttemptIdAndAssetId(
                                            ctxB.attempt().getId(), ctxB.assetId())
                                    .orElseThrow()
                                    .getState())
                    .isNotEqualTo(AudioPlayState.PLAYING);
        } finally {
            releaseLockA.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void staleEndRacesWithNewOpen() throws Exception {
        var ctx = startSingleAudio("aud-conc-stale-end");

        UUID play1Id =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "se-1"))
                                .path("playId")
                                .asText());
        assertThat(
                        performEndPlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        play1Id,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<Void> holder =
                    holdAudioRowLock(
                            pool, ctx.attempt().getId(), ctx.assetId(), lockHeld, releaseLock);
            assertThat(lockHeld.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> staleEndFuture =
                    pool.submit(
                            () ->
                                    performEndPlay(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            play1Id,
                                            ctx.sessionId()));
            Future<MvcResult> openPlay2Future =
                    pool.submit(
                            () ->
                                    performOpenPlay(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            ctx.sessionId(),
                                            "se-2"));

            awaitAudioRowLockWaiters(2);
            releaseLock.countDown();
            holder.get(15, TimeUnit.SECONDS);

            MvcResult staleEndRes = staleEndFuture.get(15, TimeUnit.SECONDS);
            MvcResult openPlay2Res = openPlay2Future.get(15, TimeUnit.SECONDS);

            assertThat(openPlay2Res.getResponse().getStatus()).isEqualTo(200);
            UUID play2Id = UUID.fromString(json(openPlay2Res).path("playId").asText());
            assertThat(play2Id).isNotEqualTo(play1Id);

            int staleStatus = staleEndRes.getResponse().getStatus();
            assertThat(staleStatus).isIn(200, 409);
            if (staleStatus == 409) {
                assertThat(json(staleEndRes).path("code").asText()).isEqualTo("AUDIO_PLAY_STALE");
            }

            AttemptAudioPlay saved =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                            .orElseThrow();
            assertThat(saved.getPlayId()).isEqualTo(play2Id);
            assertThat(saved.getPlayCount()).isEqualTo(2);
            assertThat(saved.getState()).isEqualTo(AudioPlayState.PLAYING);
            assertThat(saved.getEndReason()).isNull();
        } finally {
            releaseLock.countDown();
            pool.shutdownNow();
        }
    }

    private Future<Void> holdAudioRowLock(
            ExecutorService pool,
            UUID attemptId,
            UUID assetId,
            CountDownLatch lockHeld,
            CountDownLatch releaseLock) {
        return pool.submit(
                () -> {
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(
                                    status -> {
                                        attemptAudioPlayRepository
                                                .findByAttemptIdAndAssetIdForUpdate(
                                                        attemptId, assetId)
                                                .orElseThrow();
                                        lockHeld.countDown();
                                        try {
                                            assertThat(releaseLock.await(15, TimeUnit.SECONDS))
                                                    .isTrue();
                                        } catch (InterruptedException ex) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(ex);
                                        }
                                    });
                    return null;
                });
    }

    private void awaitAudioRowLockWaiters(int expectedWaiters) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Integer waiting =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND pid <> pg_backend_pid()
                              AND state = 'active'
                              AND wait_event_type = 'Lock'
                              AND query ILIKE '%attempt_audio_play%'
                            """,
                            Integer.class);
            if (waiting != null && waiting >= expectedWaiters) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError(
                "Timed out waiting for " + expectedWaiters + " attempt_audio_play lock waiters");
    }
}
