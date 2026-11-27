import Foundation
import XCTest
@testable import Thi360

@MainActor
final class HistoryPagingTests: XCTestCase {
    func testFortyFiveItemsPagingTwentyItemsSinglePageDedupEmptyLaterPageAndStaleLateResponseDropped() async throws {
        let all45: [[String: Any]] = try (1...45).map { idx in
            try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = String(format: "att-p45-%03d", idx)
                item["examName"] = "Đề lịch sử \(idx)"
            }
        }
        let page0Of45 = try MyExamsTestFixtures.makeHistoryPagePayload(items: Array(all45[0..<20]), page: 0, size: 20, totalItems: 45, totalPages: 3)
        let page1Raw = [all45[19]] + Array(all45[20..<40])
        let page1Of45 = try MyExamsTestFixtures.makeHistoryPagePayload(items: page1Raw, page: 1, size: 20, totalItems: 45, totalPages: 3)
        let page2Of45 = try MyExamsTestFixtures.makeHistoryPagePayload(items: Array(all45[40..<45]), page: 2, size: 20, totalItems: 45, totalPages: 3)

        let page1Gate = TestAsyncGate()

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.register(method: .get, path: "/api/v1/me/attempts") { request in
                let query = MyExamsTestStackFactory.queryItems(from: request)
                let page = Int(query["page"] ?? "0") ?? 0
                if page == 0 {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: page0Of45)
                }
                if page == 1 {
                    await page1Gate.enterAndWait()
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: page1Of45)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: page2Of45)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let feed = HistoryFeed(accountId: "acc-1", api: stack.historyAPI, optionsCache: stack.optionsCache, clock: stack.clock)
        await feed.loadOnAppear(resetFilters: false, reloadToken: 0)
        XCTAssertEqual(feed.currentPage?.items.count, 20)
        XCTAssertTrue(feed.currentPage?.hasMore == true)

        let loadPage1Task = Task { await feed.loadNextPageIfNeeded() }
        await page1Gate.waitUntilEntered()
        XCTAssertTrue(feed.currentPage?.isLoadingMore == true, "Tail loading indicator must be active while fetching page=1")
        await page1Gate.release()
        await loadPage1Task.value

        XCTAssertEqual(feed.currentPage?.items.count, 40, "Duplicate attemptId must be deduplicated cleanly")
        XCTAssertEqual(Set(feed.currentPage?.items.map(\.attemptId) ?? []).count, 40)
        XCTAssertTrue(feed.currentPage?.hasMore == true)

        await feed.loadNextPageIfNeeded()
        XCTAssertEqual(feed.currentPage?.items.count, 45)
        XCTAssertEqual(Set(feed.currentPage?.items.map(\.attemptId) ?? []).count, 45)
        XCTAssertFalse(feed.currentPage?.hasMore ?? true)

        let countAtEnd = stack.transport.callCount(method: .get, path: "/api/v1/me/attempts")
        await feed.loadNextPageIfNeeded()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), countAtEnd, "Scrolling past last page must not call API")

        let single20Data = try MyExamsTestFixtures.makeHistoryPagePayload(items: Array(all45[0..<20]), page: 0, size: 20, totalItems: 20, totalPages: 1)
        let stack20 = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts", data: single20Data)
        }
        defer { UserDefaults(suiteName: stack20.defaultsSuiteName)?.removePersistentDomain(forName: stack20.defaultsSuiteName) }

        let feed20 = HistoryFeed(accountId: "acc-20", api: stack20.historyAPI, optionsCache: stack20.optionsCache, clock: stack20.clock)
        await feed20.loadOnAppear(resetFilters: false, reloadToken: 0)
        XCTAssertEqual(feed20.currentPage?.items.count, 20)
        XCTAssertFalse(feed20.currentPage?.hasMore ?? true)
        await feed20.loadNextPageIfNeeded()
        XCTAssertEqual(stack20.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1)

        let emptyPage1Data = try MyExamsTestFixtures.makeHistoryPagePayload(items: [], page: 1, size: 20, totalItems: 45, totalPages: 3)
        let stackEmptyLater = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.enqueue(method: .get, path: "/api/v1/me/attempts", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: page0Of45) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: emptyPage1Data) }
            ])
        }
        defer { UserDefaults(suiteName: stackEmptyLater.defaultsSuiteName)?.removePersistentDomain(forName: stackEmptyLater.defaultsSuiteName) }

        let feedEmptyLater = HistoryFeed(accountId: "acc-el", api: stackEmptyLater.historyAPI, optionsCache: stackEmptyLater.optionsCache, clock: stackEmptyLater.clock)
        await feedEmptyLater.loadOnAppear(resetFilters: false, reloadToken: 0)
        await feedEmptyLater.loadNextPageIfNeeded()
        XCTAssertEqual(feedEmptyLater.currentPage?.items.count, 20)
        XCTAssertFalse(feedEmptyLater.currentPage?.hasMore ?? true)
        XCTAssertNotEqual(feedEmptyLater.loadState, .empty)

        let partPage0Items: [[String: Any]] = try (1...20).map { idx in
            try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = String(format: "att-part-%03d", idx)
                item["examName"] = "Phần P1 \(idx)"
                item["scope"] = "PART"
            }
        }
        let partPage1Items: [[String: Any]] = try (21...25).map { idx in
            try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
                item["attemptId"] = String(format: "att-part-%03d", idx)
                item["examName"] = "Phần P1 \(idx)"
                item["scope"] = "PART"
            }
        }
        let partPage0 = try MyExamsTestFixtures.makeHistoryPagePayload(items: partPage0Items, page: 0, size: 20, totalItems: 25, totalPages: 2)
        let partPage1 = try MyExamsTestFixtures.makeHistoryPagePayload(items: partPage1Items, page: 1, size: 20, totalItems: 25, totalPages: 2)
        let stalePage1Gate = TestAsyncGate()

        let raceStack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.register(method: .get, path: "/api/v1/me/attempts") { request in
                let query = MyExamsTestStackFactory.queryItems(from: request)
                let scope = query["scope"]
                let page = Int(query["page"] ?? "0") ?? 0
                if scope == nil && page == 0 {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: page0Of45)
                }
                if scope == nil && page == 1 {
                    await stalePage1Gate.enterAndWait()
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: page1Of45)
                }
                if scope == "PART" && page == 0 {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: partPage0)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: partPage1)
            }
        }
        defer { UserDefaults(suiteName: raceStack.defaultsSuiteName)?.removePersistentDomain(forName: raceStack.defaultsSuiteName) }

        let raceFeed = HistoryFeed(accountId: "acc-race", api: raceStack.historyAPI, optionsCache: raceStack.optionsCache, clock: raceStack.clock)
        await raceFeed.loadOnAppear(resetFilters: false, reloadToken: 0)

        let stalePage1Task = Task { await raceFeed.loadNextPageIfNeeded() }
        await stalePage1Gate.waitUntilEntered()

        await raceFeed.selectScope(.part)
        XCTAssertEqual(raceFeed.currentPage?.items.first?.attemptId, "att-part-001")
        XCTAssertEqual(raceFeed.currentPage?.page, 0)

        await stalePage1Gate.release()
        await stalePage1Task.value

        guard case let .loaded(afterRacePage, afterRaceStale) = raceFeed.loadState else {
            return XCTFail("Expected non-stale loaded state after dropping late page=1")
        }
        XCTAssertFalse(afterRaceStale, "Dropped stale page=1 must not show stale error banner")
        XCTAssertEqual(afterRacePage.items.count, 20)
        XCTAssertEqual(afterRacePage.items.first?.attemptId, "att-part-001")
        XCTAssertTrue(afterRacePage.hasMore)

        await raceFeed.loadNextPageIfNeeded()
        let nextQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(raceStack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(nextQuery["scope"], "PART")
        XCTAssertEqual(nextQuery["page"], "1")
        XCTAssertEqual(raceFeed.currentPage?.items.count, 25)
    }
}
