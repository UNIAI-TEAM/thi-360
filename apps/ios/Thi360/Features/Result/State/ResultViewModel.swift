import Foundation
import Observation

@Observable
@MainActor
final class ResultViewModel {
    let attemptId: String

    private(set) var loadState: LoadState<AttemptResult> = .loading
    private(set) var examOptions: [String: CatalogOptionItem] = [:]
    private(set) var subjectOptions: [String: CatalogOptionItem] = [:]
    private(set) var isGradingTimedOut: Bool = false
    private(set) var isReloadingFromTimeout: Bool = false
    private(set) var isFetching: Bool = false

    @ObservationIgnored private let repository: ResultRepository
    @ObservationIgnored private let poller: GradingPoller
    @ObservationIgnored private let connectivityMonitor: ConnectivityMonitor?
    @ObservationIgnored private var connectivityObserverId: UUID?
    @ObservationIgnored private var isSceneActive: Bool = true
    @ObservationIgnored private var isVisible: Bool = false
    @ObservationIgnored private var isNavigating: Bool = false
    @ObservationIgnored private var activeFetchTask: Task<Void, Never>?
    @ObservationIgnored private var catalogTask: Task<Void, Never>?

    init(
        attemptId: String,
        repository: ResultRepository,
        poller: GradingPoller = GradingPoller(),
        connectivityMonitor: ConnectivityMonitor? = nil
    ) {
        self.attemptId = attemptId
        self.repository = repository
        self.poller = poller
        self.connectivityMonitor = connectivityMonitor
    }

    var currentResult: AttemptResult? {
        guard case let .loaded(value, _) = loadState else { return nil }
        return value
    }

    func onAppear(isSceneActive: Bool = true) {
        self.isVisible = true
        self.isSceneActive = isSceneActive
        self.isNavigating = false
        installConnectivityObserverIfNeeded()
        loadCatalogOptionsIfNeeded()
        activeFetchTask?.cancel()
        activeFetchTask = Task { [weak self] in
            await self?.fetchResult(trigger: .appear)
        }
    }

    func onDisappear() {
        isVisible = false
        activeFetchTask?.cancel()
        activeFetchTask = nil
        catalogTask?.cancel()
        catalogTask = nil
        removeConnectivityObserver()
        Task { [poller] in
            await poller.stop()
        }
    }

    func handleSceneActiveChanged(_ active: Bool) {
        isSceneActive = active
        Task { [poller] in
            await poller.setSceneActive(active)
        }
    }

    func retry() {
        guard !isFetching else { return }
        Task { [weak self] in
            await self?.fetchResult(trigger: .retry)
        }
    }

    func reloadFromGradingTimeout() {
        guard !isReloadingFromTimeout, !isFetching else { return }
        isReloadingFromTimeout = true
        Task { [weak self] in
            await self?.performTimeoutReload()
        }
    }

    func performNavigationOnce(_ action: () -> Void) {
        guard !isNavigating else { return }
        isNavigating = true
        action()
    }

    func resetNavigationGuard() {
        isNavigating = false
    }

    func setStateForTesting(
        loadState: LoadState<AttemptResult>,
        examOptions: [String: CatalogOptionItem] = [:],
        subjectOptions: [String: CatalogOptionItem] = [:],
        isGradingTimedOut: Bool = false
    ) {
        self.loadState = loadState
        self.examOptions = examOptions
        self.subjectOptions = subjectOptions
        self.isGradingTimedOut = isGradingTimedOut
    }

    func loadResultNow(isSceneActive: Bool = true) async {
        self.isVisible = true
        self.isSceneActive = isSceneActive
        self.isNavigating = false
        installConnectivityObserverIfNeeded()
        await loadCatalogOptions()
        await fetchResult(trigger: .appear)
    }

    func reloadFromGradingTimeoutNow() async {
        guard !isReloadingFromTimeout, !isFetching else { return }
        isReloadingFromTimeout = true
        await performTimeoutReload()
    }

    private enum FetchTrigger {
        case appear
        case retry
        case poll
        case timeoutReload
    }

    private func loadCatalogOptionsIfNeeded() {
        catalogTask?.cancel()
        catalogTask = Task { [weak self] in
            await self?.loadCatalogOptions()
        }
    }

