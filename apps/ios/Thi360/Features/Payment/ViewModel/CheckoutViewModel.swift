import Foundation
import Observation

@Observable
@MainActor
final class CheckoutViewModel {
    let code: String
    let seenPrice: SeenPrice

    private(set) var baselineUser: Components.Schemas.MeResponse?
    private(set) var product: LoadState<LoadedProductDetail> = .loading
    private(set) var access: LoadState<Components.Schemas.ExamAccessItem?> = .loading
    private(set) var quote: LoadState<Components.Schemas.PriceQuote> = .loading
    var voucher: VoucherBoxState = VoucherBoxState()
    private(set) var submit: CheckoutSubmitState = .idle

    private(set) var goneProduct: Components.Schemas.Product?
    private(set) var isBundleUnavailable: Bool = false
    private(set) var accountConflict: AccountConflict?
    private(set) var priceChangedBanner: Bool = false
    private(set) var priceChangedSeenOverride: Int64?

    @ObservationIgnored private var baseQuote: Components.Schemas.PriceQuote?
    @ObservationIgnored private var overrideQuote: Components.Schemas.PriceQuote?
    @ObservationIgnored private var hasLoadedInitial = false

    private let gateway: any PaymentGateway
    private let paymentBrowser: any PaymentBrowser
    private let pendingOrderStore: PendingPaymentOrderStore
    private let apiBaseURL: URL
    private let clock: any AppClock
    private let createOrderOperation: IdempotentOperation
    private let onOpenPaymentResult: @MainActor (String, Bool) -> Void
    private let onReturnCallback: @MainActor (URL) -> Void

    init(
        code: String,
        seenPrice: SeenPrice = SeenPrice(),
        gateway: any PaymentGateway,
        paymentBrowser: any PaymentBrowser,
        pendingOrderStore: PendingPaymentOrderStore,
        apiBaseURL: URL,
        clock: any AppClock = SystemClock(),
        keyGenerator: @escaping @Sendable () -> String = { UUID().uuidString },
        onOpenPaymentResult: @escaping @MainActor (String, Bool) -> Void = { _, _ in },
        onReturnCallback: @escaping @MainActor (URL) -> Void = { _ in }
    ) {
        self.code = code
        self.seenPrice = seenPrice
        self.gateway = gateway
        self.paymentBrowser = paymentBrowser
        self.pendingOrderStore = pendingOrderStore
        self.apiBaseURL = apiBaseURL
        self.clock = clock
        self.createOrderOperation = IdempotentOperation(keyGenerator: keyGenerator)
        self.onOpenPaymentResult = onOpenPaymentResult
        self.onReturnCallback = onReturnCallback
    }

    var loadedProduct: LoadedProductDetail? {
        if case let .loaded(value, _) = product {
            return value
        }
        return nil
    }

    var activeQuote: Components.Schemas.PriceQuote? {
        if case let .loaded(value, _) = quote {
            return value
        }
        return nil
    }

    var accessRow: Components.Schemas.ExamAccessItem? {
        if case let .loaded(value, _) = access {
            return value
        }
        return nil
    }

    var isBundle: Bool {
        loadedProduct?.isBundle ?? false
    }

    var displayUser: Components.Schemas.MeResponse? {
        accountConflict?.currentUser ?? baselineUser
    }

    var displaySeenPrice: Int64? {
        priceChangedSeenOverride ?? seenPrice.price
    }

    var isBlockedByChecking: Bool {
        guard let accessRow else { return false }
        let hasPendingBundleItem = accessRow.items?.contains(where: {
            $0.hasPendingFulfillment || ($0.pendingOrderCode?.isEmpty == false)
        }) ?? false
        return accessRow.state == .CHECKING || accessRow.hasPendingFulfillment || hasPendingBundleItem
    }

    var isAccessUnavailable: Bool {
        switch access {
        case .loaded:
            return false
        case .loading, .empty, .failed:
            return true
        }
    }

    var isVoucherDisabled: Bool {
        isBlockedByChecking || isBundleUnavailable || accountConflict != nil || submit == .submitting || voucher.isSubmitting
    }

