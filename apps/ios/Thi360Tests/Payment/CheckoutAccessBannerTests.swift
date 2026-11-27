import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class CheckoutAccessBannerTests: XCTestCase {
    func testCheckingStateLocksPayAndOpensPaymentStatus() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, code: "EXAM-DEMO-08")
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-08",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.loadAll()

        XCTAssertTrue(vm.isBlockedByChecking)
        XCTAssertFalse(vm.isPayButtonEnabled)
        XCTAssertTrue(vm.isVoucherDisabled)
        XCTAssertEqual(vm.accessRow?.pendingOrderCode, "DH261126000001")
    }

    func testExistingCanStartAndInProgressAllowBuyingMoreAndRouteProperly() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, code: "EXAM-DEMO-01")
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

            XCTAssertEqual(vm.accessRow?.state, .CAN_START)
            XCTAssertEqual(vm.accessRow?.attemptsLeft, 3)
            XCTAssertEqual(vm.accessRow?.entitlementId, "00000000-0000-7000-8000-000000000002")
            XCTAssertTrue(vm.isPayButtonEnabled)

            let router = AppRouter()
            router.openPrepare(entitlementId: try XCTUnwrap(vm.accessRow?.entitlementId))
            XCTAssertEqual(
                router.path(for: .home),
                [.prepare(entitlementId: "00000000-0000-7000-8000-000000000002")]
            )
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, code: "EXAM-DEMO-03")
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-03",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()

            XCTAssertEqual(vm.accessRow?.state, .IN_PROGRESS)
            XCTAssertEqual(vm.accessRow?.activeAttemptId, "00000000-0000-7000-8000-000000000005")
            XCTAssertTrue(vm.isPayButtonEnabled)

            let router = AppRouter()
            router.openAttempt(attemptId: try XCTUnwrap(vm.accessRow?.activeAttemptId))
            XCTAssertEqual(router.presentedAttempt?.attemptId, "00000000-0000-7000-8000-000000000005")
        }
    }

    func testBundleExistingEntitlementAndPendingExamOrderBanners() async throws {
        let bundleAccessData = try PaymentFixtureHelper.mutatingFixture("exam-access-query-200.json") { obj in
            obj["items"] = [
                [
                    "code": "BUNDLE-DEMO-01",
                    "state": "NONE",
                    "attemptsLeft": 0,
                    "entitlementId": NSNull(),
                    "activeAttemptId": NSNull(),
                    "hasPendingFulfillment": false,
                    "pendingOrderCode": NSNull(),
                    "items": [
                        [
                            "examId": "00000000-0000-7000-8000-000000000001",
                            "code": "EXAM-DEMO-02",
                            "name": "Phần Tư duy định lượng",
                            "state": "CAN_START",
                            "attemptsLeft": 2,
                            "entitlementId": "ent-1",
                            "activeAttemptId": NSNull(),
                            "hasPendingFulfillment": false,
                            "pendingOrderCode": NSNull()
                        ],
                        [
                            "examId": "00000000-0000-7000-8000-000000000002",
                            "code": "EXAM-DEMO-03",
                            "name": "Phần Tư duy định tính",
                            "state": "CHECKING",
                            "attemptsLeft": 0,
                            "entitlementId": NSNull(),
                            "activeAttemptId": NSNull(),
                            "hasPendingFulfillment": true,
                            "pendingOrderCode": "DH261126000009"
                        ]
                    ]
                ]
            ]
        }

        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerBundleCheckoutDefaults(on: transport)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", statusCode: 200, data: bundleAccessData)
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

        XCTAssertTrue(vm.isBlockedByChecking)
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
        let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1800), sizeClass: .compact)
        defer { window.isHidden = true }

        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        let pendingItems = (vm.accessRow?.items ?? []).filter { $0.hasPendingFulfillment || $0.pendingOrderCode != nil }
        let existingItems = (vm.accessRow?.items ?? []).filter { $0.attemptsLeft > 0 }
        XCTAssertEqual(pendingItems.map(\.pendingOrderCode), ["DH261126000009"])
        XCTAssertEqual(existingItems.map(\.code), ["EXAM-DEMO-02"])
        XCTAssertEqual(
            PaymentStrings.text(
                "bundle.checkout.pendingExamOrder",
                locale: .vi,
                replacements: ["orderCode": "DH261126000009", "name": "Phần Tư duy định tính"]
            ),
            "Đơn DH261126000009 cho bài Phần Tư duy định tính đang được kiểm tra. Bạn không cần thanh toán lại."
        )
        XCTAssertEqual(
            PaymentStrings.plural(
                "payment.bundle.detail.existingEntitlement",
                count: 2,
                locale: .vi,
                replacements: ["name": "Phần Tư duy định lượng"]
            ),
            "Bạn đã có 2 lượt bài Phần Tư duy định lượng; mua bộ sẽ cộng thêm lượt"
        )
    }

    func testAccessErrorLocksPayUntilRetrySucceeds() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            transport.enqueue(method: .post, path: "/api/v1/me/exam-access/query", handlers: [
                { _ in ContractFakeTransport.emptyResponse(statusCode: 403) },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("exam-access-query-200.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                }
            ])
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

        XCTAssertTrue(vm.isAccessUnavailable)
        XCTAssertFalse(vm.isPayButtonEnabled)

        await vm.retryLoadAccess()
        XCTAssertFalse(vm.isAccessUnavailable)
        XCTAssertTrue(vm.isPayButtonEnabled)
    }
}
