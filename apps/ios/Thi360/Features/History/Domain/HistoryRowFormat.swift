import Foundation

enum HistoryStatusTone: Equatable, Sendable {
    case success
    case warning
    case info
    case danger
    case neutral
}

struct HistoryStatusInfo: Equatable, Sendable {
    let localizationKey: String
    let tone: HistoryStatusTone
}

enum HistoryFaultKind: Equatable, Sendable {
    case compensated
    case systemFault

    var localizationKey: String {
        switch self {
        case .compensated: "history.fault.compensated"
        case .systemFault: "history.fault.systemFault"
        }
    }
}

struct HistoryScoreDisplay: Equatable, Sendable {
    let text: String
    let hasScore: Bool
}

enum HistoryRowFormat {
    static let vietnamTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!
    private static let knownChoiceOptionCodes: Set<String> = ["ANH", "SCIENCE", "ENGLISH", "KH", "TA", "CS", "ICT"]

    static func resolveOptionLabel(_ item: CatalogOptionItem, locale: AppLocale) -> String {
        let vi = item.nameVi.trimmingCharacters(in: .whitespacesAndNewlines)
        let en = item.nameEn.trimmingCharacters(in: .whitespacesAndNewlines)
        switch locale {
        case .vi:
            if !vi.isEmpty { return vi }
            if !en.isEmpty { return en }
            return item.code
        case .en:
            if !en.isEmpty { return en }
            if !vi.isEmpty { return vi }
            return item.code
        }
    }

    static func resolveCatalogLabel(
        code: String,
        catalogMap: [String: CatalogOptionItem],
        locale: AppLocale
    ) -> String {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        guard let option = catalogMap[trimmed] else { return trimmed }
        return resolveOptionLabel(option, locale: locale)
    }

