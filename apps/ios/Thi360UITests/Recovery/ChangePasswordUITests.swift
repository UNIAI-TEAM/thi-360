import XCTest

final class ChangePasswordUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
    }

    func testSurvivesSizeClassChange() {
        let suite = "vn.unicom.thi360.uitest.changepw.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-open-change-password",
            "-uitest-state-probe"
        ]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["Đổi mật khẩu"].waitForExistence(timeout: 8),
            "Change password form must open"
        )

        app.buttons["password.oldToggle"].tap()
        app.buttons["password.newToggle"].tap()
        app.buttons["password.confirmToggle"].tap()

        let oldText = "OldSecret1!"
        let newText = "NewSecret2@"
        let confirmText = "NewSecret2@"

        let oldField = app.textFields["password.oldField"]
        XCTAssertTrue(oldField.waitForExistence(timeout: 5))
        oldField.tap()
        oldField.typeText(oldText)

        let newField = app.textFields["password.newField"]
        XCTAssertTrue(newField.waitForExistence(timeout: 5))
        newField.tap()
        newField.typeText(newText)

        let confirmField = app.textFields["password.confirmField"]
        if !confirmField.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(confirmField.waitForExistence(timeout: 5))
        confirmField.tap()
        confirmField.typeText(confirmText)

        assertFieldsEqual(in: app, old: oldText, new: newText, confirm: confirmText)

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["Đổi mật khẩu"].waitForExistence(timeout: 5))
        assertFieldsEqual(in: app, old: oldText, new: newText, confirm: confirmText)

        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.staticTexts["Đổi mật khẩu"].waitForExistence(timeout: 5))
        assertFieldsEqual(in: app, old: oldText, new: newText, confirm: confirmText)

        let toggleButton = app.buttons["changePassword.probe.toggleSizeClass"]
        if !toggleButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(toggleButton.waitForExistence(timeout: 5))
        let initialLayout = (toggleButton.value as? String) ?? ""
        XCTAssertTrue(
            initialLayout == "compact" || initialLayout == "regular",
            "Expected compact or regular layout value, got: '\(initialLayout)'"
        )
        let expectedFirstLayout = (initialLayout == "compact") ? "regular" : "compact"
        toggleButton.tap()

        let switchedFirst = NSPredicate(format: "value == %@", expectedFirstLayout)
        expectation(for: switchedFirst, evaluatedWith: app.buttons["changePassword.probe.toggleSizeClass"])
        waitForExpectations(timeout: 5)
        XCTAssertTrue(
            app.descendants(matching: .any)["password.layout.\(expectedFirstLayout)"].waitForExistence(timeout: 5),
            "Change password layout must switch from \(initialLayout) to \(expectedFirstLayout)"
        )

        assertFieldsEqual(in: app, old: oldText, new: newText, confirm: confirmText)

        let toggleBack = app.buttons["changePassword.probe.toggleSizeClass"]
        if !toggleBack.isHittable {
            app.swipeUp()
        }
        toggleBack.tap()

        let switchedBack = NSPredicate(format: "value == %@", initialLayout)
        expectation(for: switchedBack, evaluatedWith: app.buttons["changePassword.probe.toggleSizeClass"])
        waitForExpectations(timeout: 5)
        XCTAssertTrue(
            app.descendants(matching: .any)["password.layout.\(initialLayout)"].waitForExistence(timeout: 5),
            "Change password layout must switch back to \(initialLayout)"
        )
        assertFieldsEqual(in: app, old: oldText, new: newText, confirm: confirmText)
    }

    private func assertFieldsEqual(
        in app: XCUIApplication,
        old: String,
        new: String,
        confirm: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let oldField = app.textFields["password.oldField"]
        let newField = app.textFields["password.newField"]
        let confirmField = app.textFields["password.confirmField"]

        XCTAssertTrue(oldField.waitForExistence(timeout: 5), file: file, line: line)
        XCTAssertEqual(oldField.value as? String, old, "Current password input must survive layout change", file: file, line: line)
        XCTAssertEqual(newField.value as? String, new, "New password input must survive layout change", file: file, line: line)
        XCTAssertEqual(confirmField.value as? String, confirm, "Confirm password input must survive layout change", file: file, line: line)
    }
}
