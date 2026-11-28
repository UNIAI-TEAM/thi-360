import Foundation

actor ResultRepository {
    static let reviewReuseTTLSeconds: TimeInterval = 60

    private struct CachedResult {
        let value: AttemptResult
        let fetchedAt: Date
    }

    private let api: any ResultAPIClient
    private let catalogOptionsCache: CatalogOptionsCache?
    private let clock: any AppClock
    private let reuseTTLSeconds: TimeInterval
    private var cache: [String: CachedResult] = [:]

    init(
        api: any ResultAPIClient,
        catalogOptionsCache: CatalogOptionsCache? = nil,
        clock: any AppClock = SystemClock(),
        reuseTTLSeconds: TimeInterval = ResultRepository.reviewReuseTTLSeconds
    ) {
        self.api = api
        self.catalogOptionsCache = catalogOptionsCache
        self.clock = clock
        self.reuseTTLSeconds = reuseTTLSeconds
    }

    init(
        apiClient: MobileAPIClient,
        catalogOptionsCache: CatalogOptionsCache? = nil,
        clock: any AppClock = SystemClock(),
        reuseTTLSeconds: TimeInterval = ResultRepository.reviewReuseTTLSeconds
    ) {
        self.init(
            api: OpenAPIResultAPI(apiClient: apiClient),
            catalogOptionsCache: catalogOptionsCache,
            clock: clock,
            reuseTTLSeconds: reuseTTLSeconds
        )
    }

    func result(attemptId: String) async throws -> AttemptResult {
        let dto = try await api.getResult(attemptId: attemptId)
        let mapped = AttemptResult(dto: dto, fallbackAttemptId: attemptId)
        cache[attemptId] = CachedResult(value: mapped, fetchedAt: clock.now)
        return mapped
    }

    func cachedResultIfFresh(attemptId: String) -> AttemptResult? {
        guard let entry = cache[attemptId] else { return nil }
        let age = clock.now.timeIntervalSince(entry.fetchedAt)
        guard age >= 0 && age <= reuseTTLSeconds else {
            return nil
        }
        return entry.value
    }

    func resultForReview(attemptId: String) async throws -> AttemptResult {
        if let fresh = cachedResultIfFresh(attemptId: attemptId) {
            return fresh
        }
        return try await result(attemptId: attemptId)
    }

    func review(attemptId: String) async throws -> ReviewWirePayload {
        try await api.getReview(attemptId: attemptId)
    }

    func examOptions() async -> [String: CatalogOptionItem] {
        await loadCatalogMap(type: .exam)
    }

    func subjectOptions() async -> [String: CatalogOptionItem] {
        await loadCatalogMap(type: .subject)
    }

    func clearCache() {
        cache.removeAll()
    }

    private func loadCatalogMap(type: CatalogOptionType) async -> [String: CatalogOptionItem] {
        guard let catalogOptionsCache else { return [:] }
        do {
            let items = try await catalogOptionsCache.options(for: type)
            var map: [String: CatalogOptionItem] = [:]
            map.reserveCapacity(items.count)
            for item in items where !item.code.isEmpty {
                map[item.code] = item
            }
            return map
        } catch {
            return [:]
        }
    }
}

@MainActor
final class ResultRepositoryRegistry {
    static let shared = ResultRepositoryRegistry()

    private final class WeakSessionBox {
        weak var value: SessionStore?
        init(_ value: SessionStore) { self.value = value }
    }

    private weak var activeSessionStore: SessionStore?
    private weak var activeOptionsCache: CatalogOptionsCache?
    private var activeAccountId: String?
    private var activeUsername: String?
    private var activeRepository: ResultRepository?
    private var hookedSessions: [WeakSessionBox] = []

    func repository(
        sessionStore: SessionStore,
        catalogOptionsCache: CatalogOptionsCache?
    ) -> ResultRepository {
        installSessionHooksIfNeeded(on: sessionStore)
        let currentAccount = Self.trimmed(sessionStore.currentAccountId)
        let currentUsername = Self.trimmed(sessionStore.currentUsername)?.lowercased()

        if let existing = activeRepository,
           activeSessionStore === sessionStore,
           activeOptionsCache === catalogOptionsCache,
           activeAccountId == currentAccount,
           activeUsername == currentUsername
        {
            return existing
        }

        if let existing = activeRepository {
            Task { await existing.clearCache() }
        }

        let created = ResultRepository(
            apiClient: sessionStore.apiClient,
            catalogOptionsCache: catalogOptionsCache
        )
        activeSessionStore = sessionStore
        activeOptionsCache = catalogOptionsCache
        activeAccountId = currentAccount
        activeUsername = currentUsername
        activeRepository = created
        return created
    }

    func reset() {
        if let existing = activeRepository {
            Task { await existing.clearCache() }
        }
        activeRepository = nil
        activeSessionStore = nil
        activeOptionsCache = nil
        activeAccountId = nil
        activeUsername = nil
    }

    private func installSessionHooksIfNeeded(on sessionStore: SessionStore) {
        hookedSessions.removeAll { $0.value == nil }
        guard !hookedSessions.contains(where: { $0.value === sessionStore }) else {
            return
        }
        hookedSessions.append(WeakSessionBox(sessionStore))

        let previousCleared = sessionStore.onSessionCleared
        sessionStore.onSessionCleared = {
            previousCleared?()
            ResultRepositoryRegistry.shared.reset()
        }

        let previousReplaced = sessionStore.onSessionReplaced
        sessionStore.onSessionReplaced = {
            previousReplaced?()
            ResultRepositoryRegistry.shared.reset()
        }
    }

    private static func trimmed(_ value: String?) -> String? {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        return value
    }
}
