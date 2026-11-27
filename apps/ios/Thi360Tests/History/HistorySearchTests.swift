import Foundation
import XCTest
@testable import Thi360

@MainActor
final class HistorySearchTests: XCTestCase {
    func testDebounceCoalescingImmediateSubmitAndWhitespaceHandling() async throws {
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let sleeper = ControllableHistoryDebounceSleeper()
        let feed = HistoryFeed(
            accountId: "acc-1",
            api: stack.historyAPI,
            optionsCache: stack.optionsCache,
            sleeper: sleeper,
            clock: stack.clock
        )
        await feed.loadOnAppear(resetFilters: false, reloadToken: 0)
        stack.transport.resetRecordedRequests()

        feed.updateSearchText("h")
        try await MyExamsTestStackFactory.waitUntil { sleeper.sleepCallDelays.count == 1 }
        feed.updateSearchText("hs")
        try await MyExamsTestStackFactory.waitUntil { sleeper.sleepCallDelays.count == 2 }
        feed.updateSearchText("hsa")
        try await MyExamsTestStackFactory.waitUntil { sleeper.sleepCallDelays.count == 3 }

        XCTAssertEqual(sleeper.sleepCallDelays, [300, 300, 300])
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 0)

        sleeper.fireAll()
        try await MyExamsTestStackFactory.waitUntil {
            stack.transport.callCount(method: .get, path: "/api/v1/me/attempts") == 1
        }
        let coalescedQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(coalescedQuery["q"], "hsa")
        XCTAssertEqual(coalescedQuery["page"], "0")
        XCTAssertEqual(coalescedQuery["size"], "20")
        XCTAssertEqual(feed.searchText, "hsa", "Response must not overwrite or clear user searchText")

        stack.transport.resetRecordedRequests()
        feed.updateSearchText("vact")
        try await MyExamsTestStackFactory.waitUntil { sleeper.pendingCount == 1 }
        await feed.submitSearchImmediately()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1)
        sleeper.fireAll()
        try await Task.sleep(nanoseconds: 40_000_000)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1, "Cancelled debounce timer must not trigger duplicate call")
        let submitQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(submitQuery["q"], "vact")

        stack.transport.resetRecordedRequests()
        feed.updateSearchText("    ")
        await feed.submitSearchImmediately()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1)
        let blankQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertNil(blankQuery["q"], "Whitespace-only search must omit q parameter")
    }
}
