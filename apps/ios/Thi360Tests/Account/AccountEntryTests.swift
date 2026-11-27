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
        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let scrollViewsBefore = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        if let accountScroll = scrollViewsBefore.first {
            accountScroll.setContentOffset(CGPoint(x: 0, y: 120), animated: false)
            ProfileFixtureHelper.pumpMainRunLoop()
        }

        router.openProfile()
        ProfileFixtureHelper.pumpMainRunLoop(seconds: 0.12)
        XCTAssertEqual(router.path(for: .account), [.profile])

        router.back()
        ProfileFixtureHelper.pumpMainRunLoop(seconds: 0.12)
        XCTAssertTrue(router.path(for: .account).isEmpty)
        if let restoredScroll = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).first {
            XCTAssertGreaterThanOrEqual(restoredScroll.contentOffset.y, 100, "Returning from Profile must preserve Account scroll offset")
        }

        router.openOrders()
        ProfileFixtureHelper.pumpMainRunLoop(seconds: 0.12)
        XCTAssertEqual(router.path(for: .account), [.orders])

        router.back()
        ProfileFixtureHelper.pumpMainRunLoop(seconds: 0.12)
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
