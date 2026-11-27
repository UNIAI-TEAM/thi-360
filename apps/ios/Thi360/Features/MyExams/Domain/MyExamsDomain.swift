import Foundation

enum MyExamsLayout {
    static func group(_ cards: [MyExamCard]) -> MyExamsGrouped {
        var checking: [MyExamCard] = []
        var standalone: [MyExamCard] = []
        var bundleOrder: [String] = []
        var bundleRefs: [String: MyExamBundleRef] = [:]
        var bundleCards: [String: [MyExamCard]] = [:]
        var bundleCrossRefs: [String: [BundleCrossReference]] = [:]

        func ensureGroup(_ ref: MyExamBundleRef) {
            if bundleRefs[ref.bundleId] == nil {
                bundleOrder.append(ref.bundleId)
                bundleRefs[ref.bundleId] = ref
                bundleCards[ref.bundleId] = []
                bundleCrossRefs[ref.bundleId] = []
            }
        }

        for card in cards {
            if card.isChecking {
                checking.append(card)
                continue
            }
            guard let primaryBundle = card.bundles.first else {
                standalone.append(card)
                continue
            }

            ensureGroup(primaryBundle)
            bundleCards[primaryBundle.bundleId, default: []].append(card)

            var seenBundleIds: Set<String> = [primaryBundle.bundleId]
            for secondary in card.bundles.dropFirst() {
                guard seenBundleIds.insert(secondary.bundleId).inserted else { continue }
                ensureGroup(secondary)
                bundleCrossRefs[secondary.bundleId, default: []].append(
                    BundleCrossReference(
                        examCode: card.code,
                        examName: card.name,
                        primaryBundle: primaryBundle
                    )
                )
            }
        }

        let groups = bundleOrder.compactMap { bundleId -> BundleGroup? in
            guard let ref = bundleRefs[bundleId] else { return nil }
            return BundleGroup(
                ref: ref,
                cards: bundleCards[bundleId] ?? [],
                crossRefs: bundleCrossRefs[bundleId] ?? []
            )
        }

        return MyExamsGrouped(
            checking: checking,
            bundleGroups: groups,
            standalone: standalone,
            count: cards.count
        )
    }
}

enum MyExamCardButtonAction: Equatable, Hashable, Sendable {
    case `continue`(attemptId: String)
    case start(entitlementId: String)
    case rebuy(code: String)
    case viewResult(attemptId: String)
    case history
    case buyMore(code: String)
}

struct MyExamCardButtons: Equatable, Sendable {
    let continueAttemptId: String?
    let startEntitlementId: String?
    let rebuyCode: String?
    let viewResultAttemptId: String?
    let showsHistory: Bool
    let buyMoreCode: String?

    var hasSecondaryRow: Bool {
        viewResultAttemptId != nil || showsHistory
    }

    var orderedActions: [MyExamCardButtonAction] {
        var result: [MyExamCardButtonAction] = []
        if let continueAttemptId {
            result.append(.continue(attemptId: continueAttemptId))
        } else if let startEntitlementId {
            result.append(.start(entitlementId: startEntitlementId))
        }
        if let rebuyCode {
            result.append(.rebuy(code: rebuyCode))
        }
        if let viewResultAttemptId {
            result.append(.viewResult(attemptId: viewResultAttemptId))
        }
        if showsHistory {
            result.append(.history)
        }
        if let buyMoreCode {
            result.append(.buyMore(code: buyMoreCode))
        }
        return result
    }
}

enum CardButtons {
    static func of(_ card: MyExamCard) -> MyExamCardButtons {
        let actionSet = Set(card.actions)
        let isOnSale = card.saleState == "ON_SALE"
        let canPurchase = !card.isClassAssigned && isOnSale && !card.hasPendingFulfillment

        let activeAttemptId = trimmedNonEmpty(card.activeAttemptId)
        let entitlementId = trimmedNonEmpty(card.entitlementId)
        let resultAttemptId = trimmedNonEmpty(card.resultAttemptId)

        let continueAttemptId = (actionSet.contains("CONTINUE") ? activeAttemptId : nil)
        let startEntitlementId = (continueAttemptId == nil && actionSet.contains("START")) ? entitlementId : nil
        let rebuyCode = (canPurchase && actionSet.contains("REBUY")) ? card.code : nil
        let viewResultAttemptId = (actionSet.contains("VIEW_RESULT") ? resultAttemptId : nil)
        let showsHistory = actionSet.contains("HISTORY")
        let buyMoreCode = (canPurchase && actionSet.contains("BUY_MORE")) ? card.code : nil

        return MyExamCardButtons(
            continueAttemptId: continueAttemptId,
            startEntitlementId: startEntitlementId,
            rebuyCode: rebuyCode,
            viewResultAttemptId: viewResultAttemptId,
            showsHistory: showsHistory,
            buyMoreCode: buyMoreCode
        )
    }

    private static func trimmedNonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}

enum MyExamMiddleNote: Equatable, Hashable, Sendable {
    case lastAttempt
    case classExhaustedWithHistory
    case classExhausted
    case discontinuedExhaustedWithHistory
    case discontinuedExhausted
    case discontinuedCanStart

