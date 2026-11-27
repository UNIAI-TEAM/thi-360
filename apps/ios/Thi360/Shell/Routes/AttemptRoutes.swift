import SwiftUI

enum AttemptRoutes {
    @MainActor
    static func attempt(
        attemptId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        NavigationStack {
            RoutePlaceholderView(
                route: .attempt(attemptId: attemptId),
                destination: destination,
                router: router,
                preferences: preferences,
                showsCloseAttemptButton: true
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.chromeless.attempt")
    }
}
