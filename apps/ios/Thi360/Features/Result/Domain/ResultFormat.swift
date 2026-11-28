import Foundation

struct ResolvedDisplayScore: Equatable, Hashable, Sendable {
    let score: Double
    let max: Double?
}

enum ResultDurationParts: Equatable, Hashable, Sendable {
    case zero
    case seconds(Int)
    case minutes(Int)
    case hoursMinutes(hours: Int, minutes: Int)
}

enum ResultStatusTone: Equatable, Hashable, Sendable {
    case success
    case warning
    case info
    case danger
    case neutral
}

enum ResultFormat {
    static let hoChiMinhTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!

    private static let knownChoiceOptionCodes: Set<String> = [
        "ANH", "SCIENCE", "ENGLISH", "KH", "TA", "CS", "ICT"
    ]

    private static let knownVactComponentCodes: Set<String> = [
        "VIETNAMESE", "ANH", "TOAN", "SCIENTIFIC_THINKING"
    ]

    static func formatScore(_ value: Double, locale: AppLocale) -> String {
        let rounded = (value * 100).rounded() / 100
        let normalized = rounded == 0 ? 0 : rounded
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: locale == .en ? "en_US" : "vi_VN")
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        formatter.usesGroupingSeparator = true
        return formatter.string(from: NSNumber(value: normalized)) ?? String(normalized)
    }

    static func formatAttemptDateTime(_ date: Date?) -> String? {
        guard let date else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = hoChiMinhTimeZone
        formatter.dateFormat = "HH:mm, dd/MM/yyyy"
        return formatter.string(from: date)
    }

    static func computeActualDuration(startedAt: Date?, finishedAt: Date?) -> ResultDurationParts? {
        guard let startedAt, let finishedAt else { return nil }
        let totalSeconds = max(0, Int(finishedAt.timeIntervalSince(startedAt).rounded()))
        if totalSeconds == 0 {
            return .zero
        }
        if totalSeconds < 60 {
            return .seconds(totalSeconds)
        }
        let totalMinutes = totalSeconds / 60
        if totalMinutes < 60 {
            return .minutes(totalMinutes)
        }
        return .hoursMinutes(hours: totalMinutes / 60, minutes: totalMinutes % 60)
    }

    static func formatDuration(startedAt: Date?, finishedAt: Date?, locale: AppLocale) -> String {
        guard let parts = computeActualDuration(startedAt: startedAt, finishedAt: finishedAt) else {
            return "—"
        }
        switch parts {
        case .zero:
            return ResultStrings.text("result.info.durationZero", locale: locale)
        case let .seconds(seconds):
            return ResultStrings.text(
                "result.info.durationSeconds",
                locale: locale,
                replacements: ["seconds": String(seconds)]
            )
        case let .minutes(minutes):
            return ResultStrings.text(
                "result.info.durationMinutes",
                locale: locale,
                replacements: ["minutes": String(minutes)]
            )
        case let .hoursMinutes(hours, minutes):
            return ResultStrings.text(
                "result.info.durationHoursMinutes",
                locale: locale,
                replacements: ["hours": String(hours), "minutes": String(minutes)]
            )
        }
    }

    static func shouldShowScore(
        state: AttemptResultState,
        regrading: Bool,
        displayScore: Double?,
        rawScore: Double?
    ) -> Bool {
        guard displayScore != nil || rawScore != nil else { return false }
        return state == .scored || (state == .pendingReview && regrading)
    }

    static func resolveDisplayScore(for result: AttemptResult) -> ResolvedDisplayScore? {
        guard shouldShowScore(
            state: result.state,
            regrading: result.regrading,
            displayScore: result.displayScore,
            rawScore: result.rawScore
        ) else {
            return nil
        }
        guard let score = result.displayScore ?? result.rawScore else {
            return nil
        }
        let maxScore = result.scaleMax ?? result.rawMaxScore
        return ResolvedDisplayScore(score: score, max: maxScore)
    }

    static func resolveCatalogLabel(
        code: String?,
        options: [String: CatalogOptionItem],
        locale: AppLocale
    ) -> String {
        guard let code, !code.isEmpty else { return "" }
        guard let item = options[code] else { return code }
        return item.localizedName(for: locale)
    }

    static func resolveSubjectName(
        subject: AttemptResultSubjectItem,
        subjectOptions: [String: CatalogOptionItem],
        locale: AppLocale
    ) -> String {
        if let code = subject.code, let option = subjectOptions[code] {
            return option.localizedName(for: locale)
        }
        if let name = subject.name, !name.isEmpty {
            return name
        }
        return subject.code ?? "—"
    }

    static func formatChoices(
        _ choices: [AttemptChoiceItem],
        subjectOptions: [String: CatalogOptionItem],
        locale: AppLocale
    ) -> String {
        guard !choices.isEmpty else {
            return ResultStrings.text("result.info.noChoices", locale: locale)
        }
        let formatted = choices.compactMap { choice -> String? in
            let code = choice.optionCode.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !code.isEmpty else { return nil }
            let optionLabel: String
            if knownChoiceOptionCodes.contains(code) {
                optionLabel = ResultStrings.text("result.choiceOptions.\(code)", locale: locale)
            } else {
                optionLabel = resolveCatalogLabel(code: code, options: subjectOptions, locale: locale)
            }
            let subjectLabels = choice.subjectCodes
                .map { resolveCatalogLabel(code: $0, options: subjectOptions, locale: locale) }
                .filter { !$0.isEmpty }
            if subjectLabels.isEmpty {
                return optionLabel
            }
            return "\(optionLabel): \(subjectLabels.joined(separator: ", "))"
        }
        if formatted.isEmpty {
            return ResultStrings.text("result.info.noChoices", locale: locale)
        }
        return formatted.joined(separator: " · ")
    }

    static func resolveScopeLabel(_ scope: AttemptResultScope?, locale: AppLocale) -> String {
        guard let scope else { return "—" }
        if let key = scope.localizationKey {
            return ResultStrings.text(key, locale: locale)
        }
        return scope.fallbackText
    }

    static func resolveEndReasonLabel(_ reason: AttemptResultEndReason?, locale: AppLocale) -> String {
        guard let reason else { return "—" }
        if let key = reason.localizationKey {
            return ResultStrings.text(key, locale: locale)
        }
        return reason.fallbackText
    }

    static func resolveStatusTone(state: AttemptResultState, regrading: Bool) -> ResultStatusTone {
        if regrading { return .warning }
        switch state {
        case .scored: return .success
        case .pendingReview: return .warning
        case .grading: return .info
        case .gradingError: return .danger
        }
    }

    static func resolveVactComponentLabel(
        code: String,
        subjectOptions: [String: CatalogOptionItem],
        locale: AppLocale
    ) -> String {
        if knownVactComponentCodes.contains(code) {
            return ResultStrings.text("result.vact.components.\(code)", locale: locale)
        }
        return resolveCatalogLabel(code: code, options: subjectOptions, locale: locale)
    }

    static func isLogicDataSubsection(
        questionCount: Int?,
        index: Int,
        totalCount: Int
    ) -> Bool {
        if questionCount == 12 {
            return true
        }
        return questionCount != 18 && totalCount == 2 && index == 0
    }

    static func resolveVactSubsectionLabel(
        subsection: AttemptResultVactSubsectionItem,
        index: Int,
        totalCount: Int,
        locale: AppLocale
    ) -> String {
        if isLogicDataSubsection(questionCount: subsection.questionCount, index: index, totalCount: totalCount) {
            return ResultStrings.text("result.vact.subsections.logicData", locale: locale)
        }
        return ResultStrings.text("result.vact.subsections.scientificReasoning", locale: locale)
    }

    static func formatSegmentScore(_ segment: AttemptResultSegmentItem, locale: AppLocale) -> String? {
        guard let score = segment.score else { return nil }
        if let maxPoints = segment.maxPoints {
            return "\(formatScore(score, locale: locale))/\(formatScore(maxPoints, locale: locale))"
        }
        return formatScore(score, locale: locale)
    }
}
