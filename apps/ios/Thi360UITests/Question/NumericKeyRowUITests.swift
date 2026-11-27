import XCTest

final class NumericKeyRowUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testFractionNumericKeyRowInsertsNegativeFractionWhileKeepingKeyboardOpen() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-question-harness", "numericKeyRowFraction"]
        app.launch()

        let input = app.textFields["question.harness-short-fraction.shortAnswerField"]
        XCTAssertTrue(input.waitForExistence(timeout: 10), "Fraction input must appear")
        input.tap()

        let minusKey = app.buttons["question.harness-short-fraction.numericKey.num-minus"]
        let slashKey = app.buttons["question.harness-short-fraction.numericKey.num-slash"]
        XCTAssertTrue(minusKey.waitForExistence(timeout: 5))
        XCTAssertTrue(slashKey.waitForExistence(timeout: 5))

        minusKey.tap()
        input.typeText("3")
        slashKey.tap()
        input.typeText("4")

        let echo = app.staticTexts["harness.question.answerEcho"]
        XCTAssertTrue(echo.waitForExistence(timeout: 5))
        XCTAssertEqual(echo.label, "-3/4")
        XCTAssertEqual(input.value as? String, "-3/4")
    }
}
