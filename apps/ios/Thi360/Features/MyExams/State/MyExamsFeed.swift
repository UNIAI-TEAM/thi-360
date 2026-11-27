import Foundation
import Observation

protocol MyExamsRetrySleeper: Sendable {
    func sleep(attempt: Int) async throws
}

struct SystemMyExamsRetrySleeper: MyExamsRetrySleeper {
    func sleep(attempt: Int) async throws {
        let ms = UInt64(min(1_000, 200 * (attempt + 1)))
        try await Task.sleep(nanoseconds: ms * 1_000_000)
    }
}

@Observable
@MainActor
final class MyExamsFeed {
    static let maxInitialAutoRetries = 2

    private(set) var accountId: String?
    private(set) var loadState: LoadState<[MyExamCard]> = .loading
    private(set) var lastLoadedAt: Date?
    private(set) var isReloading: Bool = false
    private(set) var greetingFullName: String?
    private(set) var greetingUsername: String?

    @ObservationIgnored let clock: any AppClock
    @ObservationIgnored let navigationDebouncer: MyExamsNavigationDebouncer
    @ObservationIgnored private let api: any MyExamsAPIClientProtocol
    @ObservationIgnored private let retrySleeper: any MyExamsRetrySleeper
    @ObservationIgnored private let connectivityMonitor: ConnectivityMonitor?
    @ObservationIgnored private var cachedItems: [MyExamCard]?
    @ObservationIgnored private var hasLoadedOnce: Bool = false
    @ObservationIgnored private var hasFetchedIdentity: Bool = false
    @ObservationIgnored private var needsReloadOnTabReturn: Bool = false
    @ObservationIgnored private var lastHandledReloadToken: Int? = nil
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var activeLoadTask: Task<Void, Never>?

    init(
        accountId: String?,
        api: any MyExamsAPIClientProtocol,
        connectivityMonitor: ConnectivityMonitor? = nil,
        clock: any AppClock = SystemClock(),
        retrySleeper: any MyExamsRetrySleeper = SystemMyExamsRetrySleeper(),
        initialFullName: String? = nil,
        initialUsername: String? = nil
    ) {
        self.accountId = accountId
        self.api = api
        self.connectivityMonitor = connectivityMonitor
        self.clock = clock
        self.navigationDebouncer = MyExamsNavigationDebouncer(clock: clock)
        self.retrySleeper = retrySleeper
        self.greetingFullName = initialFullName
        self.greetingUsername = initialUsername
    }

    var hasInitialLoaded: Bool {
        hasLoadedOnce
    }

    var greetingDisplayName: String {
        MyExamsFormatting.resolveGreetingName(fullName: greetingFullName, username: greetingUsername)
    }

