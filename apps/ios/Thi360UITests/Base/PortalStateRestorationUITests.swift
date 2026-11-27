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
            "-uitest-route", "order/ord-99"
        ]
        app.launch()

        let paramText = app.staticTexts["route.placeholder.param.orderCode"].firstMatch
        XCTAssertTrue(paramText.waitForExistence(timeout: 8))
        XCTAssertEqual(paramText.label, "ord-99")

        let draftField = app.textFields["route.placeholder.draftField"].firstMatch
        XCTAssertTrue(draftField.waitForExistence(timeout: 5))
        draftField.tap()
        let sample = "Portal draft 99"
        draftField.typeText(sample)
        XCTAssertEqual(draftField.value as? String, sample)

        let title = app.staticTexts["route.placeholder.title"].firstMatch
        if title.isHittable {
            title.tap()
        }

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["route.placeholder.param.orderCode"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["route.placeholder.param.orderCode"].firstMatch.label, "ord-99")
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)

        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.staticTexts["route.placeholder.param.orderCode"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)

        let toggleButton = app.buttons["route.placeholder.toggleSizeClass"].firstMatch
        XCTAssertTrue(toggleButton.waitForExistence(timeout: 5))
        toggleButton.tap()

        XCTAssertTrue(app.staticTexts["route.placeholder.param.orderCode"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["route.placeholder.param.orderCode"].firstMatch.label, "ord-99")
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)

        let toggleBack = app.buttons["route.placeholder.toggleSizeClass"].firstMatch
        XCTAssertTrue(toggleBack.waitForExistence(timeout: 5))
        toggleBack.tap()

        XCTAssertTrue(app.staticTexts["route.placeholder.param.orderCode"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, sample)
    }
}
