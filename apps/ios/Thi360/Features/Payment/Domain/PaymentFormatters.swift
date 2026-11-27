import Foundation
import OpenAPIRuntime

enum PaymentStatusTone: Equatable, Sendable {
    case success
    case warning
    case info
    case danger
    case neutral
}

struct OrderBenefitsSnapshot: Equatable, Sendable {
    let aiExplanation: Bool
    let strengthWeakness: Bool
    let practice: Bool
    let note: String?

    static func parse(from container: OpenAPIObjectContainer) -> OrderBenefitsSnapshot {
        let dict = container.value
        let ai = (dict["aiExplanation"] as? Bool) == true
        let sw = (dict["strengthWeakness"] as? Bool) == true
        let pr = (dict["practice"] as? Bool) == true
        let rawNote = (dict["note"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let note = (rawNote?.isEmpty == false) ? rawNote : nil
        return OrderBenefitsSnapshot(
            aiExplanation: ai,
            strengthWeakness: sw,
            practice: pr,
            note: note
        )
    }
}

enum PaymentFormatters {
    static let vietnamTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? TimeZone(secondsFromGMT: 7 * 3600)!

    static func formatDateTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = vietnamTimeZone
        formatter.dateFormat = "dd/MM/yyyy HH:mm"
        return formatter.string(from: date)
    }

    static func formatTimeOnly(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = vietnamTimeZone
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    static func formatVnd(_ amount: Int64, locale: AppLocale) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = (locale == .vi) ? "." : ","
        formatter.decimalSeparator = (locale == .vi) ? "," : "."
        formatter.maximumFractionDigits = 0
        let formattedNumber = formatter.string(from: NSNumber(value: amount)) ?? String(amount)
        return PaymentStrings.text(
            "payment.price",
            locale: locale,
            replacements: ["amount": formattedNumber]
        )
    }

    static func formatIntRange(_ range: Components.Schemas.IntRange?) -> String? {
        guard let range else { return nil }
        if range.min == range.max {
            return String(range.min)
        }
        return "\(range.min)–\(range.max)"
    }

    static func formatDecimalRange(_ range: Components.Schemas.DecimalRange?, locale: AppLocale) -> String? {
        guard let range else { return nil }
        let minText = formatDecimal(range.min, locale: locale)
        let maxText = formatDecimal(range.max, locale: locale)
        if minText == maxText {
            return minText
        }
        return "\(minText)–\(maxText)"
    }

    static func formatDecimal(_ value: Double, locale: AppLocale) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        formatter.groupingSeparator = (locale == .vi) ? "." : ","
        formatter.decimalSeparator = (locale == .vi) ? "," : "."
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }

    static func localizedName(_ name: Components.Schemas.LocalizedName?, locale: AppLocale) -> String? {
        guard let name else { return nil }
        let primary = (locale == .vi ? name.nameVi : name.nameEn).trimmingCharacters(in: .whitespacesAndNewlines)
        if !primary.isEmpty { return primary }
        let fallback = (locale == .vi ? name.nameEn : name.nameVi).trimmingCharacters(in: .whitespacesAndNewlines)
        return fallback.isEmpty ? nil : fallback
    }

    static func examKindLabel(_ kind: Components.Schemas.ExamKindRef, locale: AppLocale) -> String {
        let primary = (locale == .vi ? kind.nameVi : kind.nameEn).trimmingCharacters(in: .whitespacesAndNewlines)
        if !primary.isEmpty { return primary }
        let fallback = (locale == .vi ? kind.nameEn : kind.nameVi).trimmingCharacters(in: .whitespacesAndNewlines)
        return fallback.isEmpty ? kind.code : fallback
    }

    static func resultViewLabel(_ raw: String?, locale: AppLocale) -> String? {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        switch trimmed {
        case "SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION":
            return PaymentStrings.text("payment.detail.resultViews.\(trimmed)", locale: locale)
        default:
            return trimmed
        }
    }

    static func formatSupportSummary(_ contact: SupportContact) -> String? {
        let channels = [contact.phone, contact.email]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        let hours = contact.hours.trimmingCharacters(in: .whitespacesAndNewlines)
        if channels.isEmpty {
            return hours.isEmpty ? nil : hours
        }
        return hours.isEmpty ? channels : "\(channels) (\(hours))"
    }

