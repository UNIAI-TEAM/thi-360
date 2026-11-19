package vn.unicom.thi360.shared.mail;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * AC-16: máy chủ SMTP không kết nối được thì {@code /actuator/health} vẫn {@code UP}, vì E2E và probe chờ
 * điều kiện này. Cổng 1 không có ai lắng nghe; nếu {@code management.health.mail.enabled} bật, chỉ báo thư
 * của Spring Boot thử kết nối và báo {@code DOWN}.
 */
@TestPropertySource(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1"})
class MailHealthIT extends AbstractIntegrationTest {

    @Test
    void healthStaysUpWhenSmtpIsUnreachable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
