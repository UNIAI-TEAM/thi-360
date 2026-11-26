import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

final class CatalogOptionsCacheTests: XCTestCase {
    func testCachesWithinFiveMinutesRefetchesAtFiveMinutesAndDoesNotCacheErrors() async throws {
        let clock = ManualClock()
        let transport = ContractFakeTransport()
        let examOptionsJSON = Data(
            #"[{"code":"ielts","nameVi":"IELTS","nameEn":"IELTS"},{"code":"toeic","nameVi":"TOEIC","nameEn":"TOEIC"}]"#.utf8
        )
        let subjectOptionsJSON = Data(
            #"[{"code":"reading","nameVi":"Đọc hiểu","nameEn":"Reading"}]"#.utf8
        )

        transport.registerJSON(method: .get, path: "/api/v1/catalog/options", statusCode: 200, data: examOptionsJSON)

        let fetchTracker = FetchCounter()
        let cache = CatalogOptionsCache(clock: clock) { type in
            let count = await fetchTracker.increment(type)
            let path = "/api/v1/catalog/options?type=\(type.rawValue)"
            let request = HTTPRequest(method: .get, scheme: "https", authority: "api.thi360.test", path: path)
            let (response, body) = try await transport.send(
                request,
                body: nil,
                baseURL: URL(string: "https://api.thi360.test")!,
                operationID: "getCatalogOptions"
            )
            guard response.status.code == 200, let body else {
                throw MobileApiError.unexpectedResponse(statusCode: response.status.code)
            }
            let data = try await Data(collecting: body, upTo: 65_536)
            if type == .subject {
                return try JSONDecoder().decode([CatalogOptionItem].self, from: subjectOptionsJSON)
            }
            var items = try JSONDecoder().decode([CatalogOptionItem].self, from: data)
            items.append(CatalogOptionItem(code: "fetch-\(count)", nameVi: "Lần \(count)", nameEn: "Fetch \(count)"))
            return items
        }

        let exams1 = try await cache.options(for: .exam)
        let subjects1 = try await cache.options(for: .subject)
        XCTAssertEqual(exams1.count, 3)
        XCTAssertEqual(subjects1.count, 1)
        let examCount1 = await fetchTracker.count(for: .exam)
        let subjectCount1 = await fetchTracker.count(for: .subject)
        XCTAssertEqual(examCount1, 1)
        XCTAssertEqual(subjectCount1, 1)

        // At 4m59s (299s) cache is still valid -> no refetch.
        clock.advance(by: 299)
        let exams2 = try await cache.options(for: .exam)
        let subjects2 = try await cache.options(for: .subject)
        XCTAssertEqual(exams2, exams1)
        XCTAssertEqual(subjects2, subjects1)
        let examCount2 = await fetchTracker.count(for: .exam)
        let subjectCount2 = await fetchTracker.count(for: .subject)
        XCTAssertEqual(examCount2, 1)
        XCTAssertEqual(subjectCount2, 1)

        // At 5m00s (300s) cache expires -> refetches.
        clock.advance(by: 1)
        let exams3 = try await cache.options(for: .exam)
        XCTAssertNotEqual(exams3, exams1)
        let examCount3 = await fetchTracker.count(for: .exam)
        XCTAssertEqual(examCount3, 2)

        // Errors are never cached: next read retries immediately.
        let failingCache = CatalogOptionsCache(clock: clock) { _ in
            let attempt = await fetchTracker.incrementErrorAttempt()
            if attempt == 1 {
                throw MobileApiError.api(statusCode: 500, error: ApiError(code: "SERVER_ERROR"))
            }
            return [CatalogOptionItem(code: "ok", nameVi: "Khôi phục", nameEn: "Recovered")]
        }
        do {
            _ = try await failingCache.options(for: .exam)
            XCTFail("Expected initial failure")
        } catch {}

        let recovered = try await failingCache.options(for: .exam)
        XCTAssertEqual(recovered, [CatalogOptionItem(code: "ok", nameVi: "Khôi phục", nameEn: "Recovered")])
        let errorAttempts = await fetchTracker.errorAttempts
        XCTAssertEqual(errorAttempts, 2)
    }
}

private actor FetchCounter {
    private var counts: [CatalogOptionType: Int] = [:]
    private(set) var errorAttempts = 0

    func increment(_ type: CatalogOptionType) -> Int {
        counts[type, default: 0] += 1
        return counts[type, default: 0]
    }

    func count(for type: CatalogOptionType) -> Int {
        counts[type, default: 0]
    }

    func incrementErrorAttempt() -> Int {
        errorAttempts += 1
        return errorAttempts
    }
}
