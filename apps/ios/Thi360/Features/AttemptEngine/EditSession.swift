import Foundation

enum TakeoverReloadOutcome: Sendable {
    case readyToEdit
    case closed
    case failed(AttemptAPIError?)
}

enum EditSessionCallbackEvent: Sendable {
    case stateChanged(EditSessionState)
    case serverNow(Date)
    case claimError(SaveFailureReason?, offline: Bool)
    case claimSucceeded(sessionId: String, fromTakeover: Bool)
    case takeoverFailed(AttemptAPIError?)
    case attemptClosed(ClosedReason)
    case evicted
}

@MainActor
final class EditSession {
    private let attemptId: String
    private let api: any AttemptAPIClient
    private let draftStore: DraftStore
    private let clock: any EngineClock
    private let sessionIdGenerator: @Sendable () -> String
    private let isOnline: @MainActor () -> Bool
    private let onTakeoverReload: @MainActor (String) async -> TakeoverReloadOutcome
    private let onEvent: @MainActor (EditSessionCallbackEvent) -> Void

    private(set) var state: EditSessionState = .acquiring
    private(set) var isTakeoverInFlight: Bool = false

    private var persistedSessionId: String?
    private var backoff = Backoff()
    private var claimTask: Task<Void, Never>?
    private var retryTask: Task<Void, Never>?
    private var takeoverTask: Task<Void, Never>?
    private var disposed = false

