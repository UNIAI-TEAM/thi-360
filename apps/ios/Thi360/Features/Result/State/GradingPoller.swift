import Foundation

enum PollPhase: Equatable, Sendable {
    case idle
    case polling(deadline: Duration)
    case paused(deadline: Duration)
    case timedOut
}

private protocol GradingClockDriver: Sendable {
    func elapsed() -> Duration
    func sleep(untilElapsed target: Duration) async throws
}

private struct TypedGradingClockDriver<C: Clock>: GradingClockDriver where C.Duration == Duration {
    let clock: C
    let origin: C.Instant

    init(_ clock: C) {
        self.clock = clock
        self.origin = clock.now
    }

    func elapsed() -> Duration {
        origin.duration(to: clock.now)
    }

    func sleep(untilElapsed target: Duration) async throws {
        try await clock.sleep(until: origin.advanced(by: target), tolerance: nil)
    }
}

actor GradingPoller {
    static let defaultInterval: Duration = .seconds(3)
    static let defaultWindow: Duration = .seconds(120)

    private let driver: any GradingClockDriver
    let interval: Duration
    let window: Duration

    private(set) var phase: PollPhase = .idle
    private var pollTask: Task<Void, Never>?
    private var pollHandler: (@Sendable () async -> Bool)?
    private var timeoutHandler: (@Sendable () async -> Void)?
    private var generation: UInt64 = 0

    init(
        clock: any Clock<Duration> = ContinuousClock(),
        interval: Duration = GradingPoller.defaultInterval,
        window: Duration = GradingPoller.defaultWindow
    ) {
        self.driver = Self.makeDriver(clock)
        self.interval = interval
        self.window = window
    }

    private static func makeDriver<C: Clock>(_ clock: C) -> any GradingClockDriver where C.Duration == Duration {
        TypedGradingClockDriver(clock)
    }

    func startIfNeeded(
        isSceneActive: Bool,
        onPoll: @escaping @Sendable () async -> Bool,
        onTimeout: @escaping @Sendable () async -> Void
    ) {
        self.pollHandler = onPoll
        self.timeoutHandler = onTimeout

        switch phase {
        case .polling, .paused, .timedOut:
            return
        case .idle:
            let deadline = driver.elapsed() + window
            if isSceneActive {
                beginPolling(deadline: deadline, pollImmediately: false)
            } else {
                phase = .paused(deadline: deadline)
            }
        }
    }

    func setSceneActive(_ isActive: Bool) {
        if !isActive {
            guard case let .polling(deadline) = phase else { return }
            generation &+= 1
            pollTask?.cancel()
            pollTask = nil
            phase = .paused(deadline: deadline)
            return
        }

        guard case let .paused(deadline) = phase else { return }
        if driver.elapsed() >= deadline {
            transitionToTimedOut()
            return
        }
        beginPolling(deadline: deadline, pollImmediately: true)
    }

    func restartWindowAfterReload(
        isSceneActive: Bool,
        onPoll: @escaping @Sendable () async -> Bool,
        onTimeout: @escaping @Sendable () async -> Void
    ) {
        generation &+= 1
        pollTask?.cancel()
        pollTask = nil
        self.pollHandler = onPoll
        self.timeoutHandler = onTimeout

        let deadline = driver.elapsed() + window
        if isSceneActive {
            beginPolling(deadline: deadline, pollImmediately: false)
        } else {
            phase = .paused(deadline: deadline)
        }
    }

    func stop() {
        generation &+= 1
        pollTask?.cancel()
        pollTask = nil
        phase = .idle
        pollHandler = nil
        timeoutHandler = nil
    }

    private func beginPolling(deadline: Duration, pollImmediately: Bool) {
        generation &+= 1
        let activeGeneration = generation
        pollTask?.cancel()
        phase = .polling(deadline: deadline)

        pollTask = Task { [weak self] in
            await self?.runLoop(
                generation: activeGeneration,
                deadline: deadline,
                pollImmediately: pollImmediately
            )
        }
    }

    private func runLoop(
        generation activeGeneration: UInt64,
        deadline: Duration,
        pollImmediately: Bool
    ) async {
        var shouldPollNow = pollImmediately

        while !Task.isCancelled {
            guard activeGeneration == self.generation, case .polling = phase else {
                return
            }

            let now = driver.elapsed()
            if now >= deadline {
                transitionToTimedOut()
                return
            }

            if !shouldPollNow {
                let nextWake = min(now + interval, deadline)
                do {
                    try await driver.sleep(untilElapsed: nextWake)
                } catch {
                    return
                }

                guard !Task.isCancelled, activeGeneration == self.generation, case .polling = phase else {
                    return
                }

                if driver.elapsed() >= deadline {
                    transitionToTimedOut()
                    return
                }
            }
            shouldPollNow = false

            guard let pollHandler else {
                phase = .idle
                return
            }
            let stillGrading = await pollHandler()

            guard !Task.isCancelled, activeGeneration == self.generation else {
                return
            }

            if !stillGrading {
                phase = .idle
                pollTask = nil
                return
            }

            if case .polling = phase, driver.elapsed() >= deadline {
                transitionToTimedOut()
                return
            }
        }
    }

    private func transitionToTimedOut() {
        generation &+= 1
        pollTask?.cancel()
        pollTask = nil
        phase = .timedOut
        let handler = timeoutHandler
        if let handler {
            Task {
                await handler()
            }
        }
    }
}
