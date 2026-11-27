import Foundation
import Observation

@MainActor
@Observable
final class PrepareViewModel {
    let entitlementId: String
    private(set) var state: PrepareScreenState

    @ObservationIgnored private let api: any PrepareAPIClientProtocol
    @ObservationIgnored private let startContextStore: StartContextStore
    @ObservationIgnored private let startCommand: StartCommand
    @ObservationIgnored private let waitingRetry: WaitingRetry
    @ObservationIgnored private let clock: any PrepareClock
    @ObservationIgnored private var localeProvider: () -> AppLocale
    @ObservationIgnored private var onOpenAttemptHandler: ((String) -> Void)?
    @ObservationIgnored private var pendingOpenAttemptId: String?

    @ObservationIgnored private var loadTask: Task<Void, Never>?
    @ObservationIgnored private var startTask: Task<Void, Never>?
    @ObservationIgnored private var retryTask: Task<Void, Never>?
    @ObservationIgnored private var activePoller: WaitingPoller?
    @ObservationIgnored private var hasTriggeredInitialLoad = false
    @ObservationIgnored private var isSceneActive = true
    @ObservationIgnored private var hasRegisteredHost = false
    @ObservationIgnored private var visibleHosts: Set<UUID> = []
    @ObservationIgnored private var manualForegroundOverride: Bool? = nil

    private var isForeground: Bool {
        if let manualForegroundOverride {
            return manualForegroundOverride
        }
        let hostVisible = !hasRegisteredHost || !visibleHosts.isEmpty
        return isSceneActive && hostVisible
    }

    init(
        entitlementId: String,
        api: any PrepareAPIClientProtocol,
        startContextStore: StartContextStore = StartContextStore(storeResolver: { nil }),
        startCommand: StartCommand? = nil,
        clock: any PrepareClock = SystemPrepareClock(),
        localeProvider: @escaping () -> AppLocale = { .vi },
        onOpenAttempt: ((String) -> Void)? = nil,
        initialState: PrepareScreenState = PrepareScreenState()
    ) {
        self.entitlementId = entitlementId
        self.api = api
        self.startContextStore = startContextStore
        let resolvedStartCommand = startCommand ?? StartCommand(api: api)
        self.startCommand = resolvedStartCommand
        self.waitingRetry = WaitingRetry(
            startCommand: resolvedStartCommand,
            startContextStore: startContextStore
        )
        self.clock = clock
        self.localeProvider = localeProvider
        self.onOpenAttemptHandler = onOpenAttempt
        self.state = initialState
    }

    var loadedResponse: AttemptPrepareResponse? {
        guard case let .loaded(value, _) = state.load else { return nil }
        return value
    }

    func updateBindings(
        localeProvider: @escaping () -> AppLocale,
        onOpenAttempt: @escaping (String) -> Void
    ) {
        self.localeProvider = localeProvider
        self.onOpenAttemptHandler = onOpenAttempt
        if let pending = pendingOpenAttemptId {
            pendingOpenAttemptId = nil
            onOpenAttempt(pending)
        }
    }

    func loadIfNeeded() async {
        guard !hasTriggeredInitialLoad else { return }
        hasTriggeredInitialLoad = true
        await performLoad(maxAutoRetries: 1)
    }

    func retryLoad() async {
        hasTriggeredInitialLoad = true
        await performLoad(maxAutoRetries: 0)
    }

