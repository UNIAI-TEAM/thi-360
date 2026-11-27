package vn.unicom.thi360.feature.attachments.controller

import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.feature.attachments.gateway.AttemptAudioGateway
import vn.unicom.thi360.feature.attachments.model.AudioEndReason
import vn.unicom.thi360.feature.attachments.model.AudioError
import vn.unicom.thi360.feature.attachments.model.AudioGatewayResult
import vn.unicom.thi360.feature.attachments.model.AudioLockReason
import vn.unicom.thi360.feature.attachments.model.AudioPlaySnapshot
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.model.AudioPlayerUiState
import vn.unicom.thi360.feature.attachments.model.PauseReason
import vn.unicom.thi360.feature.attachments.model.ServerAudioPlayState
import vn.unicom.thi360.feature.attachments.playback.AudioPlaybackCallbacks
import vn.unicom.thi360.feature.attachments.playback.AudioPlaybackEngine
import vn.unicom.thi360.feature.attachments.playback.PlaybackStartResult
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.AttemptClosedReason
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.CloseTarget
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.CountdownTarget
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.EngineEvent
import vn.unicom.thi360.feature.attemptengine.EngineState
import vn.unicom.thi360.feature.attemptengine.NetworkStatus
import vn.unicom.thi360.feature.attemptengine.ReadOnlyReason

