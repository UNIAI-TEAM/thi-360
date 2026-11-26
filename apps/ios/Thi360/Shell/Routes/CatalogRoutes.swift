import SwiftUI

enum CatalogRoutes {
    @MainActor
    static func homeRoot(
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        PlaceholderDestinationView(
            destination: .home,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader
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
