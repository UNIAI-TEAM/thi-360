import SwiftUI

enum ResultRoutes {
    @MainActor
    static func result(
        attemptId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .result(attemptId: attemptId),
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func review(
        attemptId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .review(attemptId: attemptId),
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}