class AttemptAudioController(
    val attemptId: UUID,
    val assetId: UUID,
    val partId: UUID?,
    val fileName: String,
    private val engine: AttemptEngine,
    private val gateway: AttemptAudioGateway,
    private val playbackEngine: AudioPlaybackEngine,
    private val scope: CoroutineScope,
    private val sessionCleanup: SessionCleanup? = null,
    private val requestIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val progressIntervalMs: Long = DEFAULT_PROGRESS_INTERVAL_MS,
    private val positionPollIntervalMs: Long = DEFAULT_POSITION_POLL_INTERVAL_MS,
    externalDisabled: Boolean = false,
) : Closeable, AudioLogoutCleanable {
    private val stateLock = Any()

    private var closed = false
    private var epoch = 0L
    private var snapshot: AudioPlaySnapshot = AudioPlaySnapshot.empty()
    private var policyMaxPlays: Int = AudioPlaySnapshot.DEFAULT_MAX_PLAYS
    private var mediaDurationMs: Long = 0L
    private var lastConfirmedPositionMs: Long = 0L
    private var displayedPositionMs: Long = 0L
    private var pendingRequestId: String? = null
    private var endSentForPlayId: UUID? = null
    private var wasPlayingBeforeOffline = false
    private var wasOnline = engine.state.value.network == NetworkStatus.Online
    private var currentExternalDisabled = externalDisabled
    private var apiClosedReason: AudioLockReason? = null
    private var lockedByTimeUp = false
    private var beforeClosePart = false
    private var beforeCloseAttempt = false
    private var partClosedEventSeen = false
    private var hasPlayError = false
    private var phase: AudioPlayerPhase = AudioPlayerPhase.Idle(AudioPlaySnapshot.DEFAULT_MAX_PLAYS)

    private var actionJob: Job? = null
    private var progressJob: Job? = null
    private var positionTickerJob: Job? = null
    private var engineStateJob: Job? = null
    private var engineEventJob: Job? = null
    private var registryHandle: Closeable? = null

    private val _uiState = MutableStateFlow(buildUiStateLocked())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    init {
        registryHandle = AttemptAudioSessionRegistry.register(this, sessionCleanup)
        synchronized(stateLock) {
            hydrateFromEngineStateLocked(engine.state.value, initial = true)
            publishLocked()
        }
        // Collect engine events on Unconfined so BeforeClose stops audio synchronously before
        // finishPart/submit HTTP calls are even launched (R-137, AC-533).
        engineEventJob = scope.launch(Dispatchers.Unconfined) {
            engine.events.collect { event ->
                handleEngineEvent(event)
            }
        }
        engineStateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.state.collect { engineState ->
                handleEngineState(engineState)
            }
        }
    }

    fun setExternalDisabled(disabled: Boolean) {
        synchronized(stateLock) {
            if (closed || currentExternalDisabled == disabled) return
            currentExternalDisabled = disabled
            applyEngineStateLocked(engine.state.value)
            publishLocked()
        }
    }

    fun onPrimaryAction() {
        val launchTask: (() -> Unit)? = synchronized(stateLock) {
            if (closed) return
            val engineState = engine.state.value
            if (engineState.network != NetworkStatus.Online) return
            if (engineState.session !is EditSessionState.Editing) return
            if (resolveLockReasonLocked(engineState) != null) return

            when (val current = phase) {
                is AudioPlayerPhase.Opening,
                is AudioPlayerPhase.Playing,
                is AudioPlayerPhase.WaitingNetwork,
                AudioPlayerPhase.Exhausted,
                is AudioPlayerPhase.Locked,
                -> null

                is AudioPlayerPhase.Paused -> {
                    val myEpoch = ++epoch
                    hasPlayError = false
                    phase = AudioPlayerPhase.Opening(
                        requestId = "resume:${current.playId}",
                        isResuming = true,
                    )
                    publishLocked()
                    val playId = current.playId
                    val task: () -> Unit = {
                        actionJob?.cancel()
                        actionJob = scope.launch {
                            resumeAndPlay(playId = playId, myEpoch = myEpoch)
                        }
                    }
                    task
                }

                is AudioPlayerPhase.Idle -> {
                    if (snapshot.playsLeft <= 0) {
                        phase = AudioPlayerPhase.Exhausted
                        publishLocked()
                        return
                    }
                    val myEpoch = ++epoch
                    val reqId = pendingRequestId ?: requestIdGenerator().also { pendingRequestId = it }
                    hasPlayError = false
                    phase = AudioPlayerPhase.Opening(requestId = reqId, isResuming = false)
                    publishLocked()
                    val task: () -> Unit = {
                        actionJob?.cancel()
                        actionJob = scope.launch {
                            openAndPlay(requestId = reqId, myEpoch = myEpoch)
                        }
                    }
                    task
                }
            }
        }
        launchTask?.invoke()
    }

    fun onAppBackgrounded() = pauseForInterruption(PauseReason.Background)

    fun onLeaveScreen() = pauseForInterruption(PauseReason.LeftScreen)

    fun pauseForInterruption(reason: PauseReason) {
        var shouldStopMedia = false
        synchronized(stateLock) {
            if (closed) return
            when (val current = phase) {
                is AudioPlayerPhase.Playing -> {
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    hasPlayError = reason == PauseReason.Error
                    wasPlayingBeforeOffline = false
                    displayedPositionMs = lastConfirmedPositionMs
                    phase = AudioPlayerPhase.Paused(
                        playId = current.playId,
                        serverPositionMs = lastConfirmedPositionMs,
                        reason = reason,
                    )
                    publishLocked()
                }

                is AudioPlayerPhase.Opening -> {
                    epoch += 1
                    actionJob?.cancel()
                    actionJob = null
                    cancelTimersLocked()
                    shouldStopMedia = true
                    val activePlayId = snapshot.playId?.takeIf { snapshot.state == ServerAudioPlayState.PLAYING }
                    phase = if (activePlayId != null) {
                        displayedPositionMs = lastConfirmedPositionMs
                        AudioPlayerPhase.Paused(
                            playId = activePlayId,
                            serverPositionMs = lastConfirmedPositionMs,
                            reason = reason,
                        )
                    } else if (snapshot.playsLeft <= 0) {
                        AudioPlayerPhase.Exhausted
                    } else {
                        AudioPlayerPhase.Idle(snapshot.playsLeft)
                    }
                    publishLocked()
                }

                else -> Unit
            }
        }
        if (shouldStopMedia) {
            playbackEngine.stopAndRelease()
        }
    }

    override fun stopAndPurgeForLogout() {
        synchronized(stateLock) {
            epoch += 1
            actionJob?.cancel()
            actionJob = null
            cancelTimersLocked()
            pendingRequestId = null
            wasPlayingBeforeOffline = false
            endSentForPlayId = null
            hasPlayError = false
            phase = AudioPlayerPhase.Locked(AudioLockReason.Disabled)
            publishLocked()
        }
        playbackEngine.stopAndRelease()
    }

    override fun close() {
        synchronized(stateLock) {
            if (closed) return
            closed = true
            epoch += 1
            actionJob?.cancel()
            actionJob = null
            cancelTimersLocked()
            engineStateJob?.cancel()
            engineStateJob = null
            engineEventJob?.cancel()
            engineEventJob = null
        }
        playbackEngine.stopAndRelease()
        registryHandle?.close()
        registryHandle = null
    }

    private suspend fun openAndPlay(requestId: String, myEpoch: Long) {
        val result = gateway.openPlay(assetId = assetId, requestId = requestId)
        when (result) {
            is AudioGatewayResult.Success -> {
                val shouldProceed = synchronized(stateLock) {
                    if (closed || epoch != myEpoch) {
                        false
                    } else {
                        pendingRequestId = null
                        true
                    }
                }
                if (shouldProceed) {
                    fetchAndStartPlayback(snap = result.value, myEpoch = myEpoch)
                }
            }

            is AudioGatewayResult.Failure -> {
                when (val error = result.error) {
                    is AudioError.InProgress -> {
                        val resumePlayId = synchronized(stateLock) {
                            if (closed || epoch != myEpoch) return
                            pendingRequestId = null
                            error.playId
                        }
                        if (resumePlayId != null) {
                            resumeAndPlay(playId = resumePlayId, myEpoch = myEpoch)
                        } else {
                            handleError(error = AudioError.Stale, myEpoch = myEpoch, fromOpenPlay = true)
                        }
                    }

                    else -> handleError(error = error, myEpoch = myEpoch, fromOpenPlay = true)
                }
            }
        }
    }

    private suspend fun resumeAndPlay(playId: UUID, myEpoch: Long) {
        val result = gateway.resume(assetId = assetId, playId = playId)
        when (result) {
            is AudioGatewayResult.Success -> {
                fetchAndStartPlayback(snap = result.value, myEpoch = myEpoch)
            }

            is AudioGatewayResult.Failure -> {
                handleError(error = result.error, myEpoch = myEpoch, fromOpenPlay = false, playId = playId)
            }
        }
    }

    private suspend fun fetchAndStartPlayback(snap: AudioPlaySnapshot, myEpoch: Long) {
        val activePlayId = synchronized(stateLock) {
            if (closed || epoch != myEpoch || !canOperateLocked()) return
            applyAuthoritativeSnapshotLocked(snap)
            if (snap.state != ServerAudioPlayState.PLAYING || snap.playId == null) {
                if (snap.endReason == AudioEndReason.CLOSED) {
                    apiClosedReason = AudioLockReason.PartClosed
                    phase = AudioPlayerPhase.Locked(AudioLockReason.PartClosed)
                    publishLocked()
                    engine.refresh()
                } else {
                    phase = if (snap.playsLeft <= 0) {
                        AudioPlayerPhase.Exhausted
                    } else {
                        AudioPlayerPhase.Idle(snap.playsLeft)
                    }
                    publishLocked()
                }
                return
            }
            publishLocked()
            snap.playId
        }

        val audioResult = gateway.fetchAudio(assetId = assetId, playId = activePlayId)
        val audioBytes = when (audioResult) {
            is AudioGatewayResult.Success -> audioResult.value
            is AudioGatewayResult.Failure -> {
                handleError(error = audioResult.error, myEpoch = myEpoch, fromOpenPlay = false, playId = activePlayId)
                return
            }
        }

        val startPos = synchronized(stateLock) {
            if (closed || epoch != myEpoch || !canOperateLocked()) {
                audioBytes.fill(0)
                return
            }
            snap.positionMs
        }

        val callbacks = object : AudioPlaybackCallbacks {
            override fun onCompleted() = handlePlaybackCompleted(activePlayId, myEpoch)
            override fun onError() = pauseForInterruption(PauseReason.Error)
            override fun onFocusLost() = pauseForInterruption(PauseReason.Interrupted)
            override fun onBecomingNoisy() = pauseForInterruption(PauseReason.Noisy)
            override fun onHeadsetPause() = pauseForInterruption(PauseReason.Headset)
        }

        val startOutcome = playbackEngine.start(
            audioBytes = audioBytes,
            startPositionMs = startPos,
            callbacks = callbacks,
        )

        when (startOutcome) {
            PlaybackStartResult.Failed -> {
                audioBytes.fill(0)
                synchronized(stateLock) {
                    if (closed || epoch != myEpoch) return
                    hasPlayError = true
                    phase = AudioPlayerPhase.Paused(
                        playId = activePlayId,
                        serverPositionMs = lastConfirmedPositionMs,
                        reason = PauseReason.Error,
                    )
                    publishLocked()
                }
            }

            is PlaybackStartResult.Started -> {
                var stopImmediately = false
                synchronized(stateLock) {
                    if (closed || epoch != myEpoch || !canOperateLocked()) {
                        stopImmediately = true
                    } else {
                        if (startOutcome.durationMs > 0L) {
                            mediaDurationMs = startOutcome.durationMs
                        }
                        if (endSentForPlayId != activePlayId) {
                            endSentForPlayId = null
                        }
                        hasPlayError = false
                        displayedPositionMs = startPos
                        phase = AudioPlayerPhase.Playing(playId = activePlayId, positionMs = startPos)
                        startProgressAndTickerLocked(activePlayId, myEpoch)
                        publishLocked()
                    }
                }
                if (stopImmediately) {
                    playbackEngine.stopAndRelease()
                }
            }
        }
    }

    private fun startProgressAndTickerLocked(playId: UUID, myEpoch: Long) {
        cancelTimersLocked()
        val safeInterval = progressIntervalMs.coerceAtLeast(100L)
        val safePoll = positionPollIntervalMs.coerceAtLeast(50L)

        positionTickerJob = scope.launch {
            while (isActive) {
                delay(safePoll)
                synchronized(stateLock) {
                    if (closed || epoch != myEpoch || phase !is AudioPlayerPhase.Playing) return@launch
                    val livePos = playbackEngine.currentPositionMs()
                    val duration = effectiveDurationLocked()
                    val clamped = if (duration > 0L) livePos.coerceIn(0L, duration) else livePos.coerceAtLeast(0L)
                    displayedPositionMs = clamped
                    phase = AudioPlayerPhase.Playing(playId = playId, positionMs = clamped)
                    publishLocked()
                }
            }
        }

        progressJob = scope.launch {
            while (isActive) {
                delay(safeInterval)
                val boundedPos = synchronized(stateLock) {
                    if (closed ||
                        epoch != myEpoch ||
                        phase !is AudioPlayerPhase.Playing ||
                        endSentForPlayId == playId ||
                        !canOperateLocked()
                    ) {
                        return@launch
                    }
                    val rawPos = playbackEngine.currentPositionMs()
                    val nonDecreasing = maxOf(lastConfirmedPositionMs, rawPos)
                    val duration = effectiveDurationLocked()
                    if (duration > 0L) nonDecreasing.coerceAtMost(duration) else nonDecreasing
                }

                when (val result = gateway.progress(assetId = assetId, playId = playId, positionMs = boundedPos)) {
                    is AudioGatewayResult.Success -> {
                        var shouldStopMedia = false
                        var shouldRefresh = false
                        synchronized(stateLock) {
                            if (closed || epoch != myEpoch || endSentForPlayId == playId) return@launch
                            val nextSnap = result.value
                            lastConfirmedPositionMs = maxOf(lastConfirmedPositionMs, nextSnap.positionMs)
                            snapshot = nextSnap
                            if (nextSnap.state == ServerAudioPlayState.ENDED) {
                                epoch += 1
                                cancelTimersLocked()
                                shouldStopMedia = true
                                displayedPositionMs = nextSnap.positionMs
                                if (nextSnap.endReason == AudioEndReason.CLOSED) {
                                    apiClosedReason = AudioLockReason.PartClosed
                                    phase = AudioPlayerPhase.Locked(AudioLockReason.PartClosed)
                                    shouldRefresh = true
                                } else {
                                    phase = if (nextSnap.playsLeft <= 0) {
                                        AudioPlayerPhase.Exhausted
                                    } else {
                                        AudioPlayerPhase.Idle(nextSnap.playsLeft)
                                    }
                                }
                            }
                            publishLocked()
                        }
                        if (shouldStopMedia) {
                            playbackEngine.stopAndRelease()
                        }
                        if (shouldRefresh) {
                            engine.refresh()
                        }
                    }

                    is AudioGatewayResult.Failure -> {
                        val isSeekRejected = result.error is AudioError.SeekRejected
                        handleError(error = result.error, myEpoch = myEpoch, fromOpenPlay = false, playId = playId)
                        if (!isSeekRejected) {
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun handlePlaybackCompleted(playId: UUID, myEpoch: Long) {
        val shouldSendEnd = synchronized(stateLock) {
            if (closed || epoch != myEpoch || endSentForPlayId == playId) {
                false
            } else {
                // Mark end sent synchronously before cancelling progress so a simultaneous 10s tick never races end (Risk test).
                endSentForPlayId = playId
                cancelTimersLocked()
                true
            }
        }
        if (!shouldSendEnd) return

        playbackEngine.stopAndRelease()

        scope.launch {
            when (val result = gateway.end(assetId = assetId, playId = playId)) {
                is AudioGatewayResult.Success -> {
                    var shouldRefresh = false
                    synchronized(stateLock) {
                        if (closed || epoch != myEpoch) return@launch
                        val ended = result.value
                        applyAuthoritativeSnapshotLocked(ended)
                        if (ended.endReason == AudioEndReason.CLOSED) {
                            apiClosedReason = AudioLockReason.PartClosed
                            phase = AudioPlayerPhase.Locked(AudioLockReason.PartClosed)
                            shouldRefresh = true
                        } else {
                            phase = if (ended.playsLeft <= 0) {
                                AudioPlayerPhase.Exhausted
                            } else {
                                AudioPlayerPhase.Idle(ended.playsLeft)
                            }
                        }
                        publishLocked()
                    }
                    if (shouldRefresh) {
                        engine.refresh()
                    }
                }

                is AudioGatewayResult.Failure -> {
                    synchronized(stateLock) {
                        if (result.error is AudioError.Network || result.error is AudioError.Other) {
                            endSentForPlayId = null
                        }
                    }
                    handleError(error = result.error, myEpoch = myEpoch, fromOpenPlay = false, playId = playId)
                }
            }
        }
    }

    private fun handleError(
        error: AudioError,
        myEpoch: Long,
        fromOpenPlay: Boolean,
        playId: UUID? = null,
    ) {
        var shouldStopMedia = false
        var shouldReportRevoked = false
        var shouldRefresh = false
        var seekTargetMs: Long? = null

        synchronized(stateLock) {
            if (closed || epoch != myEpoch) return

            when (error) {
                AudioError.Exhausted -> {
                    pendingRequestId = null
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    hasPlayError = false
                    snapshot = snapshot.copy(playsLeft = 0, state = ServerAudioPlayState.ENDED)
                    phase = AudioPlayerPhase.Exhausted
                }

                is AudioError.SeekRejected -> {
                    val serverPos = (error.positionMs ?: lastConfirmedPositionMs).coerceAtLeast(0L)
                    lastConfirmedPositionMs = serverPos
                    displayedPositionMs = serverPos
                    snapshot = snapshot.copy(positionMs = serverPos)
                    val activeId = playId ?: snapshot.playId
                    if (activeId != null && phase is AudioPlayerPhase.Playing) {
                        phase = AudioPlayerPhase.Playing(playId = activeId, positionMs = serverPos)
                    }
                    seekTargetMs = serverPos
                }

                AudioError.Stale -> {
                    pendingRequestId = null
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    hasPlayError = false
                    shouldRefresh = true
                    phase = if (snapshot.playsLeft <= 0) {
                        AudioPlayerPhase.Exhausted
                    } else {
                        AudioPlayerPhase.Idle(snapshot.playsLeft)
                    }
                }

                AudioError.PartClosed -> {
                    pendingRequestId = null
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    hasPlayError = false
                    apiClosedReason = AudioLockReason.PartClosed
                    phase = AudioPlayerPhase.Locked(AudioLockReason.PartClosed)
                    shouldRefresh = true
                }

                AudioError.TimeUp -> {
                    pendingRequestId = null
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    hasPlayError = false
                    lockedByTimeUp = true
                    apiClosedReason = AudioLockReason.TimeUp
                    phase = AudioPlayerPhase.Locked(AudioLockReason.TimeUp)
                    shouldRefresh = true
                }

                AudioError.SessionRevoked -> {
                    pendingRequestId = null
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    hasPlayError = false
                    phase = AudioPlayerPhase.Locked(AudioLockReason.Evicted)
                    shouldReportRevoked = true
                }

                AudioError.Unauthorized -> {
                    pendingRequestId = null
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    hasPlayError = false
                    val activeId = playId ?: snapshot.playId?.takeIf { snapshot.state == ServerAudioPlayState.PLAYING }
                    phase = if (activeId != null) {
                        AudioPlayerPhase.Paused(activeId, lastConfirmedPositionMs, PauseReason.Error)
                    } else {
                        AudioPlayerPhase.Idle(snapshot.playsLeft)
                    }
                }

                is AudioError.InProgress -> {
                    // Handled directly in openAndPlay; fallback reloads fresh state.
                    pendingRequestId = null
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    shouldRefresh = true
                }

                is AudioError.Network,
                is AudioError.Other,
                -> {
                    // Keep pendingRequestId intact on transient/network errors so retrying openPlay reuses it (AC-517).
                    val offlineNow = engine.state.value.network != NetworkStatus.Online
                    val activeId = playId ?: snapshot.playId?.takeIf { snapshot.state == ServerAudioPlayState.PLAYING }
                    epoch += 1
                    cancelTimersLocked()
                    shouldStopMedia = true
                    if (offlineNow && activeId != null && !fromOpenPlay) {
                        wasPlayingBeforeOffline = true
                        hasPlayError = false
                        phase = AudioPlayerPhase.WaitingNetwork(activeId)
                    } else if (!fromOpenPlay && activeId != null) {
                        hasPlayError = true
                        displayedPositionMs = lastConfirmedPositionMs
                        phase = AudioPlayerPhase.Paused(activeId, lastConfirmedPositionMs, PauseReason.Error)
                    } else {
                        hasPlayError = true
                        phase = if (snapshot.playsLeft <= 0) {
                            AudioPlayerPhase.Exhausted
                        } else {
                            AudioPlayerPhase.Idle(snapshot.playsLeft)
                        }
                    }
                }
            }
            publishLocked()
        }

        seekTargetMs?.let { playbackEngine.seekToAuthorized(it) }
        if (shouldStopMedia) {
            playbackEngine.stopAndRelease()
        }
        if (shouldReportRevoked) {
            engine.reportSessionRevoked()
        }
        if (shouldRefresh) {
            engine.refresh()
        }
    }

    private fun handleEngineEvent(event: EngineEvent) {
        var shouldStopMedia = false
        synchronized(stateLock) {
            if (closed) return
            when (event) {
                is EngineEvent.BeforeClose -> {
                    when (val target = event.target) {
                        CloseTarget.Attempt -> {
                            beforeCloseAttempt = true
                            shouldStopMedia = stopAndLockLocked(AudioLockReason.Submitted)
                        }

                        is CloseTarget.Part -> {
                            if (partId == null || partId == target.partId) {
                                beforeClosePart = true
                                shouldStopMedia = stopAndLockLocked(AudioLockReason.PartClosed)
                            }
                        }
                    }
                }

                is EngineEvent.PartClosed -> {
                    if (partId == null || partId == event.partId) {
                        partClosedEventSeen = true
                        if (isDeadlineExpiredForThisAudio(engine.state.value)) {
                            lockedByTimeUp = true
                        }
                        val reason = if (lockedByTimeUp) AudioLockReason.TimeUp else AudioLockReason.PartClosed
                        shouldStopMedia = stopAndLockLocked(reason)
                    }
                }

                is EngineEvent.AttemptClosed -> {
                    val reason = when (event.reason) {
                        AttemptClosedReason.TimeUp -> {
                            lockedByTimeUp = true
                            AudioLockReason.TimeUp
                        }

                        AttemptClosedReason.Submitted -> AudioLockReason.Submitted
                        AttemptClosedReason.ChoiceTimeout,
                        is AttemptClosedReason.Aborted,
                        AttemptClosedReason.NotOpen,
                        -> AudioLockReason.PartClosed
                    }
                    shouldStopMedia = stopAndLockLocked(reason)
                }

                EngineEvent.SessionLost -> {
                    shouldStopMedia = stopAndLockLocked(AudioLockReason.Evicted)
                }
            }
            publishLocked()
        }
        if (shouldStopMedia) {
            playbackEngine.stopAndRelease()
        }
    }

    private fun handleEngineState(engineState: EngineState) {
        var shouldStopMedia = false
        var autoResumeTask: (() -> Unit)? = null

        synchronized(stateLock) {
            if (closed) return

            val onlineNow = engineState.network == NetworkStatus.Online
            val wentOffline = wasOnline && !onlineNow
            val cameOnline = !wasOnline && onlineNow
            wasOnline = onlineNow

            hydrateFromEngineStateLocked(engineState, initial = false)

            val lockReason = resolveLockReasonLocked(engineState)
            if (lockReason != null) {
                shouldStopMedia = stopAndLockLocked(lockReason)
                publishLocked()
            } else {
                if (phase is AudioPlayerPhase.Locked) {
                    phase = unlockedPhaseFromSnapshotLocked()
                }
                if (wentOffline) {
                    when (val current = phase) {
                        is AudioPlayerPhase.Playing -> {
                            epoch += 1
                            cancelTimersLocked()
                            shouldStopMedia = true
                            wasPlayingBeforeOffline = true
                            hasPlayError = false
                            phase = AudioPlayerPhase.WaitingNetwork(current.playId)
                        }

                        is AudioPlayerPhase.Opening -> {
                            val activeId = snapshot.playId?.takeIf { snapshot.state == ServerAudioPlayState.PLAYING }
                            if (activeId != null) {
                                epoch += 1
                                actionJob?.cancel()
                                actionJob = null
                                cancelTimersLocked()
                                shouldStopMedia = true
                                wasPlayingBeforeOffline = true
                                phase = AudioPlayerPhase.WaitingNetwork(activeId)
                            }
                        }

                        else -> Unit
                    }
                } else if (cameOnline) {
                    val waiting = phase as? AudioPlayerPhase.WaitingNetwork
                    if (waiting != null && wasPlayingBeforeOffline && engineState.session is EditSessionState.Editing) {
                        wasPlayingBeforeOffline = false
                        val myEpoch = ++epoch
                        hasPlayError = false
                        phase = AudioPlayerPhase.Opening(
                            requestId = "auto-resume:${waiting.playId}",
                            isResuming = true,
                        )
                        val resumePlayId = waiting.playId
                        autoResumeTask = {
                            actionJob?.cancel()
                            actionJob = scope.launch {
                                resumeAndPlay(playId = resumePlayId, myEpoch = myEpoch)
                            }
                        }
                    }
                }
                publishLocked()
            }
        }

        if (shouldStopMedia) {
            playbackEngine.stopAndRelease()
        }
        autoResumeTask?.invoke()
    }

    private fun hydrateFromEngineStateLocked(engineState: EngineState, initial: Boolean) {
        val attempt = engineState.attempt
        val serverPolicyMax = attempt?.audioPolicy?.maxPlays?.takeIf { it > 0 }
        if (serverPolicyMax != null) {
            policyMaxPlays = serverPolicyMax
        }

        val serverEntry = attempt?.audioPlays?.firstOrNull { it.assetId == assetId }
        if (serverEntry != null) {
            val fresh = AudioPlaySnapshot.fromView(serverEntry, policyMaxPlays)
            val isLocallyActive = phase is AudioPlayerPhase.Playing ||
                phase is AudioPlayerPhase.Opening ||
                phase is AudioPlayerPhase.WaitingNetwork
            val ignoreRegressiveUpdate = !initial && isLocallyActive && (
                fresh.playsUsed < snapshot.playsUsed ||
                    (fresh.playId == snapshot.playId &&
                        fresh.state == ServerAudioPlayState.PLAYING &&
                        fresh.positionMs < lastConfirmedPositionMs)
                )
            if (!ignoreRegressiveUpdate) {
                val previous = snapshot
                snapshot = fresh
                if (fresh.playId == previous.playId && fresh.state == ServerAudioPlayState.PLAYING) {
                    lastConfirmedPositionMs = maxOf(lastConfirmedPositionMs, fresh.positionMs)
                } else {
                    lastConfirmedPositionMs = fresh.positionMs
                }
                if (phase !is AudioPlayerPhase.Playing) {
                    displayedPositionMs = lastConfirmedPositionMs
                }
                if (initial) {
                    phase = resolveLockReasonLocked(engineState)?.let { AudioPlayerPhase.Locked(it) }
                        ?: unlockedPhaseFromSnapshotLocked()
                } else if (!isLocallyActive && phase !is AudioPlayerPhase.Locked) {
                    val currentPaused = phase as? AudioPlayerPhase.Paused
                    if (currentPaused != null &&
                        fresh.state == ServerAudioPlayState.PLAYING &&
                        fresh.playId == currentPaused.playId
                    ) {
                        phase = currentPaused.copy(serverPositionMs = lastConfirmedPositionMs)
                    } else {
                        phase = unlockedPhaseFromSnapshotLocked()
                    }
                }
            }
        } else if (initial) {
            snapshot = AudioPlaySnapshot.empty(policyMaxPlays)
            lastConfirmedPositionMs = 0L
            displayedPositionMs = 0L
            phase = resolveLockReasonLocked(engineState)?.let { AudioPlayerPhase.Locked(it) }
                ?: unlockedPhaseFromSnapshotLocked()
        } else if (serverPolicyMax != null && snapshot.playsUsed == 0 && snapshot.state == ServerAudioPlayState.IDLE) {
            snapshot = AudioPlaySnapshot.empty(policyMaxPlays)
            if (phase is AudioPlayerPhase.Idle) {
                phase = AudioPlayerPhase.Idle(snapshot.playsLeft)
            }
        }
    }

    private fun applyEngineStateLocked(engineState: EngineState) {
        val lockReason = resolveLockReasonLocked(engineState)
        if (lockReason != null) {
            stopAndLockLocked(lockReason)
        } else if (phase is AudioPlayerPhase.Locked) {
            phase = unlockedPhaseFromSnapshotLocked()
        }
    }

    private fun unlockedPhaseFromSnapshotLocked(): AudioPlayerPhase {
        val activePlayId = snapshot.playId
        return when {
            snapshot.state == ServerAudioPlayState.PLAYING && activePlayId != null ->
                AudioPlayerPhase.Paused(
                    playId = activePlayId,
                    serverPositionMs = lastConfirmedPositionMs,
                    reason = PauseReason.LeftScreen,
                )

            snapshot.playsLeft <= 0 -> AudioPlayerPhase.Exhausted
            else -> AudioPlayerPhase.Idle(snapshot.playsLeft)
        }
    }

    private fun isDeadlineExpiredForThisAudio(engineState: EngineState): Boolean {
        val attemptState = engineState.attempt?.state
        val clock = engineState.clock
        return attemptState == AttemptStateResponse.State.IN_PROGRESS &&
            clock.remainingMs <= 0L &&
            (clock.target == CountdownTarget.Attempt ||
                (partId != null && clock.target == CountdownTarget.Part(partId)))
    }

    private fun resolveLockReasonLocked(engineState: EngineState): AudioLockReason? {
        if (apiClosedReason == AudioLockReason.TimeUp || lockedByTimeUp) return AudioLockReason.TimeUp
        if (engineState.closed is AttemptClosedReason.TimeUp || isDeadlineExpiredForThisAudio(engineState)) {
            lockedByTimeUp = true
            return AudioLockReason.TimeUp
        }

        val attemptState = engineState.attempt?.state
        if (beforeCloseAttempt ||
            engineState.closed is AttemptClosedReason.Submitted ||
            engineState.command == CommandState.Sending(AttemptCommandKind.Submit) ||
            attemptState == AttemptStateResponse.State.GRADING ||
            attemptState == AttemptStateResponse.State.PENDING_REVIEW ||
            attemptState == AttemptStateResponse.State.SCORED ||
            attemptState == AttemptStateResponse.State.GRADING_ERROR
        ) {
            return AudioLockReason.Submitted
        }

        val partState = partId?.let { targetPartId ->
            engineState.attempt?.parts.orEmpty().firstOrNull { it.partId == targetPartId }?.state
        }
        val finishingThisPart = engineState.command == CommandState.Sending(AttemptCommandKind.FinishPart) &&
            (partId == null || engineState.position?.partId == partId)
        if (apiClosedReason == AudioLockReason.PartClosed ||
            beforeClosePart ||
            partClosedEventSeen ||
            finishingThisPart ||
            partState == AttemptPartStateView.State.CLOSED ||
            partState == AttemptPartStateView.State.SKIPPED ||
            engineState.session == EditSessionState.ReadOnly(ReadOnlyReason.PartClosed)
        ) {
            return AudioLockReason.PartClosed
        }

        if (engineState.session is EditSessionState.Evicted ||
            engineState.session is EditSessionState.OpenElsewhere
        ) {
            return AudioLockReason.Evicted
        }

        if (engineState.session is EditSessionState.Acquiring) {
            return AudioLockReason.Acquiring
        }

        if (currentExternalDisabled ||
            engineState.closed != null ||
            engineState.session is EditSessionState.ReadOnly
        ) {
            return AudioLockReason.Disabled
        }

        return null
    }

    private fun stopAndLockLocked(reason: AudioLockReason): Boolean {
        val hadActiveWork = phase is AudioPlayerPhase.Playing || phase is AudioPlayerPhase.Opening
        if (hadActiveWork) {
            epoch += 1
            actionJob?.cancel()
            actionJob = null
            cancelTimersLocked()
        }
        pendingRequestId = null
        wasPlayingBeforeOffline = false
        hasPlayError = false
        phase = AudioPlayerPhase.Locked(reason)
        return hadActiveWork
    }

    private fun canOperateLocked(): Boolean {
        val engineState = engine.state.value
        return !closed &&
            engineState.network == NetworkStatus.Online &&
            engineState.session is EditSessionState.Editing &&
            resolveLockReasonLocked(engineState) == null
    }

    private fun applyAuthoritativeSnapshotLocked(next: AudioPlaySnapshot) {
        snapshot = next
        lastConfirmedPositionMs = next.positionMs
        displayedPositionMs = next.positionMs
    }

    private fun effectiveDurationLocked(): Long =
        if (snapshot.durationMs > 0L) snapshot.durationMs else mediaDurationMs

    private fun cancelTimersLocked() {
        progressJob?.cancel()
        progressJob = null
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    private fun publishLocked() {
        _uiState.value = buildUiStateLocked()
    }

    private fun buildUiStateLocked(): AudioPlayerUiState = AudioPlayerUiState(
        assetId = assetId,
        fileName = fileName,
        phase = phase,
        playsUsed = snapshot.playsUsed,
        playsLeft = snapshot.playsLeft,
        maxPlays = policyMaxPlays,
        positionMs = displayedPositionMs,
        durationMs = effectiveDurationLocked(),
        isOnline = wasOnline,
        hasPlayError = hasPlayError,
    )

    companion object {
        const val DEFAULT_PROGRESS_INTERVAL_MS = 10_000L
        const val DEFAULT_POSITION_POLL_INTERVAL_MS = 250L
    }
}
