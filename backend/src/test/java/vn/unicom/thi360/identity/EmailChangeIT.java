package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.keycloak.representations.idm.UserRepresentation;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.application.EmailCodeAccountLoader;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.support.MailOutbox;

/** ID-1 AC-19 đến AC-25: {@code /api/v1/me/email/change} và {@code /change/confirm} (TM 5.3, E17). */
@Import(ControlledClockConfig.class)
class EmailChangeIT extends AbstractKeycloakIntegrationTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private UserDirectory userDirectory;

    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;

    @MockitoSpyBean
    private EmailCodeAccountLoader accountLoader;

    @MockitoSpyBean
    private MailQueue mailQueue;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    @Autowired
    private ControlledClock clock;

    @Autowired
    private vn.unicom.thi360.identity.application.EmailCodeService emailCodeService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private SelfRegistrationSupport support;
    private MailOutbox mailOutbox;

    record Account(UUID id, String username, String email) {
    }

    @BeforeEach
    void setUp() {
        support = new SelfRegistrationSupport(mockMvc, jdbcTemplate, keycloakClient, redisTemplate, objectMapper);
        mailOutbox = new MailOutbox(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        clock.release();
        Mockito.reset(keycloakClient, accountLoader, mailQueue, auditRecorder);
        support.cleanup();
    }

    private Account newAccount() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "change." + suffix;
        String email = "change." + suffix + "@thi360.vn";
        support.register(support.body(username, email), SelfRegistrationSupport.freshIp()).andExpect(status().isCreated());
        Account account = new Account(support.accountId(username), username, email);
        jdbcTemplate.update("UPDATE identity.user_account SET email_verified_at = NULL WHERE id = ?", account.id());
        var userRes = KEYCLOAK.getKeycloakAdminClient().realm("thi-360").users().get(account.id().toString());
        var rep = userRes.toRepresentation();
        rep.setEmailVerified(false);
        userRes.update(rep);
        jdbcTemplate.update("DELETE FROM shared.email_message WHERE lower(to_email) = ?", account.email().toLowerCase());
        new org.springframework.transaction.support.TransactionTemplate(txManager).executeWithoutResult(s -> {
            String otp = emailCodeService.issue(account.id(), vn.unicom.thi360.identity.domain.EmailCodePurpose.VERIFY, account.email());
            mailQueue.enqueue(
                    MailKind.EMAIL_VERIFY,
                    account.email(),
                    "vi",
                    Map.of("fullName", "Nguyễn Văn An", "code", otp),
                    account.id());
        });
        Mockito.clearInvocations(mailQueue);
        return account;
    }

    private String newEmail() {
        String email = "moi." + SelfRegistrationSupport.suffix() + "@thi360.vn";
        support.track(null, email);
        return email;
    }

    private String code(String email, MailKind kind) {
        MailOutbox.Mail mail = mailOutbox.require(email, kind);
        Matcher matcher = SIX_DIGITS.matcher(mail.text());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String wrongCodeFor(String realCode) {
        return "000000".equals(realCode) ? "111111" : "000000";
    }

    private ResultActions send(Account account, String path, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/me/email" + path)
                .with(asUser(account.id(), account.username()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions requestChange(Account account, String newEmail, String password) throws Exception {
        return send(account, "/change",
                "{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"" + password + "\"}");
    }

    private ResultActions requestChange(Account account, String newEmail) throws Exception {
        return requestChange(account, newEmail, SelfRegistrationSupport.PASSWORD);
    }

    private ResultActions confirm(Account account, String code) throws Exception {
        return send(account, "/change/confirm", "{\"code\":\"" + code + "\"}");
    }

    private Map<String, Object> accountRow(Account account) {
        return jdbcTemplate.queryForMap(
                "SELECT email, email_normalized, email_verified_at, pending_email FROM identity.user_account"
                        + " WHERE id = ?", account.id());
    }

    private Map<String, Object> liveChangeCode(Account account) {
        return jdbcTemplate.queryForMap(
                "SELECT attempts, used_at, email, expires_at, created_at FROM identity.email_code"
                        + " WHERE user_id = ? AND purpose = 'CHANGE' AND used_at IS NULL", account.id());
    }

    private int auditCount(String action, UUID target) {
        while (outboxRelay.relayBatch() > 0) {
            // làm hiện nhật ký
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE action = ? AND target_id = ?",
                Integer.class, action, target.toString());
        return count == null ? 0 : count;
    }

    private ResultActions login(String identifier) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header(ClientIpResolver.X_FORWARDED_FOR, SelfRegistrationSupport.freshIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + SelfRegistrationSupport.PASSWORD
                        + "\"}"));
    }

    /** AC-19: đúng mật khẩu gửi mã tới email mới, ghi pending_email và nhật ký; email chưa đổi. */
    @Test
    void requestStoresPending() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();
        Object verifiedBefore = accountRow(account).get("email_verified_at");

        requestChange(account, newEmail).andExpect(status().isAccepted());

        Map<String, Object> row = accountRow(account);
        assertThat(row.get("pending_email")).isEqualTo(newEmail);
        assertThat(row.get("email")).isEqualTo(account.email());
        assertThat(row.get("email_verified_at")).isEqualTo(verifiedBefore);
        Map<String, Object> codeRow = liveChangeCode(account);
        assertThat(codeRow.get("email")).isEqualTo(newEmail);
        MailOutbox.Mail mail = mailOutbox.require(newEmail, MailKind.EMAIL_CHANGE);
        assertThat(mail.userId()).isEqualTo(account.id());
        assertThat(mailOutbox.count(newEmail)).isEqualTo(1);
        assertThat(mail.text()).contains(newEmail);
        assertThat(auditCount("EMAIL_CHANGE_REQUESTED", account.id())).isEqualTo(1);
        // Mã xác minh email hiện tại không bị đụng tới (đếm riêng theo mục đích).
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM identity.email_code WHERE user_id = ? AND purpose = 'VERIFY'"
                                + " AND used_at IS NULL", Integer.class, account.id()))
                .isEqualTo(1);
    }

    /** AC-20: sai mật khẩu bị từ chối, không tạo mã; tài khoản realm master nhận 403. */
    @Test
    void wrongPasswordAndMasterRealmRefused() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();

        requestChange(account, newEmail, "Wrong@1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_OLD_PASSWORD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));

        assertThat(accountRow(account).get("pending_email")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM identity.email_code WHERE user_id = ? AND purpose = 'CHANGE'",
                        Integer.class, account.id()))
                .isZero();
        assertThat(mailOutbox.count(newEmail)).isZero();

        // Một yêu cầu đang chờ không bị đổi bởi lần sai mật khẩu.
        requestChange(account, newEmail).andExpect(status().isAccepted());
        requestChange(account, "other." + newEmail, "Wrong@1234").andExpect(status().isBadRequest());
        assertThat(accountRow(account).get("pending_email")).isEqualTo(newEmail);

        mockMvc.perform(post("/api/v1/me/email/change")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    /** AC-21: trước khi xác nhận, email cũ vẫn đăng nhập được và là email khôi phục; email mới chưa đăng nhập được. */
    @Test
    void oldEmailStaysUntilConfirmed() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();
        requestChange(account, newEmail).andExpect(status().isAccepted());

        login(account.email()).andExpect(status().isOk());
        login(newEmail).andExpect(status().isUnauthorized());
        login(account.username()).andExpect(status().isOk());
        Map<String, Object> row = accountRow(account);
        assertThat(row.get("email")).isEqualTo(account.email());
        assertThat(row.get("email_normalized")).isEqualTo(account.email().toLowerCase());
        assertThat(userDirectory.findDetailById(account.id()).orElseThrow().email()).isEqualTo(account.email());
        assertThat(keycloakEmail(account)).isEqualTo(account.email());

        // Mã hết hạn: hồ sơ giữ email cũ.
        jdbcTemplate.update(
                "UPDATE identity.email_code SET expires_at = now() - interval '1 second' WHERE user_id = ?"
                        + " AND purpose = 'CHANGE'", account.id());
        confirm(account, code(newEmail, MailKind.EMAIL_CHANGE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(accountRow(account).get("email")).isEqualTo(account.email());
        login(account.email()).andExpect(status().isOk());
    }

    /** AC-22: email đã có chủ nhận 409 ở /change; email bị lấy trong lúc chờ nhận 409 ở /change/confirm. */
    @Test
    void duplicateAtRequestAndConfirm() throws Exception {
        Account account = newAccount();
        Account other = newAccount();

        requestChange(account, other.email().toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        // Câu hỏi mở 6 (tạm): đổi sang chính email hiện tại cũng là EMAIL_TAKEN.
        requestChange(account, account.email())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        assertThat(accountRow(account).get("pending_email")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM identity.email_code WHERE user_id = ? AND purpose = 'CHANGE'",
                        Integer.class, account.id()))
                .isZero();

        String contested = newEmail();
        requestChange(account, contested).andExpect(status().isAccepted());
        String code = code(contested, MailKind.EMAIL_CHANGE);
        // Trong lúc chờ, tài khoản khác lấy email này.
        jdbcTemplate.update(
                "UPDATE identity.user_account SET email = ?, email_normalized = ? WHERE id = ?",
                contested, contested.toLowerCase(), other.id());

        confirm(account, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        Map<String, Object> row = accountRow(account);
        assertThat(row.get("email")).isEqualTo(account.email());
        assertThat(row.get("pending_email")).isEqualTo(contested);
        assertThat(liveChangeCode(account).get("used_at")).isNull();
        assertThat(mailOutbox.count(account.email())).isEqualTo(1);
    }

    /** AC-23: xác nhận đúng mã đổi email ở mọi nơi, báo email cũ, giữ nguyên các phiên đang có. */
    @Test
    void confirmAppliesEverywhere() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();
        MvcResult session = login(account.username()).andExpect(status().isOk()).andReturn();
        Cookie access = session.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        requestChange(account, newEmail).andExpect(status().isAccepted());

        confirm(account, code(newEmail, MailKind.EMAIL_CHANGE)).andExpect(status().isNoContent());

        Map<String, Object> row = accountRow(account);
        assertThat(row.get("email")).isEqualTo(newEmail);
        assertThat(row.get("email_normalized")).isEqualTo(newEmail.toLowerCase());
        assertThat(row.get("email_verified_at")).isNotNull();
        assertThat(row.get("pending_email")).isNull();
        UserRepresentation inKeycloak = KEYCLOAK.getKeycloakAdminClient().realm("thi-360").users()
                .get(account.id().toString()).toRepresentation();
        assertThat(inKeycloak.getEmail()).isEqualToIgnoringCase(newEmail);
        assertThat(inKeycloak.isEmailVerified()).isTrue();
        MailOutbox.Mail notice = mailOutbox.require(account.email(), MailKind.EMAIL_CHANGED_NOTICE);
        assertThat(notice.text()).contains(newEmail);
        assertThat(mailOutbox.all(newEmail, MailKind.EMAIL_CHANGED_NOTICE)).isEmpty();
        assertThat(auditCount("EMAIL_CHANGED", account.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT used_at FROM identity.email_code WHERE user_id = ? AND purpose = 'CHANGE'",
                        Object.class, account.id()))
                .isNotNull();
        // Phiên đang có vẫn dùng được; email mới đăng nhập được, email cũ thì không.
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isOk());
        login(newEmail).andExpect(status().isOk());
        login(account.email()).andExpect(status().isUnauthorized());
        // Lớp học và các nơi khác đọc email qua UserDirectory.
        assertThat(userDirectory.findDetailById(account.id()).orElseThrow().email()).isEqualTo(newEmail);
        // Dùng lại mã đã xác nhận: không đổi thêm gì.
        confirm(account, code(newEmail, MailKind.EMAIL_CHANGE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(mailOutbox.all(account.email(), MailKind.EMAIL_CHANGED_NOTICE)).hasSize(1);
    }

    /** AC-24: Keycloak lỗi lúc xác nhận: 503, email và pending_email giữ nguyên, mã chưa dùng, không thư báo. */
    @Test
    void keycloakFailureRollsBack() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();
        requestChange(account, newEmail).andExpect(status().isAccepted());
        String code = code(newEmail, MailKind.EMAIL_CHANGE);
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null))
                .when(keycloakClient).updateUserProfile(any(UUID.class), anyString(), isNull(), isNull(), anyBoolean());

        confirm(account, code)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_ERROR"));

        Map<String, Object> row = accountRow(account);
        assertThat(row.get("email")).isEqualTo(account.email());
        assertThat(row.get("pending_email")).isEqualTo(newEmail);
        assertThat(liveChangeCode(account).get("used_at")).isNull();
        assertThat(liveChangeCode(account).get("attempts")).isEqualTo(0);
        assertThat(mailOutbox.all(account.email(), MailKind.EMAIL_CHANGED_NOTICE)).isEmpty();
        assertThat(auditCount("EMAIL_CHANGED", account.id())).isZero();
        assertThat(keycloakEmail(account)).isEqualTo(account.email());

        // Keycloak báo trùng email thì là EMAIL_TAKEN và cũng hủy toàn bộ.
        doThrow(new ConflictException("EMAIL_TAKEN"))
                .when(keycloakClient).updateUserProfile(any(UUID.class), anyString(), isNull(), isNull(), anyBoolean());
        confirm(account, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        assertThat(accountRow(account).get("email")).isEqualTo(account.email());

        // Hết lỗi, cùng mã dùng được.
        Mockito.reset(keycloakClient);
        confirm(account, code).andExpect(status().isNoContent());
        assertThat(accountRow(account).get("email")).isEqualTo(newEmail);
    }

    /** AC-25: mã đổi email theo cùng quy tắc E15, E17: 10 phút, 5 lần sai, gửi lại sau 60 giây, 5 lần mỗi ngày. */
    @Test
    void changeCodeFollowsSameLimits() throws Exception {
        clock.freeze();
        Account account = newAccount();
        String newEmail = newEmail();
        requestChange(account, newEmail).andExpect(status().isAccepted());
        String code = code(newEmail, MailKind.EMAIL_CHANGE);
        long ttl = jdbcTemplate.queryForObject(
                "SELECT extract(epoch FROM (expires_at - created_at))::bigint FROM identity.email_code"
                        + " WHERE user_id = ? AND purpose = 'CHANGE'", Long.class, account.id());
        assertThat(ttl).isEqualTo(600L);

        // 5 lần sai: 4, 3, 2, 1, 0 còn lại; lần nhập thứ 6 là EXPIRED kể cả khi đúng mã.
        String wrong = wrongCodeFor(code);
        for (int remaining = 4; remaining >= 0; remaining--) {
            confirm(account, wrong)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                    .andExpect(jsonPath("$.remainingAttempts").value(remaining));
        }
        confirm(account, code)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(accountRow(account).get("email")).isEqualTo(account.email());

        // Gửi lại: cùng thời điểm, phải chờ đúng 60 giây; mã cũ (đã chết) không thay đổi gì.
        MvcResult limited = requestChange(account, newEmail)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        JsonNode body = objectMapper.readTree(limited.getResponse().getContentAsString());
        assertThat(body.path("retryAfter").asLong()).isEqualTo(60L);
        assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo(body.path("retryAfter").asText());
        assertThat(mailOutbox.all(newEmail, MailKind.EMAIL_CHANGE)).hasSize(1);
        // Giây 59 còn phải chờ đúng 1 giây; giây 60 được gửi.
        clock.advance(Duration.ofSeconds(59));
        requestChange(account, newEmail)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfter").value(1));
        clock.advance(Duration.ofSeconds(1));
        requestChange(account, newEmail).andExpect(status().isAccepted());
        assertThat(mailOutbox.all(newEmail, MailKind.EMAIL_CHANGE)).hasSize(2);

        // Lần gửi thứ 5 còn được, thứ 6 trong ngày thì không.
        for (int sent = 3; sent <= 5; sent++) {
            clock.advance(Duration.ofSeconds(61));
            requestChange(account, newEmail).andExpect(status().isAccepted());
        }
        assertThat(mailOutbox.all(newEmail, MailKind.EMAIL_CHANGE)).hasSize(5);
        clock.advance(Duration.ofSeconds(61));
        requestChange(account, newEmail)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfter").isNumber());
        assertThat(mailOutbox.all(newEmail, MailKind.EMAIL_CHANGE)).hasSize(5);

        // Mã mới nhất xác nhận được; đếm riêng theo mục đích nên VERIFY không bị ảnh hưởng.
        confirm(account, code(newEmail, MailKind.EMAIL_CHANGE)).andExpect(status().isNoContent());
        assertThat(accountRow(account).get("email")).isEqualTo(newEmail);
    }

    /** E17: mã đổi email còn dùng được ở 10 phút trừ 1 giây và hết hạn đúng 10 phút. */
    @Test
    void changeCodeExpiresAtExactlyTenMinutes() throws Exception {
        clock.freeze();
        Account account = newAccount();
        String newEmail = newEmail();
        requestChange(account, newEmail).andExpect(status().isAccepted());
        String code = code(newEmail, MailKind.EMAIL_CHANGE);

        clock.advance(Duration.ofMinutes(10));
        confirm(account, code)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(accountRow(account).get("email")).isEqualTo(account.email());

        requestChange(account, newEmail).andExpect(status().isAccepted());
        String fresh = code(newEmail, MailKind.EMAIL_CHANGE);
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        confirm(account, fresh).andExpect(status().isNoContent());
        assertThat(accountRow(account).get("email")).isEqualTo(newEmail);
    }

    private int emailCodeCount(Account account) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity.email_code WHERE user_id = ?", Integer.class, account.id());
    }

    /** Chạy {@code action} ngay sau khi phiên được xác định là tài khoản hiện tại, trước transaction ghi. */
    private void afterSessionCheck(Runnable action) {
        doAnswer(invocation -> {
            Object found = invocation.callRealMethod();
            action.run();
            return found;
        }).when(accountLoader).requireCurrentAccount();
    }

    /**
     * R1-05: tài khoản bị khóa sau bước kiểm phiên nhưng trước transaction ghi thì không có thư, mã hay thay đổi nào,
     * ở cả bốn API (trước đây transaction đọc lại chỉ kiểm tồn tại và dùng bản đã nạp).
     */
    @Test
    void accountDeactivatedAfterSessionCheckGetsNoMailCodeOrChange() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();
        int codes = emailCodeCount(account);
        int mails = mailOutbox.count(account.email());
        afterSessionCheck(() -> jdbcTemplate.update(
                "UPDATE identity.user_account SET status = 3 WHERE id = ?", account.id()));

        send(account, "/verification", "").andExpect(status().isUnauthorized());
        jdbcTemplate.update("UPDATE identity.user_account SET status = 2 WHERE id = ?", account.id());
        requestChange(account, newEmail).andExpect(status().isUnauthorized());
        jdbcTemplate.update("UPDATE identity.user_account SET status = 2 WHERE id = ?", account.id());
        confirm(account, "123456").andExpect(status().isUnauthorized());
        jdbcTemplate.update("UPDATE identity.user_account SET status = 2 WHERE id = ?", account.id());
        send(account, "/verify", "{\"code\":\"123456\"}").andExpect(status().isUnauthorized());

        assertThat(emailCodeCount(account)).isEqualTo(codes);
        assertThat(mailOutbox.count(account.email())).isEqualTo(mails);
        assertThat(mailOutbox.count(newEmail)).isZero();
        Map<String, Object> row = accountRow(account);
        assertThat(row.get("pending_email")).isNull();
        assertThat(row.get("email")).isEqualTo(account.email());
        assertThat(row.get("email_verified_at")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT coalesce(sum(attempts), 0) FROM identity.email_code WHERE user_id = ?",
                        Integer.class, account.id()))
                .isZero();
    }

    @Test
    void accountDeletedAfterSessionCheckDoesNotReceiveVerificationMail() throws Exception {
        Account account = newAccount();
        int codes = emailCodeCount(account);
        int mails = mailOutbox.count(account.email());
        afterSessionCheck(() -> jdbcTemplate.update(
                "UPDATE identity.user_account SET deleted_at = now() WHERE id = ?", account.id()));

        send(account, "/verification", "").andExpect(status().isUnauthorized());

        assertThat(emailCodeCount(account)).isEqualTo(codes);
        assertThat(mailOutbox.count(account.email())).isEqualTo(mails);
        assertThat(accountRow(account).get("email_verified_at")).isNull();
    }

    @Test
    void missingAccountAfterSessionCheckDoesNotReceiveVerificationMail() throws Exception {
        Account account = newAccount();
        int mails = mailOutbox.count(account.email());
        afterSessionCheck(() -> {
            jdbcTemplate.update("DELETE FROM identity.email_code WHERE user_id = ?", account.id());
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", account.id());
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", account.id());
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", account.id());
        });

        send(account, "/verification", "").andExpect(status().isUnauthorized());

        assertThat(emailCodeCount(account)).isZero();
        assertThat(mailOutbox.count(account.email())).isEqualTo(mails);
    }

    /**
     * R1-05: một xác nhận khác vừa đổi email hiện tại thành địa chỉ X trước transaction của yêu cầu đổi sang X: yêu cầu
     * bị từ chối EMAIL_TAKEN (như đổi sang chính email hiện tại), không đặt X thành pending_email của chính tài khoản.
     */
    @Test
    void requestForJustConfirmedEmailIsRejectedOnTheFreshRow() throws Exception {
        Account account = newAccount();
        String justConfirmed = newEmail();
        int codes = emailCodeCount(account);
        afterSessionCheck(() -> jdbcTemplate.update(
                "UPDATE identity.user_account SET email = ?, email_normalized = ? WHERE id = ?",
                justConfirmed, justConfirmed.toLowerCase(), account.id()));

        requestChange(account, justConfirmed)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        Map<String, Object> row = accountRow(account);
        assertThat(row.get("pending_email")).isNull();
        assertThat(row.get("email")).isEqualTo(justConfirmed);
        assertThat(emailCodeCount(account)).isEqualTo(codes);
        assertThat(mailOutbox.count(justConfirmed)).isZero();
    }

    /** A real confirmation overtakes a request paused after its initial account read. */
    @Test
    void concurrentConfirmCannotBecomePendingAgain() throws Exception {
        Account account = newAccount();
        String pending = newEmail();
        requestChange(account, pending).andExpect(status().isAccepted());
        String otp = code(pending, MailKind.EMAIL_CHANGE);
        var loaded = new java.util.concurrent.CountDownLatch(1);
        var resume = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            Object found = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals("email-change-request-test")) {
                loaded.countDown();
                if (!resume.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("request was not released");
                }
            }
            return found;
        }).when(accountLoader).requireCurrentAccount();
        var request = new java.util.concurrent.FutureTask<MvcResult>(
                () -> requestChange(account, pending).andReturn());
        Thread thread = new Thread(request, "email-change-request-test");
        thread.start();
        try {
            assertThat(loaded.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            confirm(account, otp).andExpect(status().isNoContent());
        } finally {
            resume.countDown();
        }
        MvcResult result = request.get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("EMAIL_TAKEN");
        assertThat(accountRow(account).get("pending_email")).isNull();
        assertThat(accountRow(account).get("email")).isEqualTo(pending);
        assertThat(mailOutbox.all(pending, MailKind.EMAIL_CHANGE)).hasSize(1);
    }

    /**
     * R1-06: thư báo email cũ hoặc nhật ký lỗi trước khi gọi Keycloak thì Keycloak không bị đụng tới: email trong
     * Keycloak, trong cơ sở dữ liệu và mã OTP đều nguyên, và mã còn dùng được khi hết lỗi.
     */
    @Test
    void noticeOrAuditFailureAtConfirmLeavesKeycloakUntouched() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();
        requestChange(account, newEmail).andExpect(status().isAccepted());
        String code = code(newEmail, MailKind.EMAIL_CHANGE);
        Mockito.clearInvocations(keycloakClient);

        MailQueue queueSpy = AopTestUtils.getUltimateTargetObject(mailQueue);
        doThrow(new IllegalStateException("queue down"))
                .when(queueSpy).enqueue(eq(MailKind.EMAIL_CHANGED_NOTICE), anyString(), any(), any(), any());
        confirm(account, code).andExpect(status().is5xxServerError());
        assertConfirmLeftNothingBehind(account, newEmail);
        Mockito.reset(queueSpy);

        doThrow(new IllegalStateException("audit down"))
                .when(auditRecorder).record(argThat(event -> event != null && "EMAIL_CHANGED".equals(event.action())));
        confirm(account, code).andExpect(status().is5xxServerError());
        assertConfirmLeftNothingBehind(account, newEmail);
        Mockito.reset(auditRecorder);

        confirm(account, code).andExpect(status().isNoContent());
        assertThat(accountRow(account).get("email")).isEqualTo(newEmail);
        assertThat(keycloakEmail(account)).isEqualToIgnoringCase(newEmail);
    }

    private void assertConfirmLeftNothingBehind(Account account, String pendingEmail) {
        Mockito.verify(keycloakClient, Mockito.never())
                .updateUserProfile(any(UUID.class), anyString(), any(), any(), anyBoolean());
        Map<String, Object> row = accountRow(account);
        assertThat(row.get("email")).isEqualTo(account.email());
        assertThat(row.get("pending_email")).isEqualTo(pendingEmail);
        assertThat(liveChangeCode(account).get("used_at")).isNull();
        assertThat(liveChangeCode(account).get("attempts")).isEqualTo(0);
        assertThat(keycloakEmail(account)).isEqualTo(account.email());
        assertThat(mailOutbox.all(account.email(), MailKind.EMAIL_CHANGED_NOTICE)).isEmpty();
    }

    /** Hai lần xác nhận cùng mã đúng chạy đồng thời: chỉ một lần đổi email, chỉ một thư báo. */
    @Test
    void confirmTwiceConcurrentlyChangesOnce() throws Exception {
        Account account = newAccount();
        String newEmail = newEmail();
        requestChange(account, newEmail).andExpect(status().isAccepted());
        String code = code(newEmail, MailKind.EMAIL_CHANGE);

        var first = new java.util.concurrent.FutureTask<MvcResult>(() -> confirm(account, code).andReturn());
        var second = new java.util.concurrent.FutureTask<MvcResult>(() -> confirm(account, code).andReturn());
        Thread t1 = new Thread(first);
        Thread t2 = new Thread(second);
        t1.start();
        t2.start();
        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(204, 422);
        assertThat(accountRow(account).get("email")).isEqualTo(newEmail);
        assertThat(mailOutbox.all(account.email(), MailKind.EMAIL_CHANGED_NOTICE)).hasSize(1);
        assertThat(auditCount("EMAIL_CHANGED", account.id())).isEqualTo(1);
    }

    /**
     * Câu hỏi mở 4 (tạm): admin sửa email khi tài khoản đang có pending_email thì giữ pending_email; bước xác nhận
     * kiểm trùng lại và ghi đè email do admin đặt. Admin sửa email xóa trạng thái đã xác minh (không đổi hành vi cũ).
     */
    @Test
    void adminEmailEditKeepsPendingEmail() throws Exception {
        Account account = newAccount();
        String pending = newEmail();
        String adminEmail = newEmail();
        // Đã xác minh email hiện tại trước khi đổi.
        mockMvc.perform(post("/api/v1/me/email/verify")
                        .with(asUser(account.id(), account.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code(account.email(), MailKind.EMAIL_VERIFY) + "\"}"))
                .andExpect(status().isNoContent());
        requestChange(account, pending).andExpect(status().isAccepted());
        long version = jdbcTemplate.queryForObject(
                "SELECT version FROM identity.user_account WHERE id = ?", Long.class, account.id());

        mockMvc.perform(patch("/api/v1/admin/users/{id}", account.id())
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + adminEmail + "\",\"version\":" + version + "}"))
                .andExpect(status().isOk());

        Map<String, Object> afterAdmin = accountRow(account);
        assertThat(afterAdmin.get("email")).isEqualTo(adminEmail);
        assertThat(afterAdmin.get("email_verified_at")).isNull();
        assertThat(afterAdmin.get("pending_email")).isEqualTo(pending);

        confirm(account, code(pending, MailKind.EMAIL_CHANGE)).andExpect(status().isNoContent());
        Map<String, Object> confirmed = accountRow(account);
        assertThat(confirmed.get("email")).isEqualTo(pending);
        assertThat(confirmed.get("email_verified_at")).isNotNull();
        assertThat(confirmed.get("pending_email")).isNull();
        assertThat(mailOutbox.all(adminEmail, MailKind.EMAIL_CHANGED_NOTICE)).hasSize(1);
    }

    /** Dữ liệu cũ: tài khoản không có ba cột mới vẫn đăng nhập được và không bị coi là đã chấp nhận điều khoản. */
    @Test
    void legacyAccountWithEmptyColumnsCanLogin() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "legacy." + suffix;
        UUID id = keycloakClient.createUser(new KeycloakIdentityClient.CreateUserCommand(
                username, username + "@thi360.vn", "Legacy", "User", SelfRegistrationSupport.PASSWORD, true, false,
                true));
        support.track(username, username + "@thi360.vn");
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, code, username, username_normalized, full_name, email,"
                        + " email_normalized, source, status, must_change_password, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'Legacy User', ?, ?, 1, 2, false, now(), now())",
                id, String.format("U%06d", seq), username, username, username + "@thi360.vn", username + "@thi360.vn");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT terms_version, terms_accepted_at, pending_email FROM identity.user_account WHERE id = ?", id);
        assertThat(row.values()).containsOnlyNulls();
        login(username).andExpect(status().isOk());
    }

    /** Migration trên DB đã có dữ liệu: ba cột mới trống cho tài khoản cũ, bảng mã trống, dữ liệu cũ giữ nguyên. */
    @Test
    void existingAccountsSurviveMigration() throws Exception {
        String database = "migrate_" + SelfRegistrationSupport.suffix();
        String adminUrl = POSTGRES.getJdbcUrl();
        String url = adminUrl.replace("/" + POSTGRES.getDatabaseName(), "/" + database);
        try (Connection admin = DriverManager.getConnection(adminUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        try {
            Flyway.configure()
                    .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .defaultSchema("shared")
                    .schemas("shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam",
                            "commerce", "attempt")
                    .table("flyway_schema_history")
                    .locations("classpath:db/migration")
                    .placeholders(Map.of("auditAppRole", POSTGRES.getUsername()))
                    .target("202611191822")
                    .load()
                    .migrate();
            try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
                    Statement statement = connection.createStatement()) {
                statement.execute(
                        "INSERT INTO identity.user_account (id, code, username, username_normalized, full_name,"
                                + " email, email_normalized, email_verified_at, source, status, must_change_password)"
                                + " VALUES ('00000000-0000-7000-8000-000000000a01', 'U900001', 'Old.User', 'old.user',"
                                + " 'Old User', 'Old@Thi360.vn', 'old@thi360.vn', now(), 1, 2, false)");
            }
            Flyway.configure()
                    .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .defaultSchema("shared")
                    .schemas("shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam",
                            "commerce", "attempt")
                    .table("flyway_schema_history")
                    .locations("classpath:db/migration")
                    .placeholders(Map.of("auditAppRole", POSTGRES.getUsername()))
                    .target("202611192026")
                    .load()
                    .migrate();
            try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery(
                            "SELECT terms_version, terms_accepted_at, pending_email, email, email_verified_at, status"
                                    + " FROM identity.user_account WHERE code = 'U900001'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("terms_version")).isNull();
                assertThat(rs.getTimestamp("terms_accepted_at")).isNull();
                assertThat(rs.getString("pending_email")).isNull();
                assertThat(rs.getString("email")).isEqualTo("Old@Thi360.vn");
                assertThat(rs.getTimestamp("email_verified_at")).isNotNull();
                assertThat(rs.getShort("status")).isEqualTo((short) 2);
                try (Statement count = connection.createStatement();
                        ResultSet codes = count.executeQuery("SELECT count(*) FROM identity.email_code")) {
                    assertThat(codes.next()).isTrue();
                    assertThat(codes.getInt(1)).isZero();
                }
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(adminUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
                    Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            }
        }
    }

    private String keycloakEmail(Account account) {
        return KEYCLOAK.getKeycloakAdminClient().realm("thi-360").users().get(account.id().toString())
                .toRepresentation().getEmail();
    }
}
