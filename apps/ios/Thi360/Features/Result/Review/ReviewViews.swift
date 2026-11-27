import SwiftUI

struct ReviewView: View {
    @Bindable var viewModel: ReviewViewModel
    let locale: AppLocale
    let onBackToResult: () -> Void
    let onGoMyExams: () -> Void
    let onGoHistory: () -> Void
    var autoLoadOnAppear: Bool = true
    var shouldPurgeAssetsOnDisappear: () -> Bool = { true }

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.shellWidth) private var shellWidth

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
                    onRetry: { viewModel.retry() },
                    content: { model in
                        loadedContent(model)
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
        .navigationTitle(ResultStrings.text("review.pageTitle", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("route.review")
        .onAppear {
            if autoLoadOnAppear {
                viewModel.onAppear()
            }
        }
        .onDisappear {
            viewModel.onDisappear(shouldPurgeAssets: shouldPurgeAssetsOnDisappear())
        }
    }

    private var loadingSkeleton: some View {
        let label = ResultStrings.text("review.loading", locale: locale)
        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 240, height: 28)
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.bgSurfaceColor)
                    .frame(height: 56)
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
                    .frame(height: 280)
            }
            .padding(16)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityIdentifier("review.loading")
        .resultProbe(id: "review.loading", label: label)
    }

    @ViewBuilder
    private func customErrorView(kind: LoadFailureKind) -> some View {
        switch kind {
        case .forbidden:
            notIncludedView
        case let .conflict(code) where code == "REVIEW_NOT_AVAILABLE":
            notAvailableView
        case .notFound:
            notFoundView
        default:
            genericErrorView(canRetry: true)
        }
    }

    private var notIncludedView: some View {
        let title = ResultStrings.text("review.notIncluded.title", locale: locale)
        let desc = ResultStrings.text("review.notIncluded.description", locale: locale)
        let backTitle = ResultStrings.text("review.notIncluded.backToResult", locale: locale)

        return statusErrorCard(
            symbol: "lock.fill",
            symbolColor: palette.textSecondaryColor,
            eyebrow: nil,
            title: title,
            description: desc,
            identifier: "review.state.notIncluded"
        ) {
            primaryActionButton(
                title: backTitle,
                identifier: "review.notIncluded.backToResult",
                action: { viewModel.performNavigationOnce(onBackToResult) }
            )
        }
    }

    private var notAvailableView: some View {
        let title = ResultStrings.text("review.notAvailable.title", locale: locale)
        let desc = ResultStrings.text("review.notAvailable.description", locale: locale)
        let backTitle = ResultStrings.text("review.notAvailable.backToResult", locale: locale)

        return statusErrorCard(
            symbol: "clock.fill",
            symbolColor: statusPalette.warningColor,
            eyebrow: nil,
            title: title,
            description: desc,
            identifier: "review.state.notAvailable"
        ) {
            primaryActionButton(
                title: backTitle,
                identifier: "review.notAvailable.backToResult",
                action: { viewModel.performNavigationOnce(onBackToResult) }
            )
        }
    }

    private var notFoundView: some View {
        let eyebrow = ResultStrings.text("review.notFound.eyebrow", locale: locale)
        let title = ResultStrings.text("review.notFound.title", locale: locale)
        let desc = ResultStrings.text("review.notFound.description", locale: locale)
        let toHistory = ResultStrings.text("review.notFound.toHistory", locale: locale)
        let toHome = ResultStrings.text("review.notFound.toHome", locale: locale)

        return statusErrorCard(
            symbol: "magnifyingglass",
            symbolColor: palette.textMutedColor,
            eyebrow: eyebrow,
            title: title,
            description: desc,
            identifier: "review.state.notFound"
        ) {
            VStack(spacing: 10) {
                primaryActionButton(
                    title: toHistory,
                    identifier: "review.notFound.toHistory",
                    action: { viewModel.performNavigationOnce(onGoHistory) }
                )
                secondaryActionButton(
                    title: toHome,
                    identifier: "review.notFound.toHome",
                    action: { viewModel.performNavigationOnce(onGoMyExams) }
                )
            }
        }
    }

    private func genericErrorView(canRetry: Bool) -> some View {
        let title = ResultStrings.text("review.errorTitle", locale: locale)
        let desc = ResultStrings.text("review.errorDescription", locale: locale)
        let retryTitle = AppStrings.common("common.retry", locale: locale)

        return statusErrorCard(
            symbol: "exclamationmark.triangle",
            symbolColor: palette.dangerTextColor,
            eyebrow: nil,
            title: title,
            description: desc,
            identifier: "review.state.error"
        ) {
            if canRetry {
                primaryActionButton(
                    title: retryTitle,
                    identifier: "review.error.retryButton",
                    action: { viewModel.retry() }
                )
            }
        }
    }

    private func statusErrorCard<Actions: View>(
        symbol: String,
        symbolColor: Color,
        eyebrow: String?,
        title: String,
        description: String,
        identifier: String,
        @ViewBuilder actions: () -> Actions
    ) -> some View {
        ScrollView {
            VStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(symbolColor)
                    .accessibilityHidden(true)

                if let eyebrow {
                    Text(eyebrow)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(palette.dangerTextColor)
                        .resultProbe(id: "\(identifier).eyebrow", label: eyebrow)
                }

                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .resultProbe(id: "\(identifier).title", label: title)

                Text(description)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .resultProbe(id: "\(identifier).description", label: description)

                actions()
                    .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.bgSurfaceColor))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
            .padding(20)
        }
        .accessibilityIdentifier(identifier)
    }

    private func loadedContent(_ model: ReviewModel) -> some View {
        GeometryReader { proxy in
            let availableWidth = proxy.size.width
            let isTwoPane = shellWidth != .narrow && availableWidth >= 600
            let part = viewModel.selectedPart
            let groups = viewModel.currentPartGroups

            VStack(alignment: .leading, spacing: 0) {
                topChrome(model: model, showParts: model.parts.count > 1)

                if let part, part.isSkipped {
                    ScrollView {
                        skippedPartCard(part: part)
                            .padding(16)
                    }
                } else if groups.isEmpty {
                    ScrollView {
                        emptyPartCard
                            .padding(16)
                    }
                } else if isTwoPane {
                    twoPaneContent(
                        groups: groups,
                        availableWidth: availableWidth
                    )
                } else {
                    narrowSingleColumnContent(
                        groups: groups,
                        availableWidth: availableWidth
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    private func topChrome(model: ReviewModel, showParts: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            reviewHeader(model: model)

            if model.resultView == .scoreAndAnswers {
                let notice = ResultStrings.text("review.scoreOnlyNotice", locale: locale)
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "info.circle")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(statusPalette.infoColor)
                        .padding(.top, 2)
                        .accessibilityHidden(true)
                    Text(notice)
                        .font(.footnote)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(palette.bgSubtleColor))
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
                .accessibilityIdentifier("review.banner.scoreOnly")
                .resultProbe(id: "review.banner.scoreOnly", label: notice)
            }

            if model.state == .pendingReview {
                let pending = ResultStrings.text("review.pendingBanner", locale: locale)
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "clock.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(statusPalette.warningColor)
                        .padding(.top, 2)
                        .accessibilityHidden(true)
                    Text(pending)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(statusPalette.warningBgColor))
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(statusPalette.warningColor, lineWidth: 1))
                .accessibilityIdentifier("review.banner.pendingReview")
                .resultProbe(id: "review.banner.pendingReview", label: pending)
            }

            if showParts {
                partTabsStrip(parts: model.parts)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 10)
        .background(palette.bgPageColor)
    }

    private func reviewHeader(model: ReviewModel) -> some View {
        let title = ResultStrings.text("review.pageTitle", locale: locale)
        let subtitle = buildSubtitle(resultSummary: model.resultSummary)

        return VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.title3.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("review.header.title")
                .resultProbe(id: "review.header.title", label: title)

            if let subtitle {
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("review.header.subtitle")
                    .resultProbe(id: "review.header.subtitle", label: subtitle)
            }
        }
    }

    private func buildSubtitle(resultSummary: AttemptResult?) -> String? {
        guard let resultSummary, !resultSummary.examName.isEmpty else { return nil }
        var segments: [String] = [resultSummary.examName]
        if let scope = resultSummary.scope {
            segments.append(ResultFormat.resolveScopeLabel(scope, locale: locale))
        }
        if let attemptNo = resultSummary.attemptNo {
            segments.append(
                ResultStrings.text(
                    "result.attemptNo",
                    locale: locale,
                    replacements: ["count": String(attemptNo)]
                )
            )
        }
        if let scoreInfo = ResultFormat.resolveDisplayScore(for: resultSummary) {
            let totalTitle = ResultStrings.text("result.total.title", locale: locale)
            let scoreText = ResultFormat.formatScore(scoreInfo.score, locale: locale)
            if let maxScore = scoreInfo.max {
                let maxText = ResultFormat.formatScore(maxScore, locale: locale)
                segments.append("\(totalTitle): \(scoreText)/\(maxText)")
            } else {
                segments.append("\(totalTitle): \(scoreText)")
            }
        }
        return segments.joined(separator: " · ")
    }

    private func partTabsStrip(parts: [ReviewPartItem]) -> some View {
        let stripLabel = ResultStrings.text("review.nav.partsLabel", locale: locale)
        let skippedBadge = ResultStrings.text("review.nav.skippedPartBadge", locale: locale)

        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(parts.enumerated()), id: \.element.id) { index, part in
                    let isSelected = index == viewModel.selectedPartIndex
                    let chipId = "review.parts.tab.\(index)"
                    let fullLabel = part.isSkipped ? "\(part.displayTitle), \(skippedBadge)" : part.displayTitle

                    Button {
                        viewModel.selectPart(at: index)
                    } label: {
                        HStack(spacing: 6) {
                            Text(part.displayTitle)
                                .font(.subheadline.weight(isSelected ? .semibold : .medium))
                                .lineLimit(1)

                            if part.isSkipped {
                                Text(skippedBadge)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(isSelected ? palette.onPrimaryColor : statusPalette.warningColor)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(
                                        Capsule()
                                            .fill(isSelected ? palette.onPrimaryColor.opacity(0.2) : statusPalette.warningBgColor)
                                    )
                            }
                        }
                        .foregroundStyle(isSelected ? palette.onPrimaryColor : palette.textColor)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 44)
                        .background(
                            Capsule()
                                .fill(isSelected ? palette.primaryFillColor : palette.bgSurfaceColor)
                        )
                        .overlay(
                            Capsule()
                                .strokeBorder(isSelected ? palette.primaryFillColor : palette.borderColor, lineWidth: 1)
                        )
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                    .accessibilityLabel(fullLabel)
                    .accessibilityIdentifier(chipId)
                    .resultProbe(
                        id: chipId,
                        label: fullLabel,
                        isButton: true,
                        isEnabled: true,
                        isSelected: isSelected,
                        action: { viewModel.selectPart(at: index) }
                    )
                }
            }
            .padding(.vertical, 2)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(stripLabel)
        .accessibilityIdentifier("review.parts.strip")
        .resultProbe(id: "review.parts.strip", label: stripLabel)
    }

    private func skippedPartCard(part: ReviewPartItem) -> some View {
        let title = ResultStrings.text("review.skippedPart.title", locale: locale)
        let desc: String = {
            if let score = part.score, let maxScore = part.maxScore {
                return ResultStrings.text(
                    "review.skippedPart.description",
                    locale: locale,
                    replacements: [
                        "score": ResultFormat.formatScore(score, locale: locale),
                        "max": ResultFormat.formatScore(maxScore, locale: locale)
                    ]
                )
            }
            return ResultStrings.text("review.skippedPart.descriptionNoScore", locale: locale)
        }()

        return VStack(spacing: 10) {
            Image(systemName: "info.circle")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(statusPalette.warningColor)
                .accessibilityHidden(true)
            Text(title)
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .resultProbe(id: "review.skippedPart.title", label: title)
            Text(desc)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .resultProbe(id: "review.skippedPart.description", label: desc)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(statusPalette.warningBgColor))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(statusPalette.warningColor, lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("review.skippedPart.card")
    }

    private var emptyPartCard: some View {
        let title = ResultStrings.text("review.emptyPart.title", locale: locale)
        let desc = ResultStrings.text("review.emptyPart.description", locale: locale)

        return VStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(palette.textMutedColor)
                .accessibilityHidden(true)
            Text(title)
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .resultProbe(id: "review.emptyPart.title", label: title)
            Text(desc)
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .resultProbe(id: "review.emptyPart.description", label: desc)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.bgSurfaceColor))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("review.emptyPart.card")
    }

    private func narrowSingleColumnContent(
        groups: [ReviewGroup],
        availableWidth: CGFloat
    ) -> some View {
        let cardInnerWidth = max(availableWidth - 64, 220)
        return ScrollViewReader { proxy in
            VStack(spacing: 0) {
                narrowPaletteBar(groups: groups, scrollProxy: proxy)

                ScrollView {
                    questionGroupsList(groups: groups, cardInnerWidth: cardInnerWidth)
                        .padding(16)
                }
                .accessibilityIdentifier("review.questions.scroll")
            }
            .accessibilityIdentifier("review.layout.narrow")
            .resultProbe(id: "review.layout.narrow", label: "narrow")
        }
    }

    private func twoPaneContent(
        groups: [ReviewGroup],
        availableWidth: CGFloat
    ) -> some View {
        let sidebarWidth = min(320, max(236, floor(availableWidth * 0.34)))
        let mainWidth = max(availableWidth - sidebarWidth - 48, 280)
        let cardInnerWidth = max(mainWidth - 40, 220)

        return ScrollViewReader { proxy in
            HStack(alignment: .top, spacing: 16) {
                widePaletteSidebar(groups: groups, scrollProxy: proxy)
                    .frame(width: sidebarWidth)
                    .frame(maxHeight: .infinity, alignment: .top)

                ScrollView {
                    questionGroupsList(groups: groups, cardInnerWidth: cardInnerWidth)
                        .padding(.bottom, 20)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .accessibilityIdentifier("review.questions.scroll")
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
            .accessibilityIdentifier("review.layout.wide")
            .resultProbe(id: "review.layout.wide", label: "wide")
        }
    }

    private func narrowPaletteBar(groups: [ReviewGroup], scrollProxy: ScrollViewProxy) -> some View {
        let paletteTitle = ResultStrings.text("review.nav.paletteTitle", locale: locale)
        let summaryText = formattedPaletteSummary
        let prevTitle = ResultStrings.text("review.nav.prev", locale: locale)
        let nextTitle = ResultStrings.text("review.nav.next", locale: locale)

        return VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(paletteTitle)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .resultProbe(id: "review.palette.title", label: paletteTitle)
                Text(summaryText)
                    .font(.caption)
                    .foregroundStyle(palette.textMutedColor)
                    .monospacedDigit()
                    .fixedSize(horizontal: false, vertical: true)
                    .resultProbe(id: "review.palette.summary", label: summaryText)
            }

            HStack(spacing: 8) {
                Button {
                    if let targetId = viewModel.selectPreviousGroup() {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            scrollProxy.scrollTo(targetId, anchor: .top)
                        }
                    }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(viewModel.canGoPrevious ? palette.textColor : palette.textMutedColor)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(palette.bgSurfaceColor))
                        .overlay(Circle().strokeBorder(palette.borderColor, lineWidth: 1))
                        .opacity(viewModel.canGoPrevious ? 1.0 : 0.45)
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.canGoPrevious)
                .accessibilityLabel(prevTitle)
                .accessibilityIdentifier("review.palette.prevButton")
                .resultProbe(
                    id: "review.palette.prevButton",
                    label: prevTitle,
                    isButton: true,
                    isEnabled: viewModel.canGoPrevious,
                    action: {
                        if let targetId = viewModel.selectPreviousGroup() {
                            scrollProxy.scrollTo(targetId, anchor: .top)
                        }
                    }
                )

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(groups) { group in
                            paletteCell(group: group, scrollProxy: scrollProxy)
                        }
                    }
                    .padding(.vertical, 3)
                    .padding(.horizontal, 2)
                }
                .accessibilityIdentifier("review.palette.horizontalStrip")

                Button {
                    if let targetId = viewModel.selectNextGroup() {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            scrollProxy.scrollTo(targetId, anchor: .top)
                        }
                    }
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(viewModel.canGoNext ? palette.textColor : palette.textMutedColor)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(palette.bgSurfaceColor))
                        .overlay(Circle().strokeBorder(palette.borderColor, lineWidth: 1))
                        .opacity(viewModel.canGoNext ? 1.0 : 0.45)
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.canGoNext)
                .accessibilityLabel(nextTitle)
                .accessibilityIdentifier("review.palette.nextButton")
                .resultProbe(
                    id: "review.palette.nextButton",
                    label: nextTitle,
                    isButton: true,
                    isEnabled: viewModel.canGoNext,
                    action: {
                        if let targetId = viewModel.selectNextGroup() {
                            scrollProxy.scrollTo(targetId, anchor: .top)
                        }
                    }
                )
            }

            paletteLegend
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(palette.bgSurfaceColor)
        .overlay(alignment: .top) { Divider() }
        .overlay(alignment: .bottom) { Divider() }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("review.palette.narrowBar")
    }

    private func widePaletteSidebar(groups: [ReviewGroup], scrollProxy: ScrollViewProxy) -> some View {
        let paletteTitle = ResultStrings.text("review.nav.paletteTitle", locale: locale)
        let summaryText = formattedPaletteSummary
        let prevTitle = ResultStrings.text("review.nav.prev", locale: locale)
        let nextTitle = ResultStrings.text("review.nav.next", locale: locale)
        let gridColumns = [GridItem(.adaptive(minimum: 54), spacing: 8)]

        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(paletteTitle)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityAddTraits(.isHeader)
                        .resultProbe(id: "review.palette.title", label: paletteTitle)
                    Text(summaryText)
                        .font(.caption)
                        .foregroundStyle(palette.textMutedColor)
                        .monospacedDigit()
                        .fixedSize(horizontal: false, vertical: true)
                        .resultProbe(id: "review.palette.summary", label: summaryText)
                }

                LazyVGrid(columns: gridColumns, spacing: 8) {
                    ForEach(groups) { group in
                        paletteCell(group: group, scrollProxy: scrollProxy, fillsWidth: true)
                    }
                }
                .accessibilityIdentifier("review.palette.wideGrid")

                Divider()
                paletteLegend
                Divider()

                HStack(spacing: 8) {
                    Button {
                        if let targetId = viewModel.selectPreviousGroup() {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                scrollProxy.scrollTo(targetId, anchor: .top)
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.caption.weight(.bold))
                                .accessibilityHidden(true)
                            Text(prevTitle)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                        }
                        .foregroundStyle(viewModel.canGoPrevious ? palette.textColor : palette.textMutedColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .padding(.horizontal, 8)
                        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(palette.bgSubtleColor))
                        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
                        .opacity(viewModel.canGoPrevious ? 1.0 : 0.45)
                    }
                    .buttonStyle(.plain)
                    .disabled(!viewModel.canGoPrevious)
                    .accessibilityIdentifier("review.palette.prevButton")
                    .resultProbe(
                        id: "review.palette.prevButton",
                        label: prevTitle,
                        isButton: true,
                        isEnabled: viewModel.canGoPrevious,
                        action: {
                            if let targetId = viewModel.selectPreviousGroup() {
                                scrollProxy.scrollTo(targetId, anchor: .top)
                            }
                        }
                    )

                    Button {
                        if let targetId = viewModel.selectNextGroup() {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                scrollProxy.scrollTo(targetId, anchor: .top)
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Text(nextTitle)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.bold))
                                .accessibilityHidden(true)
                        }
                        .foregroundStyle(viewModel.canGoNext ? palette.textColor : palette.textMutedColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .padding(.horizontal, 8)
                        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(palette.bgSubtleColor))
                        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
                        .opacity(viewModel.canGoNext ? 1.0 : 0.45)
                    }
                    .buttonStyle(.plain)
                    .disabled(!viewModel.canGoNext)
                    .accessibilityIdentifier("review.palette.nextButton")
                    .resultProbe(
                        id: "review.palette.nextButton",
                        label: nextTitle,
                        isButton: true,
                        isEnabled: viewModel.canGoNext,
                        action: {
                            if let targetId = viewModel.selectNextGroup() {
                                scrollProxy.scrollTo(targetId, anchor: .top)
                            }
                        }
                    )
                }
            }
            .padding(16)
        }
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.bgSurfaceColor))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("review.palette.wideSidebar")
    }

    private func paletteCell(
        group: ReviewGroup,
        scrollProxy: ScrollViewProxy,
        fillsWidth: Bool = false
    ) -> some View {
        let isActive = viewModel.activeGroup?.id == group.id
        let style = paletteCellStyle(for: group.outcome)
        let outcomeText = ResultStrings.text("review.outcomes.\(group.outcome.rawValue)", locale: locale)
        let ariaLabel = ResultStrings.text(
            "review.nav.paletteItemAria",
            locale: locale,
            replacements: [
                "label": group.accessibleTitle(locale: locale),
                "outcome": outcomeText
            ]
        )
        let cellId = "review.palette.cell.\(group.id)"
        let minWidth: CGFloat = (group.rangeFrom != nil && group.rangeTo != nil) ? 62 : 44

        return Button {
            viewModel.selectGroup(id: group.id)
            withAnimation(.easeInOut(duration: 0.2)) {
                scrollProxy.scrollTo(group.id, anchor: .top)
            }
        } label: {
            ZStack(alignment: .topTrailing) {
                Text(group.badgeText)
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                    .foregroundStyle(style.foreground)
                    .padding(.horizontal, 8)
                    .frame(
                        minWidth: minWidth,
                        maxWidth: fillsWidth ? .infinity : nil,
                        minHeight: 44
                    )

                Image(systemName: style.symbol)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(style.foreground)
                    .padding(4)
                    .accessibilityHidden(true)
            }
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(style.background)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(
                        style.border,
                        style: style.isDashed
                            ? StrokeStyle(lineWidth: style.borderWidth, dash: [4, 2])
                            : StrokeStyle(lineWidth: style.borderWidth)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(isActive ? palette.primaryFillColor : Color.clear, lineWidth: 2)
                    .padding(-2)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(ariaLabel)
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
        .accessibilityIdentifier(cellId)
        .resultProbe(
            id: cellId,
            label: ariaLabel,
            value: group.badgeText,
            isButton: true,
            isEnabled: true,
            isSelected: isActive,
            action: {
                viewModel.selectGroup(id: group.id)
                scrollProxy.scrollTo(group.id, anchor: .top)
            }
        )
    }

    private var paletteLegend: some View {
        let legendLabel = ResultStrings.text("review.nav.paletteLegend", locale: locale)
        return WrappingHStackLayout(horizontalSpacing: 12, verticalSpacing: 6) {
            legendItem(outcome: .correct)
            legendItem(outcome: .partial)
            legendItem(outcome: .wrong)
            legendItem(outcome: .blank)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(legendLabel)
        .accessibilityIdentifier("review.palette.legend")
        .resultProbe(
            id: "review.palette.legend",
            label: [
                ResultStrings.text("review.outcomes.CORRECT", locale: locale),
                ResultStrings.text("review.outcomes.PARTIAL", locale: locale),
                ResultStrings.text("review.outcomes.WRONG", locale: locale),
                ResultStrings.text("review.outcomes.BLANK", locale: locale)
            ].joined(separator: " | ")
        )
    }

    private func legendItem(outcome: QuestionOutcome) -> some View {
        let style = paletteCellStyle(for: outcome)
        let title = ResultStrings.text("review.outcomes.\(outcome.rawValue)", locale: locale)

        return HStack(spacing: 5) {
            ZStack {
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(style.background)
                    .frame(width: 14, height: 14)
                    .overlay(
                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                            .strokeBorder(
                                style.border,
                                style: style.isDashed
                                    ? StrokeStyle(lineWidth: 1.5, dash: [3, 1.5])
                                    : StrokeStyle(lineWidth: 1.5)
                            )
                    )
                Image(systemName: style.symbol)
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(style.foreground)
            }
            .accessibilityHidden(true)

            Text(title)
                .font(.caption)
                .foregroundStyle(palette.textSecondaryColor)
        }
    }

    private func questionGroupsList(groups: [ReviewGroup], cardInnerWidth: CGFloat) -> some View {
        LazyVStack(alignment: .leading, spacing: 16) {
            ForEach(groups) { group in
                let isActive = viewModel.activeGroup?.id == group.id
                let cardId = "review.group.\(group.id)"

                VStack(alignment: .leading, spacing: 14) {
                    // Force compact layout inside the vertical review ScrollView so QuestionSetView stacks passage above children instead of nesting unbounded ScrollViews.
                    QuestionView(
                        item: group.item,
                        answer: group.studentAnswer,
                        answers: group.childAnswers,
                        mode: group.mode,
                        assets: viewModel.assetSource,
                        locale: locale,
                        fixedWidth: cardInnerWidth
                    )
                    .environment(\.horizontalSizeClass, .compact)

                    ForEach(group.essayCriteria) { criteriaSection in
                        ReviewEssayCriteriaCardView(
                            section: criteriaSection,
                            locale: locale
                        )
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(
                            isActive ? palette.primaryFillColor : palette.borderColor,
                            lineWidth: isActive ? 2 : 1
                        )
                )
                .id(group.id)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier(cardId)
                .resultProbe(
                    id: cardId,
                    label: group.accessibleTitle(locale: locale),
                    value: isActive ? "active" : "inactive",
                    isSelected: isActive
                )
            }
        }
    }

    private var formattedPaletteSummary: String {
        let summary = viewModel.paletteSummary
        return ResultStrings.text(
            "review.nav.paletteSummary",
            locale: locale,
            replacements: [
                "correct": String(summary.correct),
                "partial": String(summary.partial),
                "wrong": String(summary.wrong),
                "blank": String(summary.blank)
            ]
        )
    }

    private struct PaletteCellStyle {
        let background: Color
        let border: Color
        let foreground: Color
        let symbol: String
        let borderWidth: CGFloat
        let isDashed: Bool
    }

    private func paletteCellStyle(for outcome: QuestionOutcome) -> PaletteCellStyle {
        switch outcome {
        case .correct:
            PaletteCellStyle(
                background: statusPalette.successBgColor,
                border: statusPalette.successColor,
                foreground: statusPalette.successColor,
                symbol: "checkmark",
                borderWidth: 1,
                isDashed: false
            )
        case .partial:
            PaletteCellStyle(
                background: statusPalette.warningBgColor,
                border: statusPalette.warningColor,
                foreground: statusPalette.warningColor,
                symbol: "circle.lefthalf.filled",
                borderWidth: 2,
                isDashed: false
            )
        case .wrong:
            PaletteCellStyle(
                background: palette.dangerBgColor,
                border: palette.dangerTextColor,
                foreground: palette.dangerTextColor,
                symbol: "xmark",
                borderWidth: 2,
                isDashed: true
            )
        case .pendingReview:
            PaletteCellStyle(
                background: statusPalette.infoBgColor,
                border: statusPalette.infoColor,
                foreground: statusPalette.infoColor,
                symbol: "clock",
                borderWidth: 2,
                isDashed: true
            )
        case .blank:
            PaletteCellStyle(
                background: palette.bgSubtleColor,
                border: palette.borderStrongColor,
                foreground: palette.textSecondaryColor,
                symbol: "minus",
                borderWidth: 1,
                isDashed: true
            )
        }
    }

    private func primaryActionButton(
        title: String,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.onPrimaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.primaryFillColor))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
        .resultProbe(id: identifier, label: title, isButton: true, isEnabled: true, action: action)
    }

    private func secondaryActionButton(
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
        .resultProbe(id: identifier, label: title, isButton: true, isEnabled: true, action: action)
    }
}

struct ReviewEssayCriteriaCardView: View {
    let section: ReviewEssayCriteriaSectionModel
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    private var headingText: String {
        let baseTitle = ResultStrings.text("review.essayCriteria.title", locale: locale)
        guard let childNo = section.childDisplayNo else { return baseTitle }
        let childLabel = ResultStrings.text(
            "review.questionLabel",
            locale: locale,
            replacements: ["no": String(childNo)]
        )
        return "\(baseTitle) · \(childLabel)"
    }

    var body: some View {
        let criterionCol = ResultStrings.text("review.essayCriteria.criterionCol", locale: locale)
        let pointsCol = ResultStrings.text("review.essayCriteria.pointsCol", locale: locale)
        let commentCol = ResultStrings.text("review.essayCriteria.commentCol", locale: locale)
        let sectionId = "review.essayCriteria.\(section.id)"

        VStack(alignment: .leading, spacing: 10) {
            Text(headingText)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)
                .resultProbe(id: "\(sectionId).title", label: headingText)

            VStack(alignment: .leading, spacing: 8) {
                ForEach(section.criteria) { row in
                    let nameText = row.criterionId ?? ResultStrings.text(
                        "review.essayCriteria.criterionFallback",
                        locale: locale,
                        replacements: ["index": String(row.index)]
                    )
                    let pointsText = row.points.map {
                        ResultStrings.text(
                            "review.essayCriteria.pointsValue",
                            locale: locale,
                            replacements: ["points": ResultFormat.formatScore($0, locale: locale)]
                        )
                    } ?? "—"
                    let commentText = row.comment ?? "—"
                    let rowId = "\(sectionId).row.\(row.index)"

                    VStack(alignment: .leading, spacing: 6) {
                        HStack(alignment: .top, spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(criterionCol)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(palette.textMutedColor)
                                Text(nameText)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(palette.textColor)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)

                            VStack(alignment: .trailing, spacing: 2) {
                                Text(pointsCol)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(palette.textMutedColor)
                                Text(pointsText)
                                    .font(.subheadline.weight(.bold).monospacedDigit())
                                    .foregroundStyle(palette.textColor)
                            }
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(commentCol)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(palette.textMutedColor)
                            Text(commentText)
                                .font(.footnote)
                                .foregroundStyle(palette.textSecondaryColor)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(palette.bgSurfaceColor))
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier(rowId)
                    .resultProbe(
                        id: rowId,
                        label: "\(criterionCol): \(nameText) | \(pointsCol): \(pointsText) | \(commentCol): \(commentText)"
                    )
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.bgSubtleColor))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(sectionId)
    }
}
