package vn.unicom.thi360.feature.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {

    @Test
    fun lengthBoundaries7And8And64And65MatchPolicy() {
        val len7 = "Aa1!aaa"
        val len8 = "Aa1!aaaa"
        val len64 = "Aa1!" + "a".repeat(60)
        val len65 = "Aa1!" + "a".repeat(61)

        assertEquals(7, len7.length)
        assertFalse(PasswordRules.evaluate(len7).hasValidLength)
        assertFalse(PasswordRules.isStrong(len7))
        assertEquals(PasswordValidationError.WEAK, PasswordRules.validate(len7))

        assertEquals(8, len8.length)
        assertTrue(PasswordRules.evaluate(len8).hasValidLength)
        assertTrue(PasswordRules.isStrong(len8))
        assertNull(PasswordRules.validate(len8))

        assertEquals(64, len64.length)
        assertTrue(PasswordRules.evaluate(len64).hasValidLength)
        assertTrue(PasswordRules.isStrong(len64))
        assertNull(PasswordRules.validate(len64))

        assertEquals(65, len65.length)
        assertFalse(PasswordRules.evaluate(len65).hasValidLength)
        assertFalse(PasswordRules.isStrong(len65))
        assertEquals(PasswordValidationError.WEAK, PasswordRules.validate(len65))
    }

    @Test
    fun evaluatesEachOfTheFiveConditionsIndependently() {
        val missingUpper = PasswordRules.evaluate("matkhau1!")
        assertTrue(missingUpper.hasValidLength)
        assertFalse(missingUpper.hasUppercase)
        assertTrue(missingUpper.hasLowercase)
        assertTrue(missingUpper.hasDigit)
        assertTrue(missingUpper.hasSpecial)
        assertFalse(missingUpper.allSatisfied)

        val missingLower = PasswordRules.evaluate("MATKHAU1!")
        assertFalse(missingLower.hasLowercase)
        assertFalse(missingLower.allSatisfied)

        val missingDigit = PasswordRules.evaluate("Matkhau!!")
        assertFalse(missingDigit.hasDigit)
        assertFalse(missingDigit.allSatisfied)

        val missingSpecial = PasswordRules.evaluate("Matkhau12")
        assertFalse(missingSpecial.hasSpecial)
        assertFalse(missingSpecial.allSatisfied)

        val whitespaceNotSpecial = PasswordRules.evaluate("Matkhau 1")
        assertFalse("Whitespace must not satisfy special-character rule", whitespaceNotSpecial.hasSpecial)
        assertFalse(whitespaceNotSpecial.allSatisfied)

        val allValid = PasswordRules.evaluate("Matkhau1!")
        assertTrue(allValid.allSatisfied)
    }

    @Test
    fun rejectsEmptyAndPasswordMatchingUsernameCaseInsensitively() {
        assertEquals(PasswordValidationError.REQUIRED, PasswordRules.validate("", "User1!aa"))
        assertEquals(
            PasswordValidationError.SAME_AS_USERNAME,
            PasswordRules.validate("MinhAnh1@", "  minhanh1@  "),
        )
        assertNull(PasswordRules.validate("MinhAnh1@", "minhanh.tran"))
    }
}
