import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PaymentSnapshotTests: XCTestCase {
    private struct DeviceSpec {
        let name: String
        let idiom: UIUserInterfaceIdiom
        let size: CGSize
        let sizeClass: UIUserInterfaceSizeClass
    }

    private static let specDevices: [DeviceSpec] = [
        DeviceSpec(name: "iphone17-390x844", idiom: .phone, size: CGSize(width: 390, height: 844), sizeClass: .compact),
        DeviceSpec(name: "ipad-landscape-1180x820", idiom: .pad, size: CGSize(width: 1180, height: 820), sizeClass: .regular),
        DeviceSpec(name: "ipad-portrait-820x1180", idiom: .pad, size: CGSize(width: 820, height: 1180), sizeClass: .regular),
        DeviceSpec(name: "iphone-duo-folded-466x678", idiom: .phone, size: CGSize(width: 466, height: 678), sizeClass: .compact),
        DeviceSpec(name: "iphone-duo-unfolded-951x669", idiom: .phone, size: CGSize(width: 951, height: 669), sizeClass: .regular)
    ]

    private static let accountDetailPaneSpecs: [DeviceSpec] = [
        DeviceSpec(name: "ipad-1180x820-account-detail-pane", idiom: .pad, size: CGSize(width: 560, height: 820), sizeClass: .regular),
        DeviceSpec(name: "iphone-duo-unfolded-account-detail-pane", idiom: .phone, size: CGSize(width: 520, height: 669), sizeClass: .regular)
    ]

    func testFourPaymentScreensSnapshotsLightDarkAnd130PercentText() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200-voucher.json")
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
                relativeFixturePath: "api/orders-detail-200-fulfilled.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let checkoutVM = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await checkoutVM.loadAll()

        let resultVM = PaymentResultViewModel(
            initialEntry: .byOrder(orderCode: "DH261126000001"),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await resultVM.startIfNeeded()

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
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await detailVM.load(isRefresh: false)

        let support = SupportContact(phone: "1900 1234", email: "hotro@thi360.vn", hours: "08:00–22:00")

        for spec in Self.specDevices {
            let swiftUISizeClass: UserInterfaceSizeClass = (spec.sizeClass == .regular) ? .regular : .compact
            let widthClass = ShellWidth.resolve(
                idiom: spec.idiom,
                horizontalSizeClass: swiftUISizeClass,
                availableWidth: spec.size.width
            )

            try assertLightDarkSnapshot(name: "payment-checkout-\(spec.name)", spec: spec) { mode in
                CheckoutView(
                    viewModel: checkoutVM,
                    locale: .vi,
                    supportContact: support,
                    fallbackUsername: "student.demo",
                    onBackToProduct: {},
                    onBackToCatalog: {},
                    onSwitchAccount: {},
                    onEnterExam: { _ in },
                    onContinueAttempt: { _ in },
                    onViewPendingOrder: { _ in }
                )
                .environment(\.shellWidth, widthClass)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.dynamicTypeSize, .xxLarge)
            }

            try assertLightDarkSnapshot(name: "payment-result-\(spec.name)", spec: spec) { mode in
                PaymentResultView(
                    viewModel: resultVM,
                    locale: .vi,
                    supportContact: support,
                    onGoMyExams: {},
                    onViewOrderDetail: { _ in },
                    onViewOrders: {},
                    onBuyAgain: { _ in },
                    onOpenCatalog: {},
                    onSwitchAccount: {}
                )
                .environment(\.shellWidth, widthClass)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.dynamicTypeSize, .xxLarge)
            }

            try assertLightDarkSnapshot(name: "payment-orders-\(spec.name)", spec: spec) { mode in
                OrdersView(
                    viewModel: ordersVM,
                    locale: .vi,
                    supportContact: support,
                    onOpenOrderDetail: { _ in },
                    onGoMyExams: {},
                    onCreateNewOrder: { _ in },
                    onOpenCatalog: {},
                    detailPane: { _ in
                        OrderDetailView(
                            viewModel: detailVM,
                            locale: .vi,
                            supportContact: support,
                            onGoMyExams: {},
                            onOpenOrder: { _ in },
                            onBackToOrders: {},
                            onCreateNewOrder: { _ in },
                            onOpenCatalog: {},
                            onSwitchAccount: {}
                        )
                    }
                )
                .environment(\.shellWidth, widthClass)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.dynamicTypeSize, .xxLarge)
            }

            try assertLightDarkSnapshot(name: "payment-order-detail-\(spec.name)", spec: spec) { mode in
                OrderDetailView(
                    viewModel: detailVM,
                    locale: .vi,
                    supportContact: support,
                    onGoMyExams: {},
                    onOpenOrder: { _ in },
                    onBackToOrders: {},
                    onCreateNewOrder: { _ in },
                    onOpenCatalog: {},
                    onSwitchAccount: {}
                )
                .environment(\.shellWidth, widthClass)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.dynamicTypeSize, .xxLarge)
            }
        }

        for spec in Self.accountDetailPaneSpecs {
            try assertLightDarkSnapshot(name: "payment-orders-\(spec.name)", spec: spec) { mode in
                OrdersView(
                    viewModel: ordersVM,
                    locale: .vi,
                    supportContact: support,
                    onOpenOrderDetail: { _ in },
                    onGoMyExams: {},
                    onCreateNewOrder: { _ in },
                    onOpenCatalog: {},
                    detailPane: { _ in
                        OrderDetailView(
                            viewModel: detailVM,
                            locale: .vi,
                            supportContact: support,
                            onGoMyExams: {},
                            onOpenOrder: { _ in },
                            onBackToOrders: {},
                            onCreateNewOrder: { _ in },
                            onOpenCatalog: {},
                            onSwitchAccount: {}
                        )
                    }
                )
                .environment(\.shellWidth, .twoColumn)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.dynamicTypeSize, .xxLarge)
            }
        }
    }

    private func assertLightDarkSnapshot<V: View>(
        name: String,
        spec: DeviceSpec,
        @ViewBuilder makeView: (ThemeMode) -> V
    ) throws {
        var pngs: [ThemeMode: Data] = [:]
        for mode in ThemeMode.allCases {
            let (window, _) = RecoveryViewTestHelper.host(
                makeView(mode),
                size: spec.size,
                sizeClass: spec.sizeClass,
                themeMode: mode
            )
            defer { window.isHidden = true }
            pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                window: window,
                size: spec.size,
                name: "\(name)-\(mode.rawValue)",
                in: self
            )
        }
        XCTAssertNotEqual(pngs[.light], pngs[.dark], "Light/dark snapshots must differ for \(name)")
    }
}
