import SwiftUI

@main
struct Thi360App: App {
    @UIApplicationDelegateAdaptor(Thi360AppDelegate.self) private var appDelegate

    @State private var preferences: AppPreferences
    @State private var router: AppRouter
    @State private var biometricManager: BiometricManager
    @State private var appearanceRepository: AppearanceRepository
    @State private var sessionStore: SessionStore

    init() {
        let preferences = AppPreferences.makeForCurrentProcess()
        let router = AppRouter.makeForCurrentProcess()
        let biometricManager = BiometricManager.makeForCurrentProcess()
        let appearanceRepository = AppearanceRepository.makeForCurrentProcess()
        let sessionStore = SessionStore.makeForCurrentProcess(
            preferences: preferences,
            biometricManager: biometricManager
        )
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
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(
                router: router,
                preferences: preferences,
                sessionStore: sessionStore,
                appearanceRepository: appearanceRepository,
                biometricManager: biometricManager
            )
            .onOpenURL { url in
                _ = router.handleIncomingURL(url)
            }
            .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { userActivity in
                _ = router.handleUserActivity(userActivity)
            }
        }
    }
}
