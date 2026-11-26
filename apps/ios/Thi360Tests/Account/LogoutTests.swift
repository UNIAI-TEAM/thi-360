import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LogoutTests: XCTestCase {
    func testConfirmedLogoutCallsMobileLogoutClearsTokensBiometricsAndProfileAndPreservesPreferences() async throws {
        let userAProfile = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let userBProfile = try ContractFixtureLoader.loadApiFixture("me-profile-200-no-email.json")
        let userBLogin = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let meData = try ContractFixtureLoader.loadApiFixture("me-200.json")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "userA", refreshToken: "refresh-user-a"),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            locale: .en,
            themeMode: .dark
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: userAProfile)
            transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: meData)
            transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/login", statusCode: 200, data: userBLogin)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        XCTAssertEqual(accountVM.loadedProfile?.username, "student.demo")
        XCTAssertEqual(stack.biometricManager.state, .on)
        XCTAssertTrue(stack.secretStore.hasProtectedSecret())

        accountVM.requestLogout()
        XCTAssertTrue(accountVM.isShowingLogoutConfirmation)
        XCTAssertTrue(stack.sessionStore.state.isAuthenticated)

        accountVM.cancelLogout()
        XCTAssertFalse(accountVM.isShowingLogoutConfirmation)
        XCTAssertTrue(stack.sessionStore.state.isAuthenticated)

        accountVM.requestLogout()
        await accountVM.confirmLogout()

        let logoutCalls = stack.transport.requests(method: .post, path: "/api/v1/auth/mobile/logout")
        XCTAssertEqual(logoutCalls.count, 1)
        let logoutBody = try XCTUnwrap(logoutCalls.first?.bodyData)
        let logoutJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: logoutBody) as? [String: Any])
        XCTAssertEqual(logoutJSON["refreshToken"] as? String, "refresh-user-a")

        XCTAssertNil(try stack.tokenStore.read(), "Logout must delete persisted session tokens")
        XCTAssertFalse(stack.secretStore.hasProtectedSecret(), "Logout must delete biometric protected Keychain data")
        XCTAssertEqual(stack.biometricManager.state, .off, "Logout must disable biometric unlock")
        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .none))
        XCTAssertNil(accountVM.loadedProfile, "Logout must wipe previous user's profile from memory")

        XCTAssertEqual(stack.preferences.locale, .en)
        XCTAssertEqual(stack.preferences.themeMode, .dark)

        stack.transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/login", statusCode: 200, data: userBLogin)
        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: userBProfile)
        try await stack.sessionStore.login(identifier: "student.noemail", password: "Password123!")
        XCTAssertNil(accountVM.loadedProfile, "User B must not see stale profile from User A before fetching")
        await accountVM.loadProfile()
        XCTAssertEqual(accountVM.loadedProfile?.username, "student.noemail")
        XCTAssertEqual(accountVM.loadedProfile?.fullName, "Nguyễn Văn Bình")
        XCTAssertEqual(stack.preferences.locale, .en)
        XCTAssertEqual(stack.preferences.themeMode, .dark)
    }

    func testOfflineLogoutStillDeletesTokensAndBiometricsAndReturnsToLogin() async throws {
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(refreshToken: "offline-refresh"),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            locale: .en,
            themeMode: .dark
        ) { transport in
            transport.registerNetworkError(method: .post, path: "/api/v1/auth/mobile/logout", code: .notConnectedToInternet)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.confirmLogout()

        XCTAssertNil(try stack.tokenStore.read())
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .none))
        XCTAssertEqual(stack.preferences.locale, .en)
        XCTAssertEqual(stack.preferences.themeMode, .dark)
    }

    func testDoubleTapLogoutOnlyInvokesMobileLogoutOnce() async throws {
        let gate = TestAsyncGate()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(refreshToken: "double-tap-refresh"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { transport in
            transport.register(method: .post, path: "/api/v1/auth/mobile/logout") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.emptyResponse(statusCode: 204)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let first = Task { @MainActor in
            await stack.sessionStore.signOut(reason: .none)
        }
        let second = Task { @MainActor in
            await stack.sessionStore.signOut(reason: .none)
        }

        await gate.waitUntilEntered()
        await gate.release()
        await first.value
        await second.value

        let logoutCalls = stack.transport.requests(method: .post, path: "/api/v1/auth/mobile/logout")
        XCTAssertEqual(logoutCalls.count, 1, "Rapid double tap on Logout must invoke mobile/logout strictly once")
        XCTAssertNil(try stack.tokenStore.read())
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())
    }
}
