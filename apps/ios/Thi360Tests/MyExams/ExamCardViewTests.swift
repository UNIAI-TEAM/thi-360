import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ExamCardViewTests: XCTestCase {
    func testContent() throws {
        let classCardJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-class-1"
            item["code"] = "MATH-12A1"
            item["name"] = "Đề Toán 12A1"
            item["format"] = "SUBJECT"
            item["scopeLabel"] = ["nameVi": "Toán", "nameEn": ""]
            item["buildMode"] = "STANDARD"
            item["questionCount"] = ["min": 50, "max": 50]
            item["durationMinutes"] = ["min": 90, "max": 90]
            item["attemptsLeft"] = 2
            item["entitlementSource"] = "CLASS"
            item["className"] = "12A1"
        }

        let reviewCardJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-rev-1"
            item["code"] = "REV-01"
            item["name"] = "Bài ôn tập"
            item["format"] = "FULL"
            item["buildMode"] = "REVIEW"
            item["questionCount"] = ["min": 40, "max": 60]
            item["durationMinutes"] = NSNull()
            item["entitlementSource"] = "PURCHASE"
            item["className"] = NSNull()
        }

        let namelessAndUnknownCard = MyExamCard(
            entitlementId: "ent-unk-1",
            code: "UNK-01",
            name: "Đề đặc biệt",
            examKind: MyExamKind(code: "HSA", name: MyExamLocalizedName(nameVi: "HSA", nameEn: "HSA")),
            format: "CUSTOM_FORMAT",
            scopeLabel: MyExamLocalizedName(nameVi: "Vật lý", nameEn: ""),
            buildMode: "STANDARD",
            questionCount: nil,
            durationMinutes: nil,
            attemptsLeft: 1,
            attemptsTotal: 1,
            saleState: "ON_SALE",
            state: "CUSTOM_STATE",
            activeAttemptId: nil,
            activeAttemptState: nil,
            resultAttemptId: nil,
            hasPendingFulfillment: false,
            pendingOrderCode: nil,
            actions: ["START"],
            bundles: [],
            entitlementSource: "CLASS",
            classId: "cls-1",
            className: "   "
        )

        let decodedCards = try MyExamsTestFixtures.decodeMyExamCards(
            from: try MyExamsTestFixtures.makeMyExamsPayload(items: [classCardJSON, reviewCardJSON])
        )
        let classCard = decodedCards[0]
        let reviewCard = decodedCards[1]

        let view1 = MyExamCardView(card: classCard, locale: .vi, onAction: { _ in })
        let (win1, ctrl1) = CatalogViewInspector.host(
            view1
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 390, height: 520)
        )
        defer { win1.isHidden = true }
        let text1 = view1.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text1.contains("Bài theo lớp · 12A1"))
        XCTAssertTrue(text1.contains("Có thể bắt đầu"))
        XCTAssertTrue(text1.contains("Bài thi theo môn"))
        XCTAssertTrue(text1.contains("Toán"))
        XCTAssertTrue(text1.contains("50 câu · 90 phút mỗi lượt"))
        XCTAssertTrue(text1.contains("Lượt còn lại"))
        XCTAssertTrue(text1.contains("2"))
        let largeImages = RecoveryViewTestHelper.findViews(ofType: UIImageView.self, in: ctrl1.view)
            .filter { $0.bounds.width >= 60 || $0.bounds.height >= 60 }
        XCTAssertTrue(largeImages.isEmpty, "MyExamCardView must not render a cover image")
        let (_, png1) = try CatalogViewInspector.renderPNG(window: win1, size: CGSize(width: 390, height: 520))
        XCTAssertGreaterThan(png1.count, 2048)

        let view2 = MyExamCardView(card: reviewCard, locale: .vi, onAction: { _ in })
        let (win2, _) = CatalogViewInspector.host(
            view2
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 390, height: 520)
        )
        defer { win2.isHidden = true }
        let text2 = view2.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text2.contains("Đã mua"))
        XCTAssertTrue(text2.contains("Bài ôn"))
        XCTAssertTrue(text2.contains("40–60 câu"))
        XCTAssertFalse(text2.contains("phút mỗi lượt"))

        let view3 = MyExamCardView(card: namelessAndUnknownCard, locale: .vi, onAction: { _ in })
        let text3 = view3.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text3.contains("Bài theo lớp"))
        XCTAssertFalse(text3.contains("Bài theo lớp ·"))
        XCTAssertTrue(text3.contains("CUSTOM_STATE"))
        XCTAssertTrue(text3.contains("CUSTOM_FORMAT"))

        let view4 = MyExamCardView(card: namelessAndUnknownCard, locale: .en, onAction: { _ in })
        let text4 = view4.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text4.contains("Vật lý"), "English card with empty scopeLabel.nameEn must fall back to nameVi")
    }

    func testNotesPlacement() throws {
        let cardJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "IN_PROGRESS") { item in
            item["entitlementId"] = "ent-note-1"
            item["code"] = "NOTE-01"
            item["name"] = "Đề ghi chú"
            item["attemptsLeft"] = 0
            item["saleState"] = "ON_SALE"
            item["activeAttemptId"] = "att-active-1"
            item["resultAttemptId"] = "att-res-1"
            item["hasPendingFulfillment"] = true
            item["pendingOrderCode"] = "DH002"
            item["actions"] = ["CONTINUE", "VIEW_RESULT", "HISTORY", "BUY_MORE"]
            item["entitlementSource"] = "PURCHASE"
        }
        let card = try XCTUnwrap(
            try MyExamsTestFixtures.decodeMyExamCards(
                from: try MyExamsTestFixtures.makeMyExamsPayload(items: [cardJSON])
            ).first
        )

        var frames: [String: CGRect] = [:]
        let cardView = MyExamCardView(card: card, locale: .vi, onAction: { _ in })
        let (window, _) = CatalogViewInspector.host(
            cardView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
                .environment(\.myExamsLayoutFrameObserver, { id, frame in frames[id] = frame }),
            size: CGSize(width: 390, height: 680)
        )
        defer { window.isHidden = true }

        let continueRect = try XCTUnwrap(frames["myExams.card.action.continue.NOTE-01"])
        let lastAttemptRect = try XCTUnwrap(frames["myExams.card.note.home.notes.lastAttempt.NOTE-01"])
        let historyRect = try XCTUnwrap(frames["myExams.card.action.history.NOTE-01"])
        let pendingRect = try XCTUnwrap(frames["myExams.card.note.pendingTopUp.NOTE-01"])

        XCTAssertNil(frames["myExams.card.action.buyMore.NOTE-01"], "Pending fulfillment must hide buyMore button")
        XCTAssertLessThan(continueRect.maxY, lastAttemptRect.minY, "Last-attempt note must sit below primary Continue button")
        XCTAssertLessThan(lastAttemptRect.maxY, historyRect.minY, "Last-attempt note must sit above secondary row")
        XCTAssertLessThan(historyRect.maxY, pendingRect.minY, "Pending top-up warning note must sit below secondary row")
        XCTAssertTrue(cardView.renderedStrings.joined(separator: "\n").contains("DH002"))
    }
}
