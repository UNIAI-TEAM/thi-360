import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AudioLeaveScreenTests: XCTestCase {
    private let assetId = "00000000-0000-7000-8000-000000000008"

    func testSwitchPartInFreeNavigationStopsWithinOneSecondAndReturnsToResumeWithoutAutoplay() async throws {
        let stack = try AttachmentsTestStack.make(initialStateVariant: "in-progress-shared") { stateJSON in
            stateJSON["navigationMode"] = "FREE"
        }
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let part1Id = stack.server.partId(at: 0)
        let part2Id = stack.server.partId(at: 1).isEmpty ? "part-2-free" : stack.server.partId(at: 1)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, partId: part1Id, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        // Student switches from part 1 -> part 2: view disappears calling onLeaveScreen().
        let t0 = stack.clock.now
        stack.engine.position(partId: part2Id, itemId: nil)
        controller.onLeaveScreen()
        let stopLatency = (fakePlayback.stopTimestamps.last ?? stack.clock.now) - t0

        XCTAssertLessThan(stopLatency, .seconds(1), "Audio must stop within 1s when switching parts (AC-18)")
        XCTAssertFalse(fakePlayback.isPlaying)
        XCTAssertFalse(fakePlayback.hasLoadedAudioData)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(controller.uiState.playsLeft, 1)

        // Student switches back to part 1: controller shows "Phát lại", playsLeft unchanged, does NOT auto-play.
        stack.engine.position(partId: part1Id, itemId: stack.server.firstLeafItemId())
        await stack.clock.settle()
        XCTAssertFalse(fakePlayback.isPlaying)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.pausedResumable")
        XCTAssertEqual(controller.uiState.playsLeft, 1)
    }

    func testLeavingAttemptToHistoryStopsWithinOneSecondAndReopeningShowsResume() async throws {
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 35_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        // Advance 10s so server records position before leaving to History.
        await stack.clock.advance(by: .seconds(10))
        let progressed = await stack.clock.waitUntil { stack.router.callCount(for: .progress) >= 1 }
        XCTAssertTrue(progressed)

        let t0 = stack.clock.now
        controller.onLeaveScreen()
        controller.close()
        let stopLatency = (fakePlayback.stopTimestamps.last ?? stack.clock.now) - t0
        XCTAssertLessThan(stopLatency, .seconds(1), "Audio must stop within 1s when leaving attempt screen")
        XCTAssertFalse(fakePlayback.isPlaying)

        // Re-enter the open attempt -> new controller hydrated from server shows "Phát lại" and does not auto-play.
        stack.engine.refresh()
        await stack.clock.settle()
        let fakePlaybackReopened = FakeAudioPlaybackEngine(durationMs: 35_000, clock: stack.clock)
        let reopened = stack.makeController(assetId: assetId, playbackEngine: fakePlaybackReopened)
        defer { reopened.close() }

        let hydratedPaused = await stack.clock.waitUntil {
            reopened.uiState.primaryButtonRole == .resume && reopened.uiState.playsLeft == 1
        }
        XCTAssertTrue(hydratedPaused)
        XCTAssertFalse(fakePlaybackReopened.isPlaying)
        XCTAssertEqual(fakePlaybackReopened.startCalls.count, 0)
        XCTAssertEqual(reopened.uiState.banner?.stringKey, "question.audioPlayer.pausedResumable")
    }
}
