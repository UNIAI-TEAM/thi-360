import Foundation
import Observation

protocol HistoryDebounceSleeper: Sendable {
    func sleep(milliseconds: UInt64) async throws
}

struct SystemHistoryDebounceSleeper: HistoryDebounceSleeper {
    func sleep(milliseconds: UInt64) async throws {
        try await Task.sleep(nanoseconds: milliseconds * 1_000_000)
    }
}

@Observable
@MainActor
final class HistoryFeed {
    static let searchDebounceMilliseconds: UInt64 = 300

    private struct RequestKey: Equatable {
        let filter: HistoryFilter
        let page: Int
        let sequence: UInt64
    }

    private(set) var accountId: String?
    private(set) var filter: HistoryFilter = .empty
    var searchText: String = ""
    private(set) var loadState: LoadState<HistoryPage> = .loading
    private(set) var lastLoadedAt: Date?
    private(set) var isSwitchingFilter: Bool = false
    private(set) var isRetryingStale: Bool = false
    private(set) var examKindOptions: [CatalogOptionItem] = []
    private(set) var subjectOptions: [CatalogOptionItem] = []
    private(set) var subjectMap: [String: CatalogOptionItem] = [:]

    @ObservationIgnored let clock: any AppClock
    @ObservationIgnored let navigationDebouncer: MyExamsNavigationDebouncer
    @ObservationIgnored private let api: any HistoryAPIClientProtocol
    @ObservationIgnored private let optionsCache: CatalogOptionsCache?
    @ObservationIgnored private let sleeper: any HistoryDebounceSleeper
    @ObservationIgnored private let connectivityMonitor: ConnectivityMonitor?
    @ObservationIgnored private var hasLoadedOnce: Bool = false
    @ObservationIgnored private var needsReloadOnTabReturn: Bool = false
    @ObservationIgnored private var lastHandledReloadToken: Int? = nil
    @ObservationIgnored private var hasAppliedResetForCurrentToken: Bool = false
    @ObservationIgnored private var sequenceCounter: UInt64 = 0
    @ObservationIgnored private var pendingPageZeroKey: RequestKey?
    @ObservationIgnored private var pendingNextPageKey: RequestKey?
    @ObservationIgnored private var lastFailedRequest: (filter: HistoryFilter, page: Int)?
    @ObservationIgnored private var cachedPage: HistoryPage?
    @ObservationIgnored private var activeFetchTask: Task<Void, Never>?
    @ObservationIgnored private var loadMoreTask: Task<Void, Never>?
    @ObservationIgnored private var searchDebounceTask: Task<Void, Never>?
    @ObservationIgnored private var optionsTask: Task<Void, Never>?

    init(
        accountId: String?,
        api: any HistoryAPIClientProtocol,
        optionsCache: CatalogOptionsCache? = nil,
        sleeper: any HistoryDebounceSleeper = SystemHistoryDebounceSleeper(),
        connectivityMonitor: ConnectivityMonitor? = nil,
        clock: any AppClock = SystemClock()
    ) {
        self.accountId = accountId
        self.api = api
        self.optionsCache = optionsCache
        self.sleeper = sleeper
        self.connectivityMonitor = connectivityMonitor
        self.clock = clock
        self.navigationDebouncer = MyExamsNavigationDebouncer(clock: clock)
    }

    var currentPage: HistoryPage? {
        if case let .loaded(page, _) = loadState {
            return page
        }
        return nil
    }

    var displayedTotalItems: Int? {
        switch loadState {
        case let .loaded(page, _):
            return page.totalItems
        case .empty:
            return 0
        case .loading, .failed:
            return nil
        }
    }

    var isNoMatchState: Bool {
        if case let .loaded(page, _) = loadState {
            return page.items.isEmpty && !filter.isEmpty
        }
        return false
    }

    func adoptAccountIdIfMissing(_ newAccountId: String) {
        let trimmed = newAccountId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, accountId == nil else { return }
        accountId = trimmed
    }

    func loadOnAppear(resetFilters: Bool, reloadToken: Int) async {
        let tokenChanged = lastHandledReloadToken != reloadToken
        if tokenChanged {
            lastHandledReloadToken = reloadToken
            hasAppliedResetForCurrentToken = false
        }

        if resetFilters && !hasAppliedResetForCurrentToken {
            hasAppliedResetForCurrentToken = true
            await clearFilters(forceReload: true)
            return
        }

        if !hasLoadedOnce || needsReloadOnTabReturn || tokenChanged {
            hasLoadedOnce = true
            needsReloadOnTabReturn = false
            async let opts: Void = loadOptionsIfAvailable()
            async let page: Void = performPageZeroLoad(for: filter, isFilterChange: false)
            _ = await (opts, page)
        }
    }

