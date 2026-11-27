import SwiftUI

@MainActor
final class PaymentRoutesCoordinator {
    static let shared = PaymentRoutesCoordinator()

    private final class WeakSessionStoreBox {
        weak var value: SessionStore?

        init(_ value: SessionStore) {
            self.value = value
        }
    }

    private var activeResultVM: PaymentResultViewModel?
    private weak var activeResultSessionStore: SessionStore?
    private var activeResultAccountId: String?
    private var activeResultUsername: String?
    private var activeResultRefreshToken: String?
    private var activeResultTarget: PaymentResultTarget?
    private var activeCheckingOrderCodes: Set<String> = []
    private var hookedSessionStores: [WeakSessionStoreBox] = []

    func register(
        router: AppRouter,
        sessionStore: SessionStore,
        sessionCleanup: SessionCleanup,
        environment: AppEnvironment = .current,
        clock: any AppClock = SystemClock(),
        arguments: [String] = ProcessInfo.processInfo.arguments
    ) {
        installSessionHooksIfNeeded(on: sessionStore)
        let pendingStore = makePendingOrderStore(sessionStore: sessionStore)

        router.inboundLinkRouter.register(
            id: "payment.return",
            requiresUnlockedSession: true
        ) { [weak router, weak sessionStore] url, env in
            guard let parsed = PaymentReturnLink.parse(url, environment: env) else {
                return nil
            }
            return { [weak router, weak sessionStore] in
                guard let router else { return }
                PaymentRoutesCoordinator.shared.openReturnLink(
                    url,
                    parsed: parsed,
                    on: router,
                    sessionStore: sessionStore
                )
            }
        }

        let restorePendingOrder: @MainActor () async -> Void = { [weak router] in
            guard let router else { return }
            guard let pending = await pendingStore.readValid(now: clock.now) else {
                return
            }
            if PaymentRoutesCoordinator.shared.isAlreadyViewingPaymentResult(on: router) {
                return
            }
            router.openPaymentResult(.byOrder(orderCode: pending.orderCode))
        }

        router.inboundLinkRouter.onAppStarted(id: "payment.pendingOrder.start", action: restorePendingOrder)
        router.inboundLinkRouter.onForeground(id: "payment.pendingOrder.foreground", action: restorePendingOrder)

        let cleanupParticipant = PendingPaymentCleanupParticipant(
            storeProvider: sessionStore.storeProvider,
            onClearMemory: {
                await MainActor.run {
                    PaymentRoutesCoordinator.shared.resetMemory()
                }
            }
        )
        Task {
            await sessionCleanup.register(cleanupParticipant)
        }

        #if THI360_DEV
        if environment.isDev,
           let rawLink = Self.argumentValue(after: "-uitest-incoming-link", in: arguments),
           let linkURL = URL(string: rawLink)
        {
            _ = router.handleIncomingURL(linkURL, environment: environment)
        }
        #endif
    }

    func resetMemory() {
        let previousVM = activeResultVM
        activeResultVM = nil
        activeResultSessionStore = nil
        activeResultAccountId = nil
        activeResultUsername = nil
        activeResultRefreshToken = nil
        activeResultTarget = nil
        activeCheckingOrderCodes.removeAll()
        previousVM?.stopPolling()
    }

    func markOpenedFromChecking(orderCode: String) {
        activeCheckingOrderCodes.insert(orderCode)
    }

    func consumeOpenedFromChecking(orderCode: String) -> Bool {
        activeCheckingOrderCodes.remove(orderCode) != nil
    }

    func makePendingOrderStore(sessionStore: SessionStore) -> PendingPaymentOrderStore {
        let tokenStore = sessionStore.tokenStore
        return PendingPaymentOrderStore(
            storeProvider: sessionStore.storeProvider,
            accountIdProvider: {
                (try? tokenStore.read())?.accountId
            }
        )
    }

    func makePaymentBrowser(
        router: AppRouter,
        sessionStore: SessionStore,
        environment: AppEnvironment = .current
    ) -> any PaymentBrowser {
        #if THI360_DEV
        if environment.isDev,
           let rawCallback = Self.argumentValue(after: "-uitest-payment-browser-callback", in: ProcessInfo.processInfo.arguments),
           let callbackURL = URL(string: rawCallback)
        {
            return UITestCallbackPaymentBrowser(callbackURL: callbackURL)
        }
        #endif

        let pendingStore = makePendingOrderStore(sessionStore: sessionStore)
        return WebAuthPaymentBrowser(
            callbackHost: environment.webBaseURL.host ?? "",
            callbackPath: PaymentReturnLink.returnPath,
            onFallbackDismissed: { [weak router] in
                Task { @MainActor in
                    guard let router else { return }
                    guard let pending = await pendingStore.readValid(now: SystemClock().now) else {
                        return
                    }
                    if !PaymentRoutesCoordinator.shared.isAlreadyViewingPaymentResult(on: router) {
                        router.openPaymentResult(.byOrder(orderCode: pending.orderCode))
                    }
                }
            }
        )
    }

