import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PartTabsTests: XCTestCase {
    func testSequentialAndFreeTabsProgressD10AccessibilityAndAfterCloseSelection() async throws {
        let harness = try PlayerTestHarness(
            initialStateVariant: "in-progress-per-part",
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]], parts.count >= 3 {
                    let templateItems = (parts[0]["items"] as? [[String: Any]]) ?? []
                    let part1Items: [[String: Any]] = templateItems.prefix(2).enumerated().map { idx, raw in
                        var copy = raw
                        copy["itemId"] = "item-part1-\(idx + 1)"
                        copy["questionType"] = "SHORT_ANSWER"
                        copy["questionBody"] = ["type": "SHORT_ANSWER"]
                        copy["answer"] = NSNull()
                        copy["answerSeq"] = 0
                        return copy
                    }
                    parts[0]["state"] = "CLOSED"
                    parts[1]["state"] = "OPEN"
                    parts[1]["items"] = part1Items
                    parts[2]["state"] = "PENDING"
                    json["parts"] = parts
                }
            }
        )
        defer { harness.dispose() }
        await harness.settle()

        let ui = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(ui.partTabs.count, 3)
        XCTAssertTrue(ui.partTabs.allSatisfy { !$0.isClickable }, "SEQUENTIAL tabs must never be clickable")
        XCTAssertEqual(ui.partTabs[0].state, .CLOSED)
        XCTAssertEqual(ui.partTabs[1].state, .OPEN)
        XCTAssertTrue(ui.partTabs[1].isSelected)
        XCTAssertEqual(ui.partTabs[2].state, .PENDING)

        let (win, vc) = PlayerViewTestHost.host(
            PartTabs(tabs: ui.partTabs, locale: .vi, onSelectPart: { _ in })
        )
        defer { win.isHidden = true }

        let closedNode = PlayerViewTestHost.findNode(id: "player.partTab.\(ui.partTabs[0].partId)", in: vc.view, window: win)
        XCTAssertEqual(closedNode?.label, "\(ui.partTabs[0].name), Đã hoàn thành")

        let openNode = PlayerViewTestHost.findNode(id: "player.partTab.\(ui.partTabs[1].partId)", in: vc.view, window: win)
        XCTAssertEqual(
            openNode?.label,
            "\(ui.partTabs[1].name), Đang làm, Tiến độ: \(ui.partTabs[1].answeredCount) trên \(ui.partTabs[1].totalCount) câu"
        )

        let visibleLabels = RecoveryViewTestHelper.findViews(ofType: UILabel.self, in: vc.view)
            .compactMap(\.text)
            .joined(separator: " ")
        XCTAssertFalse(visibleLabels.contains("Đã hoàn thành"), "D-10: CLOSED tab must not show 'Đã hoàn thành' text visually")
        XCTAssertFalse(visibleLabels.contains("Đang làm"), "D-10: OPEN tab must not show 'Đang làm' text visually")

        let part1Leaf = harness.server.firstLeafItemId(inPartIndex: 1)
        let beforeAnswered = ui.partTabs[1].answeredCount
        harness.engine.answer(itemId: part1Leaf, value: .shortAnswer(text: "42"))
        let uiAfterAns = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(uiAfterAns.partTabs[1].answeredCount, beforeAnswered + 1)

        let freeHarness = try PlayerTestHarness(
            initialStateVariant: "in-progress-per-part",
            mutateInitialState: { json in
                json["navigationMode"] = "FREE"
                json["timing"] = "SHARED"
                json["deadlineAt"] = FakeAttemptServer.isoString(
                    FlexibleISO8601DateTranscoder.parse("2026-11-20T11:00:00Z")!
                )
                if var parts = json["parts"] as? [[String: Any]], parts.count >= 3 {
                    let templateItems = (parts[0]["items"] as? [[String: Any]]) ?? []
                    let part1Items: [[String: Any]] = templateItems.prefix(2).enumerated().map { idx, raw in
                        var copy = raw
                        copy["itemId"] = "item-free-part1-\(idx + 1)"
                        copy["questionType"] = "SHORT_ANSWER"
                        copy["questionBody"] = ["type": "SHORT_ANSWER"]
                        copy["answer"] = NSNull()
                        copy["answerSeq"] = 0
                        return copy
                    }
                    parts[0]["state"] = "OPEN"
                    parts[1]["state"] = "OPEN"
                    parts[1]["items"] = part1Items
                    parts[2]["state"] = "PENDING"
                    json["parts"] = parts
                }
            }
        )
        defer { freeHarness.dispose() }
        await freeHarness.settle()

        let freeUI = PlayerUI.make(
            state: freeHarness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: freeHarness.bodyStore.bodies(for: freeHarness.attemptId)
        )
        XCTAssertFalse(freeUI.partTabs[0].isClickable, "Already-selected OPEN tab is not clickable")
        XCTAssertTrue(freeUI.partTabs[1].isClickable, "Other OPEN tab in FREE mode must be clickable")
        XCTAssertFalse(freeUI.partTabs[2].isClickable, "Non-OPEN tab in FREE mode must NOT be clickable")

        let savesBeforeSwitch = freeHarness.server.callCount(for: .saveAnswers)
        let targetPart1 = freeUI.partTabs[1].partId
        let firstLeaf1 = freeHarness.server.firstLeafItemId(inPartIndex: 1)
        freeHarness.engine.position(partId: targetPart1, itemId: firstLeaf1)
        await freeHarness.settle(advance: .milliseconds(500))
        XCTAssertEqual(freeHarness.engine.state.position?.partId, targetPart1)
        XCTAssertEqual(freeHarness.engine.state.position?.itemId, firstLeaf1)
        XCTAssertGreaterThan(freeHarness.server.callCount(for: .saveAnswers), savesBeforeSwitch)

        let (afterCloseAttempt, _) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-per-part") { json in
            if var parts = json["parts"] as? [[String: Any]], parts.count >= 3 {
                parts[0]["state"] = "CLOSED"
                parts[1]["state"] = "AWAITING_CHOICE"
                parts[2]["state"] = "OPEN"
                json["parts"] = parts
            }
        }
        let chosen = PartSelection.afterClose(afterCloseAttempt)
        XCTAssertEqual(chosen?.partId, afterCloseAttempt.parts?[1].partId)
    }
}
