package vn.unicom.thi360.feature.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalInfoValidatorTest {

    @Test
    fun fullNameBoundary200Allowed201RejectedAndBlankRejected() {
        assertEquals(
            FullNameValidationError.REQUIRED,
            PersonalInfoValidator.validate("", "", null).fullNameError,
        )
        assertEquals(
            FullNameValidationError.REQUIRED,
            PersonalInfoValidator.validate("   \t ", "", null).fullNameError,
        )

        val exactly200 = "A".repeat(200)
        assertNull(PersonalInfoValidator.validate(exactly200, "", null).fullNameError)
        assertEquals(200, PersonalInfoValidator.clampFullNameInput(exactly200).length)

        val chars201 = "A".repeat(201)
        assertEquals(
            FullNameValidationError.TOO_LONG,
            PersonalInfoValidator.validate(chars201, "", null).fullNameError,
        )
        assertEquals(200, PersonalInfoValidator.clampFullNameInput(chars201).length)
    }

    @Test
    fun phoneBoundaryMatchesOnlyTenDigitsStartingWithZeroOrBlank() {
        assertNull(PersonalInfoValidator.validate("Nguyễn Văn An", "", null).phoneError)
        assertNull(PersonalInfoValidator.validate("Nguyễn Văn An", "   ", null).phoneError)
        assertNull(PersonalInfoValidator.validate("Nguyễn Văn An", "0912345678", null).phoneError)
        assertNull(PersonalInfoValidator.validate("Nguyễn Văn An", " 0912345678 ", null).phoneError)

        assertEquals(
            PhoneValidationError.INVALID,
            PersonalInfoValidator.validate("Nguyễn Văn An", "912345678", null).phoneError,
        )
        assertEquals(
            PhoneValidationError.INVALID,
            PersonalInfoValidator.validate("Nguyễn Văn An", "091234567", null).phoneError,
        )
        assertEquals(
            PhoneValidationError.INVALID,
            PersonalInfoValidator.validate("Nguyễn Văn An", "09123456789", null).phoneError,
        )
        assertEquals(
            PhoneValidationError.INVALID,
            PersonalInfoValidator.validate("Nguyễn Văn An", "+84 912 345 678", null).phoneError,
        )
        assertEquals(
            PhoneValidationError.INVALID,
            PersonalInfoValidator.validate("Nguyễn Văn An", "091234567a", null).phoneError,
        )

        assertNull(PersonalInfoValidator.validate("Nguyễn Văn An", "+84 912 345 678", "+84 912 345 678").phoneError)
        assertEquals(
            PhoneValidationError.INVALID,
            PersonalInfoValidator.validate("Nguyễn Văn An", "+84 912 345 679", "+84 912 345 678").phoneError,
        )

        assertNull(PersonalInfoValidator.buildPatch("Nguyễn Văn An", "+84 912 345 678", "+84 912 345 678").phone)
        assertEquals("", PersonalInfoValidator.buildPatch("Nguyễn Văn An", "", "0912345678").phone)
        assertEquals("0987654321", PersonalInfoValidator.buildPatch("Nguyễn Văn An", "0987654321", "0912345678").phone)
    }

    @Test
    fun emailAndSixDigitCodeValidation() {
        assertFalse(PersonalInfoValidator.isValidEmail(""))
        assertFalse(PersonalInfoValidator.isValidEmail("   "))
        assertFalse(PersonalInfoValidator.isValidEmail("not-an-email"))
        assertFalse(PersonalInfoValidator.isValidEmail("user@domain"))
        assertTrue(PersonalInfoValidator.isValidEmail("student.new@thi360.vn"))

        assertFalse(PersonalInfoValidator.isValidVerificationCode(""))
        assertFalse(PersonalInfoValidator.isValidVerificationCode("12345"))
        assertFalse(PersonalInfoValidator.isValidVerificationCode("1234567"))
        assertFalse(PersonalInfoValidator.isValidVerificationCode("12ab56"))
        assertTrue(PersonalInfoValidator.isValidVerificationCode("012345"))
        assertTrue(PersonalInfoValidator.isValidVerificationCode("654321"))
    }

    @Test
    fun avatarInitialsFollowVietnameseNameRule() {
        assertEquals("VA", formatAvatarInitials("Nguyễn Văn An", "student.demo"))
        assertEquals("MA", formatAvatarInitials("Minh Anh", "student.demo"))
        assertEquals("AN", formatAvatarInitials("An", "student.demo"))
        assertEquals("ST", formatAvatarInitials("   ", "student.demo"))
        assertEquals("T3", formatAvatarInitials("", ""))
    }
}
