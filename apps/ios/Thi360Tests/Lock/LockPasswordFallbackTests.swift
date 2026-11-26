import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LockPasswordFallbackTests: XCTestCase {
    func testFailedCancelledAndLockoutKeepAppLocked() async {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "locked.student"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )

        for (outcome, expectedKey) in [
            (BiometricUnlockResult.failed, "lock.error.failed"),
            (BiometricUnlockResult.cancelled, "lock.error.cancelled"),
            (BiometricUnlockResult.lockout, "lock.error.lockout")
        ] {
            stack.secretStore.nextReadOutcomeOverride = outcome
            await lockVM.unlockWithBiometrics(locale: .vi)
            XCTAssertEqual(stack.sessionStore.state, .locked, "App must stay locked after \(outcome)")
            XCTAssertEqual(lockVM.errorMessageKey, expectedKey)
            XCTAssertEqual(stack.biometricManager.state, .on, "Non-invalidation failure must not disable biometrics")
        }
    }

    func testEnterPasswordPrefillsUsernameAndBackPreservesLockedSession() async throws {
        let invalidCredsFixture = try ContractFixtureLoader.loadAuthFixture("login-401-invalid-credentials.json")
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(
                username: "locked.student",
                accessToken: "old-access",
                refreshToken: "old-refresh"
            ),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 401, data: invalidCredsFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )

        lockVM.openPasswordFallback()
        XCTAssertTrue(lockVM.isShowingPasswordFallback)

        let fallbackLoginVM = LoginViewModel(
            sessionStore: stack.sessionStore,
            prefilledIdentifier: lockVM.lockedUsername,
            isLockPasswordFallback: true
        )
        XCTAssertEqual(fallbackLoginVM.identifier, "locked.student")

        // Wrong password shows error, clears password, and preserves locked session intact.
        fallbackLoginVM.password = "WrongPassword1!"
        await fallbackLoginVM.submit()
        XCTAssertEqual(fallbackLoginVM.serverBanner?.message(locale: .vi), "Tên đăng nhập hoặc mật khẩu không đúng.")
        XCTAssertEqual(fallbackLoginVM.password, "")
        XCTAssertEqual(stack.sessionStore.state, .locked)
        XCTAssertEqual(try stack.tokenStore.read()?.refreshToken, "old-refresh")
        XCTAssertTrue(stack.secretStore.hasProtectedSecret())

        // Going back returns to Lock screen with session and biometrics intact.
        lockVM.closePasswordFallback()
        XCTAssertFalse(lockVM.isShowingPasswordFallback)
        XCTAssertEqual(stack.sessionStore.state, .locked)
        XCTAssertEqual(try stack.tokenStore.read()?.accessToken, "old-access")
    }

    func testSuccessfulPasswordFallbackReplacesSessionLogsOutOldIgnoringErrorsAndPromptsPerAC4() async throws {
        let newLoginJSON = Data(
            """
            {
              "accessToken": "new-session-access",
              "refreshToken": "new-session-refresh",
              "accessExpiresIn": 300,
              "refreshExpiresIn": 604800,
              "mustChangePassword": false
            }
            """.utf8
        )

        // Case 1: lastPromptAnswer == .yes (or nil) -> re-prompts after replacing session; old logout failure is ignored.
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(
                username: "locked.student",
                accessToken: "old-session-access",
                refreshToken: "old-session-refresh"
            ),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            initialPromptAnswer: .yes
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: newLoginJSON)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                // Simulate network failure on old-session logout -> must be ignored without hurting new session.
                throw URLError(.networkConnectionLost)
            }
            return MockClientTransport.emptyResponse(statusCode: 404)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .history)
        stack.sessionStore.onSessionReplaced = {
            router.resetForSessionReplaced()
        }

        let fallbackLoginVM = LoginViewModel(
            sessionStore: stack.sessionStore,
            prefilledIdentifier: "locked.student",
            isLockPasswordFallback: true
        )
        fallbackLoginVM.password = "ValidPassword1!"
        await fallbackLoginVM.submit()

        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertEqual(router.selectedDestination, .home, "Re-login from lock must navigate to Home")
        XCTAssertEqual(try stack.tokenStore.read()?.accessToken, "new-session-access")
        XCTAssertEqual(try stack.tokenStore.read()?.refreshToken, "new-session-refresh")

        let logoutCalls = stack.transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/auth/mobile/logout") }
        XCTAssertEqual(logoutCalls.count, 1, "Must call mobile/logout for old refresh token")
        let logoutBody = try XCTUnwrap(logoutCalls.first?.bodyData)
        let logoutJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: logoutBody) as? [String: Any])
        XCTAssertEqual(logoutJSON["refreshToken"] as? String, "old-session-refresh")

        XCTAssertFalse(stack.secretStore.hasProtectedSecret(), "Old biometric secret must be deleted")
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertTrue(stack.biometricManager.isPromptingEnable, "Must prompt to re-enable biometrics per AC-4 when lastAnswer != .later")

        // Case 2: lastPromptAnswer == .later -> replaces session and wipes old biometric secret WITHOUT prompting.
        let laterStack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(
                username: "locked.student",
                refreshToken: "old-refresh-2"
            ),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            initialPromptAnswer: .later
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: newLoginJSON)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 404)
        }
        defer { UserDefaults(suiteName: laterStack.defaultsSuiteName)?.removePersistentDomain(forName: laterStack.defaultsSuiteName) }

        let laterFallbackVM = LoginViewModel(
            sessionStore: laterStack.sessionStore,
            prefilledIdentifier: "locked.student",
            isLockPasswordFallback: true
        )
        laterFallbackVM.password = "ValidPassword1!"
        await laterFallbackVM.submit()

        XCTAssertEqual(laterStack.sessionStore.state, .active)
        XCTAssertFalse(laterStack.secretStore.hasProtectedSecret())
        XCTAssertFalse(laterStack.biometricManager.isPromptingEnable, "Must honor AC-4 Later decision and suppress re-enable prompt")
    }
}