    func markLeftTab() {
        guard hasLoadedOnce else { return }
        needsReloadOnTabReturn = true
    }

    func reloadOnReturnIfNeeded() async {
        guard hasLoadedOnce, needsReloadOnTabReturn else { return }
        needsReloadOnTabReturn = false
        async let opts: Void = loadOptionsIfAvailable()
        async let page: Void = performPageZeroLoad(for: filter, isFilterChange: false)
        _ = await (opts, page)
    }

    func reloadWithCurrentFilter() async {
        hasLoadedOnce = true
        needsReloadOnTabReturn = false
        async let opts: Void = loadOptionsIfAvailable()
        async let page: Void = performPageZeroLoad(for: filter, isFilterChange: false)
        _ = await (opts, page)
    }

    func updateSearchText(_ newValue: String) {
        searchText = newValue
        searchDebounceTask?.cancel()
        searchDebounceTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await self.sleeper.sleep(milliseconds: Self.searchDebounceMilliseconds)
            } catch {
                return
            }
            guard !Task.isCancelled else { return }
            await self.commitSearchTextIfChanged()
        }
    }

    func submitSearchImmediately() async {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        await commitSearchTextIfChanged()
    }

    private func commitSearchTextIfChanged() async {
        let candidate = filter.withSearch(searchText)
        guard candidate.trimmedQ != filter.trimmedQ else {
            filter = candidate
            return
        }
        await applyFilter(candidate)
    }

    func selectKind(_ code: String?) async {
        let candidate = filter.withKind(code)
        guard candidate != filter else { return }
        await applyFilter(candidate)
    }

    func selectScope(_ scope: HistoryScope?) async {
        let candidate = filter.withScope(scope)
        guard candidate != filter else { return }
        await applyFilter(candidate)
    }

    func selectFromDate(_ date: HistoryCalendarDate?) async {
        let candidate = filter.withFrom(date)
        guard candidate != filter else { return }
        await applyFilter(candidate)
    }

    func selectToDate(_ date: HistoryCalendarDate?) async {
        let candidate = filter.withTo(date)
        guard candidate != filter else { return }
        await applyFilter(candidate)
    }

    func clearFilters(forceReload: Bool = false) async {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        let hadChanges = !filter.isEmpty || !searchText.isEmpty
        searchText = ""
        filter = .empty
        hasLoadedOnce = true
        await loadOptionsIfAvailable()
        if hadChanges || forceReload || cachedPage == nil {
            await performPageZeroLoad(for: .empty, isFilterChange: cachedPage != nil)
        }
    }

    func applyFilter(_ nextFilter: HistoryFilter) async {
        filter = nextFilter
        hasLoadedOnce = true
        await performPageZeroLoad(for: nextFilter, isFilterChange: cachedPage != nil)
    }

    func retry() async {
        guard !isRetryingStale else { return }
        hasLoadedOnce = true
        if let failed = lastFailedRequest, failed.page > 0, failed.filter == filter, cachedPage != nil {
            isRetryingStale = true
            defer { isRetryingStale = false }
            await performNextPageLoad(pageNumber: failed.page, for: failed.filter)
            return
        }
        isRetryingStale = (cachedPage != nil)
        defer { isRetryingStale = false }
        await loadOptionsIfAvailable()
        await performPageZeroLoad(for: filter, isFilterChange: false)
    }

    func loadNextPageIfNeeded() async {
        guard case let .loaded(page, _) = loadState,
              page.hasMore,
              !page.isLoadingMore
        else {
            return
        }
        await performNextPageLoad(pageNumber: page.page + 1, for: filter)
    }

    func cancelAllTasks() {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        activeFetchTask?.cancel()
        activeFetchTask = nil
        loadMoreTask?.cancel()
        loadMoreTask = nil
        optionsTask?.cancel()
        optionsTask = nil
        pendingPageZeroKey = nil
        pendingNextPageKey = nil
        isSwitchingFilter = false
        isRetryingStale = false
    }

    func setLoadedStateForTesting(
        page: HistoryPage,
        lastLoadedAt: Date?,
        stale: Bool,
        filter: HistoryFilter = .empty,
        examKinds: [CatalogOptionItem] = [],
        subjects: [CatalogOptionItem] = []
    ) {
        self.hasLoadedOnce = true
        self.needsReloadOnTabReturn = false
        self.lastHandledReloadToken = 0
        self.hasAppliedResetForCurrentToken = false
        self.isSwitchingFilter = false
        self.isRetryingStale = false
        self.filter = filter
        self.searchText = filter.q
        self.cachedPage = page
        self.lastLoadedAt = lastLoadedAt
        self.examKindOptions = examKinds
        self.subjectOptions = subjects
        self.subjectMap = Dictionary(uniqueKeysWithValues: subjects.map { ($0.code, $0) })
        self.loadState = .loaded(value: page, stale: stale)
    }

    func setEmptyStateForTesting(filter: HistoryFilter = .empty) {
        self.hasLoadedOnce = true
        self.needsReloadOnTabReturn = false
        self.lastHandledReloadToken = 0
        self.hasAppliedResetForCurrentToken = false
        self.isSwitchingFilter = false
        self.isRetryingStale = false
        self.filter = filter
        self.searchText = filter.q
        self.cachedPage = nil
        if filter.isEmpty {
            self.loadState = .empty
        } else {
            let emptyPage = HistoryPage(items: [], page: 0, totalItems: 0, totalPages: 0, hasMore: false, isLoadingMore: false)
            self.cachedPage = emptyPage
            self.loadState = .loaded(value: emptyPage, stale: false)
        }
    }

    func setFailedStateForTesting(kind: LoadFailureKind) {
        self.hasLoadedOnce = true
        self.needsReloadOnTabReturn = false
        self.lastHandledReloadToken = 0
        self.hasAppliedResetForCurrentToken = false
        self.isSwitchingFilter = false
        self.isRetryingStale = false
        self.cachedPage = nil
        self.loadState = .failed(kind: kind, canRetry: true, retryAfter: nil)
    }

    private func loadOptionsIfAvailable() async {
        guard let optionsCache else { return }
        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            async let examsResult: [CatalogOptionItem]? = try? await optionsCache.options(for: .exam)
            async let subjectsResult: [CatalogOptionItem]? = try? await optionsCache.options(for: .subject)
            let (exams, subjects) = await (examsResult, subjectsResult)
            guard !Task.isCancelled else { return }
            if let exams {
                self.examKindOptions = exams
            }
            if let subjects {
                self.subjectOptions = subjects
                self.subjectMap = Dictionary(uniqueKeysWithValues: subjects.map { ($0.code, $0) })
            }
        }
        optionsTask = task
        await task.value
    }

    private func performPageZeroLoad(for targetFilter: HistoryFilter, isFilterChange: Bool) async {
        loadMoreTask?.cancel()
        loadMoreTask = nil
        pendingNextPageKey = nil

        if connectivityMonitor?.isOnline == false {
            isSwitchingFilter = false
            lastFailedRequest = (targetFilter, 0)
            loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
            return
        }

        sequenceCounter &+= 1
        let requestKey = RequestKey(filter: targetFilter, page: 0, sequence: sequenceCounter)
        pendingPageZeroKey = requestKey
        activeFetchTask?.cancel()

        if let existing = cachedPage, isFilterChange {
            isSwitchingFilter = true
            let clearedLoadingMore = HistoryPage(
                items: existing.items,
                page: existing.page,
                totalItems: existing.totalItems,
                totalPages: existing.totalPages,
                hasMore: existing.hasMore,
                isLoadingMore: false
            )
            cachedPage = clearedLoadingMore
            loadState = .loaded(value: clearedLoadingMore, stale: false)
        } else if cachedPage == nil {
            isSwitchingFilter = false
            loadState = .loading
        }

        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let response = try await self.api.fetchHistory(targetFilter.params(page: 0))
                guard !Task.isCancelled,
                      self.filter == targetFilter,
                      self.pendingPageZeroKey == requestKey
                else {
                    return
                }

                self.pendingPageZeroKey = nil
                self.isSwitchingFilter = false
                self.lastFailedRequest = nil
                self.lastLoadedAt = self.clock.now

                if response.totalItems == 0 && targetFilter.isEmpty {
                    self.cachedPage = nil
                    self.loadState = .empty
                    return
                }

                var seen = Set<String>()
                let deduped = response.items.filter { !$0.attemptId.isEmpty && seen.insert($0.attemptId).inserted }
                let hasMore = !deduped.isEmpty && (response.page + 1) < response.totalPages
                let newPage = HistoryPage(
                    items: deduped,
                    page: response.page,
                    totalItems: response.totalItems,
                    totalPages: response.totalPages,
                    hasMore: hasMore,
                    isLoadingMore: false
                )
                self.cachedPage = newPage
                self.loadState = .loaded(value: newPage, stale: false)
            } catch {
                guard !Task.isCancelled,
                      self.filter == targetFilter,
                      self.pendingPageZeroKey == requestKey
                else {
                    return
                }
                self.pendingPageZeroKey = nil
                self.isSwitchingFilter = false
                self.lastFailedRequest = (targetFilter, 0)

                let apiError = MobileApiError.classify(error)
                if apiError.statusCode == 403 {
                    self.cachedPage = nil
                    self.loadState = .failed(kind: .forbidden, canRetry: true, retryAfter: nil)
                    await self.api.refreshSessionAfterForbidden()
                    return
                }

                if self.connectivityMonitor?.isOnline == false
                    || (self.connectivityMonitor == nil && self.cachedPage == nil && apiError == .network(.notConnectedToInternet))
                {
                    self.loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
                } else if let existing = self.cachedPage {
                    self.loadState = .loaded(value: existing, stale: true)
                } else {
                    self.loadState = .failed(kind: .server, canRetry: true, retryAfter: nil)
                }
            }
        }
        activeFetchTask = task
        await task.value
    }

    private func performNextPageLoad(pageNumber: Int, for targetFilter: HistoryFilter) async {
        guard let existing = cachedPage else { return }

        if connectivityMonitor?.isOnline == false {
            lastFailedRequest = (targetFilter, pageNumber)
            loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
            return
        }

        sequenceCounter &+= 1
        let requestKey = RequestKey(filter: targetFilter, page: pageNumber, sequence: sequenceCounter)
        pendingNextPageKey = requestKey

        let loadingPage = HistoryPage(
            items: existing.items,
            page: existing.page,
            totalItems: existing.totalItems,
            totalPages: existing.totalPages,
            hasMore: existing.hasMore,
            isLoadingMore: true
        )
        cachedPage = loadingPage
        loadState = .loaded(value: loadingPage, stale: false)

        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let response = try await self.api.fetchHistory(targetFilter.params(page: pageNumber))
                guard !Task.isCancelled,
                      self.filter == targetFilter,
                      self.pendingNextPageKey == requestKey,
                      let current = self.cachedPage,
                      pageNumber == current.page + 1
                else {
                    return
                }

                self.pendingNextPageKey = nil
                self.lastFailedRequest = nil
                self.lastLoadedAt = self.clock.now

                if response.items.isEmpty {
                    let stoppedPage = HistoryPage(
                        items: current.items,
                        page: current.page,
                        totalItems: response.totalItems,
                        totalPages: response.totalPages,
                        hasMore: false,
                        isLoadingMore: false
                    )
                    self.cachedPage = stoppedPage
                    self.loadState = .loaded(value: stoppedPage, stale: false)
                    return
                }

                var seen = Set(current.items.map(\.attemptId))
                let appended = response.items.filter { !$0.attemptId.isEmpty && seen.insert($0.attemptId).inserted }
                let hasMore = (response.page + 1) < response.totalPages
                let mergedPage = HistoryPage(
                    items: current.items + appended,
                    page: response.page,
                    totalItems: response.totalItems,
                    totalPages: response.totalPages,
                    hasMore: hasMore,
                    isLoadingMore: false
                )
                self.cachedPage = mergedPage
                self.loadState = .loaded(value: mergedPage, stale: false)
            } catch {
                guard !Task.isCancelled,
                      self.filter == targetFilter,
                      self.pendingNextPageKey == requestKey,
                      let current = self.cachedPage
                else {
                    return
                }
                self.pendingNextPageKey = nil
                self.lastFailedRequest = (targetFilter, pageNumber)

                let apiError = MobileApiError.classify(error)
                if apiError.statusCode == 403 {
                    self.cachedPage = nil
                    self.loadState = .failed(kind: .forbidden, canRetry: true, retryAfter: nil)
                    await self.api.refreshSessionAfterForbidden()
                    return
                }

                let settledPage = HistoryPage(
                    items: current.items,
                    page: current.page,
                    totalItems: current.totalItems,
                    totalPages: current.totalPages,
                    hasMore: current.hasMore,
                    isLoadingMore: false
                )
                self.cachedPage = settledPage

                if self.connectivityMonitor?.isOnline == false {
                    self.loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
                } else {
                    self.loadState = .loaded(value: settledPage, stale: true)
                }
            }
        }
        loadMoreTask = task
        await task.value
    }
}

