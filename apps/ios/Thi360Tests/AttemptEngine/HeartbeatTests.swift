import Foundation
import XCTest
@testable import Thi360

@MainActor
final class HeartbeatTests: XCTestCase {
    // AC-4: 30s periodic heartbeat while editing & foreground; each response re-anchors clock;
    // network/5xx does not change save/session and keeps schedule; non-IN_PROGRESS stops loop, reloads, emits AttemptClosed.
    func testHeartbeatCadenceReanchorTransientErrorsAndNonInProgressClose() async throws {
        let clock = TestEngineClock()
        let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
        let deadline = baseNow.addingTimeInterval(600)
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(
            initialStateVariant: "in-progress-shared",
            initialServerNow: baseNow,
            clock: clock,
            mutateInitialState: { json in
                json["deadlineAt"] = FakeAttemptServer.isoString(deadline)
            }
        )

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }

        let claimed = await clock.waitUntil { engine.state.activeSessionId != nil }
        XCTAssertTrue(claimed)
        var receivedEvents: [EngineEvent] = []
        let eventTask = Task {
            for await event in engine.events {
                receivedEvents.append(event)
            }
        }
        defer { eventTask.cancel() }

        // Before 30s: zero heartbeats
        await clock.advance(by: .seconds(29))
        XCTAssertEqual(server.callCount(for: .heartbeat), 0)

        // At 30s: first heartbeat, server shifts serverNow forward by +10s to verify re-anchor
        let shiftedNow = baseNow.addingTimeInterval(40)
        server.enqueue(
            .heartbeat,
            try .fixture(operationId: Operations.heartbeat.id, status: 200, variant: "") { obj in
                obj["serverNow"] = FakeAttemptServer.isoString(shiftedNow)
                obj["state"] = "IN_PROGRESS"
            }
        )
        await clock.advance(by: .seconds(1))
        let hb1 = await clock.waitUntil {
            server.callCount(for: .heartbeat) == 1 && engine.state.clock.remainingSecondsCeil == 560
        }
        XCTAssertTrue(hb1)
        XCTAssertEqual(engine.state.clock.remainingSecondsCeil, 560)

        // Next two heartbeats fail with network error then 503 -> save stays .saved, session stays .editing, schedule continues!
        server.enqueue(.heartbeat, responses: [
            .networkError(.timedOut),
            try .serverError(statusCode: 503)
        ])

        await clock.advance(by: .seconds(30))
        let hb2 = await clock.waitUntil { server.callCount(for: .heartbeat) == 2 }
        XCTAssertTrue(hb2)
        XCTAssertEqual(engine.state.save, .saved)
        XCTAssertNotNil(engine.state.activeSessionId)

        await clock.advance(by: .seconds(30))
        let hb3 = await clock.waitUntil { server.callCount(for: .heartbeat) == 3 }
        XCTAssertTrue(hb3)
        XCTAssertEqual(engine.state.save, .saved)
        XCTAssertNotNil(engine.state.activeSessionId)

        // Next heartbeat returns GRADING -> stops heartbeat, reloads state, emits AttemptClosed
        server.enqueue(
            .heartbeat,
            try .fixture(operationId: Operations.heartbeat.id, status: 200, variant: "") { obj in
                obj["serverNow"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(120))
                obj["state"] = "GRADING"
            }
        )
        try server.setState(variant: "closed-submitted")
        let reloadsBefore = server.callCount(for: .getState)

        await clock.advance(by: .seconds(30))
        let hb4Closed = await clock.waitUntil {
            server.callCount(for: .heartbeat) == 4
                && engine.state.closed != nil
                && server.callCount(for: .getState) > reloadsBefore
                && receivedEvents.contains(where: {
                    if case .attemptClosed = $0 { return true }
                    return false
                })
        }
        XCTAssertTrue(hb4Closed)

        // Advance another 90s -> heartbeat must have stopped completely
        await clock.advance(by: .seconds(90))
        XCTAssertEqual(server.callCount(for: .heartbeat), 4)
    }
}
