import Foundation
import XCTest

final class VerifyEmailUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let fixtureServer = try ContractFixtureHTTPServer()
        try fixtureServer.registerStandardAuthFixtures()
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/filters", relativePath: "api/storefront-filters-200.json")
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/products", relativePath: "api/storefront-products-list-200.json")
        try fixtureServer.registerFixture(method: "POST", path: "/api/v1/me/exam-access/query", relativePath: "api/exam-access-query-200.json")
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/me/profile", relativePath: "api/me-profile-200-avatar-pending-email.json")
        fixtureServer.register(method: "POST", path: "/api/v1/me/email/verification", statusCode: 202)
        try fixtureServer.start()
        self.server = fixtureServer
        SimulatorBiometricHelper.setEnrolled(true)
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        SimulatorBiometricHelper.setEnrolled(false)
        server?.stop()
        server = nil
    }

    func testVerifyEmailAutoSendsStrictlyOnceAcrossRealRotationAndAppLockUnlock() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.verifyemail.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.verifyemail.kc.\(UUID().uuidString)"
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

        let accountTab = app.tabBars.buttons["Tài khoản"].firstMatch
        PortalBiometricUITestHelper.waitForBiometricMatch(until: accountTab, timeout: 10)
        XCTAssertTrue(accountTab.exists)
        accountTab.tap()

        let profileLoaded = app.descendants(matching: .any).matching(identifier: "account.profile.loaded").firstMatch
        XCTAssertTrue(profileLoaded.waitForExistence(timeout: 8), "Account profile header card must load")
        XCTAssertEqual(app.staticTexts["account.profile.fullName"].firstMatch.label, "Nguyễn Minh Anh")
        XCTAssertEqual(app.staticTexts["account.profile.username"].firstMatch.label, "student.demo")
        XCTAssertEqual(app.staticTexts["account.profile.email"].firstMatch.label, "student.demo@thi360.vn")
        let emailStatus = app.descendants(matching: .any).matching(identifier: "account.profile.emailStatus").firstMatch
        XCTAssertTrue(emailStatus.waitForExistence(timeout: 5))
        XCTAssertTrue(
            emailStatus.label.contains("Chưa xác minh") || app.staticTexts["Chưa xác minh"].firstMatch.exists,
            "Unverified email badge must remain queryable via account.profile.emailStatus"
        )

        let profileRow = app.buttons["account.profileRow"].firstMatch
        XCTAssertTrue(profileRow.waitForExistence(timeout: 8))
        profileRow.tap()

        let verifyBannerButton = app.buttons["profile.verifyEmailButton"].firstMatch
        XCTAssertTrue(verifyBannerButton.waitForExistence(timeout: 8))
        verifyBannerButton.tap()

        let codeField = app.textFields["verifyEmail.codeField"].firstMatch
        XCTAssertTrue(codeField.waitForExistence(timeout: 8))
        XCTAssertEqual(
            server.callCount(method: "POST", path: "/api/v1/me/email/verification"),
            1,
            "Opening Verify Email from banner must auto-send strictly once"
        )

        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 1.0)
        XCUIDevice.shared.orientation = .portrait
        Thread.sleep(forTimeInterval: 1.0)
        XCTAssertTrue(codeField.waitForExistence(timeout: 5))
        XCTAssertEqual(
            server.callCount(method: "POST", path: "/api/v1/me/email/verification"),
            1,
            "Device rotation must NOT re-trigger auto-send"
        )

        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 3.0)
        app.activate()

        let unlockButton = app.buttons["lock.unlockBiometricButton"].firstMatch
        XCTAssertTrue(unlockButton.waitForExistence(timeout: 8))
        unlockButton.tap()
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        PortalBiometricUITestHelper.waitForBiometricMatch(until: codeField, timeout: 10)
        XCTAssertTrue(codeField.exists)
        XCTAssertEqual(
            server.callCount(method: "POST", path: "/api/v1/me/email/verification"),
            1,
            "Backgrounding, locking, and unlocking must NOT re-trigger auto-send"
        )
    }
}
