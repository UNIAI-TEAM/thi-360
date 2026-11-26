import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LockExpiredSessionTests: XCTestCase {
    func testOnlineUnlockWithDeadSessionSignsOutWithExpiredMessageAndClearsBiometrics() async throws {
        let revokedFixture = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "expired.student"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                return MockClientTransport.jsonResponse(statusCode: 401, data: revokedFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        XCTAssertEqual(stack.sessionStore.state, .locked)

        // Launch validation waits while locked so expired state is revealed upon unlock.
        await stack.sessionStore.validateSessionOnLaunch()
        XCTAssertEqual(stack.sessionStore.state, .locked)
        XCTAssertTrue(stack.transport.recordedRequests.isEmpty)

        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await lockVM.unlockWithBiometrics(locale: .vi)

        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .expired))
        XCTAssertNil(try stack.tokenStore.read())
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())

        let loginVM = LoginViewModel(sessionStore: stack.sessionStore, initialReason: .expired)
        XCTAssertEqual(
            loginVM.serverBanner?.message(locale: .vi),
            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        )
    }

    func testOfflineUnlockRemainsUnlockedNormallyWithoutSigningOut() async throws {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "offline.student"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { _ in
            throw URLError(.notConnectedToInternet)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        XCTAssertEqual(stack.sessionStore.state, .locked)

        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await lockVM.unlockWithBiometrics(locale: .vi)

        XCTAssertEqual(stack.sessionStore.state, .active, "Offline biometric unlock must open app normally")
        XCTAssertEqual(stack.sessionStore.connectivityState, .offline)
        XCTAssertNotNil(try stack.tokenStore.read(), "Offline unlock must retain stored session")
        XCTAssertEqual(stack.biometricManager.state, .on)
    }
}
