import Foundation
import XCTest
@testable import Thi360

@MainActor
final class EngineLifetimeTests: XCTestCase {
    func testRegistryPreservesInstanceAcrossConfigurationChangesWithoutDuplicatingSubmit() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let registry = AttemptEngineRegistry()
        defer { registry.releaseAll() }

        var factoryCalls = 0
        func fetchForViewLayoutChange() -> AttemptEngine {
            registry.engine(for: server.attemptId, accountId: "account-A") {
                factoryCalls += 1
                return AttemptEngine(
                    attemptId: server.attemptId,
                    accountId: "account-A",
                    api: server.makeAttemptAPI(),
                    store: store,
                    clock: clock,
                    connectivity: connectivity
                )
            }
        }

        let portraitEngine = fetchForViewLayoutChange()
        let claimed = await clock.waitUntil {
            if case .editing = portraitEngine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)

        let submitGate = AttemptTestGate()
        server.setGate(submitGate, for: .submit)
        portraitEngine.submit()
        await submitGate.waitUntilEntered(count: 1)

        let landscapeEngine = fetchForViewLayoutChange()
        let splitViewEngine = fetchForViewLayoutChange()

        XCTAssertTrue(portraitEngine === landscapeEngine, "Rotation must keep exact same AttemptEngine instance")
        XCTAssertTrue(portraitEngine === splitViewEngine, "Size-class change must keep exact same instance")
        XCTAssertEqual(factoryCalls, 1)
        XCTAssertEqual(server.callCount(for: .submit), 1, "In-flight submit must NOT be duplicated on layout change")

        await submitGate.releaseAll()
        let closed = await clock.waitUntil {
            landscapeEngine.state.closed == .submitted
        }
        XCTAssertTrue(closed)
        XCTAssertEqual(server.callCount(for: .submit), 1)
    }

    func testProcessKillRecreationRestoresSessionDraftsAndPositionWithoutOpenElsewhere() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let part1Id = server.partId(at: 0)
        let items = server.leafItemIds(inPartIndex: 0)
        XCTAssertGreaterThanOrEqual(items.count, 2)
        let item1 = items[0]
        let item2 = items[1]

        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, netObserver) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let engine1 = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        let claimed1 = await clock.waitUntil {
            if case .editing = engine1.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed1)
        guard case let .editing(originalSessionId) = engine1.state.session else {
            return XCTFail("Expected editing session")
        }

        netObserver.emit(false)
        await clock.settle()
        engine1.answer(itemId: item1, value: .shortAnswer(text: "draft-survives-process-kill"))
        engine1.position(partId: part1Id, itemId: item2)
        await engine1.waitForIdleForTesting()

        engine1.dispose()

        let (connectivity2, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)
        let engine2 = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity2
        )
        defer { engine2.dispose() }

        let restored = await clock.waitUntil {
            engine2.state.session == .editing(sessionId: originalSessionId)
                && engine2.state.position == Position(partId: part1Id, itemId: item2)
                && engine2.state.answers[item1]?.value == .shortAnswer(text: "draft-survives-process-kill")
                && engine2.state.save == .saved
        }
        XCTAssertTrue(restored, "Recreated engine must reuse saved sessionId without openElsewhere and restore draft/position")
    }

    func testForceQuitPreservesDraftsOnDiskForNextLaunch() async throws {
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
        let claimed = await clock.waitUntil {
            if case .editing = engine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)

        let gate = AttemptTestGate()
        server.setGate(gate, for: .saveAnswers)
        engine.answer(itemId: item1, value: .singleChoice(optionId: "force-quit-choice"))
        await gate.waitUntilEntered(count: 1)

        engine.dispose()
        await gate.releaseAll()

        let persistedDraft = await DraftStore(store: store).readDraft(attemptId: server.attemptId, itemId: item1)
        XCTAssertEqual(
            persistedDraft?.answer,
            .singleChoice(optionId: "force-quit-choice"),
            "Force-quit must leave persisted draft intact on disk for next launch"
        )
    }

    func testReleaseStopsAllLoopsAndProducesZeroFurtherCalls() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let registry = AttemptEngineRegistry()
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

        let claimed = await clock.waitUntil {
            if case .editing = engine.state.session { return true }
            return false
        }
        XCTAssertTrue(claimed)

        registry.release(server.attemptId)
        XCTAssertNil(registry.existingEngine(for: server.attemptId))

        let callsAtRelease = server.allCalls.count
        await clock.advance(by: .seconds(180))
        XCTAssertEqual(
            server.allCalls.count,
            callsAtRelease,
            "Released AttemptEngine must stop all tasks and emit ZERO further API calls"
        )
    }
}
