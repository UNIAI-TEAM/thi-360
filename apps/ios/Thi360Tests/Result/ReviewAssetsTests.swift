import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

final class ReviewRecordingAssetURLProtocol: URLProtocol {
    static let lock = NSLock()
    static var recordedRequests: [URLRequest] = []
    static var handler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    static func reset() {
        lock.lock()
        recordedRequests.removeAll()
        handler = nil
        lock.unlock()
    }

    static func snapshotRequests() -> [URLRequest] {
        lock.lock()
        defer { lock.unlock() }
        return recordedRequests
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        Self.recordedRequests.append(request)
        let currentHandler = Self.handler
        Self.lock.unlock()

        do {
            guard let currentHandler else {
                throw URLError(.cannotFindHost)
            }
            let (response, data) = try currentHandler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

@MainActor
final class ReviewAssetsTests: XCTestCase {
    private let attemptId = "44444444-4444-4444-8444-444444444444"
    private let questionAssetId = "11111111-2222-4333-8444-555555555555"
    private let solutionAssetId = "66666666-7777-4888-8999-aaaaaaaaaaaa"
    private let missingSolutionAssetId = "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff"

    override func tearDown() {
        ReviewRecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    private func samplePNGData() -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 120, height: 80))
        return renderer.image { ctx in
            UIColor.systemIndigo.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 120, height: 80))
        }.pngData()!
    }

    func testReviewLoadsAttemptAndSolutionAssetsWithBearerAndShows404Placeholder() async throws {
        let png = samplePNGData()
        let notFoundData = try ResultTestFixtures.loadData(
            operationId: Operations.downloadSolutionAsset.id,
            status: 404,
            variant: "not-found"
        )
        let bearerToken = "review-asset-bearer-token-2871"

        ReviewRecordingAssetURLProtocol.reset()
        ReviewRecordingAssetURLProtocol.handler = { req in
            if req.url?.path.contains(self.missingSolutionAssetId) == true {
                let resp = HTTPURLResponse(
                    url: req.url!,
                    statusCode: 404,
                    httpVersion: "HTTP/1.1",
                    headerFields: ["Content-Type": "application/json"]
                )!
                return (resp, notFoundData)
            }
            let resp = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (resp, png)
        }

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: bearerToken,
                refreshToken: "review-refresh-token",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student.review",
                accountId: "acc-rev-assets"
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let session = AttemptAssetSource.makeNoDiskCacheSession(
            protocolClasses: [ReviewRecordingAssetURLProtocol.self]
        )
        let assetSource = AttemptAssetSource(
            attemptId: attemptId,
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: session
        )
        defer { assetSource.purgeAll() }

        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution") { root in
            root["attemptId"] = self.attemptId
            let part: [String: Any] = [
                "partId": "p-img",
                "partCode": "P-IMG",
                "partName": "Phần hình ảnh",
                "ord": 1,
                "state": "CLOSED",
                "pendingReview": false
            ]
            let parts: [[String: Any]] = [part]
            root["parts"] = parts

            let contentHtml = """
            <p>Nội dung đề vẫn hiển thị rõ ràng</p>
            <figure class="image" data-width="medium" data-zoom="false">
              <img src="/api/v1/me/attempts/\(self.attemptId)/assets/\(self.questionAssetId)" alt="Ảnh đề thi">
            </figure>
            """
            let solutionHtml = """
            <p>Giải thích kèm hình ảnh</p>
            <figure class="image" data-width="medium" data-zoom="false">
              <img src="/api/v1/me/attempts/\(self.attemptId)/solution-assets/\(self.solutionAssetId)" alt="Ảnh lời giải">
            </figure>
            <figure class="image" data-width="medium" data-zoom="false">
              <img src="/api/v1/me/attempts/\(self.attemptId)/solution-assets/\(self.missingSolutionAssetId)" alt="Ảnh bị thiếu">
            </figure>
            """
            let option: [String: Any] = ["id": "o1", "content": "<p>Đáp án đúng</p>"]
            let options: [[String: Any]] = [option]
            let body: [String: Any] = ["options": options]
            let studentAnswer: [String: Any] = ["type": "SINGLE_CHOICE", "optionId": "o1"]
            let expectedIds: [String] = ["o1"]
            let expectedAnswer: [String: Any] = ["expectedOptionIds": expectedIds]
            let item: [String: Any] = [
                "itemId": "q-img-1",
                "partId": "p-img",
                "partCode": "P-IMG",
                "ord": 1,
                "displayNo": 1,
                "questionType": "SINGLE_CHOICE",
                "content": contentHtml,
                "body": body,
                "studentAnswer": studentAnswer,
                "expectedAnswer": expectedAnswer,
                "solution": solutionHtml,
                "outcome": "CORRECT",
                "earnedPoints": 1.0,
                "pointsMax": 1.0
            ]
            let items: [[String: Any]] = [item]
            root["items"] = items
        }

        let (_, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
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
            attemptId: attemptId,
            repository: repo,
            assetSource: assetSource
        )
        vm.setModelForTesting(ReviewMapper.map(payload))

        let (window, controller, _, qSink) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1400),
            settleSeconds: 0.15
        )
        defer { window.isHidden = true }

        let deadline = Date().addingTimeInterval(4.0)
        while Date() < deadline {
            controller.view.layoutIfNeeded()
            let paths = Set(ReviewRecordingAssetURLProtocol.snapshotRequests().compactMap { $0.url?.path })
            let hasErrorBtn = RecoveryViewTestHelper.findViews(ofType: UIButton.self, in: controller.view)
                .contains { $0.accessibilityIdentifier == "richContent.image.error" }
            if paths.count >= 3 && hasErrorBtn {
                break
            }
            try await Task.sleep(for: .milliseconds(50))
        }

        let requests = ReviewRecordingAssetURLProtocol.snapshotRequests()
        let attemptAssetReq = try XCTUnwrap(
            requests.first { $0.url?.path == "/api/v1/me/attempts/\(attemptId)/assets/\(questionAssetId)" },
            "Expected request to /api/v1/me/attempts/{id}/assets/{assetId}"
        )
        let solutionAssetReq = try XCTUnwrap(
            requests.first { $0.url?.path == "/api/v1/me/attempts/\(attemptId)/solution-assets/\(solutionAssetId)" },
            "Expected request to /api/v1/me/attempts/{id}/solution-assets/{assetId}"
        )
        let missingReq = try XCTUnwrap(
            requests.first { $0.url?.path == "/api/v1/me/attempts/\(attemptId)/solution-assets/\(missingSolutionAssetId)" },
            "Expected request to missing solution asset"
        )

        for req in [attemptAssetReq, solutionAssetReq, missingReq] {
            XCTAssertEqual(req.url?.host, "api.thi360.test")
            XCTAssertEqual(req.value(forHTTPHeaderField: "Authorization"), "Bearer \(bearerToken)")
        }

        let errorButton = try XCTUnwrap(
            RecoveryViewTestHelper.findViews(ofType: UIButton.self, in: controller.view)
                .first { $0.accessibilityIdentifier == "richContent.image.error" },
            "404 solution image must render error placeholder button"
        )
        XCTAssertTrue(errorButton.accessibilityLabel?.contains("Ảnh bị thiếu") == true)

        let loadedImageViews = RecoveryViewTestHelper.findViews(ofType: UIImageView.self, in: controller.view)
            .filter { $0.bounds.width > 50 && !$0.isDescendant(of: errorButton) }
        XCTAssertGreaterThanOrEqual(loadedImageViews.count, 2, "Both valid attempt and solution images must render")

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        XCTAssertTrue(textViews.contains { $0.customAccessibilityDescription.contains("Nội dung đề vẫn hiển thị rõ ràng") })
        XCTAssertEqual(qSink.nodes["question.q-img-1.outcomeChip"]?.label, "Đúng")
    }
}
