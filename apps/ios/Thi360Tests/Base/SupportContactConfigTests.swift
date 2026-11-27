import Foundation
import XCTest
@testable import Thi360

final class SupportContactConfigTests: XCTestCase {
    func testSupportContactParsingAndPendingCopyFallback() throws {
        let emptyContact = SupportContact(phone: "  ", email: "", hours: "\n")
        XCTAssertTrue(emptyContact.isEmpty)
        XCTAssertFalse(emptyContact.hasAnyChannel)
        XCTAssertEqual(emptyContact.phone, "")
        XCTAssertEqual(emptyContact.email, "")
        XCTAssertEqual(emptyContact.hours, "")

        let populated = SupportContact(phone: " 1900 1234 ", email: " support@thi360.vn ", hours: " 08:00 - 21:00 ")
        XCTAssertFalse(populated.isEmpty)
        XCTAssertTrue(populated.hasAnyChannel)
        XCTAssertEqual(populated.phone, "1900 1234")
        XCTAssertEqual(populated.email, "support@thi360.vn")
        XCTAssertEqual(populated.hours, "08:00 - 21:00")

        let envEmpty = try AppEnvironment.load(from: [
            "THI360_ENV": "dev",
            "THI360_API_BASE_URL": "http://127.0.0.1:8080",
            "THI360_WEB_BASE_URL": "http://127.0.0.1:3000",
            "THI360_SUPPORT_PHONE": "",
            "THI360_SUPPORT_EMAIL": "",
            "THI360_SUPPORT_HOURS": ""
        ])
        XCTAssertTrue(envEmpty.supportContact.isEmpty)

        let envWithContact = try AppEnvironment.load(from: [
            "THI360_ENV": "demo",
            "THI360_API_BASE_URL": "https://api-demo.thi360.vn",
            "THI360_WEB_BASE_URL": "https://demo.thi360.vn",
            "THI360_SUPPORT_PHONE": "0901234567",
            "THI360_SUPPORT_EMAIL": "hotro@thi360.vn",
            "THI360_SUPPORT_HOURS": "8:00 - 20:00"
        ])
        XCTAssertEqual(envWithContact.supportContact.phone, "0901234567")
        XCTAssertEqual(envWithContact.supportContact.email, "hotro@thi360.vn")
        XCTAssertEqual(envWithContact.supportContact.hours, "8:00 - 20:00")

        let info = Bundle.main.infoDictionary ?? [:]
        XCTAssertNotNil(info["THI360_SUPPORT_PHONE"])
        XCTAssertNotNil(info["THI360_SUPPORT_EMAIL"])
        XCTAssertNotNil(info["THI360_SUPPORT_HOURS"])

        if AppEnvironment.current.supportContact.isEmpty {
            XCTAssertEqual(
                AppEnvironment.current.supportContact.pendingMessage(locale: .vi),
                "Thông tin liên hệ đang được cập nhật."
            )
            XCTAssertEqual(
                AppEnvironment.current.supportContact.pendingMessage(locale: .en),
                "Contact information is being updated."
            )
        }
    }
}
