import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AppRouterSwitchTabTests: XCTestCase {
    func testSwitchTabWithoutReloadPreservesStackAndDoesNotEmitReload() {
        let router = AppRouter()
        router.selectedDestination = .home
        router.openProduct(code: "ielts-01")
        router.switchTab(.myExams)
        router.openPrepare(entitlementId: "ent-1")

        router.switchTab(.home, reload: false)
        XCTAssertEqual(router.selectedDestination, .home)
        XCTAssertEqual(router.path(for: .home), [.product(code: "ielts-01")])
        XCTAssertEqual(router.path(for: .myExams), [.prepare(entitlementId: "ent-1")])
        XCTAssertEqual(router.reloadCount(for: .home), 0)
    }

    func testSwitchTabWithReloadClearsTargetStackAndEmitsSingleReloadSignal() {
        let router = AppRouter()
        router.selectedDestination = .myExams
        router.openPrepare(entitlementId: "ent-1")
        router.openResult(attemptId: "att-1")
        router.routeStateStore.setDraftText("keep?", for: .myExams, route: .result(attemptId: "att-1"))

        router.switchTab(.home)
        router.openProduct(code: "ielts-01")

        router.switchTab(.myExams, reload: true)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertEqual(router.path(for: .myExams), [])
        XCTAssertEqual(router.reloadCount(for: .myExams), 1)
        XCTAssertEqual(router.routeStateStore.state(for: .myExams, route: .result(attemptId: "att-1")).draftText, "")
        XCTAssertEqual(router.path(for: .home), [.product(code: "ielts-01")])
        XCTAssertEqual(router.reloadCount(for: .home), 0)
    }

    func testSameRouteInTwoTabsLivesInSeparateBackStacks() {
        let router = AppRouter()
        router.switchTab(.myExams)
        router.openResult(attemptId: "att-42")
        router.switchTab(.history)
        router.openResult(attemptId: "att-42")

        XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-42")])
        XCTAssertEqual(router.path(for: .history), [.result(attemptId: "att-42")])

        router.back()
        XCTAssertEqual(router.path(for: .history), [])
        XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-42")])
    }

    func testSwitchTabReloadTrueOnHistoryPreservesExistingFiltersAndIncrementsReload() {
        let router = AppRouter(initialDestination: .myExams)
        router.openHistory(resetFilters: true)
        XCTAssertTrue(router.historyResetFilters)
        router.openResult(attemptId: "att-9")

        router.switchTab(.history, reload: true)
        XCTAssertEqual(router.selectedDestination, .history)
        XCTAssertFalse(router.historyResetFilters, "switchTab(.history, reload: true) must preserve existing History filters")
        XCTAssertEqual(router.path(for: .history), [])
        XCTAssertEqual(router.reloadCount(for: .history), 2)
    }
}