    func resultViewModel(
        target: PaymentResultTarget,
        router: AppRouter,
        sessionStore: SessionStore,
        environment: AppEnvironment = .current
    ) -> PaymentResultViewModel {
        if let existing = activeResultVM, matchesActiveSessionIdentity(sessionStore: sessionStore) {
            if activeResultTarget != target {
                activeResultTarget = target
                let entry = makeEntry(for: target, environment: environment)
                Task { @MainActor in
                    await existing.acceptEntry(entry)
                }
            }
            return existing
        }

        activeResultVM?.stopPolling()

        let entry = makeEntry(for: target, environment: environment)
        let gateway = OpenApiPaymentGateway(apiClient: sessionStore.apiClient)
        let pendingStore = makePendingOrderStore(sessionStore: sessionStore)
        let browser = makePaymentBrowser(router: router, sessionStore: sessionStore, environment: environment)

        let viewModel = PaymentResultViewModel(
            initialEntry: entry,
            gateway: gateway,
            paymentBrowser: browser,
            pendingOrderStore: pendingStore,
            apiBaseURL: sessionStore.apiClient.serverURL,
            usernameProvider: { [weak sessionStore] in
                sessionStore?.currentUsername
            },
            onReturnCallback: { [weak router] callbackURL in
                guard let router else { return }
                _ = router.handleIncomingURL(callbackURL, environment: environment)
            }
        )
        activeResultVM = viewModel
        activeResultSessionStore = sessionStore
        activeResultAccountId = Self.trimmedNonEmpty(sessionStore.currentAccountId)
        activeResultUsername = Self.normalizedUsername(sessionStore.currentUsername)
        activeResultRefreshToken = Self.trimmedNonEmpty(sessionStore.currentRefreshToken)
        activeResultTarget = target
        return viewModel
    }

    func clearResultViewModelIfUnmounted(_ viewModel: PaymentResultViewModel, router: AppRouter, sessionStore: SessionStore) {
        guard activeResultVM === viewModel else { return }
        if sessionStore.state == .locked {
            return
        }
        if !isAlreadyViewingPaymentResult(on: router) {
            activeResultVM = nil
            activeResultSessionStore = nil
            activeResultAccountId = nil
            activeResultUsername = nil
            activeResultRefreshToken = nil
            activeResultTarget = nil
            Task {
                await viewModel.onLeaveScreen(clearPendingOrder: true)
            }
        }
    }

    func makeEntry(for target: PaymentResultTarget, environment: AppEnvironment) -> PaymentResultEntry {
        switch target {
        case let .byOrder(orderCode):
            let fromChecking = consumeOpenedFromChecking(orderCode: orderCode)
            return .byOrder(
                orderCode: orderCode,
                verifiedFromLink: false,
                openedFromChecking: fromChecking
            )
        case let .byReturnLink(url):
            if let parsed = PaymentReturnLink.parse(url, environment: environment) {
                return .byReturnLink(parsed)
            }
            return .byOrder(orderCode: "")
        }
    }

    private func installSessionHooksIfNeeded(on sessionStore: SessionStore) {
        hookedSessionStores.removeAll { $0.value == nil }
        guard !hookedSessionStores.contains(where: { $0.value === sessionStore }) else {
            return
        }
        hookedSessionStores.append(WeakSessionStoreBox(sessionStore))

        let existingCleared = sessionStore.onSessionCleared
        sessionStore.onSessionCleared = {
            existingCleared?()
            PaymentRoutesCoordinator.shared.resetMemory()
        }

        let existingReplaced = sessionStore.onSessionReplaced
        sessionStore.onSessionReplaced = {
            existingReplaced?()
            PaymentRoutesCoordinator.shared.resetMemory()
        }
    }

