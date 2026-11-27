import AVFoundation
import Foundation

struct AudioPlaybackCallbacks {
    let onCompleted: @MainActor () -> Void
    let onError: @MainActor () -> Void
    let onInterruptionBegan: @MainActor () -> Void
    let onOldDeviceUnavailable: @MainActor () -> Void
    let onRemotePause: @MainActor () -> Void
}

enum AudioPlaybackStartResult: Equatable, Sendable {
    case started(durationMs: Int64)
    case failed
}

@MainActor
protocol AudioPlaybackEngine: AnyObject {
    var isPlaying: Bool { get }
    var hasLoadedAudioData: Bool { get }

    func start(
        data: Data,
        fileName: String,
        startPositionMs: Int64,
        callbacks: AudioPlaybackCallbacks
    ) -> AudioPlaybackStartResult

    func currentPositionMs() -> Int64
    func seekToAuthorized(_ positionMs: Int64)
    func enforcePlaybackRateAndAntiSeek()
    func stopAndRelease()
}

private final class AVAudioPlayerDelegateBridge: NSObject, AVAudioPlayerDelegate {
    private let onFinish: @Sendable (Bool) -> Void
    private let onDecodeError: @Sendable () -> Void

    init(
        onFinish: @escaping @Sendable (Bool) -> Void,
        onDecodeError: @escaping @Sendable () -> Void
    ) {
        self.onFinish = onFinish
        self.onDecodeError = onDecodeError
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        onFinish(flag)
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: (any Error)?) {
        onDecodeError()
    }
}

@MainActor
final class AVAudioPlayerPlaybackEngine: AudioPlaybackEngine {
    private static let antiSeekDriftToleranceMs: Int64 = 1_500

    private let coordinator: AudioSessionCoordinator
    private let playerId = UUID()
    private var player: AVAudioPlayer?
    private var delegateBridge: AVAudioPlayerDelegateBridge?
    private var inMemoryData: Data?
    private var callbacks: AudioPlaybackCallbacks?
    private var expectedAnchorPositionMs: Int64 = 0
    private var expectedAnchorWallTime: TimeInterval = 0
    #if targetEnvironment(simulator)
    private var simulatorSilentActive = false
    private var simulatorDurationMs: Int64 = 0
    private var simulatorCompletionTask: Task<Void, Never>?
    #endif

    init(coordinator: AudioSessionCoordinator? = nil) {
        self.coordinator = coordinator ?? .shared
    }

    var isPlaying: Bool {
        #if targetEnvironment(simulator)
        return (player?.isPlaying ?? false) || simulatorSilentActive
        #else
        return player?.isPlaying ?? false
        #endif
    }

    var hasLoadedAudioData: Bool {
        inMemoryData != nil
    }

    func start(
        data: Data,
        fileName: String,
        startPositionMs: Int64,
        callbacks: AudioPlaybackCallbacks
    ) -> AudioPlaybackStartResult {
        stopAndRelease()
        guard !data.isEmpty else { return .failed }

        coordinator.beginPlayback(playerId: playerId, fileName: fileName) { [weak self] event in
            self?.handleSessionEvent(event)
        }

        do {
            let avPlayer = try AVAudioPlayer(data: data)
            avPlayer.enableRate = false
            avPlayer.rate = 1.0

            let durationMs = max(0, Int64((avPlayer.duration * 1_000.0).rounded()))
            let clampedStartMs = durationMs > 0
                ? min(max(0, startPositionMs), durationMs)
                : max(0, startPositionMs)
            avPlayer.currentTime = TimeInterval(clampedStartMs) / 1_000.0

            let bridge = AVAudioPlayerDelegateBridge(
                onFinish: { [weak self] successfully in
                    Task { @MainActor [weak self] in
                        guard let self, let activeCallbacks = self.callbacks else { return }
                        if successfully {
                            activeCallbacks.onCompleted()
                        } else {
                            activeCallbacks.onError()
                        }
                    }
                },
                onDecodeError: { [weak self] in
                    Task { @MainActor [weak self] in
                        self?.callbacks?.onError()
                    }
                }
            )
            avPlayer.delegate = bridge
            avPlayer.prepareToPlay()
            let hardwareStarted = avPlayer.play()
            if !hardwareStarted {
                #if targetEnvironment(simulator)
                guard durationMs > 0 else {
                    coordinator.endPlayback(playerId: playerId)
                    return .failed
                }
                self.simulatorSilentActive = true
                self.simulatorDurationMs = durationMs
                #else
                coordinator.endPlayback(playerId: playerId)
                return .failed
                #endif
            }

            self.inMemoryData = data
            self.player = avPlayer
            self.delegateBridge = bridge
            self.callbacks = callbacks
            self.expectedAnchorPositionMs = clampedStartMs
            self.expectedAnchorWallTime = ProcessInfo.processInfo.systemUptime
            #if targetEnvironment(simulator)
            if self.simulatorSilentActive {
                scheduleSimulatorCompletion(fromPositionMs: clampedStartMs)
            }
            #endif
            return .started(durationMs: durationMs)
        } catch {
            coordinator.endPlayback(playerId: playerId)
            return .failed
        }
    }

