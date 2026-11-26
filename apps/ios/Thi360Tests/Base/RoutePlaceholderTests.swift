import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RoutePlaceholderTests: XCTestCase {
    func testEveryRouteRendersPlaceholderWithTypedParameters() {
        let suite = "vn.unicom.thi360.tests.routeplaceholder.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let preferences = AppPreferences(userDefaults: defaults)
        let router = AppRouter()

        let routes: [AppRoute] = [
            .catalog,
            .product(code: "ielts-2026"),
            .checkout(code: "ielts-2026", seenVersionNo: 3, seenPrice: 150_000),
            .paymentResult(.byOrder(orderCode: "ORD-123")),
            .paymentResult(.byReturnLink(URL(string: "https://student.thi360.test/payment/return")!)),
            .orders,
            .order(orderCode: "ORD-123"),
            .myExams,
            .history(resetFilters: true),
            .prepare(entitlementId: "ent-99"),
            .attempt(attemptId: "att-99"),
            .result(attemptId: "att-99"),
            .review(attemptId: "att-99"),
            .profile,
            .profileVerifyEmail,
            .deleteAccount,
            .legal(.terms),
            .legal(.privacy)
        ]

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        defer { window.isHidden = true }

        for route in routes {
            let view = NavigationStack {
                AppRouteView(
                    route: route,
                    destination: .home,
                    router: router,
                    preferences: preferences
                )
            }
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

            let controller = UIHostingController(rootView: view)
            window.rootViewController = controller
            window.makeKeyAndVisible()
            window.layoutIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.02))

            XCTAssertGreaterThan(controller.view.bounds.width, 0, "Route \(route.logicalName) must lay out")
        }

        XCTAssertEqual(
            AppRoute.checkout(code: "ielts-2026", seenVersionNo: 3, seenPrice: 150_000).parameterSummary.map(\.value),
            ["ielts-2026", "3", "150000"]
        )
        XCTAssertEqual(
            AppRoute.prepare(entitlementId: "ent-99").parameterSummary.map(\.value),
            ["ent-99"]
        )
        XCTAssertEqual(
            AppRoute.order(orderCode: "ORD-123").parameterSummary.map(\.value),
            ["ORD-123"]
        )
    }
}