@MainActor
final class HistoryNavigationHandler {
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

    func openMyExamsTab() {
        guard debouncer.shouldAllow(key: "tab:myExams") else { return }
        router.switchTab(.myExams)
    }

    func handleContinue(attemptId: String) {
        guard !attemptId.isEmpty else { return }
        guard debouncer.shouldAllow(key: "attempt:\(attemptId)") else { return }
        if router.presentedAttempt?.attemptId == attemptId { return }
        router.openAttempt(attemptId: attemptId)
    }

    func handleViewResult(attemptId: String) {
        guard !attemptId.isEmpty else { return }
        let target = AppRoute.result(attemptId: attemptId)
        guard debouncer.shouldAllow(key: "result:\(attemptId)") else { return }
        if router.path(for: router.selectedDestination).last == target { return }
        router.openResult(attemptId: attemptId)
    }

    func handleTitleTap(for item: HistoryAttemptItem) {
        guard item.action == "VIEW_RESULT" else { return }
        handleViewResult(attemptId: item.attemptId)
    }
}

struct PreviewEmptyHistoryAPI: HistoryAPIClientProtocol {
    func fetchHistory(_ params: HistoryRequestParams) async throws -> HistoryPageResponse {
        HistoryPageResponse(items: [], page: params.page, size: params.size, totalItems: 0, totalPages: 0)
    }
    func refreshSessionAfterForbidden() async {}
}

