package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.application.ProvisioningReconcilerJob;
import vn.unicom.thi360.identity.application.RedirectResolver;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.SecurityProperties;
import vn.unicom.thi360.support.MailOutbox;

/** ID-1 AC-1 đến AC-7, AC-9: {@code POST /api/v1/auth/register} (TM 5.1, PBI-06). */
class SelfRegistrationIT extends AbstractKeycloakIntegrationTest {

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
    private ProvisioningReconcilerJob reconcilerJob;

    @Autowired
    private RedirectResolver redirectResolver;

    @Autowired
    private SecurityProperties securityProperties;

    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;

    private SelfRegistrationSupport support;
    private MailOutbox mailOutbox;

    @BeforeEach
    void setUp() {
        support = new SelfRegistrationSupport(mockMvc, jdbcTemplate, keycloakClient, redisTemplate, objectMapper);
        mailOutbox = new MailOutbox(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(keycloakClient);
        support.cleanup();
    }

    /** AC-1: 20 yêu cầu cùng tên đăng nhập chạy đồng thời. */
    @Test
    void twentyConcurrentSameUsername() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "conc." + suffix;
        List<String> emails = new ArrayList<>();
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String email = "conc" + i + "." + suffix + "@thi360.vn";
            emails.add(email);
            var request = support.request(support.body(username, email), SelfRegistrationSupport.freshIp());
            calls.add(() -> mockMvc.perform(request).andReturn());
        }

        List<MvcResult> results = runConcurrently(calls);

