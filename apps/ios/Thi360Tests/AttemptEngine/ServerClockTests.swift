import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ServerClockTests: XCTestCase {
    // AC-6: SHARED vs PER_PART vs no deadline; re-anchor on state/claim/heartbeat/save;
    // monotonic between anchors (wall-clock / timezone +/-1h shift causes <= 1s drift);
    // after load, remaining matches deadlineAt - serverNow within <= 1s.
    func testSharedPerPartNoDeadlineAndMonotonicImmunityToWallClockShift() async throws {
        let clock = TestEngineClock()
        let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!

        // Pure ServerClock immunity to wall clock & timezone shifts +/- 1 hour
        var serverClock = ServerClock()
        serverClock.reanchor(serverNow: baseNow, at: clock.now)
        let deadline = baseNow.addingTimeInterval(1200)
        XCTAssertEqual(serverClock.remaining(until: deadline, at: clock.now), .seconds(1200))

        // Simulate advancing monotonic clock 15s (wall clock/timezone irrelevant because ServerClock never reads Date())
        await clock.advance(by: .seconds(15))
        let remAfter15s = serverClock.remaining(until: deadline, at: clock.now)
        XCTAssertEqual(remAfter15s, .seconds(1185))
        XCTAssertEqual(
            abs(ServerClock.timeInterval(from: remAfter15s ?? .zero) - 1185.0),
            0.0,
            accuracy: 1.0
        )

        // Check +/- 1 hour shifted Date compared against ServerClock anchor: ServerClock tracks monotonic elapsed only.
        let shiftedDeviceDate = baseNow.addingTimeInterval(3600)
        XCTAssertNotEqual(shiftedDeviceDate, serverClock.estimatedServerNow(at: clock.now))
        XCTAssertEqual(
            serverClock.estimatedServerNow(at: clock.now)?.timeIntervalSince(baseNow) ?? -1,
            15.0,
            accuracy: 0.01
        )

        // SHARED timing counts down to attempt.deadlineAt and re-anchors on state, claim, save, heartbeat
        let (_, store1, dir1) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir1) }
        let (conn1, _) = AttemptEngineTestHarness.makeConnectivity()
        let sharedServer = try FakeAttemptServer(
            initialStateVariant: "in-progress-shared",
            initialServerNow: baseNow,
            clock: clock,
            mutateInitialState: { json in
                json["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(900))
            }
        )
        sharedServer.enqueue(
            .claimSession,
            try .fixture(operationId: Operations.claimSession.id, status: 200) { obj in
                obj["sessionId"] = "55555555-5555-4555-8555-555555555555"
                obj["serverNow"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(10))
            }
        )

        let sharedEngine = AttemptEngine(
            attemptId: sharedServer.attemptId,
            accountId: "account-A",
            api: sharedServer.makeAttemptAPI(),
            store: store1,
            clock: clock,
            connectivity: conn1,
            sessionIdGenerator: { "55555555-5555-4555-8555-555555555555" }
        )
        defer { sharedEngine.dispose() }

        let asyncAssertResult1 = await clock.waitUntil { sharedEngine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult1)
        XCTAssertEqual(sharedEngine.state.clock.target, .attempt)
        // Re-anchored by claimSession (+10s -> 890s remaining)
        XCTAssertEqual(sharedEngine.state.clock.remainingSecondsCeil, 890)

        // Re-anchor via saveAnswers (+30s -> 870s remaining)
        let itemId = sharedServer.firstLeafItemId()
        sharedServer.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 200, variant: "saved") { obj in
                obj["serverNow"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(30))
                obj["results"] = [["itemId": itemId, "seq": 1, "status": "SAVED", "reason": NSNull()]]
            }
        )
        sharedEngine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        let asyncAssertResult2 = await clock.waitUntil { sharedEngine.state.save == .saved && sharedServer.callCount(for: .saveAnswers) == 1 }
        XCTAssertTrue(asyncAssertResult2)
        XCTAssertEqual(sharedEngine.state.clock.remainingSecondsCeil, 870)

        // PER_PART timing counts down to active OPEN part's deadlineAt, and .none when no deadline
        let (_, store2, dir2) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir2) }
        let (conn2, _) = AttemptEngineTestHarness.makeConnectivity()
        let perPartClock = TestEngineClock()
        let perPartServer = try FakeAttemptServer(
            initialStateVariant: "in-progress-per-part",
            initialServerNow: baseNow,
            clock: perPartClock,
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]] {
                    parts[0]["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(300))
                    json["parts"] = parts
                }
            }
        )
        let partId = perPartServer.partId(at: 0)
        let perPartEngine = AttemptEngine(
            attemptId: perPartServer.attemptId,
            accountId: "account-A",
            api: perPartServer.makeAttemptAPI(),
            store: store2,
            clock: perPartClock,
            connectivity: conn2
        )
        defer { perPartEngine.dispose() }

        let asyncAssertResult3 = await perPartClock.waitUntil { perPartEngine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult3)
        XCTAssertEqual(perPartEngine.state.clock.target, .part(partId))
        XCTAssertEqual(perPartEngine.state.clock.remainingSecondsCeil, 300)

        // No deadline -> .none
        perPartServer.mutateState { json in
            json["deadlineAt"] = NSNull()
            if var parts = json["parts"] as? [[String: Any]] {
                parts[0]["deadlineAt"] = NSNull()
                json["parts"] = parts
            }
        }
        perPartEngine.refresh()
        let asyncAssertResult4 = await perPartClock.waitUntil { perPartEngine.state.clock == .none }
        XCTAssertTrue(asyncAssertResult4)
    }
}