    private func matchesActiveSessionIdentity(sessionStore: SessionStore) -> Bool {
        guard activeResultSessionStore === sessionStore else {
            return false
        }

        let currentAccountId = Self.trimmedNonEmpty(sessionStore.currentAccountId)
        let currentUsername = Self.normalizedUsername(sessionStore.currentUsername)
        let currentRefreshToken = Self.trimmedNonEmpty(sessionStore.currentRefreshToken)

        if let existingAccountId = activeResultAccountId {
            guard existingAccountId == currentAccountId else {
                return false
            }
            if let currentUsername {
                activeResultUsername = currentUsername
            }
            if let currentRefreshToken {
                activeResultRefreshToken = currentRefreshToken
            }
            return true
        }

        if let currentAccountId {
            let sameUsername = activeResultUsername != nil && activeResultUsername == currentUsername
            let sameRefreshToken = activeResultUsername == nil
                && currentUsername == nil
                && activeResultRefreshToken != nil
                && activeResultRefreshToken == currentRefreshToken
            if sameUsername || sameRefreshToken {
                activeResultAccountId = currentAccountId
                if let currentUsername {
                    activeResultUsername = currentUsername
                }
                if let currentRefreshToken {
                    activeResultRefreshToken = currentRefreshToken
                }
                return true
            }
            return false
        }

        if activeResultUsername != nil || currentUsername != nil {
            guard activeResultUsername == currentUsername else {
                return false
            }
            if let currentRefreshToken {
                activeResultRefreshToken = currentRefreshToken
            }
            return true
        }

        return activeResultRefreshToken == currentRefreshToken
    }

    private func openReturnLink(
        _ url: URL,
        parsed: PaymentReturnLink,
        on router: AppRouter,
        sessionStore: SessionStore?
    ) {
        if isAlreadyViewingPaymentResult(on: router) {
            if let existing = activeResultVM,
               sessionStore.map({ matchesActiveSessionIdentity(sessionStore: $0) }) ?? false
            {
                activeResultTarget = .byReturnLink(url)
                Task { @MainActor in
                    await existing.acceptEntry(.byReturnLink(parsed))
                }
            } else {
                if activeResultVM != nil {
                    resetMemory()
                }
                var stack = router.paths[router.selectedDestination] ?? []
                if !stack.isEmpty {
                    stack[stack.count - 1] = .paymentResult(.byReturnLink(url))
                    router.paths[router.selectedDestination] = stack
                }
            }
            return
        }
        router.openPaymentResult(.byReturnLink(url))
    }

    private func isAlreadyViewingPaymentResult(on router: AppRouter) -> Bool {
        let stack = router.path(for: router.selectedDestination)
        if case .paymentResult = stack.last {
            return true
        }
        return false
    }

    private static func trimmedNonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }

    private static func normalizedUsername(_ value: String?) -> String? {
        trimmedNonEmpty(value)?.lowercased()
    }

    private static func argumentValue(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else {
            return nil
        }
        return arguments[index + 1]
    }
}

#if THI360_DEV
@MainActor
private final class UITestCallbackPaymentBrowser: PaymentBrowser {
    let callbackURL: URL

    init(callbackURL: URL) {
        self.callbackURL = callbackURL
    }

    func open(_ url: URL) async -> PaymentBrowserOutcome {
        .callback(callbackURL)
    }
}
#endif

enum PaymentRoutes {
    @MainActor
    static func register(
        router: AppRouter,
        sessionStore: SessionStore,
        sessionCleanup: SessionCleanup,
        environment: AppEnvironment = .current
    ) {
        PaymentRoutesCoordinator.shared.register(
            router: router,
            sessionStore: sessionStore,
            sessionCleanup: sessionCleanup,
            environment: environment
        )
    }

