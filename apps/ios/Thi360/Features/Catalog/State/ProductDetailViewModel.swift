import Foundation
import Observation

@Observable
@MainActor
final class ProductDetailViewModel {
    let code: String

    private(set) var loadState: LoadState<ProductOutcome> = .loading
    private(set) var staleFailureKind: LoadFailureKind?
    private(set) var retryAvailableAt: Date?
    private(set) var accessCell: AccessCell = .loading
    private(set) var isRefreshing: Bool = false

    @ObservationIgnored let clock: any AppClock
    @ObservationIgnored private let api: any CatalogAPIClientProtocol
    @ObservationIgnored private let connectivityMonitor: ConnectivityMonitor?
    @ObservationIgnored private let connectivityObserverId = UUID()
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var hasLoadedOnce: Bool = false

    init(
        code: String,
        api: any CatalogAPIClientProtocol,
        connectivityMonitor: ConnectivityMonitor? = nil,
        clock: any AppClock = SystemClock()
    ) {
        self.code = code
        self.api = api
        self.connectivityMonitor = connectivityMonitor
        self.clock = clock

        if let connectivityMonitor {
            connectivityMonitor.addObserver(id: connectivityObserverId) { [weak self] isOnline in
                guard let self, isOnline else { return }
                let isOfflineFailed: Bool = {
                    if case .failed(kind: .offline, _, _) = self.loadState { return true }
                    return false
                }()
                let isOfflineStale: Bool = {
                    if case .loaded(_, true) = self.loadState, self.staleFailureKind == .offline { return true }
                    return false
                }()
                if isOfflineFailed || isOfflineStale {
                    Task { @MainActor [weak self] in
                        await self?.retry()
                    }
                }
            }
        }
    }

    deinit {
        if let connectivityMonitor {
            let observerId = connectivityObserverId
            Task { @MainActor in
                connectivityMonitor.removeObserver(observerId)
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

    func cancelAllTasks() {
        generation &+= 1
        connectivityMonitor?.removeObserver(connectivityObserverId)
    }

    var productName: String? {
        guard case let .loaded(outcome, _) = loadState else { return nil }
        switch outcome {
        case let .exam(detail), let .bundle(detail, _):
            return detail.name
        case .gone, .notFound, .bundleUnavailable:
            return nil
        }
    }

    var loadedOutcome: ProductOutcome? {
        if case let .loaded(outcome, _) = loadState {
            return outcome
        }
        return nil
    }

    var bundleAccessRow: ExamAccessRow? {
        if case let .ready(row) = accessCell {
            return row
        }
        return nil
    }

    func bundleItemAccess(for item: BundleDetailItemModel) -> BundleAccessItemRow? {
        guard let rows = bundleAccessRow?.items else { return nil }
        if !item.examId.isEmpty, let byId = rows.first(where: { $0.examId == item.examId }) {
            return byId
        }
        return rows.first(where: { $0.code == item.code })
    }

    func loadIfNeeded() async {
        guard !hasLoadedOnce else { return }
        hasLoadedOnce = true
        await load(showLoadingPlaceholder: true)
    }

    func retry() async {
        guard canRetryNow else { return }
        hasLoadedOnce = true
        await load(showLoadingPlaceholder: preservableLoadedOutcome == nil)
    }

    func pullToRefresh() async {
        guard canRetryNow else { return }
        hasLoadedOnce = true
        isRefreshing = true
        defer { isRefreshing = false }
        await load(showLoadingPlaceholder: false)
    }

    func reloadAccess() async {
        guard let targetCode = accessTargetCode else { return }
        await fetchAccess(for: targetCode, generation: generation, markLoading: true)
    }

    func refreshAccessOnReturn() async {
        guard let targetCode = accessTargetCode else { return }
        await fetchAccess(for: targetCode, generation: generation, markLoading: false)
    }

    private var preservableLoadedOutcome: ProductOutcome? {
        guard case let .loaded(outcome, _) = loadState else { return nil }
        switch outcome {
        case .exam, .bundle:
            return outcome
        case .gone, .notFound, .bundleUnavailable:
            return nil
        }
    }

    private var accessTargetCode: String? {
        guard case let .loaded(outcome, _) = loadState else { return nil }
        switch outcome {
        case let .exam(detail):
            return detail.code
        case let .bundle(detail, sellable):
            return sellable ? detail.code : nil
        case .gone, .notFound, .bundleUnavailable:
            return nil
        }
    }

    private func load(showLoadingPlaceholder: Bool) async {
        generation &+= 1
        let callGeneration = generation
        let previousOutcome = preservableLoadedOutcome

        if connectivityMonitor?.isOnline == false {
            if let previousOutcome {
                staleFailureKind = .offline
                loadState = .loaded(value: previousOutcome, stale: true)
            } else {
                staleFailureKind = nil
                loadState = .failed(kind: .offline, canRetry: true, retryAfter: nil)
            }
            return
        }

        if showLoadingPlaceholder {
            staleFailureKind = nil
            loadState = .loading
            accessCell = .loading
        }

        do {
            let outcome = try await api.fetchProductDetail(code: code)
            guard callGeneration == generation else { return }
            staleFailureKind = nil
            retryAvailableAt = nil
            loadState = .loaded(value: outcome, stale: false)

            switch outcome {
            case let .exam(detail):
                await fetchAccess(for: detail.code, generation: callGeneration, markLoading: showLoadingPlaceholder)
            case let .bundle(detail, sellable):
                if sellable {
                    await fetchAccess(for: detail.code, generation: callGeneration, markLoading: showLoadingPlaceholder)
                }
            case .gone, .notFound, .bundleUnavailable:
                break
            }
        } catch {
            guard callGeneration == generation else { return }
            let classified = LoadFailureKind.classify(MobileApiError.classify(error))
            let failureKind: LoadFailureKind = classified.kind == .offline ? .offline : .server
            if let retryAfter = classified.retryAfter, retryAfter > 0 {
                retryAvailableAt = clock.now.addingTimeInterval(TimeInterval(retryAfter))
            } else {
                retryAvailableAt = nil
            }
            if let previousOutcome, !showLoadingPlaceholder || failureKind == .offline {
                staleFailureKind = failureKind
                loadState = .loaded(value: previousOutcome, stale: true)
            } else {
                staleFailureKind = nil
                loadState = .failed(kind: failureKind, canRetry: true, retryAfter: classified.retryAfter)
            }
        }
    }

    private func fetchAccess(for productCode: String, generation callGeneration: UInt64, markLoading: Bool) async {
        if markLoading {
            accessCell = .loading
        }
        do {
            let rows = try await api.queryExamAccess(codes: [productCode])
            guard callGeneration == generation else { return }
            let matched = rows.first { $0.code == productCode } ?? .none(code: productCode)
            accessCell = .ready(matched)
        } catch {
            guard callGeneration == generation else { return }
            accessCell = .failed
        }
    }
}
