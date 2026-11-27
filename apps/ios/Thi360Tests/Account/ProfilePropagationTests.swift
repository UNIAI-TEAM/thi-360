import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ProfilePropagationTests: XCTestCase {
    func testMutationsPropagateToAccountCardAndLockScreenWithZeroExtraProfileGets() async throws {
        let initialData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["emailVerified"] = false
        }
        let updatedInfoData = try ContractFixtureLoader.loadApiFixture("me-profile-update-200.json")
        let updatedAvatarData = try ContractFixtureLoader.loadApiFixture("me-avatar-put-200.json")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: initialData)
            transport.registerJSON(method: .patch, path: "/api/v1/me/profile", statusCode: 200, data: updatedInfoData)
            transport.registerJSON(method: .put, path: "/api/v1/me/avatar", statusCode: 200, data: updatedAvatarData)
            transport.registerEmpty(method: .delete, path: "/api/v1/me/avatar", statusCode: 204)
            transport.registerEmpty(method: .post, path: "/api/v1/me/email/change", statusCode: 202)
            transport.registerEmpty(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 204)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)
        XCTAssertEqual(router.cachedAccountDisplayName, "Nguyễn Văn An")

        let profileVM = accountVM.ensureProfileViewModel()
        profileVM.updateFullName("Nguyễn Minh Anh")
        profileVM.updatePhone("0912345678")
        await profileVM.savePersonalInfo(locale: .vi)

        XCTAssertEqual(accountVM.loadedProfile?.fullName, "Nguyễn Minh Anh")
        XCTAssertEqual(router.cachedAccountDisplayName, "Nguyễn Minh Anh")
        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        let oldLockView = LockView(
            viewModel: lockVM,
            preferences: stack.preferences,
            displayName: "Nguyễn Văn An"
        )
        .environment(\.brandPalette, .defaultLight)
        let (oldLockWindow, _) = RecoveryViewTestHelper.host(
            oldLockView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        let oldLockPNG = try RecoveryViewTestHelper.captureSnapshot(
            window: oldLockWindow,
            size: CGSize(width: 390, height: 844),
            name: "lock-before-profile-rename",
            in: self
        )
        oldLockWindow.isHidden = true

        let lockView = LockView(
            viewModel: lockVM,
            preferences: stack.preferences,
            displayName: router.cachedAccountDisplayName
        )
        .environment(\.brandPalette, .defaultLight)
        let (lockWindow, _) = RecoveryViewTestHelper.host(
            lockView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        let updatedLockPNG = try RecoveryViewTestHelper.captureSnapshot(
            window: lockWindow,
            size: CGSize(width: 390, height: 844),
            name: "lock-after-profile-rename",
            in: self
        )
        lockWindow.isHidden = true
        XCTAssertNotEqual(oldLockPNG, updatedLockPNG, "LockView must visibly render updated displayName and initials")
        XCTAssertEqual(AccountProfile.makeInitials(fullName: router.cachedAccountDisplayName, username: "student.demo"), "MA")
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)

        let png = try ProfileFixtureHelper.makeSamplePNG()
        await profileVM.handleSelectedImageData(png, locale: .vi)
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, "/api/v1/avatars/00000000-0000-7000-8000-000000000001")
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)

        await profileVM.removeAvatar(locale: .vi)
        XCTAssertNil(accountVM.loadedProfile?.avatarUrl)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)

        profileVM.toggleChangeEmailForm()
        profileVM.updateNewEmail("changed@thi360.vn")
        profileVM.updateCurrentPassword("Password123!")
        await profileVM.submitEmailChange(locale: .vi)
        XCTAssertEqual(accountVM.loadedProfile?.pendingEmail, "changed@thi360.vn")

        profileVM.updatePendingCode("123456")
        await profileVM.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(accountVM.loadedProfile?.email, "changed@thi360.vn")
        XCTAssertTrue(accountVM.loadedProfile?.emailVerified == true)
        XCTAssertNil(accountVM.loadedProfile?.pendingEmail)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 1, "No extra GET /api/v1/me/profile should be issued for propagation")
    }

    func testMutationReplaceWinsOverInFlightStaleRefresh() async throws {
        let staleData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let updatedData = try ContractFixtureLoader.loadApiFixture("me-profile-update-200.json")
        let gate = TestAsyncGate()

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.register(method: .get, path: "/api/v1/me/profile") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: staleData)
            }
            transport.registerJSON(method: .patch, path: "/api/v1/me/profile", statusCode: 200, data: updatedData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        let profileVM = accountVM.ensureProfileViewModel()
        accountVM.profileStore.replace(AccountProfile(username: "student.demo", fullName: "Nguyễn Văn An", email: "student.demo@thi360.vn", emailVerified: false))
        profileVM.syncFromStoreIfChanged()

        let refreshTask = Task { @MainActor in
            await accountVM.profileStore.refresh()
        }
        await gate.waitUntilEntered()

        profileVM.updateFullName("Nguyễn Minh Anh")
        profileVM.updatePhone("0912345678")
        await profileVM.savePersonalInfo(locale: .vi)
        XCTAssertEqual(accountVM.loadedProfile?.fullName, "Nguyễn Minh Anh")

        await gate.release()
        _ = await refreshTask.value

        XCTAssertEqual(
            accountVM.loadedProfile?.fullName,
            "Nguyễn Minh Anh",
            "Completed mutation replace must win over stale in-flight refresh"
        )
    }
}
