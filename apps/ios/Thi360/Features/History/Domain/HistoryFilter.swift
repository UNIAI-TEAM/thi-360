import Foundation

struct HistoryCalendarDate: Equatable, Hashable, Comparable, Sendable {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    init?(isoString: String) {
        let trimmed = isoString.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = trimmed.split(separator: "-")
        guard parts.count == 3,
              parts[0].count == 4,
              parts[1].count == 2,
              parts[2].count == 2,
              let y = Int(parts[0]),
              let m = Int(parts[1]),
              let d = Int(parts[2])
        else {
            return nil
        }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        var components = DateComponents()
        components.year = y
        components.month = m
        components.day = d
        components.hour = 12
        guard let date = calendar.date(from: components) else { return nil }
        let check = calendar.dateComponents([.year, .month, .day], from: date)
        guard check.year == y, check.month == m, check.day == d else { return nil }
        self.year = y
        self.month = m
        self.day = d
    }

    init(date: Date, calendar: Calendar = .current) {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        let components = gregorian.dateComponents([.year, .month, .day], from: date)
        self.year = components.year ?? 2026
        self.month = components.month ?? 1
        self.day = components.day ?? 1
    }

    var isoString: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    var displayString: String {
        String(format: "%02d/%02d/%04d", day, month, year)
    }

    func toPickerDate(calendar: Calendar = .current) -> Date {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = 12
        return gregorian.date(from: components) ?? Date()
    }

    static func < (lhs: HistoryCalendarDate, rhs: HistoryCalendarDate) -> Bool {
        if lhs.year != rhs.year { return lhs.year < rhs.year }
        if lhs.month != rhs.month { return lhs.month < rhs.month }
        return lhs.day < rhs.day
    }
}

enum HistoryScope: String, CaseIterable, Equatable, Hashable, Sendable {
    case full = "FULL"
    case part = "PART"
    case subject = "SUBJECT"

    var localizationKey: String {
        "history.scopes.\(rawValue)"
    }
}

struct HistoryRequestParams: Equatable, Hashable, Sendable {
    let q: String?
    let kind: String?
    let scope: HistoryScope?
    let from: String?
    let to: String?
    let page: Int
    let size: Int
}

struct HistoryFilter: Equatable, Hashable, Sendable {
    static let pageSize = 20
    static let empty = HistoryFilter()

    let q: String
    let kind: String?
    let scope: HistoryScope?
    let from: HistoryCalendarDate?
    let to: HistoryCalendarDate?

    init(
        q: String = "",
        kind: String? = nil,
        scope: HistoryScope? = nil,
        from: HistoryCalendarDate? = nil,
        to: HistoryCalendarDate? = nil
    ) {
        self.q = q
        let cleanKind = kind?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.kind = cleanKind.isEmpty ? nil : cleanKind
        self.scope = scope
        if let from, let to, from > to {
            self.from = from
            self.to = nil
        } else {
            self.from = from
            self.to = to
        }
    }

    var trimmedQ: String? {
        let trimmed = q.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var isEmpty: Bool {
        trimmedQ == nil && kind == nil && scope == nil && from == nil && to == nil
    }

    func withSearch(_ newQ: String) -> HistoryFilter {
        HistoryFilter(q: newQ, kind: kind, scope: scope, from: from, to: to)
    }

    func withKind(_ newKind: String?) -> HistoryFilter {
        HistoryFilter(q: q, kind: newKind, scope: scope, from: from, to: to)
    }

    func withScope(_ newScope: HistoryScope?) -> HistoryFilter {
        HistoryFilter(q: q, kind: kind, scope: newScope, from: from, to: to)
    }

    func withFrom(_ newFrom: HistoryCalendarDate?) -> HistoryFilter {
        if let newFrom, let to, newFrom > to {
            return self
        }
        return HistoryFilter(q: q, kind: kind, scope: scope, from: newFrom, to: to)
    }

    func withTo(_ newTo: HistoryCalendarDate?) -> HistoryFilter {
        if let newTo, let from, newTo < from {
            return self
        }
        return HistoryFilter(q: q, kind: kind, scope: scope, from: from, to: newTo)
    }

    func params(page: Int) -> HistoryRequestParams {
        let safeFrom: String?
        let safeTo: String?
        if let from, let to, from > to {
            safeFrom = from.isoString
            safeTo = nil
        } else {
            safeFrom = from?.isoString
            safeTo = to?.isoString
        }
        return HistoryRequestParams(
            q: trimmedQ,
            kind: kind,
            scope: scope,
            from: safeFrom,
            to: safeTo,
            page: max(0, page),
            size: Self.pageSize
        )
    }
}

struct HistoryAttemptChoice: Equatable, Hashable, Sendable {
    let partKey: String
    let optionCode: String
    let subjectCodes: [String]
}

struct HistoryAttemptItem: Equatable, Hashable, Identifiable, Sendable {
    let attemptId: String
    let examId: String?
    let examVersionNo: Int?
    let examName: String
    let examKindCode: String?
    let scope: String?
    let partCode: String?
    let subjectCode: String?
    let choices: [HistoryAttemptChoice]
    let attemptNo: Int?
    let startedAt: Date?
    let finishedAt: Date?
    let gradedAt: Date?
    let state: String?
    let regrading: Bool
    let regradingLabel: String?
    let practiceScoring: String?
    let rawScore: Double?
    let displayScore: Double?
    let maxScore: Double?
    let scaleMax: Double?
    let action: String?
    let resultView: String?
    let faultFlag: String?
    let faultReason: String?
    let compensated: Bool

    var id: String { attemptId }
}

struct HistoryPageResponse: Equatable, Sendable {
    let items: [HistoryAttemptItem]
    let page: Int
    let size: Int
    let totalItems: Int
    let totalPages: Int
}

struct HistoryPage: Equatable, Sendable {
    let items: [HistoryAttemptItem]
    let page: Int
    let totalItems: Int
    let totalPages: Int
    let hasMore: Bool
    let isLoadingMore: Bool
}