    func triggerInitialLoadIfNeeded() {
        guard !hasTriggeredInitialLoad else { return }
        hasTriggeredInitialLoad = true
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            await self?.performLoad(maxAutoRetries: 1)
        }
    }

    func triggerRetryLoad() {
        hasTriggeredInitialLoad = true
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            await self?.performLoad(maxAutoRetries: 0)
        }
    }

    func selectOption(_ optionCode: String) {
        guard let response = loadedResponse else { return }
        guard state.selection.optionCode != optionCode else { return }
        let nextSelection = state.selection.selectingOption(optionCode)
        state.selection = nextSelection
        state.gate = StartGate.resolve(response: response, selection: nextSelection)
        if case .refused = state.start {
            state.start = .idle
        }
        Task { [startCommand] in
            await startCommand.resetKey()
        }
    }

    func toggleSubject(_ subjectCode: String) {
        guard let response = loadedResponse,
              let selectedOption = state.selection.selectedOption(in: response.preChoice)
        else {
            return
        }
        let required = PreChoiceSelection.requiredSubjectCount(for: selectedOption)
        guard required > 0 else { return }
        let nextSelection = state.selection.togglingSubject(subjectCode, maxSelect: required)
        guard nextSelection != state.selection else { return }
        state.selection = nextSelection
        state.gate = StartGate.resolve(response: response, selection: nextSelection)
        if case .refused = state.start {
            state.start = .idle
        }
        Task { [startCommand] in
            await startCommand.resetKey()
        }
    }

    func startAttempt() {
        guard startTask == nil else { return }
        guard let response = loadedResponse, state.gate == .open, state.start != .sending else {
            return
        }
        startTask = Task { [weak self] in
            await self?.executeStart(response: response)
            self?.startTask = nil
        }
    }

    func startAttemptAndWait() async {
        guard let response = loadedResponse, state.gate == .open, state.start != .sending else {
            return
        }
        await executeStart(response: response)
    }

    func retryFromWaiting() {
        guard retryTask == nil else { return }
        guard state.waitingAttemptId != nil, state.waiting != .retrying else { return }
        retryTask = Task { [weak self] in
            await self?.executeRetryFromWaiting()
            self?.retryTask = nil
        }
    }

    func retryFromWaitingAndWait() async {
        await executeRetryFromWaiting()
    }

    func setForeground(_ foreground: Bool) {
        manualForegroundOverride = foreground
        activePoller?.setForeground(foreground)
    }

    func setSceneActive(_ active: Bool) {
        manualForegroundOverride = nil
        isSceneActive = active
        activePoller?.setForeground(isForeground)
    }

    func registerVisibleHost(_ id: UUID) {
        manualForegroundOverride = nil
        hasRegisteredHost = true
        visibleHosts.insert(id)
        activePoller?.setForeground(isForeground)
    }

    func unregisterVisibleHost(_ id: UUID) {
        visibleHosts.remove(id)
        activePoller?.setForeground(isForeground)
    }

    func stopWaiting() {
        activePoller?.stop()
        activePoller = nil
    }

    func clear() {
        loadTask?.cancel()
        loadTask = nil
        startTask?.cancel()
        startTask = nil
        retryTask?.cancel()
        retryTask = nil
        pendingOpenAttemptId = nil
        stopWaiting()
    }

    private func performLoad(maxAutoRetries: Int) async {
        state.load = .loading
        var attempts = 0

        while !Task.isCancelled {
            do {
                let response = try await api.prepare(entitlementId: entitlementId)
                guard !Task.isCancelled else { return }
                await applyLoadedResponse(response)
                return
            } catch {
                guard !Task.isCancelled else { return }
                let classified = MobileApiError.classify(error)
                let failure = LoadFailureKind.classify(classified)

                if failure.kind == .notFound {
                    state.load = .failed(kind: .notFound, canRetry: false, retryAfter: nil)
                    return
                }

                if attempts < maxAutoRetries {
                    attempts += 1
                    continue
                }

                state.load = .failed(kind: failure.kind, canRetry: true, retryAfter: failure.retryAfter)
                return
            }
        }
    }

    private func applyLoadedResponse(_ response: AttemptPrepareResponse) async {
        let selection: PreChoiceSelection
        if state.selection.optionCode != nil || !state.selection.subjectCodes.isEmpty {
            selection = state.selection
        } else {
            selection = PreChoiceSelection.initial(from: response.preChoice)
        }
        let gate = StartGate.resolve(response: response, selection: selection)

        let openStartingId: String?
        if state.waiting == nil,
           let open = response.openAttempt,
           open.state == .STARTING,
           let id = open.attemptId?.trimmingCharacters(in: .whitespacesAndNewlines),
           !id.isEmpty
        {
            openStartingId = id
        } else {
            openStartingId = nil
        }

        state.load = .loaded(value: response, stale: false)
        state.selection = selection
        state.gate = gate

        if let openStartingId {
            let savedContext = await startContextStore.read(attemptId: openStartingId)
            let resolvedContext = savedContext ?? buildStartContext(
                response: response,
                selection: selection,
                preChoiceInput: nil
            )
            startWaitingPoller(
                attemptId: openStartingId,
                context: resolvedContext,
                inMemoryPreChoice: nil,
                initialElapsedSeconds: 0
            )
        }
    }

    private func executeStart(response: AttemptPrepareResponse) async {
        guard state.start != .sending else { return }
        if await startCommand.isInFlight { return }

        let preChoiceInput = state.selection.toPreChoiceInput(for: response)
        let startContext = buildStartContext(
            response: response,
            selection: state.selection,
            preChoiceInput: preChoiceInput
        )

        state.start = .sending

        guard let outcome = await startCommand.start(
            entitlementId: response.entitlementId,
            preChoice: preChoiceInput
        ) else {
            return
        }

        switch outcome {
        case let .opened(attemptId, openedState):
            _ = try? await startContextStore.save(startContext, attemptId: attemptId)
            state.start = .idle
            handleOpenedAttempt(
                attemptId: attemptId,
                openedState: openedState,
                context: startContext,
                inMemoryPreChoice: preChoiceInput
            )

        case let .refused(refusal):
            state.start = .refused(refusal)
        }
    }

    private func executeRetryFromWaiting() async {
        guard let attemptId = state.waitingAttemptId else { return }
        guard state.waiting != .retrying else { return }
        if await startCommand.isInFlight { return }

        let response = loadedResponse
        state.waiting = .retrying

        let outcome = await waitingRetry.retry(
            attemptId: attemptId,
            origin: .prepare,
            entitlementId: response?.entitlementId ?? entitlementId,
            prepareResponse: response,
            inMemoryPreChoice: state.waitingInMemoryPreChoice,
            inMemoryContext: state.waitingContext,
            fallbackContextBuilder: { [weak self] payload in
                guard let self, let response else { return nil }
                return self.buildStartContext(
                    response: response,
                    selection: self.state.selection,
                    preChoiceInput: payload
                )
            }
        )

        switch outcome {
        case .alreadyInFlight:
            break

        case let .restarted(newAttemptId, openedState, preChoice, context):
            stopWaiting()
            handleOpenedAttempt(
                attemptId: newAttemptId,
                openedState: openedState,
                context: context ?? state.waitingContext,
                inMemoryPreChoice: preChoice
            )

        case let .refused(refusal):
            state.waiting = .retryRefused(refusal)

        case .reloadPrepare, .openPrepare, .goToMyExams:
            stopWaiting()
            await startCommand.resetKey()
            state.selection = .empty
            state.start = .idle
            state.waiting = nil
            state.waitingAttemptId = nil
            state.waitingContext = nil
            state.waitingInMemoryPreChoice = nil
            state.waitingAbortReason = nil
            await performLoad(maxAutoRetries: 0)
        }
    }

    private func handleOpenedAttempt(
        attemptId: String,
        openedState: AttemptOpenState,
        context: StartContext?,
        inMemoryPreChoice: PreparePreChoiceInput?
    ) {
        switch openedState {
        case .inProgress, .closed:
            stopWaiting()
            state.waiting = nil
            state.waitingAttemptId = nil
            dispatchOpenAttempt(attemptId)

        case .starting:
            startWaitingPoller(
                attemptId: attemptId,
                context: context,
                inMemoryPreChoice: inMemoryPreChoice,
                initialElapsedSeconds: 0
            )

        case let .aborted(abortReason):
            stopWaiting()
            state.waiting = .failed(abortReason: abortReason)
            state.waitingAttemptId = attemptId
            state.waitingContext = context
            state.waitingInMemoryPreChoice = inMemoryPreChoice
            state.waitingAbortReason = abortReason
        }
    }

    private func startWaitingPoller(
        attemptId: String,
        context: StartContext?,
        inMemoryPreChoice: PreparePreChoiceInput?,
        initialElapsedSeconds: TimeInterval
    ) {
        stopWaiting()
        let initialPhase: WaitingPhase = initialElapsedSeconds >= WaitingPoller.busyThresholdSeconds
            ? .busy
            : .polling(elapsed: max(0, initialElapsedSeconds))
        state.waiting = initialPhase
        state.waitingAttemptId = attemptId
        state.waitingContext = context
        state.waitingInMemoryPreChoice = inMemoryPreChoice
        state.waitingAbortReason = nil

        let poller = WaitingPoller(
            attemptId: attemptId,
            clock: clock,
            api: api,
            initialElapsedSeconds: initialElapsedSeconds,
            onReady: { [weak self] readyId in
                guard let self else { return }
                self.stopWaiting()
                self.dispatchOpenAttempt(readyId)
            },
            onAborted: { [weak self] reason in
                guard let self, self.state.waitingAttemptId == attemptId else { return }
                self.state.waiting = .failed(abortReason: reason)
                self.state.waitingAbortReason = reason
            },
            onPhaseChanged: { [weak self] phase in
                guard let self, self.state.waitingAttemptId == attemptId else { return }
                switch self.state.waiting {
                case .retrying, .retryRefused:
                    return
                default:
                    self.state.waiting = phase
                }
            }
        )
        activePoller = poller
        poller.start(initiallyForeground: isForeground)
    }

    private func dispatchOpenAttempt(_ attemptId: String) {
        if let handler = onOpenAttemptHandler {
            pendingOpenAttemptId = nil
            handler(attemptId)
        } else {
            pendingOpenAttemptId = attemptId
        }
    }

    private func buildStartContext(
        response: AttemptPrepareResponse,
        selection: PreChoiceSelection,
        preChoiceInput: PreparePreChoiceInput?
    ) -> StartContext {
        let orderedParts = response.parts.sorted { ($0.ord ?? 0) < ($1.ord ?? 0) }
        let firstPart = orderedParts.first
        let metrics = PrepareMetrics.of(response, selection: selection)
        let firstPartMinutes: Int?
        if response.timing == .SHARED {
            firstPartMinutes = metrics.durationMinutes
        } else {
            firstPartMinutes = firstPart?.durationMinutes.map(Int.init)
        }
        let examKindLabel = PrepareFormatting.localizedName(
            nameVi: response.examKind.nameVi,
            nameEn: response.examKind.nameEn,
            code: response.examKind.code,
            locale: localeProvider()
        )
        return StartContext(
            entitlementId: response.entitlementId,
            preChoice: preChoiceInput,
            examTitle: response.examName,
            examKindLabel: examKindLabel,
            firstPartName: firstPart?.name,
            firstPartMinutes: firstPartMinutes,
            timing: response.timing.rawValue
        )
    }
}

