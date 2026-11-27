import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichDynamicTypeTests: XCTestCase {
    private struct SampleFixtureFile: Decodable {
        let assetId: String
        let samples: [Sample]
        struct Sample: Decodable { let name: String; let valid: Bool; let allowImages: Bool; let html: String }
    }

    func testAll12ValidSamplesAtAX3And200PercentScaleRatioAndOverflowInlineFormula() throws {
        let data = try Data(contentsOf: FormulaCorpusLoader.richTextSamplesURL)
        let file = try JSONDecoder().decode(SampleFixtureFile.self, from: data)
        let validSamples = file.samples.filter(\.valid)
        XCTAssertEqual(validSamples.count, 12)

        for sample in validSamples {
            let doc = RichHTMLParser.parse(sample.html, options: RichParseOptions(allowImages: sample.allowImages))
            let view = ScrollView {
                RichContentView(document: doc, fixedWidth: 358)
                    .padding(16)
            }
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
            .environment(\.dynamicTypeSize, .accessibility5)

            let (window, controller) = hostWithCategory(
                view,
                size: CGSize(width: 390, height: 844),
                category: .accessibilityExtraExtraExtraLarge
            )
            defer { window.isHidden = true }

            let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
            for tv in textViews {
                XCTAssertLessThanOrEqual(
                    tv.bounds.width,
                    390.5,
                    "AX3 sample '\(sample.name)' text view exceeded 390pt column width (\(tv.bounds.width))"
                )
            }

            if sample.name == "math-inline-and-block" || sample.name == "data-table" {
                _ = try RecoveryViewTestHelper.captureSnapshot(
                    window: window,
                    size: CGSize(width: 390, height: 844),
                    name: "richcontent-ax3-\(sample.name)",
                    in: self
                )
            }
        }

        let fracHTML = #"<p>Biết \(\frac{a}{b}\) thì</p>"#
        let fracDoc = RichHTMLParser.parse(fracHTML)
        let normalTraits = UITraitCollection(preferredContentSizeCategory: .large)
        let ax3Traits = UITraitCollection(preferredContentSizeCategory: .accessibilityExtraExtraExtraLarge)

        let normalFont = RichDynamicTypeFont.scaledFont(baseSize: 17, traitCollection: normalTraits)
        let ax3Font = RichDynamicTypeFont.scaledFont(baseSize: 17, traitCollection: ax3Traits)
        let textHeightRatio = ax3Font.capHeight / normalFont.capHeight
        XCTAssertGreaterThanOrEqual(textHeightRatio, 1.8, "Height of 'B' must scale >= 1.8x at 200% (got \(textHeightRatio))")
        XCTAssertLessThanOrEqual(textHeightRatio, 2.2, "Height of 'B' must scale <= 2.2x at 200% (got \(textHeightRatio))")

        let engine = SwaTexFormulaEngine(timeoutSeconds: 1.5, displayScale: 2.0, useCache: false)
        let formula = Formula(source: #"\frac{a}{b}"#, display: .inline)
        guard case let .rendered(_, _, normalAscent, normalDescent) = engine.render(formula, pointSize: normalFont.pointSize, color: .black),
              case let .rendered(_, _, ax3Ascent, ax3Descent) = engine.render(formula, pointSize: ax3Font.pointSize, color: .black)
        else {
            XCTFail("Expected rendered fraction formula at both 100% and 200%")
            return
        }
        let formulaHeightRatio = (ax3Ascent + ax3Descent) / (normalAscent + normalDescent)
        XCTAssertGreaterThanOrEqual(formulaHeightRatio, 1.8, "Formula height ('a' in \\frac{a}{b}) must scale >= 1.8x (got \(formulaHeightRatio))")
        XCTAssertLessThanOrEqual(formulaHeightRatio, 2.2, "Formula height ('a' in \\frac{a}{b}) must scale <= 2.2x (got \(formulaHeightRatio))")

        let wideTerms = (1...40).map { "a_{\($0)}" }.joined(separator: " + ")
        let wideHTML = #"<p>Dòng trên</p><p>Tính \(\#(wideTerms) = S\) rồi chọn</p><p>Dòng dưới</p>"#
        let wideDoc = RichHTMLParser.parse(wideHTML)
        let wideView = ScrollView {
            RichContentView(document: wideDoc, fixedWidth: 358)
                .padding(16)
        }
        .background(BrandPalette.defaultLight.bgSurfaceColor)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        .environment(\.dynamicTypeSize, .accessibility5)

        let (wideWindow, wideController) = hostWithCategory(
            wideView,
            size: CGSize(width: 390, height: 844),
            category: .accessibilityExtraExtraExtraLarge
        )
        defer { wideWindow.isHidden = true }

        let formulaScrolls = RecoveryViewTestHelper.findViews(ofType: HorizontalFormulaScrollView.self, in: wideController.view)
        let overflowScroll = try XCTUnwrap(formulaScrolls.first, "Wide inline formula at AX3 must sit in its own HorizontalFormulaScrollView")
        XCTAssertTrue(overflowScroll.isScrollEnabled)
        XCTAssertGreaterThan(overflowScroll.contentSize.width, overflowScroll.bounds.width + 20)
        XCTAssertLessThanOrEqual(overflowScroll.bounds.width, 358.5)

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: wideController.view)
        let aboveTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Dòng trên") })
        let belowTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Dòng dưới") })
        let aboveFrame = aboveTV.convert(aboveTV.bounds, to: wideWindow)
        let scrollFrame = overflowScroll.convert(overflowScroll.bounds, to: wideWindow)
        let belowFrame = belowTV.convert(belowTV.bounds, to: wideWindow)

        XCTAssertLessThanOrEqual(aboveFrame.maxY, scrollFrame.minY + 1, "Overflow formula must not overlap line above")
        XCTAssertLessThanOrEqual(scrollFrame.maxY, belowFrame.minY + 1, "Overflow formula must not overlap line below")

        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: wideWindow,
            size: CGSize(width: 390, height: 844),
            name: "richcontent-ax3-wide-inline-formula",
            in: self
        )

        _ = fracDoc
    }

    func testRescaleKeepsSlotsAndScroll() throws {
        final class SlotState: ObservableObject {
            @Published var answer: String = "giá trị khe 42"
        }

        struct RescaleHostView: View {
            @ObservedObject var state: SlotState
            let document: RichDocument

            var body: some View {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(1...8, id: \.self) { i in
                            Text("Đoạn mở đầu số \(i)")
                                .frame(height: 36)
                        }
                        RichContentView(
                            document: document,
                            slots: [
                                "s1": {
                                    AnyView(
                                        TextField("Ô 1", text: Binding(
                                            get: { state.answer },
                                            set: { state.answer = $0 }
                                        ))
                                        .frame(width: 110, height: 32)
                                        .accessibilityIdentifier("richContent.slot.s1")
                                    )
                                }
                            ],
                            fixedWidth: 358
                        )
                        ForEach(1...8, id: \.self) { i in
                            Text("Đoạn kết thúc số \(i)")
                                .frame(height: 36)
                        }
                    }
                    .padding(16)
                }
            }
        }

        let slotState = SlotState()
        let doc = RichHTMLParser.parse(
            "<p>Điền đáp án vào [[s1]] rồi tiếp tục.</p>",
            options: RichParseOptions(slotMarkers: true)
        )
        let host = RescaleHostView(state: slotState, document: doc)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = hostWithCategory(
            host,
            size: CGSize(width: 390, height: 500),
            category: .large
        )
        defer { window.isHidden = true }

        let scrollView = try XCTUnwrap(
            RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
                .first { !($0 is UITextView) }
        )
        scrollView.setContentOffset(CGPoint(x: 0, y: 120), animated: false)
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        let offsetBefore = scrollView.contentOffset.y
        XCTAssertEqual(offsetBefore, 120, accuracy: 2.0)

        let tvBefore = try XCTUnwrap(RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view).first)
        let mountedSlotField = try XCTUnwrap(
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: tvBefore).first,
            "SlotTextAttachmentViewProvider must mount the inline slot UITextField inside RichTextKitTextView"
        )
        XCTAssertNotNil(mountedSlotField.window, "Mounted inline slot UITextField must be attached to a window")
        XCTAssertGreaterThan(mountedSlotField.bounds.width, 40, "Mounted inline slot UITextField must have non-zero width")
        let fontBefore = try XCTUnwrap(tvBefore.attributedText.attribute(.font, at: 0, effectiveRange: nil) as? UIFont)
        let rebuildCountBeforeTyping = tvBefore.attributedTextRebuildCount
        let attributedBeforeTyping = tvBefore.attributedText

        slotState.answer = "giá trị khe 42 đã sửa"
        RunLoop.main.run(until: Date().addingTimeInterval(0.06))
        controller.view.layoutIfNeeded()

        XCTAssertEqual(
            tvBefore.attributedTextRebuildCount,
            rebuildCountBeforeTyping,
            "Parent state / slot value updates must NOT rebuild attributedText or tear down active slot TextField focus"
        )
        XCTAssertEqual(
            tvBefore.attributedText?.string,
            attributedBeforeTyping?.string,
            "UITextView.attributedText content must remain intact when only bound slot state changes"
        )

        controller.traitOverrides.preferredContentSizeCategory = .accessibilityExtraExtraExtraLarge
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))

        let tvScaled = try XCTUnwrap(RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view).first)
        let fontScaled = try XCTUnwrap(tvScaled.attributedText.attribute(.font, at: 0, effectiveRange: nil) as? UIFont)
        XCTAssertGreaterThan(tvScaled.attributedTextRebuildCount, rebuildCountBeforeTyping, "Dynamic Type change must invalidate signature and redraw text")
        XCTAssertGreaterThan(fontScaled.pointSize, fontBefore.pointSize * 1.5, "Text must redraw at larger Dynamic Type size")
        XCTAssertEqual(slotState.answer, "giá trị khe 42 đã sửa", "Slot value must stay unchanged after scaling up")

        controller.traitOverrides.preferredContentSizeCategory = .large
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))

        XCTAssertEqual(slotState.answer, "giá trị khe 42 đã sửa", "Slot value must stay unchanged after returning to default size")
        XCTAssertEqual(scrollView.contentOffset.y, offsetBefore, accuracy: 4.0, "Scroll position must remain preserved after Dynamic Type rescale")
    }

    private func hostWithCategory<Content: View>(
        _ view: Content,
        size: CGSize,
        category: UIContentSizeCategory
    ) -> (UIWindow, UIHostingController<Content>) {
        let controller = UIHostingController(rootView: view)
        controller.traitOverrides.horizontalSizeClass = .compact
        controller.traitOverrides.userInterfaceStyle = .light
        controller.traitOverrides.preferredContentSizeCategory = category

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        window.rootViewController = controller
        window.makeKeyAndVisible()
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.06))
        window.layoutIfNeeded()
        return (window, controller)
    }
}
