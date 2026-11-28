import Foundation
import XCTest

final class PlayerBackUITests: XCTestCase {
    private var server: PlayerUITestFixtureServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    private func performLeftEdgeSwipe(in app: XCUIApplication) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.03, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.68, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    func testBackEdgeSwipeDialogPriorityBusyBlockAndLeaveConfirmWhenUnsaved() throws {
        // Plan A6 mutation: set navigationMode=FREE and first leaf SHORT_ANSWER on certified in-progress-shared fixture for deterministic Submit dialog and unsaved draft testing.
        let mock = try PlayerUITestFixtureServer(
            attemptId: UUID().uuidString.lowercased(),
            mutateState: { json in
                json["navigationMode"] = "FREE"
                if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
                   var items = parts[0]["items"] as? [[String: Any]], !items.isEmpty
                {
                    items[0]["questionType"] = "SHORT_ANSWER"
                    items[0]["questionBody"] = ["type": "SHORT_ANSWER", "answerFormat": "TEXT"]
                    parts[0]["items"] = items
                    json["parts"] = parts
                }
            }
        )
        self.server = mock

        let suite = "vn.unicom.thi360.uitest.playerback.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "myExams",
            "-uitest-route", "attempt/\(mock.attemptId)",
            "-uitest-api-base-url", mock.baseURL
        ]
        app.launch()

        let topBar = app.otherElements["player.topBar"].firstMatch
        XCTAssertTrue(topBar.waitForExistence(timeout: 10))

        let openGrid = app.buttons["player.bottomBar.openGrid"].firstMatch
        XCTAssertTrue(openGrid.waitForExistence(timeout: 6), "Compact iPhone must display bottom grid trigger")
        openGrid.tap()
        let closeGrid = app.buttons["player.grid.closeButton"].firstMatch
        XCTAssertTrue(closeGrid.waitForExistence(timeout: 5))
        closeGrid.tap()
        XCTAssertFalse(closeGrid.waitForExistence(timeout: 2))

        let submitAction = app.buttons["player.topBar.actionButton"].firstMatch
        XCTAssertTrue(submitAction.waitForExistence(timeout: 6))
        submitAction.tap()
        let submitTitle = app.staticTexts["player.submit.title"].firstMatch
        XCTAssertTrue(submitTitle.waitForExistence(timeout: 5))
        performLeftEdgeSwipe(in: app)
        XCTAssertFalse(submitTitle.waitForExistence(timeout: 3), "Left-edge swipe back must dismiss idle Submit dialog")
        XCTAssertEqual(mock.counts.submit, 0)

