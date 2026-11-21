package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.api.AttemptAudioController.AudioProgressRequest;
import vn.unicom.thi360.attempt.api.AttemptAudioController.StartAudioPlayRequest;
import vn.unicom.thi360.attempt.application.AttemptDeadlineWorker;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AudioEndReason;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for audio playback lifecycle ({@code plays}, {@code resume}, {@code progress},
 * {@code end}), anti-seek tolerance, closed-part matrix, session/role security, and forbidden key
 * invariants (BS §17.2, AC-1..4, AC-6..8, AC-12, AC-13, AC-18, AC-19).
 */
class AttemptAudioIT extends AbstractAttemptAudioIT {

    @Autowired private AttemptDeadlineWorker deadlineWorker;

    private record SingleAudioSession(
            UUID userId,
            Attempt attempt,
            UUID sessionId,
            List<AttemptPart> parts,
            UUID assetId) {}

    private SingleAudioSession startSingle30sAudioAttempt(String userLabel, String examCode)
            throws Exception {
        UUID userId = createStudent(userLabel);
        var cbr = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID qId = createReadyListeningQuestion(cbr.assetId(), userLabel.toUpperCase());
        stubListeningPaperWithQuestions(List.of(qId));

        GrantOrderResult.Line line = grant(userId, examCode, 3);
        Attempt attempt = startAndAwaitOpen(userId, userLabel, line.entitlementId());
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        return new SingleAudioSession(userId, attempt, sid, parts, cbr.assetId());
    }

    @Test
    void playsAreCountedUpToTwo() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac1-up2", "DS-HSCA-ANH-MINI");

