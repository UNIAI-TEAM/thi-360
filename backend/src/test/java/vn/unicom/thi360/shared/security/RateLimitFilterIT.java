package vn.unicom.thi360.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.AbstractIntegrationTest;

class RateLimitFilterIT extends AbstractIntegrationTest {

    @Test
    void ignoresSpoofedXffFromUntrustedPeer_andTripsAt21stAttemptOnRemoteAddr() throws Exception {
        String untrustedPeer = "198.51.100." + (10 + Math.abs(UUID.randomUUID().hashCode()) % 200);
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
        String clientA = "203.0.113." + (10 + Math.abs(UUID.randomUUID().hashCode()) % 100);
        String clientB = "203.0.113." + (120 + Math.abs(UUID.randomUUID().hashCode()) % 100);

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
