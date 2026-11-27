import Foundation
import SwiftUI
import UIKit
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

        let shell = AdaptiveShellView(
            router: router,
            preferences: stack.preferences,
            idiom: .phone,
            sessionStore: stack.sessionStore
        )
        .environment(\.sessionStore, stack.sessionStore)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, _) = CatalogViewInspector.host(
            shell,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            settleSeconds: 0.25
        )
        defer { window.isHidden = true }

        let feed = try XCTUnwrap(CatalogFeedRegistry.shared.existingFeed(for: router))
        let loadDeadline = Date().addingTimeInterval(2.0)
        while feed.loadedCards.isEmpty && Date() < loadDeadline {
            window.setNeedsLayout()
            window.layoutIfNeeded()
            try await Task.sleep(nanoseconds: 25_000_000)
        }
        await feed.selectKind("HSA")
        await feed.loadNextPageIfNeeded()

        XCTAssertEqual(feed.query.kind, "HSA")
        XCTAssertEqual(feed.loadedCards.count, 24)
        XCTAssertEqual(feed.loadedCards[19].code, "HSA-CARD-020")

        stack.transport.resetRecordedRequests()

        router.switchTab(.history)
        window.setNeedsLayout()
        window.layoutIfNeeded()
        try await Task.sleep(nanoseconds: 120_000_000)
        router.switchTab(.home)

        let refreshDeadline = Date().addingTimeInterval(2.0)
        while stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query") == 0
            && Date() < refreshDeadline
        {
            window.setNeedsLayout()
            window.layoutIfNeeded()
            try await Task.sleep(nanoseconds: 25_000_000)
        }

        let sameFeed = try XCTUnwrap(CatalogFeedRegistry.shared.existingFeed(for: router))
        XCTAssertTrue(sameFeed === feed)
        XCTAssertEqual(sameFeed.query.kind, "HSA")
        XCTAssertEqual(sameFeed.loadedCards.count, 24)
        XCTAssertEqual(sameFeed.loadedCards[19].code, "HSA-CARD-020")
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"),
            0,
            "Returning from tab switch must not re-fetch products"
        )
        XCTAssertGreaterThanOrEqual(
            stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"),
            1,
            "Returning from another tab must automatically refresh exam-access/query"
        )
    }

    func testRegistryEvictsOnSignOutSeparatesNilAccountUsersAndAdoptsBackfilledAccountId() async throws {
        let stack = try CatalogTestStackFactory.makeStack(
            initialTokens: SessionTokens(
                accessToken: "token-a",
                refreshToken: "refresh-a",
                accessExpiresIn: 900,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "user.a",
                accountId: nil
            )
        ) { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let router = AppRouter(initialDestination: .home)
        defer { CatalogFeedRegistry.shared.clear(for: router) }

        let feedUserA = CatalogFeedRegistry.shared.feed(
            for: router,
            sessionStore: stack.sessionStore,
            connectivityMonitor: nil,
            apiOverride: stack.api
        )
        await feedUserA.loadInitialIfNeeded()
        await feedUserA.selectKind("HSA")
        XCTAssertTrue(feedUserA.hasLoadedInitialCatalog)
        XCTAssertNil(feedUserA.accountId)

        try stack.tokenStore.write(
            SessionTokens(
                accessToken: "token-a",
                refreshToken: "refresh-a",
                accessExpiresIn: 900,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "user.a",
                accountId: "acc-1"
            )
        )
        let backfilledFeed = CatalogFeedRegistry.shared.feed(
            for: router,
            sessionStore: stack.sessionStore,
            connectivityMonitor: nil,
            apiOverride: stack.api
        )
        XCTAssertTrue(backfilledFeed === feedUserA, "Backfilling accountId for the same user must preserve active CatalogFeed")
        XCTAssertEqual(backfilledFeed.accountId, "acc-1")
        XCTAssertEqual(backfilledFeed.query.kind, "HSA")

        await stack.sessionStore.signOut()
        try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertNil(
            CatalogFeedRegistry.shared.existingFeed(for: router),
            "Signing out must immediately evict cached CatalogFeed"
        )

        try await stack.sessionStore.adoptSession(
            SessionTokens(
                accessToken: "token-a-relogin",
                refreshToken: "refresh-a-relogin",
                accessExpiresIn: 900,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "user.a",
                accountId: "acc-1"
            )
        )
        stack.transport.resetRecordedRequests()
        let reloginFeed = CatalogFeedRegistry.shared.feed(
            for: router,
            sessionStore: stack.sessionStore,
            connectivityMonitor: nil,
            apiOverride: stack.api
        )
        XCTAssertFalse(reloginFeed === feedUserA, "Same-user re-login after sign-out must create a fresh CatalogFeed")
        XCTAssertFalse(reloginFeed.hasLoadedInitialCatalog)
        await reloginFeed.loadInitialIfNeeded()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/filters"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 1)

        try stack.tokenStore.write(
            SessionTokens(
                accessToken: "token-nil-a",
                refreshToken: "refresh-nil-a",
                accessExpiresIn: 900,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "user.a",
                accountId: nil
            )
        )
        let nilFeedA = CatalogFeedRegistry.shared.feed(
            for: router,
            sessionStore: stack.sessionStore,
            connectivityMonitor: nil,
            apiOverride: stack.api
        )
        try stack.tokenStore.write(
            SessionTokens(
                accessToken: "token-nil-b",
                refreshToken: "refresh-nil-b",
                accessExpiresIn: 900,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "user.b",
                accountId: nil
            )
        )
        let nilFeedB = CatalogFeedRegistry.shared.feed(
            for: router,
            sessionStore: stack.sessionStore,
            connectivityMonitor: nil,
            apiOverride: stack.api
        )
        XCTAssertFalse(nilFeedB === nilFeedA, "Distinct users with accountId == nil must never share a CatalogFeed")
    }
}
