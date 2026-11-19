package vn.unicom.thi360.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/** AC-16 (phần băm): HMAC-SHA256 có khóa, không chứa mã rõ. */
class EmailCodeHasherTests {

    private final EmailCodeHasher hasher = new EmailCodeHasher(new TokenPepperProperties("dev-only-pepper-one"));
    private final UUID user = UUID.randomUUID();

    @Test
    void producesHexHmacThatDoesNotContainTheCode() {
        String hash = hasher.hash(user, EmailCodePurpose.VERIFY, "123456");
        assertThat(hash).matches("[0-9a-f]{64}").doesNotContain("123456");
        assertThat(hasher.hash(user, EmailCodePurpose.VERIFY, "123456")).isEqualTo(hash);
    }

    @Test
    void dependsOnPepperUserAndPurpose() {
        String hash = hasher.hash(user, EmailCodePurpose.VERIFY, "123456");
        EmailCodeHasher otherPepper = new EmailCodeHasher(new TokenPepperProperties("dev-only-pepper-two"));
        assertThat(otherPepper.hash(user, EmailCodePurpose.VERIFY, "123456")).isNotEqualTo(hash);
        assertThat(hasher.hash(UUID.randomUUID(), EmailCodePurpose.VERIFY, "123456")).isNotEqualTo(hash);
        assertThat(hasher.hash(user, EmailCodePurpose.CHANGE, "123456")).isNotEqualTo(hash);
    }

    @Test
    void matchesOnlyTheSameCode() {
        String hash = hasher.hash(user, EmailCodePurpose.CHANGE, "654321");
        assertThat(hasher.matches(user, EmailCodePurpose.CHANGE, "654321", hash)).isTrue();
        assertThat(hasher.matches(user, EmailCodePurpose.CHANGE, "654322", hash)).isFalse();
        assertThat(hasher.matches(user, EmailCodePurpose.CHANGE, null, hash)).isFalse();
    }
}
