import Foundation
import XCTest

final class RefreshableContainerUITests: XCTestCase {
    private struct IndexedFixtureEntry: Decodable {
        let file: String
        let operationId: String
        let status: Int
        let variant: String?
    }

    private var server: ContractFixtureHTTPServer?
    private let orderCode = "DH261126000001"

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        let fixturePath = try resolveOrderDetailFixturePath()
        try mockServer.registerFixture(
            method: "GET",
            path: "/api/v1/me/orders/\(orderCode)",
            statusCode: 200,
            relativePath: fixturePath
        )
        try mockServer.start()
        self.server = mockServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testPullToRefreshInvokesRefreshAction() throws {
        let server = try XCTUnwrap(server)
        let orderPath = "/api/v1/me/orders/\(orderCode)"
        let suite = "vn.unicom.thi360.uitest.refresh.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "account",
            "-uitest-route", "order/\(orderCode)",
            "-uitest-api-base-url", "http://127.0.0.1:\(server.port)"
        ]
        app.launch()

        let heading = app.staticTexts["orderDetail.heading"].firstMatch
        XCTAssertTrue(heading.waitForExistence(timeout: 10), "Expected RefreshableContainer-backed OrderDetailView to load")
        XCTAssertTrue(heading.label.contains(orderCode), "Expected order code \(orderCode) in heading, got \(heading.label)")

        let initialCount = waitForCallCount(atLeast: 1, on: server, method: "GET", path: orderPath, timeout: 5)
        XCTAssertEqual(initialCount, 1, "Initial load must issue one GET /api/v1/me/orders/{code} request")

        let scrollView = app.scrollViews.firstMatch
        XCTAssertTrue(scrollView.waitForExistence(timeout: 5))

        let start = scrollView.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25))
        let finish = scrollView.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.1, thenDragTo: finish)

        let refreshedCount = waitForCallCount(atLeast: initialCount + 1, on: server, method: "GET", path: orderPath, timeout: 8)
        XCTAssertGreaterThanOrEqual(
            refreshedCount,
            initialCount + 1,
            "Pull-to-refresh gesture must trigger RefreshableContainer onRefresh and issue a second GET request"
        )
        XCTAssertTrue(heading.exists, "OrderDetailView must stay rendered after pull-to-refresh")
        XCTAssertTrue(heading.label.contains(orderCode))
    }

    private func resolveOrderDetailFixturePath() throws -> String {
        let indexData = try ContractFixtureHTTPServer.loadFixture("api/index.json")
        let entries = try JSONDecoder().decode([IndexedFixtureEntry].self, from: indexData)
        let match = try XCTUnwrap(entries.first {
            $0.operationId == "getMyOrder" && $0.status == 200 && ($0.variant ?? "") == "fulfilled"
        })
        return match.file.contains("/") ? match.file : "api/\(match.file)"
    }

    private func waitForCallCount(
        atLeast target: Int,
        on server: ContractFixtureHTTPServer,
        method: String,
        path: String,
        timeout: TimeInterval
    ) -> Int {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let count = server.callCount(method: method, path: path)
            if count >= target {
                return count
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        return server.callCount(method: method, path: path)
    }
}
