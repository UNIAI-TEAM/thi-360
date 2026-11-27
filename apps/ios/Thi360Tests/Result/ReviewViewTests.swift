import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewViewTests: XCTestCase {
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

    func testHeaderAndBanners() throws {
        let scoredResult = try ResultTestFixtures.decodeResult(variant: "scored")
        let fullPayload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution")
        let fullModel = ReviewMapper.map(fullPayload, resultSummary: scoredResult)
        let fullVM = try makeViewModel(model: fullModel)

        let (winFull, _, sinkFull, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: fullVM,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sinkFull.nodes["review.header.title"]?.label, "Xem lại bài làm")
        let subtitle = try XCTUnwrap(sinkFull.nodes["review.header.subtitle"]?.label)
        XCTAssertTrue(subtitle.contains(scoredResult.examName))
        XCTAssertTrue(subtitle.contains("Lần \(scoredResult.attemptNo ?? 1)"))
        XCTAssertTrue(subtitle.contains("Tổng điểm:"))
        XCTAssertNil(sinkFull.nodes["review.banner.scoreOnly"])
        XCTAssertNil(sinkFull.nodes["review.banner.pendingReview"])
        winFull.isHidden = true

        let scoreAndAnswersPayload = try ResultTestFixtures.decodeReviewPayload(variant: "score-and-answers")
        let saModel = ReviewMapper.map(scoreAndAnswersPayload, resultSummary: scoredResult)
        let saVM = try makeViewModel(model: saModel)

        let (winSA, _, sinkSA, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: saVM,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(
            sinkSA.nodes["review.banner.scoreOnly"]?.label,
            ResultStrings.text("review.scoreOnlyNotice", locale: .vi)
        )
        XCTAssertNil(sinkSA.nodes["review.banner.pendingReview"])
        winSA.isHidden = true

        let pendingResult = try ResultTestFixtures.decodeResult(variant: "pending-review")
        let pendingPayload = try ResultTestFixtures.decodeReviewPayload(variant: "pending-review")
        let pendingModel = ReviewMapper.map(pendingPayload, resultSummary: pendingResult)
        let pendingVM = try makeViewModel(model: pendingModel)

        let (winPending, _, sinkPending, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: pendingVM,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(
            sinkPending.nodes["review.banner.pendingReview"]?.label,
            ResultStrings.text("review.pendingBanner", locale: .vi)
        )
        let pendingSubtitle = try XCTUnwrap(sinkPending.nodes["review.header.subtitle"]?.label)
        XCTAssertFalse(pendingSubtitle.contains("Tổng điểm:"), "Non-regrading PENDING_REVIEW has no display score in subtitle")
        winPending.isHidden = true

        let regradingResult = try ResultTestFixtures.decodeResult(variant: "regrading")
        let regradingModel = ReviewMapper.map(pendingPayload, resultSummary: regradingResult)
        let regradingVM = try makeViewModel(model: regradingModel)

        let (winRegrading, _, sinkRegrading, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: regradingVM,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(
            sinkRegrading.nodes["review.banner.pendingReview"]?.label,
            ResultStrings.text("review.pendingBanner", locale: .vi)
        )
        let regradingSubtitle = try XCTUnwrap(sinkRegrading.nodes["review.header.subtitle"]?.label)
        XCTAssertTrue(regradingSubtitle.contains("Tổng điểm:"))
        winRegrading.isHidden = true
    }

    func testQuestionContent() throws {
        // Derive multi-type review items by mutating the certified full-solution fixture per A6 for AC-18 rendering checks.
        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution") { root in
            let partId = "part-q-1"
            let partCode = "PART-Q"
            root["parts"] = [[
                "partId": partId,
                "partCode": partCode,
                "partName": "Phần kiểm thử",
                "ord": 1,
                "state": "CLOSED",
                "pendingReview": false,
                "score": 4.0,
                "maxScore": 8.0
            ]]
            root["items"] = [
                [
                    "itemId": "q-sc-1",
                    "partId": partId,
                    "partCode": partCode,
                    "ord": 1,
                    "displayNo": 1,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>Câu một đáp án</p>",
                    "optionOrder": ["opt-b", "opt-a"],
                    "body": [
                        "options": [
                            ["id": "opt-a", "content": "<p>Lựa chọn A</p>"],
                            ["id": "opt-b", "content": "<p>Lựa chọn B</p>"]
                        ]
                    ],
                    "studentAnswer": ["type": "SINGLE_CHOICE", "optionId": "opt-a"],
                    "expectedAnswer": ["expectedOptionIds": ["opt-b"]],
                    "outcome": "WRONG",
                    "earnedPoints": 0.0,
                    "pointsMax": 1.0
                ],
                [
                    "itemId": "q-unscored-2",
                    "partId": partId,
                    "partCode": partCode,
                    "ord": 2,
                    "displayNo": 2,
                    "questionType": "SHORT_ANSWER",
                    "content": "<p>Câu chưa có điểm</p>",
                    "body": ["answerFormat": "TEXT"],
                    "studentAnswer": ["type": "SHORT_ANSWER", "text": "42"],
                    "outcome": "PARTIAL",
                    "pointsMax": 2.0
                ],
                [
                    "itemId": "q-match-3",
                    "partId": partId,
                    "partCode": partCode,
                    "ord": 3,
                    "displayNo": 3,
                    "questionType": "MATCHING",
                    "content": "<p>Ghép đôi</p>",
                    "body": [
                        "left": [
                            ["id": "l1", "content": "<p>Trái 1</p>"],
                            ["id": "l2", "content": "<p>Trái 2</p>"]
                        ],
                        "right": [
                            ["id": "r1", "content": "<p>Phải 1</p>"],
                            ["id": "r2", "content": "<p>Phải 2</p>"]
                        ],
                        "reuseRight": false
                    ],
                    "studentAnswer": ["type": "MATCHING", "pairs": ["l1": "r1", "l2": "r1"]],
                    "expectedAnswer": [
                        "matchingKeys": [
                            ["leftId": "l1", "rightId": "r1"],
                            ["leftId": "l2", "rightId": "r2"]
                        ]
                    ],
                    "gradingDetail": [
                        "pairResults": [
                            ["leftId": "l1", "matched": true, "pointsEarned": 1.0],
                            ["leftId": "l2", "matched": false, "pointsEarned": 0.0]
                        ]
                    ],
                    "outcome": "PARTIAL",
                    "earnedPoints": 1.0,
                    "pointsMax": 2.0
                ],
                [
                    "itemId": "q-tf-4",
                    "partId": partId,
                    "partCode": partCode,
                    "ord": 4,
                    "displayNo": 4,
                    "questionType": "TRUE_FALSE",
                    "content": "<p>Đúng sai</p>",
                    "body": [
                        "statements": [
                            ["id": "s1", "content": "<p>Ý 1</p>"],
                            ["id": "s2", "content": "<p>Ý 2</p>"],
                            ["id": "s3", "content": "<p>Ý 3</p>"]
                        ]
                    ],
                    "studentAnswer": ["type": "TRUE_FALSE_GROUP", "statements": ["s1": true, "s2": false]],
                    "expectedAnswer": [
                        "trueFalseKeys": [
                            ["statementId": "s1", "expected": true],
                            ["statementId": "s2", "expected": true],
                            ["statementId": "s3", "expected": false]
                        ]
                    ],
                    "gradingDetail": [
                        "statementResults": [
                            ["id": "s1", "answered": true, "matched": true],
                            ["id": "s2", "answered": true, "matched": false],
                            ["id": "s3", "answered": false, "matched": false]
                        ]
                    ],
                    "outcome": "PARTIAL",
                    "earnedPoints": 0.5,
                    "pointsMax": 1.0
                ],
                [
                    "itemId": "q-set-5",
                    "partId": partId,
                    "partCode": partCode,
                    "ord": 5,
                    "questionType": "QUESTION_SET",
                    "content": "<p>Chùm câu hỏi</p>",
                    "body": ["passage": "<p>Đoạn dẫn cho câu 5 và 6</p>"],
                    "outcome": "PARTIAL",
                    "earnedPoints": 1.0,
                    "pointsMax": 2.0
                ],
                [
                    "itemId": "q-set-5-c1",
                    "parentItemId": "q-set-5",
                    "partId": partId,
                    "partCode": partCode,
                    "ord": 6,
                    "displayNo": 5,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>Câu con 5</p>",
                    "body": [
                        "options": [
                            ["id": "c1-a", "content": "<p>Đáp án 5A</p>"]
                        ]
                    ],
                    "studentAnswer": ["type": "SINGLE_CHOICE", "optionId": "c1-a"],
                    "expectedAnswer": ["expectedOptionIds": ["c1-a"]],
                    "outcome": "CORRECT",
                    "earnedPoints": 1.0,
                    "pointsMax": 1.0
                ],
                [
                    "itemId": "q-set-5-c2",
                    "parentItemId": "q-set-5",
                    "partId": partId,
                    "partCode": partCode,
                    "ord": 7,
                    "displayNo": 6,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>Câu con 6</p>",
                    "body": [
                        "options": [
                            ["id": "c2-a", "content": "<p>Đáp án 6A</p>"]
                        ]
                    ],
                    "expectedAnswer": ["expectedOptionIds": ["c2-a"]],
                    "outcome": "BLANK",
                    "earnedPoints": 0.0,
                    "pointsMax": 1.0
                ]
            ]
        }

        let model = ReviewMapper.map(payload)
        let vm = try makeViewModel(model: model)

        let (window, _, _, qSink) = ResultViewTestHelper.host(
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

        XCTAssertEqual(qSink.nodes["question.q-sc-1.outcomeChip"]?.label, "Sai")
        XCTAssertEqual(qSink.nodes["question.q-sc-1.option.opt-a"]?.isSelected, true)
        XCTAssertEqual(qSink.nodes["question.q-sc-1.option.opt-a.pickTag"]?.label, "Bạn chọn · Sai")
        XCTAssertEqual(qSink.nodes["question.q-sc-1.option.opt-b.correctTag"]?.label, "Đáp án đúng")
        let optBNode = try XCTUnwrap(qSink.nodes["question.q-sc-1.option.opt-b"])
        let optANode = try XCTUnwrap(qSink.nodes["question.q-sc-1.option.opt-a"])
        XCTAssertEqual(optBNode.label, "A.")
        XCTAssertEqual(optANode.label, "B.")
        XCTAssertLessThan(optBNode.frame.minY, optANode.frame.minY, "optionOrder must display opt-b before opt-a")

        XCTAssertEqual(qSink.nodes["question.q-unscored-2.reviewPoints"]?.label, "Chưa có điểm · tối đa 2 điểm")

        let matchSummary = try XCTUnwrap(qSink.nodes["question.q-match-3.matchReviewList"]?.label)
        XCTAssertTrue(matchSummary.contains("1 / 1"), "Each matching pair max must equal pointsMax / pairCount (2 / 2 = 1): \(matchSummary)")
        XCTAssertTrue(matchSummary.contains("0 / 1"))

        XCTAssertNotNil(qSink.nodes["question.q-tf-4.tfReviewList"])

        XCTAssertEqual(qSink.nodes["question.q-set-5-c1.outcomeChip"]?.label, "Đúng")
        XCTAssertEqual(qSink.nodes["question.q-set-5-c1.reviewPoints"]?.label, "1 / 1 điểm")
        XCTAssertEqual(qSink.nodes["question.q-set-5-c2.outcomeChip"]?.label, "Bỏ trống")
        XCTAssertEqual(qSink.nodes["question.q-set-5-c2.reviewPoints"]?.label, "0 / 1 điểm")
    }
}
