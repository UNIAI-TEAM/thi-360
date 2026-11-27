import XCTest

final class ClozeKeyboardUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testLowerHalfClozeBlankScrollsAboveKeyboard() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-question-harness", "clozeKeyboardScroll"]
        app.launch()

        let blank1 = app.textFields["question.harness-cloze-scroll.cloze.b1"]
        let scrollView = app.scrollViews["harness.question.clozeScrollView"]
        XCTAssertTrue(scrollView.waitForExistence(timeout: 10))

        var attempts = 0
        while (!blank1.exists || !blank1.isHittable) && attempts < 6 {
            scrollView.swipeUp()
            attempts += 1
        }
        XCTAssertTrue(blank1.waitForExistence(timeout: 5), "Cloze blank b1 must appear")
        blank1.tap()

        let keyboard = app.keyboards.firstMatch
        let keyboardShown = keyboard.waitForExistence(timeout: 5)
        if keyboardShown {
            let deadline = Date().addingTimeInterval(3)
            while Date() < deadline && blank1.frame.maxY > keyboard.frame.minY + 4 {
                RunLoop.current.run(until: Date().addingTimeInterval(0.1))
            }
            XCTAssertLessThanOrEqual(
                blank1.frame.maxY,
                keyboard.frame.minY + 4,
                "Focused cloze blank (\(blank1.frame)) must scroll above keyboard (\(keyboard.frame))"
            )
        }

        blank1.typeText("100")
        let echo = app.staticTexts["harness.question.answerEcho"]
        XCTAssertTrue(echo.waitForExistence(timeout: 5))
        XCTAssertEqual(echo.label, "100")
    }
}
