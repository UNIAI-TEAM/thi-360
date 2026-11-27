import Foundation
import XCTest

final class TabBackStackUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        try mockServer.registerFixture(
            method: "GET",
            path: "/api/v1/catalog/options",
            statusCode: 200,
            relativePath: "api/catalog-options-200-exam.json"
        )
        try mockServer.registerFixture(
            method: "GET",
            path: "/api/v1/me/attempts/att-01/result",
            statusCode: 200,
            relativePath: "api/attempts-result-200-scored.json"
        )
        try mockServer.start()
        self.server = mockServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testSubRouteDraftAndScrollSurviveSwitchingTabs() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.tabstack.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "result/att-01",
            "-uitest-api-base-url", "http://127.0.0.1:\(server.port)"
        ]
        app.launch()

        let examName = app.staticTexts["result.header.examName"].firstMatch
        XCTAssertTrue(examName.waitForExistence(timeout: 10), "Expected pushed result/att-01 screen on Home tab")
        let initialExamName = examName.label
        XCTAssertFalse(initialExamName.isEmpty)

        app.swipeUp()

        openDestination(titled: "Tài khoản", identifier: "shell.dest.account", in: app)
        XCTAssertTrue(app.staticTexts["shell.account.heading"].firstMatch.waitForExistence(timeout: 5))

        openDestination(titled: "Trang chủ", identifier: "shell.dest.home", in: app)

        let restoredResultNav = app.navigationBars["Kết quả lượt thi"].firstMatch
        let restoredTitle = app.staticTexts["result.header.examName"].firstMatch
        XCTAssertTrue(
            restoredResultNav.waitForExistence(timeout: 8) || restoredTitle.waitForExistence(timeout: 4),
            "Returning to Home must restore pushed result sub-route"
        )
        if !restoredTitle.exists {
            app.swipeDown()
            app.swipeDown()
        }
        XCTAssertTrue(restoredTitle.waitForExistence(timeout: 5))
        XCTAssertEqual(restoredTitle.label, initialExamName)
    }

    private func openDestination(titled title: String, identifier: String, in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons[title].firstMatch
        if tabButton.waitForExistence(timeout: 2) {
            tabButton.tap()
            return
        }
        let button = app.buttons[identifier].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()
    }
}
