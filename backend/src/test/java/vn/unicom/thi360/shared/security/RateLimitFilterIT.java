package vn.unicom.thi360.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import vn.unicom.thi360.AbstractIntegrationTest;

class RateLimitFilterIT extends AbstractIntegrationTest {

    private static final String UNTRUSTED_PEER = "198.51.100.10";
    private static final String CLIENT_A = "203.0.113.10";
    private static final String CLIENT_B = "203.0.113.11";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBuckets() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + UNTRUSTED_PEER,
                RateLimitFilter.KEY_PREFIX + CLIENT_A,
                RateLimitFilter.KEY_PREFIX + CLIENT_B));
    }

    @Test
    void ignoresSpoofedXffFromUntrustedPeer_andTripsAt21stAttemptOnRemoteAddr() throws Exception {
        String untrustedPeer = UNTRUSTED_PEER;
        for (int i = 1; i <= 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(untrustedPeer);
                                return req;
                            })
                            .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(untrustedPeer);
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.250")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void usesUntrustedClientAddressFromTrustedPeer_andIsolatesDifferentClients() throws Exception {
        String clientA = CLIENT_A;
        String clientB = CLIENT_B;
        for (int i = 1; i <= 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr("127.0.0.1");
                                return req;
                            })
                            .header(ClientIpResolver.X_FORWARDED_FOR, clientA + ", 172.18.0.2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, clientA + ", 172.18.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, clientB + ", 172.18.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }
}
