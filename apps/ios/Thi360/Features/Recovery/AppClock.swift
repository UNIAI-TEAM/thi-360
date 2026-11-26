import Foundation

protocol AppClock: Sendable {
    var now: Date { get }
}

struct SystemClock: AppClock {
    var now: Date { Date() }
}

enum CooldownFormatter {
    static let longCooldownThresholdSeconds = 3600

    static func mmss(_ seconds: Int) -> String {
        let clamped = max(0, seconds)
        let minutes = clamped / 60
        let remainder = clamped % 60
        return String(format: "%02d:%02d", minutes, remainder)
    }

    static func dailyLimitDateString(_ targetDate: Date, locale: AppLocale) -> String {
        let formatter = DateFormatter()
        formatter.locale = locale.locale
        formatter.dateFormat = "HH:mm d/M"
        return formatter.string(from: targetDate)
    }
}
