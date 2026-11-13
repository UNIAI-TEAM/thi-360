package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import vn.unicom.thi360.AbstractIntegrationTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrustedProxyTomcatIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void remoteIpValveAndRateLimitFilterAgreeOnRealTomcatPort() {
        String clientA = "198.51.100." + (10 + Math.abs(UUID.randomUUID().hashCode()) % 100);
        String clientB = "198.51.100." + (120 + Math.abs(UUID.randomUUID().hashCode()) % 100);

        for (int i = 1; i <= 20; i++) {
            ResponseEntity<String> res = postLogin(clientA + ", 172.18.0.5");
            assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        ResponseEntity<String> blocked = postLogin(clientA + ", 172.18.0.5");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody()).contains("\"RATE_LIMITED\"");

        ResponseEntity<String> otherClient = postLogin(clientB + ", 172.18.0.5");
        assertThat(otherClient.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<String> postLogin(String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.put(ClientIpResolver.X_FORWARDED_FOR, List.of(forwardedFor));
        return restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>("{}", headers),
                String.class);
    }
}
