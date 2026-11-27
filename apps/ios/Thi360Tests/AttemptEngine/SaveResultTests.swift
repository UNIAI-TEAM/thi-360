import Foundation
import XCTest
@testable import Thi360

@MainActor
final class SaveResultTests: XCTestCase {
    func testSavedOlderSeqStaleAndRejectedResults() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-per-part", clock: clock)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }
        let asyncAssertResult1 = await clock.waitUntil { engine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult1)

        let items = server.leafItemIds()
        let item1 = items[0]
        let item2 = items[1]

        let gate = AttemptTestGate()
        server.setGate(gate, for: .saveAnswers)
        engine.answer(itemId: item1, value: .singleChoice(optionId: "a"))
        await gate.waitUntilEntered()
        engine.answer(itemId: item1, value: .singleChoice(optionId: "b"))
        XCTAssertEqual(engine.state.answers[item1]?.seq, 2)
        server.setGate(nil, for: .saveAnswers)
        await gate.releaseAll()

        let asyncAssertResult2 = await clock.waitUntil {
            server.callCount(for: .saveAnswers) == 2
                && engine.state.answers[item1]?.sync == .saved
                && engine.state.save == .saved
        }
        XCTAssertTrue(asyncAssertResult2)
        let draftAfterSave = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(item1)")
        XCTAssertNil(draftAfterSave, "Draft must be deleted once latest seq is SAVED")

        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]],
               var partItems = parts[0]["items"] as? [[String: Any]]
            {
                for idx in partItems.indices where partItems[idx]["itemId"] as? String == item1 {
                    partItems[idx]["answerSeq"] = 10
                    partItems[idx]["answer"] = ["type": "SINGLE_CHOICE", "optionId": "a"]
                }
                parts[0]["items"] = partItems
                json["parts"] = parts
            }
        }
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 200, variant: "stale") { obj in
                obj["results"] = [["itemId": item1, "seq": 10, "status": "STALE", "reason": NSNull()]]
            }
        )
        let reloadsBeforeStale = server.callCount(for: .getState)
        engine.answer(itemId: item1, value: .singleChoice(optionId: "b"))
        let asyncAssertResult3 = await clock.waitUntil {
            server.callCount(for: .getState) > reloadsBeforeStale
                && engine.state.answers[item1]?.seq == 10
                && engine.state.answers[item1]?.value == .singleChoice(optionId: "a")
                && engine.state.answers[item1]?.sync == .saved
        }
        XCTAssertTrue(asyncAssertResult3)

        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 200, variant: "rejected-invalid-answer") { obj in
                obj["results"] = [["itemId": item2, "seq": 1, "status": "REJECTED", "reason": "INVALID_ANSWER"]]
            }
        )
        engine.answer(itemId: item2, value: .singleChoice(optionId: "a"))
        let asyncAssertResult4 = await clock.waitUntil {
            engine.state.answers[item2]?.sync == .rejected && engine.state.save == .unsaved(.rejected)
        }
        XCTAssertTrue(asyncAssertResult4)
        let callsAfterReject = server.callCount(for: .saveAnswers)
        await clock.advance(by: .seconds(10))
        XCTAssertEqual(server.callCount(for: .saveAnswers), callsAfterReject, "Rejected item must NOT be retried until edited")

        engine.answer(itemId: item2, value: .singleChoice(optionId: "b"))
        let asyncAssertResult5 = await clock.waitUntil {
            engine.state.answers[item2]?.sync == .saved && engine.state.save == .saved
        }
        XCTAssertTrue(asyncAssertResult5)

        var events: [EngineEvent] = []
        let eventStream = engine.events
        let eventTask = Task {
            for await event in eventStream {
                events.append(event)
            }
        }
        defer { eventTask.cancel() }
        await clock.settle()

        let partId = server.partId(at: 0)
        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty {
                parts[0]["state"] = "CLOSED"
                json["parts"] = parts
            }
        }
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 200, variant: "rejected-part-closed") { obj in
                obj["results"] = [["itemId": item2, "seq": 3, "status": "REJECTED", "reason": "PART_CLOSED"]]
            }
        )
        let reloadsBeforePartClose = server.callCount(for: .getState)
        engine.answer(itemId: item2, value: .singleChoice(optionId: "a"))
        let asyncAssertResult6 = await clock.waitUntil {
            engine.state.answers[item2]?.sync == .locked
                && events.contains(.partClosed(partId))
                && server.callCount(for: .getState) > reloadsBeforePartClose
        }
        XCTAssertTrue(asyncAssertResult6)

        let timeUpServer = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let (_, store2, dir2) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir2) }
        let timeUpEngine = AttemptEngine(
            attemptId: timeUpServer.attemptId,
            accountId: "account-A",
            api: timeUpServer.makeAttemptAPI(),
            store: store2,
            clock: clock,
            connectivity: connectivity
        )
        defer { timeUpEngine.dispose() }
        let asyncAssertResult7 = await clock.waitUntil { timeUpEngine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult7)

        var timeUpEvents: [EngineEvent] = []
        let tuStream = timeUpEngine.events
        let tuTask = Task {
            for await event in tuStream { timeUpEvents.append(event) }
        }
        defer { tuTask.cancel() }
        await clock.settle()

        let tuItem = timeUpServer.firstLeafItemId()
        timeUpServer.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 200, variant: "rejected-time-up") { obj in
                obj["results"] = [["itemId": tuItem, "seq": 1, "status": "REJECTED", "reason": "TIME_UP"]]
            }
        )
        try timeUpServer.setState(variant: "closed-time-up")
        timeUpEngine.answer(itemId: tuItem, value: .singleChoice(optionId: "a"))
        let asyncAssertResult8 = await clock.waitUntil {
            timeUpEngine.state.answers[tuItem]?.sync == .locked
                && timeUpEngine.state.closed == .timeUp
                && timeUpEvents.contains(.attemptClosed(.timeUp))
        }
        XCTAssertTrue(asyncAssertResult8)
    }
}
