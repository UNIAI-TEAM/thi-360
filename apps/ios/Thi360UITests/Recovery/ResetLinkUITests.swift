import Foundation
import XCTest

final class ResetLinkUITests: XCTestCase {
    private var server: RecoveryFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mockServer = try RecoveryFixtureHTTPServer { method, path, _ in
            if method == "POST", path == "/api/v1/auth/password/reset" {
                return (204, Data())
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

    func testResetLinkLaunchArgumentOpensNewPasswordBypassingLockedStateAndResetsToLogin() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.reset.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.reset.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-locked",
            "-uitest-api-base-url", baseURL,
            "-resetLinkURL", "https://localhost/reset-password?token=uitest-reset-token-abc"
        ]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["Đặt mật khẩu mới"].waitForExistence(timeout: 8),
            "Launching with -resetLinkURL while locked must bypass lock gate and show New Password screen"
        )
        XCTAssertFalse(app.otherElements["session.locked.screen"].exists)

        let newField = app.secureTextFields["password.newField"]
        XCTAssertTrue(newField.waitForExistence(timeout: 5))
        newField.tap()
        newField.typeText("NewStrongPass1!")

        let confirmField = app.secureTextFields["password.confirmField"]
        confirmField.tap()
        confirmField.typeText("NewStrongPass1!")

        let heading = app.staticTexts["Đặt mật khẩu mới"]
        if heading.isHittable {
            heading.tap()
        }

        let submitButton = app.buttons["password.submitButton"]
        XCTAssertTrue(submitButton.waitForExistence(timeout: 5))
        if !submitButton.isHittable {
            app.swipeUp()
        }
        submitButton.tap()

        XCTAssertTrue(
            app.textFields["login.identifierField"].waitForExistence(timeout: 8),
            "Completing reset password must navigate back to Login screen"
        )
        XCTAssertTrue(
            app.staticTexts["Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới."].waitForExistence(timeout: 5)
                || app.otherElements["login.bannerMessage"].waitForExistence(timeout: 5),
            "Login screen must display post-reset confirmation banner"
        )
        XCTAssertTrue(server.recordedPaths.contains("POST /api/v1/auth/password/reset"))
    }
}
