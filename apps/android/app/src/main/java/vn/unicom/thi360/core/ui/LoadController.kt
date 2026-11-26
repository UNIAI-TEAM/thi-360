package vn.unicom.thi360.core.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.RetryAfterGate

class LoadController<T>(
    private val scope: CoroutineScope,
    private val connectivityMonitor: ConnectivityMonitor,
    private val accountIdFlow: StateFlow<String?> = MutableStateFlow(null),
    private val isEmpty: (T) -> Boolean = { false },
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val fetch: suspend () -> ApiResult<T>,
) {
    private val loadMutex = Mutex()
    private val retryGate = RetryAfterGate(clockMillis)

    private var cachedValue: T? = null
    private var hasCachedValue: Boolean = false
    private var cachedAccountId: String? = accountIdFlow.value

    private val _state = MutableStateFlow<LoadState<T>>(LoadState.Loading)
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _staleFailure = MutableStateFlow<LoadState.Failed?>(null)
    val staleFailure: StateFlow<LoadState.Failed?> = _staleFailure.asStateFlow()

    private var currentJob: Job? = null
    private val observersJob: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            var lastOnline = connectivityMonitor.online.value
            connectivityMonitor.online.collect { isOnline ->
                val becameOnline = !lastOnline && isOnline
                lastOnline = isOnline
                if (becameOnline && shouldAutoRetryOnOnline()) {
                    load()
                }
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            var observedAccountId = accountIdFlow.value
            accountIdFlow.collect { nextAccountId ->
                if (nextAccountId != observedAccountId) {
                    observedAccountId = nextAccountId
                    onAccountChanged(nextAccountId)
                }
            }
        }
    }

    fun load(): Job = launchFetch(isPullToRefresh = false)

    fun retry(): Job? {
        if (!retryGate.canRetry()) return null
        return launchFetch(isPullToRefresh = false)
    }

    fun refresh(): Job = launchFetch(isPullToRefresh = true)

    fun clearMemory() {
        currentJob?.cancel()
        currentJob = null
        cachedValue = null
        hasCachedValue = false
        cachedAccountId = accountIdFlow.value
        retryGate.reset()
        _isRefreshing.value = false
        _staleFailure.value = null
        _state.value = LoadState.Loading
    }

    fun close() {
        currentJob?.cancel()
        observersJob.cancel()
    }

    private fun onAccountChanged(nextAccountId: String?) {
        val shouldClear = nextAccountId == null ||
            (cachedAccountId != null && cachedAccountId != nextAccountId)
        if (shouldClear) {
            clearMemory()
        }
        cachedAccountId = nextAccountId
    }

    private fun shouldAutoRetryOnOnline(): Boolean {
        val current = _state.value
        if (current is LoadState.Failed && current.kind == LoadFailureKind.Offline) {
            return true
        }
        val stale = _staleFailure.value
        return current is LoadState.Loaded && current.stale && stale?.kind == LoadFailureKind.Offline
    }

    private fun launchFetch(isPullToRefresh: Boolean): Job {
        val job = scope.launch {
            loadMutex.withLock {
                if (!isPullToRefresh && !retryGate.canRetry()) {
                    return@withLock
                }
                val startAccountId = accountIdFlow.value
                val hadData = hasCachedValue && (cachedAccountId == null || cachedAccountId == startAccountId)
                val priorData = cachedValue

                if (isPullToRefresh) {
                    _isRefreshing.value = true
                } else if (!hadData) {
                    _state.value = LoadState.Loading
                }

                try {
                    if (!connectivityMonitor.online.value) {
                        val offline = LoadState.Failed(kind = LoadFailureKind.Offline, canRetry = true)
                        applyFailure(offline, hadData, priorData, startAccountId)
                        return@withLock
                    }

                    when (val result = fetch()) {
                        is ApiResult.Success -> {
                            if (accountIdFlow.value != startAccountId) {
                                return@withLock
                            }
                            retryGate.reset()
                            _staleFailure.value = null
                            if (isEmpty(result.data)) {
                                cachedValue = null
                                hasCachedValue = false
                                cachedAccountId = startAccountId
                                _state.value = LoadState.Empty
                            } else {
                                cachedValue = result.data
                                hasCachedValue = true
                                cachedAccountId = startAccountId
                                _state.value = LoadState.Loaded(value = result.data, stale = false)
                            }
                        }

                        is ApiResult.HttpFailure -> {
                            val failed = result.toLoadFailed()
                            retryGate.recordDelay(failed.retryAfter)
                            applyFailure(failed, hadData, priorData, startAccountId)
                        }

                        is ApiResult.NetworkFailure -> {
                            val failed = result.toLoadFailed(isOnline = connectivityMonitor.online.value)
                            applyFailure(failed, hadData, priorData, startAccountId)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
        currentJob = job
        return job
    }

    private fun applyFailure(
        failed: LoadState.Failed,
        hadData: Boolean,
        priorData: T?,
        startAccountId: String?,
    ) {
        if (accountIdFlow.value != startAccountId) {
            return
        }
        if (hadData && priorData != null) {
            _staleFailure.value = failed
            _state.value = LoadState.Loaded(value = priorData, stale = true)
        } else {
            _staleFailure.value = null
            _state.value = failed
        }
    }
}
