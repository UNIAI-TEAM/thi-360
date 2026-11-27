import Foundation
import Observation

struct OrdersActionError: Equatable, Sendable {
    let code: OrderActionErrorKey
    let productCode: String
}

@Observable
@MainActor
final class OrdersViewModel {
    static let pageSize = 20

    private(set) var state: LoadState<[Components.Schemas.OrderSummary]> = .loading
    private(set) var lastUpdatedAt: Date?
    private(set) var isRefreshing: Bool = false
    private(set) var isLoadingNextPage: Bool = false
    private(set) var currentPage: Int = 0
    private(set) var totalPages: Int = 0
    private(set) var totalItems: Int64 = 0

    private(set) var payingOrderCode: String?
    private(set) var actionError: OrdersActionError?
    var cancelTarget: Components.Schemas.OrderSummary?
    private(set) var isCancelling: Bool = false
    private(set) var cancelErrorKey: OrderActionErrorKey?
    private(set) var toastOrderCode: String?
    var selectedOrderCode: String?

    @ObservationIgnored private var cachedItems: [Components.Schemas.OrderSummary] = []
    @ObservationIgnored private var hasLoadedOnce = false
    @ObservationIgnored private var isActionInFlight = false
    @ObservationIgnored private var payOperations: [String: IdempotentOperation] = [:]
    @ObservationIgnored private var cancelOperations: [String: IdempotentOperation] = [:]
    @ObservationIgnored private var toastDismissTask: Task<Void, Never>?

    private let gateway: any PaymentGateway
    private let paymentBrowser: any PaymentBrowser
    private let pendingOrderStore: PendingPaymentOrderStore
    private let apiBaseURL: URL
    private let clock: any AppClock
    let usernameProvider: @MainActor () -> String?
    private let keyGenerator: @Sendable () -> String
    private let onOpenPaymentResult: @MainActor (String) -> Void
    private let onReturnCallback: @MainActor (URL) -> Void

    init(
        gateway: any PaymentGateway,
        paymentBrowser: any PaymentBrowser,
        pendingOrderStore: PendingPaymentOrderStore,
        apiBaseURL: URL,
        clock: any AppClock = SystemClock(),
        usernameProvider: @escaping @MainActor () -> String? = { nil },
        keyGenerator: @escaping @Sendable () -> String = { UUID().uuidString },
        onOpenPaymentResult: @escaping @MainActor (String) -> Void = { _ in },
        onReturnCallback: @escaping @MainActor (URL) -> Void = { _ in }
    ) {
        self.gateway = gateway
        self.paymentBrowser = paymentBrowser
        self.pendingOrderStore = pendingOrderStore
        self.apiBaseURL = apiBaseURL
        self.clock = clock
        self.usernameProvider = usernameProvider
        self.keyGenerator = keyGenerator
        self.onOpenPaymentResult = onOpenPaymentResult
        self.onReturnCallback = onReturnCallback
    }

    var hasMorePages: Bool {
        currentPage + 1 < totalPages
    }

    var anyActionRunning: Bool {
        isActionInFlight || payingOrderCode != nil || isCancelling
    }

    var username: String {
        usernameProvider() ?? ""
    }

    func loadInitialIfNeeded() async {
        guard !hasLoadedOnce else { return }
        hasLoadedOnce = true
        await loadFirstPage(isRefresh: false)
    }

    func reloadFirstPage() async {
        await loadFirstPage(isRefresh: !cachedItems.isEmpty)
    }

    func loadFirstPage(isRefresh: Bool) async {
        hasLoadedOnce = true
        if isRefresh {
            isRefreshing = true
        } else if cachedItems.isEmpty {
            state = .loading
        }
        defer { isRefreshing = false }

        do {
            let pageResponse = try await gateway.listOrders(page: 0, size: Self.pageSize)
            currentPage = Int(pageResponse.page)
            totalPages = Int(pageResponse.totalPages)
            totalItems = pageResponse.totalItems
            cachedItems = pageResponse.items
            lastUpdatedAt = clock.now
            if cachedItems.isEmpty {
                state = .empty
            } else {
                state = .loaded(value: cachedItems, stale: false)
            }
        } catch {
            let classified = LoadFailureKind.classify(MobileApiError.classify(error))
            if !cachedItems.isEmpty {
                state = .loaded(value: cachedItems, stale: true)
            } else {
                state = .failed(kind: classified.kind, canRetry: true, retryAfter: classified.retryAfter)
            }
        }
    }

