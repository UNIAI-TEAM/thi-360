import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class DeleteAccountScreenTests: XCTestCase {
    func testDeletionCheckReadyVariantsUnusedAttempts013PendingGradingAndHttpOfflineRetry() async throws {
        let unused3Data = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200-unused-attempts.json")
        let unused0Data = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200.json")
        let unused1Data = try ProfileFixtureHelper.mutateJSON("me-account-deletion-check-200.json") { dict in
            dict["unusedAttempts"] = 1
        }
        let pendingGradingData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200-pending-grading.json")

        let checkGate = TestAsyncGate()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.register(method: .get, path: "/api/v1/me/account/deletion-check") { _ in
                await checkGate.enterAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: unused3Data)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let service = OpenAPIAccountDeletionService(apiClient: stack.sessionStore.apiClient)
        let vm = DeleteAccountViewModel(deletionService: service, sessionStore: stack.sessionStore)

        let loadTask = Task { @MainActor in await vm.loadCheck() }
        await checkGate.waitUntilEntered()
        XCTAssertEqual(vm.state, .checking(.loading))
        await checkGate.release()
        await loadTask.value

        XCTAssertEqual(vm.state, .ready(unusedAttempts: 3, pendingGrading: false))
        XCTAssertFalse(vm.canRequestDelete)

        let view3 = NavigationStack {
            DeleteAccountView(viewModel: vm, preferences: stack.preferences, onOpenMyExams: {}, onOpenOrders: {})
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (win3, ctrl3) = ProfileFixtureHelper.host(view3, size: CGSize(width: 390, height: 1200), sizeClass: .compact, themeMode: .light)
        let labels3 = ProfileFixtureHelper.collectAccessibilityLabels(in: ctrl3.view)
        let ids3 = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: ctrl3.view)
        XCTAssertTrue(labels3.contains(ProfileStrings.text("account.deletion.explanation", locale: .vi)))
        XCTAssertTrue(labels3.contains("Bạn còn 3 lượt thi chưa dùng. Các lượt này sẽ mất và không được hoàn tiền."))
        XCTAssertFalse(ids3.contains("deleteAccount.pendingGradingWarning"))
        win3.isHidden = true

        vm.toggleAcknowledge()
        XCTAssertFalse(vm.canRequestDelete)
        vm.updatePassword("Password123!")
        XCTAssertTrue(vm.canRequestDelete)

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: unused1Data)
        await vm.loadCheck()
        XCTAssertEqual(vm.state, .ready(unusedAttempts: 1, pendingGrading: false))

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: unused0Data)
        await vm.loadCheck()
        XCTAssertEqual(vm.state, .ready(unusedAttempts: 0, pendingGrading: false))
        let view0 = NavigationStack {
            DeleteAccountView(viewModel: vm, preferences: stack.preferences, onOpenMyExams: {}, onOpenOrders: {})
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (win0, ctrl0) = ProfileFixtureHelper.host(view0, size: CGSize(width: 390, height: 1200), sizeClass: .compact, themeMode: .light)
        XCTAssertFalse(ProfileFixtureHelper.collectAccessibilityIdentifiers(in: ctrl0.view).contains("deleteAccount.unusedAttemptsWarning"))
        win0.isHidden = true

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: pendingGradingData)
        await vm.loadCheck()
        XCTAssertEqual(vm.state, .ready(unusedAttempts: 0, pendingGrading: true))
        XCTAssertTrue(vm.canRequestDelete, "pendingGrading must warn without blocking deletion")
        let viewPending = NavigationStack {
            DeleteAccountView(viewModel: vm, preferences: stack.preferences, onOpenMyExams: {}, onOpenOrders: {})
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (winPending, ctrlPending) = ProfileFixtureHelper.host(viewPending, size: CGSize(width: 390, height: 1200), sizeClass: .compact, themeMode: .light)
        let pendingLabels = ProfileFixtureHelper.collectAccessibilityLabels(in: ctrlPending.view)
        XCTAssertTrue(pendingLabels.contains("Bạn có bài đang chờ chấm. Xóa tài khoản thì bạn sẽ không xem được kết quả."))
        winPending.isHidden = true

        stack.transport.registerEmpty(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 500)
        await vm.loadCheck()
        guard case let .checking(.failed(kind, canRetry, _)) = vm.state, kind == .server, canRetry else {
            return XCTFail("Expected LoadState server failure with retry")
        }

        stack.transport.registerNetworkError(method: .get, path: "/api/v1/me/account/deletion-check", code: .notConnectedToInternet)
        await vm.loadCheck()
        guard case let .checking(.failed(offlineKind, offlineCanRetry, _)) = vm.state, offlineKind == .offline, offlineCanRetry else {
            return XCTFail("Expected LoadState offline failure with retry")
        }

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: unused0Data)
        await vm.loadCheck()
        XCTAssertEqual(vm.state, .ready(unusedAttempts: 0, pendingGrading: false))
    }
}
