import Foundation

enum CatalogFormatting {
    static let vietnamTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!
    static let examAccessBatchSize = 48

    static func chunked<T>(_ items: [T], size: Int = examAccessBatchSize) -> [[T]] {
        guard size > 0, !items.isEmpty else { return [] }
        var chunks: [[T]] = []
        chunks.reserveCapacity((items.count + size - 1) / size)
        var start = 0
        while start < items.count {
            let end = min(start + size, items.count)
            chunks.append(Array(items[start..<end]))
            start = end
        }
        return chunks
    }

    static func discountPercent(listPrice: Int64, price: Int64, hasPromotion: Bool) -> Int? {
        guard hasPromotion, listPrice > price, listPrice > 0 else {
            return nil
        }
        let ratio = (Double(listPrice - price) / Double(listPrice)) * 100.0
        return max(1, Int(ratio.rounded()))
    }

    static func formatDayMonth(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = vietnamTimeZone
        formatter.dateFormat = "dd/MM"
        return formatter.string(from: date)
    }

    static func formatInteger(_ value: Int64, locale: AppLocale) -> String {
        let formatter = makeNumberFormatter(locale: locale, maxFractionDigits: 0)
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }

    static func formatDecimal(_ value: Double, locale: AppLocale) -> String {
        let formatter = makeNumberFormatter(locale: locale, maxFractionDigits: 2)
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }

    static func formatPrice(_ amount: Int64, locale: AppLocale, bundle: Bundle = .main) -> String {
        let formattedAmount = formatInteger(amount, locale: locale)
        let template = CatalogStrings.text("storefront.price", locale: locale, bundle: bundle)
        return template
            .replacingOccurrences(of: "{amount, number}", with: formattedAmount)
            .replacingOccurrences(of: "{amount}", with: formattedAmount)
    }

    static func formatIntRange(_ range: IntRangeValue?, locale: AppLocale) -> String? {
        guard let range else { return nil }
        if range.min == range.max {
            return formatInteger(Int64(range.min), locale: locale)
        }
        return "\(formatInteger(Int64(range.min), locale: locale))–\(formatInteger(Int64(range.max), locale: locale))"
    }

    static func formatDecimalRange(_ range: DecimalRangeValue?, locale: AppLocale) -> String? {
        guard let range else { return nil }
        if range.min == range.max {
            return formatDecimal(range.min, locale: locale)
        }
        return "\(formatDecimal(range.min, locale: locale))–\(formatDecimal(range.max, locale: locale))"
    }

    static func mergeAnswerTypes(_ groups: [AnswerTypeGroupModel]) -> [(type: String, count: Int)] {
        var order: [String] = []
        var totals: [String: Int] = [:]
        for group in groups {
            let key = group.childAnswerType?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                ?? group.questionType
            if totals[key] == nil {
                order.append(key)
            }
            totals[key, default: 0] += group.questionCount
        }
        return order.map { ($0, totals[$0] ?? 0) }
    }

    static func formatAnswerTypeSummary(
        _ groups: [AnswerTypeGroupModel],
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        mergeAnswerTypes(groups)
            .map { item in
                let typeLabel = CatalogStrings.answerTypeLabel(for: item.type, locale: locale, bundle: bundle)
                return CatalogStrings.text(
                    "storefront.detail.answerTypeCount",
                    locale: locale,
                    replacements: [
                        "type": typeLabel,
                        "count": formatInteger(Int64(item.count), locale: locale)
                    ],
                    bundle: bundle
                )
            }
            .joined(separator: ", ")
    }

    static func cardSpecsLine(for product: CatalogProductCard, locale: AppLocale, bundle: Bundle = .main) -> String {
        var segments: [String] = []
        if let questions = formatIntRange(product.questionCount, locale: locale) {
            segments.append(
                AppStrings.common(
                    "storefront.units.questions",
                    locale: locale,
                    replacements: ["value": questions],
                    bundle: bundle
                )
            )
        }
        if let minutes = formatIntRange(product.durationMinutes, locale: locale) {
            segments.append(
                AppStrings.common(
                    "storefront.units.minutes",
                    locale: locale,
                    replacements: ["value": minutes],
                    bundle: bundle
                )
            )
        }
        let attemptsKey = product.isBundle ? "storefront.units.attemptsPerExam" : "storefront.units.attempts"
        segments.append(
            CatalogStrings.plural(
                attemptsKey,
                table: AppStrings.commonTable,
                count: product.attemptsGranted,
                locale: locale,
                bundle: bundle
            )
        )
        return segments.joined(separator: " · ")
    }

    static func showingLine(
        displayedCount: Int,
        total: Int,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        let from = displayedCount > 0 ? 1 : 0
        return CatalogStrings.text(
            "storefront.catalog.showing",
            locale: locale,
            replacements: [
                "from": formatInteger(Int64(from), locale: locale),
                "to": formatInteger(Int64(displayedCount), locale: locale),
                "total": formatInteger(Int64(total), locale: locale)
            ],
            bundle: bundle
        )
    }

    static func goneDescription(code: String, name: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let displayName = cleanName.isEmpty ? cleanCode : cleanName
        let tail = CatalogStrings.text("storefront.gone.descriptionTail", locale: locale, bundle: bundle)
        return "\(displayName) (\(cleanCode)) \(tail)"
    }

    static func bundleItemSubtitle(
        for item: BundleDetailItemModel,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        var parts: [String] = []
        if let subject = item.subjectName?.localized(for: locale).nonEmpty {
            parts.append(subject)
        }
        let scopeLabel: String
        switch item.scope {
        case "PART":
            scopeLabel = CatalogStrings.text("bundle.detail.partScope", locale: locale, bundle: bundle)
        case "SUBJECT":
            scopeLabel = CatalogStrings.text("bundle.detail.subjectScope", locale: locale, bundle: bundle)
        default:
            scopeLabel = item.scope
        }
        if !scopeLabel.isEmpty {
            parts.append(scopeLabel)
        }
        if let partCode = item.partCode?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
            parts.append(partCode)
        }
        return parts.joined(separator: " · ")
    }

    private static func makeNumberFormatter(locale: AppLocale, maxFractionDigits: Int) -> NumberFormatter {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = maxFractionDigits
        formatter.usesGroupingSeparator = true
        switch locale {
        case .vi:
            formatter.groupingSeparator = "."
            formatter.decimalSeparator = ","
        case .en:
            formatter.groupingSeparator = ","
            formatter.decimalSeparator = "."
        }
        return formatter
    }
}
