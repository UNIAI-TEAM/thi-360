import Foundation
import Observation

@MainActor
final class CatalogNavigationHandler {
    private let router: AppRouter
    private let debouncer: CatalogNavigationDebouncer

    init(router: AppRouter, clock: any AppClock = SystemClock()) {
        self.router = router
        self.debouncer = CatalogNavigationDebouncer(clock: clock)
    }

    func openProductRoute(code: String) {
        let key = "product:\(code)"
        guard debouncer.shouldAllow(key: key) else { return }
        if router.path(for: router.selectedDestination).last == .product(code: code) {
            return
        }
        router.openProduct(code: code)
    }

    func handleAccessButton(_ button: AccessButtonKind) {
        switch button {
        case let .continue(attemptId):
            if let attemptId, !attemptId.isEmpty {
                guard debouncer.shouldAllow(key: "attempt:\(attemptId)") else { return }
                if router.presentedAttempt?.attemptId == attemptId {
                    return
                }
                router.openAttempt(attemptId: attemptId)
            } else {
                guard debouncer.shouldAllow(key: "tab:myExams") else { return }
                router.switchTab(.myExams)
            }

        case let .enter(entitlementId):
            if let entitlementId, !entitlementId.isEmpty {
                let target = AppRoute.prepare(entitlementId: entitlementId)
                guard debouncer.shouldAllow(key: "prepare:\(entitlementId)") else { return }
                if router.path(for: router.selectedDestination).last == target {
                    return
                }
                router.openPrepare(entitlementId: entitlementId)
            } else {
                guard debouncer.shouldAllow(key: "tab:myExams") else { return }
                router.switchTab(.myExams)
            }

        case let .buy(code, seenVersionNo, seenPrice),
             let .buyMore(code, seenVersionNo, seenPrice),
             let .buyAgain(code, seenVersionNo, seenPrice):
            let target = AppRoute.checkout(code: code, seenVersionNo: seenVersionNo, seenPrice: seenPrice)
            guard debouncer.shouldAllow(key: "checkout:\(code):\(seenVersionNo):\(seenPrice)") else { return }
            if router.path(for: router.selectedDestination).last == target {
                return
            }
            router.openCheckout(code: code, seenVersionNo: seenVersionNo, seenPrice: seenPrice)

        case .checking:
            break
        }
    }

    func openMyExamsTab() {
        guard debouncer.shouldAllow(key: "tab:myExams") else { return }
        router.switchTab(.myExams)
    }

    func returnToCatalog(clearSelection: () -> Void) {
        clearSelection()
        router.setPath([], for: .home)
        router.switchTab(.home, reload: false)
    }
}

@MainActor
final class CatalogFeedRegistry {
    static let shared = CatalogFeedRegistry()

    private final class Entry {
        weak var router: AppRouter?
        weak var sessionStore: SessionStore?
        var accountId: String?
        var normalizedUsername: String?
        var refreshToken: String?
        var reloadCount: Int
        var feed: CatalogFeed

        init(
            router: AppRouter,
            sessionStore: SessionStore?,
            accountId: String?,
            normalizedUsername: String?,
            refreshToken: String?,
            reloadCount: Int,
            feed: CatalogFeed
        ) {
            self.router = router
            self.sessionStore = sessionStore
            self.accountId = accountId
            self.normalizedUsername = normalizedUsername
            self.refreshToken = refreshToken
            self.reloadCount = reloadCount
            self.feed = feed
        }
    }

    private var entries: [ObjectIdentifier: Entry] = [:]

    func feed(
        for router: AppRouter,
        sessionStore: SessionStore?,
        connectivityMonitor: ConnectivityMonitor?,
        apiOverride: (any CatalogAPIClientProtocol)? = nil
    ) -> CatalogFeed {
        pruneInvalidEntries()
        let key = ObjectIdentifier(router)

        if let sessionStore, !sessionStore.state.isAuthenticated {
            evict(for: key)
            return CatalogFeed(
                accountId: nil,
                api: apiOverride ?? PreviewEmptyCatalogAPI(),
                connectivityMonitor: connectivityMonitor
            )
        }

        let currentAccountId = Self.trimmedNonEmpty(sessionStore?.currentAccountId)
        let currentUsername = Self.normalizedUsername(sessionStore?.currentUsername)
        let currentRefreshToken = Self.trimmedNonEmpty(sessionStore?.currentRefreshToken)
        let currentReload = router.reloadCount(for: .home)

        if let existing = entries[key],
           existing.sessionStore === sessionStore,
           existing.reloadCount == currentReload
        {
            if let existingAccountId = existing.accountId, existingAccountId == currentAccountId {
                if let currentUsername { existing.normalizedUsername = currentUsername }
                if let currentRefreshToken { existing.refreshToken = currentRefreshToken }
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
                    if let currentUsername { existing.normalizedUsername = currentUsername }
                    if let currentRefreshToken { existing.refreshToken = currentRefreshToken }
                    existing.feed.adoptAccountIdIfMissing(currentAccountId)
                    return existing.feed
                }
            }
            if existing.accountId == nil && currentAccountId == nil {
                if sessionStore == nil {
                    return existing.feed
                }
                if existing.normalizedUsername != nil || currentUsername != nil {
                    if existing.normalizedUsername == currentUsername {
                        if let currentRefreshToken { existing.refreshToken = currentRefreshToken }
                        return existing.feed
                    }
                } else if existing.refreshToken == currentRefreshToken {
                    return existing.feed
                }
            }
        }

        evict(for: key)
        let api: any CatalogAPIClientProtocol = apiOverride
            ?? sessionStore.map { CatalogAPI(apiClient: $0.apiClient) }
            ?? PreviewEmptyCatalogAPI()
        let freshFeed = CatalogFeed(
            accountId: currentAccountId,
            api: api,
            connectivityMonitor: connectivityMonitor
        )
        let entry = Entry(
            router: router,
            sessionStore: sessionStore,
            accountId: currentAccountId,
            normalizedUsername: currentUsername,
            refreshToken: currentRefreshToken,
            reloadCount: currentReload,
            feed: freshFeed
        )
        entries[key] = entry
        if let sessionStore {
            observeSessionState(for: key, entry: entry, sessionStore: sessionStore)
        }
        return freshFeed
    }

    func existingFeed(for router: AppRouter) -> CatalogFeed? {
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

struct PreviewEmptyCatalogAPI: CatalogAPIClientProtocol {
    func fetchFilters() async throws -> CatalogFilters { .empty }
    func fetchProducts(_ params: CatalogQuery.ProductsRequestParams) async throws -> CatalogPageResponse {
        CatalogPageResponse(items: [], page: 0, size: CatalogQuery.pageSize, totalItems: 0, totalPages: 0)
    }
    func fetchProductDetail(code: String) async throws -> ProductOutcome { .notFound }
    func queryExamAccess(codes: [String]) async throws -> [ExamAccessRow] { [] }
}
