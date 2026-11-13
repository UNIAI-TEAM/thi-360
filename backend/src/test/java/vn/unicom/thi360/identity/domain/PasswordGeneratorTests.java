package vn.unicom.thi360.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordGeneratorTests {

    private static final String AMBIGUOUS = "0Oo1lI";

    private final PasswordGenerator generator = new PasswordGenerator();
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void generatedPasswords_haveLength12_excludeAmbiguousChars_andPassPasswordPolicy() {
        for (int i = 0; i < 100; i++) {
            String pwd = generator.generate("student01");
            assertThat(pwd).hasSize(PasswordGenerator.LENGTH);
            for (int c = 0; c < pwd.length(); c++) {
                char ch = pwd.charAt(c);
                assertThat(AMBIGUOUS.indexOf(ch)).isEqualTo(-1);
                if (!Character.isLetterOrDigit(ch)) {
                    assertThat(PasswordGenerator.SPECIAL.indexOf(ch)).isGreaterThanOrEqualTo(0);
                }
            }
            assertThat(policy.isValid(pwd, "student01")).isTrue();
        }
    }
}