@MainActor
final class PrepareViewModelRegistry {
    static let shared = PrepareViewModelRegistry()

    private struct Key: Hashable {
        let routerId: ObjectIdentifier
        let destination: Destination
        let entitlementId: String
    }

    private final class Entry {
        weak var router: AppRouter?
        weak var sessionStore: SessionStore?
        let accountId: String?
        let viewModel: PrepareViewModel

        init(
            router: AppRouter,
            sessionStore: SessionStore?,
            accountId: String?,
            viewModel: PrepareViewModel
        ) {
            self.router = router
            self.sessionStore = sessionStore
            self.accountId = accountId
            self.viewModel = viewModel
        }
    }

    private var entries: [Key: Entry] = [:]

    func viewModel(
        for router: AppRouter,
        destination: Destination,
        entitlementId: String,
        sessionStore: SessionStore?,
        localeProvider: @escaping () -> AppLocale,
        onOpenAttempt: @escaping (String) -> Void
    ) -> PrepareViewModel {
        pruneStaleEntries()
        let key = Key(routerId: ObjectIdentifier(router), destination: destination, entitlementId: entitlementId)
        let currentAccountId = sessionStore?.currentAccountId

        if let existing = entries[key],
           existing.sessionStore === sessionStore,
           existing.accountId == currentAccountId
        {
            existing.viewModel.updateBindings(
                localeProvider: localeProvider,
                onOpenAttempt: onOpenAttempt
            )
            return existing.viewModel
        }

        evict(for: key)

        let api: any PrepareAPIClientProtocol = sessionStore.map { PrepareAPI(apiClient: $0.apiClient) }
            ?? PreviewEmptyPrepareAPI()
        let storeProvider = sessionStore?.storeProvider
        let tokenStore = sessionStore?.tokenStore
        let startContextStore = StartContextStore(storeResolver: {
            guard let storeProvider, let tokenStore,
                  let accountId = (try? tokenStore.read())?.accountId
            else {
                return nil
            }
            return storeProvider.store(for: accountId)
        })

        let viewModel = PrepareViewModel(
            entitlementId: entitlementId,
            api: api,
            startContextStore: startContextStore,
            localeProvider: localeProvider,
            onOpenAttempt: onOpenAttempt
        )
        let entry = Entry(
            router: router,
            sessionStore: sessionStore,
            accountId: currentAccountId,
            viewModel: viewModel
        )
        entries[key] = entry
        if let sessionStore {
            observeSessionState(for: key, entry: entry, sessionStore: sessionStore)
        }
        return viewModel
    }

