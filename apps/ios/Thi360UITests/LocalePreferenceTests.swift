import XCTest

final class LocalePreferenceTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testFirstLaunchDefaultsToVietnameseRegardlessOfDeviceLanguageAndPersistsSwitch() {
        let suite = "vn.unicom.thi360.uitest.locale.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US",
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences"
        ]
        app.launch()

        XCTAssertTrue(app.staticTexts["Trang chủ"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Chưa có nội dung"].waitForExistence(timeout: 5))

        openDestination(titled: "Tài khoản", identifier: "shell.dest.account", in: app)
        XCTAssertTrue(app.staticTexts["Hiển thị"].waitForExistence(timeout: 5))

        let englishButton = app.buttons["shell.account.locale.en"]
        XCTAssertTrue(englishButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(englishButton.frame.width * 100) / 100, 44)
        XCTAssertGreaterThanOrEqual(round(englishButton.frame.height * 100) / 100, 44)

        let darkButton = app.buttons["shell.account.theme.dark"]
        XCTAssertTrue(darkButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(darkButton.frame.width * 100) / 100, 44)
        XCTAssertGreaterThanOrEqual(round(darkButton.frame.height * 100) / 100, 44)

        englishButton.tap()

        XCTAssertTrue(app.staticTexts["Account"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Display"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Hiển thị"].exists)

        openDestination(titled: "Home", identifier: "shell.dest.home", in: app)
        XCTAssertTrue(app.staticTexts["Home"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No content yet"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Chưa có nội dung"].exists)

        app.terminate()

        let relaunched = XCUIApplication()
        relaunched.launchArguments = [
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US",
            "-uitest-defaults-suite", suite
        ]
        relaunched.launch()

        XCTAssertTrue(relaunched.staticTexts["Home"].waitForExistence(timeout: 5))
        XCTAssertTrue(relaunched.staticTexts["No content yet"].waitForExistence(timeout: 5))
    }

    private func openDestination(titled title: String, identifier: String, in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons[title]
        if tabButton.waitForExistence(timeout: 2) {
            tabButton.tap()
            return
        }
        let button = app.buttons[identifier].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5), "Destination button \(identifier) not found")
        button.tap()
    }
}
