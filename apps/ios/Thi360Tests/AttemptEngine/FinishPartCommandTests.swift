import Foundation
import XCTest
@testable import Thi360

@MainActor
final class FinishPartCommandTests: XCTestCase {
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

    func testFinishPartEmitsBeforeCloseBeforeHTTPAndCancelsPendingTimersAndOmitsIdempotencyKey() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-per-part", clock: clock)
        let part1Id = server.partId(at: 0)
        let part2Id = server.partId(at: 1)
        let part1Items = server.leafItemIds(inPartIndex: 0)
        XCTAssertFalse(part1Id.isEmpty)
        XCTAssertFalse(part2Id.isEmpty)
        XCTAssertFalse(part1Items.isEmpty)
        let item1 = part1Items[0]

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
        guard case let .editing(activeSessionId) = engine.state.session else {
            return XCTFail("Expected editing session")
        }

        let order = OrderRecorder()
        let eventStream = engine.events
        let listener = Task {
            for await event in eventStream {
                switch event {
                case let .beforeClose(.part(pid)):
                    order.append("beforeClose:\(pid)")
                case let .partClosed(pid):
                    order.append("partClosed:\(pid)")
                default:
                    break
                }
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        server.onBeforeHandle = { call in
            if call.endpoint == .finishPart {
                order.append("http:finishPart")
            }
        }

        let savesBefore = server.callCount(for: .saveAnswers)
        engine.answer(itemId: item1, value: .shortAnswer(text: "unsaved-final-answer"))
        await clock.settle(yields: 8)
        XCTAssertEqual(server.callCount(for: .saveAnswers), savesBefore, "Short answer should still be debounced")

        engine.finishPart(partId: part1Id)

        let finished = await clock.waitUntil {
            engine.state.command == .idle && engine.state.position?.partId == part2Id
        }
        XCTAssertTrue(finished, "Expected finishPart to complete and advance position to part 2")
        await clock.settle()

        let finishCalls = server.calls(for: .finishPart)
        XCTAssertEqual(finishCalls.count, 1)
        let call = try XCTUnwrap(finishCalls.first)
        XCTAssertEqual(call.sessionHeader, activeSessionId)
        XCTAssertNil(call.idempotencyKeyHeader, "finishPart must NOT send Idempotency-Key")

        let body = try XCTUnwrap(call.decodedJSON())
        let finalAnswers = try XCTUnwrap(body["finalAnswers"] as? [[String: Any]])
        XCTAssertEqual(finalAnswers.count, 1)
        XCTAssertEqual(finalAnswers[0]["itemId"] as? String, item1)
        XCTAssertEqual((finalAnswers[0]["answer"] as? [String: Any])?["text"] as? String, "unsaved-final-answer")

        let recordedOrder = order.snapshot
        let beforeIdx = try XCTUnwrap(recordedOrder.firstIndex(of: "beforeClose:\(part1Id)"))
        let httpIdx = try XCTUnwrap(recordedOrder.firstIndex(of: "http:finishPart"))
        let closedIdx = try XCTUnwrap(recordedOrder.firstIndex(of: "partClosed:\(part1Id)"))
        XCTAssertLessThan(beforeIdx, httpIdx, "BeforeClose must emit strictly before POST /finish HTTP call")
        XCTAssertLessThan(httpIdx, closedIdx, "PartClosed must emit after POST /finish completes")

        XCTAssertEqual(engine.state.answers[item1]?.sync, .locked)

        await clock.advance(by: .seconds(3))
        XCTAssertEqual(
            server.callCount(for: .saveAnswers),
            savesBefore,
            "Pending debounce timer must be cancelled by finishPart"
        )
    }

    func testFinishPartFinalAnswersBoundary500And501InQueueOrderAndExcludesOtherParts() async throws {
        // Plan A6 boundary derivation: clone real leaf items from certified rich-text/attempt-50.json
        // to construct 500-item and 501-item open parts plus a second open part for boundary verification.
        try await assertFinalAnswersCap(part1ItemCount: 500, expectedSentCount: 500)
        try await assertFinalAnswersCap(part1ItemCount: 501, expectedSentCount: 500)
    }

    private func assertFinalAnswersCap(part1ItemCount: Int, expectedSentCount: Int) async throws {
        let clock = TestEngineClock()
        var part1Ids: [String] = []
        let part2ItemId = "part2-item-isolated-001"
        let part2Id = "00000000-0000-7000-8000-000000009002"

        let server = try FakeAttemptServer(
            useRichTextAttempt50: true,
            clock: clock,
            mutateInitialState: { json in
                guard var parts = json["parts"] as? [[String: Any]],
                      var firstPart = parts.first,
                      let templateItems = firstPart["items"] as? [[String: Any]],
                      let template = templateItems.first(where: { ($0["questionType"] as? String) != "QUESTION_SET" })
                else {
                    return
                }
                var expanded: [[String: Any]] = []
                expanded.reserveCapacity(part1ItemCount)
                for idx in 0..<part1ItemCount {
                    var item = template
                    let id = String(format: "part1-item-%04d", idx)
                    item["itemId"] = id
                    item["ord"] = idx + 1
                    item["answer"] = NSNull()
                    item["answerSeq"] = 0
                    item["flagged"] = false
                    expanded.append(item)
                    part1Ids.append(id)
                }
                firstPart["items"] = expanded
                firstPart["state"] = "OPEN"

                var secondItem = template
                secondItem["itemId"] = part2ItemId
                secondItem["ord"] = 1
                secondItem["answer"] = NSNull()
                secondItem["answerSeq"] = 0
                secondItem["flagged"] = false

                var secondPart = firstPart
                secondPart["partId"] = part2Id
                secondPart["ord"] = 2
                secondPart["state"] = "OPEN"
                secondPart["items"] = [secondItem]

                parts = [firstPart, secondPart]
                json["parts"] = parts
            }
        )
        let part1Id = server.partId(at: 0)
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

        engine.answer(itemId: part2ItemId, value: .shortAnswer(text: "other-part"))
        for idx in 0..<part1ItemCount {
            engine.answer(itemId: part1Ids[idx], value: .shortAnswer(text: "val-\(idx)"))
        }

        engine.finishPart(partId: part1Id)

        let sent = await clock.waitUntil {
            server.callCount(for: .finishPart) == 1 && engine.state.command == .idle
        }
        XCTAssertTrue(sent)
        await engine.waitForIdleForTesting()

        let call = try XCTUnwrap(server.calls(for: .finishPart).first)
        let body = try XCTUnwrap(call.decodedJSON())
        let finalAnswers = try XCTUnwrap(body["finalAnswers"] as? [[String: Any]])
        XCTAssertEqual(finalAnswers.count, expectedSentCount)

        let sentIds = finalAnswers.compactMap { $0["itemId"] as? String }
        XCTAssertEqual(sentIds, Array(part1Ids.prefix(expectedSentCount)), "Must send first 500 items in queue order")
        XCTAssertFalse(sentIds.contains(part2ItemId), "Must not include answers from another part")
        if part1ItemCount > 500 {
            XCTAssertFalse(sentIds.contains(part1Ids[500]), "Item #501 must NOT be sent")
        }
    }

    func testFinishPartHandles4xxRefusedAndSessionRevoked() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-per-part", clock: clock)
        let part1Id = server.partId(at: 0)

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

        let getsBefore409 = server.callCount(for: .getState)
        server.enqueue(
            .finishPart,
            try .fixture(operationId: Operations.finishPart.id, status: 409, variant: "part-closed")
        )
        engine.finishPart(partId: part1Id)

        let refused = await clock.waitUntil {
            engine.state.command == .refused(code: "PART_CLOSED")
                && server.callCount(for: .getState) > getsBefore409
        }
        XCTAssertTrue(refused)
        await engine.waitForIdleForTesting()
        XCTAssertEqual(engine.state.command, .refused(code: "PART_CLOSED"))
        XCTAssertGreaterThan(server.callCount(for: .getState), getsBefore409, "4xx != 401 must reload attempt state")

        // Plan A6 documented derivation: mutate code field on certified start 400 fixture for finishPart 400 refusal.
        let getsBefore400 = server.callCount(for: .getState)
        server.enqueue(
            .finishPart,
            try .fixture(operationId: Operations.start.id, status: 400, variant: "idempotency-key-required") { obj in
                obj["code"] = "INVALID_PART_STATE"
            }
        )
        engine.finishPart(partId: part1Id)
        let refused400 = await clock.waitUntil {
            engine.state.command == .refused(code: "INVALID_PART_STATE")
                && server.callCount(for: .getState) > getsBefore400
        }
        XCTAssertTrue(refused400)
        await engine.waitForIdleForTesting()
        XCTAssertGreaterThan(server.callCount(for: .getState), getsBefore400)

        var sawSessionLost = false
        let events = engine.events
        let listener = Task {
            for await event in events {
                if event == .sessionLost {
                    sawSessionLost = true
                }
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        server.enqueue(
            .finishPart,
            try .fixture(operationId: Operations.saveAnswers.id, status: 409, variant: "attempt-session-revoked")
        )
        engine.finishPart(partId: part1Id)

        let evicted = await clock.waitUntil {
            engine.state.session == .evicted && engine.state.command == .idle && sawSessionLost
        }
        XCTAssertTrue(evicted)
    }

    func testFinishPartUnconfirmedOnNetworkAnd5xxReconcilesStillOpenAndClosedOnReconnect() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-per-part", clock: clock)
        let part1Id = server.partId(at: 0)
        let part2Id = server.partId(at: 1)

        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, netObserver) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

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

        server.enqueue(.finishPart, .networkError(.timedOut))
        engine.finishPart(partId: part1Id)

        let unconfirmedNet = await clock.waitUntil {
            engine.state.command == .unconfirmed(.finishPart)
        }
        XCTAssertTrue(unconfirmedNet)

        engine.reconcile()
        let reconciledOpen = await clock.waitUntil {
            engine.state.command == .idle
        }
        XCTAssertTrue(reconciledOpen, "Reconcile with part still OPEN must return to .idle for retry")
        XCTAssertEqual(engine.state.position?.partId, part1Id)

        var partClosedEvents: [PartID] = []
        let events = engine.events
        let listener = Task {
            for await event in events {
                if case let .partClosed(pid) = event {
                    partClosedEvents.append(pid)
                }
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        server.enqueue(.finishPart, try .serverError(statusCode: 503))
        engine.finishPart(partId: part1Id)

        let unconfirmed5xx = await clock.waitUntil {
            engine.state.command == .unconfirmed(.finishPart)
        }
        XCTAssertTrue(unconfirmed5xx)

        server.mutateState { json in
            guard var parts = json["parts"] as? [[String: Any]] else { return }
            if parts.count >= 2 {
                parts[0]["state"] = "CLOSED"
                parts[1]["state"] = "OPEN"
                json["parts"] = parts
            }
        }

        netObserver.emit(false)
        await clock.settle()
        netObserver.emit(true)

        let autoReconciledClosed = await clock.waitUntil {
            engine.state.command == .idle
                && engine.state.position?.partId == part2Id
                && partClosedEvents.contains(part1Id)
        }
        XCTAssertTrue(autoReconciledClosed, "Reconnect reconcile with closed part must clear command and emit PartClosed")
    }
}
