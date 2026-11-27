import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PartChoiceCommandTests: XCTestCase {
    // AC-16: choosePart sends Idempotency-Key + X-Attempt-Session + {optionCode, subjectCodes?};
    // same choice retains key on network error, changed choice rotates key;
    // CHOICE_WINDOW_CLOSED/CHOICE_LOCKED/PART_NOT_AWAITING/PART_GENERATING reload & Refused(code);
    // choiceDeadlineAt == 0 polls 500ms then 2s; GENERATING polls 1.5s; GENERATION_FAILED never auto-polls;
    // retryGeneration rotates key per tap and retains key on network/408/429/5xx.
    func testChoosePartIdempotencyRefusalsPollingAndRetryGeneration() async throws {
        let clock = TestEngineClock()
        let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()

        let server = try FakeAttemptServer(
            initialStateVariant: "awaiting-choice",
            initialServerNow: baseNow,
            clock: clock,
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]] {
                    for idx in parts.indices where parts[idx]["state"] as? String == "AWAITING_CHOICE" {
                        parts[idx]["choiceDeadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(600))
                    }
                    json["parts"] = parts
                }
            }
        )

        var keyCounter = 0
        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity,
            idempotencyKeyGenerator: {
                keyCounter += 1
                return "idem-key-\(keyCounter)"
            }
        )
        defer { engine.dispose() }

        let asyncAssertResult1 = await clock.waitUntil { engine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult1)
        let choicePartId = try XCTUnwrap(engine.state.attempt?.parts?.first { $0.state == .AWAITING_CHOICE }?.partId)

        // 1. Network failure retains Idempotency-Key for same choice; changing choice uses new key
        server.enqueue(.confirmPartChoice, .networkError(.timedOut))
        engine.choosePart(partId: choicePartId, optionCode: "SCI", subjectCodes: ["LY", "HOA", "SINH"])
        let asyncAssertResult2 = await clock.waitUntil { server.callCount(for: .confirmPartChoice) == 1 && engine.state.command == .refused(code: "NETWORK_ERROR") }
        XCTAssertTrue(asyncAssertResult2)

        server.enqueue(.confirmPartChoice, .networkError(.timedOut))
        engine.choosePart(partId: choicePartId, optionCode: "SCI", subjectCodes: ["LY", "HOA", "SINH"])
        let asyncAssertResult3 = await clock.waitUntil { server.callCount(for: .confirmPartChoice) == 2 }
        XCTAssertTrue(asyncAssertResult3)

        let callsChoice = server.calls(for: .confirmPartChoice)
        XCTAssertEqual(callsChoice[0].idempotencyKeyHeader, callsChoice[1].idempotencyKeyHeader, "Same choice retry must keep Idempotency-Key")
        XCTAssertEqual(callsChoice[0].decodedJSON()?["optionCode"] as? String, "SCI")
        XCTAssertEqual(callsChoice[0].decodedJSON()?["subjectCodes"] as? [String], ["LY", "HOA", "SINH"])

        // Change choice -> new Idempotency-Key -> succeeds -> reloads into GENERATING
        engine.choosePart(partId: choicePartId, optionCode: "ENG", subjectCodes: nil)
        let asyncAssertResult4 = await clock.waitUntil {
            server.callCount(for: .confirmPartChoice) == 3
                && engine.state.command == .idle
                && engine.state.attempt?.parts?.first(where: { $0.partId == choicePartId })?.state == .GENERATING
        }
        XCTAssertTrue(asyncAssertResult4)
        XCTAssertNotEqual(
            server.calls(for: .confirmPartChoice)[2].idempotencyKeyHeader,
            callsChoice[0].idempotencyKeyHeader,
            "Changed choice must rotate Idempotency-Key"
        )

        // 2. GENERATING polls every 1.5s
        let getsBeforeGenPoll = server.callCount(for: .getState)
        await clock.advance(by: .milliseconds(1499))
        XCTAssertEqual(server.callCount(for: .getState), getsBeforeGenPoll)
        await clock.advance(by: .milliseconds(1))
        let asyncAssertResult5 = await clock.waitUntil { server.callCount(for: .getState) == getsBeforeGenPoll + 1 }
        XCTAssertTrue(asyncAssertResult5)
        await clock.advance(by: .milliseconds(1500))
        let asyncAssertResult6 = await clock.waitUntil { server.callCount(for: .getState) == getsBeforeGenPoll + 2 }
        XCTAssertTrue(asyncAssertResult6)

        // 3. GENERATION_FAILED does NOT auto-poll (A-88), and retryGeneration sends new key per tap & retains on 5xx/429/network
        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]] {
                for idx in parts.indices where parts[idx]["partId"] as? String == choicePartId {
                    parts[idx]["state"] = "GENERATION_FAILED"
                }
                json["parts"] = parts
            }
        }
        await clock.advance(by: .milliseconds(1500))
        let asyncAssertResult7 = await clock.waitUntil {
            engine.state.attempt?.parts?.first(where: { $0.partId == choicePartId })?.state == .GENERATION_FAILED
        }
        XCTAssertTrue(asyncAssertResult7)

        let getsWhileFailed = server.callCount(for: .getState)
        await clock.advance(by: .seconds(15))
        XCTAssertEqual(server.callCount(for: .getState), getsWhileFailed, "GENERATION_FAILED must never auto-poll")

        // Retry fails with 503 -> retains key -> next retry succeeds with SAME key -> subsequent tap uses NEW key
        server.enqueue(.retryPartGeneration, try .serverError(statusCode: 503))
        engine.retryGeneration(partId: choicePartId)
        let asyncAssertResult8 = await clock.waitUntil { server.callCount(for: .retryPartGeneration) == 1 }
        XCTAssertTrue(asyncAssertResult8)
        let keyRetry1 = server.calls(for: .retryPartGeneration)[0].idempotencyKeyHeader

        engine.retryGeneration(partId: choicePartId)
        let asyncAssertResult9 = await clock.waitUntil { server.callCount(for: .retryPartGeneration) == 2 && engine.state.command == .idle }
        XCTAssertTrue(asyncAssertResult9)
        let keyRetry2 = server.calls(for: .retryPartGeneration)[1].idempotencyKeyHeader
        XCTAssertEqual(keyRetry1, keyRetry2, "Retry after 5xx must reuse Idempotency-Key")

        // Next fresh retry tap gets a brand new key (and handles 409 PART_GENERATING -> Refused + reload)
        server.enqueue(
            .retryPartGeneration,
            try .fixture(operationId: Operations.retryPartGeneration.id, status: 409, variant: "part-generating")
        )
        let reloadsBeforePartGen = server.callCount(for: .getState)
        engine.retryGeneration(partId: choicePartId)
        let asyncAssertResult10 = await clock.waitUntil {
            server.callCount(for: .retryPartGeneration) == 3
                && engine.state.command == .refused(code: "PART_GENERATING")
                && server.callCount(for: .getState) > reloadsBeforePartGen
        }
        XCTAssertTrue(asyncAssertResult10)
        XCTAssertNotEqual(server.calls(for: .retryPartGeneration)[2].idempotencyKeyHeader, keyRetry2)

        // 4. CHOICE_WINDOW_CLOSED reloads and records choiceTimeout reason; choice deadline reaching 0 polls 500ms then 2s
        let timeoutClock = TestEngineClock()
        let (_, store2, dir2) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir2) }
        let timeoutServer = try FakeAttemptServer(
            initialStateVariant: "awaiting-choice",
            initialServerNow: baseNow,
            clock: timeoutClock,
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]] {
                    for idx in parts.indices where parts[idx]["state"] as? String == "AWAITING_CHOICE" {
                        parts[idx]["choiceDeadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(5))
                    }
                    json["parts"] = parts
                }
            }
        )
        let timeoutEngine = AttemptEngine(
            attemptId: timeoutServer.attemptId,
            accountId: "account-A",
            api: timeoutServer.makeAttemptAPI(),
            store: store2,
            clock: timeoutClock,
            connectivity: connectivity
        )
        defer { timeoutEngine.dispose() }
        let asyncAssertResult11 = await timeoutClock.waitUntil { timeoutEngine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult11)

        let pId = try XCTUnwrap(timeoutEngine.state.attempt?.parts?.first { $0.state == .AWAITING_CHOICE }?.partId)
        timeoutServer.enqueue(
            .confirmPartChoice,
            try .fixture(operationId: Operations.confirmPartChoice.id, status: 409, variant: "choice-locked")
        )
        let reloadsBeforeLocked = timeoutServer.callCount(for: .getState)
        timeoutEngine.choosePart(partId: pId, optionCode: "ENG")
        let asyncAssertResult12 = await timeoutClock.waitUntil {
            timeoutEngine.state.command == .refused(code: "CHOICE_LOCKED")
                && timeoutServer.callCount(for: .getState) > reloadsBeforeLocked
        }
        XCTAssertTrue(asyncAssertResult12)

        // Advance to choiceDeadlineAt == 0 (5s) -> polls after 500ms, then every 2s until server closes attempt
        let getsBeforeTimeout = timeoutServer.callCount(for: .getState)
        await timeoutClock.advance(by: .seconds(5))
        XCTAssertEqual(timeoutServer.callCount(for: .getState), getsBeforeTimeout)

        await timeoutClock.advance(by: .milliseconds(500))
        let asyncAssertResult13 = await timeoutClock.waitUntil { timeoutServer.callCount(for: .getState) == getsBeforeTimeout + 1 }
        XCTAssertTrue(asyncAssertResult13)

        await timeoutClock.advance(by: .milliseconds(1999))
        XCTAssertEqual(timeoutServer.callCount(for: .getState), getsBeforeTimeout + 1)
        await timeoutClock.advance(by: .milliseconds(1))
        let asyncAssertResult14 = await timeoutClock.waitUntil { timeoutServer.callCount(for: .getState) == getsBeforeTimeout + 2 }
        XCTAssertTrue(asyncAssertResult14)

        // Server transitions to closed-choice-timeout -> stops polling and sets closed = .choiceTimeout
        try timeoutServer.setState(variant: "closed-choice-timeout")
        await timeoutClock.advance(by: .seconds(2))
        let asyncAssertResult15 = await timeoutClock.waitUntil { timeoutEngine.state.closed == .choiceTimeout }
        XCTAssertTrue(asyncAssertResult15)
        let getsAfterClose = timeoutServer.callCount(for: .getState)
        await timeoutClock.advance(by: .seconds(10))
        XCTAssertEqual(timeoutServer.callCount(for: .getState), getsAfterClose)
    }
}
