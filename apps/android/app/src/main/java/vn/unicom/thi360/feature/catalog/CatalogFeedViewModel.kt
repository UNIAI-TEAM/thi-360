package vn.unicom.thi360.feature.catalog

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.session.RefreshOutcome
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed

data class CatalogFeedState(
    val searchInput: String = "",
    val query: CatalogQuery = CatalogQuery(),
    val filters: StorefrontFiltersData? = null,
    val filtersLoaded: Boolean = false,
    val loadState: LoadState<CatalogPage> = LoadState.Loading,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val isOffline: Boolean = false,
    val accessMap: Map<String, AccessCell> = emptyMap(),
    val selectedProductCode: String? = null,
    val savedScrollIndex: Int = 0,
    val savedScrollOffset: Int = 0,
    val hasInitialLoaded: Boolean = false,
)

class CatalogFeedController(
    private val gateway: CatalogGateway,
    private val sessionManager: SessionManager,
    private val connectivityMonitor: ConnectivityMonitor?,
    private val debounceWaiter: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(
        CatalogFeedState(isOffline = connectivityMonitor?.online?.value == false),
    )
    val state: StateFlow<CatalogFeedState> = _state.asStateFlow()

    private val generation = AtomicLong(0L)
    @Volatile
    private var activeLoadJob: Job? = null
    @Volatile
    private var nextPageJob: Job? = null
    @Volatile
    private var searchDebounceJob: Job? = null
    private val accessJobs = ConcurrentHashMap<String, Job>()
    private var connectivityJob: Job? = null

    init {
        if (connectivityMonitor != null) {
            connectivityJob = scope.launch {
                connectivityMonitor.online.collect { online ->
                    val wasOffline = _state.value.isOffline
                    _state.update { it.copy(isOffline = !online) }
                    if (wasOffline && online) {
                        val currentLoad = _state.value.loadState
                        if (currentLoad is LoadState.Failed && currentLoad.kind == LoadFailureKind.Offline) {
                            retryInitial()
                        }
                    }
                }
            }
        }
    }

    fun ensureInitialized() {
        val current = _state.value
        if (current.hasInitialLoaded || activeLoadJob?.isActive == true) {
            return
        }
        loadInitial(resetFiltersNeeded = !current.filtersLoaded)
    }

    fun updateScrollPosition(index: Int, offset: Int) {
        _state.update {
            if (it.savedScrollIndex == index && it.savedScrollOffset == offset) {
                it
            } else {
                it.copy(savedScrollIndex = index, savedScrollOffset = offset)
            }
        }
    }

    fun selectProduct(code: String?) {
        _state.update { it.copy(selectedProductCode = code) }
    }

    fun onSearchInputChanged(raw: String) {
        _state.update { it.copy(searchInput = raw) }
        searchDebounceJob?.cancel()
        searchDebounceJob = scope.launch {
            debounceWaiter(SEARCH_DEBOUNCE_MS)
            applySearchQuery(raw)
        }
    }

    fun submitSearchNow() {
        searchDebounceJob?.cancel()
        searchDebounceJob = null
        applySearchQuery(_state.value.searchInput)
    }

    fun clearSearchInput() {
        searchDebounceJob?.cancel()
        searchDebounceJob = null
        _state.update { it.copy(searchInput = "") }
        applySearchQuery("")
    }

    private fun applySearchQuery(raw: String) {
        val currentQuery = _state.value.query
        val nextQuery = currentQuery.withSearch(raw)
        if (nextQuery == currentQuery) return
        applyQueryChange(nextQuery)
    }

    fun selectKind(kindCode: String?) {
        val nextQuery = _state.value.query.withKind(kindCode)
        if (nextQuery == _state.value.query) return
        applyQueryChange(nextQuery)
    }

    fun selectFormat(format: CatalogFormat?) {
        val nextQuery = _state.value.query.withFormat(format)
        if (nextQuery == _state.value.query) return
        applyQueryChange(nextQuery)
    }

    fun selectPart(kindCode: String, partCode: String) {
        val nextQuery = _state.value.query.withPart(kindCode, partCode)
        if (nextQuery == _state.value.query) return
        applyQueryChange(nextQuery)
    }

    fun selectSubject(subjectCode: String?) {
        val nextQuery = _state.value.query.withSubject(subjectCode)
        if (nextQuery == _state.value.query) return
        applyQueryChange(nextQuery)
    }

    fun clearPartAndSubject() {
        val nextQuery = _state.value.query.clearPartAndSubject()
        if (nextQuery == _state.value.query) return
        applyQueryChange(nextQuery)
    }

    fun selectSort(sort: CatalogSort) {
        val nextQuery = _state.value.query.withSort(sort)
        if (nextQuery == _state.value.query) return
        applyQueryChange(nextQuery)
    }

    fun clearAllFilters() {
        searchDebounceJob?.cancel()
        searchDebounceJob = null
        val cleared = CatalogQuery()
        val hadSearch = _state.value.searchInput.isNotEmpty()
        val changed = cleared != _state.value.query || hadSearch
        if (!changed) return
        _state.update { it.copy(searchInput = "", query = cleared, savedScrollIndex = 0, savedScrollOffset = 0) }
        loadInitial(resetFiltersNeeded = !_state.value.filtersLoaded)
    }

    fun retryInitial() {
        loadInitial(resetFiltersNeeded = true)
    }

    fun pullToRefresh() {
        if (_state.value.isRefreshing) return
        val currentLoad = _state.value.loadState
        if (currentLoad !is LoadState.Loaded) {
            loadInitial(resetFiltersNeeded = true)
            return
        }

        val myGeneration = generation.incrementAndGet()
        activeLoadJob?.cancel()
        nextPageJob?.cancel()
        nextPageJob = null

        _state.update { it.copy(isRefreshing = true, refreshFailed = false) }
        val currentQuery = _state.value.query

        activeLoadJob = scope.launch {
            val filtersDeferred = async { gateway.getFilters() }
            val productsDeferred = async { gateway.listProducts(currentQuery.toParams(page = 0)) }
            val filtersResult = filtersDeferred.await()
            val productsResult = productsDeferred.await()

            if (myGeneration != generation.get()) return@launch

            if (filtersResult !is ApiResult.Success) {
                _state.update { state ->
                    val existing = state.loadState as? LoadState.Loaded
                    state.copy(
                        loadState = existing?.copy(stale = true) ?: state.loadState,
                        isRefreshing = false,
                        refreshFailed = true,
                    )
                }
                return@launch
            }
            _state.update { it.copy(filters = filtersResult.data, filtersLoaded = true) }

            when (productsResult) {
                is ApiResult.Success -> {
                    val pageData = productsResult.data
                    val deduped = dedupeByCode(pageData.items)
                    if (deduped.isEmpty()) {
                        _state.update {
                            it.copy(
                                loadState = LoadState.Empty,
                                isRefreshing = false,
                                refreshFailed = false,
                                savedScrollIndex = 0,
                                savedScrollOffset = 0,
                                hasInitialLoaded = true,
                            )
                        }
                    } else {
                        val hasMore = hasMorePages(pageData, loadedCount = deduped.size)
                        val newPage = CatalogPage(
                            items = deduped,
                            total = pageData.totalItems,
                            nextPage = 1,
                            tail = if (hasMore) TailState.Idle else TailState.End,
                        )
                        val refreshedCodes = deduped.map { it.code }
                        _state.update { state ->
                            val nextAccess = refreshedCodes.associateWith { AccessCell.Loading }
                            state.copy(
                                loadState = LoadState.Loaded(newPage, stale = false),
                                isRefreshing = false,
                                refreshFailed = false,
                                accessMap = nextAccess,
                                savedScrollIndex = 0,
                                savedScrollOffset = 0,
                                hasInitialLoaded = true,
                            )
                        }
                        fetchAccessBatch(refreshedCodes, myGeneration, markLoading = false)
                    }
                }

                is ApiResult.HttpFailure,
                is ApiResult.NetworkFailure,
                -> {
                    _state.update { state ->
                        val existing = state.loadState as? LoadState.Loaded
                        state.copy(
                            loadState = existing?.copy(stale = true) ?: state.loadState,
                            isRefreshing = false,
                            refreshFailed = true,
                        )
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        val current = _state.value
        val loaded = current.loadState as? LoadState.Loaded ?: return
        val page = loaded.value
        if (page.tail == TailState.Loading || page.tail == TailState.End) return
        if (nextPageJob?.isActive == true) return

        val myGeneration = generation.get()
        val requestedPage = page.nextPage
        val currentQuery = current.query

        _state.update { state ->
            val currentLoaded = state.loadState as? LoadState.Loaded ?: return@update state
            state.copy(
                loadState = currentLoaded.copy(
                    value = currentLoaded.value.copy(tail = TailState.Loading),
                ),
            )
        }

        nextPageJob = scope.launch {
            val result = gateway.listProducts(currentQuery.toParams(page = requestedPage))
            if (myGeneration != generation.get()) return@launch

            when (result) {
                is ApiResult.Success -> {
                    val pageData = result.data
                    var addedCodes: List<String> = emptyList()
                    _state.update { state ->
                        val currentLoaded = state.loadState as? LoadState.Loaded ?: return@update state
                        val existingPage = currentLoaded.value
                        if (pageData.items.isEmpty()) {
                            return@update state.copy(
                                loadState = currentLoaded.copy(
                                    value = existingPage.copy(
                                        total = existingPage.items.size.toLong(),
                                        tail = TailState.End,
                                    ),
                                ),
                            )
                        }
                        val existingCodes = existingPage.items.mapTo(mutableSetOf()) { it.code }
                        val freshItems = dedupeByCode(pageData.items).filter { it.code !in existingCodes }
                        val mergedItems = existingPage.items + freshItems
                        addedCodes = freshItems.map { it.code }
                        val reachedEnd = !hasMorePages(pageData, loadedCount = mergedItems.size)
                        val updatedAccess = state.accessMap.toMutableMap()
                        addedCodes.forEach { code ->
                            if (code !in updatedAccess) {
                                updatedAccess[code] = AccessCell.Loading
                            }
                        }
                        state.copy(
                            loadState = currentLoaded.copy(
                                value = CatalogPage(
                                    items = mergedItems,
                                    total = pageData.totalItems,
                                    nextPage = requestedPage + 1,
                                    tail = if (reachedEnd) TailState.End else TailState.Idle,
                                ),
                            ),
                            accessMap = updatedAccess,
                        )
                    }
                    if (addedCodes.isNotEmpty()) {
                        fetchAccessBatch(addedCodes, myGeneration, markLoading = false)
                    }
                }

                is ApiResult.HttpFailure -> {
                    _state.update { state ->
                        val currentLoaded = state.loadState as? LoadState.Loaded ?: return@update state
                        state.copy(
                            loadState = currentLoaded.copy(
                                value = currentLoaded.value.copy(
                                    tail = TailState.Error(retryAfterSeconds = result.retryAfterSeconds),
                                ),
                            ),
                        )
                    }
                }

                is ApiResult.NetworkFailure -> {
                    val onlineNow = connectivityMonitor?.online?.value ?: true
                    _state.update { state ->
                        val currentLoaded = state.loadState as? LoadState.Loaded ?: return@update state
                        state.copy(
                            loadState = currentLoaded.copy(
                                value = currentLoaded.value.copy(
                                    tail = TailState.Error(isOffline = !onlineNow),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun retryAccess(codes: List<String>) {
        val cleaned = codes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return
        fetchAccessBatch(cleaned, generation.get(), markLoading = true)
    }

    fun refreshAccessOnReturn() {
        val loaded = _state.value.loadState as? LoadState.Loaded ?: return
        val codes = loaded.value.items.map { it.code }
        if (codes.isEmpty()) return
        val myGeneration = generation.get()
        val chunks = AccessButtons.chunkCodes(codes)
        chunks.forEach { chunk ->
            fetchAccessBatch(chunk, myGeneration, markLoading = false)
        }
    }

    fun clear() {
        generation.incrementAndGet()
        activeLoadJob?.cancel()
        nextPageJob?.cancel()
        searchDebounceJob?.cancel()
        accessJobs.values.forEach { it.cancel() }
        accessJobs.clear()
        connectivityJob?.cancel()
        scope.cancel()
    }

    private fun applyQueryChange(nextQuery: CatalogQuery) {
        _state.update {
            it.copy(
                query = nextQuery,
                savedScrollIndex = 0,
                savedScrollOffset = 0,
                refreshFailed = false,
            )
        }
        loadInitial(resetFiltersNeeded = !_state.value.filtersLoaded)
    }

    private fun loadInitial(resetFiltersNeeded: Boolean) {
        val myGeneration = generation.incrementAndGet()
        activeLoadJob?.cancel()
        nextPageJob?.cancel()
        nextPageJob = null
        accessJobs.values.forEach { it.cancel() }
        accessJobs.clear()

        val currentQuery = _state.value.query
        _state.update {
            it.copy(
                loadState = LoadState.Loading,
                isRefreshing = false,
                refreshFailed = false,
                accessMap = emptyMap(),
            )
        }

        activeLoadJob = scope.launch {
            val isOnline = connectivityMonitor?.online?.value ?: true
            val (filtersResult, productsResult) = coroutineScope {
                val f = if (resetFiltersNeeded) async { gateway.getFilters() } else null
                val p = async { gateway.listProducts(currentQuery.toParams(page = 0)) }
                Pair(f?.await(), p.await())
            }

            if (myGeneration != generation.get()) return@launch

            val filtersFailure = when (filtersResult) {
                is ApiResult.HttpFailure -> filtersResult.toLoadFailed()
                is ApiResult.NetworkFailure -> {
                    val onlineNow = connectivityMonitor?.online?.value ?: isOnline
                    filtersResult.toLoadFailed(isOnline = onlineNow)
                }
                else -> null
            }
            if (filtersFailure != null) {
                val productsRetryAfter = (productsResult as? ApiResult.HttpFailure)?.retryAfterSeconds
                val combinedRetryAfter = listOfNotNull(filtersFailure.retryAfter, productsRetryAfter).maxOrNull()
                _state.update {
                    it.copy(
                        loadState = filtersFailure.copy(retryAfter = combinedRetryAfter),
                        hasInitialLoaded = true,
                    )
                }
                return@launch
            }
            if (filtersResult is ApiResult.Success) {
                _state.update { it.copy(filters = filtersResult.data, filtersLoaded = true) }
            }

            when (productsResult) {
                is ApiResult.Success -> {
                    val pageData = productsResult.data
                    val deduped = dedupeByCode(pageData.items)
                    if (deduped.isEmpty()) {
                        _state.update {
                            it.copy(
                                loadState = LoadState.Empty,
                                hasInitialLoaded = true,
                            )
                        }
                    } else {
                        val hasMore = hasMorePages(pageData, loadedCount = deduped.size)
                        val catalogPage = CatalogPage(
                            items = deduped,
                            total = pageData.totalItems,
                            nextPage = 1,
                            tail = if (hasMore) TailState.Idle else TailState.End,
                        )
                        val codes = deduped.map { it.code }
                        _state.update { state ->
                            state.copy(
                                loadState = LoadState.Loaded(catalogPage, stale = false),
                                accessMap = codes.associateWith { AccessCell.Loading },
                                hasInitialLoaded = true,
                            )
                        }
                        fetchAccessBatch(codes, myGeneration, markLoading = false)
                    }
                }

                is ApiResult.HttpFailure -> {
                    _state.update {
                        it.copy(
                            loadState = productsResult.toLoadFailed(),
                            hasInitialLoaded = true,
                        )
                    }
                }

                is ApiResult.NetworkFailure -> {
                    val onlineNow = connectivityMonitor?.online?.value ?: isOnline
                    _state.update {
                        it.copy(
                            loadState = productsResult.toLoadFailed(isOnline = onlineNow),
                            hasInitialLoaded = true,
                        )
                    }
                }
            }
        }
    }

    private fun fetchAccessBatch(
        codes: List<String>,
        expectedGeneration: Long,
        markLoading: Boolean,
    ) {
        val cleaned = codes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return

        if (markLoading) {
            _state.update { state ->
                val updated = state.accessMap.toMutableMap()
                cleaned.forEach { updated[it] = AccessCell.Loading }
                state.copy(accessMap = updated)
            }
        }

        val batchKey = cleaned.joinToString(",")
        accessJobs.remove(batchKey)?.cancel()
        accessJobs[batchKey] = scope.launch {
            try {
                val result = queryAccessWithForbiddenRecovery(cleaned)
                if (expectedGeneration != generation.get()) return@launch
                when (result) {
                    is ApiResult.Success -> {
                        val byCode = result.data.associateBy { it.code }
                        _state.update { state ->
                            val updated = state.accessMap.toMutableMap()
                            cleaned.forEach { code ->
                                val row = byCode[code] ?: ExamAccessRow.none(code)
                                updated[code] = AccessCell.Ready(row)
                            }
                            state.copy(accessMap = updated)
                        }
                    }

                    is ApiResult.HttpFailure,
                    is ApiResult.NetworkFailure,
                    -> {
                        _state.update { state ->
                            val updated = state.accessMap.toMutableMap()
                            cleaned.forEach { code ->
                                updated[code] = AccessCell.Failed
                            }
                            state.copy(accessMap = updated)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                accessJobs.remove(batchKey)
            }
        }
    }

    private suspend fun queryAccessWithForbiddenRecovery(codes: List<String>): ApiResult<List<ExamAccessRow>> {
        val first = gateway.queryExamAccess(codes)
        if (first is ApiResult.HttpFailure && first.statusCode == HTTP_FORBIDDEN) {
            return when (sessionManager.refreshSession(failedAccessToken = null)) {
                is RefreshOutcome.Refreshed -> gateway.queryExamAccess(codes)
                else -> first
            }
        }
        return first
    }

    private fun hasMorePages(pageData: ProductPageData, loadedCount: Int): Boolean {
        if (pageData.items.isEmpty()) return false
        if (pageData.totalPages > 0 && pageData.page + 1 >= pageData.totalPages) return false
        if (pageData.totalItems > 0 && loadedCount.toLong() >= pageData.totalItems) return false
        return true
    }

    private fun dedupeByCode(items: List<ProductCardItem>): List<ProductCardItem> {
        val seen = linkedSetOf<String>()
        val result = ArrayList<ProductCardItem>(items.size)
        for (item in items) {
            if (seen.add(item.code)) {
                result.add(item)
            }
        }
        return result
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        private const val HTTP_FORBIDDEN = 403
    }
}

class CatalogFeedStore {
    private data class ScopeKey(
        val accountId: String?,
        val sessionGeneration: Long,
        val gatewayIdentity: Int,
    )

    private var activeKey: ScopeKey? = null
    private var activeController: CatalogFeedController? = null

    fun getOrCreate(
        accountId: String?,
        sessionGeneration: Long,
        dependencies: CatalogDependencies,
    ): CatalogFeedController {
        val key = ScopeKey(
            accountId = accountId,
            sessionGeneration = sessionGeneration,
            gatewayIdentity = System.identityHashCode(dependencies.gateway),
        )
        val existing = activeController
        if (existing != null && activeKey == key) {
            return existing
        }
        existing?.clear()
        val created = CatalogFeedController(
            gateway = dependencies.gateway,
            sessionManager = dependencies.sessionManager,
            connectivityMonitor = dependencies.connectivityMonitor,
            debounceWaiter = dependencies.debounceWaiter,
        )
        activeKey = key
        activeController = created
        return created
    }

    fun clearAll() {
        activeController?.clear()
        activeController = null
        activeKey = null
    }

    companion object {
        val Default = CatalogFeedStore()
    }
}
