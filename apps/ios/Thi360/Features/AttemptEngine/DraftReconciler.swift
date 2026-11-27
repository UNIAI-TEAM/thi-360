import Foundation

struct ItemSyncRecord: Equatable, Sendable {
    let itemId: ItemID
    let partId: PartID
    var partClosed: Bool
    var localSeq: Int64
    var serverConfirmedSeq: Int64
    var answer: AnswerValue?
    var flagged: Bool
    var serverFlagged: Bool
    var dirtyAnswer: Bool
    var dirtyFlag: Bool
    var rejectedReason: String?
    var rejectedSeq: Int64?

    var hasUnresolvedAnswer: Bool {
        if partClosed || localSeq <= 0 {
            return false
        }
        if rejectedReason != nil,
           !dirtyAnswer,
           rejectedSeq == nil || localSeq <= (rejectedSeq ?? 0)
        {
            return false
        }
        return dirtyAnswer || localSeq > serverConfirmedSeq
    }

    var isDirty: Bool {
        !partClosed && (dirtyFlag || hasUnresolvedAnswer)
    }

    func toLocalAnswer() -> LocalAnswer {
        let sync: AnswerSyncState
        if partClosed {
            sync = .locked
        } else if rejectedReason != nil && !dirtyAnswer && (rejectedSeq == nil || localSeq <= (rejectedSeq ?? 0)) {
            sync = .rejected
        } else if isDirty {
            sync = .pending
        } else {
            sync = .saved
        }
        return LocalAnswer(value: answer, seq: localSeq, flagged: flagged, sync: sync)
    }

    func toDraftRecord() -> ItemDraftRecord {
        ItemDraftRecord(
            itemId: itemId,
            partId: partId,
            seq: localSeq,
            answer: answer,
            flagged: flagged,
            dirtyAnswer: dirtyAnswer,
            dirtyFlag: dirtyFlag
        )
    }
}

struct DraftReconcileResult: Equatable, Sendable {
    let records: [ItemID: ItemSyncRecord]
    let orderedItemIds: [ItemID]
    let draftsToDelete: [ItemID]
    let draftsToPersist: [ItemDraftRecord]
}

enum DraftReconciler {
    static func reconcile(
        attempt: Components.Schemas.AttemptStateResponse,
        decodedServerAnswers: [ItemID: AnswerValue],
        existing: [ItemID: ItemSyncRecord],
        diskDrafts: [ItemID: ItemDraftRecord]
    ) -> DraftReconcileResult {
        let attemptClosed = attempt.state != .IN_PROGRESS
        var records: [ItemID: ItemSyncRecord] = [:]
        var orderedIds: [ItemID] = []
        var draftsToDelete: [ItemID] = []
        var draftsToPersist: [ItemDraftRecord] = []

        for part in PartSelection.sortedParts(from: attempt) {
            let partId = part.partId ?? ""
            let partClosed = attemptClosed || part.state != .OPEN

            for item in part.items ?? [] {
                guard let itemId = item.itemId,
                      !itemId.isEmpty,
                      item.questionType != .QUESTION_SET
                else {
                    continue
                }

                orderedIds.append(itemId)
                let serverSeq = item.answerSeq ?? 0
                let serverFlagged = item.flagged ?? false
                let serverAnswer = decodedServerAnswers[itemId]
                let prior = existing[itemId]
                let draft = diskDrafts[itemId]

                if partClosed {
                    records[itemId] = ItemSyncRecord(
                        itemId: itemId,
                        partId: partId,
                        partClosed: true,
                        localSeq: serverSeq,
                        serverConfirmedSeq: max(prior?.serverConfirmedSeq ?? 0, serverSeq),
                        answer: serverAnswer,
                        flagged: serverFlagged,
                        serverFlagged: serverFlagged,
                        dirtyAnswer: false,
                        dirtyFlag: false,
                        rejectedReason: nil,
                        rejectedSeq: nil
                    )
                    if draft != nil || prior?.isDirty == true {
                        draftsToDelete.append(itemId)
                    }
                    continue
                }

                var localSeq = serverSeq
                var answer = serverAnswer
                var dirtyAnswer = false
                var flagged = serverFlagged
                var dirtyFlag = false
                var rejectedReason: String?
                var rejectedSeq: Int64?
                let confirmed = max(prior?.serverConfirmedSeq ?? 0, serverSeq)

                if let prior {
                    let priorSeq = prior.localSeq
                    let draftSeq = draft?.seq ?? 0
                    if let draft, draftSeq > max(confirmed, priorSeq) {
                        localSeq = draft.seq
                        answer = draft.answer
                        dirtyAnswer = true
                    } else if priorSeq > confirmed {
                        localSeq = priorSeq
                        answer = prior.answer
                        rejectedReason = prior.rejectedReason
                        rejectedSeq = prior.rejectedSeq
                        if prior.rejectedReason != nil,
                           !prior.dirtyAnswer,
                           let rSeq = prior.rejectedSeq,
                           priorSeq <= rSeq
                        {
                            dirtyAnswer = false
                        } else {
                            dirtyAnswer = true
                        }
                    } else {
                        localSeq = confirmed
                        answer = serverAnswer
                        dirtyAnswer = false
                    }

                    if prior.dirtyFlag {
                        flagged = prior.flagged
                        dirtyFlag = flagged != serverFlagged
                    } else if let draft {
                        let wantsFlagSync = draft.dirtyFlag ?? (draft.flagged != serverFlagged)
                        if wantsFlagSync && draft.flagged != serverFlagged {
                            flagged = draft.flagged
                            dirtyFlag = true
                        }
                    }
                } else if let draft {
                    if draft.seq > serverSeq {
                        localSeq = draft.seq
                        answer = draft.answer
                        dirtyAnswer = true
                    }
                    let wantsFlagSync = draft.dirtyFlag ?? (draft.flagged != serverFlagged)
                    if wantsFlagSync && draft.flagged != serverFlagged {
                        flagged = draft.flagged
                        dirtyFlag = true
                    }
                }

                let record = ItemSyncRecord(
                    itemId: itemId,
                    partId: partId,
                    partClosed: false,
                    localSeq: localSeq,
                    serverConfirmedSeq: confirmed,
                    answer: answer,
                    flagged: flagged,
                    serverFlagged: serverFlagged,
                    dirtyAnswer: dirtyAnswer,
                    dirtyFlag: dirtyFlag,
                    rejectedReason: rejectedReason,
                    rejectedSeq: rejectedSeq
                )
                records[itemId] = record

                if !record.isDirty && draft != nil {
                    draftsToDelete.append(itemId)
                } else if record.isDirty && draft != nil && record.localSeq <= serverSeq && record.dirtyFlag {
                    draftsToPersist.append(record.toDraftRecord())
                }
            }
        }

        return DraftReconcileResult(
            records: records,
            orderedItemIds: orderedIds,
            draftsToDelete: draftsToDelete,
            draftsToPersist: draftsToPersist
        )
    }
}
