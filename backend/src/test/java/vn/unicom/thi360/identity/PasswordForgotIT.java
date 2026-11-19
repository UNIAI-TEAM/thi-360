package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.AopTestUtils;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.PasswordResetTestSupport.Account;
import vn.unicom.thi360.identity.application.PasswordResetService;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.TokenPepperProperties;
import vn.unicom.thi360.support.MailOutbox;

/**
 * {@code POST /auth/password/forgot} (TM 5.4, E16, E27): AC-1, AC-4 đến AC-9 của plan ID-2, cộng ca đồng thời
 * "2 lần forgot cho một tài khoản chỉ có một mã còn hiệu lực".
 */
@ExtendWith(OutputCaptureExtension.class)
class PasswordForgotIT extends AbstractKeycloakIntegrationTest {

    private static final String IP = "203.0.113.150";

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
    private PasswordResetService passwordResetService;
    @Autowired
    @Qualifier("passwordResetExecutor")
    private ThreadPoolTaskExecutor executor;
    @MockitoSpyBean
    private MailQueue mailQueue;

    private PasswordResetTestSupport support;
    private MailOutbox mailbox;

    @BeforeEach
    void setUp() {
        support = new PasswordResetTestSupport(keycloakClient, userAccountRepository, jdbc, redis, pepper.pepper());
        mailbox = support.mailbox();
        redis.delete(RateLimitFilter.FORGOT_KEY_PREFIX + IP);
    }

    @AfterEach
    void cleanup() {
        support.cleanup();
        redis.delete(RateLimitFilter.FORGOT_KEY_PREFIX + IP);
    }

    /** Gọi {@code forgot} và đợi bộ thực thi nền xử lý xong yêu cầu đó (cả khi không có thư để chờ). */
    private MvcResult forgot(String email) throws Exception {
        long before = executor.getThreadPoolExecutor().getCompletedTaskCount();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .header(ClientIpResolver.X_FORWARDED_FOR, IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn();
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(executor.getThreadPoolExecutor().getCompletedTaskCount()).isGreaterThan(before));
        return result;
    }

    @Test
    void sameResponseForEveryEmail() throws Exception {
        Account eligible = support.eligible("forgot.same");
        Account locked = support.create("forgot.locked", UserStatus.INACTIVE, true, false);
        Account deleted = support.create("forgot.deleted", UserStatus.ACTIVE, true, true);
        Account unverified = support.create("forgot.unverified", UserStatus.ACTIVE, false, false);
        String unknown = "nobody." + UUID.randomUUID() + "@thi360-test.example";
        String masterEmail = masterEmail();
        try {
            String reference = null;
            for (String email : List.of(
                    eligible.email(), unknown, locked.email(), deleted.email(), unverified.email(), masterEmail)) {
                MvcResult result = forgot(email);
                assertThat(result.getResponse().getStatus()).as(email).isEqualTo(202);
                String body = result.getResponse().getContentAsString();
                if (reference == null) {
                    reference = body;
                }
                assertThat(body).as("body for %s", email).isEqualTo(reference);
            }
            assertThat(reference).contains("message");
        } finally {
            clearMasterEmail();
        }
    }

