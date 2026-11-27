import SwiftUI

enum CatalogRoutes {
    @MainActor static var spansBothPanesOnPhoneBook: Bool = false

    @MainActor
    static func homeRoot(
        router: AppRouter? = nil,
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        CatalogHomeRouteView(
            router: router,
            preferences: preferences,
            showsBrandHeader: showsBrandHeader
        )
    }

    @MainActor
    static func homeEmptyDetail(
        router: AppRouter? = nil,
        preferences: AppPreferences
    ) -> some View {
        CatalogDuoDetailRouteView(
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func product(
        code: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        CatalogProductRouteView(
            code: code,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}

private struct CatalogHomeRouteView: View {
    let router: AppRouter?
    @Bindable var preferences: AppPreferences
    let showsBrandHeader: Bool

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.imageLoader) private var imageLoader
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @State private var fallbackRouter = AppRouter()

    private var activeRouter: AppRouter {
        router ?? fallbackRouter
    }

    var body: some View {
        let feed = CatalogFeedRegistry.shared.feed(
            for: activeRouter,
            sessionStore: sessionStore,
            connectivityMonitor: connectivityMonitor
        )

        CatalogHomeView(
            feed: feed,
            router: activeRouter,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader,
            supportContact: AppEnvironment.current.supportContact,
            imageLoader: imageLoader
        )
    }
}

private struct CatalogDuoDetailRouteView: View {
    let router: AppRouter?
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.imageLoader) private var imageLoader
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @State private var fallbackRouter = AppRouter()

    private var activeRouter: AppRouter {
        router ?? fallbackRouter
    }

    var body: some View {
        let feed = CatalogFeedRegistry.shared.feed(
            for: activeRouter,
            sessionStore: sessionStore,
            connectivityMonitor: connectivityMonitor
        )
        let navigationHandler = CatalogNavigationHandler(router: activeRouter)

        Group {
            if let selectedCode = feed.selectedProductCode {
                CatalogEmbeddedDetailView(
                    viewModel: feed.detailViewModel(for: selectedCode),
                    locale: preferences.locale,
                    supportContact: AppEnvironment.current.supportContact,
                    imageLoader: imageLoader,
                    showsInlineBackBar: false,
                    router: activeRouter,
                    onBack: nil,
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
            } else {
                CatalogEmptyDetailView(locale: preferences.locale)
            }
        }
    }
}

private struct CatalogProductRouteView: View {
    let code: String
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.imageLoader) private var imageLoader
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @State private var viewModel: ProductDetailViewModel?
    @State private var boundSessionIdentifier: ObjectIdentifier?
    @State private var routeDepth: Int = 0

    private var navigationHandler: CatalogNavigationHandler {
        CatalogNavigationHandler(router: router)
    }

    var body: some View {
        Group {
            if let viewModel {
                ProductDetailScreen(
                    viewModel: viewModel,
                    locale: preferences.locale,
                    supportContact: AppEnvironment.current.supportContact,
                    imageLoader: imageLoader,
                    showsInlineBackBar: false,
                    onBack: nil,
                    onReturnToCatalog: {
                        navigationHandler.returnToCatalog {
                            CatalogFeedRegistry.shared.existingFeed(for: router)?.selectedProductCode = nil
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
                .navigationTitle(viewModel.productName ?? "")
                .navigationBarTitleDisplayMode(.inline)
            } else {
                Color.clear
            }
        }
        .onAppear {
            routeDepth = router.path(for: destination).count
            ensureViewModel()
        }
        .onChange(of: router.path(for: destination).count) { oldCount, newCount in
            if oldCount > routeDepth && newCount == routeDepth {
                Task { await viewModel?.refreshAccessOnReturn() }
            }
        }
        .onChange(of: router.presentedAttempt) { oldAttempt, newAttempt in
            if oldAttempt != nil && newAttempt == nil {
                Task { await viewModel?.refreshAccessOnReturn() }
            }
        }
    }

    private func ensureViewModel() {
        let sessionIdentifier = sessionStore.map(ObjectIdentifier.init)
        if viewModel != nil && boundSessionIdentifier == sessionIdentifier {
            return
        }
        viewModel?.cancelAllTasks()
        boundSessionIdentifier = sessionIdentifier
        let api: any CatalogAPIClientProtocol = sessionStore.map { CatalogAPI(apiClient: $0.apiClient) }
            ?? PreviewEmptyCatalogAPI()
        viewModel = ProductDetailViewModel(
            code: code,
            api: api,
            connectivityMonitor: connectivityMonitor
        )
    }
}
