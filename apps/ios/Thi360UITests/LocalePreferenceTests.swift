import XCTest

final class LocalePreferenceTests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let fixtureServer = try ContractFixtureHTTPServer()
        try fixtureServer.registerStandardAuthFixtures()
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/filters", relativePath: "api/storefront-filters-200.json")
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/products", relativePath: "api/storefront-products-list-200.json")
        try fixtureServer.registerFixture(method: "POST", path: "/api/v1/me/exam-access/query", relativePath: "api/exam-access-query-200.json")
        try fixtureServer.start()
        self.server = fixtureServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testFirstLaunchDefaultsToVietnameseRegardlessOfDeviceLanguageAndPersistsSwitch() throws {
        let server = try XCTUnwrap(server)
        let baseURL = "http://127.0.0.1:\(server.port)"
        let suite = "vn.unicom.thi360.uitest.locale.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US",
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-api-base-url", baseURL
        ]
        app.launch()

        let viHeading = app.staticTexts["catalog.heading"].firstMatch
        XCTAssertTrue(viHeading.waitForExistence(timeout: 8))
        XCTAssertEqual(viHeading.label, "Chọn bài thi")
        XCTAssertFalse(app.staticTexts["Chưa có nội dung"].exists)

        openDestination(titled: "Tài khoản", identifier: "shell.dest.account", in: app)
        XCTAssertTrue(app.staticTexts["Hiển thị"].waitForExistence(timeout: 5))

        let englishButton = app.buttons["shell.account.locale.en"]
        XCTAssertTrue(englishButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(englishButton.frame.width * 100) / 100, 44)
        XCTAssertGreaterThanOrEqual(round(englishButton.frame.height * 100) / 100, 44)

        let darkButton = app.buttons["shell.account.theme.dark"]
        XCTAssertTrue(darkButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(darkButton.frame.width * 100) / 100, 44)
        XCTAssertGreaterThanOrEqual(round(darkButton.frame.height * 100) / 100, 44)

        englishButton.tap()

        XCTAssertTrue(app.staticTexts["Account"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Display"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Hiển thị"].exists)

        openDestination(titled: "Home", identifier: "shell.dest.home", in: app)
        let enHeading = app.staticTexts["catalog.heading"].firstMatch
        XCTAssertTrue(enHeading.waitForExistence(timeout: 5))
        XCTAssertEqual(enHeading.label, "Choose an exam")
        XCTAssertFalse(app.staticTexts["Chọn bài thi"].exists)

        app.terminate()

        let relaunched = XCUIApplication()
        relaunched.launchArguments = [
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US",
            "-uitest-defaults-suite", suite,
            "-uitest-authenticated",
            "-uitest-api-base-url", baseURL
        ]
        relaunched.launch()

        let relaunchedHeading = relaunched.staticTexts["catalog.heading"].firstMatch
        XCTAssertTrue(relaunchedHeading.waitForExistence(timeout: 8))
        XCTAssertEqual(relaunchedHeading.label, "Choose an exam")
    }

    private func openDestination(titled title: String, identifier: String, in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons[title]
        if tabButton.waitForExistence(timeout: 2) {
            tabButton.tap()
            return
        }
        let button = app.buttons[identifier].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5), "Destination button \(identifier) not found")
        button.tap()
    }
}
