import Foundation

enum HeartbeatOutcome: Equatable, Sendable {
    case active(serverNow: Date)
    case attemptClosed(serverNow: Date?, state: Components.Schemas.HeartbeatResponse.statePayload?)
    case evicted
    case transientFailure
    case skipped
}

@MainActor
final class HeartbeatLoop {
    static let interval: Duration = .seconds(30)

    private let attemptId: String
    private let api: any AttemptAPIClient
    private let clock: any EngineClock
    private let sessionIdProvider: @MainActor () -> String?
    private let canSend: @MainActor () -> Bool
    private let onOutcome: @MainActor (HeartbeatOutcome) -> Void

    private var loopTask: Task<Void, Never>?
    private var disposed = false

    init(
        attemptId: String,
        api: any AttemptAPIClient,
        clock: any EngineClock,
        sessionIdProvider: @escaping @MainActor () -> String?,
        canSend: @escaping @MainActor () -> Bool,
        onOutcome: @escaping @MainActor (HeartbeatOutcome) -> Void
    ) {
        self.attemptId = attemptId
        self.api = api
        self.clock = clock
        self.sessionIdProvider = sessionIdProvider
        self.canSend = canSend
        self.onOutcome = onOutcome
    }

    var isRunning: Bool {
        loopTask != nil
    }

    func start() {
        guard !disposed, loopTask == nil else { return }
        loopTask = Task { @MainActor [weak self] in
            await self?.runLoop()
        }
    }

    func stop() {
        loopTask?.cancel()
        loopTask = nil
    }

    @discardableResult
    func sendNow() async -> HeartbeatOutcome {
        guard !disposed, canSend(), let sessionId = sessionIdProvider() else {
            return .skipped
        }
        do {
            let response = try await api.heartbeat(attemptId: attemptId, sessionId: sessionId)
            guard !disposed, !Task.isCancelled else { return .skipped }
            if response.state != .IN_PROGRESS {
                stop()
                let outcome = HeartbeatOutcome.attemptClosed(serverNow: response.serverNow, state: response.state)
                onOutcome(outcome)
                return outcome
            }
            let outcome = HeartbeatOutcome.active(serverNow: response.serverNow)
            onOutcome(outcome)
            return outcome
        } catch let apiError as AttemptAPIError {
            guard !disposed, !Task.isCancelled else { return .skipped }
            if apiError.isSessionConflict {
                stop()
                onOutcome(.evicted)
                return .evicted
            }
            if case let .conflict(code, _, _) = apiError,
               code == "TIME_UP" || code == "ATTEMPT_NOT_OPEN"
            {
                stop()
                let outcome = HeartbeatOutcome.attemptClosed(serverNow: nil, state: nil)
                onOutcome(outcome)
                return outcome
            }
            onOutcome(.transientFailure)
            return .transientFailure
        } catch {
            guard !disposed, !Task.isCancelled else { return .skipped }
            onOutcome(.transientFailure)
            return .transientFailure
        }
    }

    func dispose() {
        disposed = true
        stop()
    }

    private func runLoop() async {
        while !disposed && !Task.isCancelled {
            do {
                try await clock.sleep(for: Self.interval)
            } catch {
                break
            }
            guard !disposed, !Task.isCancelled else { break }
            let outcome = await sendNow()
            switch outcome {
            case .evicted, .attemptClosed:
                return
            case .active, .transientFailure, .skipped:
                continue
            }
        }
        loopTask = nil
    }
}
