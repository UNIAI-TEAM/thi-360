import Foundation

enum SaveQueueEvent: Sendable {
    case serverNow(Date)
    case answersChanged([ItemID: LocalAnswer])
    case statusChanged
    case staleDetected
    case partClosed(partId: PartID)
    case attemptClosed(ClosedReason)
    case sessionRevoked
    case unauthorized
}

@MainActor
final class SaveQueue {
    static let maxBatchSize = 50
    static let maxFinalAnswers = 500

    private let attemptId: String
    private let api: any AttemptAPIClient
    private let draftStore: DraftStore
    private let clock: any EngineClock
    private let positionTracker: PositionTracker
    private let sessionIdProvider: @MainActor () -> String?
    private let isReadOnly: @MainActor () -> Bool
    private let isOnline: @MainActor () -> Bool
    private let onEvent: @MainActor (SaveQueueEvent) -> Void

    private var records: [ItemID: ItemSyncRecord] = [:]
    private var orderedItemIds: [ItemID] = []
    private var dirtyQueueOrder: [ItemID] = []

    private var debounceTasks: [ItemID: Task<Void, Never>] = [:]
    private var essayMaxWaitTasks: [ItemID: Task<Void, Never>] = [:]
    private var retryTask: Task<Void, Never>?
    private var flushTask: Task<Void, Never>?
    private var pendingDraftWriteTask: Task<Void, Never>?

    private var backoff = Backoff()
    private(set) var lastErrorReason: SaveFailureReason?
    private var disposed = false

    init(
        attemptId: String,
        api: any AttemptAPIClient,
        draftStore: DraftStore,
        clock: any EngineClock,
        positionTracker: PositionTracker,
        sessionIdProvider: @escaping @MainActor () -> String?,
        isReadOnly: @escaping @MainActor () -> Bool,
        isOnline: @escaping @MainActor () -> Bool,
        onEvent: @escaping @MainActor (SaveQueueEvent) -> Void
    ) {
        self.attemptId = attemptId
        self.api = api
        self.draftStore = draftStore
        self.clock = clock
        self.positionTracker = positionTracker
        self.sessionIdProvider = sessionIdProvider
        self.isReadOnly = isReadOnly
        self.isOnline = isOnline
        self.onEvent = onEvent
    }

    var isFlushInFlight: Bool {
        flushTask != nil
    }

    var allItemIds: [ItemID] {
        orderedItemIds
    }

    var hasDirtyChanges: Bool {
        records.values.contains { $0.isDirty }
    }

    var localAnswersSnapshot: [ItemID: LocalAnswer] {
        var result: [ItemID: LocalAnswer] = [:]
        result.reserveCapacity(records.count)
        for (id, rec) in records {
            result[id] = rec.toLocalAnswer()
        }
        return result
    }

    func canEdit(itemId: ItemID) -> Bool {
        guard !disposed, !isReadOnly(), let rec = records[itemId] else {
            return false
        }
        return !rec.partClosed
    }

    func partId(for itemId: ItemID) -> PartID? {
        records[itemId]?.partId
    }

    func reconcile(
        snapshot: AttemptStateSnapshot,
        diskDrafts: [ItemID: ItemDraftRecord]
    ) {
        let reconciled = DraftReconciler.reconcile(
            attempt: snapshot.response,
            decodedServerAnswers: snapshot.decodedAnswers,
            existing: records,
            diskDrafts: diskDrafts
        )
        records = reconciled.records
        orderedItemIds = reconciled.orderedItemIds

        var retainedDirty: [ItemID] = []
        var seen = Set<ItemID>()
        for id in dirtyQueueOrder {
            if let rec = records[id], rec.isDirty {
                retainedDirty.append(id)
                seen.insert(id)
            }
        }
        for id in orderedItemIds where !seen.contains(id) {
            if let rec = records[id], rec.isDirty {
                retainedDirty.append(id)
                seen.insert(id)
            }
        }
        dirtyQueueOrder = retainedDirty

        for (id, rec) in records where rec.partClosed {
            cancelItemTimers(id)
        }

        if !reconciled.draftsToDelete.isEmpty {
            let idsToDelete = reconciled.draftsToDelete
            enqueueDraftOp { store, attemptId in
                for itemId in idsToDelete {
                    try? await store.deleteDraft(attemptId: attemptId, itemId: itemId)
                }
            }
        }
        if !reconciled.draftsToPersist.isEmpty {
            let draftsToWrite = reconciled.draftsToPersist
            enqueueDraftOp { store, attemptId in
                for draft in draftsToWrite {
                    try? await store.saveDraft(draft, attemptId: attemptId)
                }
            }
        }

        onEvent(.answersChanged(localAnswersSnapshot))
        onEvent(.statusChanged)
    }

