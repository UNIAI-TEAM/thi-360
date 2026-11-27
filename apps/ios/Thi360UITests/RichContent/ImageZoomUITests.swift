import XCTest

final class ImageZoomUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testZoomableOpensPinchesUpTo4xAndClosesKeepingSlotAndScroll() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-rich-harness", "imageZoom"]
        app.launch()

        let slotEcho = app.staticTexts["harness.zoom.slotEcho"].firstMatch
        XCTAssertTrue(slotEcho.waitForExistence(timeout: 8))
        XCTAssertEqual(slotEcho.label, "giá trị gốc 99")

        let slotInput = app.textFields["harness.zoom.slotInput"].firstMatch
        XCTAssertTrue(slotInput.waitForExistence(timeout: 8), "Inline slot TextField must be mounted inside TextKit 2 paragraph")
        slotInput.tap()
        slotInput.typeText("XYZ\n")

        let expectedTypedPredicate = NSPredicate(format: "label CONTAINS %@", "XYZ")
        let typedExpectation = expectation(for: expectedTypedPredicate, evaluatedWith: slotEcho)
        wait(for: [typedExpectation], timeout: 5)
        let typedSlotValue = slotEcho.label

        let zoomButton = app.buttons["richContent.image.zoomable"].firstMatch
        XCTAssertTrue(
            zoomButton.waitForExistence(timeout: 8),
            "Expected zoomable figure.image button; hierarchy:\n\(app.debugDescription)"
        )
        let frameBeforeZoom = zoomButton.frame
        zoomButton.tap()

        let closeButton = app.buttons["richContent.imageZoom.closeButton"].firstMatch
        XCTAssertTrue(closeButton.waitForExistence(timeout: 6), "Tapping data-zoom='true' image must open full-screen viewer")

        let zoomScroll = app.scrollViews["richContent.imageZoom.scrollView"].firstMatch
        XCTAssertTrue(zoomScroll.waitForExistence(timeout: 4))
        zoomScroll.pinch(withScale: 3.0, velocity: 2.0)

        let scaleLabel = app.staticTexts["richContent.imageZoom.scaleLabel"].firstMatch
        XCTAssertTrue(scaleLabel.waitForExistence(timeout: 4))
        let numeric = try XCTUnwrap(
            Double(scaleLabel.label.replacingOccurrences(of: "x", with: "")),
            "Expected numeric zoom scale label, got: \(scaleLabel.label)"
        )
        XCTAssertGreaterThan(numeric, 1.0, "Pinch gesture must zoom the image")
        XCTAssertLessThanOrEqual(numeric, 4.05, "Zoom scale must cap at 4.0x")

        closeButton.tap()
        XCTAssertFalse(closeButton.waitForExistence(timeout: 2), "Close button must dismiss full-screen zoom viewer")
        XCTAssertEqual(slotEcho.label, typedSlotValue, "Closing zoom viewer must preserve edited inline slot value")
        XCTAssertEqual(zoomButton.frame.minY, frameBeforeZoom.minY, accuracy: 4.0, "Closing zoom viewer must preserve scroll position")

        let nonZoomImage = app.images["Ảnh không phóng to"].firstMatch
        if nonZoomImage.waitForExistence(timeout: 3), nonZoomImage.isHittable {
            nonZoomImage.tap()
            XCTAssertFalse(app.buttons["richContent.imageZoom.closeButton"].firstMatch.waitForExistence(timeout: 1), "data-zoom='false' image must NOT open viewer")
        }

        let inlineImage = app.images["Ảnh ngoài figure"].firstMatch
        if inlineImage.waitForExistence(timeout: 3), inlineImage.isHittable {
            inlineImage.tap()
            XCTAssertFalse(app.buttons["richContent.imageZoom.closeButton"].firstMatch.waitForExistence(timeout: 1), "Image outside figure.image must NOT open viewer")
        }
    }

    func testZoomableAlsoOpensInLockedMode() {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-rich-harness", "imageZoomLocked"]
        app.launch()

        let zoomButton = app.buttons["richContent.image.zoomable"].firstMatch
        XCTAssertTrue(zoomButton.waitForExistence(timeout: 8))
        zoomButton.tap()

        let closeButton = app.buttons["richContent.imageZoom.closeButton"].firstMatch
        XCTAssertTrue(closeButton.waitForExistence(timeout: 6), "Zoom viewer must also open in locked mode")
        closeButton.tap()
        XCTAssertFalse(closeButton.waitForExistence(timeout: 2))
    }
}