    var isPayButtonEnabled: Bool {
        guard let _ = activeQuote,
              let _ = baselineUser,
              goneProduct == nil,
              !isBundleUnavailable,
              !isBlockedByChecking,
              !isAccessUnavailable,
              accountConflict == nil,
              submit != .submitting,
              !voucher.isSubmitting
        else {
            return false
        }
        return true
    }

    func loadInitialIfNeeded() async {
        guard !hasLoadedInitial else { return }
        hasLoadedInitial = true
        await loadAll()
    }

    func retryLoadProduct() async {
        await loadAll()
    }

    func retryLoadQuote() async {
        await loadBaseQuote()
    }

    func retryLoadAccess() async {
        await loadAccess()
    }

    func loadAll() async {
        hasLoadedInitial = true
        product = .loading
        access = .loading
        quote = .loading
        goneProduct = nil
        isBundleUnavailable = false
        submit = .idle

        async let userResult: Components.Schemas.MeResponse? = try? await gateway.fetchCurrentUser()
        async let productResult = gateway.loadProduct(code: code)

        let (fetchedUser, lookup) = await (userResult, productResult)
        if let fetchedUser, baselineUser == nil {
            baselineUser = fetchedUser
        }

        switch lookup {
        case let .success(loaded):
            product = .loaded(value: loaded, stale: false)
            async let accessTask: Void = loadAccess()
            async let quoteTask: Void = loadBaseQuote()
            _ = await (accessTask, quoteTask)

        case .notFound:
            product = .failed(kind: .notFound, canRetry: false, retryAfter: nil)

        case let .gone(gone):
            goneProduct = gone
            product = .failed(kind: .gone, canRetry: false, retryAfter: nil)

        case let .unavailable(embeddedProduct):
            if let embeddedProduct {
                isBundleUnavailable = true
                product = .loaded(value: embeddedProduct, stale: false)
                await loadAccess()
            } else {
                product = .failed(kind: .conflict(code: "PRODUCT_UNAVAILABLE"), canRetry: false, retryAfter: nil)
            }

        case let .failure(apiError):
            let classified = LoadFailureKind.classify(apiError)
            product = .failed(kind: classified.kind, canRetry: true, retryAfter: classified.retryAfter)
        }
    }

    private func loadAccess() async {
        access = .loading
        let result = await gateway.queryExamAccess(codes: [code])
        switch result {
        case let .success(items):
            let matched = items.first(where: { $0.code == code })
            access = .loaded(value: matched, stale: false)
        case .forbidden:
            access = .failed(kind: .forbidden, canRetry: true, retryAfter: nil)
        case let .failure(apiError):
            let classified = LoadFailureKind.classify(apiError)
            access = .failed(kind: classified.kind, canRetry: true, retryAfter: classified.retryAfter)
        }
    }

    private func loadBaseQuote() async {
        quote = .loading
        let result = await gateway.requestQuote(
            code: code,
            voucherCode: nil,
            seenPrice: seenPrice.price,
            seenVersionNo: seenPrice.versionNo
        )
        switch result {
        case let .success(fetchedQuote):
            baseQuote = fetchedQuote
            overrideQuote = nil
            quote = .loaded(value: fetchedQuote, stale: false)
            evaluatePriceChangedBanner()

        case let .gone(gone):
            goneProduct = gone
            product = .failed(kind: .gone, canRetry: false, retryAfter: nil)

        case .productUnavailable:
            isBundleUnavailable = true
            quote = .failed(kind: .conflict(code: "PRODUCT_UNAVAILABLE"), canRetry: false, retryAfter: nil)

        case .notFound:
            quote = .failed(kind: .notFound, canRetry: true, retryAfter: nil)

        case .rateLimited:
            quote = .failed(kind: .server, canRetry: true, retryAfter: nil)

        case .unauthorized:
            quote = .failed(kind: .server, canRetry: true, retryAfter: nil)

        case .voucherRejected:
            quote = .failed(kind: .server, canRetry: true, retryAfter: nil)

        case let .failure(apiError):
            let classified = LoadFailureKind.classify(apiError)
            quote = .failed(kind: classified.kind, canRetry: true, retryAfter: classified.retryAfter)
        }
    }

