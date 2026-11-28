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

    func testBackClosesGridSheetDialogCleanExitAndLeaveConfirmWhenUnsaved() throws {
        // Plan A6 mutation: make first leaf SHORT_ANSWER on certified in-progress-shared fixture for deterministic text input.
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
        if openGrid.waitForExistence(timeout: 4) {
            openGrid.tap()
            let closeGrid = app.buttons["player.grid.closeButton"].firstMatch
            XCTAssertTrue(closeGrid.waitForExistence(timeout: 5))
            closeGrid.tap()
            XCTAssertFalse(closeGrid.waitForExistence(timeout: 2))
        }

        let submitAction = app.buttons["player.topBar.actionButton"].firstMatch
        XCTAssertTrue(submitAction.waitForExistence(timeout: 6))
        submitAction.tap()
        let submitTitle = app.staticTexts["player.submit.title"].firstMatch
        XCTAssertTrue(submitTitle.waitForExistence(timeout: 5))
        app.buttons["player.submit.cancelButton"].firstMatch.tap()
        XCTAssertFalse(submitTitle.waitForExistence(timeout: 2))
        XCTAssertEqual(mock.counts.submit, 0)

        mock.setSaveAnswersStatusCode(503)
        let field = app.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 6))
        field.tap()
        field.typeText("Draft unsaved")
        Thread.sleep(forTimeInterval: 1.5)

        let backButton = app.buttons["player.topBar.backButton"].firstMatch
        XCTAssertTrue(backButton.waitForExistence(timeout: 5))
        backButton.tap()

        let leaveTitle = app.staticTexts["player.leaveConfirm.title"].firstMatch
        XCTAssertTrue(leaveTitle.waitForExistence(timeout: 6), "LeaveConfirmDialog must appear when unsaved answers exist")

        app.buttons["player.leaveConfirm.stayButton"].firstMatch.tap()
        XCTAssertFalse(leaveTitle.waitForExistence(timeout: 2))
        XCTAssertTrue(topBar.exists)

        backButton.tap()
        XCTAssertTrue(leaveTitle.waitForExistence(timeout: 5))
        app.buttons["player.leaveConfirm.leaveButton"].firstMatch.tap()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8), "Leaving attempt must return to shell")
        XCTAssertEqual(mock.counts.submit, 0, "Leaving attempt must never call submit")
    }

    func testBackWhenCleanLeavesImmediatelyWithoutLeaveConfirmOrSubmit() throws {
        let mock = try PlayerUITestFixtureServer(attemptId: "00000000-0000-7000-8000-000000000102")
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

        let backButton = app.buttons["player.topBar.backButton"].firstMatch
        XCTAssertTrue(backButton.waitForExistence(timeout: 10))
        backButton.tap()

        XCTAssertFalse(app.staticTexts["player.leaveConfirm.title"].waitForExistence(timeout: 1.5))
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8))
        XCTAssertEqual(mock.counts.submit, 0)
    }
}