    init(
        attemptId: String,
        api: any AttemptAPIClient,
        draftStore: DraftStore,
        clock: any EngineClock,
        sessionIdGenerator: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() },
        isOnline: @escaping @MainActor () -> Bool,
        onTakeoverReload: @escaping @MainActor (String) async -> TakeoverReloadOutcome,
        onEvent: @escaping @MainActor (EditSessionCallbackEvent) -> Void
    ) {
        self.attemptId = attemptId
        self.api = api
        self.draftStore = draftStore
        self.clock = clock
        self.sessionIdGenerator = sessionIdGenerator
        self.isOnline = isOnline
        self.onTakeoverReload = onTakeoverReload
        self.onEvent = onEvent
    }

    var activeSessionId: String? {
        state.activeSessionId
    }

    func startClaim() {
        guard !disposed else { return }
        if case .editing = state { return }
        if claimTask != nil { return }

        retryTask?.cancel()
        retryTask = nil
        transition(to: .acquiring)
        claimTask = Task { @MainActor [weak self] in
            await self?.runClaimAttempt()
        }
    }

    func resetBackoff() {
        guard !disposed else { return }
        backoff.reset()
    }

    func waitForPendingTakeover() async {
        await takeoverTask?.value
    }

    func handleOnline() {
        guard !disposed else { return }
        backoff.reset()
        guard case .acquiring = state else { return }
        retryTask?.cancel()
        retryTask = nil
        if claimTask == nil {
            claimTask = Task { @MainActor [weak self] in
                await self?.runClaimAttempt()
            }
        }
    }

    func handleOffline() {
        guard !disposed else { return }
        guard case .acquiring = state else { return }
        retryTask?.cancel()
        retryTask = nil
        onEvent(.claimError(nil, offline: true))
    }

    func takeover() -> Bool {
        guard !disposed, !isTakeoverInFlight else { return false }
        switch state {
        case .openElsewhere, .evicted:
            break
        default:
            return false
        }

        claimTask?.cancel()
        claimTask = nil
        retryTask?.cancel()
        retryTask = nil
        isTakeoverInFlight = true

        takeoverTask = Task { @MainActor [weak self] in
            await self?.runTakeover()
        }
        return true
    }

    func markEvicted() {
        guard !disposed else { return }
        claimTask?.cancel()
        claimTask = nil
        retryTask?.cancel()
        retryTask = nil
        takeoverTask?.cancel()
        takeoverTask = nil
        isTakeoverInFlight = false
        if state != .evicted {
            transition(to: .evicted)
            onEvent(.evicted)
        }
    }

    func markReadOnly(_ reason: ReadOnlyReason) {
        guard !disposed else { return }
        claimTask?.cancel()
        claimTask = nil
        retryTask?.cancel()
        retryTask = nil
        transition(to: .readOnly(reason))
    }

    func dispose() {
        disposed = true
        claimTask?.cancel()
        claimTask = nil
        retryTask?.cancel()
        retryTask = nil
        takeoverTask?.cancel()
        takeoverTask = nil
        isTakeoverInFlight = false
    }

    private func ensurePersistedSessionId() async -> String {
        if let cached = persistedSessionId, !cached.isEmpty {
            return cached
        }
        if let stored = await draftStore.readSessionId(attemptId: attemptId), !stored.isEmpty {
            persistedSessionId = stored
            return stored
        }
        let generated = sessionIdGenerator()
        persistedSessionId = generated
        try? await draftStore.writeSessionId(generated, attemptId: attemptId)
        return generated
    }

    private func runClaimAttempt() async {
        defer { claimTask = nil }

        let sessionId = await ensurePersistedSessionId()
        guard !disposed, !Task.isCancelled else { return }

        if !isOnline() {
            onEvent(.claimError(nil, offline: true))
            return
        }

        do {
            let response = try await api.claimSession(
                attemptId: attemptId,
                sessionId: sessionId,
                takeover: false
            )
            guard !disposed, !Task.isCancelled else { return }
            persistedSessionId = response.sessionId
            try? await draftStore.writeSessionId(response.sessionId, attemptId: attemptId)
            backoff.reset()
            onEvent(.serverNow(response.serverNow))
            onEvent(.claimError(nil, offline: false))
            transition(to: .editing(sessionId: response.sessionId))
            onEvent(.claimSucceeded(sessionId: response.sessionId, fromTakeover: false))
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return }
            handleClaimFailure(apiError)
        } catch {
            guard !disposed, !Task.isCancelled else { return }
            handleClaimFailure(.network(.unknown))
        }
    }

    private func runTakeover() async {
        defer {
            isTakeoverInFlight = false
            takeoverTask = nil
        }

        let sessionId = await ensurePersistedSessionId()
        guard !disposed, !Task.isCancelled else { return }

        do {
            let response = try await api.claimSession(
                attemptId: attemptId,
                sessionId: sessionId,
                takeover: true
            )
            guard !disposed, !Task.isCancelled else { return }
            persistedSessionId = response.sessionId
            try? await draftStore.writeSessionId(response.sessionId, attemptId: attemptId)
            guard !disposed, !Task.isCancelled else { return }
            backoff.reset()
            onEvent(.serverNow(response.serverNow))
            onEvent(.claimError(nil, offline: false))

            let reloadOutcome = await onTakeoverReload(response.sessionId)
            guard !disposed, !Task.isCancelled else { return }
            switch reloadOutcome {
            case .readyToEdit:
                transition(to: .editing(sessionId: response.sessionId))
                onEvent(.claimSucceeded(sessionId: response.sessionId, fromTakeover: true))
            case .closed:
                break
            case let .failed(apiError):
                onEvent(.takeoverFailed(apiError))
            }
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return }
            if case let .conflict(code, _, _) = apiError {
                if code == "TIME_UP" {
                    transition(to: .readOnly(.closed(.timeUp)))
                    onEvent(.attemptClosed(.timeUp))
                    return
                } else if code == "ATTEMPT_NOT_OPEN" {
                    transition(to: .readOnly(.closed(.notOpen)))
                    onEvent(.attemptClosed(.notOpen))
                    return
                }
            }
            onEvent(.takeoverFailed(apiError))
        } catch {
            guard !disposed, !Task.isCancelled else { return }
            onEvent(.takeoverFailed(nil))
        }
    }

    private func handleClaimFailure(_ error: AttemptAPIError) {
        switch error {
        case let .conflict(code, lastSeenAt, _):
            switch code {
            case "ATTEMPT_OPEN_ELSEWHERE":
                backoff.reset()
                onEvent(.claimError(nil, offline: false))
                transition(to: .openElsewhere(lastSeenAt: lastSeenAt))
            case "TIME_UP":
                backoff.reset()
                transition(to: .readOnly(.closed(.timeUp)))
                onEvent(.attemptClosed(.timeUp))
            case "ATTEMPT_NOT_OPEN":
                backoff.reset()
                transition(to: .readOnly(.closed(.notOpen)))
                onEvent(.attemptClosed(.notOpen))
            default:
                scheduleClaimRetry(reason: .networkError)
            }
        case .unauthorized:
            return
        case .serverError:
            scheduleClaimRetry(reason: .serverError)
        case let .rateLimited(retryAfter):
            let seconds = max(1, retryAfter ?? backoff.nextDelaySeconds())
            onEvent(.claimError(.rateLimited(seconds), offline: false))
            scheduleRetryAfter(delay: .seconds(seconds))
        case .network:
            if !isOnline() {
                onEvent(.claimError(nil, offline: true))
            } else {
                scheduleClaimRetry(reason: .networkError)
            }
        case .clientError:
            scheduleClaimRetry(reason: .networkError)
        }
    }

    private func scheduleClaimRetry(reason: SaveFailureReason) {
        if !isOnline() {
            onEvent(.claimError(nil, offline: true))
            return
        }
        onEvent(.claimError(reason, offline: false))
        let delay = backoff.nextDelay()
        scheduleRetryAfter(delay: delay)
    }

    private func scheduleRetryAfter(delay: Duration) {
        retryTask?.cancel()
        retryTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await self.clock.sleep(for: delay)
            } catch {
                return
            }
            guard !self.disposed, !Task.isCancelled else { return }
            self.retryTask = nil
            guard case .acquiring = self.state else { return }
            if self.claimTask == nil {
                self.claimTask = Task { @MainActor [weak self] in
                    await self?.runClaimAttempt()
                }
            }
        }
    }

    private func transition(to next: EditSessionState) {
        guard state != next else { return }
        state = next
        onEvent(.stateChanged(next))
    }
}
