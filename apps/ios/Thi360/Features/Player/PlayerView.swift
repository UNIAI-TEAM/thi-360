import SwiftUI
import UIKit

@MainActor
final class PlayerEventObserver {
    private var task: Task<Void, Never>?
    private weak var boundEngine: AttemptEngine?
    private weak var boundHost: (any PlayerAttachmentHost)?

    func bind(
        engine: AttemptEngine,
        attachmentHost: (any PlayerAttachmentHost)?,
        onPartClosed: @escaping (PartID) -> Void,
        onAttemptClosed: @escaping (ClosedReason) -> Void,
        onSessionLost: @escaping () -> Void
    ) {
        if boundEngine === engine && boundHost === attachmentHost && task != nil {
            return
        }
        stop()
        boundEngine = engine
        boundHost = attachmentHost
        let stream = engine.events
        task = Task { @MainActor [weak self] in
            for await event in stream {
                guard !Task.isCancelled, let self else { break }
                switch event {
                case let .beforeClose(target):
                    self.boundHost?.onBeforeClose(target)
                case let .partClosed(partId):
                    onPartClosed(partId)
                case let .attemptClosed(reason):
                    onAttemptClosed(reason)
                case .sessionLost:
                    onSessionLost()
                }
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        boundEngine = nil
        boundHost = nil
    }
}

struct PlayerHostView: View {
    let attemptId: String
    let engine: AttemptEngine
    let bodyStore: PlayerQuestionBodyStore
    let locale: AppLocale
    let partChoiceLoader: PartChoiceOptionsLoader?
    let prepareAPI: (any PrepareAPIClientProtocol)?
    let startContextStore: StartContextStore?
    let waitingClock: any PrepareClock
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let busyNoticeDelay: Duration
    let idiomOverride: UIUserInterfaceIdiom?
    let managesScenePhase: Bool
    let onCloseAttempt: () -> Void
    let onBackToMyExams: () -> Void
    let onViewResult: () -> Void
    let onReplaceAttempt: (String) -> Void
    let onOpenPrepare: (String) -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.playerAttachmentHost) private var attachmentHost
    @Environment(\.scenePhase) private var scenePhase

    @SceneStorage("player.localUI.v1") private var storedLocalUIJSON: String = ""
    @SceneStorage("player.localUI.attemptId.v1") private var storedLocalUIAttemptId: String = ""

    @State private var localUI = PlayerLocalUI()
    @State private var hasLoadedLocalUI = false
    @State private var partChoiceLoad: LoadState<PartChoiceMetadata> = .loading
    @State private var choiceLoadRetryNonce: UInt64 = 0
    @State private var dismissedRefusalCode: String?
    @State private var scrollRequest: ScrollToItemRequest?
    @State private var startContext: StartContext?
    @State private var eventObserver = PlayerEventObserver()

    init(
        attemptId: String,
        engine: AttemptEngine,
        bodyStore: PlayerQuestionBodyStore,
        locale: AppLocale,
        partChoiceLoader: PartChoiceOptionsLoader? = nil,
        prepareAPI: (any PrepareAPIClientProtocol)? = nil,
        startContextStore: StartContextStore? = nil,
        waitingClock: any PrepareClock = SystemPrepareClock(),
        assets: AttemptAssetSource? = nil,
        formulaEngine: any FormulaEngine = SwaTexFormulaEngine.shared,
        busyNoticeDelay: Duration = GenerationPanel.defaultBusyNoticeDelay,
        idiomOverride: UIUserInterfaceIdiom? = nil,
        managesScenePhase: Bool = true,
        onCloseAttempt: @escaping () -> Void,
        onBackToMyExams: @escaping () -> Void,
        onViewResult: @escaping () -> Void,
        onReplaceAttempt: @escaping (String) -> Void,
        onOpenPrepare: @escaping (String) -> Void
    ) {
        self.attemptId = attemptId
        self.engine = engine
        self.bodyStore = bodyStore
        self.locale = locale
        self.partChoiceLoader = partChoiceLoader
        self.prepareAPI = prepareAPI
        self.startContextStore = startContextStore
        self.waitingClock = waitingClock
        self.assets = assets
        self.formulaEngine = formulaEngine
        self.busyNoticeDelay = busyNoticeDelay
        self.idiomOverride = idiomOverride
        self.managesScenePhase = managesScenePhase
        self.onCloseAttempt = onCloseAttempt
        self.onBackToMyExams = onBackToMyExams
        self.onViewResult = onViewResult
        self.onReplaceAttempt = onReplaceAttempt
        self.onOpenPrepare = onOpenPrepare
    }

    private var needsChoiceMetadata: Bool {
        (engine.state.attempt?.parts ?? []).contains { part in
            if part.state == .AWAITING_CHOICE || part.state == .GENERATING || part.state == .GENERATION_FAILED {
                return true
            }
            return part.choice?.additionalProperties.value.isEmpty == false
        }
    }

    private func updateLocalUI(_ mutate: (inout PlayerLocalUI) -> Void) {
        var next = localUI
        mutate(&next)
        localUI = next
        storedLocalUIAttemptId = attemptId
        storedLocalUIJSON = next.encodedJSONString()
    }

    private var currentRefusalCode: String? {
        if case let .refused(code) = engine.state.command {
            return code
        }
        return nil
    }

    var body: some View {
        let bodies = bodyStore.bodies(for: attemptId)

        PlayerView(
            attemptId: attemptId,
            engineState: engine.state,
            localUI: localUI,
            bodies: bodies,
            locale: locale,
            partChoiceLoad: partChoiceLoad,
            dismissedRefusalCode: dismissedRefusalCode,
            scrollRequest: scrollRequest,
            startContext: startContext,
            prepareAPI: prepareAPI,
            startContextStore: startContextStore,
            waitingClock: waitingClock,
            assets: assets,
            formulaEngine: formulaEngine,
            busyNoticeDelay: busyNoticeDelay,
            idiomOverride: idiomOverride,
            onRetryLoad: { engine.refresh() },
            onWaitingReady: { engine.refresh() },
            onCloseAttempt: onCloseAttempt,
            onBackToMyExams: onBackToMyExams,
            onViewResult: onViewResult,
            onReplaceAttempt: onReplaceAttempt,
            onOpenPrepare: onOpenPrepare,
            onToggleCollapse: {
                updateLocalUI { $0.headerCollapsed.toggle() }
            },
            onSelectPart: { partId in
                guard engine.state.position?.partId != partId else { return }
                let targetPart = engine.state.attempt?.parts?.first(where: { $0.partId == partId })
                let firstLeaf = targetPart.flatMap(PartSelection.firstLeafItemId(in:))
                engine.position(partId: partId, itemId: firstLeaf)
                updateLocalUI {
                    $0.gridFilter = .all
                    $0.gridOpen = false
                }
            },
            onAnswer: { itemId, value in
                engine.answer(itemId: itemId, value: value)
            },
            onFlag: { itemId, flagged in
                engine.flag(itemId: itemId, flagged: flagged)
            },
            onVisibleItemChanged: { partId, itemId in
                if engine.state.position?.partId != partId || engine.state.position?.itemId != itemId {
                    engine.position(partId: partId, itemId: itemId)
                }
            },
            onOpenGridSheet: {
                updateLocalUI { $0.gridOpen = true }
            },
            onCloseGridSheet: {
                updateLocalUI { $0.gridOpen = false }
            },
            onSelectGridFilter: { filter in
                updateLocalUI { $0.gridFilter = filter }
            },
            onSelectGridItem: { itemId in
                let partId = engine.state.position?.partId
                    ?? engine.state.attempt?.parts?.first(where: { part in
                        (part.items ?? []).contains(where: { $0.itemId == itemId })
                    })?.partId
                if let partId {
                    engine.position(partId: partId, itemId: itemId)
                }
                scrollRequest = ScrollToItemRequest(
                    itemId: itemId,
                    nonce: (scrollRequest?.nonce ?? 0) &+ 1
                )
            },
            onOpenDialog: { dialog in
                dismissedRefusalCode = currentRefusalCode
                updateLocalUI { $0.dialog = dialog }
            },
            onDismissDialog: {
                dismissedRefusalCode = currentRefusalCode
                updateLocalUI { $0.dialog = .none }
            },
            onConfirmFinishPart: { partId in
                dismissedRefusalCode = nil
                engine.finishPart(partId: partId)
            },
            onConfirmSubmit: {
                dismissedRefusalCode = nil
                engine.submit()
            },
            onReconcile: {
                engine.reconcile()
            },
            onTakeover: {
                dismissedRefusalCode = nil
                engine.takeover()
            },
            onSelectChoiceOption: { partId, optionCode in
                dismissedRefusalCode = currentRefusalCode
                updateLocalUI {
                    $0.choiceDraftPartId = partId
                    $0.choiceDraft = PartChoiceDraft(optionCode: optionCode, subjectCodes: [])
                }
            },
            onToggleChoiceSubject: { partId, subjectCode, maxSelect in
                dismissedRefusalCode = currentRefusalCode
                updateLocalUI { ui in
                    var draft = (ui.choiceDraftPartId == partId) ? ui.choiceDraft : PartChoiceDraft()
                    if let idx = draft.subjectCodes.firstIndex(of: subjectCode) {
                        draft.subjectCodes.remove(at: idx)
                    } else if maxSelect > 0 && draft.subjectCodes.count < maxSelect {
                        draft.subjectCodes.append(subjectCode)
                    }
                    ui.choiceDraftPartId = partId
                    ui.choiceDraft = draft
                }
            },
            onConfirmPartChoice: { partId, optionCode, subjects in
                dismissedRefusalCode = nil
                engine.choosePart(
                    partId: partId,
                    optionCode: optionCode,
                    subjectCodes: subjects.isEmpty ? nil : subjects
                )
            },
            onRetryLoadChoiceOptions: {
                choiceLoadRetryNonce &+= 1
            },
            onRetryGeneration: { partId in
                dismissedRefusalCode = nil
                engine.retryGeneration(partId: partId)
            }
        )
        .onAppear {
            restoreLocalUIIfNeeded()
            bindEventObserver()
        }
        .onDisappear {
            eventObserver.stop()
        }
        .onChange(of: attemptId) { _, _ in
            hasLoadedLocalUI = false
            restoreLocalUIIfNeeded()
            partChoiceLoad = .loading
            dismissedRefusalCode = nil
            scrollRequest = nil
            bindEventObserver()
        }
        .onChange(of: horizontalSizeClass) { _, newSizeClass in
            if newSizeClass == .regular && localUI.gridOpen {
                updateLocalUI { $0.gridOpen = false }
            }
        }
        .onChange(of: engine.state.session) { _, newSession in
            switch newSession {
            case .openElsewhere, .evicted:
                if localUI.dialog != .none || localUI.gridOpen {
                    updateLocalUI {
                        $0.dialog = .none
                        $0.gridOpen = false
                    }
                }
            default:
                break
            }
        }
        .onChange(of: engine.state.command) { oldCommand, newCommand in
            if case .refused = newCommand,
               oldCommand == .sending(.finishPart) || oldCommand == .sending(.submit)
            {
                if localUI.dialog == .finishPart || localUI.dialog == .submit {
                    updateLocalUI { $0.dialog = .none }
                }
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            guard managesScenePhase else { return }
            switch newPhase {
            case .background, .inactive:
                engine.setForeground(false)
            case .active:
                engine.setForeground(true)
            @unknown default:
                break
            }
        }
        .task(id: "\(attemptId)::\(needsChoiceMetadata)::\(choiceLoadRetryNonce)") {
            guard needsChoiceMetadata else { return }
            guard let loader = partChoiceLoader else {
                partChoiceLoad = .failed(kind: .server, canRetry: true, retryAfter: nil)
                return
            }
            let alreadyLoaded: Bool
            if case .loaded = partChoiceLoad {
                alreadyLoaded = true
            } else {
                alreadyLoaded = false
            }
            if choiceLoadRetryNonce > 0 || !alreadyLoaded {
                partChoiceLoad = .loading
                partChoiceLoad = await loader.load(attemptId: attemptId)
            }
        }
        .task(id: attemptId) {
            startContext = await startContextStore?.read(attemptId: attemptId)
        }
    }

    private func restoreLocalUIIfNeeded() {
        guard !hasLoadedLocalUI else { return }
        hasLoadedLocalUI = true
        if storedLocalUIAttemptId == attemptId, !storedLocalUIJSON.isEmpty {
            localUI = PlayerLocalUI.decoded(from: storedLocalUIJSON)
        } else {
            localUI = PlayerLocalUI()
        }
    }

    private func bindEventObserver() {
        eventObserver.bind(
            engine: engine,
            attachmentHost: attachmentHost,
            onPartClosed: { _ in
                updateLocalUI {
                    $0.dialog = .none
                    $0.gridOpen = false
                }
            },
            onAttemptClosed: { _ in
                updateLocalUI {
                    $0.dialog = .none
                    $0.gridOpen = false
                }
            },
            onSessionLost: {
                updateLocalUI {
                    $0.dialog = .none
                    $0.gridOpen = false
                }
            }
        )
    }
}

struct PlayerView: View {
    let attemptId: String
    let engineState: EngineState
    let localUI: PlayerLocalUI
    let bodies: [ItemID: AttemptQuestionBodyValue]
    let locale: AppLocale
    let partChoiceLoad: LoadState<PartChoiceMetadata>
    let dismissedRefusalCode: String?
    let scrollRequest: ScrollToItemRequest?
    let startContext: StartContext?
    let prepareAPI: (any PrepareAPIClientProtocol)?
    let startContextStore: StartContextStore?
    let waitingClock: any PrepareClock
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let busyNoticeDelay: Duration
    let idiomOverride: UIUserInterfaceIdiom?
    let onRetryLoad: () -> Void
    let onWaitingReady: () -> Void
    let onCloseAttempt: () -> Void
    let onBackToMyExams: () -> Void
    let onViewResult: () -> Void
    let onReplaceAttempt: (String) -> Void
    let onOpenPrepare: (String) -> Void
    let onToggleCollapse: () -> Void
    let onSelectPart: (PartID) -> Void
    let onAnswer: (ItemID, AnswerValue) -> Void
    let onFlag: (ItemID, Bool) -> Void
    let onVisibleItemChanged: (PartID, ItemID) -> Void
    let onOpenGridSheet: () -> Void
    let onCloseGridSheet: () -> Void
    let onSelectGridFilter: (PlayerGridFilter) -> Void
    let onSelectGridItem: (ItemID) -> Void
    let onOpenDialog: (PlayerDialog) -> Void
    let onDismissDialog: () -> Void
    let onConfirmFinishPart: (PartID) -> Void
    let onConfirmSubmit: () -> Void
    let onReconcile: () -> Void
    let onTakeover: () -> Void
    let onSelectChoiceOption: (PartID, String) -> Void
    let onToggleChoiceSubject: (PartID, String, Int) -> Void
    let onConfirmPartChoice: (PartID, String, [String]) -> Void
    let onRetryLoadChoiceOptions: () -> Void
    let onRetryGeneration: (PartID) -> Void
    let onUnitMaterialized: ((String) -> Void)?

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    init(
        attemptId: String,
        engineState: EngineState,
        localUI: PlayerLocalUI = PlayerLocalUI(),
        bodies: [ItemID: AttemptQuestionBodyValue] = [:],
        locale: AppLocale = .vi,
        partChoiceLoad: LoadState<PartChoiceMetadata> = .loading,
        dismissedRefusalCode: String? = nil,
        scrollRequest: ScrollToItemRequest? = nil,
        startContext: StartContext? = nil,
        prepareAPI: (any PrepareAPIClientProtocol)? = nil,
        startContextStore: StartContextStore? = nil,
        waitingClock: any PrepareClock = SystemPrepareClock(),
        assets: AttemptAssetSource? = nil,
        formulaEngine: any FormulaEngine = SwaTexFormulaEngine.shared,
        busyNoticeDelay: Duration = GenerationPanel.defaultBusyNoticeDelay,
        idiomOverride: UIUserInterfaceIdiom? = nil,
        onRetryLoad: @escaping () -> Void = {},
        onWaitingReady: @escaping () -> Void = {},
        onCloseAttempt: @escaping () -> Void = {},
        onBackToMyExams: @escaping () -> Void = {},
        onViewResult: @escaping () -> Void = {},
        onReplaceAttempt: @escaping (String) -> Void = { _ in },
        onOpenPrepare: @escaping (String) -> Void = { _ in },
        onToggleCollapse: @escaping () -> Void = {},
        onSelectPart: @escaping (PartID) -> Void = { _ in },
        onAnswer: @escaping (ItemID, AnswerValue) -> Void = { _, _ in },
        onFlag: @escaping (ItemID, Bool) -> Void = { _, _ in },
        onVisibleItemChanged: @escaping (PartID, ItemID) -> Void = { _, _ in },
        onOpenGridSheet: @escaping () -> Void = {},
        onCloseGridSheet: @escaping () -> Void = {},
        onSelectGridFilter: @escaping (PlayerGridFilter) -> Void = { _ in },
        onSelectGridItem: @escaping (ItemID) -> Void = { _ in },
        onOpenDialog: @escaping (PlayerDialog) -> Void = { _ in },
        onDismissDialog: @escaping () -> Void = {},
        onConfirmFinishPart: @escaping (PartID) -> Void = { _ in },
        onConfirmSubmit: @escaping () -> Void = {},
        onReconcile: @escaping () -> Void = {},
        onTakeover: @escaping () -> Void = {},
        onSelectChoiceOption: @escaping (PartID, String) -> Void = { _, _ in },
        onToggleChoiceSubject: @escaping (PartID, String, Int) -> Void = { _, _, _ in },
        onConfirmPartChoice: @escaping (PartID, String, [String]) -> Void = { _, _, _ in },
        onRetryLoadChoiceOptions: @escaping () -> Void = {},
        onRetryGeneration: @escaping (PartID) -> Void = { _ in },
        onUnitMaterialized: ((String) -> Void)? = nil
    ) {
        self.attemptId = attemptId
        self.engineState = engineState
        self.localUI = localUI
        self.bodies = bodies
        self.locale = locale
        self.partChoiceLoad = partChoiceLoad
        self.dismissedRefusalCode = dismissedRefusalCode
        self.scrollRequest = scrollRequest
        self.startContext = startContext
        self.prepareAPI = prepareAPI
        self.startContextStore = startContextStore
        self.waitingClock = waitingClock
        self.assets = assets
        self.formulaEngine = formulaEngine
        self.busyNoticeDelay = busyNoticeDelay
        self.idiomOverride = idiomOverride
        self.onRetryLoad = onRetryLoad
        self.onWaitingReady = onWaitingReady
        self.onCloseAttempt = onCloseAttempt
        self.onBackToMyExams = onBackToMyExams
        self.onViewResult = onViewResult
        self.onReplaceAttempt = onReplaceAttempt
        self.onOpenPrepare = onOpenPrepare
        self.onToggleCollapse = onToggleCollapse
        self.onSelectPart = onSelectPart
        self.onAnswer = onAnswer
        self.onFlag = onFlag
        self.onVisibleItemChanged = onVisibleItemChanged
        self.onOpenGridSheet = onOpenGridSheet
        self.onCloseGridSheet = onCloseGridSheet
        self.onSelectGridFilter = onSelectGridFilter
        self.onSelectGridItem = onSelectGridItem
        self.onOpenDialog = onOpenDialog
        self.onDismissDialog = onDismissDialog
        self.onConfirmFinishPart = onConfirmFinishPart
        self.onConfirmSubmit = onConfirmSubmit
        self.onReconcile = onReconcile
        self.onTakeover = onTakeover
        self.onSelectChoiceOption = onSelectChoiceOption
        self.onToggleChoiceSubject = onToggleChoiceSubject
        self.onConfirmPartChoice = onConfirmPartChoice
        self.onRetryLoadChoiceOptions = onRetryLoadChoiceOptions
        self.onRetryGeneration = onRetryGeneration
        self.onUnitMaterialized = onUnitMaterialized
    }

    private var resolvedIdiom: UIUserInterfaceIdiom {
        idiomOverride ?? UIDevice.current.userInterfaceIdiom
    }

    private var viewportKind: PlayerViewportKind {
        PlayerViewportKind.resolve(idiom: resolvedIdiom, horizontalSizeClass: horizontalSizeClass)
    }

    var body: some View {
        let ui = PlayerUI.make(
            state: engineState,
            layout: viewportKind.playerLayout,
            local: localUI,
            bodies: bodies,
            partChoiceLoad: partChoiceLoad,
            dismissedRefusalCode: dismissedRefusalCode
        )

        GeometryReader { proxy in
            ZStack {
                palette.bgPageColor.ignoresSafeArea()

                switch ui.surface {
                case .loading:
                    loadingView

                case .loadError:
                    loadErrorView(ui: ui)

                case .waiting:
                    waitingView(ui: ui)

                case .closed:
                    closedView(ui: ui)

                case .evicted:
                    evictedView(ui: ui)

                case .active:
                    activeContentView(ui: ui, totalWidth: proxy.size.width)
                }

                if ui.surface == .active {
                    if ui.showTakeoverDialog {
                        TakeoverDialog(
                            examTitle: ui.examTitle,
                            lastSeenAt: ui.takeoverLastSeenAt,
                            isSending: ui.isTakeoverSending,
                            errorCode: ui.takeoverErrorCode,
                            locale: locale,
                            onConfirm: onTakeover,
                            onCancel: onBackToMyExams
                        )
                        .zIndex(30)
                    } else {
                        dialogOverlay(ui: ui)
                            .zIndex(25)
                    }
                }
            }
            .sheet(
                isPresented: Binding(
                    get: { ui.gridSheetOpen },
                    set: { isPresented in
                        if !isPresented { onCloseGridSheet() }
                    }
                )
            ) {
                QuestionGridSheet(
                    ui: ui,
                    locale: locale,
                    onDismiss: onCloseGridSheet,
                    onSelectFilter: onSelectGridFilter,
                    onSelectItem: onSelectGridItem
                )
                .environment(\.brandPalette, palette)
                .environment(\.statusPalette, statusPalette)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.screen")
        .simultaneousGesture(
            DragGesture(minimumDistance: 24, coordinateSpace: .local)
                .onEnded { value in
                    guard value.startLocation.x <= 32,
                          value.translation.width >= 72,
                          abs(value.translation.height) < 80
                    else {
                        return
                    }
                    if ui.surface == .waiting {
                        onCloseAttempt()
                    } else {
                        handleBack(ui)
                    }
                }
        )
    }

    private func handleBack(_ ui: PlayerUI) {
        if ui.gridSheetOpen {
            onCloseGridSheet()
            return
        }
        if ui.showTakeoverDialog {
            if !ui.isTakeoverSending {
                onBackToMyExams()
            }
            return
        }
        if ui.activeDialog != .none {
            let isBusy: Bool
            if case .sending = ui.commandState {
                isBusy = true
            } else {
                isBusy = false
            }
            if !isBusy {
                onDismissDialog()
            }
            return
        }
        if ui.surface == .active && ui.hasUnsavedChanges {
            onOpenDialog(.leaveConfirm)
            return
        }
        onCloseAttempt()
    }

    private var loadingView: some View {
        let title = AppStrings.common("attempt.waiting.title", locale: locale)
        return VStack(spacing: 14) {
            ProgressView()
                .tint(palette.primaryFillColor)
                .scaleEffect(1.2)
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .playerProbe("player.loading", label: title)
    }

    private func loadErrorView(ui: PlayerUI) -> some View {
        let isNotFound = (ui.loadFailureKind == .notFound)
        let titleKey = isNotFound ? "attempt.errors.notFoundTitle" : "attempt.errors.loadTitle"
        let descKey = isNotFound ? "attempt.errors.notFoundDescription" : "attempt.errors.loadDescription"
        let titleText = PlayerStrings.text(titleKey, locale: locale)
        let descText = PlayerStrings.text(descKey, locale: locale)
        let retryText = PlayerStrings.text("attempt.errors.retry", locale: locale)
        let backText = PlayerStrings.text("attempt.errors.loadBackToMyExams", locale: locale)

        return ScrollView {
            VStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(palette.dangerBgColor)
                        .frame(width: 56, height: 56)
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 26, weight: .semibold))
                        .foregroundStyle(palette.dangerTextColor)
                }
                .accessibilityHidden(true)

                Text(titleText)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .playerProbe("player.error.title", label: titleText)

                Text(descText)
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .playerProbe("player.error.description", label: descText)

                WrappingHStackLayout(horizontalSpacing: 10, verticalSpacing: 8) {
                    if ui.canRetryLoad && !isNotFound {
                        Button(action: onRetryLoad) {
                            Text(retryText)
                                .font(.body.weight(.bold))
                                .foregroundStyle(palette.onPrimaryColor)
                                .padding(.horizontal, 20)
                                .frame(minHeight: 46)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(palette.primaryFillColor)
                                )
                        }
                        .buttonStyle(.plain)
                        .playerProbe("player.error.retryButton", label: retryText, isButton: true)
                    }

                    Button(action: onBackToMyExams) {
                        Text(backText)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .padding(.horizontal, 18)
                            .frame(minHeight: 46)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(palette.bgSurfaceColor)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                    .playerProbe("player.error.backToMyExamsButton", label: backText, isButton: true)
                }
            }
            .padding(24)
            .frame(maxWidth: 480)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .frame(maxWidth: .infinity)
            .padding(20)
        }
        .accessibilityElement(children: .contain)
        .playerProbe("player.error")
    }

    private func waitingView(ui: PlayerUI) -> some View {
        let initialPhase: WaitingPhase = (ui.waitingInitialState == .ABORTED)
            ? .failed(abortReason: ui.waitingAbortReason)
            : .polling(elapsed: 0)

        return AttemptWaitingView(
            attemptId: attemptId,
            examTitle: ui.examTitle,
            context: startContext,
            origin: .player,
            onReady: { _ in onWaitingReady() },
            onRestarted: onReplaceAttempt,
            onOpenPrepare: onOpenPrepare,
            onMyExams: onBackToMyExams,
            initialPhase: initialPhase,
            timing: ui.attempt?.timing?.rawValue,
            locale: locale,
            showsTopBar: true,
            api: prepareAPI,
            startContextStore: startContextStore,
            clock: waitingClock,
            onBack: onCloseAttempt
        )
        .accessibilityElement(children: .contain)
        .playerProbe("player.waiting", label: ui.examTitle)
    }

    private func closedView(ui: PlayerUI) -> some View {
        let headingKey: String
        switch ui.closedReason {
        case .timeUp:
            headingKey = "attempt.closed.headingTimeUp"
        case .choiceTimeout:
            headingKey = "attempt.closed.headingChoiceTimeout"
        default:
            headingKey = "attempt.closed.heading"
        }

        let headingText = PlayerStrings.text(headingKey, locale: locale)
        let descText = PlayerStrings.text("attempt.closed.description", locale: locale)
        let viewResultText = PlayerStrings.text("attempt.closed.viewResult", locale: locale)
        let backText = PlayerStrings.text("attempt.closed.backToMyExams", locale: locale)

        return ScrollView {
            VStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(statusPalette.successBgColor)
                        .frame(width: 58, height: 58)
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(statusPalette.successColor)
                }
                .accessibilityHidden(true)

                Text(headingText)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .playerProbe("player.closed.heading", label: headingText)

                if !ui.examTitle.isEmpty {
                    Text(ui.examTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.primaryTextColor)
                        .multilineTextAlignment(.center)
                        .playerProbe("player.closed.examTitle", label: ui.examTitle)
                }

                Text(descText)
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .playerProbe("player.closed.description", label: descText)

                VStack(spacing: 10) {
                    Button(action: onViewResult) {
                        Text(viewResultText)
                            .font(.body.weight(.bold))
                            .foregroundStyle(palette.onPrimaryColor)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(palette.primaryFillColor)
                            )
                    }
                    .buttonStyle(.plain)
                    .playerProbe("player.closed.viewResultButton", label: viewResultText, isButton: true)

                    Button(action: onBackToMyExams) {
                        Text(backText)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(palette.bgSurfaceColor)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                    .playerProbe("player.closed.backToMyExamsButton", label: backText, isButton: true)
                }
                .padding(.top, 4)
            }
            .padding(24)
            .frame(maxWidth: 480)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .frame(maxWidth: .infinity)
            .padding(20)
        }
        .accessibilityElement(children: .contain)
        .playerProbe("player.closed")
    }

    private func evictedView(ui: PlayerUI) -> some View {
        let headingText = PlayerStrings.text("attempt.evicted.heading", locale: locale)
        let descText = PlayerStrings.text("attempt.evicted.description", locale: locale)
        let takeoverText = PlayerStrings.text("attempt.evicted.takeoverHere", locale: locale)
        let backText = PlayerStrings.text("attempt.evicted.backToMyExams", locale: locale)

        return ScrollView {
            VStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(palette.dangerBgColor)
                        .frame(width: 58, height: 58)
                    Image(systemName: "person.2.slash.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(palette.dangerTextColor)
                }
                .accessibilityHidden(true)

                Text(headingText)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .playerProbe("player.evicted.heading", label: headingText)

                if !ui.examTitle.isEmpty {
                    Text(ui.examTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textSecondaryColor)
                        .multilineTextAlignment(.center)
                }

                Text(descText)
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .playerProbe("player.evicted.description", label: descText)

                if ui.takeoverErrorCode != nil {
                    let errText = PlayerStrings.text("attempt.takeover.error", locale: locale)
                    Text(errText)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(palette.dangerTextColor)
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.dangerBgColor)
                        )
                        .playerProbe("player.evicted.error", label: errText)
                }

                VStack(spacing: 10) {
                    Button(action: onTakeover) {
                        HStack(spacing: 8) {
                            if ui.isTakeoverSending {
                                ProgressView()
                                    .tint(palette.onPrimaryColor)
                            }
                            Text(takeoverText)
                                .font(.body.weight(.bold))
                        }
                        .foregroundStyle(palette.onPrimaryColor)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.primaryFillColor)
                                .opacity(ui.isTakeoverSending ? 0.55 : 1.0)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(ui.isTakeoverSending)
                    .playerProbe("player.evicted.takeoverButton", label: takeoverText, isButton: true, isEnabled: !ui.isTakeoverSending)

                    Button(action: onBackToMyExams) {
                        Text(backText)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(palette.bgSurfaceColor)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(ui.isTakeoverSending)
                    .playerProbe("player.evicted.backToMyExamsButton", label: backText, isButton: true, isEnabled: !ui.isTakeoverSending)
                }
                .padding(.top, 4)
            }
            .padding(24)
            .frame(maxWidth: 480)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .frame(maxWidth: .infinity)
            .padding(20)
        }
        .accessibilityElement(children: .contain)
        .playerProbe("player.evicted")
    }

    @ViewBuilder
    private func activeContentView(ui: PlayerUI, totalWidth: CGFloat) -> some View {
        if viewportKind == .phoneDuoOpen && ui.choicePanel == nil && ui.generationPanel == nil {
            duoOpenLayout(ui: ui, totalWidth: totalWidth)
        } else {
            VStack(spacing: 0) {
                PlayerTopBar(
                    ui: ui,
                    locale: locale,
                    onBack: { handleBack(ui) },
                    onToggleCollapse: onToggleCollapse,
                    onAction: { handleTopAction(ui) },
                    onSelectPart: onSelectPart
                )

                if let choicePanel = ui.choicePanel {
                    PartChoicePanel(
                        panel: choicePanel,
                        locale: locale,
                        onSelectOption: { code in
                            if !choicePanel.isLocked {
                                onSelectChoiceOption(choicePanel.partId, code)
                            }
                        },
                        onToggleSubject: { code in
                            if !choicePanel.isLocked {
                                onToggleChoiceSubject(choicePanel.partId, code, choicePanel.requiredSubjectCount)
                            }
                        },
                        onConfirm: {
                            if let code = choicePanel.selectedOptionCode, !code.isEmpty {
                                onConfirmPartChoice(choicePanel.partId, code, choicePanel.selectedSubjectCodes)
                            }
                        },
                        onRetryLoadOptions: onRetryLoadChoiceOptions,
                        onBackToMyExams: onBackToMyExams
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let generationPanel = ui.generationPanel {
                    GenerationPanel(
                        panel: generationPanel,
                        locale: locale,
                        busyNoticeDelay: busyNoticeDelay,
                        onRetry: { onRetryGeneration(generationPanel.partId) }
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewportKind == .iPadRegular {
                    let sidebarWidth: CGFloat = totalWidth >= 1080 ? 300 : 260
                    let contentWidth = max(totalWidth - sidebarWidth, 320)
                    HStack(spacing: 0) {
                        PlayerContent(
                            attemptId: attemptId,
                            ui: ui,
                            answers: engineState.answers,
                            locale: locale,
                            scrollRequest: scrollRequest,
                            assets: assets,
                            formulaEngine: formulaEngine,
                            contentWidth: contentWidth,
                            duoRightPaneQuestionsOnly: false,
                            onAnswer: onAnswer,
                            onFlag: onFlag,
                            onVisibleItemChanged: onVisibleItemChanged,
                            onUnitMaterialized: onUnitMaterialized
                        )
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                        QuestionGridSidebar(
                            ui: ui,
                            locale: locale,
                            width: sidebarWidth,
                            onSelectFilter: onSelectGridFilter,
                            onSelectItem: onSelectGridItem
                        )
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityIdentifier("player.layout.iPad")
                } else {
                    VStack(spacing: 0) {
                        PlayerContent(
                            attemptId: attemptId,
                            ui: ui,
                            answers: engineState.answers,
                            locale: locale,
                            scrollRequest: scrollRequest,
                            assets: assets,
                            formulaEngine: formulaEngine,
                            contentWidth: totalWidth,
                            duoRightPaneQuestionsOnly: false,
                            onAnswer: onAnswer,
                            onFlag: onFlag,
                            onVisibleItemChanged: onVisibleItemChanged,
                            onUnitMaterialized: onUnitMaterialized
                        )
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                        if ui.totalInActivePart > 0 {
                            PlayerNarrowBottomBar(
                                currentIndex: ui.currentDisplayIndex,
                                answeredCount: ui.answeredInActivePart,
                                totalCount: ui.totalInActivePart,
                                locale: locale,
                                onOpenGrid: onOpenGridSheet
                            )
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityIdentifier("player.layout.compact")
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func duoOpenLayout(ui: PlayerUI, totalWidth: CGFloat) -> some View {
        let halfWidth = max(240, (totalWidth - 1) / 2)
        let hasActivePassage = hasQuestionSetInCurrentPosition(ui: ui)
        let gridWidth = hasActivePassage ? min(210, max(160, halfWidth * 0.44)) : halfWidth
        let passageWidth = max(180, halfWidth - gridWidth)

        return HStack(spacing: 0) {
            HStack(spacing: 0) {
                QuestionGridSidebar(
                    ui: ui,
                    locale: locale,
                    width: gridWidth,
                    onSelectFilter: onSelectGridFilter,
                    onSelectItem: onSelectGridItem
                )

                if hasActivePassage {
                    DuoPassagePane(
                        attemptId: attemptId,
                        ui: ui,
                        answers: engineState.answers,
                        locale: locale,
                        assets: assets,
                        formulaEngine: formulaEngine,
                        paneWidth: passageWidth
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .frame(width: halfWidth)
            .frame(maxHeight: .infinity)
            .accessibilityElement(children: .contain)
            .playerProbe("player.duo.leftPane")

            Rectangle()
                .fill(palette.borderColor)
                .frame(width: 1)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                PlayerTopBar(
                    ui: ui,
                    locale: locale,
                    onBack: { handleBack(ui) },
                    onToggleCollapse: onToggleCollapse,
                    onAction: { handleTopAction(ui) },
                    onSelectPart: onSelectPart
                )

                PlayerContent(
                    attemptId: attemptId,
                    ui: ui,
                    answers: engineState.answers,
                    locale: locale,
                    scrollRequest: scrollRequest,
                    assets: assets,
                    formulaEngine: formulaEngine,
                    contentWidth: halfWidth,
                    duoRightPaneQuestionsOnly: hasActivePassage,
                    onAnswer: onAnswer,
                    onFlag: onFlag,
                    onVisibleItemChanged: onVisibleItemChanged,
                    onUnitMaterialized: onUnitMaterialized
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(width: halfWidth)
            .frame(maxHeight: .infinity)
            .accessibilityElement(children: .contain)
            .playerProbe("player.duo.rightPane")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
        .playerProbe("player.layout.duoOpen")
    }

    private func hasQuestionSetInCurrentPosition(ui: PlayerUI) -> Bool {
        guard let currentId = ui.currentItemId else { return false }
        for group in ui.unitGroups {
            for unit in group.units where unit.kind == .questionSet {
                if unit.leafIdSet.contains(currentId) {
                    return true
                }
            }
        }
        return false
    }

    private func handleTopAction(_ ui: PlayerUI) {
        switch ui.topAction {
        case .finishPart:
            onOpenDialog(.finishPart)
        case .submit:
            onOpenDialog(.submit)
        case .none:
            break
        }
    }

    @ViewBuilder
    private func dialogOverlay(ui: PlayerUI) -> some View {
        switch ui.activeDialog {
        case .none:
            EmptyView()

        case .finishPart:
            if let summary = ui.finishPartSummary {
                FinishPartDialog(
                    summary: summary,
                    commandState: ui.commandState,
                    locale: locale,
                    onConfirm: { onConfirmFinishPart(summary.partId) },
                    onReconcile: onReconcile,
                    onDismiss: onDismissDialog
                )
            }

        case .submit:
            if let summary = ui.submitSummary {
                SubmitDialog(
                    summary: summary,
                    commandState: ui.commandState,
                    locale: locale,
                    onConfirm: onConfirmSubmit,
                    onReconcile: onReconcile,
                    onDismiss: onDismissDialog
                )
            }

        case .leaveConfirm:
            LeaveConfirmDialog(
                locale: locale,
                onStay: onDismissDialog,
                onLeave: {
                    onDismissDialog()
                    onCloseAttempt()
                }
            )
        }
    }
}
