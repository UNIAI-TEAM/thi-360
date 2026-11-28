import Foundation
import XCTest
@testable import Thi360

final class TimerTextTests: XCTestCase {
    func testFormatCeilSecondsUrgencyAndTimezone() {
        XCTAssertEqual(TimerText.format(remaining: .zero), "00:00")
        XCTAssertEqual(TimerText.format(remaining: .milliseconds(1)), "00:01")
        XCTAssertEqual(TimerText.format(remaining: .milliseconds(59_001)), "01:00")
        XCTAssertEqual(TimerText.format(remaining: .seconds(60)), "01:00")
        XCTAssertEqual(TimerText.format(remaining: .seconds(3599)), "59:59")
        XCTAssertEqual(TimerText.format(remaining: .seconds(3600)), "1:00:00")
        XCTAssertEqual(TimerText.format(remaining: .seconds(3661)), "1:01:01")

        XCTAssertTrue(TimerText.isUrgent(remaining: .seconds(60)))
        XCTAssertTrue(TimerText.isUrgent(remaining: .seconds(59)))
        XCTAssertFalse(TimerText.isUrgent(remaining: .seconds(61)))

        XCTAssertTrue(TimerText.isUrgent(remaining: .seconds(30), threshold: TimerText.choiceUrgentThreshold))
        XCTAssertFalse(TimerText.isUrgent(remaining: .seconds(31), threshold: TimerText.choiceUrgentThreshold))

        let viShort = TimerText.accessibilityLabel(
            prefix: PlayerStrings.text("attempt.timer.sharedRemaining", locale: .vi),
            parts: TimerText.parts(remaining: .seconds(125)),
            locale: .vi
        )
        XCTAssertEqual(viShort, "Cả bài còn: 2 phút 5 giây")

        let enLong = TimerText.accessibilityLabel(
            prefix: PlayerStrings.text("attempt.timer.partRemaining", locale: .en, replacements: ["ord": "2"]),
            parts: TimerText.parts(remaining: .seconds(3725)),
            locale: .en
        )
        XCTAssertEqual(enLong, "Section 2 left: 1 hours 2 minutes 5 seconds")

        let prevTZ = NSTimeZone.default
        NSTimeZone.default = TimeZone(secondsFromGMT: -5 * 3600)!
        defer { NSTimeZone.default = prevTZ }
        let sample = FlexibleISO8601DateTranscoder.parse("2026-11-20T17:30:05Z")!
        XCTAssertEqual(TimerText.formatLastSeenAt(sample, locale: .vi), "00:30:05 21/11/2026")
        XCTAssertEqual(TimerText.formatLastSeenAt(sample, locale: .en), "Nov 21, 2026, 00:30:05")
    }
}

@MainActor
final class PlayerTopBarTests: XCTestCase {
    func testExpandedCollapsedAndOfflineBanner() async throws {
        let harness = try PlayerTestHarness(initialStateVariant: "in-progress-per-part")
        defer { harness.dispose() }
        await harness.settle()

        let expandedUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI(headerCollapsed: false))
        XCTAssertFalse(expandedUI.headerCollapsed)
        XCTAssertEqual(expandedUI.topAction, .finishPart(harness.server.partId(at: 0)))

        let (expWin, expVC) = PlayerViewTestHost.host(
            PlayerTopBar(ui: expandedUI, locale: .vi, onBack: {}, onToggleCollapse: {}, onAction: {}, onSelectPart: { _ in })
        )
        defer { expWin.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.topBar.examTitle", in: expVC.view, window: expWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.partTabs", in: expVC.view, window: expWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.topBar.timer", in: expVC.view, window: expWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.topBar.saveStatus", in: expVC.view, window: expWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.topBar.actionButton", in: expVC.view, window: expWin))
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.topBar.collapseToggle", in: expVC.view, window: expWin)?.label,
            PlayerStrings.text("attempt.header.collapseHeader", locale: .vi)
        )
        XCTAssertNil(PlayerViewTestHost.findNode(id: "player.offlineBanner", in: expVC.view, window: expWin))

