import Foundation
import HTTPTypes
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewScreenStatesTests: XCTestCase {
    private func makeStack(
        clock: FakeResultAppClock = FakeResultAppClock(),
        initialOnline: Bool = true,
        configure: (ContractFakeTransport) throws -> Void
    ) throws -> (ResultRepository, ContractFakeTransport, ManualNetworkPathObserver, ConnectivityMonitor) {
        let observer = ManualNetworkPathObserver()
        let connectivity = ConnectivityMonitor(initialIsOnline: initialOnline, observer: observer)
        let (_, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            configure: configure
        )
        try ResultTestFixtures.registerCatalogOptions(on: transport)
        let tokenStore = InMemoryTokenStore(initialTokens: AccountTestFactory.sampleTokens())
        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: transport,
            localeProvider: { await MainActor.run { preferences.locale } }
        )
        let catalogCache = CatalogOptionsCache(apiClient: apiClient, clock: clock)
        let repository = ResultRepository(
            api: OpenAPIResultAPI(apiClient: apiClient),
            catalogOptionsCache: catalogCache,
            clock: clock
        )
        return (repository, transport, observer, connectivity)
    }

    func testSingleReviewCallWithoutPartThenResultAndSixtySecondReuse() async throws {
        let clock = FakeResultAppClock()
        let reviewData = try ResultTestFixtures.loadData(operationId: Operations.getReview.id, status: 200, variant: "full-solution")
        let resultData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "scored")

        let (repo, transport, _, connectivity) = try makeStack(clock: clock) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-rev-1/review", statusCode: 200, data: reviewData)
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-rev-1/result", statusCode: 200, data: resultData)
        }

        let vm = ReviewViewModel(
            attemptId: "att-rev-1",
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-rev-1"),
            connectivityMonitor: connectivity
        )
        await vm.loadNow()

        let reviewRequests = transport.requests(method: .get, path: "/api/v1/me/attempts/att-rev-1/review")
        let resultRequests = transport.requests(method: .get, path: "/api/v1/me/attempts/att-rev-1/result")
        XCTAssertEqual(reviewRequests.count, 1)
        XCTAssertFalse(reviewRequests[0].path.contains("part="), "Review call must NOT include part query param")
        XCTAssertEqual(resultRequests.count, 1)

        let paths = transport.recordedRequests.map(\.path)
        let reviewIndex = try XCTUnwrap(paths.firstIndex(where: { $0.contains("/review") }))
        let resultIndex = try XCTUnwrap(paths.firstIndex(where: { $0.contains("/result") }))
        XCTAssertLessThan(reviewIndex, resultIndex, "Review must finish before requesting /result")

        clock.advance(by: 30)
        let vmWithinCache = ReviewViewModel(
            attemptId: "att-rev-1",
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-rev-1"),
            connectivityMonitor: connectivity
        )
        await vmWithinCache.loadNow()
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/att-rev-1/review"), 2)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/att-rev-1/result"), 1)

        clock.advance(by: 61)
        let vmAfterExpiry = ReviewViewModel(
            attemptId: "att-rev-1",
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-rev-1"),
            connectivityMonitor: connectivity
        )
        await vmAfterExpiry.loadNow()
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/att-rev-1/result"), 2)
    }

    func testLoadingSkeletonReadsReviewLoadingLabel() throws {
        let (repo, _, _, connectivity) = try makeStack { _ in }
        let vm = ReviewViewModel(
            attemptId: "att-load",
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-load"),
            connectivityMonitor: connectivity
        )
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

        XCTAssertEqual(sink.nodes["review.loading"]?.label, "Đang tải bài làm để xem lại")
    }

    func test403NotIncludedAnd409NotAvailableAnd404And5xxAndOfflineAutoRetry() async throws {
        let forbiddenData = try ResultTestFixtures.loadData(operationId: Operations.getReview.id, status: 403, variant: "review-not-included")
        let conflictData = try ResultTestFixtures.loadData(operationId: Operations.getReview.id, status: 409, variant: "review-not-available")
        let notFoundData = try ResultTestFixtures.loadData(operationId: Operations.getReview.id, status: 404, variant: "attempt-not-found")
        let okReviewData = try ResultTestFixtures.loadData(operationId: Operations.getReview.id, status: 200, variant: "full-solution")
        let okResultData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "scored")

        let (repo403, _, _, conn403) = try makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-403/review", statusCode: 403, data: forbiddenData)
        }
        let vm403 = ReviewViewModel(
            attemptId: "att-403",
            repository: repo403,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-403"),
            connectivityMonitor: conn403
        )
        await vm403.loadNow()
        XCTAssertEqual(vm403.loadState, .failed(kind: .forbidden, canRetry: false, retryAfter: nil))

        var backCount403 = 0
        let (win403, _, sink403, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm403,
                locale: .vi,
                onBackToResult: { backCount403 += 1 },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sink403.nodes["review.state.notIncluded.title"]?.label, "Bài thi này không có phần xem lại bài làm")
        XCTAssertTrue(sink403.nodes["review.state.notIncluded.description"]?.label.contains("màn hình") == true)
        XCTAssertEqual(sink403.nodes["review.notIncluded.backToResult"]?.label, "Về kết quả")
        XCTAssertTrue(sink403.performAction(for: "review.notIncluded.backToResult"))
        XCTAssertTrue(sink403.performAction(for: "review.notIncluded.backToResult"))
        XCTAssertEqual(backCount403, 1)
        win403.isHidden = true

        let (repo409, _, _, conn409) = try makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-409/review", statusCode: 409, data: conflictData)
        }
        let vm409 = ReviewViewModel(
            attemptId: "att-409",
            repository: repo409,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-409"),
            connectivityMonitor: conn409
        )
        await vm409.loadNow()
        XCTAssertEqual(vm409.loadState, .failed(kind: .conflict(code: "REVIEW_NOT_AVAILABLE"), canRetry: false, retryAfter: nil))

        let (win409, _, sink409, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm409,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sink409.nodes["review.state.notAvailable.title"]?.label, "Chưa thể xem lại bài làm")
        XCTAssertEqual(sink409.nodes["review.notAvailable.backToResult"]?.label, "Về kết quả")
        win409.isHidden = true

        let (repo404, _, _, conn404) = try makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-404/review", statusCode: 404, data: notFoundData)
        }
        let vm404 = ReviewViewModel(
            attemptId: "att-404",
            repository: repo404,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-404"),
            connectivityMonitor: conn404
        )
        await vm404.loadNow()
        XCTAssertEqual(vm404.loadState, .failed(kind: .notFound, canRetry: false, retryAfter: nil))

        let (win404, _, sink404, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm404,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sink404.nodes["review.state.notFound.eyebrow"]?.label, "MÃ LỖI 404")
        XCTAssertEqual(sink404.nodes["review.state.notFound.title"]?.label, "Không tìm thấy bài làm")
        XCTAssertEqual(sink404.nodes["review.notFound.toHistory"]?.label, "Lịch sử luyện thi")
        XCTAssertEqual(sink404.nodes["review.notFound.toHome"]?.label, "Bài thi của tôi")
        win404.isHidden = true

        let (repo500, transport500, _, conn500) = try makeStack { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/attempts/att-500/review", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 500, data: Data(#"{"code":"INTERNAL_ERROR"}"#.utf8)) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: okReviewData) }
            ])
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-500/result", statusCode: 200, data: okResultData)
        }
        let vm500 = ReviewViewModel(
            attemptId: "att-500",
            repository: repo500,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-500"),
            connectivityMonitor: conn500
        )
        await vm500.loadNow()
        XCTAssertEqual(vm500.loadState, .failed(kind: .server, canRetry: true, retryAfter: nil))

        let (win500, _, sink500, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm500,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sink500.nodes["review.state.error.title"]?.label, "Không tải được bài làm")
        XCTAssertTrue(sink500.performAction(for: "review.error.retryButton"))
        for _ in 0..<25 where vm500.currentModel == nil {
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertNotNil(vm500.currentModel)
        XCTAssertEqual(transport500.callCount(method: .get, path: "/api/v1/me/attempts/att-500/review"), 2)
        win500.isHidden = true

        let (repoOffline, transportOffline, observer, connOffline) = try makeStack(initialOnline: false) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-off/review", statusCode: 200, data: okReviewData)
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-off/result", statusCode: 200, data: okResultData)
        }
        let vmOffline = ReviewViewModel(
            attemptId: "att-off",
            repository: repoOffline,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-off"),
            connectivityMonitor: connOffline
        )
        await vmOffline.loadNow()
        XCTAssertEqual(vmOffline.loadState, .failed(kind: .offline, canRetry: true, retryAfter: nil))
        XCTAssertEqual(transportOffline.callCount(method: .get, path: "/api/v1/me/attempts/att-off/review"), 0)

        observer.emit(online: true)
        for _ in 0..<25 where vmOffline.currentModel == nil {
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertNotNil(vmOffline.currentModel)
        XCTAssertEqual(transportOffline.callCount(method: .get, path: "/api/v1/me/attempts/att-off/review"), 1)
    }

    func testResultFailureStillRendersQuestionsWithTitleOnlyHeader() async throws {
        let okReviewData = try ResultTestFixtures.loadData(operationId: Operations.getReview.id, status: 200, variant: "full-solution")
        let (repo, _, _, connectivity) = try makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/att-rf/review", statusCode: 200, data: okReviewData)
            transport.registerJSON(
                method: .get,
                path: "/api/v1/me/attempts/att-rf/result",
                statusCode: 500,
                data: Data(#"{"code":"INTERNAL_ERROR"}"#.utf8)
            )
        }

        let vm = ReviewViewModel(
            attemptId: "att-rf",
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: "att-rf"),
            connectivityMonitor: connectivity
        )
        await vm.loadNow()

        let model = try XCTUnwrap(vm.currentModel)
        XCTAssertNil(model.resultSummary)
        XCTAssertFalse(model.groups.isEmpty)

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

        XCTAssertEqual(sink.nodes["review.header.title"]?.label, "Xem lại bài làm")
        XCTAssertNil(sink.nodes["review.header.subtitle"])
        XCTAssertNotNil(sink.nodes["review.group.\(model.groups[0].id)"])
    }
}
