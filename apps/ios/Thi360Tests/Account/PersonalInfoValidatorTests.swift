import Foundation
import XCTest
@testable import Thi360

final class PersonalInfoValidatorTests: XCTestCase {
    func testFullNameBoundariesClampAndWhitespaceValidation() {
        let exact200 = String(repeating: "a", count: 200)
        let over201 = String(repeating: "a", count: 201)

        XCTAssertEqual(PersonalInfoValidator.clampFullNameInput(exact200).count, 200)
        XCTAssertEqual(PersonalInfoValidator.clampFullNameInput(over201).count, 200)

        let whitespaceResult = PersonalInfoValidator.validate(fullName: "   \t\n  ", phone: "", savedPhone: nil)
        XCTAssertEqual(whitespaceResult.fullNameError, .required)
        XCTAssertFalse(whitespaceResult.isValid)

        let tooLongResult = PersonalInfoValidator.validate(fullName: over201, phone: "", savedPhone: nil)
        XCTAssertEqual(tooLongResult.fullNameError, .tooLong)
        XCTAssertFalse(tooLongResult.isValid)

        let exactResult = PersonalInfoValidator.validate(fullName: exact200, phone: "", savedPhone: nil)
        XCTAssertNil(exactResult.fullNameError)
        XCTAssertTrue(exactResult.isValid)
    }

    func testPhoneRegexLegacyPreservationAndEmptyClear() {
        XCTAssertTrue(PersonalInfoValidator.validate(fullName: "Nguyễn An", phone: "0912345678", savedPhone: nil).isValid)
        XCTAssertEqual(
            PersonalInfoValidator.validate(fullName: "Nguyễn An", phone: "912345678", savedPhone: nil).phoneError,
            .invalid
        )
        XCTAssertEqual(
            PersonalInfoValidator.validate(fullName: "Nguyễn An", phone: "+84 912 345 678", savedPhone: nil).phoneError,
            .invalid
        )
        XCTAssertEqual(
            PersonalInfoValidator.validate(fullName: "Nguyễn An", phone: "09123456789", savedPhone: nil).phoneError,
            .invalid
        )

        XCTAssertTrue(
            PersonalInfoValidator.validate(
                fullName: "Nguyễn An Mới",
                phone: "+84 912 345 678",
                savedPhone: "+84 912 345 678"
            ).isValid
        )

        XCTAssertTrue(PersonalInfoValidator.validate(fullName: "Nguyễn An", phone: "", savedPhone: "0912345678").isValid)
        let clearPatch = PersonalInfoValidator.buildPatch(fullName: "  Nguyễn An  ", phone: "", savedPhone: "0912345678")
        XCTAssertEqual(clearPatch.fullName, "Nguyễn An")
        XCTAssertEqual(clearPatch.phone, "")

        let unchangedPatch = PersonalInfoValidator.buildPatch(
            fullName: "  Nguyễn Mới ",
            phone: "+84 912 345 678",
            savedPhone: "+84 912 345 678"
        )
        XCTAssertEqual(unchangedPatch.fullName, "Nguyễn Mới")
        XCTAssertNil(unchangedPatch.phone)
    }

    func testEmailAndVerificationCodeFormatValidation() {
        XCTAssertTrue(PersonalInfoValidator.isValidEmail("student@thi360.vn"))
        XCTAssertFalse(PersonalInfoValidator.isValidEmail("not-an-email"))
        XCTAssertFalse(PersonalInfoValidator.isValidEmail("a@b"))
        XCTAssertFalse(PersonalInfoValidator.isValidEmail("a b@thi360.vn"))

        XCTAssertTrue(PersonalInfoValidator.isValidVerificationCode("123456"))
        XCTAssertFalse(PersonalInfoValidator.isValidVerificationCode("12345"))
        XCTAssertFalse(PersonalInfoValidator.isValidVerificationCode("1234567"))
        XCTAssertFalse(PersonalInfoValidator.isValidVerificationCode("12345a"))
        XCTAssertFalse(PersonalInfoValidator.isValidVerificationCode(""))
    }
}
