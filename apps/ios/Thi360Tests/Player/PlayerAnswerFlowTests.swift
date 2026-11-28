import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PlayerAnswerFlowTests: XCTestCase {
    func testOptimisticAnswerUnder3sDelayAllSaveStatusesLockedItemAndOfflineEditing() async throws {
        let harness = try PlayerTestHarness(initialStateVariant: "in-progress-shared")
        defer { harness.dispose() }
        await harness.settle()

        let leaves = harness.server.leafItemIds()
        XCTAssertGreaterThanOrEqual(leaves.count, 2)
        let item0 = leaves[0]
        let item1 = leaves[1]

        let saveGate = AttemptTestGate()
        harness.server.setGate(saveGate, for: .saveAnswers)
        harness.server.setDelay(.seconds(3), for: .saveAnswers)
        harness.engine.answer(itemId: item0, value: .singleChoice(optionId: "opt-a"))
        harness.engine.flag(itemId: item0, flagged: true)

        let immediateUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(harness.engine.state.answers[item0]?.value?.singleChoiceOptionId, "opt-a")
        XCTAssertEqual(immediateUI.allGridCells.first(where: { $0.itemId == item0 })?.flagged, true)
        XCTAssertEqual(immediateUI.allGridCells.first(where: { $0.itemId == item0 })?.completion, .answered)
        XCTAssertEqual(immediateUI.saveStatus, .saving)
        XCTAssertEqual(PlayerStrings.saveStatusText(.saving, locale: .vi).label, "Đang lưu…")

        await saveGate.waitUntilEntered()
        harness.server.setGate(nil, for: .saveAnswers)
        harness.server.setDelay(nil, for: .saveAnswers)
        await saveGate.releaseAll()
        await harness.clock.advance(by: .seconds(3))
        let saved = await harness.clock.waitUntil { harness.engine.state.save == .saved }
        XCTAssertTrue(saved)
        await harness.engine.waitForIdleForTesting()
        XCTAssertEqual(harness.engine.state.save, .saved)
        XCTAssertEqual(PlayerStrings.saveStatusText(.saved, locale: .vi).label, "Đã lưu")

        XCTAssertEqual(PlayerStrings.saveStatusText(.offline, locale: .vi).reason, "Mất kết nối")
        XCTAssertEqual(PlayerStrings.saveStatusText(.unsaved(.networkError), locale: .vi).reason, "Lỗi kết nối")
        XCTAssertEqual(PlayerStrings.saveStatusText(.unsaved(.serverError), locale: .vi).reason, "Máy chủ bận")
        XCTAssertEqual(PlayerStrings.saveStatusText(.unsaved(.rateLimited(12)), locale: .vi).reason, "Chờ 12 giây")
        XCTAssertEqual(PlayerStrings.saveStatusText(.unsaved(.rateLimited(12)), locale: .en).reason, "Wait 12s")
        XCTAssertEqual(PlayerStrings.saveStatusText(.unsaved(.rejected), locale: .vi).reason, "Dữ liệu không hợp lệ")
        XCTAssertEqual(PlayerStrings.saveStatusText(.unsaved(.sessionConflict), locale: .vi).reason, "Mở ở nơi khác")

        var lockedAnswers = harness.engine.state.answers
        lockedAnswers[item0] = LocalAnswer(value: .singleChoice(optionId: "opt-a"), seq: 2, flagged: false, sync: .locked)
        let ui = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(ui.questionMode(for: item0, answers: lockedAnswers), .locked)
        XCTAssertEqual(ui.questionMode(for: item1, answers: lockedAnswers), .answering)

        harness.connectivity.applyUpdate(false)
        await harness.engine.waitForIdleForTesting()
        let savesBeforeOffline = harness.server.callCount(for: .saveAnswers)

        harness.engine.answer(itemId: item1, value: .shortAnswer(text: "offline-val"))
        harness.engine.flag(itemId: item1, flagged: true)
        harness.engine.position(partId: harness.server.partId(at: 0), itemId: item1)

        XCTAssertEqual(harness.engine.state.answers[item1]?.value?.shortAnswerText, "offline-val")
        XCTAssertEqual(harness.engine.state.answers[item1]?.flagged, true)
        XCTAssertEqual(harness.engine.state.position?.itemId, item1)
        XCTAssertEqual(harness.engine.state.save, .offline)
        XCTAssertEqual(harness.server.callCount(for: .saveAnswers), savesBeforeOffline)
    }
}
