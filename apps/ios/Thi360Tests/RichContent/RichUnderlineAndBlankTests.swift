import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichUnderlineAndBlankTests: XCTestCase {
    func testUnderlineLabelsRenderBelowTargetWithoutOverlappingNextLine() throws {
        let html = #"<p>For millennia, the coconuts <u data-label="A">has been</u> central <u data-label="B">to</u> the lives of Polynesian peoples. <u>Plain underline</u> stays plain.</p>"#
        let doc = RichHTMLParser.parse(html)

        if case let .paragraph(inlines) = doc.blocks.first {
            let labeled = inlines.compactMap { inline -> (String, String)? in
                if case let .labeledUnderline(label, children) = inline {
                    let txt = children.compactMap { if case let .text(s, _) = $0 { return s }; return nil }.joined()
                    return (label, txt)
                }
                return nil
            }
            XCTAssertEqual(labeled.count, 2)
            XCTAssertEqual(labeled[0].0, "A")
            XCTAssertEqual(labeled[0].1, "has been")
            XCTAssertEqual(labeled[1].0, "B")
            XCTAssertEqual(labeled[1].1, "to")
        } else {
            XCTFail("Expected paragraph block")
        }

        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 260),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let tv = try XCTUnwrap(RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view).first)
        var attachmentLabels: [String] = []
        var attachmentBounds: [CGRect] = []
        tv.attributedText.enumerateAttribute(
            .attachment,
            in: NSRange(location: 0, length: tv.attributedText.length)
        ) { value, _, _ in
            if let att = value as? NSTextAttachment {
                attachmentLabels.append(att.accessibilityLabel ?? "")
                attachmentBounds.append(att.bounds)
            }
        }
        XCTAssertEqual(attachmentLabels, ["has been (A)", "to (B)"])
        for bounds in attachmentBounds {
            XCTAssertLessThan(bounds.origin.y, -10, "Labeled underline attachment must reserve vertical descent space below baseline so label never overlaps next line")
        }

        if let lm = tv.textLayoutManager {
            var lineFrames: [CGRect] = []
            lm.enumerateTextLayoutFragments(from: lm.documentRange.location, options: .ensuresLayout) { fragment in
                for line in fragment.textLineFragments {
                    let f = line.typographicBounds.offsetBy(dx: fragment.layoutFragmentFrame.minX, dy: fragment.layoutFragmentFrame.minY)
                    lineFrames.append(f)
                }
                return true
            }
            for i in 1..<lineFrames.count {
                XCTAssertGreaterThanOrEqual(
                    lineFrames[i].minY,
                    lineFrames[i - 1].maxY - 1.0,
                    "Line \(i) must not overlap labeled underline on line \(i - 1)"
                )
            }
        }

        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 260),
            name: "richcontent-underline-labels",
            in: self
        )
    }

    func testBlankNumberStartAndBoundaries1And20StayUnbrokenInline() throws {
        let html = #"<p>Start <span class="blank" data-blank="1"></span> middle <span class="blank" data-blank="2"></span> end <span class="blank" data-blank="20"></span></p>"#
        let doc = RichHTMLParser.parse(html)

        let view = RichContentView(document: doc, blankNumberStart: 31, fixedWidth: 358)
            .padding(16)
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 220),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let tv = try XCTUnwrap(RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view).first)
        var chipAttachments: [NSTextAttachment] = []
        tv.attributedText.enumerateAttribute(
            .attachment,
            in: NSRange(location: 0, length: tv.attributedText.length)
        ) { value, range, _ in
            if let att = value as? NSTextAttachment {
                XCTAssertEqual(range.length, 1, "Each blank chip is a single atomic attachment that cannot split mid-line")
                chipAttachments.append(att)
            }
        }
        XCTAssertEqual(
            chipAttachments.map(\.accessibilityLabel),
            ["(31) ____", "(32) ____", "(50) ____"]
        )
    }
}