        MvcResult p1 =
                performOpenPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), ctx.sessionId(), "req-1");
        assertThat(p1.getResponse().getStatus()).isEqualTo(200);
        JsonNode p1Json = json(p1);
        UUID play1Id = UUID.fromString(p1Json.path("playId").asText());
        assertThat(p1Json.path("playsUsed").asInt()).isEqualTo(1);
        assertThat(p1Json.path("playsLeft").asInt()).isEqualTo(1);
        assertThat(p1Json.path("positionMs").asLong()).isZero();
        assertThat(p1Json.path("durationMs").asLong()).isEqualTo(30_000L);
        assertThat(p1Json.path("state").asText()).isEqualTo("PLAYING");
        assertThat(p1Json.has("endReason")).isFalse();

        MvcResult end1 =
                performEndPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), play1Id, ctx.sessionId());
        assertThat(end1.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(end1).path("state").asText()).isEqualTo("ENDED");
        assertThat(json(end1).path("endReason").asText()).isEqualTo("FINISHED");

        MvcResult p2 =
                performOpenPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), ctx.sessionId(), "req-2");
        assertThat(p2.getResponse().getStatus()).isEqualTo(200);
        JsonNode p2Json = json(p2);
        UUID play2Id = UUID.fromString(p2Json.path("playId").asText());
        assertThat(play2Id).isNotEqualTo(play1Id);
        assertThat(p2Json.path("playsUsed").asInt()).isEqualTo(2);
        assertThat(p2Json.path("playsLeft").asInt()).isZero();
        assertThat(p2Json.path("positionMs").asLong()).isZero();
        assertThat(p2Json.path("durationMs").asLong()).isEqualTo(30_000L);
        assertThat(p2Json.path("state").asText()).isEqualTo("PLAYING");
    }

    @Test
    void thirdPlayIsRejected() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac1-3rd", "DS-HSCA-ANH-MINI");

        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "r1"))
                                .path("playId")
                                .asText());
        assertThat(
                        performEndPlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        p1,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        UUID p2 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "r2"))
                                .path("playId")
                                .asText());
        assertThat(
                        performEndPlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        p2,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult p3 =
                performOpenPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), ctx.sessionId(), "r3");
        assertThat(p3.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(p3).path("code").asText()).isEqualTo("AUDIO_PLAYS_EXHAUSTED");

        AttemptAudioPlay saved =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                        .orElseThrow();
        assertThat(saved.getPlayCount()).isEqualTo(2);
        assertThat(saved.playsLeft()).isZero();
        assertThat(saved.getState()).isEqualTo(AudioPlayState.ENDED);
    }

    @Test
    void retriedOpenIsIdempotent() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac2-idem", "DS-HSCA-ANH-MINI");

        MvcResult first =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        "retry-req-1");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        JsonNode firstJson = json(first);

        mutableClock.advance(Duration.ofSeconds(4));
        performProgress(
                ctx.userId(),
                ctx.attempt().getId(),
                ctx.assetId(),
                UUID.fromString(firstJson.path("playId").asText()),
                ctx.sessionId(),
                4_000L);

        MvcResult retry =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        "retry-req-1");
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        JsonNode retryJson = json(retry);
        assertThat(retryJson.path("playId").asText()).isEqualTo(firstJson.path("playId").asText());
        assertThat(retryJson.path("playsUsed").asInt()).isEqualTo(1);
        assertThat(retryJson.path("playsLeft").asInt()).isEqualTo(1);
        assertThat(retryJson.path("positionMs").asLong()).isEqualTo(4_000L);
    }

    @Test
    void openWhilePlayingIsRejected() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac2-inprog", "DS-HSCA-ANH-MINI");

        MvcResult first =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        "open-1");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String activePlayId = json(first).path("playId").asText();

        MvcResult second =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        "open-2-different");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        JsonNode err = json(second);
        assertThat(err.path("code").asText()).isEqualTo("AUDIO_PLAY_IN_PROGRESS");
        assertThat(err.path("playId").asText()).isEqualTo(activePlayId);
        assertThat(err.path("message").asText()).isNotBlank();
        assertThat(err.has("fieldErrors")).isTrue();
        assertThat(err.has("traceId")).isTrue();

        AttemptAudioPlay saved =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                        .orElseThrow();
        assertThat(saved.getPlayCount()).isEqualTo(1);
    }

    @Test
    void resumeNeverUsesPlay() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac3-resume", "DS-HSCA-ANH-MINI");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "res-open"))
                                .path("playId")
                                .asText());

        mutableClock.advance(Duration.ofSeconds(6));
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        6_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        for (int i = 1; i <= 4; i++) {
            mutableClock.advance(Duration.ofSeconds(5));
            MvcResult res =
                    performResumePlay(
                            ctx.userId(),
                            ctx.attempt().getId(),
                            ctx.assetId(),
                            playId,
                            ctx.sessionId());
            assertThat(res.getResponse().getStatus()).isEqualTo(200);
            JsonNode j = json(res);
            assertThat(j.path("playId").asText()).isEqualTo(playId.toString());
            assertThat(j.path("playsUsed").asInt()).isEqualTo(1);
            assertThat(j.path("playsLeft").asInt()).isEqualTo(1);
            assertThat(j.path("positionMs").asLong()).isEqualTo(6_000L);
            assertThat(j.path("state").asText()).isEqualTo("PLAYING");
        }
    }

    @Test
    void takeoverResumesSamePlay() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac3-takeover", "DS-HSCA-ANH-MINI");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "tk-open"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(7));
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        7_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        UUID newSessionId = takeoverSession(ctx.userId(), ctx.attempt().getId());

        MvcResult oldDeviceResume =
                performResumePlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), playId, ctx.sessionId());
        assertThat(oldDeviceResume.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(oldDeviceResume).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");

        MvcResult newDeviceResume =
                performResumePlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), playId, newSessionId);
        assertThat(newDeviceResume.getResponse().getStatus()).isEqualTo(200);
        JsonNode resumed = json(newDeviceResume);
        assertThat(resumed.path("playId").asText()).isEqualTo(playId.toString());
        assertThat(resumed.path("playsUsed").asInt()).isEqualTo(1);
        assertThat(resumed.path("playsLeft").asInt()).isEqualTo(1);
        assertThat(resumed.path("positionMs").asLong()).isEqualTo(7_000L);
        assertThat(resumed.path("state").asText()).isEqualTo("PLAYING");
    }

    @Test
    void resumeAfterLongGapWhilePartOpen() throws Exception {
        // DS-HSCA-ANH has 60-minute shared duration so a 30-minute pause leaves Listening OPEN.
        var ctx = startSingle30sAudioAttempt("aud-ac4-gap30m", "DS-HSCA-ANH");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "gap-open"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(11));
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        11_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        mutableClock.advance(Duration.ofMinutes(30));

        MvcResult resumed =
                performResumePlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), playId, ctx.sessionId());
        assertThat(resumed.getResponse().getStatus()).isEqualTo(200);
        JsonNode rj = json(resumed);
        assertThat(rj.path("playId").asText()).isEqualTo(playId.toString());
        assertThat(rj.path("positionMs").asLong()).isEqualTo(11_000L);
        assertThat(rj.path("playsUsed").asInt()).isEqualTo(1);
        assertThat(rj.path("playsLeft").asInt()).isEqualTo(1);

        mutableClock.advance(Duration.ofSeconds(4));
        MvcResult progAfterResume =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        15_000L);
        assertThat(progAfterResume.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(progAfterResume).path("positionMs").asLong()).isEqualTo(15_000L);
    }

    @Test
    void openAfterPartClosedWithoutEnd() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac4-closed", "DS-HSCA-ANH-MINI");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "no-end-req"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(5));
        performProgress(
                ctx.userId(),
                ctx.attempt().getId(),
                ctx.assetId(),
                playId,
                ctx.sessionId(),
                5_000L);

        assertThat(
                        performFinishPart(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.parts().getFirst().getId(),
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult openAfter =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        "new-req-after-close");
        assertThat(openAfter.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(openAfter).path("code").asText()).isEqualTo("PART_CLOSED");

        MvcResult resumeAfter =
                performResumePlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId());
        assertThat(resumeAfter.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(resumeAfter).path("code").asText()).isEqualTo("PART_CLOSED");

        AttemptAudioPlay saved =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                        .orElseThrow();
        assertThat(saved.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(saved.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
        assertThat(saved.getPlayCount()).isEqualTo(1);
    }

    @Test
    void retryOldRequestAfterCloseUsesNoPlay() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac4-retry-close", "DS-HSCA-ANH-MINI");

        String originalReqId = "orig-req-before-close";
        MvcResult opened =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        originalReqId);
        assertThat(opened.getResponse().getStatus()).isEqualTo(200);

        assertThat(
                        performFinishPart(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.parts().getFirst().getId(),
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult retriedAfterClose =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        originalReqId);
        assertThat(retriedAfterClose.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(retriedAfterClose).path("code").asText()).isEqualTo("PART_CLOSED");

        AttemptAudioPlay saved =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                        .orElseThrow();
        assertThat(saved.getPlayCount()).isEqualTo(1);
        assertThat(saved.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(saved.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
    }

    @Test
    void partCloseBoundaries() throws Exception {
        // 1. 1 ms BEFORE deadline: open and progress succeed (200)
        var cBefore = startSingle30sAudioAttempt("aud-ac4-b1", "DS-HSCA-ANH-MINI");
        Instant deadline1 = cBefore.attempt().getDeadlineAt();
        mutableClock.setAt(deadline1.minusMillis(1));
        MvcResult openAtMinus1 =
                performOpenPlay(
                        cBefore.userId(),
                        cBefore.attempt().getId(),
                        cBefore.assetId(),
                        cBefore.sessionId(),
                        "b-minus-1");
        assertThat(openAtMinus1.getResponse().getStatus()).isEqualTo(200);
        UUID p1 = UUID.fromString(json(openAtMinus1).path("playId").asText());
        assertThat(
                        performProgress(
                                        cBefore.userId(),
                                        cBefore.attempt().getId(),
                                        cBefore.assetId(),
                                        p1,
                                        cBefore.sessionId(),
                                        1_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        // 2. AT exact deadline (==): rejected 409 TIME_UP and PLAYING play closes to ENDED/CLOSED
        mutableClock.setAt(deadline1);
        MvcResult resumeAtExact =
                performResumePlay(
                        cBefore.userId(),
                        cBefore.attempt().getId(),
                        cBefore.assetId(),
                        p1,
                        cBefore.sessionId());
        assertThat(resumeAtExact.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(resumeAtExact).path("code").asText()).isEqualTo("TIME_UP");
        AttemptAudioPlay closedAtExact =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(cBefore.attempt().getId(), cBefore.assetId())
                        .orElseThrow();
        assertThat(closedAtExact.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(closedAtExact.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
        assertThat(closedAtExact.getPlayCount()).isEqualTo(1);

        // 3. 1 ms AFTER deadline (+1ms): open rejected 409 TIME_UP without starting a play
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        var cAfter = startSingle30sAudioAttempt("aud-ac4-b2", "DS-HSCA-ANH-MINI");
        Instant deadline2 = cAfter.attempt().getDeadlineAt();
        mutableClock.setAt(deadline2.plusMillis(1));
        MvcResult openAtPlus1 =
                performOpenPlay(
                        cAfter.userId(),
                        cAfter.attempt().getId(),
                        cAfter.assetId(),
                        cAfter.sessionId(),
                        "b-plus-1");
        assertThat(openAtPlus1.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(openAtPlus1).path("code").asText()).isEqualTo("TIME_UP");
        AttemptAudioPlay idleAfter =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(cAfter.attempt().getId(), cAfter.assetId())
                        .orElseThrow();
        assertThat(idleAfter.getState()).isEqualTo(AudioPlayState.IDLE);
        assertThat(idleAfter.getPlayCount()).isZero();
    }

    @Test
    void progressFollowingRealTimeAccepted() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac6-rt", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "rt-1"))
                                .path("playId")
                                .asText());

        for (long sec : List.of(5L, 10L, 15L, 25L)) {
            mutableClock.advance(Duration.ofSeconds(sec == 25L ? 10 : 5));
            MvcResult r =
                    performProgress(
                            ctx.userId(),
                            ctx.attempt().getId(),
                            ctx.assetId(),
                            playId,
                            ctx.sessionId(),
                            sec * 1_000L);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(r).path("positionMs").asLong()).isEqualTo(sec * 1_000L);
            assertThat(json(r).path("state").asText()).isEqualTo("PLAYING");
        }

        mutableClock.advance(Duration.ofSeconds(5));
        MvcResult endByDuration =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        30_000L);
        assertThat(endByDuration.getResponse().getStatus()).isEqualTo(200);
        JsonNode done = json(endByDuration);
        assertThat(done.path("positionMs").asLong()).isEqualTo(30_000L);
        assertThat(done.path("state").asText()).isEqualTo("ENDED");
        assertThat(done.path("endReason").asText()).isEqualTo("FINISHED");
    }

    @Test
    void backwardSeekRejected() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac6-back", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "back-1"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(10));
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        10_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult back =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        9_999L);
        assertThat(back.getResponse().getStatus()).isEqualTo(409);
        JsonNode err = json(back);
        assertThat(err.path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
        assertThat(err.path("positionMs").asLong()).isEqualTo(10_000L);

        AttemptAudioPlay saved =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                        .orElseThrow();
        assertThat(saved.getPositionMs()).isEqualTo(10_000L);
    }

    @Test
    void outOfRangeRejected() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac6-oor", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "oor-1"))
                                .path("playId")
                                .asText());

        mutableClock.advance(Duration.ofSeconds(2));
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

        MvcResult neg =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        -1L);
        assertThat(neg.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(neg).path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
        assertThat(json(neg).path("positionMs").asLong()).isEqualTo(2_000L);

        mutableClock.advance(Duration.ofSeconds(30));
        MvcResult overDuration =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        30_001L);
        assertThat(overDuration.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(overDuration).path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
        assertThat(json(overDuration).path("positionMs").asLong()).isEqualTo(2_000L);
    }

    @Test
    void forwardSeekBeyondCeilingRejected() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac6-fwd", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "fwd-1"))
                                .path("playId")
                                .asText());

        MvcResult jump =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        5_000L);
        assertThat(jump.getResponse().getStatus()).isEqualTo(409);
        JsonNode err = json(jump);
        assertThat(err.path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
        assertThat(err.path("positionMs").asLong()).isZero();
    }

    @Test
    void toleranceBoundaryAccepted() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac6-tol-bound", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "tol-b"))
                                .path("playId")
                                .asText());

        mutableClock.advance(Duration.ofSeconds(10));
        // expected = 10_000, remaining tolerance = 3_000 -> ceiling = 13_000
        MvcResult rejectPlus1 =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        13_001L);
        assertThat(rejectPlus1.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(rejectPlus1).path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
        assertThat(json(rejectPlus1).path("positionMs").asLong()).isZero();

        MvcResult exactCeiling =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        13_000L);
        assertThat(exactCeiling.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(exactCeiling).path("positionMs").asLong()).isEqualTo(13_000L);
    }

    @Test
    void toleranceIsNotCumulative() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac7-noncum", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "noncum-1"))
                                .path("playId")
                                .asText());

        long acceptedMax = 0L;
        for (int i = 1; i <= 100; i++) {
            long targetPos = i * 50L;
            MvcResult res =
                    performProgress(
                            ctx.userId(),
                            ctx.attempt().getId(),
                            ctx.assetId(),
                            playId,
                            ctx.sessionId(),
                            targetPos);
            if (targetPos <= 3_000L) {
                assertThat(res.getResponse().getStatus()).isEqualTo(200);
                acceptedMax = targetPos;
            } else {
                assertThat(res.getResponse().getStatus()).isEqualTo(409);
                assertThat(json(res).path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
                assertThat(json(res).path("positionMs").asLong()).isEqualTo(3_000L);
            }
        }
        assertThat(acceptedMax).isEqualTo(3_000L);
    }

    @Test
    void resumeDoesNotRefillSeekTolerance() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac7-resrefill", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "rr-1"))
                                .path("playId")
                                .asText());

        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        1_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performResumePlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        2_200L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performResumePlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
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
        assertThat(
                        performResumePlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        playId,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult over =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        3_001L);
        assertThat(over.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(over).path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");
        assertThat(json(over).path("positionMs").asLong()).isEqualTo(3_000L);
    }

    @Test
    void newPlayGetsFreshTolerance() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac7-newplay", "DS-HSCA-ANH-MINI");
        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "np-1"))
                                .path("playId")
                                .asText());
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        p1,
                                        ctx.sessionId(),
                                        3_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performEndPlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        p1,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        UUID p2 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "np-2"))
                                .path("playId")
                                .asText());
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        p2,
                                        ctx.sessionId(),
                                        3_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
    }

    @Test
    void staleEventsCannotTouchNewPlay() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac8-stale", "DS-HSCA-ANH-MINI");

        // While IDLE: stale playId rejected without touching state
        MvcResult idleEnd =
                performEndPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        UUID.randomUUID(),
                        ctx.sessionId());
        assertThat(idleEnd.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(idleEnd).path("code").asText()).isEqualTo("AUDIO_PLAY_STALE");

        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "s-1"))
                                .path("playId")
                                .asText());
        assertThat(
                        performEndPlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        p1,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        UUID p2 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "s-2"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(5));
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.assetId(),
                                        p2,
                                        ctx.sessionId(),
                                        5_000L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        for (MvcResult stale :
                List.of(
                        performProgress(
                                ctx.userId(),
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                p1,
                                ctx.sessionId(),
                                10_000L),
                        performResumePlay(
                                ctx.userId(),
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                p1,
                                ctx.sessionId()),
                        performEndPlay(
                                ctx.userId(),
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                p1,
                                ctx.sessionId()))) {
            assertThat(stale.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(stale).path("code").asText()).isEqualTo("AUDIO_PLAY_STALE");
        }

        AttemptAudioPlay saved =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                        .orElseThrow();
        assertThat(saved.getPlayId()).isEqualTo(p2);
        assertThat(saved.getState()).isEqualTo(AudioPlayState.PLAYING);
        assertThat(saved.getPositionMs()).isEqualTo(5_000L);
        assertThat(saved.getPlayCount()).isEqualTo(2);
    }

    @Test
    void endIsIdempotent() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac8-end-idem", "DS-HSCA-ANH-MINI");
        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "ei-1"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(8));
        performProgress(
                ctx.userId(),
                ctx.attempt().getId(),
                ctx.assetId(),
                p1,
                ctx.sessionId(),
                8_000L);

        MvcResult firstEnd =
                performEndPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), p1, ctx.sessionId());
        assertThat(firstEnd.getResponse().getStatus()).isEqualTo(200);
        MvcResult secondEnd =
                performEndPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), p1, ctx.sessionId());
        assertThat(secondEnd.getResponse().getStatus()).isEqualTo(200);
        JsonNode j2 = json(secondEnd);
        assertThat(j2.path("playId").asText()).isEqualTo(p1.toString());
        assertThat(j2.path("state").asText()).isEqualTo("ENDED");
        assertThat(j2.path("endReason").asText()).isEqualTo("FINISHED");
        assertThat(j2.path("positionMs").asLong()).isEqualTo(8_000L);
        assertThat(j2.path("playsUsed").asInt()).isEqualTo(1);
    }

    @Test
    void closingPartEndsPlay() throws Exception {
        // 1. Finish part while PLAYING -> ENDED/CLOSED
        var cFin = startSingle30sAudioAttempt("aud-ac12-fin", "DS-HSCA-ANH-MINI");
        performOpenPlay(
                cFin.userId(), cFin.attempt().getId(), cFin.assetId(), cFin.sessionId(), "fin-p");
        assertThat(
                        performFinishPart(
                                        cFin.userId(),
                                        cFin.attempt().getId(),
                                        cFin.parts().getFirst().getId(),
                                        cFin.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        AttemptAudioPlay afterFin =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(cFin.attempt().getId(), cFin.assetId())
                        .orElseThrow();
        assertThat(afterFin.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(afterFin.getEndReason()).isEqualTo(AudioEndReason.CLOSED);

        // 2. Submit attempt while PLAYING (make Listening open & non-sequential or single-part submit)
        var cSub = startSingle30sAudioAttempt("aud-ac12-sub", "DS-HSCA-ANH-MINI");
        assertThat(
                        performFinishPart(
                                        cSub.userId(),
                                        cSub.attempt().getId(),
                                        cSub.parts().getFirst().getId(),
                                        cSub.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        // Simulate a PLAYING row on part 1 / or FREE-mode submit closing an open listening part
        jdbc.update(
                "UPDATE attempt.attempt SET navigation_mode = 'FREE' WHERE id = ?",
                cSub.attempt().getId());
        jdbc.update(
                "UPDATE attempt.attempt_part SET state = 'OPEN', closed_at = NULL, close_reason = NULL WHERE id = ?",
                cSub.parts().getFirst().getId());
        jdbc.update(
                "UPDATE attempt.attempt_audio_play SET state = 'PLAYING', end_reason = NULL, play_count = 1, play_id = ? WHERE attempt_id = ? AND asset_id = ?",
                UUID.randomUUID(),
                cSub.attempt().getId(),
                cSub.assetId());
        assertThat(
                        performSubmit(
                                        cSub.userId(),
                                        cSub.attempt().getId(),
                                        "ac12-sub-k",
                                        cSub.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        AttemptAudioPlay afterSub =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(cSub.attempt().getId(), cSub.assetId())
                        .orElseThrow();
        assertThat(afterSub.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(afterSub.getEndReason()).isEqualTo(AudioEndReason.CLOSED);

        // 3. Deadline expiry via worker while PLAYING -> ENDED/CLOSED
        var cExp = startSingle30sAudioAttempt("aud-ac12-exp", "DS-HSCA-ANH-MINI");
        performOpenPlay(
                cExp.userId(), cExp.attempt().getId(), cExp.assetId(), cExp.sessionId(), "exp-p");
        mutableClock.setAt(cExp.attempt().getDeadlineAt().plusSeconds(1));
        assertThat(deadlineWorker.sweepOverdue()).isGreaterThanOrEqualTo(1);
        AttemptAudioPlay afterExp =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(cExp.attempt().getId(), cExp.assetId())
                        .orElseThrow();
        assertThat(afterExp.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(afterExp.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
        assertThat(afterExp.getLeaseUntil()).isNull();
    }

    @Test
    void revokedSessionRejected() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac13-rev", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "rev-open"))
                                .path("playId")
                                .asText());
        takeoverSession(ctx.userId(), ctx.attempt().getId());

        for (MvcResult r :
                List.of(
                        performOpenPlay(
                                ctx.userId(),
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                ctx.sessionId(),
                                "rev-2"),
                        performResumePlay(
                                ctx.userId(),
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                playId,
                                ctx.sessionId()),
                        performProgress(
                                ctx.userId(),
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                playId,
                                ctx.sessionId(),
                                1_000L),
                        performEndPlay(
                                ctx.userId(),
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                playId,
                                ctx.sessionId()))) {
            assertThat(r.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(r).path("code").asText()).isEqualTo("ATTEMPT_SESSION_REVOKED");
        }
    }

    @Test
    void ownershipAndAccess() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac13-owner", "DS-HSCA-ANH-MINI");
        UUID stranger = createStudent("aud-ac13-stranger");
        UUID manager = createManager("aud-ac13-mgr");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "own-open"))
                                .path("playId")
                                .asText());

        String base =
                "/api/v1/me/attempts/" + ctx.attempt().getId() + "/audio/" + ctx.assetId();
        var calls =
                List.of(
                        post(base + "/plays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new StartAudioPlayRequest("x"))),
                        post(base + "/plays/{playId}/resume", playId),
                        put(base + "/plays/{playId}/progress", playId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new AudioProgressRequest(1_000L))),
                        post(base + "/plays/{playId}/end", playId));

        for (var builder : calls) {
            // Rebuild fresh requests via helper for each identity
            assertThat(mockMvc.perform(builder).andReturn().getResponse().getStatus())
                    .isEqualTo(401);
        }

        for (MvcResult mgrRes :
                List.of(
                        mockMvc.perform(
                                        post(base + "/plays")
                                                .with(AttemptTestFixtures.asManageUser(manager, "m"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        objectMapper.writeValueAsString(
                                                                new StartAudioPlayRequest("m"))))
                                .andReturn(),
                        mockMvc.perform(
                                        post(base + "/plays/{playId}/resume", playId)
                                                .with(AttemptTestFixtures.asManageUser(manager, "m")))
                                .andReturn(),
                        mockMvc.perform(
                                        put(base + "/plays/{playId}/progress", playId)
                                                .with(AttemptTestFixtures.asManageUser(manager, "m"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        objectMapper.writeValueAsString(
                                                                new AudioProgressRequest(1_000L))))
                                .andReturn(),
                        mockMvc.perform(
                                        post(base + "/plays/{playId}/end", playId)
                                                .with(AttemptTestFixtures.asManageUser(manager, "m")))
                                .andReturn())) {
            assertThat(mgrRes.getResponse().getStatus()).isEqualTo(403);
            assertThat(json(mgrRes).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");
        }

        for (MvcResult otherRes :
                List.of(
                        performOpenPlay(
                                stranger, ctx.attempt().getId(), ctx.assetId(), ctx.sessionId(), "s"),
                        performResumePlay(
                                stranger, ctx.attempt().getId(), ctx.assetId(), playId, ctx.sessionId()),
                        performProgress(
                                stranger,
                                ctx.attempt().getId(),
                                ctx.assetId(),
                                playId,
                                ctx.sessionId(),
                                1_000L),
                        performEndPlay(
                                stranger, ctx.attempt().getId(), ctx.assetId(), playId, ctx.sessionId()))) {
            assertThat(otherRes.getResponse().getStatus()).isEqualTo(404);
            assertThat(json(otherRes).path("code").asText()).isEqualTo("NOT_FOUND");
        }
    }

    @Test
    void forbiddenKeysAbsent() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac18-forbid", "DS-HSCA-ANH-MINI");

        clearInvocations(questionAttachmentPort);
        MvcResult stateRes = performGetState(ctx.userId(), ctx.attempt().getId());
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        String stateRaw = stateRes.getResponse().getContentAsString();
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(stateRaw);
        verify(questionAttachmentPort).attachments(any(), eq(false));
        verify(questionAttachmentPort, never()).attachments(any(), eq(true));

        JsonNode stateJson = objectMapper.readTree(stateRaw);
        assertThat(stateJson.path("audioPolicy").path("maxPlays").asInt()).isEqualTo(2);
        assertThat(stateJson.path("audioPolicy").path("seekable").asBoolean()).isFalse();
        assertThat(stateJson.path("audioPlays")).hasSize(1);
        assertThat(stateJson.path("audioPlays").get(0).path("assetId").asText())
                .isEqualTo(ctx.assetId().toString());
        assertThat(stateJson.path("audioPlays").get(0).path("state").asText()).isEqualTo("IDLE");
        assertThat(stateJson.path("audioPlays").get(0).has("playId")).isFalse();
        JsonNode rootAtts =
                stateJson.path("parts").get(0).path("items").get(0).path("attachments");
        assertThat(rootAtts).hasSize(1);
        assertThat(rootAtts.get(0).path("assetId").asText()).isEqualTo(ctx.assetId().toString());
        assertThat(rootAtts.get(0).path("mime").asText()).isEqualTo("audio/mpeg");
        assertThat(rootAtts.get(0).path("url").asText())
                .isEqualTo(
                        "/api/v1/me/attempts/"
                                + ctx.attempt().getId()
                                + "/assets/"
                                + ctx.assetId());

        // Scan success + error responses across all 4 endpoints
        MvcResult openOk =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        "f-open");
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(openOk.getResponse().getContentAsString());
        UUID playId = UUID.fromString(json(openOk).path("playId").asText());

        MvcResult openConflict =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        ctx.sessionId(),
                        "f-open-2");
        assertThat(openConflict.getResponse().getStatus()).isEqualTo(409);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                openConflict.getResponse().getContentAsString());

        MvcResult resumeOk =
                performResumePlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), playId, ctx.sessionId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                resumeOk.getResponse().getContentAsString());
        MvcResult resumeErr =
                performResumePlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        UUID.randomUUID(),
                        ctx.sessionId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                resumeErr.getResponse().getContentAsString());

        mutableClock.advance(Duration.ofSeconds(2));
        MvcResult progOk =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        2_000L);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(progOk.getResponse().getContentAsString());
        MvcResult progErr =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        playId,
                        ctx.sessionId(),
                        1_000L);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                progErr.getResponse().getContentAsString());

        MvcResult endOk =
                performEndPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), playId, ctx.sessionId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(endOk.getResponse().getContentAsString());
        MvcResult endErr =
                performEndPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        UUID.randomUUID(),
                        ctx.sessionId());
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(endErr.getResponse().getContentAsString());
    }

    @Test
    void stateAfterListeningClosed() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac19-state", "DS-HSCA-ANH-MINI");
        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "st-1"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(6));
        performProgress(
                ctx.userId(),
                ctx.attempt().getId(),
                ctx.assetId(),
                playId,
                ctx.sessionId(),
                6_000L);
        assertThat(
                        performFinishPart(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.parts().getFirst().getId(),
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult stateRes = performGetState(ctx.userId(), ctx.attempt().getId());
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode s = json(stateRes);
        assertThat(s.path("audioPolicy").path("maxPlays").asInt()).isEqualTo(2);
        JsonNode ap = s.path("audioPlays").get(0);
        assertThat(ap.path("state").asText()).isEqualTo("ENDED");
        assertThat(ap.path("endReason").asText()).isEqualTo("CLOSED");
        assertThat(ap.path("playsUsed").asInt()).isEqualTo(1);
        assertThat(ap.path("playsLeft").asInt()).isEqualTo(1);
        assertThat(ap.path("positionMs").asLong()).isEqualTo(6_000L);
        assertThat(ap.path("playId").asText()).isEqualTo(playId.toString());

        // Defensive reconciliation in AttemptStateService: if a row were somehow left PLAYING on a
        // closed part, GET state closes it to ENDED/CLOSED cleanly without throwing.
        jdbc.update(
                "UPDATE attempt.attempt_audio_play SET state = 'PLAYING', end_reason = NULL WHERE attempt_id = ? AND asset_id = ?",
                ctx.attempt().getId(),
                ctx.assetId());
        MvcResult reconciledRes = performGetState(ctx.userId(), ctx.attempt().getId());
        assertThat(reconciledRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode reconciledAp = json(reconciledRes).path("audioPlays").get(0);
        assertThat(reconciledAp.path("state").asText()).isEqualTo("ENDED");
        assertThat(reconciledAp.path("endReason").asText()).isEqualTo("CLOSED");
        AttemptAudioPlay savedReconciled =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.assetId())
                        .orElseThrow();
        assertThat(savedReconciled.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(savedReconciled.getEndReason()).isEqualTo(AudioEndReason.CLOSED);
    }

    @Test
    void progressAndEndAfterClose() throws Exception {
        var ctx = startSingle30sAudioAttempt("aud-ac19-pae", "DS-HSCA-ANH-MINI");
        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "pae-1"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(4));
        performProgress(
                ctx.userId(), ctx.attempt().getId(), ctx.assetId(), p1, ctx.sessionId(), 4_000L);
        performEndPlay(ctx.userId(), ctx.attempt().getId(), ctx.assetId(), p1, ctx.sessionId());

        UUID p2 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.assetId(),
                                                ctx.sessionId(),
                                                "pae-2"))
                                .path("playId")
                                .asText());
        mutableClock.advance(Duration.ofSeconds(9));
        performProgress(
                ctx.userId(), ctx.attempt().getId(), ctx.assetId(), p2, ctx.sessionId(), 9_000L);

        performFinishPart(
                ctx.userId(),
                ctx.attempt().getId(),
                ctx.parts().getFirst().getId(),
                ctx.sessionId());

        MvcResult progAfterClose =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.assetId(),
                        p2,
                        ctx.sessionId(),
                        12_000L);
        assertThat(progAfterClose.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(progAfterClose).path("code").asText()).isEqualTo("PART_CLOSED");

        MvcResult endAfterClose =
                performEndPlay(
                        ctx.userId(), ctx.attempt().getId(), ctx.assetId(), p2, ctx.sessionId());
        assertThat(endAfterClose.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(endAfterClose).path("state").asText()).isEqualTo("ENDED");
        assertThat(json(endAfterClose).path("endReason").asText()).isEqualTo("CLOSED");
        assertThat(json(endAfterClose).path("positionMs").asLong()).isEqualTo(9_000L);
        assertThat(json(endAfterClose).path("playsUsed").asInt()).isEqualTo(2);
    }

    @Test
    void closedPartAudioStateMatrix() throws Exception {
        // Test all 3 closing paths (Listening closed while in Reading, after submit, after deadline)
        // across all 3 pre-close audio states (PLAYING, ENDED/FINISHED, IDLE).
        for (String closePath : List.of("FINISH_LISTENING", "SUBMIT", "DEADLINE")) {
            mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
            UUID userId = createStudent("aud-ac19-" + closePath.toLowerCase());
            var aPlaying = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
            var aFinished = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
            var aIdle = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");

            UUID qPlaying = createReadyListeningQuestion(aPlaying.assetId(), "MX-PLAY");
            UUID qFinished = createReadyListeningQuestion(aFinished.assetId(), "MX-FIN");
            UUID qIdle = createReadyListeningQuestion(aIdle.assetId(), "MX-IDLE");
            stubListeningPaperWithQuestions(List.of(qPlaying, qFinished, qIdle));

            GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);
            Attempt attempt = startAndAwaitOpen(userId, "mx-" + closePath, line.entitlementId());
            UUID sid = claimSession(userId, attempt.getId());
            List<AttemptPart> parts =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());

            // 1) aPlaying -> PLAYING at 7000ms
            UUID playingPlayId =
                    UUID.fromString(
                            json(
                                            performOpenPlay(
                                                    userId,
                                                    attempt.getId(),
                                                    aPlaying.assetId(),
                                                    sid,
                                                    "mx-p"))
                                    .path("playId")
                                    .asText());
            mutableClock.advance(Duration.ofSeconds(7));
            assertThat(
                            performProgress(
                                            userId,
                                            attempt.getId(),
                                            aPlaying.assetId(),
                                            playingPlayId,
                                            sid,
                                            7_000L)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(200);

            // 2) aFinished -> ENDED/FINISHED
            UUID finishedPlayId =
                    UUID.fromString(
                            json(
                                            performOpenPlay(
                                                    userId,
                                                    attempt.getId(),
                                                    aFinished.assetId(),
                                                    sid,
                                                    "mx-f"))
                                    .path("playId")
                                    .asText());
            mutableClock.advance(Duration.ofSeconds(4));
            assertThat(
                            performProgress(
                                            userId,
                                            attempt.getId(),
                                            aFinished.assetId(),
                                            finishedPlayId,
                                            sid,
                                            4_000L)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(200);
            assertThat(
                            performEndPlay(
                                            userId,
                                            attempt.getId(),
                                            aFinished.assetId(),
                                            finishedPlayId,
                                            sid)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(200);

            // 3) aIdle remains IDLE (playCount = 0)
            String expectedCode = "DEADLINE".equals(closePath) ? "TIME_UP" : "PART_CLOSED";
            switch (closePath) {
                case "FINISH_LISTENING" -> {
                    assertThat(
                                    performFinishPart(
                                                    userId,
                                                    attempt.getId(),
                                                    parts.getFirst().getId(),
                                                    sid)
                                            .getResponse()
                                            .getStatus())
                            .isEqualTo(200);
                    assertThat(
                                    attemptRepository
                                            .findById(attempt.getId())
                                            .orElseThrow()
                                            .getState())
                            .isEqualTo(AttemptState.IN_PROGRESS);
                }
                case "SUBMIT" -> {
                    jdbc.update(
                            "UPDATE attempt.attempt SET navigation_mode = 'FREE' WHERE id = ?",
                            attempt.getId());
                    assertThat(
                                    performSubmit(
                                                    userId,
                                                    attempt.getId(),
                                                    "mx-sub-" + UUID.randomUUID(),
                                                    sid)
                                            .getResponse()
                                            .getStatus())
                            .isEqualTo(200);
                }
                case "DEADLINE" -> {
                    mutableClock.setAt(attempt.getDeadlineAt().plusSeconds(1));
                }
                default -> throw new IllegalArgumentException(closePath);
            }

            // GET AttemptState succeeds and returns true stored/reconciled statuses
            MvcResult stateRes = performGetState(userId, attempt.getId());
            assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode stateJson = json(stateRes);
            assertThat(stateJson.path("audioPlays")).hasSize(3);

            JsonNode playingView = findAudioView(stateJson, aPlaying.assetId());
            assertThat(playingView.path("state").asText()).isEqualTo("ENDED");
            assertThat(playingView.path("endReason").asText()).isEqualTo("CLOSED");
            assertThat(playingView.path("playsUsed").asInt()).isEqualTo(1);
            assertThat(playingView.path("playsLeft").asInt()).isEqualTo(1);
            assertThat(playingView.path("positionMs").asLong()).isEqualTo(7_000L);
            assertThat(playingView.path("playId").asText()).isEqualTo(playingPlayId.toString());

            JsonNode finishedView = findAudioView(stateJson, aFinished.assetId());
            assertThat(finishedView.path("state").asText()).isEqualTo("ENDED");
            assertThat(finishedView.path("endReason").asText()).isEqualTo("FINISHED");
            assertThat(finishedView.path("playsUsed").asInt()).isEqualTo(1);
            assertThat(finishedView.path("playsLeft").asInt()).isEqualTo(1);
            assertThat(finishedView.path("positionMs").asLong()).isEqualTo(4_000L);
            assertThat(finishedView.path("playId").asText()).isEqualTo(finishedPlayId.toString());

            JsonNode idleView = findAudioView(stateJson, aIdle.assetId());
            assertThat(idleView.path("state").asText()).isEqualTo("IDLE");
            assertThat(idleView.has("endReason")).isFalse();
            assertThat(idleView.has("playId")).isFalse();
            assertThat(idleView.path("playsUsed").asInt()).isZero();
            assertThat(idleView.path("playsLeft").asInt()).isEqualTo(2);

            // opens, resumes, progress -> 409 PART_CLOSED / TIME_UP with zero position/count mutation
            for (UUID assetId :
                    List.of(aPlaying.assetId(), aFinished.assetId(), aIdle.assetId())) {
                MvcResult openBlocked =
                        performOpenPlay(userId, attempt.getId(), assetId, sid, "blk-" + assetId);
                assertThat(openBlocked.getResponse().getStatus()).isEqualTo(409);
                assertThat(json(openBlocked).path("code").asText()).isEqualTo(expectedCode);
            }

            MvcResult progBlocked =
                    performProgress(
                            userId,
                            attempt.getId(),
                            aPlaying.assetId(),
                            playingPlayId,
                            sid,
                            9_000L);
            assertThat(progBlocked.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(progBlocked).path("code").asText()).isEqualTo(expectedCode);

            // Repeated end returns 200 with real saved terminal state (CLOSED vs FINISHED)
            MvcResult endClosed =
                    performEndPlay(
                            userId, attempt.getId(), aPlaying.assetId(), playingPlayId, sid);
            assertThat(endClosed.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(endClosed).path("endReason").asText()).isEqualTo("CLOSED");

            MvcResult endFinished =
                    performEndPlay(
                            userId, attempt.getId(), aFinished.assetId(), finishedPlayId, sid);
            assertThat(endFinished.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(endFinished).path("endReason").asText()).isEqualTo("FINISHED");

            // Asset downloads -> 404
            assertThat(
                            performGetAudioAsset(
                                            userId,
                                            attempt.getId(),
                                            aPlaying.assetId(),
                                            playingPlayId)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(404);
            assertThat(
                            performGetAudioAsset(
                                            userId,
                                            attempt.getId(),
                                            aFinished.assetId(),
                                            finishedPlayId)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(404);
            assertThat(
                            performGetAudioAsset(
                                            userId, attempt.getId(), aIdle.assetId(), null)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(404);

            // play_count unchanged across every route
            assertThat(
                            attemptAudioPlayRepository
                                    .findByAttemptIdAndAssetId(
                                            attempt.getId(), aPlaying.assetId())
                                    .orElseThrow()
                                    .getPlayCount())
                    .isEqualTo(1);
            assertThat(
                            attemptAudioPlayRepository
                                    .findByAttemptIdAndAssetId(
                                            attempt.getId(), aFinished.assetId())
                                    .orElseThrow()
                                    .getPlayCount())
                    .isEqualTo(1);
            assertThat(
                            attemptAudioPlayRepository
                                    .findByAttemptIdAndAssetId(attempt.getId(), aIdle.assetId())
                                    .orElseThrow()
                                    .getPlayCount())
                    .isZero();
        }
    }

    private static JsonNode findAudioView(JsonNode stateJson, UUID assetId) {
        for (JsonNode node : stateJson.path("audioPlays")) {
            if (assetId.toString().equals(node.path("assetId").asText())) {
                return node;
            }
        }
        throw new AssertionError("Missing audioPlay view for assetId=" + assetId);
    }
}
