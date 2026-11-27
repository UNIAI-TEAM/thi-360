import SwiftSoup
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class EssayEditorTests: XCTestCase {
    func testWriteParagraphThenOrderedListEmitsExactHTMLAndWordCount() throws {
        let controller = EssayEditorController()
        let item = QuestionItem.essay(
            id: "essay-ac14",
            displayNo: 27,
            points: 10,
            stem: "<p>Viết bài nghị luận.</p>",
            wordLimit: nil
        )
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: item,
                answer: nil,
                mode: .answering,
                essayController: controller,
                onAnswer: { _, val in emitted.append(val) },
                locale: .vi,
                fixedWidth: 358
            )
            .padding(16),
            size: CGSize(width: 390, height: 640)
        )
        defer { window.isHidden = true }

        let editorLabel = QuestionViewTestHelper.accessibilityElement(
            withIdentifier: "question.essay-ac14.essayEditorLabel",
            in: window
        )
        XCTAssertEqual(editorLabel?.accessibilityLabel, "Bài viết của bạn")
        XCTAssertNil(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.essay-ac14.manualTag", in: window),
            "Chấm tay chip must not appear outside review mode"
        )

        let textView = try XCTUnwrap(controller.textView, "Expected TextKit 2 EssayUITextView")
        XCTAssertNotNil(textView.textLayoutManager, "Essay editor must use TextKit 2 NSTextLayoutManager")

        textView.insertText("Mở bài")
        textView.insertText("\n")
        controller.toggleOrderedList()
        XCTAssertTrue(controller.isOrderedListActive)
        textView.insertText("Ý một")
        QuestionViewTestHelper.pumpRunLoop()

        let last = try XCTUnwrap(emitted.last)
        XCTAssertEqual(last.essayHtml, "<p>Mở bài</p><ol><li><p>Ý một</p></li></ol>")
        XCTAssertEqual(last.essayWordCount, 4)

        textView.becomeFirstResponder()
        textView.selectedRange = NSRange(location: 0, length: textView.attributedText.length)
        textView.deleteBackward()
        QuestionViewTestHelper.pumpRunLoop()

        let cleared = try XCTUnwrap(emitted.last)
        let clearedJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(cleared)) as? [String: Any])
        XCTAssertEqual(clearedJSON["type"] as? String, "ESSAY")
        XCTAssertEqual(clearedJSON["html"] as? String, "")
        XCTAssertEqual(clearedJSON["wordCount"] as? Int, 0)
    }

    func testToolbarHasSixButtonsAndLoadingPlaceholderRendersVietnameseText() throws {
        let item = QuestionItem.essay(id: "essay-load", displayNo: 1, points: 5, stem: nil, wordLimit: nil)

        let (loadingWindow, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: item,
                answer: nil,
                mode: .answering,
                isLoadingEssayEditor: true,
                onAnswer: { _, _ in },
                locale: .vi
            )
            .padding(16),
            size: CGSize(width: 390, height: 340)
        )
        defer { loadingWindow.isHidden = true }

        let loadingStrings = QuestionViewTestHelper.allAccessibilityStrings(in: loadingWindow).joined(separator: "\n")
        XCTAssertTrue(loadingStrings.contains("Đang tải trình soạn bài viết…"))

        let (readyWindow, _) = QuestionViewTestHelper.host(
            QuestionView(item: item, answer: nil, mode: .answering, onAnswer: { _, _ in }, locale: .vi)
                .padding(16),
            size: CGSize(width: 390, height: 420)
        )
        defer { readyWindow.isHidden = true }

        for suffix in ["bold", "italic", "bulletList", "orderedList", "undo", "redo"] {
            let el = QuestionViewTestHelper.accessibilityElement(
                withIdentifier: "question.essay-load.essayToolbar.\(suffix)",
                in: readyWindow
            )
            XCTAssertNotNil(el, "Missing toolbar button \(suffix)")
            if let el {
                XCTAssertGreaterThanOrEqual(el.accessibilityFrame.width, 43.99)
                XCTAssertGreaterThanOrEqual(el.accessibilityFrame.height, 43.99)
            }
        }
    }

    func testPasteSanitizesDirtyHTMLKeepingOnlyAllowedFormatting() throws {
        let controller = EssayEditorController()
        let item = QuestionItem.essay(id: "essay-paste", displayNo: 1, points: 5, stem: nil, wordLimit: nil)
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: item,
                answer: nil,
                mode: .answering,
                essayController: controller,
                onAnswer: { _, val in emitted.append(val) },
                locale: .vi
            )
            .padding(16),
            size: CGSize(width: 390, height: 480)
        )
        defer { window.isHidden = true }

        let dirtyHTML = """
        <p style="color:red">Đoạn <strong style="color:blue">đậm</strong> và <em>nghiêng</em> <a href="https://evil.test">liên kết</a> <u>gạch chân</u> <img src="x.png" alt="bad"><script>alert(1)</script></p>
        <ul><li><p>Mục <strong>một</strong></p></li></ul>
        """
        controller.pasteHTML(dirtyHTML)
        QuestionViewTestHelper.pumpRunLoop()

        let exported = try XCTUnwrap(emitted.last?.essayHtml)
        XCTAssertTrue(exported.contains("<strong>đậm</strong>"))
        XCTAssertTrue(exported.contains("<em>nghiêng</em>"))
        XCTAssertTrue(exported.contains("<ul><li><p>Mục <strong>một</strong></p></li></ul>"))
        XCTAssertFalse(exported.contains("<img"))
        XCTAssertFalse(exported.contains("<a"))
        XCTAssertFalse(exported.contains("href"))
        XCTAssertFalse(exported.contains("color"))
        XCTAssertFalse(exported.contains("<u>"))
        XCTAssertFalse(exported.contains("<u "))
        XCTAssertFalse(exported.contains("<script"))
        XCTAssertFalse(exported.contains("alert(1)"))
    }
}

