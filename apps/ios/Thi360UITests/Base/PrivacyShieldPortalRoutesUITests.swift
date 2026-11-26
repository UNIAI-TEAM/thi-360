import Foundation
import XCTest

final class PrivacyShieldPortalRoutesUITests: XCTestCase {
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

    func testChromelessAttemptRouteIsHiddenBehindLockAndPrivacyShield() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.shield.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.shield.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-lock-timeout", "2",
            "-uitest-route", "attempt/att-shield-42",
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

        let attemptParam = app.staticTexts["route.placeholder.param.attemptId"].firstMatch
        waitForBiometricMatch(until: attemptParam, timeout: 10)
        XCTAssertTrue(attemptParam.exists)
        XCTAssertEqual(attemptParam.label, "att-shield-42")
        XCTAssertFalse(app.tabBars.firstMatch.exists, "Attempt screen must be chromeless")

        // Background beyond lock timeout -> Lock screen must completely cover chromeless attempt screen.
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 3.0)
        app.activate()

        let unlockButton = app.buttons["lock.unlockBiometricButton"].firstMatch
        XCTAssertTrue(unlockButton.waitForExistence(timeout: 8))
        XCTAssertFalse(attemptParam.exists, "Attempt content must never be visible behind lock screen")

        unlockButton.tap()
        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        waitForBiometricMatch(until: attemptParam, timeout: 10)
        XCTAssertTrue(attemptParam.exists)
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
