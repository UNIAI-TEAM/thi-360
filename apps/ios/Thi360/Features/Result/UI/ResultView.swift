import SwiftUI

struct ResultView: View {
    @Bindable var viewModel: ResultViewModel
    let locale: AppLocale
    let onOpenReview: (String) -> Void
    let onContinueAttempt: (String) -> Void
    let onGoMyExams: () -> Void
    let onGoHistory: () -> Void
    var autoLoadOnAppear: Bool = true

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            switch viewModel.loadState {
            case .loading:
                loadingSkeleton
            case let .failed(kind: .server, canRetry: canRetry, retryAfter: _):
                genericErrorView(canRetry: canRetry)
            default:
                LoadStateView(
                    state: viewModel.loadState,
                    locale: locale,
                    staleMessage: ResultStrings.text("result.errorTitle", locale: locale),
                    onRetry: { viewModel.retry() },
                    content: { result in
                        loadedScrollContent(result)
                    },
                    empty: {
                        EmptyView()
                    },
                    customError: { kind, _ in
                        customErrorView(kind: kind)
                    }
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(ResultStrings.text("result.pageTitle", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("route.result")
        .onAppear {
            if autoLoadOnAppear {
                viewModel.onAppear(isSceneActive: scenePhase == .active)
            } else {
                viewModel.resetNavigationGuard()
            }
        }
        .onDisappear {
            if autoLoadOnAppear {
                viewModel.onDisappear()
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            viewModel.handleSceneActiveChanged(newPhase == .active)
        }
    }

    private var loadingSkeleton: some View {
        let label = ResultStrings.text("result.loading", locale: locale)
        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 220, height: 28)
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
                    .frame(height: 220)
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
                    .frame(height: 180)
            }
            .padding(16)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityIdentifier("result.loading")
        .resultProbe(id: "result.loading", label: label)
    }

    @ViewBuilder
    private func customErrorView(kind: LoadFailureKind) -> some View {
        switch kind {
        case .notFound:
            notFoundView
        case let .conflict(code) where code == "ATTEMPT_NOT_FINISHED":
            notFinishedView
        default:
            genericErrorView(canRetry: true)
        }
    }

    private var notFoundView: some View {
        let eyebrow = ResultStrings.text("result.notFound.eyebrow", locale: locale)
        let title = ResultStrings.text("result.notFound.title", locale: locale)
        let desc = ResultStrings.text("result.notFound.description", locale: locale)
        let toHistory = ResultStrings.text("result.notFound.toHistory", locale: locale)
        let toHome = ResultStrings.text("result.notFound.toHome", locale: locale)

        return ScrollView {
            VStack(spacing: 14) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(palette.textMutedColor)
                    .accessibilityHidden(true)

                Text(eyebrow)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(palette.dangerTextColor)
                    .accessibilityIdentifier("result.notFound.eyebrow")
                    .resultProbe(id: "result.notFound.eyebrow", label: eyebrow)

                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .resultProbe(id: "result.notFound.title", label: title)

                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .resultProbe(id: "result.notFound.description", label: desc)

                VStack(spacing: 10) {
                    primaryButton(
                        title: toHistory,
                        identifier: "result.notFound.toHistory",
                        action: { viewModel.performNavigationOnce(onGoHistory) }
                    )
                    secondaryButton(
                        title: toHome,
                        identifier: "result.notFound.toHome",
                        action: { viewModel.performNavigationOnce(onGoMyExams) }
                    )
                }
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.bgSurfaceColor))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
            .padding(20)
        }
        .accessibilityIdentifier("result.state.notFound")
    }

