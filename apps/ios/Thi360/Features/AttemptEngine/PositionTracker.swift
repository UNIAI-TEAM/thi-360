import Foundation

@MainActor
final class PositionTracker {
    static let debounceDuration: Duration = .milliseconds(400)

    private let attemptId: String
    private let draftStore: DraftStore
    private let clock: any EngineClock
    private let onFlushRequested: @MainActor (Bool) -> Void

    private(set) var current: Position?
    private(set) var isDirty: Bool = false

    private var debounceTask: Task<Void, Never>?
    private var persistTask: Task<Void, Never>?
    private var disposed = false

    init(
        attemptId: String,
        draftStore: DraftStore,
        clock: any EngineClock,
        onFlushRequested: @escaping @MainActor (Bool) -> Void
    ) {
        self.attemptId = attemptId
        self.draftStore = draftStore
        self.clock = clock
        self.onFlushRequested = onFlushRequested
    }

    func setInitial(_ position: Position?) {
        current = position
    }

    func update(
        partId: PartID,
        itemId: ItemID?,
        trackDirty: Bool,
        canSync: Bool,
        hasUnsavedChanges: Bool
    ) {
        guard !disposed else { return }
        let next = Position(partId: partId, itemId: itemId)
        current = next
        schedulePersist(next)

        guard trackDirty else { return }
        isDirty = true
        debounceTask?.cancel()
        debounceTask = nil

        guard canSync else { return }

        if hasUnsavedChanges {
            onFlushRequested(true)
            return
        }

        debounceTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                try await self.clock.sleep(for: Self.debounceDuration)
            } catch {
                return
            }
            guard !self.disposed, !Task.isCancelled else { return }
            self.debounceTask = nil
            if self.isDirty {
                self.onFlushRequested(false)
            }
        }
    }

    func pendingPositionForFlush() -> Position? {
        guard isDirty else { return nil }
        return current
    }

    func markSent(_ sentPosition: Position) {
        if current == sentPosition {
            isDirty = false
        }
    }

    func cancelDebounce() {
        debounceTask?.cancel()
        debounceTask = nil
    }

    func waitForPendingPersist() async {
        await persistTask?.value
    }

    func dispose() {
        disposed = true
        cancelDebounce()
    }

    private func schedulePersist(_ position: Position) {
        let previous = persistTask
        let store = draftStore
        let id = attemptId
        persistTask = Task {
            await previous?.value
            try? await store.writePosition(position, attemptId: id)
        }
    }
}
