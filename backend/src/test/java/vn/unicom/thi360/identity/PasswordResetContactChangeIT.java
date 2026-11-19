package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.ResultActions;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.PasswordResetTestSupport.Account;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/**
 * R1-3 (Advisor): đặt lại mật khẩu của ID-2 và đổi email của ID-1 chạy cùng nhau trên một đầu nhánh. Liên kết đặt lại
 * gắn với địa chỉ nhận và lần xác minh của địa chỉ đó lúc phát hành; đổi email qua đúng endpoint của ID-1 (không sửa
 * SQL) làm liên kết cũ hết quyền, kể cả khi địa chỉ cũ được đổi ngược về và xác minh lại.
 */
@Import(ControlledClockConfig.class)
class PasswordResetContactChangeIT extends AbstractKeycloakIntegrationTest {

    private static final String IP = "203.0.113.190";
    private static final String NEW_PASSWORD = "Brand#New2026";
    private static final String RESET = "/api/v1/auth/password/reset";

    @Autowired
    private KeycloakIdentityClient keycloakClient;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private TokenPepperProperties pepper;
    @Autowired
    @Qualifier("passwordResetExecutor")
    private ThreadPoolTaskExecutor executor;
    @Autowired
    private ControlledClock clock;

    private PasswordResetTestSupport support;

    @BeforeEach
    void setUp() {
        support = new PasswordResetTestSupport(keycloakClient, userAccountRepository, jdbc, redis, pepper.pepper());
        clearBuckets();
    }

    @AfterEach
    void cleanup() {
        clock.release();
        support.cleanup();
        clearBuckets();
    }

    private void clearBuckets() {
        redis.delete(List.of(
                RateLimitFilter.KEY_PREFIX + IP,
                RateLimitFilter.FORGOT_KEY_PREFIX + IP,
                RateLimitFilter.RESET_KEY_PREFIX + IP));
    }

    /** Gọi {@code forgot} và đợi bộ thực thi nền xử lý xong yêu cầu đó. */
    private void forgot(String email) throws Exception {
        long before = executor.getThreadPoolExecutor().getCompletedTaskCount();
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(executor.getThreadPoolExecutor().getCompletedTaskCount()).isGreaterThan(before));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post(RESET)
                .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private ResultActions login(Account account, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + account.username() + "\",\"password\":\"" + password + "\"}"));
    }

    private void requestEmailChange(Account account, String newEmail) throws Exception {
        mockMvc.perform(post("/api/v1/me/email/change")
                        .with(asUser(account.id(), account.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"" + account.password() + "\"}"))
                .andExpect(status().isAccepted());
    }

    private void confirmEmailChange(Account account, String newEmail) throws Exception {
        mockMvc.perform(post("/api/v1/me/email/change/confirm")
                        .with(asUser(account.id(), account.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + support.emailCodeFromLatestMail(newEmail, MailKind.EMAIL_CHANGE) + "\"}"))
                .andExpect(status().isNoContent());
    }

    private boolean tokenStillUsable(Account account, String rawToken) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ? AND token_hash = ?"
                        + " AND used_at IS NULL AND invalidated_at IS NULL",
                Integer.class,
                account.id(),
                support.hash(rawToken));
        return count != null && count == 1;
    }

    private String currentEmail(Account account) {
        return jdbc.queryForObject("SELECT email FROM identity.user_account WHERE id = ?", String.class, account.id());
    }

    @Test
    void emailChangeRevokesOldLinkEvenWhenOldAddressComesBack() throws Exception {
        clock.freeze();
        Account account = support.eligible("reset.contact");
        String oldEmail = account.email();
        String newEmail = "moi." + UUID.randomUUID().toString().substring(0, 8) + "@thi360-test.example";
        forgot(oldEmail);
        String oldLink = support.tokenFromLatestMail(oldEmail);

        // Đang chờ xác nhận: email cũ vẫn là email đã xác minh nên liên kết cũ còn hiệu lực.
        clock.advance(Duration.ofSeconds(61));
        requestEmailChange(account, newEmail);
        assertThat(currentEmail(account)).isEqualTo(oldEmail);
        assertThat(tokenStillUsable(account, oldLink)).as("link valid while the change is pending").isTrue();

        // Xác nhận đổi email qua ID-1: liên kết gửi tới địa chỉ cũ hết quyền.
        confirmEmailChange(account, newEmail);
        assertThat(currentEmail(account)).isEqualTo(newEmail);
        reset(oldLink, NEW_PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        // Đổi ngược về địa chỉ cũ và xác minh lại: lần xác minh mới không làm sống lại liên kết cũ.
        clock.advance(Duration.ofSeconds(61));
        requestEmailChange(account, oldEmail);
        confirmEmailChange(account, oldEmail);
        assertThat(currentEmail(account)).isEqualTo(oldEmail);
        reset(oldLink, NEW_PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        login(account, account.password()).andExpect(status().isOk());

        // Liên kết mới gửi tới địa chỉ đã xác minh lại thì dùng được.
        clock.advance(Duration.ofSeconds(61));
        forgot(oldEmail);
        String newLink = support.tokenFromLatestMail(oldEmail);
        assertThat(newLink).isNotEqualTo(oldLink);
        reset(newLink, NEW_PASSWORD).andExpect(status().isNoContent());
        login(account, NEW_PASSWORD).andExpect(status().isOk());
        login(account, account.password()).andExpect(status().isUnauthorized());
    }
}
