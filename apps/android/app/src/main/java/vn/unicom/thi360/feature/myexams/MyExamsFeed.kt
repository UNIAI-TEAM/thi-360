package vn.unicom.thi360.feature.myexams

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
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.generated.api.MyExamsApi
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState

data class MyExamsFeedState(
    val loadState: LoadState<List<MyExamCard>> = LoadState.Loading,
    val lastLoadedAtMillis: Long? = null,
    val isRefreshing: Boolean = false,
    val isPullRefreshing: Boolean = false,
)

class MyExamsFeed(
    private val fetchExams: suspend () -> ApiResult<List<MyExamCard>>,
    private val connectivityMonitor: ConnectivityMonitor,
    private val onForbidden: suspend () -> Unit = {},
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(MyExamsFeedState())
    val state: StateFlow<MyExamsFeedState> = _state.asStateFlow()

    private val lock = Any()
    private var inFlightJob: Job? = null
    private var requestEpoch: Long = 0L

    fun onAppear() {
        val current = _state.value
        val hasCached = hasLoadedData(current.loadState)
        if (!hasCached && current.isRefreshing) {
            return
        }
        startLoad(
            allowAutoRetry = !hasCached,
            isPullToRefresh = false,
            keepExisting = hasCached,
        )
    }

    fun retry() {
        val current = _state.value
        if (current.isRefreshing) return
        val hasCached = hasLoadedData(current.loadState)
        startLoad(
            allowAutoRetry = false,
            isPullToRefresh = false,
            keepExisting = hasCached,
        )
    }

    fun pullToRefresh() {
        val current = _state.value
        if (current.isRefreshing) return
        val hasCached = hasLoadedData(current.loadState)
        startLoad(
            allowAutoRetry = false,
            isPullToRefresh = true,
            keepExisting = hasCached,
        )
    }

    fun clear() {
        synchronized(lock) {
            requestEpoch += 1L
            inFlightJob?.cancel()
            inFlightJob = null
        }
        _state.value = MyExamsFeedState()
    }

    private fun startLoad(
        allowAutoRetry: Boolean,
        isPullToRefresh: Boolean,
        keepExisting: Boolean,
    ) {
        val myEpoch: Long
        synchronized(lock) {
            if (_state.value.isRefreshing && keepExisting) {
                return
            }
            requestEpoch += 1L
            myEpoch = requestEpoch
            inFlightJob?.cancel()
            _state.update { current ->
                current.copy(
                    loadState = if (keepExisting) current.loadState else LoadState.Loading,
                    isRefreshing = true,
                    isPullRefreshing = isPullToRefresh,
                )
            }
            inFlightJob = scope.launch {
                executeLoad(
                    epoch = myEpoch,
                    allowAutoRetry = allowAutoRetry,
                    keepExisting = keepExisting,
                )
            }
        }
    }

    private suspend fun executeLoad(
        epoch: Long,
        allowAutoRetry: Boolean,
        keepExisting: Boolean,
    ) {
        if (!connectivityMonitor.online.value) {
            applyFailure(
                epoch = epoch,
                keepExisting = keepExisting,
                failed = LoadState.Failed(kind = LoadFailureKind.Offline, canRetry = true),
            )
            return
        }

        val maxAttempts = if (allowAutoRetry) INITIAL_MAX_ATTEMPTS else 1
        var attempt = 0
        var lastResult: ApiResult<List<MyExamCard>>? = null

        while (attempt < maxAttempts) {
            if (!isCurrentEpoch(epoch)) return
            attempt += 1
            val result = try {
                fetchExams()
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            if (!isCurrentEpoch(epoch)) return
            lastResult = result

            when (result) {
                is ApiResult.Success -> {
                    val items = result.data
                    val now = wallClockMillis()
                    _state.update { current ->
                        if (!isCurrentEpoch(epoch)) {
                            current
                        } else {
                            MyExamsFeedState(
                                loadState = if (items.isEmpty()) {
                                    LoadState.Empty
                                } else {
                                    LoadState.Loaded(value = items, stale = false)
                                },
                                lastLoadedAtMillis = now,
                                isRefreshing = false,
                                isPullRefreshing = false,
                            )
                        }
                    }
                    return
                }

                is ApiResult.HttpFailure -> {
                    if (result.statusCode == HTTP_FORBIDDEN) {
                        applyFailure(
                            epoch = epoch,
                            keepExisting = keepExisting,
                            failed = LoadState.Failed(
                                kind = LoadFailureKind.Forbidden,
                                canRetry = true,
                            ),
                        )
                        runCatching { onForbidden() }
                        return
                    }
                    val retryable = result.statusCode in 500..599
                    if (!retryable || attempt >= maxAttempts) {
                        applyFailure(
                            epoch = epoch,
                            keepExisting = keepExisting,
                            failed = LoadState.Failed(
                                kind = LoadFailureKind.Server,
                                canRetry = true,
                                retryAfter = result.retryAfterSeconds,
                            ),
                        )
                        return
                    }
                    retryDelay(RETRY_DELAY_MS)
                }

                is ApiResult.NetworkFailure -> {
                    if (!connectivityMonitor.online.value) {
                        applyFailure(
                            epoch = epoch,
                            keepExisting = keepExisting,
                            failed = LoadState.Failed(
                                kind = LoadFailureKind.Offline,
                                canRetry = true,
                            ),
                        )
                        return
                    }
                    if (attempt >= maxAttempts) {
                        applyFailure(
                            epoch = epoch,
                            keepExisting = keepExisting,
                            failed = LoadState.Failed(
                                kind = LoadFailureKind.Server,
                                canRetry = true,
                            ),
                        )
                        return
                    }
                    retryDelay(RETRY_DELAY_MS)
                }
            }
        }

        if (lastResult == null) {
            applyFailure(
                epoch = epoch,
                keepExisting = keepExisting,
                failed = LoadState.Failed(kind = LoadFailureKind.Server, canRetry = true),
            )
        }
    }

    private fun applyFailure(
        epoch: Long,
        keepExisting: Boolean,
        failed: LoadState.Failed,
    ) {
        _state.update { current ->
            if (!isCurrentEpoch(epoch)) {
                current
            } else {
                val existing = current.loadState
                val nextLoadState = when {
                    keepExisting && existing is LoadState.Loaded -> existing.copy(stale = true)
                    keepExisting && existing is LoadState.Empty -> LoadState.Loaded(
                        value = emptyList(),
                        stale = true,
                    )
                    else -> failed
                }
                current.copy(
                    loadState = nextLoadState,
                    isRefreshing = false,
                    isPullRefreshing = false,
                )
            }
        }
    }

    private fun isCurrentEpoch(epoch: Long): Boolean = synchronized(lock) { requestEpoch == epoch }

    private fun hasLoadedData(loadState: LoadState<List<MyExamCard>>): Boolean =
        loadState is LoadState.Loaded || loadState is LoadState.Empty

    companion object {
        const val INITIAL_MAX_ATTEMPTS = 3
        private const val HTTP_FORBIDDEN = 403
        private const val RETRY_DELAY_MS = 120L

        fun fromApi(
            api: MyExamsApi,
            connectivityMonitor: ConnectivityMonitor,
            onForbidden: suspend () -> Unit = {},
            wallClockMillis: () -> Long = System::currentTimeMillis,
            retryDelay: suspend (Long) -> Unit = { delay(it) },
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): MyExamsFeed = MyExamsFeed(
            fetchExams = {
                when (val result = safeApiCall { api.listMyExams() }) {
                    is ApiResult.Success -> ApiResult.Success(result.data.items)
                    is ApiResult.HttpFailure -> result
                    is ApiResult.NetworkFailure -> result
                }
            },
            connectivityMonitor = connectivityMonitor,
            onForbidden = onForbidden,
            wallClockMillis = wallClockMillis,
            retryDelay = retryDelay,
            scope = scope,
        )
    }
}
