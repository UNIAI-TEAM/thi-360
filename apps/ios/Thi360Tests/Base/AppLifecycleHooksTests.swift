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

        await router.notifyAppStarted(sessionState: .signedOut(reason: .none))
        await router.notifyScenePhaseChanged(.background, sessionState: .signedOut(reason: .none))
        await router.notifyScenePhaseChanged(.active, sessionState: .signedOut(reason: .none))
        XCTAssertEqual(startedCount, 0)
        XCTAssertEqual(foregroundCount, 0)

        await router.notifyAppStarted(sessionState: .locked)
        XCTAssertEqual(startedCount, 0)
        await router.notifyScenePhaseChanged(.background, sessionState: .locked)
        await router.notifyScenePhaseChanged(.active, sessionState: .locked)
        XCTAssertEqual(foregroundCount, 0)

        await router.notifySessionStateChanged(.active)
        XCTAssertEqual(startedCount, 1)
        XCTAssertEqual(foregroundCount, 1)

        await router.notifyAppStarted(sessionState: .active)
        XCTAssertEqual(startedCount, 1)

        await router.notifyScenePhaseChanged(.background, sessionState: .active)
        await router.notifyScenePhaseChanged(.active, sessionState: .active)
        XCTAssertEqual(startedCount, 1)
        XCTAssertEqual(foregroundCount, 2)
    }
}
