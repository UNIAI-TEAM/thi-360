import Foundation
import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class ResultLogRedactionTests: XCTestCase {
    func testDemoResultAndReviewLogsNeverExposeAnswersSolutionsFeedbackOrTokens() async throws {
        #if THI360_DEMO
        XCTAssertEqual(AppEnvironment.current.kind, .demo)
        #endif

        let accessTokenSentinel = "demo-result-access-token-secret-2871"
        let refreshTokenSentinel = "demo-result-refresh-token-secret-2871"
        let studentEssaySentinel = "BaiLamTuLuanBiMat-2871-StudentEssay"
        let shortTextSentinel = "DapAnNganBiMat-2871-ShortAnswer"
        let expectedAnswerSentinel = "DapAnDungBiMat-2871-ExpectedKey"
        let solutionHtmlSentinel = "LoiGiaiChiTietBiMat-2871-SolutionHtml"
        let graderCommentSentinel = "NhanXetNguoiChamBiMat-2871-Feedback"
        let criterionCommentSentinel = "NhanXetTieuChiBiMat-2871-Criterion"

        let resultData = try ResultTestFixtures.loadData(
            operationId: Operations.getResult.id,
            status: 200,
            variant: "scored"
        )
        let baseReviewData = try ResultTestFixtures.loadData(
            operationId: Operations.getReview.id,
            status: 200,
            variant: "full-solution"
        )
        var reviewRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: baseReviewData) as? [String: Any])
        reviewRoot["items"] = [
            [
                "itemId": "demo-redact-1",
                "partId": "part-1",
                "partCode": "PART-1",
                "ord": 1,
                "displayNo": 1,
                "questionType": "SHORT_ANSWER",
                "content": "<p>Câu hỏi ngắn</p>",
                "body": ["answerFormat": "TEXT"],
                "studentAnswer": ["type": "SHORT_ANSWER", "text": shortTextSentinel],
                "expectedAnswer": ["acceptedTexts": [expectedAnswerSentinel]],
                "solution": "<p>\(solutionHtmlSentinel)</p>",
                "outcome": "CORRECT",
                "earnedPoints": 1.0,
                "pointsMax": 1.0
            ],
            [
                "itemId": "demo-redact-2",
                "partId": "part-1",
                "partCode": "PART-1",
                "ord": 2,
                "displayNo": 2,
                "questionType": "ESSAY",
                "content": "<p>Câu tự luận</p>",
                "studentAnswer": ["type": "ESSAY", "html": "<p>\(studentEssaySentinel)</p>", "wordCount": 1],
                "outcome": "PARTIAL",
                "earnedPoints": 5.0,
                "pointsMax": 10.0,
                "essayFeedback": [
                    "comment": graderCommentSentinel,
                    "criteria": [
                        ["id": "C1", "points": 5.0, "comment": criterionCommentSentinel]
                    ]
                ]
            ]
        ]
        let reviewData = try JSONSerialization.data(withJSONObject: reviewRoot)

        let logSink = InMemoryLogSink()
        let demoLogger = RedactingAppLogger(environment: .demo, sink: logSink)
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: accessTokenSentinel,
                refreshToken: refreshTokenSentinel,
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student.demo",
                accountId: "acc-demo-result"
            )
        )

        let transport = ContractFakeTransport()
        try ResultTestFixtures.registerCatalogOptions(on: transport)
        transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-demo-1/result", statusCode: 200, data: resultData)
        transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-demo-1/review", statusCode: 200, data: reviewData)

        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://thi360.zone17.click")!,
            tokenStore: tokenStore,
            transport: transport,
            logger: demoLogger,
            localeProvider: { .vi }
        )
        let repo = ResultRepository(
            api: OpenAPIResultAPI(apiClient: apiClient),
            catalogOptionsCache: CatalogOptionsCache(apiClient: apiClient)
        )

        let loadedResult = try await repo.result(attemptId: "att-demo-1")
        XCTAssertEqual(loadedResult.state, .scored)

        let loadedReview = try await repo.review(attemptId: "att-demo-1")
        let mapped = ReviewMapper.map(loadedReview)
        XCTAssertEqual(mapped.groups.count, 2)

        demoLogger.logHTTP(
            method: "GET",
            path: "/api/v1/me/attempts/att-demo-1/review",
            statusCode: 200,
            headers: [("Authorization", "Bearer \(accessTokenSentinel)")]
        )
        let rawReviewText = String(decoding: reviewData, as: UTF8.self)
        demoLogger.log(.info, category: "result", message: rawReviewText)
        demoLogger.log(
            .info,
            category: "result",
            message: #"{"studentAnswer":{"type":"ESSAY","html":"<p>\#(studentEssaySentinel)</p>"},"expectedAnswer":{"acceptedTexts":["\#(expectedAnswerSentinel)"]},"solution":"<p>\#(solutionHtmlSentinel)</p>","essayFeedback":{"comment":"\#(graderCommentSentinel)"},"gradingDetail":{"text":"\#(shortTextSentinel)"}}"#
        )

        let entries = logSink.recordedEntries
        XCTAssertFalse(entries.isEmpty, "Expected demo result and review logs to be captured")

        let secrets = [
            accessTokenSentinel,
            refreshTokenSentinel,
            studentEssaySentinel,
            shortTextSentinel,
            expectedAnswerSentinel,
            solutionHtmlSentinel,
            graderCommentSentinel,
            criterionCommentSentinel
        ]
        for entry in entries {
            for secret in secrets {
                XCTAssertFalse(
                    entry.message.contains(secret),
                    "Demo log leaked sensitive review content '\(secret)': \(entry.message)"
                )
            }
        }
    }
}