@MainActor
final class HistoryFeedRegistry {
    static let shared = HistoryFeedRegistry()

    private final class Entry {
        weak var router: AppRouter?
        weak var sessionStore: SessionStore?
        var accountId: String?
        var normalizedUsername: String?
        var refreshToken: String?
        var feed: HistoryFeed

        init(
            router: AppRouter,
            sessionStore: SessionStore?,
            accountId: String?,
            normalizedUsername: String?,
            refreshToken: String?,
            feed: HistoryFeed
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
        optionsCache: CatalogOptionsCache?,
        connectivityMonitor: ConnectivityMonitor?,
        apiOverride: (any HistoryAPIClientProtocol)? = nil
    ) -> HistoryFeed {
        pruneInvalidEntries()
        let key = ObjectIdentifier(router)

        if let sessionStore, !sessionStore.state.isAuthenticated {
            evict(for: key)
            return HistoryFeed(
                accountId: nil,
                api: apiOverride ?? PreviewEmptyHistoryAPI(),
                optionsCache: optionsCache,
                connectivityMonitor: connectivityMonitor
            )
        }

        let currentAccountId = Self.trimmedNonEmpty(sessionStore?.currentAccountId)
        let currentUsername = Self.normalizedUsername(sessionStore?.currentUsername)
        let currentRefreshToken = Self.trimmedNonEmpty(sessionStore?.currentRefreshToken)

        if let existing = entries[key], existing.sessionStore === sessionStore {
            if let existingAccountId = existing.accountId, existingAccountId == currentAccountId {
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
                    return existing.feed
                }
            }
            if existing.accountId == nil && currentAccountId == nil {
                if sessionStore == nil
                    || (existing.normalizedUsername != nil && existing.normalizedUsername == currentUsername)
                    || (existing.normalizedUsername == nil && currentUsername == nil && existing.refreshToken == currentRefreshToken)
                {
                    return existing.feed
                }
            }
        }

        evict(for: key)
        let api: any HistoryAPIClientProtocol = apiOverride
            ?? sessionStore.map { HistoryAPI(apiClient: $0.apiClient) }
            ?? PreviewEmptyHistoryAPI()
        let freshFeed = HistoryFeed(
            accountId: currentAccountId,
            api: api,
            optionsCache: optionsCache,
            connectivityMonitor: connectivityMonitor
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

    func existingFeed(for router: AppRouter) -> HistoryFeed? {
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
