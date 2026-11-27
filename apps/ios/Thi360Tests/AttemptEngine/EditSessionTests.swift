import Foundation
import XCTest
@testable import Thi360

@MainActor
final class EditSessionTests: XCTestCase {
    // AC-1: sessionId UUID generated and persisted BEFORE POST .../session; reused across retries and process recreation;
    // no save/heartbeat/finish/submit/choice/retry while acquiring; changes made while waiting flush immediately after claim.
    func testClaimPersistsBeforeCall() async throws {
        let clock = TestEngineClock()
        let (provider, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let claimGate = AttemptTestGate()
        server.setGate(claimGate, for: .claimSession)

        let expectedSessionId = "11111111-1111-4111-8111-111111111111"
        let itemId = server.firstLeafItemId()
        let partId = server.partId(at: 0)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { expectedSessionId }
        )
        defer { engine.dispose() }

        await claimGate.waitUntilEntered()

        // sessionId must already be persisted in AccountScopedStore BEFORE POST .../session completes.
        let persistedData = await store.read(namespace: "attempt", key: "session:\(server.attemptId)")
        XCTAssertEqual(persistedData.flatMap { String(data: $0, encoding: .utf8) }, expectedSessionId)
        XCTAssertEqual(engine.state.session, .acquiring)

        // While acquiring, answers go into draft only; no save/heartbeat/finish/submit/choice/retry calls fire.
        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        engine.finishPart(partId: partId)
        engine.submit()
        engine.choosePart(partId: partId, optionCode: "OPT")
        engine.retryGeneration(partId: partId)
        await clock.advance(by: .seconds(35))

        XCTAssertEqual(server.callCount(for: .saveAnswers), 0)
        XCTAssertEqual(server.callCount(for: .heartbeat), 0)
        XCTAssertEqual(server.callCount(for: .finishPart), 0)
        XCTAssertEqual(server.callCount(for: .submit), 0)
        XCTAssertEqual(server.callCount(for: .confirmPartChoice), 0)
        XCTAssertEqual(server.callCount(for: .retryPartGeneration), 0)
        XCTAssertEqual(engine.state.answers[itemId]?.sync, .pending)

        let draftData = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
        XCTAssertNotNil(draftData, "Answer edited while acquiring must be persisted in AccountScopedStore")

        // Release claim -> session transitions to .editing and queued answer flushes immediately with X-Attempt-Session.
        server.setGate(nil, for: .claimSession)
        await claimGate.releaseAll()
        let flushed = await clock.waitUntil {
            engine.state.session == .editing(sessionId: expectedSessionId)
                && server.callCount(for: .saveAnswers) == 1
                && engine.state.save == .saved
        }
        XCTAssertTrue(flushed)
        XCTAssertEqual(server.calls(for: .saveAnswers).first?.sessionHeader, expectedSessionId)

        // Recreate engine (process restart) on same AccountScopedStore -> reuses stored sessionId, does NOT enter OpenElsewhere.
        engine.dispose()
        let freshStore = try XCTUnwrap(provider.store(for: "account-A"))
        let reopened = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: freshStore,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { "99999999-9999-4999-8999-999999999999" }
        )
        defer { reopened.dispose() }

