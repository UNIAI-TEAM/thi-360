import Foundation

enum SendCooldown: Equatable, Sendable {
    case ready
    case counting(until: Date)
    case dailyLimit(until: Date)

    static let defaultSeconds = 60
    static let dailyLimitThresholdSeconds = 3_600
    static let hoChiMinhTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!

    static func start(retryAfterSeconds: Int?, clock: any AppClock) -> SendCooldown {
        let seconds = max(1, retryAfterSeconds ?? defaultSeconds)
        let until = clock.now.addingTimeInterval(TimeInterval(seconds))
        if seconds >= dailyLimitThresholdSeconds {
            return .dailyLimit(until: until)
        }
        return .counting(until: until)
    }

    var untilDate: Date? {
        switch self {
        case .ready:
            nil
        case let .counting(until), let .dailyLimit(until):
            until
        }
    }

    var isDailyLimit: Bool {
        if case .dailyLimit = self {
            return true
        }
        return false
    }

    func remainingSeconds(clock: any AppClock) -> Int {
        guard let until = untilDate else { return 0 }
        let interval = until.timeIntervalSince(clock.now)
        if interval <= 0 {
            return 0
        }
        return Int(ceil(interval))
    }

    func resolved(clock: any AppClock) -> SendCooldown {
        switch self {
        case .ready:
            return .ready
        case .counting, .dailyLimit:
            return remainingSeconds(clock: clock) <= 0 ? .ready : self
        }
    }

    static func formatClock(_ seconds: Int) -> String {
        let clamped = max(0, seconds)
        let minutes = clamped / 60
        let secs = clamped % 60
        return String(format: "%02d:%02d", minutes, secs)
    }

    static func formatDailyLimit(_ until: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = hoChiMinhTimeZone
        formatter.dateFormat = "d/M HH:mm"
        return formatter.string(from: until)
    }
}
