import Foundation

protocol EngineClock: Sendable {
    var now: ContinuousClock.Instant { get }
    func sleep(for duration: Duration) async throws
}

struct SystemEngineClock: EngineClock {
    private let clock = ContinuousClock()

    var now: ContinuousClock.Instant {
        clock.now
    }

    func sleep(for duration: Duration) async throws {
        try await clock.sleep(for: duration)
    }
}

struct Backoff: Equatable, Sendable {
    static let defaultStepsSeconds: [Int] = [1, 2, 4, 8, 16, 30]

    let stepsSeconds: [Int]
    private(set) var attempt: Int = 0

    init(stepsSeconds: [Int] = Backoff.defaultStepsSeconds) {
        self.stepsSeconds = stepsSeconds.isEmpty ? Backoff.defaultStepsSeconds : stepsSeconds
    }

    mutating func nextDelaySeconds() -> Int {
        let index = min(attempt, stepsSeconds.count - 1)
        attempt += 1
        return stepsSeconds[index]
    }

    mutating func nextDelay() -> Duration {
        .seconds(nextDelaySeconds())
    }

    mutating func reset() {
        attempt = 0
    }
}
