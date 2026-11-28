import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PlayerRouteTests: XCTestCase {
    func testWaitingHosted() async throws {
        let harness = try PlayerTestHarness(
            initialStateVariant: "starting",
            mutateInitialState: { json in
                json["examName"] = "HSA đầy đủ 2026"
            }
        )
        defer { harness.dispose() }
        await harness.settle()

        let uiStarting = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(uiStarting.surface, .waiting)
        XCTAssertEqual(uiStarting.waitingInitialState, .STARTING)
        XCTAssertEqual(uiStarting.examTitle, "HSA đầy đủ 2026")

        var startCalls = 0
        let fakePrepareAPI = WaitingStubPrepareAPI(
            onStart: { startCalls += 1 },
            pollSequence: [.starting, .inProgress]
        )
        let testClock = WaitingTestClock()
        var readyTriggered = false
        var closedTriggered = false

        let (win, vc) = PlayerViewTestHost.host(
            PlayerView(
                attemptId: harness.attemptId,
                engineState: harness.engine.state,
                locale: .vi,
                prepareAPI: fakePrepareAPI,
                startContextStore: StartContextStore(store: harness.accountStore),
                waitingClock: testClock,
                onWaitingReady: { readyTriggered = true },
                onCloseAttempt: { closedTriggered = true }
            )
            .environment(\.scenePhase, .active)
        )
        defer { win.isHidden = true }

        let waitingNode = PlayerViewTestHost.findNode(id: "player.waiting", in: vc.view, window: win)
        XCTAssertNotNil(waitingNode)
        XCTAssertEqual(waitingNode?.label, uiStarting.examTitle)
        XCTAssertEqual(startCalls, 0, "Opening STARTING attempt must never issue POST /api/v1/me/attempts")

        try? await Task.sleep(for: .milliseconds(60))
        await testClock.advance(by: 2.0)
        try? await Task.sleep(for: .milliseconds(60))
        await testClock.advance(by: 2.0)
        try? await Task.sleep(for: .milliseconds(60))
        XCTAssertTrue(readyTriggered)

        try harness.server.setState(variant: "in-progress-shared")
        harness.engine.refresh()
        await harness.settle()
        let uiActive = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(uiActive.surface, .active)

        let abortedHarness = try PlayerTestHarness(
            initialStateVariant: "aborted",
            mutateInitialState: { json in
                json["examName"] = "HSA đầy đủ 2026"
            }
        )
        defer { abortedHarness.dispose() }
        await abortedHarness.settle()
        let uiAborted = PlayerUI.make(state: abortedHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(uiAborted.surface, .waiting, "ABORTED must map to embedded WaitingView, not Closed")
        XCTAssertEqual(uiAborted.waitingInitialState, .ABORTED)

        let (abortWin, abortVC) = PlayerViewTestHost.host(
            PlayerView(
                attemptId: abortedHarness.attemptId,
                engineState: abortedHarness.engine.state,
                locale: .vi,
                prepareAPI: fakePrepareAPI,
                startContextStore: StartContextStore(store: abortedHarness.accountStore),
                waitingClock: testClock,
                onCloseAttempt: { closedTriggered = true }
            )
        )
        defer { abortWin.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.waiting", in: abortVC.view, window: abortWin))
        let (_, abortPNG) = try CatalogViewInspector.renderPNG(window: abortWin, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(abortPNG.count, 2048)
        _ = closedTriggered
    }
}

@MainActor
final class PlayerWaitingRetryTests: XCTestCase {
    func testEmbeddedWaitingRetryReplacesAttemptAndReleasesOldEngineWithZeroOldAttemptCalls() async throws {
        let oldAttemptId = "00000000-0000-7000-8000-000000000091"
        let newAttemptId = "00000000-0000-7000-8000-000000000092"

        let harness = try PlayerTestHarness(attemptId: oldAttemptId, initialStateVariant: "aborted")
        defer { harness.dispose() }
        await harness.settle()

        let startStore = StartContextStore(store: harness.accountStore)
        _ = try await startStore.save(
            StartContext(
                entitlementId: "ent-retry-1",
                examTitle: harness.engine.state.attempt?.examName ?? "HSA",
                examKindLabel: "HSA"
            ),
            attemptId: oldAttemptId
        )

        let registry = AttemptEngineRegistry()
        _ = registry.engine(for: oldAttemptId, accountId: harness.accountId) { harness.engine }
        XCTAssertNotNil(registry.existingEngine(for: oldAttemptId))

        let router = AppRouter(initialDestination: .myExams)
        router.openAttempt(attemptId: oldAttemptId)

        let prepareAPI = WaitingStubPrepareAPI(
            restartResult: StartAttemptResult(
                attemptId: newAttemptId,
                state: .inProgress,
                resumed: false,
                retryable: true
            )
        )

        var controllerRef: AttemptWaitingController?
        let (win, _) = PlayerViewTestHost.host(
            AttemptWaitingView(
                attemptId: oldAttemptId,
                examTitle: harness.engine.state.attempt?.examName ?? "HSA",
                context: nil,
                origin: .player,
                onReady: { _ in },
                onRestarted: { replacedId in
                    registry.release(oldAttemptId)
                    router.replaceAttempt(attemptId: replacedId)
                },
                onOpenPrepare: { _ in },
                onMyExams: {},
                initialPhase: .failed(abortReason: harness.engine.state.attempt?.abortReason?.rawValue),
                locale: .vi,
                api: prepareAPI,
                startContextStore: startStore,
                onControllerChanged: { controllerRef = $0 }
            )
        )
        defer { win.isHidden = true }

        let controller = try XCTUnwrap(controllerRef)
        let loadedContext = await harness.clock.waitUntil {
            controller.resolvedContext != nil
        }
        XCTAssertTrue(loadedContext)
        let callsBeforeRetry = harness.server.allCalls.count
        controller.retry()
        let replaced = await harness.clock.waitUntil {
            router.presentedAttempt?.attemptId == newAttemptId
        }
        XCTAssertTrue(replaced)

        XCTAssertEqual(router.presentedAttempt?.attemptId, newAttemptId)
        XCTAssertNil(registry.existingEngine(for: oldAttemptId), "Old attempt engine must be released on replaceAttempt")

        router.closeAttempt()
        XCTAssertNil(router.presentedAttempt)

        await harness.clock.advance(by: .seconds(10))
        XCTAssertEqual(harness.server.allCalls.count, callsBeforeRetry, "No further calls may be issued for old attemptId")
    }
}

private final class WaitingStubPrepareAPI: PrepareAPIClientProtocol, @unchecked Sendable {
    private let lock = NSLock()
    private let onStart: (() -> Void)?
    private var pollQueue: [AttemptOpenState]
    private let restartResult: StartAttemptResult

    init(
        onStart: (() -> Void)? = nil,
        pollSequence: [AttemptOpenState] = [.starting],
        restartResult: StartAttemptResult = StartAttemptResult(
            attemptId: "00000000-0000-7000-8000-000000000099",
            state: .inProgress,
            resumed: false,
            retryable: true
        )
    ) {
        self.onStart = onStart
        self.pollQueue = pollSequence
        self.restartResult = restartResult
    }

    func prepare(entitlementId _: String) async throws -> AttemptPrepareResponse {
        try PlayerTestFixtures.decodePrepare(variant: "shared")
    }

    func startAttempt(
        entitlementId _: String,
        preChoice _: PreparePreChoiceInput?,
        idempotencyKey _: String
    ) async throws -> StartAttemptResult {
        onStart?()
        return restartResult
    }

    func getAttemptState(attemptId: String) async throws -> AttemptPollResult {
        let nextState: AttemptOpenState = lock.withLock {
            if pollQueue.count > 1 {
                return pollQueue.removeFirst()
            }
            return pollQueue.first ?? .starting
        }
        return AttemptPollResult(attemptId: attemptId, state: nextState)
    }
}

private final class WaitingTestClock: PrepareClock, @unchecked Sendable {
    private let lock = NSLock()
    private var currentSeconds: TimeInterval = 0
    private var waiters: [(deadline: TimeInterval, continuation: CheckedContinuation<Void, Error>)] = []

    var nowSeconds: TimeInterval {
        lock.withLock { currentSeconds }
    }

    func sleep(seconds: TimeInterval) async throws {
        try await withCheckedThrowingContinuation { cont in
            let shouldResume = lock.withLock { () -> Bool in
                if seconds <= 0 { return true }
                waiters.append((currentSeconds + seconds, cont))
                return false
            }
            if shouldResume {
                cont.resume()
            }
        }
    }

    func advance(by delta: TimeInterval) async {
        let ready: [CheckedContinuation<Void, Error>] = lock.withLock {
            currentSeconds += delta
            var due: [CheckedContinuation<Void, Error>] = []
            var remaining: [(TimeInterval, CheckedContinuation<Void, Error>)] = []
            for entry in waiters {
                if entry.deadline <= currentSeconds {
                    due.append(entry.continuation)
                } else {
                    remaining.append(entry)
                }
            }
            waiters = remaining
            return due
        }
        for cont in ready {
            cont.resume()
        }
        await Task.yield()
    }
}
