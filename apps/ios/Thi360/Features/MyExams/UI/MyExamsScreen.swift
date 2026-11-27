import SwiftUI
import UIKit

struct MyExamsScreen: View {
    @Bindable var feed: MyExamsFeed
    @Bindable var router: AppRouter
    let locale: AppLocale
    let showsBrandHeader: Bool
    var supportContact: SupportContact = AppEnvironment.current.supportContact
    var onFocusBundleGroup: ((String) -> Void)? = nil

    @Environment(\.brandPalette) private var palette
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.shellWidth) private var shellWidth
    @AccessibilityFocusState private var focusedBundleId: String?

    private var isRegularWidth: Bool {
        horizontalSizeClass == .regular || shellWidth == .twoColumn
    }

    private var navigationHandler: MyExamsNavigationHandler {
        MyExamsNavigationHandler(router: router, debouncer: feed.navigationDebouncer)
    }

    func triggerCardAction(_ action: MyExamCardButtonAction) {
        navigationHandler.handleCardAction(action)
    }

    func triggerExploreCatalog() {
        navigationHandler.openExploreCatalog()
    }

    func triggerBundleDetail(code: String) {
        navigationHandler.openBundleDetail(code: code)
    }

    func triggerOrderDetail(orderCode: String) {
        navigationHandler.openOrderDetail(orderCode: orderCode)
    }

    private var gridColumns: [GridItem] {
        if isRegularWidth {
            return [
                GridItem(.flexible(), spacing: 16, alignment: .top),
                GridItem(.flexible(), spacing: 16, alignment: .top)
            ]
        }
        return [GridItem(.flexible(), spacing: 16, alignment: .top)]
    }

    private var greetingSubtitle: String {
        MyExamsStrings.text(
            "home.subtitle",
            locale: locale,
            replacements: ["name": feed.greetingDisplayName]
        )
    }

    private var loadTaskKey: String {
        "\(ObjectIdentifier(feed)):\(router.selectedDestination == .myExams):\(router.reloadCount(for: .myExams))"
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if showsBrandHeader {
                        BrandWordmarkHeader(locale: locale)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text(MyExamsStrings.text("home.pageTitle", locale: locale))
                            .font(.largeTitle.weight(.bold))
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityIdentifier("myExams.heading")

                        Text(greetingSubtitle)
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("myExams.greeting")
                    }

                    contentBody(scrollProxy: proxy)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, isRegularWidth ? 20 : 16)
                .padding(.vertical, 16)
            }
            .refreshable {
                await feed.pullToRefresh()
            }
            .accessibilityIdentifier("myExams.scrollView")
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .task(id: loadTaskKey) {
            guard router.selectedDestination == .myExams else { return }
            await feed.loadOnAppear(reloadToken: router.reloadCount(for: .myExams))
        }
        .onChange(of: router.selectedDestination) { oldDestination, newDestination in
            if oldDestination == .myExams && newDestination != .myExams {
                feed.markLeftTab()
            }
        }
        .onChange(of: router.path(for: .myExams).count) { oldCount, newCount in
            if newCount < oldCount && newCount == 0 && router.selectedDestination == .myExams {
                Task { await feed.reloadKeepingExisting() }
            }
        }
        .onChange(of: router.presentedAttempt) { oldAttempt, newAttempt in
            if oldAttempt != nil && newAttempt == nil && router.selectedDestination == .myExams {
                Task { await feed.reloadKeepingExisting() }
            }
        }
        .onDisappear {
            if router.presentedAttempt != nil
                || router.isPresentingChangePassword
                || router.selectedDestination != .myExams
                || !router.path(for: .myExams).isEmpty
            {
                feed.markLeftTab()
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("myExams.screen")
    }

    var renderedStrings: [String] {
        var out: [String] = [
            MyExamsStrings.text("home.pageTitle", locale: locale),
            greetingSubtitle
        ]
        switch feed.loadState {
        case .loading:
            out.append(AppStrings.common("common.loading", locale: locale))
        case .empty:
            out.append(MyExamsStrings.text("home.emptyTitle", locale: locale))
            out.append(MyExamsStrings.text("home.emptyDescription", locale: locale))
            out.append(MyExamsStrings.text("home.exploreExams", locale: locale))
        case let .failed(kind, _, _):
            if kind == .offline {
                out.append(AppStrings.common("common.offline.title", locale: locale))
                out.append(AppStrings.common("common.offline.description", locale: locale))
                out.append(AppStrings.common("common.retry", locale: locale))
            } else {
                out.append(MyExamsStrings.text("home.errorTitle", locale: locale))
                out.append(MyExamsStrings.text("home.errorDescription", locale: locale))
                out.append(AppStrings.common("common.retry", locale: locale))
            }
        case let .loaded(cards, stale):
            if stale {
                let banner = MyExamsStaleBannerView(
                    lastLoadedAt: feed.lastLoadedAt,
                    isReloading: feed.isReloading,
                    locale: locale,
                    onRetry: {}
                )
                out.append(contentsOf: banner.renderedStrings)
            }
            let grouped = MyExamsLayout.group(cards)
            out.append(MyExamsStrings.text("home.sectionTitle", locale: locale))
            out.append(MyExamsStrings.plural("home.examCount", count: grouped.count, locale: locale))
            out.append(MyExamsStrings.text("home.exploreExams", locale: locale))
            if !grouped.checking.isEmpty {
                out.append(MyExamsStrings.text("home.checking.sectionTitle", locale: locale))
                for card in grouped.checking {
                    out.append(
                        contentsOf: CheckingExamCardView(
                            card: card,
                            supportContact: supportContact,
                            isReloading: feed.isReloading,
                            locale: locale,
                            onViewOrder: { _ in },
                            onReload: {}
                        ).renderedStrings
                    )
                }
            }
            for group in grouped.bundleGroups {
                out.append(group.ref.name)
                out.append(MyExamsStrings.plural("home.examCount", count: group.totalCount, locale: locale))
                out.append(group.ref.code)
                out.append(MyExamsStrings.text("bundle.home.viewBundle", locale: locale))
                for card in group.cards {
                    out.append(contentsOf: MyExamCardView(card: card, locale: locale, onAction: { _ in }).renderedStrings)
                }
                for ref in group.crossRefs {
                    let parts = MyExamsStrings.crossReferenceParts(
                        examName: ref.examName,
                        bundleName: ref.primaryBundle.name,
                        locale: locale
                    )
                    out.append(parts.prefix + parts.examName + parts.middle + parts.bundleName + parts.suffix)
                }
                out.append(MyExamsStrings.text("bundle.home.groupFootnote", locale: locale))
            }
            if !grouped.standalone.isEmpty {
                if grouped.showsStandaloneHeading {
                    out.append(MyExamsStrings.text("bundle.home.standaloneHeading", locale: locale))
                    out.append(MyExamsStrings.plural("home.examCount", count: grouped.standalone.count, locale: locale))
                }
                for card in grouped.standalone {
                    out.append(contentsOf: MyExamCardView(card: card, locale: locale, onAction: { _ in }).renderedStrings)
                }
            }
        }
        return out
    }

    @ViewBuilder
    private func contentBody(scrollProxy: ScrollViewProxy) -> some View {
        switch feed.loadState {
        case .loading:
            MyExamsSkeletonView(locale: locale, isRegularWidth: isRegularWidth)

        case .empty:
            emptyStateCard

        case let .failed(kind, canRetry, retryAfter):
            if kind == .offline {
                LoadStateView(
                    state: LoadState<Void>.failed(kind: .offline, canRetry: canRetry, retryAfter: retryAfter),
                    locale: locale,
                    onRetry: {
                        Task { await feed.retryFromError() }
                    }
                ) { _ in
                    EmptyView()
                }
            } else {
                errorStateCard
            }

        case let .loaded(cards, stale):
            let grouped = MyExamsLayout.group(cards)
            VStack(alignment: .leading, spacing: 16) {
                if stale {
                    MyExamsStaleBannerView(
                        lastLoadedAt: feed.lastLoadedAt,
                        isReloading: feed.isReloading,
                        locale: locale,
                        onRetry: {
                            Task { await feed.retryFromStaleBanner() }
                        }
                    )
                }

                sectionHeader(count: grouped.count)

                if !grouped.checking.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(MyExamsStrings.text("home.checking.sectionTitle", locale: locale))
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(palette.textMutedColor)
                            .textCase(.uppercase)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityIdentifier("myExams.checking.sectionTitle")

                        LazyVGrid(columns: gridColumns, alignment: .leading, spacing: 16) {
                            ForEach(grouped.checking) { card in
                                CheckingExamCardView(
                                    card: card,
                                    supportContact: supportContact,
                                    isReloading: feed.isReloading,
                                    locale: locale,
                                    onViewOrder: { orderCode in
                                        navigationHandler.openOrderDetail(orderCode: orderCode)
                                    },
                                    onReload: {
                                        Task { await feed.reloadFromCheckingCard() }
                                    }
                                )
                            }
                        }
                    }
                }

                if !grouped.bundleGroups.isEmpty {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(grouped.bundleGroups) { group in
                            BundleGroupSectionView(
                                group: group,
                                isRegularWidth: isRegularWidth,
                                locale: locale,
                                focusedBundleId: $focusedBundleId,
                                onViewBundle: { code in
                                    navigationHandler.openBundleDetail(code: code)
                                },
                                onCardAction: { action in
                                    navigationHandler.handleCardAction(action)
                                },
                                onJumpToBundle: { targetBundleId in
                                    withAnimation {
                                        scrollProxy.scrollTo("myExams.bundleGroup.\(targetBundleId)", anchor: .top)
                                    }
                                    focusedBundleId = targetBundleId
                                    onFocusBundleGroup?(targetBundleId)
                                }
                            )
                        }
                    }
                }

                if !grouped.standalone.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        if grouped.showsStandaloneHeading {
                            HStack(spacing: 8) {
                                Text(MyExamsStrings.text("bundle.home.standaloneHeading", locale: locale))
                                    .font(.headline.weight(.semibold))
                                    .foregroundStyle(palette.textColor)
                                    .accessibilityAddTraits(.isHeader)
                                    .accessibilityIdentifier("myExams.standalone.heading")

                                MyExamsBadgeView(
                                    text: MyExamsStrings.plural("home.examCount", count: grouped.standalone.count, locale: locale),
                                    style: .subtle
                                )
                            }
                        }

                        LazyVGrid(columns: gridColumns, alignment: .leading, spacing: 16) {
                            ForEach(grouped.standalone) { card in
                                MyExamCardView(card: card, locale: locale) { action in
                                    navigationHandler.handleCardAction(action)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func sectionHeader(count: Int) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .center, spacing: 8) {
                sectionTitleAndCount(count: count)
                Spacer(minLength: 8)
                exploreButton
            }
            VStack(alignment: .leading, spacing: 8) {
                sectionTitleAndCount(count: count)
                exploreButton
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("myExams.sectionHeader")
    }

    private func sectionTitleAndCount(count: Int) -> some View {
        HStack(spacing: 8) {
            Text(MyExamsStrings.text("home.sectionTitle", locale: locale))
                .font(.title3.weight(.bold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)

            MyExamsBadgeView(
                text: MyExamsStrings.plural("home.examCount", count: count, locale: locale),
                style: .subtle
            )
            .accessibilityIdentifier("myExams.sectionCount")
        }
    }

    private var exploreButton: some View {
        Button {
            navigationHandler.openExploreCatalog()
        } label: {
            Text(MyExamsStrings.text("home.exploreExams", locale: locale))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.primaryTextColor)
                .padding(.horizontal, 6)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(MyExamsStrings.text("home.exploreExams", locale: locale))
        .accessibilityIdentifier("myExams.exploreExamsButton")
        .reportMyExamsFrame("myExams.exploreExamsButton")
    }

    private var emptyStateCard: some View {
        VStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 56, height: 56)

                Image(systemName: "doc.text")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(palette.textMutedColor)
            }
            .accessibilityHidden(true)

            Text(MyExamsStrings.text("home.emptyTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Text(MyExamsStrings.text("home.emptyDescription", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                navigationHandler.openExploreCatalog()
            } label: {
                Text(MyExamsStrings.text("home.exploreExams", locale: locale))
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
            .accessibilityLabel(MyExamsStrings.text("home.exploreExams", locale: locale))
            .accessibilityIdentifier("myExams.empty.exploreButton")
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
        .accessibilityIdentifier("myExams.emptyState")
    }

    private var errorStateCard: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(MyExamsStrings.text("home.errorTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)

            Text(MyExamsStrings.text("home.errorDescription", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                Task { await feed.retryFromError() }
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
            .accessibilityIdentifier("myExams.error.retryButton")
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
        .accessibilityIdentifier("myExams.errorState")
    }
}
