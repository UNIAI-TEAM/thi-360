import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

@MainActor
final class WaitingRetryTests: XCTestCase {
    func testPreChoiceResolutionPriorityOrderCoversAllFiveCases() throws {
        let noPreChoice = try PrepareTestFixtures.loadPrepareResponse(variant: "shared")
        let singleOptionNoSubjects = try PrepareTestFixtures.loadPrepareResponse(variant: "pre-choice")
        let (_, twoOptions) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: nil,
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        let (_, singleOptionRequiringSubjects) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "SCIENCE",
            chooseSubjects: 2,
            includeSecondFixedOption: false
        )

        let inMem = PreparePreChoiceInput(optionCode: "MEM", subjectCodes: ["LY", "HOA"])
        let saved = PreparePreChoiceInput(optionCode: "SAVED", subjectCodes: ["SINH", "SU"])

        XCTAssertEqual(
            WaitingRetry.resolvePreparePreChoice(
                inMemoryPreChoice: inMem,
                savedPreChoice: saved,
                prepareResponse: twoOptions
            ),
            .direct(inMem),
            "1. In-memory preChoice wins over saved context and prepare response"
        )

        XCTAssertEqual(
            WaitingRetry.resolvePreparePreChoice(
                inMemoryPreChoice: nil,
                savedPreChoice: saved,
                prepareResponse: twoOptions
            ),
            .direct(saved),
            "2. Saved StartContext preChoice wins when in-memory is absent"
        )

        XCTAssertEqual(
            WaitingRetry.resolvePreparePreChoice(
                inMemoryPreChoice: nil,
                savedPreChoice: nil,
                prepareResponse: noPreChoice
            ),
            .direct(nil),
            "3. Exam without preChoice retries directly with nil preChoice"
        )

        XCTAssertEqual(
            WaitingRetry.resolvePreparePreChoice(
                inMemoryPreChoice: nil,
                savedPreChoice: nil,
                prepareResponse: singleOptionNoSubjects
            ),
            .direct(PreparePreChoiceInput(optionCode: "CS", subjectCodes: nil)),
            "4. Single option requiring 0 subjects infers that option directly"
        )

        XCTAssertEqual(
            WaitingRetry.resolvePreparePreChoice(
                inMemoryPreChoice: nil,
                savedPreChoice: nil,
                prepareResponse: twoOptions
            ),
            .mustReturnToPrepare,
            "5a. Multiple options without memory/saved context must return to Prepare"
        )