    var localizationKey: String {
        switch self {
        case .lastAttempt: "home.notes.lastAttempt"
        case .classExhaustedWithHistory: "home.notes.classExhaustedWithHistory"
        case .classExhausted: "home.notes.classExhausted"
        case .discontinuedExhaustedWithHistory: "home.notes.discontinuedExhaustedWithHistory"
        case .discontinuedExhausted: "home.notes.discontinuedExhausted"
        case .discontinuedCanStart: "home.notes.discontinuedCanStart"
        }
    }
}

struct MyExamCardNotes: Equatable, Sendable {
    let middleNotes: [MyExamMiddleNote]
    let pendingTopUpOrderCode: String?
}

enum CardNotes {
    static func of(_ card: MyExamCard) -> MyExamCardNotes {
        let buttons = CardButtons.of(card)
        let hasResultOrHistory = buttons.hasSecondaryRow
        let isOnSale = card.saleState == "ON_SALE"

        var middle: [MyExamMiddleNote] = []
        if card.state == "IN_PROGRESS" && card.attemptsLeft == 0 {
            middle.append(.lastAttempt)
        }
        if card.isClassAssigned && card.state == "EXHAUSTED" {
            middle.append(hasResultOrHistory ? .classExhaustedWithHistory : .classExhausted)
        }
        if !card.isClassAssigned && !isOnSale && card.state == "EXHAUSTED" {
            middle.append(hasResultOrHistory ? .discontinuedExhaustedWithHistory : .discontinuedExhausted)
        }
        if !card.isClassAssigned && !isOnSale && (card.state == "CAN_START" || card.state == "IN_PROGRESS") {
            middle.append(.discontinuedCanStart)
        }

        let pendingTopUpOrderCode: String?
        if card.hasPendingFulfillment, let orderCode = card.trimmedPendingOrderCode {
            pendingTopUpOrderCode = orderCode
        } else {
            pendingTopUpOrderCode = nil
        }

        return MyExamCardNotes(
            middleNotes: middle,
            pendingTopUpOrderCode: pendingTopUpOrderCode
        )
    }
}

struct CheckingCardMessage: Equatable, Sendable {
    let messageKey: String
    let text: String
    let viewOrderCode: String?
}

enum CheckingMessage {
    static func formatSupportSummary(_ support: SupportContact) -> String? {
        let channels = [support.phone, support.email]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        let hours = support.hours.trimmingCharacters(in: .whitespacesAndNewlines)

        if channels.isEmpty {
            return hours.isEmpty ? nil : hours
        }
        return hours.isEmpty ? channels : "\(channels) (\(hours))"
    }

    static func of(
        _ card: MyExamCard,
        support: SupportContact,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> CheckingCardMessage {
        let orderCode = card.trimmedPendingOrderCode
        let contactText = formatSupportSummary(support)
        let pending = support.pendingMessage(locale: locale, bundle: bundle)

        let key: String
        let text: String
        switch (orderCode, contactText) {
        case let (code?, contact?):
            key = "home.checking.message"
            text = MyExamsStrings.text(
                key,
                locale: locale,
                replacements: ["orderCode": code, "contact": contact],
                bundle: bundle
            )
        case let (code?, nil):
            key = "home.checking.messageNoContact"
            text = MyExamsStrings.text(
                key,
                locale: locale,
                replacements: ["orderCode": code, "pending": pending],
                bundle: bundle
            )
        case let (nil, contact?):
            key = "home.checking.messageNoOrder"
            text = MyExamsStrings.text(
                key,
                locale: locale,
                replacements: ["contact": contact],
                bundle: bundle
            )
        case (nil, nil):
            key = "home.checking.messageNoOrderNoContact"
            text = MyExamsStrings.text(
                key,
                locale: locale,
                replacements: ["pending": pending],
                bundle: bundle
            )
        }

        return CheckingCardMessage(
            messageKey: key,
            text: text,
            viewOrderCode: orderCode
        )
    }
}

enum MyExamsFormatting {
    static let vietnamTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!

    static func resolveGreetingName(fullName: String?, username: String?) -> String {
        if let cleanFull = fullName?.trimmingCharacters(in: .whitespacesAndNewlines), !cleanFull.isEmpty {
            return cleanFull
        }
        if let cleanUser = username?.trimmingCharacters(in: .whitespacesAndNewlines), !cleanUser.isEmpty {
            return cleanUser
        }
        return ""
    }

    static func formatTimeHm(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = vietnamTimeZone
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    static func cardSpecsLine(for card: MyExamCard, locale: AppLocale, bundle: Bundle = .main) -> String? {
        var parts: [String] = []
        if let q = card.questionCount?.formatted {
            parts.append(
                AppStrings.common(
                    "storefront.units.questions",
                    locale: locale,
                    replacements: ["value": q],
                    bundle: bundle
                )
            )
        }
        if let m = card.durationMinutes?.formatted {
            parts.append(
                MyExamsStrings.text(
                    "home.card.minutesPerAttempt",
                    locale: locale,
                    replacements: ["value": m],
                    bundle: bundle
                )
            )
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}
