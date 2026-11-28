import Foundation

struct SelectedPathMetrics: Equatable, Sendable {
    let partCount: Int
    let questionCount: Int?
    let durationMinutes: Int?
    let maxPoints: Decimal?
}

enum PrepareMetrics {
    static func of(
        _ response: AttemptPrepareResponse,
        selection: PreChoiceSelection = .empty
    ) -> SelectedPathMetrics {
        let selectedOption = selection.selectedOption(in: response.preChoice)
        return of(response, selectedOption: selectedOption)
    }

    static func of(
        _ response: AttemptPrepareResponse,
        selectedOption: Components.Schemas.ChoiceOptionView?
    ) -> SelectedPathMetrics {
        let partCount = response.parts.count
        var questionSum = 0
        var hasQuestionCount = false
        var pointsSum = Decimal.zero
        var hasMaxPoints = false
        var durationSum = 0
        var hasPartDuration = false

        for part in response.parts {
            if let q = part.questionCount {
                questionSum += Int(q)
                hasQuestionCount = true
            }
            if let p = part.maxPoints {
                pointsSum += decimalFromDouble(p)
                hasMaxPoints = true
            }
            if let d = part.durationMinutes {
                durationSum += Int(d)
                hasPartDuration = true
            }
        }

        let fallbackQuestions: Int? = hasQuestionCount ? questionSum : nil
        let fallbackPoints: Decimal? = hasMaxPoints ? normalizePoints(pointsSum) : nil
        let fallbackDuration: Int?
        if let topDuration = response.durationMinutes {
            fallbackDuration = Int(topDuration)
        } else if hasPartDuration {
            fallbackDuration = durationSum
        } else {
            fallbackDuration = nil
        }

        let resolvedQuestions = selectedOption?.questionCount.map(Int.init) ?? fallbackQuestions
        let resolvedDuration = selectedOption?.durationMinutes.map(Int.init) ?? fallbackDuration
        let resolvedPoints = selectedOption?.maxPoints.map { normalizePoints(decimalFromDouble($0)) } ?? fallbackPoints

        return SelectedPathMetrics(
            partCount: partCount,
            questionCount: resolvedQuestions,
            durationMinutes: resolvedDuration,
            maxPoints: resolvedPoints
        )
    }

    static func decimalFromDouble(_ value: Double) -> Decimal {
        guard value.isFinite else { return .zero }
        let formatted = String(format: "%.6f", locale: Locale(identifier: "en_US_POSIX"), value)
        return Decimal(string: formatted, locale: Locale(identifier: "en_US_POSIX")) ?? Decimal(value)
    }

    static func normalizePoints(_ value: Decimal) -> Decimal {
        var input = value
        var rounded = Decimal()
        NSDecimalRound(&rounded, &input, 3, .plain)
        return rounded == .zero ? .zero : rounded
    }
}

enum PrepareFormatting {
    static let vietnamTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!

    static func formatPoints(_ value: Decimal, locale: AppLocale) -> String {
        let normalized = PrepareMetrics.normalizePoints(value)
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 3
        formatter.usesGroupingSeparator = false
        switch locale {
        case .vi:
            formatter.decimalSeparator = ","
        case .en:
            formatter.decimalSeparator = "."
        }
        return formatter.string(from: NSDecimalNumber(decimal: normalized))
            ?? NSDecimalNumber(decimal: normalized).stringValue
    }

    static func formatPoints(_ value: Double, locale: AppLocale) -> String {
        formatPoints(PrepareMetrics.decimalFromDouble(value), locale: locale)
    }

    static func formatDayMonth(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = vietnamTimeZone
        formatter.dateFormat = "dd/MM"
        return formatter.string(from: date)
    }

    static func localizedName(
        nameVi: String?,
        nameEn: String?,
        code: String?,
        locale: AppLocale
    ) -> String {
        let vi = nameVi?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let en = nameEn?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let fallback = code?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        switch locale {
        case .en:
            if !en.isEmpty { return en }
            if !vi.isEmpty { return vi }
            return fallback
        case .vi:
            if !vi.isEmpty { return vi }
            if !en.isEmpty { return en }
            return fallback
        }
    }

    static func supportSummary(for contact: SupportContact) -> String {
        let channels = [contact.phone, contact.email]
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        if channels.isEmpty {
            return contact.hours
        }
        if contact.hours.isEmpty {
            return channels
        }
        return "\(channels) (\(contact.hours))"
    }

    static func grantSourceLabel(
        for response: AttemptPrepareResponse,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String? {
        guard response.attemptsLeft > 0, let grantSource = response.grantSource else {
            return nil
        }
        switch grantSource.source {
        case .COMPENSATION:
            return PrepareStrings.text("prepare.grantSource.compensation", locale: locale, bundle: bundle)
        case .CLASS:
            if let grantedAt = grantSource.grantedAt {
                return PrepareStrings.text(
                    "prepare.grantSource.classAssigned",
                    locale: locale,
                    replacements: ["date": formatDayMonth(grantedAt)],
                    bundle: bundle
                )
            }
            return PrepareStrings.text("prepare.grantSource.classAssignedNoDate", locale: locale, bundle: bundle)
        case .PURCHASE:
            let dateText = grantSource.grantedAt.map(formatDayMonth) ?? ""
            let orderCode = grantSource.orderCode?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !orderCode.isEmpty {
                return PrepareStrings.text(
                    "prepare.grantSource.purchase",
                    locale: locale,
                    replacements: ["date": dateText, "orderCode": orderCode],
                    bundle: bundle
                )
            }
            return PrepareStrings.text(
                "prepare.grantSource.purchaseNoOrder",
                locale: locale,
                replacements: ["date": dateText],
                bundle: bundle
            )
        case .none:
            return nil
        }
    }

    static func summaryLine(
        metrics: SelectedPathMetrics,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        var parts: [String] = []
        if metrics.partCount > 0 {
            parts.append(PrepareStrings.plural("prepare.units.partCount", count: metrics.partCount, locale: locale, bundle: bundle))
        }
        if let questions = metrics.questionCount {
            parts.append(PrepareStrings.plural("prepare.units.questions", count: questions, locale: locale, bundle: bundle))
        }
        if let minutes = metrics.durationMinutes {
            parts.append(PrepareStrings.plural("prepare.units.minutes", count: minutes, locale: locale, bundle: bundle))
        }
        if let maxPoints = metrics.maxPoints {
            parts.append(
                PrepareStrings.text(
                    "prepare.units.points",
                    locale: locale,
                    replacements: ["value": formatPoints(maxPoints, locale: locale)],
                    bundle: bundle
                )
            )
        }
        return parts.joined(separator: " · ")
    }

    static func optionMetricsLine(
        questionCount: Int?,
        durationMinutes: Int?,
        maxPoints: Decimal?,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        var parts: [String] = []
        if let questionCount {
            parts.append(PrepareStrings.plural("prepare.units.questions", count: questionCount, locale: locale, bundle: bundle))
        }
        if let durationMinutes {
            parts.append(PrepareStrings.plural("prepare.units.minutes", count: durationMinutes, locale: locale, bundle: bundle))
        }
        if let maxPoints {
            parts.append(
                PrepareStrings.text(
                    "prepare.units.points",
                    locale: locale,
                    replacements: ["value": formatPoints(maxPoints, locale: locale)],
                    bundle: bundle
                )
            )
        }
        return parts.joined(separator: " · ")
    }
}
