import AVFoundation
import Foundation
import MediaPlayer

enum AudioSessionEvent: Equatable, Sendable {
    case interruptionBegan
    case oldDeviceUnavailable
    case remotePause
}

protocol AudioSessionControlling: AnyObject {
    func setCategory(_ category: AVAudioSession.Category, mode: AVAudioSession.Mode, options: AVAudioSession.CategoryOptions) throws
    func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws
}

final class SystemAudioSessionController: AudioSessionControlling {
    private let session: AVAudioSession

    init(session: AVAudioSession = .sharedInstance()) {
        self.session = session
    }

    func setCategory(
        _ category: AVAudioSession.Category,
        mode: AVAudioSession.Mode,
        options: AVAudioSession.CategoryOptions
    ) throws {
        try session.setCategory(category, mode: mode, options: options)
    }

    func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws {
        try session.setActive(active, options: options)
    }
}

@MainActor
final class AudioSessionCoordinator {
    static let shared = AudioSessionCoordinator()

    private let audioSession: any AudioSessionControlling
    private let notificationCenter: NotificationCenter
    private let commandCenter: MPRemoteCommandCenter
    private let nowPlayingCenter: MPNowPlayingInfoCenter

    private var hasConfiguredSession = false
    private var hasConfiguredRemoteCommands = false
    private var pauseCommandTarget: Any?
    private var interruptionObserver: NSObjectProtocol?
    private var routeChangeObserver: NSObjectProtocol?
    private var listeners: [UUID: (AudioSessionEvent) -> Void] = [:]
    private var activePlayers: [UUID: String] = [:]

    private(set) var configurationCount = 0

    init(
        audioSession: any AudioSessionControlling = SystemAudioSessionController(),
        notificationCenter: NotificationCenter = .default,
        commandCenter: MPRemoteCommandCenter = .shared(),
        nowPlayingCenter: MPNowPlayingInfoCenter = .default()
    ) {
        self.audioSession = audioSession
        self.notificationCenter = notificationCenter
        self.commandCenter = commandCenter
        self.nowPlayingCenter = nowPlayingCenter
        installNotificationObservers()
    }

    deinit {
        if let interruptionObserver {
            notificationCenter.removeObserver(interruptionObserver)
        }
        if let routeChangeObserver {
            notificationCenter.removeObserver(routeChangeObserver)
        }
    }

    func configureAudioSessionOnce() {
        guard !hasConfiguredSession else { return }
        // Use .playback without .mixWithOthers so other apps/calls interrupt cleanly and remote commands bind,
        // while multiple AVAudioPlayer instances within our own process still mix concurrently (AC-15, AC-19).
        try? audioSession.setCategory(.playback, mode: .default, options: [])
        hasConfiguredSession = true
        configurationCount += 1
    }

    func configureRemoteCommands() {
        let disabledCommands: [MPRemoteCommand] = [
            commandCenter.playCommand,
            commandCenter.stopCommand,
            commandCenter.togglePlayPauseCommand,
            commandCenter.changePlaybackPositionCommand,
            commandCenter.skipForwardCommand,
            commandCenter.skipBackwardCommand,
            commandCenter.seekForwardCommand,
            commandCenter.seekBackwardCommand,
            commandCenter.changePlaybackRateCommand,
            commandCenter.nextTrackCommand,
            commandCenter.previousTrackCommand,
            commandCenter.changeRepeatModeCommand,
            commandCenter.changeShuffleModeCommand,
            commandCenter.enableLanguageOptionCommand,
            commandCenter.disableLanguageOptionCommand,
            commandCenter.ratingCommand,
            commandCenter.likeCommand,
            commandCenter.dislikeCommand,
            commandCenter.bookmarkCommand
        ]
        for command in disabledCommands {
            command.removeTarget(nil)
            command.isEnabled = false
        }

        if let pauseCommandTarget {
            commandCenter.pauseCommand.removeTarget(pauseCommandTarget)
        }
        commandCenter.pauseCommand.isEnabled = true
        pauseCommandTarget = commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handleRemotePauseCommand()
            }
            return .success
        }
        hasConfiguredRemoteCommands = true
    }

    @discardableResult
    func beginPlayback(playerId: UUID, fileName: String, onEvent: @escaping (AudioSessionEvent) -> Void) -> Bool {
        configureAudioSessionOnce()
        if !hasConfiguredRemoteCommands {
            configureRemoteCommands()
        }
        do {
            try audioSession.setActive(true, options: [])
        } catch {
            // Simulator or headless test hosts may not expose hardware audio routes; still bind state cleanly.
        }
        listeners[playerId] = onEvent
        activePlayers[playerId] = fileName
        updateNowPlayingInfo(title: fileName)
        return true
    }

    func endPlayback(playerId: UUID) {
        listeners.removeValue(forKey: playerId)
        activePlayers.removeValue(forKey: playerId)

        if let remainingTitle = activePlayers.values.first {
            updateNowPlayingInfo(title: remainingTitle)
        } else {
            clearNowPlayingInfo()
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    func clearNowPlayingInfo() {
        nowPlayingCenter.nowPlayingInfo = nil
    }

    @discardableResult
    func handleRemotePauseCommand() -> MPRemoteCommandHandlerStatus {
        guard !listeners.isEmpty else { return .noActionableNowPlayingItem }
        broadcast(.remotePause)
        return .success
    }

    private func updateNowPlayingInfo(title: String) {
        // Expose title only — no duration, elapsed time, or playback rate — so Lock Screen shows no scrub bar (R-134, AC-8).
        nowPlayingCenter.nowPlayingInfo = [
            MPMediaItemPropertyTitle: title
        ]
    }

    private func installNotificationObservers() {
        interruptionObserver = notificationCenter.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            guard let rawType = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue,
                  let type = AVAudioSession.InterruptionType(rawValue: rawType)
            else {
                return
            }
            // Only .began pauses; .ended (even with .shouldResume) intentionally never auto-resumes (R-136, AC-15).
            guard type == .began else { return }
            Task { @MainActor [weak self] in
                self?.broadcast(.interruptionBegan)
            }
        }

        routeChangeObserver = notificationCenter.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            guard let rawReason = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.uintValue,
                  let reason = AVAudioSession.RouteChangeReason(rawValue: rawReason),
                  reason == .oldDeviceUnavailable
            else {
                return
            }
            Task { @MainActor [weak self] in
                self?.broadcast(.oldDeviceUnavailable)
            }
        }
    }

    private func broadcast(_ event: AudioSessionEvent) {
        let targets = Array(listeners.values)
        for listener in targets {
            listener(event)
        }
    }
}
