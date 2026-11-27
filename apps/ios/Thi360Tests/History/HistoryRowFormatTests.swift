import Foundation
import XCTest
@testable import Thi360

final class HistoryRowFormatTests: XCTestCase {
    func testScopeTimeStatusFaultChoicesVactAndScoreMatrix() async throws {
        let subjects: [String: CatalogOptionItem] = [
            "TOAN": CatalogOptionItem(code: "TOAN", nameVi: "Toán", nameEn: "Math"),
            "LY": CatalogOptionItem(code: "LY", nameVi: "Vật lý", nameEn: "Physics"),
            "HOA": CatalogOptionItem(code: "HOA", nameVi: "Hóa học", nameEn: "Chemistry")
        ]

        try await MyExamsTestStackFactory.withDefaultTimeZone("America/Los_Angeles") {
            let partItemJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = "att-part-1"
                item["scope"] = "PART"
                item["partCode"] = "P1"
                item["subjectCode"] = NSNull()
                item["attemptNo"] = 2
                item["startedAt"] = "2026-11-20T02:05:00Z"
                item["state"] = "SCORED"
                item["regrading"] = false
                item["rawScore"] = 7.25
                item["displayScore"] = 7.25
                item["maxScore"] = 10.0
                item["scaleMax"] = 10.0
            }
            let regradingJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "PENDING_REVIEW") { item in
                item["attemptId"] = "att-regrade-1"
                item["state"] = "PENDING_REVIEW"
                item["regrading"] = true
                item["rawScore"] = 8.333333
                item["displayScore"] = 8.333333
                item["maxScore"] = NSNull()
                item["scaleMax"] = NSNull()
            }
            let gradingErrorJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = "att-ge-1"
                item["state"] = "GRADING_ERROR"
                item["regrading"] = false
                item["rawScore"] = NSNull()
                item["displayScore"] = NSNull()
            }
            let compensatedFaultJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = "att-comp-1"
                item["compensated"] = true
                item["faultFlag"] = "SYSTEM_FAULT"
            }
            let zeroScoreJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = "att-zero-1"
                item["state"] = "SCORED"
                item["rawScore"] = 0.0
                item["displayScore"] = 0.0
                item["maxScore"] = 40.0
                item["scaleMax"] = 40.0
            }
            let pendingReviewJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = "att-pr-1"
                item["state"] = "PENDING_REVIEW"
                item["regrading"] = false
                item["rawScore"] = 5.0
                item["displayScore"] = 5.0
            }
            let inProgressJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "IN_PROGRESS") { item in
                item["attemptId"] = "att-ip-1"
                item["state"] = "IN_PROGRESS"
                item["regrading"] = false
            }
            let vactJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = "att-vact-1"
                item["examKindCode"] = "VACT"
                item["scope"] = "SUBJECT"
                item["subjectCode"] = "TOAN"
                item["state"] = "SCORED"
                item["displayScore"] = 820.0
                item["scaleMax"] = 1200.0
            }

            let page = try MyExamsTestFixtures.decodeHistoryPage(
                from: try MyExamsTestFixtures.makeHistoryPagePayload(
                    items: [partItemJSON, regradingJSON, gradingErrorJSON, compensatedFaultJSON, zeroScoreJSON, pendingReviewJSON, inProgressJSON, vactJSON]
                )
            )
            let partItem = page.items[0]
            let regradingItem = page.items[1]
            let gradingErrorItem = page.items[2]
            let compensatedItem = page.items[3]
            let zeroItem = page.items[4]
            let pendingItem = page.items[5]
            let inProgressItem = page.items[6]
            let vactItem = page.items[7]

            XCTAssertEqual(HistoryRowFormat.formatScopeLine(for: partItem, subjectMap: subjects, locale: .vi), "Bài thi theo phần · Phần P1")
            XCTAssertEqual(HistoryRowFormat.resolveStatus(state: partItem.state, regrading: partItem.regrading).localizationKey, "history.states.SCORED")
            XCTAssertEqual(HistoryRowFormat.formatAttemptStartedLine(for: partItem, locale: .vi), "Lần 2 · 09:05, 20/11/2026")
            XCTAssertEqual(HistoryRowFormat.formatScore(for: partItem, locale: .vi).text, "7,25/10")
            XCTAssertEqual(HistoryRowFormat.formatScore(for: partItem, locale: .en).text, "7.25/10")

            XCTAssertEqual(HistoryRowFormat.resolveStatus(state: regradingItem.state, regrading: regradingItem.regrading).localizationKey, "history.states.REGRADING")
            XCTAssertTrue(HistoryRowFormat.showsRegradingNote(for: regradingItem))
            XCTAssertEqual(HistoryRowFormat.formatScore(for: regradingItem, locale: .vi).text, "8,33")

            let geStatus = HistoryRowFormat.resolveStatus(state: gradingErrorItem.state, regrading: gradingErrorItem.regrading)
            XCTAssertEqual(MyExamsStrings.text(geStatus.localizationKey, locale: .vi), "Đang xử lý kết quả")

            XCTAssertEqual(HistoryRowFormat.resolveFault(for: compensatedItem), .compensated)

            XCTAssertEqual(HistoryRowFormat.formatScore(for: zeroItem, locale: .vi).text, "0/40")
            XCTAssertEqual(HistoryRowFormat.formatScore(for: pendingItem, locale: .vi).text, "Chưa có điểm")
            XCTAssertEqual(HistoryRowFormat.formatScore(for: inProgressItem, locale: .vi).text, "Chưa có điểm")

            let sciChoice = [HistoryAttemptChoice(partKey: "P3", optionCode: "KH", subjectCodes: ["LY", "HOA"])]
            XCTAssertEqual(HistoryRowFormat.formatChoices(sciChoice, subjectMap: subjects, locale: .vi), "Khoa học: Vật lý, Hóa học")
            XCTAssertEqual(HistoryRowFormat.formatChoices([], subjectMap: subjects, locale: .vi), "—")

            let vactScore = HistoryRowFormat.formatScore(for: vactItem, locale: .vi)
            XCTAssertTrue(HistoryRowFormat.showsVactPracticeNote(for: vactItem, hasScore: vactScore.hasScore))
            XCTAssertEqual(HistoryRowFormat.formatScopeLine(for: vactItem, subjectMap: subjects, locale: .vi), "Bài thi theo môn · Môn Toán")
        }
    }
}
