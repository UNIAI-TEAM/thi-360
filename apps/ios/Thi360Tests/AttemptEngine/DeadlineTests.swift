import Foundation
import XCTest
@testable import Thi360

@MainActor
final class DeadlineTests: XCTestCase {
    func testSharedDeadlineReachesZeroLocksEmitsReloadsAt500msAndBlocksAnswers() async throws {
        let clock = TestEngineClock()
        let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
        let server = try FakeAttemptServer(
            initialStateVariant: "in-progress-shared",
            initialServerNow: baseNow,
            clock: clock,
            mutateInitialState: { json in
                json["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(3))
            }
        )
        let item1 = server.firstLeafItemId(inPartIndex: 0)
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

        var receivedClosed: [ClosedReason] = []
        let events = engine.events
        let listener = Task {
            for await event in events {
                if case let .attemptClosed(reason) = event {
                    receivedClosed.append(reason)
                }
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        let getsBeforeExpiry = server.callCount(for: .getState)
        let savesBeforeExpiry = server.callCount(for: .saveAnswers)

        await clock.advance(by: .seconds(3))

        XCTAssertEqual(engine.state.clock.remaining, Duration.zero)
        XCTAssertTrue(
            engine.state.answers.values.allSatisfy { $0.sync == .locked },
            "All items must immediately become .locked when countdown hits 0"
        )
        let receivedTimeUp = await clock.waitUntil { receivedClosed == [.timeUp] }
        XCTAssertTrue(receivedTimeUp)
        XCTAssertEqual(receivedClosed, [.timeUp])
        XCTAssertEqual(server.callCount(for: .getState), getsBeforeExpiry, "Reload must wait 500ms after expiry")

        engine.answer(itemId: item1, value: .singleChoice(optionId: "after-deadline-opt"))
        await clock.advance(by: .milliseconds(400))
        XCTAssertEqual(server.callCount(for: .getState), getsBeforeExpiry, "Reload must not fire before 500ms")
        XCTAssertEqual(server.callCount(for: .saveAnswers), savesBeforeExpiry, "Zero answers sent after deadline")

        server.mutateState { json in
            json["state"] = "GRADING"
            json["endReason"] = "TIME_UP"
        }

        await clock.advance(by: .milliseconds(100))
        let reloaded = await clock.waitUntil {
            engine.state.closed == .timeUp
        }
        XCTAssertTrue(reloaded)
        XCTAssertEqual(server.callCount(for: .getState), getsBeforeExpiry + 1)

        engine.answer(itemId: item1, value: .singleChoice(optionId: "after-reload-opt"))
        await clock.advance(by: .seconds(2))
        XCTAssertEqual(server.callCount(for: .saveAnswers), savesBeforeExpiry)
    }

    func testPerPartDeadlineAdvancesToNextPartAndLastPartClosesWithTimeUp() async throws {
        let clock = TestEngineClock()
        let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
        let server = try FakeAttemptServer(
            initialStateVariant: "in-progress-per-part",
            initialServerNow: baseNow,
            clock: clock,
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]], parts.count >= 2 {
                    parts = Array(parts.prefix(2))
                    parts[0]["state"] = "OPEN"
                    parts[0]["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(2))
                    parts[1]["state"] = "PENDING"
                    parts[1]["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(7))
                    json["parts"] = parts
                }
            }
        )
        let part1Id = server.partId(at: 0)
        let part2Id = server.partId(at: 1)
        let part1Item = server.firstLeafItemId(inPartIndex: 0)

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
        XCTAssertEqual(engine.state.position?.partId, part1Id)

        var closedParts: [PartID] = []
        var closedAttempt: [ClosedReason] = []
        let events = engine.events
        let listener = Task {
            for await event in events {
                switch event {
                case let .partClosed(pid):
                    closedParts.append(pid)
                case let .attemptClosed(reason):
                    closedAttempt.append(reason)
                default:
                    break
                }
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        await clock.advance(by: .seconds(2))
        let part1Locked = await clock.waitUntil {
            engine.state.answers[part1Item]?.sync == .locked && closedParts == [part1Id]
        }
        XCTAssertTrue(part1Locked)
        XCTAssertEqual(engine.state.answers[part1Item]?.sync, .locked)
        XCTAssertEqual(closedParts, [part1Id])
        XCTAssertTrue(closedAttempt.isEmpty)

        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]], parts.count >= 2 {
                parts[0]["state"] = "CLOSED"
                parts[1]["state"] = "OPEN"
                parts[1]["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(7))
                json["parts"] = parts
            }
        }

        await clock.advance(by: .milliseconds(500))
        let movedToPart2 = await clock.waitUntil {
            engine.state.position?.partId == part2Id && engine.state.closed == nil
        }
        XCTAssertTrue(movedToPart2)

        await clock.advance(by: .milliseconds(4500))
        let lastPartExpired = await clock.waitUntil { closedAttempt == [.timeUp] }
        XCTAssertTrue(lastPartExpired)
        XCTAssertEqual(closedAttempt, [.timeUp])

        try server.setState(variant: "closed-time-up")

        await clock.advance(by: .milliseconds(500))
        let closedFinal = await clock.waitUntil {
            engine.state.closed == .timeUp
        }
        XCTAssertTrue(closedFinal)
    }

    func testClosedElsewhereDetectedByHeartbeatWithin30sAndImmediatelyOnNextSave() async throws {
        // Case A: Closed remotely -> detected before/at next 30s heartbeat
        do {
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

            server.mutateState { json in
                json["state"] = "GRADING"
                json["endReason"] = "TIME_UP"
            }

            await clock.advance(by: .seconds(30))
            let detected = await clock.waitUntil {
                engine.state.closed != nil
            }
            XCTAssertTrue(detected, "Remote closure must be detected on next <=30s heartbeat")
        }

        // Case B: Closed remotely -> detected immediately on next save call without server accepting answer
        do {
            let clock = TestEngineClock()
            let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
            let item1 = server.firstLeafItemId(inPartIndex: 0)
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

            server.mutateState { json in
                json["state"] = "GRADING"
                json["endReason"] = "TIME_UP"
            }
            // Plan A6 reuse: certified 409 TIME_UP payload from finishPart reused for saveAnswers 409 TIME_UP
            server.enqueue(
                .saveAnswers,
                try .fixture(operationId: Operations.finishPart.id, status: 409, variant: "time-up")
            )

            engine.answer(itemId: item1, value: .singleChoice(optionId: "opt-too-late"))
            let closedOnSave = await clock.waitUntil {
                engine.state.closed == .timeUp
            }
            XCTAssertTrue(closedOnSave)
        }
    }
}
