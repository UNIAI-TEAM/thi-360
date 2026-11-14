package vn.unicom.thi360.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.unicom.thi360.shared.api.ValidationException;

class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void validPassword_passes() {
        assertThatCode(() -> policy.validate("password", "Valid@123", "student01"))
                .doesNotThrowAnyException();
        assertThat(policy.isValid("Valid@123", "student01")).isTrue();
    }

    @Test
    void missingUppercase_throwsPasswordWeak() {
        assertFailsWithFieldCode("abcd1234!", "student01", "PASSWORD_UPPERCASE");
    }

    @Test
    void missingLowercase_throwsPasswordWeak() {
        assertFailsWithFieldCode("ABCD1234!", "student01", "PASSWORD_LOWERCASE");
    }

    @Test
    void missingDigit_throwsPasswordWeak() {
        assertFailsWithFieldCode("Abcdefgh!", "student01", "PASSWORD_DIGIT");
    }

    @Test
    void missingSpecial_throwsPasswordWeak() {
        assertFailsWithFieldCode("Abcd1234", "student01", "PASSWORD_SPECIAL");
    }

    @Test
    void shorterThan8_throwsPasswordWeak() {
        assertFailsWithFieldCode("Ab1!xyz", "student01", "PASSWORD_LENGTH");
    }

    @Test
    void longerThan64_throwsPasswordWeak() {
        assertFailsWithFieldCode("Ab1!" + "a".repeat(61), "student01", "PASSWORD_LENGTH");
    }

    @Test
    void sameAsUsernameCaseInsensitive_throwsPasswordWeak() {
        assertFailsWithFieldCode("User@1234", "user@1234", "PASSWORD_SAME_AS_USERNAME");
    }

    @Test
    void nullOrEmptyPassword_throwsPasswordWeak() {
        assertThatThrownBy(() -> policy.validate("password", null, "student01"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("PASSWORD_WEAK"));
        assertThatThrownBy(() -> policy.validate("password", "", "student01"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("PASSWORD_WEAK"));
    }

    private void assertFailsWithFieldCode(String password, String username, String expectedFieldCode) {
        assertThatThrownBy(() -> policy.validate("password", password, username))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getCode()).isEqualTo("PASSWORD_WEAK");
                    assertThat(ve.getFieldErrors())
                            .anyMatch(fe -> fe.field().equals("password") && fe.code().equals(expectedFieldCode));
                });
    }
}