    func loadNextPageIfNeeded() async {
        guard hasMorePages, !isLoadingNextPage, !isRefreshing, !cachedItems.isEmpty else {
            return
        }
        isLoadingNextPage = true
        defer { isLoadingNextPage = false }

        let nextPage = currentPage + 1
        do {
            let response = try await gateway.listOrders(page: nextPage, size: Self.pageSize)
            currentPage = Int(response.page)
            totalPages = Int(response.totalPages)
            totalItems = response.totalItems
            let existingIDs = Set(cachedItems.map(\.id))
            let newItems = response.items.filter { !existingIDs.contains($0.id) }
            cachedItems.append(contentsOf: newItems)
            lastUpdatedAt = clock.now
            state = .loaded(value: cachedItems, stale: false)
        } catch {
            state = .loaded(value: cachedItems, stale: true)
        }
    }

    func continuePayment(for order: Components.Schemas.OrderSummary) async {
        guard !anyActionRunning else { return }
        isActionInFlight = true
        payingOrderCode = order.code
        actionError = nil
        defer {
            isActionInFlight = false
            payingOrderCode = nil
        }

        let operation = payOperation(for: order.code)
        do {
            guard let response = try await operation.run({ key in
                try await self.gateway.payOrder(orderCode: order.code, idempotencyKey: key)
            }) else {
                return
            }
            applyUpdatedOrderDetail(response.order)
            guard let resolvedURL = PaymentURLResolver.resolve(response.paymentUrl, apiBaseURL: apiBaseURL) else {
                actionError = OrdersActionError(code: .generic, productCode: order.productCode)
                return
            }
            await pendingOrderStore.save(orderCode: order.code, openedAt: clock.now)
            let outcome = await paymentBrowser.open(resolvedURL)
            switch outcome {
            case let .callback(callbackURL):
                onReturnCallback(callbackURL)
            case .dismissed:
                onOpenPaymentResult(order.code)
            case .openedFallback:
                break
            case .failedToOpen:
                await pendingOrderStore.clearIfMatches(orderCode: order.code)
                actionError = OrdersActionError(code: .generic, productCode: order.productCode)
            }
        } catch {
            let errKey = OrderActionErrorMapper.map(MobileApiError.classify(error))
            actionError = OrdersActionError(code: errKey, productCode: order.productCode)
            await loadFirstPage(isRefresh: true)
        }
    }

    func requestCancel(for order: Components.Schemas.OrderSummary) {
        guard !anyActionRunning else { return }
        cancelErrorKey = nil
        cancelTarget = order
    }

    func dismissCancelDialog() {
        guard !isCancelling else { return }
        if cancelErrorKey == nil, let code = cancelTarget?.code {
            let op = cancelOperation(for: code)
            Task { await op.reset() }
        }
        cancelTarget = nil
        cancelErrorKey = nil
    }

    func confirmCancel() async {
        guard let target = cancelTarget, !anyActionRunning else { return }
        isActionInFlight = true
        isCancelling = true
        cancelErrorKey = nil
        defer {
            isActionInFlight = false
            isCancelling = false
        }

        let operation = cancelOperation(for: target.code)
        do {
            guard let updated = try await operation.run({ key in
                try await self.gateway.cancelOrder(orderCode: target.code, idempotencyKey: key)
            }) else {
                return
            }
            applyUpdatedOrderDetail(updated)
            cancelTarget = nil
            cancelErrorKey = nil
            showCancelToast(orderCode: target.code)
            await loadFirstPage(isRefresh: true)
        } catch {
            let errKey = OrderActionErrorMapper.map(MobileApiError.classify(error))
            cancelErrorKey = errKey
            await loadFirstPage(isRefresh: true)
        }
    }

    func applyUpdatedOrderDetail(_ detail: Components.Schemas.OrderDetail) {
        guard let idx = cachedItems.firstIndex(where: { $0.code == detail.code || $0.id == detail.id }) else {
            return
        }
        let current = cachedItems[idx]
        let mappedState = Components.Schemas.OrderSummary.statePayload(rawValue: detail.state.rawValue) ?? current.state
        let mappedMethod = Components.Schemas.OrderSummary.paymentMethodPayload(rawValue: detail.paymentMethod.rawValue) ?? current.paymentMethod
        let mappedProductType = Components.Schemas.OrderSummary.productTypePayload(rawValue: detail.productType.rawValue) ?? current.productType
        let mappedDiscount = Components.Schemas.OrderSummary.discountAppliedPayload(rawValue: detail.discountApplied.rawValue) ?? current.discountApplied
        cachedItems[idx] = Components.Schemas.OrderSummary(
            amountDueVnd: detail.amountDueVnd,
            attemptsGranted: detail.attemptsGranted,
            bundleId: detail.bundleId,
            code: detail.code,
            createdAt: detail.createdAt,
            discountApplied: mappedDiscount,
            examId: detail.examId,
            expiresAt: detail.expiresAt,
            fulfilledAt: detail.fulfilledAt,
            id: detail.id,
            listPriceVnd: detail.listPriceVnd,
            paidAt: detail.paidAt,
            paymentMethod: mappedMethod,
            productCode: detail.productCode,
            productName: detail.productName,
            productType: mappedProductType,
            promotionDiscountVnd: detail.promotionDiscountVnd,
            state: mappedState,
            voucherDiscountVnd: detail.voucherDiscountVnd
        )
        state = .loaded(value: cachedItems, stale: false)
    }

