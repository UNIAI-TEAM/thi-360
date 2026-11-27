import Foundation
import XCTest
@testable import Thi360

@MainActor
final class DraftReconcileTests: XCTestCase {
    // AC-12: draft seq > serverSeq wins & sends after claim; draft seq == / < serverSeq -> server wins & draft deleted;
    // draft flag != serverFlag -> used & sent, equal -> not sent; closed part (including expired while offline) -> server wins & drafts discarded.
    func testDraftReconcileMatrixOnOpenAndAfterOfflineExpiry() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()

        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let items = server.leafItemIds()
        XCTAssertGreaterThanOrEqual(items.count, 5)
        let itemDraftWins = items[0]
        let itemEqualSeq = items[1]
        let itemOlderSeq = items[2]
        let itemFlagDiff = items[3]
        let itemFlagSame = items[4]
        let partId = server.partId(at: 0)

        // Seed server state with existing answers & flags
        server.mutateState { json in
            guard var parts = json["parts"] as? [[String: Any]],
                  var partItems = parts[0]["items"] as? [[String: Any]]
            else { return }
            for idx in partItems.indices {
                let id = partItems[idx]["itemId"] as? String
                if id == itemDraftWins {
                    partItems[idx]["answerSeq"] = 2
                    partItems[idx]["answer"] = ["type": "SINGLE_CHOICE", "optionId": "a"]
                } else if id == itemEqualSeq {
                    partItems[idx]["answerSeq"] = 3
                    partItems[idx]["answer"] = ["type": "SINGLE_CHOICE", "optionId": "a"]
                } else if id == itemOlderSeq {
                    partItems[idx]["answerSeq"] = 5
                    partItems[idx]["answer"] = ["type": "SINGLE_CHOICE", "optionId": "a"]
                } else if id == itemFlagDiff {
                    partItems[idx]["flagged"] = false
                } else if id == itemFlagSame {
                    partItems[idx]["flagged"] = true
                }
            }
            parts[0]["items"] = partItems
            json["parts"] = parts
        }

        // Seed drafts directly into AccountScopedStore
        let draftStore = DraftStore(store: store)
        try await draftStore.saveDraft(
            ItemDraftRecord(itemId: itemDraftWins, partId: partId, seq: 4, answer: .singleChoice(optionId: "b"), flagged: false, dirtyAnswer: true, dirtyFlag: false),
            attemptId: server.attemptId
        )
        try await draftStore.saveDraft(
            ItemDraftRecord(itemId: itemEqualSeq, partId: partId, seq: 3, answer: .singleChoice(optionId: "c"), flagged: false, dirtyAnswer: true, dirtyFlag: false),
            attemptId: server.attemptId
        )
        try await draftStore.saveDraft(
            ItemDraftRecord(itemId: itemOlderSeq, partId: partId, seq: 2, answer: .singleChoice(optionId: "d"), flagged: false, dirtyAnswer: true, dirtyFlag: false),
            attemptId: server.attemptId
        )
        try await draftStore.saveDraft(
            ItemDraftRecord(itemId: itemFlagDiff, partId: partId, seq: 0, answer: nil, flagged: true, dirtyAnswer: false, dirtyFlag: true),
            attemptId: server.attemptId
        )
        try await draftStore.saveDraft(
            ItemDraftRecord(itemId: itemFlagSame, partId: partId, seq: 0, answer: nil, flagged: true, dirtyAnswer: false, dirtyFlag: true),
            attemptId: server.attemptId
        )

        let claimGate = AttemptTestGate()
        server.setGate(claimGate, for: .claimSession)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }

        await claimGate.waitUntilEntered()
        // Before claim completes: verify reconciled state
        XCTAssertEqual(engine.state.answers[itemDraftWins]?.value, .singleChoice(optionId: "b"))
        XCTAssertEqual(engine.state.answers[itemDraftWins]?.seq, 4)
        XCTAssertEqual(engine.state.answers[itemDraftWins]?.sync, .pending)

        XCTAssertEqual(engine.state.answers[itemEqualSeq]?.value, .singleChoice(optionId: "a"))
        XCTAssertEqual(engine.state.answers[itemEqualSeq]?.sync, .saved)

        XCTAssertEqual(engine.state.answers[itemOlderSeq]?.value, .singleChoice(optionId: "a"))
        XCTAssertEqual(engine.state.answers[itemOlderSeq]?.sync, .saved)

        XCTAssertEqual(engine.state.answers[itemFlagDiff]?.flagged, true)
        XCTAssertEqual(engine.state.answers[itemFlagDiff]?.sync, .pending)

        XCTAssertEqual(engine.state.answers[itemFlagSame]?.flagged, true)
        XCTAssertEqual(engine.state.answers[itemFlagSame]?.sync, .saved)

        // Release claim -> only itemDraftWins (answer) and itemFlagDiff (flag) are sent!
        server.setGate(nil, for: .claimSession)
        await claimGate.releaseAll()
        let asyncAssertResult1 = await clock.waitUntil {
            server.callCount(for: .saveAnswers) == 1 && engine.state.save == .saved
        }
        XCTAssertTrue(asyncAssertResult1)

        let saveBody = try XCTUnwrap(server.calls(for: .saveAnswers).first?.decodedJSON())
        let sentChanges = (saveBody["changes"] as? [[String: Any]]) ?? []
        let sentFlags = (saveBody["flags"] as? [[String: Any]]) ?? []
        XCTAssertEqual(sentChanges.map { $0["itemId"] as? String }, [itemDraftWins])
        XCTAssertEqual(sentFlags.map { $0["itemId"] as? String }, [itemFlagDiff])

        // Equal & older drafts were deleted from store
        let asyncAssertResult2 = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(itemEqualSeq)")
        XCTAssertNil(asyncAssertResult2)
        let asyncAssertResult3 = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(itemOlderSeq)")
        XCTAssertNil(asyncAssertResult3)
        let asyncAssertResult4 = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(itemFlagSame)")
        XCTAssertNil(asyncAssertResult4)

        // Offline expiry: edit item while offline, server closes part/attempt while offline -> reconnect discards draft & adopts server!
        connectivity.applyUpdate(false)
        await clock.settle()
        engine.answer(itemId: itemDraftWins, value: .singleChoice(optionId: "d"))
        XCTAssertEqual(engine.state.answers[itemDraftWins]?.value, .singleChoice(optionId: "d"))
        XCTAssertEqual(engine.state.answers[itemDraftWins]?.sync, .pending)

        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]] {
                parts[0]["state"] = "CLOSED"
                json["parts"] = parts
            }
        }
        server.resetCalls()
        connectivity.applyUpdate(true)
        let asyncAssertResult5 = await clock.waitUntil {
            engine.state.answers[itemDraftWins]?.sync == .locked
                && engine.state.answers[itemDraftWins]?.value == .singleChoice(optionId: "b")
        }
        XCTAssertTrue(asyncAssertResult5)
        await engine.waitForIdleForTesting()
        XCTAssertEqual(server.callCount(for: .saveAnswers), 0, "Closed part draft must be dropped, never sent")
        let asyncAssertResult6 = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(itemDraftWins)")
        XCTAssertNil(asyncAssertResult6)
    }
}
