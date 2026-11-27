import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class PaymentResultViewModel {
    private(set) var state: PaymentResultState
    private(set) var currentOrderCode: String?
    private(set) var verifiedFromLink: Bool = false
    private(set) var isRetryingPay: Bool = false
    private(set) var isCheckingAgain: Bool = false

    @ObservationIgnored private var openedFromChecking: Bool = false
    @ObservationIgnored private var activeReturnLink: PaymentReturnLink?
    @ObservationIgnored private var verifiedPaymentState: String?
    @ObservationIgnored private var verifiedTxnRef: String?
    @ObservationIgnored private var verifiedResponseCode: String?
    @ObservationIgnored private var hasTimedOut: Bool = false
    @ObservationIgnored private var lastLoadedOrder: Components.Schemas.OrderDetail?
    @ObservationIgnored private var hasStarted: Bool = false
    @ObservationIgnored private var initialEntry: PaymentResultEntry
    @ObservationIgnored private var payOperationsByOrder: [String: IdempotentOperation] = [:]

    private let gateway: any PaymentGateway
    private let paymentBrowser: any PaymentBrowser
    private let pendingOrderStore: PendingPaymentOrderStore
    private let poller: OrderPoller
    private let apiBaseURL: URL
    private let clock: any AppClock
    private let usernameProvider: @MainActor () -> String?
    private let keyGenerator: @Sendable () -> String
    private let onReturnCallback: @MainActor (URL) -> Void

    init(
        initialEntry: PaymentResultEntry,
        gateway: any PaymentGateway,
        paymentBrowser: any PaymentBrowser,
        pendingOrderStore: PendingPaymentOrderStore,
        poller: OrderPoller? = nil,
        apiBaseURL: URL,
        clock: any AppClock = SystemClock(),
        usernameProvider: @escaping @MainActor () -> String? = { nil },
        keyGenerator: @escaping @Sendable () -> String = { UUID().uuidString },
        onReturnCallback: @escaping @MainActor (URL) -> Void = { _ in }
    ) {
        self.initialEntry = initialEntry
        self.gateway = gateway
        self.paymentBrowser = paymentBrowser
        self.pendingOrderStore = pendingOrderStore
        self.poller = poller ?? OrderPoller(clock: clock)
        self.apiBaseURL = apiBaseURL
        self.clock = clock
        self.usernameProvider = usernameProvider
        self.keyGenerator = keyGenerator
        self.onReturnCallback = onReturnCallback

        switch initialEntry {
        case let .byOrder(orderCode, fromLink, fromChecking):
            self.currentOrderCode = orderCode.isEmpty ? nil : orderCode
            self.verifiedFromLink = fromLink
            self.openedFromChecking = fromChecking
            self.state = .loading
        case let .byReturnLink(link):
            self.activeReturnLink = link
            self.state = .verifying
        }
    }

    func startIfNeeded() async {
        guard !hasStarted else { return }
        hasStarted = true
        await processEntry(initialEntry)
    }

    func acceptEntry(_ entry: PaymentResultEntry) async {
        switch entry {
        case let .byReturnLink(link):
            if activeReturnLink == link && hasStarted {
                return
            }
            hasStarted = true
            poller.stop()
            activeReturnLink = link
            hasTimedOut = false
            await verifyAndLoad(link: link)

        case let .byOrder(orderCode, fromLink, fromChecking):
            let trimmed = orderCode.trimmingCharacters(in: .whitespacesAndNewlines)
            if hasStarted && currentOrderCode == trimmed {
                if case .loading = state {
                    return
                }
                if case .verifying = state {
                    return
                }
            }
            hasStarted = true
            poller.stop()
            currentOrderCode = trimmed
            verifiedFromLink = fromLink
            if !fromLink {
                verifiedPaymentState = nil
                verifiedTxnRef = nil
                verifiedResponseCode = nil
            }
            openedFromChecking = openedFromChecking || fromChecking
            hasTimedOut = false
            await loadOrder(code: trimmed, isPoll: false)
        }
    }

    func retryLoad() async {
        switch state {
        case let .loadError(canRetryVerify) where canRetryVerify:
            if let link = activeReturnLink {
                await verifyAndLoad(link: link)
            }
        default:
            if let code = currentOrderCode, !code.isEmpty {
                hasTimedOut = false
                await loadOrder(code: code, isPoll: false)
            } else if let link = activeReturnLink {
                await verifyAndLoad(link: link)
            }
        }
    }

    func checkAgainNow() async {
        guard let code = currentOrderCode, !code.isEmpty, !isCheckingAgain else { return }
        isCheckingAgain = true
        hasTimedOut = false
        defer { isCheckingAgain = false }

        if let order = lastLoadedOrder {
            if case .noResultYet(_, _, _, let err) = state {
                state = .noResultYet(order: order, timedOut: false, pollError: false, retryPayError: err)
            } else if case .checking = state {
                state = .checking(order: order, timedOut: false, pollError: false)
            }
        }

        let directive = await pollOnce(code: code)
        if directive == .continue {
            poller.restartWindow(
                onPoll: { [weak self] in
                    guard let self, let activeCode = self.currentOrderCode else { return .stop }
                    return await self.pollOnce(code: activeCode)
                },
                onTimeout: { [weak self] in
                    self?.handlePollTimeout()
                }
            )
        }
    }

    func retryPay() async {
        guard let order = activeOrderForRetryPay, !isRetryingPay else { return }
        isRetryingPay = true
        defer { isRetryingPay = false }

        let operation = payOperation(for: order.code)
        do {
            guard let response = try await operation.run({ key in
                try await self.gateway.payOrder(orderCode: order.code, idempotencyKey: key)
            }) else {
                return
            }
            guard let resolvedURL = PaymentURLResolver.resolve(response.paymentUrl, apiBaseURL: apiBaseURL) else {
                applyRetryPayError(.generic)
                return
            }
            poller.stop()
            await pendingOrderStore.save(orderCode: order.code, openedAt: clock.now)
            let outcome = await paymentBrowser.open(resolvedURL)
            switch outcome {
            case let .callback(callbackURL):
                onReturnCallback(callbackURL)
            case .dismissed:
                verifiedFromLink = false
                verifiedPaymentState = nil
                verifiedTxnRef = nil
                verifiedResponseCode = nil
                hasTimedOut = false
                await loadOrder(code: order.code, isPoll: false)
            case .openedFallback:
                break
            case .failedToOpen:
                await pendingOrderStore.clearIfMatches(orderCode: order.code)
                applyRetryPayError(.generic)
            }
        } catch {
            let errKey = OrderActionErrorMapper.map(MobileApiError.classify(error))
            applyRetryPayError(errKey)
            await loadOrder(code: order.code, isPoll: false, preservingRetryError: errKey)
        }
    }

    var isPollingActive: Bool {
        poller.isRunning
    }

    func stopPolling() {
        poller.stop()
    }

    func onScenePhaseChanged(_ phase: ScenePhase) {
        poller.setScenePhase(phase)
    }

    func onLeaveScreen(clearPendingOrder: Bool = true) async {
        poller.stop()
        if clearPendingOrder {
            if let code = currentOrderCode, !code.isEmpty {
                await pendingOrderStore.clearIfMatches(orderCode: code)
            } else {
                await pendingOrderStore.clear()
            }
        }
    }

    private var activeOrderForRetryPay: Components.Schemas.OrderDetail? {
        switch state {
        case let .failed(order, _, _, _), let .noResultYet(order, _, _, _):
            return order
        default:
            return nil
        }
    }

    private func payOperation(for orderCode: String) -> IdempotentOperation {
        if let existing = payOperationsByOrder[orderCode] {
            return existing
        }
        let created = IdempotentOperation(keyGenerator: keyGenerator)
        payOperationsByOrder[orderCode] = created
        return created
    }

    private func processEntry(_ entry: PaymentResultEntry) async {
        switch entry {
        case let .byReturnLink(link):
            await verifyAndLoad(link: link)
        case let .byOrder(orderCode, fromLink, fromChecking):
            verifiedFromLink = fromLink
            openedFromChecking = fromChecking
            let trimmed = orderCode.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else {
                state = .loadError(canRetryVerify: false)
                return
            }
            currentOrderCode = trimmed
            await loadOrder(code: trimmed, isPoll: false)
        }
    }

    private func verifyAndLoad(link: PaymentReturnLink) async {
        if lastLoadedOrder == nil {
            state = .verifying
        }
        let result = await gateway.verifyVnpayReturn(vnpParams: link.vnpParams)
        switch result {
        case let .success(verified):
            currentOrderCode = verified.orderCode
            verifiedFromLink = true
            verifiedPaymentState = verified.paymentState.uppercased()
            verifiedTxnRef = link.txnRef
            verifiedResponseCode = link.responseCode
            await loadOrder(code: verified.orderCode, isPoll: false)

        case .invalidSignature:
            poller.stop()
            state = .invalidSignature

        case .orderNotFound:
            poller.stop()
            state = .verifyNotFound

        case .failure:
            poller.stop()
            state = .loadError(canRetryVerify: true)
        }
    }

    private func loadOrder(
        code: String,
        isPoll: Bool,
        preservingRetryError: OrderActionErrorKey? = nil
    ) async {
        if !isPoll && lastLoadedOrder == nil {
            state = .loading
        }
        do {
            let order = try await gateway.getOrder(orderCode: code)
            lastLoadedOrder = order
            let shouldPoll = await applyOrder(order, preservingRetryError: preservingRetryError)
            if shouldPoll && !hasTimedOut {
                poller.start(
                    onPoll: { [weak self] in
                        guard let self, let activeCode = self.currentOrderCode else { return .stop }
                        return await self.pollOnce(code: activeCode)
                    },
                    onTimeout: { [weak self] in
                        self?.handlePollTimeout()
                    }
                )
            } else {
                poller.stop()
            }
        } catch {
            let classified = MobileApiError.classify(error)
            if classified.statusCode == 404 || classified.apiCode == "ORDER_NOT_FOUND" {
                poller.stop()
                state = .notOwned(orderCode: code, username: usernameProvider())
                return
            }
            if let existing = lastLoadedOrder {
                switch state {
                case .checking:
                    state = .checking(order: existing, timedOut: hasTimedOut, pollError: true)
                case let .noResultYet(_, _, _, retryErr):
                    state = .noResultYet(
                        order: existing,
                        timedOut: hasTimedOut,
                        pollError: true,
                        retryPayError: preservingRetryError ?? retryErr
                    )
                default:
                    if !isPoll {
                        state = .loadError(canRetryVerify: false)
                    }
                }
            } else {
                poller.stop()
                state = .loadError(canRetryVerify: false)
            }
        }
    }

    private func pollOnce(code: String) async -> OrderPollDirective {
        do {
            let order = try await gateway.getOrder(orderCode: code)
            lastLoadedOrder = order
            let shouldContinue = await applyOrder(order, preservingRetryError: nil)
            return shouldContinue ? .continue : .stop
        } catch {
            let classified = MobileApiError.classify(error)
            if classified.statusCode == 404 || classified.apiCode == "ORDER_NOT_FOUND" {
                state = .notOwned(orderCode: code, username: usernameProvider())
                return .stop
            }
            if let existing = lastLoadedOrder {
                switch state {
                case .checking:
                    state = .checking(order: existing, timedOut: hasTimedOut, pollError: true)
                case let .noResultYet(_, _, _, retryErr):
                    state = .noResultYet(order: existing, timedOut: hasTimedOut, pollError: true, retryPayError: retryErr)
                default:
                    break
                }
            }
            return .continue
        }
    }

    private func applyOrder(
        _ order: Components.Schemas.OrderDetail,
        preservingRetryError: OrderActionErrorKey?
    ) async -> Bool {
        switch order.state {
        case .FULFILLED:
            await pendingOrderStore.clearIfMatches(orderCode: order.code)
            state = .fulfilled(order)
            return false

        case .EXPIRED, .CANCELLED, .REPLACED, .REFUNDED:
            await pendingOrderStore.clearIfMatches(orderCode: order.code)
            state = .closed(order)
            return false

        case .PAID:
            state = .checking(order: order, timedOut: hasTimedOut, pollError: false)
            return true

        case .PENDING_PAYMENT:
            let latest = PaymentFormatters.latestPayment(in: order)
            let matchesLatestTxn: Bool
            if let latest, let vnpTxn = verifiedTxnRef {
                matchesLatestTxn = (latest.txnRef == vnpTxn)
            } else {
                matchesLatestTxn = (latest == nil)
            }
            let verifiedFailed = verifiedFromLink && verifiedPaymentState == "FAILED" && matchesLatestTxn
            if latest?.state == .FAILED || verifiedFailed {
                await pendingOrderStore.clearIfMatches(orderCode: order.code)
                let reasonCode: String?
                if latest?.state == .FAILED {
                    reasonCode = latest?.responseCode
                } else {
                    reasonCode = latest?.responseCode ?? verifiedResponseCode
                }
                state = .failed(
                    order: order,
                    reasonCode: reasonCode,
                    txnRef: latest?.txnRef ?? verifiedTxnRef,
                    retryPayError: preservingRetryError
                )
                return false
            }
            if verifiedFromLink || openedFromChecking {
                state = .checking(order: order, timedOut: hasTimedOut, pollError: false)
                return true
            }
            state = .noResultYet(
                order: order,
                timedOut: hasTimedOut,
                pollError: false,
                retryPayError: preservingRetryError
            )
            return true
        }
    }

    private func handlePollTimeout() {
        hasTimedOut = true
        switch state {
        case let .checking(order, _, pollError):
            state = .checking(order: order, timedOut: true, pollError: pollError)
        case let .noResultYet(order, _, pollError, retryPayError):
            state = .noResultYet(order: order, timedOut: true, pollError: pollError, retryPayError: retryPayError)
        default:
            break
        }
    }

    private func applyRetryPayError(_ errorKey: OrderActionErrorKey) {
        switch state {
        case let .failed(order, reasonCode, txnRef, _):
            state = .failed(order: order, reasonCode: reasonCode, txnRef: txnRef, retryPayError: errorKey)
        case let .noResultYet(order, timedOut, pollError, _):
            state = .noResultYet(order: order, timedOut: timedOut, pollError: pollError, retryPayError: errorKey)
        default:
            break
        }
    }
}
