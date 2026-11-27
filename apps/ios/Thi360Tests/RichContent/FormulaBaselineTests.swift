import SwaTex
import SwaTexRender
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class FormulaBaselineTests: XCTestCase {
    func testInlineFractionBaselineAlignmentAndLineClearance() throws {
        let html = #"<p>Dòng phía trên để đo khoảng cách<br>Biết \(x + \frac{a}{b}\) thì<br>Dòng phía dưới để đo khoảng cách</p>"#
        let doc = RichHTMLParser.parse(html)

        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 240),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let tv = try XCTUnwrap(RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view).first)
        let lm = try XCTUnwrap(tv.textLayoutManager, "RichTextKitTextView must use TextKit 2 NSTextLayoutManager")

        var foundAttachment: FormulaTextAttachment?
        var attachmentCharIndex: Int = -1
        tv.attributedText.enumerateAttribute(
            .attachment,
            in: NSRange(location: 0, length: tv.attributedText.length)
        ) { value, range, _ in
            if let att = value as? FormulaTextAttachment {
                foundAttachment = att
                attachmentCharIndex = range.location
            }
        }
        let attachment = try XCTUnwrap(foundAttachment, "Expected FormulaTextAttachment for inline fraction")
        XCTAssertGreaterThanOrEqual(attachmentCharIndex, 0)
        XCTAssertEqual(attachment.bounds.origin.y, -attachment.formulaDescent, accuracy: 0.01)

        var lineFragments: [(frame: CGRect, baselineY: CGFloat, text: String)] = []
        lm.enumerateTextLayoutFragments(from: lm.documentRange.location, options: .ensuresLayout) { fragment in
            let fragOrigin = fragment.layoutFragmentFrame.origin
            for line in fragment.textLineFragments {
                let bounds = line.typographicBounds.offsetBy(dx: fragOrigin.x, dy: fragOrigin.y)
                let baselineY = fragOrigin.y + line.typographicBounds.minY + line.glyphOrigin.y
                let str = (line.attributedString.string as NSString).substring(with: line.characterRange)
                lineFragments.append((bounds, baselineY, str))
            }
            return true
        }

        XCTAssertEqual(lineFragments.count, 3, "Expected 3 lines (above, formula line, below)")
        let aboveLine = lineFragments[0]
        let formulaLine = lineFragments[1]
        let belowLine = lineFragments[2]

        XCTAssertTrue(formulaLine.text.contains("Biết"), "Middle line must contain 'Biết'")
        XCTAssertTrue(formulaLine.text.contains("thì"), "Middle line must contain 'thì'")

        let attachmentTopInView = formulaLine.baselineY - attachment.formulaAscent
        let attachmentBottomInView = formulaLine.baselineY + attachment.formulaDescent

        XCTAssertGreaterThanOrEqual(
            attachmentTopInView,
            aboveLine.frame.maxY - 0.5,
            "Numerator 'a' must not touch the line above (attTop=\(attachmentTopInView), aboveMaxY=\(aboveLine.frame.maxY))"
        )
        XCTAssertLessThanOrEqual(
            attachmentBottomInView,
            belowLine.frame.minY + 0.5,
            "Denominator 'b' must not touch the line below (attBottom=\(attachmentBottomInView), belowMinY=\(belowLine.frame.minY))"
        )

        let font = try XCTUnwrap(tv.attributedText.attribute(.font, at: 0, effectiveRange: nil) as? UIFont)
        let displayList = try SwaTexEngine.displayList(for: #"x + \frac{a}{b}"#, style: .text, color: .black)
        let options = RenderOptions(fontSize: font.pointSize, padding: 0, backgroundColor: nil)
        let metrics = DisplayListRenderer.metrics(for: displayList, options: options)

        let xGlyph = try XCTUnwrap(displayList.items.first { item in
            if case let .glyphPath(_, _, _, _, charCode, _) = item {
                return charCode == UInt32(UnicodeScalar("x").value)
            }
            return false
        })
        let fracRule = try XCTUnwrap(displayList.items.first { item in
            if case .line = item { return true }
            return false
        })

        if case let .glyphPath(_, xBaselineEm, _, _, _, _) = xGlyph,
           case let .line(_, ruleYEm, _, _, _, _) = fracRule
        {
            let xBaselinePtFromTop = CGFloat(xBaselineEm) * font.pointSize
            let xBaselineDistanceFromBottom = metrics.height - xBaselinePtFromTop
            let xBaselineInView = attachmentBottomInView - xBaselineDistanceFromBottom
            let baselineDeltaPx = abs(xBaselineInView - formulaLine.baselineY) * window.screen.scale

            XCTAssertLessThanOrEqual(
                baselineDeltaPx,
                1.0,
                "Baseline of 'x' in formula must match baseline of 'Biết'/'thì' within 1px (delta=\(baselineDeltaPx)px)"
            )

            let ruleYFromTop = CGFloat(ruleYEm) * font.pointSize
            let xTopFromTop = xBaselinePtFromTop - font.xHeight
            XCTAssertGreaterThanOrEqual(ruleYFromTop, xTopFromTop - 0.5, "Fraction bar must lie at or below top of 'x'")
            XCTAssertLessThanOrEqual(ruleYFromTop, xBaselinePtFromTop + 0.5, "Fraction bar must lie at or above baseline of 'x'")
        } else {
            XCTFail("Expected glyphPath for 'x' and line for fraction bar")
        }

        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 240),
            name: "formula-baseline-ac15",
            in: self
        )
    }
}
