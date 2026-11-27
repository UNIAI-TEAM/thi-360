import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichContentViewTests: XCTestCase {
    func testTapHasNoAction() {
        let html = #"<p>a <font>b</font> c</p><script>x()</script><p style="color:red" onclick="x()">d</p>"#
        let doc = RichHTMLParser.parse(html)
        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 300),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        XCTAssertEqual(textViews.count, 2)
        XCTAssertEqual(textViews[0].attributedText.string, "a b c")
        XCTAssertEqual(textViews[1].attributedText.string, "d")

        for tv in textViews {
            XCTAssertFalse(tv.isEditable)
            XCTAssertFalse(tv.isSelectable)
            XCTAssertEqual(tv.dataDetectorTypes, [])
            XCTAssertFalse(tv.hasInteractiveSlots)
            XCTAssertNil(tv.hitTest(CGPoint(x: 4, y: 4), with: nil), "Tapping plain rich text must have no interactive target")

            let fullRange = NSRange(location: 0, length: tv.attributedText.length)
            tv.attributedText.enumerateAttributes(in: fullRange) { attrs, _, _ in
                XCTAssertNil(attrs[.link], "No link attributes may exist in rendered rich text")
                if let fg = attrs[.foregroundColor] as? UIColor {
                    var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                    fg.getRed(&r, green: &g, blue: &b, alpha: &a)
                    XCTAssertLessThan(r, 0.5, "Inline style='color:red' must be dropped; text must use normal theme color")
                }
            }
        }
    }

    func testInlineStylesAndOrderedList() throws {
        let html = #"<p>H<sub>2</sub>O và x<sup>2</sup>, <strong>đậm</strong> <em>nghiêng</em> <u>gạch chân</u> <s>gạch ngang</s></p><ol><li>một</li><li>hai</li></ol>"#
        let doc = RichHTMLParser.parse(html)
        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 320),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        XCTAssertGreaterThanOrEqual(textViews.count, 3)

        let paraAttr = try XCTUnwrap(textViews[0].attributedText)
        let paraStr = paraAttr.string as NSString
        XCTAssertTrue(paraStr.contains("H2O"))
        XCTAssertTrue(paraStr.contains("đậm"))
        XCTAssertTrue(paraStr.contains("nghiêng"))
        XCTAssertTrue(paraStr.contains("gạch chân"))
        XCTAssertTrue(paraStr.contains("gạch ngang"))

        let subRange = paraStr.range(of: "2")
        let subOffset = paraAttr.attribute(.baselineOffset, at: subRange.location, effectiveRange: nil) as? CGFloat
        XCTAssertLessThan(subOffset ?? 0, 0, "<sub> must shift baseline downward")

        let supSearchStart = subRange.location + subRange.length
        let supRange = paraStr.range(of: "2", range: NSRange(location: supSearchStart, length: paraStr.length - supSearchStart))
        let supOffset = paraAttr.attribute(.baselineOffset, at: supRange.location, effectiveRange: nil) as? CGFloat
        XCTAssertGreaterThan(supOffset ?? 0, 0, "<sup> must shift baseline upward")

        let boldRange = paraStr.range(of: "đậm")
        let boldFont = try XCTUnwrap(paraAttr.attribute(.font, at: boldRange.location, effectiveRange: nil) as? UIFont)
        XCTAssertTrue(boldFont.fontDescriptor.symbolicTraits.contains(.traitBold), "<strong> must be bold")

        let italicRange = paraStr.range(of: "nghiêng")
        let italicFont = try XCTUnwrap(paraAttr.attribute(.font, at: italicRange.location, effectiveRange: nil) as? UIFont)
        XCTAssertTrue(italicFont.fontDescriptor.symbolicTraits.contains(.traitItalic), "<em> must be italic")

        let uRange = paraStr.range(of: "gạch chân")
        let underline = paraAttr.attribute(.underlineStyle, at: uRange.location, effectiveRange: nil) as? Int
        XCTAssertEqual(underline, NSUnderlineStyle.single.rawValue, "<u> must have underlineStyle")

        let sRange = paraStr.range(of: "gạch ngang")
        let strike = paraAttr.attribute(.strikethroughStyle, at: sRange.location, effectiveRange: nil) as? Int
        XCTAssertEqual(strike, NSUnderlineStyle.single.rawValue, "<s> must have strikethroughStyle")

        let allLabels = RecoveryViewTestHelper.findViews(ofType: UILabel.self, in: controller.view).map { $0.text ?? "" }
        let joinedText = (allLabels + textViews.map(\.customAccessibilityDescription)).joined(separator: " ")
        XCTAssertTrue(joinedText.contains("một"))
        XCTAssertTrue(joinedText.contains("hai"))
        if case let .list(ordered, items) = doc.blocks[1] {
            XCTAssertTrue(ordered)
            XCTAssertEqual(items.count, 2)
        } else {
            XCTFail("Expected ordered list block")
        }

        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 320),
            name: "richcontent-inline-styles-and-list",
            in: self
        )
    }

    func testLongWordWrapsAt390pt() throws {
        let longWord = String(repeating: "a", count: 300)
        let doc = RichHTMLParser.parse("<p>\(longWord)</p>")
        let view = ScrollView {
            RichContentView(document: doc, fixedWidth: 358)
                .padding(16)
        }
        .background(BrandPalette.defaultLight.bgSurfaceColor)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 500),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        let tv = try XCTUnwrap(textViews.first)
        XCTAssertLessThanOrEqual(tv.bounds.width, 390, "300-char unbroken word must stay within 390pt width")
        XCTAssertGreaterThan(tv.bounds.height, 80, "300-char word must wrap across multiple lines")

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        for sv in scrollViews where sv !== tv {
            XCTAssertLessThanOrEqual(
                sv.contentSize.width,
                sv.bounds.width + 1,
                "Enclosing page ScrollView must never scroll horizontally"
            )
        }

        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 500),
            name: "richcontent-long-word-390pt",
            in: self
        )
    }
}