    func updateVoucherInput(_ text: String) {
        voucher.codeInput = text
        if case .empty = voucher.feedback {
            voucher.feedback = nil
        } else if case .error = voucher.feedback {
            voucher.feedback = nil
        }
    }

    func applyVoucher() async {
        guard !isVoucherDisabled else { return }
        let trimmed = voucher.codeInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            voucher.feedback = .empty
            return
        }

        let previousQuote = activeQuote
        voucher.isSubmitting = true
        voucher.feedback = nil
        defer { voucher.isSubmitting = false }

        // Apply voucher sends seenVersionNo only (not seenPrice) per Spec §4.2 and AC-4 / ĐT 705.
        let result = await gateway.requestQuote(
            code: code,
            voucherCode: trimmed,
            seenPrice: nil,
            seenVersionNo: seenPrice.versionNo
        )

        switch result {
        case let .success(nextQuote):
            await createOrderOperation.reset()
            submit = .idle
            overrideQuote = nextQuote
            quote = .loaded(value: nextQuote, stale: false)
            evaluatePriceChangedBanner()

            if nextQuote.voucherNotBetter {
                voucher.feedback = .notBetter(code: trimmed)
            } else if let applied = nextQuote.voucher {
                voucher.codeInput = applied.code
                let previousHadPromo = (previousQuote?.promotion?.discountVnd ?? 0) > 0
                let nextHasPromo = (nextQuote.promotion?.discountVnd ?? 0) > 0
                let variant: VoucherAppliedVariant
                if nextQuote.discountApplied == .COMBINED {
                    variant = .combined
                } else if previousHadPromo && !nextHasPromo {
                    variant = .replacedPromo
                } else {
                    variant = .applied
                }
                voucher.feedback = .applied(
                    variant: variant,
                    code: applied.code,
                    discountVnd: applied.discountVnd
                )
            }

        case let .gone(gone):
            goneProduct = gone
            product = .failed(kind: .gone, canRetry: false, retryAfter: nil)

        case .productUnavailable:
            isBundleUnavailable = true

        case let .voucherRejected(key):
            voucher.feedback = .error(key)

        case .rateLimited:
            voucher.feedback = .error(.rateLimited)

        case .notFound, .unauthorized, .failure:
            voucher.feedback = .error(.generic)
        }
    }

    func removeVoucher() async {
        guard !isVoucherDisabled else { return }
        voucher.isSubmitting = true
        voucher.feedback = nil
        defer { voucher.isSubmitting = false }

        let result = await gateway.requestQuote(
            code: code,
            voucherCode: nil,
            seenPrice: seenPrice.price,
            seenVersionNo: seenPrice.versionNo
        )

        switch result {
        case let .success(nextQuote):
            await createOrderOperation.reset()
            submit = .idle
            voucher.codeInput = ""
            voucher.feedback = nil
            baseQuote = nextQuote
            overrideQuote = nil
            quote = .loaded(value: nextQuote, stale: false)
            evaluatePriceChangedBanner()

        case let .gone(gone):
            goneProduct = gone
            product = .failed(kind: .gone, canRetry: false, retryAfter: nil)

        case .productUnavailable:
            isBundleUnavailable = true

        case let .voucherRejected(key):
            voucher.feedback = .error(key)

        case .rateLimited:
            voucher.feedback = .error(.rateLimited)

        case .notFound, .unauthorized, .failure:
            voucher.feedback = .error(.generic)
        }
    }

    func acknowledgeAccountChange() async {
        guard let conflict = accountConflict else { return }
        baselineUser = conflict.currentUser
        accountConflict = nil
        overrideQuote = nil
        baseQuote = nil
        priceChangedBanner = false
        priceChangedSeenOverride = nil
        submit = .idle
        voucher = VoucherBoxState()
        await createOrderOperation.reset()
        async let accessTask: Void = loadAccess()
        async let quoteTask: Void = loadBaseQuote()
        _ = await (accessTask, quoteTask)
    }

    func createOrder() async {
        guard isPayButtonEnabled,
              let currentQuote = activeQuote,
              let currentUser = baselineUser
        else {
            return
        }

        submit = .submitting
        let request = Components.Schemas.CreateOrderRequest(
            code: code,
            expectedAccountId: currentUser.id,
            expectedAmountDue: currentQuote.finalPrice,
            expectedVersionNo: currentQuote.versionNo,
            voucherCode: currentQuote.voucher?.code
        )

        do {
            guard let outcome = try await createOrderOperation.run({ key in
                try await self.gateway.createOrder(request: request, idempotencyKey: key)
            }) else {
                return
            }
            await handleCreateOrderOutcome(outcome, sentQuote: currentQuote, baseline: currentUser)
        } catch {
            let classified = MobileApiError.classify(error)
            if classified.statusCode == 429 || classified.apiCode == "RATE_LIMITED" {
                submit = .failed(.rateLimited)
            } else {
                submit = .failed(.generic)
            }
        }
    }

    private func handleCreateOrderOutcome(
        _ outcome: CreateOrderOutcome,
        sentQuote: Components.Schemas.PriceQuote,
        baseline: Components.Schemas.MeResponse
    ) async {
        switch outcome {
        case let .success(response):
            switch response.next {
            case .FULFILLED:
                submit = .idle
                onOpenPaymentResult(response.order.code, false)
            case .CHECKING:
                submit = .idle
                onOpenPaymentResult(response.order.code, true)
            case .REDIRECT:
                guard let rawURL = response.paymentUrl,
                      let resolvedURL = PaymentURLResolver.resolve(rawURL, apiBaseURL: apiBaseURL)
                else {
                    submit = .failed(.generic)
                    return
                }
                await pendingOrderStore.save(orderCode: response.order.code, openedAt: clock.now)
                let browserOutcome = await paymentBrowser.open(resolvedURL)
                submit = .idle
                switch browserOutcome {
                case let .callback(callbackURL):
                    onReturnCallback(callbackURL)
                case .dismissed:
                    onOpenPaymentResult(response.order.code, false)
                case .openedFallback:
                    break
                case .failedToOpen:
                    await pendingOrderStore.clearIfMatches(orderCode: response.order.code)
                    submit = .failed(.generic)
                }
            }

        case let .priceChanged(newQuote):
            priceChangedBanner = true
            priceChangedSeenOverride = sentQuote.finalPrice
            overrideQuote = newQuote
            quote = .loaded(value: newQuote, stale: false)
            voucher = VoucherBoxState(codeInput: newQuote.voucher?.code ?? "")
            submit = .idle

        case .accountChanged:
            if let refreshed = try? await gateway.fetchCurrentUser(), refreshed.id != baseline.id {
                accountConflict = AccountConflict(previousUsername: baseline.username, currentUser: refreshed)
                submit = .idle
            } else {
                submit = .failed(.generic)
            }

        case let .productGone(gone):
            goneProduct = gone
            product = .failed(kind: .gone, canRetry: false, retryAfter: nil)
            submit = .idle

        case .productUnavailable:
            isBundleUnavailable = true
            submit = .idle

        case let .voucherRejected(key):
            submit = .failed(.voucher(key))
            voucher = VoucherBoxState()
            overrideQuote = nil
            await loadBaseQuote()

        case .paymentAttemptsExceeded:
            submit = .failed(.paymentAttemptsExceeded)

        case .unauthorized:
            submit = .idle

        case .deterministicFailure:
            submit = .failed(.generic)
        }
    }

    private func evaluatePriceChangedBanner() {
        if priceChangedBanner {
            return
        }
        guard let currentQuote = activeQuote else { return }
        if baseQuote?.changed == true {
            priceChangedBanner = true
            return
        }
        if overrideQuote?.voucher == nil && currentQuote.changed {
            priceChangedBanner = true
            return
        }
        if let seenVer = seenPrice.versionNo, Int(currentQuote.versionNo) != seenVer {
            priceChangedBanner = true
            return
        }
        if let base = baseQuote, currentQuote.versionNo != base.versionNo {
            priceChangedBanner = true
            return
        }
        if overrideQuote == nil, let seenAmount = seenPrice.price, currentQuote.finalPrice != seenAmount {
            priceChangedBanner = true
            return
        }
    }
}
