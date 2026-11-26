import XCTest

final class TabBackStackUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSubRouteDraftAndScrollSurviveSwitchingTabs() {
        let suite = "vn.unicom.thi360.uitest.tabstack.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "product/ielts-01"
        ]
        app.launch()

        let paramText = app.staticTexts["route.placeholder.param.code"].firstMatch
        XCTAssertTrue(paramText.waitForExistence(timeout: 8), "Expected product/ielts-01 route placeholder")
        XCTAssertEqual(paramText.label, "ielts-01")

        let draftField = app.textFields["route.placeholder.draftField"].firstMatch
        XCTAssertTrue(draftField.waitForExistence(timeout: 5))
        draftField.tap()
        let draftSample = "Draft on product route"
        draftField.typeText(draftSample + "\n")
        XCTAssertEqual(draftField.value as? String, draftSample)

        let title = app.staticTexts["route.placeholder.title"].firstMatch
        if title.exists && title.isHittable {
            title.tap()
        }
        app.swipeUp()

        openDestination(titled: "Tài khoản", identifier: "shell.dest.account", in: app)
        XCTAssertTrue(app.staticTexts["shell.account.heading"].firstMatch.waitForExistence(timeout: 5))

        openDestination(titled: "Trang chủ", identifier: "shell.dest.home", in: app)

        let restoredParam = app.staticTexts["route.placeholder.param.code"].firstMatch
        XCTAssertTrue(restoredParam.waitForExistence(timeout: 5), "Returning to Home must restore pushed product route")
        XCTAssertEqual(restoredParam.label, "ielts-01")

        let restoredDraft = app.textFields["route.placeholder.draftField"].firstMatch
        if !restoredDraft.exists {
            app.swipeDown()
            app.swipeDown()
        }
        XCTAssertTrue(restoredDraft.waitForExistence(timeout: 5))
        XCTAssertEqual(restoredDraft.value as? String, draftSample)
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
