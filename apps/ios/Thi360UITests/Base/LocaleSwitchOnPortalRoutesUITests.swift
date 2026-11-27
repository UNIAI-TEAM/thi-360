import XCTest

final class LocaleSwitchOnPortalRoutesUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let fixtureServer = try ContractFixtureHTTPServer()
        try fixtureServer.registerStandardAuthFixtures()
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/filters", relativePath: "api/storefront-filters-200.json")
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/products", relativePath: "api/storefront-products-list-200.json")
        try fixtureServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/products/EXAM-DEMO-01", relativePath: "api/storefront-products-detail-200-exam.json")
        try fixtureServer.registerFixture(method: "POST", path: "/api/v1/me/exam-access/query", relativePath: "api/exam-access-query-200.json")
        try fixtureServer.start()
        self.server = fixtureServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testSwitchingLocaleUpdatesPortalSubRouteTitleImmediatelyAndKeepsServerParameters() throws {
        let server = try XCTUnwrap(server)
        let baseURL = "http://127.0.0.1:\(server.port)"
        let suite = "vn.unicom.thi360.uitest.portallocale.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-api-base-url", baseURL,
            "-uitest-destination", "home",
            "-uitest-route", "product/EXAM-DEMO-01"
        ]
        app.launch()

        let codeLabel = app.staticTexts["productDetail.code"].firstMatch
        XCTAssertTrue(codeLabel.waitForExistence(timeout: 8))
        XCTAssertEqual(codeLabel.label, "Mã sản phẩm EXAM-DEMO-01")
        XCTAssertTrue(app.staticTexts["Cấu trúc bài thi"].firstMatch.waitForExistence(timeout: 5))

        openDestination(titled: "Tài khoản", identifier: "shell.dest.account", in: app)
        let englishButton = app.buttons["shell.account.locale.en"].firstMatch
        XCTAssertTrue(englishButton.waitForExistence(timeout: 5))
        if !englishButton.isHittable {
            app.swipeUp()
        }
        englishButton.tap()

        openDestination(titled: "Home", identifier: "shell.dest.home", in: app)

        let updatedCodeLabel = app.staticTexts["productDetail.code"].firstMatch
        XCTAssertTrue(updatedCodeLabel.waitForExistence(timeout: 8))
        XCTAssertEqual(
            updatedCodeLabel.label,
            "Product code EXAM-DEMO-01",
            "Locale switch must update ProductDetailView immediately while keeping server product code unchanged"
        )
        XCTAssertTrue(app.staticTexts["Exam structure"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertFalse(app.textFields["login.identifierField"].exists, "Locale switch must not sign out")
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
