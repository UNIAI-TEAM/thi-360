import SwiftUI

enum PaymentRoutes {
    @MainActor
    static func checkout(
        code: String,
        seenVersionNo: Int?,
        seenPrice: Int?,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .checkout(code: code, seenVersionNo: seenVersionNo, seenPrice: seenPrice),
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func paymentResult(
        target: PaymentResultTarget,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .paymentResult(target),
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func orders(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .orders,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func order(
        orderCode: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        RoutePlaceholderView(
            route: .order(orderCode: orderCode),
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}
