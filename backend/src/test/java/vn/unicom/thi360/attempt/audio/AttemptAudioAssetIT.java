package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AudioEndReason;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

/**
 * Integration tests for audio asset streaming, lease expiration/renewal, byte-range responses
 * (206/416 and malformed Range), server duration capping, and closed-part/deadline 404 gating (BS
 * §17.2, AC-5, AC-10).
 */
class AttemptAudioAssetIT extends AbstractAttemptAudioIT {

    private record AssetTestCtx(
            UUID userId,
            Attempt attempt,
            UUID sessionId,
            List<AttemptPart> parts,
            AudioAssetFixture.CreatedAsset cbr) {}

    private AssetTestCtx startWith30sAudio(String label) throws Exception {
        UUID userId = createStudent(label);
        var cbr = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID qId = createReadyListeningQuestion(cbr.assetId(), label.toUpperCase());
        stubListeningPaperWithQuestions(List.of(qId));

        GrantOrderResult.Line line = grant(userId, "DS-HSCA-ANH-MINI", 2);
        Attempt attempt = startAndAwaitOpen(userId, label, line.entitlementId());
        UUID sid = claimSession(userId, attempt.getId());
        List<AttemptPart> parts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        return new AssetTestCtx(userId, attempt, sid, parts, cbr);
    }

