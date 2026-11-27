import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class TrueFalseQuestionTests: XCTestCase {
    private let tfItem = QuestionItem.trueFalse(
        id: "tf-ac10",
        displayNo: 24,
        points: 1,
        stem: "<p>Mỗi phát biểu sau đúng hay sai?</p>",
        statements: [
            TrueFalseStatement(id: "a", content: "<p>Hai đường thẳng song song thì đồng phẳng.</p>", answer: true, points: 0.25),
            TrueFalseStatement(id: "b", content: "<p>Hai đường thẳng chéo nhau có điểm chung.</p>", answer: false, points: 0.25),
            TrueFalseStatement(id: "c", content: "<p>Ba điểm bất kỳ luôn thẳng hàng.</p>", answer: false, points: 0.25),
            TrueFalseStatement(id: "d", content: "<p>Qua hai điểm phân biệt có duy nhất một đường thẳng.</p>", answer: true, points: 0.25)
        ],
        scoringMethod: .perStatement
    )

    func testTrueFalseSelectionAndRetapOmitsClearedStatement() throws {
        var map: [String: Bool] = [:]
        map["a"] = true
        map["c"] = false
        let first = AnswerValues.trueFalse(item: tfItem, statements: map)
        let firstJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(first)) as? [String: Any])
        XCTAssertEqual(firstJSON["type"] as? String, "TRUE_FALSE")
        XCTAssertEqual(firstJSON["statements"] as? [String: Bool], ["a": true, "c": false])

        map.removeValue(forKey: "a")
        let afterClear = AnswerValues.trueFalse(item: tfItem, statements: map)
        let clearedJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(afterClear)) as? [String: Any])
        XCTAssertEqual(clearedJSON["type"] as? String, "TRUE_FALSE")
        let statementsDict = try XCTUnwrap(clearedJSON["statements"] as? [String: Bool])
        XCTAssertNil(statementsDict["a"], "Cleared statement ID must be omitted from statements map")
        XCTAssertEqual(statementsDict, ["c": false])
    }

    func testNarrowScreenAndAX3ButtonsWrapBelowTextWithoutClipping() throws {
        for category in [UIContentSizeCategory.large, .accessibilityExtraExtraExtraLarge] {
            let (window, _) = QuestionViewTestHelper.host(
                ScrollView {
                    QuestionView(
                        item: tfItem,
                        answer: AnswerValues.trueFalse(item: tfItem, statements: ["a": true, "c": false]),
                        mode: .answering,
                        onAnswer: { _ in },
                        locale: .vi,
                        fixedWidth: 320
                    )
                    .padding(12)
                },
                size: CGSize(width: 344, height: 844),
                sizeClass: .compact,
                contentSizeCategory: category
            )
            defer { window.isHidden = true }

            let trueBtn = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.tf-ac10.tf.a.true", in: window)
            )
            let falseBtn = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.tf-ac10.tf.a.false", in: window)
            )

            XCTAssertGreaterThanOrEqual(trueBtn.accessibilityFrame.width, 44)
            XCTAssertGreaterThanOrEqual(trueBtn.accessibilityFrame.height, 44)
            XCTAssertGreaterThanOrEqual(falseBtn.accessibilityFrame.width, 44)
            XCTAssertGreaterThanOrEqual(falseBtn.accessibilityFrame.height, 44)
            XCTAssertLessThanOrEqual(falseBtn.accessibilityFrame.maxX, 344 + 1, "Buttons must stay within 344pt viewport")
            XCTAssertFalse(trueBtn.accessibilityFrame.intersects(falseBtn.accessibilityFrame), "True/False buttons must not overlap")
        }
    }

    func testAnsweredStatementRowDifferentiatesBackgroundFillFromUnansweredRow() throws {
        for mode in ThemeMode.allCases {
            let size = CGSize(width: 390, height: 760)
            let (window, _) = QuestionViewTestHelper.host(
                ScrollView {
                    QuestionView(
                        item: tfItem,
                        answer: AnswerValues.trueFalse(item: tfItem, statements: ["a": true]),
                        mode: .answering,
                        onAnswer: { _ in },
                        locale: .vi,
                        fixedWidth: 358
                    )
                    .padding(16)
                },
                size: size,
                sizeClass: .compact,
                themeMode: mode
            )
            defer { window.isHidden = true }

            let answeredRow = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.tf-ac10.tf.a.row", in: window)
            )
            let unansweredRow = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.tf-ac10.tf.b.row", in: window)
            )
            XCTAssertEqual(answeredRow.accessibilityValue, "answered")
            XCTAssertEqual(unansweredRow.accessibilityValue, "unanswered")

            let (image, _) = try XCTUnwrap(QuestionViewTestHelper.snapshotPNG(window: window, size: size))
            let palette = BrandPaletteSet.defaultThi360Green.palette(for: mode)
            let answeredPoint = CGPoint(x: answeredRow.accessibilityFrame.maxX - 8, y: answeredRow.accessibilityFrame.maxY - 8)
            let unansweredPoint = CGPoint(x: unansweredRow.accessibilityFrame.maxX - 8, y: unansweredRow.accessibilityFrame.maxY - 8)
            let answeredPixel = try XCTUnwrap(pixelRGBA(in: image, at: answeredPoint))
            let unansweredPixel = try XCTUnwrap(pixelRGBA(in: image, at: unansweredPoint))
            let expectedSubtle = UIColor(hex: palette.bgSubtle)
            let expectedSurface = UIColor(hex: palette.bgSurface)

            XCTAssertTrue(
                colorsMatch(answeredPixel, expectedSubtle, tolerance: 0.05),
                "Answered statement row in \(mode.rawValue) must fill with bgSubtle (\(palette.bgSubtle))"
            )
            XCTAssertTrue(
                colorsMatch(unansweredPixel, expectedSurface, tolerance: 0.05),
                "Unanswered statement row in \(mode.rawValue) must fill with bgSurface (\(palette.bgSurface))"
            )
            XCTAssertFalse(
                colorsMatch(answeredPixel, unansweredPixel, tolerance: 0.01),
                "Answered and unanswered statement rows must visually differ in \(mode.rawValue)"
            )
        }
    }

    private func pixelRGBA(in image: UIImage, at point: CGPoint) -> (CGFloat, CGFloat, CGFloat)? {
        guard let cgImage = image.cgImage else { return nil }
        let width = cgImage.width
        let height = cgImage.height
        let px = Int(round(point.x * image.scale))
        let py = Int(round(point.y * image.scale))
        guard px >= 0, py >= 0, px < width, py < height else { return nil }
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return nil
        }
        ctx.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        let offset = (py * width + px) * 4
        return (CGFloat(pixels[offset]) / 255.0, CGFloat(pixels[offset + 1]) / 255.0, CGFloat(pixels[offset + 2]) / 255.0)
    }

    private func colorsMatch(_ lhs: (CGFloat, CGFloat, CGFloat), _ rhs: UIColor, tolerance: CGFloat) -> Bool {
        var r: CGFloat = 0
        var g: CGFloat = 0
        var b: CGFloat = 0
        var a: CGFloat = 0
        guard rhs.getRed(&r, green: &g, blue: &b, alpha: &a) else { return false }
        return abs(lhs.0 - r) <= tolerance && abs(lhs.1 - g) <= tolerance && abs(lhs.2 - b) <= tolerance
    }

    private func colorsMatch(_ lhs: (CGFloat, CGFloat, CGFloat), _ rhs: (CGFloat, CGFloat, CGFloat), tolerance: CGFloat) -> Bool {
        abs(lhs.0 - rhs.0) <= tolerance && abs(lhs.1 - rhs.1) <= tolerance && abs(lhs.2 - rhs.2) <= tolerance
    }
}
