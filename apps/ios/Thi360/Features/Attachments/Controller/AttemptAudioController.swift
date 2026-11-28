import Foundation
import Observation

@Observable
@MainActor
final class AttemptAudioController: AudioLogoutCleanable {
    nonisolated static let defaultProgressInterval: Duration = .seconds(10)
    nonisolated static let defaultPositionPollInterval: Duration = .milliseconds(250)

    let attemptId: String
    let assetId: String
    let partId: String?
    let fileName: String

    private(set) var uiState: AudioPlayerUIState

    @ObservationIgnored private let engine: AttemptEngine
    @ObservationIgnored private let gateway: any AttemptAudioGateway
    @ObservationIgnored private let playbackEngine: any AudioPlaybackEngine
    @ObservationIgnored private let clock: any EngineClock
    @ObservationIgnored private let requestIdGenerator: () -> String
    @ObservationIgnored private let progressInterval: Duration
    @ObservationIgnored private let positionPollInterval: Duration
    @ObservationIgnored private let registry: AttemptAudioSessionRegistry

    @ObservationIgnored private var isClosed = false
    @ObservationIgnored private var epoch: UInt64 = 0
    @ObservationIgnored private var snapshot: AudioPlaySnapshot = .empty()
    @ObservationIgnored private var policyMaxPlays: Int = AudioPlaySnapshot.defaultMaxPlays
    @ObservationIgnored private var mediaDurationMs: Int64 = 0
    @ObservationIgnored private var lastConfirmedPositionMs: Int64 = 0
    @ObservationIgnored private var displayedPositionMs: Int64 = 0
    @ObservationIgnored private var pendingRequestId: String?
    @ObservationIgnored private var endSentForPlayId: String?
    @ObservationIgnored private var wasPlayingBeforeOffline = false
    @ObservationIgnored private var wasOnline: Bool
    @ObservationIgnored private var currentExternalDisabled: Bool
    @ObservationIgnored private var apiClosedReason: AudioLockReason?
    @ObservationIgnored private var lockedByTimeUp = false
    @ObservationIgnored private var beforeClosePart = false
    @ObservationIgnored private var beforeCloseAttempt = false
    @ObservationIgnored private var partClosedEventSeen = false
    @ObservationIgnored private var hasPlayError = false
    @ObservationIgnored private var lastHydratedAttempt: Components.Schemas.AttemptStateResponse?
    @ObservationIgnored private var forceAcceptNextServerSnapshot = false
    @ObservationIgnored private var playbackState: AudioPlaybackState = .idle(playsLeft: AudioPlaySnapshot.defaultMaxPlays)

    @ObservationIgnored private var actionTask: Task<Void, Never>?
    @ObservationIgnored private var progressTask: Task<Void, Never>?
    @ObservationIgnored private var positionTickerTask: Task<Void, Never>?
    @ObservationIgnored private var endTask: Task<Void, Never>?
    @ObservationIgnored private var engineEventTask: Task<Void, Never>?
    @ObservationIgnored private var registryToken: UUID?

    init(
        attemptId: String,
        assetId: String,
        partId: String?,
        fileName: String,
        engine: AttemptEngine,
        gateway: any AttemptAudioGateway,
        playbackEngine: (any AudioPlaybackEngine)? = nil,
        clock: any EngineClock = SystemEngineClock(),
        requestIdGenerator: @escaping () -> String = { UUID().uuidString.lowercased() },
        progressInterval: Duration = AttemptAudioController.defaultProgressInterval,
        positionPollInterval: Duration = AttemptAudioController.defaultPositionPollInterval,
        externalDisabled: Bool = false,
        registry: AttemptAudioSessionRegistry? = nil
    ) {
        let resolvedRegistry = registry ?? .shared
        self.attemptId = attemptId
        self.assetId = assetId
        self.partId = partId
        self.fileName = fileName
        self.engine = engine
        self.gateway = gateway
        self.playbackEngine = playbackEngine ?? AVAudioPlayerPlaybackEngine()
        self.clock = clock
        self.requestIdGenerator = requestIdGenerator
        self.progressInterval = progressInterval
        self.positionPollInterval = positionPollInterval
        self.wasOnline = engine.state.network == .online
        self.currentExternalDisabled = externalDisabled
        self.registry = resolvedRegistry
        self.uiState = AudioPlayerUIState(
            assetId: assetId,
            fileName: fileName,
            playbackState: .idle(playsLeft: AudioPlaySnapshot.defaultMaxPlays),
            playsUsed: 0,
            playsLeft: AudioPlaySnapshot.defaultMaxPlays,
            maxPlays: AudioPlaySnapshot.defaultMaxPlays,
            positionMs: 0,
            durationMs: 0,
            isOnline: engine.state.network == .online,
            hasPlayError: false
        )

        self.registryToken = resolvedRegistry.register(self)
        hydrateFromEngineState(engine.state, initial: true)
        publish()

        let eventStream = engine.events
        self.engineEventTask = Task { @MainActor [weak self] in
            for await event in eventStream {
                guard let self, !self.isClosed else { break }
                self.handleEngineEvent(event)
            }
        }
        startStateObservation()
    }

