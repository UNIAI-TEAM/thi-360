import Foundation

struct SeenPrice: Equatable, Hashable, Sendable {
    let versionNo: Int?
    let price: Int64?

    init(versionNo: Int? = nil, price: Int64? = nil) {
        self.versionNo = versionNo.flatMap { $0 >= 1 ? $0 : nil }
        self.price = price.flatMap { $0 >= 0 ? $0 : nil }
    }
}

enum VoucherErrorKey: String, Equatable, Hashable, Sendable, CaseIterable {
    case rateLimited = "RATE_LIMITED"
    case voucherExhausted = "VOUCHER_EXHAUSTED"
    case voucherExpired = "VOUCHER_EXPIRED"
    case voucherInvalid = "VOUCHER_INVALID"
    case voucherNotApplicable = "VOUCHER_NOT_APPLICABLE"
    case voucherNotEligible = "VOUCHER_NOT_ELIGIBLE"
    case voucherUserLimit = "VOUCHER_USER_LIMIT"
    case generic = "generic"

    func messageKey(isBundle: Bool) -> String {
        if self == .voucherNotApplicable && isBundle {
            return "checkout.voucher.errors.VOUCHER_NOT_APPLICABLE_BUNDLE"
        }
        return "checkout.voucher.errors.\(rawValue)"
    }
}

enum VoucherAppliedVariant: Equatable, Sendable {
    case applied
    case combined
    case replacedPromo
}

enum VoucherFeedback: Equatable, Sendable {
    case empty
    case error(VoucherErrorKey)
    case notBetter(code: String)
    case applied(variant: VoucherAppliedVariant, code: String, discountVnd: Int64)
}

struct VoucherBoxState: Equatable, Sendable {
    var codeInput: String = ""
    var isSubmitting: Bool = false
    var feedback: VoucherFeedback? = nil
}

enum CheckoutSubmitError: Equatable, Sendable {
    case rateLimited
    case paymentAttemptsExceeded
    case voucher(VoucherErrorKey)
    case generic
}

enum CheckoutSubmitState: Equatable, Sendable {
    case idle
    case submitting
    case failed(CheckoutSubmitError)
}

struct AccountConflict: Equatable, Sendable {
    let previousUsername: String
    let currentUser: Components.Schemas.MeResponse
}

struct BundleItemMeta: Codable, Equatable, Hashable, Sendable {
    let subjectName: Components.Schemas.LocalizedName?
    let questionCount: Components.Schemas.IntRange?
    let durationMinutes: Components.Schemas.IntRange?
    let maxPoints: Components.Schemas.DecimalRange?
}

struct LoadedProductDetail: Equatable, Hashable, Sendable {
    let detail: Components.Schemas.ProductDetail
    let bundleItemMetaByCode: [String: BundleItemMeta]

    var isBundle: Bool {
        detail.productType?.uppercased() == "BUNDLE" || detail.format.uppercased() == "BUNDLE"
    }

    var bundleItems: [Components.Schemas.BundleDetailItem] {
        isBundle ? (detail.items ?? []) : []
    }
}

struct PaymentReturnLink: Equatable, Hashable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    static let returnPath = "/checkout/result/app"

    let vnpParams: [String: String]

    init(vnpParams: [String: String]) {
        self.vnpParams = vnpParams
    }

    var txnRef: String? {
        let value = vnpParams["vnp_TxnRef"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (value?.isEmpty == false) ? value : nil
    }

    var responseCode: String? {
        let value = vnpParams["vnp_ResponseCode"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (value?.isEmpty == false) ? value : nil
    }

    var description: String {
        "PaymentReturnLink(paramCount: \(vnpParams.count), params: <redacted>)"
    }

    var debugDescription: String {
        description
    }

    static func parse(
        _ url: URL,
        environment: AppEnvironment = .current
    ) -> PaymentReturnLink? {
        guard let allowedHost = environment.webBaseURL.host?.lowercased(), !allowedHost.isEmpty else {
            return nil
        }
        return parse(url, allowedWebHost: allowedHost, environmentKind: environment.kind)
    }

    static func parse(
        _ url: URL,
        allowedWebHost: String,
        environmentKind: BuildEnvironment
    ) -> PaymentReturnLink? {
        guard !allowedWebHost.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.user == nil,
              components.password == nil,
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased(),
              host == allowedWebHost.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        else {
            return nil
        }

        switch environmentKind {
        case .demo:
            guard scheme == "https" else { return nil }
        case .dev:
            guard scheme == "https" || scheme == "http" else { return nil }
        }

        guard components.path == returnPath else {
            return nil
        }

        let extracted = extractVnpParams(from: components.percentEncodedQuery)
        guard !extracted.isEmpty else {
            return nil
        }
        return PaymentReturnLink(vnpParams: extracted)
    }

    static func extractVnpParams(from percentEncodedQuery: String?) -> [String: String] {
        guard let percentEncodedQuery, !percentEncodedQuery.isEmpty else {
            return [:]
        }
        var params: [String: String] = [:]
        for pair in percentEncodedQuery.split(separator: "&", omittingEmptySubsequences: true) {
            let parts = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard let rawKey = parts.first else { continue }
            let rawValue = parts.count > 1 ? String(parts[1]) : ""
            guard let key = decodeFormComponent(String(rawKey)), key.hasPrefix("vnp_") else {
                continue
            }
            let value = decodeFormComponent(rawValue) ?? ""
            params[key] = value
        }
        return params
    }

    private static func decodeFormComponent(_ raw: String) -> String? {
        raw.replacingOccurrences(of: "+", with: " ").removingPercentEncoding
    }
}

enum PaymentResultEntry: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    case byOrder(orderCode: String, verifiedFromLink: Bool = false, openedFromChecking: Bool = false)
    case byReturnLink(PaymentReturnLink)

    var description: String {
        switch self {
        case let .byOrder(orderCode, verifiedFromLink, openedFromChecking):
            "PaymentResultEntry.byOrder(orderCode: \(orderCode), verifiedFromLink: \(verifiedFromLink), openedFromChecking: \(openedFromChecking))"
        case .byReturnLink:
            "PaymentResultEntry.byReturnLink(<redacted>)"
        }
    }

    var debugDescription: String {
        description
    }
}

