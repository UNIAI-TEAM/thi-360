import SwiftUI

@main
struct Thi360App: App {
    @UIApplicationDelegateAdaptor(Thi360AppDelegate.self) private var appDelegate

    @State private var preferences: AppPreferences
    @State private var router: AppRouter
    @State private var sessionStore: SessionStore

    init() {
        let preferences = AppPreferences.makeForCurrentProcess()
        let router = AppRouter.makeForCurrentProcess()
        let sessionStore = SessionStore.makeForCurrentProcess(preferences: preferences)
        _preferences = State(initialValue: preferences)
        _router = State(initialValue: router)
        _sessionStore = State(initialValue: sessionStore)
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(
                router: router,
                preferences: preferences,
                sessionStore: sessionStore
            )
        }
    }
}