    var isActivelyPlayingMedia: Bool {
        playbackEngine.isPlaying
    }

    var hasInMemoryAudioData: Bool {
        playbackEngine.hasLoadedAudioData
    }

    func setExternalDisabled(_ disabled: Bool) {
        guard !isClosed, currentExternalDisabled != disabled else { return }
        currentExternalDisabled = disabled
        let shouldStop = applyEngineState(engine.state)
        publish()
        if shouldStop {
            playbackEngine.stopAndRelease()
        }
    }

    func onPrimaryAction() {
        guard !isClosed else { return }
        let engineState = engine.state
        guard engineState.network == .online else { return }
        guard case .editing = engineState.session else { return }
        guard resolveLockReason(engineState) == nil else { return }

        switch playbackState {
        case .opening, .playing, .waitingNetwork, .exhausted, .locked:
            return

        case let .paused(playId, _, _):
            epoch &+= 1
            let myEpoch = epoch
            hasPlayError = false
            playbackState = .opening(requestId: "resume:\(playId)", isResuming: true)
            publish()
            actionTask?.cancel()
            actionTask = Task { @MainActor [weak self] in
                await self?.resumeAndPlay(playId: playId, myEpoch: myEpoch)
            }

        case .idle:
            if snapshot.playsLeft <= 0 {
                playbackState = .exhausted
                publish()
                return
            }
            epoch &+= 1
            let myEpoch = epoch
            let reqId = pendingRequestId ?? {
                let created = requestIdGenerator()
                pendingRequestId = created
                return created
            }()
            hasPlayError = false
            playbackState = .opening(requestId: reqId, isResuming: false)
            publish()
            actionTask?.cancel()
            actionTask = Task { @MainActor [weak self] in
                await self?.openAndPlay(requestId: reqId, myEpoch: myEpoch)
            }
        }
    }

    func onAppBackgrounded() {
        pauseForInterruption(.background)
    }

    func onLeaveScreen() {
        pauseForInterruption(.leftScreen)
    }

    func pauseForInterruption(_ reason: AudioPauseReason) {
        guard !isClosed else { return }
        var shouldStopMedia = false

        switch playbackState {
        case let .playing(playId, _):
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            hasPlayError = (reason == .error)
            wasPlayingBeforeOffline = false
            displayedPositionMs = lastConfirmedPositionMs
            playbackState = .paused(
                playId: playId,
                serverPositionMs: lastConfirmedPositionMs,
                reason: reason
            )
            publish()

        case .opening:
            epoch &+= 1
            actionTask?.cancel()
            actionTask = nil
            cancelTimers()
            shouldStopMedia = true
            wasPlayingBeforeOffline = false
            if snapshot.state == .playing, let activePlayId = snapshot.playId {
                displayedPositionMs = lastConfirmedPositionMs
                playbackState = .paused(
                    playId: activePlayId,
                    serverPositionMs: lastConfirmedPositionMs,
                    reason: reason
                )
            } else if snapshot.playsLeft <= 0 {
                playbackState = .exhausted
            } else {
                playbackState = .idle(playsLeft: snapshot.playsLeft)
            }
            publish()

        case let .waitingNetwork(playId):
            epoch &+= 1
            cancelTimers()
            wasPlayingBeforeOffline = false
            hasPlayError = false
            displayedPositionMs = lastConfirmedPositionMs
            playbackState = .paused(
                playId: playId,
                serverPositionMs: lastConfirmedPositionMs,
                reason: reason
            )
            publish()

        case .idle, .paused, .exhausted, .locked:
            return
        }

        if shouldStopMedia {
            playbackEngine.stopAndRelease()
        }
    }

