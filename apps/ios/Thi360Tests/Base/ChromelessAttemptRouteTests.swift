import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ChromelessAttemptRouteTests: XCTestCase {
    func testAttemptRouteHidesNavigationChromeAcrossAllThreeLayoutsAndRestoresOnClose() {
        struct LayoutCase {
            let name: String
            let idiom: UIUserInterfaceIdiom
            let size: CGSize
            let sizeClass: UIUserInterfaceSizeClass
            let expectedShellIdentifier: String
        }

        let cases: [LayoutCase] = [
            LayoutCase(
                name: "iPhone compact tab bar",
                idiom: .phone,
                size: CGSize(width: 390, height: 844),
                sizeClass: .compact,
                expectedShellIdentifier: "shell.layout.compactTabBar"
            ),
            LayoutCase(
                name: "iPad sidebar",
                idiom: .pad,
                size: CGSize(width: 1180, height: 820),
                sizeClass: .regular,
                expectedShellIdentifier: "shell.layout.iPadSidebar"
            ),
            LayoutCase(
                name: "iPhone Duo two-pane book",
                idiom: .phone,
                size: CGSize(width: 951, height: 669),
                sizeClass: .regular,
                expectedShellIdentifier: "shell.layout.phoneTwoPaneBook"
            )
        ]

        for item in cases {
            let suite = "vn.unicom.thi360.tests.chromeless.\(UUID().uuidString)"
            let defaults = UserDefaults(suiteName: suite)!
            defer { defaults.removePersistentDomain(forName: suite) }

            let preferences = AppPreferences(userDefaults: defaults)
            let router = AppRouter(initialDestination: .myExams)
            router.openAttempt(attemptId: "att-100")

            let rootView = AppRootView(
                router: router,
                preferences: preferences,
                idiomOverride: item.idiom
            )
            let (window, controller) = RecoveryViewTestHelper.host(
                rootView,
                size: item.size,
                sizeClass: item.sizeClass,
                themeMode: .light
            )
            defer { window.isHidden = true }

            XCTAssertTrue(
                RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: controller.view).isEmpty,
                "\(item.name): tab bar must not appear during attempt"
            )
            XCTAssertFalse(
                containsAccessibilityIdentifier(item.expectedShellIdentifier, in: controller.view),
                "\(item.name): shell chrome \(item.expectedShellIdentifier) must be hidden during attempt"
            )
            XCTAssertFalse(
                containsAccessibilityIdentifier("shell.ipad.sidebar", in: controller.view),
                "\(item.name): iPad sidebar must be hidden during attempt"
            )
            XCTAssertFalse(
                containsAccessibilityIdentifier("shell.duo.leftTabBar", in: controller.view),
                "\(item.name): Duo left tab bar must be hidden during attempt"
            )

            router.openResultFromAttempt(attemptId: "att-100")
            window.layoutIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.05))

            XCTAssertNil(router.presentedAttempt)
            XCTAssertEqual(router.selectedDestination, .myExams)
            XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-100")])
        }
    }

    private func containsAccessibilityIdentifier(_ identifier: String, in view: UIView) -> Bool {
        if view.accessibilityIdentifier == identifier {
            return true
        }
        return view.subviews.contains { containsAccessibilityIdentifier(identifier, in: $0) }
    }
}
