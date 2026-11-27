import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ResultRepositoryTests: XCTestCase {
    func testEveryResultOpenCallsEndpointOnceAndCatalogOptionsCachedFor5Minutes() async throws {
        let clock = FakeResultAppClock()
        let attempt1 = "00000000-0000-7000-8000-000000000001"
        let attempt2 = "00000000-0000-7000-8000-000000000099"

        let (sessionStore, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try ResultTestFixtures.registerCatalogOptions(on: transport)
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/attempts/\(attempt1)/result",
                statusCode: 200,
                relativeFixturePath: ResultTestFixtures.loadRelativePath(
                    operationId: Operations.getResult.id,
                    status: 200,
                    variant: "scored"
                )
            )
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/attempts/\(attempt2)/result",
                statusCode: 200,
                relativeFixturePath: ResultTestFixtures.loadRelativePath(
                    operationId: Operations.getResult.id,
                    status: 200,
                    variant: "vact"
                )
            )
        }
        defer { _ = preferences }

        let optionsCache = CatalogOptionsCache(apiClient: sessionStore.apiClient, clock: clock)
        let repo = ResultRepository(
            apiClient: sessionStore.apiClient,
            catalogOptionsCache: optionsCache,
            clock: clock
        )

        let r1 = try await repo.result(attemptId: attempt1)
        let exams1 = await repo.examOptions()
        let subjects1 = await repo.subjectOptions()
        XCTAssertEqual(r1.state, .scored)
        XCTAssertFalse(exams1.isEmpty)
        XCTAssertFalse(subjects1.isEmpty)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attempt1)/result"), 1)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/catalog/options"), 2)

        clock.advance(by: 120)
        _ = try await repo.result(attemptId: attempt2)
        _ = await repo.examOptions()
        _ = await repo.subjectOptions()
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attempt2)/result"), 1)
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/catalog/options"),
            2,
            "Catalog options must not refetch within 5 minutes even when opening another attempt"
        )

        _ = try await repo.result(attemptId: attempt1)
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attempt1)/result"),
            2,
            "Re-opening result screen must trigger exactly one fresh GET .../result"
        )

        clock.advance(by: 200)
        _ = await repo.examOptions()
        _ = await repo.subjectOptions()
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/catalog/options"),
            4,
            "Catalog options should refresh after 5-minute TTL expires"
        )
    }

    func testReviewReusesCachedResultWithin60SecondsAndRefetchesAfter60Seconds() async throws {
        let clock = FakeResultAppClock()
        let attemptId = "00000000-0000-7000-8000-000000000001"

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/attempts/\(attemptId)/result",
                statusCode: 200,
                relativeFixturePath: ResultTestFixtures.loadRelativePath(
                    operationId: Operations.getResult.id,
                    status: 200,
                    variant: "scored"
                )
            )
        }

        let repo = ResultRepository(apiClient: sessionStore.apiClient, clock: clock)
        _ = try await repo.result(attemptId: attemptId)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"), 1)

        clock.advance(by: 45)
        let reused = try await repo.resultForReview(attemptId: attemptId)
        XCTAssertEqual(reused.state, .scored)
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"),
            1,
            "Review must reuse in-memory result cached within 60 seconds"
        )

        clock.advance(by: 20)
        _ = try await repo.resultForReview(attemptId: attemptId)
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/me/attempts/\(attemptId)/result"),
            2,
            "Review must fetch fresh result after 60-second reuse window expires"
        )
    }
}
