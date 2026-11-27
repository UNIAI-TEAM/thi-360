import XCTest

final class RefreshableContainerUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testPullToRefreshInvokesRefreshAction() {
        let suite = "vn.unicom.thi360.uitest.refresh.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-state-probe",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "orders"
        ]
        app.launch()

        let counter = app.staticTexts["route.placeholder.refreshCount"].firstMatch
        XCTAssertTrue(counter.waitForExistence(timeout: 8))
        XCTAssertEqual(counter.label, "0")

        let scrollView = app.scrollViews.firstMatch
        XCTAssertTrue(scrollView.waitForExistence(timeout: 5))

        let start = scrollView.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25))
        let finish = scrollView.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.1, thenDragTo: finish)

        let predicate = NSPredicate(format: "label != '0'")
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: counter)
        let result = XCTWaiter().wait(for: [expectation], timeout: 8)
        XCTAssertEqual(result, .completed, "Pull-to-refresh gesture must trigger RefreshableContainer onRefresh")
    }
}
