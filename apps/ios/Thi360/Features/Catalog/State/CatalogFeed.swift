import Foundation
import Observation

protocol CatalogDebounceSleeper: Sendable {
    func sleep(milliseconds: UInt64) async throws
}

struct SystemCatalogDebounceSleeper: CatalogDebounceSleeper {
    func sleep(milliseconds: UInt64) async throws {
        try await Task.sleep(nanoseconds: milliseconds * 1_000_000)
    }
}

@Observable
@MainActor
final class CatalogFeed {
    static let searchDebounceMilliseconds: UInt64 = 400

    private(set) var accountId: String?

    private(set) var query: CatalogQuery = .empty
    var searchText: String = ""
    private(set) var filters: CatalogFilters = .empty
    private(set) var loadState: LoadState<CatalogPage> = .loading
    private(set) var staleFailureKind: LoadFailureKind?
    private(set) var retryAvailableAt: Date?
    private(set) var accessByCode: [String: AccessCell] = [:]
    private(set) var scrollToTopToken: Int = 0
    private(set) var isRefreshing: Bool = false
    var selectedProductCode: String?
    var lastScrolledCardCode: String?

    @ObservationIgnored let clock: any AppClock
    @ObservationIgnored private let api: any CatalogAPIClientProtocol
    @ObservationIgnored private let sleeper: any CatalogDebounceSleeper
    @ObservationIgnored private let connectivityMonitor: ConnectivityMonitor?
    @ObservationIgnored private let connectivityObserverId = UUID()
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var hasLoadedOnce: Bool = false
    @ObservationIgnored private var needsAccessRefreshOnHomeReturn: Bool = false
    @ObservationIgnored private var cachedDetailViewModel: ProductDetailViewModel?
    @ObservationIgnored private var activeFetchTask: Task<Void, Never>?
    @ObservationIgnored private var loadMoreTask: Task<Void, Never>?
    @ObservationIgnored private var searchDebounceTask: Task<Void, Never>?
    @ObservationIgnored private var accessTasks: [Task<Void, Never>] = []
    @ObservationIgnored private var accessBatchByCode: [String: [String]] = [:]

    init(
        accountId: String?,
        api: any CatalogAPIClientProtocol,
        sleeper: any CatalogDebounceSleeper = SystemCatalogDebounceSleeper(),
        connectivityMonitor: ConnectivityMonitor? = nil,
        clock: any AppClock = SystemClock()
    ) {
        self.accountId = accountId
        self.api = api
        self.sleeper = sleeper
        self.connectivityMonitor = connectivityMonitor
        self.clock = clock

        if let connectivityMonitor {
            connectivityMonitor.addObserver(id: connectivityObserverId) { [weak self] isOnline in
                guard let self, isOnline else { return }
                let isOfflineFailed: Bool = {
                    if case .failed(kind: .offline, _, _) = self.loadState { return true }
                    return false
                }()
                let isOfflineStale: Bool = {
                    if case .loaded(_, true) = self.loadState, self.staleFailureKind == .offline { return true }
                    return false
                }()
                let isOfflineTail: Bool = {
                    if case let .loaded(page, false) = self.loadState, page.tail == .failed(.offline) { return true }
                    return false
                }()
                if isOfflineFailed || isOfflineStale {
                    Task { @MainActor [weak self] in
                        await self?.retry()
                    }
                } else if isOfflineTail {
                    Task { @MainActor [weak self] in
                        await self?.retryNextPage()
                    }
                }
            }
        }
    }

    deinit {
        if let connectivityMonitor {
            let observerId = connectivityObserverId
            Task { @MainActor in
                connectivityMonitor.removeObserver(observerId)
            }
        }
    }

    var retryRemainingSeconds: Int {
        guard let retryAvailableAt else { return 0 }
        let remaining = retryAvailableAt.timeIntervalSince(clock.now)
        return remaining > 0 ? Int(ceil(remaining)) : 0
    }

    var canRetryNow: Bool {
        retryRemainingSeconds == 0
    }

    var hasLoadedInitialCatalog: Bool {
        hasLoadedOnce
    }

    var activeEmbeddedDetailViewModel: ProductDetailViewModel? {
        guard let selected = selectedProductCode, cachedDetailViewModel?.code == selected else {
            return nil
        }
        return cachedDetailViewModel
    }

