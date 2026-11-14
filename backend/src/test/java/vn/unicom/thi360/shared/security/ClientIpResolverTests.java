package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTests {

    private final SecurityProperties properties = new SecurityProperties(
            List.of("http://localhost:3001"),
            List.of("127.0.0.1", "::1", "172.16.0.0/12"),
            new SecurityProperties.RateLimitProperties(20, Duration.ofMinutes(1), 10, Duration.ofMinutes(1)),
            new SecurityProperties.LoginThrottleProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
            new SecurityProperties.SessionProperties(5, Duration.ofDays(30)));

    private final ClientIpResolver resolver = new ClientIpResolver(properties);

    @Test
    void ignoresForwardedForFromUntrustedPeer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.42");
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.99");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.42");
    }

    @Test
    void extractsUntrustedClientAddressWhenPeerIsTrustedLoopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void extractsUntrustedClientAddressAcrossTrustedDockerBridgeChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.25, 172.17.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.25");
    }

    @Test
    void honorsIpv6LoopbackAsTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "198.51.100.77");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.77");
    }

    @Test
    void rejectsNonIpSpoofValueInForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "evil.host.example");

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void generatesTomcatInternalProxiesRegexMatchingTrustedRanges() {
        String regex = resolver.trustedProxyMatcher().toTomcatRegex();
        assertThat("127.0.0.1").matches(regex);
        assertThat("::1").matches(regex);
        assertThat("0:0:0:0:0:0:0:1").matches(regex);
        assertThat("172.16.0.1").matches(regex);
        assertThat("172.31.255.254").matches(regex);
        assertThat("172.32.0.1").doesNotMatch(regex);
        assertThat("198.51.100.1").doesNotMatch(regex);
    }
}
