import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AccountLayoutTests: XCTestCase {
    func testCompactLayoutRendersSingleColumnWithTabBar() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: profileData)
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

        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertEqual(ShellLayoutResolver.resolve(idiom: .phone, horizontalSizeClass: .compact), .compactTabBar)
        XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: controller.view).count, 1)
        XCTAssertTrue(
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).isEmpty,
            "Compact Account must show single-column list without inline password form"
        )
    }

    func testIPadRegularRendersAccountBesideSidebar() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: profileData)
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

        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertEqual(ShellLayoutResolver.resolve(idiom: .pad, horizontalSizeClass: .regular), .iPadSidebar)
        XCTAssertFalse(findControllers(ofType: UISplitViewController.self, in: controller).isEmpty)
        XCTAssertTrue(RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: controller.view).isEmpty)
        XCTAssertEqual(accountVM.selectedDetail, .profile)

        let padReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 2
                && RecoveryViewTestHelper.findViews(ofType: UISwitch.self, in: controller.view).count == 1
        }
        XCTAssertTrue(padReady)

        let textFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(
            textFields.count,
            2,
            "iPad regular Account must render two-pane list and default Profile detail pane (fullName + phone)"
        )
        let switches = RecoveryViewTestHelper.findViews(ofType: UISwitch.self, in: controller.view)
        XCTAssertEqual(switches.count, 1, "iPad Account list column must display biometric UISwitch")
        if let firstField = textFields.first, let bioSwitch = switches.first {
            let fieldMidX = firstField.convert(firstField.bounds, to: controller.view).midX
            let switchMidX = bioSwitch.convert(bioSwitch.bounds, to: controller.view).midX
            XCTAssertGreaterThan(fieldMidX, switchMidX, "Profile detail pane must sit to the right of Account list")
        }
    }

    func testIPhoneRegularUnfoldedRendersTwoPaneWithLeftAccountListAndRightDetailForm() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: profileData)
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

        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 951, height: 669),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertEqual(ShellLayoutResolver.resolve(idiom: .phone, horizontalSizeClass: .regular), .phoneTwoPaneBook)
        XCTAssertTrue(RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: controller.view).isEmpty)
        XCTAssertEqual(accountVM.selectedDetail, .profile)

        let duoReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 2
                && RecoveryViewTestHelper.findViews(ofType: UISwitch.self, in: controller.view).count == 1
        }
        XCTAssertTrue(duoReady)

        let textFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(
            textFields.count,
            2,
            "iPhone Duo unfolded Account right pane must default to Profile detail form with fullName and phone fields"
        )
        let switches = RecoveryViewTestHelper.findViews(ofType: UISwitch.self, in: controller.view)
        XCTAssertEqual(switches.count, 1, "Left pane must display biometric UISwitch")
        if let firstField = textFields.first, let bioSwitch = switches.first {
            let fieldMidX = firstField.convert(firstField.bounds, to: controller.view).midX
            let switchMidX = bioSwitch.convert(bioSwitch.bounds, to: controller.view).midX
            XCTAssertLessThan(switchMidX, controller.view.bounds.midX, "Account settings list must live in left half")
            XCTAssertGreaterThan(fieldMidX, controller.view.bounds.midX, "Selected Account detail must live in right half")
        }

        accountVM.selectDetail(.changePassword)
        let passwordReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 3
        }
        XCTAssertTrue(passwordReady)

        let passwordFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(
            passwordFields.count,
            3,
            "Selecting Change Password in iPhone Duo two-pane must switch right pane to 3 password fields"
        )
    }

    private func findControllers<T: UIViewController>(
        ofType type: T.Type,
        in root: UIViewController
    ) -> [T] {
        var result: [T] = []
        if let typed = root as? T {
            result.append(typed)
        }
        for child in root.children {
            result.append(contentsOf: findControllers(ofType: type, in: child))
        }
        return result
    }
}
