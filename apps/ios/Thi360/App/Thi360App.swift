import SwiftUI

@main
struct Thi360App: App {
    @UIApplicationDelegateAdaptor(Thi360AppDelegate.self) private var appDelegate

    @State private var preferences: AppPreferences
    @State private var router: AppRouter
    @State private var biometricManager: BiometricManager
    @State private var appearanceRepository: AppearanceRepository
    @State private var sessionStore: SessionStore
    @State private var connectivityMonitor: ConnectivityMonitor
    private let sessionCleanup: SessionCleanup
    private let imageLoader: ImageLoader
    private let catalogOptionsCache: CatalogOptionsCache

    init() {
        let preferences = AppPreferences.makeForCurrentProcess()
        let router = AppRouter.makeForCurrentProcess()
        let biometricManager = BiometricManager.makeForCurrentProcess()
        let appearanceRepository = AppearanceRepository.makeForCurrentProcess()
        let storeProvider = AccountScopedStoreProvider()
        let sessionCleanup = SessionCleanup(storeProvider: storeProvider)
        let sessionStore = SessionStore.makeForCurrentProcess(
            preferences: preferences,
            biometricManager: biometricManager,
            storeProvider: storeProvider,
            sessionCleanup: sessionCleanup
        )
        let imageLoader = ImageLoader(
            apiBaseURL: sessionStore.apiClient.serverURL,
            tokenStore: sessionStore.tokenStore,
            refresher: sessionStore.apiClient.refresher
        )
        let catalogOptionsCache = CatalogOptionsCache(apiClient: sessionStore.apiClient)
        let connectivityMonitor = ConnectivityMonitor()
        connectivityMonitor.start()

        sessionStore.onSessionCleared = {
            router.resetForSessionCleared()
        }
        sessionStore.onSessionReplaced = {
            router.resetForSessionReplaced()
        }

        _preferences = State(initialValue: preferences)
        _router = State(initialValue: router)
        _biometricManager = State(initialValue: biometricManager)
        _appearanceRepository = State(initialValue: appearanceRepository)
        _sessionStore = State(initialValue: sessionStore)
        _connectivityMonitor = State(initialValue: connectivityMonitor)
        self.sessionCleanup = sessionCleanup
        self.imageLoader = imageLoader
        self.catalogOptionsCache = catalogOptionsCache
    }

    var body: some Scene {
        WindowGroup {
            StartupCleanupGateView(
                sessionCleanup: sessionCleanup,
                participants: [imageLoader]
            ) {
                AppRootView(
                    router: router,
                    preferences: preferences,
                    sessionStore: sessionStore,
                    appearanceRepository: appearanceRepository,
                    biometricManager: biometricManager,
                    imageLoader: imageLoader,
                    catalogOptionsCache: catalogOptionsCache,
                    connectivityMonitor: connectivityMonitor
                )
            }
            .onOpenURL { url in
                _ = router.handleIncomingURL(url)
            }
            .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { userActivity in
                _ = router.handleUserActivity(userActivity)
            }
        }
    }
}

struct StartupCleanupGateView<Content: View>: View {
    let sessionCleanup: SessionCleanup
    let participants: [any SessionCleanupParticipant]
    @ViewBuilder let content: () -> Content

    @State private var isReady = false

    var body: some View {
        Group {
            if isReady {
                content()
            } else {
                Color.clear
                    .accessibilityIdentifier("app.startupCleanupGate")
            }
        }
        .task {
            guard !isReady else { return }
            for participant in participants {
                await sessionCleanup.register(participant)
            }
            await sessionCleanup.runAppStart()
            isReady = true
        }
    }
}
