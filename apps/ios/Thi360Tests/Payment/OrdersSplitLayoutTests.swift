import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class OrdersSplitLayoutTests: XCTestCase {
    func testOrdersLayoutAdaptsAcrossCompactRegularAndAccountDetailPaneWidths() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders",
                statusCode: 200,
                relativeFixturePath: "api/orders-list-200.json"
            )
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-pending-payment.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let ordersVM = OrdersViewModel(
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            usernameProvider: { "student.demo" }
        )
        await ordersVM.loadFirstPage(isRefresh: false)
        ordersVM.selectedOrderCode = "DH261126000001"

        let detailVM = OrderDetailViewModel(
            orderCode: "DH261126000001",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            onOrderMutated: { detail in
                ordersVM.applyUpdatedOrderDetail(detail)
            }
        )
        await detailVM.load(isRefresh: false)

        func makeOrdersView(shellWidth: ShellWidth) -> some View {
            OrdersView(
                viewModel: ordersVM,
                locale: .vi,
                supportContact: .empty,
                onOpenOrderDetail: { _ in },
                onGoMyExams: {},
                onCreateNewOrder: { _ in },
                onOpenCatalog: {},
                detailPane: { _ in
                    OrderDetailView(
                        viewModel: detailVM,
                        locale: .vi,
                        supportContact: .empty,
                        onGoMyExams: {},
                        onOpenOrder: { _ in },
                        onBackToOrders: {},
                        onCreateNewOrder: { _ in },
                        onOpenCatalog: {},
                        onSwitchAccount: {}
                    )
                }
            )
            .environment(\.shellWidth, shellWidth)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
        }

        XCTAssertEqual(OrdersView<EmptyView>.resolvedShellWidth(shellWidth: .narrow, availableWidth: 390), .narrow)
        XCTAssertEqual(OrdersView<EmptyView>.resolvedShellWidth(shellWidth: .twoColumn, availableWidth: 1180), .twoColumn)
        XCTAssertEqual(OrdersView<EmptyView>.resolvedShellWidth(shellWidth: .twoColumn, availableWidth: 951), .twoColumn)
        XCTAssertEqual(OrdersView<EmptyView>.resolvedShellWidth(shellWidth: .twoColumn, availableWidth: 560), .wide)
        XCTAssertEqual(OrdersView<EmptyView>.resolvedShellWidth(shellWidth: .twoColumn, availableWidth: 520), .wide)

        do {
            let (window, controller) = RecoveryViewTestHelper.host(
                makeOrdersView(shellWidth: .narrow),
                size: CGSize(width: 390, height: 844),
                sizeClass: .compact
            )
            defer { window.isHidden = true }
            XCTAssertEqual(controller.view.bounds.width, 390, accuracy: 1)
        }

        do {
            let (window, controller) = RecoveryViewTestHelper.host(
                makeOrdersView(shellWidth: .twoColumn),
                size: CGSize(width: 1180, height: 820),
                sizeClass: .regular
            )
            defer { window.isHidden = true }
            XCTAssertEqual(controller.view.bounds.width, 1180, accuracy: 1)
        }

        do {
            let (window, controller) = RecoveryViewTestHelper.host(
                makeOrdersView(shellWidth: .twoColumn),
                size: CGSize(width: 951, height: 669),
                sizeClass: .regular
            )
            defer { window.isHidden = true }
            XCTAssertEqual(controller.view.bounds.width, 951, accuracy: 1)
        }

        do {
            let (window, controller) = RecoveryViewTestHelper.host(
                makeOrdersView(shellWidth: .twoColumn),
                size: CGSize(width: 520, height: 820),
                sizeClass: .regular
            )
            defer { window.isHidden = true }
            XCTAssertEqual(controller.view.bounds.width, 520, accuracy: 1)
        }

        try harness.transport.registerFixture(
            method: .post,
            path: "/api/v1/me/orders/DH261126000001/cancel",
            statusCode: 200,
            relativeFixturePath: "api/orders-cancel-200.json"
        )
        detailVM.requestCancel()
        await detailVM.confirmCancel()

        if case let .loaded(items, _) = ordersVM.state {
            XCTAssertEqual(items.first?.state, .CANCELLED, "Right-pane cancel must update left list immediately")
        } else {
            XCTFail("Expected orders list loaded after right-pane cancel")
        }
    }
}