    func adoptAccountIdIfMissing(_ newAccountId: String) {
        let trimmed = newAccountId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, accountId == nil else { return }
        accountId = trimmed
    }

    func detailViewModel(for code: String) -> ProductDetailViewModel {
        if let existing = cachedDetailViewModel, existing.code == code {
            return existing
        }
        cachedDetailViewModel?.cancelAllTasks()
        let created = ProductDetailViewModel(
            code: code,
            api: api,
            connectivityMonitor: connectivityMonitor,
            clock: clock
        )
        cachedDetailViewModel = created
        return created
    }

    func clearCachedDetailViewModel() {
        cachedDetailViewModel?.cancelAllTasks()
        cachedDetailViewModel = nil
    }

    func markLeftHomeTab() {
        guard hasLoadedOnce else { return }
        needsAccessRefreshOnHomeReturn = true
    }

    func refreshAccessOnHomeReturnIfNeeded() async {
        guard hasLoadedOnce, needsAccessRefreshOnHomeReturn else { return }
        needsAccessRefreshOnHomeReturn = false
        await refreshAccessOnReturn()
        await activeEmbeddedDetailViewModel?.refreshAccessOnReturn()
    }

    var isEmptyUnfilteredCatalog: Bool {
        if case .empty = loadState {
            return true
        }
        return false
    }

    var isNoResultsState: Bool {
        if case let .loaded(page, _) = loadState {
            return page.cards.isEmpty && query.isFiltered
        }
        return false
    }

    var loadedCards: [CatalogProductCard] {
        if case let .loaded(page, _) = loadState {
            return page.cards
        }
        return []
    }

    var currentGeneration: UInt64 {
        generation
    }

    func accessCell(for code: String) -> AccessCell {
        accessByCode[code] ?? .loading
    }

    func loadInitialIfNeeded() async {
        guard !hasLoadedOnce else { return }
        hasLoadedOnce = true
        await performFullLoad(includeFilters: true, preserveExistingOnError: false)
    }

    func retry() async {
        guard canRetryNow else { return }
        hasLoadedOnce = true
        let hasExistingCards = !loadedCards.isEmpty
        await performFullLoad(includeFilters: true, preserveExistingOnError: hasExistingCards)
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
        let candidate = query.withSearch(searchText)
        guard candidate.trimmedQ != query.trimmedQ else {
            query = candidate
            return
        }
        await applyQuery(candidate)
    }

    func selectKind(_ code: String?) async {
        await applyQuery(query.withKind(code))
    }

    func selectFormat(_ format: CatalogFormat?) async {
        await applyQuery(query.withFormat(format))
    }

    func selectPart(kind: String, part: String) async {
        await applyQuery(query.withPart(kind: kind, part: part))
    }

    func selectSubject(_ subject: String?) async {
        await applyQuery(query.withSubject(subject))
    }

    func clearPartAndSubject() async {
        await applyQuery(query.clearingPartAndSubject())
    }

    func selectSort(_ sort: CatalogSort) async {
        await applyQuery(query.withSort(sort))
    }

    func clearFilters() async {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        searchText = ""
        await applyQuery(query.cleared())
    }

    func applyQuery(_ nextQuery: CatalogQuery) async {
        query = nextQuery
        lastScrolledCardCode = nil
        hasLoadedOnce = true
        let needsFilters = filters == .empty
        await performFullLoad(includeFilters: needsFilters, preserveExistingOnError: false, preserveExistingOnOffline: true)
    }

    func pullToRefresh() async {
        guard canRetryNow else { return }
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        lastScrolledCardCode = nil
        hasLoadedOnce = true
        isRefreshing = true
        defer { isRefreshing = false }
        await performFullLoad(
            includeFilters: true,
            preserveExistingOnError: true,
            preserveExistingOnOffline: true,
            scrollToTopOnSuccess: true,
            showLoadingPlaceholder: false
        )
    }

