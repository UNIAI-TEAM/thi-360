import Foundation

struct ItemDraftRecord: Codable, Equatable, Sendable {
    let itemId: ItemID
    let partId: PartID?
    let seq: Int64
    let answer: AnswerValue?
    let flagged: Bool
    let dirtyAnswer: Bool?
    let dirtyFlag: Bool?

    init(
        itemId: ItemID,
        partId: PartID? = nil,
        seq: Int64,
        answer: AnswerValue?,
        flagged: Bool,
        dirtyAnswer: Bool? = nil,
        dirtyFlag: Bool? = nil
    ) {
        self.itemId = itemId
        self.partId = partId
        self.seq = seq
        self.answer = answer
        self.flagged = flagged
        self.dirtyAnswer = dirtyAnswer
        self.dirtyFlag = dirtyFlag
    }
}

actor DraftStore {
    static let namespace = "attempt"

    static func draftKey(attemptId: String, itemId: ItemID) -> String {
        "draft:\(attemptId):\(itemId)"
    }

    static func draftIndexKey(attemptId: String) -> String {
        "draftIndex:\(attemptId)"
    }

    static func positionKey(attemptId: String) -> String {
        "position:\(attemptId)"
    }

    static func sessionKey(attemptId: String) -> String {
        "session:\(attemptId)"
    }

    static func startContextKey(attemptId: String) -> String {
        "startContext:\(attemptId)"
    }

    private let store: AccountScopedStore
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(store: AccountScopedStore) {
        self.store = store
    }

    func readSessionId(attemptId: String) async -> String? {
        guard let data = await store.read(namespace: Self.namespace, key: Self.sessionKey(attemptId: attemptId)),
              let raw = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty
        else {
            return nil
        }
        return raw
    }

    func writeSessionId(_ sessionId: String, attemptId: String) async throws {
        let trimmed = sessionId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        try await store.write(
            Data(trimmed.utf8),
            namespace: Self.namespace,
            key: Self.sessionKey(attemptId: attemptId)
        )
    }

    func deleteSessionId(attemptId: String) async throws {
        try await store.delete(namespace: Self.namespace, key: Self.sessionKey(attemptId: attemptId))
    }

    func readPosition(attemptId: String) async -> Position? {
        guard let data = await store.read(namespace: Self.namespace, key: Self.positionKey(attemptId: attemptId)) else {
            return nil
        }
        return try? decoder.decode(Position.self, from: data)
    }

    func writePosition(_ position: Position, attemptId: String) async throws {
        let data = try encoder.encode(position)
        try await store.write(data, namespace: Self.namespace, key: Self.positionKey(attemptId: attemptId))
    }

    func deletePosition(attemptId: String) async throws {
        try await store.delete(namespace: Self.namespace, key: Self.positionKey(attemptId: attemptId))
    }

    func saveDraft(_ draft: ItemDraftRecord, attemptId: String) async throws {
        let data = try encoder.encode(draft)
        try await store.write(
            data,
            namespace: Self.namespace,
            key: Self.draftKey(attemptId: attemptId, itemId: draft.itemId)
        )
        var indexed = await readDraftIndex(attemptId: attemptId)
        if !indexed.contains(draft.itemId) {
            indexed.append(draft.itemId)
            try await writeDraftIndex(indexed, attemptId: attemptId)
        }
    }

    func readDraft(attemptId: String, itemId: ItemID) async -> ItemDraftRecord? {
        guard let data = await store.read(
            namespace: Self.namespace,
            key: Self.draftKey(attemptId: attemptId, itemId: itemId)
        ) else {
            return nil
        }
        return try? decoder.decode(ItemDraftRecord.self, from: data)
    }

    func loadDrafts(attemptId: String, knownItemIds: [ItemID] = []) async -> [ItemID: ItemDraftRecord] {
        var candidateIds = await readDraftIndex(attemptId: attemptId)
        var seen = Set(candidateIds)
        for itemId in knownItemIds where !seen.contains(itemId) {
            seen.insert(itemId)
            candidateIds.append(itemId)
        }

        var loaded: [ItemID: ItemDraftRecord] = [:]
        for itemId in candidateIds {
            if let draft = await readDraft(attemptId: attemptId, itemId: itemId) {
                loaded[itemId] = draft
            }
        }
        return loaded
    }

    func deleteDraft(attemptId: String, itemId: ItemID) async throws {
        try await store.delete(namespace: Self.namespace, key: Self.draftKey(attemptId: attemptId, itemId: itemId))
        var indexed = await readDraftIndex(attemptId: attemptId)
        if let index = indexed.firstIndex(of: itemId) {
            indexed.remove(at: index)
            if indexed.isEmpty {
                try await store.delete(namespace: Self.namespace, key: Self.draftIndexKey(attemptId: attemptId))
            } else {
                try await writeDraftIndex(indexed, attemptId: attemptId)
            }
        }
    }

    func deleteStartContext(attemptId: String) async throws {
        try await store.delete(namespace: Self.namespace, key: Self.startContextKey(attemptId: attemptId))
    }

    func clearAttempt(
        attemptId: String,
        keepStartContext: Bool,
        knownItemIds: [ItemID] = []
    ) async throws {
        let indexed = await readDraftIndex(attemptId: attemptId)
        let allItemIds = Set(indexed).union(knownItemIds)
        for itemId in allItemIds {
            try await store.delete(namespace: Self.namespace, key: Self.draftKey(attemptId: attemptId, itemId: itemId))
        }
        try await store.delete(namespace: Self.namespace, key: Self.draftIndexKey(attemptId: attemptId))
        try await deletePosition(attemptId: attemptId)
        try await deleteSessionId(attemptId: attemptId)
        if !keepStartContext {
            try await deleteStartContext(attemptId: attemptId)
        }
    }

    private func readDraftIndex(attemptId: String) async -> [ItemID] {
        guard let data = await store.read(namespace: Self.namespace, key: Self.draftIndexKey(attemptId: attemptId)),
              let decoded = try? decoder.decode([ItemID].self, from: data)
        else {
            return []
        }
        return decoded
    }

    private func writeDraftIndex(_ ids: [ItemID], attemptId: String) async throws {
        let data = try encoder.encode(ids)
        try await store.write(data, namespace: Self.namespace, key: Self.draftIndexKey(attemptId: attemptId))
    }
}

struct AttemptDataCleanup: SessionCleanupParticipant, Sendable {
    static let participantId = "attempt.localData"

    let id: String = AttemptDataCleanup.participantId
    private let storeProvider: AccountScopedStoreProvider
    private let onAccountLogout: (@Sendable (String) async -> Void)?

    init(
        storeProvider: AccountScopedStoreProvider,
        onAccountLogout: (@Sendable (String) async -> Void)? = nil
    ) {
        self.storeProvider = storeProvider
        self.onAccountLogout = onAccountLogout
    }

    func onLogout(accountId: String) async throws {
        if let onAccountLogout {
            await onAccountLogout(accountId)
        }
        guard let store = storeProvider.store(for: accountId) else { return }
        try await store.deleteNamespace(DraftStore.namespace)
    }

    func onAppStart() async throws {}
}
