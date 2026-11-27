import Foundation
import XCTest
@testable import Thi360

@MainActor
final class HistoryAuthTests: XCTestCase {
    func testUnauthorizedRefreshesAndRetriesTransparently() async throws {
        let unauthorized401 = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.me.id, status: 401, variant: "unauthenticated")
        let attempts200 = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listHistory.id, status: 200, variant: "")
        let refresh200 = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")

        let stack = try MyExamsTestStackFactory.makeStack(
            initialTokens: SessionTokens(
                accessToken: "expired-access-history",
                refreshToken: "valid-refresh-history",
                accessExpiresIn: 300,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student.demo",
                accountId: "acc-h1"
            )
        ) { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 200, data: refresh200)
            transport.enqueue(method: .get, path: "/api/v1/me/attempts", handlers: [
                { req in
                    XCTAssertEqual(req.header(.authorization), "Bearer expired-access-history")
                    return ContractFakeTransport.jsonResponse(statusCode: 401, data: unauthorized401)
                },
                { req in
                    XCTAssertEqual(req.header(.authorization), "Bearer fixture-access-token")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: attempts200)
                }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let feed = HistoryFeed(accountId: "acc-h1", api: stack.historyAPI, optionsCache: stack.optionsCache, clock: stack.clock)
        await feed.loadOnAppear(resetFilters: false, reloadToken: 0)

        guard case let .loaded(page, stale) = feed.loadState else {
            return XCTFail("Expected .loaded after 401 refresh retry, got \(feed.loadState)")
        }
        XCTAssertFalse(stale)
        XCTAssertFalse(page.items.isEmpty)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 2)
        XCTAssertEqual(stack.sessionStore.state, .active)
    }

    func testForbiddenAttemptAccessDoesNotAutoRetryAndRefreshesToStudentOnlyOrExpired() async throws {
        let forbidden403 = try MyExamsTestFixtures.loadIndexedData(
            operationId: Operations.listHistory.id,
            status: 403,
            variant: "attempt-access-forbidden"
        )
        let studentOnly403 = try ContractFixtureLoader.loadAuthFixture("refresh-403-app-student-only.json")
        let revoked401 = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")

        let stack1 = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts", statusCode: 403, data: forbidden403)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 403, data: studentOnly403)
        }
        defer { UserDefaults(suiteName: stack1.defaultsSuiteName)?.removePersistentDomain(forName: stack1.defaultsSuiteName) }

        let feed1 = HistoryFeed(accountId: "acc-h1", api: stack1.historyAPI, optionsCache: stack1.optionsCache, clock: stack1.clock)
        await feed1.loadOnAppear(resetFilters: false, reloadToken: 0)
        try await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(stack1.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1, "403 ATTEMPT_ACCESS_FORBIDDEN must not auto-retry")
        XCTAssertEqual(stack1.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(feed1.loadState, .failed(kind: .forbidden, canRetry: true, retryAfter: nil))
        XCTAssertEqual(stack1.sessionStore.state, .signedOut(reason: .studentOnly(username: "student.demo")))

        let stack2 = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts", statusCode: 403, data: forbidden403)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 401, data: revoked401)
        }
        defer { UserDefaults(suiteName: stack2.defaultsSuiteName)?.removePersistentDomain(forName: stack2.defaultsSuiteName) }

        let feed2 = HistoryFeed(accountId: "acc-h2", api: stack2.historyAPI, optionsCache: stack2.optionsCache, clock: stack2.clock)
        await feed2.loadOnAppear(resetFilters: false, reloadToken: 0)
        try await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(stack2.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1)
        XCTAssertEqual(stack2.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(stack2.sessionStore.state, .signedOut(reason: .expired))
    }
}
