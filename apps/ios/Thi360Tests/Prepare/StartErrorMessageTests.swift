import Foundation
import XCTest
@testable import Thi360

final class StartErrorMessageTests: XCTestCase {
    func testErrorClassificationCoversAllRefusalCodes429AndGenericFallbacks() {
        let cases: [(MobileApiError, StartRefusal)] = [
            (.api(statusCode: 409, error: ApiError(code: "NO_ATTEMPTS_LEFT", message: "")), .noAttemptsLeft),
            (.api(statusCode: 422, error: ApiError(code: "CHOICE_NOT_ALLOWED", message: "")), .choiceNotAllowed),
            (.api(statusCode: 409, error: ApiError(code: "NO_SERVING_OPTION", message: "")), .noServingOption),
            (.api(statusCode: 422, error: ApiError(code: "SUBJECTS_INVALID", message: "")), .subjectsInvalid),
            (.api(statusCode: 429, error: ApiError(code: "RATE_LIMITED", message: "")), .rateLimited),
            (.api(statusCode: 429, error: ApiError(code: "TOO_MANY", message: "")), .rateLimited),
            (.api(statusCode: 400, error: ApiError(code: "RATE_LIMITED", message: "")), .rateLimited),
            (.unexpectedResponse(statusCode: 429), .rateLimited),
            (.api(statusCode: 400, error: ApiError(code: "UNKNOWN_CODE", message: "")), .generic),
            (.api(statusCode: 500, error: ApiError(code: "INTERNAL_ERROR", message: "")), .generic),
            (.unexpectedResponse(statusCode: 503), .generic),
            (.network(.notConnectedToInternet), .generic)
        ]

        for (error, expected) in cases {
            XCTAssertEqual(StartErrorMessage.refusal(from: error), expected)
        }
    }

    func testLocalizedMessagesAndNoServingOptionSupportContactLine() {
        let withSupport = SupportContact(phone: "1900 1234", email: "support@thi360.vn", hours: "8h–21h")

        XCTAssertEqual(
            StartErrorMessage.display(for: .noAttemptsLeft, locale: .vi),
            StartErrorDisplay(primary: "Bạn đã dùng hết lượt của bài thi này.", supportLine: nil)
        )
        XCTAssertEqual(
            StartErrorMessage.display(for: .choiceNotAllowed, locale: .vi),
            StartErrorDisplay(primary: "Lựa chọn hiện tại không hợp lệ hoặc không thuộc phạm vi bạn đã mua.", supportLine: nil)
        )
        XCTAssertEqual(
            StartErrorMessage.display(for: .subjectsInvalid, locale: .vi),
            StartErrorDisplay(primary: "Danh sách môn đã chọn chưa đúng số lượng hoặc không hợp lệ.", supportLine: nil)
        )
        XCTAssertEqual(
            StartErrorMessage.display(for: .rateLimited, locale: .vi),
            StartErrorDisplay(primary: "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.", supportLine: nil)
        )
        XCTAssertEqual(
            StartErrorMessage.display(for: .generic, locale: .vi),
            StartErrorDisplay(primary: "Không thể bắt đầu bài thi lúc này. Vui lòng thử lại.", supportLine: nil)
        )

        let noServingWithContact = StartErrorMessage.display(
            for: .noServingOption,
            locale: .vi,
            supportContact: withSupport
        )
        XCTAssertEqual(noServingWithContact.primary, "Các phương án bạn đã mua hiện không còn được phục vụ.")
        XCTAssertEqual(
            noServingWithContact.supportLine,
            "Vui lòng liên hệ hỗ trợ qua 1900 1234 · support@thi360.vn (8h–21h) để được hoàn tiền hoặc cấp bù."
        )

        let noServingPending = StartErrorMessage.display(
            for: .noServingOption,
            locale: .vi,
            supportContact: .empty
        )
        XCTAssertEqual(noServingPending.supportLine, "Thông tin liên hệ hỗ trợ đang được cập nhật.")

        let noServingEn = StartErrorMessage.display(
            for: .noServingOption,
            locale: .en,
            supportContact: withSupport
        )
        XCTAssertEqual(noServingEn.primary, "None of your purchased options are currently available.")
        XCTAssertEqual(
            noServingEn.supportLine,
            "Please contact support via 1900 1234 · support@thi360.vn (8h–21h) for a refund or compensation."
        )
    }
}