    static func formatOrderListAmount(
        paymentMethod: Components.Schemas.OrderSummary.paymentMethodPayload,
        amountDueVnd: Int64,
        voucherDiscountVnd: Int64,
        locale: AppLocale
    ) -> String {
        if paymentMethod == .FREE || amountDueVnd == 0 {
            if voucherDiscountVnd > 0 {
                return PaymentStrings.text("orders.amount.freeVoucher", locale: locale)
            }
            return PaymentStrings.text("orders.amount.freeAmount", locale: locale)
        }
        return formatVnd(amountDueVnd, locale: locale)
    }

    static func formatOrderDetailAmount(
        order: Components.Schemas.OrderDetail,
        locale: AppLocale
    ) -> String {
        if order.paymentMethod == .FREE || order.amountDueVnd == 0 {
            if order.voucherDiscountVnd > 0 {
                return PaymentStrings.text("orders.amount.freeVoucher", locale: locale)
            }
            return PaymentStrings.text("orders.amount.freeAmount", locale: locale)
        }
        return formatVnd(order.amountDueVnd, locale: locale)
    }

    static func formatPaymentMethodBadge(
        paymentMethod: Components.Schemas.OrderDetail.paymentMethodPayload,
        voucherDiscountVnd: Int64,
        locale: AppLocale
    ) -> String {
        switch paymentMethod {
        case .FREE:
            if voucherDiscountVnd > 0 {
                return PaymentStrings.text("orders.amount.freeVoucherNote", locale: locale)
            }
            return PaymentStrings.text("orders.amount.free", locale: locale)
        case .VNPAY:
            return PaymentStrings.text("orders.amount.vnpay", locale: locale)
        }
    }

    static func orderStateTone(_ state: String) -> PaymentStatusTone {
        switch state {
        case "PENDING_PAYMENT", "PAID":
            return .warning
        case "FULFILLED":
            return .success
        case "REFUNDED":
            return .info
        default:
            return .neutral
        }
    }

    static func orderStateLabel(_ rawState: String, locale: AppLocale) -> String {
        switch rawState {
        case "PENDING_PAYMENT", "PAID", "FULFILLED", "EXPIRED", "REPLACED", "CANCELLED", "REFUNDED":
            return PaymentStrings.text("orders.states.\(rawState)", locale: locale)
        default:
            return rawState
        }
    }

    static func paymentStateTone(_ state: Components.Schemas.OrderPaymentView.statePayload) -> PaymentStatusTone {
        switch state {
        case .CREATED:
            return .neutral
        case .SUCCEEDED:
            return .success
        case .FAILED:
            return .danger
        case .UNKNOWN:
            return .warning
        }
    }

    static func paymentStateLabel(_ state: Components.Schemas.OrderPaymentView.statePayload, locale: AppLocale) -> String {
        PaymentStrings.text("orders.detail.payments.states.\(state.rawValue)", locale: locale)
    }

    static func formatBankAndCard(bankCode: String?, cardType: String?) -> String {
        let parts = [bankCode, cardType]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return parts.isEmpty ? "—" : parts.joined(separator: " · ")
    }

    static func formatAttemptSequence(_ seq: Int32) -> String {
        String(format: "%02d", Int(seq))
    }

    static func latestPayment(in order: Components.Schemas.OrderDetail) -> Components.Schemas.OrderPaymentView? {
        order.payments.max(by: { $0.attemptSeq < $1.attemptSeq })
    }
}

enum VoucherErrorMapper {
    private static let knownVoucherCodes: Set<String> = [
        "RATE_LIMITED",
        "VOUCHER_EXHAUSTED",
        "VOUCHER_EXPIRED",
        "VOUCHER_INVALID",
        "VOUCHER_NOT_APPLICABLE",
        "VOUCHER_NOT_ELIGIBLE",
        "VOUCHER_USER_LIMIT"
    ]

    static func map(statusCode: Int?, apiCode: String?) -> VoucherErrorKey {
        if statusCode == 429 || apiCode == "RATE_LIMITED" {
            return .rateLimited
        }
        if let apiCode, knownVoucherCodes.contains(apiCode), let key = VoucherErrorKey(rawValue: apiCode) {
            return key
        }
        return .generic
    }

    static func map(_ error: MobileApiError) -> VoucherErrorKey {
        map(statusCode: error.statusCode, apiCode: error.apiCode)
    }
}

