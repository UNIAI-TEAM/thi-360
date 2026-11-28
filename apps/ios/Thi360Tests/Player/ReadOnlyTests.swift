import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ReadOnlyTests: XCTestCase {
    func testReadOnlyBlocksEditsAndServerSavesWhileGridNavigationRemainsAvailable() async throws {
        let harness = try PlayerTestHarness(autoStart: false)
        defer { harness.dispose() }
        harness.server.setActiveSession("other-session-1")
        harness.engine.start()
        await harness.settle()

        let firstItem = harness.server.firstLeafItemId()
        let secondItem = harness.server.leafItemIds().dropFirst().first ?? firstItem
        let savesBefore = harness.server.callCount(for: .saveAnswers)

        harness.engine.answer(itemId: firstItem, value: .singleChoice(optionId: "opt-1"))
        harness.engine.flag(itemId: firstItem, flagged: true)
        await harness.settle()
        XCTAssertNil(harness.engine.state.answers[firstItem]?.value)
        XCTAssertFalse(harness.engine.state.answers[firstItem]?.flagged == true)
        XCTAssertEqual(harness.server.callCount(for: .saveAnswers), savesBefore)

        var roState = harness.engine.state
        roState.session = .readOnly(.attemptNotInProgress)
        let roUI = PlayerUI.make(state: roState, layout: .compact, local: PlayerLocalUI(gridOpen: true))
        XCTAssertFalse(roUI.canEdit)
        XCTAssertEqual(roUI.questionMode, .locked)
        XCTAssertFalse(roUI.topActionEnabled)
        XCTAssertTrue(roUI.partTabs.allSatisfy { !$0.isClickable })
        XCTAssertTrue(roUI.gridSheetOpen, "Grid sheet must still open in session read-only mode")
        XCTAssertEqual(roUI.readOnlyBanner, .sessionUnavailable)

        let partId = harness.server.partId(at: 0)
        harness.engine.position(partId: partId, itemId: secondItem)
        XCTAssertEqual(harness.engine.state.position?.itemId, secondItem)
        XCTAssertEqual(harness.server.callCount(for: .saveAnswers), savesBefore)

        var closedPartState = harness.engine.state
        closedPartState.session = .editing(sessionId: "sess-1")
        if var attempt = closedPartState.attempt, var parts = attempt.parts, !parts.isEmpty {
            parts[0].state = .CLOSED
            attempt.parts = parts
            closedPartState.attempt = attempt
        }
        let closedPartUI = PlayerUI.make(state: closedPartState, layout: .compact, local: PlayerLocalUI())
        XCTAssertFalse(closedPartUI.canEdit)
        XCTAssertEqual(closedPartUI.questionMode, .locked)
        XCTAssertEqual(closedPartUI.readOnlyBanner, .partClosed)

        let evictHarness = try PlayerTestHarness()
        defer { evictHarness.dispose() }
        await evictHarness.settle()
        evictHarness.engine.reportSessionRevoked()
        let evictSaves = evictHarness.server.callCount(for: .saveAnswers)
        let evictItem = evictHarness.server.firstLeafItemId()
        evictHarness.engine.answer(itemId: evictItem, value: .shortAnswer(text: "blocked"))
        evictHarness.engine.flag(itemId: evictItem, flagged: true)
        await evictHarness.settle()
        XCTAssertEqual(evictHarness.server.callCount(for: .saveAnswers), evictSaves)
    }
}
