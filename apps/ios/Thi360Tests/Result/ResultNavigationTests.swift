import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ResultNavigationTests: XCTestCase {
    func testOpenResultFromAttemptReplacesAttemptFlowAndBackReturnsToOriginRoot() {
        let router = AppRouter(initialDestination: .myExams)
        router.openPrepare(entitlementId: "ent-1")
        router.openAttemptAfterStart(attemptId: "att-100")
        XCTAssertEqual(router.presentedAttempt, AttemptRef(attemptId: "att-100", originDestination: .myExams))
        XCTAssertTrue(router.path(for: .myExams).isEmpty)

        router.openResultFromAttempt(attemptId: "att-100")
        XCTAssertNil(router.presentedAttempt)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-100")])

        router.back()
        XCTAssertTrue(router.path(for: .myExams).isEmpty, "Back from result opened via attempt must return to origin tab root")
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenResultFromMyExamsAndHistoryStaysInTabStackAndNavigationButtonsSwitchWithReload() {
        let router = AppRouter(initialDestination: .history)
        router.openResult(attemptId: "att-200")
        XCTAssertEqual(router.selectedDestination, .history)
        XCTAssertEqual(router.path(for: .history), [.result(attemptId: "att-200")])

        router.back()
        XCTAssertTrue(router.path(for: .history).isEmpty)

        router.openResult(attemptId: "att-200")
        let beforeMyExamsReload = router.reloadCount(for: .myExams)
        router.switchTab(.myExams, reload: true)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertTrue(router.path(for: .myExams).isEmpty)
        XCTAssertEqual(router.reloadCount(for: .myExams), beforeMyExamsReload + 1)

        router.openResult(attemptId: "att-300")
        XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-300")])
        let beforeHistoryReload = router.reloadCount(for: .history)
        router.switchTab(.history, reload: true)
        XCTAssertEqual(router.selectedDestination, .history)
        XCTAssertTrue(router.path(for: .history).isEmpty)
        XCTAssertEqual(router.reloadCount(for: .history), beforeHistoryReload + 1)
    }

    func testReviewOpensOnce() throws {
        let scored = try ResultTestFixtures.decodeResult(variant: "scored")
        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let vm = ResultViewModel(attemptId: scored.attemptId, repository: repo)
        vm.setStateForTesting(loadState: .loaded(value: scored, stale: false))

        let router = AppRouter(initialDestination: .history)
        router.openResult(attemptId: scored.attemptId)

        let (window, _, sink, _) = ResultViewTestHelper.host(
            NavigationStack {
                ResultView(
                    viewModel: vm,
                    locale: .vi,
                    onOpenReview: { router.openReview(attemptId: $0) },
                    onContinueAttempt: { router.openAttempt(attemptId: $0) },
                    onGoMyExams: { router.switchTab(.myExams, reload: true) },
                    onGoHistory: { router.switchTab(.history, reload: true) },
                    autoLoadOnAppear: false
                )
            }
        )
        defer { window.isHidden = true }

        XCTAssertTrue(sink.performAction(for: "result.actions.reviewButton"))
        XCTAssertTrue(sink.performAction(for: "result.actions.reviewButton"))

        XCTAssertEqual(
            router.path(for: .history),
            [.result(attemptId: scored.attemptId), .review(attemptId: scored.attemptId)],
            "Rapid double-tap on Review button must push exactly one review route"
        )
    }

    func testBackFromReview() async throws {
        let attemptId = "00000000-0000-7000-8000-000000000001"
        let firstData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "pending-review")
        let secondData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "scored")

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
            transport.enqueue(
                method: .get,
                path: "/api/v1/me/attempts/\(attemptId)/result",
                handlers: [
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: firstData) },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: secondData) }
                ]
            )
        }

        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let resultVM = ResultViewModel(attemptId: attemptId, repository: repo)
        await resultVM.loadResultNow()
        XCTAssertEqual(resultVM.currentResult?.state, .pendingReview)

        let router = AppRouter(initialDestination: .history)
        router.openResult(attemptId: attemptId)
        router.openReview(attemptId: attemptId)
        XCTAssertEqual(router.path(for: .history), [.result(attemptId: attemptId), .review(attemptId: attemptId)])

        let reviewVM = ReviewViewModel(
            attemptId: attemptId,
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: attemptId)
        )
        reviewVM.setFailedStateForTesting(.forbidden)

        let (window, _, sink, _) = ResultViewTestHelper.host(
            NavigationStack {
                ReviewView(
                    viewModel: reviewVM,
                    locale: .vi,
                    onBackToResult: { router.back() },
                    onGoMyExams: { router.switchTab(.myExams, reload: true) },
                    onGoHistory: { router.switchTab(.history, reload: true) },
                    autoLoadOnAppear: false
                )
            }
        )
        defer { window.isHidden = true }

        XCTAssertTrue(sink.performAction(for: "review.notIncluded.backToResult"))
        XCTAssertEqual(router.path(for: .history), [.result(attemptId: attemptId)])

        await resultVM.loadResultNow()
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 2)
        XCTAssertEqual(resultVM.currentResult?.state, .scored)
    }
}