    func evict(router: AppRouter, destination: Destination, entitlementId: String) {
        let key = Key(routerId: ObjectIdentifier(router), destination: destination, entitlementId: entitlementId)
        evict(for: key)
    }

    func clearAll() {
        for entry in entries.values {
            entry.viewModel.clear()
        }
        entries.removeAll()
    }

    private func evict(for key: Key) {
        entries[key]?.viewModel.clear()
        entries.removeValue(forKey: key)
    }

    private func pruneStaleEntries() {
        let staleKeys = entries.compactMap { key, entry -> Key? in
            if entry.router == nil { return key }
            if let sessionStore = entry.sessionStore, !sessionStore.state.isAuthenticated { return key }
            return nil
        }
        for key in staleKeys {
            evict(for: key)
        }
    }

    private func observeSessionState(for key: Key, entry: Entry, sessionStore: SessionStore) {
        withObservationTracking {
            _ = sessionStore.state
        } onChange: { [weak self, weak entry, weak sessionStore] in
            Task { @MainActor in
                guard let self, let entry, let sessionStore else { return }
                guard self.entries[key] === entry else { return }
                if !sessionStore.state.isAuthenticated {
                    self.evict(for: key)
                    return
                }
                self.observeSessionState(for: key, entry: entry, sessionStore: sessionStore)
            }
        }
    }
}