    func recordAnswer(itemId: ItemID, value: AnswerValue) {
        guard !disposed, !isReadOnly(), var rec = records[itemId], !rec.partClosed else {
            return
        }

        rec.localSeq += 1
        rec.answer = value
        rec.dirtyAnswer = true
        rec.rejectedReason = nil
        rec.rejectedSeq = nil
        records[itemId] = rec
        appendToDirtyQueueIfNeeded(itemId)

        let draft = rec.toDraftRecord()
        enqueueDraftOp { store, attemptId in
            try? await store.saveDraft(draft, attemptId: attemptId)
        }

        if lastErrorReason != nil && (lastErrorReason != .rejected || !hasRejectedItems()) {
            lastErrorReason = nil
        }

        onEvent(.answersChanged(localAnswersSnapshot))
        onEvent(.statusChanged)

        cancelDebounce(itemId)

        switch value.saveSchedule {
        case .immediate:
            cancelEssayMaxWait(itemId)
            if isOnline() && sessionIdProvider() != nil {
                scheduleFlush()
            }

        case let .debounced(delay):
            cancelEssayMaxWait(itemId)
            debounceTasks[itemId] = Task { @MainActor [weak self] in
                guard let self else { return }
                do {
                    try await self.clock.sleep(for: delay)
                } catch {
                    return
                }
                guard !self.disposed, !Task.isCancelled else { return }
                self.debounceTasks.removeValue(forKey: itemId)
                if self.isOnline() && !self.isReadOnly() && self.sessionIdProvider() != nil {
                    self.scheduleFlush()
                }
            }

        case let .essay(debounce, maxWait):
            if essayMaxWaitTasks[itemId] == nil {
                essayMaxWaitTasks[itemId] = Task { @MainActor [weak self] in
                    guard let self else { return }
                    do {
                        try await self.clock.sleep(for: maxWait)
                    } catch {
                        return
                    }
                    guard !self.disposed, !Task.isCancelled else { return }
                    self.essayMaxWaitTasks.removeValue(forKey: itemId)
                    self.cancelDebounce(itemId)
                    if self.isOnline() && !self.isReadOnly() && self.sessionIdProvider() != nil {
                        self.scheduleFlush()
                    }
                }
            }

            debounceTasks[itemId] = Task { @MainActor [weak self] in
                guard let self else { return }
                do {
                    try await self.clock.sleep(for: debounce)
                } catch {
                    return
                }
                guard !self.disposed, !Task.isCancelled else { return }
                self.debounceTasks.removeValue(forKey: itemId)
                self.cancelEssayMaxWait(itemId)
                if self.isOnline() && !self.isReadOnly() && self.sessionIdProvider() != nil {
                    self.scheduleFlush()
                }
            }
        }
    }

    func recordFlag(itemId: ItemID, flagged: Bool) {
        guard !disposed, !isReadOnly(), var rec = records[itemId], !rec.partClosed else {
            return
        }

        rec.flagged = flagged
        rec.dirtyFlag = (flagged != rec.serverFlagged)
        records[itemId] = rec

        if rec.isDirty {
            appendToDirtyQueueIfNeeded(itemId)
            let draft = rec.toDraftRecord()
            enqueueDraftOp { store, attemptId in
                try? await store.saveDraft(draft, attemptId: attemptId)
            }
        } else {
            removeFromDirtyQueue(itemId)
            enqueueDraftOp { store, attemptId in
                try? await store.deleteDraft(attemptId: attemptId, itemId: itemId)
            }
        }

        if lastErrorReason != .rejected {
            lastErrorReason = nil
        }

        onEvent(.answersChanged(localAnswersSnapshot))
        onEvent(.statusChanged)

        if rec.dirtyFlag && isOnline() && sessionIdProvider() != nil {
            scheduleFlush()
        }
    }

    func scheduleFlush() {
        guard !disposed, !isReadOnly(), isOnline(), sessionIdProvider() != nil else {
            return
        }
        if flushTask != nil {
            return
        }
        retryTask?.cancel()
        retryTask = nil
        flushTask = Task { @MainActor [weak self] in
            await self?.runFlushLoop()
        }
        onEvent(.statusChanged)
    }

