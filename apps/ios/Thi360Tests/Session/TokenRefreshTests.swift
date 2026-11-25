import Foundation
import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class TokenRefreshTests: XCTestCase {
    func testSingleFlight() async throws {
        let unauthorizedFixture = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let meFixture = try ContractFixtureLoader.loadApiFixture("me-200.json")
        let rawRefreshFixture = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let refreshFixtureString = try XCTUnwrap(String(data: rawRefreshFixture, encoding: .utf8))
            .replacingOccurrences(of: "fixture-access-token", with: "refreshed-access-token")
            .replacingOccurrences(of: "fixture-refresh-token", with: "refreshed-refresh-token")
        let refreshFixture = Data(refreshFixtureString.utf8)

        let initialTokens = SessionTokens(
            accessToken: "expired-access-token",
            refreshToken: "valid-refresh-token",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo"
        )

        let counter = AtomicCounter()
        let (sessionStore, tokenStore, transport, _) = SessionTestFactory.makeStack(
            initialTokens: initialTokens
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                await counter.increment()
                try await Task.sleep(nanoseconds: 120_000_000)
                return MockClientTransport.jsonResponse(statusCode: 200, data: refreshFixture)
            }
            if request.path.hasSuffix("/api/v1/me") {
                let auth = request.header(.authorization)
                if auth == "Bearer refreshed-access-token" {
                    return MockClientTransport.jsonResponse(statusCode: 200, data: meFixture)
                }
                return MockClientTransport.jsonResponse(statusCode: 401, data: unauthorizedFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        let client = sessionStore.apiClient
        let results = try await withThrowingTaskGroup(of: Components.Schemas.MeResponse.self) { group in
            for _ in 0..<3 {
                group.addTask {
                    try await client.me()
                }
            }
            var collected: [Components.Schemas.MeResponse] = []
            for try await me in group {
                collected.append(me)
            }
            return collected
        }

        XCTAssertEqual(results.count, 3, "All three concurrent requests must succeed after refresh")
        for result in results {
            XCTAssertEqual(result.username, "student.demo")
        }

        let refreshCalls = await counter.value
        XCTAssertEqual(refreshCalls, 1, "Three concurrent 401 responses must trigger exactly one refresh call")

        let stored = try tokenStore.read()
        XCTAssertEqual(stored?.accessToken, "refreshed-access-token")
        XCTAssertEqual(stored?.refreshToken, "refreshed-refresh-token")
        XCTAssertEqual(stored?.username, "student.demo", "Username metadata must be preserved across refresh")
        XCTAssertEqual(sessionStore.state, .active)

        let refreshRequests = transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/auth/mobile/refresh") }
        XCTAssertEqual(refreshRequests.count, 1)
    }

    func testExpiredSessionSignsOut() async throws {
        let unauthorizedFixture = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let revokedFixture = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")

        let initialTokens = SessionTokens(
            accessToken: "expired-access-token",
            refreshToken: "revoked-refresh-token",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo"
        )

        let (sessionStore, tokenStore, _, _) = SessionTestFactory.makeStack(
            initialTokens: initialTokens
        ) { request in
            if request.path.hasSuffix("/api/v1/me") {
                return MockClientTransport.jsonResponse(statusCode: 401, data: unauthorizedFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                return MockClientTransport.jsonResponse(statusCode: 401, data: revokedFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        do {
            _ = try await sessionStore.apiClient.me()
            XCTFail("Expected 401 error when refresh fails with 401")
        } catch let error as MobileApiError {
            XCTAssertEqual(error.statusCode, 401)
            XCTAssertEqual(error.apiCode, "SESSION_REVOKED")
        }

        XCTAssertNil(try tokenStore.read(), "401 on refresh must clear persisted tokens")
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .expired))

        let loginVM = LoginViewModel(sessionStore: sessionStore, initialReason: .expired)
        XCTAssertEqual(
            loginVM.serverBanner?.message(locale: .vi),
            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        )
        XCTAssertEqual(
            loginVM.serverBanner?.message(locale: .en),
            "Your session has expired. Please sign in again."
        )
    }

    func testRefreshReturningMustChangePasswordTransitionsState() async throws {
        let mustChangeFixture = try ContractFixtureLoader.loadAuthFixture("login-200-must-change-password.json")
        let initialTokens = SessionTokens(
            accessToken: "old-access",
            refreshToken: "valid-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo"
        )

        let (sessionStore, _, _, _) = SessionTestFactory.makeStack(initialTokens: initialTokens) { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: mustChangeFixture)
        }

        await sessionStore.validateSessionOnLaunch()
        XCTAssertEqual(sessionStore.state, .mustChangePassword)
    }

    func testUnrecoverableUnauthorizedAfterSuccessfulRefreshSignsOut() async throws {
        let unauthorizedFixture = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let refreshFixture = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")

        let initialTokens = SessionTokens(
            accessToken: "expired-access-token",
            refreshToken: "valid-refresh-token",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo"
        )

        let (sessionStore, tokenStore, transport, _) = SessionTestFactory.makeStack(
            initialTokens: initialTokens
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: refreshFixture)
            }
            if request.path.hasSuffix("/api/v1/me") {
                return MockClientTransport.jsonResponse(statusCode: 401, data: unauthorizedFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        do {
            _ = try await sessionStore.apiClient.me()
            XCTFail("Expected 401 when retried /api/v1/me still fails after refresh")
        } catch let error as MobileApiError {
            XCTAssertEqual(error.statusCode, 401)
        }

        XCTAssertNil(try tokenStore.read(), "Unrecoverable 401 after refresh retry must clear persisted tokens")
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .expired))
        XCTAssertEqual(
            transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/auth/mobile/refresh") }.count,
            1
        )
        XCTAssertEqual(
            transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/me") }.count,
            2
        )
    }

    func testSignOutDuringInFlightRefreshDoesNotResurrectSession() async throws {
        let refreshFixture = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let gate = RefreshBarrier()

        let initialTokens = SessionTokens(
            accessToken: "userA-access",
            refreshToken: "userA-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "userA"
        )

        let (sessionStore, tokenStore, _, _) = SessionTestFactory.makeStack(
            initialTokens: initialTokens
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                await gate.markEnteredAndWait()
                return MockClientTransport.jsonResponse(statusCode: 200, data: refreshFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        let probeTask = Task { @MainActor in
            await sessionStore.validateSessionOnLaunch()
        }

        await gate.waitUntilEntered()
        await sessionStore.signOut()
        XCTAssertNil(try tokenStore.read())
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .none))

        await gate.release()
        await probeTask.value

        XCTAssertNil(try tokenStore.read(), "Late 200 refresh after signOut must not re-persist tokens")
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .none), "Late 200 refresh after signOut must not resurrect session")
    }

    func testAccountSwitchDuringInFlightFailedRefreshPreservesNewUserSession() async throws {
        let userBLoginJSON = """
        {
          "accessToken": "userB-access",
          "refreshToken": "userB-refresh",
          "accessExpiresIn": 300,
          "refreshExpiresIn": 604800,
          "mustChangePassword": false
        }
        """

        for failureCase in [
            (statusCode: 401, fixture: "refresh-401-session-revoked.json"),
            (statusCode: 403, fixture: "refresh-403-app-student-only.json")
        ] {
            let failureBody = try ContractFixtureLoader.loadAuthFixture(failureCase.fixture)
            let gate = RefreshBarrier()

            let userATokens = SessionTokens(
                accessToken: "userA-access",
                refreshToken: "userA-refresh",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "userA"
            )

            let (sessionStore, tokenStore, _, _) = SessionTestFactory.makeStack(
                initialTokens: userATokens
            ) { request in
                if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                    await gate.markEnteredAndWait()
                    return MockClientTransport.jsonResponse(statusCode: failureCase.statusCode, data: failureBody)
                }
                if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                    return MockClientTransport.emptyResponse(statusCode: 204)
                }
                if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                    return MockClientTransport.jsonResponse(statusCode: 200, data: Data(userBLoginJSON.utf8))
                }
                return MockClientTransport.emptyResponse(statusCode: 500)
            }

            let staleRefreshTask = Task {
                try? await sessionStore.apiClient.refresher.refreshIfNeeded(failedAccessToken: "userA-access")
            }

            await gate.waitUntilEntered()
            await sessionStore.signOut()
            try await sessionStore.login(identifier: "userB", password: "Password123!")

            XCTAssertEqual(try tokenStore.read()?.username, "userB")
            XCTAssertEqual(sessionStore.state, .active)

            await gate.release()
            _ = await staleRefreshTask.value

            XCTAssertEqual(try tokenStore.read()?.accessToken, "userB-access", "Stale refresh failure (\(failureCase.statusCode)) must not wipe User B tokens")
            XCTAssertEqual(try tokenStore.read()?.username, "userB")
            XCTAssertEqual(sessionStore.state, .active, "Stale refresh failure (\(failureCase.statusCode)) must not sign out User B")
        }
    }

    func testSingleFlightFollowersReceiveCommittedTokensWithUsername() async throws {
        let gate = RefreshBarrier()
        let initialTokens = SessionTokens(
            accessToken: "old-access",
            refreshToken: "old-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo"
        )
        let store = InMemoryTokenStore(initialTokens: initialTokens)
        let refresher = TokenRefresher(
            tokenStore: store,
            refreshExecutor: { _ in
                await gate.markEnteredAndWait()
                return SessionTokens(
                    accessToken: "new-access",
                    refreshToken: "new-refresh",
                    accessExpiresIn: 300,
                    refreshExpiresIn: 604_800,
                    mustChangePassword: false,
                    username: nil
                )
            }
        )

        let leaderTask = Task {
            try await refresher.refreshIfNeeded(failedAccessToken: "old-access")
        }
        await gate.waitUntilEntered()

        let followerTask = Task {
            let tokens = try await refresher.refreshIfNeeded(failedAccessToken: "old-access")
            let persistedAccess = try store.read()?.accessToken
            return (tokens, persistedAccess)
        }

        await gate.release()

        let leaderTokens = try await leaderTask.value
        let (followerTokens, persistedAccessAtFollowerResume) = try await followerTask.value

        XCTAssertEqual(leaderTokens.username, "student.demo")
        XCTAssertEqual(followerTokens.username, "student.demo")
        XCTAssertEqual(followerTokens.accessToken, "new-access")
        XCTAssertEqual(persistedAccessAtFollowerResume, "new-access")
    }
}

private actor RefreshBarrier {
    private var entered = false
    private var released = false
    private var enteredWaiters: [CheckedContinuation<Void, Never>] = []
    private var releaseWaiters: [CheckedContinuation<Void, Never>] = []

    func markEnteredAndWait() async {
        entered = true
        let waiters = enteredWaiters
        enteredWaiters.removeAll()
        waiters.forEach { $0.resume() }

        guard !released else { return }
        await withCheckedContinuation { continuation in
            releaseWaiters.append(continuation)
        }
    }

    func waitUntilEntered() async {
        guard !entered else { return }
        await withCheckedContinuation { continuation in
            enteredWaiters.append(continuation)
        }
    }

    func release() {
        released = true
        let waiters = releaseWaiters
        releaseWaiters.removeAll()
        waiters.forEach { $0.resume() }
    }
}

private actor AtomicCounter {
    private(set) var value: Int = 0

    func increment() {
        value += 1
    }
}
