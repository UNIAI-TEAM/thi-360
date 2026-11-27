import Foundation
import XCTest

final class LockOnPortalRoutesUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        try mockServer.start()
        self.server = mockServer
        SimulatorBiometricHelper.setEnrolled(true)
    }

    override func tearDownWithError() throws {
        SimulatorBiometricHelper.setEnrolled(false)
        server?.stop()
        server = nil
    }

    func testLockAndUnlockPreservesPortalSubRouteAndDraft() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.lockportal.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.lockportal.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-lock-timeout", "2",
            "-uitest-route", "result/att-lock",
            "-uitest-api-base-url", baseURL
        ]
        app.launch()

        let identifierField = app.textFields["login.identifierField"].firstMatch
        XCTAssertTrue(identifierField.waitForExistence(timeout: 8))
        identifierField.tap()
        identifierField.typeText("student.demo")

        let passwordField = app.secureTextFields["login.passwordField"].firstMatch
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        passwordField.tap()
        passwordField.typeText("Password123!")
        app.buttons["login.submitButton"].firstMatch.tap()

        let enableButton = app.buttons["biometric.enable.confirmButton"].firstMatch
        XCTAssertTrue(enableButton.waitForExistence(timeout: 8))
        enableButton.tap()
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 2.5)

        let paramText = app.staticTexts["route.placeholder.param.attemptId"].firstMatch
        PortalBiometricUITestHelper.waitForBiometricMatch(until: paramText, timeout: 10)
        XCTAssertTrue(paramText.exists)
        XCTAssertEqual(paramText.label, "att-lock")

        let draftField = app.textFields["route.placeholder.draftField"].firstMatch
        XCTAssertTrue(draftField.waitForExistence(timeout: 5))
        let draftSample = "Keep across lock"
        enterText(draftSample, into: draftField, in: app)
        XCTAssertEqual(draftField.value as? String, draftSample)

        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 4.5)
        app.activate()

        let unlockButton = app.buttons["lock.unlockBiometricButton"].firstMatch
        XCTAssertTrue(unlockButton.waitForExistence(timeout: 12), "Lock screen must cover portal sub-route after timeout")
        XCTAssertFalse(paramText.exists, "Portal route content must be hidden while locked")

        unlockButton.tap()
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        PortalBiometricUITestHelper.waitForBiometricMatch(until: paramText, timeout: 10)

        XCTAssertTrue(paramText.exists, "Unlocking must return to portal sub-route")
        XCTAssertEqual(paramText.label, "att-lock")
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, draftSample)
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
