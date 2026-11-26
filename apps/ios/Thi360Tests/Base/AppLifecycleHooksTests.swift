import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class AppLifecycleHooksTests: XCTestCase {
    func testAppStartedRunsOnceAndForegroundRunsEachReturnOnlyWhenActiveUnlocked() async {
        let router = InboundLinkRouter()
        var startedCount = 0
        var foregroundCount = 0

        router.onAppStarted(id: "hook.start") {
            startedCount += 1
        }
        router.onForeground(id: "hook.fg") {
            foregroundCount += 1
        }

        // Signed out: neither hook runs.
        await router.notifyAppStarted(sessionState: .signedOut(reason: .none))
        await router.notifyScenePhaseChanged(.background, sessionState: .signedOut(reason: .none))
        await router.notifyScenePhaseChanged(.active, sessionState: .signedOut(reason: .none))
        XCTAssertEqual(startedCount, 0)
        XCTAssertEqual(foregroundCount, 0)

        // Locked on launch: hooks wait until unlocked.
        await router.notifyAppStarted(sessionState: .locked)
        XCTAssertEqual(startedCount, 0)
        await router.notifyScenePhaseChanged(.background, sessionState: .locked)
        await router.notifyScenePhaseChanged(.active, sessionState: .locked)
        XCTAssertEqual(foregroundCount, 0)

        // Unlocking to .active flushes appStarted (once) and pending foreground.
        await router.notifySessionStateChanged(.active)
        XCTAssertEqual(startedCount, 1)
        XCTAssertEqual(foregroundCount, 1)

        // Repeated notifyAppStarted does NOT re-run appStarted.
        await router.notifyAppStarted(sessionState: .active)
        XCTAssertEqual(startedCount, 1)

        // Next background -> active cycle runs foreground again, never appStarted.
        await router.notifyScenePhaseChanged(.background, sessionState: .active)
        await router.notifyScenePhaseChanged(.active, sessionState: .active)
        XCTAssertEqual(startedCount, 1)
        XCTAssertEqual(foregroundCount, 2)
    }
}
