import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewVisibilityTests: XCTestCase {
    private func makeMutatedPayload(resultView: String) throws -> ReviewWirePayload {
        try ResultTestFixtures.decodeReviewPayload(variant: "full-solution") { root in
            root["resultView"] = resultView
            root["parts"] = [[
                "partId": "p-vis",
                "partCode": "P-VIS",
                "partName": "Phần kiểm tra mức xem",
                "ord": 1,
                "state": "CLOSED",
                "pendingReview": false
            ]]
            root["items"] = [
                [
                    "itemId": "q-with-sol",
                    "partId": "p-vis",
                    "partCode": "P-VIS",
                    "ord": 1,
                    "displayNo": 1,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>Câu có lời giải</p>",
                    "body": [
                        "options": [
                            ["id": "o1", "content": "<p>Đáp án 1</p>"],
                            ["id": "o2", "content": "<p>Đáp án 2</p>"]
                        ]
                    ],
                    "studentAnswer": ["type": "SINGLE_CHOICE", "optionId": "o1"],
                    "expectedAnswer": ["expectedOptionIds": ["o2"]],
                    "solution": "<p>Giải thích chi tiết cho câu 1</p>",
                    "outcome": "WRONG",
                    "earnedPoints": 0.0,
                    "pointsMax": 1.0
                ],
                [
                    "itemId": "q-no-sol",
                    "partId": "p-vis",
                    "partCode": "P-VIS",
                    "ord": 2,
                    "displayNo": 2,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>Câu không có lời giải</p>",
                    "body": ["options": [["id": "o1", "content": "<p>Đáp án 1</p>"]]],
                    "studentAnswer": ["type": "SINGLE_CHOICE", "optionId": "o1"],
                    "expectedAnswer": ["expectedOptionIds": ["o1"]],
                    "outcome": "CORRECT",
                    "earnedPoints": 1.0,
                    "pointsMax": 1.0
                ],
                [
                    "itemId": "essay-graded",
                    "partId": "p-vis",
                    "partCode": "P-VIS",
                    "ord": 3,
                    "displayNo": 3,
                    "questionType": "ESSAY",
                    "content": "<p>Bài tự luận đã chấm</p>",
                    "studentAnswer": ["type": "ESSAY", "html": "<p>Bài viết của học viên</p>", "wordCount": 5],
                    "outcome": "PARTIAL",
                    "earnedPoints": 6.5,
                    "pointsMax": 10.0,
                    "essayFeedback": [
                        "comment": "Lập luận chặt chẽ, cần mở rộng ví dụ",
                        "criteria": [
                            ["id": "Nội dung", "points": 4.0, "comment": "Đủ ý chính"],
                            ["points": 2.5],
                            ["id": "Trình bày"]
                        ]
                    ]
                ],
                [
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
                    "essayFeedback": [
                        "comment": "Không được hiện khi BLANK",
                        "criteria": [
                            ["id": "Tiêu chí ẩn", "points": 0.0, "comment": "Ẩn"]
                        ]
                    ]
                ],
                [
                    "itemId": "set-essay",
                    "partId": "p-vis",
                    "partCode": "P-VIS",
                    "ord": 5,
                    "questionType": "QUESTION_SET",
                    "content": "<p>Chùm có câu tự luận con</p>",
                    "body": ["passage": "<p>Ngữ liệu chùm</p>"],
                    "outcome": "PARTIAL",
                    "earnedPoints": 3.0,
                    "pointsMax": 5.0
                ],
                [
                    "itemId": "set-essay-child",
                    "parentItemId": "set-essay",
                    "partId": "p-vis",
                    "partCode": "P-VIS",
                    "ord": 6,
                    "displayNo": 5,
                    "questionType": "ESSAY",
                    "content": "<p>Câu tự luận con</p>",
                    "studentAnswer": ["type": "ESSAY", "html": "<p>Trả lời câu con</p>", "wordCount": 3],
                    "outcome": "PARTIAL",
                    "earnedPoints": 3.0,
                    "pointsMax": 5.0,
                    "essayFeedback": [
                        "criteria": [
                            ["id": "Ý chính", "points": 3.0, "comment": "Tốt"]
                        ]
                    ]
                ]
            ]
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
