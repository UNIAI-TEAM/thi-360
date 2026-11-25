import SwiftUI

@main
struct Thi360App: App {
    @UIApplicationDelegateAdaptor(Thi360AppDelegate.self) private var appDelegate

    @State private var preferences = AppPreferences.makeForCurrentProcess()
    @State private var router = AppRouter.makeForCurrentProcess()

    var body: some Scene {
        WindowGroup {
            AppRootView(router: router, preferences: preferences)
        }
    }
}
