import Foundation

enum AccessButtonKind: Equatable, Hashable, Sendable {
    case `continue`(attemptId: String?)
    case enter(entitlementId: String?)
    case buy(code: String, seenVersionNo: Int, seenPrice: Int)
    case buyMore(code: String, seenVersionNo: Int, seenPrice: Int)
    case buyAgain(code: String, seenVersionNo: Int, seenPrice: Int)
    case checking(orderCode: String?)
}

enum AccessNoteKind: Equatable, Hashable, Sendable {
    case continueInProgress
    case attemptsLeft(Int)
    case exhausted
    case pendingTopUp(orderCode: String)
    case checkingOrder(orderCode: String)
}

struct AccessButtonPlan: Equatable, Hashable, Sendable {
    let leadNote: AccessNoteKind?
    let primary: AccessButtonKind
    let secondary: AccessButtonKind?
    let trailingNote: AccessNoteKind?
    let isBundle: Bool
    let showsViewDetail: Bool

    var allButtons: [AccessButtonKind] {
        if let secondary {
            return [primary, secondary]
        }
        return [primary]
    }
}

enum AccessButtons {
    static func forCard(
        row: ExamAccessRow,
        isBundle: Bool,
        seenVersionNo: Int = 0,
        seenPrice: Int = 0
    ) -> AccessButtonPlan {
        buildPlan(
            row: row,
            isBundle: isBundle,
            seenVersionNo: seenVersionNo,
            seenPrice: seenPrice,
            isPricePanel: false
        )
    }

    static func forPricePanel(
        row: ExamAccessRow,
        seenVersionNo: Int = 0,
        seenPrice: Int = 0
    ) -> AccessButtonPlan {
        buildPlan(
            row: row,
            isBundle: false,
            seenVersionNo: seenVersionNo,
            seenPrice: seenPrice,
            isPricePanel: true
        )
    }

    private static func buildPlan(
        row: ExamAccessRow,
        isBundle: Bool,
        seenVersionNo: Int,
        seenPrice: Int,
        isPricePanel: Bool
    ) -> AccessButtonPlan {
        let buyMore: AccessButtonKind? = row.hasPendingFulfillment
            ? nil
            : .buyMore(code: row.code, seenVersionNo: seenVersionNo, seenPrice: seenPrice)
        let pendingTopUpNote: AccessNoteKind? = row.hasPendingFulfillment
            ? .pendingTopUp(orderCode: row.pendingOrderCode ?? "")
            : nil
        let showsViewDetail = !isPricePanel

        switch row.state {
        case .inProgress:
            return AccessButtonPlan(
                leadNote: .continueInProgress,
                primary: .continue(attemptId: row.activeAttemptId?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty),
                secondary: buyMore,
                trailingNote: pendingTopUpNote,
                isBundle: isBundle,
                showsViewDetail: showsViewDetail
            )

        case .canStart:
            return AccessButtonPlan(
                leadNote: .attemptsLeft(row.attemptsLeft),
                primary: .enter(entitlementId: row.entitlementId?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty),
                secondary: buyMore,
                trailingNote: pendingTopUpNote,
                isBundle: isBundle,
                showsViewDetail: showsViewDetail
            )

        case .exhausted:
            if isPricePanel {
                return AccessButtonPlan(
                    leadNote: .exhausted,
                    primary: .buyAgain(code: row.code, seenVersionNo: seenVersionNo, seenPrice: seenPrice),
                    secondary: nil,
                    trailingNote: nil,
                    isBundle: false,
                    showsViewDetail: false
                )
            }
            return AccessButtonPlan(
                leadNote: nil,
                primary: .buy(code: row.code, seenVersionNo: seenVersionNo, seenPrice: seenPrice),
                secondary: nil,
                trailingNote: nil,
                isBundle: isBundle,
                showsViewDetail: true
            )

        case .checking:
            let cleanOrderCode = row.pendingOrderCode?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            return AccessButtonPlan(
                leadNote: nil,
                primary: .checking(orderCode: cleanOrderCode),
                secondary: nil,
                trailingNote: cleanOrderCode.map { .checkingOrder(orderCode: $0) },
                isBundle: isBundle,
                showsViewDetail: showsViewDetail
            )

        case .none:
            return AccessButtonPlan(
                leadNote: nil,
                primary: .buy(code: row.code, seenVersionNo: seenVersionNo, seenPrice: seenPrice),
                secondary: nil,
                trailingNote: nil,
                isBundle: isBundle,
                showsViewDetail: showsViewDetail
            )
        }
    }
}

@MainActor
final class CatalogNavigationDebouncer {
    private let clock: any AppClock
    private let minimumInterval: TimeInterval
    private var lastTriggerDate: Date?
    private var lastTriggerKey: String?

    init(clock: any AppClock = SystemClock(), minimumInterval: TimeInterval = 0.5) {
        self.clock = clock
        self.minimumInterval = minimumInterval
    }

    func shouldAllow(key: String) -> Bool {
        let now = clock.now
        if let lastDate = lastTriggerDate,
           lastTriggerKey == key,
           now.timeIntervalSince(lastDate) < minimumInterval
        {
            return false
        }
        lastTriggerDate = now
        lastTriggerKey = key
        return true
    }

    func reset() {
        lastTriggerDate = nil
        lastTriggerKey = nil
    }
}
