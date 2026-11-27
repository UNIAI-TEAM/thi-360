package vn.unicom.thi360.feature.result.review

import androidx.compose.runtime.Immutable
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.feature.result.ResultRepository

@Immutable
data class ReviewUiState(
    val attemptId: String,
    val loadState: LoadState<ReviewModel> = LoadState.Loading,
    val headerResult: AttemptResultResponse? = null,
    val selectedPartIndex: Int = 0,
    val activeGroupIndex: Int = 0,
    val isRetrying: Boolean = false,
) {
    val selectedPart: ReviewPart?
        get() {
            val parts = (loadState as? LoadState.Loaded)?.value?.parts.orEmpty()
            return parts.getOrNull(selectedPartIndex) ?: parts.firstOrNull()
        }

    val activeGroups: List<ReviewGroup>
        get() = selectedPart?.groups.orEmpty()
}

class ReviewViewModel(
    private val attemptId: String,
    private val repository: ResultRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val scope: CoroutineScope,
) : Closeable {
    private val _state = MutableStateFlow(ReviewUiState(attemptId = attemptId))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val loadMutex = Mutex()
    private var hasLoadedOnce: Boolean = false
    private var fetchInFlight: Boolean = false

    private val connectivityJob: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
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

    fun onAppear() {
        if (hasLoadedOnce) return
        hasLoadedOnce = true
        scope.launch {
            fetchReview()
        }
    }

    fun retry() {
        if (fetchInFlight) return
        scope.launch {
            fetchReview()
        }
    }

    fun selectPart(index: Int) {
        val parts = (_state.value.loadState as? LoadState.Loaded)?.value?.parts.orEmpty()
        if (index !in parts.indices) return
        _state.update { current ->
            current.copy(
                selectedPartIndex = index,
                activeGroupIndex = 0,
            )
        }
    }

    fun selectGroup(index: Int) {
        val groups = _state.value.activeGroups
        if (index !in groups.indices) return
        _state.update { it.copy(activeGroupIndex = index) }
    }

    fun previousGroup(): Int? {
        val current = _state.value.activeGroupIndex
        if (current <= 0) return null
        val target = current - 1
        _state.update { it.copy(activeGroupIndex = target) }
        return target
    }

    fun nextGroup(): Int? {
        val groups = _state.value.activeGroups
        val current = _state.value.activeGroupIndex
        if (current < 0 || current >= groups.lastIndex) return null
        val target = current + 1
        _state.update { it.copy(activeGroupIndex = target) }
        return target
    }

    private suspend fun fetchReview() {
        loadMutex.withLock {
            fetchInFlight = true
            _state.update { it.copy(loadState = LoadState.Loading, isRetrying = true) }
            try {
                if (!connectivityMonitor.online.value) {
                    _state.update {
                        it.copy(
                            loadState = LoadState.Failed(
                                kind = LoadFailureKind.Offline,
                                canRetry = true,
                            ),
                            isRetrying = false,
                        )
                    }
                    return@withLock
                }

                when (val outcome = repository.review(attemptId)) {
                    is ApiResult.Success -> {
                        val model = ReviewMapper.map(outcome.data)
                        val cachedHeader = repository.cachedResultIfFresh(attemptId)
                        _state.update {
                            it.copy(
                                loadState = LoadState.Loaded(value = model, stale = false),
                                headerResult = cachedHeader,
                                selectedPartIndex = 0,
                                activeGroupIndex = 0,
                                isRetrying = false,
                            )
                        }
                        if (cachedHeader == null) {
                            fetchHeaderResult()
                        }
                    }

                    is ApiResult.HttpFailure -> {
                        val errorCode = outcome.error?.code
                        val failed = if (outcome.statusCode == 403 && errorCode != REVIEW_NOT_INCLUDED_CODE) {
                            LoadState.Failed(kind = LoadFailureKind.Server, canRetry = true)
                        } else {
                            outcome.toLoadFailed()
                        }
                        _state.update {
                            it.copy(loadState = failed, isRetrying = false)
                        }
                    }

                    is ApiResult.NetworkFailure -> {
                        val failed = outcome.toLoadFailed(isOnline = connectivityMonitor.online.value)
                        _state.update {
                            it.copy(loadState = failed, isRetrying = false)
                        }
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

    private suspend fun fetchHeaderResult() {
        when (val resultOutcome = repository.result(attemptId)) {
            is ApiResult.Success -> {
                _state.update { it.copy(headerResult = resultOutcome.data) }
            }
            else -> Unit
        }
    }

    override fun close() {
        connectivityJob.cancel()
    }

    companion object {
        const val REVIEW_NOT_INCLUDED_CODE: String = "REVIEW_NOT_INCLUDED"
        const val REVIEW_NOT_AVAILABLE_CODE: String = "REVIEW_NOT_AVAILABLE"
    }
}