        let resumed = await clock.waitUntil {
            reopened.state.session == .editing(sessionId: expectedSessionId)
        }
        XCTAssertTrue(resumed)
        let lastClaimBody = server.calls(for: .claimSession).last?.decodedJSON()
        XCTAssertEqual(lastClaimBody?["sessionId"] as? String, expectedSessionId)
        XCTAssertNil(lastClaimBody?["takeover"])
    }

    // AC-1: every post-claim command carries X-Attempt-Session.
    func testHeaderOnEveryCommand() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-per-part", clock: clock)
        let expectedSessionId = "22222222-2222-4222-8222-222222222222"

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { expectedSessionId }
        )
        defer { engine.dispose() }

        let asyncAssertResult1 = await clock.waitUntil { engine.state.session == .editing(sessionId: expectedSessionId) }
        XCTAssertTrue(asyncAssertResult1)

        let itemId = server.firstLeafItemId()
        let partId = server.partId(at: 0)

        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        let asyncAssertResult2 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 1 }
        XCTAssertTrue(asyncAssertResult2)

        await clock.advance(by: .seconds(30))
        let asyncAssertResult3 = await clock.waitUntil { server.callCount(for: .heartbeat) >= 1 }
        XCTAssertTrue(asyncAssertResult3)

        // Switch part state to AWAITING_CHOICE for choice command, then GENERATION_FAILED for retry, then OPEN for finish + submit.
        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]] {
                parts[0]["state"] = "AWAITING_CHOICE"
                json["parts"] = parts
            }
        }
        engine.refresh()
        let asyncAssertResult4 = await clock.waitUntil { engine.state.attempt?.parts?.first?.state == .AWAITING_CHOICE }
        XCTAssertTrue(asyncAssertResult4)

        engine.choosePart(partId: partId, optionCode: "SCI", subjectCodes: ["LY", "HOA", "SINH"])
        let asyncAssertResult5 = await clock.waitUntil { server.callCount(for: .confirmPartChoice) == 1 }
        XCTAssertTrue(asyncAssertResult5)

        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]] {
                parts[0]["state"] = "GENERATION_FAILED"
                json["parts"] = parts
            }
        }
        engine.refresh()
        let asyncAssertResult6 = await clock.waitUntil { engine.state.attempt?.parts?.first?.state == .GENERATION_FAILED }
        XCTAssertTrue(asyncAssertResult6)

        engine.retryGeneration(partId: partId)
        let asyncAssertResult7 = await clock.waitUntil { server.callCount(for: .retryPartGeneration) == 1 }
        XCTAssertTrue(asyncAssertResult7)

        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]] {
                parts[0]["state"] = "OPEN"
                json["parts"] = parts
            }
        }
        engine.refresh()
        let asyncAssertResult8 = await clock.waitUntil { engine.state.attempt?.parts?.first?.state == .OPEN }
        XCTAssertTrue(asyncAssertResult8)

        engine.finishPart(partId: partId)
        let asyncAssertResult9 = await clock.waitUntil { server.callCount(for: .finishPart) == 1 }
        XCTAssertTrue(asyncAssertResult9)

        // Reopen state for submit verification
        server.mutateState { json in
            json["state"] = "IN_PROGRESS"
            if var parts = json["parts"] as? [[String: Any]] {
                parts[0]["state"] = "OPEN"
                json["parts"] = parts
            }
        }
        engine.refresh()
        let asyncAssertResult10 = await clock.waitUntil { engine.state.attempt?.state == .IN_PROGRESS }
        XCTAssertTrue(asyncAssertResult10)

        engine.submit()
        let asyncAssertResult11 = await clock.waitUntil { server.callCount(for: .submit) == 1 }
        XCTAssertTrue(asyncAssertResult11)

        for endpoint in [
            FakeAttemptServer.Endpoint.saveAnswers,
            .heartbeat,
            .confirmPartChoice,
            .retryPartGeneration,
            .finishPart,
            .submit
        ] {
            let calls = server.calls(for: endpoint)
            XCTAssertFalse(calls.isEmpty, "Expected at least one call for \(endpoint)")
            for call in calls {
                XCTAssertEqual(call.sessionHeader, expectedSessionId, "Missing/wrong X-Attempt-Session on \(endpoint)")
            }
        }
    }

    // AC-2: 409 TIME_UP / ATTEMPT_NOT_OPEN -> reload & closed != nil; network/5xx -> Acquiring, Unsaved/Offline,
    // finishPart/submit ignored, retry with same sessionId at 1,2,4,8,16,30,30...s and immediately on reconnect.
    func testClaimFailures() async throws {
        // Subcase A: 409 TIME_UP & ATTEMPT_NOT_OPEN
        for (variant, expectedClosed) in [
            ("time-up", ClosedReason.timeUp),
            ("attempt-not-open", ClosedReason.notOpen)
        ] {
            let clock = TestEngineClock()
            let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
            defer { try? FileManager.default.removeItem(at: dir) }
            let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
            let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
            server.enqueue(
                .claimSession,
                try .fixture(operationId: Operations.claimSession.id, status: 409, variant: variant)
            )
            server.enqueue(
                .getState,
                try .fixture(operationId: Operations.getState.id, status: 200, variant: "in-progress-shared") { obj in
                    obj["deadlineAt"] = FakeAttemptServer.isoString(server.currentServerNow.addingTimeInterval(1800))
                }
            )
            server.enqueue(
                .getState,
                try .fixture(
                    operationId: Operations.getState.id,
                    status: 200,
                    variant: variant == "time-up" ? "closed-time-up" : "closed-submitted"
                )
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

            let closed = await clock.waitUntil { engine.state.closed != nil }
            XCTAssertTrue(closed, "Expected closed != nil for claim 409 \(variant)")
            XCTAssertEqual(engine.state.closed, expectedClosed)
            XCTAssertGreaterThanOrEqual(server.callCount(for: .getState), 2)
        }

        // Subcase B: Network & 5xx backoff schedule 1, 2, 4, 8, 16, 30, 30 and immediate retry on reconnect
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let expectedSessionId = "33333333-3333-4333-8333-333333333333"
        let itemId = server.firstLeafItemId()
        let partId = server.partId(at: 0)

        server.enqueue(.claimSession, responses: [
            .networkError(.timedOut),
            try .serverError(statusCode: 503),
            try .serverError(statusCode: 500),
            try .serverError(statusCode: 502),
            try .serverError(statusCode: 503),
            try .serverError(statusCode: 503),
            try .serverError(statusCode: 503),
            .networkError(.networkConnectionLost)
        ])

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { expectedSessionId }
        )
        defer { engine.dispose() }

        let asyncAssertResult12 = await clock.waitUntil { server.callCount(for: .claimSession) == 1 }
        XCTAssertTrue(asyncAssertResult12)
        XCTAssertEqual(engine.state.session, .acquiring)
        XCTAssertEqual(engine.state.save, .unsaved(.networkError))

        // Answer while acquiring, verify finishPart & submit are ignored
        engine.answer(itemId: itemId, value: .singleChoice(optionId: "b"))
        engine.finishPart(partId: partId)
        engine.submit()
        XCTAssertEqual(server.callCount(for: .finishPart), 0)
        XCTAssertEqual(server.callCount(for: .submit), 0)
        XCTAssertEqual(server.callCount(for: .saveAnswers), 0)

        let expectedDelays = [1, 2, 4, 8, 16, 30, 30]
        for (idx, delaySec) in expectedDelays.enumerated() {
            let before = server.callCount(for: .claimSession)
            if delaySec > 1 {
                await clock.advance(by: .seconds(delaySec - 1))
                XCTAssertEqual(server.callCount(for: .claimSession), before, "Must not retry before \(delaySec)s at step \(idx)")
                await clock.advance(by: .seconds(1))
            } else {
                await clock.advance(by: .seconds(1))
            }
            let asyncAssertResult13 = await clock.waitUntil { server.callCount(for: .claimSession) == before + 1 }
            XCTAssertTrue(asyncAssertResult13)
            if idx < 6 {
                XCTAssertEqual(engine.state.save, .unsaved(.serverError))
            }
        }

        // Go offline -> save = .offline; back online -> immediate retry (without waiting 30s) succeeds and flushes unsaved answer!
        connectivity.applyUpdate(false)
        await clock.settle()
        XCTAssertEqual(engine.state.save, .offline)

        connectivity.applyUpdate(true)
        let recovered = await clock.waitUntil {
            engine.state.session == .editing(sessionId: expectedSessionId)
                && server.callCount(for: .saveAnswers) == 1
                && engine.state.save == .saved
        }
        XCTAssertTrue(recovered)

        // Every single claim retry used the exact same persisted sessionId
        for call in server.calls(for: .claimSession) {
            XCTAssertEqual(call.decodedJSON()?["sessionId"] as? String, expectedSessionId)
        }
    }

    // AC-3: 409 ATTEMPT_OPEN_ELSEWHERE -> stays OpenElsewhere forever (no auto takeover); answer/flag/choosePart ignored;
    // takeover() sends takeover: true with stored sessionId, single in-flight, persists new sessionId, reloads, enters Editing.
    func testOpenElsewhereAndTakeover() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let otherLastSeen = FlexibleISO8601DateTranscoder.parse("2026-11-20T09:58:00Z")!
        server.setActiveSession("other-device-session", lastSeenAt: otherLastSeen)

        let storedId = "44444444-4444-4444-8444-444444444444"
        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { storedId }
        )
        defer { engine.dispose() }

        let asyncAssertResult14 = await clock.waitUntil {
            if case let .openElsewhere(seen) = engine.state.session {
                return seen == otherLastSeen
            }
            return false
        }
        XCTAssertTrue(asyncAssertResult14)

        // Waiting 10 minutes must never auto-takeover
        await clock.advance(by: .seconds(600))
        XCTAssertEqual(server.callCount(for: .claimSession), 1)
        XCTAssertEqual(engine.state.session, .openElsewhere(lastSeenAt: otherLastSeen))

        // answer, flag, choosePart must be complete no-ops in OpenElsewhere
        let itemId = server.firstLeafItemId()
        let partId = server.partId(at: 0)
        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        engine.flag(itemId: itemId, flagged: true)
        engine.choosePart(partId: partId, optionCode: "OPT")
        await clock.settle()

        XCTAssertNil(engine.state.answers[itemId]?.value)
        XCTAssertEqual(engine.state.answers[itemId]?.flagged, false)
        XCTAssertEqual(server.callCount(for: .saveAnswers), 0)
        XCTAssertEqual(server.callCount(for: .confirmPartChoice), 0)

        // Single in-flight takeover()
        let takeoverGate = AttemptTestGate()
        server.setGate(takeoverGate, for: .claimSession)
        engine.takeover()
        engine.takeover()
        await takeoverGate.waitUntilEntered()
        XCTAssertEqual(engine.state.command, .sending(.takeover))
        XCTAssertEqual(server.callCount(for: .claimSession), 2, "Duplicate takeover() while sending must be ignored")

        server.setGate(nil, for: .claimSession)
        await takeoverGate.releaseAll()

        let tookOver = await clock.waitUntil {
            if case .editing = engine.state.session {
                return engine.state.command == .idle && server.callCount(for: .getState) >= 2
            }
            return false
        }
        XCTAssertTrue(tookOver)

        let takeoverCall = try XCTUnwrap(server.calls(for: .claimSession).last)
        XCTAssertEqual(takeoverCall.decodedJSON()?["takeover"] as? Bool, true)
        XCTAssertEqual(takeoverCall.decodedJSON()?["sessionId"] as? String, storedId)

        guard case let .editing(newSessionId) = engine.state.session else {
            return XCTFail("Expected editing state after takeover")
        }
        XCTAssertNotEqual(newSessionId, storedId)
        let savedSession = await store.read(namespace: "attempt", key: "session:\(server.attemptId)")
        XCTAssertEqual(savedSession.flatMap { String(data: $0, encoding: .utf8) }, newSessionId)
    }
}
