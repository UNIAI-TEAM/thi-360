import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class LockViewTests: XCTestCase {
    func testLockScreenShowsThreeButtonsPrivacyShieldAndRestoresExactScreenOnUnlock() async throws {
        let refreshFixture = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "minhanh.tran"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: refreshFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .history)
        stack.sessionStore.unlockSession()
        XCTAssertEqual(router.selectedDestination, .history)

        // Background past 5 minutes -> privacy shield activates, then returning locks app.
        _ = stack.biometricManager.handleScenePhaseChange(.inactive, sessionState: stack.sessionStore.state)
        XCTAssertTrue(stack.biometricManager.isPrivacyShieldVisible)

        let shieldedRoot = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )
        let (shieldWindow, shieldController) = RecoveryViewTestHelper.host(
            shieldedRoot,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { shieldWindow.isHidden = true }
        XCTAssertTrue(
            RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: shieldController.view).count <= 1
        )
        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: shieldWindow,
            size: CGSize(width: 390, height: 844),
            name: "privacy-shield-iphone17-light",
            in: self
        )

        _ = stack.biometricManager.handleScenePhaseChange(.background, sessionState: stack.sessionStore.state)
        stack.clock.advance(by: 301)
        XCTAssertTrue(stack.biometricManager.handleScenePhaseChange(.active, sessionState: stack.sessionStore.state))
        stack.sessionStore.lockSession()
        XCTAssertEqual(stack.sessionStore.state, .locked)

        let lockVM = router.lockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        XCTAssertEqual(lockVM.lockedUsername, "minhanh.tran")
        XCTAssertEqual(lockVM.unlockButtonTitle(locale: .vi), "Mở khóa bằng Face ID")
        XCTAssertEqual(AccountStrings.text("lock.enterPasswordButton", locale: .vi), "Nhập mật khẩu")
        XCTAssertEqual(AccountStrings.text("lock.otherAccountButton", locale: .vi), "Đăng nhập bằng tài khoản khác")

        // Render and capture Lock screen + Biometric Enable snapshots.
        let lockView = LockView(viewModel: lockVM, preferences: stack.preferences, displayName: "Trần Minh Anh")
            .environment(\.brandPalette, .defaultLight)
        let (lockWindow, lockController) = RecoveryViewTestHelper.host(
            lockView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { lockWindow.isHidden = true }
        XCTAssertTrue(RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: lockController.view).isEmpty)
        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: lockWindow,
            size: CGSize(width: 390, height: 844),
            name: "lock-faceid-iphone17-light",
            in: self
        )

        let enableView = BiometricEnableView(
            biometricManager: stack.biometricManager,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore
        )
        .environment(\.brandPalette, .defaultLight)
        let (enableWindow, _) = RecoveryViewTestHelper.host(
            enableView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { enableWindow.isHidden = true }
        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: enableWindow,
            size: CGSize(width: 390, height: 844),
            name: "biometric-enable-faceid-iphone17-light",
            in: self
        )

        // Unlock with biometrics -> returns to exact prior destination (.history).
        await lockVM.unlockWithBiometrics(locale: .vi)
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertEqual(router.selectedDestination, .history, "Biometric unlock must restore exact screen viewed before lock")
    }

    func testOtherAccount() async throws {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "locked.user", refreshToken: "locked-refresh-token"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 404)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        XCTAssertEqual(stack.sessionStore.state, .locked)
        XCTAssertEqual(stack.biometricManager.state, .on)
        XCTAssertTrue(stack.secretStore.hasProtectedSecret())

        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await lockVM.signInWithOtherAccount()

        let logoutCalls = stack.transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/auth/mobile/logout") }
        XCTAssertEqual(logoutCalls.count, 1)
        XCTAssertNil(try stack.tokenStore.read())
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .none))

        let emptyLoginVM = LoginViewModel(sessionStore: stack.sessionStore)
        XCTAssertEqual(emptyLoginVM.identifier, "", "Other account login must open blank identifier")
        XCTAssertEqual(emptyLoginVM.password, "")
    }

    func testLabelsFollowDevice() {
        let faceIDStack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { _ in MockClientTransport.emptyResponse(statusCode: 200) }
        defer { UserDefaults(suiteName: faceIDStack.defaultsSuiteName)?.removePersistentDomain(forName: faceIDStack.defaultsSuiteName) }

        let faceLockVM = LockViewModel(sessionStore: faceIDStack.sessionStore, biometricManager: faceIDStack.biometricManager)
        let faceAccountVM = AccountViewModel(sessionStore: faceIDStack.sessionStore, biometricManager: faceIDStack.biometricManager)

        XCTAssertEqual(faceLockVM.unlockButtonTitle(locale: .vi), "Mở khóa bằng Face ID")
        XCTAssertEqual(faceLockVM.unlockButtonTitle(locale: .en), "Unlock with Face ID")
        XCTAssertEqual(faceLockVM.subtitleText(locale: .vi), "Ứng dụng đã khóa. Dùng Face ID để tiếp tục.")
        XCTAssertEqual(faceAccountVM.biometricTitle(locale: .vi), "Mở khóa bằng Face ID")
        XCTAssertEqual(faceAccountVM.biometricTitle(locale: .en), "Unlock with Face ID")

        let touchIDStack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .touchIDAvailable,
            initialEnrolled: true
        ) { _ in MockClientTransport.emptyResponse(statusCode: 200) }
        defer { UserDefaults(suiteName: touchIDStack.defaultsSuiteName)?.removePersistentDomain(forName: touchIDStack.defaultsSuiteName) }

        let touchLockVM = LockViewModel(sessionStore: touchIDStack.sessionStore, biometricManager: touchIDStack.biometricManager)
        let touchAccountVM = AccountViewModel(sessionStore: touchIDStack.sessionStore, biometricManager: touchIDStack.biometricManager)

        XCTAssertEqual(touchLockVM.unlockButtonTitle(locale: .vi), "Mở khóa bằng Touch ID")
        XCTAssertEqual(touchLockVM.unlockButtonTitle(locale: .en), "Unlock with Touch ID")
        XCTAssertEqual(touchLockVM.subtitleText(locale: .vi), "Ứng dụng đã khóa. Dùng Touch ID để tiếp tục.")
        XCTAssertEqual(touchAccountVM.biometricTitle(locale: .vi), "Mở khóa bằng Touch ID")
        XCTAssertEqual(touchAccountVM.biometricTitle(locale: .en), "Unlock with Touch ID")
    }
}
