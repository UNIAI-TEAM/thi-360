import Foundation
import XCTest
@testable import Thi360

@MainActor
final class CatalogStateRetentionTests: XCTestCase {
    func testClearFiltersResetsSearchChipsFormatPartSortAndCallsDefaultPage0() async throws {
        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        feed.updateSearchText("toan")
        await feed.submitSearchImmediately()
        await feed.selectKind("HSA")
        await feed.selectFormat(.part)
        await feed.selectSort(.priceDesc)

        XCTAssertTrue(feed.query.isFiltered)
        stack.transport.resetRecordedRequests()

        await feed.clearFilters()

        XCTAssertEqual(feed.searchText, "")
        XCTAssertEqual(feed.query, .empty)
        XCTAssertFalse(feed.query.isFiltered)

        let req = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").first)
        let query = CatalogTestStackFactory.queryItems(from: req)
        XCTAssertEqual(query["sort"], "NEWEST")
        XCTAssertEqual(query["page"], "0")
        XCTAssertEqual(query["size"], "12")
        XCTAssertNil(query["q"])
        XCTAssertNil(query["kind"])
        XCTAssertNil(query["format"])
        XCTAssertNil(query["part"])
        XCTAssertNil(query["subject"])
    }

    func testFilterAndLoadedCardsSurviveDetailOpenReturnAndTabSwitchWithoutProductsReload() async throws {
        let allItems = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 24, codePrefix: "HSA-CARD")
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let page = Int(CatalogTestStackFactory.queryItems(from: request)["page"] ?? "0") ?? 0
                let data = try CatalogTestFixtures.makePageData(allItems: allItems, page: page)
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let router = AppRouter(initialDestination: .home)
        defer { CatalogFeedRegistry.shared.clear(for: router) }

        let feed = CatalogFeedRegistry.shared.feed(
            for: router,
            sessionStore: stack.sessionStore,
            connectivityMonitor: nil,
            apiOverride: stack.api
        )
        await feed.loadInitialIfNeeded()
        await feed.selectKind("HSA")
        await feed.loadNextPageIfNeeded()

        XCTAssertEqual(feed.query.kind, "HSA")
        XCTAssertEqual(feed.loadedCards.count, 24)
        XCTAssertEqual(feed.loadedCards[19].code, "HSA-CARD-020")

        stack.transport.resetRecordedRequests()

        feed.selectedProductCode = "HSA-CARD-020"
        feed.selectedProductCode = nil
        await feed.refreshAccessOnReturn()

        router.switchTab(.history)
        router.switchTab(.home)
        let sameFeed = CatalogFeedRegistry.shared.feed(
            for: router,
            sessionStore: stack.sessionStore,
            connectivityMonitor: nil,
            apiOverride: stack.api
        )
        await sameFeed.loadInitialIfNeeded()

        XCTAssertTrue(sameFeed === feed)
        XCTAssertEqual(sameFeed.query.kind, "HSA")
        XCTAssertEqual(sameFeed.loadedCards.count, 24)
        XCTAssertEqual(sameFeed.loadedCards[19].code, "HSA-CARD-020")
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"),
            0,
            "Returning from detail or switching tabs must not re-fetch products"
        )
        XCTAssertGreaterThanOrEqual(
            stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"),
            1
        )
    }
}