        let collapsedUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI(headerCollapsed: true))
        let (colWin, colVC) = PlayerViewTestHost.host(
            PlayerTopBar(ui: collapsedUI, locale: .vi, onBack: {}, onToggleCollapse: {}, onAction: {}, onSelectPart: { _ in })
        )
        defer { colWin.isHidden = true }
        XCTAssertNil(PlayerViewTestHost.findNode(id: "player.topBar.examTitle", in: colVC.view, window: colWin))
        XCTAssertNil(PlayerViewTestHost.findNode(id: "player.partTabs", in: colVC.view, window: colWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.topBar.collapsedPartName", in: colVC.view, window: colWin))
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.topBar.collapseToggle", in: colVC.view, window: colWin)?.label,
            PlayerStrings.text("attempt.header.expandHeader", locale: .vi)
        )

        harness.connectivity.applyUpdate(false)
        await harness.engine.waitForIdleForTesting()
        let offlineUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertTrue(offlineUI.isOffline)
        let (offWin, offVC) = PlayerViewTestHost.host(
            PlayerTopBar(ui: offlineUI, locale: .vi, onBack: {}, onToggleCollapse: {}, onAction: {}, onSelectPart: { _ in })
        )
        defer { offWin.isHidden = true }
        let banner = PlayerViewTestHost.findNode(id: "player.offlineBanner", in: offVC.view, window: offWin)
        XCTAssertNotNil(banner)
        XCTAssertTrue(banner?.label.contains(PlayerStrings.text("attempt.player.offlineBanner", locale: .vi)) == true)

        harness.connectivity.applyUpdate(true)
        await harness.settle()
        XCTAssertFalse(PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI()).isOffline)
    }

    func testTimer() async throws {
        let shared = try PlayerTestHarness(
            initialStateVariant: "in-progress-shared",
            mutateInitialState: { json in
                let deadline = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:01:02Z")!
                json["deadlineAt"] = FakeAttemptServer.isoString(deadline)
            }
        )
        defer { shared.dispose() }
        await shared.settle(advance: .zero)

        let ui1 = PlayerUI.make(state: shared.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertTrue(ui1.timer.visible)
        XCTAssertTrue(ui1.timer.isShared)
        XCTAssertFalse(ui1.timer.isUrgent)
        XCTAssertEqual(ui1.timer.formatted, "01:02")

        await shared.clock.advance(by: .seconds(2))
        await shared.engine.waitForIdleForTesting()
        let ui2 = PlayerUI.make(state: shared.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertTrue(ui2.timer.isUrgent, "Timer at <= 60s must enter urgent state")
        XCTAssertEqual(ui2.timer.formatted, "01:00")

        let (win, vc) = PlayerViewTestHost.host(
            PlayerTopBar(ui: ui2, locale: .vi, onBack: {}, onToggleCollapse: {}, onAction: {}, onSelectPart: { _ in })
        )
        defer { win.isHidden = true }
        let timerNode = PlayerViewTestHost.findNode(id: "player.topBar.timer", in: vc.view, window: win)
        XCTAssertEqual(timerNode?.value, "urgent")
        XCTAssertTrue(timerNode?.label.contains("Cả bài còn") == true)

        let unlimited = try PlayerTestHarness(
            initialStateVariant: "in-progress-shared",
            mutateInitialState: { json in
                json["deadlineAt"] = NSNull()
            }
        )
        defer { unlimited.dispose() }
        await unlimited.settle()
        XCTAssertFalse(PlayerUI.make(state: unlimited.engine.state, layout: .compact, local: PlayerLocalUI()).timer.visible)

        let choiceHarness = try PlayerTestHarness(initialStateVariant: "awaiting-choice")
        defer { choiceHarness.dispose() }
        await choiceHarness.settle()
        let choiceUI = PlayerUI.make(state: choiceHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertFalse(choiceUI.timer.visible, "PER_PART exam timer must not show when part is AWAITING_CHOICE")
        XCTAssertEqual(choiceUI.choicePanel?.isTimerVisible, true)
    }
}
