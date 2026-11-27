import SwiftUI

enum MyExamsRoutes {
    @MainActor static var myExamsSpansBothPanesOnPhoneBook: Bool = true
    @MainActor static var historySpansBothPanesOnPhoneBook: Bool = true

    @MainActor
    static func myExamsRoot(
        router: AppRouter? = nil,
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        MyExamsRouteView(
            router: router,
            preferences: preferences,
            showsBrandHeader: showsBrandHeader
        )
    }

    @MainActor
    static func myExamsEmptyDetail(
        router: AppRouter? = nil,
        preferences: AppPreferences
    ) -> some View {
        let _ = (router, preferences)
        return Color.clear
    }

    @MainActor
    static func historyRoot(
        resetFilters: Bool,
        router: AppRouter? = nil,
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        HistoryRouteView(
            resetFilters: resetFilters,
            router: router,
            preferences: preferences,
            showsBrandHeader: showsBrandHeader
        )
    }

    @MainActor
    static func historyEmptyDetail(
        router: AppRouter? = nil,
        preferences: AppPreferences
    ) -> some View {
        let _ = (router, preferences)
        return Color.clear
    }
}

private struct MyExamsRouteView: View {
    let router: AppRouter?
    @Bindable var preferences: AppPreferences
    let showsBrandHeader: Bool

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @State private var fallbackRouter = AppRouter(initialDestination: .myExams)

    private var activeRouter: AppRouter {
        router ?? fallbackRouter
    }

    var body: some View {
        let feed = MyExamsFeedRegistry.shared.feed(
            for: activeRouter,
            sessionStore: sessionStore,
            connectivityMonitor: connectivityMonitor
        )
        MyExamsScreen(
            feed: feed,
            router: activeRouter,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader,
            supportContact: AppEnvironment.current.supportContact
        )
    }
}

private struct HistoryRouteView: View {
    let resetFilters: Bool
    let router: AppRouter?
    @Bindable var preferences: AppPreferences
    let showsBrandHeader: Bool

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.catalogOptionsCache) private var catalogOptionsCache
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @State private var fallbackRouter = AppRouter(initialDestination: .history)

    private var activeRouter: AppRouter {
        router ?? fallbackRouter
    }

    var body: some View {
        let feed = HistoryFeedRegistry.shared.feed(
            for: activeRouter,
            sessionStore: sessionStore,
            optionsCache: sessionStore == nil ? nil : catalogOptionsCache,
            connectivityMonitor: connectivityMonitor
        )
        HistoryScreen(
            feed: feed,
            router: activeRouter,
            resetFilters: resetFilters,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader
        )
    }
}
