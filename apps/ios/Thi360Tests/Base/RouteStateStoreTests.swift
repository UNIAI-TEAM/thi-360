import Foundation
import XCTest
@testable import Thi360

@MainActor
final class RouteStateStoreTests: XCTestCase {
    func testPersistsDraftAndScrollPerDestinationAndRouteAndClearsOnPopOrSessionChange() {
        let router = AppRouter()
        router.selectedDestination = .home
        router.openProduct(code: "ielts-01")
        router.routeStateStore.setDraftText("draft note", for: .home, route: .product(code: "ielts-01"))
        router.routeStateStore.setScrollRowIndex(14, for: .home, route: .product(code: "ielts-01"))

        router.switchTab(.myExams)
        router.openResult(attemptId: "att-1")
        router.routeStateStore.setDraftText("my exams result", for: .myExams, route: .result(attemptId: "att-1"))

        router.switchTab(.history)
        router.openResult(attemptId: "att-1")
        router.routeStateStore.setDraftText("history result", for: .history, route: .result(attemptId: "att-1"))

        router.switchTab(.home)
        let restored = router.routeStateStore.state(for: .home, route: .product(code: "ielts-01"))
        XCTAssertEqual(restored.draftText, "draft note")
        XCTAssertEqual(restored.scrollRowIndex, 14)

        XCTAssertEqual(
            router.routeStateStore.state(for: .myExams, route: .result(attemptId: "att-1")).draftText,
            "my exams result"
        )
        XCTAssertEqual(
            router.routeStateStore.state(for: .history, route: .result(attemptId: "att-1")).draftText,
            "history result"
        )

        router.back()
        XCTAssertEqual(router.routeStateStore.state(for: .home, route: .product(code: "ielts-01")).draftText, "")
        XCTAssertEqual(router.routeStateStore.state(for: .home, route: .product(code: "ielts-01")).scrollRowIndex, 0)

        router.resetForSessionCleared()
        XCTAssertEqual(router.routeStateStore.count, 0)
    }
}
