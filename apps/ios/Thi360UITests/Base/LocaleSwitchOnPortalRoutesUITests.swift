import XCTest

final class LocaleSwitchOnPortalRoutesUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSwitchingLocaleUpdatesPortalSubRouteTitleImmediatelyAndKeepsServerParameters() {
        let suite = "vn.unicom.thi360.uitest.portallocale.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "product/ielts-2026"
        ]
        app.launch()

        let title = app.staticTexts["route.placeholder.title"].firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 8))
        XCTAssertEqual(title.label, "Chi tiết bài thi")
        XCTAssertEqual(app.staticTexts["route.placeholder.param.code"].firstMatch.label, "ielts-2026")

        openDestination(titled: "Tài khoản", identifier: "shell.dest.account", in: app)
        let englishButton = app.buttons["shell.account.locale.en"].firstMatch
        XCTAssertTrue(englishButton.waitForExistence(timeout: 5))
        if !englishButton.isHittable {
            app.swipeUp()
        }
        englishButton.tap()

        openDestination(titled: "Home", identifier: "shell.dest.home", in: app)

        let updatedTitle = app.staticTexts["route.placeholder.title"].firstMatch
        XCTAssertTrue(updatedTitle.waitForExistence(timeout: 5))
        XCTAssertEqual(updatedTitle.label, "Product details")
        XCTAssertEqual(
            app.staticTexts["route.placeholder.param.code"].firstMatch.label,
            "ielts-2026",
            "Route server parameter must remain unchanged after locale switch"
        )
        XCTAssertFalse(app.textFields["login.identifierField"].exists, "Locale switch must not sign out")
    }

    private func openDestination(titled title: String, identifier: String, in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons[title].firstMatch
        if tabButton.waitForExistence(timeout: 2) {
            tabButton.tap()
            return
        }
        let button = app.buttons[identifier].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()
    }
}
