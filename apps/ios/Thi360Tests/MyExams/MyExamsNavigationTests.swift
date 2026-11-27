import Foundation
import XCTest
@testable import Thi360

@MainActor
final class MyExamsNavigationTests: XCTestCase {
    func testCardActionsOpenExactRoutesWithDoubleTapDebounceAndZeroApiCalls() async throws {
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let clock = MyExamsManualClock()
        let router = AppRouter(initialDestination: .myExams)
        let nav = MyExamsNavigationHandler(router: router, clock: clock)

        nav.handleCardAction(.continue(attemptId: "att-100"))
        nav.handleCardAction(.continue(attemptId: "att-100"))
        XCTAssertEqual(router.presentedAttempt?.attemptId, "att-100")
        router.closeAttempt()
        XCTAssertNil(router.presentedAttempt)

        clock.advance(by: 1.0)
        nav.handleCardAction(.start(entitlementId: "ent-200"))
        nav.handleCardAction(.start(entitlementId: "ent-200"))
        XCTAssertEqual(router.path(for: .myExams), [.prepare(entitlementId: "ent-200")])
        router.back()

        clock.advance(by: 1.0)
        nav.handleCardAction(.viewResult(attemptId: "att-res-300"))
        nav.handleCardAction(.viewResult(attemptId: "att-res-300"))
        XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-res-300")])
        router.back()

        clock.advance(by: 1.0)
        nav.handleCardAction(.rebuy(code: "EXAM-REBUY"))
        nav.handleCardAction(.rebuy(code: "EXAM-REBUY"))
        XCTAssertEqual(router.path(for: .myExams), [.checkout(code: "EXAM-REBUY")])
        router.back()

        clock.advance(by: 1.0)
        nav.handleCardAction(.buyMore(code: "EXAM-MORE"))
        nav.handleCardAction(.buyMore(code: "EXAM-MORE"))
        XCTAssertEqual(router.path(for: .myExams), [.checkout(code: "EXAM-MORE")])
        router.back()

        clock.advance(by: 1.0)
        nav.handleCardAction(.history)
        XCTAssertEqual(router.selectedDestination, .history)
        XCTAssertTrue(router.historyResetFilters)
        router.switchTab(.myExams)

        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            clock: clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "an"
        )
        await feed.loadOnAppear()
        stack.transport.resetRecordedRequests()

        clock.advance(by: 1.0)
        nav.handleCardAction(.continue(attemptId: "att-submitted-elsewhere"))
        XCTAssertEqual(router.presentedAttempt?.attemptId, "att-submitted-elsewhere")
        XCTAssertTrue(stack.transport.recordedRequests.isEmpty, "Card action buttons must NOT call any API")

        router.closeAttempt()
        await feed.reloadKeepingExisting()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 1)
    }
}
