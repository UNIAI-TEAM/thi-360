import Foundation

protocol PrepareClock: Sendable {
    var nowSeconds: TimeInterval { get }
    func sleep(seconds: TimeInterval) async throws
}

struct SystemPrepareClock: PrepareClock {
    private let clock = ContinuousClock()
    private let origin: ContinuousClock.Instant

    init() {
        self.origin = ContinuousClock().now
    }

    var nowSeconds: TimeInterval {
        let elapsed = origin.duration(to: clock.now)
        let components = elapsed.components
        return TimeInterval(components.seconds) + TimeInterval(components.attoseconds) / 1_000_000_000_000_000_000.0
    }

    func sleep(seconds: TimeInterval) async throws {
        let clamped = max(0, seconds)
        try await clock.sleep(for: .milliseconds(Int64((clamped * 1000.0).rounded())))
    }
}

@MainActor
final class WaitingPoller {
    static let fastIntervalSeconds: TimeInterval = 1.0
    static let slowIntervalSeconds: TimeInterval = 5.0
    static let busyThresholdSeconds: TimeInterval = 90.0

    let attemptId: String
    private let clock: any PrepareClock
    private let api: any PrepareAPIClientProtocol
    private let initialElapsedSeconds: TimeInterval
    private let onReady: @MainActor (String) -> Void
    private let onAborted: @MainActor (String?) -> Void
    private let onPhaseChanged: @MainActor (WaitingPhase) -> Void

    private(set) var phase: WaitingPhase
    private var startedAtSeconds: TimeInterval = 0
    private var hasStarted = false
    private var isForeground = true
    private var isTerminated = false
    private var pollTask: Task<Void, Never>?
    private var busyTask: Task<Void, Never>?

    init(
        attemptId: String,
        clock: any PrepareClock = SystemPrepareClock(),
        api: any PrepareAPIClientProtocol,
        initialElapsedSeconds: TimeInterval = 0,
        onReady: @escaping @MainActor (String) -> Void = { _ in },
        onAborted: @escaping @MainActor (String?) -> Void = { _ in },
        onPhaseChanged: @escaping @MainActor (WaitingPhase) -> Void = { _ in }
    ) {
        self.attemptId = attemptId
        self.clock = clock
        self.api = api
        let clampedInitial = max(0, initialElapsedSeconds)
        self.initialElapsedSeconds = clampedInitial
        self.onReady = onReady
        self.onAborted = onAborted
        self.onPhaseChanged = onPhaseChanged
        self.phase = clampedInitial >= Self.busyThresholdSeconds
            ? .busy
            : .polling(elapsed: clampedInitial)
    }

    var currentElapsedSeconds: TimeInterval {
        guard hasStarted else { return initialElapsedSeconds }
        return max(0, initialElapsedSeconds + (clock.nowSeconds - startedAtSeconds))
    }

    func start(initiallyForeground: Bool = true) {
        guard !hasStarted, !isTerminated else { return }
        hasStarted = true
        startedAtSeconds = clock.nowSeconds
        isForeground = initiallyForeground
        emitPhase(phase)
        if isForeground {
            launchLoops(immediateFirstPoll: false)
        }
    }

    func setForeground(_ foreground: Bool) {
        guard !isTerminated else { return }
        if !hasStarted {
            isForeground = foreground
            return
        }
        guard isForeground != foreground else { return }
        isForeground = foreground
        if foreground {
            launchLoops(immediateFirstPoll: true)
        } else {
            cancelLoops()
        }
    }

    func stop() {
        isTerminated = true
        cancelLoops()
    }

    private func cancelLoops() {
        pollTask?.cancel()
        pollTask = nil
        busyTask?.cancel()
        busyTask = nil
    }

    private func launchLoops(immediateFirstPoll: Bool) {
        cancelLoops()
        guard !isTerminated, isForeground else { return }

        let elapsedNow = currentElapsedSeconds
        if elapsedNow >= Self.busyThresholdSeconds {
            emitPhase(.busy)
        } else {
            let remaining = max(0, Self.busyThresholdSeconds - elapsedNow)
            busyTask = Task { [weak self] in
                guard let self else { return }
                do {
                    try await self.clock.sleep(seconds: remaining)
                } catch {
                    return
                }
                guard !Task.isCancelled, !self.isTerminated, self.isForeground else { return }
                self.emitPhase(.busy)
            }
        }

        pollTask = Task { [weak self] in
            guard let self else { return }
            var nextDelay: TimeInterval
            if immediateFirstPoll {
                nextDelay = 0
            } else if self.currentElapsedSeconds >= Self.busyThresholdSeconds {
                nextDelay = Self.slowIntervalSeconds
            } else {
                nextDelay = Self.fastIntervalSeconds
            }

            while !Task.isCancelled && !self.isTerminated && self.isForeground {
                if nextDelay > 0 {
                    do {
                        try await self.clock.sleep(seconds: nextDelay)
                    } catch {
                        return
                    }
                }
                guard !Task.isCancelled, !self.isTerminated, self.isForeground else { return }

                let pollResult = try? await self.api.getAttemptState(attemptId: self.attemptId)
                guard !Task.isCancelled, !self.isTerminated, self.isForeground else { return }

                if let pollResult {
                    switch pollResult.state {
                    case .starting:
                        break
                    case let .aborted(abortReason):
                        self.isTerminated = true
                        self.busyTask?.cancel()
                        self.busyTask = nil
                        self.pollTask = nil
                        self.emitPhase(.failed(abortReason: abortReason))
                        self.onAborted(abortReason)
                        return
                    case .inProgress, .closed:
                        self.isTerminated = true
                        self.busyTask?.cancel()
                        self.busyTask = nil
                        self.pollTask = nil
                        self.onReady(self.attemptId)
                        return
                    }
                }

                let elapsed = self.currentElapsedSeconds
                if elapsed >= Self.busyThresholdSeconds {
                    self.emitPhase(.busy)
                    nextDelay = Self.slowIntervalSeconds
                } else {
                    self.emitPhase(.polling(elapsed: elapsed))
                    nextDelay = Self.fastIntervalSeconds
                }
            }
        }
    }

    private func emitPhase(_ next: WaitingPhase) {
        phase = next
        onPhaseChanged(next)
    }
}
