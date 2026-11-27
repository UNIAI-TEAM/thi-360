import SwiftUI

enum ProfileRoutes {
    @MainActor
    static func profile(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .profile,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func profileVerifyEmail(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .profileVerifyEmail,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func deleteAccount(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .deleteAccount,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}
