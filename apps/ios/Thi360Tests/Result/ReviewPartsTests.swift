import Foundation
import HTTPTypes
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewPartsTests: XCTestCase {
    private func makeStack(
        reviewData: Data,
        resultData: Data
    ) throws -> (ResultRepository, ContractFakeTransport) {
        let (_, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        try ResultTestFixtures.registerCatalogOptions(on: transport)
        transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-parts/review", statusCode: 200, data: reviewData)
        transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-parts/result", statusCode: 200, data: resultData)
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
        return (repo, transport)
    }

    func testMultiPartSortingSwitchingSkippedEmptyAndReopenReset() async throws {
        // Mutate certified pending-review fixture to supply unsorted parts, a SKIPPED part with score, a SKIPPED part without score, and an empty part.
        let baseData = try ResultTestFixtures.loadData(operationId: Operations.getReview.id, status: 200, variant: "pending-review")
        var root = try XCTUnwrap(JSONSerialization.jsonObject(with: baseData) as? [String: Any])
        let origParts = try XCTUnwrap(root["parts"] as? [[String: Any]])
        var p0 = origParts[0]
        p0["ord"] = 0
        p0["partName"] = "Phần trống"
        var p1 = origParts[1]
        p1["ord"] = 1
        p1["partName"] = "Phần trắc nghiệm"
        var p2 = origParts[2]
        p2["ord"] = 2
        p2["partName"] = "Phần tự chọn"
        let p3SkippedWithScore: [String: Any] = [
            "partId": "part-skip-score",
            "partCode": "SKIP-SCORE",
            "partName": "Phần bỏ qua có điểm",
            "ord": 3,
            "state": "SKIPPED",
            "pendingReview": false,
            "score": 0.0,
            "maxScore": 20.0
        ]
        let p4SkippedNoScore: [String: Any] = [
            "partId": "part-skip-noscore",
            "partCode": "SKIP-NOSCORE",
            "partName": "Phần bỏ qua không điểm",
            "ord": 4,
            "state": "SKIPPED",
            "pendingReview": false
        ]
        root["parts"] = [p4SkippedNoScore, p2, p0, p3SkippedWithScore, p1]
        let mutatedReviewData = try JSONSerialization.data(withJSONObject: root)
        let resultData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "pending-review")

        let (repo, transport) = try makeStack(reviewData: mutatedReviewData, resultData: resultData)
        let vm = ReviewViewModel(
            attemptId: "att-parts",
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-parts")
        )
        await vm.loadNow()

        let model = try XCTUnwrap(vm.currentModel)
        XCTAssertEqual(
            model.parts.map(\.displayTitle),
            ["Phần trống", "Phần trắc nghiệm", "Phần tự chọn", "Phần bỏ qua có điểm", "Phần bỏ qua không điểm"]
        )
        XCTAssertEqual(vm.selectedPartIndex, 0)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/att-parts/review"), 1)

        let (window, _, sink, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 420, height: 900)
        )
        defer { window.isHidden = true }

        XCTAssertEqual(sink.nodes["review.parts.strip"]?.label, "Các phần của đề")
        XCTAssertEqual(sink.nodes["review.parts.tab.0"]?.isSelected, true)
        XCTAssertTrue(sink.nodes["review.parts.tab.3"]?.label.contains("Bị bỏ qua") == true)
        XCTAssertEqual(sink.nodes["review.emptyPart.title"]?.label, "Không có câu hỏi trong phần này")
        XCTAssertEqual(sink.nodes["review.emptyPart.description"]?.label, "Phần này hiện chưa có câu hỏi nào để xem lại.")

        XCTAssertTrue(sink.performAction(for: "review.parts.tab.1"))
        try await Task.sleep(for: .milliseconds(80))
        XCTAssertEqual(vm.selectedPartIndex, 1)
        XCTAssertFalse(vm.currentPartGroups.isEmpty)
        XCTAssertEqual(vm.activeGroupId, vm.currentPartGroups.first?.id)
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/me/attempts/att-parts/review"),
            1,
            "Switching parts must NOT trigger another network request"
        )

        XCTAssertTrue(sink.performAction(for: "review.parts.tab.2"))
        try await Task.sleep(for: .milliseconds(80))
        XCTAssertEqual(vm.selectedPartIndex, 2)
        XCTAssertEqual(vm.activeGroupId, vm.currentPartGroups.first?.id)

        XCTAssertTrue(sink.performAction(for: "review.parts.tab.3"))
        try await Task.sleep(for: .milliseconds(80))
        XCTAssertEqual(sink.nodes["review.skippedPart.title"]?.label, "Phần này bị bỏ qua")
        XCTAssertTrue(sink.nodes["review.skippedPart.description"]?.label.contains("0/20") == true)

        XCTAssertTrue(sink.performAction(for: "review.parts.tab.4"))
        try await Task.sleep(for: .milliseconds(80))
        XCTAssertEqual(sink.nodes["review.skippedPart.title"]?.label, "Phần này bị bỏ qua")
        XCTAssertEqual(
            sink.nodes["review.skippedPart.description"]?.label,
            ResultStrings.text("review.skippedPart.descriptionNoScore", locale: .vi)
        )

        let reopenedVM = ReviewViewModel(
            attemptId: "att-parts",
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-parts")
        )
        await reopenedVM.loadNow()
        XCTAssertEqual(reopenedVM.selectedPartIndex, 0)
    }

    func testSinglePartHidesPartTabsStrip() throws {
        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution")
        let model = ReviewMapper.map(payload)
        XCTAssertEqual(model.parts.count, 1)

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

        let (window, _, sink, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        defer { window.isHidden = true }

        XCTAssertNil(sink.nodes["review.parts.strip"])
    }
}
