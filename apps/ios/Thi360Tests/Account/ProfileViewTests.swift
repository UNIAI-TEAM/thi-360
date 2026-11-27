import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ProfileViewTests: XCTestCase {
    func testInitialLoadSkeletonRevisitRefreshStaleErrorOfflineAndLogoutWipe() async throws {
        let initialData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let updatedElsewhere = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["fullName"] = "Nguyễn Đổi Ở Web"
            dict["phone"] = "0909123456"
        }

        let gate = TestAsyncGate()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.register(method: .get, path: "/api/v1/me/profile") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: initialData)
            }
            transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.ensureCleanupRegistered()
        let profileVM = accountVM.ensureProfileViewModel()

        let loadTask = Task { @MainActor in
            await profileVM.onAppearLoad()
        }
        await gate.waitUntilEntered()
        guard case .loading = profileVM.profileState else {
            return XCTFail("First load in session must show loading skeleton")
        }

        let loadingView = ProfileView(viewModel: profileVM, preferences: stack.preferences)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
        let (loadingWindow, loadingController) = ProfileFixtureHelper.host(
            loadingView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        let loadingLabels = ProfileFixtureHelper.collectAccessibilityLabels(in: loadingController.view)
        XCTAssertTrue(loadingLabels.contains(AppStrings.common("common.loading", locale: .vi)))
        loadingWindow.isHidden = true

        await gate.release()
        await loadTask.value
        XCTAssertEqual(profileVM.fullName, "Nguyễn Văn An")
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: updatedElsewhere)
        await profileVM.onAppearLoad()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 2)
        XCTAssertEqual(profileVM.fullName, "Nguyễn Đổi Ở Web", "Revisit refresh must replace fields with values changed elsewhere")
        XCTAssertEqual(profileVM.phone, "0909123456")

        stack.transport.registerEmpty(method: .get, path: "/api/v1/me/profile", statusCode: 500)
        await profileVM.onAppearLoad()
        guard case let .loaded(preserved, stale) = profileVM.profileState else {
            return XCTFail("Refresh failure must keep loaded data visible")
        }
        XCTAssertTrue(stale)
        XCTAssertEqual(preserved.fullName, "Nguyễn Đổi Ở Web")
        XCTAssertNotNil(profileVM.refreshError)

        await accountVM.confirmLogout()
        guard case .loading = accountVM.profileStore.state else {
            return XCTFail("Logout must wipe profile store")
        }
        XCTAssertNil(accountVM.loadedProfile)
    }

    func testInitialHttpErrorAndOfflineErrorCopyAndRetry() async throws {
        let okData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerEmpty(method: .get, path: "/api/v1/me/profile", statusCode: 500)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        let profileVM = accountVM.ensureProfileViewModel()
        await profileVM.onAppearLoad()

        guard case let .failed(kind, _, _) = profileVM.profileState, kind == .server else {
            return XCTFail("Expected server error on initial 500")
        }
        XCTAssertEqual(ProfileStrings.text("account.profile.errorTitle", locale: .vi), "Không tải được hồ sơ")
        XCTAssertEqual(ProfileStrings.text("account.profile.errorDescription", locale: .vi), "Đã có lỗi khi tải hồ sơ. Hãy thử lại.")

        stack.transport.registerNetworkError(method: .get, path: "/api/v1/me/profile", code: .notConnectedToInternet)
        await profileVM.retryLoad()
        guard case let .failed(offlineKind, _, _) = profileVM.profileState, offlineKind == .offline else {
            return XCTFail("Expected offline error")
        }
        XCTAssertFalse(AppStrings.common("common.offline.title", locale: .vi).isEmpty)
        XCTAssertFalse(AppStrings.common("common.offline.description", locale: .vi).isEmpty)

        let retryGate = TestAsyncGate()
        stack.transport.register(method: .get, path: "/api/v1/me/profile") { _ in
            await retryGate.enterAndWait()
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: okData)
        }
        let retryTask = Task { @MainActor in
            await profileVM.retryLoad()
        }
        await retryGate.waitUntilEntered()
        guard case .loading = profileVM.profileState else {
            return XCTFail("Retrying from initial error must return to loading skeleton")
        }
        await retryGate.release()
        await retryTask.value
        XCTAssertEqual(profileVM.profile?.username, "student.demo")
    }

    func testContentBlocks() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        let profileVM = accountVM.ensureProfileViewModel()

        let view = NavigationStack {
            ProfileView(viewModel: profileVM, preferences: stack.preferences)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = ProfileFixtureHelper.host(
            view,
            size: CGSize(width: 390, height: 1400),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let ids = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
        XCTAssertTrue(ids.contains("profile.intro"))
        XCTAssertTrue(ids.contains("profile.section.avatar"))
        XCTAssertTrue(ids.contains("profile.section.info"))
        XCTAssertTrue(ids.contains("profile.section.email"))
        XCTAssertFalse(ids.contains("account.changePasswordButton"))

        let labels = ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
        XCTAssertTrue(labels.contains(ProfileStrings.text("account.profile.intro", locale: .vi)))
        XCTAssertTrue(labels.contains(ProfileStrings.text("account.profile.avatar.title", locale: .vi)))
        XCTAssertTrue(labels.contains(ProfileStrings.text("account.profile.info.title", locale: .vi)))
        XCTAssertTrue(labels.contains(ProfileStrings.text("account.profile.email.title", locale: .vi)))
        XCTAssertFalse(labels.contains(AccountStrings.text("account.changePassword", locale: .vi)), "Profile screen must NOT contain Password block")
    }

    func testEmailBlockVariants() async throws {
        let verifiedData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let unverifiedData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["emailVerified"] = false
        }
        let noEmailData = try ContractFixtureLoader.loadApiFixture("me-profile-200-no-email.json")

        let cases: [(name: String, data: Data, expectBanner: Bool, expectedBadgeOrNone: String)] = [
            ("verified", verifiedData, false, "Đã xác minh"),
            ("unverified", unverifiedData, true, "Chưa xác minh"),
            ("noEmail", noEmailData, false, "Tài khoản chưa có email.")
        ]

        for item in cases {
            let stack = AccountTestFactory.makeContractStack(
                initialTokens: AccountTestFactory.sampleTokens()
            ) { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: item.data)
            }
            defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

            let accountVM = AccountViewModel(
                sessionStore: stack.sessionStore,
                biometricManager: stack.biometricManager
            )
            await accountVM.loadProfile()
            let profileVM = accountVM.ensureProfileViewModel()
            XCTAssertEqual(profileVM.profile?.needsEmailVerificationBanner, item.expectBanner, item.name)

            let view = NavigationStack {
                ProfileView(viewModel: profileVM, preferences: stack.preferences)
            }
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

            let (window, controller) = ProfileFixtureHelper.host(
                view,
                size: CGSize(width: 390, height: 1500),
                sizeClass: .compact,
                themeMode: .light
            )
            defer { window.isHidden = true }

            let ids = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
            XCTAssertEqual(ids.contains("profile.unverifiedBanner"), item.expectBanner, "Banner mismatch for \(item.name)")
            XCTAssertTrue(ids.contains("profile.email.changeButton"), "Change email button must exist for \(item.name)")

            let labels = ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
            XCTAssertTrue(
                labels.contains { $0.contains(item.expectedBadgeOrNone) },
                "Expected '\(item.expectedBadgeOrNone)' in \(item.name)"
            )
        }
    }
}