    func flushNow() async {
        guard !disposed, !isReadOnly() else { return }
        cancelAllPendingTimers()
        cancelRetryTimer()
        guard isOnline(), sessionIdProvider() != nil else {
            onEvent(.statusChanged)
            return
        }
        if let existing = flushTask {
            await existing.value
            if hasReadyWork() && isOnline() && !isReadOnly() && sessionIdProvider() != nil {
                let next = Task<Void, Never> { @MainActor [weak self] in
                    await self?.runFlushLoop()
                }
                flushTask = next
                onEvent(.statusChanged)
                await next.value
            }
            return
        }
        let task = Task<Void, Never> { @MainActor [weak self] in
            await self?.runFlushLoop()
        }
        flushTask = task
        onEvent(.statusChanged)
        await task.value
    }

    func waitForCurrentFlush() async {
        await flushTask?.value
    }

    func waitForPendingDraftWrites() async {
        await pendingDraftWriteTask?.value
    }

    func handleOnlineReset() {
        backoff.reset()
        retryTask?.cancel()
        retryTask = nil
        if lastErrorReason != .rejected && lastErrorReason != .sessionConflict {
            lastErrorReason = nil
        }
        onEvent(.statusChanged)
    }

    func handleOffline() {
        retryTask?.cancel()
        retryTask = nil
        onEvent(.statusChanged)
    }

    func clearTransientError() {
        if lastErrorReason != .rejected || !hasRejectedItems() {
            lastErrorReason = nil
        }
        onEvent(.statusChanged)
    }

    func cancelInFlightFlush() {
        flushTask?.cancel()
        flushTask = nil
    }

    func markSessionConflictError() {
        cancelAllPendingTimers()
        retryTask?.cancel()
        retryTask = nil
        lastErrorReason = .sessionConflict
        onEvent(.statusChanged)
    }

    func cancelAllPendingTimers() {
        for task in debounceTasks.values {
            task.cancel()
        }
        debounceTasks.removeAll()
        for task in essayMaxWaitTasks.values {
            task.cancel()
        }
        essayMaxWaitTasks.removeAll()
        positionTracker.cancelDebounce()
    }

    func cancelRetryTimer() {
        retryTask?.cancel()
        retryTask = nil
    }

    func lockItems(partId: PartID?) {
        var deletedIds: [ItemID] = []
        for id in orderedItemIds {
            guard var rec = records[id] else { continue }
            if let partId, rec.partId != partId { continue }
            rec.partClosed = true
            rec.dirtyAnswer = false
            rec.dirtyFlag = false
            records[id] = rec
            cancelItemTimers(id)
            removeFromDirtyQueue(id)
            deletedIds.append(id)
        }
        if !deletedIds.isEmpty {
            let idsToDelete = deletedIds
            enqueueDraftOp { store, attemptId in
                for id in idsToDelete {
                    try? await store.deleteDraft(attemptId: attemptId, itemId: id)
                }
            }
        }
        onEvent(.answersChanged(localAnswersSnapshot))
        onEvent(.statusChanged)
    }

    func collectFinalAnswers(partId: PartID?) -> [AttemptAnswerChangeInput] {
        var results: [AttemptAnswerChangeInput] = []
        var visited = Set<ItemID>()

        func consider(_ itemId: ItemID) {
            guard !visited.contains(itemId),
                  let rec = records[itemId],
                  !rec.partClosed,
                  rec.hasUnresolvedAnswer,
                  let answer = rec.answer
            else {
                return
            }
            if let partId, rec.partId != partId {
                return
            }
            visited.insert(itemId)
            results.append(AttemptAnswerChangeInput(itemId: rec.itemId, seq: rec.localSeq, answer: answer))
        }

        for id in dirtyQueueOrder {
            consider(id)
            if results.count >= Self.maxFinalAnswers {
                return results
            }
        }
        for id in orderedItemIds {
            consider(id)
            if results.count >= Self.maxFinalAnswers {
                return results
            }
        }
        return results
    }

