import SwiftUI

enum CatalogRoutes {
    @MainActor
    static func homeRoot(
        router: AppRouter? = nil,
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        let _ = router
        return PlaceholderDestinationView(
            destination: .home,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader
        )
    }

    @MainActor
    static func homeEmptyDetail(
        router: AppRouter? = nil,
        preferences: AppPreferences
    ) -> some View {
        let _ = router
        return PlaceholderDestinationView(
            destination: .home,
            locale: preferences.locale,
            showsBrandHeader: false
        )
    }

    @MainActor
    static func product(
        code: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .product(code: code),
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}
