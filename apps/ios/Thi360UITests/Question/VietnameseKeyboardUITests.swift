import XCTest

final class VietnameseKeyboardUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testVietnameseEssayInputWordCountAndUndoRedo() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-question-harness", "vietnameseTelexEssay"]
        app.launch()

        let editor = app.textViews["question.harness-telex-essay.essayInput"]
        XCTAssertTrue(editor.waitForExistence(timeout: 10), "Essay editor textView must appear")
        editor.tap()

        let keyboardVisible = app.keyboards.firstMatch.waitForExistence(timeout: 4)
        let metaLabel = app.staticTexts["harness.question.essayEmitCount"]
        let forceTelex = ProcessInfo.processInfo.environment["THI360_VI_TELEX_ACTIVE"] == "1"
        let hasVietnameseIM = metaLabel.exists && metaLabel.label.contains("IM:vi")
        let useSoftwareTelex = keyboardVisible && (forceTelex || hasVietnameseIM)

        if useSoftwareTelex {
            let keyboard = app.keyboards.firstMatch

            for _ in 0..<4 {
                if metaLabel.exists && metaLabel.label.contains("IM:vi") {
                    break
                }
                let globePredicate = NSPredicate(
                    format: "label == 'Bàn phím tiếp theo' OR label == 'Next keyboard' OR identifier == 'Next keyboard'"
                )
                let globeButton = app.buttons.matching(globePredicate).firstMatch
                if globeButton.exists && globeButton.isHittable {
                    globeButton.tap()
                    RunLoop.current.run(until: Date().addingTimeInterval(0.45))
                } else {
                    break
                }
            }

            try typeViaSoftwareKeyboard(app: app, sequence: "Tieengs Vieetj cos daaus")
            let echoProbe = app.staticTexts["harness.question.essayEcho"]
            if echoProbe.exists && !echoProbe.label.contains("dấu") {
                let returnKey = keyboard.buttons["Nhập"].exists
                    ? keyboard.buttons["Nhập"]
                    : (keyboard.buttons["nhập"].exists
                        ? keyboard.buttons["nhập"]
                        : (keyboard.buttons["return"].exists ? keyboard.buttons["return"] : keyboard.buttons["Return"]))
                if returnKey.exists {
                    returnKey.tap()
                }
            }
        } else {
            editor.typeText("Tiếng Việt có dấu")
        }

        let wordCount = app.staticTexts["question.harness-telex-essay.wordCount"]
        XCTAssertTrue(wordCount.waitForExistence(timeout: 5))
        XCTAssertEqual(wordCount.label, "4 chữ")

        let echo = app.staticTexts["harness.question.essayEcho"]
        XCTAssertTrue(echo.waitForExistence(timeout: 5))
        let expectedHTML = "<p>Tiếng Việt có dấu</p>"
        XCTAssertTrue(
            echo.label.hasPrefix(expectedHTML),
            "Expected Vietnamese Telex output '\(expectedHTML)', got: \(echo.label) | meta=\(metaLabel.label)"
        )

        let undoButton = app.buttons["question.harness-telex-essay.essayToolbar.undo"]
        let redoButton = app.buttons["question.harness-telex-essay.essayToolbar.redo"]
        XCTAssertTrue(undoButton.waitForExistence(timeout: 5))
        XCTAssertTrue(redoButton.waitForExistence(timeout: 5))
        XCTAssertTrue(undoButton.isEnabled, "Undo button must be enabled after typing Vietnamese text")

        let beforeUndo = echo.label
        undoButton.tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        let afterUndo = echo.exists ? echo.label : ""
        XCTAssertNotEqual(afterUndo, beforeUndo, "Undo must revert the last typed edit")
        XCTAssertTrue(redoButton.isEnabled, "Redo button must become enabled after Undo")

        redoButton.tap()
        XCTAssertTrue(echo.waitForExistence(timeout: 5))
        XCTAssertEqual(echo.label, beforeUndo, "Redo must restore the exact Vietnamese text")
    }

    @MainActor
    private func typeViaSoftwareKeyboard(app: XCUIApplication, sequence: String) throws {
        let keyboard = app.keyboards.firstMatch
        for ch in sequence {
            if ch == " " {
                let spaceKey = keyboard.keys["space"].exists
                    ? keyboard.keys["space"]
                    : (keyboard.keys["Dấu cách"].exists ? keyboard.keys["Dấu cách"] : keyboard.keys["dấu cách"])
                XCTAssertTrue(spaceKey.waitForExistence(timeout: 3), "Space key must exist on software keyboard")
                spaceKey.tap()
                continue
            }

            let raw = String(ch)
            let exactPredicate = NSPredicate(format: "label == %@", raw)
            let exactKey = keyboard.keys.matching(exactPredicate).firstMatch
            if exactKey.exists {
                exactKey.tap()
                continue
            }

            let shift = keyboard.buttons["shift"].exists
                ? keyboard.buttons["shift"]
                : keyboard.keys["shift"]
            if shift.exists {
                shift.tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.15))
            }

            let toggledKey = keyboard.keys.matching(exactPredicate).firstMatch
            if toggledKey.exists {
                toggledKey.tap()
                continue
            }

            let fallback = keyboard.keys[raw.lowercased()]
            XCTAssertTrue(fallback.waitForExistence(timeout: 2), "Software key '\(raw)' not found on keyboard")
            fallback.tap()
        }
    }
}