    private var notFinishedView: some View {
        let title = ResultStrings.text("result.notFinished.title", locale: locale)
        let desc = ResultStrings.text("result.notFinished.description", locale: locale)
        let continueTitle = ResultStrings.text("result.notFinished.continueAttempt", locale: locale)
        let toHistory = ResultStrings.text("result.notFinished.toHistory", locale: locale)

        return ScrollView {
            VStack(spacing: 14) {
                Image(systemName: "clock")
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(statusPalette.warningColor)
                    .accessibilityHidden(true)

                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .resultProbe(id: "result.notFinished.title", label: title)

                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .resultProbe(id: "result.notFinished.description", label: desc)

                VStack(spacing: 10) {
                    primaryButton(
                        title: continueTitle,
                        identifier: "result.notFinished.continueAttempt",
                        action: { viewModel.performNavigationOnce { onContinueAttempt(viewModel.attemptId) } }
                    )
                    secondaryButton(
                        title: toHistory,
                        identifier: "result.notFinished.toHistory",
                        action: { viewModel.performNavigationOnce(onGoHistory) }
                    )
                }
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.bgSurfaceColor))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
            .padding(20)
        }
        .accessibilityIdentifier("result.state.notFinished")
    }

    private func genericErrorView(canRetry: Bool) -> some View {
        let title = ResultStrings.text("result.errorTitle", locale: locale)
        let desc = ResultStrings.text("result.errorDescription", locale: locale)
        let retryTitle = AppStrings.common("common.retry", locale: locale)

        return ScrollView {
            VStack(spacing: 14) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(palette.dangerTextColor)
                    .accessibilityHidden(true)

                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .resultProbe(id: "result.error.title", label: title)

                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .resultProbe(id: "result.error.description", label: desc)

                if canRetry {
                    primaryButton(
                        title: retryTitle,
                        identifier: "result.error.retryButton",
                        action: { viewModel.retry() }
                    )
                    .padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.bgSurfaceColor))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
            .padding(20)
        }
        .accessibilityIdentifier("result.state.error")
    }

    private func loadedScrollContent(_ result: AttemptResult) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                headerBlock(result)

                if result.showsSystemFaultBanner {
                    faultBanner(result)
                }

                switch result.state {
                case .grading:
                    gradingCard(result)
                case .gradingError:
                    gradingErrorCard
                case .scored, .pendingReview:
                    totalScoreCard(result)
                }

                if result.isVact, !result.vactComponents.isEmpty {
                    ResultVactComponentsSection(
                        components: result.vactComponents,
                        subjectOptions: viewModel.subjectOptions,
                        locale: locale
                    )
                }

                attemptInfoCard(result)

                if !result.parts.isEmpty {
                    ResultPartsBreakdownSection(
                        parts: result.parts,
                        endReason: result.endReason,
                        hasPendingAttempt: result.isPendingUnregraded,
                        locale: locale
                    )
                }

                if !result.segments.isEmpty {
                    ResultSegmentsBreakdownSection(
                        segments: result.segments,
                        hasPendingAttempt: result.isPendingUnregraded,
                        locale: locale
                    )
                }

                if !result.isVact, !result.subjects.isEmpty {
                    ResultSubjectsBreakdownSection(
                        subjects: result.subjects,
                        subjectOptions: viewModel.subjectOptions,
                        hasPendingAttempt: result.isPendingUnregraded,
                        locale: locale
                    )
                }
            }
            .frame(maxWidth: 920, alignment: .leading)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .accessibilityIdentifier("result.loaded.scroll")
    }

    private func headerBlock(_ result: AttemptResult) -> some View {
        let eyebrow = ResultStrings.text("result.eyebrow", locale: locale)
        let statusText = ResultStrings.text(result.statusStateKey, locale: locale)
        let statusTone = ResultFormat.resolveStatusTone(state: result.state, regrading: result.regrading)
        let examKindLabel = result.examKindCode.map {
            ResultFormat.resolveCatalogLabel(code: $0, options: viewModel.examOptions, locale: locale)
        }
        let scopeLabel = result.scope.map { ResultFormat.resolveScopeLabel($0, locale: locale) }
        let attemptNoLabel = result.attemptNo.map {
            ResultStrings.text("result.attemptNo", locale: locale, replacements: ["count": String($0)])
        }

        return VStack(alignment: .leading, spacing: 8) {
            Text(eyebrow)
                .font(.caption.weight(.bold))
                .foregroundStyle(statusPalette.warningColor)
                .textCase(.uppercase)
                .resultProbe(id: "result.header.eyebrow", label: eyebrow)

            Text(result.examName)
                .font(.title2.weight(.bold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("result.header.examName")
                .resultProbe(id: "result.header.examName", label: result.examName)

            WrappingHStackLayout(horizontalSpacing: 8, verticalSpacing: 6) {
                if let examKindLabel, !examKindLabel.isEmpty {
                    metadataPill(examKindLabel, id: "result.header.examKind")
                }
                if let scopeLabel {
                    metadataPill(scopeLabel, id: "result.header.scope")
                }
                if let attemptNoLabel {
                    metadataPill(attemptNoLabel, id: "result.header.attemptNo")
                }
                ResultStatusChip(title: statusText, tone: statusTone, symbolName: statusSymbol(for: result))
                    .accessibilityIdentifier("result.header.statusChip")
                    .resultProbe(id: "result.header.statusChip", label: statusText)
            }
        }
    }

    private func statusSymbol(for result: AttemptResult) -> String {
        if result.regrading { return "arrow.triangle.2.circlepath" }
        switch result.state {
        case .scored: return "checkmark"
        case .pendingReview: return "clock"
        case .grading: return "hourglass"
        case .gradingError: return "exclamationmark.triangle"
        }
    }

    private func metadataPill(_ text: String, id: String) -> some View {
        Text(text)
            .font(.caption.weight(.medium))
            .foregroundStyle(palette.textSecondaryColor)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Capsule().fill(palette.bgSubtleColor))
            .accessibilityIdentifier(id)
            .resultProbe(id: id, label: text)
    }

    private func faultBanner(_ result: AttemptResult) -> some View {
        let bannerText = ResultStrings.text(
            result.compensated ? "result.fault.compensatedBanner" : "result.fault.faultNoticeBanner",
            locale: locale
        )
        let reasonText = result.faultReason.map {
            "\(ResultStrings.text("result.fault.reasonLabel", locale: locale)) \($0)"
        }
        let fg = result.compensated ? statusPalette.infoColor : statusPalette.warningColor
        let bg = result.compensated ? statusPalette.infoBgColor : statusPalette.warningBgColor
        let symbol = result.compensated ? "info.circle.fill" : "exclamationmark.triangle.fill"

        return HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol)
                .font(.body.weight(.semibold))
                .foregroundStyle(fg)
                .padding(.top, 2)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(bannerText)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(fg)
                    .fixedSize(horizontal: false, vertical: true)

                if let reasonText {
                    Text(reasonText)
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(bg))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(fg, lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("result.fault.banner")
        .resultProbe(
            id: "result.fault.banner",
            label: [bannerText, reasonText].compactMap { $0 }.joined(separator: " | ")
        )
    }

    private func gradingCard(_ result: AttemptResult) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if viewModel.isGradingTimedOut {
                let heading = ResultStrings.text("result.grading.timeoutHeading", locale: locale)
                let desc = ResultStrings.text("result.grading.timeoutDescription", locale: locale)
                let reloadTitle = ResultStrings.text("result.grading.reload", locale: locale)
                let historyTitle = ResultStrings.text("result.backToHistory", locale: locale)

                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "clock.fill")
                        .font(.title2)
                        .foregroundStyle(statusPalette.infoColor)
                        .frame(width: 44, height: 44)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(statusPalette.infoBgColor))
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(heading)
                            .font(.headline.weight(.bold))
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                            .resultProbe(id: "result.grading.timeoutHeading", label: heading)

                        Text(desc)
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .resultProbe(id: "result.grading.timeoutDescription", label: desc)
                    }
                }

                VStack(spacing: 10) {
                    Button {
                        viewModel.reloadFromGradingTimeout()
                    } label: {
                        HStack(spacing: 8) {
                            if viewModel.isReloadingFromTimeout {
                                ProgressView()
                                    .tint(palette.textColor)
                            } else {
                                Image(systemName: "arrow.clockwise")
                                    .accessibilityHidden(true)
                            }
                            Text(reloadTitle)
                                .font(.body.weight(.semibold))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .foregroundStyle(palette.textColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .padding(.horizontal, 16)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.bgSubtleColor))
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.borderStrongColor, lineWidth: 1))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.isReloadingFromTimeout)
                    .accessibilityIdentifier("result.grading.reloadButton")
                    .resultProbe(
                        id: "result.grading.reloadButton",
                        label: reloadTitle,
                        value: viewModel.isReloadingFromTimeout ? "loading" : "idle",
                        isButton: true,
                        isEnabled: !viewModel.isReloadingFromTimeout,
                        action: { viewModel.reloadFromGradingTimeout() }
                    )

                    secondaryButton(
                        title: historyTitle,
                        identifier: "result.grading.toHistoryButton",
                        action: { viewModel.performNavigationOnce(onGoHistory) }
                    )
                }
            } else {
                let heading = ResultStrings.text("result.grading.heading", locale: locale)
                let desc = ResultStrings.text("result.grading.description", locale: locale)

                HStack(alignment: .top, spacing: 12) {
                    ProgressView()
                        .tint(palette.primaryTextColor)
                        .frame(width: 36, height: 36)
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(heading)
                            .font(.headline.weight(.bold))
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                            .resultProbe(id: "result.grading.heading", label: heading)

                        Text(desc)
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .resultProbe(id: "result.grading.description", label: desc)
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.bgSurfaceColor))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("result.grading.card")
    }

    private var gradingErrorCard: some View {
        let heading = ResultStrings.text("result.gradingError.heading", locale: locale)
        let message = ResultStrings.text("result.gradingError.message", locale: locale)
        let historyTitle = ResultStrings.text("result.backToHistory", locale: locale)
        let myExamsTitle = ResultStrings.text("result.actions.myExams", locale: locale)

        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "exclamationmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(statusPalette.warningColor)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    Text(heading)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .resultProbe(id: "result.gradingError.heading", label: heading)

                    Text(message)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .resultProbe(id: "result.gradingError.message", label: message)
                }
            }

            VStack(spacing: 10) {
                secondaryButton(
                    title: historyTitle,
                    identifier: "result.gradingError.toHistoryButton",
                    action: { viewModel.performNavigationOnce(onGoHistory) }
                )
                secondaryButton(
                    title: myExamsTitle,
                    identifier: "result.gradingError.toMyExamsButton",
                    action: { viewModel.performNavigationOnce(onGoMyExams) }
                )
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(statusPalette.warningBgColor))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(statusPalette.warningColor, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("result.gradingError.card")
    }

    private func totalScoreCard(_ result: AttemptResult) -> some View {
        let totalTitle = ResultStrings.text("result.total.title", locale: locale)
        let resolvedScore = ResultFormat.resolveDisplayScore(for: result)

        return VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 8) {
                Text(totalTitle)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(palette.textMutedColor)
                    .textCase(.uppercase)
                    .accessibilityAddTraits(.isHeader)
                    .resultProbe(id: "result.total.title", label: totalTitle)

                if let resolvedScore {
                    if result.isVact {
                        let practiceLabel = ResultStrings.text("result.vact.scoreLabel", locale: locale)
                        ResultStatusChip(title: practiceLabel, tone: .warning, symbolName: nil)
                            .accessibilityIdentifier("result.vact.scoreLabel")
                            .resultProbe(id: "result.vact.scoreLabel", label: practiceLabel)
                    }

                    let scoreString = ResultFormat.formatScore(resolvedScore.score, locale: locale)
                    let maxString = resolvedScore.max.map { "/\(ResultFormat.formatScore($0, locale: locale))" }

                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(scoreString)
                            .font(.system(size: 44, weight: .bold).monospacedDigit())
                            .foregroundStyle(palette.primaryTextColor)
                            .minimumScaleFactor(0.6)
                            .lineLimit(1)
                        if let maxString {
                            Text(maxString)
                                .font(.title2.weight(.semibold).monospacedDigit())
                                .foregroundStyle(palette.textMutedColor)
                        }
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("result.total.scoreValue")
                    .resultProbe(id: "result.total.scoreValue", label: scoreString + (maxString ?? ""))

                    if result.isVact {
                        let practiceNote = ResultStrings.text("result.vact.practiceNote", locale: locale)
                        Text(practiceNote)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(statusPalette.warningColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .resultProbe(id: "result.vact.practiceNote", label: practiceNote)
                    }

                    if result.regrading {
                        let regradingLabel = ResultStrings.text("result.states.REGRADING", locale: locale)
                        let regradingNote = ResultStrings.text("result.total.regradingNote", locale: locale)
                        ResultStatusChip(title: regradingLabel, tone: .warning, symbolName: "arrow.triangle.2.circlepath")
                            .accessibilityIdentifier("result.total.regradingChip")
                            .resultProbe(id: "result.total.regradingChip", label: regradingLabel)
                        Text(regradingNote)
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .resultProbe(id: "result.total.regradingNote", label: regradingNote)
                    } else {
                        let scaleDesc: String = {
                            if result.isVact, let maxVal = resolvedScore.max {
                                return ResultStrings.text(
                                    "result.total.vactScaleDescription",
                                    locale: locale,
                                    replacements: ["scaleMax": ResultFormat.formatScore(maxVal, locale: locale)]
                                )
                            }
                            return ResultStrings.text("result.total.scaleDescription", locale: locale)
                        }()
                        Text(scaleDesc)
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .resultProbe(id: "result.total.scaleDescription", label: scaleDesc)
                    }
                } else {
                    let pendingTitle = ResultStrings.text("result.total.pendingTitle", locale: locale)
                    let pendingDesc = ResultStrings.text("result.total.pendingDescription", locale: locale)
                    let pendingReturn = ResultStrings.text("result.total.pendingReturnNote", locale: locale)

                    HStack(spacing: 10) {
                        Image(systemName: "clock.fill")
                            .font(.title2)
                            .foregroundStyle(statusPalette.warningColor)
                            .frame(width: 44, height: 44)
                            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(statusPalette.warningBgColor))
                            .accessibilityHidden(true)

                        Text(pendingTitle)
                            .font(.title2.weight(.bold))
                            .foregroundStyle(statusPalette.warningColor)
                            .resultProbe(id: "result.total.pendingTitle", label: pendingTitle)
                    }

                    Text(pendingDesc)
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .resultProbe(id: "result.total.pendingDescription", label: pendingDesc)

                    Text(pendingReturn)
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .resultProbe(id: "result.total.pendingReturnNote", label: pendingReturn)
                }
            }

            if let stats = result.statistics {
                Divider()
                ResultStatBucketsView(
                    stats: stats,
                    showPendingBucket: result.state == .pendingReview,
                    locale: locale
                )
            }

            Divider()

            actionRow(result)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.bgSurfaceColor))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("result.total.card")
    }

    private func actionRow(_ result: AttemptResult) -> some View {
        let myExamsTitle = ResultStrings.text("result.actions.myExams", locale: locale)
        let historyTitle = ResultStrings.text("result.backToHistory", locale: locale)

        return VStack(alignment: .leading, spacing: 10) {
            if result.canOpenReview {
                let reviewTitle = ResultStrings.text(
                    result.state == .pendingReview ? "result.actions.reviewAutoGraded" : "result.actions.reviewFull",
                    locale: locale
                )
                primaryButton(
                    title: reviewTitle,
                    symbolName: "eye",
                    identifier: "result.actions.reviewButton",
                    action: {
                        viewModel.performNavigationOnce {
                            onOpenReview(result.attemptId)
                        }
                    }
                )
            }

            if result.resultView == .score {
                let scoreOnly = ResultStrings.text("result.actions.scoreOnlyNote", locale: locale)
                Text(scoreOnly)
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("result.actions.scoreOnlyNote")
                    .resultProbe(id: "result.actions.scoreOnlyNote", label: scoreOnly)
            }

            if result.canOpenReview && result.state == .pendingReview {
                let pendingNote = ResultStrings.text("result.actions.pendingReviewNote", locale: locale)
                Text(pendingNote)
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("result.actions.pendingReviewNote")
                    .resultProbe(id: "result.actions.pendingReviewNote", label: pendingNote)
            }

            VStack(spacing: 10) {
                secondaryButton(
                    title: myExamsTitle,
                    identifier: "result.actions.myExamsButton",
                    action: { viewModel.performNavigationOnce(onGoMyExams) }
                )
                secondaryButton(
                    title: historyTitle,
                    identifier: "result.actions.historyButton",
                    action: { viewModel.performNavigationOnce(onGoHistory) }
                )
            }
        }
    }

    private func attemptInfoCard(_ result: AttemptResult) -> some View {
        let examKindLabel = result.examKindCode.map {
            ResultFormat.resolveCatalogLabel(code: $0, options: viewModel.examOptions, locale: locale)
        } ?? "—"
        let scopeLabel = ResultFormat.resolveScopeLabel(result.scope, locale: locale)
        let choicesText = ResultFormat.formatChoices(
            result.choices,
            subjectOptions: viewModel.subjectOptions,
            locale: locale
        )
        let startedText = ResultFormat.formatAttemptDateTime(result.startedAt)
        let finishedText = ResultFormat.formatAttemptDateTime(result.finishedAt)
        let durationText = ResultFormat.formatDuration(
            startedAt: result.startedAt,
            finishedAt: result.finishedAt,
            locale: locale
        )

        return ResultSectionCard(
            title: ResultStrings.text("result.info.title", locale: locale),
            identifier: "result.info.section"
        ) {
            VStack(alignment: .leading, spacing: 10) {
                infoRow(
                    id: "result.info.examKind",
                    label: ResultStrings.text("result.info.examKind", locale: locale),
                    value: examKindLabel
                )
                infoRow(
                    id: "result.info.scope",
                    label: ResultStrings.text("result.info.scope", locale: locale),
                    value: scopeLabel
                )
                if let partCode = result.partCode {
                    infoRow(
                        id: "result.info.part",
                        label: ResultStrings.text("result.info.part", locale: locale),
                        value: partCode
                    )
                }
                if let subjectCode = result.subjectCode {
                    infoRow(
                        id: "result.info.subject",
                        label: ResultStrings.text("result.info.subject", locale: locale),
                        value: ResultFormat.resolveCatalogLabel(
                            code: subjectCode,
                            options: viewModel.subjectOptions,
                            locale: locale
                        )
                    )
                }
                if let attemptNo = result.attemptNo {
                    infoRow(
                        id: "result.info.attemptNo",
                        label: ResultStrings.text("result.info.attemptNo", locale: locale),
                        value: ResultStrings.text(
                            "result.attemptNo",
                            locale: locale,
                            replacements: ["count": String(attemptNo)]
                        )
                    )
                }
                infoRow(
                    id: "result.info.choices",
                    label: ResultStrings.text("result.info.choices", locale: locale),
                    value: choicesText
                )
                if let startedText {
                    infoRow(
                        id: "result.info.startedAt",
                        label: ResultStrings.text("result.info.startedAt", locale: locale),
                        value: startedText
                    )
                }
                if let finishedText {
                    infoRow(
                        id: "result.info.finishedAt",
                        label: ResultStrings.text("result.info.finishedAt", locale: locale),
                        value: finishedText
                    )
                }
                infoRow(
                    id: "result.info.duration",
                    label: ResultStrings.text("result.info.duration", locale: locale),
                    value: durationText
                )
                if let endReason = result.endReason {
                    infoRow(
                        id: "result.info.endReason",
                        label: ResultStrings.text("result.info.endReason", locale: locale),
                        value: ResultFormat.resolveEndReasonLabel(endReason, locale: locale)
                    )
                }
            }
        }
    }

    private func infoRow(id: String, label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(value)")
        .accessibilityIdentifier(id)
        .resultProbe(id: id, label: "\(label): \(value)")
    }

    private func primaryButton(
        title: String,
        symbolName: String? = nil,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let symbolName {
                    Image(systemName: symbolName)
                        .font(.body.weight(.semibold))
                        .accessibilityHidden(true)
                }
                Text(title)
                    .font(.body.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(palette.onPrimaryColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.primaryFillColor))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
        .resultProbe(
            id: identifier,
            label: title,
            isButton: true,
            isEnabled: true,
            action: action
        )
    }

    private func secondaryButton(
        title: String,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.bgSubtleColor))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.borderStrongColor, lineWidth: 1))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
        .resultProbe(
            id: identifier,
            label: title,
            isButton: true,
            isEnabled: true,
            action: action
        )
    }
}
