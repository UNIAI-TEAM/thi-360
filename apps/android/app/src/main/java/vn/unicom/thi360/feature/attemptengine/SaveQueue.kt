package vn.unicom.thi360.feature.attemptengine

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.AnswerChange
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptPosition
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.FinalAnswerChange
import vn.unicom.thi360.core.network.generated.model.FinalAnswerResult
import vn.unicom.thi360.core.network.generated.model.FlagChange
import vn.unicom.thi360.core.network.generated.model.SaveAnswersRequest

internal interface SaveQueueCallbacks {
    fun activeSessionId(): UUID?
    fun isEditable(): Boolean
    fun isOnline(): Boolean
    fun consumePendingPosition(): EnginePosition?
    fun restorePendingPositionIfUnchanged(position: EnginePosition)
    fun hasPendingPosition(): Boolean
    fun onServerNow(serverNow: OffsetDateTime)
    fun onStateChanged()
    fun onStaleDetected()
    fun onPartClosed(partId: PartId)
    fun onAttemptClosed(reason: AttemptClosedReason)
    fun onSessionConflict()
}

internal class SaveQueue(
    private val attemptId: UUID,
    private val api: AttemptApi,
    private val draftStore: DraftStore,
    private val scope: CoroutineScope,
    private val callbacks: SaveQueueCallbacks,
    private val backoff: Backoff = Backoff(),
) {
    private val items = LinkedHashMap<ItemId, MutableItemEntry>()
    private val dirtyAnswerOrder = LinkedHashSet<ItemId>()
    private val debounceJobs = HashMap<ItemId, Job>()
    private val essayMaxWaitJobs = HashMap<ItemId, Job>()
    private var retryJob: Job? = null
    private var flushJob: Job? = null
    private var flushRequestedWhileRunning = false
    private var lastErrorReason: UnsavedReason? = null
    private var destroyed = false

    fun knownItemIds(): Set<ItemId> = items.keys.toSet()

    fun reconcile(attempt: AttemptStateResponse, storedDrafts: Map<ItemId, DraftRecord>) {
        val outcome = DraftReconciler.reconcile(
            attempt = attempt,
            existingItems = items,
            storedDrafts = storedDrafts,
        )
        items.clear()
        items.putAll(outcome.entries)

        for (lockedId in outcome.lockedItemIds) {
            cancelItemTimers(lockedId)
            dirtyAnswerOrder.remove(lockedId)
        }
        for (draftId in outcome.draftsToDelete) {
            draftStore.deleteDraft(attemptId, draftId)
        }

        dirtyAnswerOrder.retainAll { id -> items[id]?.hasUnresolvedAnswer() == true }
        for (entry in items.values) {
            if (entry.hasUnresolvedAnswer()) {
                dirtyAnswerOrder.add(entry.itemId)
            }
            if (!entry.partClosed && (entry.dirtyFlag || entry.hasUnresolvedAnswer())) {
                syncDraftForItem(entry)
            }
        }
        reconcileErrorState()
        callbacks.onStateChanged()
    }

    fun lockScope(partId: PartId?) {
        for (entry in items.values) {
            if (partId == null || entry.partId == partId) {
                entry.partClosed = true
                entry.dirtyAnswer = false
                entry.dirtyFlag = false
                cancelItemTimers(entry.itemId)
                dirtyAnswerOrder.remove(entry.itemId)
            }
        }
        reconcileErrorState()
        callbacks.onStateChanged()
    }

    fun recordAnswer(itemId: ItemId, value: AnswerValue): Boolean {
        if (destroyed || !callbacks.isEditable()) return false
        val existing = items[itemId] ?: return false
        if (existing.partClosed) return false

        existing.localSeq += 1L
        existing.answer = value
        existing.dirtyAnswer = true
        existing.rejectedReason = null
        existing.rejectedSeq = null
        dirtyAnswerOrder.add(itemId)

        draftStore.writeDraft(attemptId, itemId, existing.toDraftRecord())

        if (lastErrorReason == UnsavedReason.Rejected) {
            lastErrorReason = null
        }

        callbacks.onStateChanged()

        cancelDebounce(itemId)
        when (val schedule = value.saveSchedule()) {
            SaveSchedule.Immediate -> {
                cancelEssayMaxWait(itemId)
                if (callbacks.isOnline() && callbacks.activeSessionId() != null) {
                    scheduleFlush()
                }
            }

            is SaveSchedule.Debounced -> {
                debounceJobs[itemId] = scope.launch {
                    delay(schedule.delayMs)
                    debounceJobs.remove(itemId)
                    if (!destroyed && callbacks.isOnline() && callbacks.activeSessionId() != null) {
                        scheduleFlush()
                    }
                }
            }

            is SaveSchedule.Essay -> {
                if (!essayMaxWaitJobs.containsKey(itemId)) {
                    essayMaxWaitJobs[itemId] = scope.launch {
                        delay(schedule.maxWaitMs)
                        essayMaxWaitJobs.remove(itemId)
                        cancelDebounce(itemId)
                        if (!destroyed && callbacks.isOnline() && callbacks.activeSessionId() != null) {
                            scheduleFlush()
                        }
                    }
                }
                debounceJobs[itemId] = scope.launch {
                    delay(schedule.debounceMs)
                    debounceJobs.remove(itemId)
                    cancelEssayMaxWait(itemId)
                    if (!destroyed && callbacks.isOnline() && callbacks.activeSessionId() != null) {
                        scheduleFlush()
                    }
                }
            }
        }
        return true
    }

    fun recordFlag(itemId: ItemId, flagged: Boolean): Boolean {
        if (destroyed || !callbacks.isEditable()) return false
        val existing = items[itemId] ?: return false
        if (existing.partClosed) return false

        existing.flagged = flagged
        existing.dirtyFlag = flagged != existing.serverFlagged
        syncDraftForItem(existing)
        reconcileErrorState()
        callbacks.onStateChanged()

        if (existing.dirtyFlag && callbacks.isOnline() && callbacks.activeSessionId() != null) {
            scheduleFlush()
        }
        return true
    }

    fun hasDirtyItems(): Boolean = items.values.any { !it.partClosed && (it.dirtyFlag || it.hasUnresolvedAnswer()) }

    fun hasAnyPendingWork(): Boolean = hasDirtyItems() || callbacks.hasPendingPosition()

    fun snapshotAnswers(): Map<ItemId, LocalAnswer> =
        items.mapValues { (_, entry) -> entry.toLocalAnswer() }

    fun computeSaveStatus(claimFailureOverride: SaveStatus? = null): SaveStatus {
        val online = callbacks.isOnline()
        val hasDirty = hasDirtyItems()
        val inFlight = flushJob?.isActive == true

        if (claimFailureOverride != null) {
            return if (!online) SaveStatus.Offline else claimFailureOverride
        }
        if (lastErrorReason == UnsavedReason.SessionConflict) {
            return SaveStatus.Unsaved(UnsavedReason.SessionConflict)
        }
        if (!online && (hasDirty || inFlight)) {
            return SaveStatus.Offline
        }
        val err = lastErrorReason
        if (err != null) {
            return SaveStatus.Unsaved(err)
        }
        if (hasDirty || inFlight) {
            return SaveStatus.Saving
        }
        return SaveStatus.Saved
    }

    fun cancelAllPendingTimers() {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        essayMaxWaitJobs.values.forEach { it.cancel() }
        essayMaxWaitJobs.clear()
        retryJob?.cancel()
        retryJob = null
    }

    fun flushNow() {
        if (destroyed || !callbacks.isEditable()) return
        cancelAllPendingTimers()
        if (!callbacks.isOnline()) {
            callbacks.onStateChanged()
            return
        }
        if (callbacks.activeSessionId() == null) {
            callbacks.onStateChanged()
            return
        }
        scheduleFlush()
    }

    fun onOnline() {
        if (destroyed) return
        backoff.reset()
        retryJob?.cancel()
        retryJob = null
        if (lastErrorReason != UnsavedReason.Rejected && lastErrorReason != UnsavedReason.SessionConflict) {
            lastErrorReason = null
        }
        callbacks.onStateChanged()
    }

    fun onOffline() {
        if (destroyed) return
        retryJob?.cancel()
        retryJob = null
        callbacks.onStateChanged()
    }

    fun clearSessionConflictError() {
        if (lastErrorReason == UnsavedReason.SessionConflict) {
            lastErrorReason = null
            callbacks.onStateChanged()
        }
    }

    fun buildFinalAnswers(partId: PartId? = null): List<FinalAnswerChange> {
        val changes = ArrayList<FinalAnswerChange>()
        for (itemId in dirtyAnswerOrder) {
            val item = items[itemId] ?: continue
            if (item.partClosed) continue
            if (partId != null && item.partId != partId) continue
            val answer = item.answer ?: continue
            if (item.hasUnresolvedAnswer()) {
                changes.add(
                    FinalAnswerChange(
                        itemId = item.itemId,
                        seq = item.localSeq,
                        answer = answer,
                    ),
                )
            }
            if (changes.size >= FINAL_ANSWERS_LIMIT) break
        }
        return changes
    }

    fun applyFinalAnswerResults(results: List<FinalAnswerResult>) {
        backoff.reset()
        if (lastErrorReason != UnsavedReason.SessionConflict) {
            lastErrorReason = null
        }
        for (res in results) {
            val item = items[res.itemId] ?: continue
            when (res.status) {
                FinalAnswerResult.Status.SAVED -> {
                    item.serverConfirmedSeq = maxOf(item.serverConfirmedSeq, res.seq)
                    item.dirtyAnswer = item.localSeq > item.serverConfirmedSeq
                    if (!item.dirtyAnswer) {
                        item.rejectedReason = null
                        item.rejectedSeq = null
                        dirtyAnswerOrder.remove(item.itemId)
                    }
                    syncDraftForItem(item)
                }

                FinalAnswerResult.Status.STALE -> {
                    item.serverConfirmedSeq = maxOf(item.serverConfirmedSeq, res.seq)
                    if (item.localSeq <= res.seq) {
                        item.dirtyAnswer = false
                        item.rejectedReason = null
                        item.rejectedSeq = null
                        dirtyAnswerOrder.remove(item.itemId)
                    }
                }

                FinalAnswerResult.Status.REJECTED -> {
                    val reason = res.reason ?: "INVALID_ANSWER"
                    item.rejectedReason = reason
                    item.rejectedSeq = res.seq
                    item.dirtyAnswer = item.localSeq > res.seq
                    if (!item.dirtyAnswer) {
                        dirtyAnswerOrder.remove(item.itemId)
                    }
                    if (reason == OpenApiAttemptApi.CODE_TIME_UP || reason == OpenApiAttemptApi.CODE_PART_CLOSED) {
                        item.partClosed = true
                        item.dirtyAnswer = false
                        item.dirtyFlag = false
                        dirtyAnswerOrder.remove(item.itemId)
                    } else if (!item.dirtyAnswer) {
                        lastErrorReason = UnsavedReason.Rejected
                    }
                    syncDraftForItem(item)
                }
            }
        }
        reconcileErrorState()
        callbacks.onStateChanged()
    }

    fun stop() {
        destroyed = true
        cancelAllPendingTimers()
        flushJob?.cancel()
        flushJob = null
    }

    private fun scheduleFlush() {
        if (destroyed || !callbacks.isOnline() || callbacks.activeSessionId() == null) return
        if (flushJob?.isActive == true) {
            flushRequestedWhileRunning = true
            return
        }
        retryJob?.cancel()
        retryJob = null
        flushJob = scope.launch {
            try {
                runFlushLoop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                flushJob = null
                callbacks.onStateChanged()
            }
        }
        callbacks.onStateChanged()
    }

    private suspend fun runFlushLoop() {
        val unackedSeqs = HashMap<ItemId, Long>()
        while (!destroyed && callbacks.isOnline() && callbacks.isEditable()) {
            flushRequestedWhileRunning = false
            val sessionId = callbacks.activeSessionId() ?: break

            val readyAnswers = dirtyAnswerOrder.mapNotNull { id ->
                val item = items[id] ?: return@mapNotNull null
                if (!item.partClosed &&
                    hasReadyDirtyAnswer(item) &&
                    unackedSeqs[item.itemId] != item.localSeq
                ) {
                    item
                } else {
                    null
                }
            }
            val pendingFlags = items.values.filter { !it.partClosed && it.dirtyFlag }
            val positionToSend = callbacks.consumePendingPosition()

            if (readyAnswers.isEmpty() && pendingFlags.isEmpty() && positionToSend == null) {
                reconcileErrorState()
                break
            }

            callbacks.onStateChanged()

            val answerBatches = readyAnswers.chunked(SAVE_BATCH_SIZE)
            val flagBatches = pendingFlags.chunked(SAVE_BATCH_SIZE)
            val batchCount = maxOf(1, answerBatches.size, flagBatches.size)

            var staleDetected = false
            var closedReasonDetected: String? = null
            var closedPartId: PartId? = null
            var batchAborted = false

            for (batchIndex in 0 until batchCount) {
                if (destroyed || !callbacks.isOnline() || callbacks.activeSessionId() != sessionId) {
                    if (positionToSend != null) {
                        callbacks.restorePendingPositionIfUnchanged(positionToSend)
                    }
                    batchAborted = true
                    break
                }
                val changesChunk = answerBatches.getOrNull(batchIndex).orEmpty()
                val flagsChunk = flagBatches.getOrNull(batchIndex).orEmpty()
                val isLastBatch = batchIndex == batchCount - 1
                val sentSeqs = LinkedHashMap<ItemId, Long>()
                val sentFlags = LinkedHashMap<ItemId, Boolean>()

                val answerChanges = changesChunk.mapNotNull { item ->
                    val nonNullAnswer = item.answer ?: return@mapNotNull null
                    sentSeqs[item.itemId] = item.localSeq
                    AnswerChange(
                        itemId = item.itemId,
                        seq = item.localSeq,
                        answer = nonNullAnswer,
                    )
                }.takeIf { it.isNotEmpty() }

                val flagChanges = flagsChunk.map { item ->
                    sentFlags[item.itemId] = item.flagged
                    FlagChange(flagged = item.flagged, itemId = item.itemId)
                }.takeIf { it.isNotEmpty() }

                val batchPosition = if (isLastBatch && positionToSend != null) {
                    AttemptPosition(
                        itemId = positionToSend.itemId,
                        partId = positionToSend.partId,
                    )
                } else {
                    null
                }

                if (answerChanges == null && flagChanges == null && batchPosition == null) {
                    continue
                }

                when (
                    val result = api.saveAnswers(
                        attemptId = attemptId,
                        sessionId = sessionId,
                        request = SaveAnswersRequest(
                            changes = answerChanges,
                            flags = flagChanges,
                            position = batchPosition,
                        ),
                    )
                ) {
                    is ApiResult.Success -> {
                        if (destroyed || callbacks.activeSessionId() != sessionId) {
                            batchAborted = true
                            break
                        }
                        callbacks.onServerNow(result.data.serverNow)
                        backoff.reset()
                        lastErrorReason = null

                        val ackedIds = HashSet<ItemId>()
                        for (res in result.data.results) {
                            ackedIds.add(res.itemId)
                            unackedSeqs.remove(res.itemId)
                            val item = items[res.itemId] ?: continue
                            when (res.status) {
                                FinalAnswerResult.Status.SAVED -> {
                                    item.serverConfirmedSeq = maxOf(item.serverConfirmedSeq, res.seq)
                                    item.dirtyAnswer = item.localSeq > item.serverConfirmedSeq
                                    if (!item.dirtyAnswer) {
                                        item.rejectedReason = null
                                        item.rejectedSeq = null
                                        dirtyAnswerOrder.remove(item.itemId)
                                    }
                                    syncDraftForItem(item)
                                }

                                FinalAnswerResult.Status.STALE -> {
                                    item.serverConfirmedSeq = maxOf(item.serverConfirmedSeq, res.seq)
                                    if (item.localSeq <= res.seq) {
                                        item.dirtyAnswer = false
                                        item.rejectedReason = null
                                        item.rejectedSeq = null
                                        dirtyAnswerOrder.remove(item.itemId)
                                    }
                                    staleDetected = true
                                }

                                FinalAnswerResult.Status.REJECTED -> {
                                    val reason = res.reason ?: "INVALID_ANSWER"
                                    item.rejectedReason = reason
                                    item.rejectedSeq = res.seq
                                    item.dirtyAnswer = item.localSeq > res.seq
                                    if (!item.dirtyAnswer) {
                                        dirtyAnswerOrder.remove(item.itemId)
                                    }
                                    if (reason == OpenApiAttemptApi.CODE_TIME_UP ||
                                        reason == OpenApiAttemptApi.CODE_PART_CLOSED
                                    ) {
                                        item.partClosed = true
                                        item.dirtyAnswer = false
                                        item.dirtyFlag = false
                                        dirtyAnswerOrder.remove(item.itemId)
                                        cancelItemTimers(item.itemId)
                                        closedReasonDetected = reason
                                        closedPartId = item.partId
                                    } else if (!item.dirtyAnswer) {
                                        lastErrorReason = UnsavedReason.Rejected
                                    }
                                    syncDraftForItem(item)
                                }
                            }
                        }

                        for ((sentItemId, sentSeq) in sentSeqs) {
                            if (sentItemId !in ackedIds) {
                                unackedSeqs[sentItemId] = sentSeq
                            }
                        }

                        for ((flaggedItemId, sentFlagged) in sentFlags) {
                            val item = items[flaggedItemId] ?: continue
                            item.serverFlagged = sentFlagged
                            item.dirtyFlag = item.flagged != sentFlagged
                            syncDraftForItem(item)
                        }

                        callbacks.onStateChanged()
                    }

                    is ApiResult.HttpFailure -> {
                        if (positionToSend != null) {
                            callbacks.restorePendingPositionIfUnchanged(positionToSend)
                        }
                        val batchPartId = changesChunk.firstOrNull()?.partId
                            ?: flagsChunk.firstOrNull()?.partId
                            ?: positionToSend?.partId
                        handleSaveHttpFailure(result, batchPartId)
                        batchAborted = true
                        break
                    }

                    is ApiResult.NetworkFailure -> {
                        if (positionToSend != null) {
                            callbacks.restorePendingPositionIfUnchanged(positionToSend)
                        }
                        handleSaveNetworkFailure()
                        batchAborted = true
                        break
                    }
                }
            }

            if (batchAborted) break

            if (closedReasonDetected != null) {
                val targetClosedPartId = closedPartId
                if (closedReasonDetected == OpenApiAttemptApi.CODE_PART_CLOSED && targetClosedPartId != null) {
                    callbacks.onPartClosed(targetClosedPartId)
                } else {
                    callbacks.onAttemptClosed(AttemptClosedReason.TimeUp)
                }
                break
            }

            if (staleDetected) {
                callbacks.onStaleDetected()
                break
            }

            val moreReady = flushRequestedWhileRunning ||
                callbacks.hasPendingPosition() ||
                items.values.any { item ->
                    !item.partClosed && (
                        item.dirtyFlag ||
                            (hasReadyDirtyAnswer(item) && unackedSeqs[item.itemId] != item.localSeq)
                        )
                }
            if (!moreReady) break
        }
    }

    private fun handleSaveHttpFailure(failure: ApiResult.HttpFailure, batchPartId: PartId?) {
        if (!callbacks.isOnline()) {
            callbacks.onStateChanged()
            return
        }
        val code = failure.error?.code
        when {
            failure.statusCode == HTTP_UNAUTHORIZED -> {
                callbacks.onStateChanged()
            }

            failure.statusCode == HTTP_CONFLICT &&
                (code == OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED ||
                    code == OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE) -> {
                lastErrorReason = UnsavedReason.SessionConflict
                cancelAllPendingTimers()
                callbacks.onStateChanged()
                callbacks.onSessionConflict()
            }

            failure.statusCode == HTTP_CONFLICT && code == OpenApiAttemptApi.CODE_PART_CLOSED -> {
                val targetPartId = batchPartId ?: items.values.firstOrNull { !it.partClosed }?.partId
                if (targetPartId != null) {
                    callbacks.onPartClosed(targetPartId)
                } else {
                    callbacks.onAttemptClosed(AttemptClosedReason.NotOpen)
                }
            }

            failure.statusCode == HTTP_CONFLICT &&
                (code == OpenApiAttemptApi.CODE_TIME_UP || code == OpenApiAttemptApi.CODE_ATTEMPT_NOT_OPEN) -> {
                val reason = if (code == OpenApiAttemptApi.CODE_TIME_UP) {
                    AttemptClosedReason.TimeUp
                } else {
                    AttemptClosedReason.NotOpen
                }
                callbacks.onAttemptClosed(reason)
            }

            failure.statusCode == HTTP_TOO_MANY_REQUESTS -> {
                val delayMs = if (failure.retryAfterSeconds != null) {
                    failure.retryAfterSeconds.coerceAtLeast(1L) * 1_000L
                } else {
                    backoff.nextDelayMs()
                }
                val displaySeconds = (delayMs / 1_000L).coerceAtLeast(1L)
                lastErrorReason = UnsavedReason.RateLimited(displaySeconds)
                callbacks.onStateChanged()
                scheduleRetry(delayMs)
            }

            failure.statusCode in 500..599 -> {
                lastErrorReason = UnsavedReason.ServerError
                callbacks.onStateChanged()
                scheduleRetry(backoff.nextDelayMs())
            }

            else -> {
                lastErrorReason = UnsavedReason.NetworkError
                callbacks.onStateChanged()
                scheduleRetry(backoff.nextDelayMs())
            }
        }
    }

    private fun handleSaveNetworkFailure() {
        if (!callbacks.isOnline()) {
            callbacks.onStateChanged()
            return
        }
        lastErrorReason = UnsavedReason.NetworkError
        callbacks.onStateChanged()
        scheduleRetry(backoff.nextDelayMs())
    }

    private fun scheduleRetry(delayMs: Long) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            retryJob = null
            if (!destroyed && callbacks.isOnline() && callbacks.activeSessionId() != null) {
                scheduleFlush()
            }
        }
    }

    private fun hasReadyDirtyAnswer(item: MutableItemEntry): Boolean =
        item.hasUnresolvedAnswer() && !debounceJobs.containsKey(item.itemId)

    private fun syncDraftForItem(item: MutableItemEntry) {
        if (!item.dirtyFlag && !item.hasUnresolvedAnswer()) {
            draftStore.deleteDraft(attemptId, item.itemId)
        } else {
            draftStore.writeDraft(attemptId, item.itemId, item.toDraftRecord())
        }
    }

    private fun reconcileErrorState() {
        when (lastErrorReason) {
            UnsavedReason.Rejected -> {
                if (items.values.none { !it.partClosed && it.rejectedReason != null }) {
                    lastErrorReason = null
                }
            }

            UnsavedReason.NetworkError,
            UnsavedReason.ServerError,
            is UnsavedReason.RateLimited,
            -> {
                if (!hasAnyPendingWork() && flushJob?.isActive != true) {
                    retryJob?.cancel()
                    retryJob = null
                    lastErrorReason = null
                }
            }

            UnsavedReason.SessionConflict,
            null,
            -> Unit
        }
    }

    private fun cancelDebounce(itemId: ItemId) {
        debounceJobs.remove(itemId)?.cancel()
    }

    private fun cancelEssayMaxWait(itemId: ItemId) {
        essayMaxWaitJobs.remove(itemId)?.cancel()
    }

    private fun cancelItemTimers(itemId: ItemId) {
        cancelDebounce(itemId)
        cancelEssayMaxWait(itemId)
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_CONFLICT = 409
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
