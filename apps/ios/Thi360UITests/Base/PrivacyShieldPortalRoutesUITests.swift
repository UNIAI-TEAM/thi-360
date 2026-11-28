import Foundation
import XCTest

final class PrivacyShieldPortalRoutesUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        try registerAttemptFixtures(on: mockServer, attemptId: "att-shield-42")
        try mockServer.start()
        self.server = mockServer
        SimulatorBiometricHelper.setEnrolled(true)
    }

    private func registerAttemptFixtures(on mockServer: ContractFixtureHTTPServer, attemptId: String) throws {
        let indexData = try ContractFixtureHTTPServer.loadFixture("api/index.json")
        struct Entry: Decodable {
            let file: String
            let operationId: String
            let status: Int
            let variant: String?
        }
        let entries = try JSONDecoder().decode([Entry].self, from: indexData)
        func load(_ op: String, status: Int, variant: String = "") throws -> [String: Any] {
            let match = try XCTUnwrap(entries.first {
                $0.operationId == op && $0.status == status && ($0.variant ?? "") == variant
            })
            let rel = match.file.contains("/") ? match.file : "api/\(match.file)"
            let data = try ContractFixtureHTTPServer.loadFixture(rel)
            return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        }

        var stateObj = try load("getState", status: 200, variant: "in-progress-shared")
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let now = Date()
        stateObj["attemptId"] = attemptId
        stateObj["serverNow"] = iso.string(from: now)
        stateObj["deadlineAt"] = iso.string(from: now.addingTimeInterval(1800))
        let stateData = try JSONSerialization.data(withJSONObject: stateObj)
        mockServer.register(method: "GET", path: "/api/v1/me/attempts/\(attemptId)", statusCode: 200, body: stateData)

        var claimObj = try load("claimSession", status: 200)
        claimObj["serverNow"] = iso.string(from: now)
        let claimData = try JSONSerialization.data(withJSONObject: claimObj)
        mockServer.register(method: "POST", path: "/api/v1/me/attempts/\(attemptId)/session", statusCode: 200, body: claimData)

        var hbObj = try load("heartbeat", status: 200)
        hbObj["serverNow"] = iso.string(from: now)
        let hbData = try JSONSerialization.data(withJSONObject: hbObj)
        mockServer.register(method: "POST", path: "/api/v1/me/attempts/\(attemptId)/heartbeat", statusCode: 200, body: hbData)
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
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 2.5)

        let examTitle = app.staticTexts["player.topBar.examTitle"].firstMatch
        PortalBiometricUITestHelper.waitForBiometricMatch(until: examTitle, timeout: 10)
        XCTAssertTrue(examTitle.exists)
        XCTAssertFalse(app.tabBars.firstMatch.exists, "Attempt screen must be chromeless")

        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 3.0)
        app.activate()

        let unlockButton = app.buttons["lock.unlockBiometricButton"].firstMatch
        XCTAssertTrue(unlockButton.waitForExistence(timeout: 8))
        XCTAssertFalse(examTitle.exists, "Attempt content must never be visible behind lock screen")

        unlockButton.tap()
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        PortalBiometricUITestHelper.waitForBiometricMatch(until: examTitle, timeout: 10)
        XCTAssertTrue(examTitle.exists)
    }
}
