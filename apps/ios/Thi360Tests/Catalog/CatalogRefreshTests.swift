import Foundation
import XCTest
@testable import Thi360

@MainActor
final class CatalogRefreshTests: XCTestCase {
    func testAccessOnReturn() async throws {
        XCTAssertEqual(CatalogFormatting.chunked(Array(1...60), size: 48).map(\.count), [48, 12])
        XCTAssertEqual(CatalogFormatting.chunked(Array(1...48), size: 48).map(\.count), [48])
        XCTAssertEqual(CatalogFormatting.chunked(Array(1...50), size: 48).map(\.count), [48, 2])

        let all60 = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 60, codePrefix: "RET")
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )

        let updatedReturnState = AtomicFlag(false)

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let page = Int(CatalogTestStackFactory.queryItems(from: request)["page"] ?? "0") ?? 0
                let data = try CatalogTestFixtures.makePageData(allItems: all60, page: page)
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
            transport.register(method: .post, path: "/api/v1/me/exam-access/query") { request in
                let codes = CatalogTestStackFactory.accessRequestCodes(from: request)
                let rows: [[String: Any]] = try codes.map { code in
                    if code == "RET-001", updatedReturnState.get() {
                        return try CatalogTestFixtures.makeExamAccessItem(
                            templateState: "CAN_START",
                            code: code,
                            attemptsLeft: 2,
                            entitlementId: "ent-new"
                        )
                    }
                    if code == "RET-002", updatedReturnState.get() {
                        return try CatalogTestFixtures.makeExamAccessItem(
                            templateState: "CHECKING",
                            code: code,
                            pendingOrderCode: "DH009"
                        )
                    }
                    return try CatalogTestFixtures.makeExamAccessItem(templateState: "NONE", code: code)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: try CatalogTestFixtures.makeExamAccessData(items: rows))
            }
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        for _ in 0..<4 {
            await feed.loadNextPageIfNeeded()
        }
        XCTAssertEqual(feed.loadedCards.count, 60)
        if case let .ready(row) = feed.accessCell(for: "RET-001") {
            XCTAssertEqual(row.state, .none)
        } else {
            XCTFail("Expected initial NONE for RET-001")
        }

        stack.transport.resetRecordedRequests()
        updatedReturnState.set(true)
        await feed.refreshAccessOnReturn()

        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products"), 0)
        let batches = stack.transport
            .requests(method: .post, path: "/api/v1/me/exam-access/query")
            .map { CatalogTestStackFactory.accessRequestCodes(from: $0).count }
        XCTAssertEqual(batches, [48, 12])

        if case let .ready(updated1) = feed.accessCell(for: "RET-001") {
            XCTAssertEqual(updated1.state, .canStart)
            XCTAssertEqual(updated1.attemptsLeft, 2)
        } else {
            XCTFail("Expected CAN_START for RET-001 after return refresh")
        }

        if case let .ready(updated2) = feed.accessCell(for: "RET-002") {
            XCTAssertEqual(updated2.state, .checking)
            XCTAssertEqual(updated2.pendingOrderCode, "DH009")
        } else {
            XCTFail("Expected CHECKING for RET-002 after return refresh")
        }
    }

    func testPullToRefresh() async throws {
        let all40 = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 40, codePrefix: "PTR")
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )

        let serverError500 = try CatalogTestFixtures.makeInternalServerError500Data()
        let refreshed = AtomicFlag(false)
        let shouldFailRefresh = AtomicFlag(false)

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                if shouldFailRefresh.get() {
                    return ContractFakeTransport.jsonResponse(statusCode: 500, data: serverError500)
                }
                let page = Int(CatalogTestStackFactory.queryItems(from: request)["page"] ?? "0") ?? 0
                let data = try CatalogTestFixtures.makePageData(allItems: all40, page: page)
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
            transport.register(method: .post, path: "/api/v1/me/exam-access/query") { request in
                let codes = CatalogTestStackFactory.accessRequestCodes(from: request)
                let rows: [[String: Any]] = try codes.map { code in
                    try CatalogTestFixtures.makeExamAccessItem(
                        templateState: (code == "PTR-001" && refreshed.get()) ? "EXHAUSTED" : "NONE",
                        code: code
                    )
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: try CatalogTestFixtures.makeExamAccessData(items: rows))
            }
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        await feed.selectKind("HSA")
        await feed.loadNextPageIfNeeded()
        await feed.loadNextPageIfNeeded()
        XCTAssertEqual(feed.loadedCards.count, 36)

        shouldFailRefresh.set(true)
        await feed.pullToRefresh()
        guard case let .loaded(preserved, isStale) = feed.loadState else {
            return XCTFail("Expected preserved cards on failed refresh")
        }
        XCTAssertTrue(isStale)
        XCTAssertEqual(preserved.cards.count, 36)

        shouldFailRefresh.set(false)
        refreshed.set(true)
        stack.transport.resetRecordedRequests()
        let oldScrollToken = feed.scrollToTopToken

        await feed.pullToRefresh()

        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/filters"), 1)
        let productReqs = stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products")
        XCTAssertEqual(productReqs.count, 1)
        let q = CatalogTestStackFactory.queryItems(from: productReqs[0])
        XCTAssertEqual(q["kind"], "HSA")
        XCTAssertEqual(q["page"], "0")
        XCTAssertEqual(q["size"], "12")

        let accessReqs = stack.transport.requests(method: .post, path: "/api/v1/me/exam-access/query")
        XCTAssertEqual(accessReqs.count, 1)
        XCTAssertEqual(CatalogTestStackFactory.accessRequestCodes(from: accessReqs[0]).count, 12)

        guard case let .loaded(resetPage, resetStale) = feed.loadState else {
            return XCTFail("Expected loaded page after refresh")
        }
        XCTAssertFalse(resetStale)
        XCTAssertEqual(resetPage.cards.count, 12)
        XCTAssertEqual(resetPage.total, 40)
        XCTAssertEqual(resetPage.nextPage, 1)
        XCTAssertGreaterThan(feed.scrollToTopToken, oldScrollToken)
        XCTAssertEqual(
            CatalogFormatting.showingLine(displayedCount: resetPage.cards.count, total: resetPage.total, locale: .vi),
            "Hiển thị 1–12 trong 40 bài thi đang mở bán"
        )
        if case let .ready(row) = feed.accessCell(for: "PTR-001") {
            XCTAssertEqual(row.state, .exhausted)
        } else {
            XCTFail("Expected updated EXHAUSTED state on PTR-001")
        }

        await feed.loadNextPageIfNeeded()
        let nextReq = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").last)
        XCTAssertEqual(CatalogTestStackFactory.queryItems(from: nextReq)["page"], "1")
    }

    func testStaleNextPageDropped() async throws {
        let all40 = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 40, codePrefix: "STALE")
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let page2Gate = CatalogAsyncGate()
        let page2Entered = CatalogAsyncGate()

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let page = Int(CatalogTestStackFactory.queryItems(from: request)["page"] ?? "0") ?? 0
                if page == 2 {
                    await page2Entered.open()
                    await page2Gate.wait()
                }
                let data = try CatalogTestFixtures.makePageData(allItems: all40, page: page)
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        await feed.loadNextPageIfNeeded()
        XCTAssertEqual(feed.loadedCards.count, 24)

        let page2Task = Task { await feed.loadNextPageIfNeeded() }
        await page2Entered.wait()

        await feed.pullToRefresh()
        XCTAssertEqual(feed.loadedCards.count, 12)

        await page2Gate.open()
        await page2Task.value

        XCTAssertEqual(feed.loadedCards.count, 12)
        XCTAssertEqual(feed.loadedCards.first?.code, "STALE-001")
        XCTAssertEqual(feed.loadedCards.last?.code, "STALE-012")
    }
}