    @MainActor
    static func checkout(
        code: String,
        seenVersionNo: Int?,
        seenPrice: Int?,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        CheckoutRouteHostView(
            code: code,
            seenVersionNo: seenVersionNo,
            seenPrice: seenPrice,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func paymentResult(
        target: PaymentResultTarget,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        PaymentResultRouteHostView(
            target: target,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func orders(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        OrdersRouteHostView(
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func order(
        orderCode: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        OrderDetailRouteHostView(
            orderCode: orderCode,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}

private struct CheckoutRouteHostView: View {
    let code: String
    let seenVersionNo: Int?
    let seenPrice: Int?
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @State private var viewModel: CheckoutViewModel?

    var body: some View {
        Group {
            if let sessionStore {
                if let vm = viewModel {
                    CheckoutView(
                        viewModel: vm,
                        locale: preferences.locale,
                        supportContact: AppEnvironment.current.supportContact,
                        fallbackUsername: sessionStore.currentUsername,
                        onBackToProduct: { router.back() },
                        onBackToCatalog: { router.switchTab(.home, reload: true) },
                        onSwitchAccount: {
                            Task { await sessionStore.signOut() }
                        },
                        onEnterExam: { entitlementId in
                            router.openPrepare(entitlementId: entitlementId)
                        },
                        onContinueAttempt: { attemptId in
                            router.openAttempt(attemptId: attemptId)
                        },
                        onViewPendingOrder: { orderCode in
                            if let orderCode, !orderCode.isEmpty {
                                PaymentRoutesCoordinator.shared.markOpenedFromChecking(orderCode: orderCode)
                                router.openPaymentResult(.byOrder(orderCode: orderCode))
                            } else {
                                router.openOrders()
                            }
                        }
                    )
                } else {
                    Color.clear
                        .onAppear {
                            viewModel = makeViewModel(sessionStore: sessionStore)
                        }
                }
            } else {
                PaymentUnboundSessionFallbackView(
                    title: PaymentStrings.text("checkout.pageTitle", locale: preferences.locale),
                    identifier: AppRoute.checkout(code: code, seenVersionNo: seenVersionNo, seenPrice: seenPrice).accessibilityIdentifier
                )
            }
        }
    }

    private func makeViewModel(sessionStore: SessionStore) -> CheckoutViewModel {
        let coordinator = PaymentRoutesCoordinator.shared
        return CheckoutViewModel(
            code: code,
            seenPrice: SeenPrice(versionNo: seenVersionNo, price: seenPrice.map(Int64.init)),
            gateway: OpenApiPaymentGateway(apiClient: sessionStore.apiClient),
            paymentBrowser: coordinator.makePaymentBrowser(router: router, sessionStore: sessionStore),
            pendingOrderStore: coordinator.makePendingOrderStore(sessionStore: sessionStore),
            apiBaseURL: sessionStore.apiClient.serverURL,
            onOpenPaymentResult: { orderCode, fromChecking in
                if fromChecking {
                    coordinator.markOpenedFromChecking(orderCode: orderCode)
                }
                router.openPaymentResult(.byOrder(orderCode: orderCode))
            },
            onReturnCallback: { callbackURL in
                _ = router.handleIncomingURL(callbackURL)
            }
        )
    }
}

private struct PaymentResultRouteHostView: View {
    let target: PaymentResultTarget
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @State private var viewModel: PaymentResultViewModel?
    @State private var appliedTarget: PaymentResultTarget?

    var body: some View {
        Group {
            if let sessionStore {
                if let vm = viewModel {
                    PaymentResultView(
                        viewModel: vm,
                        locale: preferences.locale,
                        supportContact: AppEnvironment.current.supportContact,
                        onGoMyExams: { router.switchTab(.myExams, reload: true) },
                        onViewOrderDetail: { orderCode in router.openOrder(orderCode: orderCode) },
                        onViewOrders: { router.openOrders() },
                        onBuyAgain: { productCode in router.openCheckout(code: productCode) },
                        onOpenCatalog: { router.switchTab(.home, reload: true) },
                        onSwitchAccount: {
                            Task { await sessionStore.signOut() }
                        }
                    )
                    .task(id: target) {
                        guard appliedTarget != target else { return }
                        appliedTarget = target
                        let entry = PaymentRoutesCoordinator.shared.makeEntry(for: target, environment: AppEnvironment.current)
                        await vm.acceptEntry(entry)
                    }
                    .onDisappear {
                        PaymentRoutesCoordinator.shared.clearResultViewModelIfUnmounted(
                            vm,
                            router: router,
                            sessionStore: sessionStore
                        )
                    }
                } else {
                    Color.clear
                        .onAppear {
                            let acquired = PaymentRoutesCoordinator.shared.resultViewModel(
                                target: target,
                                router: router,
                                sessionStore: sessionStore
                            )
                            appliedTarget = target
                            viewModel = acquired
                        }
                }
            } else {
                PaymentUnboundSessionFallbackView(
                    title: PaymentStrings.text("checkout.pageTitle", locale: preferences.locale),
                    identifier: AppRoute.paymentResult(target).accessibilityIdentifier
                )
            }
        }
    }
}

private struct OrdersRouteHostView: View {
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @State private var viewModel: OrdersViewModel?

    var body: some View {
        Group {
            if let sessionStore {
                if let vm = viewModel {
                    OrdersView(
                        viewModel: vm,
                        locale: preferences.locale,
                        supportContact: AppEnvironment.current.supportContact,
                        onOpenOrderDetail: { orderCode in
                            router.openOrder(orderCode: orderCode)
                        },
                        onGoMyExams: { router.switchTab(.myExams, reload: true) },
                        onCreateNewOrder: { productCode in
                            router.openCheckout(code: productCode)
                        },
                        onOpenCatalog: { router.switchTab(.home, reload: true) },
                        detailPane: { selectedCode in
                            OrderDetailRouteHostView(
                                orderCode: selectedCode,
                                destination: destination,
                                router: router,
                                preferences: preferences,
                                onOrderMutated: { updated in
                                    vm.applyUpdatedOrderDetail(updated)
                                },
                                onOpenOrderInSplit: { replacementCode in
                                    vm.selectedOrderCode = replacementCode
                                }
                            )
                            .id(selectedCode)
                        }
                    )
                } else {
                    Color.clear
                        .onAppear {
                            viewModel = makeViewModel(sessionStore: sessionStore)
                        }
                }
            } else {
                PaymentUnboundSessionFallbackView(
                    title: PaymentStrings.text("orders.pageTitle", locale: preferences.locale),
                    identifier: AppRoute.orders.accessibilityIdentifier
                )
            }
        }
    }

    private func makeViewModel(sessionStore: SessionStore) -> OrdersViewModel {
        let coordinator = PaymentRoutesCoordinator.shared
        return OrdersViewModel(
            gateway: OpenApiPaymentGateway(apiClient: sessionStore.apiClient),
            paymentBrowser: coordinator.makePaymentBrowser(router: router, sessionStore: sessionStore),
            pendingOrderStore: coordinator.makePendingOrderStore(sessionStore: sessionStore),
            apiBaseURL: sessionStore.apiClient.serverURL,
            usernameProvider: { [weak sessionStore] in
                sessionStore?.currentUsername
            },
            onOpenPaymentResult: { orderCode in
                router.openPaymentResult(.byOrder(orderCode: orderCode))
            },
            onReturnCallback: { callbackURL in
                _ = router.handleIncomingURL(callbackURL)
            }
        )
    }
}

private struct OrderDetailRouteHostView: View {
    let orderCode: String
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    var onOrderMutated: (@MainActor (Components.Schemas.OrderDetail) -> Void)? = nil
    var onOpenOrderInSplit: (@MainActor (String) -> Void)? = nil

    @Environment(\.sessionStore) private var sessionStore
    @State private var viewModel: OrderDetailViewModel?

    var body: some View {
        Group {
            if let sessionStore {
                if let vm = viewModel {
                    OrderDetailView(
                        viewModel: vm,
                        locale: preferences.locale,
                        supportContact: AppEnvironment.current.supportContact,
                        onGoMyExams: { router.switchTab(.myExams, reload: true) },
                        onOpenOrder: { targetCode in
                            if let onOpenOrderInSplit {
                                onOpenOrderInSplit(targetCode)
                            } else {
                                router.openOrder(orderCode: targetCode)
                            }
                        },
                        onBackToOrders: { router.back() },
                        onCreateNewOrder: { productCode in router.openCheckout(code: productCode) },
                        onOpenCatalog: { router.switchTab(.home, reload: true) },
                        onSwitchAccount: {
                            Task { await sessionStore.signOut() }
                        }
                    )
                } else {
                    Color.clear
                        .onAppear {
                            viewModel = makeViewModel(sessionStore: sessionStore)
                        }
                }
            } else {
                PaymentUnboundSessionFallbackView(
                    title: PaymentStrings.text("orders.detailPageTitle", locale: preferences.locale),
                    identifier: AppRoute.order(orderCode: orderCode).accessibilityIdentifier
                )
            }
        }
    }

    private func makeViewModel(sessionStore: SessionStore) -> OrderDetailViewModel {
        let coordinator = PaymentRoutesCoordinator.shared
        return OrderDetailViewModel(
            orderCode: orderCode,
            gateway: OpenApiPaymentGateway(apiClient: sessionStore.apiClient),
            paymentBrowser: coordinator.makePaymentBrowser(router: router, sessionStore: sessionStore),
            pendingOrderStore: coordinator.makePendingOrderStore(sessionStore: sessionStore),
            apiBaseURL: sessionStore.apiClient.serverURL,
            onOrderMutated: { detail in
                onOrderMutated?(detail)
            },
            onOpenPaymentResult: { code in
                router.openPaymentResult(.byOrder(orderCode: code))
            },
            onReturnCallback: { callbackURL in
                _ = router.handleIncomingURL(callbackURL)
            }
        )
    }
}

private struct PaymentUnboundSessionFallbackView: View {
    let title: String
    let identifier: String

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundStyle(palette.textColor)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityIdentifier(identifier)
    }
}
