import Foundation

struct TimerParts: Equatable, Hashable, Sendable {
    let hours: Int
    let minutes: Int
    let seconds: Int
}

enum TimerText {
    static let examUrgentThresholdSeconds: Int = 60
    static let choiceUrgentThresholdSeconds: Int = 30
    static let examUrgentThreshold: Duration = .seconds(60)
    static let choiceUrgentThreshold: Duration = .seconds(30)
    static let vietnamTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!

    static func ceilSeconds(from duration: Duration) -> Int {
        let components = duration.components
        if components.seconds <= 0 && components.attoseconds <= 0 {
            return 0
        }
        let whole = Int(components.seconds)
        return components.attoseconds > 0 ? whole + 1 : whole
    }

    static func ceilSeconds(fromMilliseconds remainingMs: Int64) -> Int {
        guard remainingMs > 0 else { return 0 }
        return Int((remainingMs + 999) / 1_000)
    }

    static func parts(remaining: Duration) -> TimerParts {
        parts(totalSeconds: ceilSeconds(from: remaining))
    }

    static func parts(remainingMs: Int64) -> TimerParts {
        parts(totalSeconds: ceilSeconds(fromMilliseconds: remainingMs))
    }

    static func parts(totalSeconds: Int) -> TimerParts {
        let clamped = max(0, totalSeconds)
        let hours = clamped / 3_600
        let minutes = (clamped % 3_600) / 60
        let seconds = clamped % 60
        return TimerParts(hours: hours, minutes: minutes, seconds: seconds)
    }

    static func format(remaining: Duration, locale _: AppLocale = .vi) -> String {
        format(parts: parts(remaining: remaining))
    }

    static func format(remainingMs: Int64, locale _: AppLocale = .vi) -> String {
        format(parts: parts(remainingMs: remainingMs))
    }

    static func format(parts: TimerParts) -> String {
        if parts.hours > 0 {
            return "\(parts.hours):\(pad2(parts.minutes)):\(pad2(parts.seconds))"
        }
        return "\(pad2(parts.minutes)):\(pad2(parts.seconds))"
    }

    static func isUrgent(
        remaining: Duration,
        threshold: Duration = examUrgentThreshold
    ) -> Bool {
        remaining <= threshold
    }

    static func isUrgent(
        remainingMs: Int64,
        thresholdSeconds: Int = examUrgentThresholdSeconds
    ) -> Bool {
        remainingMs <= Int64(thresholdSeconds) * 1_000
    }

    static func accessibilityLabel(
        prefix: String,
        parts: TimerParts,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        if parts.hours > 0 {
            return PlayerStrings.text(
                "attempt.timer.ariaLabel",
                locale: locale,
                replacements: [
                    "label": prefix,
                    "hours": String(parts.hours),
                    "minutes": String(parts.minutes),
                    "seconds": String(parts.seconds)
                ],
                bundle: bundle
            )
        }
        return PlayerStrings.text(
            "attempt.timer.ariaLabelShort",
            locale: locale,
            replacements: [
                "label": prefix,
                "minutes": String(parts.minutes),
                "seconds": String(parts.seconds)
            ],
            bundle: bundle
        )
    }

    static func formatLastSeenAt(_ date: Date, locale: AppLocale) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = vietnamTimeZone
        switch locale {
        case .vi:
            formatter.locale = Locale(identifier: "vi_VN")
            formatter.dateFormat = "HH:mm:ss dd/MM/yyyy"
        case .en:
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "MMM d, yyyy, HH:mm:ss"
        }
        return formatter.string(from: date)
    }

    private static func pad2(_ value: Int) -> String {
        value < 10 ? "0\(value)" : "\(value)"
    }
}
