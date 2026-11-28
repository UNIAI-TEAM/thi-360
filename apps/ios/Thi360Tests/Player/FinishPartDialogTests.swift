import Foundation
import XCTest
@testable import Thi360

@MainActor
final class FinishPartDialogTests: XCTestCase {
    func testFinishPartVisibilitySummaryDoubleTapRefusedAndUnconfirmedReconcile() async throws {
        let harness = try PlayerTestHarness(
            initialStateVariant: "in-progress-per-part",
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]], parts.count >= 2,
                   let p0Items = parts[0]["items"] as? [[String: Any]], let first = p0Items.first
                {
                    var cloned = first
                    cloned["itemId"] = "item-part1-first"
                    cloned["questionType"] = "SINGLE_CHOICE"
                    cloned["answer"] = NSNull()
                    cloned["answerSeq"] = 0
                    parts[1]["items"] = [cloned]
                    json["parts"] = parts
                }
            }
        )
        defer { harness.dispose() }
        await harness.settle()

        let part0Id = harness.server.partId(at: 0)
        let part1Id = harness.server.partId(at: 1)

        let openDialogUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(dialog: .finishPart),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(openDialogUI.topAction, .finishPart(part0Id))
        let summary = try XCTUnwrap(openDialogUI.finishPartSummary)
        XCTAssertEqual(summary.partId, part0Id)
        XCTAssertEqual(summary.totalCount, harness.server.leafItemIds(inPartIndex: 0).count)

        let (win, vc) = PlayerViewTestHost.host(
            FinishPartDialog(
                summary: summary,
                commandState: .idle,
                locale: .vi,
                onConfirm: {},
                onReconcile: {},
                onDismiss: {}
            )
        )
        defer { win.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.finishPart.title", in: vc.view, window: win)?.label,
            PlayerStrings.text("attempt.dialogs.finishPart.title", locale: .vi)
        )
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.finishPart.warning", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.finishPart.missingSummary", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.finishPart.nextNote", in: vc.view, window: win))

        harness.server.enqueue(
            .finishPart,
            try .fixture(operationId: Operations.finishPart.id, status: 409, variant: "part-closed")
        )
        harness.engine.finishPart(partId: part0Id)
        await harness.settle()
        let refusedUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(dialog: .finishPart)
        )
        XCTAssertEqual(refusedUI.activeDialog, .none, "Refused finishPart must close the dialog")

        harness.server.enqueue(.finishPart, .networkError(.timedOut))
        harness.engine.finishPart(partId: part0Id)
        await harness.settle()
        XCTAssertEqual(harness.engine.state.command, .unconfirmed(.finishPart))

        let unconfUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI(dialog: .finishPart))
        XCTAssertEqual(unconfUI.activeDialog, .finishPart)
        let (unWin, unVC) = PlayerViewTestHost.host(
            FinishPartDialog(
                summary: try XCTUnwrap(unconfUI.finishPartSummary),
                commandState: harness.engine.state.command,
                locale: .vi,
                onConfirm: {},
                onReconcile: {},
                onDismiss: {}
            )
        )
        defer { unWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.finishPart.unconfirmedTitle", in: unVC.view, window: unWin)?.label,
            PlayerStrings.text("attempt.dialogs.finishPart.unconfirmedTitle", locale: .vi)
        )
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.finishPart.reconcileButton", in: unVC.view, window: unWin))

        harness.engine.reconcile()
        await harness.settle()
        XCTAssertEqual(harness.engine.state.command, .idle)

        let gate = AttemptTestGate()
        harness.server.setGate(gate, for: .finishPart)
        let callsBefore = harness.server.callCount(for: .finishPart)
        harness.engine.finishPart(partId: part0Id)
        harness.engine.finishPart(partId: part0Id)

        let (sendWin, sendVC) = PlayerViewTestHost.host(
            FinishPartDialog(
                summary: summary,
                commandState: harness.engine.state.command,
                locale: .vi,
                onConfirm: {},
                onReconcile: {},
                onDismiss: {}
            )
        )
        defer { sendWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.finishPart.confirmButton", in: sendVC.view, window: sendWin)?.isEnabled,
            false
        )
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.finishPart.cancelButton", in: sendVC.view, window: sendWin)?.isEnabled,
            false
        )

        await gate.waitUntilEntered()
        harness.server.setGate(nil, for: .finishPart)
        await gate.releaseAll()
        await harness.settle()

        XCTAssertEqual(harness.server.callCount(for: .finishPart), callsBefore + 1, "Double-tap must send only 1 finishPart request")
        XCTAssertEqual(harness.engine.state.position?.partId, part1Id)
        XCTAssertEqual(harness.engine.state.position?.itemId, harness.server.firstLeafItemId(inPartIndex: 1))
    }
}