        long created = results.stream().filter(r -> r.getResponse().getStatus() == 201).count();
        List<MvcResult> conflicts = results.stream().filter(r -> r.getResponse().getStatus() == 409).toList();
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).hasSize(19);
        for (MvcResult conflict : conflicts) {
            assertThat(objectMapper.readTree(conflict.getResponse().getContentAsString()).path("code").asText())
                    .isEqualTo("USERNAME_TAKEN");
        }
        assertThat(support.liveAccountCount(username)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM identity.user_account WHERE username_normalized = ? AND status = 2",
                        Integer.class, username))
                .as("no PROVISIONING row is left")
                .isEqualTo(1);
        assertThat(KEYCLOAK.getKeycloakAdminClient().realm("thi-360").users().searchByUsername(username, true))
                .hasSize(1);
        // Yêu cầu thua không để lại tài khoản theo email của chúng.
        long accountsByEmail = emails.stream().filter(e -> support.liveAccountCountByEmail(e) > 0).count();
        assertThat(accountsByEmail).isEqualTo(1);
    }

    /** AC-2: email trùng, kể cả hai yêu cầu đồng thời khác tên đăng nhập. */
    @Test
    void duplicateEmail() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String email = "dup." + suffix + "@thi360.vn";
        String first = "dupa." + suffix;
        support.register(support.body(first, email), SelfRegistrationSupport.freshIp()).andExpect(status().isCreated());

        String second = "dupb." + suffix;
        support.register(support.body(second, email.toUpperCase()), SelfRegistrationSupport.freshIp())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        assertThat(support.liveAccountCount(second)).isZero();
        assertThat(support.keycloakHasUser(second)).isFalse();

        // Hai yêu cầu đồng thời, khác tên đăng nhập, cùng email.
        String raceEmail = "race." + suffix + "@thi360.vn";
        String raceA = "racea." + suffix;
        String raceB = "raceb." + suffix;
        var requestA = support.request(support.body(raceA, raceEmail), SelfRegistrationSupport.freshIp());
        var requestB = support.request(support.body(raceB, raceEmail), SelfRegistrationSupport.freshIp());
        List<MvcResult> results = runConcurrently(List.of(
                () -> mockMvc.perform(requestA).andReturn(), () -> mockMvc.perform(requestB).andReturn()));

        assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(201, 409);
        MvcResult loser = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(loser.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("EMAIL_TAKEN");
        assertThat(support.liveAccountCountByEmail(raceEmail)).isEqualTo(1);
        assertThat(support.liveAccountCount(raceA) + support.liveAccountCount(raceB)).isEqualTo(1);
        assertThat(support.keycloakHasUser(raceA) ^ support.keycloakHasUser(raceB)).isTrue();
    }

    /** AC-3: tài khoản ACTIVE, nguồn SELF_REGISTERED, vai trò Học viên, điều khoản, ngôn ngữ, nhật ký, một thư. */
    @Test
    void createsStudentAccount() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "Reg.Student." + suffix;
        String email = "Reg.Student." + suffix + "@Thi360.vn";

        support.register(support.body(username, email), SelfRegistrationSupport.freshIp(),
                        new Cookie("thi360_locale", "en"))
                .andExpect(status().isCreated());

        UUID id = support.accountId(username);
        assertThat(id).isNotNull();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, source, preferred_locale, terms_version, terms_accepted_at, email_verified_at,"
                        + " email_normalized, must_change_password, provisioning_role_ids, full_name"
                        + " FROM identity.user_account WHERE id = ?", id);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("source")).intValue()).isEqualTo(3);
        assertThat(row.get("preferred_locale")).isEqualTo("en");
        assertThat(row.get("terms_version")).isEqualTo(SelfRegistrationSupport.TERMS_VERSION);
        assertThat(row.get("terms_accepted_at")).isNotNull();
        assertThat(row.get("email_verified_at")).isNull();
        assertThat(row.get("email_normalized")).isEqualTo(email.toLowerCase());
        assertThat(row.get("must_change_password")).isEqualTo(false);
        assertThat(row.get("provisioning_role_ids")).isNull();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code_normalized FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id"
                                + " WHERE ur.user_id = ?", String.class, id))
                .containsExactly("student");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM identity.user_identity WHERE user_id = ?", Integer.class, id))
                .isEqualTo(1);

        while (outboxRelay.relayBatch() > 0) {
            // làm hiện nhật ký trong audit.audit_event
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM audit.audit_event WHERE action = 'USER_SELF_REGISTERED' AND target_id = ?",
                        Integer.class, id.toString()))
                .isEqualTo(1);

        List<MailOutbox.Mail> mails = mailOutbox.all(email, MailKind.EMAIL_VERIFY);
        assertThat(mails).hasSize(1);
        assertThat(mails.getFirst().locale()).isEqualTo("en");
        assertThat(mails.getFirst().userId()).isEqualTo(id);
        assertThat(mailOutbox.count(email)).isEqualTo(1);
        assertThat(SIX_DIGITS.matcher(mails.getFirst().text()).find()).isTrue();
    }

    /** Ngôn ngữ mặc định khi không có cookie hoặc cookie lạ. */
    @Test
    void localeDefaultsToVietnamese() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "locale.def." + suffix;
        support.register(support.body(username, username + "@thi360.vn"), SelfRegistrationSupport.freshIp(),
                        new Cookie("thi360_locale", "fr"))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT preferred_locale FROM identity.user_account WHERE id = ?",
                        String.class, support.accountId(username)))
                .isEqualTo("vi");
    }

    /** AC-4: cookie phiên HttpOnly dùng được ngay; callbackUrl hợp lệ được giữ, lạ thì về /home. */
    @Test
    void createsSessionAndResolvesRedirect() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "reg.session." + suffix;

        Map<String, Object> body = support.body(username, username + "@thi360.vn");
        body.put("callbackUrl", "/checkout/ABC123");
        MvcResult result = support.register(body, SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated())
                .andReturn();

        String expectedCallback = redirectResolver.validateCallbackUrl("/checkout/ABC123").orElseThrow();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("redirectTo").asText())
                .isEqualTo(expectedCallback);
        Cookie access = result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie refresh = result.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        Cookie xsrf = result.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        assertThat(xsrf).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SelfRegistrationSupport.PASSWORD);

        mockMvc.perform(get("/api/v1/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.realm").value("APP"));

        String home = redirectResolver.resolveHomeUrl(Access.EXAM);
        for (String bad : new String[] {"https://evil.example/steal", "//evil.example/x", null, ""}) {
            String other = "reg.redirect." + SelfRegistrationSupport.suffix();
            Map<String, Object> otherBody = support.body(other, other + "@thi360.vn");
            otherBody.put("callbackUrl", bad);
            support.register(otherBody, SelfRegistrationSupport.freshIp())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.redirectTo").value(home));
        }
    }

    /** AC-5: đầu vào sai nhận lỗi theo trường; không có tài khoản, không có thư. */
    @Test
    void rejectsInvalidInput() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        record Case(String name, String field, java.util.function.Consumer<Map<String, Object>> mutate) {
        }
        List<Case> cases = List.of(
                new Case("username three chars", "username", b -> b.put("username", "abc")),
                new Case("username 31 chars", "username", b -> b.put("username", "a".repeat(31))),
                new Case("username with space", "username", b -> b.put("username", "ab cd ef")),
                new Case("username reserved", "username", b -> b.put("username", "admin")),
                new Case("username invalid chars", "username", b -> b.put("username", "ab#cd$ef")),
                new Case("password weak", "password", b -> b.put("password", "weakpass")),
                new Case("password too short", "password", b -> b.put("password", "Aa1!")),
                new Case("password equals username", "password", b -> {
                    b.put("username", "Same.Pass.1");
                    b.put("password", "Same.Pass.1");
                }),
                new Case("terms not accepted", "acceptTerms", b -> b.put("acceptTerms", false)),
                new Case("terms missing", "acceptTerms", b -> b.remove("acceptTerms")),
                new Case("terms version blank", "termsVersion", b -> b.put("termsVersion", "  ")),
                new Case("full name blank", "fullName", b -> b.put("fullName", " ")),
                new Case("email invalid", "email", b -> b.put("email", "not-an-email")),
                new Case("email blank", "email", b -> b.put("email", "")));

        int n = 0;
        for (Case c : cases) {
            String username = "bad." + (n++) + "." + suffix;
            String email = "bad" + n + "." + suffix + "@thi360.vn";
            Map<String, Object> body = support.body(username, email);
            c.mutate().accept(body);
            if (body.get("username") != null) {
                support.track(String.valueOf(body.get("username")), null);
            }
            MvcResult result = support.register(body, SelfRegistrationSupport.freshIp())
                    .andExpect(status().isBadRequest())
                    .andReturn();
            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            List<String> fields = new ArrayList<>();
            json.path("fieldErrors").forEach(fe -> fields.add(fe.path("field").asText()));
            assertThat(fields).as(c.name()).contains(c.field());
            assertThat(result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE)).as(c.name()).isNull();
            assertThat(support.liveAccountCountByEmail(email)).as(c.name()).isZero();
            assertThat(mailOutbox.count(email)).as(c.name()).isZero();
            String attempted = String.valueOf(body.get("username"));
            assertThat(support.liveAccountCount(attempted)).as(c.name()).isZero();
            assertThat(support.keycloakHasUser(attempted)).as(c.name()).isFalse();
        }

        // Nhiều lỗi cùng lúc trả đủ trong một lần.
        Map<String, Object> many = support.body("ab", "bad@@");
        many.put("password", "x");
        many.put("acceptTerms", false);
        MvcResult result = support.register(many, SelfRegistrationSupport.freshIp())
                .andExpect(status().isBadRequest())
                .andReturn();
        List<String> fields = new ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString())
                .path("fieldErrors").forEach(fe -> fields.add(fe.path("field").asText()));
        assertThat(fields).contains("username", "password", "email", "acceptTerms");
    }

    /** Biên tên đăng nhập: 4 và 30 ký tự được nhận, 3 và 31 bị từ chối. */
    @Test
    void usernameLengthBoundaries() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String four = "u" + suffix.substring(0, 3);
        String thirty = ("w" + suffix + "0123456789abcdefghijklmnop").substring(0, 30);
        assertThat(four).hasSize(4);
        assertThat(thirty).hasSize(30);
        support.register(support.body(four, "b4." + suffix + "@thi360.vn"), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
        support.register(support.body(thirty, "b30." + suffix + "@thi360.vn"), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
        support.register(support.body("u" + suffix.substring(0, 2), "b3." + suffix + "@thi360.vn"),
                        SelfRegistrationSupport.freshIp())
                .andExpect(status().isBadRequest());
        support.register(support.body(thirty + "x", "b31." + suffix + "@thi360.vn"), SelfRegistrationSupport.freshIp())
                .andExpect(status().isBadRequest());
    }

    /** AC-6: Keycloak lỗi khi tạo user hoặc đặt mật khẩu thì không còn tài khoản dở dang. */
    @Test
    void keycloakFailureLeavesNothing() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();

        // (a) tạo user Keycloak lỗi
        String createFail = "kc.create." + suffix;
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null))
                .when(keycloakClient).createUser(any(CreateUserCommand.class));
        MvcResult a = support.register(support.body(createFail, createFail + "@thi360.vn"),
                        SelfRegistrationSupport.freshIp())
                .andExpect(status().isBadGateway())
                .andReturn();
        assertNothingLeft(createFail, a);
        Mockito.reset(keycloakClient);

        // (b) đặt mật khẩu lỗi (Keycloak đã tạo user)
        String passwordFail = "kc.pass." + suffix;
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null))
                .when(keycloakClient).setPassword(any(UUID.class), anyString(), anyBoolean());
        MvcResult b = support.register(support.body(passwordFail, passwordFail + "@thi360.vn"),
                        SelfRegistrationSupport.freshIp())
                .andExpect(status().isBadGateway())
                .andReturn();
        assertNothingLeft(passwordFail, b);
        Mockito.reset(keycloakClient);

        // (c) Keycloak từ chối mật khẩu theo chính sách của realm
        String policyFail = "kc.policy." + suffix;
        doThrow(new ValidationException("PASSWORD_WEAK", null,
                List.of(new FieldError("password", "PASSWORD_WEAK", "weak"))))
                .when(keycloakClient).setPassword(any(UUID.class), anyString(), anyBoolean());
        MvcResult c = support.register(support.body(policyFail, policyFail + "@thi360.vn"),
                        SelfRegistrationSupport.freshIp())
                .andExpect(status().isBadRequest())
                .andReturn();
        assertNothingLeft(policyFail, c);
        Mockito.reset(keycloakClient);

        // Sau lỗi, cùng tên đăng nhập và email đăng ký lại được ngay (không bị dòng dở dang chặn).
        support.register(support.body(createFail, createFail + "@thi360.vn"), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
    }

    private void assertNothingLeft(String username, MvcResult result) {
        assertThat(support.liveAccountCount(username)).as("user_account of %s", username).isZero();
        assertThat(support.keycloakHasUser(username)).as("keycloak user of %s", username).isFalse();
        assertThat(result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE)).isNull();
        assertThat(result.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE)).isNull();
        assertThat(mailOutbox.count(username + "@thi360.vn")).isZero();
    }

    /** Câu hỏi mở 1 (tạm): tạo phiên lỗi sau khi tài khoản đã tạo xong thì giữ tài khoản, trả lỗi, đăng nhập lại được. */
    @Test
    void sessionFailureKeepsAccountAndUserCanLogin() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "reg.nosession." + suffix;
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null))
                .when(keycloakClient).passwordGrant(anyString(), anyString(), anyString());

        MvcResult result = support.register(support.body(username, username + "@thi360.vn"),
                        SelfRegistrationSupport.freshIp())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(500);
        assertThat(result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE)).isNull();
        assertThat(support.liveAccountCount(username)).isEqualTo(1);
        Mockito.reset(keycloakClient);
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, SelfRegistrationSupport.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + SelfRegistrationSupport.PASSWORD
                                + "\"}"))
                .andExpect(status().isOk());
    }

    /** Tiến trình chết sau khi đặt mật khẩu Keycloak: dòng PROVISIONING được job đối soát hoàn tất, không bị xóa. */
    private static final class SimulatedCrash extends Error {
        SimulatedCrash() {
            super("simulated process death after the Keycloak password was set");
        }
    }

    /** AC-7: dòng PROVISIONING của đăng ký kẹt quá 10 phút được hoàn tất với vai trò Học viên. */
    @Test
    void reconcilerCompletesStuckRegistration() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "reg.stuck." + suffix;
        String email = username + "@thi360.vn";
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new SimulatedCrash();
        }).when(keycloakClient).setPassword(any(UUID.class), anyString(), anyBoolean());

        // Error không bị bắt ở tầng dịch vụ (không có bước bù, như tiến trình chết); Spring MVC trả 500.
        support.register(support.body(username, email), SelfRegistrationSupport.freshIp())
                .andExpect(status().isInternalServerError());
        Mockito.reset(keycloakClient);

        UUID provisionalId = support.accountId(username);
        assertThat(provisionalId).isNotNull();
        Map<String, Object> stuck = jdbcTemplate.queryForMap(
                "SELECT status, provisioning_role_ids, source, terms_version FROM identity.user_account WHERE id = ?",
                provisionalId);
        assertThat(((Number) stuck.get("status")).intValue()).isEqualTo(1);
        assertThat(stuck.get("provisioning_role_ids")).isNotNull();
        assertThat(((Number) stuck.get("source")).intValue()).isEqualTo(3);
        assertThat(stuck.get("terms_version")).isEqualTo(SelfRegistrationSupport.TERMS_VERSION);
        jdbcTemplate.update(
                "UPDATE identity.user_account SET created_at = now() - interval '11 minutes' WHERE id = ?",
                provisionalId);

        ProvisioningReconcilerJob.ReconcileResult reconciled = reconcilerJob.reconcile();

        assertThat(reconciled.promoted()).isGreaterThanOrEqualTo(1);
        UUID keycloakId = keycloakClient.findUserIdByUsername(username).orElseThrow();
        Map<String, Object> done = jdbcTemplate.queryForMap(
                "SELECT status, deleted_at, provisioning_role_ids FROM identity.user_account WHERE id = ?", keycloakId);
        assertThat(((Number) done.get("status")).intValue()).isEqualTo(2);
        assertThat(done.get("deleted_at")).isNull();
        assertThat(done.get("provisioning_role_ids")).isNull();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code_normalized FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id"
                                + " WHERE ur.user_id = ?", String.class, keycloakId))
                .containsExactly("student");
        // Tài khoản đã hoàn tất đăng nhập được.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, SelfRegistrationSupport.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + SelfRegistrationSupport.PASSWORD
                                + "\"}"))
                .andExpect(status().isOk());
    }

    /** AC-9: không cần cookie; cookie cũ còn sót, không có XSRF, vẫn đăng ký được (không 401, không 403 CSRF_INVALID). */
    @Test
    void publicWithStaleCookies() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();

        // Không có cookie nào.
        String plain = "reg.plain." + suffix;
        support.register(support.body(plain, plain + "@thi360.vn"), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());

        // Token thật của một phiên đã đăng xuất, cộng cookie rác: cả hai đều cũ, không có XSRF.
        String previous = "reg.prev." + suffix;
        support.register(support.body(previous, previous + "@thi360.vn"), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
        var tokens = keycloakClient.passwordGrant("thi-360", previous, SelfRegistrationSupport.PASSWORD);
        keycloakClient.logout("thi-360", tokens.refreshToken());
        Cookie staleAccess = new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, tokens.accessToken());
        Cookie staleRefresh = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, tokens.refreshToken());

        String withCookies = "reg.stale." + suffix;
        MvcResult result = support.register(support.body(withCookies, withCookies + "@thi360.vn"),
                        SelfRegistrationSupport.freshIp(), staleAccess, staleRefresh)
                .andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);

        String garbage = "reg.garbage." + suffix;
        support.register(support.body(garbage, garbage + "@thi360.vn"), SelfRegistrationSupport.freshIp(),
                        new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "expired.token.value"),
                        new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "old-refresh"))
                .andExpect(status().isCreated());
    }

    /** E19: ngưỡng mặc định của đăng ký là 30 mỗi giờ và 100 mỗi ngày. */
    @Test
    void defaultRegisterThresholdsFollowE19() {
        SecurityProperties.RateLimitProperties rateLimit = securityProperties.rateLimit();
        assertThat(rateLimit.registerPerIpHourLimit()).isEqualTo(30);
        assertThat(rateLimit.registerPerIpHourWindow()).isEqualTo(Duration.ofHours(1));
        assertThat(rateLimit.registerPerIpDayLimit()).isEqualTo(100);
        assertThat(rateLimit.registerPerIpDayWindow()).isEqualTo(Duration.ofHours(24));
    }

    /** Ca mới của "Ảnh hưởng ngoài task": tài khoản vừa đăng ký có trong kết quả lọc nguồn SELF_REGISTERED của admin. */
    @Test
    void adminUserListFiltersSelfRegistered() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "reg.listed." + suffix;
        support.register(support.body(username, username + "@thi360.vn"), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());

        MvcResult listed = mockMvc.perform(get("/api/v1/admin/users")
                        .param("source", "SELF_REGISTERED")
                        .param("q", username)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(listed.getResponse().getContentAsString()).path("items");
        List<String> usernames = new ArrayList<>();
        items.forEach(item -> usernames.add(item.path("username").asText()));
        assertThat(usernames).contains(username);

        MvcResult otherSource = mockMvc.perform(get("/api/v1/admin/users")
                        .param("source", "ADMIN_CREATED")
                        .param("q", username)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(otherSource.getResponse().getContentAsString()).path("items")).isEmpty();
    }

    /** AC-8 (biên mặc định): lần 30 còn qua, lần 31 trong một giờ nhận 429 có Retry-After; IP khác không bị ảnh hưởng. */
    @Test
    void thirtyFirstRegistrationInAnHourIsLimited() throws Exception {
        String ip = SelfRegistrationSupport.freshIp();
        String otherIp = SelfRegistrationSupport.freshIp();
        for (int i = 1; i <= 30; i++) {
            // Thân rỗng: 400 ngay ở máy chủ, vẫn tính vào giới hạn.
            mockMvc.perform(post("/api/v1/auth/register")
                            .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        MvcResult limited = mockMvc.perform(post("/api/v1/auth/register")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();
        long retryAfter = Long.parseLong(limited.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(60L, 3601L);
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(ClientIpResolver.X_FORWARDED_FOR, otherIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        redisTemplate.delete(List.of(
                vn.unicom.thi360.shared.security.RateLimitFilter.REGISTER_KEY_PREFIX + ip,
                vn.unicom.thi360.shared.security.RateLimitFilter.REGISTER_KEY_PREFIX + otherIp));
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
