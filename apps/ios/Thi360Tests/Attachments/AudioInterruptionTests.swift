import AVFoundation
import Foundation
import MediaPlayer
import XCTest
@testable import Thi360

@MainActor
final class AudioInterruptionTests: XCTestCase {
    func testInterruptionBeganStopsAndEndedWithShouldResumeNeverAutoResumes() async throws {
        let center = NotificationCenter()
        let coordinator = AudioSessionCoordinator(
            audioSession: MockAudioSessionController(),
            notificationCenter: center,
            commandCenter: .shared(),
            nowPlayingCenter: .default()
        )
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }

        let assetId = "00000000-0000-7000-8000-000000000008"
        try stack.router.configureAsset(
            assetId: assetId,
            maxPlays: 2,
            durationMs: 35_000,
            audioData: try AttachmentsTestResources.silence35sWav()
        )
        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let itemId = stack.server.firstLeafItemId()
        stack.engine.answer(itemId: itemId, value: .singleChoice(optionId: "opt-a"))
        await stack.clock.settle()

        let realEngine = AVAudioPlayerPlaybackEngine(coordinator: coordinator)
        let controller = stack.makeController(
            assetId: assetId,
            fileName: "silence-35s.wav",
            playbackEngine: realEngine
        )
        defer { controller.close() }

        controller.onPrimaryAction()
        let started = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(started)

        let clockBefore = stack.engine.state.clock
        let answersBefore = stack.engine.state.answers
        let positionBefore = stack.engine.state.position

        // Incoming phone call interruption begins -> stops immediately.
        center.post(
            name: AVAudioSession.interruptionNotification,
            object: nil,
            userInfo: [
                AVAudioSessionInterruptionTypeKey: NSNumber(value: AVAudioSession.InterruptionType.began.rawValue)
            ]
        )
        let paused = await stack.clock.waitUntil {
            if case .paused(_, _, .interrupted) = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(paused)
        XCTAssertFalse(realEngine.isPlaying)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.pausedResumable")

        // Interruption ends WITH .shouldResume -> must NOT auto-resume (R-136, AC-15).
        center.post(
            name: AVAudioSession.interruptionNotification,
            object: nil,
            userInfo: [
                AVAudioSessionInterruptionTypeKey: NSNumber(value: AVAudioSession.InterruptionType.ended.rawValue),
                AVAudioSessionInterruptionOptionKey: NSNumber(value: AVAudioSession.InterruptionOptions.shouldResume.rawValue)
            ]
        )
        await stack.clock.settle()
        XCTAssertFalse(realEngine.isPlaying)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(stack.router.callCount(for: .resume), 0)

        // Engine clock, answers, and position remain untouched (AC-15).
        XCTAssertEqual(stack.engine.state.clock, clockBefore)
        XCTAssertEqual(stack.engine.state.answers, answersBefore)
        XCTAssertEqual(stack.engine.state.position, positionBefore)

        // App returns to foreground after interruption -> engine refreshes once while player remains at "Phát lại".
        let getStateBeforeFg = stack.server.callCount(for: .getState)
        stack.engine.setForeground(false)
        stack.engine.setForeground(true)
        let refreshed = await stack.clock.waitUntil {
            stack.server.callCount(for: .getState) > getStateBeforeFg
        }
        XCTAssertTrue(refreshed)
        XCTAssertFalse(realEngine.isPlaying)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(stack.router.callCount(for: .resume), 0)
    }

    func testOldDeviceUnavailableStopsImmediatelyAndSwitchesToResume() async throws {
        let center = NotificationCenter()
        let coordinator = AudioSessionCoordinator(
            audioSession: MockAudioSessionController(),
            notificationCenter: center,
            commandCenter: .shared(),
            nowPlayingCenter: .default()
        )
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }

        let assetId = "00000000-0000-7000-8000-000000000008"
        try stack.router.configureAsset(
            assetId: assetId,
            maxPlays: 2,
            durationMs: 35_000,
            audioData: try AttachmentsTestResources.silence35sWav()
        )
        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let realEngine = AVAudioPlayerPlaybackEngine(coordinator: coordinator)
        let controller = stack.makeController(
            assetId: assetId,
            fileName: "silence-35s.wav",
            playbackEngine: realEngine
        )
        defer { controller.close() }

        controller.onPrimaryAction()
        let started = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(started)

        // Headphones unplugged -> AVAudioSession.RouteChangeReason.oldDeviceUnavailable (AC-17).
        center.post(
            name: AVAudioSession.routeChangeNotification,
            object: nil,
            userInfo: [
                AVAudioSessionRouteChangeReasonKey: NSNumber(value: AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue)
            ]
        )
        let stopped = await stack.clock.waitUntil {
            if case .paused(_, _, .routeChange) = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(stopped)
        XCTAssertFalse(realEngine.isPlaying)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.pausedResumable")
    }
}