    func loadNextPageIfNeeded() async {
        guard case let .loaded(page, _) = loadState,
              let nextPage = page.nextPage,
              page.tail != .loading
        else {
            return
        }

        if connectivityMonitor?.isOnline == false {
            let updated = CatalogPage(
                cards: page.cards,
                total: page.total,
                nextPage: nextPage,
                tail: .failed(.offline)
            )
            loadState = .loaded(value: updated, stale: false)
            return
        }

        let callGeneration = generation
        let loadingPage = CatalogPage(
            cards: page.cards,
            total: page.total,
            nextPage: nextPage,
            tail: .loading
        )
        loadState = .loaded(value: loadingPage, stale: false)

        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let response = try await self.api.fetchProducts(self.query.params(page: nextPage))
                guard !Task.isCancelled, callGeneration == self.generation else { return }
                guard case let .loaded(currentPage, _) = self.loadState else { return }

                if response.items.isEmpty {
                    let stoppedPage = CatalogPage(
                        cards: currentPage.cards,
                        total: response.totalItems,
                        nextPage: nil,
                        tail: .idle
                    )
                    self.loadState = .loaded(value: stoppedPage, stale: false)
                    return
                }

                var seen = Set(currentPage.cards.map(\.code))
                var appended: [CatalogProductCard] = []
                for card in response.items where seen.insert(card.code).inserted {
                    appended.append(card)
                }

                let mergedCards = currentPage.cards + appended
                let hasMore = (response.page + 1) < response.totalPages
                let updatedPage = CatalogPage(
                    cards: mergedCards,
                    total: response.totalItems,
                    nextPage: hasMore ? response.page + 1 : nil,
                    tail: .idle
                )
                self.loadState = .loaded(value: updatedPage, stale: false)

                let pageCodes = response.items.map(\.code)
                if !pageCodes.isEmpty {
                    await self.queryAccessBatches(codes: pageCodes, generation: callGeneration, markLoading: true)
                }
            } catch {
                guard !Task.isCancelled, callGeneration == self.generation else { return }
                guard case let .loaded(currentPage, _) = self.loadState else { return }
                let classified = LoadFailureKind.classify(MobileApiError.classify(error))
                let failedKind: LoadFailureKind = classified.kind == .offline ? .offline : .server
                let failedPage = CatalogPage(
                    cards: currentPage.cards,
                    total: currentPage.total,
                    nextPage: nextPage,
                    tail: .failed(failedKind)
                )
                self.loadState = .loaded(value: failedPage, stale: false)
            }
        }
        loadMoreTask = task
        await task.value
    }

    func retryNextPage() async {
        await loadNextPageIfNeeded()
    }

    func refreshAccessOnReturn() async {
        let codes = loadedCards.map(\.code)
        guard !codes.isEmpty else { return }
        let callGeneration = generation
        await queryAccessBatches(codes: codes, generation: callGeneration, markLoading: false)
    }

    func reloadAccess(for code: String) async {
        let callGeneration = generation
        let batchCodes = accessBatchByCode[code] ?? [code]
        await queryAccessBatches(codes: batchCodes, generation: callGeneration, markLoading: true)
    }

    func cancelAllTasks() {
        connectivityMonitor?.removeObserver(connectivityObserverId)
        clearCachedDetailViewModel()
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        activeFetchTask?.cancel()
        activeFetchTask = nil
        loadMoreTask?.cancel()
        loadMoreTask = nil
        for task in accessTasks {
            task.cancel()
        }
        accessTasks.removeAll()
    }

    private func performFullLoad(
        includeFilters: Bool,
        preserveExistingOnError: Bool,
        preserveExistingOnOffline: Bool = true,
        scrollToTopOnSuccess: Bool = false,
        showLoadingPlaceholder: Bool = true
    ) async {
        generation &+= 1
        let callGeneration = generation
        activeFetchTask?.cancel()
        loadMoreTask?.cancel()
        loadMoreTask = nil
        for task in accessTasks {
            task.cancel()
        }
        accessTasks.removeAll()

        let previousPage: CatalogPage? = {
            if case let .loaded(page, _) = loadState, !page.cards.isEmpty {
                return page
            }
            return nil
        }()

        if connectivityMonitor?.isOnline == false {
            if preserveExistingOnOffline, let previousPage {
                staleFailureKind = .offline
                loadState = .loaded(value: previousPage, stale: true)
            } else {
                staleFailureKind = nil
                loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
            }
            return
        }

        if showLoadingPlaceholder {
            staleFailureKind = nil
            loadState = .loading
        }

        let currentQuery = query
        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let fetchedFilters: CatalogFilters?
                let productsResponse: CatalogPageResponse

                if includeFilters {
                    async let filtersCall = self.api.fetchFilters()
                    async let productsCall = self.api.fetchProducts(currentQuery.params(page: 0))
                    let (f, p) = try await (filtersCall, productsCall)
                    fetchedFilters = f
                    productsResponse = p
                } else {
                    fetchedFilters = nil
                    productsResponse = try await self.api.fetchProducts(currentQuery.params(page: 0))
                }

                guard !Task.isCancelled, callGeneration == self.generation else { return }

                if let fetchedFilters {
                    self.filters = fetchedFilters
                }
                self.staleFailureKind = nil
                self.retryAvailableAt = nil

                if productsResponse.totalItems == 0 && !currentQuery.isFiltered {
                    self.accessByCode.removeAll()
                    self.accessBatchByCode.removeAll()
                    self.loadState = .empty
                    return
                }

                var seen = Set<String>()
                let dedupedCards = productsResponse.items.filter { seen.insert($0.code).inserted }
                let hasMore = !dedupedCards.isEmpty && (productsResponse.page + 1) < productsResponse.totalPages
                let newPage = CatalogPage(
                    cards: dedupedCards,
                    total: productsResponse.totalItems,
                    nextPage: hasMore ? productsResponse.page + 1 : nil,
                    tail: .idle
                )
                self.loadState = .loaded(value: newPage, stale: false)
                if scrollToTopOnSuccess {
                    self.scrollToTopToken &+= 1
                }

                let pageCodes = dedupedCards.map(\.code)
                self.accessByCode = self.accessByCode.filter { key, _ in seen.contains(key) }
                self.accessBatchByCode = self.accessBatchByCode.filter { key, _ in seen.contains(key) }

                if !pageCodes.isEmpty {
                    await self.queryAccessBatches(codes: pageCodes, generation: callGeneration, markLoading: true)
                }
            } catch {
                guard !Task.isCancelled, callGeneration == self.generation else { return }
                let apiError = MobileApiError.classify(error)
                let classified = LoadFailureKind.classify(apiError)
                let failureKind: LoadFailureKind = classified.kind == .offline ? .offline : .server
                if let retryAfter = classified.retryAfter, retryAfter > 0 {
                    self.retryAvailableAt = self.clock.now.addingTimeInterval(TimeInterval(retryAfter))
                } else {
                    self.retryAvailableAt = nil
                }

                if let previousPage,
                   preserveExistingOnError || (preserveExistingOnOffline && failureKind == .offline)
                {
                    self.staleFailureKind = failureKind
                    self.loadState = .loaded(value: previousPage, stale: true)
                } else {
                    self.staleFailureKind = nil
                    self.loadState = .failed(kind: failureKind, canRetry: true, retryAfter: classified.retryAfter)
                }
            }
        }
        activeFetchTask = task
        await task.value
    }

    private func queryAccessBatches(codes: [String], generation callGeneration: UInt64, markLoading: Bool) async {
        let chunks = CatalogFormatting.chunked(codes, size: CatalogFormatting.examAccessBatchSize)
        guard !chunks.isEmpty else { return }

        for chunk in chunks {
            for code in chunk {
                accessBatchByCode[code] = chunk
                if markLoading || accessByCode[code] == nil {
                    accessByCode[code] = .loading
                }
            }
        }

        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            for chunk in chunks {
                guard !Task.isCancelled, callGeneration == self.generation else { return }
                do {
                    let rows = try await self.api.queryExamAccess(codes: chunk)
                    guard !Task.isCancelled, callGeneration == self.generation else { return }
                    let rowMap = Dictionary(uniqueKeysWithValues: rows.map { ($0.code, $0) })
                    for code in chunk {
                        self.accessByCode[code] = .ready(rowMap[code] ?? .none(code: code))
                    }
                } catch {
                    guard !Task.isCancelled, callGeneration == self.generation else { return }
                    for code in chunk {
                        self.accessByCode[code] = .failed
                    }
                }
            }
        }
        accessTasks.append(task)
        await task.value
    }
}
