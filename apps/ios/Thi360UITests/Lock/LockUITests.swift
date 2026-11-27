import Foundation
import XCTest

final class LockUITests: XCTestCase {
    private var server: RecoveryFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let authDir = repoRoot.appendingPathComponent("contracts/mobile/auth", isDirectory: true)
        let apiDir = repoRoot.appendingPathComponent("contracts/mobile/api", isDirectory: true)
        let loginBody = try Data(contentsOf: authDir.appendingPathComponent("login-200.json"))
        let refreshBody = try Data(contentsOf: authDir.appendingPathComponent("refresh-200.json"))
        let profileBody = try Data(contentsOf: apiDir.appendingPathComponent("me-profile-200.json"))
        let filtersBody = try Data(contentsOf: apiDir.appendingPathComponent("storefront-filters-200.json"))
        let productsBody = try Data(contentsOf: apiDir.appendingPathComponent("storefront-products-list-200.json"))
        let accessBody = try Data(contentsOf: apiDir.appendingPathComponent("exam-access-query-200.json"))

        let mockServer = try RecoveryFixtureHTTPServer { method, path, _ in
            if method == "POST", path == "/api/v1/auth/mobile/login" {
                return (200, loginBody)
            }
            if method == "POST", path == "/api/v1/auth/mobile/refresh" {
                return (200, refreshBody)
            }
            if method == "GET", path == "/api/v1/me/profile" {
                return (200, profileBody)
            }
            if method == "GET", path == "/api/v1/public/storefront/filters" {
                return (200, filtersBody)
            }
            if method == "GET", path == "/api/v1/public/storefront/products" {
                return (200, productsBody)
            }
            if method == "POST", path == "/api/v1/me/exam-access/query" {
                return (200, accessBody)
            }
            if method == "POST", path == "/api/v1/auth/mobile/logout" {
                return (204, Data())
            }
            return (404, Data(#"{"code":"NOT_FOUND","message":"Not found","fieldErrors":[],"traceId":"0"}"#.utf8))
        }
        try mockServer.start()
        self.server = mockServer
        SimulatorBiometricHelper.setEnrolled(true)
    }

    override func tearDownWithError() throws {
        SimulatorBiometricHelper.setEnrolled(false)
        server?.stop()
        server = nil
    }

    func testLocksAfterBackground() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.lock.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.lock.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-lock-timeout", "2",
            "-uitest-api-base-url", baseURL
        ]
        app.launch()

        let identifierField = app.textFields["login.identifierField"]
        XCTAssertTrue(identifierField.waitForExistence(timeout: 8))
        identifierField.tap()
        identifierField.typeText("student.demo")

        let passwordField = app.secureTextFields["login.passwordField"]
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        passwordField.tap()
        passwordField.typeText("Password123!")

        app.buttons["login.submitButton"].tap()

        // Enrolled simulator must display Biometric Enable prompt after password login.
        let enableButton = app.buttons["biometric.enable.confirmButton"]
        XCTAssertTrue(enableButton.waitForExistence(timeout: 8), "Expected biometric enable prompt after login on enrolled simulator")
        enableButton.tap()

        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 2.5)
        let homeHeading = app.staticTexts["catalog.heading"].firstMatch
        waitForBiometricMatch(until: homeHeading, timeout: 10)
        XCTAssertTrue(homeHeading.exists, "Successful biometric enable must enter Home")

        // Switch to History destination before backgrounding so we verify unlock returns to exact screen.
        openDestination(titled: "Lịch sử", identifier: "shell.dest.history", in: app)
        XCTAssertTrue(app.staticTexts["Lịch sử"].waitForExistence(timeout: 5))

        // Background past lock timeout (2s in Dev UI test) and return to foreground.
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 4.5)
        app.activate()

        // Lock screen must appear with all three action buttons.
        let unlockButton = app.buttons["lock.unlockBiometricButton"]
        XCTAssertTrue(unlockButton.waitForExistence(timeout: 12), "Returning after lock timeout must present Lock screen")
        XCTAssertTrue(app.buttons["lock.enterPasswordButton"].exists)
        XCTAssertTrue(app.buttons["lock.otherAccountButton"].exists)

        // Trigger Face ID / Touch ID match on simulator -> returns to exact prior screen (History).
        unlockButton.tap()
        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        waitForBiometricMatch(until: app.staticTexts["Lịch sử"], timeout: 10)
        XCTAssertTrue(app.staticTexts["Lịch sử"].exists, "Biometric unlock must restore exact screen before lock")
        XCTAssertFalse(app.buttons["lock.unlockBiometricButton"].exists)
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

    private func openDestination(titled title: String, identifier: String, in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons[title]
        if tabButton.waitForExistence(timeout: 2) {
            tabButton.tap()
            return
        }
        let button = app.buttons[identifier].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()
    }
}
