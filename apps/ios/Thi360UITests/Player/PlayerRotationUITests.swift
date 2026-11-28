import Foundation
import XCTest

final class PlayerRotationUITests: XCTestCase {
    private var server: PlayerUITestFixtureServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        server?.stop()
        server = nil
    }

    func testRotationAndSizeClassToggleKeepDraftFlagFilterAndCloseGridSheetWithoutDuplicateRequests() throws {
        // Plan A6 mutation: make first leaf SHORT_ANSWER on certified in-progress-shared fixture to test unsent draft preservation across rotation.
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
        XCUIDevice.shared.orientation = .portrait

        let suite = "vn.unicom.thi360.uitest.playerrot.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-state-probe",
            "-uitest-authenticated",
            "-uitest-destination", "myExams",
            "-uitest-route", "attempt/\(mock.attemptId)",
            "-uitest-api-base-url", mock.baseURL
        ]
        app.launch()

        let field = app.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText("KeepRotate42")

        let flagBtn = app.buttons["question.\(mock.firstItemId).flagButton"].firstMatch
        XCTAssertTrue(flagBtn.waitForExistence(timeout: 5))
        flagBtn.tap()

        let openGrid = app.buttons["player.bottomBar.openGrid"].firstMatch
        XCTAssertTrue(openGrid.waitForExistence(timeout: 6), "Compact iPhone must display bottom grid trigger")
        openGrid.tap()
        let flaggedFilter = app.buttons["player.grid.filter.flagged"].firstMatch
        XCTAssertTrue(flaggedFilter.waitForExistence(timeout: 5))
        flaggedFilter.tap()
        XCTAssertTrue(flaggedFilter.isSelected)
        app.buttons["player.grid.closeButton"].firstMatch.tap()

        let getStateBefore = mock.counts.getState

        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 0.6)
        XCUIDevice.shared.orientation = .portrait
        Thread.sleep(forTimeInterval: 0.6)

        let toggleProbe = app.buttons["player.probe.toggleSizeClass"].firstMatch
        XCTAssertTrue(toggleProbe.waitForExistence(timeout: 5), "State probe size-class toggle must exist under -uitest-state-probe")
        toggleProbe.tap()
        let sidebarTitle = app.staticTexts["player.grid.title"].firstMatch
        XCTAssertTrue(sidebarTitle.waitForExistence(timeout: 5), "Widening size class must display fixed question grid sidebar")
        XCTAssertFalse(app.buttons["player.grid.closeButton"].exists, "Sheet close button must not exist in fixed sidebar")
        XCTAssertTrue(app.buttons["player.grid.filter.flagged"].firstMatch.isSelected)

        app.buttons["player.probe.toggleSizeClass"].firstMatch.tap()
        XCTAssertTrue(app.buttons["player.bottomBar.openGrid"].firstMatch.waitForExistence(timeout: 5))

        let fieldAfter = app.textFields["question.\(mock.firstItemId).shortAnswerField"].firstMatch
        XCTAssertTrue(fieldAfter.waitForExistence(timeout: 6))
        XCTAssertEqual(fieldAfter.value as? String, "KeepRotate42")
        XCTAssertEqual(app.buttons["question.\(mock.firstItemId).flagButton"].firstMatch.value as? String, "1")

        XCTAssertEqual(mock.counts.getState, getStateBefore, "Rotation and resize must not re-fetch GET /attempts/{id}")
    }
}
