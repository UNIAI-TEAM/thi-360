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
        let padProfileReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 2
        }
        XCTAssertTrue(padProfileReady)
        let profileFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(profileFields.count, 2, "Default iPad Account detail must be Profile (fullName + phone)")

        accountVM.selectDetail(.orders)
        _ = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
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
        _ = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
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
        let duoProfileReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 2
        }
        XCTAssertTrue(duoProfileReady)
        XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 2)

        accountVM.selectDetail(.orders)
        _ = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
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
        _ = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 3
        }
        XCTAssertEqual(accountVM.selectedDetail, .changePassword)
        XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 3)
    }

    func testAccountProbeSurvivesConditionalGroupTransitionFromColorClearToContentAndRemovesOnUnmount() async {
        let model = ConditionalProbeHostModel()
        let (window, controller) = ProfileFixtureHelper.host(
            ConditionalGroupProbeHostView(model: model),
            size: CGSize(width: 420, height: 320),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let sink = ProfileFixtureHelper.sink(for: controller.view)
        XCTAssertNotNil(sink)

        let initialReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            (sink?.identifiers.contains("probe.group.container") ?? false)
                && Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view)
                && !(sink?.identifiers.contains("probe.group.loadedText") ?? true)
        }
        XCTAssertTrue(initialReady, "AccountProbeModifier must register identifier on initial Color.clear branch")
        XCTAssertTrue(ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view).contains("Conditional Group"))

        model.showsContent = true
        let switchedReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            let ids = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view)
            let frame = ProfileFixtureHelper.findElementFrame(
                identifier: "probe.group.container",
                in: controller.view,
                window: window
            )
            return ids.contains("probe.group.loadedText")
                && ids.contains("probe.group.container")
                && (sink?.identifiers.contains("probe.group.container") ?? false)
                && Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view)
                && (frame.map { !$0.isEmpty } ?? false)
        }
        XCTAssertTrue(switchedReady, "AccountProbeModifier must retain identifier after Group flips from Color.clear to content")
        XCTAssertTrue(sink?.identifiers.contains("probe.group.loadedText") ?? false)
        XCTAssertTrue(sink?.identifiers.contains("probe.group.container") ?? false)
        XCTAssertTrue(Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view))
        let frame = ProfileFixtureHelper.findElementFrame(
            identifier: "probe.group.container",
            in: controller.view,
            window: window
        )
        XCTAssertNotNil(frame)
        if let frame {
            XCTAssertFalse(frame.isEmpty, "Probed container must report a non-empty frame after Group transition")
        }

        model.showsContent = false
        let flippedToClear = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            !(sink?.identifiers.contains("probe.group.loadedText") ?? true)
                && (sink?.identifiers.contains("probe.group.container") ?? false)
                && Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view)
        }
        XCTAssertTrue(flippedToClear)
        XCTAssertTrue(sink?.identifiers.contains("probe.group.container") ?? false)
        XCTAssertTrue(Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view))

        model.showsContent = true
        let flippedBackToContent = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            (sink?.identifiers.contains("probe.group.loadedText") ?? false)
                && (sink?.identifiers.contains("probe.group.container") ?? false)
                && Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view)
        }
        XCTAssertTrue(flippedBackToContent)
        XCTAssertTrue(sink?.identifiers.contains("probe.group.container") ?? false)
        XCTAssertTrue(Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view))

        ProfileFixtureHelper.setRootView(Color.clear, on: controller)
        let unmounted = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            !(sink?.identifiers.contains("probe.group.container") ?? true)
                && !Self.containsUIKitSubview(identifier: "probe.group.container", in: controller.view)
        }
        XCTAssertTrue(unmounted, "AccountProbeModifier must clean up identifier when container unmounts")
    }

    private static func containsUIKitSubview(identifier: String, in root: UIView) -> Bool {
        if !root.isHidden, root.alpha >= 0.01, root.accessibilityIdentifier == identifier {
            return true
        }
        for subview in root.subviews {
            if containsUIKitSubview(identifier: identifier, in: subview) {
                return true
            }
        }
        return false
    }
}

@Observable
@MainActor
private final class ConditionalProbeHostModel {
    var showsContent: Bool = false
}

private struct ConditionalGroupProbeHostView: View {
    @Bindable var model: ConditionalProbeHostModel

    var body: some View {
        Group {
            if model.showsContent {
                VStack {
                    Text("Loaded content")
                        .accessibilityIdentifier("probe.group.loadedText")
                        .accountProbe(id: "probe.group.loadedText", label: "Loaded content")
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                Color.clear
            }
        }
        .accountProbe(id: "probe.group.container", label: "Conditional Group")
    }
}
