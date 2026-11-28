import Foundation
import XCTest
@testable import Thi360

@MainActor
final class SubmitDialogTests: XCTestCase {
    func testSubmitSummaryMetricsEssayUnderLimitDoubleTapRefusedUnconfirmedAndSuccess() async throws {
        // Plan A6 mutation: make first item ESSAY with wordLimit=20 to verify essay status + under-limit warning in SubmitDialog.
        let harness = try PlayerTestHarness(
            initialStateVariant: "in-progress-shared",
            mutateInitialState: { json in
                json["navigationMode"] = "FREE"
                if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
                   var items = parts[0]["items"] as? [[String: Any]], !items.isEmpty
                {
                    items[0]["questionType"] = "ESSAY"
                    items[0]["questionBody"] = ["type": "ESSAY", "wordLimit": 20]
                    parts[0]["items"] = items
                    json["parts"] = parts
                }
            }
        )
        defer { harness.dispose() }
        await harness.settle()

        let leaves = harness.server.leafItemIds()
        let essayId = leaves[0]
        harness.engine.answer(itemId: essayId, value: AnswerValues.essay(html: "<p>Ba chữ thôi</p>"))
        harness.engine.flag(itemId: essayId, flagged: true)

        let ui = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(dialog: .submit),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(ui.topAction, .submit)
        let summary = try XCTUnwrap(ui.submitSummary)
        XCTAssertEqual(summary.answeredCount, 1)
        XCTAssertEqual(summary.flaggedCount, 1)
        XCTAssertEqual(summary.totalCount, leaves.count)
        XCTAssertEqual(summary.essayStatus?.totalWords, 3)
        XCTAssertEqual(summary.essayStatus?.hasUnsavedWords, true)
        XCTAssertEqual(summary.essayWarnings.count, 1)
        XCTAssertEqual(summary.essayWarnings.first?.wordCount, 3)
        XCTAssertEqual(summary.essayWarnings.first?.wordLimit, 20)

        let (win, vc) = PlayerViewTestHost.host(
            SubmitDialog(
                summary: summary,
                commandState: .idle,
                locale: .vi,
                onConfirm: {},
                onReconcile: {},
                onDismiss: {}
            )
        )
        defer { win.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.submit.metric.answered", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.submit.metric.partial", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.submit.metric.blank", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.submit.metric.flagged", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.submit.essayStatus", in: vc.view, window: win))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.submit.essayWarning.\(essayId)", in: vc.view, window: win))

        harness.server.enqueue(
            .submit,
            try .fixture(operationId: Operations.submit.id, status: 409, variant: "part-not-last")
        )
        harness.engine.submit()
        await harness.settle()
        let refusedUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI(dialog: .submit))
        XCTAssertEqual(refusedUI.activeDialog, .none, "Refused submit must close dialog")

        harness.server.enqueue(.submit, .networkError(.timedOut))
        harness.engine.submit()
        await harness.settle()
        XCTAssertEqual(harness.engine.state.command, .unconfirmed(.submit))

        let (unWin, unVC) = PlayerViewTestHost.host(
            SubmitDialog(
                summary: summary,
                commandState: harness.engine.state.command,
                locale: .vi,
                onConfirm: {},
                onReconcile: {},
                onDismiss: {}
            )
        )
        defer { unWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.submit.unconfirmedTitle", in: unVC.view, window: unWin)?.label,
            PlayerStrings.text("attempt.dialogs.submitConfirm.unconfirmedTitle", locale: .vi)
        )

        harness.engine.reconcile()
        await harness.settle()
        XCTAssertEqual(harness.engine.state.command, .idle)

        let gate = AttemptTestGate()
        harness.server.setGate(gate, for: .submit)
        let callsBefore = harness.server.callCount(for: .submit)
        harness.engine.submit()
        harness.engine.submit()
        await gate.waitUntilEntered()
        harness.server.setGate(nil, for: .submit)
        await gate.releaseAll()
        await harness.settle()

        XCTAssertEqual(harness.server.callCount(for: .submit), callsBefore + 1)
        let closedUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(closedUI.surface, .closed)
        XCTAssertEqual(closedUI.closedReason, .submitted)
    }
}
