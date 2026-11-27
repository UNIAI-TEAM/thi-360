import Foundation
import XCTest
@testable import Thi360

@MainActor
final class EvictionTests: XCTestCase {
    func testEvictionAcrossAllSixCommandsAndReportSessionRevoked() async throws {
        enum Trigger {
            case heartbeatRevoked
            case heartbeatOpenElsewhere
            case saveRevoked
            case saveOpenElsewhere
            case choosePartRevoked
            case retryGenerationRevoked
            case finishPartRevoked
            case submitRevoked
            case audioReportSessionRevoked
        }

        let triggers: [Trigger] = [
            .heartbeatRevoked,
            .heartbeatOpenElsewhere,
            .saveRevoked,
            .saveOpenElsewhere,
            .choosePartRevoked,
            .retryGenerationRevoked,
            .finishPartRevoked,
            .submitRevoked,
            .audioReportSessionRevoked
        ]

        for trigger in triggers {
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

            var receivedEvents: [EngineEvent] = []
            let eventStream = engine.events
            let eventTask = Task {
                for await event in eventStream {
                    receivedEvents.append(event)
                }
            }
            defer { eventTask.cancel() }
            await clock.settle()

            let itemId = server.firstLeafItemId()
            let partId = server.partId(at: 0)

            // Plan A6 documented derivation for ATTEMPT_OPEN_ELSEWHERE on non-claim endpoints:
            // reuse certified claimSession 409 ATTEMPT_OPEN_ELSEWHERE fixture.
            let openElsewhere409 = try FakeAttemptServer.OverrideResponse.fixture(
                operationId: Operations.claimSession.id,
                status: 409,
                variant: "attempt-open-elsewhere"
            )
            let revoked409 = try FakeAttemptServer.OverrideResponse.fixture(
                operationId: Operations.saveAnswers.id,
                status: 409,
                variant: "attempt-session-revoked"
            )

            switch trigger {
            case .heartbeatRevoked:
                server.enqueue(.heartbeat, revoked409)
                await clock.advance(by: .seconds(30))
            case .heartbeatOpenElsewhere:
                server.enqueue(.heartbeat, openElsewhere409)
                await clock.advance(by: .seconds(30))
            case .saveRevoked:
                server.enqueue(.saveAnswers, revoked409)
                engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
            case .saveOpenElsewhere:
                server.enqueue(.saveAnswers, openElsewhere409)
                engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
            case .choosePartRevoked:
                server.mutateState { json in
                    if var parts = json["parts"] as? [[String: Any]] {
                        parts[0]["state"] = "AWAITING_CHOICE"
                        json["parts"] = parts
                    }
                }
                engine.refresh()
                let asyncAssertResult2 = await clock.waitUntil { engine.state.attempt?.parts?.first?.state == .AWAITING_CHOICE }
                XCTAssertTrue(asyncAssertResult2)
                server.enqueue(.confirmPartChoice, revoked409)
                engine.choosePart(partId: partId, optionCode: "SCI")
            case .retryGenerationRevoked:
                server.enqueue(.retryPartGeneration, revoked409)
                engine.retryGeneration(partId: partId)
            case .finishPartRevoked:
                server.enqueue(.finishPart, revoked409)
                engine.finishPart(partId: partId)
            case .submitRevoked:
                server.enqueue(.submit, revoked409)
                engine.submit()
            case .audioReportSessionRevoked:
                engine.reportSessionRevoked()
            }

            let evicted = await clock.waitUntil {
                engine.state.session == .evicted
                    && engine.state.command == .idle
                    && receivedEvents.contains(.sessionLost)
            }
            XCTAssertTrue(evicted, "Expected Evicted + SessionLost for trigger \(trigger)")

            let callsAfterEviction = server.allCalls.count
            engine.answer(itemId: itemId, value: .singleChoice(optionId: "b"))
            engine.flag(itemId: itemId, flagged: true)
            await clock.advance(by: .seconds(60))
            XCTAssertEqual(server.allCalls.count, callsAfterEviction, "No calls may fire after eviction (\(trigger))")
        }
    }

    func testExternalTakeoverDetectedWithin30sDuringInFlightSaveAndRecoverable() async throws {
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

        let asyncAssertResult3 = await clock.waitUntil { engine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult3)

        await clock.advance(by: .seconds(12))
        server.simulateExternalTakeover(newSessionId: "device-B-session")
        await clock.advance(by: .seconds(18))

        let asyncAssertResult4 = await clock.waitUntil { engine.state.session == .evicted }
        XCTAssertTrue(asyncAssertResult4)

        engine.takeover()
        let asyncAssertResult5 = await clock.waitUntil {
            if case .editing = engine.state.session {
                return engine.state.command == .idle
            }
            return false
        }
        XCTAssertTrue(asyncAssertResult5)

        let saveGate = AttemptTestGate()
        server.setGate(saveGate, for: .saveAnswers)
        let itemId = server.firstLeafItemId()
        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        await saveGate.waitUntilEntered()

        server.simulateExternalTakeover(newSessionId: "device-C-session")
        server.setGate(nil, for: .saveAnswers)
        await saveGate.releaseAll()

        let evictedDuringSave = await clock.waitUntil {
            engine.state.session == .evicted && engine.state.save == .unsaved(.sessionConflict)
        }
        XCTAssertTrue(evictedDuringSave)
    }
}
