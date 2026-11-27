import Foundation
import XCTest

final class BiometricInvalidationTests: XCTestCase {
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

    func testBiometricReEnrollmentInvalidatesKeychainItemDemandsPasswordAndTurnsSwitchOff() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.bioinv.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.bioinv.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        // 1. Login and enable biometrics (stores real .biometryCurrentSet Keychain item).
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
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

        let enableButton = app.buttons["biometric.enable.confirmButton"]
        XCTAssertTrue(enableButton.waitForExistence(timeout: 8))
        enableButton.tap()

        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 2.5)
        let homeHeading = app.staticTexts["catalog.heading"].firstMatch
        waitForBiometricMatch(until: homeHeading, timeout: 10)
        XCTAssertTrue(homeHeading.exists)

        // 2. Terminate app and clear simulator biometric enrollment so the .biometryCurrentSet Keychain item cannot be unlocked.
        app.terminate()
        SimulatorBiometricHelper.setEnrolled(false)

        // 3. Cold relaunch opens Lock screen, detects biometric invalidation, clears enrollment, and demands password.
        let relaunched = XCUIApplication()
        relaunched.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-keychain-service", keychainService,
            "-uitest-api-base-url", baseURL
        ]
        relaunched.launch()

        let statusBanner = relaunched.descendants(matching: .any)["lock.statusBanner"]
        XCTAssertTrue(
            statusBanner.waitForExistence(timeout: 8),
            "Biometric enrollment change must invalidate .biometryCurrentSet Keychain item and display password-required notice"
        )
        XCTAssertFalse(
            relaunched.buttons["lock.unlockBiometricButton"].exists,
            "Invalidated biometrics must disable biometric unlock button and demand password"
        )

        // 4. User re-enrolls new biometrics on device; lock screen still requires password and biometric stays OFF until explicitly re-enabled.
        SimulatorBiometricHelper.setEnrolled(true)

        let enterPasswordButton = relaunched.buttons["lock.enterPasswordButton"]
        XCTAssertTrue(enterPasswordButton.waitForExistence(timeout: 5))
        enterPasswordButton.tap()

        let fallbackPasswordField = relaunched.secureTextFields["login.passwordField"]
        XCTAssertTrue(fallbackPasswordField.waitForExistence(timeout: 8))
        fallbackPasswordField.tap()
        fallbackPasswordField.typeText("Password123!")
        relaunched.buttons["login.submitButton"].tap()

        let laterButton = relaunched.buttons["biometric.enable.laterButton"]
        if laterButton.waitForExistence(timeout: 6) {
            laterButton.tap()
        }

        XCTAssertTrue(relaunched.staticTexts["catalog.heading"].firstMatch.waitForExistence(timeout: 8))
        openAccountDestination(in: relaunched)

        let bioToggle = relaunched.switches["account.biometricToggle"]
        XCTAssertTrue(bioToggle.waitForExistence(timeout: 8))
        XCTAssertTrue(bioToggle.isEnabled, "After re-enrollment, Account biometric switch must be interactive again")
        XCTAssertEqual(bioToggle.value as? String, "0", "Account biometric switch must remain OFF after invalidation until user re-enables")
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

    private func openAccountDestination(in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons["Tài khoản"]
        if tabButton.waitForExistence(timeout: 2) {
            tabButton.tap()
            return
        }
        let button = app.buttons["shell.dest.account"].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()
    }
}
