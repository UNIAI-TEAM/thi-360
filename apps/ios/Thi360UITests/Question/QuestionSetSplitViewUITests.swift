import XCTest

final class QuestionSetSplitViewUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testSplitViewPassageScrollAndChildDraftPreservationAcrossSizeClassAndRotation() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-question-harness", "questionSetSplitView"]
        app.launch()

        let setContainer = app.otherElements["question.harness-set.setContainer"]
        XCTAssertTrue(setContainer.waitForExistence(timeout: 10), "QuestionSet container must appear")

        let isIPad = UIDevice.current.userInterfaceIdiom == .pad
        if isIPad {
            let passageScroll = app.scrollViews["question.harness-set.passageScroll"]
            let childrenScroll = app.scrollViews["question.harness-set.childrenScroll"]
            XCTAssertTrue(passageScroll.waitForExistence(timeout: 5), "iPad must render left passage ScrollView")
            XCTAssertTrue(childrenScroll.waitForExistence(timeout: 5), "iPad must render right children ScrollView")
            XCTAssertLessThanOrEqual(
                passageScroll.frame.maxX,
                childrenScroll.frame.minX + 4,
                "Passage pane must sit strictly to the left of children pane in regular two-column layout"
            )

            let child2Before = app.textFields["question.set-child-2.shortAnswerField"]
            let yBefore = child2Before.exists ? child2Before.frame.minY : -1
            passageScroll.swipeUp()
            XCTAssertTrue(childrenScroll.exists)
            if yBefore >= 0 && child2Before.exists {
                XCTAssertEqual(child2Before.frame.minY, yBefore, accuracy: 2.0, "Scrolling passage must not move right children pane")
            }
        }

        let child2Field = app.textFields["question.set-child-2.shortAnswerField"]
        var scrollAttempts = 0
        while (!child2Field.exists || !child2Field.isHittable) && scrollAttempts < 6 {
            if isIPad, app.scrollViews["question.harness-set.childrenScroll"].exists {
                app.scrollViews["question.harness-set.childrenScroll"].swipeUp()
            } else {
                app.swipeUp()
            }
            scrollAttempts += 1
        }
        XCTAssertTrue(child2Field.waitForExistence(timeout: 5), "Child 2 short answer input must exist")
        child2Field.tap()
        child2Field.typeText("tái tạo")

        let echo = app.staticTexts["harness.question.setEcho"]
        XCTAssertTrue(echo.waitForExistence(timeout: 5))
        XCTAssertTrue(echo.label.contains("LAST:set-child-2"), "Child answer must route to child ID, not set ID")
        XCTAssertTrue(echo.label.contains("C2:tái tạo"))

        let toggleButton = app.buttons["harness.question.toggleSizeClass"]
        XCTAssertTrue(toggleButton.waitForExistence(timeout: 5))
        toggleButton.tap()
        XCTAssertTrue(echo.label.contains("C2:tái tạo"), "Child draft must survive size-class switch")

        toggleButton.tap()
        XCTAssertTrue(echo.label.contains("C2:tái tạo"), "Child draft must survive switching back")

        if isIPad {
            XCUIDevice.shared.orientation = .landscapeLeft
            XCTAssertTrue(echo.waitForExistence(timeout: 5))
            XCTAssertTrue(echo.label.contains("C2:tái tạo"), "Child draft must survive landscape rotation")
            XCUIDevice.shared.orientation = .portrait
            XCTAssertTrue(echo.label.contains("C2:tái tạo"), "Child draft must survive portrait rotation")
        }
    }
}
