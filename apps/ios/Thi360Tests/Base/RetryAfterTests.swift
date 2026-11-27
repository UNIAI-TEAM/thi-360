import Foundation
import HTTPTypes
import XCTest
@testable import Thi360

final class RetryAfterTests: XCTestCase {
    func testParsingAndHeaderPriorityOverBodyField() {
        XCTAssertEqual(RetryAfter.parseSeconds("1"), 1)
        XCTAssertEqual(RetryAfter.parseSeconds(" 45 "), 45)
        XCTAssertNil(RetryAfter.parseSeconds(nil))
        XCTAssertNil(RetryAfter.parseSeconds(""))
        XCTAssertNil(RetryAfter.parseSeconds("0"))
        XCTAssertNil(RetryAfter.parseSeconds("-5"))
        XCTAssertNil(RetryAfter.parseSeconds("Wed, 21 Oct 2026 07:28:00 GMT"))

        XCTAssertEqual(RetryAfter.resolve(headerValue: "30", bodyRetryAfter: 10), 30, "HTTP header must beat body field")
        XCTAssertEqual(RetryAfter.resolve(headerValue: nil, bodyRetryAfter: 12), 12)
        XCTAssertEqual(RetryAfter.resolve(headerValue: "invalid", bodyRetryAfter: 12), 12)
        XCTAssertNil(RetryAfter.resolve(headerValue: nil, bodyRetryAfter: nil))
        XCTAssertNil(RetryAfter.resolve(headerValue: nil, bodyRetryAfter: 0))
    }

    @MainActor
    func testMobileApiClientPopulatesRetryAfterSecondsFromHeaderOrBody() async throws {
        let rateLimitedBody = try ContractFixtureLoader.loadApiFixture("register-429-otp-rate-limited.json")

        let (session1, _, _, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerRateLimited(
                method: .get,
                path: "/api/v1/me",
                retryAfterHeader: "1",
                bodyData: rateLimitedBody
            )
        }
        do {
            _ = try await session1.apiClient.me()
            XCTFail("Expected 429 error")
        } catch let error as MobileApiError {
            XCTAssertEqual(error.statusCode, 429)
            XCTAssertEqual(error.retryAfterSeconds, 1)
        }

        let (session2, _, _, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerRateLimited(
                method: .get,
                path: "/api/v1/me",
                retryAfterHeader: "25",
                bodyData: nil
            )
        }
        do {
            _ = try await session2.apiClient.me()
            XCTFail("Expected 429 error")
        } catch let error as MobileApiError {
            XCTAssertEqual(error.statusCode, 429)
            XCTAssertEqual(error.retryAfterSeconds, 25)
        }

        let (session3, _, _, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerRateLimited(
                method: .get,
                path: "/api/v1/me",
                retryAfterHeader: nil,
                bodyData: rateLimitedBody
            )
        }
        do {
            _ = try await session3.apiClient.me()
            XCTFail("Expected 429 error")
        } catch let error as MobileApiError {
            XCTAssertEqual(error.statusCode, 429)
            XCTAssertEqual(error.retryAfterSeconds, 60)
        }

        let noRetryBody = try ContractFixtureLoader.loadAuthFixture("login-429-rate-limited.json")
        let (session4, _, _, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerRateLimited(
                method: .get,
                path: "/api/v1/me",
                retryAfterHeader: nil,
                bodyData: noRetryBody
            )
        }
        do {
            _ = try await session4.apiClient.me()
            XCTFail("Expected 429 error")
        } catch let error as MobileApiError {
            XCTAssertEqual(error.statusCode, 429)
            XCTAssertNil(error.retryAfterSeconds)
        }
    }
}
