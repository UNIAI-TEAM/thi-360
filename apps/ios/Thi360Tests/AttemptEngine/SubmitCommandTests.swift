import Foundation
import XCTest
@testable import Thi360

@MainActor
final class SubmitCommandTests: XCTestCase {
    private final class OrderRecorder: @unchecked Sendable {
        private let lock = NSLock()
        private var entries: [String] = []

        func append(_ entry: String) {
            lock.lock()
            entries.append(entry)
            lock.unlock()
        }

        var snapshot: [String] {
            lock.lock()
            defer { lock.unlock() }
            return entries
        }
    }

    func testSubmitEmitsBeforeCloseCancelsTimersPreventsDoubleSendAndDeletesLocalData() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let items = server.leafItemIds(inPartIndex: 0)
        XCTAssertFalse(items.isEmpty)
        let item1 = items[0]

        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        try await store.write(
            Data(#"{"origin":"prep"}"#.utf8),
            namespace: DraftStore.namespace,
            key: DraftStore.startContextKey(attemptId: server.attemptId)
        )
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }

        let claimed = await clock.waitUntil {
            if case .editing = engine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)
        guard case let .editing(activeSessionId) = engine.state.session else {
            return XCTFail("Expected editing session")
        }

        let order = OrderRecorder()
        let events = engine.events
        let listener = Task {
            for await event in events {
                switch event {
                case .beforeClose(.attempt):
                    order.append("beforeClose:attempt")
                case let .attemptClosed(reason):
                    order.append("attemptClosed:\(reason)")
                default:
                    break
                }
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        let submitGate = AttemptTestGate()
        server.setGate(submitGate, for: .submit)
        server.onBeforeHandle = { call in
            if call.endpoint == .submit {
                order.append("http:submit")
            }
        }

        let savesBefore = server.callCount(for: .saveAnswers)
        engine.answer(itemId: item1, value: .shortAnswer(text: "final-submit-answer"))
        await clock.settle(yields: 8)
        XCTAssertEqual(server.callCount(for: .saveAnswers), savesBefore)

        engine.submit()
        await submitGate.waitUntilEntered(count: 1)
        XCTAssertEqual(engine.state.command, .sending(.submit))

        engine.submit()
        await clock.settle()
        XCTAssertEqual(server.callCount(for: .submit), 1, "Second submit() while sending must be a no-op")

        await submitGate.releaseAll()

        let submitted = await clock.waitUntil {
            engine.state.closed == .submitted && engine.state.command == .idle
        }
        XCTAssertTrue(submitted)
        await clock.settle()

        let call = try XCTUnwrap(server.calls(for: .submit).first)
        XCTAssertEqual(call.sessionHeader, activeSessionId)
        XCTAssertNotNil(call.idempotencyKeyHeader)
        XCTAssertFalse((call.idempotencyKeyHeader ?? "").isEmpty)

        let body = try XCTUnwrap(call.decodedJSON())
        let finalAnswers = try XCTUnwrap(body["finalAnswers"] as? [[String: Any]])
        XCTAssertEqual(finalAnswers.count, 1)
        XCTAssertEqual(finalAnswers[0]["itemId"] as? String, item1)

        let recordedOrder = order.snapshot
        let beforeIdx = try XCTUnwrap(recordedOrder.firstIndex(of: "beforeClose:attempt"))
        let httpIdx = try XCTUnwrap(recordedOrder.firstIndex(of: "http:submit"))
        XCTAssertLessThan(beforeIdx, httpIdx, "BeforeClose(.attempt) must emit strictly before POST /submit")

        await clock.advance(by: .seconds(3))
        XCTAssertEqual(server.callCount(for: .saveAnswers), savesBefore, "Pending timers must be cancelled on submit")

        let draftStore = DraftStore(store: store)
        let remainingDraft = await draftStore.readDraft(attemptId: server.attemptId, itemId: item1)
        let remainingSession = await draftStore.readSessionId(attemptId: server.attemptId)
        let remainingStartContext = await store.read(
            namespace: DraftStore.namespace,
            key: DraftStore.startContextKey(attemptId: server.attemptId)
        )
        XCTAssertNil(remainingDraft)
        XCTAssertNil(remainingSession)
        XCTAssertNil(remainingStartContext)
    }

    func testSubmitFinalAnswersIncludesAllOpenPartsCappedAt500AndExcludesClosedParts() async throws {
        // Plan A6 boundary derivation: clone items from certified rich-text/attempt-50.json
        // across 2 open parts (260 + 241 = 501 items) + 1 closed part.
        let clock = TestEngineClock()
        var openIds: [String] = []
        let closedItemId = "closed-part-item-001"

        let server = try FakeAttemptServer(
            useRichTextAttempt50: true,
            clock: clock,
            mutateInitialState: { json in
                guard let parts = json["parts"] as? [[String: Any]],
                      let firstPart = parts.first,
                      let templateItems = firstPart["items"] as? [[String: Any]],
                      let template = templateItems.first(where: { ($0["questionType"] as? String) != "QUESTION_SET" })
                else {
                    return
                }
                func makeItems(prefix: String, count: Int) -> [[String: Any]] {
                    (0..<count).map { idx in
                        var item = template
                        let id = String(format: "%@-%04d", prefix, idx)
                        item["itemId"] = id
                        item["ord"] = idx + 1
                        item["answer"] = NSNull()
                        item["answerSeq"] = 0
                        item["flagged"] = false
                        return item
                    }
                }

                let p1Items = makeItems(prefix: "open-p1", count: 260)
                let p2Items = makeItems(prefix: "open-p2", count: 241)
                openIds = (p1Items + p2Items).compactMap { $0["itemId"] as? String }

                var closedItem = template
                closedItem["itemId"] = closedItemId
                closedItem["ord"] = 1

                var part1 = firstPart
                part1["partId"] = "00000000-0000-7000-8000-000000008001"
                part1["ord"] = 1
                part1["state"] = "OPEN"
                part1["items"] = p1Items

                var part2 = firstPart
                part2["partId"] = "00000000-0000-7000-8000-000000008002"
                part2["ord"] = 2
                part2["state"] = "OPEN"
                part2["items"] = p2Items

                var part3 = firstPart
                part3["partId"] = "00000000-0000-7000-8000-000000008003"
                part3["ord"] = 3
                part3["state"] = "CLOSED"
                part3["items"] = [closedItem]

                json["parts"] = [part1, part2, part3]
            }
        )

        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }

        let claimed = await clock.waitUntil {
            if case .editing = engine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)

        for (idx, id) in openIds.enumerated() {
            engine.answer(itemId: id, value: .shortAnswer(text: "ans-\(idx)"))
        }
        engine.answer(itemId: closedItemId, value: .shortAnswer(text: "must-ignore"))

        engine.submit()
        let done = await clock.waitUntil {
            engine.state.closed == .submitted
        }
        XCTAssertTrue(done)

        let call = try XCTUnwrap(server.calls(for: .submit).first)
        let body = try XCTUnwrap(call.decodedJSON())
        let finalAnswers = try XCTUnwrap(body["finalAnswers"] as? [[String: Any]])
        XCTAssertEqual(finalAnswers.count, 500)
        let sentIds = finalAnswers.compactMap { $0["itemId"] as? String }
        XCTAssertEqual(sentIds, Array(openIds.prefix(500)))
        XCTAssertFalse(sentIds.contains(openIds[500]))
        XCTAssertFalse(sentIds.contains(closedItemId))
    }

