import Foundation
import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class CatalogOptionsCacheTests: XCTestCase {
    func testCachesWithinFiveMinutesRefetchesAtFiveMinutesAndDoesNotCacheErrors() async throws {
        let clock = ManualClock()
        let examFixture = try ContractFixtureLoader.loadApiFixture("catalog-options-200-exam.json")
        let subjectFixture = try ContractFixtureLoader.loadApiFixture("catalog-options-200-subject.json")
        let serverErrorFixture = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.register(method: .get, path: "/api/v1/catalog/options") { request in
                if request.path.contains("group=SUBJECT") {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: subjectFixture)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: examFixture)
            }
        }

        let cache = CatalogOptionsCache(apiClient: sessionStore.apiClient, clock: clock)

        let exams1 = try await cache.options(for: .exam)
        let subjects1 = try await cache.options(for: .subject)
        XCTAssertFalse(exams1.isEmpty)
        XCTAssertFalse(subjects1.isEmpty)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/catalog/options"), 2)

        clock.advance(by: 299)
        let exams2 = try await cache.options(for: .exam)
        let subjects2 = try await cache.options(for: .subject)
        XCTAssertEqual(exams2, exams1)
        XCTAssertEqual(subjects2, subjects1)
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/catalog/options"),
            2,
            "At 4m59s (299s), cache must remain warm without new HTTP requests"
        )

        clock.advance(by: 1)
        let exams3 = try await cache.options(for: .exam)
        XCTAssertEqual(exams3, exams1)
        XCTAssertEqual(
            transport.callCount(method: .get, path: "/api/v1/catalog/options"),
            3,
            "At 5m00s (300s), cache expires and refetches"
        )

        let (errorSession, _, errorTransport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.enqueue(method: .get, path: "/api/v1/catalog/options", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 500, data: serverErrorFixture) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: examFixture) }
            ])
        }
        let failingCache = CatalogOptionsCache(apiClient: errorSession.apiClient, clock: clock)

        do {
            _ = try await failingCache.options(for: .exam)
            XCTFail("Expected initial failure")
        } catch {}

        let recovered = try await failingCache.options(for: .exam)
        XCTAssertFalse(recovered.isEmpty)
        XCTAssertEqual(
            errorTransport.callCount(method: .get, path: "/api/v1/catalog/options"),
            2,
            "Failed catalog fetch must not be cached and next read must retry"
        )
    }
}
