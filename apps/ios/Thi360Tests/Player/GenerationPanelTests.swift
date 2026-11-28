import Foundation
import XCTest
@testable import Thi360

@MainActor
final class GenerationPanelTests: XCTestCase {
    func testGeneratingBusyNoticeTransitionToOpenAndFailedRetryWithRefusalError() async throws {
        let genHarness = try PlayerTestHarness(initialStateVariant: "generating")
        defer { genHarness.dispose() }
        await genHarness.settle(advance: .zero)

        let genUI = PlayerUI.make(state: genHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        let genPanel = try XCTUnwrap(genUI.generationPanel)
        XCTAssertFalse(genPanel.isFailed)

        let (genWin, genVC) = PlayerViewTestHost.host(
            GenerationPanel(panel: genPanel, locale: .vi, busyNoticeDelay: .milliseconds(30), onRetry: {}),
            settleSeconds: 0.04
        )
        defer { genWin.isHidden = true }
        try? await Task.sleep(for: .milliseconds(100))
        genWin.layoutIfNeeded()
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.generation.title", in: genVC.view, window: genWin)?.label,
            PlayerStrings.text("attempt.partChoice.generatingTitle", locale: .vi)
        )
        XCTAssertNotNil(
            PlayerViewTestHost.findNode(id: "player.generation.busyNotice", in: genVC.view, window: genWin),
            "A-87 busyNotice must appear after threshold while still GENERATING"
        )

        try genHarness.server.setState(variant: "in-progress-per-part")
        await genHarness.settle(advance: .seconds(4))
        let openUI = PlayerUI.make(
            state: genHarness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: genHarness.bodyStore.bodies(for: genHarness.attemptId)
        )
        XCTAssertNil(openUI.generationPanel)
        XCTAssertFalse(openUI.unitGroups.isEmpty)
        XCTAssertTrue(openUI.timer.visible)

        let failHarness = try PlayerTestHarness(initialStateVariant: "generation-failed")
        defer { failHarness.dispose() }
        await failHarness.settle(advance: .zero)

        let failedPartId = try XCTUnwrap(
            failHarness.engine.state.attempt?.parts?.first(where: { $0.state == .GENERATION_FAILED })?.partId
        )

        failHarness.server.enqueue(
            .retryPartGeneration,
            try .fixture(operationId: Operations.retryPartGeneration.id, status: 409, variant: "part-generating")
        )
        failHarness.engine.retryGeneration(partId: failedPartId)
        await failHarness.settle()

        let refusedUI = PlayerUI.make(state: failHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        let refusedPanel = try XCTUnwrap(refusedUI.generationPanel)
        XCTAssertTrue(refusedPanel.isFailed)
        XCTAssertEqual(refusedPanel.errorCode, "PART_GENERATING")

        let (failWin, failVC) = PlayerViewTestHost.host(
            GenerationPanel(panel: refusedPanel, locale: .vi, onRetry: {})
        )
        defer { failWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.generation.failedTitle", in: failVC.view, window: failWin)?.label,
            PlayerStrings.text("attempt.partChoice.failedTitle", locale: .vi)
        )
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.generation.errorBanner", in: failVC.view, window: failWin)?.label,
            PlayerStrings.text("attempt.partChoice.errors.PART_GENERATING", locale: .vi)
        )

        let gate = AttemptTestGate()
        failHarness.server.setGate(gate, for: .retryPartGeneration)
        failHarness.engine.retryGeneration(partId: failedPartId)
        XCTAssertEqual(PlayerUI.make(state: failHarness.engine.state, layout: .compact, local: PlayerLocalUI()).generationPanel?.isRetrying, true)
        await gate.waitUntilEntered()
        failHarness.server.setGate(nil, for: .retryPartGeneration)
        await gate.releaseAll()
        await failHarness.settle()
        XCTAssertEqual(PlayerUI.make(state: failHarness.engine.state, layout: .compact, local: PlayerLocalUI()).generationPanel?.isFailed, false)
    }
}
