import Foundation
import XCTest

final class RegisterFlowUITests: XCTestCase {
    private var server: RecoveryFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let register200 = try Data(
            contentsOf: repoRoot.appendingPathComponent("contracts/mobile/api/register-200.json")
        )

        let mockServer = try RecoveryFixtureHTTPServer { method, path, _ in
            if method == "POST", path == "/api/v1/auth/register" {
                return (200, register200)
            }
            return (404, Data(#"{"code":"NOT_FOUND","message":"Not found","fieldErrors":[],"traceId":"0"}"#.utf8))
        }
        try mockServer.start()
        self.server = mockServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testRelaunchReturnsToLogin() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.reg.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.reg.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-api-base-url", baseURL
        ]
        app.launch()

        let registerButton = app.buttons["login.registerButton"]
        XCTAssertTrue(registerButton.waitForExistence(timeout: 8))
        registerButton.tap()

        let fullNameField = app.textFields["register.fullNameField"]
        XCTAssertTrue(fullNameField.waitForExistence(timeout: 5))
        fullNameField.tap()
        fullNameField.typeText("Tran Minh Anh")

        let usernameField = app.textFields["register.usernameField"]
        usernameField.tap()
        usernameField.typeText("minhanh.tran")

        let emailField = app.textFields["register.emailField"]
        emailField.tap()
        emailField.typeText("student.demo@thi360.vn")

        let passwordToggle = app.buttons["register.passwordToggle"]
        XCTAssertTrue(passwordToggle.waitForExistence(timeout: 5))
        passwordToggle.tap()

        let passwordField = app.textFields["register.passwordField"]
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        passwordField.tap()
        passwordField.typeText("StrongPass1!")

        let heading = app.staticTexts["Đăng ký"]
        if heading.isHittable {
            heading.tap()
        }

        let termsCheckbox = app.buttons["register.termsCheckbox"]
        if !termsCheckbox.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(termsCheckbox.waitForExistence(timeout: 5))
        termsCheckbox.tap()

        let submitButton = app.buttons["register.submitButton"]
        if !submitButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(submitButton.waitForExistence(timeout: 5))
        submitButton.tap()

        XCTAssertTrue(
            app.staticTexts["Xác minh email"].waitForExistence(timeout: 8),
            "Submitting valid registration must advance to OTP verification screen"
        )
        XCTAssertTrue(server.recordedPaths.contains("POST /api/v1/auth/register"))

        app.terminate()

        let relaunched = XCUIApplication()
        relaunched.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-keychain-service", keychainService,
            "-uitest-api-base-url", baseURL
        ]
        relaunched.launch()

        XCTAssertTrue(
            relaunched.textFields["login.identifierField"].waitForExistence(timeout: 8),
            "Killing app on OTP screen and relaunching must return to Login screen"
        )
        XCTAssertFalse(relaunched.staticTexts["Xác minh email"].exists)
    }
}
