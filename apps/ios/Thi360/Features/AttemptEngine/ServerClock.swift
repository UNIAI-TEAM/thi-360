import Foundation

struct Countdown: Equatable, Sendable {
    enum Target: Equatable, Sendable {
        case attempt
        case part(PartID)
        case choice(PartID)
        case none
    }

    let target: Target
    let remaining: Duration?

    static let none = Countdown(target: .none, remaining: nil)

    var remainingSecondsCeil: Int? {
        guard let remaining else { return nil }
        let components = remaining.components
        if components.seconds <= 0 && components.attoseconds <= 0 {
            return 0
        }
        let whole = Int(components.seconds)
        return components.attoseconds > 0 ? whole + 1 : whole
    }

    var isExpired: Bool {
        guard let remaining else { return false }
        return remaining <= .zero
    }
}

struct ServerClock: Equatable, Sendable {
    struct Anchor: Equatable, Sendable {
        let serverNow: Date
        let monotonicInstant: ContinuousClock.Instant
    }

    private(set) var anchor: Anchor?

    init(anchor: Anchor? = nil) {
        self.anchor = anchor
    }

    mutating func reanchor(serverNow: Date, at monotonicInstant: ContinuousClock.Instant) {
        anchor = Anchor(serverNow: serverNow, monotonicInstant: monotonicInstant)
    }

    func estimatedServerNow(at monotonicInstant: ContinuousClock.Instant) -> Date? {
        guard let anchor else { return nil }
        let elapsed = max(.zero, monotonicInstant - anchor.monotonicInstant)
        return anchor.serverNow.addingTimeInterval(Self.timeInterval(from: elapsed))
    }

    func remaining(until deadline: Date?, at monotonicInstant: ContinuousClock.Instant) -> Duration? {
        guard let deadline, let currentServerNow = estimatedServerNow(at: monotonicInstant) else {
            return nil
        }
        let deltaSeconds = deadline.timeIntervalSince(currentServerNow)
        if deltaSeconds <= 0 {
            return .zero
        }
        return Self.duration(from: deltaSeconds)
    }

    static func timeInterval(from duration: Duration) -> TimeInterval {
        let components = duration.components
        return TimeInterval(components.seconds) + TimeInterval(components.attoseconds) / 1e18
    }

    static func duration(from seconds: TimeInterval) -> Duration {
        guard seconds > 0 else { return .zero }
        let milliseconds = Int64((seconds * 1000.0).rounded())
        return .milliseconds(milliseconds)
    }
}
