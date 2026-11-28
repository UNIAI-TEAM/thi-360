import SwiftUI

struct PrepareView: View {
    @Bindable var viewModel: PrepareViewModel
    let locale: AppLocale
    var supportContact: SupportContact = AppEnvironment.current.supportContact
    var usesNavigationBarChrome: Bool = true
    let onOpenAttempt: (String) -> Void
    let onOpenCheckout: (String) -> Void
    let onOpenMyExams: () -> Void
    var onBackFromWaiting: (() -> Void)? = nil

    @Environment(\.brandPalette) private var palette
    @Environment(\.shellWidth) private var shellWidth
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var waitingExamTitle: String {
        if let loaded = viewModel.loadedResponse {
            return loaded.examName
        }
        return viewModel.state.waitingContext?.examTitle
            ?? AppStrings.common("attempt.waiting.title", locale: locale)
    }

    private var projectedLoadState: LoadState<AttemptPrepareResponse> {
        switch viewModel.state.load {
        case .loading, .loaded, .empty:
            return viewModel.state.load
        case let .failed(kind, canRetry, retryAfter):
            switch kind {
            case .notFound:
                return .failed(kind: .notFound, canRetry: false, retryAfter: nil)
            case .offline:
                return .failed(kind: .conflict(code: "PREPARE_LOAD_OFFLINE"), canRetry: canRetry, retryAfter: retryAfter)
            case .server:
                return .failed(kind: .conflict(code: "PREPARE_LOAD_SERVER"), canRetry: canRetry, retryAfter: retryAfter)
            case .forbidden, .gone, .conflict:
                return .failed(kind: kind, canRetry: canRetry, retryAfter: retryAfter)
            }
        }
    }

