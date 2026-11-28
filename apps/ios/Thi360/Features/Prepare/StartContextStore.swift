import Foundation

struct StartContext: Codable, Equatable, Sendable {
    let entitlementId: String
    let preChoice: PreparePreChoiceInput?
    let examTitle: String
    let examKindLabel: String
    let firstPartName: String?
    let firstPartMinutes: Int?
    let timing: String?

    init(
        entitlementId: String,
        preChoice: PreparePreChoiceInput? = nil,
        examTitle: String,
        examKindLabel: String,
        firstPartName: String? = nil,
        firstPartMinutes: Int? = nil,
        timing: String? = nil
    ) {
        self.entitlementId = entitlementId
        self.preChoice = preChoice
        self.examTitle = examTitle
        self.examKindLabel = examKindLabel
        self.firstPartName = firstPartName
        self.firstPartMinutes = firstPartMinutes
        self.timing = timing
    }

    func withPreChoice(_ preChoice: PreparePreChoiceInput?) -> StartContext {
        StartContext(
            entitlementId: entitlementId,
            preChoice: preChoice,
            examTitle: examTitle,
            examKindLabel: examKindLabel,
            firstPartName: firstPartName,
            firstPartMinutes: firstPartMinutes,
            timing: timing
        )
    }
}

final class StartContextStore: Sendable {
    static let namespace = "attempt"

    static func key(for attemptId: String) -> String {
        "startContext:\(attemptId)"
    }

    private let storeResolver: @Sendable () -> AccountScopedStore?

    init(storeResolver: @escaping @Sendable () -> AccountScopedStore?) {
        self.storeResolver = storeResolver
    }

    convenience init(store: AccountScopedStore) {
        self.init(storeResolver: { store })
    }

    convenience init(provider: AccountScopedStoreProvider, accountId: @escaping @Sendable () -> String?) {
        self.init(storeResolver: { provider.store(for: accountId()) })
    }

    @discardableResult
    func save(_ context: StartContext, attemptId: String) async throws -> Bool {
        let trimmed = attemptId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let store = storeResolver() else {
            return false
        }
        let data = try JSONEncoder().encode(context)
        try await store.write(data, namespace: Self.namespace, key: Self.key(for: trimmed))
        return true
    }

    func read(attemptId: String) async -> StartContext? {
        let trimmed = attemptId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let store = storeResolver(),
              let data = await store.read(namespace: Self.namespace, key: Self.key(for: trimmed))
        else {
            return nil
        }
        return try? JSONDecoder().decode(StartContext.self, from: data)
    }

    @discardableResult
    func move(
        from oldAttemptId: String,
        to newAttemptId: String,
        updatedContext: StartContext? = nil
    ) async throws -> Bool {
        let oldTrimmed = oldAttemptId.trimmingCharacters(in: .whitespacesAndNewlines)
        let newTrimmed = newAttemptId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !newTrimmed.isEmpty else { return false }

        let resolvedContext: StartContext?
        if let updatedContext {
            resolvedContext = updatedContext
        } else {
            resolvedContext = await read(attemptId: oldTrimmed)
        }
        guard let contextToSave = resolvedContext else {
            return false
        }

        let saved = try await save(contextToSave, attemptId: newTrimmed)
        if saved && !oldTrimmed.isEmpty && oldTrimmed != newTrimmed {
            try await delete(attemptId: oldTrimmed)
        }
        return saved
    }

    func delete(attemptId: String) async throws {
        let trimmed = attemptId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let store = storeResolver() else { return }
        try await store.delete(namespace: Self.namespace, key: Self.key(for: trimmed))
    }
}
