import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PortalAuthCallTests: XCTestCase {
    func testRefreshOnceThenRetry() async throws {
        let refreshFixture = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let profileFixture = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let expired401 = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")

        let initialTokens = SessionTokens(
            accessToken: "expired-access",
            refreshToken: "valid-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo",
            accountId: "acc-refresh-1"
        )

        let (sessionStore, tokenStore, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: initialTokens
        ) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 200, data: refreshFixture)
            transport.enqueue(method: .get, path: "/api/v1/me/profile", handlers: [
                { req in
                    XCTAssertEqual(req.header(.authorization), "Bearer expired-access")
                    return ContractFakeTransport.jsonResponse(statusCode: 401, data: expired401)
                },
                { req in
                    XCTAssertEqual(req.header(.authorization), "Bearer fixture-access-token")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: profileFixture)
                }
            ])
        }

        let profile = try await sessionStore.apiClient.getMyProfile()
        XCTAssertEqual(profile.username, "student.demo")
        XCTAssertEqual(sessionStore.state, .active)
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/profile"), 2)
        XCTAssertEqual(try tokenStore.read()?.accountId, "acc-refresh-1", "Refresh must preserve accountId")
    }

    func testConcurrentCallsSingleRefresh() async throws {
        let refreshFixture = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let meFixture = try ContractFixtureLoader.loadApiFixture("me-200.json")
        let expired401 = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let refreshGate = TestAsyncGate()

        let initialTokens = SessionTokens(
            accessToken: "expired-concurrent-access",
            refreshToken: "valid-concurrent-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo",
            accountId: "acc-concurrent"
        )

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: initialTokens
        ) { transport in
            transport.register(method: .post, path: "/api/v1/auth/mobile/refresh") { _ in
                await refreshGate.enterAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: refreshFixture)
            }
            transport.register(method: .get, path: "/api/v1/me") { req in
                if req.header(.authorization) == "Bearer expired-concurrent-access" {
                    return ContractFakeTransport.jsonResponse(statusCode: 401, data: expired401)
                }
                XCTAssertEqual(req.header(.authorization), "Bearer fixture-access-token")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: meFixture)
            }
        }

        let client = sessionStore.apiClient
        let t1 = Task { try await client.me() }
        let t2 = Task { try await client.me() }
        let t3 = Task { try await client.me() }

        await refreshGate.waitUntilEntered()
        await refreshGate.release()

        let (r1, r2, r3) = try await (t1.value, t2.value, t3.value)
        XCTAssertEqual(r1.username, "student.demo")
        XCTAssertEqual(r2.username, "student.demo")
        XCTAssertEqual(r3.username, "student.demo")
        XCTAssertEqual(
            transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"),
            1,
            "Three concurrent 401 calls must coalesce into strictly one refresh"
        )
        XCTAssertEqual(sessionStore.state, .active)
    }

    func testExpiredSessionKeepsAccountData() async throws {
        let revoked401 = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")
        let loginFixture = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let meFixture = try ContractFixtureLoader.loadApiFixture("me-200.json")

        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-expired-store-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let cleanup = SessionCleanup(storeProvider: storeProvider)

        final class SpyParticipant: SessionCleanupParticipant, @unchecked Sendable {
            let id = "spy.expired"
            private let lock = NSLock()
            private var _logoutCalls = 0
            var logoutCalls: Int { lock.lock(); defer { lock.unlock() }; return _logoutCalls }
            func onLogout(accountId: String) async throws {
                lock.lock()
                _logoutCalls += 1
                lock.unlock()
            }
        }
        let spy = SpyParticipant()
        await cleanup.register(spy)

        let accountId = "019320a0-0000-7000-8000-000000000001"
        let initialTokens = SessionTokens(
            accessToken: "expired-token",
            refreshToken: "revoked-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo",
            accountId: accountId
        )

        let (sessionStore, tokenStore, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: initialTokens,
            storeProvider: storeProvider,
            sessionCleanup: cleanup
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 401, data: revoked401)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 401, data: revoked401)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/login", statusCode: 200, data: loginFixture)
            transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: meFixture)
        }

        let savedPayload = Data("in-progress-attempt-draft".utf8)
        let written = try await storeProvider.write(savedPayload, accountId: accountId, namespace: "drafts", key: "att-1")
        XCTAssertTrue(written)

        do {
            _ = try await sessionStore.apiClient.getMyProfile()
            XCTFail("Expected 401 error after failed refresh")
        } catch {}

        try await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(sessionStore.state, .signedOut(reason: .expired))
        XCTAssertNil(try tokenStore.read())
        XCTAssertEqual(spy.logoutCalls, 0, "Session expiry must NOT run SessionCleanup logout participants")

        let loginVM = LoginViewModel(sessionStore: sessionStore, initialReason: .expired)
        XCTAssertEqual(
            loginVM.serverBanner?.message(locale: .vi),
            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        )

        let stillPresent = await storeProvider.read(accountId: accountId, namespace: "drafts", key: "att-1")
        XCTAssertEqual(stillPresent, savedPayload, "AccountScopedStore data must survive session expiry")

        try await sessionStore.login(identifier: "student.demo", password: "Password123!")
        XCTAssertEqual(sessionStore.state, .active)
        XCTAssertEqual(sessionStore.currentAccountId, accountId)
        let afterRelogin = await sessionStore.currentAccountStore()?.read(namespace: "drafts", key: "att-1")
        XCTAssertEqual(afterRelogin, savedPayload)
    }
}
