import SwiftUI

struct CatalogEmptyDetailView: View {
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.primaryContainerColor)
                    .frame(width: 56, height: 56)

                Image(systemName: "doc.text.magnifyingglass")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(palette.primaryTextColor)
            }
            .accessibilityHidden(true)

            Text(CatalogStrings.text("storefront.catalog.selectExamPlaceholder", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textSecondaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: 320)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(CatalogStrings.text("storefront.catalog.selectExamPlaceholder", locale: locale))
        .accessibilityIdentifier("catalog.emptyDetailPlaceholder")
    }
}

struct CatalogSkeletonView: View {
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 14) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 12) {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .aspectRatio(16 / 9, contentMode: .fit)

                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(width: 140, height: 14)

                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(maxWidth: .infinity, minHeight: 20, maxHeight: 20)

                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                }
                .padding(16)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(palette.borderColor, lineWidth: 1)
                )
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(CatalogStrings.text("storefront.catalog.loading", locale: locale))
        .accessibilityIdentifier("catalog.loadingSkeleton")
    }
}

struct CatalogListColumnView: View {
    @Bindable var feed: CatalogFeed
    let locale: AppLocale
    let showsBrandHeader: Bool
    let showsSelectionHighlight: Bool
    var imageLoader: ImageLoader? = nil
    let onSelectProduct: (String) -> Void
    let onAccessButtonTap: (AccessButtonKind) -> Void

    @Environment(\.brandPalette) private var palette
    @State private var isFilterSheetPresented: Bool = false
    @State private var visibleCardCodes: Set<String> = []
    @State private var pendingScrollRestoreCode: String?

    var headingText: String {
        CatalogStrings.text("storefront.catalog.title", locale: locale)
    }

    var showsFilterHeader: Bool {
        !feed.isEmptyUnfilteredCatalog
    }

    var staleBannerMessage: String {
        if feed.staleFailureKind == .offline {
            return AppStrings.common("common.offline.title", locale: locale)
        }
        return CatalogStrings.text("storefront.catalog.errorMessage", locale: locale)
    }

    var loadingSkeletonLabel: String {
        CatalogStrings.text("storefront.catalog.loading", locale: locale)
    }

    var emptyCatalogMessage: String {
        CatalogStrings.text("storefront.catalog.emptyTitle", locale: locale)
    }

    var noResultsMessages: (title: String, description: String, clearLabel: String) {
        (
            CatalogStrings.text("storefront.catalog.noResultsTitle", locale: locale),
            CatalogStrings.text("storefront.catalog.noResultsDescription", locale: locale),
            CatalogStrings.text("storefront.catalog.clearFilters", locale: locale)
        )
    }

    var serverErrorMessages: (message: String, retryLabel: String) {
        (
            CatalogStrings.text("storefront.catalog.errorMessage", locale: locale),
            AppStrings.common("common.retry", locale: locale)
        )
    }