    func updateFallbackIdentity(fullName: String?, username: String?) {
        if let cleanFull = fullName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !cleanFull.isEmpty,
           (greetingFullName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
        {
            greetingFullName = cleanFull
        }
        if let cleanUser = username?.trimmingCharacters(in: .whitespacesAndNewlines),
           !cleanUser.isEmpty,
           (greetingUsername?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
        {
            greetingUsername = cleanUser
        }
    }

    func adoptAccountIdIfMissing(_ newAccountId: String) {
        let trimmed = newAccountId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, accountId == nil else { return }
        accountId = trimmed
    }

    func loadOnAppear(reloadToken: Int = 0) async {
        let tokenChanged = lastHandledReloadToken != reloadToken
        if tokenChanged {
            lastHandledReloadToken = reloadToken
        }

        if !hasLoadedOnce {
            hasLoadedOnce = true
            needsReloadOnTabReturn = false
            await performLoad(allowAutoRetry: true, showSkeletonIfEmpty: true)
        } else if needsReloadOnTabReturn || tokenChanged {
            needsReloadOnTabReturn = false
            await performLoad(allowAutoRetry: false, showSkeletonIfEmpty: cachedItems == nil)
        }
    }

    func markLeftTab() {
        guard hasLoadedOnce else { return }
        needsReloadOnTabReturn = true
    }

    func reloadOnReturnIfNeeded() async {
        guard hasLoadedOnce, needsReloadOnTabReturn else { return }
        needsReloadOnTabReturn = false
        await performLoad(allowAutoRetry: false, showSkeletonIfEmpty: cachedItems == nil)
    }

    func reloadKeepingExisting() async {
        hasLoadedOnce = true
        needsReloadOnTabReturn = false
        await performLoad(allowAutoRetry: false, showSkeletonIfEmpty: cachedItems == nil)
    }

    func retryFromError() async {
        guard !isReloading else { return }
        hasLoadedOnce = true
        await performLoad(allowAutoRetry: cachedItems == nil, showSkeletonIfEmpty: cachedItems == nil)
    }

    func retryFromStaleBanner() async {
        guard !isReloading else { return }
        await performLoad(allowAutoRetry: false, showSkeletonIfEmpty: false)
    }

    func reloadFromCheckingCard() async {
        guard !isReloading else { return }
        await performLoad(allowAutoRetry: false, showSkeletonIfEmpty: false)
    }

    func pullToRefresh() async {
        guard !isReloading else { return }
        hasLoadedOnce = true
        await performLoad(allowAutoRetry: false, showSkeletonIfEmpty: false)
    }

    func cancelAllTasks() {
        activeLoadTask?.cancel()
        activeLoadTask = nil
        isReloading = false
    }

    func setLoadedStateForTesting(items: [MyExamCard], lastLoadedAt: Date?, stale: Bool) {
        self.hasLoadedOnce = true
        self.needsReloadOnTabReturn = false
        self.lastHandledReloadToken = 0
        self.isReloading = false
        self.cachedItems = items
        self.lastLoadedAt = lastLoadedAt
        if items.isEmpty {
            self.loadState = .empty
        } else {
            self.loadState = .loaded(value: items, stale: stale)
        }
    }

    func setFailedStateForTesting(kind: LoadFailureKind) {
        self.hasLoadedOnce = true
        self.needsReloadOnTabReturn = false
        self.lastHandledReloadToken = 0
        self.isReloading = false
        self.cachedItems = nil
        self.loadState = .failed(kind: kind, canRetry: true, retryAfter: nil)
    }

    private func performLoad(allowAutoRetry: Bool, showSkeletonIfEmpty: Bool) async {
        if let cached = cachedItems, connectivityMonitor?.isOnline == false {
            loadState = .loaded(value: cached, stale: true)
            return
        }
        if cachedItems == nil && connectivityMonitor?.isOnline == false {
            loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
            return
        }

        generation &+= 1
        let callGeneration = generation
        activeLoadTask?.cancel()

        if showSkeletonIfEmpty && cachedItems == nil {
            loadState = .loading
        }

        isReloading = true

        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            defer {
                if callGeneration == self.generation {
                    self.isReloading = false
                }
            }

            let maxRetries = (allowAutoRetry && self.cachedItems == nil) ? Self.maxInitialAutoRetries : 0
            var attempt = 0

            while true {
                guard !Task.isCancelled, callGeneration == self.generation else { return }
                do {
                    let items = try await self.api.fetchMyExams()
                    guard !Task.isCancelled, callGeneration == self.generation else { return }

                    self.lastLoadedAt = self.clock.now
                    if items.isEmpty {
                        self.cachedItems = nil
                        self.loadState = .empty
                    } else {
                        self.cachedItems = items
                        self.loadState = .loaded(value: items, stale: false)
                    }

                    await self.fetchIdentityOnceIfNeeded(generation: callGeneration)
                    return
                } catch {
                    guard !Task.isCancelled, callGeneration == self.generation else { return }
                    let apiError = MobileApiError.classify(error)

                    if Self.isRetryableInitialError(apiError), attempt < maxRetries {
                        do {
                            try await self.retrySleeper.sleep(attempt: attempt)
                        } catch {
                            return
                        }
                        attempt += 1
                        continue
                    }

                    if apiError.statusCode == 403 {
                        self.cachedItems = nil
                        self.loadState = .failed(kind: .forbidden, canRetry: true, retryAfter: nil)
                        await self.api.refreshSessionAfterForbidden()
                        return
                    }

                    if let cached = self.cachedItems {
                        self.loadState = .loaded(value: cached, stale: true)
                    } else if self.connectivityMonitor?.isOnline == false {
                        self.loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
                    } else {
                        self.loadState = .failed(kind: .server, canRetry: true, retryAfter: nil)
                    }
                    return
                }
            }
        }

        activeLoadTask = task
        await task.value
    }

    private func fetchIdentityOnceIfNeeded(generation callGeneration: UInt64) async {
        guard !hasFetchedIdentity else { return }
        if let full = greetingFullName?.trimmingCharacters(in: .whitespacesAndNewlines), !full.isEmpty {
            hasFetchedIdentity = true
            return
        }
        hasFetchedIdentity = true
        if let identity = try? await api.fetchMe(), callGeneration == self.generation {
            greetingFullName = identity.fullName
            if !identity.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                greetingUsername = identity.username
            }
        }
    }

    private static func isRetryableInitialError(_ error: MobileApiError) -> Bool {
        switch error {
        case .network:
            return true
        case let .api(statusCode, _), let .unexpectedResponse(statusCode):
            return (500...599).contains(statusCode)
        }
    }
}

