import Foundation
import Observation

enum PaymentResultTarget: Hashable, Sendable {
    case byOrder(orderCode: String)
    // In-memory only: never persisted across state restoration or disk.
    case byReturnLink(URL)

    var displayValue: String {
        switch self {
        case let .byOrder(orderCode):
            "orderCode=\(orderCode)"
        case let .byReturnLink(url):
            "returnLink=\(url.path)"
        }
    }
}

enum AppRoute: Hashable, Sendable {
    case catalog
    case product(code: String)
    case checkout(code: String, seenVersionNo: Int? = nil, seenPrice: Int? = nil)
    case paymentResult(PaymentResultTarget)
    case orders
    case order(orderCode: String)
    case myExams
    case history(resetFilters: Bool = false)
    case prepare(entitlementId: String)
    case attempt(attemptId: String)
    case result(attemptId: String)
    case review(attemptId: String)
    case profile
    case profileVerifyEmail
    case deleteAccount
    case legal(LegalId)

    var nameKey: String {
        switch self {
        case .catalog: "shell.route.name.catalog"
        case .product: "shell.route.name.product"
        case .checkout: "shell.route.name.checkout"
        case .paymentResult: "shell.route.name.paymentResult"
        case .orders: "shell.route.name.orders"
        case .order: "shell.route.name.order"
        case .myExams: "shell.route.name.myExams"
        case .history: "shell.route.name.history"
        case .prepare: "shell.route.name.prepare"
        case .attempt: "shell.route.name.attempt"
        case .result: "shell.route.name.result"
        case .review: "shell.route.name.review"
        case .profile: "shell.route.name.profile"
        case .profileVerifyEmail: "shell.route.name.profileVerifyEmail"
        case .deleteAccount: "shell.route.name.deleteAccount"
        case .legal: "shell.route.name.legal"
        }
    }

    var logicalName: String {
        switch self {
        case .catalog: "catalog"
        case .product: "product"
        case .checkout: "checkout"
        case .paymentResult: "payment-result"
        case .orders: "orders"
        case .order: "order"
        case .myExams: "my-exams"
        case .history: "history"
        case .prepare: "prepare"
        case .attempt: "attempt"
        case .result: "result"
        case .review: "review"
        case .profile: "profile"
        case .profileVerifyEmail: "profile/verify-email"
        case .deleteAccount: "account/delete"
        case .legal: "legal"
        }
    }

    var parameterSummary: [(key: String, value: String)] {
        switch self {
        case .catalog, .orders, .myExams, .profile, .profileVerifyEmail, .deleteAccount:
            return []
        case let .product(code):
            return [("code", code)]
        case let .checkout(code, seenVersionNo, seenPrice):
            var items: [(String, String)] = [("code", code)]
            if let seenVersionNo {
                items.append(("seenVersionNo", String(seenVersionNo)))
            }
            if let seenPrice {
                items.append(("seenPrice", String(seenPrice)))
            }
            return items
        case let .paymentResult(target):
            switch target {
            case let .byOrder(orderCode):
                return [("orderCode", orderCode)]
            case let .byReturnLink(url):
                return [("returnLink", url.host ?? url.path)]
            }
        case let .order(orderCode):
            return [("orderCode", orderCode)]
        case let .history(resetFilters):
            return [("resetFilters", resetFilters ? "true" : "false")]
        case let .prepare(entitlementId):
            return [("entitlementId", entitlementId)]
        case let .attempt(attemptId), let .result(attemptId), let .review(attemptId):
            return [("attemptId", attemptId)]
        case let .legal(id):
            return [("id", id.rawValue)]
        }
    }

    var accessibilityIdentifier: String {
        "route.\(logicalName.replacingOccurrences(of: "/", with: "."))"
    }

    var isAttemptFlowRoute: Bool {
        switch self {
        case .attempt, .prepare:
            true
        default:
            false
        }
    }