enum OrderActionErrorMapper {
    static func map(statusCode: Int?, apiCode: String?) -> OrderActionErrorKey {
        if statusCode == 429 || apiCode == "RATE_LIMITED" {
            return .rateLimited
        }
        if statusCode == 410 || apiCode == "PRODUCT_DISCONTINUED" {
            return .productDiscontinued
        }
        if statusCode == 404 && apiCode == "PRODUCT_NOT_FOUND" {
            return .productNotFound
        }
        if statusCode == 404 || apiCode == "ORDER_NOT_FOUND" {
            return .orderNotFound
        }
        switch apiCode {
        case "ORDER_EXPIRED":
            return .orderExpired
        case "ORDER_NOT_PENDING":
            return .orderNotPending
        case "PAYMENT_ATTEMPTS_EXCEEDED":
            return .paymentAttemptsExceeded
        default:
            return .generic
        }
    }

    static func map(_ error: MobileApiError) -> OrderActionErrorKey {
        switch error {
        case let .api(statusCode, payload):
            return map(statusCode: statusCode, apiCode: payload.code)
        case let .unexpectedResponse(statusCode):
            return map(statusCode: statusCode, apiCode: nil)
        case .network:
            return .generic
        }
    }
}

enum VnpayReasonMapper {
    private static let knownCodes: Set<String> = [
        "07", "09", "10", "11", "12", "13", "24", "51", "65", "75", "79", "99"
    ]

    static func reasonKey(for responseCode: String?) -> String {
        let trimmed = responseCode?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if knownCodes.contains(trimmed) {
            return "orders.vnpayReasons.\(trimmed)"
        }
        return "orders.vnpayReasons.generic"
    }

    static func message(for responseCode: String?, locale: AppLocale) -> String {
        PaymentStrings.text(reasonKey(for: responseCode), locale: locale)
    }
}

enum OrderHistoryMapper {
    private static let knownActors: Set<String> = [
        "LEARNER", "SYSTEM", "VNPAY_IPN", "ADMIN"
    ]

    private static let knownReasons: Set<String> = [
        "ORDER_CREATED",
        "FREE_ORDER_PAID",
        "PAYMENT_ATTEMPT_CREATED",
        "REUSE_PENDING_PAYMENT",
        "REPLACED_BY",
        "CANCELLED_BY_USER",
        "PAYMENT_SUCCEEDED",
        "FULFILLED",
        "EXCESS_AFTER_COUNTED",
        "AUTO_EXCESS_AFTER_COUNTED",
        "LATE_SUCCESS_PENDING_REVIEW",
        "DOUBLE_CHARGE_REVIEW",
        "DOUBLE_CHARGE_REPLACED_ORDER",
        "LATE_PAYMENT_RECLAIMED",
        "LATE_PAYMENT_REPLACED_RECLAIMED",
        "LATE_PAYMENT_REPLACED_DOUBLE_CHARGE",
        "EXPIRED",
        "REFUNDED"
    ]

    static func actorLabel(_ rawActor: String, locale: AppLocale) -> String {
        let normalized = rawActor.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let key = knownActors.contains(normalized) ? normalized : "unknown"
        return PaymentStrings.text("orders.detail.history.actors.\(key)", locale: locale)
    }

    static func parseReason(_ rawReason: String) -> (key: String, ref: String) {
        let trimmed = rawReason.trimmingCharacters(in: .whitespacesAndNewlines)
        if let colonIndex = trimmed.firstIndex(of: ":") {
            let prefix = String(trimmed[..<colonIndex]).trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let ref = String(trimmed[trimmed.index(after: colonIndex)...]).trimmingCharacters(in: .whitespacesAndNewlines)
            if knownReasons.contains(prefix) {
                return (prefix, ref)
            }
            return ("unknown", ref)
        }
        let upper = trimmed.uppercased()
        if knownReasons.contains(upper) {
            return (upper, "")
        }
        return ("unknown", "")
    }

    static func reasonLabel(_ rawReason: String, locale: AppLocale) -> String {
        let parsed = parseReason(rawReason)
        if parsed.key == "unknown" {
            return PaymentStrings.text("orders.detail.history.reasons.unknown", locale: locale)
        }
        return PaymentStrings.text(
            "orders.detail.history.reasons.\(parsed.key)",
            locale: locale,
            replacements: ["ref": parsed.ref.isEmpty ? "—" : parsed.ref]
        )
    }

    static func transitionLabel(fromState: String?, toState: String, locale: AppLocale) -> String {
        guard let fromState, !fromState.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return PaymentStrings.text("orders.detail.history.created", locale: locale)
        }
        return PaymentStrings.text(
            "orders.detail.history.transition",
            locale: locale,
            replacements: [
                "from": PaymentFormatters.orderStateLabel(fromState, locale: locale),
                "to": PaymentFormatters.orderStateLabel(toState, locale: locale)
            ]
        )
    }
}
