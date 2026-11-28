import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ResultScreenStatesTests: XCTestCase {
    private let attemptId = "00000000-0000-7000-8000-000000000001"

    func testCatalogFailureShowsRawCodes() async throws {
        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerEmpty(method: .get, path: "/api/v1/catalog/options", statusCode: 500)
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/attempts/\(self.attemptId)/result",
                statusCode: 200,
                relativeFixturePath: ResultTestFixtures.loadRelativePath(
                    operationId: Operations.getResult.id,
                    status: 200,
                    variant: "scored"
                )
            )
        }

        let optionsCache = CatalogOptionsCache(apiClient: sessionStore.apiClient)
        let repo = ResultRepository(apiClient: sessionStore.apiClient, catalogOptionsCache: optionsCache)
        let vm = ResultViewModel(attemptId: attemptId, repository: repo)
        await vm.loadResultNow()

        let (window, _, sink, _) = ResultViewTestHelper.host(
            NavigationStack {
                ResultView(
                    viewModel: vm,
                    locale: .vi,
                    onOpenReview: { _ in },
                    onContinueAttempt: { _ in },
                    onGoMyExams: {},
                    onGoHistory: {},
                    autoLoadOnAppear: false
                )
            }
        )
        defer { window.isHidden = true }

        XCTAssertEqual(sink.nodes["result.header.examKind"]?.label, "HSA")
        XCTAssertEqual(sink.nodes["result.total.scoreValue"]?.label, "1/5")
    }

    func testLoading404409AndServerErrorStates() async throws {
        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
        }
        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let vm = ResultViewModel(attemptId: attemptId, repository: repo)

        let (loadingWin, _, loadingSink, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(loadingSink.nodes["result.loading"]?.label, "Đang tải kết quả lượt thi")
        loadingWin.isHidden = true

        try transport.registerFixture(
            method: .get,
            path: "/api/v1/me/attempts/\(attemptId)/result",
            statusCode: 404,
            relativeFixturePath: ResultTestFixtures.loadRelativePath(
                operationId: Operations.getResult.id,
                status: 404,
                variant: "attempt-not-found"
            )
        )
        await vm.loadResultNow()
        var historyTapped = 0
        var myExamsTapped = 0
        let (win404, _, sink404, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: { myExamsTapped += 1 },
                onGoHistory: { historyTapped += 1 },
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sink404.nodes["result.notFound.eyebrow"]?.label, "MÃ LỖI 404")
        XCTAssertEqual(sink404.nodes["result.notFound.title"]?.label, "Không tìm thấy kết quả lượt thi")
        XCTAssertTrue(sink404.performAction(for: "result.notFound.toHistory"))
        XCTAssertEqual(historyTapped, 1)
        vm.resetNavigationGuard()
        XCTAssertTrue(sink404.performAction(for: "result.notFound.toHome"))
        XCTAssertEqual(myExamsTapped, 1)
        win404.isHidden = true

        try transport.registerFixture(
            method: .get,
            path: "/api/v1/me/attempts/\(attemptId)/result",
            statusCode: 409,
            relativeFixturePath: ResultTestFixtures.loadRelativePath(
                operationId: Operations.getResult.id,
                status: 409,
                variant: "attempt-not-finished"
            )
        )
        await vm.loadResultNow()
        var continuedAttemptId: String?
        let (win409, _, sink409, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { continuedAttemptId = $0 },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sink409.nodes["result.notFinished.title"]?.label, "Lượt thi chưa kết thúc")
        XCTAssertTrue(sink409.performAction(for: "result.notFinished.continueAttempt"))
        XCTAssertEqual(continuedAttemptId, attemptId)
        win409.isHidden = true

        transport.registerJSON(
            method: .get,
            path: "/api/v1/me/attempts/\(attemptId)/result",
            statusCode: 500,
            data: Data("{corrupt".utf8)
        )
        await vm.loadResultNow()
        let countBeforeRetry = transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result")
        let (win500, _, sink500, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sink500.nodes["result.error.title"]?.label, "Không tải được kết quả lượt thi")
        XCTAssertTrue(sink500.performAction(for: "result.error.retryButton"))
        try await Task.sleep(for: .milliseconds(80))
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"),
            countBeforeRetry + 1
        )
        win500.isHidden = true
    }

    func testOfflineAutoRetriesWhenConnectivityReturns() async throws {
        let observer = ManualNetworkPathObserver()
        let monitor = ConnectivityMonitor(initialIsOnline: false, observer: observer, autoStart: true)

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/attempts/\(self.attemptId)/result",
                statusCode: 200,
                relativeFixturePath: ResultTestFixtures.loadRelativePath(
                    operationId: Operations.getResult.id,
                    status: 200,
                    variant: "scored"
                )
            )
        }

        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let vm = ResultViewModel(attemptId: attemptId, repository: repo, connectivityMonitor: monitor)
        await vm.loadResultNow()

        XCTAssertEqual(vm.loadState, .failed(kind: .offline, canRetry: true, retryAfter: nil))
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 0)

        observer.emit(online: true)
        try await Task.sleep(for: .milliseconds(120))
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 1)
        XCTAssertEqual(vm.currentResult?.state, .scored)
    }

    func testStaleKeepsOldResult() async throws {
        let scoredData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "scored")
        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
            transport.enqueue(
                method: .get,
                path: "/api/v1/me/attempts/\(self.attemptId)/result",
                handlers: [
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: scoredData) },
                    { _ in ContractFakeTransport.emptyResponse(statusCode: 503) },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: scoredData) }
                ]
            )
        }

        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let vm = ResultViewModel(attemptId: attemptId, repository: repo)
        await vm.loadResultNow()
        XCTAssertEqual(vm.currentResult?.displayScore, 1.0)

        let (freshWindow, _, _, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        let (_, freshPNG) = try CatalogViewInspector.renderPNG(window: freshWindow, size: CGSize(width: 390, height: 844))
        freshWindow.isHidden = true

        await vm.loadResultNow()
        guard case let .loaded(value, stale) = vm.loadState else {
            XCTFail("Expected stale loaded state")
            return
        }
        XCTAssertTrue(stale)
        XCTAssertEqual(value.displayScore, 1.0)

        let (window, _, sink, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        defer { window.isHidden = true }

        XCTAssertEqual(sink.nodes["result.total.scoreValue"]?.label, "1/5")
        XCTAssertEqual(ResultStrings.text("result.errorTitle", locale: .vi), "Không tải được kết quả lượt thi")
        let (_, stalePNG) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertNotEqual(freshPNG, stalePNG, "Stale state must visibly render StaleBanner above preserved result")

        vm.retry()
        for _ in 0..<25 {
            if case .loaded(_, false) = vm.loadState { break }
            try await Task.sleep(for: .milliseconds(15))
        }
        if case let .loaded(_, staleAfterRetry) = vm.loadState {
            XCTAssertFalse(staleAfterRetry, "Retrying stale result must clear stale banner on success")
        } else {
            XCTFail("Expected non-stale loaded state after retry")
        }
    }

    func testReopenShowsNewScore() async throws {
        let pendingData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "pending-review")
        let scoredData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "scored")
        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
            transport.enqueue(
                method: .get,
                path: "/api/v1/me/attempts/\(self.attemptId)/result",
                handlers: [
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: pendingData) },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: scoredData) }
                ]
            )
        }

        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let vm = ResultViewModel(attemptId: attemptId, repository: repo)
        await vm.loadResultNow()
        XCTAssertEqual(vm.currentResult?.state, .pendingReview)

        try await Task.sleep(for: .milliseconds(80))
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"),
            1,
            "PENDING_REVIEW screen must not poll while staying open"
        )
        XCTAssertEqual(vm.currentResult?.state, .pendingReview)

        await vm.loadResultNow()
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 2)
        XCTAssertEqual(vm.currentResult?.state, .scored)
        XCTAssertEqual(vm.currentResult?.displayScore, 1.0)
    }

    func testGradingPolls() async throws {
        let gradingData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "grading")
        let scoredData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "scored")
        let clock = FakePollClock()
        let poller = GradingPoller(clock: clock, interval: .seconds(3), window: .seconds(120))

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
            transport.enqueue(
                method: .get,
                path: "/api/v1/me/attempts/\(self.attemptId)/result",
                handlers: [
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: gradingData) },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: gradingData) },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: scoredData) }
                ]
            )
        }

        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let vm = ResultViewModel(attemptId: attemptId, repository: repo, poller: poller)
        await vm.loadResultNow()
        await clock.waitForWaiter()

        XCTAssertEqual(vm.currentResult?.state, .grading)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 1)

        await clock.advance(by: .seconds(3))
        await clock.waitForWaiter()
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 2)
        XCTAssertEqual(vm.currentResult?.state, .grading)

        await clock.advance(by: .seconds(3))
        for _ in 0..<25 where vm.currentResult?.state != .scored {
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 3)
        XCTAssertEqual(vm.currentResult?.state, .scored)

        await clock.advance(by: .seconds(12))
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 3)
    }

    func testReloadSendsOnce() async throws {
        let gradingData = try ResultTestFixtures.loadData(operationId: Operations.getResult.id, status: 200, variant: "grading")
        let clock = FakePollClock()
        let poller = GradingPoller(clock: clock, interval: .seconds(3), window: .seconds(120))
        let releaseGate = AsyncGate()

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
            transport.register(method: .get, path: "/api/v1/me/attempts/\(self.attemptId)/result") { _ in
                await releaseGate.wait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: gradingData)
            }
        }

        let repo = ResultRepository(apiClient: sessionStore.apiClient)
        let vm = ResultViewModel(attemptId: attemptId, repository: repo, poller: poller)
        let grading = try ResultTestFixtures.decodeResult(variant: "grading")
        vm.setStateForTesting(loadState: .loaded(value: grading, stale: false), isGradingTimedOut: true)

        vm.reloadFromGradingTimeout()
        XCTAssertTrue(vm.isReloadingFromTimeout)
        vm.reloadFromGradingTimeout()

        try await Task.sleep(for: .milliseconds(50))
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"),
            1,
            "Second tap on Reload while first call is in flight must not send another request"
        )

        await releaseGate.open()
        try await Task.sleep(for: .milliseconds(80))
        XCTAssertFalse(vm.isReloadingFromTimeout)
        XCTAssertFalse(vm.isGradingTimedOut)
        await poller.stop()
    }
}

private actor AsyncGate {
    private var isOpen = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        if isOpen { return }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    func open() {
        isOpen = true
        let pending = waiters
        waiters.removeAll()
        for waiter in pending {
            waiter.resume()
        }
    }
}
