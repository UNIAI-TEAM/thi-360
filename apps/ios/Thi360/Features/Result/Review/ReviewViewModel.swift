import Foundation
import Observation

@Observable
@MainActor
final class ReviewViewModel {
    let attemptId: String
    let assetSource: AttemptAssetSource

    private(set) var loadState: LoadState<ReviewModel> = .loading
    private(set) var selectedPartIndex: Int = 0
    private(set) var activeGroupId: String?
    private(set) var isFetching: Bool = false

    @ObservationIgnored private let repository: ResultRepository
    @ObservationIgnored private let connectivityMonitor: ConnectivityMonitor?
    @ObservationIgnored private var connectivityObserverId: UUID?
    @ObservationIgnored private var hasLoadedOnce: Bool = false
    @ObservationIgnored private var isVisible: Bool = false
    @ObservationIgnored private var isNavigating: Bool = false

    init(
        attemptId: String,
        repository: ResultRepository,
        assetSource: AttemptAssetSource,
        connectivityMonitor: ConnectivityMonitor? = nil
    ) {
        self.attemptId = attemptId
        self.repository = repository
        self.assetSource = assetSource
        self.connectivityMonitor = connectivityMonitor
    }

    deinit {
        assetSource.purgeAll()
    }

    var currentModel: ReviewModel? {
        guard case let .loaded(model, _) = loadState else { return nil }
        return model
    }

    var selectedPart: ReviewPartItem? {
        guard let model = currentModel, !model.parts.isEmpty else { return nil }
        let clamped = min(max(selectedPartIndex, 0), model.parts.count - 1)
        return model.parts[clamped]
    }

    var currentPartGroups: [ReviewGroup] {
        guard let model = currentModel else { return [] }
        return model.groups(for: selectedPart)
    }

    var paletteSummary: ReviewPaletteSummary {
        ReviewModel.paletteSummary(for: currentPartGroups)
    }

    var activeGroupIndex: Int {
        let groups = currentPartGroups
        guard !groups.isEmpty else { return -1 }
        if let activeGroupId, let idx = groups.firstIndex(where: { $0.id == activeGroupId }) {
            return idx
        }
        return 0
    }

    var activeGroup: ReviewGroup? {
        let groups = currentPartGroups
        let idx = activeGroupIndex
        guard idx >= 0 && idx < groups.count else { return nil }
        return groups[idx]
    }

    var canGoPrevious: Bool {
        activeGroupIndex > 0
    }

    var canGoNext: Bool {
        let groups = currentPartGroups
        return activeGroupIndex >= 0 && activeGroupIndex < groups.count - 1
    }

    func onAppear() {
        isVisible = true
        isNavigating = false
        installConnectivityObserverIfNeeded()
        guard !hasLoadedOnce else { return }
        Task { [weak self] in
            await self?.loadNow()
        }
    }

    func onDisappear(shouldPurgeAssets: Bool = true) {
        isVisible = false
        removeConnectivityObserver()
        if shouldPurgeAssets {
            assetSource.purgeAll()
        }
    }

    func retry() {
        guard !isFetching else { return }
        Task { [weak self] in
            await self?.loadNow()
        }
    }

    func selectPart(at index: Int) {
        guard let model = currentModel, index >= 0, index < model.parts.count else { return }
        selectedPartIndex = index
        let groups = model.groups(for: model.parts[index])
        activeGroupId = groups.first?.id
    }

    func selectGroup(id: String) {
        guard currentPartGroups.contains(where: { $0.id == id }) else { return }
        activeGroupId = id
    }

    @discardableResult
    func selectPreviousGroup() -> String? {
        let groups = currentPartGroups
        let idx = activeGroupIndex
        guard idx > 0 else { return nil }
        let target = groups[idx - 1].id
        activeGroupId = target
        return target
    }

    @discardableResult
    func selectNextGroup() -> String? {
        let groups = currentPartGroups
        let idx = activeGroupIndex
        guard idx >= 0, idx < groups.count - 1 else { return nil }
        let target = groups[idx + 1].id
        activeGroupId = target
        return target
    }

    func performNavigationOnce(_ action: () -> Void) {
        guard !isNavigating else { return }
        isNavigating = true
        action()
    }

    func loadNow() async {
        isVisible = true
        installConnectivityObserverIfNeeded()

        if let connectivityMonitor, !connectivityMonitor.isOnline {
            loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
            return
        }

        guard !isFetching else { return }
        isFetching = true
        loadState = .loading
        defer { isFetching = false }

        do {
            let wirePayload = try await repository.review(attemptId: attemptId)
            guard !Task.isCancelled else { return }

            let summary = try? await repository.resultForReview(attemptId: attemptId)
            guard !Task.isCancelled else { return }

            let mapped = ReviewMapper.map(
                wirePayload,
                fallbackAttemptId: attemptId,
                resultSummary: summary
            )
            hasLoadedOnce = true
            selectedPartIndex = 0
            let firstGroups = mapped.groups(for: mapped.parts.first)
            activeGroupId = firstGroups.first?.id
            loadState = .loaded(value: mapped, stale: false)
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            let classified = LoadFailureKind.classify(MobileApiError.classify(error))
            loadState = .failed(
                kind: classified.kind,
                canRetry: classified.canRetry,
                retryAfter: classified.retryAfter
            )
        }
    }

    func setModelForTesting(_ model: ReviewModel, selectedPartIndex: Int = 0) {
        hasLoadedOnce = true
        self.selectedPartIndex = selectedPartIndex
        let selectedPart = (selectedPartIndex >= 0 && selectedPartIndex < model.parts.count)
            ? model.parts[selectedPartIndex]
            : model.parts.first
        self.activeGroupId = model.groups(for: selectedPart).first?.id
        self.loadState = .loaded(value: model, stale: false)
    }

    func setFailedStateForTesting(_ kind: LoadFailureKind) {
        hasLoadedOnce = true
        self.loadState = .failed(kind: kind, canRetry: kind == .offline || kind == .server, retryAfter: nil)
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
