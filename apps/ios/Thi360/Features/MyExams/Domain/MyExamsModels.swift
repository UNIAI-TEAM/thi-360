import Foundation

struct MyExamLocalizedName: Equatable, Hashable, Sendable {
    let nameVi: String
    let nameEn: String

    func localized(for locale: AppLocale) -> String? {
        let vi = nameVi.trimmingCharacters(in: .whitespacesAndNewlines)
        let en = nameEn.trimmingCharacters(in: .whitespacesAndNewlines)
        switch locale {
        case .vi:
            if !vi.isEmpty { return vi }
            return en.isEmpty ? nil : en
        case .en:
            if !en.isEmpty { return en }
            return vi.isEmpty ? nil : vi
        }
    }
}

struct MyExamKind: Equatable, Hashable, Sendable {
    let code: String
    let name: MyExamLocalizedName

    func displayName(for locale: AppLocale) -> String {
        name.localized(for: locale) ?? code
    }
}

struct MyExamIntRange: Equatable, Hashable, Sendable {
    let min: Int
    let max: Int

    var formatted: String {
        if min == max {
            return String(min)
        }
        return "\(min)–\(max)"
    }
}

struct MyExamBundleRef: Equatable, Hashable, Sendable {
    let bundleId: String
    let code: String
    let name: String
}

struct MyExamCard: Equatable, Hashable, Identifiable, Sendable {
    let entitlementId: String?
    let code: String
    let name: String
    let examKind: MyExamKind
    let format: String
    let scopeLabel: MyExamLocalizedName?
    let buildMode: String
    let questionCount: MyExamIntRange?
    let durationMinutes: MyExamIntRange?
    let attemptsLeft: Int
    let attemptsTotal: Int
    let saleState: String
    let state: String
    let activeAttemptId: String?
    let activeAttemptState: String?
    let resultAttemptId: String?
    let hasPendingFulfillment: Bool
    let pendingOrderCode: String?
    let actions: [String]
    let bundles: [MyExamBundleRef]
    let entitlementSource: String?
    let classId: String?
    let className: String?

    var id: String {
        if let entitlementId = entitlementId?.trimmingCharacters(in: .whitespacesAndNewlines),
           !entitlementId.isEmpty
        {
            return entitlementId
        }
        if let orderCode = pendingOrderCode?.trimmingCharacters(in: .whitespacesAndNewlines),
           !orderCode.isEmpty
        {
            return "order:\(orderCode):\(code)"
        }
        return "card:\(state):\(code)"
    }

    var isChecking: Bool {
        state == "CHECKING"
    }

    var isClassAssigned: Bool {
        entitlementSource == "CLASS"
    }

    var isReview: Bool {
        buildMode == "REVIEW"
    }

    var trimmedClassName: String? {
        let value = className?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? nil : value
    }

    var trimmedPendingOrderCode: String? {
        let value = pendingOrderCode?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? nil : value
    }
}

struct BundleCrossReference: Equatable, Hashable, Identifiable, Sendable {
    let examCode: String
    let examName: String
    let primaryBundle: MyExamBundleRef

    var id: String { "\(examCode):\(primaryBundle.bundleId)" }
}

struct BundleGroup: Equatable, Hashable, Identifiable, Sendable {
    let ref: MyExamBundleRef
    let cards: [MyExamCard]
    let crossRefs: [BundleCrossReference]

    var id: String { ref.bundleId }

    var totalCount: Int {
        cards.count + crossRefs.count
    }
}

struct MyExamsGrouped: Equatable, Sendable {
    let checking: [MyExamCard]
    let bundleGroups: [BundleGroup]
    let standalone: [MyExamCard]
    let count: Int

    var showsStandaloneHeading: Bool {
        !bundleGroups.isEmpty && !standalone.isEmpty
    }
}

struct MyExamsUserIdentity: Equatable, Sendable {
    let fullName: String
    let username: String
}
