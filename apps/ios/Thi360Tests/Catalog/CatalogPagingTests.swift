import Foundation
import XCTest
@testable import Thi360

@MainActor
final class CatalogPagingTests: XCTestCase {
    func testThirtyItemsPaginateAcrossThreePagesAndUpdateShowingLine() async throws {
        let allItems = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 30, codePrefix: "PG")
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

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        XCTAssertEqual(feed.loadedCards.count, 12)
        XCTAssertEqual(
            CatalogFormatting.showingLine(displayedCount: 12, total: 30, locale: .vi),
            "Hiển thị 1–12 trong 30 bài thi đang mở bán"
        )

        await feed.loadNextPageIfNeeded()
        XCTAssertEqual(feed.loadedCards.count, 24)

        await feed.loadNextPageIfNeeded()
        XCTAssertEqual(feed.loadedCards.count, 30)
        XCTAssertEqual(
            CatalogFormatting.showingLine(displayedCount: 30, total: 30, locale: .vi),
            "Hiển thị 1–30 trong 30 bài thi đang mở bán"
        )

        let callsBeforeExtra = stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products")
        await feed.loadNextPageIfNeeded()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), callsBeforeExtra)

        let pages = stack.transport
            .requests(method: .get, path: "/api/v1/public/storefront/products")
            .compactMap { CatalogTestStackFactory.queryItems(from: $0)["page"] }
        XCTAssertEqual(pages, ["0", "1", "2"])
    }

    func testInFlightGuardDeduplicationAndEmptyNextPageStop() async throws {
        let allItems = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 20, codePrefix: "DUP")
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let gate = CatalogAsyncGate()

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let page = Int(CatalogTestStackFactory.queryItems(from: request)["page"] ?? "0") ?? 0
                if page == 0 {
                    let data = try CatalogTestFixtures.makePageData(
                        allItems: Array(allItems[0..<12]),
                        page: 0,
                        totalItemsOverride: 30,
                        totalPagesOverride: 3
                    )
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                }
                if page == 1 {
                    await gate.wait()
                    let overlapSlice = [allItems[11]] + Array(allItems[12..<20])
                    let payload: [String: Any] = [
                        "items": overlapSlice,
                        "page": 1,
                        "size": 12,
                        "totalItems": 30,
                        "totalPages": 3
                    ]
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: try CatalogTestFixtures.encodeJSON(payload))
                }
                let emptyPayload: [String: Any] = [
                    "items": [],
                    "page": 2,
                    "size": 12,
                    "totalItems": 20,
                    "totalPages": 3
                ]
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: try CatalogTestFixtures.encodeJSON(emptyPayload))
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        XCTAssertEqual(feed.loadedCards.count, 12)

        let firstTask = Task { await feed.loadNextPageIfNeeded() }
        await Task.yield()
        let secondTask = Task { await feed.loadNextPageIfNeeded() }
        await gate.open()
        await firstTask.value
        await secondTask.value

        let page1Calls = stack.transport
            .requests(method: .get, path: "/api/v1/public/storefront/products")
            .filter { CatalogTestStackFactory.queryItems(from: $0)["page"] == "1" }
        XCTAssertEqual(page1Calls.count, 1, "Concurrent trigger must not send duplicate page=1 request")

        XCTAssertEqual(feed.loadedCards.count, 20)
        XCTAssertEqual(feed.loadedCards.filter { $0.code == "DUP-012" }.count, 1)

        await feed.loadNextPageIfNeeded()
        guard case let .loaded(finalPage, _) = feed.loadState else {
            return XCTFail("Expected loaded state")
        }
        XCTAssertEqual(finalPage.cards.count, 20)
        XCTAssertEqual(finalPage.total, 20)
        XCTAssertNil(finalPage.nextPage)
    }

    func testNextPageErrorRetriesSamePage() async throws {
        let allItems = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 24, codePrefix: "ERRPG")
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let serverError500 = try CatalogTestFixtures.makeInternalServerError500Data()
        let failPage1 = AtomicFlag(true)

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let page = Int(CatalogTestStackFactory.queryItems(from: request)["page"] ?? "0") ?? 0
                if page == 1 && failPage1.get() {
                    return ContractFakeTransport.jsonResponse(statusCode: 500, data: serverError500)
                }
                let data = try CatalogTestFixtures.makePageData(allItems: allItems, page: page)
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        XCTAssertEqual(feed.loadedCards.count, 12)

        await feed.loadNextPageIfNeeded()
        guard case let .loaded(failedPage, _) = feed.loadState else {
            return XCTFail("Expected loaded page with tail error")
        }
        XCTAssertEqual(failedPage.cards.count, 12)
        XCTAssertEqual(failedPage.nextPage, 1)
        XCTAssertEqual(failedPage.tail, .failed(.server))

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

        let hosted = listView
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, _) = CatalogViewInspector.host(hosted)
        defer { window.isHidden = true }
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 4096)

        failPage1.set(false)
        await feed.retryNextPage()

        guard case let .loaded(recoveredPage, _) = feed.loadState else {
            return XCTFail("Expected recovered page")
        }
        XCTAssertEqual(recoveredPage.cards.count, 24)
        XCTAssertEqual(recoveredPage.tail, .idle)

        let page1Attempts = stack.transport
            .requests(method: .get, path: "/api/v1/public/storefront/products")
            .filter { CatalogTestStackFactory.queryItems(from: $0)["page"] == "1" }
        XCTAssertEqual(page1Attempts.count, 2)
    }
}

actor CatalogAsyncGate {
    private var isOpen = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        if isOpen { return }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    func open() {
        isOpen = true
        let list = waiters
        waiters.removeAll()
        for continuation in list {
            continuation.resume()
        }
    }
}

final class AtomicFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Bool

    init(_ initial: Bool) {
        self.value = initial
    }

    func get() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set(_ next: Bool) {
        lock.lock()
        value = next
        lock.unlock()
    }
}
