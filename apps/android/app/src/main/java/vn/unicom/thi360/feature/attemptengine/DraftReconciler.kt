package vn.unicom.thi360.feature.attemptengine

import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse

internal data class MutableItemEntry(
    val itemId: ItemId,
    val partId: PartId,
    var partClosed: Boolean,
    var localSeq: Long,
    var serverConfirmedSeq: Long,
    var answer: AnswerValue?,
    var flagged: Boolean,
    var serverFlagged: Boolean,
    var dirtyAnswer: Boolean,
    var dirtyFlag: Boolean,
    var rejectedReason: String? = null,
    var rejectedSeq: Long? = null,
) {
    fun hasUnresolvedAnswer(): Boolean {
        if (partClosed || localSeq <= 0L || answer == null) return false
        if (rejectedReason != null &&
            !dirtyAnswer &&
            (rejectedSeq == null || localSeq <= rejectedSeq!!)
        ) {
            return false
        }
        return dirtyAnswer || localSeq > serverConfirmedSeq
    }

    fun toLocalAnswer(): LocalAnswer {
        val status = when {
            partClosed -> AnswerSyncStatus.Locked
            dirtyFlag || hasUnresolvedAnswer() -> AnswerSyncStatus.Pending
            rejectedReason != null -> AnswerSyncStatus.Rejected
            else -> AnswerSyncStatus.Saved
        }
        return LocalAnswer(
            value = answer,
            seq = localSeq,
            flagged = flagged,
            sync = status,
            rejectedReason = if (status == AnswerSyncStatus.Rejected) rejectedReason else null,
        )
    }

    fun toDraftRecord(): DraftRecord = DraftRecord(
        seq = localSeq,
        answer = answer,
        flagged = flagged,
        dirtyFlag = dirtyFlag,
    )
}

internal data class ReconcileOutcome(
    val entries: LinkedHashMap<ItemId, MutableItemEntry>,
    val draftsToDelete: List<ItemId>,
    val lockedItemIds: List<ItemId>,
)

internal object DraftReconciler {

    fun reconcile(
        attempt: AttemptStateResponse,
        existingItems: Map<ItemId, MutableItemEntry>,
        storedDrafts: Map<ItemId, DraftRecord>,
    ): ReconcileOutcome {
        val entries = LinkedHashMap<ItemId, MutableItemEntry>()
        val draftsToDelete = mutableListOf<ItemId>()
        val lockedItemIds = mutableListOf<ItemId>()
        val attemptClosed = attempt.state != AttemptStateResponse.State.IN_PROGRESS

        for (part in PartSelection.sortedParts(attempt)) {
            val partId = part.partId ?: continue
            val partClosed = attemptClosed || part.state != AttemptPartStateView.State.OPEN
            for (item in part.items.orEmpty()) {
                val itemId = item.itemId ?: continue
                if (item.questionType == AttemptItemView.QuestionType.QUESTION_SET) continue

                val serverSeq = item.answerSeq ?: 0L
                val serverFlagged = item.flagged ?: false
                val existing = existingItems[itemId]
                val draft = storedDrafts[itemId]

                if (partClosed) {
                    lockedItemIds.add(itemId)
                    if (draft != null || existing != null) {
                        draftsToDelete.add(itemId)
                    }
                    entries[itemId] = MutableItemEntry(
                        itemId = itemId,
                        partId = partId,
                        partClosed = true,
                        localSeq = serverSeq,
                        serverConfirmedSeq = maxOf(existing?.serverConfirmedSeq ?: 0L, serverSeq),
                        answer = item.answer,
                        flagged = serverFlagged,
                        serverFlagged = serverFlagged,
                        dirtyAnswer = false,
                        dirtyFlag = false,
                    )
                    continue
                }

                var localSeq = serverSeq
                var answer = item.answer
                var dirtyAnswer = false
                var flagged = serverFlagged
                var dirtyFlag = false
                var rejectedReason: String? = null
                var rejectedSeq: Long? = null
                val confirmedSeq = maxOf(existing?.serverConfirmedSeq ?: 0L, serverSeq)

                if (existing != null) {
                    val draftSeq = if (draft != null && draft.answer != null) draft.seq else -1L
                    if (draft != null && draftSeq > maxOf(confirmedSeq, existing.localSeq)) {
                        localSeq = draft.seq
                        answer = draft.answer
                        dirtyAnswer = true
                    } else if (existing.localSeq > confirmedSeq && existing.answer != null) {
                        localSeq = existing.localSeq
                        answer = existing.answer
                        rejectedReason = existing.rejectedReason
                        rejectedSeq = existing.rejectedSeq
                        val isStillRejected = rejectedReason != null &&
                            !existing.dirtyAnswer &&
                            (rejectedSeq == null || localSeq <= rejectedSeq)
                        dirtyAnswer = !isStillRejected
                    } else {
                        localSeq = confirmedSeq
                        answer = item.answer
                        dirtyAnswer = false
                        draftsToDelete.add(itemId)
                    }
                    if (existing.dirtyFlag) {
                        flagged = existing.flagged
                        dirtyFlag = flagged != serverFlagged
                    } else if (draft != null && draft.dirtyFlag != false && draft.flagged != serverFlagged) {
                        flagged = draft.flagged
                        dirtyFlag = true
                    }
                } else if (draft != null) {
                    if (draft.seq > serverSeq && draft.answer != null) {
                        localSeq = draft.seq
                        answer = draft.answer
                        dirtyAnswer = true
                    }
                    val draftFlagChanged = when (draft.dirtyFlag) {
                        true -> draft.flagged != serverFlagged
                        false -> false
                        null -> draft.flagged != serverFlagged
                    }
                    if (draftFlagChanged) {
                        flagged = draft.flagged
                        dirtyFlag = true
                    }
                    if (!dirtyAnswer && !dirtyFlag) {
                        draftsToDelete.add(itemId)
                    }
                }

                val entry = MutableItemEntry(
                    itemId = itemId,
                    partId = partId,
                    partClosed = false,
                    localSeq = localSeq,
                    serverConfirmedSeq = confirmedSeq,
                    answer = answer,
                    flagged = flagged,
                    serverFlagged = serverFlagged,
                    dirtyAnswer = dirtyAnswer,
                    dirtyFlag = dirtyFlag,
                    rejectedReason = rejectedReason,
                    rejectedSeq = rejectedSeq,
                )
                if (!entry.dirtyFlag && !entry.hasUnresolvedAnswer() && (draft != null || existing != null)) {
                    draftsToDelete.add(itemId)
                }
                entries[itemId] = entry
            }
        }

        return ReconcileOutcome(
            entries = entries,
            draftsToDelete = draftsToDelete.distinct(),
            lockedItemIds = lockedItemIds,
        )
    }
}
