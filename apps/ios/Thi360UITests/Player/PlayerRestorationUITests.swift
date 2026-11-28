import Foundation
import XCTest

final class PlayerRestorationUITests: XCTestCase {
    private var server: PlayerUITestFixtureServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testProcessReclaimRestoresSameAttemptQuestionAndUnsavedDraftAndColdLaunchCanContinueFromMyExams() throws {
        // Plan A6 mutation: make first leaf SHORT_ANSWER on certified in-progress-shared fixture so unsaved draft persistence across process reclaim and cold launch is verified.
        let mock = try PlayerUITestFixtureServer(
            attemptId: UUID().uuidString.lowercased(),
            mutateState: { json in
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

        let suite = "vn.unicom.thi360.uitest.playerrestore.\(UUID().uuidString)"
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

        mock.setSaveAnswersStatusCode(503)
        let field = app.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 6))
        field.tap()
        field.typeText("RestoreDraft99")
        Thread.sleep(forTimeInterval: 0.6)

        let openGrid = app.buttons["player.bottomBar.openGrid"].firstMatch
        XCTAssertTrue(openGrid.waitForExistence(timeout: 6), "Compact iPhone must display grid trigger")
        openGrid.tap()

        let cell2 = app.buttons["player.grid.cell.\(mock.secondItemId)"].firstMatch
        XCTAssertTrue(cell2.waitForExistence(timeout: 5), "Question 2 cell must exist in grid sheet")
        cell2.tap()
        Thread.sleep(forTimeInterval: 0.8)
        XCTAssertTrue(
            openGrid.label.contains("2/"),
            "Expected bottom bar position to reflect Question 2, got: \(openGrid.label)"
        )

        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 0.8)
        app.terminate()

        app.launch()
        XCTAssertTrue(app.staticTexts["player.topBar.examTitle"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertFalse(
            app.staticTexts["player.takeover.title"].waitForExistence(timeout: 2.0),
            "Reclaiming process on same device/session must NOT show Takeover dialog"
        )

        let restoredGridBtn = app.buttons["player.bottomBar.openGrid"].firstMatch
        XCTAssertTrue(restoredGridBtn.waitForExistence(timeout: 6))
        XCTAssertTrue(
            restoredGridBtn.label.contains("2/"),
            "Process reclaim must restore Question 2 position, got: \(restoredGridBtn.label)"
        )

        restoredGridBtn.tap()
        let cell1 = app.buttons["player.grid.cell.\(mock.firstItemId)"].firstMatch
        XCTAssertTrue(cell1.waitForExistence(timeout: 5))
        cell1.tap()
        let restoredField = app.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(restoredField.waitForExistence(timeout: 6))
        XCTAssertEqual(restoredField.value as? String, "RestoreDraft99", "Unsaved local draft must survive process reclaim")

        restoredGridBtn.tap()
        let cell2Again = app.buttons["player.grid.cell.\(mock.secondItemId)"].firstMatch
        XCTAssertTrue(cell2Again.waitForExistence(timeout: 5))
        cell2Again.tap()
        Thread.sleep(forTimeInterval: 0.8)

        app.terminate()
        let coldApp = XCUIApplication()
        coldApp.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-authenticated",
            "-uitest-destination", "myExams",
            "-uitest-api-base-url", mock.baseURL
        ]
        coldApp.launch()

        XCTAssertTrue(coldApp.tabBars.firstMatch.waitForExistence(timeout: 8), "User cold launch starts on main shell")
        let continueBtn = coldApp.buttons["Tiếp tục"].firstMatch
        XCTAssertTrue(continueBtn.waitForExistence(timeout: 8), "MyExams card must expose Tiếp tục button for active attempt")
        continueBtn.tap()

        XCTAssertTrue(
            coldApp.staticTexts["player.topBar.examTitle"].firstMatch.waitForExistence(timeout: 10),
            "Tapping Continue on MyExams must open the active attempt"
        )
        XCTAssertFalse(
            coldApp.staticTexts["player.takeover.title"].waitForExistence(timeout: 1.5),
            "Cold launch Continue on same device must NOT show Takeover dialog"
        )
        let coldGridBtn = coldApp.buttons["player.bottomBar.openGrid"].firstMatch
        XCTAssertTrue(coldGridBtn.waitForExistence(timeout: 6))
        XCTAssertTrue(
            coldGridBtn.label.contains("2/"),
            "Cold launch Continue must restore Question 2 position, got: \(coldGridBtn.label)"
        )
        coldGridBtn.tap()
        let coldCell1 = coldApp.buttons["player.grid.cell.\(mock.firstItemId)"].firstMatch
        XCTAssertTrue(coldCell1.waitForExistence(timeout: 5))
        coldCell1.tap()
        let coldField = coldApp.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(coldField.waitForExistence(timeout: 6))
        XCTAssertEqual(coldField.value as? String, "RestoreDraft99", "Unsaved draft must survive cold launch Continue")
    }
}
