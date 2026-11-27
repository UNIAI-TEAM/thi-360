package vn.unicom.thi360.feature.attemptengine

import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse

object PartSelection {

    private val closedAttemptStates = setOf(
        AttemptStateResponse.State.GRADING,
        AttemptStateResponse.State.PENDING_REVIEW,
        AttemptStateResponse.State.SCORED,
        AttemptStateResponse.State.GRADING_ERROR,
    )

    fun isClosedState(state: AttemptStateResponse.State): Boolean = state in closedAttemptStates

    fun isUnfinishedState(state: AttemptPartStateView.State?): Boolean =
        state == AttemptPartStateView.State.AWAITING_CHOICE ||
            state == AttemptPartStateView.State.GENERATING ||
            state == AttemptPartStateView.State.GENERATION_FAILED

    fun isSelectableState(state: AttemptPartStateView.State?): Boolean =
        state == AttemptPartStateView.State.OPEN || isUnfinishedState(state)

    fun sortedParts(attempt: AttemptStateResponse): List<AttemptPartStateView> =
        attempt.parts.orEmpty().sortedBy { it.ord ?: 0 }

    fun leafItems(part: AttemptPartStateView): List<AttemptItemView> =
        part.items.orEmpty().filter {
            it.itemId != null && it.questionType != AttemptItemView.QuestionType.QUESTION_SET
        }

    fun initial(
        attempt: AttemptStateResponse,
        serverPos: EnginePosition?,
        localPos: EnginePosition?,
    ): AttemptPartStateView? {
        val parts = sortedParts(attempt)
        if (parts.isEmpty()) return null
        val preferredPartId = serverPos?.partId ?: localPos?.partId
        if (preferredPartId != null) {
            val preferred = parts.firstOrNull { it.partId == preferredPartId }
            if (preferred != null && isSelectableState(preferred.state)) {
                return preferred
            }
        }
        return afterClose(attempt)
    }

    fun afterClose(attempt: AttemptStateResponse): AttemptPartStateView? {
        val parts = sortedParts(attempt)
        if (parts.isEmpty()) return null
        val unfinished = parts.firstOrNull { isUnfinishedState(it.state) }
        val firstOpen = parts.firstOrNull { it.state == AttemptPartStateView.State.OPEN }
        if (unfinished != null &&
            (firstOpen == null || (unfinished.ord ?: 0) < (firstOpen.ord ?: 0))
        ) {
            return unfinished
        }
        if (firstOpen != null) return firstOpen
        if (unfinished != null) return unfinished
        return parts.firstOrNull()
    }

    fun parseServerPosition(attempt: AttemptStateResponse): EnginePosition? {
        val raw = attempt.lastPosition ?: return null
        val partIdStr = (raw["partId"] as? JsonPrimitive)?.contentOrNull ?: return null
        val partId = runCatching { UUID.fromString(partIdStr) }.getOrNull() ?: return null
        val itemIdStr = (raw["itemId"] as? JsonPrimitive)?.contentOrNull
        val itemId = itemIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return EnginePosition(partId = partId, itemId = itemId)
    }

    fun initialPosition(
        attempt: AttemptStateResponse,
        serverPos: EnginePosition?,
        localPos: EnginePosition?,
    ): EnginePosition? {
        val selectedPart = initial(attempt, serverPos, localPos) ?: return null
        val partId = selectedPart.partId ?: return null
        val preferredItemId = resolvePreferredItemId(selectedPart, serverPos, localPos)
        return EnginePosition(partId = partId, itemId = preferredItemId)
    }

    fun firstPositionOfPart(part: AttemptPartStateView?): EnginePosition? {
        val partId = part?.partId ?: return null
        val firstLeafId = leafItems(part).firstOrNull()?.itemId
        return EnginePosition(partId = partId, itemId = firstLeafId)
    }

    fun resolveNextPosition(
        attempt: AttemptStateResponse,
        currentPosition: EnginePosition?,
        localFallback: EnginePosition? = null,
    ): EnginePosition? {
        val parts = sortedParts(attempt)
        if (parts.isEmpty()) return null
        val currentPart = currentPosition?.partId?.let { id -> parts.firstOrNull { it.partId == id } }
        if (currentPart != null && isSelectableState(currentPart.state)) {
            val partId = currentPart.partId ?: return null
            val itemId = resolveItemInPart(currentPart, currentPosition.itemId)
            return EnginePosition(partId = partId, itemId = itemId)
        }
        val nextPart = if (currentPosition == null) {
            initial(attempt, parseServerPosition(attempt), localFallback)
        } else {
            afterClose(attempt)
        }
        return firstPositionOfPart(nextPart)
    }

    fun inferClosedReason(
        attempt: AttemptStateResponse,
        hint: AttemptClosedReason? = null,
    ): AttemptClosedReason? {
        if (attempt.state == AttemptStateResponse.State.ABORTED) {
            return AttemptClosedReason.Aborted(attempt.abortReason)
        }
        if (!isClosedState(attempt.state)) {
            return null
        }
        if (hint != null) return hint

        val parts = sortedParts(attempt)
        for (part in parts) {
            val choiceDeadline = part.choiceDeadlineAt
            val closedAt = part.closedAt
            if (part.state == AttemptPartStateView.State.SKIPPED &&
                choiceDeadline != null &&
                closedAt != null &&
                !closedAt.isBefore(choiceDeadline)
            ) {
                return AttemptClosedReason.ChoiceTimeout
            }
        }
        if (parts.any { it.state == AttemptPartStateView.State.SKIPPED && it.choiceDeadlineAt == null }) {
            return AttemptClosedReason.TimeUp
        }
        val attemptDeadline = attempt.deadlineAt
        if (attempt.timing == AttemptStateResponse.Timing.SHARED &&
            attemptDeadline != null &&
            parts.any { part -> part.closedAt?.let { !it.isBefore(attemptDeadline) } == true }
        ) {
            return AttemptClosedReason.TimeUp
        }
        val lastPart = parts.lastOrNull()
        val lastClosedAt = lastPart?.closedAt
        val lastDeadlineAt = lastPart?.deadlineAt
        if (lastClosedAt != null && lastDeadlineAt != null && !lastClosedAt.isBefore(lastDeadlineAt)) {
            return AttemptClosedReason.TimeUp
        }
        return AttemptClosedReason.Submitted
    }

    private fun resolvePreferredItemId(
        part: AttemptPartStateView,
        serverPos: EnginePosition?,
        localPos: EnginePosition?,
    ): UUID? {
        val candidate = serverPos?.itemId ?: localPos?.itemId
        return resolveItemInPart(part, candidate)
    }

    private fun resolveItemInPart(part: AttemptPartStateView, candidateItemId: UUID?): UUID? {
        val leaves = leafItems(part)
        if (leaves.isEmpty()) return null
        if (candidateItemId != null) {
            val directMatch = leaves.firstOrNull { it.itemId == candidateItemId }
            if (directMatch != null) return directMatch.itemId
            val childOfSet = leaves.firstOrNull { it.parentItemId == candidateItemId }
            if (childOfSet != null) return childOfSet.itemId
        }
        return leaves.first().itemId
    }
}