    static func decode(path rawPath: String) -> AppRoute? {
        let trimmed = rawPath.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        let normalized = trimmed.hasPrefix("/") ? trimmed : "/\(trimmed)"
        guard let components = URLComponents(string: "https://route.local\(normalized)") else {
            return nil
        }
        let segments = components.path
            .split(separator: "/", omittingEmptySubsequences: false)
            .dropFirst()
            .map(String.init)
        guard !segments.isEmpty else { return nil }

        let queryMap: [String: String] = (components.queryItems ?? []).reduce(into: [:]) { acc, item in
            if let value = item.value {
                acc[item.name] = value
            }
        }

        switch (segments.count, segments.first, segments.count > 1 ? segments[1] : nil) {
        case (1, "catalog", _):
            return queryMap.isEmpty ? .catalog : nil
        case (2, "product", let code?) where !code.isEmpty:
            return .product(code: code)
        case (2, "checkout", let code?) where !code.isEmpty:
            let versionNo: Int?
            if let raw = queryMap["seenVersionNo"] {
                guard let parsed = Int(raw) else { return nil }
                versionNo = parsed
            } else {
                versionNo = nil
            }
            let price: Int?
            if let raw = queryMap["seenPrice"] {
                guard let parsed = Int(raw) else { return nil }
                price = parsed
            } else {
                price = nil
            }
            return .checkout(code: code, seenVersionNo: versionNo, seenPrice: price)
        case (2, "payment-result", let orderCode?) where !orderCode.isEmpty:
            return .paymentResult(.byOrder(orderCode: orderCode))
        case (1, "payment-result", _):
            if let orderCode = queryMap["orderCode"], !orderCode.isEmpty {
                return .paymentResult(.byOrder(orderCode: orderCode))
            }
            return nil
        case (1, "orders", _):
            return .orders
        case (2, "order", let orderCode?) where !orderCode.isEmpty:
            return .order(orderCode: orderCode)
        case (1, "my-exams", _):
            return .myExams
        case (1, "history", _):
            if let raw = queryMap["resetFilters"] {
                switch raw.lowercased() {
                case "true": return .history(resetFilters: true)
                case "false": return .history(resetFilters: false)
                default: return nil
                }
            }
            return .history(resetFilters: false)
        case (2, "prepare", let entitlementId?) where !entitlementId.isEmpty:
            return .prepare(entitlementId: entitlementId)
        case (2, "attempt", let attemptId?) where !attemptId.isEmpty:
            return .attempt(attemptId: attemptId)
        case (2, "result", let attemptId?) where !attemptId.isEmpty:
            return .result(attemptId: attemptId)
        case (2, "review", let attemptId?) where !attemptId.isEmpty:
            return .review(attemptId: attemptId)
        case (1, "profile", _):
            return .profile
        case (2, "profile", "verify-email"):
            return .profileVerifyEmail
        case (2, "account", "delete"):
            return .deleteAccount
        case (2, "legal", let rawId?):
            guard let id = LegalId(rawValue: rawId) else { return nil }
            return .legal(id)
        default:
            return nil
        }
    }
}

struct AttemptRef: Hashable, Sendable {
    let attemptId: String
    let originDestination: Destination
}

struct RouteStateKey: Hashable, Sendable {
    let destination: Destination
    let route: AppRoute
}

struct RouteScreenState: Equatable, Sendable {
    var draftText: String = ""
    var scrollRowIndex: Int = 0
}

@Observable
@MainActor
final class RouteStateStore {
    private var states: [RouteStateKey: RouteScreenState] = [:]

    func state(for destination: Destination, route: AppRoute) -> RouteScreenState {
        states[RouteStateKey(destination: destination, route: route)] ?? RouteScreenState()
    }

    func setDraftText(_ text: String, for destination: Destination, route: AppRoute) {
        let key = RouteStateKey(destination: destination, route: route)
        var current = states[key] ?? RouteScreenState()
        current.draftText = text
        states[key] = current
    }

    func setScrollRowIndex(_ index: Int, for destination: Destination, route: AppRoute) {
        let key = RouteStateKey(destination: destination, route: route)
        var current = states[key] ?? RouteScreenState()
        current.scrollRowIndex = index
        states[key] = current
    }

    func removeState(for destination: Destination, route: AppRoute) {
        states.removeValue(forKey: RouteStateKey(destination: destination, route: route))
    }

    func removeStates(for destination: Destination, notIn activeRoutes: Set<AppRoute>) {
        states = states.filter { key, _ in
            key.destination != destination || activeRoutes.contains(key.route)
        }
    }

    func removeAll() {
        states.removeAll()
    }

    var count: Int {
        states.count
    }
}
