import Foundation
import XCTest
@testable import Thi360

@MainActor
final class OfflineTests: XCTestCase {
    func testOfflineEditsClockAndOnlineReconcileFlush() async throws {
        let clock = TestEngineClock()
        let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(
            initialStateVariant: "in-progress-shared",
            initialServerNow: baseNow,
            clock: clock,
            mutateInitialState: { json in
                json["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(600))
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

        let items = server.leafItemIds()
        let partId = server.partId(at: 0)

        server.enqueue(.saveAnswers, responses: [
            try .serverError(statusCode: 503),
            try .serverError(statusCode: 503)
        ])
        engine.answer(itemId: items[0], value: .singleChoice(optionId: "a"))
        let err1 = await clock.waitUntil {
            server.callCount(for: .saveAnswers) == 1 && engine.state.save == .unsaved(.serverError)
        }
        XCTAssertTrue(err1)
        await engine.waitForIdleForTesting()
        await clock.settle()
        await clock.advance(by: .seconds(1))
        let err2 = await clock.waitUntil {
            server.callCount(for: .saveAnswers) == 2 && engine.state.save == .unsaved(.serverError)
        }
        XCTAssertTrue(err2)
        await engine.waitForIdleForTesting()
        await clock.settle()

        connectivity.applyUpdate(false)
        await clock.settle()
        XCTAssertEqual(engine.state.network, .offline)
        XCTAssertEqual(engine.state.save, .offline)

        let savesWhileOnline = server.callCount(for: .saveAnswers)
        let remainingBefore = engine.state.clock.remainingSecondsCeil ?? 0

        engine.answer(itemId: items[1], value: .singleChoice(optionId: "b"))
        engine.flag(itemId: items[1], flagged: true)
        engine.position(partId: partId, itemId: items[1])

        await clock.advance(by: .seconds(10))
        XCTAssertEqual(server.callCount(for: .saveAnswers), savesWhileOnline, "Zero saves allowed while offline")
        XCTAssertEqual(engine.state.answers[items[1]]?.sync, .pending)
        XCTAssertEqual(engine.state.network, .offline)
        XCTAssertEqual(engine.state.save, .offline)
        XCTAssertLessThan(engine.state.clock.remainingSecondsCeil ?? remainingBefore, remainingBefore)

        let draftData = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(items[1])")
        let posData = await store.read(namespace: "attempt", key: "position:\(server.attemptId)")
        XCTAssertNotNil(draftData)
        XCTAssertNotNil(posData)

        let getsBefore = server.callCount(for: .getState)
        connectivity.applyUpdate(true)
        let syncedOnline = await clock.waitUntil {
            server.callCount(for: .getState) > getsBefore
                && engine.state.network == .online
                && engine.state.save == .saved
                && engine.state.answers[items[0]]?.sync == .saved
                && engine.state.answers[items[1]]?.sync == .saved
        }
        XCTAssertTrue(syncedOnline)

        server.resetCalls()
        server.enqueue(.saveAnswers, try .serverError(statusCode: 500))
        engine.answer(itemId: items[0], value: .singleChoice(optionId: "c"))
        let errAfterOnline = await clock.waitUntil {
            server.callCount(for: .saveAnswers) == 1 && engine.state.save == .unsaved(.serverError)
        }
        XCTAssertTrue(errAfterOnline)
        await engine.waitForIdleForTesting()
        await clock.settle()
        await clock.advance(by: .seconds(1))
        let recoveredAt1s = await clock.waitUntil { server.callCount(for: .saveAnswers) == 2 && engine.state.save == .saved }
        XCTAssertTrue(recoveredAt1s)
    }
}