    var body: some View {
        Group {
            if let waitingPhase = viewModel.state.waiting,
               let attemptId = viewModel.state.waitingAttemptId
            {
                AttemptWaitingView(
                    attemptId: attemptId,
                    examTitle: waitingExamTitle,
                    context: viewModel.state.waitingContext,
                    origin: .prepare,
                    onReady: { readyId in
                        viewModel.stopWaiting()
                        onOpenAttempt(readyId)
                    },
                    onRestarted: { _ in },
                    onOpenPrepare: { _ in },
                    onMyExams: {
                        viewModel.stopWaiting()
                        onOpenMyExams()
                    },
                    controlledPhase: waitingPhase,
                    controlledAbortReason: viewModel.state.waitingAbortReason,
                    timing: viewModel.loadedResponse?.timing.rawValue ?? viewModel.state.waitingContext?.timing,
                    locale: locale,
                    supportContact: supportContact,
                    showsTopBar: true,
                    onRetryOverride: {
                        viewModel.retryFromWaiting()
                    },
                    onBack: usesNavigationBarChrome ? nil : onBackFromWaiting
                )
            } else {
                prepareLoadBody
            }
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(navigationTitleText)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.setSceneActive(scenePhase == .active)
            viewModel.triggerInitialLoadIfNeeded()
        }
        .onChange(of: scenePhase) { _, newPhase in
            viewModel.setSceneActive(newPhase == .active)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.screen")
    }

    private var navigationTitleText: String {
        if viewModel.state.waiting != nil {
            return ""
        }
        return PrepareStrings.text("prepare.pageTitle", locale: locale)
    }

    private var prepareLoadBody: some View {
        LoadStateView(
            state: projectedLoadState,
            locale: locale,
            onRetry: {
                viewModel.triggerRetryLoad()
            },
            content: { response in
                loadedLayout(response: response)
            },
            empty: {
                EmptyView()
            },
            customError: { kind, _ in
                errorBody(kind: kind)
            }
        )
    }

    private func errorBody(kind: LoadFailureKind) -> some View {
        let model = Self.errorPresentation(for: kind, locale: locale)
        return ScrollView {
            VStack(spacing: 16) {
                PrepareErrorCard(
                    title: model.title,
                    description: model.description,
                    backTitle: model.backTitle,
                    retryTitle: model.retryTitle,
                    identifier: kind == .notFound ? "prepare.error.notFound" : "prepare.error.load",
                    onBackToMyExams: onOpenMyExams,
                    onRetry: model.retryTitle == nil ? nil : { viewModel.triggerRetryLoad() }
                )
            }
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
            .padding(20)
        }
    }

    private func loadedLayout(response: AttemptPrepareResponse) -> some View {
        let metrics = PrepareMetrics.of(response, selection: viewModel.state.selection)
        let showsPreChoice = response.preChoice != nil && !isOpenInProgress(viewModel.state.gate)

        return GeometryReader { proxy in
            let useTwoColumn = (shellWidth == .twoColumn) && proxy.size.width >= 740 && !dynamicTypeSize.isAccessibilitySize

            if useTwoColumn {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        PrepareHeaderSection(response: response, metrics: metrics, locale: locale)

                        HStack(alignment: .top, spacing: 20) {
                            leftDetailColumn(response: response, metrics: metrics, showsPreChoice: showsPreChoice)
                                .frame(maxWidth: .infinity, alignment: .topLeading)

                            PrepareReadySection(
                                response: response,
                                metrics: metrics,
                                gate: viewModel.state.gate,
                                startState: viewModel.state.start,
                                locale: locale,
                                supportContact: supportContact,
                                showsActionsInline: true,
                                onStart: { viewModel.startAttempt() },
                                onContinue: { attemptId in onOpenAttempt(attemptId) },
                                onBuyMore: { code in onOpenCheckout(code) },
                                onBackToMyExams: onOpenMyExams
                            )
                            .frame(width: min(360, max(280, proxy.size.width * 0.36)), alignment: .topLeading)
                        }
                    }
                    .frame(maxWidth: 1120, alignment: .leading)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(20)
                }
            } else {
                let pinBottomActionBar = !dynamicTypeSize.isAccessibilitySize
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        PrepareHeaderSection(response: response, metrics: metrics, locale: locale)

                        if showsPreChoice, let preChoice = response.preChoice {
                            PreparePreChoiceSection(
                                preChoice: preChoice,
                                selection: viewModel.state.selection,
                                metrics: metrics,
                                locale: locale,
                                supportContact: supportContact,
                                onSelectOption: { viewModel.selectOption($0) },
                                onToggleSubject: { viewModel.toggleSubject($0) }
                            )
                        }

                        PrepareReadySection(
                            response: response,
                            metrics: metrics,
                            gate: viewModel.state.gate,
                            startState: viewModel.state.start,
                            locale: locale,
                            supportContact: supportContact,
                            showsActionsInline: !pinBottomActionBar,
                            onStart: { viewModel.startAttempt() },
                            onContinue: { attemptId in onOpenAttempt(attemptId) },
                            onBuyMore: { code in onOpenCheckout(code) },
                            onBackToMyExams: onOpenMyExams
                        )

                        PrepareStructureSection(response: response, locale: locale)

                        if !response.afterStartChoices.isEmpty {
                            PrepareAfterStartChoicesSection(
                                choices: response.afterStartChoices,
                                locale: locale
                            )
                        }

                        PrepareInstructionsSection(
                            instructionsHTML: response.instructions,
                            locale: locale
                        )
                    }
                    .frame(maxWidth: 640, alignment: .leading)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(16)
                }
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    if pinBottomActionBar {
                        VStack(spacing: 0) {
                            Rectangle()
                                .fill(palette.borderColor)
                                .frame(height: 1)
                            PrepareActionBlock(
                                response: response,
                                gate: viewModel.state.gate,
                                startState: viewModel.state.start,
                                locale: locale,
                                supportContact: supportContact,
                                onStart: { viewModel.startAttempt() },
                                onContinue: { attemptId in onOpenAttempt(attemptId) },
                                onBuyMore: { code in onOpenCheckout(code) }
                            )
                            .frame(maxWidth: 640)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                        .background(palette.bgSurfaceColor.opacity(0.96))
                    }
                }
            }
        }
    }

    private func leftDetailColumn(
        response: AttemptPrepareResponse,
        metrics: SelectedPathMetrics,
        showsPreChoice: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            if showsPreChoice, let preChoice = response.preChoice {
                PreparePreChoiceSection(
                    preChoice: preChoice,
                    selection: viewModel.state.selection,
                    metrics: metrics,
                    locale: locale,
                    supportContact: supportContact,
                    onSelectOption: { viewModel.selectOption($0) },
                    onToggleSubject: { viewModel.toggleSubject($0) }
                )
            }

            PrepareStructureSection(response: response, locale: locale)

            if !response.afterStartChoices.isEmpty {
                PrepareAfterStartChoicesSection(
                    choices: response.afterStartChoices,
                    locale: locale
                )
            }

            PrepareInstructionsSection(
                instructionsHTML: response.instructions,
                locale: locale
            )
        }
    }

    private func isOpenInProgress(_ gate: StartGate) -> Bool {
        if case .inProgress = gate { return true }
        return false
    }

    struct RenderedError: Equatable {
        let title: String
        let description: String
        let backTitle: String
        let retryTitle: String?
    }

    var isShowingWaiting: Bool {
        viewModel.state.waiting != nil && viewModel.state.waitingAttemptId != nil
    }

    var isShowingLoading: Bool {
        guard !isShowingWaiting else { return false }
        if case .loading = projectedLoadState { return true }
        return false
    }

    var showsStartButton: Bool {
        guard !isShowingWaiting, let response = viewModel.loadedResponse else { return false }
        return PrepareActionBlock(
            response: response,
            gate: viewModel.state.gate,
            startState: viewModel.state.start,
            locale: locale,
            supportContact: supportContact,
            onStart: {},
            onContinue: { _ in },
            onBuyMore: { _ in }
        ).rendered.showsStartButton
    }

    var showsPreChoice: Bool {
        guard !isShowingWaiting, let response = viewModel.loadedResponse else { return false }
        return response.preChoice != nil && !isOpenInProgress(viewModel.state.gate)
    }

    var renderedAction: PrepareActionPresentation? {
        guard !isShowingWaiting, let response = viewModel.loadedResponse else { return nil }
        return PrepareActionBlock(
            response: response,
            gate: viewModel.state.gate,
            startState: viewModel.state.start,
            locale: locale,
            supportContact: supportContact,
            onStart: {},
            onContinue: { _ in },
            onBuyMore: { _ in }
        ).rendered
    }

    var renderedPreChoice: PreparePreChoicePresentation? {
        guard showsPreChoice, let response = viewModel.loadedResponse, let preChoice = response.preChoice else {
            return nil
        }
        return PreparePreChoiceSection(
            preChoice: preChoice,
            selection: viewModel.state.selection,
            metrics: PrepareMetrics.of(response, selection: viewModel.state.selection),
            locale: locale,
            supportContact: supportContact,
            onSelectOption: { _ in },
            onToggleSubject: { _ in }
        ).rendered
    }

    var renderedError: RenderedError? {
        guard !isShowingWaiting, case let .failed(kind, _, _) = projectedLoadState else {
            return nil
        }
        return Self.errorPresentation(for: kind, locale: locale)
    }

    private static func errorPresentation(for kind: LoadFailureKind, locale: AppLocale) -> RenderedError {
        if kind == .notFound {
            return RenderedError(
                title: PrepareStrings.text("prepare.notFound.title", locale: locale),
                description: PrepareStrings.text("prepare.notFound.description", locale: locale),
                backTitle: PrepareStrings.text("prepare.backToMyExams", locale: locale),
                retryTitle: nil
            )
        }
        return RenderedError(
            title: AppStrings.common("prepare.errorLoad.title", locale: locale),
            description: AppStrings.common("prepare.errorLoad.description", locale: locale),
            backTitle: PrepareStrings.text("prepare.backToMyExams", locale: locale),
            retryTitle: AppStrings.common("common.retry", locale: locale)
        )
    }
}

private struct PrepareErrorCard: View {
    let title: String
    let description: String
    let backTitle: String
    let retryTitle: String?
    let identifier: String
    let onBackToMyExams: () -> Void
    let onRetry: (() -> Void)?

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(title)
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("\(identifier).title")

            Text(description)
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("\(identifier).description")

            VStack(spacing: 10) {
                if let retryTitle, let onRetry {
                    Button(action: onRetry) {
                        Text(retryTitle)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.onPrimaryColor)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .padding(.horizontal, 16)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(palette.primaryFillColor)
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("prepare.error.retryButton")
                }

                Button(action: onBackToMyExams) {
                    Text(backTitle)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(retryTitle == nil ? palette.onPrimaryColor : palette.textSecondaryColor)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .padding(.horizontal, 16)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(retryTitle == nil ? palette.primaryFillColor : palette.bgSubtleColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(retryTitle == nil ? Color.clear : palette.borderColor, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("prepare.error.backToMyExamsButton")
            }
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }
}
