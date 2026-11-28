import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AccountDeletionRowTests: XCTestCase {
    func testDeleteAccountRowIsLastBelowLogoutOpensRouteInPhoneAndIPadAndStaysBehindLockScreen() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let checkData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
            transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: checkData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }
        stack.sessionStore.unlockSession()

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()

        let phoneView = AccountView(
            viewModel: accountVM,
            router: router,
            preferences: stack.preferences,
            effectiveSizeClass: .compact,
            serverDefaultThemeMode: .light,
            onOpenChangePassword: {}
        )
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (phoneWindow, phoneController) = ProfileFixtureHelper.host(
            phoneView,
            size: CGSize(width: 390, height: 1500),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { phoneWindow.isHidden = true }

        let rowsReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            ProfileFixtureHelper.findElementFrame(identifier: "account.logoutButton", in: phoneController.view, window: phoneWindow) != nil
                && ProfileFixtureHelper.findElementFrame(identifier: "account.deleteAccountRow", in: phoneController.view, window: phoneWindow) != nil
        }
        XCTAssertTrue(rowsReady)
        let logoutFrame = ProfileFixtureHelper.findElementFrame(identifier: "account.logoutButton", in: phoneController.view, window: phoneWindow)
        let deleteFrame = ProfileFixtureHelper.findElementFrame(identifier: "account.deleteAccountRow", in: phoneController.view, window: phoneWindow)
        XCTAssertNotNil(logoutFrame, "Logout button must exist")
        XCTAssertNotNil(deleteFrame, "Delete account row must exist")
        if let logoutFrame, let deleteFrame {
            XCTAssertGreaterThan(deleteFrame.minY, logoutFrame.maxY + 8, "Delete account must sit in its own separate group below Logout")
        }

        router.openDeleteAccount()
        XCTAssertEqual(router.path(for: .account), [.deleteAccount])

        stack.sessionStore.lockSession()
        XCTAssertEqual(stack.sessionStore.state, .locked)
        let lockedRoot = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )
        let (lockWindow, lockController) = ProfileFixtureHelper.host(
            lockedRoot,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { lockWindow.isHidden = true }
        let lockedIDs = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: lockController.view)
        XCTAssertTrue(RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: lockController.view).isEmpty, "Lock screen must replace authenticated tab shell")
        XCTAssertFalse(lockedIDs.contains("shell.account.view"), "Account list must remain hidden behind lock screen")
        XCTAssertFalse(lockedIDs.contains("deleteAccount.screen"), "Delete account screen must remain hidden behind lock screen")

        stack.sessionStore.unlockSession()
        XCTAssertEqual(router.path(for: .account), [.deleteAccount], "Unlocking must restore Delete Account route")

        let padRouter = AppRouter(initialDestination: .account)
        let padAccountVM = padRouter.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await padAccountVM.loadProfile()
        padAccountVM.selectDetail(.deleteAccount)

        let padRoot = AppRootView(
            router: padRouter,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .pad
        )
        let (padWindow, padController) = ProfileFixtureHelper.host(
            padRoot,
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { padWindow.isHidden = true }
        let padDeleteReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            ProfileFixtureHelper.collectAccessibilityIdentifiers(in: padController.view).contains("deleteAccount.screen")
        }
        XCTAssertTrue(padDeleteReady)
        let padIDs = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: padController.view)
        XCTAssertEqual(padAccountVM.selectedDetail, .deleteAccount)
        XCTAssertTrue(padIDs.contains("deleteAccount.screen"))
    }
}
