import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PlayerFiftyItemsTests: XCTestCase {
    func testFiftyItemsPartRendersFirstQuestionLazilyWithoutMaterializingAllFifty() throws {
        let (attempt, bodies) = try PlayerTestFixtures.decodeAttempt50()
        let firstPart = try XCTUnwrap(attempt.parts?.first)
        let leaves = UnitGrouping.leafItems(in: firstPart)
        XCTAssertGreaterThanOrEqual(leaves.count, 50, "attempt-50 fixture must contain at least 50 leaf items")

        var state = EngineState.initial(isOnline: true)
        state.load = .loaded(value: (), stale: false)
        state.attempt = attempt
        state.session = .editing(sessionId: "sess-50")
        state.position = Position(partId: firstPart.partId ?? "", itemId: leaves.first?.itemId)
        state.clock = Countdown(target: .attempt, remaining: .seconds(3600))

        var materializedKeys = Set<String>()
        let (win, vc) = PlayerViewTestHost.host(
            PlayerView(
                attemptId: attempt.attemptId,
                engineState: state,
                localUI: PlayerLocalUI(),
                bodies: bodies,
                locale: .vi,
                onUnitMaterialized: { key in
                    materializedKeys.insert(key)
                }
            ),
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            settleSeconds: 0.12
        )
        defer { win.isHidden = true }

        let firstLeafId = try XCTUnwrap(leaves.first?.itemId)
        XCTAssertNotNil(
            PlayerViewTestHost.findNode(id: "question.\(firstLeafId).title", in: vc.view, window: win)
                ?? PlayerViewTestHost.findNode(id: "player.unit.\(firstLeafId)", in: vc.view, window: win),
            "First question of 50-item part must render fully on initial open"
        )
        XCTAssertGreaterThan(materializedKeys.count, 0, "Visible units must be materialized")
        XCTAssertLessThan(
            materializedKeys.count,
            25,
            "LazyVStack must NOT materialize all 50 items upfront (got \(materializedKeys.count))"
        )
    }
}