    func applyFinalAnswerResults(_ results: [Components.Schemas.FinalAnswerResult]) {
        for result in results {
            guard var rec = records[result.itemId] else { continue }
            switch result.status {
            case .SAVED:
                rec.serverConfirmedSeq = max(rec.serverConfirmedSeq, result.seq)
                rec.dirtyAnswer = rec.localSeq > rec.serverConfirmedSeq
                if !rec.dirtyAnswer {
                    rec.rejectedReason = nil
                    rec.rejectedSeq = nil
                }
                records[result.itemId] = rec
                syncDraftAfterAck(rec)
            case .STALE:
                rec.serverConfirmedSeq = max(rec.serverConfirmedSeq, result.seq)
                if rec.localSeq <= result.seq {
                    rec.dirtyAnswer = false
                    rec.rejectedReason = nil
                    rec.rejectedSeq = nil
                }
                records[result.itemId] = rec
                syncDraftAfterAck(rec)
            case .REJECTED:
                let reason = result.reason ?? "INVALID_ANSWER"
                rec.rejectedReason = reason
                rec.rejectedSeq = result.seq
                rec.dirtyAnswer = rec.localSeq > result.seq
                if reason == "TIME_UP" || reason == "PART_CLOSED" {
                    rec.partClosed = true
                    rec.dirtyAnswer = false
                    rec.dirtyFlag = false
                } else {
                    lastErrorReason = .rejected
                }
                records[result.itemId] = rec
                syncDraftAfterAck(rec)
            }
        }
        onEvent(.answersChanged(localAnswersSnapshot))
        onEvent(.statusChanged)
    }

    func dispose() {
        disposed = true
        cancelAllPendingTimers()
        cancelRetryTimer()
        flushTask?.cancel()
        flushTask = nil
    }

    private func runFlushLoop() async {
        defer {
            flushTask = nil
            onEvent(.statusChanged)
        }

        var unackedSeqs: [ItemID: Int64] = [:]

        while !disposed && !Task.isCancelled && isOnline() && !isReadOnly() {
            guard let sessionId = sessionIdProvider() else {
                break
            }

            await waitForPendingDraftWrites()
            await positionTracker.waitForPendingPersist()
            guard !disposed, !Task.isCancelled, isOnline(), !isReadOnly() else {
                break
            }

            let readyChanges = collectReadyChanges(excludingUnacked: unackedSeqs)
            let readyFlags = collectReadyFlags()
            let pendingPosition = positionTracker.pendingPositionForFlush()

            if readyChanges.isEmpty && readyFlags.isEmpty && pendingPosition == nil {
                break
            }

            let changeBatches = chunk(readyChanges, size: Self.maxBatchSize)
            let flagBatches = chunk(readyFlags, size: Self.maxBatchSize)
            let batchCount = max(1, max(changeBatches.count, flagBatches.count))

            var staleDetected = false
            var closedTimeUp = false
            var closedPartIds: [PartID] = []

            do {
                for batchIndex in 0..<batchCount {
                    guard !disposed, !Task.isCancelled, isOnline(), !isReadOnly(),
                          let activeSession = sessionIdProvider(), activeSession == sessionId
                    else {
                        return
                    }

                    let changesChunk = batchIndex < changeBatches.count ? changeBatches[batchIndex] : []
                    let flagsChunk = batchIndex < flagBatches.count ? flagBatches[batchIndex] : []
                    let batchPosition = (batchIndex == batchCount - 1) ? pendingPosition : nil

                    var sentSeqs: [ItemID: Int64] = [:]
                    for change in changesChunk {
                        sentSeqs[change.itemId] = change.seq
                    }
                    var sentFlags: [ItemID: Bool] = [:]
                    for flag in flagsChunk {
                        sentFlags[flag.itemId] = flag.flagged
                    }

                    let response = try await api.saveAnswers(
                        attemptId: attemptId,
                        sessionId: sessionId,
                        changes: changesChunk,
                        flags: flagsChunk,
                        position: batchPosition
                    )
                    guard !disposed, !Task.isCancelled, !isReadOnly() else { return }

                    backoff.reset()
                    if lastErrorReason != .rejected {
                        lastErrorReason = nil
                    }
                    onEvent(.serverNow(response.serverNow))

                    if let batchPosition {
                        positionTracker.markSent(batchPosition)
                    }

                    var ackedIds = Set<ItemID>()
                    var batchHadNonClosingReject = false

                    for result in response.results {
                        ackedIds.insert(result.itemId)
                        unackedSeqs.removeValue(forKey: result.itemId)
                        guard var rec = records[result.itemId] else { continue }

                        switch result.status {
                        case .SAVED:
                            rec.serverConfirmedSeq = max(rec.serverConfirmedSeq, result.seq)
                            rec.dirtyAnswer = rec.localSeq > rec.serverConfirmedSeq
                            if !rec.dirtyAnswer {
                                rec.rejectedReason = nil
                                rec.rejectedSeq = nil
                            }
                            records[result.itemId] = rec
                            syncDraftAfterAck(rec)

                        case .STALE:
                            rec.serverConfirmedSeq = max(rec.serverConfirmedSeq, result.seq)
                            if rec.localSeq <= result.seq {
                                rec.dirtyAnswer = false
                                rec.rejectedReason = nil
                                rec.rejectedSeq = nil
                            }
                            records[result.itemId] = rec
                            staleDetected = true

                        case .REJECTED:
                            let reason = result.reason ?? "INVALID_ANSWER"
                            rec.rejectedReason = reason
                            rec.rejectedSeq = result.seq
                            rec.dirtyAnswer = rec.localSeq > result.seq
                            if reason == "TIME_UP" {
                                rec.partClosed = true
                                rec.dirtyAnswer = false
                                rec.dirtyFlag = false
                                closedTimeUp = true
                            } else if reason == "PART_CLOSED" {
                                rec.partClosed = true
                                rec.dirtyAnswer = false
                                rec.dirtyFlag = false
                                if !closedPartIds.contains(rec.partId) {
                                    closedPartIds.append(rec.partId)
                                }
                            } else {
                                batchHadNonClosingReject = true
                            }
                            records[result.itemId] = rec
                            syncDraftAfterAck(rec)
                        }
                    }

                    for (sentId, sentSeq) in sentSeqs where !ackedIds.contains(sentId) {
                        unackedSeqs[sentId] = sentSeq
                    }

                    for (flagItemId, sentFlagged) in sentFlags {
                        guard var rec = records[flagItemId] else { continue }
                        rec.serverFlagged = sentFlagged
                        rec.dirtyFlag = (rec.flagged != sentFlagged)
                        records[flagItemId] = rec
                        syncDraftAfterAck(rec)
                    }

                    if batchHadNonClosingReject {
                        lastErrorReason = .rejected
                    } else if !response.results.isEmpty || !sentFlags.isEmpty {
                        if lastErrorReason == .rejected && !hasRejectedItems() {
                            lastErrorReason = nil
                        } else if lastErrorReason == .rejected && response.results.contains(where: { $0.status == .SAVED }) {
                            lastErrorReason = nil
                        }
                    }

                    onEvent(.answersChanged(localAnswersSnapshot))
                    onEvent(.statusChanged)
                }

                if closedTimeUp {
                    onEvent(.attemptClosed(.timeUp))
                    break
                }
                if let firstClosedPart = closedPartIds.first {
                    onEvent(.partClosed(partId: firstClosedPart))
                    break
                }
                if staleDetected {
                    onEvent(.staleDetected)
                    break
                }

                if !hasReadyWork(excludingUnacked: unackedSeqs) {
                    break
                }
            } catch let apiError as AttemptAPIError {
                guard !disposed, !Task.isCancelled else { return }
                handleFlushError(apiError)
                break
            } catch {
                guard !disposed, !Task.isCancelled else { return }
                handleFlushError(.network(.unknown))
                break
            }
        }
    }

