import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class OrderDetailViewTests: XCTestCase {
    func testSnapshotPaymentsHistoryAndFallbackFormatters() async throws {
        XCTAssertEqual(OrderHistoryMapper.actorLabel("LEARNER", locale: .vi), "Học viên")
        XCTAssertEqual(OrderHistoryMapper.actorLabel("VNPAY_IPN", locale: .vi), "Cổng thanh toán VNPAY")
        XCTAssertEqual(OrderHistoryMapper.actorLabel("MYSTERY_BOT", locale: .vi), "Hệ thống")

        XCTAssertEqual(
            OrderHistoryMapper.reasonLabel("REPLACED_BY:DH261126000099", locale: .vi),
            "Đã thay thế bằng đơn DH261126000099"
        )
        XCTAssertEqual(
            OrderHistoryMapper.reasonLabel("UNKNOWN_FUTURE_REASON", locale: .vi),
            "Cập nhật trạng thái đơn hàng"
        )
        XCTAssertEqual(
            OrderHistoryMapper.transitionLabel(fromState: nil, toState: "PENDING_PAYMENT", locale: .vi),
            "Tạo đơn"
        )
        XCTAssertEqual(
            OrderHistoryMapper.transitionLabel(fromState: "PENDING_PAYMENT", toState: "PAID", locale: .vi),
            "Chờ thanh toán → Đang kiểm tra"
        )

        XCTAssertEqual(PaymentFormatters.formatBankAndCard(bankCode: "NCB", cardType: "ATM"), "NCB · ATM")
        XCTAssertEqual(PaymentFormatters.formatBankAndCard(bankCode: nil, cardType: nil), "—")
        XCTAssertEqual(PaymentFormatters.formatAttemptSequence(1), "01")
        XCTAssertEqual(PaymentFormatters.formatAttemptSequence(12), "12")

        XCTAssertEqual(
            PaymentFormatters.formatPaymentMethodBadge(paymentMethod: .VNPAY, voucherDiscountVnd: 0, locale: .vi),
            "VNPAY"
        )
        XCTAssertEqual(
            PaymentFormatters.formatPaymentMethodBadge(paymentMethod: .FREE, voucherDiscountVnd: 0, locale: .vi),
            "Miễn phí"
        )
        XCTAssertEqual(
            PaymentFormatters.formatPaymentMethodBadge(paymentMethod: .FREE, voucherDiscountVnd: 200_000, locale: .vi),
            "Miễn phí nhờ voucher"
        )

        XCTAssertEqual(
            OrderDetailView.formatJoinedPair("minhanh.tran", "Trần Minh Anh"),
            "minhanh.tran · Trần Minh Anh"
        )
        XCTAssertEqual(OrderDetailView.formatJoinedPair("minhanh.tran", ""), "minhanh.tran")
        XCTAssertEqual(OrderDetailView.formatJoinedPair("  ", "Trần Minh Anh"), "Trần Minh Anh")
        XCTAssertNil(OrderDetailView.formatJoinedPair(" ", nil))
        XCTAssertEqual(
            OrderDetailView.formatBundleItemTitle(examName: "Đề Toán", examCode: "EXAM-MATH-01"),
            "Đề Toán (EXAM-MATH-01)"
        )
        XCTAssertEqual(
            OrderDetailView.formatBundleItemTitle(examName: "Đề Toán", examCode: "  "),
            "Đề Toán"
        )

        let nullVersionData = try PaymentFixtureHelper.mutatingFixture("orders-detail-200-free-voucher.json") { obj in
            obj["examVersionNo"] = NSNull()
            obj["bundleVersionNo"] = NSNull()
            obj["productType"] = "BUNDLE"
            obj["items"] = [
                [
                    "id": "00000000-0000-7000-8000-000000000021",
                    "position": 1,
                    "examId": "00000000-0000-7000-8000-000000000031",
                    "examCode": "EXAM-DEMO-02",
                    "examName": "Đề thi Toán HSA",
                    "examVersionNo": 1,
                    "attemptsGranted": 2,
                    "resultView": "FULL",
                    "optionCodes": [],
                    "benefits": ["aiExplanation": true, "strengthWeakness": true, "practice": false]
                ]
            ]
        }
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/orders/DH261126000001", statusCode: 200, data: nullVersionData)
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

        let view = OrderDetailView(
            viewModel: vm,
            locale: .vi,
            supportContact: .empty,
            onGoMyExams: {},
            onOpenOrder: { _ in },
            onBackToOrders: {},
            onCreateNewOrder: { _ in },
            onOpenCatalog: {},
            onSwitchAccount: {}
        )
        let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1800), sizeClass: .compact)
        defer { window.isHidden = true }

        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        XCTAssertEqual(
            OrderDetailView.formatJoinedPair(vm.loadedOrder?.buyerUsername, vm.loadedOrder?.buyerFullName),
            "\(vm.loadedOrder?.buyerUsername ?? "") · \(vm.loadedOrder?.buyerFullName ?? "")"
        )
        XCTAssertEqual(
            OrderDetailView.formatJoinedPair(vm.loadedOrder?.productName, vm.loadedOrder?.productCode),
            "\(vm.loadedOrder?.productName ?? "") · \(vm.loadedOrder?.productCode ?? "")"
        )
        XCTAssertEqual(vm.loadedOrder?.items.first?.examCode, "EXAM-DEMO-02")
        XCTAssertNil(vm.loadedOrder?.examVersionNo)
        XCTAssertNil(vm.loadedOrder?.bundleVersionNo)
        XCTAssertEqual(vm.loadedOrder?.payments.count, 0)
        XCTAssertEqual(PaymentStrings.text("orders.detail.payments.empty", locale: .vi), "Đơn miễn phí, không phát sinh giao dịch VNPAY.")
    }

    func testNotFound404AndStaleRefreshError() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/MISSING",
                    statusCode: 404,
                    relativeFixturePath: "api/orders-detail-404-order-not-found.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = OrderDetailViewModel(
                orderCode: "MISSING",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.load(isRefresh: false)
            XCTAssertTrue(vm.isNotFound)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/DH261126000001",
                    statusCode: 200,
                    relativeFixturePath: "api/orders-detail-200-paid.json"
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
            XCTAssertEqual(vm.loadedOrder?.state, .PAID)

            harness.transport.registerNetworkError(method: .get, path: "/api/v1/me/orders/DH261126000001")
            await vm.reload()

            if case let .loaded(order, stale) = vm.state {
                XCTAssertEqual(order.code, "DH261126000001")
                XCTAssertTrue(stale)
            } else {
                XCTFail("Expected stale loaded state")
            }
        }
    }
}
