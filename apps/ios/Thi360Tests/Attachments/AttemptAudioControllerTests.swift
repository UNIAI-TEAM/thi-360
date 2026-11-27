import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AttemptAudioControllerTests: XCTestCase {
    private let assetId = "00000000-0000-7000-8000-000000000008"

    func testFirstPlay() async throws {
        let stack = try AttachmentsTestStack.make(
            accessToken: "bearer-ac4-secret",
            sessionId: "session-ac4-1"
        )
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 30_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId == "session-ac4-1" }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 30_000, clock: stack.clock)
        var generatedIds: [String] = []
        let controller = stack.makeController(
            assetId: assetId,
            fileName: "listening-1.mp3",
            playbackEngine: fakePlayback,
            requestIdGenerator: {
                let id = "req-ac4-\(generatedIds.count + 1)"
                generatedIds.append(id)
                return id
            }
        )
        defer { controller.close() }

        XCTAssertEqual(controller.uiState.playsLeft, 2)
        XCTAssertEqual(controller.uiState.maxPlays, 2)
        XCTAssertEqual(AttachmentsStrings.playsRemaining(left: controller.uiState.playsLeft, max: controller.uiState.effectiveMaxPlays, locale: .vi), "Còn 2/2 lần nghe")
        XCTAssertEqual(AudioTimeFormatter.format(milliseconds: controller.uiState.positionMs), "00:00")
        XCTAssertEqual(AudioTimeFormatter.format(milliseconds: controller.uiState.durationMs), "00:30")
        XCTAssertEqual(controller.uiState.primaryButtonRole, .play)
        XCTAssertEqual(AttachmentsStrings.text(controller.uiState.primaryButtonRole.stringKey, locale: .vi), "Nghe")

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)
        XCTAssertTrue(fakePlayback.isPlaying)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .playing)
        XCTAssertEqual(AttachmentsStrings.text(controller.uiState.primaryButtonRole.stringKey, locale: .vi), "Đang phát…")
        XCTAssertFalse(controller.uiState.isPrimaryButtonEnabled)
        XCTAssertEqual(controller.uiState.playsLeft, 1)
        XCTAssertEqual(AttachmentsStrings.playsRemaining(left: controller.uiState.playsLeft, max: controller.uiState.effectiveMaxPlays, locale: .vi), "Còn 1/2 lần nghe")

        let openCalls = stack.router.recordedCalls(for: .openPlay)
        XCTAssertEqual(openCalls.count, 1)
        XCTAssertEqual(openCalls[0].authorizationHeader, "Bearer bearer-ac4-secret")
        XCTAssertEqual(openCalls[0].sessionHeader, "session-ac4-1")
        XCTAssertEqual(openCalls[0].decodedJSON()?["requestId"] as? String, "req-ac4-1")

        let fetchCalls = stack.router.recordedCalls(for: .fetchAudio)
        XCTAssertEqual(fetchCalls.count, 1)
        XCTAssertEqual(fetchCalls[0].authorizationHeader, "Bearer bearer-ac4-secret")
        XCTAssertTrue(fetchCalls[0].query?.contains("playId=play-0008-1") == true)
        XCTAssertEqual(fakePlayback.startCalls.first?.startPositionMs, 0)
    }

    func testEndAndExhaustedAndConcurrentProgressNeverDuplicatesEnd() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 20_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 20_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        // Play #1
        controller.onPrimaryAction()
        let playing1 = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing1)

        // Advance right near 10s tick and complete synchronously to verify end is sent strictly once.
        fakePlayback.setManualPositionMs(20_000)
        fakePlayback.simulateCompletion()
        await stack.clock.advance(by: .seconds(10))

        let backToIdle = await stack.clock.waitUntil {
            controller.uiState.playbackState == .idle(playsLeft: 1)
        }
        XCTAssertTrue(backToIdle)
        XCTAssertEqual(stack.router.callCount(for: .end), 1)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .play)
        XCTAssertTrue(controller.uiState.isPrimaryButtonEnabled)
        XCTAssertFalse(fakePlayback.hasLoadedAudioData)

        // Play #2
        controller.onPrimaryAction()
        let playing2 = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing2)

        fakePlayback.simulateCompletion()
        let exhausted = await stack.clock.waitUntil {
            controller.uiState.playbackState == .exhausted
        }
        XCTAssertTrue(exhausted)
        XCTAssertEqual(stack.router.callCount(for: .end), 2)
        XCTAssertEqual(controller.uiState.playsLeft, 0)
        XCTAssertFalse(controller.uiState.isPrimaryButtonEnabled)
        XCTAssertEqual(
            AttachmentsStrings.playsExhaustedBadge(max: controller.uiState.effectiveMaxPlays, locale: .vi),
            "Đã hết lượt nghe (0/2)"
        )
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.exhaustedReason")

        // Certified 409 AUDIO_PLAYS_EXHAUSTED fixture on fresh controller also transitions straight to .exhausted.
        let stack2 = try AttachmentsTestStack.make()
        defer { stack2.cleanup() }
        try stack2.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 30_000)
        stack2.router.enqueue(
            .openPlay,
            try .fixture(operationId: Operations.openAudioPlay.id, status: 409, variant: "audio-plays-exhausted")
        )
        let ready2 = await stack2.clock.waitUntil { stack2.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready2)

        let controller2 = stack2.makeController(assetId: assetId, playbackEngine: FakeAudioPlaybackEngine(clock: stack2.clock))
        defer { controller2.close() }
        controller2.onPrimaryAction()
        let exhausted2 = await stack2.clock.waitUntil { controller2.uiState.playbackState == .exhausted }
        XCTAssertTrue(exhausted2)
        XCTAssertEqual(controller2.uiState.banner?.stringKey, "question.audioPlayer.exhaustedReason")
    }

    func testRetrySameRequestId() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 30_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        // First openPlay hits a transient network error (as if response was lost after server processed it).
        stack.router.enqueue(.openPlay, .networkError(.timedOut))

        var seq = 0
        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 30_000, clock: stack.clock)
        let controller = stack.makeController(
            assetId: assetId,
            playbackEngine: fakePlayback,
            requestIdGenerator: {
                seq += 1
                return "stable-req-\(seq)"
            }
        )
        defer { controller.close() }

        controller.onPrimaryAction()
        let errored = await stack.clock.waitUntil {
            controller.uiState.hasPlayError && controller.uiState.playbackState == .idle(playsLeft: 2)
        }
        XCTAssertTrue(errored)
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.playErrorRetry")

        // Tap again -> must reuse the EXACT same requestId (AC-6) so server decrements playsLeft only once.
        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        let openCalls = stack.router.recordedCalls(for: .openPlay)
        XCTAssertEqual(openCalls.count, 2)
        let firstReqId = openCalls[0].decodedJSON()?["requestId"] as? String
        let secondReqId = openCalls[1].decodedJSON()?["requestId"] as? String
        XCTAssertEqual(firstReqId, "stable-req-1")
        XCTAssertEqual(secondReqId, "stable-req-1", "Retry after failed openPlay must reuse the exact same requestId")
        XCTAssertEqual(controller.uiState.playsUsed, 1)
        XCTAssertEqual(controller.uiState.playsLeft, 1)
    }

    func testDoubleTapSingleRequest() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 30_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let gate = AttemptTestGate()
        stack.router.enqueue(
            .openPlay,
            try .fixture(operationId: Operations.openAudioPlay.id, status: 200, gate: gate) { obj in
                obj["playId"] = "play-0008-1"
            }
        )

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 30_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        await gate.waitUntilEntered()
        // Second tap within 300ms while first openPlay is still in flight -> ignored.
        controller.onPrimaryAction()
        await stack.clock.advance(by: .milliseconds(150))
        controller.onPrimaryAction()

        await gate.releaseAll()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)
        XCTAssertEqual(stack.router.callCount(for: .openPlay), 1, "Double tap within 300ms must emit strictly 1 openPlay request")
    }

    func testUnauthorizedRefreshRetriesOpenPlayWithSameRequestId() async throws {
        let stack = try AttachmentsTestStack.make(accessToken: "expired-audio-token")
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 30_000)

        var refreshObj = try AttemptFixtureCatalog.loadJSONObject(operationId: Operations.mobileRefresh.id, status: 200)
        refreshObj["accessToken"] = "refreshed-audio-token"
        let refreshData = try JSONSerialization.data(withJSONObject: refreshObj)
        stack.server.transport.register(method: .post, path: "/api/v1/auth/mobile/refresh") { _ in
            ContractFakeTransport.jsonResponse(statusCode: 200, data: refreshData)
        }

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let unauthData = try AttemptFixtureCatalog.loadData(
            operationId: Operations.downloadAttemptAsset.id,
            status: 401,
            variant: "unauthenticated"
        )
        stack.router.enqueue(.openPlay, .json(statusCode: 401, data: unauthData))

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 30_000, clock: stack.clock)
        let controller = stack.makeController(
            assetId: assetId,
            playbackEngine: fakePlayback,
            requestIdGenerator: { "req-401-idempotent" }
        )
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        let openCalls = stack.router.recordedCalls(for: .openPlay)
        XCTAssertEqual(openCalls.count, 2)
        XCTAssertEqual(openCalls[0].authorizationHeader, "Bearer expired-audio-token")
        XCTAssertEqual(openCalls[1].authorizationHeader, "Bearer refreshed-audio-token")
        XCTAssertEqual(openCalls[0].decodedJSON()?["requestId"] as? String, "req-401-idempotent")
        XCTAssertEqual(openCalls[1].decodedJSON()?["requestId"] as? String, "req-401-idempotent")
    }

    func testReopenPlayingShowsResume() async throws {
        let stack = try AttachmentsTestStack.make(initialStateVariant: "audio-playing")
        defer { stack.cleanup() }

        let stateJSON = stack.server.currentStateJSON()
        let audioPlays = try XCTUnwrap(stateJSON["audioPlays"] as? [[String: Any]])
        let firstPlay = try XCTUnwrap(audioPlays.first)
        let fixtureAssetId = try XCTUnwrap(firstPlay["assetId"] as? String)
        let fixturePlayId = try XCTUnwrap(firstPlay["playId"] as? String)
        let fixtureDurationMs = (firstPlay["durationMs"] as? NSNumber)?.int64Value ?? 120_000

        // Configure asset at PLAYING position 01:10 (70_000 ms) matching AC-7.
        try stack.router.configureAsset(
            assetId: fixtureAssetId,
            maxPlays: 2,
            durationMs: max(fixtureDurationMs, 120_000),
            playsUsed: 1,
            activePlayId: fixturePlayId,
            positionMs: 70_000,
            state: "PLAYING",
            knownPlayIds: [fixturePlayId]
        )

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 120_000, clock: stack.clock)
        let controller = stack.makeController(assetId: fixtureAssetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        // Must NOT auto-play on mount (AC-7).
        await stack.clock.settle()
        XCTAssertFalse(fakePlayback.isPlaying)
        XCTAssertEqual(fakePlayback.startCalls.count, 0)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(AttachmentsStrings.text(controller.uiState.primaryButtonRole.stringKey, locale: .vi), "Phát lại")
        XCTAssertEqual(AudioTimeFormatter.format(milliseconds: controller.uiState.positionMs), "01:10")
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.pausedResumable")
        XCTAssertEqual(controller.uiState.playsLeft, 1)

        // Tap "Phát lại" -> calls resume, plays from 01:10, playsLeft unchanged.
        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)
        XCTAssertEqual(stack.router.callCount(for: .resume), 1)
        XCTAssertEqual(stack.router.callCount(for: .openPlay), 0)
        XCTAssertEqual(fakePlayback.startCalls.first?.startPositionMs, 70_000)
        XCTAssertEqual(controller.uiState.playsLeft, 1)
    }

    func testInProgressResumesWithoutErrorBanner() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        let existingPlayId = "00000000-0000-7000-8000-000000000010"
        try stack.router.configureAsset(
            assetId: assetId,
            maxPlays: 2,
            durationMs: 30_000,
            playsUsed: 0,
            activePlayId: nil,
            positionMs: 0,
            state: "IDLE",
            knownPlayIds: [existingPlayId]
        )

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        stack.router.enqueue(
            .openPlay,
            try .fixture(operationId: Operations.openAudioPlay.id, status: 409, variant: "audio-play-in-progress") { obj in
                obj["playId"] = existingPlayId
            }
        )
        stack.router.enqueue(
            .resume,
            try .fixture(operationId: Operations.resumeAudioPlay.id, status: 200) { obj in
                obj["playId"] = existingPlayId
                obj["positionMs"] = 18_000
                obj["playsUsed"] = 1
                obj["playsLeft"] = 1
                obj["durationMs"] = 30_000
                obj["state"] = "PLAYING"
            }
        )

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 30_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)
        XCTAssertEqual(stack.router.callCount(for: .openPlay), 1)
        XCTAssertEqual(stack.router.callCount(for: .resume), 1)
        XCTAssertEqual(fakePlayback.startCalls.first?.startPositionMs, 18_000)
        XCTAssertNil(controller.uiState.banner, "409 AUDIO_PLAY_IN_PROGRESS auto-resume must not show an error banner")
    }

    func testSeekRejectedSnapsBackToServerPositionAndContinuesPlaying() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        stack.router.enqueue(
            .progress,
            try .fixture(operationId: Operations.updateAudioProgress.id, status: 409, variant: "audio-seek-rejected") { obj in
                obj["positionMs"] = 9_500
            }
        )

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        // Simulate rogue seek forward to 25s before 10s progress tick fires.
        fakePlayback.setManualPositionMs(25_000)
        await stack.clock.advance(by: .seconds(10))

        let soughtBack = await stack.clock.waitUntil { fakePlayback.seekCalls == [9_500] }
        XCTAssertTrue(soughtBack)
        XCTAssertTrue(fakePlayback.isPlaying, "Audio must keep playing after snapping back on AUDIO_SEEK_REJECTED")
        if case let .playing(_, pos) = controller.uiState.playbackState {
            XCTAssertGreaterThanOrEqual(pos, 9_500)
        } else {
            XCTFail("Expected controller to remain in .playing state after seek rejection")
        }
    }

    func testProgressCadenceMonotonicAndBoundedOver35Seconds() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        await stack.clock.advance(by: .seconds(10))
        await stack.clock.advance(by: .seconds(10))
        await stack.clock.advance(by: .seconds(10))
        await stack.clock.advance(by: .seconds(5))

        let progressCalls = stack.router.recordedCalls(for: .progress)
        XCTAssertEqual(progressCalls.count, 3, "35s continuous playback must send 3 progress calls at 10s, 20s, 30s (AC-10)")

        let reportedPositions = progressCalls.compactMap { ($0.decodedJSON()?["positionMs"] as? NSNumber)?.int64Value }
        XCTAssertEqual(reportedPositions.count, 3)
        XCTAssertGreaterThanOrEqual(reportedPositions[0], 9_000)
        XCTAssertLessThanOrEqual(reportedPositions[0], 11_000)
        XCTAssertGreaterThanOrEqual(reportedPositions[1], 19_000)
        XCTAssertLessThanOrEqual(reportedPositions[1], 21_000)
        XCTAssertGreaterThanOrEqual(reportedPositions[2], 29_000)
        XCTAssertLessThanOrEqual(reportedPositions[2], 31_000)

        for i in 1..<reportedPositions.count {
            XCTAssertGreaterThanOrEqual(reportedPositions[i], reportedPositions[i - 1], "Progress positions must be monotonically non-decreasing")
        }
        for pos in reportedPositions {
            XCTAssertLessThanOrEqual(pos, 35_000, "Progress position must never exceed durationMs")
        }
    }

    func testStaleReloadsAttemptAndUpdatesBadge() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        stack.router.enqueue(
            .progress,
            try .fixture(operationId: Operations.updateAudioProgress.id, status: 409, variant: "audio-play-stale")
        )

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        let getStateBefore = stack.server.callCount(for: .getState)
        // Server advances audioPlays to exhausted (playsUsed=2, playsLeft=0, ENDED) before returning 409 STALE.
        stack.router.updateAssetState(
            assetId: assetId,
            playsUsed: 2,
            activePlayId: "play-0008-2",
            positionMs: 35_000,
            state: "ENDED",
            endReason: "FINISHED"
        )

        await stack.clock.advance(by: .seconds(10))

        let reloaded = await stack.clock.waitUntil {
            !fakePlayback.isPlaying
                && stack.server.callCount(for: .getState) > getStateBefore
                && controller.uiState.playbackState == .exhausted
        }
        XCTAssertTrue(reloaded)
        XCTAssertEqual(controller.uiState.playsLeft, 0)
    }

    func testSessionRevokedAndEngineSessionLostAndNoAudioAPIWithoutEditingSession() async throws {
        // Part 1: 409 ATTEMPT_SESSION_REVOKED from progress -> stops within 1s, shows evictedReason, calls reportSessionRevoked.
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        stack.router.enqueue(
            .progress,
            try .fixture(operationId: Operations.updateAudioProgress.id, status: 409, variant: "attempt-session-revoked")
        )

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        let timeBeforeTick = stack.clock.now
        await stack.clock.advance(by: .seconds(10))

        let evicted = await stack.clock.waitUntil {
            !fakePlayback.isPlaying
                && controller.uiState.playbackState == .locked(reason: .evicted)
                && stack.engine.state.session == .evicted
        }
        XCTAssertTrue(evicted)
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.evictedReason")
        if let stopAt = fakePlayback.stopTimestamps.last {
            let delta = stopAt - (timeBeforeTick + .seconds(10))
            XCTAssertLessThan(delta, .seconds(1), "Audio must stop within 1s of ATTEMPT_SESSION_REVOKED (AC-12)")
        }

        // Part 2: Heartbeat eviction emitting EngineEvent.sessionLost -> stops within 1s, shows evictedReason.
        let stack2 = try AttachmentsTestStack.make()
        defer { stack2.cleanup() }
        try stack2.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 60_000)

        let ready2 = await stack2.clock.waitUntil { stack2.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready2)

        let fakePlayback2 = FakeAudioPlaybackEngine(durationMs: 60_000, clock: stack2.clock)
        let controller2 = stack2.makeController(assetId: assetId, playbackEngine: fakePlayback2)
        defer { controller2.close() }

        controller2.onPrimaryAction()
        let playing2 = await stack2.clock.waitUntil {
            if case .playing = controller2.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing2)

        stack2.server.simulateExternalTakeover()
        await stack2.clock.advance(by: .seconds(30))

        let evictedByHeartbeat = await stack2.clock.waitUntil {
            !fakePlayback2.isPlaying && controller2.uiState.playbackState == .locked(reason: .evicted)
        }
        XCTAssertTrue(evictedByHeartbeat)
        XCTAssertEqual(controller2.uiState.banner?.stringKey, "question.audioPlayer.evictedReason")

        // Part 3: Zero audio API calls when session is .acquiring.
        let stack3 = try AttachmentsTestStack.make(autoStart: false)
        defer { stack3.cleanup() }
        try stack3.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 30_000)

        let fakePlayback3 = FakeAudioPlaybackEngine(durationMs: 30_000, clock: stack3.clock)
        let controller3 = stack3.makeController(assetId: assetId, playbackEngine: fakePlayback3)
        defer { controller3.close() }

        XCTAssertEqual(controller3.uiState.playbackState, .locked(reason: .acquiring))
        XCTAssertNil(controller3.uiState.banner, "Acquiring state must disable button without showing an error banner")
        XCTAssertFalse(controller3.uiState.isPrimaryButtonEnabled)
        controller3.onPrimaryAction()
        await stack3.clock.settle()
        XCTAssertEqual(stack3.router.recordedCalls().count, 0, "No audio API call may be sent when session != .editing")
    }

    func testLeaseExpiredResumeRenewsBeforeFetchingAudio() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        stack.router.setLeaseTimeout(seconds: 45)
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        // Pause playback and let >45s elapse without any progress or resume calls so server lease expires (AC-13).
        controller.onAppBackgrounded()
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)

        await stack.clock.advance(by: .seconds(50))
        stack.router.resetCalls()

        // Tap "Phát lại" -> MUST call resume BEFORE fetchAudio so lease is renewed and fetch succeeds.
        controller.onPrimaryAction()
        let resumed = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(resumed)

        let calls = stack.router.recordedCalls()
        XCTAssertGreaterThanOrEqual(calls.count, 2)
        XCTAssertEqual(calls[0].endpoint, .resume, "resume MUST be called before fetchAudio after lease expiry")
        XCTAssertEqual(calls[1].endpoint, .fetchAudio)
        XCTAssertEqual(controller.uiState.playsLeft, 1, "Resuming expired lease must not consume another play")
    }

    func testOfflineThenAutoResumeAndBackgroundCancelsAutoResume() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        // Network drops while playing -> stops immediately, offlineWaiting banner, button disabled (AC-16).
        stack.networkObserver.emit(false)
        let offline = await stack.clock.waitUntil {
            if case .waitingNetwork = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(offline)
        XCTAssertFalse(fakePlayback.isPlaying)
        XCTAssertFalse(controller.uiState.isPrimaryButtonEnabled)
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.offlineWaiting")

        // Network comes back -> auto-calls resume and starts playing without user tap!
        stack.networkObserver.emit(true)
        let autoResumed = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState, stack.router.callCount(for: .resume) == 1 {
                return true
            }
            return false
        }
        XCTAssertTrue(autoResumed)
        XCTAssertTrue(fakePlayback.isPlaying)
        XCTAssertEqual(controller.uiState.playsLeft, 1)

        // Now drop network again AND background while offline -> restoring network MUST NOT auto-resume!
        stack.networkObserver.emit(false)
        let offline2 = await stack.clock.waitUntil {
            if case .waitingNetwork = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(offline2)

        controller.onAppBackgrounded()
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)

        stack.networkObserver.emit(true)
        await stack.clock.settle()
        XCTAssertFalse(fakePlayback.isPlaying, "Backgrounding while offline must cancel auto-resume")
        XCTAssertEqual(stack.router.callCount(for: .resume), 1)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
    }

    func testTimeUpFinishPartBeforeHTTPPartClosedSubmitAndPolicyMaxPlays() async throws {
        // 1. TimeUp when attempt countdown hits 00:00 (AC-20).
        let stackTimeUp = try AttachmentsTestStack.make { stateJSON in
            let now = FlexibleISO8601DateTranscoder.parse(stateJSON["serverNow"] as! String)!
            stateJSON["deadlineAt"] = FakeAttemptServer.isoString(now.addingTimeInterval(5))
        }
        defer { stackTimeUp.cleanup() }
        try stackTimeUp.router.configureAsset(assetId: assetId, maxPlays: 3, durationMs: 35_000)

        let readyTimeUp = await stackTimeUp.clock.waitUntil { stackTimeUp.engine.state.activeSessionId != nil }
        XCTAssertTrue(readyTimeUp)

        let fakePlaybackTimeUp = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stackTimeUp.clock)
        let controllerTimeUp = stackTimeUp.makeController(assetId: assetId, playbackEngine: fakePlaybackTimeUp)
        defer { controllerTimeUp.close() }

        XCTAssertEqual(controllerTimeUp.uiState.maxPlays, 3, "maxPlays must be read from audioPolicy (AC-20)")
        XCTAssertEqual(controllerTimeUp.uiState.playsLeft, 3)

        controllerTimeUp.onPrimaryAction()
        let playingTimeUp = await stackTimeUp.clock.waitUntil {
            if case .playing = controllerTimeUp.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playingTimeUp)

        await stackTimeUp.clock.advance(by: .seconds(6))
        let lockedTimeUp = await stackTimeUp.clock.waitUntil {
            !fakePlaybackTimeUp.isPlaying && controllerTimeUp.uiState.playbackState == .locked(reason: .timeUp)
        }
        XCTAssertTrue(lockedTimeUp)
        XCTAssertFalse(controllerTimeUp.uiState.isPrimaryButtonEnabled)
        XCTAssertEqual(controllerTimeUp.uiState.banner?.stringKey, "question.audioPlayer.timeUpReason")

        // 2. FinishPart stops audio STRICTLY BEFORE POST /parts/{partId}/finish hits FakeAttemptServer (AC-20).
        let stackFinish = try AttachmentsTestStack.make(initialStateVariant: "in-progress-per-part")
        defer { stackFinish.cleanup() }
        try stackFinish.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let readyFinish = await stackFinish.clock.waitUntil { stackFinish.engine.state.activeSessionId != nil }
        XCTAssertTrue(readyFinish)

        let fakePlaybackFinish = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stackFinish.clock)
        let partId = stackFinish.server.partId(at: 0)
        let controllerFinish = stackFinish.makeController(assetId: assetId, partId: partId, playbackEngine: fakePlaybackFinish)
        defer { controllerFinish.close() }

        controllerFinish.onPrimaryAction()
        let playingFinish = await stackFinish.clock.waitUntil {
            if case .playing = controllerFinish.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playingFinish)

        let audioStoppedBeforeFinishHTTP = NSLock()
        var wasPlayingAtFinishHTTP: Bool?
        stackFinish.server.onBeforeHandle = { call in
            guard call.endpoint == .finishPart else { return }
            let playingSnapshot = fakePlaybackFinish.isPlayingThreadSafe
            audioStoppedBeforeFinishHTTP.lock()
            wasPlayingAtFinishHTTP = playingSnapshot
            audioStoppedBeforeFinishHTTP.unlock()
        }

        stackFinish.engine.finishPart(partId: partId)
        let finished = await stackFinish.clock.waitUntil {
            stackFinish.server.callCount(for: .finishPart) == 1
                && controllerFinish.uiState.playbackState == .locked(reason: .partClosed)
        }
        XCTAssertTrue(finished)
        XCTAssertEqual(wasPlayingAtFinishHTTP, false, "Audio MUST stop before POST /parts/{partId}/finish is sent (AC-20)")
        XCTAssertEqual(controllerFinish.uiState.banner?.stringKey, "question.audioPlayer.partClosedReason")

        // 3. 409 PART_CLOSED on resume AND 200 endReason=CLOSED on end lock partClosed + refresh engine.
        let stackClosed = try AttachmentsTestStack.make()
        defer { stackClosed.cleanup() }
        try stackClosed.router.configureAsset(
            assetId: assetId,
            maxPlays: 2,
            durationMs: 30_000,
            playsUsed: 1,
            activePlayId: "play-0008-1",
            positionMs: 10_000,
            state: "PLAYING",
            knownPlayIds: ["play-0008-1"]
        )
        let readyClosed = await stackClosed.clock.waitUntil { stackClosed.engine.state.activeSessionId != nil }
        XCTAssertTrue(readyClosed)

        stackClosed.router.enqueue(
            .resume,
            try .fixture(operationId: Operations.resumeAudioPlay.id, status: 409, variant: "part-closed")
        )
        let controllerClosed = stackClosed.makeController(
            assetId: assetId,
            playbackEngine: FakeAudioPlaybackEngine(clock: stackClosed.clock)
        )
        defer { controllerClosed.close() }

        let getStateBeforeClosed = stackClosed.server.callCount(for: .getState)
        controllerClosed.onPrimaryAction()
        let lockedClosed = await stackClosed.clock.waitUntil {
            controllerClosed.uiState.playbackState == .locked(reason: .partClosed)
                && stackClosed.server.callCount(for: .getState) > getStateBeforeClosed
        }
        XCTAssertTrue(lockedClosed)
        XCTAssertEqual(controllerClosed.uiState.banner?.stringKey, "question.audioPlayer.partClosedReason")

        // 4. endReason=CLOSED on end + submit + externalDisabled.
        let stackEndClosed = try AttachmentsTestStack.make()
        defer { stackEndClosed.cleanup() }
        try stackEndClosed.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 20_000)
        let readyEnd = await stackEndClosed.clock.waitUntil { stackEndClosed.engine.state.activeSessionId != nil }
        XCTAssertTrue(readyEnd)

        stackEndClosed.router.enqueue(
            .end,
            try .fixture(operationId: Operations.endAudioPlay.id, status: 200, variant: "closed")
        )
        let fakeEnd = FakeAudioPlaybackEngine(durationMs: 20_000, clock: stackEndClosed.clock)
        let controllerEnd = stackEndClosed.makeController(assetId: assetId, playbackEngine: fakeEnd)
        defer { controllerEnd.close() }

        controllerEnd.onPrimaryAction()
        let playingEnd = await stackEndClosed.clock.waitUntil {
            if case .playing = controllerEnd.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playingEnd)

        let getStateBeforeEnd = stackEndClosed.server.callCount(for: .getState)
        fakeEnd.simulateCompletion()
        let lockedByEndClosed = await stackEndClosed.clock.waitUntil {
            controllerEnd.uiState.playbackState == .locked(reason: .partClosed)
                && stackEndClosed.server.callCount(for: .getState) > getStateBeforeEnd
        }
        XCTAssertTrue(lockedByEndClosed)

        // 5. Submit stops audio strictly BEFORE POST /submit + shows submittedReason, and externalDisabled shows disabledReason.
        let stackSubmit = try AttachmentsTestStack.make()
        defer { stackSubmit.cleanup() }
        try stackSubmit.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)
        let readySubmit = await stackSubmit.clock.waitUntil { stackSubmit.engine.state.activeSessionId != nil }
        XCTAssertTrue(readySubmit)

        let fakeSubmit = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stackSubmit.clock)
        let controllerSubmit = stackSubmit.makeController(assetId: assetId, playbackEngine: fakeSubmit)
        defer { controllerSubmit.close() }

        controllerSubmit.setExternalDisabled(true)
        XCTAssertEqual(controllerSubmit.uiState.playbackState, .locked(reason: .disabled))
        XCTAssertEqual(controllerSubmit.uiState.banner?.stringKey, "question.audioPlayer.disabledReason")

        controllerSubmit.setExternalDisabled(false)
        controllerSubmit.onPrimaryAction()
        let playingSubmit = await stackSubmit.clock.waitUntil {
            if case .playing = controllerSubmit.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playingSubmit)

        let submitLock = NSLock()
        var wasPlayingAtSubmitHTTP: Bool?
        stackSubmit.server.onBeforeHandle = { call in
            guard call.endpoint == .submit else { return }
            let playingSnapshot = fakeSubmit.isPlayingThreadSafe
            submitLock.lock()
            wasPlayingAtSubmitHTTP = playingSnapshot
            submitLock.unlock()
        }
        stackSubmit.engine.submit()
        let submitted = await stackSubmit.clock.waitUntil {
            stackSubmit.server.callCount(for: .submit) == 1
                && controllerSubmit.uiState.playbackState == .locked(reason: .submitted)
        }
        XCTAssertTrue(submitted)
        XCTAssertEqual(wasPlayingAtSubmitHTTP, false, "Audio MUST stop before POST /submit is sent")
        XCTAssertEqual(controllerSubmit.uiState.banner?.stringKey, "question.audioPlayer.submittedReason")
    }
}
