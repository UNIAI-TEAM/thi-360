import Foundation
import XCTest
@testable import Thi360

@MainActor
final class EditSessionTests: XCTestCase {
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

        let persistedData = await store.read(namespace: "attempt", key: "session:\(server.attemptId)")
        XCTAssertEqual(persistedData.flatMap { String(data: $0, encoding: .utf8) }, expectedSessionId)
        XCTAssertEqual(engine.state.session, .acquiring)

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

        server.setGate(nil, for: .claimSession)
        await claimGate.releaseAll()
        let flushed = await clock.waitUntil {
            engine.state.session == .editing(sessionId: expectedSessionId)
                && server.callCount(for: .saveAnswers) == 1
                && engine.state.save == .saved
        }
        XCTAssertTrue(flushed)
        XCTAssertEqual(server.calls(for: .saveAnswers).first?.sessionHeader, expectedSessionId)

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

    func testClaimFailures() async throws {
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

        for call in server.calls(for: .claimSession) {
            XCTAssertEqual(call.decodedJSON()?["sessionId"] as? String, expectedSessionId)
        }
    }

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

        await clock.advance(by: .seconds(600))
        XCTAssertEqual(server.callCount(for: .claimSession), 1)
        XCTAssertEqual(engine.state.session, .openElsewhere(lastSeenAt: otherLastSeen))

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

        let firstTakeoverGets = server.callCount(for: .getState)
        server.enqueue(.getState, try .serverError(statusCode: 503))
        engine.takeover()
        let failedReload = await clock.waitUntil {
            server.callCount(for: .claimSession) == 2
                && server.callCount(for: .getState) == firstTakeoverGets + 1
                && engine.state.command == .refused(code: "SERVICE_UNAVAILABLE")
        }
        XCTAssertTrue(failedReload)
        XCTAssertEqual(
            engine.state.session,
            .openElsewhere(lastSeenAt: otherLastSeen),
            "Failed getState during takeover must NOT transition session to .editing"
        )
        let firstTakeoverCall = try XCTUnwrap(server.calls(for: .claimSession).last)
        XCTAssertEqual(firstTakeoverCall.decodedJSON()?["takeover"] as? Bool, true)
        XCTAssertEqual(firstTakeoverCall.decodedJSON()?["sessionId"] as? String, storedId)
        let persistedAfterFailedReload = await store.read(namespace: "attempt", key: "session:\(server.attemptId)")
        let firstGrantedSessionId = try XCTUnwrap(persistedAfterFailedReload.flatMap { String(data: $0, encoding: .utf8) })
        XCTAssertNotEqual(firstGrantedSessionId, storedId)

        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        XCTAssertNil(engine.state.answers[itemId]?.value, "Editing must remain blocked after failed takeover reload")

        let takeoverGate = AttemptTestGate()
        let getStateGate = AttemptTestGate()
        server.setGate(takeoverGate, for: .claimSession)
        server.setGate(getStateGate, for: .getState)
        engine.takeover()
        engine.takeover()
        await takeoverGate.waitUntilEntered()
        XCTAssertEqual(engine.state.command, .sending(.takeover))
        XCTAssertEqual(server.callCount(for: .claimSession), 3, "Duplicate takeover() while sending must be ignored")

        server.setGate(nil, for: .claimSession)
        await takeoverGate.releaseAll()
        await getStateGate.waitUntilEntered()

        XCTAssertEqual(
            engine.state.session,
            .openElsewhere(lastSeenAt: otherLastSeen),
            "Session must stay .openElsewhere until GET /attempts/{id} finishes reloading"
        )
        XCTAssertEqual(engine.state.command, .sending(.takeover))
        engine.takeover()
        engine.answer(itemId: itemId, value: .singleChoice(optionId: "b"))
        XCTAssertEqual(server.callCount(for: .claimSession), 3, "Duplicate takeover() during getState must be ignored")
        XCTAssertNil(engine.state.answers[itemId]?.value, "Editing must stay blocked while takeover getState is in flight")

        server.setGate(nil, for: .getState)
        await getStateGate.releaseAll()

        let tookOver = await clock.waitUntil {
            if case .editing = engine.state.session {
                return engine.state.command == .idle && server.callCount(for: .getState) >= firstTakeoverGets + 2
            }
            return false
        }
        XCTAssertTrue(tookOver)

        let takeoverCall = try XCTUnwrap(server.calls(for: .claimSession).last)
        XCTAssertEqual(takeoverCall.decodedJSON()?["takeover"] as? Bool, true)
        XCTAssertEqual(takeoverCall.decodedJSON()?["sessionId"] as? String, firstGrantedSessionId)

        guard case let .editing(newSessionId) = engine.state.session else {
            return XCTFail("Expected editing state after takeover")
        }
        XCTAssertNotEqual(newSessionId, storedId)
        let savedSession = await store.read(namespace: "attempt", key: "session:\(server.attemptId)")
        XCTAssertEqual(savedSession.flatMap { String(data: $0, encoding: .utf8) }, newSessionId)
    }
}
