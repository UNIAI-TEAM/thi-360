import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ForegroundTests: XCTestCase {
    func testBackgroundFlushesDirtyWithDraftSavedFirstAndStopsHeartbeatAndPolling() async throws {
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

        let saveGate = AttemptTestGate()
        server.setGate(saveGate, for: .saveAnswers)

        engine.answer(itemId: item1, value: .shortAnswer(text: "bg-flushed-draft"))
        await clock.settle(yields: 8)
        XCTAssertEqual(server.callCount(for: .saveAnswers), 0, "Debounced short answer should not flush yet")

        engine.setForeground(false)
        await saveGate.waitUntilEntered(count: 1)

        let draftDuringFlush = await DraftStore(store: store).readDraft(attemptId: server.attemptId, itemId: item1)
        XCTAssertEqual(
            draftDuringFlush?.answer,
            .shortAnswer(text: "bg-flushed-draft"),
            "Draft must be persisted BEFORE background flush HTTP call completes"
        )

        await saveGate.releaseAll()
        server.setGate(nil, for: .saveAnswers)

        let saved = await clock.waitUntil {
            engine.state.save == .saved
        }
        XCTAssertTrue(saved)

        let heartbeatsBefore = server.callCount(for: .heartbeat)
        await clock.advance(by: .seconds(120))
        XCTAssertEqual(server.callCount(for: .heartbeat), heartbeatsBefore, "No heartbeats while in background")

        // Verify GENERATING part does not poll while in background
        let clock2 = TestEngineClock()
        let genServer = try FakeAttemptServer(initialStateVariant: "generating", clock: clock2)
        let (_, store2, rootURL2) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL2) }
        let (conn2, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)
        let genEngine = AttemptEngine(
            attemptId: genServer.attemptId,
            accountId: "account-A",
            api: genServer.makeAttemptAPI(),
            store: store2,
            clock: clock2,
            connectivity: conn2
        )
        defer { genEngine.dispose() }

        let genClaimed = await clock2.waitUntil {
            if case .editing = genEngine.state.session { return true }
            return false
        }
        XCTAssertTrue(genClaimed)

        genEngine.setForeground(false)
        await clock2.settle()
        let getsWhileBg = genServer.callCount(for: .getState)
        await clock2.advance(by: .seconds(15))
        XCTAssertEqual(genServer.callCount(for: .getState), getsWhileBg, "No part polling while in background")
    }

    func testReturnToForegroundViaInboundLinkHookSyncsHeartbeatStateClockAndDrafts() async throws {
        let clock = TestEngineClock()
        let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
        let deadline = baseNow.addingTimeInterval(600)
        let server = try FakeAttemptServer(
            initialStateVariant: "in-progress-shared",
            initialServerNow: baseNow,
            clock: clock,
            mutateInitialState: { json in
                json["deadlineAt"] = FakeAttemptServer.isoString(deadline)
            }
        )
        let item1 = server.firstLeafItemId(inPartIndex: 0)
        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let registry = AttemptEngineRegistry()
        let router = InboundLinkRouter()
        registry.bindForegroundHook(to: router)

        let engine = registry.engine(for: server.attemptId, accountId: "account-A") {
            AttemptEngine(
                attemptId: server.attemptId,
                accountId: "account-A",
                api: server.makeAttemptAPI(),
                store: store,
                clock: clock,
                connectivity: connectivity
            )
        }
        defer { registry.releaseAll() }

        let claimed = await clock.waitUntil {
            if case .editing = engine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)

        registry.handleScenePhaseChange(.background)
        await router.notifyScenePhaseChanged(.background, sessionState: .active)
        await clock.settle()

        let draft = ItemDraftRecord(
            itemId: item1,
            seq: 3,
            answer: .shortAnswer(text: "reconcile-on-foreground"),
            flagged: false
        )
        let draftStore = DraftStore(store: store)
        try await draftStore.saveDraft(draft, attemptId: server.attemptId)

        let driftedServerNow = baseNow.addingTimeInterval(120)
        server.setServerNow(driftedServerNow)

        let heartbeatsBefore = server.callCount(for: .heartbeat)
        let getsBefore = server.callCount(for: .getState)

        // App returns to foreground behind biometric lock: hook stays queued until unlocked
        await router.notifyScenePhaseChanged(.active, sessionState: .locked)
        await clock.settle()
        XCTAssertEqual(server.callCount(for: .heartbeat), heartbeatsBefore, "Must wait for biometric unlock before resuming")

        // Biometric unlock transitions session back to .active and fires InboundLinkRouter.onForeground hook
        await router.notifySessionStateChanged(.active)

        let synced = await clock.waitUntil {
            server.callCount(for: .heartbeat) == heartbeatsBefore + 1
                && server.callCount(for: .getState) == getsBefore + 1
                && engine.state.answers[item1]?.value == .shortAnswer(text: "reconcile-on-foreground")
                && engine.state.save == .saved
        }
        XCTAssertTrue(synced)

        let expectedRemaining = deadline.timeIntervalSince(driftedServerNow)
        let remainingDuration = try XCTUnwrap(engine.state.clock.remaining)
        let actualRemaining = ServerClock.timeInterval(from: remainingDuration)
        XCTAssertLessThanOrEqual(abs(actualRemaining - expectedRemaining), 1.0, "Clock must re-anchor within <= 1s")
    }

    func testExpiredInBackgroundClosesOrAdvancesPartWithZeroAcceptedSaves() async throws {
        // Case A: SHARED attempt expires while in background -> closed on foreground return, zero saves
        do {
            let clock = TestEngineClock()
            let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
            let server = try FakeAttemptServer(
                initialStateVariant: "in-progress-shared",
                initialServerNow: baseNow,
                clock: clock,
                mutateInitialState: { json in
                    json["deadlineAt"] = FakeAttemptServer.isoString(baseNow.addingTimeInterval(60))
                }
            )
            let item1 = server.firstLeafItemId(inPartIndex: 0)
            let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
            defer { try? FileManager.default.removeItem(at: rootURL) }
            let (connectivity, netObserver) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

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

            netObserver.emit(false)
            await clock.settle()
            engine.answer(itemId: item1, value: .singleChoice(optionId: "late-bg-answer"))
            await engine.waitForIdleForTesting()

            engine.setForeground(false)
            await clock.advance(by: .seconds(90))
            try server.setState(variant: "closed-time-up")

            let savesBeforeReturn = server.callCount(for: .saveAnswers)
            netObserver.emit(true)
            engine.setForeground(true)

            let closed = await clock.waitUntil {
                engine.state.closed == .timeUp
            }
            XCTAssertTrue(closed)
            XCTAssertEqual(server.callCount(for: .saveAnswers), savesBeforeReturn, "No save accepted after bg expiry")
        }

        // Case B: PER_PART expires part 1 in background -> advances to part 2 and drops part 1 draft
        do {
            let clock = TestEngineClock()
            let baseNow = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
            let server = try FakeAttemptServer(
                initialStateVariant: "in-progress-per-part",
                initialServerNow: baseNow,
                clock: clock
            )
            let part1Item = server.firstLeafItemId(inPartIndex: 0)
            let part2Id = server.partId(at: 1)

            let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
            defer { try? FileManager.default.removeItem(at: rootURL) }
            let (connectivity, netObserver) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

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

            netObserver.emit(false)
            await clock.settle()
            engine.answer(itemId: part1Item, value: .singleChoice(optionId: "expired-part1-opt"))
            await engine.waitForIdleForTesting()
            engine.setForeground(false)

            server.mutateState { json in
                if var parts = json["parts"] as? [[String: Any]], parts.count >= 2 {
                    parts[0]["state"] = "CLOSED"
                    parts[1]["state"] = "OPEN"
                    json["parts"] = parts
                }
            }

            let savesBeforeReturn = server.callCount(for: .saveAnswers)
            netObserver.emit(true)
            engine.setForeground(true)

            let moved = await clock.waitUntil {
                engine.state.position?.partId == part2Id
            }
            XCTAssertTrue(moved)
            XCTAssertEqual(server.callCount(for: .saveAnswers), savesBeforeReturn, "Closed part draft must NOT be sent")
        }
    }

    func testTakenOverInBackgroundEvictsOnFirstHeartbeatWithoutSendingUnsavedChanges() async throws {
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

        var sawSessionLost = false
        let events = engine.events
        let listener = Task {
            for await event in events where event == .sessionLost {
                sawSessionLost = true
            }
        }
        defer { listener.cancel() }
        await clock.settle()

        engine.setForeground(false)
        await clock.settle()

        let draftStore = DraftStore(store: store)
        try await draftStore.saveDraft(
            ItemDraftRecord(itemId: item1, seq: 5, answer: .shortAnswer(text: "must-not-send"), flagged: false),
            attemptId: server.attemptId
        )

        server.simulateExternalTakeover(newSessionId: "other-device-session-bg")
        let savesBeforeReturn = server.callCount(for: .saveAnswers)
        let heartbeatsBeforeReturn = server.callCount(for: .heartbeat)

        engine.setForeground(true)

        let evicted = await clock.waitUntil {
            engine.state.session == .evicted && sawSessionLost
        }
        XCTAssertTrue(evicted)
        XCTAssertEqual(server.callCount(for: .heartbeat), heartbeatsBeforeReturn + 1)
        XCTAssertEqual(
            server.callCount(for: .saveAnswers),
            savesBeforeReturn,
            "Unsaved changes must NOT be sent when background takeover revokes session"
        )
    }
}
