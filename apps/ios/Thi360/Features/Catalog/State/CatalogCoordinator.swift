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
        var reloadCount: Int
        var feed: CatalogFeed

        init(router: AppRouter, sessionStore: SessionStore?, reloadCount: Int, feed: CatalogFeed) {
            self.router = router
            self.sessionStore = sessionStore
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
        pruneDeallocated()
        let key = ObjectIdentifier(router)
        let currentAccountId = sessionStore?.currentAccountId
        let currentReload = router.reloadCount(for: .home)

        if let existing = entries[key],
           existing.feed.accountId == currentAccountId,
           existing.sessionStore === sessionStore,
           existing.reloadCount == currentReload
        {
            return existing.feed
        }

        entries[key]?.feed.cancelAllTasks()
        let api: any CatalogAPIClientProtocol = apiOverride
            ?? sessionStore.map { CatalogAPI(apiClient: $0.apiClient) }
            ?? PreviewEmptyCatalogAPI()
        let freshFeed = CatalogFeed(
            accountId: currentAccountId,
            api: api,
            connectivityMonitor: connectivityMonitor
        )
        entries[key] = Entry(
            router: router,
            sessionStore: sessionStore,
            reloadCount: currentReload,
            feed: freshFeed
        )
        return freshFeed
    }

    func existingFeed(for router: AppRouter) -> CatalogFeed? {
        pruneDeallocated()
        return entries[ObjectIdentifier(router)]?.feed
    }

    func clear(for router: AppRouter) {
        let key = ObjectIdentifier(router)
        entries[key]?.feed.cancelAllTasks()
        entries.removeValue(forKey: key)
    }

    private func pruneDeallocated() {
        entries = entries.filter { _, entry in entry.router != nil }
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