@MainActor
final class MyExamsNavigationDebouncer {
    private let minimumIntervalSeconds: TimeInterval
    private let clock: any AppClock
    private var lastTapDate: Date?
    private var lastTapKey: String?

    init(minimumIntervalSeconds: TimeInterval = 0.6, clock: any AppClock = SystemClock()) {
        self.minimumIntervalSeconds = minimumIntervalSeconds
        self.clock = clock
    }

    func shouldAllow(key: String) -> Bool {
        let now = clock.now
        if let lastTapDate, now.timeIntervalSince(lastTapDate) < minimumIntervalSeconds {
            return false
        }
        lastTapDate = now
        lastTapKey = key
        return true
    }
}

@MainActor
final class MyExamsNavigationHandler {
    private let router: AppRouter
    private let debouncer: MyExamsNavigationDebouncer

    init(router: AppRouter, debouncer: MyExamsNavigationDebouncer) {
        self.router = router
        self.debouncer = debouncer
    }

    init(router: AppRouter, clock: any AppClock = SystemClock()) {
        self.router = router
        self.debouncer = MyExamsNavigationDebouncer(clock: clock)
    }

    func openExploreCatalog() {
        guard debouncer.shouldAllow(key: "tab:home") else { return }
        router.switchTab(.home)
    }

    func openBundleDetail(code: String) {
        let target = AppRoute.product(code: code)
        guard debouncer.shouldAllow(key: "product:\(code)") else { return }
        if router.path(for: router.selectedDestination).last == target { return }
        router.openProduct(code: code)
    }

    func openOrderDetail(orderCode: String) {
        let target = AppRoute.order(orderCode: orderCode)
        guard debouncer.shouldAllow(key: "order:\(orderCode)") else { return }
        if router.path(for: router.selectedDestination).last == target { return }
        router.openOrder(orderCode: orderCode)
    }

    func handleCardAction(_ action: MyExamCardButtonAction) {
        switch action {
        case let .continue(attemptId):
            guard debouncer.shouldAllow(key: "attempt:\(attemptId)") else { return }
            if router.presentedAttempt?.attemptId == attemptId { return }
            router.openAttempt(attemptId: attemptId)

        case let .start(entitlementId):
            let target = AppRoute.prepare(entitlementId: entitlementId)
            guard debouncer.shouldAllow(key: "prepare:\(entitlementId)") else { return }
            if router.path(for: router.selectedDestination).last == target { return }
            router.openPrepare(entitlementId: entitlementId)

        case let .rebuy(code):
            let target = AppRoute.checkout(code: code)
            guard debouncer.shouldAllow(key: "checkout:\(code)") else { return }
            if router.path(for: router.selectedDestination).last == target { return }
            router.openCheckout(code: code)

        case let .viewResult(attemptId):
            let target = AppRoute.result(attemptId: attemptId)
            guard debouncer.shouldAllow(key: "result:\(attemptId)") else { return }
            if router.path(for: router.selectedDestination).last == target { return }
            router.openResult(attemptId: attemptId)

        case .history:
            guard debouncer.shouldAllow(key: "history:reset") else { return }
            router.openHistory(resetFilters: true)

        case let .buyMore(code):
            let target = AppRoute.checkout(code: code)
            guard debouncer.shouldAllow(key: "checkout:\(code)") else { return }
            if router.path(for: router.selectedDestination).last == target { return }
            router.openCheckout(code: code)
        }
    }
}

struct PreviewEmptyMyExamsAPI: MyExamsAPIClientProtocol {
    func fetchMyExams() async throws -> [MyExamCard] { [] }
    func fetchMe() async throws -> MyExamsUserIdentity? { nil }
    func refreshSessionAfterForbidden() async {}
}

@MainActor
final class MyExamsFeedRegistry {
    static let shared = MyExamsFeedRegistry()

    private final class Entry {
        weak var router: AppRouter?
        weak var sessionStore: SessionStore?
        var accountId: String?
        var normalizedUsername: String?
        var refreshToken: String?
        var feed: MyExamsFeed

        init(
            router: AppRouter,
            sessionStore: SessionStore?,
            accountId: String?,
            normalizedUsername: String?,
            refreshToken: String?,
            feed: MyExamsFeed
        ) {
            self.router = router
            self.sessionStore = sessionStore
            self.accountId = accountId
            self.normalizedUsername = normalizedUsername
            self.refreshToken = refreshToken
            self.feed = feed
        }
    }

    private var entries: [ObjectIdentifier: Entry] = [:]

