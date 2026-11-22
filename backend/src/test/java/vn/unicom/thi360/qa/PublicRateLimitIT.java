package vn.unicom.thi360.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.RateLimitFilter;

/**
 * AC-10: Verifies public, auth, VNPAY callback, exempt IP, and CSRF-exemption behavior through the
 * real Spring Security filter chain and real Testcontainers Redis.
 */
@TestPropertySource(properties = "thi360.security.rate-limit.exempt-ips=198.51.100.240/28")
class PublicRateLimitIT extends AbstractIntegrationTest {

    private static final String IP_STOREFRONT_A = "203.0.113.201";
    private static final String IP_STOREFRONT_B = "203.0.113.202";
    private static final String IP_EXEMPT = "198.51.100.242";
    private static final String IP_VNPAY = "203.0.113.203";
    private static final String IP_REGISTER_A = "203.0.113.204";
    private static final String IP_REGISTER_B = "203.0.113.205";
    private static final String IP_FORGOT_A = "203.0.113.206";
    private static final String IP_FORGOT_B = "203.0.113.207";
    private static final String IP_RESET_A = "203.0.113.208";
    private static final String IP_RESET_B = "203.0.113.209";
    private static final String IP_LOGIN_A = "203.0.113.210";
    private static final String IP_LOGIN_B = "203.0.113.211";
    private static final String IP_CSRF = "203.0.113.212";
    private static final String IP_SPOOF_UNTRUSTED = "203.0.113.213";
    private static final String IP_HEAD_AUTH = "203.0.113.214";

    private static final List<String> OWNED_IPS = List.of(
            IP_STOREFRONT_A,
            IP_STOREFRONT_B,
            IP_EXEMPT,
            IP_VNPAY,
            IP_REGISTER_A,
            IP_REGISTER_B,
            IP_FORGOT_A,
            IP_FORGOT_B,
            IP_RESET_A,
            IP_RESET_B,
            IP_LOGIN_A,
            IP_LOGIN_B,
            IP_CSRF,
            IP_SPOOF_UNTRUSTED,
            IP_HEAD_AUTH);

    private static final List<String> BUCKET_PREFIXES = List.of(
            RateLimitFilter.PUBLIC_KEY_PREFIX,
            RateLimitFilter.REGISTER_KEY_PREFIX,
            RateLimitFilter.FORGOT_KEY_PREFIX,
            RateLimitFilter.RESET_KEY_PREFIX,
            RateLimitFilter.KEY_PREFIX);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CatalogLookup catalogLookup;