final class EssayHTMLTests: XCTestCase {
    func testSanitizePastedHTMLStripsDisallowedTagsAndStyles() {
        let dirty = #"<p style="color:#ff0000">Xin <b class="x">chào</b> <i id="y">Việt Nam</i> <a href="https://example.com">link</a> <u>u</u> <img src="a.jpg"></p>"#
        let clean = EssayHTML.sanitizePastedHTML(dirty)
        XCTAssertEqual(clean, "<p>Xin <strong>chào</strong> <em>Việt Nam</em> link u </p>")
    }

    func testEscapesSpecialHTMLCharactersAndNbsp() {
        let attr = EssayHTML.attributedString(
            from: "<p>a &lt; b &amp; c &gt; d&nbsp;e</p>",
            baseFont: UIFont.systemFont(ofSize: 17),
            textColor: .label
        )
        XCTAssertEqual(EssayHTML.export(attr), "<p>a &lt; b &amp; c &gt; d&nbsp;e</p>")
    }
}

final class EssayHTMLRoundTripTests: XCTestCase {
    private struct EssaysFixtureFile: Decodable {
        let samples: [EssaySample]
        struct EssaySample: Decodable {
            let id: String
            let input: String
            let serverHtml: String
            let wordCount: Int
        }
    }

    func testAll10EssaysFixtureSamplesMatchServerHTMLAndWordCountAndRoundTrip() throws {
        let data = try Data(contentsOf: QuestionTestFixtures.essaysFixtureURL)
        let fixture = try JSONDecoder().decode(EssaysFixtureFile.self, from: data)
        XCTAssertEqual(fixture.samples.count, 10, "Expected 10 essay fixture samples")

        let baseFont = UIFont.systemFont(ofSize: 17)
        for sample in fixture.samples {
            let attr1 = EssayHTML.attributedString(from: sample.input, baseFont: baseFont, textColor: .label)
            let exported1 = EssayHTML.export(attr1)
            XCTAssertEqual(
                canonicalDOMSignature(exported1),
                canonicalDOMSignature(sample.serverHtml),
                "DOM mismatch for \(sample.id): got \(exported1) vs \(sample.serverHtml)"
            )
            XCTAssertEqual(exported1, sample.serverHtml, "Exact HTML mismatch for \(sample.id)")
            XCTAssertEqual(WordCounter.count(html: exported1), sample.wordCount, "Word count mismatch for \(sample.id)")

            let attr2 = EssayHTML.attributedString(from: sample.serverHtml, baseFont: baseFont, textColor: .label)
            let exported2 = EssayHTML.export(attr2)
            XCTAssertEqual(exported2, sample.serverHtml, "Round-trip reload mismatch for \(sample.id)")
            XCTAssertEqual(WordCounter.count(html: exported2), sample.wordCount, "Round-trip wordCount mismatch for \(sample.id)")
        }
    }

    private func canonicalDOMSignature(_ html: String) -> String {
        guard !html.isEmpty else { return "" }
        guard let body = try? SwiftSoup.parseBodyFragment(html).body() else { return html }
        return serializeNode(body)
    }

    private func serializeNode(_ node: Node) -> String {
        if let text = node as? TextNode {
            return "#text(\(text.getWholeText()))"
        }
        if let el = node as? Element {
            let children = el.getChildNodes().map(serializeNode).joined()
            return "<\(el.tagName().lowercased())>\(children)</\(el.tagName().lowercased())>"
        }
        return ""
    }
}
