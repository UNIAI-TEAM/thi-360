import Observation
import SwiftUI

@MainActor
@Observable
final class AttemptWaitingController {
    struct RestartedOutcome {
        let attemptId: String
        let state: AttemptOpenState
        let context: StartContext?
    }

    let attemptId: String
    let origin: WaitingOrigin
    private(set) var phase: WaitingPhase
    private(set) var lastAbortReason: String?
    private(set) var resolvedContext: StartContext?

    @ObservationIgnored private let api: any PrepareAPIClientProtocol
    @ObservationIgnored private let startContextStore: StartContextStore
    @ObservationIgnored private let waitingRetry: WaitingRetry
    @ObservationIgnored private let clock: any PrepareClock
    @ObservationIgnored private var context: StartContext?
    @ObservationIgnored private var onReady: (String) -> Void
    @ObservationIgnored private var onRestarted: (String) -> Void
    @ObservationIgnored private var onOpenPrepare: (String) -> Void
    @ObservationIgnored private var onMyExams: () -> Void
    @ObservationIgnored private var poller: WaitingPoller?
    @ObservationIgnored private var retryTask: Task<Void, Never>?
    @ObservationIgnored private var contextLoadTask: Task<Void, Never>?
    @ObservationIgnored private var started = false
    @ObservationIgnored private var hasAttemptedContextLoad = false
    @ObservationIgnored private var isForeground = true
    @ObservationIgnored private(set) var lastRestartedOutcome: RestartedOutcome?

    init(
        attemptId: String,
        context: StartContext?,
        origin: WaitingOrigin,
        initialPhase: WaitingPhase = .polling(elapsed: 0),
        api: any PrepareAPIClientProtocol,
        startContextStore: StartContextStore,
        clock: any PrepareClock = SystemPrepareClock(),
        onReady: @escaping (String) -> Void,
        onRestarted: @escaping (String) -> Void,
        onOpenPrepare: @escaping (String) -> Void,
        onMyExams: @escaping () -> Void
    ) {
        self.attemptId = attemptId
        self.context = context
        self.resolvedContext = context
        self.origin = origin
        self.phase = initialPhase
        if case let .failed(reason) = initialPhase {
            self.lastAbortReason = reason
        }
        self.api = api
        self.startContextStore = startContextStore
        self.waitingRetry = WaitingRetry(
            startCommand: StartCommand(api: api),
            startContextStore: startContextStore
        )
        self.clock = clock
        self.onReady = onReady
        self.onRestarted = onRestarted
        self.onOpenPrepare = onOpenPrepare
        self.onMyExams = onMyExams
    }

    func updateBindings(
        context: StartContext?,
        onReady: @escaping (String) -> Void,
        onRestarted: @escaping (String) -> Void,
        onOpenPrepare: @escaping (String) -> Void,
        onMyExams: @escaping () -> Void
    ) {
        self.context = context
        if let context {
            resolvedContext = context
        }
        self.onReady = onReady
        self.onRestarted = onRestarted
        self.onOpenPrepare = onOpenPrepare
        self.onMyExams = onMyExams
        loadPersistedContextIfNeeded()
    }

    func startIfNeeded(isForeground: Bool) {
        self.isForeground = isForeground
        loadPersistedContextIfNeeded()
        guard !started else {
            poller?.setForeground(isForeground)
            return
        }
        started = true
        switch phase {
        case let .polling(elapsed):
            startPoller(initialElapsed: elapsed)
        case .busy:
            startPoller(initialElapsed: WaitingPoller.busyThresholdSeconds)
        case .failed, .retrying, .retryRefused:
            break
        }
    }

    func setForeground(_ foreground: Bool) {
        isForeground = foreground
        poller?.setForeground(foreground)
    }

    func stop() {
        poller?.stop()
        poller = nil
        retryTask?.cancel()
        retryTask = nil
        contextLoadTask?.cancel()
        contextLoadTask = nil
    }

    func handleBack(_ action: () -> Void) {
        stop()
        action()
    }

    func retry() {
        guard retryTask == nil, phase != .retrying else { return }
        phase = .retrying
        retryTask = Task { [weak self] in
            await self?.executeRetry()
            self?.retryTask = nil
        }
    }

