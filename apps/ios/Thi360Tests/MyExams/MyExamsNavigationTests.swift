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

    func testScreenLevelNavigationDebouncesRapidHistoryAndCrossButtonTaps() throws {
        let clock = MyExamsManualClock()
        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-debounce",
            api: PreviewEmptyMyExamsAPI(),
            clock: clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "an"
        )
        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)

        screen.triggerCardAction(.history)
        clock.advance(by: 0.1)
        screen.triggerCardAction(.history)
        XCTAssertEqual(router.selectedDestination, .history)
        XCTAssertEqual(router.reloadCount(for: .history), 1, "Rapid double-tap on History button must increment reloadToken strictly once")

        router.switchTab(.myExams)
        clock.advance(by: 1.0)
        screen.triggerCardAction(.viewResult(attemptId: "att-1"))
        clock.advance(by: 0.2)
        screen.triggerCardAction(.buyMore(code: "EXAM-2"))
        XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-1")], "Rapid tap on second button within 600ms must be debounced")
    }
}
