import Foundation
import XCTest
@testable import Thi360

@MainActor
final class EvictedViewTests: XCTestCase {
    func testEvictedReplacesScreenClosesDialogsShowsConflictAndRecoversOnTakeover() async throws {
        let harness = try PlayerTestHarness()
        defer { harness.dispose() }
        await harness.settle()

        let firstItem = harness.server.firstLeafItemId()
        harness.server.simulateExternalTakeover(newSessionId: "device-b-session")
        harness.engine.answer(itemId: firstItem, value: .singleChoice(optionId: "opt-1"))
        let becameEvicted = await harness.clock.waitUntil {
            harness.engine.state.session == .evicted
        }
        XCTAssertTrue(becameEvicted)
        await harness.settle()

        let ui = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(gridOpen: true, dialog: .submit)
        )
        XCTAssertEqual(ui.surface, .evicted)
        XCTAssertEqual(ui.activeDialog, .none, "Evicted must close any open dialog")
        XCTAssertFalse(ui.gridSheetOpen, "Evicted must close grid sheet")
        XCTAssertEqual(ui.saveStatus, .unsaved(.sessionConflict))
        XCTAssertEqual(
            PlayerStrings.saveStatusText(ui.saveStatus, locale: .vi).reason,
            PlayerStrings.text("attempt.saveStatus.reasons.session_conflict", locale: .vi)
        )

        let (win, vc) = PlayerViewTestHost.host(
            PlayerView(attemptId: harness.attemptId, engineState: harness.engine.state, locale: .vi)
        )
        defer { win.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.evicted", in: vc.view, window: win))
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.evicted.heading", in: vc.view, window: win)?.label,
            PlayerStrings.text("attempt.evicted.heading", locale: .vi)
        )
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.evicted.takeoverButton", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.evicted.backToMyExamsButton", in: vc.view, window: win))

        let gate = AttemptTestGate()
        harness.server.setGate(gate, for: .claimSession)
        harness.engine.takeover()
        harness.engine.takeover()

        XCTAssertEqual(PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI()).isTakeoverSending, true)
        await gate.waitUntilEntered()
        harness.server.setGate(nil, for: .claimSession)
        await gate.releaseAll()
        await harness.settle()

        let recovered = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(recovered.surface, .active)
        XCTAssertTrue(recovered.canEdit)

        let takeoverCalls = harness.server.calls(for: .claimSession).filter {
            ($0.decodedJSON()?["takeover"] as? Bool) == true
        }
        XCTAssertEqual(takeoverCalls.count, 1)
    }
}
