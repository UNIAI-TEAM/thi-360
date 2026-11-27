import Foundation
import XCTest
@testable import Thi360

@MainActor
final class SaveQueueBatchingTests: XCTestCase {
    func testEmptyValuesBatchSplit50And51AndNoOverlappingPuts() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()

        // Derive 51 leaf items from certified RT-1 attempt-50.json (cloning one item with unique UUID per A6 boundary test,
        // and normalizing initial flagged=false across items so 50/51 flag batches reflect exact dirty counts).
        let server = try FakeAttemptServer(
            useRichTextAttempt50: true,
            clock: clock,
            mutateInitialState: { json in
                guard var parts = json["parts"] as? [[String: Any]],
                      var items = parts[0]["items"] as? [[String: Any]]
                else { return }
                for idx in items.indices {
                    items[idx]["flagged"] = false
                }
                let leafCount = items.filter { ($0["questionType"] as? String) != "QUESTION_SET" }.count
                if leafCount < 51, var clone = items.first(where: { ($0["questionType"] as? String) == "SINGLE_CHOICE" }) {
                    clone["itemId"] = "30000000-0000-4000-8000-000000000099"
                    clone["displayNo"] = 51
                    clone["flagged"] = false
                    items.append(clone)
                }
                parts[0]["items"] = items
                json["parts"] = parts
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

        let asyncAssertResult1 = await clock.waitUntil { engine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult1)
        let items = server.leafItemIds()
        XCTAssertGreaterThanOrEqual(items.count, 51)
        let partId = server.partId(at: 0)

        let emptyValues: [AnswerValue] = [
            .singleChoice(optionId: nil),
            .multipleChoice(optionIds: []),
            .shortAnswer(text: ""),
            .cloze(blanks: [:]),
            .trueFalse(statements: [:]),
            .matching(pairs: [:]),
            .essay(html: "")
        ]
        connectivity.applyUpdate(false)
        await clock.settle()
        for (idx, emptyVal) in emptyValues.enumerated() {
            engine.answer(itemId: items[idx], value: emptyVal)
        }
        connectivity.applyUpdate(true)
        await clock.advance(by: .seconds(3))
        let asyncAssertResult2 = await clock.waitUntil { engine.state.save == .saved }
        XCTAssertTrue(asyncAssertResult2)

        let sentEmptyBody = try XCTUnwrap(server.calls(for: .saveAnswers).last?.decodedJSON())
        let sentEmptyChanges = try XCTUnwrap(sentEmptyBody["changes"] as? [[String: Any]])
        XCTAssertEqual(sentEmptyChanges.count, 7)
        for change in sentEmptyChanges {
            let ans = change["answer"] as? [String: Any]
            XCTAssertNotNil(ans, "answer must never be null")
            XCTAssertNotNil(ans?["type"] as? String)
        }

        server.resetCalls()
        connectivity.applyUpdate(false)
        await clock.settle()
        for idx in 0..<50 {
            engine.answer(itemId: items[idx], value: .singleChoice(optionId: "a"))
            engine.flag(itemId: items[idx], flagged: true)
        }
        engine.position(partId: partId, itemId: items[49])
        connectivity.applyUpdate(true)
        let asyncAssertResult3 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 1 && engine.state.save == .saved }
        XCTAssertTrue(asyncAssertResult3)
        let batch50Body = try XCTUnwrap(server.calls(for: .saveAnswers).first?.decodedJSON())
        XCTAssertEqual((batch50Body["changes"] as? [[String: Any]])?.count, 50)
        XCTAssertEqual((batch50Body["flags"] as? [[String: Any]])?.count, 50)
        XCTAssertNotNil(batch50Body["position"])

        engine.flag(itemId: items[50], flagged: true)
        let seededFlag51 = await clock.waitUntil { engine.state.save == .saved && engine.state.answers[items[50]]?.flagged == true }
        XCTAssertTrue(seededFlag51)

        server.resetCalls()
        connectivity.applyUpdate(false)
        await clock.settle()
        for idx in 0..<51 {
            engine.answer(itemId: items[idx], value: .singleChoice(optionId: "b"))
            engine.flag(itemId: items[idx], flagged: false)
        }
        engine.position(partId: partId, itemId: items[50])
        connectivity.applyUpdate(true)
        let asyncAssertResult4 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 2 && engine.state.save == .saved }
        XCTAssertTrue(asyncAssertResult4)

        let calls51 = server.calls(for: .saveAnswers)
        let firstBody = try XCTUnwrap(calls51[0].decodedJSON())
        let secondBody = try XCTUnwrap(calls51[1].decodedJSON())
        XCTAssertEqual((firstBody["changes"] as? [[String: Any]])?.count, 50)
        XCTAssertEqual((firstBody["flags"] as? [[String: Any]])?.count, 50)
        XCTAssertNil(firstBody["position"], "position must ONLY be on the last batch")
        XCTAssertEqual((secondBody["changes"] as? [[String: Any]])?.count, 1)
        XCTAssertEqual((secondBody["flags"] as? [[String: Any]])?.count, 1)
        XCTAssertNotNil(secondBody["position"])
        XCTAssertEqual(server.maxConcurrency(for: .saveAnswers), 1)

        server.resetCalls()
        server.setDelay(.milliseconds(1200), for: .saveAnswers)
        let rapidItem = items[0]
        let baseSeq = engine.state.answers[rapidItem]?.seq ?? 0
        for tap in 1...30 {
            let opt = (tap % 2 == 0) ? "a" : "b"
            engine.answer(itemId: rapidItem, value: .singleChoice(optionId: opt))
            await clock.advance(by: .milliseconds(333))
        }
        for _ in 0..<20 {
            if engine.state.save == .saved && engine.state.answers[rapidItem]?.sync == .saved {
                break
            }
            await clock.advance(by: .milliseconds(500))
        }
        let asyncAssertResult5 = await clock.waitUntil { engine.state.save == .saved }
        XCTAssertTrue(asyncAssertResult5)

        XCTAssertEqual(server.maxConcurrency(for: .saveAnswers), 1, "Never two overlapping PUT .../answers calls")
        XCTAssertLessThan(server.callCount(for: .saveAnswers), 30, "Changes during in-flight PUT must coalesce")
        XCTAssertEqual(engine.state.answers[rapidItem]?.seq, baseSeq + 30)
        XCTAssertEqual(engine.state.answers[rapidItem]?.sync, .saved)
    }
}