    private func loadCatalogOptions() async {
        async let exams = repository.examOptions()
        async let subjects = repository.subjectOptions()
        let (loadedExams, loadedSubjects) = await (exams, subjects)
        guard !Task.isCancelled else { return }
        if !loadedExams.isEmpty {
            examOptions = loadedExams
        }
        if !loadedSubjects.isEmpty {
            subjectOptions = loadedSubjects
        }
    }

    private func fetchResult(trigger: FetchTrigger) async {
        if trigger == .retry, isFetching {
            return
        }

        if currentResult == nil,
           let connectivityMonitor,
           !connectivityMonitor.isOnline
        {
            loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
            return
        }

        if currentResult == nil && trigger != .poll {
            loadState = .loading
        }

        isFetching = true
        defer { isFetching = false }

        do {
            let fetched = try await repository.result(attemptId: attemptId)
            guard !Task.isCancelled else { return }
            applyLoadedResult(fetched)
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            applyFetchError(error)
        }
    }

    private func performTimeoutReload() async {
        defer { isReloadingFromTimeout = false }
        isFetching = true
        defer { isFetching = false }

        do {
            let fetched = try await repository.result(attemptId: attemptId)
            guard !Task.isCancelled else { return }
            if fetched.state == .grading {
                isGradingTimedOut = false
                loadState = .loaded(value: fetched, stale: false)
                await poller.restartWindowAfterReload(
                    isSceneActive: isSceneActive,
                    onPoll: { [weak self] in
                        await self?.executeScheduledPoll() ?? false
                    },
                    onTimeout: { [weak self] in
                        await self?.handlePollerTimeout()
                    }
                )
            } else {
                isGradingTimedOut = false
                loadState = .loaded(value: fetched, stale: false)
                await poller.stop()
            }
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            applyFetchError(error)
        }
    }

    private func applyLoadedResult(_ result: AttemptResult) {
        loadState = .loaded(value: result, stale: false)
        if result.state == .grading {
            guard !isGradingTimedOut else { return }
            Task { [weak self] in
                await self?.startPollingIfNeeded()
            }
        } else {
            isGradingTimedOut = false
            Task { [poller] in
                await poller.stop()
            }
        }
    }

    private func applyFetchError(_ error: Error) {
        let classified = LoadFailureKind.classify(MobileApiError.classify(error))
        if let existing = currentResult {
            switch classified.kind {
            case .notFound, .conflict(code: "ATTEMPT_NOT_FINISHED"):
                isGradingTimedOut = false
                loadState = .failed(
                    kind: classified.kind,
                    canRetry: classified.canRetry,
                    retryAfter: classified.retryAfter
                )
                Task { [poller] in
                    await poller.stop()
                }
            default:
                loadState = .loaded(value: existing, stale: true)
            }
            return
        }

        loadState = .failed(
            kind: classified.kind,
            canRetry: classified.canRetry,
            retryAfter: classified.retryAfter
        )
    }

    private func startPollingIfNeeded() async {
        guard isVisible else { return }
        await poller.startIfNeeded(
            isSceneActive: isSceneActive,
            onPoll: { [weak self] in
                await self?.executeScheduledPoll() ?? false
            },
            onTimeout: { [weak self] in
                await self?.handlePollerTimeout()
            }
        )
    }

    private func executeScheduledPoll() async -> Bool {
        guard isVisible else { return false }
        await fetchResult(trigger: .poll)
        guard let latest = currentResult else { return false }
        return latest.state == .grading
    }

    private func handlePollerTimeout() {
        guard isVisible else { return }
        if currentResult?.state == .grading {
            isGradingTimedOut = true
        }
    }

    private func installConnectivityObserverIfNeeded() {
        guard let connectivityMonitor, connectivityObserverId == nil else { return }
        connectivityObserverId = connectivityMonitor.addObserver { [weak self] online in
            guard let self, online, self.isVisible else { return }
            if case .failed(kind: .offline, _, _) = self.loadState {
                self.retry()
            }
        }
    }

    private func removeConnectivityObserver() {
        if let token = connectivityObserverId {
            connectivityMonitor?.removeObserver(token)
            connectivityObserverId = nil
        }
    }
}