    private func handleFlushError(_ error: AttemptAPIError) {
        guard !disposed, !isReadOnly() else { return }
        if !isOnline() {
            onEvent(.statusChanged)
            return
        }

        switch error {
        case .unauthorized:
            onEvent(.unauthorized)

        case let .conflict(code, _, _):
            if code == "ATTEMPT_SESSION_REVOKED" || code == "ATTEMPT_OPEN_ELSEWHERE" {
                lastErrorReason = .sessionConflict
                onEvent(.sessionRevoked)
            } else if code == "TIME_UP" {
                onEvent(.attemptClosed(.timeUp))
            } else if code == "ATTEMPT_NOT_OPEN" {
                onEvent(.attemptClosed(.notOpen))
            } else if code == "PART_CLOSED" {
                let activePartId = positionTracker.current?.partId ?? ""
                onEvent(.partClosed(partId: activePartId))
            } else {
                lastErrorReason = .networkError
                scheduleRetry(after: backoff.nextDelay())
            }

        case let .rateLimited(retryAfterSeconds):
            let delaySeconds: Int
            if let retryAfterSeconds {
                delaySeconds = max(1, retryAfterSeconds)
            } else {
                delaySeconds = backoff.nextDelaySeconds()
            }
            lastErrorReason = .rateLimited(delaySeconds)
            scheduleRetry(after: .seconds(delaySeconds))

        case .serverError:
            lastErrorReason = .serverError
            scheduleRetry(after: backoff.nextDelay())

        case .clientError, .network:
            lastErrorReason = .networkError
            scheduleRetry(after: backoff.nextDelay())
        }

        onEvent(.statusChanged)
    }

