package vn.unicom.thi360.feature.result

import androidx.compose.runtime.Immutable
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed

@Immutable
data class ResultUiState(
    val attemptId: String,
    val loadState: LoadState<AttemptResultResponse> = LoadState.Loading,
    val pollPhase: PollPhase = PollPhase.Idle,
    val isReloadingFromTimeout: Boolean = false,
    val isRetrying: Boolean = false,
    val examKinds: Map<String, CatalogOptionItem> = emptyMap(),
    val subjects: Map<String, CatalogOptionItem> = emptyMap(),
) {
    val isGradingTimedOut: Boolean
        get() = pollPhase is PollPhase.TimedOut
}

class ResultViewModel(
    private val attemptId: String,
    private val repository: ResultRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val scope: CoroutineScope,
    private val poller: GradingPoller = GradingPoller(),
) : Closeable {
    private val _state = MutableStateFlow(ResultUiState(attemptId = attemptId))
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    private val fetchMutex = Mutex()
    private var hasStartedInitialLoad: Boolean = false
    private var leftComposition: Boolean = false
    private var catalogsRequested: Boolean = false
    private var fetchInFlight: Boolean = false

    private val observersJob: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            poller.phase.collect { phase ->
                _state.update { it.copy(pollPhase = phase) }
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            poller.isReloading.collect { reloading ->
                _state.update { it.copy(isReloadingFromTimeout = reloading) }
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            var lastOnline = connectivityMonitor.online.value
            connectivityMonitor.online.collect { isOnline ->
                val becameOnline = !lastOnline && isOnline
                lastOnline = isOnline
                val currentLoad = _state.value.loadState
                if (becameOnline && currentLoad is LoadState.Failed && currentLoad.kind == LoadFailureKind.Offline) {
                    retry()
                }
            }
        }
    }

    fun onEnterComposition() {
        if (!hasStartedInitialLoad) {
            onInitialAppear()
        } else if (leftComposition) {
            leftComposition = false
            onReappearFromSubRoute()
        }
    }

    fun onLeaveComposition() {
        leftComposition = true
        poller.stop()
    }

    fun onInitialAppear() {
        if (hasStartedInitialLoad) return
        hasStartedInitialLoad = true
        leftComposition = false
        ensureCatalogOptionsLoaded()
        scope.launch {
            fetchResult(isRetryButton = false)
        }
    }

    fun onReappearFromSubRoute() {
        ensureCatalogOptionsLoaded()
        scope.launch {
            fetchResult(isRetryButton = false)
        }
    }

    fun onBackground() {
        poller.onBackground()
    }

    fun onForeground() {
        poller.onForeground(scope) {
            fetchResult(isRetryButton = false)
        }
    }

    fun retry() {
        if (fetchInFlight) return
        ensureCatalogOptionsLoaded()
        scope.launch {
            fetchResult(isRetryButton = true)
        }
    }

    fun reloadFromGradingTimeout() {
        poller.reloadFromTimeout(scope) {
            fetchResult(isRetryButton = false)
        }
    }

    private fun ensureCatalogOptionsLoaded() {
        if (catalogsRequested) return
        catalogsRequested = true
        scope.launch {
            val examDeferred = async { repository.loadExamKinds() }
            val subjectDeferred = async { repository.loadSubjects() }
            val exams = examDeferred.await()
            val subjects = subjectDeferred.await()
            _state.update { current ->
                current.copy(
                    examKinds = if (exams.isNotEmpty()) exams else current.examKinds,
                    subjects = if (subjects.isNotEmpty()) subjects else current.subjects,
                )
            }
        }
    }

    private suspend fun fetchResult(isRetryButton: Boolean): AttemptResultResponse.State? {
        return fetchMutex.withLock {
            fetchInFlight = true
            val priorLoaded = (_state.value.loadState as? LoadState.Loaded)?.value
            if (isRetryButton) {
                _state.update { current ->
                    current.copy(
                        isRetrying = true,
                        loadState = if (priorLoaded == null) LoadState.Loading else current.loadState,
                    )
                }
            } else if (priorLoaded == null) {
                _state.update { it.copy(loadState = LoadState.Loading) }
            }

            try {
                if (!connectivityMonitor.online.value) {
                    applyFailure(
                        failed = LoadState.Failed(kind = LoadFailureKind.Offline, canRetry = true),
                        priorLoaded = priorLoaded,
                    )
                    return@withLock null
                }

                when (val outcome = repository.result(attemptId)) {
                    is ApiResult.Success -> {
                        val response = outcome.data
                        _state.update { current ->
                            current.copy(
                                loadState = LoadState.Loaded(value = response, stale = false),
                                isRetrying = false,
                            )
                        }
                        poller.onResultState(response.state, scope) {
                            fetchResult(isRetryButton = false)
                        }
                        response.state
                    }

                    is ApiResult.HttpFailure -> {
                        val failed = outcome.toLoadFailed()
                        applyFailure(failed = failed, priorLoaded = priorLoaded)
                        null
                    }

                    is ApiResult.NetworkFailure -> {
                        val failed = outcome.toLoadFailed(isOnline = connectivityMonitor.online.value)
                        applyFailure(failed = failed, priorLoaded = priorLoaded)
                        null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                fetchInFlight = false
                _state.update { it.copy(isRetrying = false) }
            }
        }
    }

    private fun applyFailure(
        failed: LoadState.Failed,
        priorLoaded: AttemptResultResponse?,
    ) {
        val isTerminalRouteError = failed.kind is LoadFailureKind.NotFound ||
            (failed.kind is LoadFailureKind.Conflict && failed.kind.code == ATTEMPT_NOT_FINISHED_CODE)
        if (priorLoaded != null && !isTerminalRouteError) {
            _state.update { current ->
                current.copy(
                    loadState = LoadState.Loaded(value = priorLoaded, stale = true),
                    isRetrying = false,
                )
            }
        } else {
            poller.stop()
            _state.update { current ->
                current.copy(
                    loadState = failed,
                    isRetrying = false,
                )
            }
        }
    }

    override fun close() {
        poller.stop()
        observersJob.cancel()
    }

    companion object {
        const val ATTEMPT_NOT_FINISHED_CODE: String = "ATTEMPT_NOT_FINISHED"
    }
}
