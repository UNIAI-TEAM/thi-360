import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class CatalogStatesTests: XCTestCase {
    func testLoadingSkeletonAndEmptyUnfilteredHidesFilters() async throws {
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let emptyData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            variant: "empty"
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products", data: emptyData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)

        let listView = CatalogListColumnView(
            feed: feed,
            locale: .vi,
            showsBrandHeader: false,
            showsSelectionHighlight: false,
            onSelectProduct: { _ in },
            onAccessButtonTap: { _ in }
        )
        XCTAssertEqual(listView.loadingSkeletonLabel, "Đang tải danh mục")
        XCTAssertTrue(listView.showsFilterHeader)

        let wrapped = listView
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (loadingWindow, loadingController) = CatalogViewInspector.host(wrapped)
        let loadingFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: loadingController.view)
        XCTAssertEqual(loadingFields.count, 1, "Filter search field must remain usable during loading")
        let (_, loadingPNG) = try CatalogViewInspector.renderPNG(window: loadingWindow, size: CGSize(width: 390, height: 844))
        loadingWindow.isHidden = true

        await feed.loadInitialIfNeeded()
        XCTAssertTrue(feed.isEmptyUnfilteredCatalog)
        XCTAssertFalse(listView.showsFilterHeader)
        XCTAssertEqual(listView.emptyCatalogMessage, "Hiện chưa có bài thi nào đang mở bán")

        let (emptyWindow, emptyController) = CatalogViewInspector.host(wrapped)
        defer { emptyWindow.isHidden = true }
        let emptyFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: emptyController.view)
        XCTAssertEqual(emptyFields.count, 0, "Unfiltered empty catalog must hide filter bar")
        let (_, emptyPNG) = try CatalogViewInspector.renderPNG(window: emptyWindow, size: CGSize(width: 390, height: 844))
        XCTAssertNotEqual(loadingPNG, emptyPNG)
    }

    func testFilteredEmptyIncludingSortOnlyKeepsFiltersAndClearResets() async throws {
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let populatedData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            variant: ""
        )
        let emptyData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            variant: "empty"
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let query = CatalogTestStackFactory.queryItems(from: request)
                if query["sort"] == "PRICE_DESC" {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: emptyData)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: populatedData)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        await feed.selectSort(.priceDesc)

        XCTAssertTrue(feed.isNoResultsState)
        XCTAssertFalse(feed.isEmptyUnfilteredCatalog)

        let listView = CatalogListColumnView(
            feed: feed,
            locale: .vi,
            showsBrandHeader: false,
            showsSelectionHighlight: false,
            onSelectProduct: { _ in },
            onAccessButtonTap: { _ in }
        )
        XCTAssertTrue(listView.showsFilterHeader)
        XCTAssertEqual(listView.noResultsMessages.title, "Không tìm thấy bài thi phù hợp")
        XCTAssertEqual(listView.noResultsMessages.description, "Thử từ khóa khác hoặc bỏ bớt bộ lọc.")
        XCTAssertEqual(listView.noResultsMessages.clearLabel, "Xóa bộ lọc")

        let (window, controller) = CatalogViewInspector.host(
            listView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
        )
        defer { window.isHidden = true }
        XCTAssertEqual(
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count,
            1,
            "Filter bar must remain visible on no-results"
        )

        await feed.clearFilters()
        XCTAssertEqual(feed.query, .empty)
        XCTAssertEqual(feed.loadedCards.count, 2)
    }

    func testServer5xxAnd429KeepFiltersAndRetryBothEndpoints() async throws {
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let productsData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            variant: ""
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )
        let rateLimit429 = try CatalogTestFixtures.makeRateLimited429Data()
        let serverError500 = try CatalogTestFixtures.makeInternalServerError500Data()

        let mode = AtomicInt(500)

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.register(method: .get, path: "/api/v1/public/storefront/filters") { _ in
                if mode.get() == 500 {
                    return ContractFakeTransport.jsonResponse(statusCode: 500, data: serverError500)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: filtersData)
            }
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { _ in
                if mode.get() == 429 {
                    return ContractFakeTransport.jsonResponse(statusCode: 429, data: rateLimit429, headers: ["Retry-After": "2"])
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: productsData)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let clock = ManualClock()
        let feed = CatalogFeed(accountId: "acc-1", api: stack.api, clock: clock)
        await feed.selectKind("HSA")
        guard case let .failed(kind500, true, _) = feed.loadState else {
            return XCTFail("Expected server failure on filters 500, got \(feed.loadState)")
        }
        XCTAssertEqual(kind500, .server)
        XCTAssertEqual(feed.query.kind, "HSA")
        XCTAssertTrue(feed.canRetryNow)

        mode.set(429)
        stack.transport.resetRecordedRequests()
        await feed.retry()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/filters"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), 1)
        guard case let .failed(kind429, true, retryAfter429) = feed.loadState else {
            return XCTFail("Expected server failure on 429, got \(feed.loadState)")
        }
        XCTAssertEqual(kind429, .server)
        XCTAssertEqual(retryAfter429, 2)
        XCTAssertEqual(feed.query.kind, "HSA")
        XCTAssertFalse(feed.canRetryNow)

        let listView = CatalogListColumnView(
            feed: feed,
            locale: .vi,
            showsBrandHeader: false,
            showsSelectionHighlight: false,
            onSelectProduct: { _ in },
            onAccessButtonTap: { _ in }
        )
        XCTAssertEqual(listView.serverErrorMessages.message, "Chưa tải được danh mục bài thi. Bộ lọc của bạn vẫn được giữ.")
        XCTAssertEqual(listView.serverErrorMessages.retryLabel, "Thử lại")
        XCTAssertTrue(listView.isRetryBlocked)

        let (window, controller) = CatalogViewInspector.host(
            listView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
        )
        defer { window.isHidden = true }
        XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 1)

        mode.set(200)
        stack.transport.resetRecordedRequests()
        await feed.retry()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), 0, "Retry must stay blocked during Retry-After cooldown")

        clock.advance(by: 2.0)
        XCTAssertTrue(feed.canRetryNow)
        XCTAssertFalse(listView.isRetryBlocked)
        await feed.retry()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/filters"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), 1)
        XCTAssertEqual(feed.loadedCards.count, 2)
    }

    func testOfflineLaunchRecoversAndOfflineAfterLoadPreservesCardsWithBanner() async throws {
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let productsData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            variant: ""
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let offline = AtomicFlag(true)

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.register(method: .get, path: "/api/v1/public/storefront/filters") { _ in
                if offline.get() { throw URLError(.notConnectedToInternet) }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: filtersData)
            }
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { _ in
                if offline.get() { throw URLError(.notConnectedToInternet) }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: productsData)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let pathObserver = FakeNetworkPathObserver()
        let monitor = ConnectivityMonitor(initialIsOnline: false, observer: pathObserver, autoStart: false)
        monitor.start()

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api, connectivityMonitor: monitor)
        defer { feed.cancelAllTasks() }
        await feed.loadInitialIfNeeded()

        guard case let .failed(kind, true, _) = feed.loadState else {
            return XCTFail("Expected offline state, got \(feed.loadState)")
        }
        XCTAssertEqual(kind, .offline)

        let listView = CatalogListColumnView(
            feed: feed,
            locale: .vi,
            showsBrandHeader: false,
            showsSelectionHighlight: false,
            onSelectProduct: { _ in },
            onAccessButtonTap: { _ in }
        )
        XCTAssertEqual(listView.offlineErrorMessages.title, "Không có kết nối")
        XCTAssertEqual(listView.offlineErrorMessages.description, "Kiểm tra kết nối mạng rồi thử lại.")
        XCTAssertEqual(listView.offlineErrorMessages.retryLabel, "Thử lại")

        let (offlineWindow, _) = CatalogViewInspector.host(
            listView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
        )
        let (_, offlinePNG) = try CatalogViewInspector.renderPNG(window: offlineWindow, size: CGSize(width: 390, height: 844))
        offlineWindow.isHidden = true

        offline.set(false)
        pathObserver.emit(isOnline: true)
        let onlineDeadline = Date().addingTimeInterval(2.0)
        while feed.loadedCards.isEmpty && Date() < onlineDeadline {
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertEqual(feed.loadedCards.count, 2, "ConnectivityMonitor regaining network must auto-load offline Home")

        offline.set(true)
        pathObserver.emit(isOnline: false)
        await feed.selectKind("HSA")
        guard case let .loaded(preservedPage, stale) = feed.loadState else {
            return XCTFail("Expected preserved cards with stale banner, got \(feed.loadState)")
        }
        XCTAssertTrue(stale)
        XCTAssertEqual(feed.staleFailureKind, .offline)
        XCTAssertEqual(preservedPage.cards.count, 2)
        XCTAssertEqual(preservedPage.cards[0].name, "Đề thi HSA tổng hợp DEMO")
        XCTAssertEqual(listView.staleBannerMessage, "Không có kết nối")

        let (staleWindow, _) = CatalogViewInspector.host(
            listView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
        )
        defer { staleWindow.isHidden = true }
        let (_, stalePNG) = try CatalogViewInspector.renderPNG(window: staleWindow, size: CGSize(width: 390, height: 844))
        XCTAssertNotEqual(offlinePNG, stalePNG)

        offline.set(false)
        pathObserver.emit(isOnline: true)
        let recoverDeadline = Date().addingTimeInterval(2.0)
        while feed.staleFailureKind != nil && Date() < recoverDeadline {
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        if case let .loaded(refreshedPage, isStillStale) = feed.loadState {
            XCTAssertFalse(isStillStale)
            XCTAssertEqual(refreshedPage.cards.count, 2)
        } else {
            XCTFail("Expected stale offline catalog to auto-recover on online transition, got \(feed.loadState)")
        }
    }
}

final class AtomicInt: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Int

    init(_ initial: Int) {
        self.value = initial
    }

    func get() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set(_ next: Int) {
        lock.lock()
        value = next
        lock.unlock()
    }
}
