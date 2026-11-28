import Foundation
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PlayerEssayImeTests: XCTestCase {
    func testFifteenSecondMaxWaitSendsOnlyCommittedTextAndNeverUncommittedTelexComposition() async throws {
        // Plan A6 mutation: convert first item of certified in-progress-shared fixture to ESSAY so Essay 15s maxWait + Telex IME behavior runs end-to-end against FakeAttemptServer.
        let harness = try PlayerTestHarness(
            initialStateVariant: "in-progress-shared",
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
                   var items = parts[0]["items"] as? [[String: Any]], !items.isEmpty
                {
                    items[0]["questionType"] = "ESSAY"
                    items[0]["questionBody"] = ["type": "ESSAY", "wordLimit": 50]
                    parts[0]["items"] = items
                    json["parts"] = parts
                }
            }
        )
        defer { harness.dispose() }
        await harness.settle()

        let essayItemId = harness.server.firstLeafItemId()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 400))
        let textView = EssayUITextView(usingTextKit2: false)
        textView.frame = CGRect(x: 0, y: 0, width: 360, height: 220)
        window.addSubview(textView)
        window.makeKeyAndVisible()
        defer { window.isHidden = true }

        let controller = EssayEditorController()
        controller.textView = textView
        controller.onCommittedHTMLChange = { html, words in
            harness.engine.answer(itemId: essayItemId, value: AnswerValues.essay(html: html, wordCount: words))
        }
        textView.onDidUnmarkText = { [weak controller, weak textView] in
            guard let controller, let textView else { return }
            controller.handleTextDidChange(in: textView)
        }
        controller.configure(
            baseFont: UIFont.preferredFont(forTextStyle: .body),
            textColor: .label,
            html: "",
            isLocked: false
        )

        let initialSaveCount = harness.server.callCount(for: .saveAnswers)

        let steps = [
            "B", "Bà", "Bài", "Bài ", "Bài l", "Bài là", "Bài làm",
            "Bài làm ", "Bài làm t", "Bài làm ti", "Bài làm tiế", "Bài làm tiến", "Bài làm tiếng",
            "Bài làm tiếng "
        ]
        for (idx, text) in steps.enumerated() {
            textView.attributedText = NSAttributedString(
                string: text,
                attributes: [.font: UIFont.preferredFont(forTextStyle: .body), .foregroundColor: UIColor.label]
            )
            textView.selectedRange = NSRange(location: (text as NSString).length, length: 0)
            controller.handleTextDidChange(in: textView)
            if idx < steps.count - 1 {
                await harness.clock.advance(by: .seconds(1))
            }
        }
        await harness.clock.advance(by: .seconds(1))
        XCTAssertEqual(
            harness.server.callCount(for: .saveAnswers),
            initialSaveCount,
            "Continuous typing every 1s must not flush before 15s maxWait"
        )

        textView.setMarkedText("Vieej", selectedRange: NSRange(location: 5, length: 0))
        controller.handleTextDidChange(in: textView)
        XCTAssertNotNil(textView.markedTextRange, "UITextView must hold active IME markedTextRange for 'Vieej'")

        await harness.clock.advance(by: .milliseconds(1500))
        await harness.engine.waitForIdleForTesting()

        let saveCallsAfter15s = harness.server.calls(for: .saveAnswers).dropFirst(initialSaveCount)
        XCTAssertGreaterThanOrEqual(
            saveCallsAfter15s.count,
            1,
            "PUT .../answers must fire within 1s after the 15s maxWait milestone"
        )
        let firstPayload = try XCTUnwrap(saveCallsAfter15s.first?.decodedJSON())
        let firstChanges = try XCTUnwrap(firstPayload["changes"] as? [[String: Any]])
        let firstAnswer = try XCTUnwrap(firstChanges.first?["answer"] as? [String: Any])
        let firstHtml = try XCTUnwrap(firstAnswer["html"] as? String)
        XCTAssertTrue(firstHtml.contains("Bài làm tiếng"), "15s flush must carry committed text, got: \(firstHtml)")
        XCTAssertFalse(firstHtml.contains("Vieej"), "15s flush must NEVER include uncommitted IME marked text 'Vieej'")
        XCTAssertFalse(firstHtml.contains("V"), "15s flush must contain zero characters of uncommitted 'Vieej'")

        textView.setMarkedText("Việt", selectedRange: NSRange(location: 4, length: 0))
        textView.unmarkText()
        XCTAssertNil(textView.markedTextRange)

        await harness.clock.advance(by: .seconds(3))
        await harness.engine.waitForIdleForTesting()

        let allSaves = harness.server.calls(for: .saveAnswers)
        let lastPayload = try XCTUnwrap(allSaves.last?.decodedJSON())
        let lastChanges = try XCTUnwrap(lastPayload["changes"] as? [[String: Any]])
        let lastAnswer = try XCTUnwrap(lastChanges.first?["answer"] as? [String: Any])
        let lastHtml = try XCTUnwrap(lastAnswer["html"] as? String)
        XCTAssertTrue(lastHtml.contains("Bài làm tiếng Việt"), "Committed IME text must be sent on next save, got: \(lastHtml)")
    }
}
