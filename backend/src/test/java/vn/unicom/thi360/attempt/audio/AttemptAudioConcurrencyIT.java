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
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AudioEndReason;
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

    @Test
    void getStateReconcilesConcurrentlyWithoutOptimisticLock412() throws Exception {
        // Case A: Concurrent DB update (ENDED/FINISHED) commits between GET's initial plain load and FOR UPDATE lock.
        var ctxA = startSingleAudio("aud-conc-state-a");
        UUID playAId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctxA.userId(),
                                                ctxA.attempt().getId(),
                                                ctxA.assetId(),
                                                ctxA.sessionId(),
                                                "gs-a1"))
                                .path("playId")
                                .asText());
        AttemptPart partA =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(ctxA.attempt().getId()).getFirst();
        assertThat(
                        performFinishPart(
                                        ctxA.userId(),
                                        ctxA.attempt().getId(),
                                        partA.getId(),
                                        ctxA.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        // Reset audio row to stale PLAYING on CLOSED part so AttemptStateService enters locked reconciliation.
        jdbc.update(
                "UPDATE attempt.attempt_audio_play SET state = 'PLAYING', end_reason = NULL WHERE attempt_id = ? AND asset_id = ?",
                ctxA.attempt().getId(),
                ctxA.assetId());

        CountDownLatch lockHeldA = new CountDownLatch(1);
        CountDownLatch waiterReadyA = new CountDownLatch(1);
        CountDownLatch commitUpdateA = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<Void> holderA =
                    pool.submit(
                            () -> {
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    attemptAudioPlayRepository
                                                            .findByAttemptIdAndAssetIdForUpdate(
                                                                    ctxA.attempt().getId(),
                                                                    ctxA.assetId())
                                                            .orElseThrow();
                                                    lockHeldA.countDown();
                                                    try {
                                                        assertThat(
                                                                        commitUpdateA.await(
                                                                                15,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException ex) {
                                                        Thread.currentThread().interrupt();
                                                        throw new IllegalStateException(ex);
                                                    }
                                                    jdbc.update(
                                                            "UPDATE attempt.attempt_audio_play "
                                                                    + "SET state = 'ENDED', end_reason = 'FINISHED', lease_until = NULL, version = version + 1 "
                                                                    + "WHERE attempt_id = ? AND asset_id = ?",
                                                            ctxA.attempt().getId(),
                                                            ctxA.assetId());
                                                });
                                return null;
                            });
            assertThat(lockHeldA.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> getFutureA =
                    pool.submit(
                            () -> {
                                waiterReadyA.countDown();
                                return performGetState(ctxA.userId(), ctxA.attempt().getId());
                            });
            assertThat(waiterReadyA.await(15, TimeUnit.SECONDS)).isTrue();
            awaitAudioRowLockWaiters(1);
            commitUpdateA.countDown();
            holderA.get(15, TimeUnit.SECONDS);

            MvcResult getResA = getFutureA.get(15, TimeUnit.SECONDS);
            assertThat(getResA.getResponse().getStatus()).isEqualTo(200);
            JsonNode apA = json(getResA).path("audioPlays").get(0);
            assertThat(apA.path("state").asText()).isEqualTo("ENDED");
            assertThat(apA.path("endReason").asText()).isEqualTo("FINISHED");
            assertThat(apA.path("playId").asText()).isEqualTo(playAId.toString());

            // Case B: Two parallel GET calls both reconcile a stale PLAYING row on a closed part -> both 200.
            var ctxB = startSingleAudio("aud-conc-state-b");
            performOpenPlay(
                    ctxB.userId(),
                    ctxB.attempt().getId(),
                    ctxB.assetId(),
                    ctxB.sessionId(),
                    "gs-b1");
            AttemptPart partB =
                    attemptPartRepository
                            .findByAttemptIdOrderByOrdAsc(ctxB.attempt().getId())
                            .getFirst();
            assertThat(
                            performFinishPart(
                                            ctxB.userId(),
                                            ctxB.attempt().getId(),
                                            partB.getId(),
                                            ctxB.sessionId())
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(200);
            jdbc.update(
                    "UPDATE attempt.attempt_audio_play SET state = 'PLAYING', end_reason = NULL WHERE attempt_id = ? AND asset_id = ?",
                    ctxB.attempt().getId(),
                    ctxB.assetId());

            CountDownLatch lockHeldB = new CountDownLatch(1);
            CountDownLatch releaseLockB = new CountDownLatch(1);
            Future<Void> holderB =
                    holdAudioRowLock(
                            pool, ctxB.attempt().getId(), ctxB.assetId(), lockHeldB, releaseLockB);
            assertThat(lockHeldB.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> g1 =
                    pool.submit(() -> performGetState(ctxB.userId(), ctxB.attempt().getId()));
            Future<MvcResult> g2 =
                    pool.submit(() -> performGetState(ctxB.userId(), ctxB.attempt().getId()));
            awaitAudioRowLockWaiters(2);
            releaseLockB.countDown();
            holderB.get(15, TimeUnit.SECONDS);

            MvcResult gr1 = g1.get(15, TimeUnit.SECONDS);
            MvcResult gr2 = g2.get(15, TimeUnit.SECONDS);
            assertThat(gr1.getResponse().getStatus()).isEqualTo(200);
            assertThat(gr2.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(gr1).path("audioPlays").get(0).path("state").asText())
                    .isEqualTo("ENDED");
            assertThat(json(gr1).path("audioPlays").get(0).path("endReason").asText())
                    .isEqualTo("CLOSED");
            assertThat(json(gr2).path("audioPlays").get(0).path("state").asText())
                    .isEqualTo("ENDED");
            assertThat(json(gr2).path("audioPlays").get(0).path("endReason").asText())
                    .isEqualTo("CLOSED");
            AttemptAudioPlay finalB =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctxB.attempt().getId(), ctxB.assetId())
                            .orElseThrow();
            assertThat(finalB.getState()).isEqualTo(AudioPlayState.ENDED);
            assertThat(finalB.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
            assertThat(finalB.getLeaseUntil()).isNull();
        } finally {
            commitUpdateA.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void playingPlayClosedAndCommittedInLockBefore409() throws Exception {
        // Case A: Deadline race with active PLAYING row -> commits ENDED/CLOSED + lease_until=NULL before 409 TIME_UP.
        var ctxA = startSingleAudio("aud-conc-inlock-dl");
        UUID playAId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctxA.userId(),
                                                ctxA.attempt().getId(),
                                                ctxA.assetId(),
                                                ctxA.sessionId(),
                                                "il-dl"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(4));
        assertThat(
                        performProgress(
                                        ctxA.userId(),
                                        ctxA.attempt().getId(),
                                        ctxA.assetId(),
                                        playAId,
                                        ctxA.sessionId(),
                                        4_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        mutableClock.setAt(ctxA.attempt().getDeadlineAt().minusSeconds(1));
        CountDownLatch lockHeldA = new CountDownLatch(1);
        CountDownLatch releaseLockA = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<Void> holderA =
                    holdAudioRowLock(
                            pool, ctxA.attempt().getId(), ctxA.assetId(), lockHeldA, releaseLockA);
            assertThat(lockHeldA.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> resumeFutureA =
                    pool.submit(
                            () ->
                                    performResumePlay(
                                            ctxA.userId(),
                                            ctxA.attempt().getId(),
                                            ctxA.assetId(),
                                            playAId,
                                            ctxA.sessionId()));
            awaitAudioRowLockWaiters(1);

            mutableClock.setAt(ctxA.attempt().getDeadlineAt().plusSeconds(1));
            releaseLockA.countDown();
            holderA.get(15, TimeUnit.SECONDS);

            MvcResult resumeResA = resumeFutureA.get(15, TimeUnit.SECONDS);
            assertThat(resumeResA.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(resumeResA).path("code").asText()).isEqualTo("TIME_UP");

            AttemptAudioPlay savedA =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctxA.attempt().getId(), ctxA.assetId())
                            .orElseThrow();
            assertThat(savedA.getState()).isEqualTo(AudioPlayState.ENDED);
            assertThat(savedA.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
            assertThat(savedA.getLeaseUntil()).isNull();
            assertThat(savedA.getPositionMs()).isEqualTo(4_000L);

            // Case B: Closed part without overdue closer (deadline not reached, triggerOverdueClose=false):
            // proves AttemptAudioService itself commits PLAYING -> ENDED/CLOSED before throwing 409 PART_CLOSED.
            mutableClock.setAt(
                    vn.unicom.thi360.attempt.AttemptTestFixtures.DEFAULT_CLOCK_START);
            var ctxB = startSingleAudio("aud-conc-inlock-cp");
            UUID playBId =
                    UUID.fromString(
                            json(
                                            performOpenPlay(
                                                    ctxB.userId(),
                                                    ctxB.attempt().getId(),
                                                    ctxB.assetId(),
                                                    ctxB.sessionId(),
                                                    "il-cp"))
                                    .path("playId")
                                    .asText());
            AttemptPart partB =
                    attemptPartRepository
                            .findByAttemptIdOrderByOrdAsc(ctxB.attempt().getId())
                            .getFirst();

            CountDownLatch lockHeldB = new CountDownLatch(1);
            CountDownLatch releaseLockB = new CountDownLatch(1);
            Future<Void> holderB =
                    holdAudioRowLock(
                            pool, ctxB.attempt().getId(), ctxB.assetId(), lockHeldB, releaseLockB);
            assertThat(lockHeldB.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> progFutureB =
                    pool.submit(
                            () ->
                                    performProgress(
                                            ctxB.userId(),
                                            ctxB.attempt().getId(),
                                            ctxB.assetId(),
                                            playBId,
                                            ctxB.sessionId(),
                                            1_000L));
            awaitAudioRowLockWaiters(1);

            // Close part B in DB while caller is blocked on attempt_audio_play FOR UPDATE (play is still PLAYING).
            jdbc.update(
                    "UPDATE attempt.attempt_part SET state = 'CLOSED', close_reason = 'FINISHED', closed_at = now() WHERE id = ?",
                    partB.getId());
            releaseLockB.countDown();
            holderB.get(15, TimeUnit.SECONDS);

            MvcResult progResB = progFutureB.get(15, TimeUnit.SECONDS);
            assertThat(progResB.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(progResB).path("code").asText()).isEqualTo("PART_CLOSED");

            AttemptAudioPlay savedB =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctxB.attempt().getId(), ctxB.assetId())
                            .orElseThrow();
            assertThat(savedB.getState()).isEqualTo(AudioPlayState.ENDED);
            assertThat(savedB.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
            assertThat(savedB.getLeaseUntil()).isNull();
            assertThat(savedB.getPositionMs()).isZero();
        } finally {
            releaseLockA.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void twoDevicesTakeoverRaceRevokesOldSessionWithoutAudioMutation() throws Exception {
        var ctx = startSingleAudio("aud-conc-takeover");

        // 1. Device A (S1) starts openPlay and blocks on attempt_audio_play FOR UPDATE while Device B takes over (S2).
        CountDownLatch lockHeld1 = new CountDownLatch(1);
        CountDownLatch releaseLock1 = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> holder1 =
                    holdAudioRowLock(
                            pool, ctx.attempt().getId(), ctx.assetId(), lockHeld1, releaseLock1);
            assertThat(lockHeld1.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> devAOpen =
                    pool.submit(
                            () ->
                                    performOpenPlay(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            ctx.sessionId(),
                                            "dev-a-open"));
            awaitAudioRowLockWaiters(1);

            UUID s2 = takeoverSession(ctx.userId(), ctx.attempt().getId());
            releaseLock1.countDown();
            holder1.get(15, TimeUnit.SECONDS);

            MvcResult devARes = devAOpen.get(15, TimeUnit.SECONDS);
            assertThat(devARes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(devARes).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");

            AttemptAudioPlay afterRace1 =
                    attemptAudioPlayRepository
                            .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                            .orElseThrow();
            assertThat(afterRace1.getState()).isEqualTo(AudioPlayState.IDLE);
            assertThat(afterRace1.getPlayCount()).isZero();
            assertThat(afterRace1.getPlayId()).isNull();

            // Device B (S2) opens cleanly
            MvcResult devBOpen =
                    performOpenPlay(
                            ctx.userId(),
                            ctx.attempt().getId(),
                            ctx.assetId(),
                            s2,
                            "dev-b-open");
            assertThat(devBOpen.getResponse().getStatus()).isEqualTo(200);
            UUID playId = UUID.fromString(json(devBOpen).path("playId").asText());

            // 2. Device B (S2) starts progress while Device A re-takes over (S3) -> S2 rejected 409 without mutating position
            CountDownLatch lockHeld2 = new CountDownLatch(1);
            CountDownLatch releaseLock2 = new CountDownLatch(1);
            Future<Void> holder2 =
                    holdAudioRowLock(
                            pool, ctx.attempt().getId(), ctx.assetId(), lockHeld2, releaseLock2);
            assertThat(lockHeld2.await(15, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> devBProg =
                    pool.submit(
                            () ->
                                    performProgress(
                                            ctx.userId(),
                                            ctx.attempt().getId(),
                                            ctx.assetId(),
                                            playId,
                                            s2,
                                            2_000L));
            awaitAudioRowLockWaiters(1);

            UUID s3 = takeoverSession(ctx.userId(), ctx.attempt().getId());
            releaseLock2.countDown();
            holder2.get(15, TimeUnit.SECONDS);

            MvcResult devBProgRes = devBProg.get(15, TimeUnit.SECONDS);
            assertThat(devBProgRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(devBProgRes).path("code").asText())
                    .isEqualTo("ATTEMPT_SESSION_REVOKED");
            assertThat(
                            attemptAudioPlayRepository
                                    .findByAttemptIdAndAssetId(
                                            ctx.attempt().getId(), ctx.assetId())
                                    .orElseThrow()
                                    .getPositionMs())
                    .isZero();

            MvcResult devAProgS3 =
                    performProgress(
                            ctx.userId(),
                            ctx.attempt().getId(),
                            ctx.assetId(),
                            playId,
                            s3,
                            2_000L);
            assertThat(devAProgS3.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(devAProgS3).path("positionMs").asLong()).isEqualTo(2_000L);
        } finally {
            releaseLock1.countDown();
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
