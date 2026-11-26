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
            "-uitest-route", "product/ielts-lock",
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
        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 2.5)

        let paramText = app.staticTexts["route.placeholder.param.code"].firstMatch
        waitForBiometricMatch(until: paramText, timeout: 10)
        XCTAssertTrue(paramText.exists)
        XCTAssertEqual(paramText.label, "ielts-lock")

        let draftField = app.textFields["route.placeholder.draftField"].firstMatch
        XCTAssertTrue(draftField.waitForExistence(timeout: 5))
        draftField.tap()
        draftField.typeText("Keep across lock")

        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 3.0)
        app.activate()

        let unlockButton = app.buttons["lock.unlockBiometricButton"].firstMatch
        XCTAssertTrue(unlockButton.waitForExistence(timeout: 8), "Lock screen must cover portal sub-route after timeout")
        XCTAssertFalse(paramText.exists, "Portal route content must be hidden while locked")

        unlockButton.tap()
        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        waitForBiometricMatch(until: paramText, timeout: 10)

        XCTAssertTrue(paramText.exists, "Unlocking must return to portal sub-route")
        XCTAssertEqual(paramText.label, "ielts-lock")
        XCTAssertEqual(app.textFields["route.placeholder.draftField"].firstMatch.value as? String, "Keep across lock")
    }

    private func waitForBiometricMatch(until element: XCUIElement, timeout: TimeInterval) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists {
                return
            }
            SimulatorBiometricHelper.triggerMatch()
            if element.waitForExistence(timeout: 1.0) {
                return
            }
        }
    }
}
