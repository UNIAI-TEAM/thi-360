import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class AvatarRemoveTests: XCTestCase {
    func testRemoveAvatarButtonVisibilityImmediateDeleteDoubleTapDedupAndFailureRetention() async throws {
        let withAvatar = try ContractFixtureLoader.loadApiFixture("me-avatar-put-200.json")
        let removeGate = TestAsyncGate()

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: withAvatar)
            transport.register(method: .delete, path: "/api/v1/me/avatar") { _ in
                await removeGate.enterAndWait()
                return ContractFakeTransport.emptyResponse(statusCode: 204)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()
        XCTAssertTrue(vm.profile?.hasAvatar == true)

        let beforeView = NavigationStack {
            ProfileView(viewModel: vm, preferences: stack.preferences)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (beforeWindow, beforeController) = ProfileFixtureHelper.host(
            beforeView,
            size: CGSize(width: 390, height: 1200),
            sizeClass: .compact,
            themeMode: .light
        )
        XCTAssertTrue(ProfileFixtureHelper.collectAccessibilityIdentifiers(in: beforeController.view).contains("profile.avatar.removeButton"))
        beforeWindow.isHidden = true

        stack.transport.registerEmpty(method: .delete, path: "/api/v1/me/avatar", statusCode: 500)
        await vm.removeAvatar(locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.generic")
        XCTAssertTrue(accountVM.loadedProfile?.hasAvatar == true)

        stack.transport.registerNetworkError(method: .delete, path: "/api/v1/me/avatar", code: .notConnectedToInternet)
        await vm.removeAvatar(locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.generic")
        XCTAssertTrue(accountVM.loadedProfile?.hasAvatar == true)

        stack.transport.resetRecordedRequests()
        stack.transport.register(method: .delete, path: "/api/v1/me/avatar") { _ in
            await removeGate.enterAndWait()
            return ContractFakeTransport.emptyResponse(statusCode: 204)
        }

        let first = Task { @MainActor in await vm.removeAvatar(locale: .vi) }
        await removeGate.waitUntilEntered()
        XCTAssertTrue(vm.isRemovingAvatar)
        XCTAssertTrue(vm.isAvatarBusy)
        let second = Task { @MainActor in await vm.removeAvatar(locale: .vi) }
        await removeGate.release()
        await first.value
        await second.value

        XCTAssertEqual(stack.transport.callCount(method: .delete, path: "/api/v1/me/avatar"), 1)
        XCTAssertNil(accountVM.loadedProfile?.avatarUrl)
        XCTAssertNil(accountVM.loadedProfile?.avatarAssetId)
        XCTAssertEqual(vm.activeToast?.message, "Đã xóa ảnh đại diện.")

        let afterView = NavigationStack {
            ProfileView(viewModel: vm, preferences: stack.preferences)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (afterWindow, afterController) = ProfileFixtureHelper.host(
            afterView,
            size: CGSize(width: 390, height: 1200),
            sizeClass: .compact,
            themeMode: .light
        )
        XCTAssertFalse(ProfileFixtureHelper.collectAccessibilityIdentifiers(in: afterController.view).contains("profile.avatar.removeButton"))
        afterWindow.isHidden = true
    }
}
