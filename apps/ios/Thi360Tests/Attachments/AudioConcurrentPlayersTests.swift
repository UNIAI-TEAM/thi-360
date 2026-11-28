import AVFoundation
import Foundation
import MediaPlayer
import XCTest
@testable import Thi360

@MainActor
final class AudioConcurrentPlayersTests: XCTestCase {
    func testTwoPlayersPlaySimultaneouslyWithoutInterruptingEachOtherAndConfigureSessionOnce() async throws {
        let mockSession = MockAudioSessionController()
        let coordinator = AudioSessionCoordinator(
            audioSession: mockSession,
            notificationCenter: NotificationCenter(),
            commandCenter: .shared(),
            nowPlayingCenter: .default()
        )
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }

        let assetA = "00000000-0000-7000-8000-0000000000a1"
        let assetB = "00000000-0000-7000-8000-0000000000b2"
        let wav = try AttachmentsTestResources.silence35sWav()
        try stack.router.configureAsset(assetId: assetA, maxPlays: 2, durationMs: 35_000, audioData: wav)
        try stack.router.configureAsset(assetId: assetB, maxPlays: 2, durationMs: 35_000, audioData: wav)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let engineA = AVAudioPlayerPlaybackEngine(coordinator: coordinator)
        let engineB = AVAudioPlayerPlaybackEngine(coordinator: coordinator)
        let controllerA = stack.makeController(assetId: assetA, fileName: "audio-a.wav", playbackEngine: engineA)
        let controllerB = stack.makeController(assetId: assetB, fileName: "audio-b.wav", playbackEngine: engineB)
        defer {
            controllerA.close()
            controllerB.close()
        }

        // Start player A.
        controllerA.onPrimaryAction()
        let playingA = await stack.clock.waitUntil {
            if case .playing = controllerA.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playingA)
        XCTAssertTrue(engineA.isPlaying)
        XCTAssertEqual(controllerA.uiState.playsLeft, 1)

        // Start player B while A is still playing (AC-19).
        controllerB.onPrimaryAction()
        let playingB = await stack.clock.waitUntil {
            if case .playing = controllerB.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playingB)

        // Both players are simultaneously playing; A's playsLeft did not change, B's decremented by 1.
        XCTAssertTrue(engineA.isPlaying, "Starting player B must NOT stop player A (AC-19)")
        XCTAssertTrue(engineB.isPlaying)
        if case .playing = controllerA.uiState.playbackState {
            // Expected.
        } else {
            XCTFail("Player A must stay in .playing state when player B starts")
        }
        XCTAssertEqual(controllerA.uiState.playsLeft, 1)
        XCTAssertEqual(controllerB.uiState.playsLeft, 1)

        // AVAudioSession category configured strictly once across both players (AC-19).
        XCTAssertEqual(coordinator.configurationCount, 1)
        XCTAssertEqual(mockSession.categoryCalls.count, 1)
        XCTAssertEqual(mockSession.categoryCalls.first?.category, .playback)
        XCTAssertEqual(mockSession.categoryCalls.first?.options, [])
    }
}
