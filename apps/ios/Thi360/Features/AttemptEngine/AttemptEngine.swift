import Foundation
import Observation

@MainActor
@Observable
final class AttemptEngine {
    let attemptId: String
    let accountId: String

    private(set) var state: EngineState

    @ObservationIgnored private let api: any AttemptAPIClient
    @ObservationIgnored private let draftStore: DraftStore
    @ObservationIgnored private let clock: any EngineClock
    @ObservationIgnored private let connectivity: ConnectivityMonitor
    @ObservationIgnored private var connectivityToken: UUID?

    @ObservationIgnored private var serverClock = ServerClock()
    @ObservationIgnored private var editSession: EditSession!
    @ObservationIgnored private var heartbeatLoop: HeartbeatLoop!
    @ObservationIgnored private var positionTracker: PositionTracker!
    @ObservationIgnored private var saveQueue: SaveQueue!
    @ObservationIgnored private var partPolling: PartPolling!
    @ObservationIgnored private var partCommands: PartCommands!

    @ObservationIgnored private var eventContinuations: [UUID: AsyncStream<EngineEvent>.Continuation] = [:]
    @ObservationIgnored private var isForeground: Bool = true
    @ObservationIgnored private var needsForegroundHeartbeat: Bool = false
    @ObservationIgnored private var isClosing: Bool = false
    @ObservationIgnored private var disposed: Bool = false
    @ObservationIgnored private var started: Bool = false

    @ObservationIgnored private var claimErrorReason: SaveFailureReason?
    @ObservationIgnored private var claimOffline: Bool = false
    @ObservationIgnored private var explicitClosedReason: ClosedReason?
    @ObservationIgnored private var emittedAttemptClosed: Bool = false
    @ObservationIgnored private var handledExpiredDeadlines: Set<String> = []
    @ObservationIgnored private var handledExpiredChoiceDeadlines: Set<String> = []

    @ObservationIgnored private var loadTask: Task<Void, Never>?
    @ObservationIgnored private var commandTask: Task<Void, Never>?
    @ObservationIgnored private var countdownTask: Task<Void, Never>?
    @ObservationIgnored private var deadlineReloadTask: Task<Void, Never>?
    @ObservationIgnored private var foregroundTask: Task<Void, Never>?
    @ObservationIgnored private var onlineTask: Task<Void, Never>?

