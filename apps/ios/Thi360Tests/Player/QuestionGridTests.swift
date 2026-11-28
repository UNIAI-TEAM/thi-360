import Foundation
import XCTest
@testable import Thi360

@MainActor
final class QuestionGridTests: XCTestCase {
    func testCellsFiltersD29FlaggedTokensLegendAndAccessibility() throws {
        let (attempt, bodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared")
        let part = try XCTUnwrap(attempt.parts?.first)
        let leaves = UnitGrouping.leafItems(in: part)
        XCTAssertGreaterThanOrEqual(leaves.count, 3)

        var state = EngineState.initial(isOnline: true)
        state.load = .loaded(value: (), stale: false)
        state.attempt = attempt
        state.session = .editing(sessionId: "sess-1")
        state.position = Position(partId: part.partId ?? "", itemId: leaves[0].itemId)

        let id0 = try XCTUnwrap(leaves[0].itemId)
        let id1 = try XCTUnwrap(leaves[1].itemId)
        let id2 = try XCTUnwrap(leaves[2].itemId)

        state.answers[id0] = LocalAnswer(value: .singleChoice(optionId: "opt-1"), seq: 1, flagged: true, sync: .saved)
        state.answers[id1] = LocalAnswer(value: nil, seq: 1, flagged: true, sync: .pending)
        state.answers[id2] = LocalAnswer(value: nil, seq: 0, flagged: false, sync: .saved)

        for mode in [ThemeMode.light, ThemeMode.dark] {
            let pal = BrandPaletteSet.defaultThi360Green.palette(for: mode)
            let stat = StatusPalette.forMode(mode)

            let flaggedAnswered = QuestionGridCellStyle.resolve(completion: .answered, flagged: true, palette: pal, statusPalette: stat)
            XCTAssertEqual(flaggedAnswered.backgroundHex, stat.dangerSolid)
            XCTAssertEqual(flaggedAnswered.foregroundHex, stat.onDanger)
            XCTAssertEqual(flaggedAnswered.flagHex, stat.onDanger)
            XCTAssertFalse(flaggedAnswered.isDashed)

            let flaggedPartial = QuestionGridCellStyle.resolve(completion: .partial, flagged: true, palette: pal, statusPalette: stat)
            XCTAssertEqual(flaggedPartial.backgroundHex, pal.dangerBg)
            XCTAssertEqual(flaggedPartial.foregroundHex, pal.dangerText)
            XCTAssertEqual(flaggedPartial.borderHex, pal.dangerText)
            XCTAssertEqual(flaggedPartial.flagHex, pal.dangerText)
            XCTAssertTrue(flaggedPartial.isDashed)

            let flaggedBlank = QuestionGridCellStyle.resolve(completion: .blank, flagged: true, palette: pal, statusPalette: stat)
            XCTAssertEqual(flaggedBlank.backgroundHex, pal.dangerBg)
            XCTAssertEqual(flaggedBlank.foregroundHex, pal.dangerText)
            XCTAssertEqual(flaggedBlank.borderHex, pal.dangerText)
            XCTAssertEqual(flaggedBlank.flagHex, pal.dangerText)
            XCTAssertFalse(flaggedBlank.isDashed)
        }

        let allUI = PlayerUI.make(state: state, layout: .wide, local: PlayerLocalUI(gridFilter: .all), bodies: bodies)
        XCTAssertEqual(allUI.allGridCells.count, leaves.count)
        XCTAssertEqual(allUI.filteredGridCells.count, leaves.count)

        let unansweredUI = PlayerUI.make(state: state, layout: .wide, local: PlayerLocalUI(gridFilter: .unanswered), bodies: bodies)
        XCTAssertFalse(unansweredUI.filteredGridCells.contains(where: { $0.itemId == id0 }))
        XCTAssertTrue(unansweredUI.filteredGridCells.contains(where: { $0.itemId == id1 }))

        let flaggedUI = PlayerUI.make(state: state, layout: .wide, local: PlayerLocalUI(gridFilter: .flagged), bodies: bodies)
        XCTAssertEqual(Set(flaggedUI.filteredGridCells.map(\.itemId)), Set([id0, id1]))

        let (win, vc) = PlayerViewTestHost.host(
            QuestionGridBody(
                ui: allUI,
                locale: .vi,
                showsCloseButton: true,
                onClose: {},
                onSelectFilter: { _ in },
                onSelectItem: { _ in }
            )
        )
        defer { win.isHidden = true }

        let cell0Label = PlayerViewTestHost.findNode(id: "player.grid.cell.\(id0)", in: vc.view, window: win)?.label ?? ""
        XCTAssertTrue(cell0Label.contains("đã trả lời"))
        XCTAssertTrue(cell0Label.contains("đang xem"))
        XCTAssertTrue(cell0Label.contains("đánh dấu xem lại"))
        XCTAssertTrue(cell0Label.contains("đã lưu"))

        let cell1Label = PlayerViewTestHost.findNode(id: "player.grid.cell.\(id1)", in: vc.view, window: win)?.label ?? ""
        XCTAssertTrue(cell1Label.contains("chưa lưu"))

        for legendId in ["answered", "partial", "blank", "current", "flagged", "unsaved"] {
            XCTAssertNotNil(
                PlayerViewTestHost.findNode(id: "player.grid.legend.\(legendId)", in: vc.view, window: win),
                "Missing legend item \(legendId)"
            )
        }

        state.answers[id0]?.flagged = false
        state.answers[id1]?.flagged = false
        let emptyFlaggedUI = PlayerUI.make(state: state, layout: .wide, local: PlayerLocalUI(gridFilter: .flagged), bodies: bodies)
        let (empWin, empVC) = PlayerViewTestHost.host(
            QuestionGridBody(
                ui: emptyFlaggedUI,
                locale: .vi,
                showsCloseButton: false,
                onClose: {},
                onSelectFilter: { _ in },
                onSelectItem: { _ in }
            )
        )
        defer { empWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.grid.emptyFiltered", in: empVC.view, window: empWin)?.label,
            PlayerStrings.text("attempt.nav.emptyFiltered", locale: .vi)
        )
    }

    func testNavigation() async throws {
        let harness = try PlayerTestHarness(initialStateVariant: "in-progress-shared")
        defer { harness.dispose() }
        await harness.settle()

        let leaves = harness.server.leafItemIds()
        XCTAssertGreaterThanOrEqual(leaves.count, 2)
        let targetId = leaves[1]

        let compactUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(gridOpen: true),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertTrue(compactUI.gridSheetOpen)
        let (compWin, compVC) = PlayerViewTestHost.host(
            PlayerView(
                attemptId: harness.attemptId,
                engineState: harness.engine.state,
                localUI: PlayerLocalUI(gridOpen: false),
                bodies: harness.bodyStore.bodies(for: harness.attemptId),
                locale: .vi
            ),
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact
        )
        defer { compWin.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.bottomBar", in: compVC.view, window: compWin))
        XCTAssertNil(PlayerViewTestHost.findNode(id: "player.grid.sidebar", in: compVC.view, window: compWin))

        let wideUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .wide,
            local: PlayerLocalUI(gridOpen: true),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertFalse(wideUI.gridSheetOpen, "Wide layout must auto-close sheet and show fixed sidebar")
        let (wideWin, wideVC) = PlayerViewTestHost.host(
            PlayerView(
                attemptId: harness.attemptId,
                engineState: harness.engine.state,
                localUI: PlayerLocalUI(),
                bodies: harness.bodyStore.bodies(for: harness.attemptId),
                locale: .vi,
                idiomOverride: .pad
            ),
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular
        )
        defer { wideWin.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.grid.sidebar", in: wideVC.view, window: wideWin))
        XCTAssertNil(PlayerViewTestHost.findNode(id: "player.bottomBar", in: wideVC.view, window: wideWin))

        harness.engine.position(partId: harness.server.partId(at: 0), itemId: targetId)
        XCTAssertEqual(harness.engine.state.position?.itemId, targetId)
    }
}
