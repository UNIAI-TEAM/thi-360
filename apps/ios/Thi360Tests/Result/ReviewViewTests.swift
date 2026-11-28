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
            let part: [String: Any] = [
                "partId": partId,
                "partCode": partCode,
                "partName": "Phần kiểm thử",
                "ord": 1,
                "state": "CLOSED",
                "pendingReview": false,
                "score": 4.0,
                "maxScore": 8.0
            ]
            let parts: [[String: Any]] = [part]
            root["parts"] = parts

            let scOptionA: [String: Any] = ["id": "opt-a", "content": "<p>Lựa chọn A</p>"]
            let scOptionB: [String: Any] = ["id": "opt-b", "content": "<p>Lựa chọn B</p>"]
            let scOptions: [[String: Any]] = [scOptionA, scOptionB]
            let scBody: [String: Any] = ["options": scOptions]
            let scStudentAnswer: [String: Any] = ["type": "SINGLE_CHOICE", "optionId": "opt-a"]
            let scExpectedIds: [String] = ["opt-b"]
            let scExpectedAnswer: [String: Any] = ["expectedOptionIds": scExpectedIds]
            let scOptionOrder: [String] = ["opt-b", "opt-a"]
            let scItem: [String: Any] = [
                "itemId": "q-sc-1",
                "partId": partId,
                "partCode": partCode,
                "ord": 1,
                "displayNo": 1,
                "questionType": "SINGLE_CHOICE",
                "content": "<p>Câu một đáp án</p>",
                "optionOrder": scOptionOrder,
                "body": scBody,
                "studentAnswer": scStudentAnswer,
                "expectedAnswer": scExpectedAnswer,
                "outcome": "WRONG",
                "earnedPoints": 0.0,
                "pointsMax": 1.0
            ]

            let unscoredBody: [String: Any] = ["answerFormat": "TEXT"]
            let unscoredStudentAnswer: [String: Any] = ["type": "SHORT_ANSWER", "text": "42"]
            let unscoredItem: [String: Any] = [
                "itemId": "q-unscored-2",
                "partId": partId,
                "partCode": partCode,
                "ord": 2,
                "displayNo": 2,
                "questionType": "SHORT_ANSWER",
                "content": "<p>Câu chưa có điểm</p>",
                "body": unscoredBody,
                "studentAnswer": unscoredStudentAnswer,
                "outcome": "PARTIAL",
                "pointsMax": 2.0
            ]

            let matchLeft1: [String: Any] = ["id": "l1", "content": "<p>Trái 1</p>"]
            let matchLeft2: [String: Any] = ["id": "l2", "content": "<p>Trái 2</p>"]
            let matchLeft: [[String: Any]] = [matchLeft1, matchLeft2]
            let matchRight1: [String: Any] = ["id": "r1", "content": "<p>Phải 1</p>"]
            let matchRight2: [String: Any] = ["id": "r2", "content": "<p>Phải 2</p>"]
            let matchRight: [[String: Any]] = [matchRight1, matchRight2]
            let matchBody: [String: Any] = [
                "left": matchLeft,
                "right": matchRight,
                "reuseRight": false
            ]
            let matchPairs: [String: String] = ["l1": "r1", "l2": "r1"]
            let matchStudentAnswer: [String: Any] = ["type": "MATCHING", "pairs": matchPairs]
            let matchKey1: [String: Any] = ["leftId": "l1", "rightId": "r1"]
            let matchKey2: [String: Any] = ["leftId": "l2", "rightId": "r2"]
            let matchKeys: [[String: Any]] = [matchKey1, matchKey2]
            let matchExpectedAnswer: [String: Any] = ["matchingKeys": matchKeys]
            let matchPairResult1: [String: Any] = ["leftId": "l1", "matched": true, "pointsEarned": 1.0]
            let matchPairResult2: [String: Any] = ["leftId": "l2", "matched": false, "pointsEarned": 0.0]
            let matchPairResults: [[String: Any]] = [matchPairResult1, matchPairResult2]
            let matchGradingDetail: [String: Any] = ["pairResults": matchPairResults]
            let matchItem: [String: Any] = [
                "itemId": "q-match-3",
                "partId": partId,
                "partCode": partCode,
                "ord": 3,
                "displayNo": 3,
                "questionType": "MATCHING",
                "content": "<p>Ghép đôi</p>",
                "body": matchBody,
                "studentAnswer": matchStudentAnswer,
                "expectedAnswer": matchExpectedAnswer,
                "gradingDetail": matchGradingDetail,
                "outcome": "PARTIAL",
                "earnedPoints": 1.0,
                "pointsMax": 2.0
            ]

            let tfStmt1: [String: Any] = ["id": "s1", "content": "<p>Ý 1</p>"]
            let tfStmt2: [String: Any] = ["id": "s2", "content": "<p>Ý 2</p>"]
            let tfStmt3: [String: Any] = ["id": "s3", "content": "<p>Ý 3</p>"]
            let tfStatements: [[String: Any]] = [tfStmt1, tfStmt2, tfStmt3]
            let tfBody: [String: Any] = ["statements": tfStatements]
            let tfStudentStatements: [String: Bool] = ["s1": true, "s2": false]
            let tfStudentAnswer: [String: Any] = ["type": "TRUE_FALSE", "statements": tfStudentStatements]
            let tfKey1: [String: Any] = ["statementId": "s1", "expected": true]
            let tfKey2: [String: Any] = ["statementId": "s2", "expected": true]
            let tfKey3: [String: Any] = ["statementId": "s3", "expected": false]
            let tfKeys: [[String: Any]] = [tfKey1, tfKey2, tfKey3]
            let tfExpectedAnswer: [String: Any] = ["trueFalseKeys": tfKeys]
            let tfResult1: [String: Any] = ["id": "s1", "answered": true, "matched": true]
            let tfResult2: [String: Any] = ["id": "s2", "answered": true, "matched": false]
            let tfResult3: [String: Any] = ["id": "s3", "answered": false, "matched": false]
            let tfStatementResults: [[String: Any]] = [tfResult1, tfResult2, tfResult3]
            let tfGradingDetail: [String: Any] = ["statementResults": tfStatementResults]
            let tfItem: [String: Any] = [
                "itemId": "q-tf-4",
                "partId": partId,
                "partCode": partCode,
                "ord": 4,
                "displayNo": 4,
                "questionType": "TRUE_FALSE",
                "content": "<p>Đúng sai</p>",
                "body": tfBody,
                "studentAnswer": tfStudentAnswer,
                "expectedAnswer": tfExpectedAnswer,
                "gradingDetail": tfGradingDetail,
                "outcome": "PARTIAL",
                "earnedPoints": 0.5,
                "pointsMax": 1.0
            ]

            let setBody: [String: Any] = ["passage": "<p>Đoạn dẫn cho câu 5 và 6</p>"]
            let setItem: [String: Any] = [
                "itemId": "q-set-5",
                "partId": partId,
                "partCode": partCode,
                "ord": 5,
                "questionType": "QUESTION_SET",
                "content": "<p>Chùm câu hỏi</p>",
                "body": setBody,
                "outcome": "PARTIAL",
                "earnedPoints": 1.0,
                "pointsMax": 2.0
            ]

            let child1Option: [String: Any] = ["id": "c1-a", "content": "<p>Đáp án 5A</p>"]
            let child1Options: [[String: Any]] = [child1Option]
            let child1Body: [String: Any] = ["options": child1Options]
            let child1StudentAnswer: [String: Any] = ["type": "SINGLE_CHOICE", "optionId": "c1-a"]
            let child1ExpectedIds: [String] = ["c1-a"]
            let child1ExpectedAnswer: [String: Any] = ["expectedOptionIds": child1ExpectedIds]
            let child1Item: [String: Any] = [
                "itemId": "q-set-5-c1",
                "parentItemId": "q-set-5",
                "partId": partId,
                "partCode": partCode,
                "ord": 6,
                "displayNo": 5,
                "questionType": "SINGLE_CHOICE",
                "content": "<p>Câu con 5</p>",
                "body": child1Body,
                "studentAnswer": child1StudentAnswer,
                "expectedAnswer": child1ExpectedAnswer,
                "outcome": "CORRECT",
                "earnedPoints": 1.0,
                "pointsMax": 1.0
            ]

            let child2Option: [String: Any] = ["id": "c2-a", "content": "<p>Đáp án 6A</p>"]
            let child2Options: [[String: Any]] = [child2Option]
            let child2Body: [String: Any] = ["options": child2Options]
            let child2ExpectedIds: [String] = ["c2-a"]
            let child2ExpectedAnswer: [String: Any] = ["expectedOptionIds": child2ExpectedIds]
            let child2Item: [String: Any] = [
                "itemId": "q-set-5-c2",
                "parentItemId": "q-set-5",
                "partId": partId,
                "partCode": partCode,
                "ord": 7,
                "displayNo": 6,
                "questionType": "SINGLE_CHOICE",
                "content": "<p>Câu con 6</p>",
                "body": child2Body,
                "expectedAnswer": child2ExpectedAnswer,
                "outcome": "BLANK",
                "earnedPoints": 0.0,
                "pointsMax": 1.0
            ]

            let items: [[String: Any]] = [
                scItem,
                unscoredItem,
                matchItem,
                tfItem,
                setItem,
                child1Item,
                child2Item
            ]
            root["items"] = items
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
        let optBNode: QuestionInspectedNode = try XCTUnwrap(qSink.nodes["question.q-sc-1.option.opt-b"])
        let optANode: QuestionInspectedNode = try XCTUnwrap(qSink.nodes["question.q-sc-1.option.opt-a"])
        XCTAssertEqual(optBNode.label, "A.")
        XCTAssertEqual(optANode.label, "B.")
        XCTAssertLessThan(optBNode.frame.minY, optANode.frame.minY, "optionOrder must display opt-b before opt-a")

        XCTAssertEqual(qSink.nodes["question.q-unscored-2.reviewPoints"]?.label, "Chưa có điểm · tối đa 2 điểm")

        let matchSummary: String = try XCTUnwrap(qSink.nodes["question.q-match-3.matchReviewList"]?.label)
        XCTAssertTrue(matchSummary.contains("1 / 1"), "Each matching pair max must equal pointsMax / pairCount (2 / 2 = 1): \(matchSummary)")
        XCTAssertTrue(matchSummary.contains("0 / 1"))

        XCTAssertNotNil(qSink.nodes["question.q-tf-4.tfReviewList"])
        let matchedTFGroup: ReviewGroup? = model.groups.first(where: { $0.id == "q-tf-4" })
        let tfGroup: ReviewGroup = try XCTUnwrap(matchedTFGroup)
        XCTAssertEqual(
            tfGroup.studentAnswer?.trueFalseStatements,
            ["s1": true, "s2": false],
            "TRUE_FALSE wire discriminator must extract student statements into ReviewGroup"
        )
        XCTAssertEqual(tfGroup.mode.reviewData?.detail["s1"]?.outcome, .correct)
        XCTAssertEqual(tfGroup.mode.reviewData?.detail["s2"]?.outcome, .wrong)
        XCTAssertEqual(tfGroup.mode.reviewData?.detail["s3"]?.outcome, .blank)

        let answeredTFWindow = ResultViewTestHelper.host(
            QuestionView(
                item: tfGroup.item,
                answer: tfGroup.studentAnswer,
                answers: [:],
                mode: tfGroup.mode,
                assets: vm.assetSource,
                locale: .vi,
                fixedWidth: 358
            ),
            size: CGSize(width: 390, height: 600),
            settleSeconds: 0.08
        )
        let (_, answeredTFPNG) = try CatalogViewInspector.renderPNG(window: answeredTFWindow.0, size: CGSize(width: 390, height: 600))
        answeredTFWindow.0.isHidden = true

        let blankTFWindow = ResultViewTestHelper.host(
            QuestionView(
                item: tfGroup.item,
                answer: nil,
                answers: [:],
                mode: tfGroup.mode,
                assets: vm.assetSource,
                locale: .vi,
                fixedWidth: 358
            ),
            size: CGSize(width: 390, height: 600),
            settleSeconds: 0.08
        )
        let (_, blankTFPNG) = try CatalogViewInspector.renderPNG(window: blankTFWindow.0, size: CGSize(width: 390, height: 600))
        blankTFWindow.0.isHidden = true
        XCTAssertNotEqual(
            answeredTFPNG,
            blankTFPNG,
            "True/False review view must visibly render student picks ('Đúng'/'Sai') instead of all 'Chưa trả lời'"
        )

        XCTAssertEqual(qSink.nodes["question.q-set-5-c1.outcomeChip"]?.label, "Đúng")
        XCTAssertEqual(qSink.nodes["question.q-set-5-c1.reviewPoints"]?.label, "1 / 1 điểm")
        XCTAssertEqual(qSink.nodes["question.q-set-5-c2.outcomeChip"]?.label, "Bỏ trống")
        XCTAssertEqual(qSink.nodes["question.q-set-5-c2.reviewPoints"]?.label, "0 / 1 điểm")
    }
}
