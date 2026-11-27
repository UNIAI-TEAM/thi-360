import Foundation
import XCTest
@testable import Thi360

@MainActor
final class SaveErrorTests: XCTestCase {
    // AC-10: 429 Retry-After 0, 1, n, missing; 5xx -> ServerError; 4xx != 401/409/429 & online network -> NetworkError;
    // backoff 1,2,4,8,16,30,30 & reset on success/online; 409 TIME_UP/ATTEMPT_NOT_OPEN/PART_CLOSED reload; 409 session -> SessionConflict + Evicted.
    func testRateLimitRetryAfterBoundariesAndExponentialBackoff() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)

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

        let itemId = server.firstLeafItemId()

        // 1. 429 Retry-After: 0 -> clamps delay to max(0, 1) = 1s, does not advance backoff
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: ["Retry-After": "0"])
        )
        // 2. 429 Retry-After: 1 -> waits 1s
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: ["Retry-After": "1"])
        )
        // 3. 429 Retry-After: 5 -> waits 5s
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: ["Retry-After": "5"])
        )
        // 4. 429 missing Retry-After (A6 documented header omission on certified fixture) -> backoff step 1 = 1s, step 2 = 2s
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: [:])
        )
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: [:])
        )

        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        let s1 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 1 }
        XCTAssertTrue(s1)
        XCTAssertEqual(engine.state.save, .unsaved(.rateLimited(1)))

        await clock.advance(by: .seconds(1))
        let s2 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 2 }
        XCTAssertTrue(s2)
        XCTAssertEqual(engine.state.save, .unsaved(.rateLimited(1)))

        await clock.advance(by: .seconds(1))
        let s3 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 3 }
        XCTAssertTrue(s3)
        XCTAssertEqual(engine.state.save, .unsaved(.rateLimited(5)))

        await clock.advance(by: .seconds(4))
        XCTAssertEqual(server.callCount(for: .saveAnswers), 3)
        await clock.advance(by: .seconds(1))
        let s4 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 4 }
        XCTAssertTrue(s4)
        XCTAssertEqual(engine.state.save, .unsaved(.rateLimited(1)))

        await clock.advance(by: .seconds(1))
        let s5 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 5 }
        XCTAssertTrue(s5)
        XCTAssertEqual(engine.state.save, .unsaved(.rateLimited(2)))

        await clock.advance(by: .seconds(1))
        XCTAssertEqual(server.callCount(for: .saveAnswers), 5)
        await clock.advance(by: .seconds(1))
        let s6 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 6 && engine.state.save == .saved }
        XCTAssertTrue(s6)

        // 5. Full exponential backoff 1, 2, 4, 8, 16, 30, 30 across 5xx, 400, and online network error, then reset on success
        server.resetCalls()
        server.enqueue(.saveAnswers, responses: [
            try .serverError(statusCode: 503),
            try .json(statusCode: 400, object: ["code": "BAD_REQUEST", "message": "bad", "fieldErrors": [], "traceId": "t"]),
            .networkError(.timedOut),
            try .serverError(statusCode: 500),
            try .serverError(statusCode: 502),
            try .serverError(statusCode: 503),
            try .serverError(statusCode: 503)
        ])

        engine.answer(itemId: itemId, value: .singleChoice(optionId: "b"))
        let expected: [(SaveFailureReason, Int)] = [
            (.serverError, 1),
            (.networkError, 2),
            (.networkError, 4),
            (.serverError, 8),
            (.serverError, 16),
            (.serverError, 30),
            (.serverError, 30)
        ]

        for (index, (expectedReason, delaySec)) in expected.enumerated() {
            let arrived = await clock.waitUntil { server.callCount(for: .saveAnswers) == index + 1 }
            XCTAssertTrue(arrived)
            XCTAssertEqual(engine.state.save, .unsaved(expectedReason), "Step \(index)")
            if delaySec > 1 {
                await clock.advance(by: .seconds(delaySec - 1))
                XCTAssertEqual(server.callCount(for: .saveAnswers), index + 1, "Must not retry early at step \(index)")
                await clock.advance(by: .seconds(1))
            } else {
                await clock.advance(by: .seconds(1))
            }
        }
        let s8 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 8 && engine.state.save == .saved }
        XCTAssertTrue(s8)

        // Verify backoff reset back to 1s after success
        server.resetCalls()
        server.enqueue(.saveAnswers, try .serverError(statusCode: 500))
        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        let resetErr = await clock.waitUntil { server.callCount(for: .saveAnswers) == 1 && engine.state.save == .unsaved(.serverError) }
        XCTAssertTrue(resetErr)
        await clock.advance(by: .seconds(1))
        let resetOk = await clock.waitUntil { server.callCount(for: .saveAnswers) == 2 && engine.state.save == .saved }
        XCTAssertTrue(resetOk)

        // 6. 409 TIME_UP reloads state and closes
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.finishPart.id, status: 409, variant: "time-up")
        )
        try server.setState(variant: "closed-time-up")
        let reloadsBefore = server.callCount(for: .getState)
        engine.answer(itemId: itemId, value: .singleChoice(optionId: "b"))
        let closed409 = await clock.waitUntil {
            server.callCount(for: .getState) > reloadsBefore && engine.state.closed == .timeUp
        }
        XCTAssertTrue(closed409)
    }
}
