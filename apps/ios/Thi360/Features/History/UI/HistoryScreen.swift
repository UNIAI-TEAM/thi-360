import SwiftUI

struct HistoryScreen: View {
    @Bindable var feed: HistoryFeed
    @Bindable var router: AppRouter
    let resetFilters: Bool
    let locale: AppLocale
    let showsBrandHeader: Bool

    @Environment(\.brandPalette) private var palette
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.shellWidth) private var shellWidth
    @State private var isFilterSheetPresented: Bool = false

    private var isRegularWidth: Bool {
        horizontalSizeClass == .regular || shellWidth == .twoColumn
    }

    private var navigationHandler: HistoryNavigationHandler {
        HistoryNavigationHandler(router: router)
    }

    private var loadTaskKey: String {
        "\(ObjectIdentifier(feed)):\(router.selectedDestination == .history):\(router.reloadCount(for: .history)):\(resetFilters)"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if showsBrandHeader {
                    BrandWordmarkHeader(locale: locale)
                }

                VStack(alignment: .leading, spacing: 6) {
                    MyExamsChipFlowLayout(horizontalSpacing: 8, verticalSpacing: 6) {
                        Text(MyExamsStrings.text("history.pageTitle", locale: locale))
                            .font(.largeTitle.weight(.bold))
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityIdentifier("history.heading")

                        if let total = feed.displayedTotalItems {
                            MyExamsBadgeView(
                                text: MyExamsStrings.plural("history.count", count: total, locale: locale),
                                style: .subtle
                            )
                            .accessibilityIdentifier("history.totalCount")
                        }
                    }

                    Text(MyExamsStrings.text("history.subtitle", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("history.subtitle")
                }

                HistoryFilterBarView(
                    feed: feed,
                    locale: locale,
                    onOpenFilterSheet: { isFilterSheetPresented = true }
                )

                listRegion
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, isRegularWidth ? 20 : 16)
            .padding(.vertical, 16)
        }
        .accessibilityIdentifier("history.scrollView")
        .background(palette.bgPageColor.ignoresSafeArea())
        .sheet(isPresented: $isFilterSheetPresented) {
            HistoryFilterSheetView(
                feed: feed,
                locale: locale,
                onDismiss: { isFilterSheetPresented = false }
            )
        }
        .task(id: loadTaskKey) {
            guard router.selectedDestination == .history else { return }
            await feed.loadOnAppear(
                resetFilters: resetFilters,
                reloadToken: router.reloadCount(for: .history)
            )
        }
        .onChange(of: router.selectedDestination) { oldDestination, newDestination in
            if oldDestination == .history && newDestination != .history {
                feed.markLeftTab()
            }
        }
        .onChange(of: router.path(for: .history).count) { oldCount, newCount in
            if newCount < oldCount && newCount == 0 && router.selectedDestination == .history {
                Task { await feed.reloadWithCurrentFilter() }
            }
        }
        .onChange(of: router.presentedAttempt) { oldAttempt, newAttempt in
            if oldAttempt != nil && newAttempt == nil && router.selectedDestination == .history {
                Task { await feed.reloadWithCurrentFilter() }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("history.screen")
    }

    var renderedStrings: [String] {
        var out: [String] = [
            MyExamsStrings.text("history.pageTitle", locale: locale),
            MyExamsStrings.text("history.subtitle", locale: locale)
        ]
        if let total = feed.displayedTotalItems {
            out.append(MyExamsStrings.plural("history.count", count: total, locale: locale))
        }
        switch feed.loadState {
        case .loading:
            out.append(MyExamsStrings.text("history.loading", locale: locale))
        case .empty:
            out.append(MyExamsStrings.text("history.empty.noAttemptsTitle", locale: locale))
            out.append(MyExamsStrings.text("history.empty.noAttemptsDescription", locale: locale))
            out.append(MyExamsStrings.text("history.empty.toMyExams", locale: locale))
        case let .failed(kind, _, _):
            if kind == .offline {
                out.append(AppStrings.common("common.offline.title", locale: locale))
                out.append(AppStrings.common("common.offline.description", locale: locale))
                out.append(AppStrings.common("common.retry", locale: locale))
            } else {
                out.append(MyExamsStrings.text("history.error.title", locale: locale))
                out.append(MyExamsStrings.text("history.error.description", locale: locale))
                out.append(AppStrings.common("common.retry", locale: locale))
            }
        case let .loaded(page, stale):
            if stale {
                let banner = HistoryStaleBannerView(
                    lastLoadedAt: feed.lastLoadedAt,
                    isRetrying: feed.isRetryingStale,
                    locale: locale,
                    onRetry: {}
                )
                out.append(contentsOf: banner.renderedStrings)
            }
            if page.items.isEmpty && !feed.filter.isEmpty {
                out.append(MyExamsStrings.text("history.empty.title", locale: locale))
                out.append(MyExamsStrings.text("history.empty.description", locale: locale))
                out.append(MyExamsStrings.text("history.empty.clearFilters", locale: locale))
            } else {
                for item in page.items {
                    out.append(
                        contentsOf: HistoryRowCardView(
                            item: item,
                            subjectMap: feed.subjectMap,
                            locale: locale,
                            onContinue: { _ in },
                            onViewResult: { _ in }
                        ).renderedStrings
                    )
                }
            }
        }
        return out
    }

    @ViewBuilder
    private var listRegion: some View {
        switch feed.loadState {
        case .loading:
            HistorySkeletonView(locale: locale)

        case .empty:
            noAttemptsEmptyCard

        case let .failed(kind, canRetry, retryAfter):
            if kind == .offline {
                LoadStateView(
                    state: LoadState<Void>.failed(kind: .offline, canRetry: canRetry, retryAfter: retryAfter),
                    locale: locale,
                    onRetry: {
                        Task { await feed.retry() }
                    }
                ) { _ in
                    EmptyView()
                }
            } else {
                errorStateCard
            }

        case let .loaded(page, stale):
            VStack(alignment: .leading, spacing: 12) {
                if stale {
                    HistoryStaleBannerView(
                        lastLoadedAt: feed.lastLoadedAt,
                        isRetrying: feed.isRetryingStale,
                        locale: locale,
                        onRetry: {
                            Task { await feed.retry() }
                        }
                    )
                }

                if page.items.isEmpty && !feed.filter.isEmpty {
                    noMatchEmptyCard
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(Array(page.items.enumerated()), id: \.element.attemptId) { index, item in
                            HistoryRowCardView(
                                item: item,
                                subjectMap: feed.subjectMap,
                                locale: locale,
                                onContinue: { attemptId in
                                    navigationHandler.handleContinue(attemptId: attemptId)
                                },
                                onViewResult: { attemptId in
                                    navigationHandler.handleViewResult(attemptId: attemptId)
                                }
                            )
                            .onAppear {
                                if index >= max(0, page.items.count - 2), page.hasMore, !page.isLoadingMore {
                                    Task { await feed.loadNextPageIfNeeded() }
                                }
                            }
                        }

                        if page.hasMore && !page.isLoadingMore {
                            Color.clear
                                .frame(height: 1)
                                .id("history.loadMoreSentinel.\(page.items.count)")
                                .onAppear {
                                    Task { await feed.loadNextPageIfNeeded() }
                                }
                        }

                        if page.isLoadingMore {
                            HStack(spacing: 10) {
                                ProgressView()
                                    .tint(palette.primaryTextColor)
                                Text(AppStrings.common("common.loading", locale: locale))
                                    .font(.subheadline)
                                    .foregroundStyle(palette.textMutedColor)
                            }
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .accessibilityElement(children: .combine)
                            .accessibilityIdentifier("history.tail.loading")
                        }
                    }
                    .opacity(feed.isSwitchingFilter ? 0.6 : 1.0)
                    .accessibilityIdentifier(feed.isSwitchingFilter ? "history.list.dimmed" : "history.list")
                }
            }
        }
    }

    private var noAttemptsEmptyCard: some View {
        VStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 56, height: 56)

                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(palette.textMutedColor)
            }
            .accessibilityHidden(true)

            Text(MyExamsStrings.text("history.empty.noAttemptsTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Text(MyExamsStrings.text("history.empty.noAttemptsDescription", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                navigationHandler.openMyExamsTab()
            } label: {
                Text(MyExamsStrings.text("history.empty.toMyExams", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .padding(.horizontal, 24)
                    .frame(minWidth: 140, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(MyExamsStrings.text("history.empty.toMyExams", locale: locale))
            .accessibilityIdentifier("history.empty.toMyExamsButton")
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("history.emptyNoAttempts")
    }

    private var noMatchEmptyCard: some View {
        VStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 56, height: 56)

                Image(systemName: "magnifyingglass")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(palette.textMutedColor)
            }
            .accessibilityHidden(true)

            Text(MyExamsStrings.text("history.empty.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Text(MyExamsStrings.text("history.empty.description", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                Task { await feed.clearFilters() }
            } label: {
                Text(MyExamsStrings.text("history.empty.clearFilters", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .padding(.horizontal, 24)
                    .frame(minWidth: 140, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(MyExamsStrings.text("history.empty.clearFilters", locale: locale))
            .accessibilityIdentifier("history.empty.clearFiltersButton")
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("history.emptyNoMatch")
    }

    private var errorStateCard: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(MyExamsStrings.text("history.error.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)

            Text(MyExamsStrings.text("history.error.description", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                Task { await feed.retry() }
            } label: {
                Text(AppStrings.common("common.retry", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .padding(.horizontal, 24)
                    .frame(minWidth: 120, minHeight: 44)
                    .background(
                        Capsule(style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(Capsule(style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(AppStrings.common("common.retry", locale: locale))
            .accessibilityIdentifier("history.error.retryButton")
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("history.errorState")
    }
}
