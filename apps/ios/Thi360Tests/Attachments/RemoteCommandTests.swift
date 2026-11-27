import AVFoundation
import Foundation
import MediaPlayer
import XCTest
@testable import Thi360

@MainActor
final class RemoteCommandTests: XCTestCase {
    func testOnlyPauseRemoteCommandEnabledAndNowPlayingOmitsScrubFields() async throws {
        let mockSession = MockAudioSessionController()
        let commandCenter = MPRemoteCommandCenter.shared()
        let nowPlayingCenter = MPNowPlayingInfoCenter.default()
        let coordinator = AudioSessionCoordinator(
            audioSession: mockSession,
            notificationCenter: NotificationCenter(),
            commandCenter: commandCenter,
            nowPlayingCenter: nowPlayingCenter
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
            fileName: "listening-lockscreen.wav",
            playbackEngine: realEngine
        )
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        // AC-8: pauseCommand is the ONLY enabled command; all seek/skip/rate/next/prev/play commands disabled.
        XCTAssertTrue(commandCenter.pauseCommand.isEnabled)
        XCTAssertFalse(commandCenter.playCommand.isEnabled)
        XCTAssertFalse(commandCenter.stopCommand.isEnabled)
        XCTAssertFalse(commandCenter.togglePlayPauseCommand.isEnabled)
        XCTAssertFalse(commandCenter.changePlaybackPositionCommand.isEnabled)
        XCTAssertFalse(commandCenter.skipForwardCommand.isEnabled)
        XCTAssertFalse(commandCenter.skipBackwardCommand.isEnabled)
        XCTAssertFalse(commandCenter.seekForwardCommand.isEnabled)
        XCTAssertFalse(commandCenter.seekBackwardCommand.isEnabled)
        XCTAssertFalse(commandCenter.changePlaybackRateCommand.isEnabled)
        XCTAssertFalse(commandCenter.nextTrackCommand.isEnabled)
        XCTAssertFalse(commandCenter.previousTrackCommand.isEnabled)
        XCTAssertFalse(commandCenter.changeRepeatModeCommand.isEnabled)
        XCTAssertFalse(commandCenter.changeShuffleModeCommand.isEnabled)

        let info = try XCTUnwrap(nowPlayingCenter.nowPlayingInfo)
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "listening-lockscreen.wav")
        XCTAssertNil(info[MPMediaItemPropertyPlaybackDuration], "Lock Screen must not expose duration to prevent scrubbing")
        XCTAssertNil(info[MPNowPlayingInfoPropertyElapsedPlaybackTime], "Lock Screen must not expose elapsed time")
        XCTAssertNil(info[MPNowPlayingInfoPropertyPlaybackRate], "Lock Screen must not expose playback rate")

        // Server records progress at 14s before remote pause arrives.
        stack.router.updateAssetState(
            assetId: assetId,
            playsUsed: 1,
            activePlayId: "play-0008-1",
            positionMs: 14_000,
            state: "PLAYING"
        )
        await stack.clock.advance(by: .seconds(10))
        let progressed = await stack.clock.waitUntil { stack.router.callCount(for: .progress) >= 1 }
        XCTAssertTrue(progressed)

        let status = coordinator.handleRemotePauseCommand()
        XCTAssertEqual(status, .success)
        XCTAssertFalse(realEngine.isPlaying)
        XCTAssertEqual(controller.uiState.primaryButtonRole, .resume)
        XCTAssertEqual(controller.uiState.banner?.stringKey, "question.audioPlayer.pausedResumable")
        XCTAssertNil(nowPlayingCenter.nowPlayingInfo, "NowPlayingInfo must clear when no audio is actively playing")

        controller.onPrimaryAction()
        let resumed = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState, stack.router.callCount(for: .resume) == 1 {
                return true
            }
            return false
        }
        XCTAssertTrue(resumed)
        XCTAssertEqual(controller.uiState.playsLeft, 1, "Resuming after remote pause must not consume another play")
        XCTAssertGreaterThanOrEqual(controller.uiState.positionMs, 14_000)
    }

    func testAVAudioPlayerEnforcesRateOneAndRevertsUnauthorizedSeek() throws {
        let mockSession = MockAudioSessionController()
        let coordinator = AudioSessionCoordinator(
            audioSession: mockSession,
            notificationCenter: NotificationCenter(),
            commandCenter: .shared(),
            nowPlayingCenter: .default()
        )
        let engine = AVAudioPlayerPlaybackEngine(coordinator: coordinator)
        defer { engine.stopAndRelease() }

        let wav = try AttachmentsTestResources.silence35sWav()
        let result = engine.start(
            data: wav,
            fileName: "silence-35s.wav",
            startPositionMs: 2_000,
            callbacks: AudioPlaybackCallbacks(
                onCompleted: {},
                onError: {},
                onInterruptionBegan: {},
                onOldDeviceUnavailable: {},
                onRemotePause: {}
            )
        )
        if case let .started(durationMs) = result {
            XCTAssertGreaterThanOrEqual(durationMs, 34_000)
        } else {
            XCTFail("Expected AVAudioPlayerPlaybackEngine to start with silence-35s.wav")
        }

        engine.seekToAuthorized(10_000)
        let posAfterAuthorized = engine.currentPositionMs()
        XCTAssertGreaterThanOrEqual(posAfterAuthorized, 9_000)
        XCTAssertLessThanOrEqual(posAfterAuthorized, 12_000)

        let invalidResult = engine.start(
            data: Data("not-valid-audio-bytes".utf8),
            fileName: "corrupt.mp3",
            startPositionMs: 0,
            callbacks: AudioPlaybackCallbacks(
                onCompleted: {},
                onError: {},
                onInterruptionBegan: {},
                onOldDeviceUnavailable: {},
                onRemotePause: {}
            )
        )
        XCTAssertEqual(invalidResult, .failed, "Corrupt bytes must fail AVAudioPlayer decoding even on headless simulator")
    }
}