    func dismissToast() {
        toastDismissTask?.cancel()
        toastDismissTask = nil
        toastOrderCode = nil
    }

    private func showCancelToast(orderCode: String) {
        toastDismissTask?.cancel()
        toastOrderCode = orderCode
        toastDismissTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            if !Task.isCancelled {
                self?.toastOrderCode = nil
            }
        }
    }

    private func payOperation(for orderCode: String) -> IdempotentOperation {
        if let existing = payOperations[orderCode] {
            return existing
        }
        let created = IdempotentOperation(keyGenerator: keyGenerator)
        payOperations[orderCode] = created
        return created
    }

    private func cancelOperation(for orderCode: String) -> IdempotentOperation {
        if let existing = cancelOperations[orderCode] {
            return existing
        }
        let created = IdempotentOperation(keyGenerator: keyGenerator)
        cancelOperations[orderCode] = created
        return created
    }
}

@Observable
@MainActor
final class OrderDetailViewModel {
    let orderCode: String

    private(set) var state: LoadState<Components.Schemas.OrderDetail> = .loading
    private(set) var lastUpdatedAt: Date?
    private(set) var isRefreshing: Bool = false
    private(set) var isNotFound: Bool = false
    private(set) var isPaying: Bool = false
    private(set) var actionError: OrdersActionError?
    var isShowingCancelDialog: Bool = false
    private(set) var isCancelling: Bool = false
    private(set) var cancelErrorKey: OrderActionErrorKey?
    private(set) var toastOrderCode: String?

    @ObservationIgnored private var cachedOrder: Components.Schemas.OrderDetail?
    @ObservationIgnored private var hasLoadedOnce = false
    @ObservationIgnored private var isActionInFlight = false
    @ObservationIgnored private let payOperation: IdempotentOperation
    @ObservationIgnored private let cancelOperation: IdempotentOperation
    @ObservationIgnored private var toastDismissTask: Task<Void, Never>?

    private let gateway: any PaymentGateway
    private let paymentBrowser: any PaymentBrowser
    private let pendingOrderStore: PendingPaymentOrderStore
    private let apiBaseURL: URL
    private let clock: any AppClock
    private let onOrderMutated: @MainActor (Components.Schemas.OrderDetail) -> Void
    private let onOpenPaymentResult: @MainActor (String) -> Void
    private let onReturnCallback: @MainActor (URL) -> Void

    init(
        orderCode: String,
        gateway: any PaymentGateway,
        paymentBrowser: any PaymentBrowser,
        pendingOrderStore: PendingPaymentOrderStore,
        apiBaseURL: URL,
        clock: any AppClock = SystemClock(),
        keyGenerator: @escaping @Sendable () -> String = { UUID().uuidString },
        onOrderMutated: @escaping @MainActor (Components.Schemas.OrderDetail) -> Void = { _ in },
        onOpenPaymentResult: @escaping @MainActor (String) -> Void = { _ in },
        onReturnCallback: @escaping @MainActor (URL) -> Void = { _ in }
    ) {
        self.orderCode = orderCode
        self.gateway = gateway
        self.paymentBrowser = paymentBrowser
        self.pendingOrderStore = pendingOrderStore
        self.apiBaseURL = apiBaseURL
        self.clock = clock
        self.payOperation = IdempotentOperation(keyGenerator: keyGenerator)
        self.cancelOperation = IdempotentOperation(keyGenerator: keyGenerator)
        self.onOrderMutated = onOrderMutated
        self.onOpenPaymentResult = onOpenPaymentResult
        self.onReturnCallback = onReturnCallback
    }

    var loadedOrder: Components.Schemas.OrderDetail? {
        if case let .loaded(value, _) = state {
            return value
        }
        return cachedOrder
    }