    func feed(
        for router: AppRouter,
        sessionStore: SessionStore?,
        connectivityMonitor: ConnectivityMonitor?,
        apiOverride: (any MyExamsAPIClientProtocol)? = nil
    ) -> MyExamsFeed {
        pruneInvalidEntries()
        let key = ObjectIdentifier(router)

        if let sessionStore, !sessionStore.state.isAuthenticated {
            evict(for: key)
            return MyExamsFeed(
                accountId: nil,
                api: apiOverride ?? PreviewEmptyMyExamsAPI(),
                connectivityMonitor: connectivityMonitor
            )
        }

        let currentAccountId = Self.trimmedNonEmpty(sessionStore?.currentAccountId)
        let currentUsername = Self.normalizedUsername(sessionStore?.currentUsername)
        let currentRefreshToken = Self.trimmedNonEmpty(sessionStore?.currentRefreshToken)

        if let existing = entries[key], existing.sessionStore === sessionStore {
            if let existingAccountId = existing.accountId, existingAccountId == currentAccountId {
                existing.feed.updateFallbackIdentity(
                    fullName: router.cachedAccountDisplayName,
                    username: sessionStore?.currentUsername
                )
                return existing.feed
            }
            if existing.accountId == nil, let currentAccountId {
                let sameUsername = existing.normalizedUsername != nil && existing.normalizedUsername == currentUsername
                let sameRefreshToken = existing.normalizedUsername == nil
                    && currentUsername == nil
                    && existing.refreshToken != nil
                    && existing.refreshToken == currentRefreshToken
                if sameUsername || sameRefreshToken {
                    existing.accountId = currentAccountId
                    existing.feed.adoptAccountIdIfMissing(currentAccountId)
                    existing.feed.updateFallbackIdentity(
                        fullName: router.cachedAccountDisplayName,
                        username: sessionStore?.currentUsername
                    )
                    return existing.feed
                }
            }
            if existing.accountId == nil && currentAccountId == nil {
                if sessionStore == nil
                    || (existing.normalizedUsername != nil && existing.normalizedUsername == currentUsername)
                    || (existing.normalizedUsername == nil && currentUsername == nil && existing.refreshToken == currentRefreshToken)
                {
                    existing.feed.updateFallbackIdentity(
                        fullName: router.cachedAccountDisplayName,
                        username: sessionStore?.currentUsername
                    )
                    return existing.feed
                }
            }
        }

        evict(for: key)
        let api: any MyExamsAPIClientProtocol = apiOverride
            ?? sessionStore.map { MyExamsAPI(apiClient: $0.apiClient) }
            ?? PreviewEmptyMyExamsAPI()
        let freshFeed = MyExamsFeed(
            accountId: currentAccountId,
            api: api,
            connectivityMonitor: connectivityMonitor,
            initialFullName: router.cachedAccountDisplayName,
            initialUsername: sessionStore?.currentUsername
        )
        let entry = Entry(
            router: router,
            sessionStore: sessionStore,
            accountId: currentAccountId,
            normalizedUsername: currentUsername,
            refreshToken: currentRefreshToken,
            feed: freshFeed
        )
        entries[key] = entry
        if let sessionStore {
            observeSessionState(for: key, entry: entry, sessionStore: sessionStore)
        }
        return freshFeed
    }

    func existingFeed(for router: AppRouter) -> MyExamsFeed? {
        pruneInvalidEntries()
        return entries[ObjectIdentifier(router)]?.feed
    }

    func clear(for router: AppRouter) {
        evict(for: ObjectIdentifier(router))
    }

    private func evict(for key: ObjectIdentifier) {
        entries[key]?.feed.cancelAllTasks()
        entries.removeValue(forKey: key)
    }

    private func pruneInvalidEntries() {
        let staleKeys = entries.compactMap { key, entry -> ObjectIdentifier? in
            if entry.router == nil { return key }
            if let sessionStore = entry.sessionStore, !sessionStore.state.isAuthenticated { return key }
            return nil
        }
        for key in staleKeys {
            evict(for: key)
        }
    }

    private func observeSessionState(for key: ObjectIdentifier, entry: Entry, sessionStore: SessionStore) {
        withObservationTracking {
            _ = sessionStore.state
        } onChange: { [weak self, weak entry, weak sessionStore] in
            Task { @MainActor in
                guard let self, let entry, let sessionStore else { return }
                guard self.entries[key] === entry else { return }
                if !sessionStore.state.isAuthenticated {
                    self.evict(for: key)
                    return
                }
                self.observeSessionState(for: key, entry: entry, sessionStore: sessionStore)
            }
        }
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
}