    func stopAndPurgeForLogout() {
        epoch &+= 1
        actionTask?.cancel()
        actionTask = nil
        endTask?.cancel()
        endTask = nil
        cancelTimers()
        pendingRequestId = nil
        wasPlayingBeforeOffline = false
        endSentForPlayId = nil
        hasPlayError = false
        playbackState = .locked(reason: .disabled)
        publish()
        playbackEngine.stopAndRelease()
    }

    func close() {
        guard !isClosed else { return }
        isClosed = true
        epoch &+= 1
        actionTask?.cancel()
        actionTask = nil
        endTask?.cancel()
        endTask = nil
        cancelTimers()
        engineEventTask?.cancel()
        engineEventTask = nil
        playbackEngine.stopAndRelease()
        if let registryToken {
            registry.unregister(registryToken)
            self.registryToken = nil
        }
    }

    private func startStateObservation() {
        guard !isClosed else { return }
        _ = withObservationTracking {
            engine.state
        } onChange: { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, !self.isClosed else { return }
                let nextState = self.engine.state
                self.startStateObservation()
                self.handleEngineState(nextState)
            }
        }
    }

    private func openAndPlay(requestId: String, myEpoch: UInt64) async {
        do {
            let opened = try await gateway.openPlay(assetId: assetId, requestId: requestId)
            guard !isClosed, epoch == myEpoch else { return }
            pendingRequestId = nil
            await fetchAndStartPlayback(snap: opened, myEpoch: myEpoch)
        } catch let audioError as AudioError {
            guard !isClosed, epoch == myEpoch else { return }
            if case let .inProgress(resumePlayId) = audioError {
                pendingRequestId = nil
                if let resumePlayId, !resumePlayId.isEmpty {
                    await resumeAndPlay(playId: resumePlayId, myEpoch: myEpoch)
                } else {
                    handleError(.stale, myEpoch: myEpoch, fromOpenPlay: true)
                }
                return
            }
            handleError(audioError, myEpoch: myEpoch, fromOpenPlay: true)
        } catch is CancellationError {
            return
        } catch {
            guard !isClosed, epoch == myEpoch else { return }
            handleError(.network(.unknown), myEpoch: myEpoch, fromOpenPlay: true)
        }
    }

    private func resumeAndPlay(playId: String, myEpoch: UInt64) async {
        do {
            let resumed = try await gateway.resume(assetId: assetId, playId: playId)
            guard !isClosed, epoch == myEpoch else { return }
            await fetchAndStartPlayback(snap: resumed, myEpoch: myEpoch)
        } catch let audioError as AudioError {
            guard !isClosed, epoch == myEpoch else { return }
            handleError(audioError, myEpoch: myEpoch, fromOpenPlay: false, playId: playId)
        } catch is CancellationError {
            return
        } catch {
            guard !isClosed, epoch == myEpoch else { return }
            handleError(.network(.unknown), myEpoch: myEpoch, fromOpenPlay: false, playId: playId)
        }
    }

    private func fetchAndStartPlayback(snap: AudioPlaySnapshot, myEpoch: UInt64) async {
        guard !isClosed, epoch == myEpoch, canOperate() else { return }
        applyAuthoritativeSnapshot(snap)

        guard snap.state == .playing, let activePlayId = snap.playId, !activePlayId.isEmpty else {
            if snap.endReason == .closed {
                apiClosedReason = .partClosed
                playbackState = .locked(reason: .partClosed)
                publish()
                engine.refresh()
            } else {
                playbackState = snap.playsLeft <= 0 ? .exhausted : .idle(playsLeft: snap.playsLeft)
                publish()
            }
            return
        }
        publish()

        let audioData: Data
        do {
            audioData = try await gateway.fetchAudio(assetId: assetId, playId: activePlayId)
        } catch let audioError as AudioError {
            guard !isClosed, epoch == myEpoch else { return }
            handleError(audioError, myEpoch: myEpoch, fromOpenPlay: false, playId: activePlayId)
            return
        } catch is CancellationError {
            return
        } catch {
            guard !isClosed, epoch == myEpoch else { return }
            handleError(.network(.unknown), myEpoch: myEpoch, fromOpenPlay: false, playId: activePlayId)
            return
        }

        guard !isClosed, epoch == myEpoch, canOperate() else { return }
        let startPositionMs = snap.positionMs

        let callbacks = AudioPlaybackCallbacks(
            onCompleted: { [weak self] in
                self?.handlePlaybackCompleted(playId: activePlayId, myEpoch: myEpoch)
            },
            onError: { [weak self] in
                self?.pauseForInterruption(.error)
            },
            onInterruptionBegan: { [weak self] in
                self?.pauseForInterruption(.interrupted)
            },
            onOldDeviceUnavailable: { [weak self] in
                self?.pauseForInterruption(.routeChange)
            },
            onRemotePause: { [weak self] in
                self?.pauseForInterruption(.remotePause)
            }
        )

        let outcome = playbackEngine.start(
            data: audioData,
            fileName: fileName,
            startPositionMs: startPositionMs,
            callbacks: callbacks
        )

        switch outcome {
        case .failed:
            guard !isClosed, epoch == myEpoch else { return }
            hasPlayError = true
            playbackState = .paused(
                playId: activePlayId,
                serverPositionMs: lastConfirmedPositionMs,
                reason: .error
            )
            publish()

        case let .started(durationMs):
            guard !isClosed, epoch == myEpoch, canOperate() else {
                playbackEngine.stopAndRelease()
                return
            }
            if durationMs > 0 {
                mediaDurationMs = durationMs
            }
            if endSentForPlayId != activePlayId {
                endSentForPlayId = nil
            }
            hasPlayError = false
            displayedPositionMs = startPositionMs
            playbackState = .playing(playId: activePlayId, positionMs: startPositionMs)
            startProgressAndTicker(playId: activePlayId, myEpoch: myEpoch)
            publish()
        }
    }

    private func startProgressAndTicker(playId: String, myEpoch: UInt64) {
        cancelTimers()
        let safePoll = max(.milliseconds(50), positionPollInterval)
        let safeInterval = max(.milliseconds(100), progressInterval)

        positionTickerTask = Task { @MainActor [weak self] in
            while let self, !self.isClosed, !Task.isCancelled {
                do {
                    try await self.clock.sleep(for: safePoll)
                } catch {
                    break
                }
                guard !self.isClosed,
                      !Task.isCancelled,
                      self.epoch == myEpoch,
                      case .playing = self.playbackState
                else {
                    break
                }
                let livePos = self.playbackEngine.currentPositionMs()
                let duration = self.effectiveDurationMs()
                let clamped = duration > 0 ? min(max(0, livePos), duration) : max(0, livePos)
                self.displayedPositionMs = clamped
                self.playbackState = .playing(playId: playId, positionMs: clamped)
                self.publish()
            }
        }

        progressTask = Task { @MainActor [weak self] in
            while let self, !self.isClosed, !Task.isCancelled {
                do {
                    try await self.clock.sleep(for: safeInterval)
                } catch {
                    break
                }
                guard !self.isClosed,
                      !Task.isCancelled,
                      self.epoch == myEpoch,
                      case .playing = self.playbackState,
                      self.endSentForPlayId != playId,
                      self.canOperate()
                else {
                    break
                }

                let rawPos = self.playbackEngine.currentPositionMs()
                let nonDecreasing = max(self.lastConfirmedPositionMs, rawPos)
                let duration = self.effectiveDurationMs()
                let boundedPos = duration > 0 ? min(nonDecreasing, duration) : nonDecreasing

                do {
                    let nextSnap = try await self.gateway.progress(
                        assetId: self.assetId,
                        playId: playId,
                        positionMs: boundedPos
                    )
                    guard !self.isClosed,
                          !Task.isCancelled,
                          self.epoch == myEpoch,
                          self.endSentForPlayId != playId
                    else {
                        break
                    }
                    self.lastConfirmedPositionMs = max(self.lastConfirmedPositionMs, nextSnap.positionMs)
                    self.snapshot = nextSnap
                    if nextSnap.state == .ended {
                        self.epoch &+= 1
                        self.cancelTimers()
                        self.displayedPositionMs = nextSnap.positionMs
                        if nextSnap.endReason == .closed {
                            self.apiClosedReason = .partClosed
                            self.playbackState = .locked(reason: .partClosed)
                            self.publish()
                            self.playbackEngine.stopAndRelease()
                            self.engine.refresh()
                        } else {
                            self.playbackState = nextSnap.playsLeft <= 0
                                ? .exhausted
                                : .idle(playsLeft: nextSnap.playsLeft)
                            self.publish()
                            self.playbackEngine.stopAndRelease()
                        }
                        break
                    }
                    self.publish()
                } catch let audioError as AudioError {
                    guard !self.isClosed, self.epoch == myEpoch, self.endSentForPlayId != playId else { break }
                    let isSeekRejected: Bool
                    if case .seekRejected = audioError {
                        isSeekRejected = true
                    } else {
                        isSeekRejected = false
                    }
                    self.handleError(audioError, myEpoch: myEpoch, fromOpenPlay: false, playId: playId)
                    if !isSeekRejected {
                        break
                    }
                } catch is CancellationError {
                    break
                } catch {
                    guard !self.isClosed, self.epoch == myEpoch, self.endSentForPlayId != playId else { break }
                    self.handleError(.network(.unknown), myEpoch: myEpoch, fromOpenPlay: false, playId: playId)
                    break
                }
            }
        }
    }

    private func handlePlaybackCompleted(playId: String, myEpoch: UInt64) {
        guard !isClosed, epoch == myEpoch, endSentForPlayId != playId else { return }
        // Mark end sent synchronously before cancelling progress so a simultaneous 10s tick never races end.
        endSentForPlayId = playId
        cancelTimers()
        playbackEngine.stopAndRelease()

        endTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let ended = try await self.gateway.end(assetId: self.assetId, playId: playId)
                guard !self.isClosed, self.epoch == myEpoch else { return }
                self.applyAuthoritativeSnapshot(ended)
                if ended.endReason == .closed {
                    self.apiClosedReason = .partClosed
                    self.playbackState = .locked(reason: .partClosed)
                    self.publish()
                    self.engine.refresh()
                } else {
                    self.playbackState = ended.playsLeft <= 0 ? .exhausted : .idle(playsLeft: ended.playsLeft)
                    self.publish()
                }
            } catch let audioError as AudioError {
                guard !self.isClosed, self.epoch == myEpoch else { return }
                switch audioError {
                case .network, .other:
                    self.endSentForPlayId = nil
                default:
                    break
                }
                self.handleError(audioError, myEpoch: myEpoch, fromOpenPlay: false, playId: playId)
            } catch is CancellationError {
                return
            } catch {
                guard !self.isClosed, self.epoch == myEpoch else { return }
                self.endSentForPlayId = nil
                self.handleError(.network(.unknown), myEpoch: myEpoch, fromOpenPlay: false, playId: playId)
            }
        }
    }

    private func handleError(
        _ error: AudioError,
        myEpoch: UInt64,
        fromOpenPlay: Bool,
        playId: String? = nil
    ) {
        guard !isClosed, epoch == myEpoch else { return }

        var shouldStopMedia = false
        var shouldReportRevoked = false
        var shouldRefresh = false
        var seekTargetMs: Int64?

        switch error {
        case .exhausted:
            pendingRequestId = nil
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            hasPlayError = false
            snapshot.playsLeft = 0
            snapshot.state = .ended
            playbackState = .exhausted

        case let .seekRejected(positionMs):
            let serverPos = max(0, positionMs ?? lastConfirmedPositionMs)
            lastConfirmedPositionMs = serverPos
            displayedPositionMs = serverPos
            snapshot.positionMs = serverPos
            let activeId = playId ?? snapshot.playId
            if let activeId, case .playing = playbackState {
                playbackState = .playing(playId: activeId, positionMs: serverPos)
            }
            seekTargetMs = serverPos

        case .stale:
            pendingRequestId = nil
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            hasPlayError = false
            forceAcceptNextServerSnapshot = true
            shouldRefresh = true
            playbackState = snapshot.playsLeft <= 0 ? .exhausted : .idle(playsLeft: snapshot.playsLeft)

        case .partClosed:
            pendingRequestId = nil
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            hasPlayError = false
            apiClosedReason = .partClosed
            playbackState = .locked(reason: .partClosed)
            shouldRefresh = true

        case .timeUp:
            pendingRequestId = nil
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            hasPlayError = false
            lockedByTimeUp = true
            apiClosedReason = .timeUp
            playbackState = .locked(reason: .timeUp)
            shouldRefresh = true

        case .sessionRevoked:
            pendingRequestId = nil
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            hasPlayError = false
            playbackState = .locked(reason: .evicted)
            shouldReportRevoked = true

        case .unauthorized:
            pendingRequestId = nil
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            hasPlayError = false
            let activeId = playId ?? (snapshot.state == .playing ? snapshot.playId : nil)
            if let activeId {
                playbackState = .paused(
                    playId: activeId,
                    serverPositionMs: lastConfirmedPositionMs,
                    reason: .error
                )
            } else {
                playbackState = .idle(playsLeft: snapshot.playsLeft)
            }

        case .inProgress:
            pendingRequestId = nil
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            shouldRefresh = true

        case .network, .other:
            // Preserve pendingRequestId across transient/network errors so retrying openPlay reuses it (AC-517).
            let offlineNow = engine.state.network != .online
            let activeId = playId ?? (snapshot.state == .playing ? snapshot.playId : nil)
            epoch &+= 1
            cancelTimers()
            shouldStopMedia = true
            if offlineNow, let activeId, !fromOpenPlay {
                wasPlayingBeforeOffline = true
                hasPlayError = false
                playbackState = .waitingNetwork(playId: activeId)
            } else if !fromOpenPlay, let activeId {
                hasPlayError = true
                displayedPositionMs = lastConfirmedPositionMs
                playbackState = .paused(
                    playId: activeId,
                    serverPositionMs: lastConfirmedPositionMs,
                    reason: .error
                )
            } else {
                hasPlayError = true
                playbackState = snapshot.playsLeft <= 0 ? .exhausted : .idle(playsLeft: snapshot.playsLeft)
            }
        }

        publish()

        if let seekTargetMs {
            playbackEngine.seekToAuthorized(seekTargetMs)
        }
        if shouldStopMedia {
            playbackEngine.stopAndRelease()
        }
        if shouldReportRevoked {
            engine.reportSessionRevoked()
        }
        if shouldRefresh {
            engine.refresh()
        }
    }

    private func handleEngineEvent(_ event: EngineEvent) {
        guard !isClosed else { return }
        var shouldStopMedia = false

        switch event {
        case let .beforeClose(target):
            switch target {
            case .attempt:
                beforeCloseAttempt = true
                shouldStopMedia = stopAndLock(.submitted)
            case let .part(closedPartId):
                if partId == nil || partId == closedPartId {
                    beforeClosePart = true
                    shouldStopMedia = stopAndLock(.partClosed)
                }
            }

        case let .partClosed(closedPartId):
            if partId == nil || partId == closedPartId {
                partClosedEventSeen = true
                if isDeadlineExpiredForThisAudio(engine.state) {
                    lockedByTimeUp = true
                }
                let reason: AudioLockReason = lockedByTimeUp ? .timeUp : .partClosed
                shouldStopMedia = stopAndLock(reason)
            }

        case let .attemptClosed(closedReason):
            let reason: AudioLockReason
            switch closedReason {
            case .timeUp:
                lockedByTimeUp = true
                reason = .timeUp
            case .submitted:
                reason = .submitted
            case .choiceTimeout, .aborted, .notOpen:
                reason = .partClosed
            }
            shouldStopMedia = stopAndLock(reason)

        case .sessionLost:
            shouldStopMedia = stopAndLock(.evicted)
        }

        publish()
        if shouldStopMedia {
            playbackEngine.stopAndRelease()
        }
    }

    private func handleEngineState(_ engineState: EngineState) {
        guard !isClosed else { return }

        let onlineNow = engineState.network == .online
        let wentOffline = wasOnline && !onlineNow
        let cameOnline = !wasOnline && onlineNow
        wasOnline = onlineNow

        hydrateFromEngineState(engineState, initial: false)

        if let lockReason = resolveLockReason(engineState) {
            let shouldStop = stopAndLock(lockReason)
            publish()
            if shouldStop {
                playbackEngine.stopAndRelease()
            }
            return
        }

        if case .locked = playbackState {
            playbackState = unlockedStateFromSnapshot()
        }

        var shouldStopMedia = false
        var autoResumePlayId: String?
        var autoResumeEpoch: UInt64 = 0

        if wentOffline {
            switch playbackState {
            case let .playing(playId, _):
                epoch &+= 1
                cancelTimers()
                shouldStopMedia = true
                wasPlayingBeforeOffline = true
                hasPlayError = false
                playbackState = .waitingNetwork(playId: playId)

            case .opening:
                if snapshot.state == .playing, let activeId = snapshot.playId {
                    epoch &+= 1
                    actionTask?.cancel()
                    actionTask = nil
                    cancelTimers()
                    shouldStopMedia = true
                    wasPlayingBeforeOffline = true
                    playbackState = .waitingNetwork(playId: activeId)
                }

            default:
                break
            }
        } else if cameOnline {
            if case let .waitingNetwork(waitingPlayId) = playbackState,
               wasPlayingBeforeOffline,
               case .editing = engineState.session
            {
                wasPlayingBeforeOffline = false
                epoch &+= 1
                let myEpoch = epoch
                hasPlayError = false
                playbackState = .opening(requestId: "auto-resume:\(waitingPlayId)", isResuming: true)
                autoResumePlayId = waitingPlayId
                autoResumeEpoch = myEpoch
            }
        }

        publish()
        if shouldStopMedia {
            playbackEngine.stopAndRelease()
        }
        if let autoResumePlayId {
            actionTask?.cancel()
            actionTask = Task { @MainActor [weak self] in
                await self?.resumeAndPlay(playId: autoResumePlayId, myEpoch: autoResumeEpoch)
            }
        }
    }

    private func hydrateFromEngineState(_ engineState: EngineState, initial: Bool) {
        let attempt = engineState.attempt
        if !initial && !forceAcceptNextServerSnapshot && attempt == lastHydratedAttempt {
            return
        }
        lastHydratedAttempt = attempt

        if let serverPolicyMax = attempt?.audioPolicy?.maxPlays, serverPolicyMax > 0 {
            policyMaxPlays = Int(serverPolicyMax)
        }

        if let serverEntry = attempt?.audioPlays?.first(where: { $0.assetId == assetId }) {
            let fresh = AudioPlaySnapshot.fromView(serverEntry, maxPlays: policyMaxPlays)
            let isLocallyActive: Bool
            switch playbackState {
            case .playing, .opening, .waitingNetwork:
                isLocallyActive = true
            case .idle, .paused, .exhausted, .locked:
                isLocallyActive = false
            }

            let ignoreRegressive = !initial && !forceAcceptNextServerSnapshot && (
                fresh.playsUsed < snapshot.playsUsed
                    || (fresh.playsUsed == snapshot.playsUsed
                        && snapshot.state == .playing
                        && fresh.state == .idle)
                    || (fresh.playId == snapshot.playId
                        && snapshot.state == .playing
                        && fresh.state == .playing
                        && fresh.positionMs < lastConfirmedPositionMs)
                    || (fresh.playsUsed == snapshot.playsUsed
                        && fresh.playId == snapshot.playId
                        && snapshot.state == .ended
                        && fresh.state != .ended)
            )

            if !ignoreRegressive {
                let acceptingStaleReload = forceAcceptNextServerSnapshot
                forceAcceptNextServerSnapshot = false
                let previous = snapshot
                snapshot = fresh
                if !acceptingStaleReload, fresh.playId == previous.playId, fresh.state == .playing {
                    lastConfirmedPositionMs = max(lastConfirmedPositionMs, fresh.positionMs)
                } else {
                    lastConfirmedPositionMs = fresh.positionMs
                }
                if case .playing = playbackState {
                    // Preserve live cursor while actively playing.
                } else {
                    displayedPositionMs = lastConfirmedPositionMs
                }

                if initial {
                    if let lockReason = resolveLockReason(engineState) {
                        playbackState = .locked(reason: lockReason)
                    } else {
                        playbackState = unlockedStateFromSnapshot()
                    }
                } else if !isLocallyActive {
                    if case .locked = playbackState {
                        // Lock state takes precedence until unlocked.
                    } else if case let .paused(currentPlayId, _, currentReason) = playbackState,
                              fresh.state == .playing,
                              fresh.playId == currentPlayId
                    {
                        playbackState = .paused(
                            playId: currentPlayId,
                            serverPositionMs: lastConfirmedPositionMs,
                            reason: currentReason
                        )
                    } else {
                        playbackState = unlockedStateFromSnapshot()
                    }
                }
            }
        } else if initial {
            forceAcceptNextServerSnapshot = false
            snapshot = .empty(maxPlays: policyMaxPlays)
            lastConfirmedPositionMs = 0
            displayedPositionMs = 0
            if let lockReason = resolveLockReason(engineState) {
                playbackState = .locked(reason: lockReason)
            } else {
                playbackState = unlockedStateFromSnapshot()
            }
        } else {
            forceAcceptNextServerSnapshot = false
            if snapshot.playsUsed == 0, snapshot.state == .idle {
                snapshot = .empty(maxPlays: policyMaxPlays)
                if case .idle = playbackState {
                    playbackState = .idle(playsLeft: snapshot.playsLeft)
                }
            }
        }
    }

    private func applyEngineState(_ engineState: EngineState) -> Bool {
        if let lockReason = resolveLockReason(engineState) {
            return stopAndLock(lockReason)
        }
        if case .locked = playbackState {
            playbackState = unlockedStateFromSnapshot()
        }
        return false
    }

    private func unlockedStateFromSnapshot() -> AudioPlaybackState {
        if snapshot.state == .playing, let activePlayId = snapshot.playId, !activePlayId.isEmpty {
            return .paused(
                playId: activePlayId,
                serverPositionMs: lastConfirmedPositionMs,
                reason: .reopened
            )
        }
        if snapshot.playsLeft <= 0 {
            return .exhausted
        }
        return .idle(playsLeft: snapshot.playsLeft)
    }

    private func isDeadlineExpiredForThisAudio(_ engineState: EngineState) -> Bool {
        guard engineState.attempt?.state == .IN_PROGRESS,
              engineState.clock.isExpired
        else {
            return false
        }
        switch engineState.clock.target {
        case .attempt:
            return true
        case let .part(targetPartId):
            return partId == nil || partId == targetPartId
        case .choice, .none:
            return false
        }
    }

    private func resolveLockReason(_ engineState: EngineState) -> AudioLockReason? {
        if apiClosedReason == .timeUp || lockedByTimeUp {
            return .timeUp
        }
        if engineState.closed == .timeUp || isDeadlineExpiredForThisAudio(engineState) {
            lockedByTimeUp = true
            return .timeUp
        }

        let attemptState = engineState.attempt?.state
        if beforeCloseAttempt
            || engineState.closed == .submitted
            || engineState.command == .sending(.submit)
            || attemptState == .GRADING
            || attemptState == .PENDING_REVIEW
            || attemptState == .SCORED
            || attemptState == .GRADING_ERROR
        {
            return .submitted
        }

        let partState = partId.flatMap { targetId in
            engineState.attempt?.parts?.first(where: { $0.partId == targetId })?.state
        }
        let finishingThisPart = engineState.command == .sending(.finishPart)
            && (partId == nil || engineState.position?.partId == partId)
        if apiClosedReason == .partClosed
            || beforeClosePart
            || partClosedEventSeen
            || finishingThisPart
            || partState == .CLOSED
            || partState == .SKIPPED
            || engineState.session == .readOnly(.partNotOpen)
        {
            return .partClosed
        }

        switch engineState.session {
        case .evicted, .openElsewhere:
            return .evicted
        case .acquiring:
            return .acquiring
        case .readOnly:
            return .disabled
        case .editing:
            break
        }

        if currentExternalDisabled || engineState.closed != nil {
            return .disabled
        }
        return nil
    }

    private func stopAndLock(_ reason: AudioLockReason) -> Bool {
        let hadActiveWork: Bool
        switch playbackState {
        case .playing, .opening:
            hadActiveWork = true
        case .idle, .paused, .waitingNetwork, .exhausted, .locked:
            hadActiveWork = false
        }

        if hadActiveWork {
            epoch &+= 1
            actionTask?.cancel()
            actionTask = nil
            cancelTimers()
        }
        pendingRequestId = nil
        wasPlayingBeforeOffline = false
        hasPlayError = false
        playbackState = .locked(reason: reason)
        return hadActiveWork
    }

    private func canOperate() -> Bool {
        let engineState = engine.state
        guard !isClosed, engineState.network == .online else { return false }
        guard case .editing = engineState.session else { return false }
        return resolveLockReason(engineState) == nil
    }

    private func applyAuthoritativeSnapshot(_ next: AudioPlaySnapshot) {
        snapshot = next
        lastConfirmedPositionMs = next.positionMs
        displayedPositionMs = next.positionMs
    }

    private func effectiveDurationMs() -> Int64 {
        snapshot.durationMs > 0 ? snapshot.durationMs : mediaDurationMs
    }

    private func cancelTimers() {
        progressTask?.cancel()
        progressTask = nil
        positionTickerTask?.cancel()
        positionTickerTask = nil
    }

    private func publish() {
        uiState = AudioPlayerUIState(
            assetId: assetId,
            fileName: fileName,
            playbackState: playbackState,
            playsUsed: snapshot.playsUsed,
            playsLeft: snapshot.playsLeft,
            maxPlays: policyMaxPlays,
            positionMs: displayedPositionMs,
            durationMs: effectiveDurationMs(),
            isOnline: wasOnline,
            hasPlayError: hasPlayError
        )
    }
}
