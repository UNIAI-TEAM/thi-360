import XCTest

final class PortalStateRestorationUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
    }

    func testPortalRoutePreservesParametersDraftAndScrollAcrossRotationAndSizeClassToggle() {
        let suite = "vn.unicom.thi360.uitest.portalstate.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-state-probe",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "attempt/att-99"
        ]
        app.launch()

        let paramText = app.staticTexts["route.placeholder.param.attemptId"].firstMatch
        XCTAssertTrue(paramText.waitForExistence(timeout: 8))
        XCTAssertEqual(paramText.label, "att-99")

        let draftField = app.textFields["route.placeholder.draftField"].firstMatch
        XCTAssertTrue(draftField.waitForExistence(timeout: 5))
        let sample = "Portal draft 99"
        enterText(sample, into: draftField, in: app)
        XCTAssertEqual(draftField.value as? String, sample)

        let title = app.staticTexts["route.placeholder.title"].firstMatch
        if title.isHittable {
            title.tap()
        }

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["route.placeholder.param.attemptId"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["route.placeholder.param.attemptId"].firstMatch.label, "att-99")
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)

        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.staticTexts["route.placeholder.param.attemptId"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)

        let toggleButton = app.buttons["route.placeholder.toggleSizeClass"].firstMatch
        XCTAssertTrue(toggleButton.waitForExistence(timeout: 5))
        toggleButton.tap()

        XCTAssertTrue(app.staticTexts["route.placeholder.param.attemptId"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["route.placeholder.param.attemptId"].firstMatch.label, "att-99")
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)

        let toggleBack = app.buttons["route.placeholder.toggleSizeClass"].firstMatch
        XCTAssertTrue(toggleBack.waitForExistence(timeout: 5))
        toggleBack.tap()

        XCTAssertTrue(app.staticTexts["route.placeholder.param.attemptId"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)
    }

    private func enterText(_ text: String, into field: XCUIElement, in app: XCUIApplication) {
        field.tap()
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 3.0)
        field.typeText(text)
        if (field.value as? String) == text {
            return
        }
        field.tap()
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 2.0)
        let currentLength = ((field.value as? String) ?? "").count
        if currentLength > 0 {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentLength + 4))
        }
        field.typeText(text)
    }
}