    func testSubmitRefusedOn409AndEvictedOnSessionRevoked() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }

        let claimed = await clock.waitUntil {
            if case .editing = engine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)

        // Plan A6 / Orchestrator Addendum: defensive 409 TIME_UP on submit derived from certified
        // attempts-finish-409-time-up.json (live backend returns 200 GRADING for overdue submit).
        let getsBefore409 = server.callCount(for: .getState)
        server.enqueue(
            .submit,
            try .fixture(operationId: Operations.finishPart.id, status: 409, variant: "time-up")
        )
        engine.submit()

        let refused = await clock.waitUntil {
            engine.state.command == .refused(code: "TIME_UP")
        }
        XCTAssertTrue(refused)
        XCTAssertGreaterThan(server.callCount(for: .getState), getsBefore409)

        var sawSessionLost = false
        let events = engine.events
        let listener = Task {
            for await event in events where event == .sessionLost {
                sawSessionLost = true
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        server.enqueue(
            .submit,
            try .fixture(operationId: Operations.saveAnswers.id, status: 409, variant: "attempt-session-revoked")
        )
        engine.submit()

        let evicted = await clock.waitUntil {
            engine.state.session == .evicted && engine.state.command == .idle && sawSessionLost
        }
        XCTAssertTrue(evicted)
    }

    func testSubmitUnconfirmedRetainsIdempotencyKeyAcrossRetriesAndReconcilesAlreadySubmitted() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }

        let claimed = await clock.waitUntil {
            if case .editing = engine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)

        server.enqueue(.submit, .networkError(.timedOut))
        engine.submit()
        let unconfirmed1 = await clock.waitUntil {
            engine.state.command == .unconfirmed(.submit)
        }
        XCTAssertTrue(unconfirmed1)

        engine.reconcile()
        let idle1 = await clock.waitUntil {
            engine.state.command == .idle
        }
        XCTAssertTrue(idle1)

        server.enqueue(.submit, try .serverError(statusCode: 503))
        engine.submit()
        let unconfirmed2 = await clock.waitUntil {
            engine.state.command == .unconfirmed(.submit)
        }
        XCTAssertTrue(unconfirmed2)

        engine.reconcile()
        let idle2 = await clock.waitUntil {
            engine.state.command == .idle
        }
        XCTAssertTrue(idle2)

        engine.submit()
        let done = await clock.waitUntil {
            engine.state.closed == .submitted && engine.state.command == .idle
        }
        XCTAssertTrue(done)

        let submitCalls = server.calls(for: .submit)
        XCTAssertEqual(submitCalls.count, 3)
        let key1 = try XCTUnwrap(submitCalls[0].idempotencyKeyHeader)
        let key2 = try XCTUnwrap(submitCalls[1].idempotencyKeyHeader)
        let key3 = try XCTUnwrap(submitCalls[2].idempotencyKeyHeader)
        XCTAssertEqual(key1, key2, "Submit retry must retain identical Idempotency-Key across network failure")
        XCTAssertEqual(key2, key3, "Submit retry must retain identical Idempotency-Key across 5xx until 200")
    }

    func testSubmitCrashMidFlightReopenHandlesBothSubmittedAndStillOpen() async throws {
        // Case A: crash mid-submit while server already finalized attempt -> reopen sees closed & clears drafts.
        do {
            let clock = TestEngineClock()
            let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
            let item1 = server.firstLeafItemId(inPartIndex: 0)
            let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
            defer { try? FileManager.default.removeItem(at: rootURL) }
            let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

            let engine1 = AttemptEngine(
                attemptId: server.attemptId,
                accountId: "account-A",
                api: server.makeAttemptAPI(),
                store: store,
                clock: clock,
                connectivity: connectivity
            )
            let claimed = await clock.waitUntil {
                if case .editing = engine1.state.session { return true }
                return false
            }
            XCTAssertTrue(claimed)

            let gate = AttemptTestGate()
            server.setGate(gate, for: .submit)
            engine1.answer(itemId: item1, value: .shortAnswer(text: "draft-before-crash"))
            await engine1.waitForIdleForTesting()
            engine1.submit()
            await gate.waitUntilEntered(count: 1)

            engine1.dispose()
            await gate.releaseAll()
            server.setGate(nil, for: .submit)

            server.mutateState { json in
                json["state"] = "GRADING"
                json["endReason"] = "SUBMITTED"
            }

            let engine2 = AttemptEngine(
                attemptId: server.attemptId,
                accountId: "account-A",
                api: server.makeAttemptAPI(),
                store: store,
                clock: clock,
                connectivity: connectivity
            )
            defer { engine2.dispose() }

            let reopenedClosed = await clock.waitUntil {
                engine2.state.closed == .submitted
            }
            XCTAssertTrue(reopenedClosed)
            let draftAfter = await DraftStore(store: store).readDraft(attemptId: server.attemptId, itemId: item1)
            XCTAssertNil(draftAfter)
        }

        // Case B: crash mid-submit while server stayed IN_PROGRESS -> reopen restores session & drafts and continues.
        do {
            let clock = TestEngineClock()
            let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
            let item1 = server.firstLeafItemId(inPartIndex: 0)
            let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
            defer { try? FileManager.default.removeItem(at: rootURL) }
            let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

            let engine1 = AttemptEngine(
                attemptId: server.attemptId,
                accountId: "account-A",
                api: server.makeAttemptAPI(),
                store: store,
                clock: clock,
                connectivity: connectivity
            )
            let claimed = await clock.waitUntil {
                if case .editing = engine1.state.session { return true }
                return false
            }
            XCTAssertTrue(claimed)
            guard case let .editing(savedSessionId) = engine1.state.session else {
                return XCTFail("Expected editing session")
            }

            let gate = AttemptTestGate()
            server.enqueue(.submit, .networkError(.networkConnectionLost, gate: gate))
            engine1.answer(itemId: item1, value: .shortAnswer(text: "survives-crash"))
            await engine1.waitForIdleForTesting()
            engine1.submit()
            await gate.waitUntilEntered(count: 1)

            engine1.dispose()
            await gate.releaseAll()

            let engine2 = AttemptEngine(
                attemptId: server.attemptId,
                accountId: "account-A",
                api: server.makeAttemptAPI(),
                store: store,
                clock: clock,
                connectivity: connectivity
            )
            defer { engine2.dispose() }

            let resumed = await clock.waitUntil {
                engine2.state.session == .editing(sessionId: savedSessionId)
                    && engine2.state.answers[item1]?.value == .shortAnswer(text: "survives-crash")
                    && engine2.state.save == .saved
            }
            XCTAssertTrue(resumed, "Reopen with IN_PROGRESS server must restore session & drafts without openElsewhere")
        }
    }
}
