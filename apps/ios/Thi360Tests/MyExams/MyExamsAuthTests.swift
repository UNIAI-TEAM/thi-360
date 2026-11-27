import Foundation
import XCTest
@testable import Thi360

@MainActor
final class MyExamsAuthTests: XCTestCase {
    func testUnauthorizedRefreshesAndRetriesTransparently() async throws {
        let unauthorized401 = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.me.id, status: 401, variant: "unauthenticated")
        let myExams200 = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listMyExams.id, status: 200, variant: "")
        let refresh200 = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")

        let stack = try MyExamsTestStackFactory.makeStack(
            initialTokens: SessionTokens(
                accessToken: "expired-access-myexams",
                refreshToken: "valid-refresh-myexams",
                accessExpiresIn: 300,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student.demo",
                accountId: "acc-1"
            )
        ) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 200, data: refresh200)
            transport.enqueue(method: .get, path: "/api/v1/me/exams", handlers: [
                { req in
                    XCTAssertEqual(req.header(.authorization), "Bearer expired-access-myexams")
                    return ContractFakeTransport.jsonResponse(statusCode: 401, data: unauthorized401)
                },
                { req in
                    XCTAssertEqual(req.header(.authorization), "Bearer fixture-access-token")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: myExams200)
                }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "student.demo"
        )
        await feed.loadOnAppear()

        guard case let .loaded(cards, stale) = feed.loadState else {
            return XCTFail("Expected .loaded after 401 refresh retry, got \(feed.loadState)")
        }
        XCTAssertFalse(stale)
        XCTAssertFalse(cards.isEmpty)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 2)
        XCTAssertEqual(stack.sessionStore.state, .active)
    }

    func testForbiddenDoesNotAutoRetryAndRefreshesToStudentOnlyOrExpired() async throws {
        let forbidden403 = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listMyExams.id, status: 403, variant: "forbidden")
        let studentOnly403 = try ContractFixtureLoader.loadAuthFixture("refresh-403-app-student-only.json")
        let revoked401 = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")

        let sleeper1 = RecordingRetrySleeper()
        let stack1 = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/exams", statusCode: 403, data: forbidden403)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 403, data: studentOnly403)
        }
        defer { UserDefaults(suiteName: stack1.defaultsSuiteName)?.removePersistentDomain(forName: stack1.defaultsSuiteName) }

        let feed1 = MyExamsFeed(
            accountId: "acc-1",
            api: stack1.myExamsAPI,
            clock: stack1.clock,
            retrySleeper: sleeper1
        )
        await feed1.loadOnAppear()
        try await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(stack1.transport.callCount(method: .get, path: "/api/v1/me/exams"), 1, "403 FORBIDDEN must NOT auto-retry")
        XCTAssertTrue(sleeper1.recordedAttempts.isEmpty)
        XCTAssertEqual(stack1.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(feed1.loadState, .failed(kind: .forbidden, canRetry: true, retryAfter: nil))
        XCTAssertEqual(stack1.sessionStore.state, .signedOut(reason: .studentOnly(username: "student.demo")))

        let sleeper2 = RecordingRetrySleeper()
        let stack2 = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/exams", statusCode: 403, data: forbidden403)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 401, data: revoked401)
        }
        defer { UserDefaults(suiteName: stack2.defaultsSuiteName)?.removePersistentDomain(forName: stack2.defaultsSuiteName) }

        let feed2 = MyExamsFeed(
            accountId: "acc-2",
            api: stack2.myExamsAPI,
            clock: stack2.clock,
            retrySleeper: sleeper2
        )
        await feed2.loadOnAppear()
        try await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(stack2.transport.callCount(method: .get, path: "/api/v1/me/exams"), 1)
        XCTAssertEqual(stack2.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(stack2.sessionStore.state, .signedOut(reason: .expired))
    }
}
