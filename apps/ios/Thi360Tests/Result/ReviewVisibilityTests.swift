import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewVisibilityTests: XCTestCase {
    private func makeMutatedPayload(resultView: String) throws -> ReviewWirePayload {
        try ResultTestFixtures.decodeReviewPayload(variant: "full-solution") { root in
            root["resultView"] = resultView
            let part: [String: Any] = [
                "partId": "p-vis",
                "partCode": "P-VIS",
                "partName": "Phần kiểm tra mức xem",
                "ord": 1,
                "state": "CLOSED",
                "pendingReview": false
            ]
            let parts: [[String: Any]] = [part]
            root["parts"] = parts

            let q1Opt1: [String: Any] = ["id": "o1", "content": "<p>Đáp án 1</p>"]
            let q1Opt2: [String: Any] = ["id": "o2", "content": "<p>Đáp án 2</p>"]
            let q1Options: [[String: Any]] = [q1Opt1, q1Opt2]
            let q1Body: [String: Any] = ["options": q1Options]
            let q1Student: [String: Any] = ["type": "SINGLE_CHOICE", "optionId": "o1"]
            let q1ExpectedIds: [String] = ["o2"]
            let q1Expected: [String: Any] = ["expectedOptionIds": q1ExpectedIds]
            let qWithSol: [String: Any] = [
                "itemId": "q-with-sol",
                "partId": "p-vis",
                "partCode": "P-VIS",
                "ord": 1,
                "displayNo": 1,
                "questionType": "SINGLE_CHOICE",
                "content": "<p>Câu có lời giải</p>",
                "body": q1Body,
                "studentAnswer": q1Student,
                "expectedAnswer": q1Expected,
                "solution": "<p>Giải thích chi tiết cho câu 1</p>",
                "outcome": "WRONG",
                "earnedPoints": 0.0,
                "pointsMax": 1.0
            ]

            let q2Opt1: [String: Any] = ["id": "o1", "content": "<p>Đáp án 1</p>"]
            let q2Options: [[String: Any]] = [q2Opt1]
            let q2Body: [String: Any] = ["options": q2Options]
            let q2Student: [String: Any] = ["type": "SINGLE_CHOICE", "optionId": "o1"]
            let q2ExpectedIds: [String] = ["o1"]
            let q2Expected: [String: Any] = ["expectedOptionIds": q2ExpectedIds]
            let qNoSol: [String: Any] = [
                "itemId": "q-no-sol",
                "partId": "p-vis",
                "partCode": "P-VIS",
                "ord": 2,
                "displayNo": 2,
                "questionType": "SINGLE_CHOICE",
                "content": "<p>Câu không có lời giải</p>",
                "body": q2Body,
                "studentAnswer": q2Student,
                "expectedAnswer": q2Expected,
                "outcome": "CORRECT",
                "earnedPoints": 1.0,
                "pointsMax": 1.0
            ]

            let essayStudent: [String: Any] = [
                "type": "ESSAY",
                "html": "<p>Bài viết của học viên</p>",
                "wordCount": 5
            ]
            let essayCriterion1: [String: Any] = ["id": "Nội dung", "points": 4.0, "comment": "Đủ ý chính"]
            let essayCriterion2: [String: Any] = ["points": 2.5]
            let essayCriterion3: [String: Any] = ["id": "Trình bày"]
            let essayCriteria: [[String: Any]] = [essayCriterion1, essayCriterion2, essayCriterion3]
            let essayFeedback: [String: Any] = [
                "comment": "Lập luận chặt chẽ, cần mở rộng ví dụ",
                "criteria": essayCriteria
            ]
            let essayGraded: [String: Any] = [
                "itemId": "essay-graded",
                "partId": "p-vis",
                "partCode": "P-VIS",
                "ord": 3,
                "displayNo": 3,
                "questionType": "ESSAY",
                "content": "<p>Bài tự luận đã chấm</p>",
                "studentAnswer": essayStudent,
                "outcome": "PARTIAL",
                "earnedPoints": 6.5,
                "pointsMax": 10.0,
                "essayFeedback": essayFeedback
            ]

            let blankCriterion: [String: Any] = ["id": "Tiêu chí ẩn", "points": 0.0, "comment": "Ẩn"]
            let blankCriteria: [[String: Any]] = [blankCriterion]
            let blankFeedback: [String: Any] = [
                "comment": "Không được hiện khi BLANK",
                "criteria": blankCriteria
            ]
            let essayBlank: [String: Any] = [
                "itemId": "essay-blank",
                "partId": "p-vis",
                "partCode": "P-VIS",
                "ord": 4,
                "displayNo": 4,
                "questionType": "ESSAY",
                "content": "<p>Bài tự luận bỏ trống</p>",
                "outcome": "BLANK",
                "earnedPoints": 0.0,
                "pointsMax": 5.0,
                "essayFeedback": blankFeedback
            ]

            let setBody: [String: Any] = ["passage": "<p>Ngữ liệu chùm</p>"]
            let setEssay: [String: Any] = [
                "itemId": "set-essay",
                "partId": "p-vis",
                "partCode": "P-VIS",
                "ord": 5,
                "questionType": "QUESTION_SET",
                "content": "<p>Chùm có câu tự luận con</p>",
                "body": setBody,
                "outcome": "PARTIAL",
                "earnedPoints": 3.0,
                "pointsMax": 5.0
            ]

            let childStudent: [String: Any] = [
                "type": "ESSAY",
                "html": "<p>Trả lời câu con</p>",
                "wordCount": 3
            ]
            let childCriterion: [String: Any] = ["id": "Ý chính", "points": 3.0, "comment": "Tốt"]
            let childCriteria: [[String: Any]] = [childCriterion]
            let childFeedback: [String: Any] = ["criteria": childCriteria]
            let setEssayChild: [String: Any] = [
                "itemId": "set-essay-child",
                "parentItemId": "set-essay",
                "partId": "p-vis",
                "partCode": "P-VIS",
                "ord": 6,
                "displayNo": 5,
                "questionType": "ESSAY",
                "content": "<p>Câu tự luận con</p>",
                "studentAnswer": childStudent,
                "outcome": "PARTIAL",
                "earnedPoints": 3.0,
                "pointsMax": 5.0,
                "essayFeedback": childFeedback
            ]

            let items: [[String: Any]] = [
                qWithSol,
                qNoSol,
                essayGraded,
                essayBlank,
                setEssay,
                setEssayChild
            ]
            root["items"] = items
        }
    }

    private func makeViewModel(model: ReviewModel) throws -> ReviewViewModel {
        let (_, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let tokenStore = InMemoryTokenStore(initialTokens: AccountTestFactory.sampleTokens())
        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: transport,
            localeProvider: { await MainActor.run { preferences.locale } }
        )
        let repo = ResultRepository(
            api: OpenAPIResultAPI(apiClient: apiClient),
            catalogOptionsCache: CatalogOptionsCache(apiClient: apiClient)
        )
        let vm = ReviewViewModel(
            attemptId: model.attemptId,
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: model.attemptId)
        )
        vm.setModelForTesting(model)
        return vm
    }

    func testScoreAndAnswersStripsSolutionCommentAndCriteriaInMapperAndView() throws {
        let payload = try makeMutatedPayload(resultView: "SCORE_AND_ANSWERS")
        let model = ReviewMapper.map(payload)

        for group in model.groups {
            XCTAssertNil(group.mode.reviewData?.solutionHtml)
            XCTAssertNil(group.mode.reviewData?.essay?.comment)
            XCTAssertTrue(group.essayCriteria.isEmpty)
            for child in (group.mode.reviewData?.children ?? [:]).values {
                XCTAssertNil(child.solutionHtml)
                XCTAssertNil(child.essay?.comment)
            }
        }

        let vm = try makeViewModel(model: model)
        let (window, _, rSink, qSink) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 420, height: 3200),
            settleSeconds: 0.14
        )
        defer { window.isHidden = true }

        XCTAssertEqual(qSink.nodes["question.q-with-sol.option.o2.correctTag"]?.label, "Đáp án đúng")
        XCTAssertEqual(qSink.nodes["question.q-with-sol.outcomeChip"]?.label, "Sai")
        XCTAssertNil(qSink.nodes["question.review.solutionBlock"])
        XCTAssertNil(qSink.nodes["question.essay-graded.graderComment"])
        XCTAssertNil(rSink.nodes["review.essayCriteria.essay-graded.title"])
        XCTAssertNil(rSink.nodes["review.essayCriteria.set-essay-child.title"])
    }

    func testFullSolutionShowsSolutionCommentCriteriaFallbacksAndHidesBlankEssayFeedback() throws {
        let payload = try makeMutatedPayload(resultView: "FULL_SOLUTION")
        let model = ReviewMapper.map(payload)

        let qWithSol = try XCTUnwrap(model.groups.first(where: { $0.id == "q-with-sol" }))
        XCTAssertNotNil(qWithSol.mode.reviewData?.solutionHtml)
        let qNoSol = try XCTUnwrap(model.groups.first(where: { $0.id == "q-no-sol" }))
        XCTAssertNil(qNoSol.mode.reviewData?.solutionHtml)

        let blankEssay = try XCTUnwrap(model.groups.first(where: { $0.id == "essay-blank" }))
        XCTAssertTrue(blankEssay.essayCriteria.isEmpty, "BLANK essay must strip criteria in mapper")
        XCTAssertNil(blankEssay.mode.reviewData?.essay?.comment, "BLANK essay must strip comment in mapper")

        let vm = try makeViewModel(model: model)
        let (window, _, rSink, qSink) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 420, height: 3400),
            settleSeconds: 0.14
        )
        defer { window.isHidden = true }

        XCTAssertEqual(qSink.nodes["question.review.solutionBlock"]?.label, "Lời giải")

        let graderComment = try XCTUnwrap(qSink.nodes["question.essay-graded.graderComment"]?.label)
        XCTAssertTrue(graderComment.contains("Nhận xét của người chấm"))
        XCTAssertTrue(graderComment.contains("Lập luận chặt chẽ"))

        XCTAssertEqual(rSink.nodes["review.essayCriteria.essay-graded.title"]?.label, "Điểm từng tiêu chí")
        XCTAssertEqual(
            rSink.nodes["review.essayCriteria.essay-graded.row.1"]?.label,
            "Tiêu chí: Nội dung | Điểm: 4 điểm | Nhận xét tiêu chí: Đủ ý chính"
        )
        XCTAssertEqual(
            rSink.nodes["review.essayCriteria.essay-graded.row.2"]?.label,
            "Tiêu chí: Tiêu chí 2 | Điểm: 2,5 điểm | Nhận xét tiêu chí: —"
        )
        XCTAssertEqual(
            rSink.nodes["review.essayCriteria.essay-graded.row.3"]?.label,
            "Tiêu chí: Trình bày | Điểm: — | Nhận xét tiêu chí: —"
        )

        XCTAssertEqual(
            rSink.nodes["review.essayCriteria.set-essay-child.title"]?.label,
            "Điểm từng tiêu chí · Câu 5"
        )

        XCTAssertEqual(qSink.nodes["question.essay-blank.essayBlank"]?.label, "Chưa trả lời")
        XCTAssertNil(qSink.nodes["question.essay-blank.graderComment"])
        XCTAssertNil(rSink.nodes["review.essayCriteria.essay-blank.title"])
    }
}
