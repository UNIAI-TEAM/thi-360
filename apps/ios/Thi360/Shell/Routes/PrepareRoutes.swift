import SwiftUI

enum PrepareRoutes {
    @MainActor
    static func prepare(
        entitlementId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .prepare(entitlementId: entitlementId),
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}