        XCTAssertEqual(
            WaitingRetry.resolvePreparePreChoice(
                inMemoryPreChoice: nil,
                savedPreChoice: nil,
                prepareResponse: singleOptionRequiringSubjects
            ),
            .mustReturnToPrepare,
            "5b. Single option requiring subjects without memory/saved context must return to Prepare"
        )
    }

    func testPrepareRetryUsesFreshIdempotencyKeyMovesStartContextAndHandlesUnresolvableReload() async throws {
        let (twoOptionSubjectData, _) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "SCIENCE",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        let abortedStart = try PrepareTestFixtures.makeStartData(
            status: 200,
            variant: "aborted",
            attemptId: "att-old-1"
        )
        let restartedStarting = try PrepareTestFixtures.makeStartData(
            status: 202,
            variant: "starting",
            attemptId: "att-new-2"
        )
        let startStep = LockedStep()
        let clock = VirtualPrepareClock()

        let stack = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(
                method: .get,
                path: "/api/v1/me/entitlements/ent-retry/prepare",
                data: twoOptionSubjectData
            )
            transport.register(method: .post, path: "/api/v1/me/attempts") { _ in
                let step = startStep.next()
                var headers = HTTPFields()
                headers[.contentType] = "application/json"
                if step == 1 {
                    return (HTTPResponse(status: .ok, headerFields: headers), HTTPBody(abortedStart))
                }
                return (HTTPResponse(status: .accepted, headerFields: headers), HTTPBody(restartedStarting))
            }
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let vm = PrepareViewModel(
            entitlementId: "ent-retry",
            api: stack.api,
            startContextStore: stack.startContextStore,
            clock: clock,
            localeProvider: { .vi }
        )
        defer { vm.clear() }

        await vm.loadIfNeeded()
        vm.toggleSubject("LY")
        vm.toggleSubject("HOA")
        await vm.startAttemptAndWait()

        XCTAssertEqual(vm.state.waiting, .failed(abortReason: "GENERATION_FAILED"))
        XCTAssertEqual(vm.state.waitingAttemptId, "att-old-1")
        let savedOld = await stack.startContextStore.read(attemptId: "att-old-1")
        XCTAssertNotNil(savedOld)

        await vm.retryFromWaitingAndWait()

        XCTAssertEqual(vm.state.waitingAttemptId, "att-new-2")
        if case .polling = vm.state.waiting {} else {
            XCTFail("Expected polling phase after retry returned 202 STARTING, got \(String(describing: vm.state.waiting))")
        }

        let startRequests = stack.transport.requests(method: .post, path: "/api/v1/me/attempts")
        XCTAssertEqual(startRequests.count, 2)
        let key1 = startRequests[0].headers[HTTPField.Name("Idempotency-Key")!]
        let key2 = startRequests[1].headers[HTTPField.Name("Idempotency-Key")!]
        XCTAssertNotNil(key1)
        XCTAssertNotNil(key2)
        XCTAssertNotEqual(key1, key2, "Retry after ABORTED must use a new Idempotency-Key per B1.13")

        let oldCtxAfterMove = await stack.startContextStore.read(attemptId: "att-old-1")
        let newCtxAfterMove = await stack.startContextStore.read(attemptId: "att-new-2")
        XCTAssertNil(oldCtxAfterMove, "Old attempt StartContext must be deleted after move")
        XCTAssertEqual(newCtxAfterMove?.preChoice, PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"]))

        // Plan A6 derivation: sets `openAttempt` to `STARTING` on mutated two-option `pre-choice` fixture to test unresolvable retry reload.
        var openStartingWithTwoOptions = try JSONSerialization.jsonObject(
            with: try PrepareTestFixtures.makeSubjectChoicePrepareData(
                preselectedCode: nil,
                chooseSubjects: 2,
                includeSecondFixedOption: true
            ).0
        ) as! [String: Any]
        openStartingWithTwoOptions["openAttempt"] = [
            "attemptId": "att-unresolvable",
            "state": "STARTING"
        ]
        let unresolvableData = try PrepareTestFixtures.encodeJSON(openStartingWithTwoOptions)
        let abortedPoll = try PrepareTestFixtures.makeAttemptStateData(
            variant: "aborted",
            attemptId: "att-unresolvable"
        )
        let prepareCalls = LockedStep()
        let stack2 = try PrepareTestStackFactory.makeStack { transport in
            transport.register(method: .get, path: "/api/v1/me/entitlements/ent-unres/prepare") { _ in
                _ = prepareCalls.next()
                var headers = HTTPFields()
                headers[.contentType] = "application/json"
                return (HTTPResponse(status: .ok, headerFields: headers), HTTPBody(unresolvableData))
            }
            transport.registerJSON(
                method: .get,
                path: "/api/v1/me/attempts/att-unresolvable",
                data: abortedPoll
            )
        }
        defer { PrepareTestStackFactory.cleanup(stack2) }

        let clock2 = VirtualPrepareClock()
        let vm2 = PrepareViewModel(
            entitlementId: "ent-unres",
            api: stack2.api,
            startContextStore: stack2.startContextStore,
            clock: clock2
        )
        defer { vm2.clear() }

        await vm2.loadIfNeeded()
        await clock2.advance(by: 1.0)
        XCTAssertEqual(vm2.state.waiting, .failed(abortReason: "STALE_STARTING"))

        openStartingWithTwoOptions["openAttempt"] = NSNull()
        let reloadedData = try PrepareTestFixtures.encodeJSON(openStartingWithTwoOptions)
        stack2.transport.registerJSON(
            method: .get,
            path: "/api/v1/me/entitlements/ent-unres/prepare",
            data: reloadedData
        )

        await vm2.retryFromWaitingAndWait()
        XCTAssertNil(vm2.state.waiting, "Unresolvable retry must return to Prepare form")
        XCTAssertEqual(vm2.state.gate, .needOption)
        XCTAssertEqual(
            stack2.transport.requests(method: .post, path: "/api/v1/me/attempts").count,
            0,
            "Unresolvable retry must NOT call POST /api/v1/me/attempts"
        )
    }

    func testPlayerOriginRetryMissingContextGoesToMyExamsAndInvalidChoiceOpensPrepareAndStopsOldPoller() async throws {
        let choiceNotAllowedData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.start.id,
            status: 422,
            variant: "choice-not-allowed"
        )
        let subjectsInvalidData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.start.id,
            status: 422,
            variant: "subjects-invalid"
        )
        let noAttemptsLeftData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.start.id,
            status: 409,
            variant: "no-attempts-left"
        )
        let restartedData = try PrepareTestFixtures.makeStartData(
            status: 202,
            variant: "starting",
            attemptId: "att-player-new"
        )

        let stack = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(
                method: .post,
                path: "/api/v1/me/attempts",
                statusCode: 422,
                data: choiceNotAllowedData
            )
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let retry = WaitingRetry(
            startCommand: StartCommand(api: stack.api),
            startContextStore: stack.startContextStore
        )

        let noContextOutcome = await retry.retry(attemptId: "att-missing", origin: .player)
        XCTAssertEqual(noContextOutcome, .goToMyExams, "Player origin without StartContext must go to my-exams")

        let ctx = StartContext(
            entitlementId: "ent-player-1",
            preChoice: PreparePreChoiceInput(optionCode: "OLD"),
            examTitle: "Đề thi mẫu",
            examKindLabel: "HSA"
        )
        try await stack.startContextStore.save(ctx, attemptId: "att-player-1")

        let choiceNotAllowedOutcome = await retry.retry(attemptId: "att-player-1", origin: .player)
        XCTAssertEqual(choiceNotAllowedOutcome, .openPrepare(entitlementId: "ent-player-1"))

        stack.transport.registerJSON(
            method: .post,
            path: "/api/v1/me/attempts",
            statusCode: 422,
            data: subjectsInvalidData
        )
        let subjectsInvalidOutcome = await retry.retry(attemptId: "att-player-1", origin: .player)
        XCTAssertEqual(subjectsInvalidOutcome, .openPrepare(entitlementId: "ent-player-1"))

        stack.transport.registerJSON(
            method: .post,
            path: "/api/v1/me/attempts",
            statusCode: 409,
            data: noAttemptsLeftData
        )
        let noAttemptsOutcome = await retry.retry(attemptId: "att-player-1", origin: .player)
        XCTAssertEqual(noAttemptsOutcome, .refused(.noAttemptsLeft))

        stack.transport.registerJSON(
            method: .post,
            path: "/api/v1/me/attempts",
            statusCode: 202,
            data: restartedData
        )
        let startingPoll = try PrepareTestFixtures.makeAttemptStateData(variant: "starting", attemptId: "att-player-1")
        stack.transport.registerJSON(
            method: .get,
            path: "/api/v1/me/attempts/att-player-1",
            data: startingPoll
        )

        let clock = VirtualPrepareClock()
        var restartedIds: [String] = []
        let controller = AttemptWaitingController(
            attemptId: "att-player-1",
            context: ctx,
            origin: .player,
            initialPhase: .polling(elapsed: 0),
            api: stack.api,
            startContextStore: stack.startContextStore,
            clock: clock,
            onReady: { _ in },
            onRestarted: { restartedIds.append($0) },
            onOpenPrepare: { _ in },
            onMyExams: {}
        )
        defer { controller.stop() }

        controller.startIfNeeded(isForeground: true)
        await clock.advance(by: 1.0)
        let pollsBeforeRestart = stack.transport.requests(method: .get, path: "/api/v1/me/attempts/att-player-1").count
        XCTAssertEqual(pollsBeforeRestart, 1)

        controller.retry()
        let restarted = await clock.waitUntil { !restartedIds.isEmpty }
        XCTAssertTrue(restarted)
        XCTAssertEqual(restartedIds, ["att-player-new"])

        await clock.advance(by: 10.0)
        let pollsAfterRestart = stack.transport.requests(method: .get, path: "/api/v1/me/attempts/att-player-1").count
        XCTAssertEqual(pollsAfterRestart, pollsBeforeRestart, "Old attempt poller MUST stop before calling onRestarted")

        let movedContext = await stack.startContextStore.read(attemptId: "att-player-new")
        let oldContext = await stack.startContextStore.read(attemptId: "att-player-1")
        XCTAssertEqual(movedContext, ctx)
        XCTAssertNil(oldContext)
    }
}

private final class LockedStep: @unchecked Sendable {
    private let lock = NSLock()
    private var step = 0

    func next() -> Int {
        lock.lock()
        defer { lock.unlock() }
        step += 1
        return step
    }
}
