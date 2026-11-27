package vn.unicom.thi360.feature.catalog

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

data class ProductDetailState(
    val code: String,
    val loadState: LoadState<ProductOutcome> = LoadState.Loading,
    val accessCell: AccessCell = AccessCell.Loading,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)

class ProductDetailController(
    private val code: String,
    private val gateway: CatalogGateway,
    private val sessionManager: SessionManager,
    private val connectivityMonitor: ConnectivityMonitor?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(ProductDetailState(code = code))
    val state: StateFlow<ProductDetailState> = _state.asStateFlow()

    private val generation = AtomicLong(0L)
    @Volatile
    private var loadJob: Job? = null
    @Volatile
    private var accessJob: Job? = null
    private var connectivityJob: Job? = null

    init {
        if (connectivityMonitor != null) {
            connectivityJob = scope.launch {
                var wasOffline = !connectivityMonitor.online.value
                connectivityMonitor.online.collect { online ->
                    if (wasOffline && online) {
                        val currentLoad = _state.value.loadState
                        if (currentLoad is LoadState.Failed && currentLoad.kind == LoadFailureKind.Offline) {
                            retry()
                        }
                    }
                    wasOffline = !online
                }
            }
        }
    }

    fun loadInitial() {
        loadDetail(isPullToRefresh = false)
    }

    fun retry() {
        loadDetail(isPullToRefresh = false)
    }

    fun pullToRefresh() {
        if (_state.value.isRefreshing) return
        val existing = _state.value.loadState
        if (existing !is LoadState.Loaded) {
            loadDetail(isPullToRefresh = false)
            return
        }
        loadDetail(isPullToRefresh = true)
    }

    fun retryAccess() {
        val loaded = _state.value.loadState as? LoadState.Loaded ?: return
        val productCode = accessQueryCodeFor(loaded.value) ?: return
        fetchAccess(productCode, expectedGeneration = generation.get(), markLoading = true)
    }

    fun refreshAccessOnReturn() {
        val loaded = _state.value.loadState as? LoadState.Loaded ?: return
        val productCode = accessQueryCodeFor(loaded.value) ?: return
        fetchAccess(productCode, expectedGeneration = generation.get(), markLoading = false)
    }

    fun clear() {
        generation.incrementAndGet()
        loadJob?.cancel()
        accessJob?.cancel()
        connectivityJob?.cancel()
        scope.cancel()
    }

    private fun loadDetail(isPullToRefresh: Boolean) {
        val myGeneration = generation.incrementAndGet()
        loadJob?.cancel()
        accessJob?.cancel()

        _state.update { state ->
            if (isPullToRefresh) {
                state.copy(isRefreshing = true, refreshFailed = false)
            } else {
                state.copy(
                    loadState = LoadState.Loading,
                    accessCell = AccessCell.Loading,
                    isRefreshing = false,
                    refreshFailed = false,
                )
            }
        }

        loadJob = scope.launch {
            val isOnline = connectivityMonitor?.online?.value ?: true
            when (val result = gateway.getProductDetail(code)) {
                is CatalogDetailResult.Success -> {
                    if (myGeneration != generation.get()) return@launch
                    val outcome = result.outcome
                    val queryCode = accessQueryCodeFor(outcome)
                    _state.update {
                        it.copy(
                            loadState = LoadState.Loaded(outcome, stale = false),
                            accessCell = if (queryCode != null) AccessCell.Loading else it.accessCell,
                            isRefreshing = false,
                            refreshFailed = false,
                        )
                    }
                    if (queryCode != null) {
                        fetchAccess(queryCode, expectedGeneration = myGeneration, markLoading = false)
                    }
                }

                is CatalogDetailResult.HttpFailure -> {
                    if (myGeneration != generation.get()) return@launch
                    if (isPullToRefresh) {
                        _state.update { state ->
                            val existing = state.loadState as? LoadState.Loaded
                            state.copy(
                                loadState = existing?.copy(stale = true) ?: state.loadState,
                                isRefreshing = false,
                                refreshFailed = true,
                            )
                        }
                    } else {
                        val failed = ApiResult.HttpFailure(
                            statusCode = result.statusCode,
                            error = null,
                            retryAfterSeconds = result.retryAfterSeconds,
                        ).toLoadFailed()
                        _state.update {
                            it.copy(
                                loadState = failed,
                                isRefreshing = false,
                                refreshFailed = false,
                            )
                        }
                    }
                }

                is CatalogDetailResult.NetworkFailure -> {
                    if (myGeneration != generation.get()) return@launch
                    if (isPullToRefresh) {
                        _state.update { state ->
                            val existing = state.loadState as? LoadState.Loaded
                            state.copy(
                                loadState = existing?.copy(stale = true) ?: state.loadState,
                                isRefreshing = false,
                                refreshFailed = true,
                            )
                        }
                    } else {
                        val onlineNow = connectivityMonitor?.online?.value ?: isOnline
                        val failed = ApiResult.NetworkFailure(result.cause).toLoadFailed(isOnline = onlineNow)
                        _state.update {
                            it.copy(
                                loadState = failed,
                                isRefreshing = false,
                                refreshFailed = false,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun fetchAccess(
        productCode: String,
        expectedGeneration: Long,
        markLoading: Boolean,
    ) {
        if (markLoading) {
            _state.update { it.copy(accessCell = AccessCell.Loading) }
        }
        accessJob?.cancel()
        accessJob = scope.launch {
            try {
                val result = queryAccessWithForbiddenRecovery(productCode)
                if (expectedGeneration != generation.get()) return@launch
                when (result) {
                    is ApiResult.Success -> {
                        val row = result.data.firstOrNull { it.code == productCode }
                            ?: ExamAccessRow.none(productCode)
                        _state.update { it.copy(accessCell = AccessCell.Ready(row)) }
                    }

                    is ApiResult.HttpFailure,
                    is ApiResult.NetworkFailure,
                    -> {
                        _state.update { it.copy(accessCell = AccessCell.Failed) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    private suspend fun queryAccessWithForbiddenRecovery(productCode: String): ApiResult<List<ExamAccessRow>> {
        val codes = listOf(productCode)
        val first = gateway.queryExamAccess(codes)
        if (first is ApiResult.HttpFailure && first.statusCode == HTTP_FORBIDDEN) {
            return when (sessionManager.refreshSession(failedAccessToken = null)) {
                is RefreshOutcome.Refreshed -> gateway.queryExamAccess(codes)
                else -> first
            }
        }
        return first
    }

    private fun accessQueryCodeFor(outcome: ProductOutcome): String? = when (outcome) {
        is ProductOutcome.Exam -> outcome.detail.code
        is ProductOutcome.Bundle -> if (outcome.sellable) outcome.detail.code else null
        is ProductOutcome.Gone,
        ProductOutcome.NotFound,
        ProductOutcome.BundleUnavailable,
        -> null
    }

    companion object {
        private const val HTTP_FORBIDDEN = 403
    }
}
