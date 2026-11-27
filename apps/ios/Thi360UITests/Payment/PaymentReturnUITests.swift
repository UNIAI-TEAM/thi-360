import Foundation
import XCTest

final class PaymentReturnUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        try mockServer.registerFixture(
            method: "GET",
            path: "/api/v1/public/storefront/products/EXAM-DEMO-01",
            statusCode: 200,
            relativePath: "api/storefront-products-detail-200-exam.json"
        )
        try mockServer.registerFixture(
            method: "POST",
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativePath: "api/price-quote-200.json"
        )
        try mockServer.registerFixture(
            method: "POST",
            path: "/api/v1/me/exam-access/query",
            statusCode: 200,
            relativePath: "api/exam-access-query-200.json"
        )
        try mockServer.registerFixture(
            method: "POST",
            path: "/api/v1/me/orders",
            statusCode: 201,
            relativePath: "api/orders-create-201-redirect.json"
        )
        try mockServer.registerFixture(
            method: "GET",
            path: "/api/v1/public/payments/vnpay/return",
            statusCode: 200,
            relativePath: "api/vnpay-return-200-succeeded.json"
        )
        try mockServer.registerFixture(
            method: "GET",
            path: "/api/v1/me/orders/DH261126000001",
            statusCode: 200,
            relativePath: "api/orders-detail-200-fulfilled.json"
        )
        try mockServer.start()
        self.server = mockServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testWarmLinkOpensResult() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.paywarm.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.paywarm.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"
        let callbackURL = "https://localhost/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_Amount=20000000&vnp_ResponseCode=00&vnp_SecureHash=uitestwarm"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "checkout/EXAM-DEMO-01",
            "-uitest-api-base-url", baseURL,
            "-uitest-payment-browser-callback", callbackURL
        ]
        app.launch()

        let payButton = app.buttons["checkout.payButton"].firstMatch
        for _ in 0..<4 where !payButton.waitForExistence(timeout: 2) {
            app.swipeUp()
        }
        XCTAssertTrue(payButton.waitForExistence(timeout: 8), "Checkout pay button must appear after product and quote load")
        if !payButton.isHittable {
            app.swipeUp()
        }
        payButton.tap()

        let heading = app.staticTexts["Đã cấp bài thi"].firstMatch
        XCTAssertTrue(
            heading.waitForExistence(timeout: 10),
            "Payment browser callback return link must open PaymentResult and show fulfilled heading"
        )
        XCTAssertTrue(server.recordedPaths.contains("POST /api/v1/me/orders"))
        XCTAssertTrue(server.recordedPaths.contains("GET /api/v1/public/payments/vnpay/return"))
        XCTAssertTrue(server.recordedPaths.contains("GET /api/v1/me/orders/DH261126000001"))
    }

    func testColdLaunchWithLinkOpensResult() throws {
        let server = try XCTUnwrap(server)
        let baseURL = "http://127.0.0.1:\(server.port)"
        let returnLink = "https://localhost/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_Amount=20000000&vnp_ResponseCode=00&vnp_SecureHash=uitestcold"

        do {
            let suite = "vn.unicom.thi360.uitest.paycold.\(UUID().uuidString)"
            let keychainService = "vn.unicom.thi360.uitest.paycold.kc.\(UUID().uuidString)"
            let app = XCUIApplication()
            app.launchArguments = [
                "-uitest-defaults-suite", suite,
                "-uitest-reset-preferences",
                "-uitest-keychain-service", keychainService,
                "-uitest-reset-session",
                "-uitest-authenticated",
                "-uitest-api-base-url", baseURL,
                "-uitest-incoming-link", returnLink
            ]
            app.launch()

            XCTAssertTrue(
                app.staticTexts["Đã cấp bài thi"].waitForExistence(timeout: 10),
                "Cold launch with return link must open PaymentResult directly"
            )
            XCTAssertTrue(server.recordedPaths.contains("GET /api/v1/public/payments/vnpay/return"))
            app.terminate()
        }

        do {
            let verifyCountBeforeLockedLaunch = server.callCount(method: "GET", path: "/api/v1/public/payments/vnpay/return")
            let suite = "vn.unicom.thi360.uitest.paycoldlock.\(UUID().uuidString)"
            let keychainService = "vn.unicom.thi360.uitest.paycoldlock.kc.\(UUID().uuidString)"
            let app = XCUIApplication()
            app.launchArguments = [
                "-uitest-defaults-suite", suite,
                "-uitest-reset-preferences",
                "-uitest-keychain-service", keychainService,
                "-uitest-reset-session",
                "-uitest-locked",
                "-uitest-api-base-url", baseURL,
                "-uitest-incoming-link", returnLink
            ]
            app.launch()

            let enterPasswordButton = app.buttons["lock.enterPasswordButton"].firstMatch
            XCTAssertTrue(
                enterPasswordButton.waitForExistence(timeout: 8),
                "Locked cold launch with payment return link must show Lock screen first"
            )
            XCTAssertFalse(app.staticTexts["Đã cấp bài thi"].exists)
            XCTAssertEqual(
                server.callCount(method: "GET", path: "/api/v1/public/payments/vnpay/return"),
                verifyCountBeforeLockedLaunch,
                "Payment return link must not verify while session is locked"
            )

            enterPasswordButton.tap()

            let identifierField = app.textFields["login.identifierField"].firstMatch
            XCTAssertTrue(identifierField.waitForExistence(timeout: 8))
            identifierField.tap()
            identifierField.typeText("student.demo")

            let passwordField = app.secureTextFields["login.passwordField"].firstMatch
            XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
            passwordField.tap()
            passwordField.typeText("Password123!")

            app.buttons["login.submitButton"].firstMatch.tap()

            XCTAssertTrue(
                app.staticTexts["Đã cấp bài thi"].waitForExistence(timeout: 10),
                "Unlocking after locked cold launch must immediately open PaymentResult for the queued return link"
            )
            XCTAssertEqual(
                server.callCount(method: "GET", path: "/api/v1/public/payments/vnpay/return"),
                verifyCountBeforeLockedLaunch + 1
            )
        }
    }
}
