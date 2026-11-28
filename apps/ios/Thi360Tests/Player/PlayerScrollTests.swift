import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PlayerScrollTests: XCTestCase {
    func testScrollToItemInitialRestoreAndOffscreenQuestionSetChildJump() async throws {
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
        XCTAssertGreaterThanOrEqual(leaves.count, 47)
        let restoredItemId = leaves[14]
        let partId = harness.server.partId(at: 0)

        XCTAssertEqual(harness.engine.state.position?.partId, partId)
        XCTAssertEqual(harness.engine.state.position?.itemId, restoredItemId)

        let restoredUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(restoredUI.currentItemId, restoredItemId)
        XCTAssertEqual(restoredUI.currentDisplayIndex, 15)

        let (restoreWin, restoreVC) = PlayerViewTestHost.host(
            PlayerContent(
                attemptId: harness.attemptId,
                ui: restoredUI,
                answers: harness.engine.state.answers,
                locale: .vi,
                scrollRequest: nil,
                assets: nil,
                contentWidth: 390,
                onAnswer: { _, _ in },
                onFlag: { _, _ in },
                onVisibleItemChanged: { pId, iId in
                    harness.engine.position(partId: pId, itemId: iId)
                }
            ),
            size: CGSize(width: 390, height: 844),
            settleSeconds: 0.3
        )
        defer { restoreWin.isHidden = true }

        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.content", in: restoreVC.view, window: restoreWin))
        XCTAssertNotNil(
            PlayerViewTestHost.findNode(id: "question.\(restoredItemId).title", in: restoreVC.view, window: restoreWin),
            "Initial restore must scroll LazyVStack to materialize Question 15"
        )
        let restoreScrolls = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: restoreVC.view)
        XCTAssertTrue(
            restoreScrolls.contains { $0.contentOffset.y > 0 },
            "Initial restore to Question 15 must advance UIScrollView.contentOffset.y > 0"
        )

        let firstPart = try XCTUnwrap(harness.engine.state.attempt?.parts?.first)
        let parentSetId = try XCTUnwrap((firstPart.items ?? []).first(where: { $0.questionType == .QUESTION_SET })?.itemId)
        let setChildId = try XCTUnwrap((firstPart.items ?? []).first(where: { $0.parentItemId == parentSetId })?.itemId)

        harness.engine.position(partId: partId, itemId: setChildId)
        let setUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(setUI.currentItemId, setChildId)
        XCTAssertGreaterThanOrEqual(setUI.currentDisplayIndex, 46)

        let (jumpWin, jumpVC) = PlayerViewTestHost.host(
            PlayerContent(
                attemptId: harness.attemptId,
                ui: setUI,
                answers: harness.engine.state.answers,
                locale: .vi,
                scrollRequest: ScrollToItemRequest(itemId: setChildId, nonce: 1),
                assets: nil,
                contentWidth: 390,
                onAnswer: { _, _ in },
                onFlag: { _, _ in },
                onVisibleItemChanged: { pId, iId in
                    harness.engine.position(partId: pId, itemId: iId)
                }
            ),
            size: CGSize(width: 390, height: 844),
            settleSeconds: 0.3
        )
        defer { jumpWin.isHidden = true }

        XCTAssertNotNil(
            PlayerViewTestHost.findNode(id: "question.\(setChildId).title", in: jumpVC.view, window: jumpWin),
            "Jumping to off-screen QUESTION_SET child must materialize target child question"
        )
        let jumpScrolls = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: jumpVC.view)
        XCTAssertTrue(
            jumpScrolls.contains { $0.contentOffset.y > 0 },
            "Jumping to off-screen QUESTION_SET child must advance UIScrollView.contentOffset.y > 0"
        )
    }
}
