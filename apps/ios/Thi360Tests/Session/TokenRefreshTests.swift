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
}

private actor AtomicCounter {
    private(set) var value: Int = 0

    func increment() {
        value += 1
    }
}
