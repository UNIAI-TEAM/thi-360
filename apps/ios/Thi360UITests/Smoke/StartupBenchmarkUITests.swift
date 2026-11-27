import Foundation
import XCTest

final class StartupBenchmarkUITests: XCTestCase {
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
            if method == "GET", path == "/api/v1/public/storefront/filters" {
                return (200, filtersBody)
            }
            if method == "GET", path == "/api/v1/public/storefront/products" {
                return (200, productsBody)
            }
            if method == "POST", path == "/api/v1/me/exam-access/query" {
                return (200, accessBody)
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

    func testMedianColdLaunchWithPersistedSessionIsWithinTwoSeconds() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.startup.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.startup.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let seedApp = XCUIApplication()
        seedApp.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-api-base-url", baseURL
        ]
        seedApp.launch()

        let identifierField = seedApp.textFields["login.identifierField"].firstMatch
        XCTAssertTrue(identifierField.waitForExistence(timeout: 10))
        identifierField.tap()
        identifierField.typeText("student.demo")

        let passwordField = seedApp.secureTextFields["login.passwordField"].firstMatch
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        passwordField.tap()
        passwordField.typeText("Password123!")

        seedApp.buttons["login.submitButton"].firstMatch.tap()
        waitForSeedHome(in: seedApp)
        seedApp.terminate()

        let coldLaunchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-keychain-service", keychainService,
            "-uitest-api-base-url", baseURL
        ]

        let verifyApp = XCUIApplication()
        verifyApp.launchArguments = coldLaunchArguments
        verifyApp.launch()
        XCTAssertTrue(
            verifyApp.staticTexts["catalog.heading"].firstMatch.waitForExistence(timeout: 10),
            "Cold launch with persisted session must land directly on interactive Home screen"
        )
        XCTAssertFalse(
            verifyApp.textFields["login.identifierField"].firstMatch.exists,
            "Persisted session cold launch must not fall back to Login screen"
        )
        verifyApp.terminate()
        _ = verifyApp.wait(for: .notRunning, timeout: 5)

        let options = XCTMeasureOptions()
        options.iterationCount = 5
        options.invocationOptions = [.manuallyStop]
        measure(metrics: [XCTApplicationLaunchMetric(waitUntilResponsive: true)], options: options) {
            let app = XCUIApplication()
            app.launchArguments = coldLaunchArguments
            app.launch()
            _ = app.staticTexts["catalog.heading"].firstMatch.waitForExistence(timeout: 5)
            stopMeasuring()
            app.terminate()
            _ = app.wait(for: .notRunning, timeout: 5)
        }
    }

    private func waitForSeedHome(in app: XCUIApplication) {
        let homeTitle = app.staticTexts["catalog.heading"].firstMatch
        let bioLater = app.buttons["biometric.enable.laterButton"].firstMatch
        let deadline = Date().addingTimeInterval(12)

        while Date() < deadline {
            if homeTitle.exists {
                return
            }
            if bioLater.exists && bioLater.isHittable {
                bioLater.tap()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTAssertTrue(homeTitle.exists, "Seed login must arrive on Home screen")
    }
}
