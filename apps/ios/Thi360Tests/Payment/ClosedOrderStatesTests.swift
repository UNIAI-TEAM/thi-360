import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ClosedOrderStatesTests: XCTestCase {
    func testClosedOrderLabelsTonesAndReplacedOrderLink() async throws {
        XCTAssertEqual(PaymentFormatters.orderStateTone("EXPIRED"), .neutral)
        XCTAssertEqual(PaymentFormatters.orderStateTone("CANCELLED"), .neutral)
        XCTAssertEqual(PaymentFormatters.orderStateTone("REPLACED"), .neutral)
        XCTAssertEqual(PaymentFormatters.orderStateTone("REFUNDED"), .info)

        XCTAssertEqual(PaymentFormatters.orderStateLabel("EXPIRED", locale: .vi), "Hết hạn")
        XCTAssertEqual(PaymentFormatters.orderStateLabel("CANCELLED", locale: .vi), "Đã hủy")
        XCTAssertEqual(PaymentFormatters.orderStateLabel("REFUNDED", locale: .vi), "Đã hoàn tiền")
        XCTAssertEqual(PaymentFormatters.orderStateLabel("REPLACED", locale: .vi), "Đã thay thế")

        let replacedData = try PaymentFixtureHelper.mutatingFixture("orders-detail-200-replaced.json") { obj in
            obj["replacedByOrderCode"] = "DH261126000099"
            obj["history"] = [
                [
                    "id": "00000000-0000-7000-8000-000000000088",
                    "fromState": "PENDING_PAYMENT",
                    "toState": "REPLACED",
                    "actor": "SYSTEM",
                    "reason": "REPLACED_BY:DH261126000099",
                    "at": "2026-11-20T10:05:00Z"
                ]
            ]
        }
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/orders/DH261126000001", statusCode: 200, data: replacedData)
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = OrderDetailViewModel(
            orderCode: "DH261126000001",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.load(isRefresh: false)

        var openedReplacement: String?
        let view = OrderDetailView(
            viewModel: vm,
            locale: .vi,
            supportContact: .empty,
            onGoMyExams: {},
            onOpenOrder: { openedReplacement = $0 },
            onBackToOrders: {},
            onCreateNewOrder: { _ in },
            onOpenCatalog: {},
            onSwitchAccount: {}
        )
        let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1500), sizeClass: .compact)
        defer { window.isHidden = true }

        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        XCTAssertEqual(vm.loadedOrder?.state, .REPLACED)
        XCTAssertEqual(vm.loadedOrder?.replacedByOrderCode, "DH261126000099")
        XCTAssertEqual(
            PaymentStrings.text("orders.actions.viewReplacement", locale: .vi, replacements: ["code": "DH261126000099"]),
            "Xem đơn thay thế DH261126000099"
        )
        XCTAssertEqual(
            OrderHistoryMapper.reasonLabel(vm.loadedOrder?.history.first?.reason ?? "", locale: .vi),
            "Đã thay thế bằng đơn DH261126000099"
        )
        XCTAssertNil(openedReplacement)
    }

    func testPendingOrderExpiresWhileViewingReloadsToExpiredAndPayReturnsOrderExpired() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/orders/DH261126000001", handlers: [
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-pending-payment.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-expired.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                }
            ])
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders/DH261126000001/pay",
                statusCode: 409,
                relativeFixturePath: "api/orders-pay-409-order-expired.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = OrderDetailViewModel(
            orderCode: "DH261126000001",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.load(isRefresh: false)
        XCTAssertEqual(vm.loadedOrder?.state, .PENDING_PAYMENT)

        await vm.continuePayment()
        XCTAssertEqual(vm.actionError?.code, .orderExpired)
        XCTAssertEqual(vm.loadedOrder?.state, .EXPIRED)
    }
}
