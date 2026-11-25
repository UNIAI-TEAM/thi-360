import XCTest
@testable import Thi360

final class PasswordRulesTests: XCTestCase {
    func testBoundaryLengths7_8_64_65() {
        let len7 = "Aa1!xxx"
        XCTAssertEqual(len7.count, 7)
        XCTAssertFalse(PasswordRules.evaluate(len7).hasValidLength)
        XCTAssertFalse(PasswordRules.isStrong(len7))

        let len8 = "Aa1!xxxx"
        XCTAssertEqual(len8.count, 8)
        XCTAssertTrue(PasswordRules.evaluate(len8).hasValidLength)
        XCTAssertTrue(PasswordRules.isStrong(len8))

        let len64 = "Aa1!" + String(repeating: "x", count: 60)
        XCTAssertEqual(len64.count, 64)
        XCTAssertTrue(PasswordRules.evaluate(len64).hasValidLength)
        XCTAssertTrue(PasswordRules.isStrong(len64))

        let len65 = "Aa1!" + String(repeating: "x", count: 61)
        XCTAssertEqual(len65.count, 65)
        XCTAssertFalse(PasswordRules.evaluate(len65).hasValidLength)
        XCTAssertFalse(PasswordRules.isStrong(len65))
    }

    func testEachIndividualCharacterCategoryRequirement() {
        let missingUpper = PasswordRules.evaluate("lower123!")
        XCTAssertTrue(missingUpper.hasValidLength)
        XCTAssertFalse(missingUpper.hasUppercase)
        XCTAssertTrue(missingUpper.hasLowercase)
        XCTAssertTrue(missingUpper.hasDigit)
        XCTAssertTrue(missingUpper.hasSpecial)
        XCTAssertFalse(missingUpper.isStrong)

        let missingLower = PasswordRules.evaluate("UPPER123!")
        XCTAssertTrue(missingLower.hasUppercase)
        XCTAssertFalse(missingLower.hasLowercase)
        XCTAssertFalse(missingLower.isStrong)

        let missingDigit = PasswordRules.evaluate("NoDigits!!")
        XCTAssertFalse(missingDigit.hasDigit)
        XCTAssertFalse(missingDigit.isStrong)

        let missingSpecial = PasswordRules.evaluate("NoSpecial1")
        XCTAssertFalse(missingSpecial.hasSpecial)
        XCTAssertFalse(missingSpecial.isStrong)

        let spaceNotSpecial = PasswordRules.evaluate("Has Space1")
        XCTAssertFalse(spaceNotSpecial.hasSpecial, "Whitespace must not count as a special character")
        XCTAssertFalse(spaceNotSpecial.isStrong)

        let unicodeVietnamese = PasswordRules.evaluate("Ânh1234!")
        XCTAssertTrue(unicodeVietnamese.hasUppercase)
        XCTAssertTrue(unicodeVietnamese.hasLowercase)
        XCTAssertTrue(unicodeVietnamese.hasDigit)
        XCTAssertTrue(unicodeVietnamese.hasSpecial)
        XCTAssertTrue(unicodeVietnamese.isStrong)
    }

    func testMatchesUsernameIsCaseInsensitiveAndIgnoresEmptyUsername() {
        XCTAssertTrue(PasswordRules.matchesUsername("Student.1!", username: "student.1!"))
        XCTAssertTrue(PasswordRules.matchesUsername("Student.1!", username: "  STUDENT.1!  "))
        XCTAssertFalse(PasswordRules.matchesUsername("Different1!", username: "student.1!"))
        XCTAssertFalse(PasswordRules.matchesUsername("Student.1!", username: nil))
        XCTAssertFalse(PasswordRules.matchesUsername("Student.1!", username: "   "))
    }
}
