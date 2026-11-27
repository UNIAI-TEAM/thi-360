import SwiftUI

enum MyExamsRoutes {
    @MainActor
    static func myExamsRoot(
        router: AppRouter? = nil,
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        let _ = router
        return PlaceholderDestinationView(
            destination: .myExams,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader
        )
    }

    @MainActor
    static func myExamsEmptyDetail(
        router: AppRouter? = nil,
        preferences: AppPreferences
    ) -> some View {
        let _ = router
        return PlaceholderDestinationView(
            destination: .myExams,
            locale: preferences.locale,
            showsBrandHeader: false
        )
    }

    @MainActor
    static func historyRoot(
        resetFilters: Bool,
        router: AppRouter? = nil,
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        let _ = (resetFilters, router)
        return PlaceholderDestinationView(
            destination: .history,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader
        )
    }

    @MainActor
    static func historyEmptyDetail(
        router: AppRouter? = nil,
        preferences: AppPreferences
    ) -> some View {
        let _ = router
        return PlaceholderDestinationView(
            destination: .history,
            locale: preferences.locale,
            showsBrandHeader: false
        )
    }
}
