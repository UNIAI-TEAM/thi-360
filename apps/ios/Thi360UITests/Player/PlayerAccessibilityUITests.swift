import Foundation
import XCTest

final class PlayerAccessibilityUITests: XCTestCase {
    private var server: PlayerUITestFixtureServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testDynamicType130And200LabelsTouchTargetsAndScrollability() throws {
        let mock = try PlayerUITestFixtureServer(attemptId: "00000000-0000-7000-8000-000000000501")
        self.server = mock

        let categories = [
            "UICTContentSizeCategoryXXXL",
            "UICTContentSizeCategoryAccessibilityLarge"
        ]

        for category in categories {
            let suite = "vn.unicom.thi360.uitest.playera11y.\(UUID().uuidString)"
            let app = XCUIApplication()
            app.launchArguments = [
                "-uitest-defaults-suite", suite,
                "-uitest-reset-preferences",
                "-uitest-authenticated",
                "-uitest-destination", "myExams",
                "-uitest-route", "attempt/\(mock.attemptId)",
                "-uitest-api-base-url", mock.baseURL,
                "-UIPreferredContentSizeCategoryName", category
            ]
            app.launch()

            let topBar = app.otherElements["player.topBar"].firstMatch
            XCTAssertTrue(topBar.waitForExistence(timeout: 10), "Top bar must appear at \(category)")

            let backBtn = app.buttons["player.topBar.backButton"].firstMatch
            XCTAssertTrue(backBtn.exists)
            XCTAssertFalse(backBtn.label.isEmpty)
            XCTAssertGreaterThanOrEqual(backBtn.frame.height, 43.0, "Back button height < 44pt at \(category)")

            let collapseBtn = app.buttons["player.topBar.collapseToggle"].firstMatch
            XCTAssertTrue(collapseBtn.exists)
            XCTAssertFalse(collapseBtn.label.isEmpty)
            XCTAssertGreaterThanOrEqual(collapseBtn.frame.height, 43.0, "Collapse button height < 44pt at \(category)")

            let actionBtn = app.buttons["player.topBar.actionButton"].firstMatch
            XCTAssertTrue(actionBtn.waitForExistence(timeout: 5))
            XCTAssertFalse(actionBtn.label.isEmpty)
            XCTAssertGreaterThanOrEqual(actionBtn.frame.height, 43.0, "Action button height < 44pt at \(category)")

            let timerEl = app.otherElements["player.topBar.timer"].firstMatch
            XCTAssertTrue(timerEl.waitForExistence(timeout: 5))
            XCTAssertFalse(timerEl.label.isEmpty, "Timer must expose VoiceOver label at \(category)")

            let openGridBtn = app.buttons["player.bottomBar.openGrid"].firstMatch
            if openGridBtn.waitForExistence(timeout: 4) {
                XCTAssertFalse(openGridBtn.label.isEmpty)
                XCTAssertGreaterThanOrEqual(openGridBtn.frame.height, 43.0)
            }

            let content = app.scrollViews.firstMatch
            XCTAssertTrue(content.waitForExistence(timeout: 5))
            content.swipeUp()
            app.terminate()
        }
    }
}