    var offlineErrorMessages: (title: String, description: String, retryLabel: String) {
        (
            AppStrings.common("common.offline.title", locale: locale),
            AppStrings.common("common.offline.description", locale: locale),
            AppStrings.common("common.retry", locale: locale)
        )
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Color.clear
                        .frame(height: 1)
                        .id("catalog.scrollTop")

                    if showsBrandHeader {
                        BrandWordmarkHeader(locale: locale)
                    }

                    Text(CatalogStrings.text("storefront.catalog.title", locale: locale))
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("catalog.heading")

                    if !feed.isEmptyUnfilteredCatalog {
                        CatalogFilterHeaderView(
                            feed: feed,
                            locale: locale,
                            onOpenFilterSheet: { isFilterSheetPresented = true }
                        )
                    }

                    catalogBodyContent
                }
                .frame(maxWidth: 680, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }
            .refreshable {
                await feed.pullToRefresh()
            }
            .accessibilityIdentifier("catalog.listScrollView")
            .onAppear {
                if let saved = feed.lastScrolledCardCode,
                   feed.loadedCards.contains(where: { $0.code == saved })
                {
                    pendingScrollRestoreCode = saved
                    proxy.scrollTo(saved, anchor: .center)
                }
            }
            .task(id: pendingScrollRestoreCode) {
                guard let saved = pendingScrollRestoreCode else { return }
                try? await Task.sleep(nanoseconds: 80_000_000)
                guard !Task.isCancelled else { return }
                proxy.scrollTo(saved, anchor: .center)
                try? await Task.sleep(nanoseconds: 120_000_000)
                if !Task.isCancelled {
                    pendingScrollRestoreCode = nil
                }
            }
            .onChange(of: feed.scrollToTopToken) { _, _ in
                pendingScrollRestoreCode = nil
                proxy.scrollTo("catalog.scrollTop", anchor: .top)
            }
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .sheet(isPresented: $isFilterSheetPresented) {
            CatalogFilterSheetView(
                feed: feed,
                locale: locale,
                onDismiss: { isFilterSheetPresented = false }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("catalog.listColumn")
    }

    @ViewBuilder
    private var catalogBodyContent: some View {
        switch feed.loadState {
        case .loading:
            CatalogSkeletonView(locale: locale)

        case .empty:
            emptyUnfilteredCard

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
                catalogErrorCard(canRetry: canRetry)
            }

        case let .loaded(page, stale):
            VStack(alignment: .leading, spacing: 14) {
                if stale {
                    StaleBanner(
                        message: staleBannerMessage,
                        locale: locale,
                        onRetry: {
                            Task { await feed.retry() }
                        }
                    )
                }

                if page.cards.isEmpty && feed.query.isFiltered {
                    noResultsCard
                } else {
                    Text(
                        CatalogFormatting.showingLine(
                            displayedCount: page.cards.count,
                            total: page.total,
                            locale: locale
                        )
                    )
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("catalog.showingLine")

                    LazyVStack(spacing: 14) {
                        ForEach(Array(page.cards.enumerated()), id: \.element.code) { index, card in
                            CatalogProductCardView(
                                product: card,
                                accessCell: feed.accessCell(for: card.code),
                                locale: locale,
                                isSelected: showsSelectionHighlight && feed.selectedProductCode == card.code,
                                imageLoader: imageLoader,
                                onOpenDetail: { onSelectProduct(card.code) },
                                onAccessButtonTap: onAccessButtonTap,
                                onReloadAccess: {
                                    Task { await feed.reloadAccess(for: card.code) }
                                }
                            )
                            .id(card.code)
                            .onAppear {
                                visibleCardCodes.insert(card.code)
                                if pendingScrollRestoreCode == nil {
                                    feed.lastScrolledCardCode = card.code
                                }
                                if index >= max(0, page.cards.count - 2),
                                   page.nextPage != nil,
                                   page.tail == .idle
                                {
                                    Task { await feed.loadNextPageIfNeeded() }
                                }
                            }
                            .onDisappear {
                                visibleCardCodes.remove(card.code)
                                if pendingScrollRestoreCode == nil,
                                   let anchor = page.cards.first(where: { visibleCardCodes.contains($0.code) })
                                {
                                    feed.lastScrolledCardCode = anchor.code
                                }
                            }
                        }

                        if page.nextPage != nil && page.tail == .idle {
                            Color.clear
                                .frame(height: 1)
                                .id("catalog.loadMoreSentinel.\(page.cards.count)")
                                .onAppear {
                                    Task { await feed.loadNextPageIfNeeded() }
                                }
                        }
                    }

                    tailFooter(for: page)
                }
            }
        }
    }

    private var emptyUnfilteredCard: some View {
        VStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 56, height: 56)

