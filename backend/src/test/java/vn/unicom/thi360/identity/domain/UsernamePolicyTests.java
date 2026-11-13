package vn.unicom.thi360.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.unicom.thi360.shared.api.ValidationException;

class UsernamePolicyTests {

    private final UsernamePolicy policy = new UsernamePolicy((IdentityProperties) null);

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void blankUsername_isRejectedAsRequired(String raw) {
        UsernamePolicy.CheckResult result = policy.check(raw);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(UsernamePolicy.CODE_REQUIRED);
        assertThatThrownBy(() -> policy.validate("username", raw))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getFieldErrors())
                        .anyMatch(fe -> fe.field().equals("username")
                                && fe.code().equals(UsernamePolicy.CODE_REQUIRED)));
    }

    @Test
    void nullUsername_isRejectedAsRequired() {
        UsernamePolicy.CheckResult result = policy.check(null);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(UsernamePolicy.CODE_REQUIRED);
    }

    @Test
    void lengthBoundaries_4to30_areEnforced() {
        assertThat(policy.check("abc").valid()).isFalse();
        assertThat(policy.check("abcd").valid()).isTrue();
        assertThat(policy.check("a".repeat(30)).valid()).isTrue();
        assertThat(policy.check("a".repeat(31)).valid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user.name_1-2@ok", "a123", "9_user-name.test"})
    void validCharacters_areAccepted(String candidate) {
        UsernamePolicy.CheckResult result = policy.check(candidate);
        assertThat(result.valid()).isTrue();
        assertThat(result.normalized()).isEqualTo(candidate.toLowerCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nguyễn.an", "trần_bình", "user$123", "user!123", "user/123", "user+123"})
    void nonAsciiOrDisallowedSymbols_areRejected(String candidate) {
        UsernamePolicy.CheckResult result = policy.check(candidate);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(UsernamePolicy.CODE_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {".user", "_user", "-user", "@user"})
    void leadingNonAlphanumeric_isRejected(String candidate) {
        UsernamePolicy.CheckResult result = policy.check(candidate);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(UsernamePolicy.CODE_INVALID);
    }

    @Test
    void leadingDigit_isAccepted() {
        UsernamePolicy.CheckResult result = policy.check("1student");
        assertThat(result.valid()).isTrue();
        assertThat(result.normalized()).isEqualTo("1student");
    }

    @Test
    void outerWhitespaceIsTrimmed_innerWhitespaceIsRejected() {
        UsernamePolicy.CheckResult trimmedValid = policy.check("  User.Ok_01  ");
        assertThat(trimmedValid.valid()).isTrue();
        assertThat(trimmedValid.trimmed()).isEqualTo("User.Ok_01");
        assertThat(trimmedValid.normalized()).isEqualTo("user.ok_01");

        assertThat(policy.check("bad user").valid()).isFalse();
        assertThat(policy.check("bad\tuser").valid()).isFalse();
    }

    @Test
    void caseInsensitiveNormalization_producesLowercaseNormalized() {
        UsernamePolicy.CheckResult result = policy.check("Nguyen.Van.An");
        assertThat(result.valid()).isTrue();
        assertThat(result.trimmed()).isEqualTo("Nguyen.Van.An");
        assertThat(result.normalized()).isEqualTo("nguyen.van.an");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "admin", "ADMIN", "  Root ", "administrator", "system", "support", "platform", "thi360", "null", "undefined"
    })
    void reservedUsernames_areRejectedCaseInsensitively(String candidate) {
        UsernamePolicy.CheckResult result = policy.check(candidate);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(UsernamePolicy.CODE_RESERVED);
    }

    @Test
    void customReservedList_isConfigurable() {
        UsernamePolicy custom = new UsernamePolicy(List.of("CustomAdmin", "moderator"));
        assertThat(custom.check("customadmin").reasonCode()).isEqualTo(UsernamePolicy.CODE_RESERVED);
        assertThat(custom.check("MODERATOR").reasonCode()).isEqualTo(UsernamePolicy.CODE_RESERVED);
        assertThat(custom.check("student01").valid()).isTrue();
    }
}
