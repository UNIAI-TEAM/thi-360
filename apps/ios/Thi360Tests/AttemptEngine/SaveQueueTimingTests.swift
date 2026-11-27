import Foundation
import XCTest
@testable import Thi360

@MainActor
final class SaveQueueTimingTests: XCTestCase {
    // AC-7: optimistic state + draft before first send even with 3s server delay; immediate for SC/MC/TF/MATCHING;
    // 800ms debounce for SHORT_ANSWER/CLOZE; 2s debounce & 15s maxWait ceiling for ESSAY; flag immediate + cancel on toggle back.
    func testOptimisticImmediateDraftAndPerQuestionTypeSchedulesAndFlags() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(useRichTextAttempt50: true, clock: clock)

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
        let items = server.leafItemIds()
        XCTAssertGreaterThanOrEqual(items.count, 10)

        // 1. Optimistic update + draft persisted BEFORE first PUT arrives even with 3s simulated server delay
        let saveGate = AttemptTestGate()
        server.setGate(saveGate, for: .saveAnswers)
        server.setDelay(.seconds(3), for: .saveAnswers)

        let scItem = items[0]
        engine.answer(itemId: scItem, value: .singleChoice(optionId: "a"))

        // Immediately visible synchronously before server finishes
        XCTAssertEqual(engine.state.answers[scItem]?.value, .singleChoice(optionId: "a"))
        XCTAssertEqual(engine.state.answers[scItem]?.seq, 1)
        XCTAssertEqual(engine.state.answers[scItem]?.sync, .pending)
        XCTAssertEqual(engine.state.save, .saving)

        await saveGate.waitUntilEntered()
        let persistedDraft = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(scItem)")
        XCTAssertNotNil(persistedDraft, "Draft must be on disk BEFORE first PUT .../answers executes")

        server.setGate(nil, for: .saveAnswers)
        server.setDelay(nil, for: .saveAnswers)
        await saveGate.releaseAll()
        await clock.advance(by: .seconds(3))
        let asyncAssertResult2 = await clock.waitUntil { engine.state.save == .saved && engine.state.answers[scItem]?.sync == .saved }
        XCTAssertTrue(asyncAssertResult2)

        // 2. Immediate send for MULTIPLE_CHOICE, TRUE_FALSE, MATCHING
        for (idx, val) in [
            (1, AnswerValue.multipleChoice(optionIds: ["a", "b"])),
            (2, AnswerValue.trueFalse(statements: ["s1": true])),
            (3, AnswerValue.matching(pairs: ["l1": "r1"]))
        ] {
            let before = server.callCount(for: .saveAnswers)
            engine.answer(itemId: items[idx], value: val)
            let asyncAssertResult3 = await clock.waitUntil { server.callCount(for: .saveAnswers) == before + 1 }
            XCTAssertTrue(asyncAssertResult3)
        }

        // 3. SHORT_ANSWER & CLOZE: 800ms debounce after last edit
        let shortItem = items[4]
        let clozeItem = items[5]
        let countBeforeShort = server.callCount(for: .saveAnswers)
        engine.answer(itemId: shortItem, value: .shortAnswer(text: "1"))
        await clock.advance(by: .milliseconds(500))
        XCTAssertEqual(server.callCount(for: .saveAnswers), countBeforeShort)
        engine.answer(itemId: shortItem, value: .shortAnswer(text: "12"))
        await clock.advance(by: .milliseconds(700))
        XCTAssertEqual(server.callCount(for: .saveAnswers), countBeforeShort)
        await clock.advance(by: .milliseconds(100))
        let asyncAssertResult4 = await clock.waitUntil { server.callCount(for: .saveAnswers) == countBeforeShort + 1 }
        XCTAssertTrue(asyncAssertResult4)

        let countBeforeCloze = server.callCount(for: .saveAnswers)
        engine.answer(itemId: clozeItem, value: .cloze(blanks: ["1": "abc"]))
        await clock.advance(by: .milliseconds(799))
        XCTAssertEqual(server.callCount(for: .saveAnswers), countBeforeCloze)
        await clock.advance(by: .milliseconds(1))
        let asyncAssertResult5 = await clock.waitUntil { server.callCount(for: .saveAnswers) == countBeforeCloze + 1 }
        XCTAssertTrue(asyncAssertResult5)

        // 4. ESSAY: 2s debounce after idle AND 15s maxWait ceiling while continuously typing every 1s
        let essayItem = items[6]
        let countBeforeEssay = server.callCount(for: .saveAnswers)
        engine.answer(itemId: essayItem, value: .essay(html: "<p>a</p>"))
        await clock.advance(by: .milliseconds(1999))
        XCTAssertEqual(server.callCount(for: .saveAnswers), countBeforeEssay)
        await clock.advance(by: .milliseconds(1))
        let asyncAssertResult6 = await clock.waitUntil { server.callCount(for: .saveAnswers) == countBeforeEssay + 1 }
        XCTAssertTrue(asyncAssertResult6)

        // Continuous typing every 1s for 15s -> must flush at 15s ceiling despite debounce resetting!
        let countBeforeCeiling = server.callCount(for: .saveAnswers)
        for sec in 0..<15 {
            engine.answer(itemId: essayItem, value: .essay(html: "<p>word \(sec)</p>"))
            if sec < 14 {
                await clock.advance(by: .seconds(1))
                XCTAssertEqual(server.callCount(for: .saveAnswers), countBeforeCeiling, "Essay must not flush at \(sec + 1)s while typing")
            } else {
                await clock.advance(by: .seconds(1))
            }
        }
        let asyncAssertResult7 = await clock.waitUntil { server.callCount(for: .saveAnswers) == countBeforeCeiling + 1 }
        XCTAssertTrue(asyncAssertResult7)

        // 5. Flag sends immediately; toggling flag on then off before send results in zero flag changes
        let flagItem = items[7]
        let countBeforeFlag = server.callCount(for: .saveAnswers)
        engine.flag(itemId: flagItem, flagged: true)
        let asyncAssertResult8 = await clock.waitUntil { server.callCount(for: .saveAnswers) == countBeforeFlag + 1 }
        XCTAssertTrue(asyncAssertResult8)
        let flagCallBody = server.calls(for: .saveAnswers).last?.decodedJSON()
        let sentFlags = flagCallBody?["flags"] as? [[String: Any]]
        XCTAssertEqual(sentFlags?.first?["itemId"] as? String, flagItem)
        XCTAssertEqual(sentFlags?.first?["flagged"] as? Bool, true)

        // Toggle off then on while offline (before send) -> reverts to serverFlagged (true) -> no dirty flag!
        connectivity.applyUpdate(false)
        await clock.settle()
        engine.flag(itemId: flagItem, flagged: false)
        XCTAssertEqual(engine.state.answers[flagItem]?.sync, .pending)
        engine.flag(itemId: flagItem, flagged: true)
        XCTAssertEqual(engine.state.answers[flagItem]?.sync, .saved)
        XCTAssertEqual(engine.state.save, .saved)

        let countBeforeReconnect = server.callCount(for: .saveAnswers)
        connectivity.applyUpdate(true)
        await clock.settle()
        XCTAssertEqual(server.callCount(for: .saveAnswers), countBeforeReconnect, "Canceled flag toggle must not send")
    }
}
