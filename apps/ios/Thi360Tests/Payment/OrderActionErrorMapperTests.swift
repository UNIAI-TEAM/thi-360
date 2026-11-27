import XCTest
@testable import Thi360

final class OrderActionErrorMapperTests: XCTestCase {
    func testFullMappingTable() {
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 429, apiCode: nil), .rateLimited)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 400, apiCode: "RATE_LIMITED"), .rateLimited)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 410, apiCode: nil), .productDiscontinued)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 409, apiCode: "PRODUCT_DISCONTINUED"), .productDiscontinued)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 404, apiCode: "PRODUCT_NOT_FOUND"), .productNotFound)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 404, apiCode: "ORDER_NOT_FOUND"), .orderNotFound)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 404, apiCode: nil), .orderNotFound)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 409, apiCode: "ORDER_EXPIRED"), .orderExpired)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 409, apiCode: "ORDER_NOT_PENDING"), .orderNotPending)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 422, apiCode: "PAYMENT_ATTEMPTS_EXCEEDED"), .paymentAttemptsExceeded)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 500, apiCode: "INTERNAL_ERROR"), .generic)
        XCTAssertEqual(OrderActionErrorMapper.map(statusCode: 409, apiCode: "PRODUCT_UNAVAILABLE"), .generic)
        XCTAssertEqual(OrderActionErrorMapper.map(.network(.notConnectedToInternet)), .generic)
    }
}