    func currentPositionMs() -> Int64 {
        guard let player else { return expectedAnchorPositionMs }
        #if targetEnvironment(simulator)
        if simulatorSilentActive {
            enforcePlaybackRateAndAntiSeek()
            let now = ProcessInfo.processInfo.systemUptime
            let elapsedMs = max(0, Int64(((now - expectedAnchorWallTime) * 1_000.0).rounded()))
            return min(simulatorDurationMs, expectedAnchorPositionMs + elapsedMs)
        }
        #endif
        enforcePlaybackRateAndAntiSeek()
        return max(0, Int64((player.currentTime * 1_000.0).rounded()))
    }

    func seekToAuthorized(_ positionMs: Int64) {
        guard let player else { return }
        let safeMs = max(0, positionMs)
        let durationMs = max(0, Int64((player.duration * 1_000.0).rounded()))
        let clampedMs = durationMs > 0 ? min(safeMs, durationMs) : safeMs
        player.currentTime = TimeInterval(clampedMs) / 1_000.0
        expectedAnchorPositionMs = clampedMs
        expectedAnchorWallTime = ProcessInfo.processInfo.systemUptime
        #if targetEnvironment(simulator)
        if simulatorSilentActive {
            scheduleSimulatorCompletion(fromPositionMs: clampedMs)
        }
        #endif
    }

    func enforcePlaybackRateAndAntiSeek() {
        guard let player else { return }
        if player.rate != 1.0 {
            player.rate = 1.0
        }
        if player.enableRate {
            player.enableRate = false
        }
        #if targetEnvironment(simulator)
        if simulatorSilentActive {
            let now = ProcessInfo.processInfo.systemUptime
            let elapsedMs = max(0, Int64(((now - expectedAnchorWallTime) * 1_000.0).rounded()))
            let expectedMs = min(simulatorDurationMs, expectedAnchorPositionMs + elapsedMs)
            let actualMs = max(0, Int64((player.currentTime * 1_000.0).rounded()))
            if abs(actualMs - expectedMs) > Self.antiSeekDriftToleranceMs {
                player.currentTime = TimeInterval(expectedMs) / 1_000.0
            }
            return
        }
        #endif
        guard player.isPlaying else { return }
        let now = ProcessInfo.processInfo.systemUptime
        let elapsedMs = max(0, Int64(((now - expectedAnchorWallTime) * 1_000.0).rounded()))
        let expectedMs = expectedAnchorPositionMs + elapsedMs
        let actualMs = max(0, Int64((player.currentTime * 1_000.0).rounded()))
        if abs(actualMs - expectedMs) > Self.antiSeekDriftToleranceMs {
            let durationMs = max(0, Int64((player.duration * 1_000.0).rounded()))
            let clamped = durationMs > 0 ? min(expectedMs, durationMs) : expectedMs
            player.currentTime = TimeInterval(clamped) / 1_000.0
            expectedAnchorPositionMs = clamped
            expectedAnchorWallTime = now
        } else {
            expectedAnchorPositionMs = actualMs
            expectedAnchorWallTime = now
        }
    }

    var rawPlayerPositionMsForTesting: Int64? {
        player.map { max(0, Int64(($0.currentTime * 1_000.0).rounded())) }
    }

    var rawPlayerRateForTesting: Float? {
        player?.rate
    }

    func simulateExternalTamperForTesting(positionMs: Int64? = nil, rate: Float? = nil) {
        guard let player else { return }
        if let positionMs {
            player.currentTime = TimeInterval(max(0, positionMs)) / 1_000.0
        }
        if let rate {
            player.enableRate = true
            player.rate = rate
        }
    }

    func stopAndRelease() {
        #if targetEnvironment(simulator)
        simulatorCompletionTask?.cancel()
        simulatorCompletionTask = nil
        simulatorSilentActive = false
        simulatorDurationMs = 0
        #endif
        callbacks = nil
        delegateBridge = nil
        if let player {
            player.delegate = nil
            player.stop()
        }
        player = nil
        inMemoryData = nil
        expectedAnchorPositionMs = 0
        expectedAnchorWallTime = 0
        coordinator.endPlayback(playerId: playerId)
    }

    #if targetEnvironment(simulator)
    private func scheduleSimulatorCompletion(fromPositionMs positionMs: Int64) {
        simulatorCompletionTask?.cancel()
        let remainingMs = max(0, simulatorDurationMs - positionMs)
        simulatorCompletionTask = Task { @MainActor [weak self] in
            if remainingMs > 0 {
                try? await Task.sleep(for: .milliseconds(remainingMs))
            }
            guard let self, !Task.isCancelled, self.simulatorSilentActive else { return }
            self.simulatorSilentActive = false
            self.callbacks?.onCompleted()
        }
    }
    #endif

    private func handleSessionEvent(_ event: AudioSessionEvent) {
        guard let activeCallbacks = callbacks else { return }
        switch event {
        case .interruptionBegan:
            activeCallbacks.onInterruptionBegan()
        case .oldDeviceUnavailable:
            activeCallbacks.onOldDeviceUnavailable()
        case .remotePause:
            activeCallbacks.onRemotePause()
        }
    }
}
