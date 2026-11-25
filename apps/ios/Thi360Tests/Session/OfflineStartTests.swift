import Foundation
import XCTest
@testable import Thi360

@MainActor
final class OfflineStartTests: XCTestCase {
    func testOfflineLaunchNeverSignsOutEvenWhenLocalClockExceedsExpiry() async throws {
        // Simulate a token issued 60 days ago (far past both accessExpiresIn=300 and refreshExpiresIn=604800 and 30d cap).
        let staleIssuedAt = Date(timeIntervalSinceNow: -60 * 24 * 60 * 60)
        let storedTokens = SessionTokens(
            accessToken: "stored-access-token",
            refreshToken: "stored-refresh-token",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.offline",
            issuedAt: staleIssuedAt
        )

        let (sessionStore, tokenStore, transport, _) = SessionTestFactory.makeStack(
            initialTokens: storedTokens
        ) { _ in
            throw URLError(.notConnectedToInternet)
        }

        XCTAssertEqual(sessionStore.state, .active, "Initial state with stored tokens must not sign out locally based on clock")

        await sessionStore.validateSessionOnLaunch()

        XCTAssertEqual(sessionStore.state, .active, "Offline launch must retain authenticated session regardless of device clock")
        XCTAssertEqual(sessionStore.connectivityState, .offline, "Offline launch must surface offline status for Retry UI")
        XCTAssertEqual(try tokenStore.read(), storedTokens, "Offline launch must keep stored tokens intact")

        let refreshFixture = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        transport.setHandler { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: refreshFixture)
        }

        await sessionStore.retryConnection()

        XCTAssertEqual(sessionStore.connectivityState, .online)
        XCTAssertEqual(sessionStore.state, .active)
        XCTAssertEqual(try tokenStore.read()?.accessToken, "fixture-access-token")
    }

    func testOfflineLaunchWithTimeoutRetainsMustChangePasswordSession() async throws {
        let staleIssuedAt = Date(timeIntervalSinceNow: -10 * 24 * 60 * 60)
        let storedTokens = SessionTokens(
            accessToken: "stored-access-token",
            refreshToken: "stored-refresh-token",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: true,
            username: "student.forced",
            issuedAt: staleIssuedAt
        )

        let (sessionStore, tokenStore, _, _) = SessionTestFactory.makeStack(
            initialTokens: storedTokens
        ) { _ in
            throw URLError(.timedOut)
        }

        await sessionStore.validateSessionOnLaunch()

        XCTAssertEqual(sessionStore.state, .mustChangePassword)
        XCTAssertEqual(sessionStore.connectivityState, .offline)
        XCTAssertEqual(try tokenStore.read(), storedTokens)
    }
}
