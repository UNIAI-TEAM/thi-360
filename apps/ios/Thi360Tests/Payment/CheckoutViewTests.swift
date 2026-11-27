import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class CheckoutViewTests: XCTestCase {
    func testShowsAccountProductRefund() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            seenPrice: SeenPrice(versionNo: 1, price: 200_000),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.loadAll()

        XCTAssertEqual(vm.displayUser?.username, "student.demo")
        XCTAssertEqual(vm.displayUser?.fullName, "Nguyễn Văn An")
        XCTAssertEqual(vm.loadedProduct?.detail.name, "Đề thi HSA tổng hợp DEMO")
        XCTAssertEqual(vm.activeQuote?.versionNo, 1)
        XCTAssertTrue(vm.isPayButtonEnabled)

        let support = SupportContact(phone: "1900 1234", email: "hotro@thi360.vn", hours: "08:00–22:00")
        let view = NavigationStack {
            CheckoutView(
                viewModel: vm,
                locale: .vi,
                supportContact: support,
                fallbackUsername: nil,
                onBackToProduct: {},
                onBackToCatalog: {},
                onSwitchAccount: {},
                onEnterExam: { _ in },
                onContinueAttempt: { _ in },
                onViewPendingOrder: { _ in }
            )
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1400), sizeClass: .compact)
        defer { window.isHidden = true }

        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        XCTAssertEqual(PaymentStrings.text("checkout.heading", locale: .vi), "Xác nhận mua")
        XCTAssertEqual(PaymentStrings.text("checkout.account.title", locale: .vi), "Tài khoản nhận bài thi")
        XCTAssertEqual(PaymentStrings.text("checkout.product.sectionTitle", locale: .vi), "Thông tin bài thi")
        XCTAssertEqual(
            PaymentStrings.text("checkout.product.version", locale: .vi, replacements: ["version": "1"]),
            "Phiên bản v1"
        )
        XCTAssertEqual(PaymentStrings.text("checkout.refund.title", locale: .vi), "Chính sách hoàn tiền")
        XCTAssertEqual(PaymentFormatters.formatSupportSummary(support), "1900 1234 · hotro@thi360.vn (08:00–22:00)")
        XCTAssertEqual(SupportContact.empty.pendingMessage(locale: .vi), "Thông tin liên hệ đang được cập nhật.")
    }

    func testBundleListsExams() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerBundleCheckoutDefaults(on: transport)
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "BUNDLE-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.loadAll()

        XCTAssertTrue(vm.isBundle)
        XCTAssertEqual(vm.loadedProduct?.bundleItems.count, 2)
        XCTAssertNotNil(vm.loadedProduct?.bundleItemMetaByCode["EXAM-DEMO-02"]?.subjectName)
        XCTAssertNotNil(vm.loadedProduct?.bundleItemMetaByCode["EXAM-DEMO-02"]?.questionCount)
        XCTAssertNotNil(vm.loadedProduct?.bundleItemMetaByCode["EXAM-DEMO-02"]?.durationMinutes)

        let view = NavigationStack {
            CheckoutView(
                viewModel: vm,
                locale: .vi,
                supportContact: .empty,
                fallbackUsername: nil,
                onBackToProduct: {},
                onBackToCatalog: {},
                onSwitchAccount: {},
                onEnterExam: { _ in },
                onContinueAttempt: { _ in },
                onViewPendingOrder: { _ in }
            )
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1800), sizeClass: .compact)
        defer { window.isHidden = true }

        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        XCTAssertEqual(vm.accessRow?.items?.first(where: { $0.code == "EXAM-DEMO-02" })?.attemptsLeft, 2)
        XCTAssertEqual(PaymentStrings.plural("payment.access.attemptsLeft", count: 2, locale: .vi), "Còn 2 lượt")
        XCTAssertEqual(
            PaymentStrings.text("bundle.checkout.existingNote", locale: .vi),
            "Bài đã có quyền thì lượt mua thêm cộng vào lượt đang có. Quyền lợi theo từng lần cấp."
        )
        XCTAssertEqual(
            CheckoutView.quoteErrorDescription(locale: .vi),
            "Vui lòng kiểm tra kết nối mạng và thử lại."
        )
        XCTAssertEqual(
            CheckoutView.quoteErrorDescription(locale: .en),
            "Please check your internet connection and try again."
        )

        do {
            let noAccessData = try PaymentFixtureHelper.mutatingFixture("exam-access-query-200.json") { obj in
                if var items = obj["items"] as? [[String: Any]], !items.isEmpty {
                    items[0]["attemptsLeft"] = 0
                    items[0]["state"] = "NONE"
                    items[0]["items"] = []
                    obj["items"] = items
                }
            }
            let harnessNoAccess = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerBundleCheckoutDefaults(on: transport)
                transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", statusCode: 200, data: noAccessData)
            }
            defer { try? FileManager.default.removeItem(at: harnessNoAccess.storeDirURL) }

            let vmNoAccess = CheckoutViewModel(
                code: "BUNDLE-DEMO-01",
                gateway: harnessNoAccess.gateway,
                paymentBrowser: harnessNoAccess.browser,
                pendingOrderStore: harnessNoAccess.pendingOrderStore,
                apiBaseURL: harnessNoAccess.sessionStore.apiClient.serverURL
            )
            await vmNoAccess.loadAll()
            let viewNoAccess = CheckoutView(
                viewModel: vmNoAccess,
                locale: .vi,
                supportContact: .empty,
                fallbackUsername: nil,
                onBackToProduct: {},
                onBackToCatalog: {},
                onSwitchAccount: {},
                onEnterExam: { _ in },
                onContinueAttempt: { _ in },
                onViewPendingOrder: { _ in }
            )
            let (win, ctrl) = RecoveryViewTestHelper.host(viewNoAccess, size: CGSize(width: 390, height: 1800), sizeClass: .compact)
            defer { win.isHidden = true }
            XCTAssertGreaterThan(ctrl.view.bounds.width, 0)
            XCTAssertGreaterThan(vmNoAccess.loadedProduct?.detail.versionNo ?? 0, 0)
        }
    }

    func testLoadingHasNoPayButton() async throws {
        let gate = TestAsyncGate()
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.register(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01") { _ in
                await gate.enterAndWait()
                let data = try ContractFixtureLoader.loadApiFixture("storefront-products-detail-200-exam.json")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )

        let loadTask = Task { await vm.loadAll() }
        await gate.waitUntilEntered()

        XCTAssertEqual(vm.product, .loading)
        XCTAssertFalse(vm.isPayButtonEnabled)

        let view = CheckoutView(
            viewModel: vm,
            locale: .vi,
            supportContact: .empty,
            fallbackUsername: nil,
            onBackToProduct: {},
            onBackToCatalog: {},
            onSwitchAccount: {},
            onEnterExam: { _ in },
            onContinueAttempt: { _ in },
            onViewPendingOrder: { _ in }
        )
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 844), sizeClass: .compact)
        defer { window.isHidden = true }

        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        XCTAssertNil(vm.loadedProduct)
        XCTAssertNil(vm.activeQuote)
        XCTAssertFalse(vm.isPayButtonEnabled)

        await gate.release()
        await loadTask.value
    }

    func testProductErrors() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/public/storefront/products/MISSING-01",
                    statusCode: 404,
                    relativeFixturePath: "api/storefront-products-detail-404-not-found.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "MISSING-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertEqual(vm.product, .failed(kind: .notFound, canRetry: false, retryAfter: nil))
            XCTAssertFalse(vm.isPayButtonEnabled)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/public/storefront/products/GONE-01",
                    statusCode: 410,
                    relativeFixturePath: "api/storefront-products-detail-410-product-discontinued.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "GONE-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertEqual(vm.product, .failed(kind: .gone, canRetry: false, retryAfter: nil))
            XCTAssertNotNil(vm.goneProduct)
            XCTAssertFalse(vm.isPayButtonEnabled)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/price-quote",
                    statusCode: 410,
                    relativeFixturePath: "api/price-quote-410-product-discontinued.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertEqual(vm.product, .failed(kind: .gone, canRetry: false, retryAfter: nil))
            XCTAssertNotNil(vm.goneProduct)
            XCTAssertFalse(vm.isPayButtonEnabled)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 410,
                    relativeFixturePath: "api/orders-create-410-product-discontinued.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            await vm.createOrder()
            XCTAssertEqual(vm.product, .failed(kind: .gone, canRetry: false, retryAfter: nil))
            XCTAssertNotNil(vm.goneProduct)
            XCTAssertFalse(vm.isPayButtonEnabled)
        }

        // 3. 409 PRODUCT_UNAVAILABLE without product (Plan A6 derived from certified orders-create-409-product-unavailable.json)
        do {
            let unavailableWithoutProduct = try ContractFixtureLoader.loadApiFixture("orders-create-409-product-unavailable.json")
            let harness = try PaymentTestFactory.makeHarness { transport in
                transport.registerJSON(
                    method: .get,
                    path: "/api/v1/public/storefront/products/BUNDLE-DEMO-01",
                    statusCode: 409,
                    data: unavailableWithoutProduct
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "BUNDLE-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertEqual(vm.product, .failed(kind: .conflict(code: "PRODUCT_UNAVAILABLE"), canRetry: false, retryAfter: nil))
            XCTAssertFalse(vm.isPayButtonEnabled)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerBundleCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/price-quote",
                    statusCode: 409,
                    relativeFixturePath: "api/orders-create-409-product-unavailable.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "BUNDLE-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertNotNil(vm.loadedProduct)
            XCTAssertNil(vm.activeQuote)
            XCTAssertTrue(vm.isBundleUnavailable)
            XCTAssertTrue(vm.isVoucherDisabled)
            XCTAssertFalse(vm.isPayButtonEnabled)
            let unavailableQuoteView = CheckoutView(
                viewModel: vm,
                locale: .vi,
                supportContact: .empty,
                fallbackUsername: nil,
                onBackToProduct: {},
                onBackToCatalog: {},
                onSwitchAccount: {},
                onEnterExam: { _ in },
                onContinueAttempt: { _ in },
                onViewPendingOrder: { _ in }
            )
            let (uWin, uCtrl) = RecoveryViewTestHelper.host(unavailableQuoteView, size: CGSize(width: 390, height: 1800), sizeClass: .compact)
            defer { uWin.isHidden = true }
            XCTAssertGreaterThan(uCtrl.view.bounds.width, 0)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerBundleCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 409,
                    relativeFixturePath: "api/orders-create-409-product-unavailable.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "BUNDLE-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            await vm.createOrder()
            XCTAssertNotNil(vm.loadedProduct)
            XCTAssertNotNil(vm.activeQuote)
            XCTAssertTrue(vm.isBundleUnavailable)
            XCTAssertTrue(vm.isVoucherDisabled)
            XCTAssertFalse(vm.isPayButtonEnabled)
            let unavailableCreateView = CheckoutView(
                viewModel: vm,
                locale: .vi,
                supportContact: .empty,
                fallbackUsername: nil,
                onBackToProduct: {},
                onBackToCatalog: {},
                onSwitchAccount: {},
                onEnterExam: { _ in },
                onContinueAttempt: { _ in },
                onViewPendingOrder: { _ in }
            )
            let (cWin, cCtrl) = RecoveryViewTestHelper.host(unavailableCreateView, size: CGSize(width: 390, height: 1800), sizeClass: .compact)
            defer { cWin.isHidden = true }
            XCTAssertGreaterThan(cCtrl.view.bounds.width, 0)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                transport.enqueue(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01", handlers: [
                    { _ in throw URLError(.notConnectedToInternet) },
                    { _ in
                        let data = try ContractFixtureLoader.loadApiFixture("storefront-products-detail-200-exam.json")
                        return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                    }
                ])
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/price-quote",
                    statusCode: 200,
                    relativeFixturePath: "api/price-quote-200.json"
                )
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/exam-access/query",
                    statusCode: 200,
                    relativeFixturePath: "api/exam-access-query-200.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertEqual(vm.product, .failed(kind: .offline, canRetry: true, retryAfter: nil))
            await vm.retryLoadProduct()
            XCTAssertNotNil(vm.loadedProduct)
            XCTAssertTrue(vm.isPayButtonEnabled)
        }
    }
}
