import Foundation
import XCTest

final class MyExamsUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testColdStartShowsFreshLoad() throws {
        let rawListData = try ContractFixtureHTTPServer.loadFixture("api/my-exams-list-200.json")
        var listRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: rawListData) as? [String: Any])
        var items = try XCTUnwrap(listRoot["items"] as? [[String: Any]])
        var firstRunCard = try XCTUnwrap(items.first(where: { ($0["state"] as? String) == "CAN_START" }))
        firstRunCard["code"] = "COLD-RUN-1"
        firstRunCard["name"] = "Đề lần chạy một"
        firstRunCard["bundles"] = []
        listRoot["items"] = [firstRunCard]
        let firstRunPayload = try JSONSerialization.data(withJSONObject: listRoot)

        var secondRunCard = firstRunCard
        secondRunCard["code"] = "COLD-RUN-2"
        secondRunCard["name"] = "Đề lần chạy hai"
        listRoot["items"] = [secondRunCard]
        let secondRunPayload = try JSONSerialization.data(withJSONObject: listRoot)

        let lock = NSLock()
        var isSecondRun = false
        let secondRunGate = DispatchSemaphore(value: 0)
        let secondRunEntered = DispatchSemaphore(value: 0)

        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        try mockServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/filters", relativePath: "api/storefront-filters-200.json")
        try mockServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/products", relativePath: "api/storefront-products-list-200.json")
        try mockServer.registerFixture(method: "POST", path: "/api/v1/me/exam-access/query", relativePath: "api/exam-access-query-200.json")
        mockServer.register(method: "GET", path: "/api/v1/me/exams") { _, _, _ in
            lock.lock()
            let second = isSecondRun
            lock.unlock()
            if !second {
                return ContractHTTPResponse(statusCode: 200, body: firstRunPayload)
            }
            secondRunEntered.signal()
            _ = secondRunGate.wait(timeout: .now() + 10)
            return ContractHTTPResponse(statusCode: 200, body: secondRunPayload)
        }
        try mockServer.start()
        self.server = mockServer

        let baseURL = "http://127.0.0.1:\(mockServer.port)"
        let suite = "vn.unicom.thi360.uitest.myexams.coldstart.\(UUID().uuidString)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "myExams",
            "-uitest-api-base-url", baseURL
        ]
        app.launch()

        let firstTitle = app.staticTexts["myExams.card.title.COLD-RUN-1"].firstMatch
        XCTAssertTrue(firstTitle.waitForExistence(timeout: 10), "Run 1 must load MyExams list from fixture server")
        XCTAssertEqual(firstTitle.label, "Đề lần chạy một")

        app.terminate()
        lock.lock()
        isSecondRun = true
        lock.unlock()

        app.launch()

        let entered = secondRunEntered.wait(timeout: .now() + 10)
        XCTAssertEqual(entered, .success, "Cold start must request GET /api/v1/me/exams anew")

        let skeleton = app.descendants(matching: .any).matching(identifier: "myExams.loadingSkeleton").firstMatch
        XCTAssertTrue(skeleton.waitForExistence(timeout: 6), "Cold start must display fresh loading skeleton")
        XCTAssertFalse(app.staticTexts["myExams.card.title.COLD-RUN-1"].firstMatch.exists, "Cold start must NOT display data from previous process run")

        secondRunGate.signal()

        let secondTitle = app.staticTexts["myExams.card.title.COLD-RUN-2"].firstMatch
        XCTAssertTrue(secondTitle.waitForExistence(timeout: 10), "Cold start must render newly fetched data once response arrives")
        XCTAssertEqual(secondTitle.label, "Đề lần chạy hai")
    }
}
