import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AccountTwoPaneTests: XCTestCase {
    func testIPadRegularDefaultsToProfileDetailAndSwitchesToOrdersAndChangePassword() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()

        let rootView = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .pad
        )
        let (window, controller) = ProfileFixtureHelper.host(
            rootView,
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertEqual(accountVM.selectedDetail, .profile)
        let profileFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(profileFields.count, 2, "Default iPad Account detail must be Profile (fullName + phone)")

        accountVM.selectDetail(.orders)
        _ = await ProfileFixtureHelper.waitUntil(timeout: 2.0) {
            let ids = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
            return !ids.contains("profile.info.fullNameField")
                && (ids.contains("account.detail.orders") || ids.contains("orders.screen") || ids.contains("payment.orders.screen"))
                && RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count != 2
        }
        XCTAssertEqual(accountVM.selectedDetail, .orders)
        let ordersIDs = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
        XCTAssertFalse(ordersIDs.contains("profile.info.fullNameField"), "Orders route rendered in detail pane must replace Profile form")
        XCTAssertTrue(
            ordersIDs.contains("account.detail.orders") || ordersIDs.contains("route.placeholder.logicalName") || ordersIDs.contains("orders.screen") || ordersIDs.contains("payment.orders.screen"),
            "Orders detail pane must render the orders route"
        )
        XCTAssertNotEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 2)

        accountVM.selectDetail(.changePassword)
        _ = await ProfileFixtureHelper.waitUntil(timeout: 2.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 3
        }
        XCTAssertEqual(accountVM.selectedDetail, .changePassword)
        XCTAssertEqual(
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count,
            3,
            "Change Password detail pane must render 3 password fields"
        )
    }

    func testIPhoneDuoUnfoldedDefaultsToProfileAndSwitchesToOrdersAndChangePassword() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()

        let rootView = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )
        let (window, controller) = ProfileFixtureHelper.host(
            rootView,
            size: CGSize(width: 951, height: 669),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertEqual(accountVM.selectedDetail, .profile)
        XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 2)

        accountVM.selectDetail(.orders)
        _ = await ProfileFixtureHelper.waitUntil(timeout: 2.0) {
            let ids = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
            return !ids.contains("profile.info.fullNameField")
                && (ids.contains("account.detail.orders") || ids.contains("orders.screen") || ids.contains("payment.orders.screen"))
                && RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count != 2
        }
        XCTAssertEqual(accountVM.selectedDetail, .orders)
        let duoOrdersIDs = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
        XCTAssertFalse(duoOrdersIDs.contains("profile.info.fullNameField"))
        XCTAssertTrue(
            duoOrdersIDs.contains("account.detail.orders") || duoOrdersIDs.contains("route.placeholder.logicalName") || duoOrdersIDs.contains("orders.screen") || duoOrdersIDs.contains("payment.orders.screen"),
            "iPhone Duo right pane must render the orders route"
        )
        XCTAssertNotEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 2)

        accountVM.selectDetail(.changePassword)
        _ = await ProfileFixtureHelper.waitUntil(timeout: 2.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 3
        }
        XCTAssertEqual(accountVM.selectedDetail, .changePassword)
        XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 3)
    }
}