    @Test
    void mailOnlyForEligibleAccounts() throws Exception {
        Account student = support.eligible("forgot.student");
        Account centerAdmin = support.eligible("forgot.admin");
        jdbc.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_at) VALUES (?, ?::uuid, now())",
                centerAdmin.id(),
                "00000000-0000-7000-8000-000000000001");
        Account locked = support.create("forgot.locked2", UserStatus.INACTIVE, true, false);
        Account deleted = support.create("forgot.deleted2", UserStatus.ACTIVE, true, true);
        Account unverified = support.create("forgot.unverified2", UserStatus.ACTIVE, false, false);

        for (Account account : List.of(student, centerAdmin, locked, deleted, unverified)) {
            forgot(account.email());
        }

        for (Account eligible : List.of(student, centerAdmin)) {
            assertThat(mailbox.all(eligible.email(), MailKind.PASSWORD_RESET)).hasSize(1);
            String text = mailbox.require(eligible.email(), MailKind.PASSWORD_RESET).text();
            assertThat(text).contains("/reset-password?token=");
            assertThat(support.unusedTokenRows(eligible.id())).isEqualTo(1);
        }
        for (Account ineligible : List.of(locked, deleted, unverified)) {
            assertThat(mailbox.count(ineligible.email())).as(ineligible.username()).isZero();
            assertThat(support.tokenRows(ineligible.id())).as(ineligible.username()).isZero();
        }
    }

    @Test
    void masterRealmGetsNoMail() throws Exception {
        String email = masterEmail();
        try {
            MvcResult result = forgot(email);

            assertThat(result.getResponse().getStatus()).isEqualTo(202);
            assertThat(mailbox.count(email)).isZero();
            Integer forEmail = jdbc.queryForObject(
                    "SELECT count(*) FROM identity.password_reset_token t JOIN identity.user_account u ON u.id = t.user_id"
                            + " WHERE lower(u.email) = lower(?)",
                    Integer.class,
                    email);
            assertThat(forEmail).isZero();
        } finally {
            clearMasterEmail();
        }
    }

    @Test
    void tokenAndMailShareTransaction() throws Exception {
        Account account = support.eligible("forgot.tx");
        // MailQueue là bean @Transactional(MANDATORY): đặt stub lên đối tượng đích, không qua proxy giao dịch.
        doThrow(new IllegalStateException("mail store down"))
                .when(AopTestUtils.<MailQueue>getUltimateTargetObject(mailQueue))
                .enqueue(any(), any(), any(), any(), any());

        // Qua endpoint: vẫn 202, và không còn mã nào.
        MvcResult result = forgot(account.email());
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(support.tokenRows(account.id())).isZero();
        assertThat(mailbox.count(account.email())).isZero();

        // Gọi thẳng service: lỗi xếp thư làm rollback cả dòng mã (cùng transaction).
        assertThatThrownBy(() -> passwordResetService.issue(account.email(), IP))
                .isInstanceOf(IllegalStateException.class);
        assertThat(support.tokenRows(account.id())).isZero();
    }

    @Test
    void storesOnlyHmac(CapturedOutput output) throws Exception {
        Account account = support.eligible("forgot.hmac");

        forgot(account.email());

        String raw = support.tokenFromLatestMail(account.email());
        var row = jdbc.queryForMap(
                "SELECT token_hash, request_ip, expires_at, created_at FROM identity.password_reset_token WHERE user_id = ?",
                account.id());
        assertThat((String) row.get("token_hash")).isEqualTo(support.hash(raw)).hasSize(64).doesNotContain(raw);
        assertThat(row.get("request_ip")).isEqualTo(IP);
        Instant created = ((Timestamp) row.get("created_at")).toInstant();
        Instant expires = ((Timestamp) row.get("expires_at")).toInstant();
        assertThat(Duration.between(created, expires)).isEqualTo(Duration.ofHours(1));
        // Mã rõ không nằm ở bất kỳ cột nào của dòng, cũng không trong log.
        String wholeRow = jdbc.queryForObject(
                "SELECT to_json(t)::text FROM identity.password_reset_token t WHERE user_id = ?",
                String.class,
                account.id());
        assertThat(wholeRow).doesNotContain(raw);
        assertThat(output.getAll()).doesNotContain(raw);
    }

    @Test
    void accountCooldown() throws Exception {
        Account account = support.eligible("forgot.cooldown");

        forgot(account.email());
        String first = support.tokenFromLatestMail(account.email());

        // Yêu cầu thứ hai ngay sau đó: vẫn 202 cùng thân, không thư mới, mã trước vẫn dùng được.
        MvcResult second = forgot(account.email());
        assertThat(second.getResponse().getStatus()).isEqualTo(202);
        assertThat(mailbox.all(account.email(), MailKind.PASSWORD_RESET)).hasSize(1);
        assertThat(support.tokenRows(account.id())).isEqualTo(1);
        assertThat(support.unusedTokenRows(account.id())).isEqualTo(1);
        assertThat(support.tokenFromLatestMail(account.email())).isEqualTo(first);

        // Giây 58: còn trong cooldown. Giây 62: qua cooldown, được phép và mã cũ mất hiệu lực.
        support.ageTokens(account.id(), 58);
        forgot(account.email());
        assertThat(mailbox.all(account.email(), MailKind.PASSWORD_RESET)).hasSize(1);

        support.ageTokens(account.id(), 62);
        forgot(account.email());
        assertThat(mailbox.all(account.email(), MailKind.PASSWORD_RESET)).hasSize(2);
        assertThat(support.tokenRows(account.id())).isEqualTo(2);
        assertThat(support.unusedTokenRows(account.id())).as("only the newest token stays valid").isEqualTo(1);
    }

    @Test
    void accountDailyLimit() throws Exception {
        Account account = support.eligible("forgot.daily");
        Instant now = Instant.now();
        // Bốn yêu cầu trước, đều quá cooldown và trong 24 giờ trượt.
        for (int i = 1; i <= 4; i++) {
            support.insertToken(account.id(), now.minus(Duration.ofHours(i)), now.plus(Duration.ofHours(1)), now);
        }

        forgot(account.email());
        assertThat(mailbox.all(account.email(), MailKind.PASSWORD_RESET)).as("5th request in the day").hasSize(1);
        assertThat(support.tokenRows(account.id())).isEqualTo(5);

        // Yêu cầu thứ 6 (cooldown đã qua): vẫn 202 cùng thân nhưng không có thư mới.
        support.ageTokens(account.id(), 120);
        MvcResult sixth = forgot(account.email());
        assertThat(sixth.getResponse().getStatus()).isEqualTo(202);
        assertThat(mailbox.all(account.email(), MailKind.PASSWORD_RESET)).as("6th request in the day").hasSize(1);
        assertThat(support.tokenRows(account.id())).isEqualTo(5);

        // Cửa sổ là 24 giờ trượt: khi cả năm mã đã quá 24 giờ thì được yêu cầu lại.
        support.ageTokens(account.id(), Duration.ofHours(24).plusMinutes(1).toSeconds());
        forgot(account.email());
        assertThat(mailbox.all(account.email(), MailKind.PASSWORD_RESET)).hasSize(2);
    }

    @Test
    void concurrentForgotLeavesOneValidToken() throws Exception {
        Account account = support.eligible("forgot.race");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> {
                        start.await();
                        passwordResetService.issue(account.email(), IP);
                        return null;
                    }),
                    pool.submit(() -> {
                        start.await();
                        passwordResetService.issue(account.email(), IP);
                        return null;
                    }));
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(support.unusedTokenRows(account.id())).isEqualTo(1);
        assertThat(support.tokenRows(account.id())).isEqualTo(1);
        assertThat(mailbox.all(account.email(), MailKind.PASSWORD_RESET)).hasSize(1);
    }

    /** Gán email cho tài khoản `platform` của realm master để chứng minh E27: có email trong Keycloak vẫn không có thư. */
    private String masterEmail() {
        String email = "platform." + UUID.randomUUID().toString().substring(0, 8) + "@thi360-test.example";
        var users = KEYCLOAK.getKeycloakAdminClient().realm("master").users();
        UserRepresentation platform = users.searchByUsername("platform", true).getFirst();
        platform.setEmail(email);
        users.get(platform.getId()).update(platform);
        return email;
    }

    private void clearMasterEmail() {
        var users = KEYCLOAK.getKeycloakAdminClient().realm("master").users();
        UserRepresentation platform = users.searchByUsername("platform", true).getFirst();
        platform.setEmail(null);
        users.get(platform.getId()).update(platform);
    }
}
