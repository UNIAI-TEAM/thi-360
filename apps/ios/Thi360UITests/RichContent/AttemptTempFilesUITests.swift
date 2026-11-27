import XCTest

final class AttemptTempFilesUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testColdStartAfterMidViewerCrashPurgesAttemptTempFilesBeforeFirstScreen() {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-rich-harness", "tempFiles"]
        app.launch()

        let openButton = app.buttons["harness.temp.openButton"].firstMatch
        XCTAssertTrue(openButton.waitForExistence(timeout: 8))
        openButton.tap()

        let createdExists = app.staticTexts["harness.temp.createdExists"].firstMatch
        let existsPredicate = NSPredicate(format: "label == 'CREATED_EXISTS:true'")
        expectation(for: existsPredicate, evaluatedWith: createdExists)
        waitForExpectations(timeout: 8)

        // Terminate app abruptly while viewer temp file is still open on disk.
        app.terminate()

        app.launch()
        let mountCount = app.staticTexts["harness.temp.mountCount"].firstMatch
        XCTAssertTrue(mountCount.waitForExistence(timeout: 8))
        XCTAssertEqual(
            mountCount.label,
            "MOUNT_COUNT:0",
            "SessionCleanup.runAppStart() must purge Caches/AttemptAssets/ before the first screen mounts"
        )
    }
}
