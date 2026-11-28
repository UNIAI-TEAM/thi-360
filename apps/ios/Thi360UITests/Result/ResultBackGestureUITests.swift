import Foundation
import XCTest

final class ResultBackGestureUITests: XCTestCase {
    private var server: ContractFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testLeftEdgeBackSwipeFromReviewToResultAndResultToHome() throws {
        let attemptId = "00000000-0000-7000-8000-000000000001"
        let resultData = try ContractFixtureHTTPServer.loadFixture("api/attempts-result-200-scored.json")
        let reviewData = try ContractFixtureHTTPServer.loadFixture("api/attempts-review-200-full-solution.json")
        let examOptionsData = try ContractFixtureHTTPServer.loadFixture("api/catalog-options-200-exam.json")
        let subjectOptionsData = try ContractFixtureHTTPServer.loadFixture("api/catalog-options-200-subject.json")

        let lock = NSLock()
        var resultCallCount = 0
        var reviewCallCount = 0

        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        try mockServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/filters", relativePath: "api/storefront-filters-200.json")
        try mockServer.registerFixture(method: "GET", path: "/api/v1/public/storefront/products", relativePath: "api/storefront-products-list-200.json")
        mockServer.register(method: "GET", path: "/api/v1/catalog/options") { _, query, _ in
            if query.contains("SUBJECT") {
                return ContractHTTPResponse(statusCode: 200, body: subjectOptionsData)
            }
            return ContractHTTPResponse(statusCode: 200, body: examOptionsData)
        }
        mockServer.register(method: "GET", path: "/api/v1/me/attempts/\(attemptId)/result") { _, _, _ in
            lock.lock()
            resultCallCount += 1
            lock.unlock()
            return ContractHTTPResponse(statusCode: 200, body: resultData)
        }
        mockServer.register(method: "GET", path: "/api/v1/me/attempts/\(attemptId)/review") { _, _, _ in
            lock.lock()
            reviewCallCount += 1
            lock.unlock()
            return ContractHTTPResponse(statusCode: 200, body: reviewData)
        }
        try mockServer.start()
        self.server = mockServer

        let suite = "vn.unicom.thi360.uitest.result.backgesture.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "result/\(attemptId)",
            "-uitest-api-base-url", "http://127.0.0.1:\(mockServer.port)"
        ]
        app.launch()

        let examName = app.staticTexts["result.header.examName"].firstMatch
        XCTAssertTrue(examName.waitForExistence(timeout: 12), "Expected Result screen to load")

        let reviewButton = app.buttons["result.actions.reviewButton"].firstMatch
        for _ in 0..<4 where !reviewButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(reviewButton.waitForExistence(timeout: 8), "Expected Review button on scored Result screen")
        reviewButton.tap()

        let reviewTitle = app.staticTexts["review.header.title"].firstMatch
        XCTAssertTrue(reviewTitle.waitForExistence(timeout: 10), "Tapping Review must open Review screen")

        lock.lock()
        let reviewCallsBeforeBack = reviewCallCount
        let resultCallsBeforeBack = resultCallCount
        lock.unlock()
        XCTAssertEqual(reviewCallsBeforeBack, 1)
        XCTAssertGreaterThanOrEqual(resultCallsBeforeBack, 1)

        swipeBackFromLeftEdge(in: app)

        let restoredResultNav = app.navigationBars["Kết quả lượt thi"].firstMatch
        let restoredExamName = app.staticTexts["result.header.examName"].firstMatch
        XCTAssertTrue(
            restoredResultNav.waitForExistence(timeout: 8) || restoredExamName.waitForExistence(timeout: 4),
            "Left-edge swipe back from Review must return to Result"
        )
        XCTAssertFalse(reviewTitle.exists, "Review screen must be popped after swipe back")

        let refetchDeadline = Date().addingTimeInterval(6)
        var didRefetch = false
        while Date() < refetchDeadline {
            lock.lock()
            didRefetch = resultCallCount > resultCallsBeforeBack
            lock.unlock()
            if didRefetch { break }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        XCTAssertTrue(didRefetch, "Returning to Result from Review must refetch /result")

        swipeBackFromLeftEdge(in: app)

        let homeHeading = app.descendants(matching: .any).matching(identifier: "shell.home.heading").firstMatch
        let poppedResult = waitUntilGone(restoredResultNav, timeout: 8)
        XCTAssertTrue(poppedResult || homeHeading.waitForExistence(timeout: 5), "Left-edge swipe back from Result must return to Home")
    }

    private func swipeBackFromLeftEdge(in app: XCUIApplication) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    private func waitUntilGone(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "exists == false")
        let exp = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter.wait(for: [exp], timeout: timeout) == .completed
    }
}
