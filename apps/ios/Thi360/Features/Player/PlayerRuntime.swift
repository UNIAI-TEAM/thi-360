import Foundation
import SwiftUI

@MainActor
final class PlayerRuntime {
    static let shared = PlayerRuntime()

    let engineRegistry = AttemptEngineRegistry()
    let bodyStore = PlayerQuestionBodyStore()

    private var assetSources: [String: AttemptAssetSource] = [:]
    private var registeredCleanupIds: Set<ObjectIdentifier> = []
    private var boundRouterIds: Set<ObjectIdentifier> = []

    func bindLifecycleIfNeeded(router: AppRouter, sessionStore: SessionStore?) {
        let routerId = ObjectIdentifier(router)
        if !boundRouterIds.contains(routerId) {
            boundRouterIds.insert(routerId)
            engineRegistry.bindForegroundHook(to: router.inboundLinkRouter)
        }
        if let sessionStore {
            let cleanupId = ObjectIdentifier(sessionStore.sessionCleanup)
            if !registeredCleanupIds.contains(cleanupId) {
                registeredCleanupIds.insert(cleanupId)
                let participant = engineRegistry.makeCleanupParticipant(storeProvider: sessionStore.storeProvider)
                Task {
                    await sessionStore.sessionCleanup.register(participant)
                }
            }
        }
    }

    private let fallbackStoreProvider = AccountScopedStoreProvider()

    func engine(
        for attemptId: String,
        sessionStore: SessionStore?,
        connectivity: ConnectivityMonitor?,
        localeProvider: @escaping @Sendable () -> AppLocale
    ) -> AttemptEngine {
        let accountId = sessionStore?.currentAccountId ?? "anonymous"
        let resolvedConnectivity = connectivity ?? ConnectivityMonitor()
        return engineRegistry.engine(for: attemptId, accountId: accountId) { [bodyStore, fallbackStoreProvider] in
            let api: any AttemptAPIClient
            let store: AccountScopedStore
            if let sessionStore {
                api = PlayerAttemptAPIFactory.make(apiClient: sessionStore.apiClient, bodyStore: bodyStore)
                store = sessionStore.currentAccountStore() ?? fallbackStoreProvider.store(for: accountId)!
            } else {
                api = PlayerOfflineFallbackAPI()
                store = fallbackStoreProvider.store(for: accountId)!
            }
            return AttemptEngine(
                attemptId: attemptId,
                accountId: accountId,
                api: api,
                store: store,
                connectivity: resolvedConnectivity
            )
        }
    }

    func assetSource(
        for attemptId: String,
        sessionStore: SessionStore?,
        localeProvider: @escaping @Sendable () async -> AppLocale
    ) -> AttemptAssetSource? {
        if let existing = assetSources[attemptId] {
            return existing
        }
        guard let sessionStore else { return nil }
        let created = AttemptAssetSource(
            attemptId: attemptId,
            mobileAPIClient: sessionStore.apiClient,
            tokenStore: sessionStore.tokenStore,
            localeProvider: localeProvider
        )
        assetSources[attemptId] = created
        return created
    }

    func release(attemptId: String) {
        engineRegistry.release(attemptId)
        bodyStore.remove(for: attemptId)
        assetSources.removeValue(forKey: attemptId)?.purgeAll()
    }

    func releaseAll() {
        engineRegistry.releaseAll()
        bodyStore.removeAll()
        for source in assetSources.values {
            source.purgeAll()
        }
        assetSources.removeAll()
    }
}

private struct PlayerOfflineFallbackAPI: AttemptAPIClient {
    func getState(attemptId _: String) async throws -> AttemptStateSnapshot {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }

    func claimSession(attemptId _: String, sessionId _: String, takeover _: Bool) async throws -> Components.Schemas.ClaimSessionResponse {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }

    func heartbeat(attemptId _: String, sessionId _: String) async throws -> Components.Schemas.HeartbeatResponse {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }

    func saveAnswers(
        attemptId _: String,
        sessionId _: String,
        changes _: [AttemptAnswerChangeInput],
        flags _: [AttemptFlagChangeInput],
        position _: Position?
    ) async throws -> Components.Schemas.SaveAnswersResponse {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }

    func finishPart(
        attemptId _: String,
        partId _: PartID,
        sessionId _: String,
        finalAnswers _: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.FinishPartResponse {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }

    func submit(
        attemptId _: String,
        sessionId _: String,
        idempotencyKey _: String,
        finalAnswers _: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.SubmitAttemptResponse {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }

    func confirmPartChoice(
        attemptId _: String,
        partId _: PartID,
        sessionId _: String,
        idempotencyKey _: String,
        optionCode _: String,
        subjectCodes _: [String]?
    ) async throws -> Components.Schemas.PartChoiceResponse {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }

    func retryPartGeneration(
        attemptId _: String,
        partId _: PartID,
        sessionId _: String,
        idempotencyKey _: String,
        reason _: String?
    ) async throws -> Components.Schemas.PartChoiceResponse {
        throw AttemptAPIError.network(.notConnectedToInternet)
    }
}