    private func loadPersistedContextIfNeeded() {
        guard context == nil, resolvedContext == nil, !hasAttemptedContextLoad else { return }
        hasAttemptedContextLoad = true
        contextLoadTask?.cancel()
        contextLoadTask = Task { [weak self] in
            guard let self else { return }
            let stored = await self.startContextStore.read(attemptId: self.attemptId)
            guard !Task.isCancelled else { return }
            if self.context == nil, let stored {
                self.resolvedContext = stored
            }
        }
    }

    private func executeRetry() async {
        let activeContext = context ?? resolvedContext
        let outcome = await waitingRetry.retry(
            attemptId: attemptId,
            origin: origin,
            entitlementId: activeContext?.entitlementId,
            inMemoryContext: activeContext
        )
        switch outcome {
        case .alreadyInFlight:
            break

        case let .restarted(newAttemptId, openedState, _, movedContext):
            lastRestartedOutcome = RestartedOutcome(
                attemptId: newAttemptId,
                state: openedState,
                context: movedContext ?? activeContext
            )
            poller?.stop()
            poller = nil
            onRestarted(newAttemptId)

        case let .refused(refusal):
            phase = .retryRefused(refusal)

        case let .openPrepare(entitlementId):
            stop()
            onOpenPrepare(entitlementId)

        case .reloadPrepare:
            stop()
            if let entitlementId = activeContext?.entitlementId {
                onOpenPrepare(entitlementId)
            } else {
                onMyExams()
            }

        case .goToMyExams:
            stop()
            onMyExams()
        }
    }

    private func startPoller(initialElapsed: TimeInterval) {
        poller?.stop()
        let nextPoller = WaitingPoller(
            attemptId: attemptId,
            clock: clock,
            api: api,
            initialElapsedSeconds: initialElapsed,
            onReady: { [weak self] readyId in
                self?.stop()
                self?.onReady(readyId)
            },
            onAborted: { [weak self] reason in
                self?.lastAbortReason = reason
                self?.phase = .failed(abortReason: reason)
            },
            onPhaseChanged: { [weak self] next in
                guard let self else { return }
                switch self.phase {
                case .retrying, .retryRefused:
                    return
                default:
                    self.phase = next
                }
            }
        )
        poller = nextPoller
        nextPoller.start(initiallyForeground: isForeground)
    }
}

struct AttemptWaitingView: View {
    let attemptId: String
    let examTitle: String
    let context: StartContext?
    let origin: WaitingOrigin
    let onReady: (String) -> Void
    let onRestarted: (String) -> Void
    let onOpenPrepare: (String) -> Void
    let onMyExams: () -> Void