enum OrderActionErrorKey: String, Equatable, Hashable, Sendable, CaseIterable {
    case orderExpired = "ORDER_EXPIRED"
    case orderNotFound = "ORDER_NOT_FOUND"
    case orderNotPending = "ORDER_NOT_PENDING"
    case paymentAttemptsExceeded = "PAYMENT_ATTEMPTS_EXCEEDED"
    case productDiscontinued = "PRODUCT_DISCONTINUED"
    case productNotFound = "PRODUCT_NOT_FOUND"
    case rateLimited = "RATE_LIMITED"
    case generic = "generic"

    var messageKey: String {
        "orders.errors.\(rawValue)"
    }
}

enum PaymentResultState: Equatable, Sendable {
    case verifying
    case loading
    case fulfilled(Components.Schemas.OrderDetail)
    case failed(
        order: Components.Schemas.OrderDetail,
        reasonCode: String?,
        txnRef: String?,
        retryPayError: OrderActionErrorKey?
    )
    case checking(order: Components.Schemas.OrderDetail, timedOut: Bool, pollError: Bool)
    case noResultYet(
        order: Components.Schemas.OrderDetail,
        timedOut: Bool,
        pollError: Bool,
        retryPayError: OrderActionErrorKey?
    )
    case closed(Components.Schemas.OrderDetail)
    case invalidSignature
    case verifyNotFound
    case notOwned(orderCode: String, username: String?)
    case loadError(canRetryVerify: Bool)
}

enum PaymentBrowserOutcome: Equatable, Sendable {
    case callback(URL)
    case dismissed
    case openedFallback
    case failedToOpen
}

@MainActor
protocol PaymentBrowser: AnyObject {
    func open(_ url: URL) async -> PaymentBrowserOutcome
}

enum PaymentURLResolver {
    static func resolve(_ rawURL: String, apiBaseURL: URL) -> URL? {
        let trimmed = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let lowered = trimmed.lowercased()
        if lowered.hasPrefix("https://") || lowered.hasPrefix("http://") {
            return URL(string: trimmed)
        }
        guard !trimmed.contains("://"),
              let resolved = URL(string: trimmed, relativeTo: apiBaseURL)?.absoluteURL,
              let scheme = resolved.scheme?.lowercased(),
              scheme == "https" || scheme == "http"
        else {
            return nil
        }
        return resolved
    }
}

struct PendingPaymentOrder: Codable, Equatable, Sendable {
    static let ttl: TimeInterval = 15 * 60

    let orderCode: String
    let openedAt: Date

    func isExpired(at now: Date) -> Bool {
        now.timeIntervalSince(openedAt) > Self.ttl
    }
}

final class PendingPaymentOrderStore: Sendable {
    static let namespace = "payment"
    static let key = "pending_order"

    private let storeProvider: AccountScopedStoreProvider
    private let accountIdProvider: @Sendable () -> String?

    init(
        storeProvider: AccountScopedStoreProvider,
        accountIdProvider: @escaping @Sendable () -> String?
    ) {
        self.storeProvider = storeProvider
        self.accountIdProvider = accountIdProvider
    }

    func save(orderCode: String, openedAt: Date) async {
        let trimmed = orderCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              let store = storeProvider.store(for: accountIdProvider())
        else {
            return
        }
        let record = PendingPaymentOrder(orderCode: trimmed, openedAt: openedAt)
        guard let data = try? JSONEncoder().encode(record) else { return }
        try? await store.write(data, namespace: Self.namespace, key: Self.key)
    }

    func readValid(now: Date) async -> PendingPaymentOrder? {
        guard let store = storeProvider.store(for: accountIdProvider()),
              let data = await store.read(namespace: Self.namespace, key: Self.key)
        else {
            return nil
        }
        guard let record = try? JSONDecoder().decode(PendingPaymentOrder.self, from: data),
              !record.orderCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !record.isExpired(at: now)
        else {
            try? await store.delete(namespace: Self.namespace, key: Self.key)
            return nil
        }
        return record
    }

    func clear() async {
        guard let store = storeProvider.store(for: accountIdProvider()) else { return }
        try? await store.delete(namespace: Self.namespace, key: Self.key)
    }

    func clearIfMatches(orderCode: String) async {
        guard let store = storeProvider.store(for: accountIdProvider()),
              let data = await store.read(namespace: Self.namespace, key: Self.key)
        else {
            return
        }
        if let record = try? JSONDecoder().decode(PendingPaymentOrder.self, from: data),
           record.orderCode != orderCode
        {
            return
        }
        try? await store.delete(namespace: Self.namespace, key: Self.key)
    }
}

struct PendingPaymentCleanupParticipant: SessionCleanupParticipant {
    let id = "payment.pendingOrder"
    private let storeProvider: AccountScopedStoreProvider
    private let onClearMemory: @Sendable () async -> Void

    init(
        storeProvider: AccountScopedStoreProvider,
        onClearMemory: @escaping @Sendable () async -> Void = {}
    ) {
        self.storeProvider = storeProvider
        self.onClearMemory = onClearMemory
    }

    func onLogout(accountId: String) async throws {
        await onClearMemory()
        if let store = storeProvider.store(for: accountId) {
            try? await store.deleteNamespace(PendingPaymentOrderStore.namespace)
        }
    }
}