        mock.setSaveAnswersStatusCode(503)
        let field = app.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 6))
        field.tap()
        field.typeText("Draft unsaved")
        Thread.sleep(forTimeInterval: 1.5)

        performLeftEdgeSwipe(in: app)

        let leaveTitle = app.staticTexts["player.leaveConfirm.title"].firstMatch
        XCTAssertTrue(leaveTitle.waitForExistence(timeout: 6), "Left-edge swipe back with unsaved changes must show LeaveConfirmDialog (A-86)")

        app.buttons["player.leaveConfirm.stayButton"].firstMatch.tap()
        XCTAssertFalse(leaveTitle.waitForExistence(timeout: 2))
        XCTAssertTrue(topBar.exists)
        XCTAssertEqual(field.value as? String, "Draft unsaved")

        performLeftEdgeSwipe(in: app)
        XCTAssertTrue(leaveTitle.waitForExistence(timeout: 5))
        app.buttons["player.leaveConfirm.leaveButton"].firstMatch.tap()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8), "Leaving attempt must return to shell")
        XCTAssertEqual(mock.counts.submit, 0, "Leaving attempt must never call submit")

        app.terminate()
        app.launch()
        let reopenedField = app.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(reopenedField.waitForExistence(timeout: 10))
        XCTAssertEqual(reopenedField.value as? String, "Draft unsaved", "Draft must remain in local store after Leave")
    }

    func testBackEdgeSwipeDoesNotDismissInFlightSubmitDialog() throws {
        // Plan A6 mutation: set navigationMode=FREE on certified in-progress-shared fixture so top action opens SubmitDialog.
        let mock = try PlayerUITestFixtureServer(
            attemptId: UUID().uuidString.lowercased(),
            mutateState: { json in
                json["navigationMode"] = "FREE"
            }
        )
        mock.setSubmitDelay(2.5)
        self.server = mock

        let suite = "vn.unicom.thi360.uitest.playerbusy.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "myExams",
            "-uitest-route", "attempt/\(mock.attemptId)",
            "-uitest-api-base-url", mock.baseURL
        ]
        app.launch()

        let submitAction = app.buttons["player.topBar.actionButton"].firstMatch
        XCTAssertTrue(submitAction.waitForExistence(timeout: 10))
        submitAction.tap()

        let confirmBtn = app.buttons["player.submit.confirmButton"].firstMatch
        XCTAssertTrue(confirmBtn.waitForExistence(timeout: 5))
        confirmBtn.tap()

        Thread.sleep(forTimeInterval: 0.4)
        performLeftEdgeSwipe(in: app)
        XCTAssertTrue(
            app.staticTexts["player.submit.title"].firstMatch.exists,
            "Back gesture must NOT dismiss Submit dialog while submission is in flight (ĐT 421)"
        )
        XCTAssertFalse(app.tabBars.firstMatch.exists)

        let closedHeading = app.staticTexts["player.closed.heading"].firstMatch
        XCTAssertTrue(closedHeading.waitForExistence(timeout: 8), "In-flight submit must complete and transition to Closed screen")
        XCTAssertEqual(mock.counts.submit, 1)
    }

    func testBackEdgeSwipeWhenCleanLeavesImmediatelyWithoutLeaveConfirmOrSubmit() throws {
        let mock = try PlayerUITestFixtureServer(attemptId: UUID().uuidString.lowercased())
        self.server = mock

        let suite = "vn.unicom.thi360.uitest.playerclean.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "myExams",
            "-uitest-route", "attempt/\(mock.attemptId)",
            "-uitest-api-base-url", mock.baseURL
        ]
        app.launch()

        let examTitle = app.staticTexts["player.topBar.examTitle"].firstMatch
        XCTAssertTrue(examTitle.waitForExistence(timeout: 10))
        performLeftEdgeSwipe(in: app)

        XCTAssertFalse(app.staticTexts["player.leaveConfirm.title"].waitForExistence(timeout: 1.5))
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8))
        XCTAssertEqual(mock.counts.submit, 0)
    }

    func testBackEdgeSwipeOnEmbeddedWaitingReturnsAndStopsPolling() throws {
        let mock = try PlayerUITestFixtureServer(
            attemptId: UUID().uuidString.lowercased(),
            variant: "starting",
            mutateState: { json in
                json["examName"] = "HSA chờ khởi tạo"
            }
        )
        self.server = mock

        let suite = "vn.unicom.thi360.uitest.playerwaitback.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-destination", "myExams",
            "-uitest-route", "attempt/\(mock.attemptId)",
            "-uitest-api-base-url", mock.baseURL
        ]
        app.launch()

        let waitingTitle = app.staticTexts["HSA chờ khởi tạo"].firstMatch
        XCTAssertTrue(waitingTitle.waitForExistence(timeout: 10), "Embedded WaitingView must render for STARTING attempt")

        performLeftEdgeSwipe(in: app)
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8), "Back gesture on embedded WaitingView must return to shell")

        Thread.sleep(forTimeInterval: 0.4)
        let pollsAfterLeave = mock.counts.getState
        Thread.sleep(forTimeInterval: 2.5)
        XCTAssertEqual(mock.counts.getState, pollsAfterLeave, "Leaving embedded WaitingView must stop all polling calls (ĐT 424)")
    }
}
