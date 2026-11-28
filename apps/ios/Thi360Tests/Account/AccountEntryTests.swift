import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AccountEntryTests: XCTestCase {
    func testSingleColumnTappingProfileAndOrdersPushesRoutesAndRestoresScrollPosition() async throws {
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
            size: CGSize(width: 390, height: 520),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let scrollReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            guard let scrollView = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).first else {
                return false
            }
            return scrollView.contentSize.height > 650
        }
        XCTAssertTrue(scrollReady, "Account scroll view must have scrollable content > 650pt")
        let accountScroll = try XCTUnwrap(RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).first)
        accountScroll.setContentOffset(CGPoint(x: 0, y: 120), animated: false)
        let offsetApplied = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            accountScroll.contentOffset.y >= 100
        }
        XCTAssertTrue(offsetApplied, "Account scroll offset must advance to >= 100pt before pushing Profile")

        router.openProfile()
        let profilePushed = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            router.path(for: .account) == [.profile]
                && ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: controller)
                    .contains { $0.viewControllers.count == 2 && $0.transitionCoordinator == nil }
                && !RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).isEmpty
        }
        XCTAssertTrue(profilePushed, "Pushing Profile must finish navigation transition and mount ProfileView")
        XCTAssertEqual(router.path(for: .account), [.profile])

        router.back()
        let scrollRestored = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            let navs = ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: controller)
            guard router.path(for: .account).isEmpty,
                  !navs.isEmpty,
                  navs.allSatisfy({ $0.viewControllers.count <= 1 && $0.transitionCoordinator == nil }),
                  RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).isEmpty,
                  let restoredScroll = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).first
            else {
                return false
            }
            return restoredScroll.contentOffset.y >= 100
        }
        XCTAssertTrue(scrollRestored, "Returning from Profile must finish pop transition and preserve Account scroll offset")
        XCTAssertTrue(router.path(for: .account).isEmpty)
        let restoredScroll = try XCTUnwrap(RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).first)
        XCTAssertGreaterThanOrEqual(restoredScroll.contentOffset.y, 100, "Returning from Profile must preserve Account scroll offset")

        router.openOrders()
        let ordersPushed = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            router.path(for: .account) == [.orders]
                && ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: controller)
                    .contains { $0.viewControllers.count == 2 && $0.transitionCoordinator == nil }
        }
        XCTAssertTrue(ordersPushed, "Pushing Orders must finish navigation transition")
        XCTAssertEqual(router.path(for: .account), [.orders])

        router.back()
        let ordersPopped = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            let navs = ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: controller)
            return router.path(for: .account).isEmpty
                && !navs.isEmpty
                && navs.allSatisfy { $0.viewControllers.count <= 1 && $0.transitionCoordinator == nil }
        }
        XCTAssertTrue(ordersPopped, "Returning from Orders must finish pop transition")
        XCTAssertTrue(router.path(for: .account).isEmpty)
    }

    func testQuickLinksRemainUsableDuringLoadingErrorAndOfflineAndProfileSelfLoads() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let gate = TestAsyncGate()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.register(method: .get, path: "/api/v1/me/profile") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.emptyResponse(statusCode: 500)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )

        let loadingTask = Task { @MainActor in
            await accountVM.loadProfile()
        }
        await gate.waitUntilEntered()
        guard case .loading = accountVM.profileState else {
            return XCTFail("Expected loading state")
        }

        router.openProfile()
        XCTAssertEqual(router.path(for: .account), [.profile])
        router.back()
        router.openOrders()
        XCTAssertEqual(router.path(for: .account), [.orders])
        router.back()

        await gate.release()
        await loadingTask.value
        guard case let .failed(kind, _, _) = accountVM.profileState, kind == .server else {
            return XCTFail("Expected server error state")
        }

        router.openProfile()
        XCTAssertEqual(router.path(for: .account), [.profile])
        router.back()
        router.openOrders()
        XCTAssertEqual(router.path(for: .account), [.orders])
        router.back()

        stack.transport.registerNetworkError(method: .get, path: "/api/v1/me/profile", code: .notConnectedToInternet)
        await accountVM.loadProfile()
        guard case let .failed(offlineKind, _, _) = accountVM.profileState, offlineKind == .offline else {
            return XCTFail("Expected offline state")
        }

        router.openOrders()
        XCTAssertEqual(router.path(for: .account), [.orders])
        router.back()

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        router.openProfile()
        let profileVM = accountVM.ensureProfileViewModel()
        await profileVM.onAppearLoad()
        XCTAssertEqual(profileVM.profile?.fullName, "Nguyễn Văn An")
    }
}
