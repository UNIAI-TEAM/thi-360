package vn.unicom.thi360.feature.history

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.catalog.CatalogOptionType
import vn.unicom.thi360.core.catalog.CatalogOptionsCache
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.generated.api.MyAttemptsResultsApi
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.network.generated.model.PageResponseAttemptHistoryItem
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState

data class HistoryFeedState(
    val searchInput: String = "",
    val filter: HistoryFilter = HistoryFilter.Empty,
    val loadState: LoadState<HistoryPage> = LoadState.Loading,
    val lastLoadedAtMillis: Long? = null,
    val isFilterRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRetryingStale: Boolean = false,
    val isOfflineBlock: Boolean = false,
    val examKinds: List<CatalogOptionItem> = emptyList(),
    val subjectMap: Map<String, CatalogOptionItem> = emptyMap(),
) {
    val hasActiveFilters: Boolean
        get() = searchInput.trim().isNotEmpty() || !filter.isEmpty
}

class HistoryFeed(
    private val fetchHistory: suspend (HistoryApiParams) -> ApiResult<PageResponseAttemptHistoryItem>,
    private val catalogOptionsCache: CatalogOptionsCache,
    private val connectivityMonitor: ConnectivityMonitor,
    private val onForbidden: suspend () -> Unit = {},
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val debounceDelay: suspend (Long) -> Unit = { delay(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private data class RequestKey(
        val filter: HistoryFilter,
        val page: Int,
        val sequence: Long,
    )

    private data class FailedRequest(
        val filter: HistoryFilter,
        val page: Int,
    )

    private val _state = MutableStateFlow(HistoryFeedState())
    val state: StateFlow<HistoryFeedState> = _state.asStateFlow()

    private val lock = Any()
    private var activeRequestKey: RequestKey? = null
    private var requestSequence: Long = 0L
    private var searchDebounceJob: Job? = null
    private var activeFetchJob: Job? = null
    private var lastFailedRequest: FailedRequest? = null
    private var pendingFilterReset: Boolean = false
    private var lastConsumedReloadCounter: Long = 0L
    private var lastConsumedEntryId: String? = null

    fun requestFilterReset() {
        synchronized(lock) {
            pendingFilterReset = true
        }
    }

    fun onAppear(
        resetFilters: Boolean = false,
        backStackEntryId: String? = null,
        reloadCounter: Long = 0L,
    ) {
        val shouldReset = synchronized(lock) {
            val fromPendingReset = pendingFilterReset
            pendingFilterReset = false
            val fromSwitchTabReload = reloadCounter != lastConsumedReloadCounter
            lastConsumedReloadCounter = reloadCounter
            val isNewEntry = backStackEntryId != null && backStackEntryId != lastConsumedEntryId
            if (backStackEntryId != null) {
                lastConsumedEntryId = backStackEntryId
            }
            when {
                fromPendingReset -> true
                resetFilters && !fromSwitchTabReload && (backStackEntryId == null || isNewEntry) -> true
                else -> false
            }
        }

        if (shouldReset) {
            cancelPendingSearch()
            val clearedFilter = HistoryFilter.Empty
            _state.update { current ->
                current.copy(
                    searchInput = "",
                    filter = clearedFilter,
                    isOfflineBlock = false,
                )
            }
            loadCatalogOptions()
            startPageRequest(filter = clearedFilter, page = 0, isFilterChange = false)
            return
        }

        loadCatalogOptions()
        val current = _state.value
        val hasData = hasLoadedPage(current.loadState)
        if (!hasData && activeFetchJob?.isActive == true) {
            return
        }
        startPageRequest(filter = current.filter, page = 0, isFilterChange = false)
    }

    fun onSearchInputChanged(rawText: String) {
        _state.update { it.copy(searchInput = rawText) }
        val trimmed = rawText.trim()
        synchronized(lock) {
            searchDebounceJob?.cancel()
            if (trimmed == _state.value.filter.trimmedQuery) {
                searchDebounceJob = null
                return
            }
            searchDebounceJob = scope.launch {
                debounceDelay(SEARCH_DEBOUNCE_MS)
                applyQueryFilter(trimmed)
            }
        }
    }

    fun onSearchSubmit() {
        cancelPendingSearch()
        val trimmed = _state.value.searchInput.trim()
        if (trimmed != _state.value.filter.trimmedQuery) {
            applyQueryFilter(trimmed)
        }
    }

    fun selectExamKind(kindCode: String?) {
        val normalized = kindCode?.takeIf { it.isNotBlank() }
        val current = _state.value.filter
        if (current.kind == normalized) return
        applyFilterChange(current.copy(kind = normalized))
    }

    fun selectScope(scopeValue: HistoryScope?) {
        val current = _state.value.filter
        if (current.scope == scopeValue) return
        applyFilterChange(current.copy(scope = scopeValue))
    }

    fun selectFromDate(date: LocalDate?) {
        val current = _state.value.filter
        if (date != null && current.to != null && date.isAfter(current.to)) {
            return
        }
        if (current.from == date) return
        applyFilterChange(current.copy(from = date))
    }

    fun selectToDate(date: LocalDate?) {
        val current = _state.value.filter
        if (date != null && current.from != null && date.isBefore(current.from)) {
            return
        }
        if (current.to == date) return
        applyFilterChange(current.copy(to = date))
    }

    fun clearAllFilters() {
        cancelPendingSearch()
        val cleared = HistoryFilter.Empty
        val hadSearchOnly = _state.value.searchInput.isNotEmpty()
        val filterChanged = _state.value.filter != cleared
        _state.update { current ->
            current.copy(
                searchInput = "",
                filter = cleared,
            )
        }
        if (filterChanged || hadSearchOnly) {
            startPageRequest(filter = cleared, page = 0, isFilterChange = hasLoadedPage(_state.value.loadState))
        }
    }

    fun loadNextPage() {
        val current = _state.value
        val loaded = current.loadState as? LoadState.Loaded ?: return
        val pageData = loaded.value
        if (loaded.stale || !pageData.hasMore || current.isLoadingMore || current.isFilterRefreshing || current.isOfflineBlock) {
            return
        }
        startPageRequest(
            filter = current.filter,
            page = pageData.currentPage + 1,
            isFilterChange = false,
        )
    }

    fun retry() {
        val current = _state.value
        if (current.isRetryingStale) return
        val failed = synchronized(lock) { lastFailedRequest }
        val targetFilter = failed?.filter ?: current.filter
        val targetPage = failed?.page ?: 0
        loadCatalogOptions()
        startPageRequest(
            filter = targetFilter,
            page = targetPage,
            isFilterChange = false,
            isStaleRetry = current.loadState is LoadState.Loaded && current.loadState.stale,
        )
    }

    fun clear() {
        synchronized(lock) {
            requestSequence += 1L
            activeRequestKey = null
            lastFailedRequest = null
            pendingFilterReset = false
            lastConsumedReloadCounter = 0L
            lastConsumedEntryId = null
            searchDebounceJob?.cancel()
            searchDebounceJob = null
            activeFetchJob?.cancel()
            activeFetchJob = null
        }
        _state.value = HistoryFeedState()
    }

    private fun applyQueryFilter(trimmedQuery: String) {
        val current = _state.value.filter
        if (current.trimmedQuery == trimmedQuery) return
        applyFilterChange(current.copy(q = trimmedQuery))
    }

    private fun applyFilterChange(nextFilter: HistoryFilter) {
        val hasExisting = hasLoadedPage(_state.value.loadState)
        _state.update { it.copy(filter = nextFilter) }
        startPageRequest(
            filter = nextFilter,
            page = 0,
            isFilterChange = hasExisting,
        )
    }

    private fun cancelPendingSearch() {
        synchronized(lock) {
            searchDebounceJob?.cancel()
            searchDebounceJob = null
        }
    }

    private fun loadCatalogOptions() {
        scope.launch {
            val examResult = runCatching { catalogOptionsCache.get(CatalogOptionType.EXAM) }.getOrNull()
            if (examResult is ApiResult.Success) {
                val valid = examResult.data.filter { it.code.isNotBlank() }
                _state.update { it.copy(examKinds = valid) }
            }
        }
        scope.launch {
            val subjectResult = runCatching { catalogOptionsCache.get(CatalogOptionType.SUBJECT) }.getOrNull()
            if (subjectResult is ApiResult.Success) {
                val map = LinkedHashMap<String, CatalogOptionItem>()
                for (item in subjectResult.data) {
                    if (item.code.isNotBlank()) {
                        map[item.code] = item
                    }
                }
                _state.update { it.copy(subjectMap = map) }
            }
        }
    }

    private fun startPageRequest(
        filter: HistoryFilter,
        page: Int,
        isFilterChange: Boolean,
        isStaleRetry: Boolean = false,
    ) {
        val key: RequestKey
        synchronized(lock) {
            requestSequence += 1L
            key = RequestKey(filter = filter, page = page, sequence = requestSequence)
            activeRequestKey = key
            if (page == 0) {
                activeFetchJob?.cancel()
            }
        }

        if (!connectivityMonitor.online.value) {
            synchronized(lock) {
                lastFailedRequest = FailedRequest(filter = filter, page = page)
            }
            _state.update { current ->
                current.copy(
                    isOfflineBlock = true,
                    isFilterRefreshing = false,
                    isLoadingMore = false,
                    isRetryingStale = false,
                )
            }
            return
        }

        _state.update { current ->
            val keepOld = hasLoadedPage(current.loadState) && (page > 0 || !current.isOfflineBlock)
            current.copy(
                loadState = if (keepOld) current.loadState else LoadState.Loading,
                isOfflineBlock = false,
                isFilterRefreshing = page == 0 && isFilterChange && keepOld,
                isLoadingMore = page > 0,
                isRetryingStale = isStaleRetry,
            )
        }

        val job = scope.launch {
            val result = try {
                fetchHistory(filter.toParams(page))
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            handleResponse(key = key, result = result)
        }
        synchronized(lock) {
            if (activeRequestKey == key) {
                activeFetchJob = job
            }
        }
    }

    private suspend fun handleResponse(
        key: RequestKey,
        result: ApiResult<PageResponseAttemptHistoryItem>,
    ) {
        val isStillActive = synchronized(lock) {
            activeRequestKey == key && _state.value.filter == key.filter
        }
        if (!isStillActive) {
            return
        }

        when (result) {
            is ApiResult.Success -> {
                synchronized(lock) {
                    if (lastFailedRequest?.filter == key.filter && lastFailedRequest?.page == key.page) {
                        lastFailedRequest = null
                    }
                }
                val response = result.data
                val now = wallClockMillis()
                _state.update { current ->
                    if (!isKeyCurrent(key)) return@update current
                    if (key.page == 0) {
                        val deduped = deduplicateByAttemptId(emptyList(), response.items)
                        val nextLoadState = if (deduped.isEmpty()) {
                            LoadState.Empty
                        } else {
                            LoadState.Loaded(
                                value = HistoryPage(
                                    items = deduped,
                                    currentPage = response.page,
                                    totalItems = response.totalItems,
                                    totalPages = response.totalPages,
                                    hasMore = response.page + 1 < response.totalPages,
                                ),
                                stale = false,
                            )
                        }
                        current.copy(
                            loadState = nextLoadState,
                            lastLoadedAtMillis = now,
                            isFilterRefreshing = false,
                            isLoadingMore = false,
                            isRetryingStale = false,
                            isOfflineBlock = false,
                        )
                    } else {
                        val existing = (current.loadState as? LoadState.Loaded)?.value
                            ?: return@update current.copy(
                                isLoadingMore = false,
                                isRetryingStale = false,
                            )
                        if (response.items.isEmpty()) {
                            current.copy(
                                loadState = LoadState.Loaded(
                                    value = existing.copy(
                                        totalItems = response.totalItems,
                                        totalPages = response.totalPages,
                                        hasMore = false,
                                    ),
                                    stale = false,
                                ),
                                lastLoadedAtMillis = now,
                                isLoadingMore = false,
                                isRetryingStale = false,
                                isOfflineBlock = false,
                            )
                        } else {
                            val merged = deduplicateByAttemptId(existing.items, response.items)
                            current.copy(
                                loadState = LoadState.Loaded(
                                    value = HistoryPage(
                                        items = merged,
                                        currentPage = response.page,
                                        totalItems = response.totalItems,
                                        totalPages = response.totalPages,
                                        hasMore = response.page + 1 < response.totalPages,
                                    ),
                                    stale = false,
                                ),
                                lastLoadedAtMillis = now,
                                isLoadingMore = false,
                                isRetryingStale = false,
                                isOfflineBlock = false,
                            )
                        }
                    }
                }
            }

            is ApiResult.HttpFailure -> {
                synchronized(lock) {
                    lastFailedRequest = FailedRequest(filter = key.filter, page = key.page)
                }
                val failureKind = if (result.statusCode == HTTP_FORBIDDEN) {
                    LoadFailureKind.Forbidden
                } else {
                    LoadFailureKind.Server
                }
                applyFailure(
                    key = key,
                    failed = LoadState.Failed(
                        kind = failureKind,
                        canRetry = true,
                        retryAfter = result.retryAfterSeconds,
                    ),
                )
                if (result.statusCode == HTTP_FORBIDDEN) {
                    runCatching { onForbidden() }
                }
            }

            is ApiResult.NetworkFailure -> {
                synchronized(lock) {
                    lastFailedRequest = FailedRequest(filter = key.filter, page = key.page)
                }
                if (!connectivityMonitor.online.value) {
                    _state.update { current ->
                        if (!isKeyCurrent(key)) {
                            current
                        } else {
                            current.copy(
                                isOfflineBlock = true,
                                isFilterRefreshing = false,
                                isLoadingMore = false,
                                isRetryingStale = false,
                            )
                        }
                    }
                } else {
                    applyFailure(
                        key = key,
                        failed = LoadState.Failed(
                            kind = LoadFailureKind.Server,
                            canRetry = true,
                        ),
                    )
                }
            }
        }
    }

    private fun applyFailure(
        key: RequestKey,
        failed: LoadState.Failed,
    ) {
        _state.update { current ->
            if (!isKeyCurrent(key)) return@update current
            val existing = current.loadState
            val nextLoadState = when (existing) {
                is LoadState.Loaded -> existing.copy(stale = true)
                is LoadState.Empty -> LoadState.Loaded(
                    value = HistoryPage(
                        items = emptyList(),
                        currentPage = 0,
                        totalItems = 0,
                        totalPages = 0,
                        hasMore = false,
                    ),
                    stale = true,
                )
                else -> failed
            }
            current.copy(
                loadState = nextLoadState,
                isFilterRefreshing = false,
                isLoadingMore = false,
                isRetryingStale = false,
                isOfflineBlock = false,
            )
        }
    }

    private fun isKeyCurrent(key: RequestKey): Boolean = synchronized(lock) {
        activeRequestKey == key && _state.value.filter == key.filter
    }

    private fun hasLoadedPage(loadState: LoadState<HistoryPage>): Boolean =
        loadState is LoadState.Loaded || loadState is LoadState.Empty

    private fun deduplicateByAttemptId(
        existing: List<AttemptHistoryItem>,
        incoming: List<AttemptHistoryItem>,
    ): List<AttemptHistoryItem> {
        val seenIds = HashSet<UUID>(existing.size + incoming.size)
        val merged = ArrayList<AttemptHistoryItem>(existing.size + incoming.size)
        for (item in existing) {
            val id = item.attemptId
            if (id == null || seenIds.add(id)) {
                merged.add(item)
            }
        }
        for (item in incoming) {
            val id = item.attemptId
            if (id == null || seenIds.add(id)) {
                merged.add(item)
            }
        }
        return merged
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        private const val HTTP_FORBIDDEN = 403

        fun fromApi(
            api: MyAttemptsResultsApi,
            catalogOptionsCache: CatalogOptionsCache,
            connectivityMonitor: ConnectivityMonitor,
            onForbidden: suspend () -> Unit = {},
            wallClockMillis: () -> Long = System::currentTimeMillis,
            debounceDelay: suspend (Long) -> Unit = { delay(it) },
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): HistoryFeed = HistoryFeed(
            fetchHistory = { params ->
                safeApiCall {
                    api.listHistory(
                        q = params.q,
                        kind = params.kind,
                        scope = params.scope,
                        examId = null,
                        from = params.from,
                        to = params.to,
                        page = params.page,
                        size = params.size,
                    )
                }
            },
            catalogOptionsCache = catalogOptionsCache,
            connectivityMonitor = connectivityMonitor,
            onForbidden = onForbidden,
            wallClockMillis = wallClockMillis,
            debounceDelay = debounceDelay,
            scope = scope,
        )
    }
}
