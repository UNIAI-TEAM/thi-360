import Foundation
import XCTest
@testable import Thi360

@MainActor
final class CatalogFeedTests: XCTestCase {
    func testFirstOpenCallsBothEndpoints() async throws {
        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/filters"), 1)
        let productRequests = stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products")
        XCTAssertEqual(productRequests.count, 1)

        let queryItems = CatalogTestStackFactory.queryItems(from: productRequests[0])
        XCTAssertEqual(queryItems["sort"], "NEWEST")
        XCTAssertEqual(queryItems["page"], "0")
        XCTAssertEqual(queryItems["size"], "12")
        XCTAssertNil(queryItems["q"])
        XCTAssertNil(queryItems["kind"])
        XCTAssertNil(queryItems["format"])
        XCTAssertNil(queryItems["subject"])
        XCTAssertNil(queryItems["part"])

        let accessRequests = stack.transport.requests(method: .post, path: "/api/v1/me/exam-access/query")
        XCTAssertEqual(accessRequests.count, 1)
        XCTAssertEqual(
            CatalogTestStackFactory.accessRequestCodes(from: accessRequests[0]),
            ["EXAM-DEMO-01", "BUNDLE-DEMO-01"]
        )

        guard case let .loaded(page, stale) = feed.loadState else {
            return XCTFail("Expected loaded catalog state, got \(feed.loadState)")
        }
        XCTAssertFalse(stale)
        XCTAssertEqual(page.cards.count, 2)
        XCTAssertEqual(page.total, 2)
        XCTAssertNil(page.nextPage)
        XCTAssertEqual(feed.filters.effectiveKinds.map(\.code), ["HSA", "QDA", "BCA", "VACT", "SPT", "SP2E", "THPTQG", "HSCA", "VSAT"])
    }
}