    @Autowired
    private PromotionRepository promotionRepository;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        clearOwnedRedisKeys();
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        try {
            clearOwnedRedisKeys();
        } finally {
            try {
                jdbc.update(
                        "DELETE FROM commerce.payment_event WHERE payment_id IS NULL AND raw ->> '_clientIp' = ?",
                        IP_VNPAY);
            } finally {
                if (fixtures != null) {
                    fixtures.cleanup();
                }
            }
        }
    }

    private void clearOwnedRedisKeys() {
        List<String> keys = new ArrayList<>();
        for (String prefix : BUCKET_PREFIXES) {
            for (String ip : OWNED_IPS) {
                keys.add(prefix + ip);
            }
        }
        redisTemplate.delete(keys);
    }

    @Test
    void storefrontPublicEndpointsAllow120PerMinutePerIpAndRespectExemptIp() throws Exception {
        var product = fixtures.insertOnSaleProduct("HSA", 150_000L, 3);
        String detailPath = "/api/v1/public/storefront/products/" + product.code();

        // Spread 120 calls across GET /products, anonymous HEAD /products (401 in SecurityConfig, still counted by RateLimitFilter),
        // GET /filters, and GET /products/{code} for ordinary IP A
        for (int i = 1; i <= 30; i++) {
            performPublicGet("/api/v1/public/storefront/products", IP_STOREFRONT_A).andExpect(status().isOk());
            performPublicHead("/api/v1/public/storefront/products", IP_STOREFRONT_A).andExpect(status().isUnauthorized());
            performPublicGet("/api/v1/public/storefront/filters", IP_STOREFRONT_A).andExpect(status().isOk());
            performPublicGet(detailPath, IP_STOREFRONT_A).andExpect(status().isOk());
        }

        // 121st GET and 122nd HEAD from IP A return 429 RATE_LIMITED with positive integer Retry-After header
        MvcResult rateLimitedGet = performPublicGet("/api/v1/public/storefront/products", IP_STOREFRONT_A)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(rateLimitedGet);

        MvcResult rateLimitedHead = performPublicHead("/api/v1/public/storefront/products", IP_STOREFRONT_A)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();
        assertRetryAfterAtLeastOne(rateLimitedHead);

        // Authenticated HEAD shares and enforces the exact 120/min bucket with GET (returning 200 up to 120, 429 at 121)
        for (int i = 1; i <= 120; i++) {
            mockMvc.perform(head("/api/v1/public/storefront/products")
                            .with(asUser())
                            .with(req -> {
                                req.setRemoteAddr(IP_HEAD_AUTH);
                                return req;
                            }))
                    .andExpect(status().isOk());
        }
        MvcResult authHeadLimited = mockMvc.perform(head("/api/v1/public/storefront/products")
                        .with(asUser())
                        .with(req -> {
                            req.setRemoteAddr(IP_HEAD_AUTH);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();
        assertRetryAfterAtLeastOne(authHeadLimited);
        MvcResult sharedGetLimited = performPublicGet("/api/v1/public/storefront/products", IP_HEAD_AUTH)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(sharedGetLimited);

        // Distinct client IP B still gets 200 across GET & authenticated HEAD
        performPublicGet("/api/v1/public/storefront/products", IP_STOREFRONT_B).andExpect(status().isOk());
        mockMvc.perform(head("/api/v1/public/storefront/products")
                        .with(asUser())
                        .with(req -> {
                            req.setRemoteAddr(IP_STOREFRONT_B);
                            return req;
                        }))
                .andExpect(status().isOk());
        performPublicGet("/api/v1/public/storefront/filters", IP_STOREFRONT_B).andExpect(status().isOk());
        performPublicGet(detailPath, IP_STOREFRONT_B).andExpect(status().isOk());

        // Exempt IP (inside 198.51.100.240/28) exceeds 120 across GET and HEAD without 429
        for (int i = 1; i <= 125; i++) {
            performPublicGet("/api/v1/public/storefront/products", IP_EXEMPT).andExpect(status().isOk());
        }
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(head("/api/v1/public/storefront/products")
                            .with(asUser())
                            .with(req -> {
                                req.setRemoteAddr(IP_EXEMPT);
                                return req;
                            }))
                    .andExpect(status().isOk());
        }
        assertThat(redisTemplate.hasKey(RateLimitFilter.PUBLIC_KEY_PREFIX + IP_EXEMPT)).isFalse();

        // Untrusted remoteAddr spoofing X-Forwarded-For with IP_EXEMPT still hits 429 at 121
        for (int i = 1; i <= 120; i++) {
            mockMvc.perform(get("/api/v1/public/storefront/products")
                            .header("X-Forwarded-For", IP_EXEMPT)
                            .with(req -> {
                                req.setRemoteAddr(IP_SPOOF_UNTRUSTED);
                                return req;
                            }))
                    .andExpect(status().isOk());
        }
        MvcResult spoofedRateLimited = mockMvc.perform(get("/api/v1/public/storefront/products")
                        .header("X-Forwarded-For", IP_EXEMPT)
                        .with(req -> {
                            req.setRemoteAddr(IP_SPOOF_UNTRUSTED);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(spoofedRateLimited);
        assertThat(redisTemplate.hasKey(RateLimitFilter.PUBLIC_KEY_PREFIX + IP_EXEMPT)).isFalse();
    }

    @Test
    void vnpayIpnIsNeverRateLimitedWhileVnpayReturnIsLimitedAt121() throws Exception {
        for (int i = 1; i <= 135; i++) {
            mockMvc.perform(get("/api/v1/public/payments/vnpay/ipn")
                            .with(req -> {
                                req.setRemoteAddr(IP_VNPAY);
                                return req;
                            }))
                    .andExpect(status().isOk());
        }
        assertThat(redisTemplate.hasKey(RateLimitFilter.PUBLIC_KEY_PREFIX + IP_VNPAY)).isFalse();

        for (int i = 1; i <= 120; i++) {
            MvcResult res = mockMvc.perform(get("/api/v1/public/payments/vnpay/return")
                            .with(req -> {
                                req.setRemoteAddr(IP_VNPAY);
                                return req;
                            }))
                    .andReturn();
            assertThat(res.getResponse().getStatus()).as("vnpay return #%d", i).isNotEqualTo(429);
        }

        MvcResult returnLimited = mockMvc.perform(get("/api/v1/public/payments/vnpay/return")
                        .with(req -> {
                            req.setRemoteAddr(IP_VNPAY);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(returnLimited);
    }

    @Test
    void registerEnforces30PerHourPerIpLimit() throws Exception {
        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .with(req -> {
                                req.setRemoteAddr(IP_REGISTER_A);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        MvcResult regLimited = mockMvc.perform(post("/api/v1/auth/register")
                        .with(req -> {
                            req.setRemoteAddr(IP_REGISTER_A);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(regLimited);

        // Distinct IP is unaffected
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(req -> {
                            req.setRemoteAddr(IP_REGISTER_B);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotAndResetPasswordEnforce60PerMinutePerIpLimits() throws Exception {
        for (int i = 1; i <= 60; i++) {
            mockMvc.perform(post("/api/v1/auth/password/forgot")
                            .with(req -> {
                                req.setRemoteAddr(IP_FORGOT_A);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@thi360-test.example\"}"))
                    .andExpect(status().isAccepted());
        }

        MvcResult forgotLimited = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .with(req -> {
                            req.setRemoteAddr(IP_FORGOT_A);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@thi360-test.example\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(forgotLimited);

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .with(req -> {
                            req.setRemoteAddr(IP_FORGOT_B);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@thi360-test.example\"}"))
                .andExpect(status().isAccepted());

        for (int i = 1; i <= 60; i++) {
            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .with(req -> {
                                req.setRemoteAddr(IP_RESET_A);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"no-such-token\",\"newPassword\":\"Valid@1234\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        MvcResult resetLimited = mockMvc.perform(post("/api/v1/auth/password/reset")
                        .with(req -> {
                            req.setRemoteAddr(IP_RESET_A);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"no-such-token\",\"newPassword\":\"Valid@1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(resetLimited);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .with(req -> {
                            req.setRemoteAddr(IP_RESET_B);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"no-such-token\",\"newPassword\":\"Valid@1234\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginEnforces20PerMinutePerIpLimit() throws Exception {
        for (int i = 1; i <= 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(IP_LOGIN_A);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        MvcResult loginLimited = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(IP_LOGIN_A);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(loginLimited);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(IP_LOGIN_B);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // IP_EXEMPT is ONLY exempt on public GET/HEAD storefront routes, NEVER on /api/v1/auth/login
        for (int i = 1; i <= 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(IP_EXEMPT);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        MvcResult exemptLoginLimited = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(IP_EXEMPT);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        assertRetryAfterAtLeastOne(exemptLoginLimited);
    }

    @Test
    void publicPostsWorkWithoutCookiesAndWithStaleCookieAndNoXsrfToken() throws Exception {
        Cookie staleAccess = new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "stale.access.jwt");
        Cookie staleRefresh = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "stale.refresh.token");

        // Without cookies
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@thi360-test.example\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"no-such-token\",\"newPassword\":\"Valid@1234\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // With stale session cookies and NO X-XSRF-TOKEN header: never rejected with 403 CSRF_INVALID
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .cookie(staleAccess, staleRefresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .cookie(staleAccess, staleRefresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@thi360-test.example\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .cookie(staleAccess, staleRefresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"no-such-token\",\"newPassword\":\"Valid@1234\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .cookie(staleAccess, staleRefresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Protected POST with stale session cookie (refresh_token triggers CsrfFilter without invalid-JWT 401 first)
        // and no XSRF token IS rejected with 403 CSRF_INVALID
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(req -> {
                            req.setRemoteAddr(IP_CSRF);
                            return req;
                        })
                        .cookie(staleRefresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private ResultActions performPublicGet(String path, String remoteIp) throws Exception {
        return mockMvc.perform(get(path).with(req -> {
            req.setRemoteAddr(remoteIp);
            return req;
        }));
    }

    private ResultActions performPublicHead(String path, String remoteIp) throws Exception {
        return mockMvc.perform(head(path).with(req -> {
            req.setRemoteAddr(remoteIp);
            return req;
        }));
    }

    private static void assertRetryAfterAtLeastOne(MvcResult result) {
        String header = result.getResponse().getHeader("Retry-After");
        assertThat(header).as("Retry-After header").isNotBlank();
        long seconds = Long.parseLong(header.trim());
        assertThat(seconds).isGreaterThanOrEqualTo(1L);
    }
}