                Image(systemName: "tray")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(palette.textMutedColor)
            }
            .accessibilityHidden(true)

            Text(CatalogStrings.text("storefront.catalog.emptyTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
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
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("catalog.emptyState")
    }

    private var noResultsCard: some View {
        VStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 52, height: 52)

                Image(systemName: "magnifyingglass")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(palette.textMutedColor)
            }
            .accessibilityHidden(true)

            Text(CatalogStrings.text("storefront.catalog.noResultsTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Text(CatalogStrings.text("storefront.catalog.noResultsDescription", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                Task { await feed.clearFilters() }
            } label: {
                Text(CatalogStrings.text("storefront.catalog.clearFilters", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .padding(.horizontal, 20)
                    .frame(minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("catalog.noResults.clearFiltersButton")
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
        .accessibilityIdentifier("catalog.noResultsState")
    }

    private func catalogErrorCard(canRetry: Bool) -> some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(CatalogStrings.text("storefront.catalog.errorMessage", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            if canRetry {
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
                .accessibilityIdentifier("catalog.error.retryButton")
            }
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
        .accessibilityIdentifier("catalog.errorState")
    }

    @ViewBuilder
    private func tailFooter(for page: CatalogPage) -> some View {
        switch page.tail {
        case .idle:
            EmptyView()

        case .loading:
            HStack(spacing: 10) {
                ProgressView()
                    .tint(palette.primaryTextColor)
                Text(AppStrings.common("common.loading", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("catalog.tail.loading")

        case let .failed(kind):
            VStack(spacing: 10) {
                Text(
                    kind == .offline
                        ? AppStrings.common("common.offline.title", locale: locale)
                        : CatalogStrings.text("storefront.catalog.errorMessage", locale: locale)
                )
                .font(.footnote)
                .foregroundStyle(palette.dangerTextColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

                Button {
                    Task { await feed.retryNextPage() }
                } label: {
                    Text(AppStrings.common("common.retry", locale: locale))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .padding(.horizontal, 18)
                        .frame(minHeight: 44)
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
                .accessibilityIdentifier("catalog.tail.retryButton")
            }
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("catalog.tail.error")
        }
    }
}

struct CatalogEmbeddedDetailView: View {
    let code: String
    let api: any CatalogAPIClientProtocol
    let connectivityMonitor: ConnectivityMonitor?
    let locale: AppLocale
    let supportContact: SupportContact
    let imageLoader: ImageLoader?
    let showsInlineBackBar: Bool
    let router: AppRouter
    let onBack: (() -> Void)?
    let onReturnToCatalog: () -> Void
    let onOpenExam: (String) -> Void
    let onOpenMyExams: () -> Void
    let onAccessButtonTap: (AccessButtonKind) -> Void
    let onBuyBundle: (ProductDetailModel) -> Void

    @State private var viewModel: ProductDetailViewModel

    init(
        code: String,
        api: any CatalogAPIClientProtocol,
        connectivityMonitor: ConnectivityMonitor?,
        locale: AppLocale,
        supportContact: SupportContact,
        imageLoader: ImageLoader?,
        showsInlineBackBar: Bool,
        router: AppRouter,
        onBack: (() -> Void)?,
        onReturnToCatalog: @escaping () -> Void,
        onOpenExam: @escaping (String) -> Void,
        onOpenMyExams: @escaping () -> Void,
        onAccessButtonTap: @escaping (AccessButtonKind) -> Void,
        onBuyBundle: @escaping (ProductDetailModel) -> Void
    ) {
        self.code = code
        self.api = api
        self.connectivityMonitor = connectivityMonitor
        self.locale = locale
        self.supportContact = supportContact
        self.imageLoader = imageLoader
        self.showsInlineBackBar = showsInlineBackBar
        self.router = router
        self.onBack = onBack
        self.onReturnToCatalog = onReturnToCatalog
        self.onOpenExam = onOpenExam
        self.onOpenMyExams = onOpenMyExams
        self.onAccessButtonTap = onAccessButtonTap
        self.onBuyBundle = onBuyBundle
        _viewModel = State(
            initialValue: ProductDetailViewModel(
                code: code,
                api: api,
                connectivityMonitor: connectivityMonitor
            )
        )
    }

    var body: some View {
        ProductDetailScreen(
            viewModel: viewModel,
            locale: locale,
            supportContact: supportContact,
            imageLoader: imageLoader,
            showsInlineBackBar: showsInlineBackBar,
            onBack: onBack,
            onReturnToCatalog: onReturnToCatalog,
            onOpenExam: onOpenExam,
            onOpenMyExams: onOpenMyExams,
            onAccessButtonTap: onAccessButtonTap,
            onBuyBundle: onBuyBundle
        )
        .onChange(of: router.path(for: .home).count) { oldCount, newCount in
            if newCount < oldCount && newCount == 0 {
                Task { await viewModel.refreshAccessOnReturn() }
            }
        }
        .onChange(of: router.presentedAttempt) { oldAttempt, newAttempt in
            if oldAttempt != nil && newAttempt == nil {
                Task { await viewModel.refreshAccessOnReturn() }
            }
        }
    }
}

struct CatalogHomeView: View {
    @Bindable var feed: CatalogFeed
    @Bindable var router: AppRouter
    let locale: AppLocale
    let showsBrandHeader: Bool
    let api: any CatalogAPIClientProtocol
    var supportContact: SupportContact = AppEnvironment.current.supportContact
    var imageLoader: ImageLoader? = nil
    var connectivityMonitor: ConnectivityMonitor? = nil

    @Environment(\.shellWidth) private var shellWidth
    @State private var selectionDebouncer = CatalogNavigationDebouncer()

    private var isPhoneTwoPaneLeftOnly: Bool {
        shellWidth == .wide && showsBrandHeader
    }

    private var navigationHandler: CatalogNavigationHandler {
        CatalogNavigationHandler(router: router)
    }

    var body: some View {
        Group {
            if isPhoneTwoPaneLeftOnly {
                CatalogListColumnView(
                    feed: feed,
                    locale: locale,
                    showsBrandHeader: showsBrandHeader,
                    showsSelectionHighlight: true,
                    imageLoader: imageLoader,
                    onSelectProduct: handleSelectProduct,
                    onAccessButtonTap: { navigationHandler.handleAccessButton($0) }
                )
            } else {
                ListDetailLayout(
                    selection: $feed.selectedProductCode,
                    listWidthFraction: 0.42
                ) {
                    CatalogListColumnView(
                        feed: feed,
                        locale: locale,
                        showsBrandHeader: showsBrandHeader,
                        showsSelectionHighlight: shellWidth == .twoColumn,
                        imageLoader: imageLoader,
                        onSelectProduct: handleSelectProduct,
                        onAccessButtonTap: { navigationHandler.handleAccessButton($0) }
                    )
                } detail: { selectedCode in
                    CatalogEmbeddedDetailView(
                        code: selectedCode,
                        api: api,
                        connectivityMonitor: connectivityMonitor,
                        locale: locale,
                        supportContact: supportContact,
                        imageLoader: imageLoader,
                        showsInlineBackBar: shellWidth != .twoColumn,
                        router: router,
                        onBack: { feed.selectedProductCode = nil },
                        onReturnToCatalog: {
                            navigationHandler.returnToCatalog {
                                feed.selectedProductCode = nil
                            }
                        },
                        onOpenExam: { examCode in
                            navigationHandler.openProductRoute(code: examCode)
                        },
                        onOpenMyExams: {
                            navigationHandler.openMyExamsTab()
                        },
                        onAccessButtonTap: { button in
                            navigationHandler.handleAccessButton(button)
                        },
                        onBuyBundle: { bundle in
                            navigationHandler.handleAccessButton(
                                .buy(code: bundle.code, seenVersionNo: bundle.versionNo, seenPrice: Int(bundle.price))
                            )
                        }
                    )
                    .id(selectedCode)
                } emptyDetail: {
                    CatalogEmptyDetailView(locale: locale)
                }
            }
        }
        .task(id: feed.accountId) {
            await feed.loadInitialIfNeeded()
        }
        .onChange(of: feed.selectedProductCode) { oldCode, newCode in
            if oldCode != nil && newCode == nil {
                Task { await feed.refreshAccessOnReturn() }
            }
        }
        .onChange(of: router.selectedDestination) { oldDestination, newDestination in
            if oldDestination != .home && newDestination == .home {
                Task { await feed.refreshAccessOnReturn() }
            }
        }
        .onChange(of: router.path(for: .home).count) { oldCount, newCount in
            if newCount < oldCount && newCount == 0 {
                Task { await feed.refreshAccessOnReturn() }
            }
        }
        .onChange(of: router.presentedAttempt) { oldAttempt, newAttempt in
            if oldAttempt != nil && newAttempt == nil && router.selectedDestination == .home {
                Task { await feed.refreshAccessOnReturn() }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("catalog.homeView")
    }

    private func handleSelectProduct(_ code: String) {
        guard selectionDebouncer.shouldAllow(key: "select:\(code)") else { return }
        feed.lastScrolledCardCode = code
        if feed.selectedProductCode != code {
            feed.selectedProductCode = code
        }
    }
}
