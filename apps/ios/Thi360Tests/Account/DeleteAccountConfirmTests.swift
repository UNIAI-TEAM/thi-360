import Foundation
import XCTest
@testable import Thi360

@MainActor
final class DeleteAccountConfirmTests: XCTestCase {
    func testConfirmationDialogCancelMakesZeroCallsAndDoubleConfirmSendsStrictlyOnePost() async throws {
        let checkData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200.json")
        let deleteGate = TestAsyncGate()

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: checkData)
            transport.register(method: .post, path: "/api/v1/me/account/deletion") { _ in
                await deleteGate.enterAndWait()
                return ContractFakeTransport.emptyResponse(statusCode: 204)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let service = OpenAPIAccountDeletionService(apiClient: stack.sessionStore.apiClient)
        let vm = DeleteAccountViewModel(deletionService: service, sessionStore: stack.sessionStore)
        await vm.loadCheck()

        vm.updatePassword("Password123!")
        vm.toggleAcknowledge()
        XCTAssertTrue(vm.canRequestDelete)

        XCTAssertEqual(ProfileStrings.text("account.deletion.confirmTitle", locale: .vi), "Xóa tài khoản vĩnh viễn?")
        XCTAssertEqual(ProfileStrings.text("account.deletion.confirmAction", locale: .vi), "Xóa tài khoản")
        XCTAssertEqual(ProfileStrings.text("account.deletion.confirmCancel", locale: .vi), "Hủy")

        vm.requestConfirmation()
        XCTAssertTrue(vm.isConfirmDialogPresented)
        vm.cancelConfirmation()
        XCTAssertFalse(vm.isConfirmDialogPresented)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/account/deletion"), 0)

        vm.requestConfirmation()
        let first = Task { @MainActor in await vm.confirmDelete() }
        await deleteGate.waitUntilEntered()
        XCTAssertTrue(vm.isSubmittingDeletion)
        XCTAssertFalse(vm.canRequestDelete)

        let second = Task { @MainActor in await vm.confirmDelete() }
        let third = Task { @MainActor in await vm.confirmDelete() }
        await deleteGate.release()
        await first.value
        await second.value
        await third.value

        let requests = stack.transport.requests(method: .post, path: "/api/v1/me/account/deletion")
        XCTAssertEqual(requests.count, 1, "Rapid double-confirm must emit strictly one POST /api/v1/me/account/deletion")
        let body = try XCTUnwrap(requests.first?.bodyData)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
        XCTAssertEqual(json["currentPassword"] as? String, "Password123!")
    }
}
