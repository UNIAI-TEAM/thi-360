import Foundation
import XCTest
@testable import Thi360

@MainActor
final class TakeoverDialogTests: XCTestCase {
    func testOpenElsewhereDialogTimezoneFormattingDoubleTapCancelAndSilentPeer() async throws {
        let previousDefaultTZ = NSTimeZone.default
        NSTimeZone.default = TimeZone(identifier: "America/New_York")!
        defer { NSTimeZone.default = previousDefaultTZ }

        let lastSeen = FlexibleISO8601DateTranscoder.parse("2026-11-20T03:15:30Z")!
        XCTAssertEqual(TimerText.formatLastSeenAt(lastSeen, locale: .vi), "10:15:30 20/11/2026")
        XCTAssertEqual(TimerText.formatLastSeenAt(lastSeen, locale: .en), "Nov 20, 2026, 10:15:30")

        let silentPeerLastSeen = FlexibleISO8601DateTranscoder.parse("2026-11-20T07:00:00Z")!
        let harness = try PlayerTestHarness(autoStart: false)
        defer { harness.dispose() }
        harness.server.setActiveSession("other-device-session", lastSeenAt: silentPeerLastSeen)
        harness.engine.start()
        await harness.settle()

        let localWithDialog = PlayerLocalUI(dialog: .finishPart)
        let ui = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: localWithDialog,
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertTrue(ui.showTakeoverDialog)
        XCTAssertEqual(ui.activeDialog, .none, "FinishPart/Submit dialog must close when Takeover appears")
        XCTAssertFalse(ui.canEdit, "Background content must be read-only under Takeover dialog")
        XCTAssertEqual(ui.questionMode, .locked)

        let (win, vc) = PlayerViewTestHost.host(
            PlayerView(attemptId: harness.attemptId, engineState: harness.engine.state, localUI: localWithDialog, locale: .vi)
        )
        defer { win.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.takeover.dialog", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.takeover.examTitle", in: vc.view, window: win))
        let lastSeenNode = PlayerViewTestHost.findNode(id: "player.takeover.lastSeenAt", in: vc.view, window: win)
        XCTAssertNotNil(lastSeenNode)
        XCTAssertTrue(lastSeenNode?.label.contains("14:00:00 20/11/2026") == true)
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.takeover.notes", in: vc.view, window: win))

        var noLastSeenState = harness.engine.state
        noLastSeenState.session = .openElsewhere(lastSeenAt: nil)
        let (winNil, vcNil) = PlayerViewTestHost.host(
            PlayerView(attemptId: harness.attemptId, engineState: noLastSeenState, locale: .vi)
        )
        defer { winNil.isHidden = true }
        XCTAssertNil(PlayerViewTestHost.findNode(id: "player.takeover.lastSeenAt", in: vcNil.view, window: winNil))

        let callsBeforeCancel = harness.server.allCalls.count
        var cancelledToMyExams = false
        let onCancel: () -> Void = { cancelledToMyExams = true }
        onCancel()
        XCTAssertTrue(cancelledToMyExams)
        XCTAssertEqual(harness.server.allCalls.count, callsBeforeCancel)

        let gate = AttemptTestGate()
        harness.server.setGate(gate, for: .claimSession)
        harness.engine.takeover()
        harness.engine.takeover()

        let sendingUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertTrue(sendingUI.isTakeoverSending)

        let (sendingWin, sendingVC) = PlayerViewTestHost.host(
            PlayerView(attemptId: harness.attemptId, engineState: harness.engine.state, locale: .vi)
        )
        defer { sendingWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.takeover.confirmButton", in: sendingVC.view, window: sendingWin)?.isEnabled,
            false
        )
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.takeover.cancelButton", in: sendingVC.view, window: sendingWin)?.isEnabled,
            false
        )

        await gate.waitUntilEntered()
        harness.server.setGate(nil, for: .claimSession)
        await gate.releaseAll()
        await harness.settle()

        let takeoverCalls = harness.server.calls(for: .claimSession).filter {
            ($0.decodedJSON()?["takeover"] as? Bool) == true
        }
        XCTAssertEqual(takeoverCalls.count, 1, "Double-tapping Takeover must send only 1 takeover claim")

        let recoveredUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertFalse(recoveredUI.showTakeoverDialog)
        XCTAssertTrue(recoveredUI.canEdit)
    }
}
