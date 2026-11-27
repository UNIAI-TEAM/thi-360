import XCTest

final class FormulaOverflowUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testHorizontalSwipeOnWideFormulaAndMatrixDoesNotFlipEnclosingPager() {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-rich-harness", "formulaOverflow"]
        app.launch()

        let pageIndicator = app.staticTexts["harness.overflow.pageIndicator"].firstMatch
        XCTAssertTrue(pageIndicator.waitForExistence(timeout: 8))
        XCTAssertEqual(pageIndicator.label, "PAGE:0")

        let inlineOverflow = app.scrollViews["richContent.formula.inlineOverflow.scroll"].firstMatch
        XCTAssertTrue(inlineOverflow.waitForExistence(timeout: 8), "Wide inline formula must appear on its own horizontal ScrollView")
        XCTAssertLessThanOrEqual(inlineOverflow.frame.width, app.frame.width + 1, "Page must not scroll horizontally")

        inlineOverflow.swipeLeft()
        XCTAssertEqual(pageIndicator.label, "PAGE:0", "Horizontal swipe on wide inline formula must NOT flip enclosing horizontal pager")

        let blockMatrix = app.scrollViews["richContent.formula.block.scroll"].firstMatch
        XCTAssertTrue(blockMatrix.waitForExistence(timeout: 8), "1x12 matrix block formula must sit in its own horizontal ScrollView")
        blockMatrix.swipeLeft()
        XCTAssertEqual(pageIndicator.label, "PAGE:0", "Horizontal swipe on 1x12 matrix formula must NOT flip enclosing horizontal pager")
    }
}
