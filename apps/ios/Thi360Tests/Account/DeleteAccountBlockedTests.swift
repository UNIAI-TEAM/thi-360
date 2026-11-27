import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class DeleteAccountBlockedTests: XCTestCase {
    func testEveryBlockerVariantShowsExactCopyAndRouteButtonsWithoutPasswordOrSubmit() async throws {
        struct BlockerCase {
            let fixture: String
            let expectedBlockers: [DeletionBlocker]
            let expectedCopies: [String]
            let expectMyExamsButton: Bool
            let expectOrdersButton: Bool
        }

        let cases: [BlockerCase] = [
            BlockerCase(
                fixture: "me-account-deletion-check-200-blocked-attempt.json",
                expectedBlockers: [.attemptInProgress],
                expectedCopies: ["Bạn đang có lượt thi chưa nộp. Hãy nộp bài rồi quay lại."],
                expectMyExamsButton: true,
                expectOrdersButton: false
            ),
            BlockerCase(
                fixture: "me-account-deletion-check-200-blocked-order.json",
                expectedBlockers: [.orderBeingChecked],
                expectedCopies: ["Bạn có đơn hàng đang được kiểm tra. Hãy chờ bài thi được cấp hoặc liên hệ hỗ trợ rồi quay lại."],
                expectMyExamsButton: false,
                expectOrdersButton: false
            ),
            BlockerCase(
                fixture: "me-account-deletion-check-200-blocked-pending-payment.json",
                expectedBlockers: [.orderPendingPayment],
                expectedCopies: ["Bạn có đơn hàng đang chờ thanh toán. Hãy hủy đơn hoặc chờ đơn hết hạn rồi quay lại."],
                expectMyExamsButton: false,
                expectOrdersButton: true
            ),
            BlockerCase(
                fixture: "me-account-deletion-check-200-blocked.json",
                expectedBlockers: [.attemptInProgress, .orderBeingChecked, .orderPendingPayment],
                expectedCopies: [
                    "Bạn đang có lượt thi chưa nộp. Hãy nộp bài rồi quay lại.",
                    "Bạn có đơn hàng đang được kiểm tra. Hãy chờ bài thi được cấp hoặc liên hệ hỗ trợ rồi quay lại.",
                    "Bạn có đơn hàng đang chờ thanh toán. Hãy hủy đơn hoặc chờ đơn hết hạn rồi quay lại."
                ],
                expectMyExamsButton: true,
                expectOrdersButton: true
            )
        ]

        for item in cases {
            let data = try ContractFixtureLoader.loadApiFixture(item.fixture)
            let stack = AccountTestFactory.makeContractStack(
                initialTokens: AccountTestFactory.sampleTokens()
            ) { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: data)
            }
            defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

            let router = AppRouter(initialDestination: .account)
            let service = OpenAPIAccountDeletionService(apiClient: stack.sessionStore.apiClient)
            let vm = DeleteAccountViewModel(deletionService: service, sessionStore: stack.sessionStore)
            await vm.loadCheck()

            XCTAssertEqual(vm.state, .blocked(item.expectedBlockers), item.fixture)
            XCTAssertFalse(vm.canRequestDelete, item.fixture)

            let view = NavigationStack {
                DeleteAccountView(
                    viewModel: vm,
                    preferences: stack.preferences,
                    onOpenMyExams: { router.switchTab(.myExams) },
                    onOpenOrders: { router.openOrders() }
                )
            }
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

            let (window, controller) = ProfileFixtureHelper.host(
                view,
                size: CGSize(width: 390, height: 1200),
                sizeClass: .compact,
                themeMode: .light
            )
            defer { window.isHidden = true }

            let ids = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
            let labels = ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
            for copy in item.expectedCopies {
                XCTAssertTrue(labels.contains(copy), "Missing blocker copy '\(copy)' in \(item.fixture)")
            }
            XCTAssertEqual(ids.contains("deleteAccount.openMyExamsButton"), item.expectMyExamsButton, item.fixture)
            XCTAssertEqual(ids.contains("deleteAccount.openMyOrdersButton"), item.expectOrdersButton, item.fixture)
            XCTAssertFalse(ids.contains("deleteAccount.passwordField"), "Blocked screen must not show password field")
            XCTAssertFalse(ids.contains("deleteAccount.acknowledgeButton"), "Blocked screen must not show acknowledge checkbox")
            XCTAssertFalse(ids.contains("deleteAccount.submitButton"), "Blocked screen must not show delete submit button")
            XCTAssertTrue(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).isEmpty)

            if item.expectMyExamsButton {
                router.switchTab(.myExams)
                XCTAssertEqual(router.selectedDestination, .myExams)
            }
            if item.expectOrdersButton {
                router.switchTab(.account)
                router.openOrders()
                XCTAssertEqual(router.path(for: .account).last, .orders)
            }
        }
    }
}
