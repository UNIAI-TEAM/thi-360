package vn.unicom.thi360.feature.prepare

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.OpenAttemptView
import vn.unicom.thi360.core.network.generated.model.PreChoiceInput
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.feature.prepare.waiting.WAITING_BUSY_THRESHOLD_MS
import vn.unicom.thi360.feature.prepare.waiting.WaitingPoller
import vn.unicom.thi360.feature.prepare.waiting.WaitingRetry
import vn.unicom.thi360.feature.prepare.waiting.WaitingRetryOutcome

data class PrepareSavedSnapshot(
    val selectedOptionCode: String? = null,
    val selectedSubjectCodes: List<String> = emptyList(),
    val hasExplicitSelection: Boolean = false,
    val waitingAttemptId: String? = null,
    val waitingKind: String? = null,
    val waitingElapsedMs: Long = 0L,
    val waitingAbortReason: String? = null,
    val waitingPreChoiceOption: String? = null,
    val waitingPreChoiceSubjects: List<String>? = null,
) {
    companion object {
        const val KIND_POLLING = "polling"
        const val KIND_BUSY = "busy"
        const val KIND_FAILED = "failed"
    }
}

class PrepareViewModel(
    val entitlementId: String,
    private val api: PrepareApi,
    private val startContextStore: StartContextStore,
    private val scope: CoroutineScope,
    private val startCommand: StartCommand = StartCommand(api),
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val localeProvider: () -> AppLocale = { AppLocale.VI },
    private val isOnlineProvider: () -> Boolean = { true },
    onOpenAttempt: (String) -> Unit = {},
) {
    @Volatile
    private var openAttemptCallback: ((String) -> Unit)? = onOpenAttempt

    @Volatile
    private var pendingOpenAttemptId: String? = null

    @Volatile
    private var currentLocaleProvider: () -> AppLocale = localeProvider

    @Volatile
    private var currentIsOnlineProvider: () -> Boolean = isOnlineProvider

    fun updateRuntimeBindings(
        localeProvider: () -> AppLocale,
        isOnlineProvider: () -> Boolean,
    ) {
        currentLocaleProvider = localeProvider
        currentIsOnlineProvider = isOnlineProvider
    }

    fun attachOpenAttemptHandler(handler: (String) -> Unit) {
        openAttemptCallback = handler
        val pending = pendingOpenAttemptId
        if (pending != null) {
            pendingOpenAttemptId = null
            handler(pending)
        }
    }

    fun detachOpenAttemptHandler(handler: (String) -> Unit) {
        if (openAttemptCallback === handler) {
            openAttemptCallback = null
        }
    }

    private fun dispatchOpenAttempt(attemptId: String) {
        val callback = openAttemptCallback
        if (callback != null) {
            pendingOpenAttemptId = null
            callback(attemptId)
        } else {
            pendingOpenAttemptId = attemptId
        }
    }

    private val waitingRetry = WaitingRetry(
        startCommand = startCommand,
        startContextStore = startContextStore,
    )

    private val _state = MutableStateFlow(PrepareScreenState())
    val state: StateFlow<PrepareScreenState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var activePoller: WaitingPoller? = null
    private var foreground: Boolean = true
    private var initialLoadTriggered: Boolean = false
    private var restoredSnapshot: PrepareSavedSnapshot? = null

    fun restoreSnapshot(snapshot: PrepareSavedSnapshot?) {
        if (snapshot == null) return
        restoredSnapshot = snapshot
        if (snapshot.hasExplicitSelection) {
            val restoredSelection = PreChoiceSelection(
                optionCode = snapshot.selectedOptionCode,
                subjectCodes = snapshot.selectedSubjectCodes.toCollection(LinkedHashSet()),
            )
            _state.update { current ->
                val loaded = (current.load as? LoadState.Loaded)?.value
                current.copy(
                    selection = restoredSelection,
                    gate = loaded?.let { resolveStartGate(it, restoredSelection) } ?: current.gate,
                )
            }
        }
        val restoredAttemptId = snapshot.waitingAttemptId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (restoredAttemptId != null && _state.value.waiting == null) {
            val restoredPreChoice = snapshot.waitingPreChoiceOption?.let { code ->
                PreChoiceInput(
                    optionCode = code,
                    subjectCodes = snapshot.waitingPreChoiceSubjects,
                )
            }
            val savedCtx = startContextStore.read(restoredAttemptId)
            when (snapshot.waitingKind) {
                PrepareSavedSnapshot.KIND_FAILED -> {
                    _state.update {
                        it.copy(
                            waiting = WaitingPhase.Failed(snapshot.waitingAbortReason),
                            waitingAttemptId = restoredAttemptId,
                            waitingContext = savedCtx,
                            waitingInMemoryPreChoice = restoredPreChoice,
                            waitingAbortReason = snapshot.waitingAbortReason,
                        )
                    }
                }

                PrepareSavedSnapshot.KIND_BUSY -> {
                    startWaitingPoller(
                        attemptId = restoredAttemptId,
                        context = savedCtx,
                        inMemoryPreChoice = restoredPreChoice,
                        initialElapsedMs = maxOf(snapshot.waitingElapsedMs, WAITING_BUSY_THRESHOLD_MS),
                    )
                }

                PrepareSavedSnapshot.KIND_POLLING -> {
                    startWaitingPoller(
                        attemptId = restoredAttemptId,
                        context = savedCtx,
                        inMemoryPreChoice = restoredPreChoice,
                        initialElapsedMs = snapshot.waitingElapsedMs,
                    )
                }
            }
        }
    }

    fun exportSnapshot(): PrepareSavedSnapshot {
        val current = _state.value
        val waitingPhase = current.waiting
        val waitingKind = when (waitingPhase) {
            is WaitingPhase.Polling -> PrepareSavedSnapshot.KIND_POLLING
            WaitingPhase.Busy -> PrepareSavedSnapshot.KIND_BUSY
            is WaitingPhase.Failed,
            WaitingPhase.Retrying,
            is WaitingPhase.RetryRefused,
            -> PrepareSavedSnapshot.KIND_FAILED
            null -> null
        }
        val abortReason = (waitingPhase as? WaitingPhase.Failed)?.abortReason ?: current.waitingAbortReason
        val elapsed = activePoller?.currentElapsedMs()
            ?: (waitingPhase as? WaitingPhase.Polling)?.elapsedMs
            ?: if (waitingPhase == WaitingPhase.Busy) WAITING_BUSY_THRESHOLD_MS else 0L
        return PrepareSavedSnapshot(
            selectedOptionCode = current.selection.optionCode,
            selectedSubjectCodes = current.selection.subjectCodes.toList(),
            hasExplicitSelection = current.load is LoadState.Loaded ||
                current.selection.optionCode != null ||
                current.selection.subjectCodes.isNotEmpty(),
            waitingAttemptId = current.waitingAttemptId?.toString(),
            waitingKind = waitingKind,
            waitingElapsedMs = elapsed,
            waitingAbortReason = abortReason,
            waitingPreChoiceOption = current.waitingInMemoryPreChoice?.optionCode,
            waitingPreChoiceSubjects = current.waitingInMemoryPreChoice?.subjectCodes,
        )
    }

    fun ensureLoaded() {
        if (initialLoadTriggered) return
        initialLoadTriggered = true
        load(maxAutoRetries = 1)
    }

    fun retryLoad() {
        initialLoadTriggered = true
        load(maxAutoRetries = 0)
    }

    fun selectOption(optionCode: String) {
        val current = _state.value
        val response = (current.load as? LoadState.Loaded)?.value ?: return
        if (current.selection.optionCode == optionCode) return
        val nextSelection = current.selection.selectOption(optionCode)
        startCommand.resetKey()
        _state.update {
            it.copy(
                selection = nextSelection,
                gate = resolveStartGate(response, nextSelection),
                start = if (it.start is StartState.Refused) StartState.Idle else it.start,
            )
        }
    }

    fun toggleSubject(subjectCode: String) {
        val current = _state.value
        val response = (current.load as? LoadState.Loaded)?.value ?: return
        val selectedOption = current.selection.selectedOption(response.preChoice) ?: return
        val maxSelect = PreChoiceSelection.requiredSubjectCount(selectedOption)
        if (maxSelect <= 0) return
        val nextSelection = current.selection.toggleSubject(subjectCode, maxSelect)
        if (nextSelection == current.selection) return
        startCommand.resetKey()
        _state.update {
            it.copy(
                selection = nextSelection,
                gate = resolveStartGate(response, nextSelection),
                start = if (it.start is StartState.Refused) StartState.Idle else it.start,
            )
        }
    }

    fun startAttempt() {
        val current = _state.value
        val response = (current.load as? LoadState.Loaded)?.value ?: return
        if (current.gate != StartGate.Open) return
        if (current.start == StartState.Sending || startCommand.isInFlight) return

        val preChoiceInput = current.selection.toPreChoiceInput(response)
        val startContext = buildStartContext(response, current.selection, preChoiceInput)

        _state.update { it.copy(start = StartState.Sending) }
        scope.launch {
            val outcome = startCommand.start(
                entitlementId = response.entitlementId,
                preChoice = preChoiceInput,
            ) ?: return@launch

            when (outcome) {
                is StartOutcome.Opened -> {
                    startContextStore.save(outcome.attemptId, startContext)
                    _state.update { it.copy(start = StartState.Idle) }
                    handleOpenedAttempt(
                        attemptId = outcome.attemptId,
                        openedState = outcome.state,
                        context = startContext,
                        inMemoryPreChoice = preChoiceInput,
                    )
                }

                is StartOutcome.Refused -> {
                    _state.update { it.copy(start = StartState.Refused(outcome.refusal)) }
                }
            }
        }
    }

    fun retryFromWaiting() {
        val current = _state.value
        val attemptId = current.waitingAttemptId ?: return
        if (current.waiting == WaitingPhase.Retrying || startCommand.isInFlight) return
        val response = (current.load as? LoadState.Loaded)?.value

        _state.update { it.copy(waiting = WaitingPhase.Retrying) }
        scope.launch {
            val outcome = waitingRetry.retry(
                attemptId = attemptId,
                origin = WaitingOrigin.Prepare,
                entitlementId = response?.entitlementId ?: parseUuidOrNull(entitlementId),
                prepareResponse = response,
                inMemoryPreChoice = current.waitingInMemoryPreChoice,
                inMemoryContext = current.waitingContext,
                fallbackContextBuilder = { payload ->
                    response?.let { buildStartContext(it, current.selection, payload) }
                },
            )
            when (outcome) {
                WaitingRetryOutcome.AlreadyInFlight -> Unit

                is WaitingRetryOutcome.Restarted -> {
                    stopWaitingPoller()
                    handleOpenedAttempt(
                        attemptId = outcome.newAttemptId,
                        openedState = outcome.state,
                        context = outcome.context ?: current.waitingContext,
                        inMemoryPreChoice = outcome.preChoice,
                    )
                }

                is WaitingRetryOutcome.Refused -> {
                    _state.update {
                        it.copy(
                            waiting = WaitingPhase.RetryRefused(outcome.refusal),
                        )
                    }
                }

                WaitingRetryOutcome.ReloadPrepare,
                is WaitingRetryOutcome.OpenPrepare,
                WaitingRetryOutcome.GoToMyExams,
                -> {
                    stopWaitingPoller()
                    startCommand.resetKey()
                    _state.update {
                        it.copy(
                            selection = PreChoiceSelection(),
                            start = StartState.Idle,
                            waiting = null,
                            waitingAttemptId = null,
                            waitingContext = null,
                            waitingInMemoryPreChoice = null,
                            waitingAbortReason = null,
                        )
                    }
                    retryLoad()
                }
            }
        }
    }

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        activePoller?.setForeground(isForeground)
    }

    fun stopWaiting() {
        stopWaitingPoller()
    }

    fun clear() {
        loadJob?.cancel()
        loadJob = null
        pendingOpenAttemptId = null
        stopWaitingPoller()
    }

    private fun load(maxAutoRetries: Int) {
        loadJob?.cancel()
        val parsedId = parseUuidOrNull(entitlementId)
        if (parsedId == null) {
            _state.update {
                it.copy(
                    load = LoadState.Failed(
                        kind = LoadFailureKind.NotFound,
                        canRetry = false,
                    ),
                )
            }
            return
        }

        _state.update { it.copy(load = LoadState.Loading) }
        loadJob = scope.launch {
            var attempt = 0
            while (true) {
                val result = api.prepare(parsedId)
                when (result) {
                    is ApiResult.Success -> {
                        applyLoadedResponse(result.data)
                        return@launch
                    }

                    is ApiResult.HttpFailure -> {
                        if (result.statusCode == HTTP_NOT_FOUND) {
                            _state.update {
                                it.copy(
                                    load = LoadState.Failed(
                                        kind = LoadFailureKind.NotFound,
                                        canRetry = false,
                                    ),
                                )
                            }
                            return@launch
                        }
                        if (attempt < maxAutoRetries) {
                            attempt++
                            continue
                        }
                        _state.update {
                            it.copy(
                                load = result.toLoadFailed().copy(canRetry = true),
                            )
                        }
                        return@launch
                    }

                    is ApiResult.NetworkFailure -> {
                        if (attempt < maxAutoRetries) {
                            attempt++
                            continue
                        }
                        _state.update {
                            it.copy(
                                load = result.toLoadFailed(currentIsOnlineProvider()).copy(canRetry = true),
                            )
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun applyLoadedResponse(response: AttemptPrepareResponse) {
        val current = _state.value
        val snapshot = restoredSnapshot
        val selection = if (snapshot != null && snapshot.hasExplicitSelection) {
            val restored = PreChoiceSelection(
                optionCode = snapshot.selectedOptionCode,
                subjectCodes = snapshot.selectedSubjectCodes.toCollection(LinkedHashSet()),
            )
            restoredSnapshot = null
            restored
        } else if (current.selection.optionCode != null || current.selection.subjectCodes.isNotEmpty()) {
            current.selection
        } else {
            PreChoiceSelection.initial(response.preChoice)
        }
        val gate = resolveStartGate(response, selection)

        val openAttempt = response.openAttempt
        val openStartingId = if (
            current.waiting == null &&
            openAttempt?.state == OpenAttemptView.State.STARTING
        ) {
            openAttempt.attemptId
        } else {
            null
        }

        val computedContext = buildStartContext(
            response = response,
            selection = selection,
            preChoiceInput = null,
        )

        _state.update {
            it.copy(
                load = LoadState.Loaded(response),
                selection = selection,
                gate = gate,
                waitingContext = it.waitingContext ?: if (openStartingId != null) {
                    startContextStore.read(openStartingId) ?: computedContext
                } else {
                    null
                },
            )
        }

        if (openStartingId != null) {
            val savedCtx = startContextStore.read(openStartingId) ?: computedContext
            startWaitingPoller(
                attemptId = openStartingId,
                context = savedCtx,
                inMemoryPreChoice = null,
                initialElapsedMs = 0L,
            )
        }
    }

    private fun handleOpenedAttempt(
        attemptId: UUID,
        openedState: StartOutcome.OpenedState,
        context: StartContext?,
        inMemoryPreChoice: PreChoiceInput?,
    ) {
        when (openedState) {
            StartOutcome.OpenedState.InProgress,
            StartOutcome.OpenedState.Closed,
            -> {
                stopWaitingPoller()
                _state.update {
                    it.copy(
                        waiting = null,
                        waitingAttemptId = null,
                    )
                }
                dispatchOpenAttempt(attemptId.toString())
            }

            StartOutcome.OpenedState.Starting -> {
                startWaitingPoller(
                    attemptId = attemptId,
                    context = context,
                    inMemoryPreChoice = inMemoryPreChoice,
                    initialElapsedMs = 0L,
                )
            }

            is StartOutcome.OpenedState.Aborted -> {
                stopWaitingPoller()
                _state.update {
                    it.copy(
                        waiting = WaitingPhase.Failed(openedState.abortReason),
                        waitingAttemptId = attemptId,
                        waitingContext = context,
                        waitingInMemoryPreChoice = inMemoryPreChoice,
                        waitingAbortReason = openedState.abortReason,
                    )
                }
            }
        }
    }

    private fun startWaitingPoller(
        attemptId: UUID,
        context: StartContext?,
        inMemoryPreChoice: PreChoiceInput?,
        initialElapsedMs: Long,
    ) {
        stopWaitingPoller()
        val initialPhase = if (initialElapsedMs >= WAITING_BUSY_THRESHOLD_MS) {
            WaitingPhase.Busy
        } else {
            WaitingPhase.Polling(initialElapsedMs.coerceAtLeast(0L))
        }
        _state.update {
            it.copy(
                waiting = initialPhase,
                waitingAttemptId = attemptId,
                waitingContext = context,
                waitingInMemoryPreChoice = inMemoryPreChoice,
                waitingAbortReason = null,
            )
        }
        val poller = WaitingPoller(
            attemptId = attemptId,
            clockMillis = clockMillis,
            api = api,
            initialElapsedMs = initialElapsedMs,
            onReady = { readyId ->
                stopWaitingPoller()
                dispatchOpenAttempt(readyId.toString())
            },
            onAborted = { reason ->
                _state.update { current ->
                    if (current.waitingAttemptId == attemptId) {
                        current.copy(
                            waiting = WaitingPhase.Failed(reason),
                            waitingAbortReason = reason,
                        )
                    } else {
                        current
                    }
                }
            },
            onPhaseChanged = { phase ->
                _state.update { current ->
                    if (current.waitingAttemptId == attemptId &&
                        current.waiting != WaitingPhase.Retrying &&
                        current.waiting !is WaitingPhase.RetryRefused
                    ) {
                        current.copy(waiting = phase)
                    } else {
                        current
                    }
                }
            },
        )
        activePoller = poller
        poller.start(coroutineScope = scope, initiallyForeground = foreground)
    }

    private fun stopWaitingPoller() {
        activePoller?.stop()
        activePoller = null
    }

    private fun buildStartContext(
        response: AttemptPrepareResponse,
        selection: PreChoiceSelection,
        preChoiceInput: PreChoiceInput?,
    ): StartContext {
        val orderedParts = response.parts.sortedBy { it.ord ?: 0 }
        val firstPart = orderedParts.firstOrNull()
        val metrics = PrepareMetrics.of(response, selection)
        val firstPartMinutes = if (response.timing == AttemptPrepareResponse.Timing.SHARED) {
            metrics.durationMinutes
        } else {
            firstPart?.durationMinutes
        }
        val examKindLabel = pickPrepareLocalizedName(
            nameVi = response.examKind.nameVi,
            nameEn = response.examKind.nameEn,
            code = response.examKind.code,
            locale = currentLocaleProvider(),
        )
        return StartContext(
            entitlementId = response.entitlementId.toString(),
            preChoice = preChoiceInput,
            examTitle = response.examName,
            examKindLabel = examKindLabel,
            firstPartName = firstPart?.name,
            firstPartMinutes = firstPartMinutes,
            timing = response.timing.value,
        )
    }

    companion object {
        private const val HTTP_NOT_FOUND = 404

        internal fun parseUuidOrNull(raw: String): UUID? =
            runCatching { UUID.fromString(raw.trim()) }.getOrNull()
    }
}
