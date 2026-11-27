package vn.unicom.thi360.feature.attemptengine

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ClaimSessionResponse
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed

class AttemptEngine(
    val attemptId: UUID,
    val accountId: String,
    private val api: AttemptApi,
    store: AccountScopedStore,
    clock: EngineClock = SystemEngineClock,
    private val connectivity: ConnectivityMonitor,
    private val scope: CoroutineScope,
    uuidGenerator: () -> UUID = UUID::randomUUID,
    startImmediately: Boolean = true,
) {
    init {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        require(store.accountId == accountId) {
            "AccountScopedStore accountId does not match engine accountId"
        }
    }

    private val draftStore = DraftStore(store)
    private val serverClock = ServerClock(clock)

    private val _state = MutableStateFlow(
        EngineState(
            network = if (connectivity.online.value) NetworkStatus.Online else NetworkStatus.Offline,
        ),
    )
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private var currentLoad: LoadState<Unit> = LoadState.Loading
    private var currentAttempt: AttemptStateResponse? = null
    private var currentSessionState: EditSessionState = EditSessionState.Acquiring
    private var claimFailureSaveStatus: SaveStatus? = null
    private var currentCommandState: CommandState = CommandState.Idle
    private var currentClosedReason: AttemptClosedReason? = null
    private var closedHint: AttemptClosedReason? = null
    private var attemptClosedEventEmitted = false
    private var isForeground = true
    private var isStopped = false

    private val handledDeadlines = HashSet<String>()
    private var clockTickerJob: Job? = null
    private var deadlineReloadJob: Job? = null
    private var connectivityJob: Job? = null
    private var bootstrapJob: Job? = null
    private var foregroundResumeJob: Job? = null
    private var onlineRecoveryJob: Job? = null

    private val positionTracker = PositionTracker(
        attemptId = attemptId,
        draftStore = draftStore,
        scope = scope,
    )

    private val saveQueue: SaveQueue = SaveQueue(
        attemptId = attemptId,
        api = api,
        draftStore = draftStore,
        scope = scope,
        callbacks = object : SaveQueueCallbacks {
            override fun activeSessionId(): UUID? = editSession.activeSessionId

            override fun isEditable(): Boolean = isEngineEditable()

            override fun isOnline(): Boolean = connectivity.online.value

            override fun consumePendingPosition(): EnginePosition? = positionTracker.consumeForSync()

            override fun restorePendingPositionIfUnchanged(position: EnginePosition) {
                positionTracker.restoreIfUnchanged(position)
            }

            override fun hasPendingPosition(): Boolean = positionTracker.hasPendingSync

            override fun onServerNow(serverNow: OffsetDateTime) {
                reanchorClock(serverNow)
            }

            override fun onStateChanged() {
                publishState()
            }

            override fun onStaleDetected() {
                scope.launch {
                    val fresh = reloadServerState()
                    if (!isStopped &&
                        fresh?.state == AttemptStateResponse.State.IN_PROGRESS &&
                        editSession.activeSessionId != null &&
                        connectivity.online.value &&
                        saveQueue.hasAnyPendingWork()
                    ) {
                        saveQueue.flushNow()
                    }
                }
            }

            override fun onPartClosed(partId: PartId) {
                _events.tryEmit(EngineEvent.PartClosed(partId))
                scope.launch { reloadServerState() }
            }

            override fun onAttemptClosed(reason: AttemptClosedReason) {
                closedHint = closedHint ?: reason
                scope.launch {
                    val fresh = reloadServerState()
                    if (fresh == null && currentClosedReason == null) {
                        finalizeClosed(reason, keepStartContext = false)
                    }
                }
            }

            override fun onSessionConflict() {
                editSession.markEvicted(fromSave = true)
            }
        },
    )

    private val heartbeatLoop: HeartbeatLoop = HeartbeatLoop(
        attemptId = attemptId,
        api = api,
        scope = scope,
        callbacks = object : HeartbeatCallbacks {
            override fun activeSessionId(): UUID? = editSession.activeSessionId

            override fun isForeground(): Boolean = isForeground

            override fun isOnline(): Boolean = connectivity.online.value

            override fun onServerNow(serverNow: OffsetDateTime) {
                reanchorClock(serverNow)
            }

            override fun onAttemptClosedFromHeartbeat(reason: AttemptClosedReason?) {
                if (reason != null) {
                    closedHint = closedHint ?: reason
                }
                scope.launch {
                    val fresh = reloadServerState()
                    if (fresh == null && currentClosedReason == null) {
                        finalizeClosed(reason ?: AttemptClosedReason.NotOpen, keepStartContext = false)
                    }
                }
            }

            override fun onSessionRevokedFromHeartbeat() {
                editSession.markEvicted(fromSave = false)
            }
        },
    )

    private val editSession: EditSession = EditSession(
        attemptId = attemptId,
        api = api,
        draftStore = draftStore,
        scope = scope,
        uuidGenerator = uuidGenerator,
        callbacks = object : EditSessionCallbacks {
            override fun isOnline(): Boolean = connectivity.online.value

            override fun onSessionStateChanged(
                state: EditSessionState,
                claimFailureSaveStatus: SaveStatus?,
            ) {
                currentSessionState = state
                this@AttemptEngine.claimFailureSaveStatus = claimFailureSaveStatus
                publishState()
            }

            override fun onServerNow(serverNow: OffsetDateTime) {
                reanchorClock(serverNow)
            }

            override fun onClaimed(response: ClaimSessionResponse, fromTakeover: Boolean) {
                saveQueue.clearSessionConflictError()
                if (fromTakeover) {
                    scope.launch {
                        val fresh = reloadServerState(preferServerPosition = true)
                        if (isStopped) return@launch
                        if (fresh == null || fresh.state == AttemptStateResponse.State.IN_PROGRESS) {
                            if (isForeground) {
                                heartbeatLoop.restart()
                            }
                            syncPollingAndClock()
                            if (saveQueue.hasAnyPendingWork()) {
                                saveQueue.flushNow()
                            }
                        }
                    }
                } else {
                    if (isForeground) {
                        heartbeatLoop.start()
                    }
                    syncPollingAndClock()
                    if (saveQueue.hasAnyPendingWork()) {
                        saveQueue.flushNow()
                    }
                }
            }

            override fun onAttemptClosedFromSession(reason: AttemptClosedReason) {
                closedHint = closedHint ?: reason
                scope.launch {
                    val fresh = reloadServerState()
                    if (fresh == null && currentClosedReason == null) {
                        finalizeClosed(reason, keepStartContext = false)
                    }
                }
            }

            override fun onEvicted(fromSave: Boolean) {
                heartbeatLoop.stop()
                partPolling.stop()
                clockTickerJob?.cancel()
                clockTickerJob = null
                deadlineReloadJob?.cancel()
                deadlineReloadJob = null
                saveQueue.cancelAllPendingTimers()
                positionTracker.cancelPendingTimer()
                partCommands.clearOnEviction()
                currentCommandState = CommandState.Idle
                publishState()
                _events.tryEmit(EngineEvent.SessionLost)
            }
        },
    )

    private val partPolling: PartPolling = PartPolling(
        scope = scope,
        serverClock = serverClock,
        onReloadRequested = { reloadServerState() },
        onChoiceDeadlineExpired = {
            closedHint = closedHint ?: AttemptClosedReason.ChoiceTimeout
            publishState()
        },
    )

    private val partCommands: PartCommands = PartCommands(
        attemptId = attemptId,
        api = api,
        scope = scope,
        uuidGenerator = uuidGenerator,
        callbacks = object : PartCommandsCallbacks {
            override fun currentAttempt(): AttemptStateResponse? = currentAttempt

            override fun activeSessionId(): UUID? = editSession.activeSessionId

            override fun isClosed(): Boolean = currentClosedReason != null

            override fun setCommandState(state: CommandState) {
                currentCommandState = state
                publishState()
            }

            override fun onBeforeClose(target: CloseTarget) {
                _events.tryEmit(EngineEvent.BeforeClose(target))
            }

            override fun cancelPendingSaveTimers() {
                saveQueue.cancelAllPendingTimers()
                positionTracker.cancelPendingTimer()
            }

            override fun buildFinalAnswers(partId: PartId?) = saveQueue.buildFinalAnswers(partId)

            override fun applyFinalAnswerResults(
                results: List<vn.unicom.thi360.core.network.generated.model.FinalAnswerResult>,
            ) {
                saveQueue.applyFinalAnswerResults(results)
            }

            override fun onServerNow(serverNow: OffsetDateTime) {
                reanchorClock(serverNow)
            }

            override fun onChoiceWindowClosedHint() {
                closedHint = AttemptClosedReason.ChoiceTimeout
            }

            override fun onClosedHint(reason: AttemptClosedReason) {
                closedHint = reason
            }

            override fun clearAllLocalDataForSubmittedAttempt() {
                draftStore.clearAttemptData(
                    attemptId = attemptId,
                    knownItemIds = saveQueue.knownItemIds(),
                    keepStartContext = false,
                )
            }

            override suspend fun reloadState(): AttemptStateResponse? = reloadServerState()

            override fun onPartClosedSuccess(partId: PartId) {
                _events.tryEmit(EngineEvent.PartClosed(partId))
            }

            override fun onAttemptSubmittedSuccess(reason: AttemptClosedReason) {
                finalizeClosed(reason, keepStartContext = false)
            }

            override fun onSessionRevoked() {
                editSession.markEvicted(fromSave = false)
            }

            override fun onFlushRemainingAfterReconcile() {
                if (saveQueue.hasAnyPendingWork()) {
                    saveQueue.flushNow()
                }
            }
        },
    )

    init {
        observeConnectivity()
        if (startImmediately) {
            start()
        }
    }

    fun start() {
        if (isStopped) return
        if (bootstrapJob?.isActive == true) return
        bootstrapJob = scope.launch {
            bootstrap()
        }
    }

    fun answer(itemId: ItemId, value: AnswerValue) {
        if (isStopped) return
        saveQueue.recordAnswer(itemId, value)
    }

    fun flag(itemId: ItemId, flagged: Boolean) {
        if (isStopped) return
        saveQueue.recordFlag(itemId, flagged)
    }

    fun position(partId: PartId, itemId: ItemId?) {
        if (isStopped || currentClosedReason != null) return
        val attempt = currentAttempt
        val canSync = attempt?.state == AttemptStateResponse.State.IN_PROGRESS && isEngineEditable()
        positionTracker.recordUserPosition(
            position = EnginePosition(partId = partId, itemId = itemId),
            shouldSyncToServer = canSync,
            hasDirtyChanges = saveQueue.hasDirtyItems(),
            onFlushNow = { saveQueue.flushNow() },
            onDebouncedFlush = { saveQueue.flushNow() },
        )
        syncPollingAndClock()
        publishState()
    }

    fun takeover() {
        if (isStopped || currentClosedReason != null) return
        editSession.requestTakeover(
            onStarted = {
                currentCommandState = CommandState.Sending(AttemptCommandKind.Takeover)
                publishState()
            },
            onFinished = { succeeded, failureCode ->
                if (!succeeded) {
                    currentCommandState = if (failureCode != null) {
                        CommandState.Refused(failureCode)
                    } else {
                        CommandState.Idle
                    }
                    publishState()
                } else {
                    currentCommandState = CommandState.Idle
                    publishState()
                }
            },
        )
    }

    fun finishPart(partId: PartId) {
        if (isStopped) return
        partCommands.finishPart(partId)
    }

    fun submit() {
        if (isStopped) return
        partCommands.submit()
    }

    fun choosePart(partId: PartId, optionCode: String, subjectCodes: List<String>? = null) {
        if (isStopped) return
        partCommands.choosePart(partId = partId, optionCode = optionCode, subjectCodes = subjectCodes)
    }

    fun retryGeneration(partId: PartId) {
        if (isStopped) return
        partCommands.retryGeneration(partId)
    }

    fun refresh() {
        if (isStopped) return
        if (currentAttempt == null) {
            start()
        } else {
            scope.launch {
                reloadServerState()
            }
        }
    }

    fun reconcile() {
        if (isStopped) return
        partCommands.reconcile()
    }

    fun setForeground(foreground: Boolean) {
        if (isStopped || isForeground == foreground) return
        isForeground = foreground
        if (!foreground) {
            foregroundResumeJob?.cancel()
            foregroundResumeJob = null
            heartbeatLoop.stop()
            partPolling.stop()
            if (currentAttempt?.state == AttemptStateResponse.State.IN_PROGRESS &&
                currentClosedReason == null &&
                editSession.activeSessionId != null &&
                connectivity.online.value &&
                saveQueue.hasAnyPendingWork()
            ) {
                saveQueue.flushNow()
            }
            return
        }

        if (currentClosedReason != null || currentAttempt == null) return
        if (currentAttempt?.state != AttemptStateResponse.State.IN_PROGRESS) return
        when (val session = currentSessionState) {
            is EditSessionState.Editing -> {
                foregroundResumeJob?.cancel()
                foregroundResumeJob = scope.launch {
                    resumeForegroundEditing(session.sessionId)
                }
            }

            EditSessionState.Acquiring -> {
                syncPollingAndClock()
                if (connectivity.online.value) {
                    editSession.onOnline()
                }
            }

            is EditSessionState.OpenElsewhere,
            EditSessionState.Evicted,
            is EditSessionState.ReadOnly,
            -> Unit
        }
    }

    fun reportSessionRevoked() {
        if (isStopped) return
        editSession.markEvicted(fromSave = false)
    }

    fun close() {
        if (isStopped) return
        isStopped = true
        bootstrapJob?.cancel()
        connectivityJob?.cancel()
        foregroundResumeJob?.cancel()
        onlineRecoveryJob?.cancel()
        clockTickerJob?.cancel()
        deadlineReloadJob?.cancel()
        heartbeatLoop.stop()
        partPolling.stop()
        editSession.stop()
        saveQueue.stop()
        positionTracker.cancelPendingTimer()
        partCommands.stop()
    }

    private suspend fun bootstrap() {
        currentLoad = LoadState.Loading
        publishState()

        when (val result = api.getState(attemptId)) {
            is ApiResult.Success -> {
                if (isStopped) return
                val fresh = result.data
                applyLoadedAttempt(fresh, isInitialLoad = true, preferServerPosition = true)
                currentLoad = LoadState.Loaded(Unit)
                publishState()

                if (fresh.state == AttemptStateResponse.State.IN_PROGRESS) {
                    editSession.startClaim()
                }
            }

            is ApiResult.HttpFailure -> {
                if (isStopped) return
                currentLoad = if (result.statusCode == 404 || result.error?.code == CODE_ATTEMPT_NOT_FOUND) {
                    LoadState.Failed(kind = LoadFailureKind.NotFound, canRetry = false)
                } else {
                    result.toLoadFailed()
                }
                publishState()
            }

            is ApiResult.NetworkFailure -> {
                if (isStopped) return
                currentLoad = result.toLoadFailed(isOnline = connectivity.online.value)
                publishState()
            }
        }
    }

    private suspend fun reloadServerState(preferServerPosition: Boolean = false): AttemptStateResponse? {
        if (isStopped) return null
        return when (val result = api.getState(attemptId)) {
            is ApiResult.Success -> {
                if (isStopped) return null
                applyLoadedAttempt(
                    fresh = result.data,
                    isInitialLoad = false,
                    preferServerPosition = preferServerPosition,
                )
                currentLoad = LoadState.Loaded(Unit)
                publishState()
                result.data
            }

            is ApiResult.HttpFailure,
            is ApiResult.NetworkFailure,
            -> null
        }
    }

    private fun applyLoadedAttempt(
        fresh: AttemptStateResponse,
        isInitialLoad: Boolean,
        preferServerPosition: Boolean,
    ) {
        currentAttempt = fresh
        serverClock.reanchor(fresh.serverNow)

        val leafIds = fresh.parts.orEmpty()
            .flatMap { PartSelection.leafItems(it) }
            .mapNotNull { it.itemId }
        val storedDrafts = draftStore.readDrafts(attemptId, leafIds)
        saveQueue.reconcile(fresh, storedDrafts)

        val serverPos = PartSelection.parseServerPosition(fresh)
        val localPos = draftStore.readPosition(attemptId)
        val resolvedPos = if (isInitialLoad || preferServerPosition) {
            PartSelection.initialPosition(fresh, serverPos, localPos)
        } else {
            PartSelection.resolveNextPosition(fresh, positionTracker.current, localPos)
        }
        positionTracker.setResolvedPosition(resolvedPos)

        when (fresh.state) {
            AttemptStateResponse.State.IN_PROGRESS -> {
                currentClosedReason = null
                if (!isInitialLoad && currentSessionState is EditSessionState.ReadOnly) {
                    editSession.startClaim()
                }
                syncPollingAndClock()
            }

            AttemptStateResponse.State.STARTING -> {
                currentClosedReason = null
                editSession.markReadOnly(ReadOnlyReason.AttemptNotInProgress)
                stopActiveLoops()
            }

            AttemptStateResponse.State.ABORTED -> {
                finalizeClosed(
                    reason = AttemptClosedReason.Aborted(fresh.abortReason),
                    keepStartContext = true,
                )
            }

            else -> {
                val inferred = PartSelection.inferClosedReason(fresh, closedHint)
                    ?: AttemptClosedReason.Submitted
                finalizeClosed(reason = inferred, keepStartContext = false)
            }
        }
    }

    private fun finalizeClosed(reason: AttemptClosedReason, keepStartContext: Boolean) {
        currentClosedReason = reason
        stopActiveLoops()
        saveQueue.lockScope(partId = null)
        draftStore.clearAttemptData(
            attemptId = attemptId,
            knownItemIds = saveQueue.knownItemIds(),
            keepStartContext = keepStartContext,
        )
        editSession.markReadOnly(ReadOnlyReason.AttemptClosed)
        publishState()
        if (!attemptClosedEventEmitted) {
            attemptClosedEventEmitted = true
            _events.tryEmit(EngineEvent.AttemptClosed(reason))
        }
    }

    private suspend fun resumeForegroundEditing(sessionId: UUID) {
        try {
            val hbOutcome = heartbeatLoop.sendImmediate(sessionId)
            if (isStopped || !isForeground) return
            when (hbOutcome) {
                HeartbeatOutcome.Evicted,
                is HeartbeatOutcome.AttemptNotInProgress,
                is HeartbeatOutcome.ClosedConflict,
                HeartbeatOutcome.Unauthorized,
                -> return

                HeartbeatOutcome.Active,
                HeartbeatOutcome.IgnoredTransientError,
                -> Unit
            }

            val fresh = reloadServerState()
            if (isStopped || !isForeground) return
            if (fresh != null && fresh.state != AttemptStateResponse.State.IN_PROGRESS) {
                return
            }
            if (editSession.activeSessionId == sessionId && currentClosedReason == null) {
                heartbeatLoop.restart()
                syncPollingAndClock()
                if (connectivity.online.value && saveQueue.hasAnyPendingWork()) {
                    saveQueue.flushNow()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    private fun observeConnectivity() {
        var previousOnline = connectivity.online.value
        connectivityJob = scope.launch {
            connectivity.online.collect { isOnline ->
                if (isStopped) return@collect
                if (isOnline == previousOnline) return@collect
                previousOnline = isOnline
                if (!isOnline) {
                    saveQueue.onOffline()
                    editSession.onOffline()
                    publishState()
                } else {
                    saveQueue.onOnline()
                    publishState()
                    if (partCommands.isUnconfirmed) {
                        partCommands.reconcile()
                    }
                    when (currentSessionState) {
                        EditSessionState.Acquiring -> {
                            editSession.onOnline()
                        }

                        is EditSessionState.Editing -> {
                            if (currentAttempt?.state == AttemptStateResponse.State.IN_PROGRESS &&
                                currentClosedReason == null
                            ) {
                                onlineRecoveryJob?.cancel()
                                onlineRecoveryJob = scope.launch {
                                    val fresh = reloadServerState()
                                    if (isStopped) return@launch
                                    if (fresh != null &&
                                        fresh.state == AttemptStateResponse.State.IN_PROGRESS &&
                                        editSession.activeSessionId != null &&
                                        saveQueue.hasAnyPendingWork()
                                    ) {
                                        saveQueue.flushNow()
                                    }
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun reanchorClock(serverNow: OffsetDateTime) {
        serverClock.reanchor(serverNow)
        syncPollingAndClock()
        publishState()
    }

    private fun syncPollingAndClock() {
        val activePart = activePart()
        partPolling.sync(
            attempt = currentAttempt,
            activePart = activePart,
            isForeground = isForeground,
            isStopped = isStopped ||
                currentClosedReason != null ||
                currentSessionState is EditSessionState.Evicted ||
                currentSessionState is EditSessionState.OpenElsewhere,
        )
        syncClockTicker()
    }

    private fun syncClockTicker() {
        val attempt = currentAttempt
        if (isStopped ||
            currentClosedReason != null ||
            attempt == null ||
            attempt.state != AttemptStateResponse.State.IN_PROGRESS ||
            !serverClock.isAnchored
        ) {
            clockTickerJob?.cancel()
            clockTickerJob = null
            return
        }
        val countdown = serverClock.computeCountdown(attempt, activePart())
        if (countdown.target == CountdownTarget.None) {
            clockTickerJob?.cancel()
            clockTickerJob = null
            return
        }
        if (clockTickerJob?.isActive == true) {
            checkExamDeadlineExpiration()
            return
        }
        clockTickerJob = scope.launch {
            while (isActive && !isStopped && currentClosedReason == null) {
                publishState()
                checkExamDeadlineExpiration()
                val activeCountdown = serverClock.computeCountdown(currentAttempt, activePart())
                val sleepMs = if (activeCountdown.target != CountdownTarget.None && activeCountdown.remainingMs > 0L) {
                    activeCountdown.remainingMs.coerceAtMost(CLOCK_TICK_INTERVAL_MS)
                } else {
                    CLOCK_TICK_INTERVAL_MS
                }
                delay(sleepMs)
            }
        }
    }

    private fun checkExamDeadlineExpiration() {
        val attempt = currentAttempt ?: return
        if (attempt.state != AttemptStateResponse.State.IN_PROGRESS) return
        val activePart = activePart()
        val (target, deadline) = serverClock.examDeadline(attempt, activePart) ?: return
        val remaining = serverClock.remainingMs(deadline) ?: return
        if (remaining > 0L) return

        val deadlineKey = "$target:$deadline"
        if (!handledDeadlines.add(deadlineKey)) return

        when (target) {
            is CountdownTarget.Part -> {
                saveQueue.lockScope(partId = target.partId)
                val sortedParts = PartSelection.sortedParts(attempt)
                val currentOrd = activePart?.ord ?: 0
                val hasNextPart = sortedParts.any {
                    (it.ord ?: 0) > currentOrd &&
                        it.state != AttemptPartStateView.State.CLOSED &&
                        it.state != AttemptPartStateView.State.SKIPPED
                }
                if (hasNextPart) {
                    _events.tryEmit(EngineEvent.PartClosed(target.partId))
                } else {
                    closedHint = closedHint ?: AttemptClosedReason.TimeUp
                    if (!attemptClosedEventEmitted) {
                        attemptClosedEventEmitted = true
                        _events.tryEmit(EngineEvent.AttemptClosed(AttemptClosedReason.TimeUp))
                    }
                }
            }

            CountdownTarget.Attempt -> {
                saveQueue.lockScope(partId = null)
                closedHint = closedHint ?: AttemptClosedReason.TimeUp
                if (!attemptClosedEventEmitted) {
                    attemptClosedEventEmitted = true
                    _events.tryEmit(EngineEvent.AttemptClosed(AttemptClosedReason.TimeUp))
                }
            }

            is CountdownTarget.Choice,
            CountdownTarget.None,
            -> return
        }

        deadlineReloadJob?.cancel()
        deadlineReloadJob = scope.launch {
            delay(DEADLINE_RELOAD_DELAY_MS)
            val fresh = reloadServerState()
            if (fresh == null && target == CountdownTarget.Attempt && currentClosedReason == null) {
                finalizeClosed(AttemptClosedReason.TimeUp, keepStartContext = false)
            }
        }
    }

    private fun stopActiveLoops() {
        heartbeatLoop.stop()
        partPolling.stop()
        clockTickerJob?.cancel()
        clockTickerJob = null
        deadlineReloadJob?.cancel()
        deadlineReloadJob = null
        saveQueue.cancelAllPendingTimers()
        positionTracker.cancelPendingTimer()
    }

    private fun activePart(): AttemptPartStateView? {
        val attempt = currentAttempt ?: return null
        val parts = PartSelection.sortedParts(attempt)
        if (parts.isEmpty()) return null
        val currentPartId = positionTracker.current?.partId
        return parts.firstOrNull { it.partId == currentPartId } ?: parts.firstOrNull()
    }

    private fun isEngineEditable(): Boolean {
        if (isStopped || currentClosedReason != null) return false
        if (currentAttempt?.state != AttemptStateResponse.State.IN_PROGRESS) return false
        return when (currentSessionState) {
            is EditSessionState.Editing,
            EditSessionState.Acquiring,
            -> true
            is EditSessionState.OpenElsewhere,
            EditSessionState.Evicted,
            is EditSessionState.ReadOnly,
            -> false
        }
    }

    private fun publishState() {
        val activePart = activePart()
        val countdown = serverClock.computeCountdown(currentAttempt, activePart)
        val saveStatus = saveQueue.computeSaveStatus(
            claimFailureOverride = if (currentSessionState is EditSessionState.Acquiring) {
                claimFailureSaveStatus
            } else {
                null
            },
        )
        _state.value = EngineState(
            load = currentLoad,
            attempt = currentAttempt,
            answers = saveQueue.snapshotAnswers(),
            position = positionTracker.current,
            session = currentSessionState,
            clock = countdown,
            save = saveStatus,
            network = if (connectivity.online.value) NetworkStatus.Online else NetworkStatus.Offline,
            command = currentCommandState,
            closed = currentClosedReason,
        )
    }

    companion object {
        private const val CODE_ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND"
    }
}