    @Test
    void expiredLeaseGets404() throws Exception {
        var ctx = startWith30sAudio("aud-ast-lease");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "lease-1"))
                                .path("playId")
                                .asText());
        Instant leaseUntil =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.cbr().assetId())
                        .orElseThrow()
                        .getLeaseUntil();
        assertThat(leaseUntil).isEqualTo(AttemptTestFixtures.DEFAULT_CLOCK_START.plusSeconds(45));

        // lease_until - 1ms -> 200
        mutableClock.setAt(leaseUntil.minusMillis(1));
        MvcResult beforeExpiry =
                performGetAudioAsset(
                        ctx.userId(), ctx.attempt().getId(), ctx.cbr().assetId(), playId);
        assertThat(beforeExpiry.getResponse().getStatus()).isEqualTo(200);
        assertThat(beforeExpiry.getResponse().getContentAsByteArray())
                .isEqualTo(ctx.cbr().bytes());

        // == lease_until -> 404
        mutableClock.setAt(leaseUntil);
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // lease_until + 1ms -> 404
        mutableClock.setAt(leaseUntil.plusMillis(1));
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    void resumeRenewsLease() throws Exception {
        var ctx = startWith30sAudio("aud-ast-renew");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "renew-1"))
                                .path("playId")
                                .asText());

        mutableClock.advance(Duration.ofSeconds(60));
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        assertThat(
                        performResumePlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        MvcResult afterResume =
                performGetAudioAsset(
                        ctx.userId(), ctx.attempt().getId(), ctx.cbr().assetId(), playId);
        assertThat(afterResume.getResponse().getStatus()).isEqualTo(200);
        assertThat(afterResume.getResponse().getContentAsByteArray())
                .isEqualTo(ctx.cbr().bytes());
    }

    @Test
    void stalledProgressEndsAtPartClose() throws Exception {
        var ctx = startWith30sAudio("aud-ast-stall");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "stall-1"))
                                .path("playId")
                                .asText());

        // Advance 30s and report stalled position 0ms (extends lease beyond initial 45s)
        mutableClock.advance(Duration.ofSeconds(30));
        assertThat(
                        performProgress(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId,
                                        ctx.sessionId(),
                                        0L)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        // At t+60s (past initial 45s lease, within renewed 75s lease) -> still 200
        mutableClock.advance(Duration.ofSeconds(30));
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        // Close Listening part -> asset immediately returns 404
        assertThat(
                        performFinishPart(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.parts().getFirst().getId(),
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    void positionCappedByServerDuration() throws Exception {
        var ctx = startWith30sAudio("aud-ast-cap");

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "cap-1"))
                                .path("playId")
                                .asText());

        // Advance 40s (so expectedMs > 30_000) and try sending client-claimed duration 30_001ms
        mutableClock.advance(Duration.ofSeconds(40));
        MvcResult overCap =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.cbr().assetId(),
                        playId,
                        ctx.sessionId(),
                        30_001L);
        assertThat(overCap.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(overCap).path("code").asText()).isEqualTo("AUDIO_SEEK_REJECTED");

        // Exact server durationMs (30_000) finishes play and revokes asset download
        MvcResult exactCap =
                performProgress(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.cbr().assetId(),
                        playId,
                        ctx.sessionId(),
                        30_000L);
        assertThat(exactCap.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(exactCap).path("state").asText()).isEqualTo("ENDED");
        assertThat(json(exactCap).path("endReason").asText()).isEqualTo("FINISHED");

        AttemptAudioPlay saved =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(ctx.attempt().getId(), ctx.cbr().assetId())
                        .orElseThrow();
        assertThat(saved.getState()).isEqualTo(AudioPlayState.ENDED);
        assertThat(saved.getEndReason()).isEqualTo(AudioEndReason.FINISHED);

        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    void servesRangeWhilePlaying() throws Exception {
        var ctx = startWith30sAudio("aud-ast-range");
        byte[] allBytes = ctx.cbr().bytes();
        int total = allBytes.length;
        assertThat(total).isEqualTo(120_000);

        UUID playId =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "range-1"))
                                .path("playId")
                                .asText());

        // 1. Full GET
        MvcResult full =
                performGetAudioAsset(
                        ctx.userId(), ctx.attempt().getId(), ctx.cbr().assetId(), playId);
        assertThat(full.getResponse().getStatus()).isEqualTo(200);
        assertThat(full.getResponse().getContentType()).isEqualTo("audio/mpeg");
        assertThat(full.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, no-store");
        assertThat(full.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(full.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline;");
        assertThat(full.getResponse().getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(full.getResponse().getContentAsByteArray()).isEqualTo(allBytes);

        // 2. Start range bytes=0-1023 -> 206 Partial Content
        MvcResult startRange =
                mockMvc.perform(
                                get(
                                                "/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId())
                                        .param("playId", playId.toString())
                                        .header(HttpHeaders.RANGE, "bytes=0-1023")
                                        .with(AttemptTestFixtures.asExamUser(ctx.userId(), "learner")))
                        .andReturn();
        assertThat(startRange.getResponse().getStatus()).isEqualTo(206);
        assertThat(startRange.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 0-1023/" + total);
        assertThat(startRange.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, no-store");
        assertThat(startRange.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(allBytes, 0, 1024));

        // 3. End range bytes=119000- -> 206 Partial Content
        MvcResult endRange =
                mockMvc.perform(
                                get(
                                                "/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId())
                                        .param("playId", playId.toString())
                                        .header(HttpHeaders.RANGE, "bytes=119000-")
                                        .with(AttemptTestFixtures.asExamUser(ctx.userId(), "learner")))
                        .andReturn();
        assertThat(endRange.getResponse().getStatus()).isEqualTo(206);
        assertThat(endRange.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 119000-" + (total - 1) + "/" + total);
        assertThat(endRange.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(allBytes, 119000, total));

        // 4. Out-of-bounds range bytes=999999- -> 416 empty body
        MvcResult oobRange =
                mockMvc.perform(
                                get(
                                                "/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId())
                                        .param("playId", playId.toString())
                                        .header(HttpHeaders.RANGE, "bytes=999999-")
                                        .with(AttemptTestFixtures.asExamUser(ctx.userId(), "learner")))
                        .andReturn();
        assertThat(oobRange.getResponse().getStatus()).isEqualTo(416);
        assertThat(oobRange.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes */" + total);
        assertThat(oobRange.getResponse().getContentAsByteArray()).isEmpty();

        // 5. Malformed Range headers -> clean 416 with empty body, never 500
        for (String malformed : List.of("bytes=abc-def", "invalid-range")) {
            MvcResult badRange =
                    mockMvc.perform(
                                    get(
                                                    "/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                                                    ctx.attempt().getId(),
                                                    ctx.cbr().assetId())
                                            .param("playId", playId.toString())
                                            .header(HttpHeaders.RANGE, malformed)
                                            .with(
                                                    AttemptTestFixtures.asExamUser(
                                                            ctx.userId(), "learner")))
                            .andReturn();
            assertThat(badRange.getResponse().getStatus()).isEqualTo(416);
            assertThat(badRange.getResponse().getContentAsByteArray()).isEmpty();
        }
    }

    @Test
    void wrongPlayIdGets404() throws Exception {
        var ctx = startWith30sAudio("aud-ast-wrongid");

        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "wp-1"))
                                .path("playId")
                                .asText());
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        UUID.randomUUID())
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // Non-UUID ?playId= must return 404 NOT_FOUND rather than 400 VALIDATION_FAILED (R1-22)
        MvcResult invalidSyntax =
                mockMvc.perform(
                                get(
                                                "/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId())
                                        .param("playId", "not-a-uuid")
                                        .with(AttemptTestFixtures.asExamUser(ctx.userId(), "learner")))
                        .andReturn();
        assertThat(invalidSyntax.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(invalidSyntax).path("code").asText()).isEqualTo("NOT_FOUND");

        performEndPlay(
                ctx.userId(), ctx.attempt().getId(), ctx.cbr().assetId(), p1, ctx.sessionId());
        UUID p2 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "wp-2"))
                                .path("playId")
                                .asText());
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p2)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
    }

    @Test
    void notPlayingGets404() throws Exception {
        var ctx = startWith30sAudio("aud-ast-notplay");

        // While IDLE
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        UUID.randomUUID())
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // Non-UUID ?playId= must return 404 NOT_FOUND rather than 400 VALIDATION_FAILED (R1-22)
        MvcResult invalidSyntax =
                mockMvc.perform(
                                get(
                                                "/api/v1/me/attempts/{attemptId}/assets/{assetId}",
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId())
                                        .param("playId", "not-a-uuid")
                                        .with(AttemptTestFixtures.asExamUser(ctx.userId(), "learner")))
                        .andReturn();
        assertThat(invalidSyntax.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(invalidSyntax).path("code").asText()).isEqualTo("NOT_FOUND");

        // After ENDED/FINISHED
        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "np-end"))
                                .path("playId")
                                .asText());
        performEndPlay(
                ctx.userId(), ctx.attempt().getId(), ctx.cbr().assetId(), p1, ctx.sessionId());
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // Safety net: even if attempt_audio_play row is absent, an audio/* asset is never served via non-audio fallback
        jdbc.update(
                "DELETE FROM attempt.attempt_audio_play WHERE attempt_id = ? AND asset_id = ?",
                ctx.attempt().getId(),
                ctx.cbr().assetId());
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    void closedPartGets404() throws Exception {
        var ctx = startWith30sAudio("aud-ast-closedpart");

        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "cp-1"))
                                .path("playId")
                                .asText());
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        assertThat(
                        performFinishPart(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.parts().getFirst().getId(),
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    void afterDeadlineGets404() throws Exception {
        var ctx = startWith30sAudio("aud-ast-deadline");

        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                ctx.userId(),
                                                ctx.attempt().getId(),
                                                ctx.cbr().assetId(),
                                                ctx.sessionId(),
                                                "dl-1"))
                                .path("playId")
                                .asText());
        Instant deadline = ctx.attempt().getDeadlineAt();

        // Renew lease 10s before deadline so leaseUntil extends past deadline
        mutableClock.setAt(deadline.minusSeconds(10));
        assertThat(
                        performResumePlay(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p1,
                                        ctx.sessionId())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        mutableClock.setAt(deadline.minusMillis(1));
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        mutableClock.setAt(deadline);
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    void perPartDeadlineGets404EvenWhenLeaseExceedsPartDeadline() throws Exception {
        UUID userId = createStudent("aud-ast-perpart-dl");
        var cbr = assetFixture.createFromAudioResource("silence-30s-cbr.mp3");
        UUID qId = createReadyListeningQuestion(cbr.assetId(), "AST-PERPART");
        stubListeningPaperWithQuestions(List.of(qId));

        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2);
        Attempt attempt = startAndAwaitOpen(userId, "ast-pp", line.entitlementId());
        assertThat(attempt.getDeadlineAt()).isNull();
        UUID sid = claimSession(userId, attempt.getId());
        Instant part0Deadline =
                attemptPartRepository
                        .findByAttemptIdOrderByOrdAsc(attempt.getId())
                        .getFirst()
                        .getDeadlineAt();
        assertThat(part0Deadline).isNotNull();

        UUID p1 =
                UUID.fromString(
                        json(
                                        performOpenPlay(
                                                userId,
                                                attempt.getId(),
                                                cbr.assetId(),
                                                sid,
                                                "pp-ast-1"))
                                .path("playId")
                                .asText());

        // Renew lease 10s before Part 0 deadline so leaseUntil > part0Deadline
        mutableClock.setAt(part0Deadline.minusSeconds(10));
        assertThat(
                        performResumePlay(userId, attempt.getId(), cbr.assetId(), p1, sid)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        Instant leaseUntil =
                attemptAudioPlayRepository
                        .findByAttemptIdAndAssetId(attempt.getId(), cbr.assetId())
                        .orElseThrow()
                        .getLeaseUntil();
        assertThat(leaseUntil).isAfter(part0Deadline);

        mutableClock.setAt(part0Deadline.minusMillis(1));
        assertThat(
                        performGetAudioAsset(userId, attempt.getId(), cbr.assetId(), p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        // At exact part0Deadline -> 404 even though leaseUntil is still in the future
        mutableClock.setAt(part0Deadline);
        assertThat(
                        performGetAudioAsset(userId, attempt.getId(), cbr.assetId(), p1)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
    }

    @Test
    void delayedIdempotentOpenPlayRenewsExpiredLeaseWithoutUsingExtraPlay() throws Exception {
        var ctx = startWith30sAudio("aud-ast-delayed-idem");

        MvcResult initial =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.cbr().assetId(),
                        ctx.sessionId(),
                        "delayed-idem-req");
        assertThat(initial.getResponse().getStatus()).isEqualTo(200);
        UUID playId = UUID.fromString(json(initial).path("playId").asText());

        // Advance past 45s lease (60s) -> asset download rejects with 404
        mutableClock.advance(Duration.ofSeconds(60));
        assertThat(
                        performGetAudioAsset(
                                        ctx.userId(),
                                        ctx.attempt().getId(),
                                        ctx.cbr().assetId(),
                                        playId)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // Retry with same requestId -> renews lease without incrementing playCount
        MvcResult retry =
                performOpenPlay(
                        ctx.userId(),
                        ctx.attempt().getId(),
                        ctx.cbr().assetId(),
                        ctx.sessionId(),
                        "delayed-idem-req");
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        var retryJson = objectMapper.readTree(retry.getResponse().getContentAsString());
        assertThat(retryJson.path("playId").asText()).isEqualTo(playId.toString());
        assertThat(retryJson.path("playsUsed").asInt()).isEqualTo(1);
        assertThat(retryJson.path("playsLeft").asInt()).isEqualTo(1);
        assertThat(retryJson.path("state").asText()).isEqualTo("PLAYING");

        // Immediate asset download succeeds with 200
        MvcResult afterRetryGet =
                performGetAudioAsset(
                        ctx.userId(), ctx.attempt().getId(), ctx.cbr().assetId(), playId);
        assertThat(afterRetryGet.getResponse().getStatus()).isEqualTo(200);
        assertThat(afterRetryGet.getResponse().getContentAsByteArray())
                .isEqualTo(ctx.cbr().bytes());
    }
}
