package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.application.EmailCodeGenerator;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.infrastructure.EmailCodeHasher;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.support.MailOutbox;

/** ID-1 AC-10 đến AC-18, AC-28: {@code /api/v1/me/email/verification} và {@code /verify} (TM 5.3, E15, E18). */
@Import(ControlledClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class EmailVerificationIT extends AbstractKeycloakIntegrationTest {

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
    private EmailCodeHasher hasher;

    @MockitoSpyBean
    private EmailCodeGenerator generator;

    @Autowired
    private ControlledClock clock;

    @Autowired
    private KeycloakIdentityClient keycloakClient;

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
        Mockito.reset(generator);
        support.cleanup();
    }

    private Account newAccount() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "verify." + suffix;
        String email = "verify." + suffix + "@thi360.vn";
        support.register(support.body(username, email), SelfRegistrationSupport.freshIp()).andExpect(status().isCreated());
        return new Account(support.accountId(username), username, email);
    }

    private String latestCode(Account account) {
        MailOutbox.Mail mail = mailOutbox.require(account.email(), MailKind.EMAIL_VERIFY);
        Matcher matcher = SIX_DIGITS.matcher(mail.text());
        assertThat(matcher.find()).as("a six digit code in the mail").isTrue();
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

    private ResultActions verify(Account account, String code) throws Exception {
        return send(account, "/verify", "{\"code\":\"" + code + "\"}");
    }

    private ResultActions requestCode(Account account) throws Exception {
        return send(account, "/verification", "");
    }

    private void ageLiveCode(Account account, long seconds) {
        jdbcTemplate.update(
                "UPDATE identity.email_code SET created_at = created_at - make_interval(secs => ?)"
                        + " WHERE user_id = ? AND purpose = 'VERIFY'",
                seconds, account.id());
    }

    private Map<String, Object> liveCodeRow(Account account) {
        return jdbcTemplate.queryForMap(
                "SELECT attempts, used_at, expires_at, created_at, code_hash FROM identity.email_code"
                        + " WHERE user_id = ? AND purpose = 'VERIFY' AND used_at IS NULL", account.id());
    }

    private Object emailVerifiedAt(Account account) {
        return jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM identity.user_account WHERE id = ?", Object.class, account.id());
    }

    /** AC-10: mã đúng đặt email_verified_at, ghi nhật ký EMAIL_VERIFIED, mã không dùng lại được. */
    @Test
    void correctCode() throws Exception {
        Account account = newAccount();
        String code = latestCode(account);
        assertThat(emailVerifiedAt(account)).isNull();

        verify(account, code).andExpect(status().isNoContent());

        assertThat(emailVerifiedAt(account)).isNotNull();
        while (outboxRelay.relayBatch() > 0) {
            // làm hiện nhật ký
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM audit.audit_event WHERE action = 'EMAIL_VERIFIED' AND target_id = ?",
                        Integer.class, account.id().toString()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT used_at FROM identity.email_code WHERE user_id = ? AND purpose = 'VERIFY'",
                        Object.class, account.id()))
                .isNotNull();

        verify(account, code)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
    }

    /** AC-11: mã sai nhận 422 EMAIL_CODE_INVALID, remainingAttempts giảm dần 4, 3, 2, 1, 0. */
    @Test
    void wrongCodeCountsDown() throws Exception {
        Account account = newAccount();
        String wrong = wrongCodeFor(latestCode(account));

        for (int remaining = 4; remaining >= 0; remaining--) {
            verify(account, wrong)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                    .andExpect(jsonPath("$.remainingAttempts").value(remaining));
        }
        assertThat(emailVerifiedAt(account)).isNull();
    }

    /** AC-12: hết hạn sau 10 phút hoặc đã dùng nhận 422 EMAIL_CODE_EXPIRED; biên 10 phút trừ 1 giây và đúng 10 phút. */
    @Test
    void expiredOrUsedCode() throws Exception {
        clock.freeze();
        Account almost = newAccount();
        String almostCode = latestCode(almost);
        Account expired = newAccount();
        String expiredCode = latestCode(expired);

        // Hạn mặc định là 10 phút kể từ lúc gửi.
        long seconds = jdbcTemplate.queryForObject(
                "SELECT extract(epoch FROM (expires_at - created_at))::bigint FROM identity.email_code"
                        + " WHERE user_id = ?", Long.class, expired.id());
        assertThat(seconds).isEqualTo(600L);
        assertThat(liveCodeRow(expired).get("attempts")).isEqualTo(0);

        // Còn 1 giây: dùng được. Đúng 10 phút: đã hết hạn, kể cả mã đúng.
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        verify(almost, almostCode).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(1));
        verify(expired, expiredCode)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(emailVerifiedAt(expired)).isNull();
    }

    /** AC-13: sau 5 lần sai, lần nhập tiếp theo nhận EXPIRED kể cả khi mã đúng; email chưa được xác minh. */
    @Test
    void deadAfterFiveWrong() throws Exception {
        Account account = newAccount();
        String code = latestCode(account);
        String wrong = wrongCodeFor(code);
        for (int i = 0; i < 5; i++) {
            verify(account, wrong).andExpect(status().isUnprocessableEntity());
        }

        verify(account, code)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(emailVerifiedAt(account)).isNull();
        assertThat(liveCodeRow(account).get("attempts")).isEqualTo(5);
        // Lần nhập thứ 6 không làm tăng bộ đếm.
        verify(account, wrong).andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(liveCodeRow(account).get("attempts")).isEqualTo(5);
    }

    /** AC-14: gửi lại trước 60 giây nhận 429 kèm retryAfter, mã cũ vẫn dùng được; sau 60 giây nhận 202, mã cũ mất hiệu lực. */
    @Test
    void resendCooldown() throws Exception {
        clock.freeze();
        Account early = newAccount();
        String earlyCode = latestCode(early);
        MvcResult limited = requestCode(early)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        long retryAfter = objectMapper.readTree(limited.getResponse().getContentAsString()).path("retryAfter").asLong();
        assertThat(retryAfter).isEqualTo(60L);
        assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo(Long.toString(retryAfter));
        assertThat(mailOutbox.all(early.email(), MailKind.EMAIL_VERIFY)).hasSize(1);
        verify(early, earlyCode).andExpect(status().isNoContent());

        Account later = newAccount();
        String oldCode = latestCode(later);
        // Giây 59: còn phải chờ đúng 1 giây, mã cũ vẫn là mã đang chờ nhập.
        clock.advance(Duration.ofSeconds(59));
        requestCode(later).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.retryAfter").value(1));
        verify(later, wrongCodeFor(oldCode)).andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
        // Giây 60: được gửi, mã cũ mất hiệu lực (mã mới khác mã cũ để phép thử không phụ thuộc may rủi).
        String newCode = wrongCodeFor(oldCode);
        doReturn(newCode).when(generator).next();
        clock.advance(Duration.ofSeconds(1));
        requestCode(later).andExpect(status().isAccepted());
        assertThat(mailOutbox.all(later.email(), MailKind.EMAIL_VERIFY)).hasSize(2);
        assertThat(latestCode(later)).isEqualTo(newCode);
        verify(later, oldCode)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                .andExpect(jsonPath("$.remainingAttempts").value(4));
        verify(later, newCode).andExpect(status().isNoContent());
    }

    /** AC-14 (bổ sung): mã cũ bị thay hẳn, không còn nhập được dù nhập đúng (cố định mã để hai mã khác nhau). */
    @Test
    void resendInvalidatesOldCode() throws Exception {
        Account account = newAccount();
        String oldCode = latestCode(account);
        ageLiveCode(account, 61);
        String replacement = wrongCodeFor(oldCode);
        doReturn(replacement).when(generator).next();

        requestCode(account).andExpect(status().isAccepted());

        verify(account, oldCode)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                .andExpect(jsonPath("$.remainingAttempts").value(4));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM identity.email_code WHERE user_id = ? AND purpose = 'VERIFY'"
                                + " AND used_at IS NULL", Integer.class, account.id()))
                .isEqualTo(1);
        verify(account, replacement).andExpect(status().isNoContent());
    }

    /** AC-15: lần gửi thứ 6 trong ngày nhận 429 kèm retryAfter, không có thư mới; mã đầu rời cửa sổ đúng sau 24 giờ. */
    @Test
    void dailySendLimit() throws Exception {
        clock.freeze();
        Instant first = clock.instant();
        Account account = newAccount();
        // Thư đăng ký là mã thứ nhất; gửi lại bốn lần nữa (cách nhau 61 giây) là mã thứ 5.
        for (int sent = 2; sent <= 5; sent++) {
            clock.advance(Duration.ofSeconds(61));
            requestCode(account).andExpect(status().isAccepted());
        }
        assertThat(mailOutbox.all(account.email(), MailKind.EMAIL_VERIFY)).hasSize(5);

        clock.advance(Duration.ofSeconds(61));
        MvcResult sixth = requestCode(account)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        JsonNode body = objectMapper.readTree(sixth.getResponse().getContentAsString());
        long untilFirstLeaves = Duration.between(clock.instant(), first.plus(Duration.ofHours(24))).getSeconds();
        assertThat(body.path("retryAfter").asLong()).isEqualTo(untilFirstLeaves);
        assertThat(sixth.getResponse().getHeader("Retry-After")).isEqualTo(body.path("retryAfter").asText());
        assertThat(mailOutbox.all(account.email(), MailKind.EMAIL_VERIFY)).hasSize(5);

        // Một giây trước khi mã đầu rời cửa sổ 24 giờ vẫn bị chặn; đúng lúc đó thì được gửi tiếp.
        clock.advance(Duration.ofSeconds(untilFirstLeaves - 1));
        requestCode(account).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.retryAfter").value(1));
        clock.advance(Duration.ofSeconds(1));
        requestCode(account).andExpect(status().isAccepted());
        assertThat(mailOutbox.all(account.email(), MailKind.EMAIL_VERIFY)).hasSize(6);
    }

    /** AC-16: chỉ có HMAC-SHA256 trong cơ sở dữ liệu, phản hồi và log không chứa mã, chỉ một mã còn hiệu lực. */
    @Test
    void storesOnlyHmacAndOneLiveCode(CapturedOutput output) throws Exception {
        Account account = newAccount();
        String code = "271828";
        ageLiveCode(account, 61);
        doReturn(code).when(generator).next();

        MvcResult sent = requestCode(account).andExpect(status().isAccepted()).andReturn();

        assertThat(sent.getResponse().getContentAsString()).doesNotContain(code);
        assertThat(mailOutbox.require(account.email(), MailKind.EMAIL_VERIFY).text()).contains(code);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM identity.email_code WHERE user_id = ? AND purpose = 'VERIFY'", account.id());
        assertThat(rows).hasSize(2);
        for (Map<String, Object> row : rows) {
            String hash = (String) row.get("code_hash");
            assertThat(hash).matches("[0-9a-f]{64}");
            assertThat(row.values().stream().map(String::valueOf)).doesNotContain(code);
        }
        Map<String, Object> live = liveCodeRow(account);
        assertThat(live.get("code_hash"))
                .isEqualTo(hasher.hash(account.id(), EmailCodePurpose.VERIFY, code));
        assertThat(rows.stream().filter(r -> r.get("used_at") == null)).hasSize(1);

        verify(account, code).andExpect(status().isNoContent());
        verify(account, wrongCodeFor(code)).andExpect(status().isUnprocessableEntity());
        assertThat(output.getAll()).doesNotContain(code);
    }

    /** AC-17: không có phiên nhận 401; mã của tài khoản này không dùng được cho tài khoản kia. */
    @Test
    void requiresSessionAndOwnCode() throws Exception {
        for (String path : List.of("/verification", "/verify", "/change", "/change/confirm")) {
            mockMvc.perform(post("/api/v1/me/email" + path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"123456\",\"newEmail\":\"x@thi360.vn\",\"currentPassword\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // Mã cố định khác nhau cho từng tài khoản: phép thử "mã của A không dùng được cho B" không phụ thuộc may rủi.
        doReturn("111111").when(generator).next();
        Account a = newAccount();
        doReturn("222222").when(generator).next();
        Account b = newAccount();
        String codeOfA = latestCode(a);
        String codeOfB = latestCode(b);
        assertThat(codeOfA).isEqualTo("111111");
        assertThat(codeOfB).isEqualTo("222222");

        // B nhập mã của A: sai, và không đổi gì ở A.
        verify(b, codeOfA)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
        assertThat(emailVerifiedAt(a)).isNull();
        assertThat(liveCodeRow(a).get("attempts")).isEqualTo(0);
        assertThat(emailVerifiedAt(b)).isNull();
        // Mã đúng của mỗi bên vẫn dùng được cho chính bên đó.
        verify(a, codeOfA).andExpect(status().isNoContent());
        verify(b, codeOfB).andExpect(status().isNoContent());

        // Tài khoản realm master không có hồ sơ học viên.
        for (String path : List.of("/verification", "/verify", "/change", "/change/confirm")) {
            mockMvc.perform(post("/api/v1/me/email" + path)
                            .with(asPlatformAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"123456\",\"newEmail\":\"x@thi360.vn\",\"currentPassword\":\"x\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * R1-09: gửi mã xác minh thành công ghi đúng một sự kiện kiểm toán (không có mã, HMAC hay nội dung thư); bị giới hạn
     * thì không ghi gì thêm.
     */
    @Test
    void verificationIssuanceIsAuditedOnlyWhenSent() throws Exception {
        clock.freeze();
        Account account = newAccount();
        assertThat(auditCount("EMAIL_VERIFICATION_REQUESTED", account)).isZero();

        requestCode(account).andExpect(status().isTooManyRequests());
        assertThat(auditCount("EMAIL_VERIFICATION_REQUESTED", account)).isZero();

        clock.advance(Duration.ofSeconds(60));
        doReturn("314159").when(generator).next();
        requestCode(account).andExpect(status().isAccepted());
        assertThat(auditCount("EMAIL_VERIFICATION_REQUESTED", account)).isEqualTo(1);
        requestCode(account).andExpect(status().isTooManyRequests());
        assertThat(auditCount("EMAIL_VERIFICATION_REQUESTED", account)).isEqualTo(1);

        String payload = jdbcTemplate.queryForObject(
                "SELECT coalesce(\"after\"::text, '') || coalesce(\"before\"::text, '') FROM audit.audit_event"
                        + " WHERE action = 'EMAIL_VERIFICATION_REQUESTED' AND target_id = ?",
                String.class, account.id().toString());
        assertThat(payload).doesNotContain("314159").doesNotContain(hasher.hash(account.id(), EmailCodePurpose.VERIFY, "314159"));
    }

    /**
     * R1-10: phiên cookie thật do đăng ký cấp (không phải principal chèn sẵn) gọi API email ghi: thiếu XSRF thì 403 và
     * không đổi gì; đủ XSRF thì thành công. Cookie đến từ chính phản hồi đăng ký.
     */
    @Test
    void registrationIssuedCookieSessionNeedsXsrfOnEmailWrites() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "verify.cookie." + suffix;
        String email = username + "@thi360.vn";
        MvcResult registered = support.register(support.body(username, email), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated())
                .andReturn();
        Cookie access = registered.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrf = registered.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        assertThat(access).isNotNull();
        assertThat(xsrf).isNotNull();
        Account account = new Account(support.accountId(username), username, email);
        String code = latestCode(account);

        // Không có tiêu đề XSRF: bị chặn, mã chưa bị đụng tới.
        mockMvc.perform(post("/api/v1/me/email/verify")
                        .cookie(access, xsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(emailVerifiedAt(account)).isNull();
        assertThat(liveCodeRow(account).get("attempts")).isEqualTo(0);
        // XSRF không khớp cookie cũng bị chặn.
        mockMvc.perform(post("/api/v1/me/email/verify")
                        .cookie(access, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue() + "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isForbidden());

        // Đủ XSRF: thành công với chính phiên đó.
        mockMvc.perform(post("/api/v1/me/email/verify")
                        .cookie(access, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(emailVerifiedAt(account)).isNotNull();
    }

    private int auditCount(String action, Account account) {
        while (outboxRelay.relayBatch() > 0) {
            // làm hiện nhật ký
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE action = ? AND target_id = ?",
                Integer.class, action, account.id().toString());
        return count == null ? 0 : count;
    }

    /** AC-18: tài khoản chưa xác minh email vẫn đăng nhập được (E18). */
    @Test
    void unverifiedCanLogin() throws Exception {
        Account account = newAccount();
        assertThat(emailVerifiedAt(account)).isNull();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, SelfRegistrationSupport.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + account.username() + "\",\"password\":\""
                                + SelfRegistrationSupport.PASSWORD + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, SelfRegistrationSupport.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + account.email() + "\",\"password\":\""
                                + SelfRegistrationSupport.PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    /** Câu hỏi mở 5 (tạm): tài khoản chưa có email gọi /verification nhận 422. */
    @Test
    void accountWithoutEmailCannotRequestVerification() throws Exception {
        Account account = newAccount();
        jdbcTemplate.update(
                "UPDATE identity.user_account SET email = NULL, email_normalized = NULL WHERE id = ?", account.id());

        requestCode(account)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));
        verify(account, "123456")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
    }

    /** Mã gửi cho email cũ không còn dùng được sau khi admin đổi email của tài khoản. */
    @Test
    void codeIsBoundToTheCurrentEmail() throws Exception {
        Account account = newAccount();
        String code = latestCode(account);
        jdbcTemplate.update(
                "UPDATE identity.user_account SET email = ?, email_normalized = ? WHERE id = ?",
                "other." + account.email(), ("other." + account.email()).toLowerCase(), account.id());

        verify(account, code)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(emailVerifiedAt(account)).isNull();
    }

    /** AC-28: hai yêu cầu cùng mã đúng: một thành công, một EXPIRED; lần sai đồng thời không vượt 5 lần. */
    @Test
    void concurrentAttempts() throws Exception {
        Account account = newAccount();
        String code = latestCode(account);
        List<Callable<MvcResult>> twin = List.of(
                () -> verify(account, code).andReturn(), () -> verify(account, code).andReturn());
        List<MvcResult> results = runConcurrently(twin);
        assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(204, 422);
        MvcResult loser = results.stream().filter(r -> r.getResponse().getStatus() == 422).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(loser.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("EMAIL_CODE_EXPIRED");
        assertThat(emailVerifiedAt(account)).isNotNull();

        Account target = newAccount();
        String realCode = latestCode(target);
        String wrong = wrongCodeFor(realCode);
        List<Callable<MvcResult>> wrongCalls = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            wrongCalls.add(() -> verify(target, wrong).andReturn());
        }
        List<MvcResult> wrongResults = runConcurrently(wrongCalls);
        TreeSet<Integer> remainings = new TreeSet<>();
        int expired = 0;
        for (MvcResult result : wrongResults) {
            assertThat(result.getResponse().getStatus()).isEqualTo(422);
            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            if ("EMAIL_CODE_INVALID".equals(json.path("code").asText())) {
                remainings.add(json.path("remainingAttempts").asInt());
            } else {
                assertThat(json.path("code").asText()).isEqualTo("EMAIL_CODE_EXPIRED");
                expired++;
            }
        }
        assertThat(remainings).containsExactly(0, 1, 2, 3, 4);
        assertThat(expired).isEqualTo(4);
        assertThat(liveCodeRow(target).get("attempts")).isEqualTo(5);
        verify(target, realCode)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
