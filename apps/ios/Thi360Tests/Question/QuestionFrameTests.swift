import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class QuestionFrameTests: XCTestCase {
    func testClozeTwoBlanksDisplaysRangeAndCountChip() {
        let item = QuestionItem.cloze(
            id: "q-cloze-2",
            displayNo: 22,
            points: 2,
            stem: "<p>Điền khuyết</p>",
            passage: "<p>Ô [[b1]] và [[b2]]</p>",
            blanks: [ClozeBlank(id: "b1"), ClozeBlank(id: "b2")],
            blankNumberStart: 22
        )
        XCTAssertEqual(QuestionStrings.questionTitle(for: item, locale: .vi), "Câu 22–23")
        XCTAssertEqual(QuestionStrings.typeChipLabel(for: item, locale: .vi), "Điền khuyết · 2")
        XCTAssertEqual(QuestionStrings.questionTitle(for: item, locale: .en), "Question 22–23")
        XCTAssertEqual(QuestionStrings.typeChipLabel(for: item, locale: .en), "Fill in the blanks · 2")
    }

    func testClozeSingleBlankDisplaysSingleNumber() {
        let item = QuestionItem.cloze(
            id: "q-cloze-1",
            displayNo: 7,
            points: 1,
            stem: nil,
            passage: "<p>Chỉ có [[b1]]</p>",
            blanks: [ClozeBlank(id: "b1")],
            blankNumberStart: 7
        )
        XCTAssertEqual(QuestionStrings.questionTitle(for: item, locale: .vi), "Câu 7")
        XCTAssertEqual(QuestionStrings.typeChipLabel(for: item, locale: .vi), "Điền khuyết · 1")
    }

    func testMultipleChoiceTitleAndTypeChip() {
        let item = QuestionItem.multipleChoice(
            id: "q-multi-5",
            displayNo: 5,
            points: 1,
            stem: "<p>Chọn nhiều</p>",
            optionOrder: nil,
            options: [ChoiceOption(id: "a", content: "<p>A</p>")],
            scoringRule: .allOrNothing
        )
        XCTAssertEqual(QuestionStrings.questionTitle(for: item, locale: .vi), "Câu 5")
        XCTAssertEqual(QuestionStrings.typeChipLabel(for: item, locale: .vi), "Chọn một hoặc nhiều đáp án")
    }

    func testFlagButtonPresenceAndTouchTargetAcrossModes() throws {
        let item = QuestionItem.multipleChoice(
            id: "q-flag-test",
            displayNo: 5,
            points: 1,
            stem: "<p>Kiểm thử đánh dấu xem lại.</p>",
            optionOrder: nil,
            options: [ChoiceOption(id: "o1", content: "<p>Phương án A</p>")],
            scoringRule: .allOrNothing
        )

        let (ansWindow, _) = QuestionViewTestHelper.host(
            QuestionFrame(item: item, mode: .answering, flagged: true) { EmptyView() },
            size: CGSize(width: 390, height: 240)
        )
        defer { ansWindow.isHidden = true }

        let flagElement = QuestionViewTestHelper.accessibilityElement(
            withIdentifier: "question.q-flag-test.flagButton",
            in: ansWindow
        )
        XCTAssertNotNil(flagElement, "Flag button must exist in answering mode")
        if let flagElement {
            XCTAssertEqual(flagElement.accessibilityLabel, "Bỏ đánh dấu")
            XCTAssertEqual(flagElement.accessibilityValue, "1")
            let frame = flagElement.accessibilityFrame
            XCTAssertGreaterThanOrEqual(frame.width, 44, "Flag button width must be >= 44pt")
            XCTAssertGreaterThanOrEqual(frame.height, 44, "Flag button height must be >= 44pt")
        }

        let (lockedWindow, _) = QuestionViewTestHelper.host(
            QuestionFrame(item: item, mode: .locked, flagged: true) { EmptyView() },
            size: CGSize(width: 390, height: 240)
        )
        defer { lockedWindow.isHidden = true }
        XCTAssertNil(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.q-flag-test.flagButton", in: lockedWindow),
            "Flag button must be absent in locked mode"
        )

        let (reviewWindow, _) = QuestionViewTestHelper.host(
            QuestionFrame(
                item: item,
                mode: .review(ItemReview(outcome: .correct, earned: 1, max: 1, level: .fullSolution)),
                flagged: true
            ) { EmptyView() },
            size: CGSize(width: 390, height: 240)
        )
        defer { reviewWindow.isHidden = true }
        XCTAssertNil(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.q-flag-test.flagButton", in: reviewWindow),
            "Flag button must be absent in review mode"
        )
    }

    func testFlaggedQuestionFrameSnapshotsLightAndDark() throws {
        let item = QuestionItem.cloze(
            id: "q-flag-snap",
            displayNo: 22,
            points: 2,
            stem: "<p>Câu hỏi điền khuyết đang được đánh dấu xem lại.</p>",
            passage: "<p>[[b1]] và [[b2]]</p>",
            blanks: [ClozeBlank(id: "b1"), ClozeBlank(id: "b2")],
            blankNumberStart: 22
        )

        for mode in ThemeMode.allCases {
            let size = CGSize(width: 390, height: 260)
            let (window, _) = QuestionViewTestHelper.host(
                QuestionFrame(item: item, mode: .answering, flagged: true, fixedWidth: 358) {
                    Text("Nội dung câu hỏi")
                }
                .padding(16),
                size: size,
                themeMode: mode
            )
            defer { window.isHidden = true }
            let (image, data) = try XCTUnwrap(QuestionViewTestHelper.snapshotPNG(window: window, size: size))
            XCTAssertGreaterThan(data.count, 512)

            let flagEl = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.q-flag-snap.flagButton", in: window)
            )
            let expectedWarning = UIColor(hex: StatusPalette.forMode(mode).warning)
            XCTAssertTrue(
                snapshotContainsColor(image: image, inRect: flagEl.accessibilityFrame, expected: expectedWarning, tolerance: 0.08),
                "Flagged icon in \(mode.rawValue) mode must render with StatusPalette.warning (\(StatusPalette.forMode(mode).warning))"
            )

            let attachment = XCTAttachment(image: image)
            attachment.name = "ac1-flagged-frame-\(mode.rawValue).png"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    private func snapshotContainsColor(image: UIImage, inRect rect: CGRect, expected: UIColor, tolerance: CGFloat) -> Bool {
        guard let cgImage = image.cgImage else { return false }
        var er: CGFloat = 0
        var eg: CGFloat = 0
        var eb: CGFloat = 0
        var ea: CGFloat = 0
        guard expected.getRed(&er, green: &eg, blue: &eb, alpha: &ea) else { return false }

        let width = cgImage.width
        let height = cgImage.height
        let scale = image.scale
        let minX = max(0, Int(floor(rect.minX * scale)))
        let minY = max(0, Int(floor(rect.minY * scale)))
        let maxX = min(width - 1, Int(ceil(rect.maxX * scale)))
        let maxY = min(height - 1, Int(ceil(rect.maxY * scale)))
        guard minX <= maxX, minY <= maxY else { return false }

        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return false
        }
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        for y in minY...maxY {
            for x in minX...maxX {
                let offset = (y * width + x) * 4
                let r = CGFloat(pixels[offset]) / 255.0
                let g = CGFloat(pixels[offset + 1]) / 255.0
                let b = CGFloat(pixels[offset + 2]) / 255.0
                if abs(r - er) <= tolerance && abs(g - eg) <= tolerance && abs(b - eb) <= tolerance {
                    return true
                }
            }
        }
        return false
    }
}
