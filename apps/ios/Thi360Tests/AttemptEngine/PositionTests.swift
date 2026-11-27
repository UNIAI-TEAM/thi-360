import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PositionTests: XCTestCase {
    func testPositionDebounceFlushInitialSelectionTakeoverAndClosedPart() async throws {
        let clock = TestEngineClock()
        let (_, storeA, dirA) = try AttemptEngineTestHarness.makeStore(accountId: "account-A")
        defer { try? FileManager.default.removeItem(at: dirA) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)

        let engineA = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: storeA,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { "device-A-session" }
        )
        defer { engineA.dispose() }
        let asyncAssertResult1 = await clock.waitUntil { engineA.state.activeSessionId == "device-A-session" }
        XCTAssertTrue(asyncAssertResult1)

        let items = server.leafItemIds()
        XCTAssertGreaterThanOrEqual(items.count, 12)
        let partId = server.partId(at: 0)
        let item12 = items[11]

        engineA.position(partId: partId, itemId: item12)
        await engineA.waitForIdleForTesting()
        let storedPos = await DraftStore(store: storeA).readPosition(attemptId: server.attemptId)
        XCTAssertEqual(storedPos, Position(partId: partId, itemId: item12))
        XCTAssertEqual(server.callCount(for: .saveAnswers), 0)

        await clock.advance(by: .milliseconds(399))
        XCTAssertEqual(server.callCount(for: .saveAnswers), 0)
        await clock.advance(by: .milliseconds(1))
        let asyncAssertResult2 = await clock.waitUntil {
            server.callCount(for: .saveAnswers) == 1 && engineA.state.save == .saved
        }
        XCTAssertTrue(asyncAssertResult2)
        await engineA.waitForIdleForTesting()

        let sentPos = server.calls(for: .saveAnswers).last?.decodedJSON()?["position"] as? [String: Any]
        XCTAssertEqual(sentPos?["partId"] as? String, partId)
        XCTAssertEqual(sentPos?["itemId"] as? String, item12)

        let (_, storeB, dirB) = try AttemptEngineTestHarness.makeStore(accountId: "account-A")
        defer { try? FileManager.default.removeItem(at: dirB) }
        let engineB = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: server.makeAttemptAPI(),
            store: storeB,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { "device-B-session" }
        )
        defer { engineB.dispose() }
        let asyncAssertResult3 = await clock.waitUntil {
            if case .openElsewhere = engineB.state.session { return true }
            return false
        }
        XCTAssertTrue(asyncAssertResult3)
        engineB.takeover()
        let asyncAssertResult4 = await clock.waitUntil { engineB.state.activeSessionId != nil && engineB.state.command == .idle }
        XCTAssertTrue(asyncAssertResult4)
        XCTAssertEqual(engineB.state.position, Position(partId: partId, itemId: item12))

        server.resetCalls()
        engineB.answer(itemId: items[0], value: .shortAnswer(text: "99"))
        XCTAssertEqual(server.callCount(for: .saveAnswers), 0)
        engineB.position(partId: partId, itemId: items[1])
        let asyncAssertResult5 = await clock.waitUntil { server.callCount(for: .saveAnswers) == 1 && engineB.state.save == .saved }
        XCTAssertTrue(asyncAssertResult5)
        let comboBody = try XCTUnwrap(server.calls(for: .saveAnswers).first?.decodedJSON())
        XCTAssertEqual((comboBody["position"] as? [String: Any])?["itemId"] as? String, items[1])

        server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]] {
                parts[0]["state"] = "CLOSED"
                json["parts"] = parts
            }
        }
        server.resetCalls()
        engineB.position(partId: partId, itemId: items[2])
        await clock.advance(by: .milliseconds(500))
        let asyncAssertResult6 = await clock.waitUntil {
            server.callCount(for: .saveAnswers) == 1 && engineB.state.save == .saved
        }
        XCTAssertTrue(
            asyncAssertResult6,
            "Expected 1 saveAnswers call after closed-part position, got \(server.callCount(for: .saveAnswers)), session=\(engineB.state.session), save=\(engineB.state.save)"
        )
        XCTAssertEqual(engineB.state.save, .saved)

        let genFailedData = try AttemptFixtureCatalog.loadData(
            operationId: Operations.getState.id,
            status: 200,
            variant: "generation-failed"
        )
        let genFailedAttempt = try MobileAPIClient.makeJSONDecoder().decode(
            Components.Schemas.AttemptStateResponse.self,
            from: genFailedData
        )
        let failedPartId = try XCTUnwrap(genFailedAttempt.parts?.first { $0.state == .GENERATION_FAILED }?.partId)
        let closedPartId = try XCTUnwrap(genFailedAttempt.parts?.first { $0.state == .CLOSED }?.partId)

        let keptFailed = PartSelection.initial(
            attempt: genFailedAttempt,
            serverPosition: nil,
            localPosition: Position(partId: failedPartId, itemId: nil)
        )
        XCTAssertEqual(keptFailed?.partId, failedPartId)

        let fromClosed = PartSelection.initial(
            attempt: genFailedAttempt,
            serverPosition: Position(partId: closedPartId, itemId: nil),
            localPosition: nil
        )
        XCTAssertEqual(fromClosed?.partId, failedPartId)
    }
}