    init(
        attemptId: String,
        accountId: String,
        api: any AttemptAPIClient,
        store: AccountScopedStore,
        clock: any EngineClock = SystemEngineClock(),
        connectivity: ConnectivityMonitor,
        sessionIdGenerator: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() },
        idempotencyKeyGenerator: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() },
        autoStart: Bool = true
    ) {
        self.attemptId = attemptId
        self.accountId = accountId
        self.api = api
        self.draftStore = DraftStore(store: store)
        self.clock = clock
        self.connectivity = connectivity
        self.state = EngineState.initial(isOnline: connectivity.isOnline)

        self.positionTracker = PositionTracker(
            attemptId: attemptId,
            draftStore: self.draftStore,
            clock: clock,
            onFlushRequested: { [weak self] flushAllPending in
                guard let self else { return }
                if flushAllPending {
                    self.saveQueue.cancelAllPendingTimers()
                }
                self.saveQueue.scheduleFlush()
            }
        )

        self.saveQueue = SaveQueue(
            attemptId: attemptId,
            api: api,
            draftStore: self.draftStore,
            clock: clock,
            positionTracker: self.positionTracker,
            sessionIdProvider: { [weak self] in
                self?.editSession.activeSessionId
            },
            isReadOnly: { [weak self] in
                self?.isEditingBlocked() ?? true
            },
            isOnline: { [weak self] in
                self?.connectivity.isOnline ?? false
            },
            onEvent: { [weak self] event in
                self?.handleSaveQueueEvent(event)
            }
        )

        self.editSession = EditSession(
            attemptId: attemptId,
            api: api,
            draftStore: self.draftStore,
            clock: clock,
            sessionIdGenerator: sessionIdGenerator,
            isOnline: { [weak self] in
                self?.connectivity.isOnline ?? false
            },
            onTakeoverReload: { [weak self] sessionId in
                guard let self else { return .closed }
                return await self.performTakeoverReload(sessionId: sessionId)
            },
            onEvent: { [weak self] event in
                self?.handleEditSessionEvent(event)
            }
        )

        self.heartbeatLoop = HeartbeatLoop(
            attemptId: attemptId,
            api: api,
            clock: clock,
            sessionIdProvider: { [weak self] in
                self?.editSession.activeSessionId
            },
            canSend: { [weak self] in
                guard let self else { return false }
                return !self.disposed && self.isForeground && self.connectivity.isOnline && self.state.closed == nil
            },
            onOutcome: { [weak self] outcome in
                self?.handleHeartbeatOutcome(outcome)
            }
        )

        self.partPolling = PartPolling(
            clock: clock,
            onPoll: { [weak self] in
                await self?.reloadStateFromServer(reconcileLocalDrafts: false)
            }
        )

        self.partCommands = PartCommands(
            attemptId: attemptId,
            api: api,
            keyGenerator: idempotencyKeyGenerator
        )

        self.connectivityToken = connectivity.addObserver { [weak self] isOnline in
            self?.handleConnectivityChanged(isOnline)
        }

        if autoStart {
            start()
        }
    }

    var events: AsyncStream<EngineEvent> {
        let id = UUID()
        return AsyncStream { continuation in
            if self.disposed {
                continuation.finish()
                return
            }
            self.eventContinuations[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in
                    self?.eventContinuations.removeValue(forKey: id)
                }
            }
        }
    }

    func start() {
        guard !disposed, !started else { return }
        started = true
        loadTask = Task { @MainActor [weak self] in
            await self?.bootstrap()
        }
    }

    func answer(itemId: ItemID, value: AnswerValue) {
        guard !disposed, !isEditingBlocked(), state.attempt?.state == .IN_PROGRESS else {
            return
        }
        saveQueue.recordAnswer(itemId: itemId, value: value)
    }

    func flag(itemId: ItemID, flagged: Bool) {
        guard !disposed, !isEditingBlocked(), state.attempt?.state == .IN_PROGRESS else {
            return
        }
        saveQueue.recordFlag(itemId: itemId, flagged: flagged)
    }

    func position(partId: PartID, itemId: ItemID?) {
        guard !disposed else { return }
        let next = Position(partId: partId, itemId: itemId)
        state.position = next

        let trackDirty = !isEditingBlocked()
            && state.attempt?.state == .IN_PROGRESS
        let canSync = trackDirty
            && editSession.activeSessionId != nil
            && connectivity.isOnline
        positionTracker.update(
            partId: partId,
            itemId: itemId,
            trackDirty: trackDirty,
            canSync: canSync,
            hasUnsavedChanges: saveQueue.hasDirtyChanges
        )
        refreshCountdownAndPolling()
    }

    func takeover() {
        guard !disposed else { return }
        guard editSession.takeover() else { return }
        state.command = .sending(.takeover)
    }

    func finishPart(partId: PartID) {
        guard !disposed,
              !isEditingBlocked(),
              state.attempt?.state == .IN_PROGRESS,
              let sessionId = editSession.activeSessionId,
              !isCommandSending()
        else {
            return
        }

        emit(.beforeClose(.part(partId)))
        saveQueue.cancelAllPendingTimers()
        saveQueue.cancelRetryTimer()

        let finalAnswers = saveQueue.collectFinalAnswers(partId: partId)
        state.command = .sending(.finishPart)

        commandTask = Task { @MainActor [weak self] in
            await self?.runFinishPart(partId: partId, sessionId: sessionId, finalAnswers: finalAnswers)
        }
    }

    func submit() {
        guard !disposed,
              !isEditingBlocked(),
              state.attempt?.state == .IN_PROGRESS,
              let sessionId = editSession.activeSessionId,
              !isCommandSending()
        else {
            return
        }

        emit(.beforeClose(.attempt))
        saveQueue.cancelAllPendingTimers()
        saveQueue.cancelRetryTimer()

        let finalAnswers = saveQueue.collectFinalAnswers(partId: nil)
        state.command = .sending(.submit)

        commandTask = Task { @MainActor [weak self] in
            await self?.runSubmit(sessionId: sessionId, finalAnswers: finalAnswers)
        }
    }

    func choosePart(partId: PartID, optionCode: String, subjectCodes: [String]? = nil) {
        guard !disposed,
              !isEditingBlocked(),
              state.attempt?.state == .IN_PROGRESS,
              let sessionId = editSession.activeSessionId,
              !isCommandSending(),
              partState(for: partId) == .AWAITING_CHOICE
        else {
            return
        }

        state.command = .sending(.choice)
        commandTask = Task { @MainActor [weak self] in
            await self?.runChoosePart(
                partId: partId,
                optionCode: optionCode,
                subjectCodes: subjectCodes,
                sessionId: sessionId
            )
        }
    }

    func retryGeneration(partId: PartID) {
        guard !disposed,
              !isEditingBlocked(),
              state.attempt?.state == .IN_PROGRESS,
              let sessionId = editSession.activeSessionId,
              !isCommandSending()
        else {
            return
        }

        state.command = .sending(.retry)
        commandTask = Task { @MainActor [weak self] in
            await self?.runRetryGeneration(partId: partId, sessionId: sessionId)
        }
    }

    func refresh() {
        guard !disposed else { return }
        if state.attempt == nil {
            state.load = .loading
            loadTask?.cancel()
            loadTask = Task { @MainActor [weak self] in
                await self?.bootstrap()
            }
        } else {
            loadTask?.cancel()
            loadTask = Task { @MainActor [weak self] in
                await self?.reloadStateFromServer(reconcileLocalDrafts: false)
            }
        }
    }

    func reconcile() {
        guard !disposed else { return }
        switch state.command {
        case .unconfirmed(.finishPart):
            let targetPartId = partCommands.unconfirmedFinishPartId ?? state.position?.partId
            commandTask = Task { @MainActor [weak self] in
                await self?.runReconcileFinishPart(partId: targetPartId)
            }
        case .unconfirmed(.submit):
            commandTask = Task { @MainActor [weak self] in
                await self?.runReconcileSubmit()
            }
        default:
            refresh()
        }
    }

    func setForeground(_ foreground: Bool) {
        guard !disposed else { return }
        if isForeground == foreground {
            return
        }
        isForeground = foreground

        if !foreground {
            needsForegroundHeartbeat = true
            onlineTask?.cancel()
            onlineTask = nil
            heartbeatLoop.stop()
            partPolling.stop()
            stopCountdownTicker()
            if !isEditingBlocked(),
               state.attempt?.state == .IN_PROGRESS,
               connectivity.isOnline,
               editSession.activeSessionId != nil,
               saveQueue.hasDirtyChanges || positionTracker.isDirty
            {
                foregroundTask?.cancel()
                foregroundTask = Task { @MainActor [weak self] in
                    await self?.saveQueue.flushNow()
                }
            }
            return
        }

        foregroundTask?.cancel()
        foregroundTask = Task { @MainActor [weak self] in
            await self?.handleReturnToForeground()
        }
    }

    func reportSessionRevoked() {
        guard !disposed else { return }
        applyEviction(markSaveConflict: saveQueue.hasDirtyChanges)
    }

    func dispose() {
        guard !disposed else { return }
        disposed = true

        if let token = connectivityToken {
            connectivity.removeObserver(token)
            connectivityToken = nil
        }

        loadTask?.cancel()
        loadTask = nil
        commandTask?.cancel()
        commandTask = nil
        countdownTask?.cancel()
        countdownTask = nil
        deadlineReloadTask?.cancel()
        deadlineReloadTask = nil
        foregroundTask?.cancel()
        foregroundTask = nil
        onlineTask?.cancel()
        onlineTask = nil

        heartbeatLoop.dispose()
        partPolling.dispose()
        saveQueue.dispose()
        positionTracker.dispose()
        editSession.dispose()

        for continuation in eventContinuations.values {
            continuation.finish()
        }
        eventContinuations.removeAll()
    }

    func waitForIdleForTesting() async {
        await loadTask?.value
        await editSession.waitForPendingTakeover()
        await commandTask?.value
        await foregroundTask?.value
        await onlineTask?.value
        await saveQueue.waitForCurrentFlush()
        await saveQueue.waitForPendingDraftWrites()
        await positionTracker.waitForPendingPersist()
    }

    private func bootstrap() async {
        state.load = .loading
        do {
            async let snapshotTask = api.getState(attemptId: attemptId)
            async let localPosTask = draftStore.readPosition(attemptId: attemptId)

            let snapshot = try await snapshotTask
            let localPos = await localPosTask
            guard !disposed, !Task.isCancelled else { return }

            let knownItemIds = Self.extractLeafItemIds(from: snapshot.response)
            let diskDrafts = await draftStore.loadDrafts(attemptId: attemptId, knownItemIds: knownItemIds)
            guard !disposed, !Task.isCancelled else { return }

            serverClock.reanchor(serverNow: snapshot.response.serverNow, at: clock.now)
            state.attempt = snapshot.response
            state.load = .loaded(value: (), stale: false)

            saveQueue.reconcile(snapshot: snapshot, diskDrafts: diskDrafts)

            let serverPos = PartSelection.parseServerPosition(from: snapshot.response)
            let initialPos = PartSelection.initial(
                attempt: snapshot.response,
                serverPosition: serverPos,
                localPosition: localPos
            )
            positionTracker.setInitial(initialPos)
            state.position = initialPos

            if snapshot.response.state == .IN_PROGRESS {
                state.closed = nil
                explicitClosedReason = nil
                recomputeSaveStatus()
                refreshCountdownAndPolling()
                editSession.startClaim()
            } else {
                await applyNonInProgressState(snapshot.response)
            }
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return }
            let failure = apiError.toLoadFailure()
            state.load = .failed(kind: failure.kind, canRetry: failure.canRetry, retryAfter: failure.retryAfter)
        } catch {
            guard !disposed, !Task.isCancelled else { return }
            state.load = .failed(kind: .offline, canRetry: true, retryAfter: nil)
        }
    }

    @discardableResult
    private func reloadStateFromServer(reconcileLocalDrafts: Bool) async -> Components.Schemas.AttemptStateResponse? {
        do {
            let snapshot = try await api.getState(attemptId: attemptId)
            guard !disposed, !Task.isCancelled else { return nil }

            let diskDrafts: [ItemID: ItemDraftRecord]
            if reconcileLocalDrafts {
                let ids = Self.extractLeafItemIds(from: snapshot.response)
                diskDrafts = await draftStore.loadDrafts(attemptId: attemptId, knownItemIds: ids)
            } else {
                diskDrafts = [:]
            }
            guard !disposed, !Task.isCancelled else { return nil }

            let previousPosition = state.position
            serverClock.reanchor(serverNow: snapshot.response.serverNow, at: clock.now)
            state.attempt = snapshot.response
            state.load = .loaded(value: (), stale: false)
            saveQueue.reconcile(snapshot: snapshot, diskDrafts: diskDrafts)

            if snapshot.response.state == .IN_PROGRESS {
                updatePositionAfterReload(snapshot.response, previous: previousPosition)
                refreshCountdownAndPolling()
                recomputeSaveStatus()
            } else {
                await applyNonInProgressState(snapshot.response)
            }
            return snapshot.response
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return nil }
            if apiError.isSessionConflict {
                applyEviction(markSaveConflict: true)
            }
            return nil
        } catch {
            return nil
        }
    }

    private func applyNonInProgressState(_ attempt: Components.Schemas.AttemptStateResponse) async {
        heartbeatLoop.stop()
        partPolling.stop()
        stopCountdownTicker()
        saveQueue.cancelInFlightFlush()
        saveQueue.cancelAllPendingTimers()
        saveQueue.cancelRetryTimer()

        switch attempt.state {
        case .STARTING:
            editSession.markReadOnly(.attemptNotInProgress)
            state.closed = nil
            state.clock = .none

        case .ABORTED:
            isClosing = true
            defer { isClosing = false }
            let reason = ClosedReason.aborted(reason: attempt.abortReason?.rawValue)
            state.clock = .none
            editSession.markReadOnly(.closed(reason))
            saveQueue.lockItems(partId: nil)
            saveQueue.clearTransientError()
            await saveQueue.waitForPendingDraftWrites()
            await positionTracker.waitForPendingPersist()
            let knownIds = saveQueue.allItemIds
            try? await draftStore.clearAttempt(
                attemptId: attemptId,
                keepStartContext: true,
                knownItemIds: knownIds
            )
            state.closed = reason
            emitAttemptClosedOnce(reason)

        default:
            isClosing = true
            defer { isClosing = false }
            let inferred = PartSelection.inferClosedReason(from: attempt) ?? .submitted
            let resolved = explicitClosedReason ?? inferred
            state.clock = .none
            editSession.markReadOnly(.closed(resolved))
            saveQueue.lockItems(partId: nil)
            saveQueue.clearTransientError()
            await saveQueue.waitForPendingDraftWrites()
            await positionTracker.waitForPendingPersist()
            let knownIds = saveQueue.allItemIds
            try? await draftStore.clearAttempt(
                attemptId: attemptId,
                keepStartContext: false,
                knownItemIds: knownIds
            )
            state.closed = resolved
            emitAttemptClosedOnce(resolved)
        }

        recomputeSaveStatus()
    }

    private func updatePositionAfterReload(
        _ attempt: Components.Schemas.AttemptStateResponse,
        previous: Position?
    ) {
        let parts = PartSelection.sortedParts(from: attempt)
        if let previous,
           let matchingPart = parts.first(where: { $0.partId == previous.partId }),
           PartSelection.isSelectable(matchingPart.state)
        {
            state.position = previous
            positionTracker.setInitial(previous)
            return
        }

        let next = PartSelection.afterClose(attempt)
        state.position = next
        positionTracker.setInitial(next)
    }

    private func handleEditSessionEvent(_ event: EditSessionCallbackEvent) {
        guard !disposed else { return }
        switch event {
        case let .stateChanged(nextSession):
            state.session = nextSession
            if case .openElsewhere = nextSession {
                heartbeatLoop.stop()
                partPolling.stop()
                saveQueue.cancelAllPendingTimers()
                saveQueue.cancelRetryTimer()
            } else if case .evicted = nextSession {
                heartbeatLoop.stop()
                partPolling.stop()
                saveQueue.cancelAllPendingTimers()
                saveQueue.cancelRetryTimer()
            }
            recomputeSaveStatus()

        case let .serverNow(serverNow):
            serverClock.reanchor(serverNow: serverNow, at: clock.now)
            refreshCountdownAndPolling()

        case let .claimError(reason, offline):
            claimErrorReason = reason
            claimOffline = offline
            recomputeSaveStatus()

        case let .claimSucceeded(_, fromTakeover):
            claimErrorReason = nil
            claimOffline = false
            saveQueue.clearTransientError()
            if state.command == .sending(.takeover) {
                state.command = .idle
            }
            recomputeSaveStatus()
            refreshCountdownAndPolling()

            if isForeground && connectivity.isOnline && state.closed == nil {
                heartbeatLoop.start()
            }
            if fromTakeover {
                if (saveQueue.hasDirtyChanges || positionTracker.isDirty) && connectivity.isOnline && isForeground {
                    saveQueue.scheduleFlush()
                }
            } else if (saveQueue.hasDirtyChanges || positionTracker.isDirty) && connectivity.isOnline && isForeground {
                Task { @MainActor [weak self] in
                    await self?.saveQueue.flushNow()
                }
            }

        case let .takeoverFailed(error):
            if let code = error?.apiCode {
                state.command = .refused(code: code)
            } else {
                state.command = .idle
            }

        case let .attemptClosed(reason):
            explicitClosedReason = explicitClosedReason ?? reason
            if state.command == .sending(.takeover) {
                state.command = .idle
            }
            loadTask?.cancel()
            loadTask = Task { @MainActor [weak self] in
                guard let self else { return }
                let fresh = await self.reloadStateFromServer(reconcileLocalDrafts: false)
                if fresh == nil && !self.disposed {
                    self.state.closed = self.explicitClosedReason ?? reason
                    self.emitAttemptClosedOnce(self.explicitClosedReason ?? reason)
                }
            }

        case .evicted:
            state.command = .idle
            heartbeatLoop.stop()
            partPolling.stop()
            saveQueue.cancelAllPendingTimers()
            saveQueue.cancelRetryTimer()
            emit(.sessionLost)
            recomputeSaveStatus()
        }
    }

    private func performTakeoverReload(sessionId _: String) async -> TakeoverReloadOutcome {
        do {
            let snapshot = try await api.getState(attemptId: attemptId)
            guard !disposed, !Task.isCancelled else { return .closed }

            let ids = Self.extractLeafItemIds(from: snapshot.response)
            let diskDrafts = await draftStore.loadDrafts(attemptId: attemptId, knownItemIds: ids)
            let localPos = await draftStore.readPosition(attemptId: attemptId)
            guard !disposed, !Task.isCancelled else { return .closed }

            serverClock.reanchor(serverNow: snapshot.response.serverNow, at: clock.now)
            state.attempt = snapshot.response
            state.load = .loaded(value: (), stale: false)
            saveQueue.reconcile(snapshot: snapshot, diskDrafts: diskDrafts)

            let serverPos = PartSelection.parseServerPosition(from: snapshot.response)
            let resolvedPos = PartSelection.initial(
                attempt: snapshot.response,
                serverPosition: serverPos,
                localPosition: localPos
            )
            state.position = resolvedPos
            positionTracker.setInitial(resolvedPos)

            if snapshot.response.state != .IN_PROGRESS {
                await applyNonInProgressState(snapshot.response)
                if state.command == .sending(.takeover) {
                    state.command = .idle
                }
                return .closed
            }

            saveQueue.clearTransientError()
            return .readyToEdit
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return .closed }
            if apiError.isSessionConflict {
                applyEviction(markSaveConflict: saveQueue.hasDirtyChanges)
                return .closed
            }
            return .failed(apiError)
        } catch {
            guard !disposed, !Task.isCancelled else { return .closed }
            return .failed(nil)
        }
    }

    private func handleHeartbeatOutcome(_ outcome: HeartbeatOutcome) {
        guard !disposed else { return }
        switch outcome {
        case let .active(serverNow):
            serverClock.reanchor(serverNow: serverNow, at: clock.now)
            refreshCountdownAndPolling()

        case let .attemptClosed(serverNow, _):
            if let serverNow {
                serverClock.reanchor(serverNow: serverNow, at: clock.now)
            }
            loadTask?.cancel()
            loadTask = Task { @MainActor [weak self] in
                guard let self else { return }
                let fresh = await self.reloadStateFromServer(reconcileLocalDrafts: false)
                if fresh == nil && !self.disposed {
                    let fallback = self.explicitClosedReason ?? .notOpen
                    self.state.closed = fallback
                    self.emitAttemptClosedOnce(fallback)
                }
            }

        case .evicted:
            applyEviction(markSaveConflict: false)

        case .transientFailure, .skipped:
            break
        }
    }

    private func handleSaveQueueEvent(_ event: SaveQueueEvent) {
        guard !disposed else { return }
        switch event {
        case let .serverNow(serverNow):
            serverClock.reanchor(serverNow: serverNow, at: clock.now)
            refreshCountdownAndPolling()

        case let .answersChanged(answers):
            state.answers = answers

        case .statusChanged:
            recomputeSaveStatus()

        case .staleDetected:
            loadTask?.cancel()
            loadTask = Task { @MainActor [weak self] in
                guard let self else { return }
                await self.reloadStateFromServer(reconcileLocalDrafts: false)
                if !self.disposed && self.saveQueue.hasDirtyChanges && self.connectivity.isOnline {
                    self.saveQueue.scheduleFlush()
                }
            }

        case let .partClosed(partId):
            if !partId.isEmpty {
                saveQueue.lockItems(partId: partId)
                emit(.partClosed(partId))
            }
            loadTask?.cancel()
            loadTask = Task { @MainActor [weak self] in
                await self?.reloadStateFromServer(reconcileLocalDrafts: false)
            }

        case let .attemptClosed(reason):
            explicitClosedReason = explicitClosedReason ?? reason
            saveQueue.lockItems(partId: nil)
            loadTask?.cancel()
            loadTask = Task { @MainActor [weak self] in
                guard let self else { return }
                let fresh = await self.reloadStateFromServer(reconcileLocalDrafts: false)
                if fresh == nil && !self.disposed {
                    self.state.closed = self.explicitClosedReason ?? reason
                    self.emitAttemptClosedOnce(self.explicitClosedReason ?? reason)
                }
            }

        case .sessionRevoked:
            applyEviction(markSaveConflict: true)

        case .unauthorized:
            heartbeatLoop.stop()
            partPolling.stop()
            saveQueue.cancelAllPendingTimers()
            saveQueue.cancelRetryTimer()
        }
    }

    private func handleConnectivityChanged(_ isOnline: Bool) {
        guard !disposed else { return }
        state.network = isOnline ? .online : .offline

        if !isOnline {
            saveQueue.handleOffline()
            editSession.handleOffline()
            recomputeSaveStatus()
            return
        }

        saveQueue.handleOnlineReset()

        guard isForeground else {
            editSession.resetBackoff()
            recomputeSaveStatus()
            return
        }

        editSession.handleOnline()

        switch state.command {
        case .unconfirmed(.finishPart), .unconfirmed(.submit):
            reconcile()
            return
        default:
            break
        }

        if state.attempt == nil {
            refresh()
            return
        }

        guard state.attempt?.state == .IN_PROGRESS, state.closed == nil else {
            return
        }

        onlineTask?.cancel()
        onlineTask = Task { @MainActor [weak self] in
            guard let self else { return }
            await self.saveQueue.waitForCurrentFlush()
            guard !self.disposed, !Task.isCancelled, self.connectivity.isOnline, self.isForeground else { return }

            if self.needsForegroundHeartbeat && self.editSession.activeSessionId != nil {
                let outcome = await self.heartbeatLoop.sendNow()
                guard !self.disposed, !Task.isCancelled, self.connectivity.isOnline, self.isForeground else { return }
                if outcome == .evicted || self.isClosedOutcome(outcome) {
                    self.needsForegroundHeartbeat = false
                    return
                }
                if case .active = outcome {
                    self.needsForegroundHeartbeat = false
                }
            }

            let fresh = await self.reloadStateFromServer(reconcileLocalDrafts: true)
            guard !self.disposed, !Task.isCancelled, self.connectivity.isOnline, self.isForeground else { return }
            if fresh?.state == .IN_PROGRESS,
               !self.isEditingBlocked(),
               self.editSession.activeSessionId != nil,
               self.saveQueue.hasDirtyChanges || self.positionTracker.isDirty
            {
                await self.saveQueue.flushNow()
            }
            guard !self.disposed, !Task.isCancelled else { return }
            if self.isForeground
                && self.connectivity.isOnline
                && self.state.closed == nil
                && self.editSession.activeSessionId != nil
            {
                self.heartbeatLoop.start()
                self.refreshCountdownAndPolling()
            }
        }
    }

    private func handleReturnToForeground() async {
        guard !disposed, !Task.isCancelled else { return }
        guard state.closed == nil, state.attempt?.state == .IN_PROGRESS else {
            needsForegroundHeartbeat = false
            return
        }

        if case .editing = editSession.state {
            if connectivity.isOnline {
                let outcome = await heartbeatLoop.sendNow()
                guard !disposed, !Task.isCancelled else { return }
                if outcome == .evicted || isClosedOutcome(outcome) {
                    needsForegroundHeartbeat = false
                    return
                }
                if case .active = outcome {
                    needsForegroundHeartbeat = false
                }
                let fresh = await reloadStateFromServer(reconcileLocalDrafts: true)
                guard !disposed, !Task.isCancelled else { return }
                if fresh?.state == .IN_PROGRESS,
                   !isEditingBlocked(),
                   editSession.activeSessionId != nil,
                   saveQueue.hasDirtyChanges || positionTracker.isDirty
                {
                    await saveQueue.flushNow()
                }
                guard !disposed, !Task.isCancelled else { return }
                if isForeground && connectivity.isOnline && state.closed == nil && editSession.activeSessionId != nil {
                    heartbeatLoop.start()
                    refreshCountdownAndPolling()
                }
            } else {
                refreshCountdownAndPolling()
            }
        } else if case .acquiring = editSession.state {
            needsForegroundHeartbeat = false
            if connectivity.isOnline {
                editSession.handleOnline()
            }
            refreshCountdownAndPolling()
        } else {
            needsForegroundHeartbeat = false
        }
    }

    private func isClosedOutcome(_ outcome: HeartbeatOutcome) -> Bool {
        if case .attemptClosed = outcome {
            return true
        }
        return false
    }

    private func runFinishPart(
        partId: PartID,
        sessionId: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async {
        defer { commandTask = nil }
        await Task.yield()
        guard !disposed, !Task.isCancelled else { return }
        do {
            let response = try await partCommands.finishPart(
                partId: partId,
                sessionId: sessionId,
                finalAnswers: finalAnswers
            )
            guard !disposed, !Task.isCancelled else { return }

            serverClock.reanchor(serverNow: response.serverNow, at: clock.now)
            saveQueue.applyFinalAnswerResults(response.results)
            if let endReason = response.endReason {
                explicitClosedReason = Self.mapEndReason(endReason.rawValue)
            }
            saveQueue.lockItems(partId: partId)
            state.command = .idle
            await reloadStateFromServer(reconcileLocalDrafts: false)
            guard !disposed, !Task.isCancelled else { return }
            emit(.partClosed(partId))
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return }
            if apiError.isSessionConflict {
                applyEviction(markSaveConflict: true)
            } else if case .unauthorized = apiError {
                state.command = .idle
            } else {
                switch apiError {
                case .network, .serverError:
                    state.command = .unconfirmed(.finishPart)
                default:
                    let code = apiError.apiCode ?? "FINISH_PART_REFUSED"
                    state.command = .refused(code: code)
                    await reloadStateFromServer(reconcileLocalDrafts: false)
                }
            }
        } catch {
            guard !disposed, !Task.isCancelled else { return }
            state.command = .unconfirmed(.finishPart)
        }
    }

    private func runSubmit(
        sessionId: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async {
        defer { commandTask = nil }
        await Task.yield()
        guard !disposed, !Task.isCancelled else { return }
        do {
            let response = try await partCommands.submit(
                sessionId: sessionId,
                finalAnswers: finalAnswers
            )
            guard !disposed, !Task.isCancelled else { return }

            isClosing = true
            defer { isClosing = false }
            saveQueue.cancelInFlightFlush()
            saveQueue.cancelAllPendingTimers()
            saveQueue.cancelRetryTimer()
            heartbeatLoop.stop()
            partPolling.stop()
            stopCountdownTicker()

            serverClock.reanchor(serverNow: response.serverNow, at: clock.now)
            saveQueue.applyFinalAnswerResults(response.results)

            let resolvedReason = Self.mapEndReason(response.endReason?.rawValue) ?? .submitted
            explicitClosedReason = resolvedReason
            editSession.markReadOnly(.closed(resolvedReason))
            saveQueue.lockItems(partId: nil)
            saveQueue.clearTransientError()

            await saveQueue.waitForPendingDraftWrites()
            await positionTracker.waitForPendingPersist()
            let knownIds = saveQueue.allItemIds
            try? await draftStore.clearAttempt(
                attemptId: attemptId,
                keepStartContext: false,
                knownItemIds: knownIds
            )

            state.closed = resolvedReason
            state.clock = .none
            state.command = .idle
            recomputeSaveStatus()

            _ = await reloadStateFromServer(reconcileLocalDrafts: false)
            emitAttemptClosedOnce(resolvedReason)
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return }
            if apiError.isSessionConflict {
                applyEviction(markSaveConflict: true)
            } else if case let .conflict(code, _, _) = apiError {
                state.command = .refused(code: code.isEmpty ? "CONFLICT" : code)
                await reloadStateFromServer(reconcileLocalDrafts: false)
            } else if case .unauthorized = apiError {
                state.command = .idle
            } else {
                state.command = .unconfirmed(.submit)
            }
        } catch {
            guard !disposed, !Task.isCancelled else { return }
            state.command = .unconfirmed(.submit)
        }
    }

    private func runChoosePart(
        partId: PartID,
        optionCode: String,
        subjectCodes: [String]?,
        sessionId: String
    ) async {
        defer { commandTask = nil }
        do {
            let response = try await partCommands.choosePart(
                partId: partId,
                optionCode: optionCode,
                subjectCodes: subjectCodes,
                sessionId: sessionId
            )
            guard !disposed, !Task.isCancelled else { return }
            serverClock.reanchor(serverNow: response.serverNow, at: clock.now)
            state.command = .idle
            await reloadStateFromServer(reconcileLocalDrafts: false)
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return }
            await handlePartChoiceError(apiError)
        } catch {
            guard !disposed, !Task.isCancelled else { return }
            state.command = .refused(code: "NETWORK_ERROR")
        }
    }

    private func runRetryGeneration(partId: PartID, sessionId: String) async {
        defer { commandTask = nil }
        do {
            let response = try await partCommands.retryGeneration(partId: partId, sessionId: sessionId)
            guard !disposed, !Task.isCancelled else { return }
            serverClock.reanchor(serverNow: response.serverNow, at: clock.now)
            state.command = .idle
            await reloadStateFromServer(reconcileLocalDrafts: false)
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return }
            await handlePartChoiceError(apiError)
        } catch {
            guard !disposed, !Task.isCancelled else { return }
            state.command = .refused(code: "NETWORK_ERROR")
        }
    }

    private func handlePartChoiceError(_ apiError: AttemptAPIError) async {
        if apiError.isSessionConflict {
            applyEviction(markSaveConflict: false)
            return
        }
        if case .unauthorized = apiError {
            state.command = .idle
            return
        }
        let code: String
        switch apiError {
        case let .conflict(rawCode, _, _),
             let .clientError(_, rawCode, _),
             let .serverError(_, rawCode):
            code = rawCode.isEmpty ? "GENERIC" : rawCode
        case .rateLimited:
            code = "RATE_LIMITED"
        case .network:
            code = "NETWORK_ERROR"
        case .unauthorized:
            code = "UNAUTHENTICATED"
        }

        if code == "CHOICE_WINDOW_CLOSED" {
            explicitClosedReason = .choiceTimeout
        }
        state.command = .refused(code: code)

        if code == "CHOICE_WINDOW_CLOSED"
            || code == "CHOICE_LOCKED"
            || code == "PART_NOT_AWAITING"
            || code == "PART_GENERATING"
        {
            await reloadStateFromServer(reconcileLocalDrafts: false)
        }
    }

    private func runReconcileFinishPart(partId: PartID?) async {
        defer { commandTask = nil }
        guard let fresh = await reloadStateFromServer(reconcileLocalDrafts: false), !disposed else {
            return
        }
        let targetPart = fresh.parts?.first { $0.partId == partId }
        if fresh.state != .IN_PROGRESS || targetPart == nil || targetPart?.state != .OPEN {
            partCommands.clearUnconfirmedFinishPart()
            state.command = .idle
            if let partId {
                emit(.partClosed(partId))
            }
            if fresh.state == .IN_PROGRESS && isForeground && connectivity.isOnline && state.closed == nil && editSession.activeSessionId != nil {
                heartbeatLoop.start()
                refreshCountdownAndPolling()
            }
        } else {
            partCommands.clearUnconfirmedFinishPart()
            state.command = .idle
            if isForeground && connectivity.isOnline && state.closed == nil && editSession.activeSessionId != nil {
                heartbeatLoop.start()
                refreshCountdownAndPolling()
            }
            if saveQueue.hasDirtyChanges && connectivity.isOnline && editSession.activeSessionId != nil {
                saveQueue.scheduleFlush()
            }
        }
    }

    private func runReconcileSubmit() async {
        defer { commandTask = nil }
        guard let fresh = await reloadStateFromServer(reconcileLocalDrafts: false), !disposed else {
            return
        }
        if fresh.state != .IN_PROGRESS {
            await partCommands.clearSubmitIdempotencyKey()
            state.command = .idle
        } else {
            state.command = .idle
            if isForeground && connectivity.isOnline && state.closed == nil && editSession.activeSessionId != nil {
                heartbeatLoop.start()
                refreshCountdownAndPolling()
            }
            if saveQueue.hasDirtyChanges && connectivity.isOnline && editSession.activeSessionId != nil {
                saveQueue.scheduleFlush()
            }
        }
    }

    private func applyEviction(markSaveConflict: Bool) {
        guard state.closed == nil, !isClosing else { return }
        state.command = .idle
        heartbeatLoop.stop()
        partPolling.stop()
        if markSaveConflict {
            saveQueue.markSessionConflictError()
        } else {
            saveQueue.cancelAllPendingTimers()
            saveQueue.cancelRetryTimer()
        }
        editSession.markEvicted()
        recomputeSaveStatus()
    }

    private func refreshCountdownAndPolling() {
        guard !disposed else { return }
        guard let attempt = state.attempt, attempt.state == .IN_PROGRESS, state.closed == nil else {
            state.clock = .none
            stopCountdownTicker()
            partPolling.stop()
            return
        }

        let activePart = resolveActivePart(in: attempt)
        let countdown = computeCountdown(attempt: attempt, activePart: activePart)
        state.clock = countdown

        if isForeground && !isSessionTerminalForPolling() {
            if let activePart, let partId = activePart.partId {
                switch activePart.state {
                case .GENERATING:
                    partPolling.configure(.generating(partId: partId))
                case .AWAITING_CHOICE:
                    if countdown.target == .choice(partId), countdown.isExpired {
                        let deadlineKey = "choice:\(partId):\(activePart.choiceDeadlineAt?.timeIntervalSince1970 ?? 0)"
                        if !handledExpiredChoiceDeadlines.contains(deadlineKey) {
                            handledExpiredChoiceDeadlines.insert(deadlineKey)
                            explicitClosedReason = explicitClosedReason ?? .choiceTimeout
                        }
                        partPolling.configure(.choiceExpired(partId: partId))
                    } else {
                        partPolling.stop()
                    }
                case .GENERATION_FAILED:
                    partPolling.stop()
                default:
                    partPolling.stop()
                }
            } else {
                partPolling.stop()
            }
        } else {
            partPolling.stop()
        }

        if countdown.target != .none {
            checkAndHandleDeadlineExpiry(attempt: attempt, activePart: activePart, countdown: countdown)
            if isForeground && state.closed == nil {
                startCountdownTickerIfNeeded()
            }
        } else {
            stopCountdownTicker()
        }
    }

    private func computeCountdown(
        attempt: Components.Schemas.AttemptStateResponse,
        activePart: Components.Schemas.AttemptPartStateView?
    ) -> Countdown {
        if let activePart,
           activePart.state == .AWAITING_CHOICE,
           let partId = activePart.partId,
           let choiceDeadlineAt = activePart.choiceDeadlineAt
        {
            return Countdown(
                target: .choice(partId),
                remaining: serverClock.remaining(until: choiceDeadlineAt, at: clock.now)
            )
        }

        if attempt.timing == .SHARED, let deadlineAt = attempt.deadlineAt {
            return Countdown(
                target: .attempt,
                remaining: serverClock.remaining(until: deadlineAt, at: clock.now)
            )
        }

        if attempt.timing == .PER_PART,
           let activePart,
           activePart.state == .OPEN,
           let partId = activePart.partId,
           let deadlineAt = activePart.deadlineAt
        {
            return Countdown(
                target: .part(partId),
                remaining: serverClock.remaining(until: deadlineAt, at: clock.now)
            )
        }

        return .none
    }

    private func checkAndHandleDeadlineExpiry(
        attempt: Components.Schemas.AttemptStateResponse,
        activePart: Components.Schemas.AttemptPartStateView?,
        countdown: Countdown
    ) {
        guard countdown.isExpired else { return }

        switch countdown.target {
        case .attempt:
            let key = "attempt:\(attempt.deadlineAt?.timeIntervalSince1970 ?? 0)"
            guard !handledExpiredDeadlines.contains(key) else { return }
            handledExpiredDeadlines.insert(key)

            explicitClosedReason = explicitClosedReason ?? .timeUp
            saveQueue.cancelAllPendingTimers()
            saveQueue.cancelRetryTimer()
            saveQueue.lockItems(partId: nil)
            emitAttemptClosedOnce(.timeUp)
            scheduleDeadlineReload()

        case let .part(partId):
            let key = "part:\(partId):\(activePart?.deadlineAt?.timeIntervalSince1970 ?? 0)"
            guard !handledExpiredDeadlines.contains(key) else { return }
            handledExpiredDeadlines.insert(key)

            saveQueue.cancelAllPendingTimers()
            saveQueue.cancelRetryTimer()

            let hasNextPart = hasFollowingIncompletePart(in: attempt, after: activePart)
            if hasNextPart {
                saveQueue.lockItems(partId: partId)
                emit(.partClosed(partId))
            } else {
                explicitClosedReason = explicitClosedReason ?? .timeUp
                saveQueue.lockItems(partId: nil)
                emitAttemptClosedOnce(.timeUp)
            }
            scheduleDeadlineReload()

        case let .choice(partId):
            let key = "choice:\(partId):\(activePart?.choiceDeadlineAt?.timeIntervalSince1970 ?? 0)"
            if !handledExpiredChoiceDeadlines.contains(key) {
                handledExpiredChoiceDeadlines.insert(key)
                explicitClosedReason = explicitClosedReason ?? .choiceTimeout
            }
            if isForeground && !isSessionTerminalForPolling() {
                partPolling.configure(.choiceExpired(partId: partId))
            }

        case .none:
            break
        }
    }

    private func scheduleDeadlineReload() {
        deadlineReloadTask?.cancel()
        deadlineReloadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await self.clock.sleep(for: .milliseconds(500))
            } catch {
                return
            }
            guard !self.disposed, !Task.isCancelled else { return }
            self.deadlineReloadTask = nil
            await self.reloadStateFromServer(reconcileLocalDrafts: false)
        }
    }

    private func startCountdownTickerIfNeeded() {
        guard countdownTask == nil, !disposed else { return }
        countdownTask = Task { @MainActor [weak self] in
            guard let self else { return }
            while !self.disposed && !Task.isCancelled && self.isForeground && self.state.closed == nil {
                do {
                    try await self.clock.sleep(for: .seconds(1))
                } catch {
                    break
                }
                guard !self.disposed, !Task.isCancelled else { break }
                self.refreshCountdownAndPolling()
                if self.state.clock.target == .none {
                    break
                }
            }
            self.countdownTask = nil
        }
    }

    private func stopCountdownTicker() {
        countdownTask?.cancel()
        countdownTask = nil
    }

    private func recomputeSaveStatus() {
        let hasDirty = saveQueue.hasDirtyChanges
        let inFlight = saveQueue.isFlushInFlight
        let online = connectivity.isOnline

        if case .sessionConflict = saveQueue.lastErrorReason {
            state.save = .unsaved(.sessionConflict)
            return
        }
        if !online && (hasDirty || inFlight || claimOffline) {
            state.save = .offline
            return
        }
        if let queueReason = saveQueue.lastErrorReason {
            state.save = .unsaved(queueReason)
            return
        }
        if let claimReason = claimErrorReason {
            state.save = .unsaved(claimReason)
            return
        }
        if hasDirty || inFlight {
            state.save = .saving
            return
        }
        state.save = .saved
    }

    private func isEditingBlocked() -> Bool {
        if disposed || isClosing || state.closed != nil {
            return true
        }
        switch editSession.state {
        case .openElsewhere, .evicted, .readOnly:
            return true
        case .acquiring, .editing:
            return false
        }
    }

    private func isSessionTerminalForPolling() -> Bool {
        switch editSession.state {
        case .openElsewhere, .evicted, .readOnly:
            return true
        case .acquiring, .editing:
            return false
        }
    }

    private func isCommandSending() -> Bool {
        if case .sending = state.command {
            return true
        }
        return partCommands.isSending
    }

    private func resolveActivePart(
        in attempt: Components.Schemas.AttemptStateResponse
    ) -> Components.Schemas.AttemptPartStateView? {
        let parts = PartSelection.sortedParts(from: attempt)
        if let currentPartId = state.position?.partId,
           let matched = parts.first(where: { $0.partId == currentPartId })
        {
            return matched
        }
        return parts.first
    }

    private func partState(for partId: PartID) -> Components.Schemas.AttemptPartStateView.statePayload? {
        state.attempt?.parts?.first { $0.partId == partId }?.state
    }

    private func hasFollowingIncompletePart(
        in attempt: Components.Schemas.AttemptStateResponse,
        after current: Components.Schemas.AttemptPartStateView?
    ) -> Bool {
        guard let currentOrd = current?.ord else { return false }
        return PartSelection.sortedParts(from: attempt).contains { part in
            (part.ord ?? 0) > currentOrd && part.state != .CLOSED && part.state != .SKIPPED
        }
    }

    private func emit(_ event: EngineEvent) {
        guard !disposed else { return }
        for continuation in eventContinuations.values {
            continuation.yield(event)
        }
    }

    private func emitAttemptClosedOnce(_ reason: ClosedReason) {
        guard !emittedAttemptClosed else { return }
        emittedAttemptClosed = true
        emit(.attemptClosed(reason))
    }

    private static func extractLeafItemIds(from attempt: Components.Schemas.AttemptStateResponse) -> [ItemID] {
        var ids: [ItemID] = []
        for part in PartSelection.sortedParts(from: attempt) {
            for item in part.items ?? [] {
                if item.questionType != .QUESTION_SET, let id = item.itemId, !id.isEmpty {
                    ids.append(id)
                }
            }
        }
        return ids
    }

    private static func mapEndReason(_ raw: String?) -> ClosedReason? {
        switch raw {
        case "TIME_UP": .timeUp
        case "SUBMITTED": .submitted
        case "CHOICE_TIMEOUT": .choiceTimeout
        default: nil
        }
    }
}
