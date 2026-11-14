package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtxCookieSignerTests {

    private final Instant now = Instant.parse("2026-11-13T12:00:00Z");
    private final CtxProperties props = new CtxProperties("ctx-test-secret-at-least-32-bytes!!", Duration.ofMinutes(5));
    private final CtxCookieSigner signer = new CtxCookieSigner(props, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void signsAndVerifiesValidCtxCookie() {
        UUID userId = UUID.randomUUID();
        String signed = signer.sign(userId, Realm.APP, Access.MANAGE, 3L);

        var verified = signer.verify(signed);
        assertThat(verified).isPresent();
        assertThat(verified.get().userId()).isEqualTo(userId);
        assertThat(verified.get().realm()).isEqualTo(Realm.APP);
        assertThat(verified.get().access()).isEqualTo(Access.MANAGE);
        assertThat(verified.get().version()).isEqualTo(3L);
        assertThat(verified.get().expiresAt()).isEqualTo(now.plusSeconds(300));
    }

    @Test
    void failsVerificationWhenAnyCharacterIsTampered() {
        UUID userId = UUID.randomUUID();
        String signed = signer.sign(userId, Realm.APP, Access.EXAM, 1L);

        for (int i = 0; i < signed.length(); i++) {
            char c = signed.charAt(i);
            if (c == '.') {
                continue;
            }
            char replacement = (c == 'A') ? 'B' : 'A';
            String tampered = signed.substring(0, i) + replacement + signed.substring(i + 1);
            assertThat(signer.verify(tampered))
                    .as("Tampering index %d must fail verification", i)
                    .isEmpty();
        }
    }

    @Test
    void failsVerificationWhenExpired() {
        UUID userId = UUID.randomUUID();
        String expired = signer.sign(userId, Realm.MASTER, Access.MANAGE, 0L, now.minusSeconds(1));

        assertThat(signer.verify(expired)).isEmpty();
    }
}
