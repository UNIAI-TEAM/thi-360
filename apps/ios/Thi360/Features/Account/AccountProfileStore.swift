import Foundation
import Observation

@Observable
@MainActor
final class AccountProfileStore {
    typealias FetchProfile = @Sendable () async throws -> AccountProfile

    private(set) var state: LoadState<AccountProfile> = .loading
    private(set) var refreshError: LoadFailureKind?

    private let fetchProfile: FetchProfile
    private let isAuthenticatedProvider: @MainActor () -> Bool
    private var epoch: UInt64 = 0
    private var inFlightRefreshTask: Task<AccountProfile?, Never>?

    init(
        fetchProfile: @escaping FetchProfile,
        isAuthenticatedProvider: @escaping @MainActor () -> Bool = { true }
    ) {
        self.fetchProfile = fetchProfile
        self.isAuthenticatedProvider = isAuthenticatedProvider
    }

    convenience init(
        profileService: any ProfileServiceProtocol,
        sessionStore: SessionStore
    ) {
        self.init(
            fetchProfile: { try await profileService.fetchProfile() },
            isAuthenticatedProvider: { sessionStore.state.isAuthenticated }
        )
    }

    var loadedProfile: AccountProfile? {
        if case let .loaded(profile, _) = state {
            return profile
        }
        return nil
    }

    var hasLoadedProfile: Bool {
        loadedProfile != nil
    }

    @discardableResult
    func refresh() async -> AccountProfile? {
        if let existingTask = inFlightRefreshTask {
            return await existingTask.value
        }

        let startEpoch = epoch
        let existingProfile = loadedProfile
        if existingProfile == nil {
            state = .loading
            refreshError = nil
        }

        let task = Task<AccountProfile?, Never> { @MainActor in
            do {
                let fresh = try await self.fetchProfile()
                guard self.epoch == startEpoch, self.isAuthenticatedProvider() else {
                    return self.loadedProfile
                }
                self.refreshError = nil
                self.state = .loaded(value: fresh, stale: false)
                return fresh
            } catch {
                guard self.epoch == startEpoch, self.isAuthenticatedProvider() else {
                    return self.loadedProfile
                }
                let apiError = MobileApiError.classify(error)
                let classified = LoadFailureKind.classify(apiError)
                if let existingProfile {
                    self.refreshError = classified.kind
                    self.state = .loaded(value: existingProfile, stale: true)
                } else {
                    self.refreshError = nil
                    self.state = .failed(
                        kind: classified.kind,
                        canRetry: true,
                        retryAfter: classified.retryAfter
                    )
                }
                return nil
            }
        }

        inFlightRefreshTask = task
        let result = await task.value
        if epoch == startEpoch {
            inFlightRefreshTask = nil
        }
        return result
    }

    func replace(_ profile: AccountProfile) {
        epoch &+= 1
        inFlightRefreshTask = nil
        refreshError = nil
        state = .loaded(value: profile, stale: false)
    }

    func wipe() {
        epoch &+= 1
        inFlightRefreshTask?.cancel()
        inFlightRefreshTask = nil
        refreshError = nil
        state = .loading
    }

    func makeCleanupParticipant() -> any SessionCleanupParticipant {
        AccountProfileStoreCleanupParticipant(store: self)
    }
}

private final class AccountProfileStoreCleanupParticipant: SessionCleanupParticipant, @unchecked Sendable {
    let id = "account.profileStore"
    private weak var store: AccountProfileStore?

    init(store: AccountProfileStore) {
        self.store = store
    }

    func onLogout(accountId: String) async throws {
        await MainActor.run {
            store?.wipe()
        }
    }
}
