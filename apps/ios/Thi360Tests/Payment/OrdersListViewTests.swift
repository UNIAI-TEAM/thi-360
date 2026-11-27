import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class OrdersListViewTests: XCTestCase {
    func testPaginationStopsAtLastPageAndStaleBannerPreservesItems() async throws {
        let page0 = try PaymentFixtureHelper.mutatingFixture("orders-list-200.json") { obj in
            obj["page"] = 0
            obj["size"] = 20
            obj["totalPages"] = 2
            obj["totalItems"] = 2
        }
        let page1 = try PaymentFixtureHelper.mutatingFixture("orders-list-200.json") { obj in
            obj["page"] = 1
            obj["size"] = 20
            obj["totalPages"] = 2
            obj["totalItems"] = 2
            if var items = obj["items"] as? [[String: Any]], !items.isEmpty {
                items[0]["id"] = "00000000-0000-7000-8000-000000000099"
                items[0]["code"] = "DH261126000002"
                items[0]["state"] = "FULFILLED"
                obj["items"] = items
            }
        }

        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.register(method: .get, path: "/api/v1/me/orders") { req in
                if req.path.contains("page=1") {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: page1)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: page0)
            }
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = OrdersViewModel(
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock,
            usernameProvider: { "student.demo" }
        )

        await vm.loadFirstPage(isRefresh: false)
        XCTAssertTrue(vm.hasMorePages)

        let req0 = try XCTUnwrap(harness.transport.requests(method: .get, path: "/api/v1/me/orders").first)
        XCTAssertTrue(req0.path.contains("page=0"))
        XCTAssertTrue(req0.path.contains("size=20"))

        await vm.loadNextPageIfNeeded()
        XCTAssertFalse(vm.hasMorePages)
        if case let .loaded(items, stale) = vm.state {
            XCTAssertEqual(items.map(\.code), ["DH261126000001", "DH261126000002"])
            XCTAssertFalse(stale)
        } else {
            return XCTFail("Expected loaded state")
        }

        let callsAtLastPage = harness.transport.callCount(method: .get, path: "/api/v1/me/orders")
        await vm.loadNextPageIfNeeded()
        XCTAssertEqual(harness.transport.callCount(method: .get, path: "/api/v1/me/orders"), callsAtLastPage)

        harness.transport.registerNetworkError(method: .get, path: "/api/v1/me/orders")
        await vm.loadFirstPage(isRefresh: true)
        if case let .loaded(items, stale) = vm.state {
            XCTAssertEqual(items.count, 2)
            XCTAssertTrue(stale)
            XCTAssertNotNil(vm.lastUpdatedAt)
        } else {
            XCTFail("Expected stale loaded state")
        }
    }

    func testEmptyErrorAndAmountFormatting() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders",
                    statusCode: 200,
                    relativeFixturePath: "api/orders-list-200-empty.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = OrdersViewModel(
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadFirstPage(isRefresh: false)
            XCTAssertEqual(vm.state, .empty)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                transport.registerEmpty(method: .get, path: "/api/v1/me/orders", statusCode: 500)
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = OrdersViewModel(
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadFirstPage(isRefresh: false)
            XCTAssertEqual(vm.state, .failed(kind: .server, canRetry: true, retryAfter: nil))
        }

        XCTAssertEqual(
            PaymentFormatters.formatOrderListAmount(paymentMethod: .FREE, amountDueVnd: 0, voucherDiscountVnd: 200_000, locale: .vi),
            "0 đ (đã áp dụng voucher)"
        )
        XCTAssertEqual(
            PaymentFormatters.formatOrderListAmount(paymentMethod: .FREE, amountDueVnd: 0, voucherDiscountVnd: 0, locale: .vi),
            "0 đ"
        )
        XCTAssertEqual(
            PaymentFormatters.formatOrderListAmount(paymentMethod: .VNPAY, amountDueVnd: 200_000, voucherDiscountVnd: 0, locale: .vi),
            "200.000 đ"
        )
    }
}
