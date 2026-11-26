import Foundation
import Observation

@Observable
@MainActor
final class LoadController<T: Sendable> {
    private(set) var state: LoadState<T> = .loading
    private(set) var isRefreshing: Bool = false
    private(set) var retryAvailableAt: Date?
    private(set) var lastFailureKind: LoadFailureKind?

    private var cachedValue: T?
    private var cachedAccountId: String?
    private var isLoading: Bool = false
    private var connectivityToken: UUID?

    private let fetch: @Sendable () async throws -> T
    private let isEmptyValue: (T) -> Bool
    private let accountIdProvider: @MainActor () -> String?
    private let connectivityMonitor: ConnectivityMonitor?
    private let clock: any AppClock

    init(
        accountIdProvider: @escaping @MainActor () -> String? = { nil },
        connectivityMonitor: ConnectivityMonitor? = nil,
        clock: any AppClock = SystemClock(),
        isEmpty: @escaping (T) -> Bool = { _ in false },
        fetch: @escaping @Sendable () async throws -> T
    ) {
        self.accountIdProvider = accountIdProvider
        self.connectivityMonitor = connectivityMonitor
        self.clock = clock
        self.isEmptyValue = isEmpty
        self.fetch = fetch

        if let connectivityMonitor {
            self.connectivityToken = connectivityMonitor.addObserver { [weak self] isOnline in
                guard let self, isOnline else { return }
                if case .failed(kind: .offline, _, _) = self.state {
                    Task { @MainActor in
                        await self.load()
                    }
                }
            }
        }
    }

    var retryRemainingSeconds: Int {
        guard let retryAvailableAt else { return 0 }
        let remaining = retryAvailableAt.timeIntervalSince(clock.now)
        return remaining > 0 ? Int(ceil(remaining)) : 0
    }

    var canRetryNow: Bool {
        retryRemainingSeconds == 0
    }

    func load() async {
        syncAccountScopeIfChanged()
        guard !isLoading, canRetryNow else { return }

        if cachedValue == nil {
            state = .loading
        }
        await performFetch(isPullToRefresh: false)
    }

    func refresh() async {
        syncAccountScopeIfChanged()
        guard !isLoading, canRetryNow else { return }

        isRefreshing = true
        defer { isRefreshing = false }
        await performFetch(isPullToRefresh: true)
    }

    func retry() async {
        guard canRetryNow else { return }
        await load()
    }

    func onAccountChanged(_ newAccountId: String?) {
        guard newAccountId != cachedAccountId else { return }
        clearCachedData()
        cachedAccountId = newAccountId
    }

    func clearCachedData() {
        cachedValue = nil
        cachedAccountId = nil
        retryAvailableAt = nil
        lastFailureKind = nil
        state = .loading
    }

    private func syncAccountScopeIfChanged() {
        let activeAccountId = accountIdProvider()
        if activeAccountId != cachedAccountId {
            cachedValue = nil
            cachedAccountId = activeAccountId
            retryAvailableAt = nil
            lastFailureKind = nil
        }
    }

    private func performFetch(isPullToRefresh: Bool) async {
        isLoading = true
        defer { isLoading = false }

        let requestAccountId = accountIdProvider()
        do {
            let value = try await fetch()
            // Drop stale responses if the signed-in account changed mid-request.
            guard accountIdProvider() == requestAccountId else { return }

            cachedAccountId = requestAccountId
            retryAvailableAt = nil
            lastFailureKind = nil

            if isEmptyValue(value) {
                cachedValue = nil
                state = .empty
            } else {
                cachedValue = value
                state = .loaded(value: value, stale: false)
            }
        } catch {
            guard accountIdProvider() == requestAccountId else { return }

            let apiError = MobileApiError.classify(error)
            let classified = LoadFailureKind.classify(apiError)
            lastFailureKind = classified.kind

            if let retryAfter = classified.retryAfter, retryAfter > 0 {
                retryAvailableAt = clock.now.addingTimeInterval(TimeInterval(retryAfter))
            }

            if let cachedValue, (isPullToRefresh || classified.kind == .offline || classified.kind == .server) {
                state = .loaded(value: cachedValue, stale: true)
            } else {
                state = .failed(
                    kind: classified.kind,
                    canRetry: classified.canRetry,
                    retryAfter: classified.retryAfter
                )
            }
        }
    }
}