    private func scheduleRetry(after delay: Duration) {
        retryTask?.cancel()
        retryTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await self.clock.sleep(for: delay)
            } catch {
                return
            }
            guard !self.disposed, !Task.isCancelled else { return }
            self.retryTask = nil
            if self.isOnline() && !self.isReadOnly() && self.sessionIdProvider() != nil {
                self.scheduleFlush()
            }
        }
    }

    private func collectReadyChanges(excludingUnacked unackedSeqs: [ItemID: Int64]) -> [AttemptAnswerChangeInput] {
        var changes: [AttemptAnswerChangeInput] = []
        var visited = Set<ItemID>()

        func consider(_ itemId: ItemID) {
            guard !visited.contains(itemId),
                  let rec = records[itemId],
                  !rec.partClosed,
                  rec.hasUnresolvedAnswer,
                  debounceTasks[itemId] == nil,
                  unackedSeqs[itemId] != rec.localSeq,
                  let answer = rec.answer
            else {
                return
            }
            visited.insert(itemId)
            changes.append(AttemptAnswerChangeInput(itemId: rec.itemId, seq: rec.localSeq, answer: answer))
        }

        for id in dirtyQueueOrder { consider(id) }
        for id in orderedItemIds { consider(id) }
        return changes
    }

    private func collectReadyFlags() -> [AttemptFlagChangeInput] {
        var flags: [AttemptFlagChangeInput] = []
        var visited = Set<ItemID>()

        func consider(_ itemId: ItemID) {
            guard !visited.contains(itemId),
                  let rec = records[itemId],
                  !rec.partClosed,
                  rec.dirtyFlag
            else {
                return
            }
            visited.insert(itemId)
            flags.append(AttemptFlagChangeInput(itemId: rec.itemId, flagged: rec.flagged))
        }

        for id in dirtyQueueOrder { consider(id) }
        for id in orderedItemIds { consider(id) }
        return flags
    }

    private func hasReadyWork(excludingUnacked unackedSeqs: [ItemID: Int64] = [:]) -> Bool {
        if !collectReadyChanges(excludingUnacked: unackedSeqs).isEmpty {
            return true
        }
        if !collectReadyFlags().isEmpty {
            return true
        }
        return positionTracker.pendingPositionForFlush() != nil
    }

    private func hasRejectedItems() -> Bool {
        records.values.contains { $0.toLocalAnswer().sync == .rejected }
    }

    private func syncDraftAfterAck(_ rec: ItemSyncRecord) {
        if !rec.isDirty {
            removeFromDirtyQueue(rec.itemId)
            let itemId = rec.itemId
            enqueueDraftOp { store, attemptId in
                try? await store.deleteDraft(attemptId: attemptId, itemId: itemId)
            }
        } else {
            let draft = rec.toDraftRecord()
            enqueueDraftOp { store, attemptId in
                try? await store.saveDraft(draft, attemptId: attemptId)
            }
        }
    }

    private func appendToDirtyQueueIfNeeded(_ itemId: ItemID) {
        if !dirtyQueueOrder.contains(itemId) {
            dirtyQueueOrder.append(itemId)
        }
    }

    private func removeFromDirtyQueue(_ itemId: ItemID) {
        dirtyQueueOrder.removeAll { $0 == itemId }
    }

    private func cancelDebounce(_ itemId: ItemID) {
        debounceTasks.removeValue(forKey: itemId)?.cancel()
    }

    private func cancelEssayMaxWait(_ itemId: ItemID) {
        essayMaxWaitTasks.removeValue(forKey: itemId)?.cancel()
    }

    private func cancelItemTimers(_ itemId: ItemID) {
        cancelDebounce(itemId)
        cancelEssayMaxWait(itemId)
    }

    private func enqueueDraftOp(_ op: @escaping @Sendable (DraftStore, String) async -> Void) {
        let previous = pendingDraftWriteTask
        let store = draftStore
        let id = attemptId
        pendingDraftWriteTask = Task {
            await previous?.value
            await op(store, id)
        }
    }

    private func chunk<T>(_ items: [T], size: Int) -> [[T]] {
        guard !items.isEmpty, size > 0 else { return [] }
        var result: [[T]] = []
        var index = 0
        while index < items.count {
            let end = min(index + size, items.count)
            result.append(Array(items[index..<end]))
            index = end
        }
        return result
    }
}
