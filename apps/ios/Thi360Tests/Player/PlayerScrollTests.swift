import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PlayerScrollTests: XCTestCase {
    func testScrollToItemInitialRestoreAndVisiblePositionUpdates() async throws {
        let harness = try PlayerTestHarness(
            useRichTextAttempt50: true,
            mutateInitialState: { json in
                if let parts = json["parts"] as? [[String: Any]],
                   let firstPart = parts.first,
                   let partId = firstPart["partId"] as? String,
                   let items = firstPart["items"] as? [[String: Any]]
                {
                    let leaves = items.filter { ($0["questionType"] as? String) != "QUESTION_SET" }
                    if leaves.count >= 15, let restoredItemId = leaves[14]["itemId"] as? String {
                        json["lastPosition"] = ["partId": partId, "itemId": restoredItemId]
                    }
                }
            }
        )
        defer { harness.dispose() }
        await harness.settle()

        let leaves = harness.server.leafItemIds()
        XCTAssertGreaterThanOrEqual(leaves.count, 15)
        let restoredItemId = leaves[14]
        let partId = harness.server.partId(at: 0)

        XCTAssertEqual(harness.engine.state.position?.partId, partId)
        XCTAssertEqual(harness.engine.state.position?.itemId, restoredItemId)

        let ui = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(ui.currentItemId, restoredItemId)
        XCTAssertEqual(ui.currentDisplayIndex, 15)

        let (win, vc) = PlayerViewTestHost.host(
            PlayerContent(
                attemptId: harness.attemptId,
                ui: ui,
                answers: harness.engine.state.answers,
                locale: .vi,
                scrollRequest: ScrollToItemRequest(itemId: leaves[5], nonce: 1),
                assets: nil,
                contentWidth: 390,
                onAnswer: { _, _ in },
                onFlag: { _, _ in },
                onVisibleItemChanged: { pId, iId in
                    harness.engine.position(partId: pId, itemId: iId)
                }
            )
        )
        defer { win.isHidden = true }

        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.content", in: vc.view, window: win))

        let targetItemId = leaves[20]
        harness.engine.position(partId: partId, itemId: targetItemId)
        let updatedUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(updatedUI.currentItemId, targetItemId)
        XCTAssertEqual(updatedUI.currentDisplayIndex, 21)
    }
}
