import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class QuestionGridSnapshotTests: XCTestCase {
    func testFlaggedCellsThreeTiersLightAndDarkSnapshots() throws {
        // Plan A6 mutation: set 3 leaf items in certified fixture to Cloze (partial) and SingleChoice (answered/blank) to exercise all 3 D-29 flagged tiers.
        let (attempt, baseBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared") { json in
            if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
               var items = parts[0]["items"] as? [[String: Any]], items.count >= 4
            {
                items[0]["questionType"] = "SINGLE_CHOICE"
                items[1]["questionType"] = "CLOZE"
                items[2]["questionType"] = "SINGLE_CHOICE"
                items[3]["questionType"] = "SINGLE_CHOICE"
                parts[0]["items"] = items
                json["parts"] = parts
            }
        }
        let part = try XCTUnwrap(attempt.parts?.first)
        let leaves = UnitGrouping.leafItems(in: part)
        let id0 = try XCTUnwrap(leaves[0].itemId)
        let id1 = try XCTUnwrap(leaves[1].itemId)
        let id2 = try XCTUnwrap(leaves[2].itemId)
        let id3 = try XCTUnwrap(leaves[3].itemId)

        var bodies = baseBodies
        bodies[id1] = .cloze(
            Components.Schemas.Cloze(
                value1: .init(_type: "CLOZE"),
                value2: .init(blanks: [.init(id: "b1"), .init(id: "b2")], passage: "")
            )
        )

        var state = EngineState.initial(isOnline: true)
        state.load = .loaded(value: (), stale: false)
        state.attempt = attempt
        state.session = .editing(sessionId: "sess-grid-snap")
        state.position = Position(partId: part.partId ?? "", itemId: id0)
        state.answers[id0] = LocalAnswer(value: .singleChoice(optionId: "opt-1"), seq: 1, flagged: true, sync: .saved)
        state.answers[id1] = LocalAnswer(value: .cloze(blanks: ["b1": "done"]), seq: 1, flagged: true, sync: .pending)
        state.answers[id2] = LocalAnswer(value: nil, seq: 0, flagged: true, sync: .saved)
        state.answers[id3] = LocalAnswer(value: .singleChoice(optionId: "opt-2"), seq: 1, flagged: false, sync: .saved)

        let ui = PlayerUI.make(state: state, layout: .wide, local: PlayerLocalUI(), bodies: bodies)
        XCTAssertEqual(ui.allGridCells[0].completion, .answered)
        XCTAssertTrue(ui.allGridCells[0].flagged)
        XCTAssertEqual(ui.allGridCells[1].completion, .partial)
        XCTAssertTrue(ui.allGridCells[1].flagged)
        XCTAssertEqual(ui.allGridCells[2].completion, .blank)
        XCTAssertTrue(ui.allGridCells[2].flagged)

        let size = CGSize(width: 340, height: 560)
        let (lightWin, _) = PlayerViewTestHost.host(
            QuestionGridBody(ui: ui, locale: .vi, showsCloseButton: true, onClose: {}, onSelectFilter: { _ in }, onSelectItem: { _ in }),
            size: size,
            style: .light
        )
        defer { lightWin.isHidden = true }
        let (lightImage, lightPNG) = try CatalogViewInspector.renderPNG(window: lightWin, size: size)

        let (darkWin, _) = PlayerViewTestHost.host(
            QuestionGridBody(ui: ui, locale: .vi, showsCloseButton: true, onClose: {}, onSelectFilter: { _ in }, onSelectItem: { _ in }),
            size: size,
            style: .dark
        )
        defer { darkWin.isHidden = true }
        let (darkImage, darkPNG) = try CatalogViewInspector.renderPNG(window: darkWin, size: size)

        XCTAssertGreaterThan(lightPNG.count, 2048)
        XCTAssertGreaterThan(darkPNG.count, 2048)
        XCTAssertNotEqual(lightPNG, darkPNG)

        persistSnapshot(image: lightImage, png: lightPNG, name: "question-grid-flagged-light")
        persistSnapshot(image: darkImage, png: darkPNG, name: "question-grid-flagged-dark")
    }

    private func persistSnapshot(image: UIImage, png: Data, name: String) {
        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)

        let env = ProcessInfo.processInfo.environment
        let outputDir = [env["TEST_RUNNER_THI360_SNAPSHOT_DIR"], env["THI360_SNAPSHOT_DIR"]]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })

        if let outputDir {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try? FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try? png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }
    }
}
