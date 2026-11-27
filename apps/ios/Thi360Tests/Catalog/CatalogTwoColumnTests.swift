import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class CatalogTwoColumnTests: XCTestCase {
    func testRegularTwoColumnSelectXThenYPlaceholderFilterRetentionAndCompactTransition() async throws {
        let examDetail = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            variant: "exam"
        )
        let bundleDetail = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            variant: "bundle"
        )
        let emptyList = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            variant: "empty"
        )
        let normalList = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            variant: ""
        )
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
                let query = CatalogTestStackFactory.queryItems(from: request)
                if query["kind"] == "TSA" {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: emptyList)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: normalList)
            }
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01", data: examDetail)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/BUNDLE-DEMO-01", data: bundleDetail)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        let router = AppRouter(initialDestination: .home)

        XCTAssertEqual(
            CatalogStrings.text("storefront.catalog.selectExamPlaceholder", locale: .vi),
            "Chọn một bài thi để xem chi tiết."
        )

        let regularSize = CGSize(width: 1180, height: 820)
        let regularView = CatalogHomeView(
            feed: feed,
            router: router,
            locale: .vi,
            showsBrandHeader: false
        )
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        .environment(\.shellWidth, .twoColumn)

        let (window, _) = CatalogViewInspector.host(regularView, size: regularSize, sizeClass: .regular)
        defer { window.isHidden = true }
        let (_, emptyPNG) = try CatalogViewInspector.renderPNG(window: window, size: regularSize)

        feed.selectedProductCode = "EXAM-DEMO-01"
        try await waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01") == 1
                && feed.activeEmbeddedDetailViewModel?.loadedOutcome != nil
        }
        try await Task.sleep(nanoseconds: 80_000_000)
        window.layoutIfNeeded()
        let (_, examPNG) = try CatalogViewInspector.renderPNG(window: window, size: regularSize)
        XCTAssertNotEqual(emptyPNG, examPNG)

        feed.selectedProductCode = "BUNDLE-DEMO-01"
        try await waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/BUNDLE-DEMO-01") == 1
                && feed.activeEmbeddedDetailViewModel?.loadedOutcome != nil
        }
        try await Task.sleep(nanoseconds: 80_000_000)
        window.layoutIfNeeded()
        let (_, bundlePNG) = try CatalogViewInspector.renderPNG(window: window, size: regularSize)
        XCTAssertNotEqual(examPNG, bundlePNG)

        await feed.selectKind("TSA")
        XCTAssertEqual(feed.selectedProductCode, "BUNDLE-DEMO-01")
        XCTAssertTrue(feed.isNoResultsState)

        let compactView = CatalogHomeView(
            feed: feed,
            router: router,
            locale: .vi,
            showsBrandHeader: true
        )
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        .environment(\.shellWidth, .narrow)

        let compactSize = CGSize(width: 390, height: 844)
        let (compactWindow, _) = CatalogViewInspector.host(compactView, size: compactSize, sizeClass: .compact, settleSeconds: 0.15)
        defer { compactWindow.isHidden = true }
        let (_, compactDetailPNG) = try CatalogViewInspector.renderPNG(window: compactWindow, size: compactSize)

        feed.selectedProductCode = nil
        CatalogViewInspector.settle(window: compactWindow, seconds: 0.15)
        let (_, compactListPNG) = try CatalogViewInspector.renderPNG(window: compactWindow, size: compactSize)
        XCTAssertNotEqual(compactDetailPNG, compactListPNG)
        XCTAssertEqual(feed.query.kind, "TSA")

        let nav = CatalogNavigationHandler(router: router)
        nav.handleAccessButton(.buy(code: "EXAM-DEMO-01", seenVersionNo: 1, seenPrice: 200_000))
        XCTAssertEqual(
            router.path(for: .home),
            [.checkout(code: "EXAM-DEMO-01", seenVersionNo: 1, seenPrice: 200_000)]
        )
    }

    func testPhoneTwoPaneBookLeftListAndRightDetailCoordinateViaRegistry() async throws {
        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
            let examDetail = try CatalogTestFixtures.loadIndexedData(
                operationId: Operations.getStorefrontProduct.id,
                status: 200,
                variant: "exam"
            )
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01", data: examDetail)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        XCTAssertFalse(CatalogRoutes.spansBothPanesOnPhoneBook, "iPhone Duo book shell must render left list + right detail panes")

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

        let duoSize = CGSize(width: 669, height: 951)
        let (window, controller) = CatalogViewInspector.host(shell, size: duoSize, sizeClass: .regular, settleSeconds: 0.2)
        defer { window.isHidden = true }

        let navCount = findNavigationControllers(in: controller).count
        XCTAssertEqual(navCount, 2)
        let (_, beforeSelectPNG) = try CatalogViewInspector.renderPNG(window: window, size: duoSize)

        let feed = try XCTUnwrap(CatalogFeedRegistry.shared.existingFeed(for: router))
        feed.selectedProductCode = "EXAM-DEMO-01"
        try await waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01") == 1
                && feed.activeEmbeddedDetailViewModel?.loadedOutcome != nil
        }
        try await Task.sleep(nanoseconds: 80_000_000)
        window.layoutIfNeeded()

        let (_, afterSelectPNG) = try CatalogViewInspector.renderPNG(window: window, size: duoSize)
        XCTAssertNotEqual(beforeSelectPNG, afterSelectPNG, "Right Duo pane must visibly transition from placeholder to EXAM-DEMO-01 detail")

        stack.transport.resetRecordedRequests()
        router.switchTab(.history)
        CatalogViewInspector.settle(window: window, seconds: 0.12)
        router.switchTab(.home)
        try await waitUntil(window: window) {
            let batches = stack.transport
                .requests(method: .post, path: "/api/v1/me/exam-access/query")
                .map(CatalogTestStackFactory.accessRequestCodes(from:))
            return batches.contains(["EXAM-DEMO-01", "BUNDLE-DEMO-01"]) && batches.contains(["EXAM-DEMO-01"])
        }
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"),
            0,
            "Returning to Home on iPhone Duo must NOT re-fetch /products"
        )
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01"),
            0,
            "Returning to Home on iPhone Duo must retain loaded detail without re-fetching /products/{code}"
        )
    }

    func testIPadSidebarTabSwitchRetainsDetailAndRefreshesAccessWithoutReloadingProducts() async throws {
        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
            let examDetail = try CatalogTestFixtures.loadIndexedData(
                operationId: Operations.getStorefrontProduct.id,
                status: 200,
                variant: "exam"
            )
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01", data: examDetail)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let router = AppRouter(initialDestination: .home)
        defer { CatalogFeedRegistry.shared.clear(for: router) }

        let shell = AdaptiveShellView(
            router: router,
            preferences: stack.preferences,
            idiom: .pad,
            sessionStore: stack.sessionStore
        )
        .environment(\.sessionStore, stack.sessionStore)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let padSize = CGSize(width: 1180, height: 820)
        let (window, _) = CatalogViewInspector.host(shell, size: padSize, sizeClass: .regular, settleSeconds: 0.25)
        defer { window.isHidden = true }

        let feed = try XCTUnwrap(CatalogFeedRegistry.shared.existingFeed(for: router))
        try await waitUntil(window: window) { !feed.loadedCards.isEmpty }

        feed.selectedProductCode = "EXAM-DEMO-01"
        try await waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01") == 1
        }
        CatalogViewInspector.settle(window: window, seconds: 0.1)

        stack.transport.resetRecordedRequests()
        router.switchTab(.history)
        CatalogViewInspector.settle(window: window, seconds: 0.12)
        router.switchTab(.home)

        try await waitUntil(window: window) {
            let batches = stack.transport
                .requests(method: .post, path: "/api/v1/me/exam-access/query")
                .map(CatalogTestStackFactory.accessRequestCodes(from:))
            return batches.contains(["EXAM-DEMO-01", "BUNDLE-DEMO-01"]) && batches.contains(["EXAM-DEMO-01"])
        }
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"),
            0,
            "Returning to Home on iPad sidebar must NOT re-fetch /products"
        )
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01"),
            0,
            "Returning to Home on iPad sidebar must retain loaded detail without re-fetching /products/{code}"
        )
    }

    private func waitUntil(
        window: UIWindow,
        timeout: TimeInterval = 5.0,
        condition: () -> Bool
    ) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            window.setNeedsLayout()
            window.layoutIfNeeded()
            if condition() { return }
            try await Task.sleep(nanoseconds: 25_000_000)
        }
        XCTAssertTrue(condition(), "Timed out waiting for async UI update")
    }

    private func findNavigationControllers(in root: UIViewController) -> [UINavigationController] {
        var found: [UINavigationController] = []
        if let nav = root as? UINavigationController {
            found.append(nav)
        }
        for child in root.children {
            found.append(contentsOf: findNavigationControllers(in: child))
        }
        return found
    }
}
