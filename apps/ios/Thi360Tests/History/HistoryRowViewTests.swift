import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class HistoryRowViewTests: XCTestCase {
    func testRenderedRowDisplaysBadgesChoicesNotesFaultsAndTitleAction() async throws {
        let subjects: [String: CatalogOptionItem] = [
            "LY": CatalogOptionItem(code: "LY", nameVi: "Vật lý", nameEn: "Physics"),
            "HOA": CatalogOptionItem(code: "HOA", nameVi: "Hóa học", nameEn: "Chemistry")
        ]

        try await MyExamsTestStackFactory.withDefaultTimeZone("UTC") {
            let scoredJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = "att-view-1"
                item["examName"] = "Đề HSA VACT mẫu"
                item["examKindCode"] = "VACT"
                item["scope"] = "PART"
                item["partCode"] = "P1"
                item["subjectCode"] = NSNull()
                item["choices"] = [
                    "P3": [
                        "optionCode": "KH",
                        "subjectCodes": ["LY", "HOA"]
                    ]
                ]
                item["attemptNo"] = 2
                item["startedAt"] = "2026-11-20T02:05:00Z"
                item["state"] = "PENDING_REVIEW"
                item["regrading"] = true
                item["rawScore"] = 8.333333
                item["displayScore"] = 8.333333
                item["maxScore"] = 10.0
                item["scaleMax"] = 10.0
                item["action"] = "VIEW_RESULT"
                item["compensated"] = true
                item["faultFlag"] = "SYSTEM_FAULT"
            }

            let inProgressJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "IN_PROGRESS") { item in
                item["attemptId"] = "att-view-2"
                item["examName"] = "Đề đang làm dở"
                item["state"] = "IN_PROGRESS"
                item["action"] = "CONTINUE"
                item["choices"] = [:]
            }

            let page = try MyExamsTestFixtures.decodeHistoryPage(
                from: try MyExamsTestFixtures.makeHistoryPagePayload(items: [scoredJSON, inProgressJSON])
            )

            var frames1: [String: CGRect] = [:]
            let row1 = HistoryRowCardView(
                item: page.items[0],
                subjectMap: subjects,
                locale: .vi,
                onContinue: { _ in },
                onViewResult: { _ in }
            )
            let (win1, _) = CatalogViewInspector.host(
                row1
                    .environment(\.brandPalette, .defaultLight)
                    .environment(\.statusPalette, .light)
                    .environment(\.myExamsLayoutFrameObserver, { id, frame in frames1[id] = frame }),
                size: CGSize(width: 390, height: 420)
            )
            defer { win1.isHidden = true }

            let text1 = row1.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(text1.contains("Đề HSA VACT mẫu"))
            XCTAssertTrue(text1.contains("Bài thi theo phần · Phần P1"))
            XCTAssertTrue(text1.contains("Đang chấm lại"))
            XCTAssertTrue(text1.contains("Điểm trước khi chấm lại"))
            XCTAssertTrue(text1.contains("Điểm luyện tập"))
            XCTAssertTrue(text1.contains("8,33/10"))
            XCTAssertTrue(text1.contains("Lần 2 · 09:05, 20/11/2026"))
            XCTAssertTrue(text1.contains("Khoa học: Vật lý, Hóa học"))
            XCTAssertTrue(text1.contains(MyExamsStrings.text("history.fault.compensated", locale: .vi)))
            XCTAssertFalse(text1.contains(MyExamsStrings.text("history.fault.systemFault", locale: .vi)))
            XCTAssertTrue(row1.hasTitleButton)
            XCTAssertNotNil(frames1["history.row.titleButton.att-view-1"])
            XCTAssertNotNil(frames1["history.row.viewResultButton.att-view-1"])

            var frames2: [String: CGRect] = [:]
            let row2 = HistoryRowCardView(
                item: page.items[1],
                subjectMap: subjects,
                locale: .vi,
                onContinue: { _ in },
                onViewResult: { _ in }
            )
            let (win2, _) = CatalogViewInspector.host(
                row2
                    .environment(\.brandPalette, .defaultLight)
                    .environment(\.statusPalette, .light)
                    .environment(\.myExamsLayoutFrameObserver, { id, frame in frames2[id] = frame }),
                size: CGSize(width: 390, height: 320)
            )
            defer { win2.isHidden = true }

            let text2 = row2.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(text2.contains("Đề đang làm dở"))
            XCTAssertTrue(text2.contains("Chưa có điểm"))
            XCTAssertTrue(text2.contains("—"))
            XCTAssertTrue(text2.contains("Tiếp tục"))
            XCTAssertFalse(row2.hasTitleButton)
            XCTAssertNil(frames2["history.row.titleButton.att-view-2"])
            XCTAssertNotNil(frames2["history.row.continueButton.att-view-2"])
        }
    }
}
