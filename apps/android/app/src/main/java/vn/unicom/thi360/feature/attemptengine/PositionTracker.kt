package vn.unicom.thi360.feature.attemptengine

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PositionTracker(
    private val attemptId: UUID,
    private val draftStore: DraftStore,
    private val scope: CoroutineScope,
    private val debounceMs: Long = POSITION_DEBOUNCE_MS,
) {
    private var currentPosition: EnginePosition? = null
    private var dirtyPosition = false
    private var debounceJob: Job? = null

    val current: EnginePosition?
        get() = currentPosition

    val hasPendingSync: Boolean
        get() = dirtyPosition && currentPosition != null

    fun setResolvedPosition(position: EnginePosition?) {
        currentPosition = position
    }

    fun recordUserPosition(
        position: EnginePosition,
        shouldSyncToServer: Boolean,
        hasDirtyChanges: Boolean,
        onFlushNow: () -> Unit,
        onDebouncedFlush: () -> Unit,
    ) {
        currentPosition = position
        draftStore.writePosition(attemptId, position)

        if (!shouldSyncToServer) return
        dirtyPosition = true

        if (hasDirtyChanges) {
            debounceJob?.cancel()
            debounceJob = null
            onFlushNow()
            return
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            debounceJob = null
            if (dirtyPosition) {
                onDebouncedFlush()
            }
        }
    }

    fun consumeForSync(): EnginePosition? {
        if (!dirtyPosition) return null
        val snapshot = currentPosition ?: return null
        dirtyPosition = false
        return snapshot
    }

    fun restoreIfUnchanged(snapshot: EnginePosition) {
        if (currentPosition == snapshot) {
            dirtyPosition = true
        }
    }

    fun cancelPendingTimer() {
        debounceJob?.cancel()
        debounceJob = null
    }
}
