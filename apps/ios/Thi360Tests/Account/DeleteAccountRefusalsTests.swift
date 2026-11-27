import Foundation
import XCTest
@testable import Thi360

@MainActor
final class DeleteAccountRefusalsTests: XCTestCase {
    func testWrongPasswordThrottledBlockedServerAndOfflineRefusalsPreserveSessionAndTokens() async throws {
        let checkData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200.json")
        let wrongPasswordData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-400-wrong-old-password.json")
        let throttledData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-429-login-throttled.json")
        let blockedAllData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-409-account-deletion-blocked.json")

        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-delete-refusals-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        _ = try await storeProvider.write(Data("draft-keep".utf8), accountId: "acc-refusal", namespace: "drafts", key: "k1")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accountId: "acc-refusal"),
            storeProvider: storeProvider
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: checkData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let service = OpenAPIAccountDeletionService(apiClient: stack.sessionStore.apiClient)
        let vm = DeleteAccountViewModel(deletionService: service, sessionStore: stack.sessionStore)
        await vm.loadCheck()

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/account/deletion", statusCode: 400, data: wrongPasswordData)
        vm.updatePassword("WrongPass!")
        vm.toggleAcknowledge()
        vm.requestConfirmation()
        await vm.confirmDelete()

        XCTAssertEqual(vm.state, .failed(kind: .wrongPassword, unusedAttempts: 0, pendingGrading: false))
        XCTAssertEqual(vm.currentPassword, "", "Wrong password must clear the password field")
        XCTAssertFalse(vm.canRequestDelete, "Delete button must disable after clearing wrong password")
        XCTAssertEqual(ProfileStrings.text("account.deletion.wrongPassword", locale: .vi), "Mật khẩu hiện tại chưa đúng.")
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertNotNil(try stack.tokenStore.read())

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/account/deletion", statusCode: 429, data: throttledData)
        vm.updatePassword("Password123!")
        vm.requestConfirmation()
        await vm.confirmDelete()
        XCTAssertEqual(vm.state, .failed(kind: .throttled, unusedAttempts: 0, pendingGrading: false))
        XCTAssertEqual(ProfileStrings.text("account.deletion.throttled", locale: .vi), "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau.")
        XCTAssertEqual(stack.sessionStore.state, .active)

        stack.transport.registerEmpty(method: .post, path: "/api/v1/me/account/deletion", statusCode: 500)
        vm.requestConfirmation()
        await vm.confirmDelete()
        XCTAssertEqual(vm.state, .failed(kind: .server, unusedAttempts: 0, pendingGrading: false))
        XCTAssertEqual(ProfileStrings.text("account.deletion.failed", locale: .vi), "Chưa xóa được tài khoản. Vui lòng thử lại.")
        XCTAssertTrue(vm.canRequestDelete)
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertNotNil(try stack.tokenStore.read())
        XCTAssertTrue(storeProvider.accountDirectoryExists(for: "acc-refusal"))

        stack.transport.registerNetworkError(method: .post, path: "/api/v1/me/account/deletion", code: .notConnectedToInternet)
        vm.requestConfirmation()
        await vm.confirmDelete()
        XCTAssertEqual(vm.state, .failed(kind: .network, unusedAttempts: 0, pendingGrading: false))
        XCTAssertTrue(vm.canRequestDelete)
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertNotNil(try stack.tokenStore.read())
        XCTAssertTrue(storeProvider.accountDirectoryExists(for: "acc-refusal"))

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/account/deletion", statusCode: 409, data: blockedAllData)
        vm.requestConfirmation()
        await vm.confirmDelete()
        XCTAssertEqual(vm.state, .blocked([.attemptInProgress, .orderBeingChecked, .orderPendingPayment]))
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertNotNil(try stack.tokenStore.read())
    }
}
