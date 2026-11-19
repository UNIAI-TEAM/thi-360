package vn.unicom.thi360.commerce.storefront;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.security.ClientIpResolver;

/**
 * CM-1 AC-17 and AC-18: the 120 requests a minute per IP of the public API (the policy of C-0, configured in
 * {@code application.yml}) and the IPs that are exempt from it. Each test uses addresses of its own, so no test shares a
 * counter with another. The exempt range is set here the way an environment sets it.
 */
@TestPropertySource(properties = "thi360.security.rate-limit.exempt-ips=198.51.100.0/24")
class StorefrontRateLimitIT extends AbstractIntegrationTest {

    private static final String FILTERS = "/api/v1/public/storefront/filters";

    private static String address(String prefix) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return prefix + "." + random.nextInt(1, 255);
    }

    private org.springframework.test.web.servlet.ResultActions call(String clientIp) throws Exception {
        return mockMvc.perform(get(FILTERS).header(ClientIpResolver.X_FORWARDED_FOR, clientIp));
    }

    @Test
    void ordinaryIpIsLimitedAt121() throws Exception {
        String ordinary = address("203.0.113");
        String bystander = address("192.0.2");

        for (int i = 1; i <= 120; i++) {
            call(ordinary).andExpect(status().isOk());
        }

        call(ordinary).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // The count is per IP: another address is not affected.
        call(bystander).andExpect(status().isOk());
    }

    @Test
    void exemptIpIsNotLimited() throws Exception {
        String exempt = address("198.51.100");

        for (int i = 1; i <= 130; i++) {
            call(exempt).andExpect(status().isOk());
        }
    }
}