    let initialPhase: WaitingPhase
    let controlledPhase: WaitingPhase?
    let controlledAbortReason: String?
    let timing: String?
    let localeOverride: AppLocale?
    let supportContact: SupportContact
    let showsTopBar: Bool
    let apiOverride: (any PrepareAPIClientProtocol)?
    let startContextStoreOverride: StartContextStore?
    let clock: any PrepareClock
    let onRetryOverride: (() -> Void)?
    let onBack: (() -> Void)?
    let onControllerChanged: ((AttemptWaitingController) -> Void)?

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.locale) private var envLocale
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    @State private var controller: AttemptWaitingController?

    init(
        attemptId: String,
        examTitle: String,
        context: StartContext?,
        origin: WaitingOrigin,
        onReady: @escaping (String) -> Void,
        onRestarted: @escaping (String) -> Void,
        onOpenPrepare: @escaping (String) -> Void,
        onMyExams: @escaping () -> Void,
        initialPhase: WaitingPhase = .polling(elapsed: 0),
        controlledPhase: WaitingPhase? = nil,
        controlledAbortReason: String? = nil,
        timing: String? = nil,
        locale: AppLocale? = nil,
        supportContact: SupportContact = AppEnvironment.current.supportContact,
        showsTopBar: Bool = true,
        api: (any PrepareAPIClientProtocol)? = nil,
        startContextStore: StartContextStore? = nil,
        clock: any PrepareClock = SystemPrepareClock(),
        onRetryOverride: (() -> Void)? = nil,
        onBack: (() -> Void)? = nil,
        onControllerChanged: ((AttemptWaitingController) -> Void)? = nil
    ) {
        self.attemptId = attemptId
        self.examTitle = examTitle
        self.context = context
        self.origin = origin
        self.onReady = onReady
        self.onRestarted = onRestarted
        self.onOpenPrepare = onOpenPrepare
        self.onMyExams = onMyExams
        self.initialPhase = initialPhase
        self.controlledPhase = controlledPhase
        self.controlledAbortReason = controlledAbortReason
        self.timing = timing
        self.localeOverride = locale
        self.supportContact = supportContact
        self.showsTopBar = showsTopBar
        self.apiOverride = api
        self.startContextStoreOverride = startContextStore
        self.clock = clock
        self.onRetryOverride = onRetryOverride
        self.onBack = onBack
        self.onControllerChanged = onControllerChanged
    }

    private var activeLocale: AppLocale {
        if let localeOverride { return localeOverride }
        let id = envLocale.identifier.lowercased()
        return id.hasPrefix("en") ? .en : .vi
    }

    private var effectivePhase: WaitingPhase {
        controlledPhase ?? controller?.phase ?? initialPhase
    }

    private var effectiveAbortReason: String? {
        if case let .failed(reason) = effectivePhase {
            return reason
        }
        return controlledAbortReason ?? controller?.lastAbortReason
    }

    private var effectiveContext: StartContext? {
        context ?? controller?.resolvedContext
    }

    private var resolvedTitle: String {
        let trimmed = examTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
        if let ctxTitle = effectiveContext?.examTitle.trimmingCharacters(in: .whitespacesAndNewlines), !ctxTitle.isEmpty {
            return ctxTitle
        }
        return AppStrings.common("attempt.waiting.title", locale: activeLocale)
    }

    private var resolvedTiming: String? {
        timing ?? effectiveContext?.timing
    }

    var body: some View {
        VStack(spacing: 0) {
            if showsTopBar {
                topBar
            }

            ScrollView {
                VStack(spacing: 20) {
                    if !showsTopBar && dynamicTypeSize.isAccessibilitySize {
                        clockPausedBadge
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    switch effectivePhase {
                    case .polling:
                        pollingContent
                    case .busy:
                        busyContent
                    case .failed, .retrying, .retryRefused:
                        failedContent
                    }
                }
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
            .background(palette.bgPageColor.ignoresSafeArea())
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("waiting.screen")
        .onAppear {
            if controlledPhase == nil {
                ensureController()
                controller?.startIfNeeded(isForeground: scenePhase == .active)
            }
        }
        .onChange(of: attemptId) { _, _ in
            guard controlledPhase == nil else { return }
            rebindControllerForAttemptChange()
        }
        .onChange(of: context) { _, newContext in
            guard controlledPhase == nil else { return }
            controller?.updateBindings(
                context: newContext,
                onReady: onReady,
                onRestarted: onRestarted,
                onOpenPrepare: onOpenPrepare,
                onMyExams: onMyExams
            )
        }
        .onChange(of: scenePhase) { _, newPhase in
            controller?.setForeground(newPhase == .active)
        }
        .onDisappear {
            controller?.stop()
        }
    }

    private var topBar: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 10) {
                if let onBack {
                    backButton(action: onBack)
                }
                Text(resolvedTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("waiting.topBar.title")

                clockPausedBadge
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    if let onBack {
                        backButton(action: onBack)
                    }
                    Text(resolvedTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("waiting.topBar.title")
                }
                clockPausedBadge
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(palette.bgSurfaceColor)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(palette.borderColor)
                .frame(height: 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("waiting.topBar")
    }

    private func backButton(action: @escaping () -> Void) -> some View {
        Button {
            if let controller {
                controller.handleBack(action)
            } else {
                action()
            }
        } label: {
            Image(systemName: "chevron.backward")
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(PrepareStrings.text("attempt.waiting.backToMyExams", locale: activeLocale))
        .accessibilityIdentifier("waiting.backButton")
    }

    var clockPausedBadge: some View {
        HStack(spacing: 6) {
            Image(systemName: "clock")
                .font(.footnote.weight(.semibold))
                .accessibilityHidden(true)
            Text(PrepareStrings.text("attempt.waiting.clockPaused", locale: activeLocale))
                .font(.footnote.weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(palette.textSecondaryColor)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            Capsule(style: .continuous)
                .fill(palette.bgSubtleColor)
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(palette.borderStrongColor, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("waiting.clockPaused")
    }

    private var pollingContent: some View {
        let model = rendered
        return VStack(spacing: 20) {
            VStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(palette.primaryContainerColor)
                        .frame(width: 64, height: 64)
                    ProgressView()
                        .tint(palette.primaryTextColor)
                        .scaleEffect(1.2)
                }
                .accessibilityHidden(true)

                Text(model.pollingTitle)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("waiting.title")

                Text(model.pollingDescription)
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("waiting.description")

                ProgressView()
                    .progressViewStyle(.linear)
                    .tint(palette.primaryFillColor)
                    .frame(maxWidth: 200)
                    .accessibilityHidden(true)
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(cardBackground)

            metadataCard
            reassureNoteCard
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("waiting.polling")
    }

    private var busyContent: some View {
        let model = rendered
        return VStack(spacing: 20) {
            VStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(statusPalette.warningBgColor)
                        .frame(width: 64, height: 64)
                    Image(systemName: "clock.badge.exclamationmark")
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(statusPalette.warningColor)
                }
                .accessibilityHidden(true)

                Text(model.busyNotice ?? "")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(statusPalette.warningColor)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(statusPalette.warningBgColor)
                    )
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("waiting.busyNotice")

                Text(model.busyTitle ?? "")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("waiting.busyTitle")

                Text(model.busyDescription ?? "")
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("waiting.busyDescription")
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(cardBackground)

            metadataCard
            reassureNoteCard
            backToMyExamsButton
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("waiting.busy")
    }

    private var failedContent: some View {
        let model = rendered
        let isRetrying = (model.phase == .retrying)

        return VStack(spacing: 20) {
            VStack(spacing: 14) {
                Text(model.failedNotice ?? "")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.dangerTextColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("waiting.failedNotice")

                ZStack {
                    Circle()
                        .fill(palette.dangerBgColor)
                        .frame(width: 60, height: 60)
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 26, weight: .semibold))
                        .foregroundStyle(palette.dangerTextColor)
                }
                .accessibilityHidden(true)

                Text(model.failedHeading ?? "")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("waiting.failedHeading")

                VStack(alignment: .leading, spacing: 6) {
                    Text(model.failedReason ?? "")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("waiting.failedReason")

                    Text(model.failedRefundNote ?? "")
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("waiting.failedRefundNote")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.dangerBgColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(palette.dangerTextColor.opacity(0.45), lineWidth: 1)
                )

                VStack(spacing: 10) {
                    Button {
                        if let onRetryOverride {
                            onRetryOverride()
                        } else {
                            controller?.retry()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            if isRetrying {
                                ProgressView()
                                    .tint(palette.onPrimaryColor)
                            } else {
                                Image(systemName: "arrow.clockwise")
                                    .font(.body.weight(.semibold))
                                    .accessibilityHidden(true)
                            }
                            Text(model.retryTitle ?? "")
                                .font(.body.weight(.semibold))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .foregroundStyle(palette.onPrimaryColor)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.primaryFillColor)
                                .opacity(isRetrying ? 0.65 : 1.0)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(isRetrying)
                    .accessibilityIdentifier("waiting.retryButton")

                    backToMyExamsButton
                }

                if let errorDisplay = model.retryError {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(errorDisplay.primary)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.dangerTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                        if let supportLine = errorDisplay.supportLine {
                            Text(supportLine)
                                .font(.footnote)
                                .foregroundStyle(palette.textSecondaryColor)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.dangerBgColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.dangerTextColor.opacity(0.45), lineWidth: 1)
                    )
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("waiting.retryError")
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(cardBackground)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("waiting.failed")
    }

    @ViewBuilder
    private var metadataCard: some View {
        let model = rendered
        if model.firstPartName != nil || model.durationValue != nil {
            VStack(spacing: 0) {
                if let label = model.firstPartLabel, let partName = model.firstPartName {
                    metadataRow(
                        label: label,
                        value: partName,
                        identifier: "waiting.metadata.firstPart"
                    )
                }
                if model.firstPartName != nil && model.durationValue != nil {
                    Divider()
                        .overlay(palette.borderColor)
                }
                if let label = model.durationLabel, let value = model.durationValue {
                    metadataRow(
                        label: label,
                        value: value,
                        identifier: "waiting.metadata.duration"
                    )
                }
            }
            .background(cardBackground)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("waiting.metadata")
        }
    }

    private func metadataRow(label: String, value: String, identifier: String) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Spacer(minLength: 8)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.trailing)
                    .monospacedDigit()
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .monospacedDigit()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
    }

    private var reassureNoteCard: some View {
        let parts = PrepareStrings.reassureNoteParts(locale: activeLocale)
        let fullSentence = parts.prefix + parts.linkText + parts.suffix
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "checkmark.shield.fill")
                    .font(.body)
                    .foregroundStyle(palette.primaryTextColor)
                    .accessibilityHidden(true)

                Text(fullSentence)
                    .font(.footnote)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("waiting.reassureNote")
            }

            if !parts.linkText.isEmpty {
                Button {
                    controller?.stop()
                    onMyExams()
                } label: {
                    HStack(spacing: 4) {
                        Text(parts.linkText)
                            .font(.footnote.weight(.semibold))
                            .underline()
                        Image(systemName: "arrow.right")
                            .font(.caption.weight(.semibold))
                            .accessibilityHidden(true)
                    }
                    .foregroundStyle(palette.primaryTextColor)
                    .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("waiting.reassureMyExamsLink")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(cardBackground)
    }

    private var backToMyExamsButton: some View {
        Button {
            controller?.stop()
            onMyExams()
        } label: {
            Text(PrepareStrings.text("attempt.waiting.backToMyExams", locale: activeLocale))
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.textSecondaryColor)
                .frame(maxWidth: .infinity, minHeight: 48)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(palette.borderColor, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("waiting.backToMyExamsButton")
    }

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(palette.bgSurfaceColor)
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
    }

    struct RenderedPresentation: Equatable {
        let topBarTitle: String
        let clockPausedLabel: String
        let phase: WaitingPhase
        let pollingTitle: String
        let pollingDescription: String
        let firstPartLabel: String?
        let firstPartName: String?
        let durationLabel: String?
        let durationValue: String?
        let reassureFullText: String
        let reassureLinkText: String
        let busyNotice: String?
        let busyTitle: String?
        let busyDescription: String?
        let failedNotice: String?
        let failedHeading: String?
        let failedReason: String?
        let failedRefundNote: String?
        let retryTitle: String?
        let backToMyExamsTitle: String?
        let retryError: StartErrorDisplay?
    }

    var rendered: RenderedPresentation {
        let activeContext = effectiveContext
        let partName = activeContext?.firstPartName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let hasPart = partName?.isEmpty == false
        let minutes = activeContext?.firstPartMinutes
        let durationKey = resolvedTiming == "SHARED"
            ? "attempt.waiting.durationSharedLabel"
            : "attempt.waiting.durationLabel"
        let parts = PrepareStrings.reassureNoteParts(locale: activeLocale)
        let fullReassure = parts.prefix + parts.linkText + parts.suffix
        let phase = effectivePhase

        var busyNotice: String?
        var busyTitle: String?
        var busyDescription: String?
        var failedNotice: String?
        var failedHeading: String?
        var failedReason: String?
        var failedRefundNote: String?
        var retryTitle: String?
        var backTitle: String?
        var retryError: StartErrorDisplay?

        switch phase {
        case .polling:
            break
        case .busy:
            busyNotice = PrepareStrings.text("attempt.waiting.busyNoticeLabel", locale: activeLocale)
            busyTitle = PrepareStrings.text("attempt.waiting.busyTitle", locale: activeLocale)
            busyDescription = PrepareStrings.text("attempt.waiting.busyDescription", locale: activeLocale)
            backTitle = PrepareStrings.text("attempt.waiting.backToMyExams", locale: activeLocale)
        case .failed, .retrying, .retryRefused:
            failedNotice = PrepareStrings.text("attempt.waiting.failedNoticeLabel", locale: activeLocale)
            failedHeading = PrepareStrings.text("attempt.waiting.failedHeading", locale: activeLocale)
            failedReason = PrepareStrings.abortReasonMessage(for: effectiveAbortReason, locale: activeLocale)
            failedRefundNote = PrepareStrings.text("attempt.waiting.failedRefundNote", locale: activeLocale)
            retryTitle = PrepareStrings.text("attempt.waiting.retry", locale: activeLocale)
            backTitle = PrepareStrings.text("attempt.waiting.backToMyExams", locale: activeLocale)
            if case let .retryRefused(refusal) = phase {
                retryError = StartErrorMessage.display(
                    for: refusal,
                    locale: activeLocale,
                    supportContact: supportContact
                )
            }
        }

        return RenderedPresentation(
            topBarTitle: resolvedTitle,
            clockPausedLabel: PrepareStrings.text("attempt.waiting.clockPaused", locale: activeLocale),
            phase: phase,
            pollingTitle: AppStrings.common("attempt.waiting.title", locale: activeLocale),
            pollingDescription: PrepareStrings.text("attempt.waiting.description", locale: activeLocale),
            firstPartLabel: hasPart ? PrepareStrings.text("attempt.waiting.firstPartLabel", locale: activeLocale) : nil,
            firstPartName: hasPart ? partName : nil,
            durationLabel: minutes == nil ? nil : PrepareStrings.text(durationKey, locale: activeLocale),
            durationValue: minutes.map {
                PrepareStrings.text("attempt.waiting.minutesValue", locale: activeLocale, replacements: ["minutes": String($0)])
            },
            reassureFullText: fullReassure,
            reassureLinkText: parts.linkText,
            busyNotice: busyNotice,
            busyTitle: busyTitle,
            busyDescription: busyDescription,
            failedNotice: failedNotice,
            failedHeading: failedHeading,
            failedReason: failedReason,
            failedRefundNote: failedRefundNote,
            retryTitle: retryTitle,
            backToMyExamsTitle: backTitle,
            retryError: retryError
        )
    }

    private func rebindControllerForAttemptChange() {
        let restarted = controller?.lastRestartedOutcome
        let matchedRestart = restarted?.attemptId == attemptId ? restarted : nil
        ensureController(restartedOutcome: matchedRestart)
        if let matchedRestart,
           matchedRestart.state == .inProgress || matchedRestart.state == .closed
        {
            controller?.stop()
            onReady(attemptId)
            return
        }
        controller?.startIfNeeded(isForeground: scenePhase == .active)
    }

    private func ensureController(restartedOutcome: AttemptWaitingController.RestartedOutcome? = nil) {
        if let existing = controller, existing.attemptId == attemptId {
            existing.updateBindings(
                context: context,
                onReady: onReady,
                onRestarted: onRestarted,
                onOpenPrepare: onOpenPrepare,
                onMyExams: onMyExams
            )
            return
        }
        controller?.stop()
        let api: any PrepareAPIClientProtocol = apiOverride
            ?? sessionStore.map { PrepareAPI(apiClient: $0.apiClient) }
            ?? PreviewEmptyPrepareAPI()
        let store: StartContextStore
        if let startContextStoreOverride {
            store = startContextStoreOverride
        } else {
            let storeProvider = sessionStore?.storeProvider
            let tokenStore = sessionStore?.tokenStore
            store = StartContextStore(storeResolver: {
                guard let storeProvider, let tokenStore,
                      let accountId = (try? tokenStore.read())?.accountId
                else {
                    return nil
                }
                return storeProvider.store(for: accountId)
            })
        }

        let resolvedInitialPhase: WaitingPhase
        let resolvedInitialContext: StartContext?
        if let restartedOutcome {
            switch restartedOutcome.state {
            case .starting, .inProgress, .closed:
                resolvedInitialPhase = .polling(elapsed: 0)
            case let .aborted(reason):
                resolvedInitialPhase = .failed(abortReason: reason)
            }
            resolvedInitialContext = context ?? restartedOutcome.context
        } else {
            resolvedInitialPhase = initialPhase
            resolvedInitialContext = context
        }

        let created = AttemptWaitingController(
            attemptId: attemptId,
            context: resolvedInitialContext,
            origin: origin,
            initialPhase: resolvedInitialPhase,
            api: api,
            startContextStore: store,
            clock: clock,
            onReady: onReady,
            onRestarted: onRestarted,
            onOpenPrepare: onOpenPrepare,
            onMyExams: onMyExams
        )
        controller = created
        onControllerChanged?(created)
    }
}