    var anyActionRunning: Bool {
        isActionInFlight || isPaying || isCancelling
    }

    func loadInitialIfNeeded() async {
        guard !hasLoadedOnce else { return }
        hasLoadedOnce = true
        await load(isRefresh: false)
    }

    func reload() async {
        await load(isRefresh: cachedOrder != nil)
    }

    func load(isRefresh: Bool) async {
        hasLoadedOnce = true
        if isRefresh {
            isRefreshing = true
        } else if cachedOrder == nil {
            state = .loading
        }
        defer { isRefreshing = false }

        do {
            let detail = try await gateway.getOrder(orderCode: orderCode)
            cachedOrder = detail
            isNotFound = false
            lastUpdatedAt = clock.now
            state = .loaded(value: detail, stale: false)
            onOrderMutated(detail)
        } catch {
            let apiError = MobileApiError.classify(error)
            if apiError.statusCode == 404 || apiError.apiCode == "ORDER_NOT_FOUND" {
                isNotFound = true
                cachedOrder = nil
                state = .failed(kind: .notFound, canRetry: false, retryAfter: nil)
                return
            }
            let classified = LoadFailureKind.classify(apiError)
            if let cachedOrder {
                state = .loaded(value: cachedOrder, stale: true)
            } else {
                state = .failed(kind: classified.kind, canRetry: true, retryAfter: classified.retryAfter)
            }
        }
    }

    func continuePayment() async {
        guard let order = loadedOrder, !anyActionRunning else { return }
        isActionInFlight = true
        isPaying = true
        actionError = nil
        defer {
            isActionInFlight = false
            isPaying = false
        }

        do {
            guard let response = try await payOperation.run({ key in
                try await self.gateway.payOrder(orderCode: order.code, idempotencyKey: key)
            }) else {
                return
            }
            cachedOrder = response.order
            state = .loaded(value: response.order, stale: false)
            onOrderMutated(response.order)

            guard let resolvedURL = PaymentURLResolver.resolve(response.paymentUrl, apiBaseURL: apiBaseURL) else {
                actionError = OrdersActionError(code: .generic, productCode: order.productCode)
                return
            }
            await pendingOrderStore.save(orderCode: order.code, openedAt: clock.now)
            let outcome = await paymentBrowser.open(resolvedURL)
            switch outcome {
            case let .callback(callbackURL):
                onReturnCallback(callbackURL)
            case .dismissed:
                onOpenPaymentResult(order.code)
            case .openedFallback:
                break
            case .failedToOpen:
                await pendingOrderStore.clearIfMatches(orderCode: order.code)
                actionError = OrdersActionError(code: .generic, productCode: order.productCode)
            }
        } catch {
            let errKey = OrderActionErrorMapper.map(MobileApiError.classify(error))
            actionError = OrdersActionError(code: errKey, productCode: order.productCode)
            await load(isRefresh: true)
        }
    }

    func requestCancel() {
        guard !anyActionRunning else { return }
        cancelErrorKey = nil
        isShowingCancelDialog = true
    }

    func dismissCancelDialog() {
        guard !isCancelling else { return }
        if cancelErrorKey == nil {
            Task { await cancelOperation.reset() }
        }
        isShowingCancelDialog = false
        cancelErrorKey = nil
    }

    func confirmCancel() async {
        guard let order = loadedOrder, !anyActionRunning else { return }
        isActionInFlight = true
        isCancelling = true
        cancelErrorKey = nil
        defer {
            isActionInFlight = false
            isCancelling = false
        }

        do {
            guard let updated = try await cancelOperation.run({ key in
                try await self.gateway.cancelOrder(orderCode: order.code, idempotencyKey: key)
            }) else {
                return
            }
            cachedOrder = updated
            lastUpdatedAt = clock.now
            state = .loaded(value: updated, stale: false)
            onOrderMutated(updated)
            isShowingCancelDialog = false
            cancelErrorKey = nil
            showCancelToast(orderCode: order.code)
        } catch {
            let errKey = OrderActionErrorMapper.map(MobileApiError.classify(error))
            cancelErrorKey = errKey
            actionError = OrdersActionError(code: errKey, productCode: order.productCode)
            await load(isRefresh: true)
        }
    }

    func dismissToast() {
        toastDismissTask?.cancel()
        toastDismissTask = nil
        toastOrderCode = nil
    }

    private func showCancelToast(orderCode: String) {
        toastDismissTask?.cancel()
        toastOrderCode = orderCode
        toastDismissTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            if !Task.isCancelled {
                self?.toastOrderCode = nil
            }
        }
    }
}