    static func formatDateTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = vietnamTimeZone
        formatter.dateFormat = "HH:mm, dd/MM/yyyy"
        return formatter.string(from: date)
    }

    static func shouldShowScore(
        state: String?,
        regrading: Bool,
        displayScore: Double?,
        rawScore: Double?
    ) -> Bool {
        guard displayScore != nil || rawScore != nil else { return false }
        return state == "SCORED" || (state == "PENDING_REVIEW" && regrading)
    }

    static func formatScoreNumber(_ value: Double, locale: AppLocale) -> String {
        let rounded = (value * 100.0).rounded() / 100.0
        let normalized = rounded == 0 ? 0.0 : rounded
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        formatter.usesGroupingSeparator = false
        formatter.decimalSeparator = (locale == .en) ? "." : ","
        return formatter.string(from: NSNumber(value: normalized)) ?? String(normalized)
    }

    static func formatScore(
        for item: HistoryAttemptItem,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> HistoryScoreDisplay {
        guard shouldShowScore(
            state: item.state,
            regrading: item.regrading,
            displayScore: item.displayScore,
            rawScore: item.rawScore
        ) else {
            return HistoryScoreDisplay(
                text: MyExamsStrings.text("history.noScore", locale: locale, bundle: bundle),
                hasScore: false
            )
        }
        let scoreVal = item.displayScore ?? item.rawScore ?? 0
        let maxVal = item.scaleMax ?? item.maxScore
        let formattedScore = formatScoreNumber(scoreVal, locale: locale)
        if let maxVal {
            return HistoryScoreDisplay(
                text: "\(formattedScore)/\(formatScoreNumber(maxVal, locale: locale))",
                hasScore: true
            )
        }
        return HistoryScoreDisplay(text: formattedScore, hasScore: true)
    }

    static func resolveStatus(state: String?, regrading: Bool) -> HistoryStatusInfo {
        if regrading {
            return HistoryStatusInfo(localizationKey: "history.states.REGRADING", tone: .warning)
        }
        switch state {
        case "SCORED":
            return HistoryStatusInfo(localizationKey: "history.states.SCORED", tone: .success)
        case "PENDING_REVIEW":
            return HistoryStatusInfo(localizationKey: "history.states.PENDING_REVIEW", tone: .warning)
        case "STARTING", "IN_PROGRESS":
            return HistoryStatusInfo(localizationKey: "history.states.IN_PROGRESS", tone: .info)
        case "GRADING":
            return HistoryStatusInfo(localizationKey: "history.states.GRADING", tone: .info)
        default:
            return HistoryStatusInfo(localizationKey: "history.states.GRADING_ERROR", tone: .danger)
        }
    }

    static func resolveFault(for item: HistoryAttemptItem) -> HistoryFaultKind? {
        if item.compensated {
            return .compensated
        }
        if item.faultFlag == "SYSTEM_FAULT" {
            return .systemFault
        }
        return nil
    }

    static func formatScopeLine(
        for item: HistoryAttemptItem,
        subjectMap: [String: CatalogOptionItem],
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        let rawScope = item.scope?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let scopeLabel: String
        if let knownScope = HistoryScope(rawValue: rawScope) {
            scopeLabel = MyExamsStrings.text(knownScope.localizationKey, locale: locale, bundle: bundle)
        } else if !rawScope.isEmpty {
            scopeLabel = rawScope
        } else {
            scopeLabel = MyExamsStrings.text("history.noChoice", locale: locale, bundle: bundle)
        }

        if rawScope == "PART",
           let partCode = item.partCode?.trimmingCharacters(in: .whitespacesAndNewlines),
           !partCode.isEmpty
        {
            let detail = MyExamsStrings.text(
                "history.scopeDetails.PART",
                locale: locale,
                replacements: ["code": partCode],
                bundle: bundle
            )
            return "\(scopeLabel) · \(detail)"
        }

        if rawScope == "SUBJECT",
           let subjectCode = item.subjectCode?.trimmingCharacters(in: .whitespacesAndNewlines),
           !subjectCode.isEmpty
        {
            let subjectName = resolveCatalogLabel(code: subjectCode, catalogMap: subjectMap, locale: locale)
            let detail = MyExamsStrings.text(
                "history.scopeDetails.SUBJECT",
                locale: locale,
                replacements: ["name": subjectName],
                bundle: bundle
            )
            return "\(scopeLabel) · \(detail)"
        }

        return scopeLabel
    }

    static func formatChoices(
        _ choices: [HistoryAttemptChoice],
        subjectMap: [String: CatalogOptionItem],
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        var formatted: [String] = []
        for entry in choices {
            let optionCode = entry.optionCode.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !optionCode.isEmpty else { continue }
            let optionLabel: String
            if knownChoiceOptionCodes.contains(optionCode) {
                optionLabel = MyExamsStrings.text("history.choiceOptions.\(optionCode)", locale: locale, bundle: bundle)
            } else {
                optionLabel = resolveCatalogLabel(code: optionCode, catalogMap: subjectMap, locale: locale)
            }
            let subjectLabels = entry.subjectCodes
                .map { resolveCatalogLabel(code: $0, catalogMap: subjectMap, locale: locale) }
                .filter { !$0.isEmpty }
            if subjectLabels.isEmpty {
                formatted.append(optionLabel)
            } else {
                formatted.append("\(optionLabel): \(subjectLabels.joined(separator: ", "))")
            }
        }
        if formatted.isEmpty {
            return MyExamsStrings.text("history.noChoice", locale: locale, bundle: bundle)
        }
        return formatted.joined(separator: " · ")
    }

    static func formatAttemptStartedLine(
        for item: HistoryAttemptItem,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        var parts: [String] = []
        if let attemptNo = item.attemptNo {
            parts.append(
                MyExamsStrings.text(
                    "history.attemptNo",
                    locale: locale,
                    replacements: ["count": String(attemptNo)],
                    bundle: bundle
                )
            )
        }
        if let startedAt = item.startedAt {
            parts.append(formatDateTime(startedAt))
        }
        return parts.joined(separator: " · ")
    }

    static func showsVactPracticeNote(for item: HistoryAttemptItem, hasScore: Bool) -> Bool {
        item.examKindCode == "VACT" && hasScore
    }

    static func showsRegradingNote(for item: HistoryAttemptItem) -> Bool {
        item.regrading
    }
}
