import Foundation
import XCTest
@testable import Thi360

@MainActor
final class CatalogNavigationTests: XCTestCase {
    func testSingleDetailOnDoubleTap() {
        let clock = MutableTestClock(now: Date(timeIntervalSince1970: 1_000))
        let router = AppRouter(initialDestination: .home)
        let handler = CatalogNavigationHandler(router: router, clock: clock)

        handler.openProductRoute(code: "EXAM-DEMO-01")
        handler.openProductRoute(code: "EXAM-DEMO-01")

        XCTAssertEqual(router.path(for: .home), [.product(code: "EXAM-DEMO-01")])

        clock.advance(by: 1.0)
        handler.openProductRoute(code: "EXAM-DEMO-01")
        XCTAssertEqual(router.path(for: .home), [.product(code: "EXAM-DEMO-01")])
    }

    func testAccessTargets() {
        let clock = MutableTestClock(now: Date(timeIntervalSince1970: 2_000))
        let router = AppRouter(initialDestination: .home)
        let handler = CatalogNavigationHandler(router: router, clock: clock)

        handler.handleAccessButton(.enter(entitlementId: "E"))
        handler.handleAccessButton(.enter(entitlementId: "E"))
        XCTAssertEqual(router.path(for: .home), [.prepare(entitlementId: "E")])

        clock.advance(by: 1.0)
        handler.handleAccessButton(.continue(attemptId: "A"))
        handler.handleAccessButton(.continue(attemptId: "A"))
        XCTAssertEqual(router.presentedAttempt?.attemptId, "A")
        router.closeAttempt()

        clock.advance(by: 1.0)
        router.setPath([], for: .home)
        handler.handleAccessButton(.buy(code: "EXAM-01", seenVersionNo: 3, seenPrice: 150_000))
        handler.handleAccessButton(.buy(code: "EXAM-01", seenVersionNo: 3, seenPrice: 150_000))
        XCTAssertEqual(
            router.path(for: .home),
            [.checkout(code: "EXAM-01", seenVersionNo: 3, seenPrice: 150_000)]
        )

        clock.advance(by: 1.0)
        router.switchTab(.home)
        handler.handleAccessButton(.enter(entitlementId: nil))
        XCTAssertEqual(router.selectedDestination, .myExams)

        clock.advance(by: 1.0)
        router.switchTab(.home)
        router.setPath([], for: .home)
        handler.handleAccessButton(.checking(orderCode: "DH002"))
        XCTAssertEqual(router.selectedDestination, .home)
        XCTAssertTrue(router.path(for: .home).isEmpty)
    }
}

final class MutableTestClock: AppClock, @unchecked Sendable {
    private let lock = NSLock()
    private var current: Date

    init(now: Date) {
        self.current = now
    }

    var now: Date {
        lock.lock()
        defer { lock.unlock() }
        return current
    }

    func advance(by seconds: TimeInterval) {
        lock.lock()
        current = current.addingTimeInterval(seconds)
        lock.unlock()
    }
}
