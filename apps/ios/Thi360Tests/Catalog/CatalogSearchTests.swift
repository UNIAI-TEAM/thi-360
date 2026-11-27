import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class CatalogSearchTests: XCTestCase {
    func testSearchLabelAndPlaceholderMatchSpecR10() {
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.searchLabel", locale: .vi), "Tìm theo tên hoặc mã")
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.searchPlaceholder", locale: .vi), "Ví dụ: HSA FULL hoặc HSA-FULL-01")
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.searchLabel", locale: .en), "Search by name or code")
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.searchPlaceholder", locale: .en), "For example: HSA FULL or HSA-FULL-01")
    }

    func testTypingToanDebounces400msAndFiveRapidKeystrokesCallOnce() async throws {
        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let sleeper = ControllableDebounceSleeper()
        let feed = CatalogFeed(accountId: "acc-1", api: stack.api, sleeper: sleeper)
        await feed.loadInitialIfNeeded()
        stack.transport.resetRecordedRequests()

        feed.updateSearchText("t")
        await Task.yield()
        feed.updateSearchText("to")
        await Task.yield()
        feed.updateSearchText("toa")
        await Task.yield()
        feed.updateSearchText("toan")
        await Task.yield()
        feed.updateSearchText("toan1")
        await Task.yield()

        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), 0)
        XCTAssertTrue(sleeper.sleepCallDelays.allSatisfy { $0 == 400 })

        sleeper.fireAll()
        try await waitUntil {
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products") == 1
        }

        let requests = stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products")
        XCTAssertEqual(requests.count, 1)
        let query = CatalogTestStackFactory.queryItems(from: requests[0])
        XCTAssertEqual(query["q"], "toan1")
        XCTAssertEqual(query["page"], "0")
        XCTAssertEqual(feed.loadedCards.count, 2)
    }

    func testClearAndWhitespaceOmitQAndLongQueryOver100CharsIsSentWithoutError() async throws {
        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let sleeper = ControllableDebounceSleeper()
        let feed = CatalogFeed(accountId: "acc-1", api: stack.api, sleeper: sleeper)
        await feed.loadInitialIfNeeded()
        stack.transport.resetRecordedRequests()

        feed.updateSearchText("toan")
        await feed.submitSearchImmediately()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), 1)
        XCTAssertEqual(
            CatalogTestStackFactory.queryItems(from: stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products")[0])["q"],
            "toan"
        )

        feed.updateSearchText("   \t  ")
        await feed.submitSearchImmediately()
        let secondRequest = stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products")[1]
        XCTAssertNil(CatalogTestStackFactory.queryItems(from: secondRequest)["q"])

        let longQuery = String(repeating: "a", count: 125)
        feed.updateSearchText(longQuery)
        await feed.submitSearchImmediately()
        let thirdRequest = stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products")[2]
        XCTAssertEqual(CatalogTestStackFactory.queryItems(from: thirdRequest)["q"], longQuery)
        if case .loaded = feed.loadState {
        } else {
            XCTFail("Expected loaded state for >100 char query, got \(feed.loadState)")
        }
    }

    private func waitUntil(
        timeout: TimeInterval = 5.0,
        condition: @escaping () -> Bool
    ) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertTrue(condition(), "Timed out waiting for async condition")
    }
}
