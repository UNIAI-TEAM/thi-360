import XCTest

final class ShortAnswerUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testSmartPunctuationOff() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-question-harness", "shortAnswerSmartPunctuation"]
        app.launch()

        let input = app.textFields["question.harness-short-text.shortAnswerField"]
        XCTAssertTrue(input.waitForExistence(timeout: 10), "Short answer input must appear")
        input.tap()
        input.typeText("\"CO2\" -- x")

        let echo = app.staticTexts["harness.question.answerEcho"]
        XCTAssertTrue(echo.waitForExistence(timeout: 5))
        let value = echo.label
        XCTAssertEqual(value, "\"CO2\" -- x", "Smart punctuation must stay off (no curly quotes or em-dash)")
        XCTAssertFalse(value.contains("“"))
        XCTAssertFalse(value.contains("”"))
        XCTAssertFalse(value.contains("—"))
    }
}
