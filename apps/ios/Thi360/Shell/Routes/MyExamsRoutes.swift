import SwiftUI

enum MyExamsRoutes {
    @MainActor
    static func myExamsRoot(
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        PlaceholderDestinationView(
            destination: .myExams,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader
        )
    }

    @MainActor
    static func historyRoot(
        resetFilters: Bool,
        preferences: AppPreferences,
        showsBrandHeader: Bool
    ) -> some View {
        let _ = resetFilters
        return PlaceholderDestinationView(
            destination: .history,
            locale: preferences.locale,
            showsBrandHeader: showsBrandHeader
        )
    }
}
