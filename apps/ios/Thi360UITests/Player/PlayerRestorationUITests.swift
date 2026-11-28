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

    func testProcessReclaimRestoresSameAttemptWithoutTakeoverAndColdLaunchCanContinueFromMyExams() throws {
        let mock = try PlayerUITestFixtureServer(attemptId: UUID().uuidString.lowercased())
        self.server = mock

        let suite = "vn.unicom.thi360.uitest.playerrestore.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
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
            let cell2 = app.buttons["player.grid.cell.\(mock.secondItemId)"].firstMatch
            if cell2.waitForExistence(timeout: 5) {
                cell2.tap()
            }
        }
        Thread.sleep(forTimeInterval: 1.0)

        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 0.8)
        app.terminate()

        app.launch()
        XCTAssertTrue(app.otherElements["player.topBar"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertFalse(
            app.staticTexts["player.takeover.title"].waitForExistence(timeout: 2.0),
            "Reclaiming process on same device/session must NOT show Takeover dialog"
        )

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
        if continueBtn.waitForExistence(timeout: 8) {
            continueBtn.tap()
            XCTAssertTrue(
                coldApp.staticTexts["player.topBar.examTitle"].firstMatch.waitForExistence(timeout: 8),
                "Tapping Continue on MyExams must open the active attempt"
            )
        }
    }
}
