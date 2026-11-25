package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "thi360.mail.scheduling.enabled=true",
        "thi360.mail.poll-delay=PT0.1S",
        "THI360_SMTP_HOST=127.0.0.1",
        "thi360.mail.tls=none"
})
class MobileLocaleIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";
    private static final String LOGIN_IP = "203.0.113.198";

    private static final GreenMail GREEN_MAIL = new GreenMail(ServerSetup.SMTP.dynamicPort());

    static {
        GREEN_MAIL.start();
        Runtime.getRuntime().addShutdownHook(new Thread(GREEN_MAIL::stop));
    }

    @DynamicPropertySource
    static void registerSmtpPort(DynamicPropertyRegistry registry) {
        registry.add("THI360_SMTP_PORT", () -> GREEN_MAIL.getSmtp().getPort());
    }

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private SelfRegistrationSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new SelfRegistrationSupport(mockMvc, jdbcTemplate, keycloakClient, redisTemplate, objectMapper);
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + LOGIN_IP);
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + LOGIN_IP);
        support.cleanup();
    }

    @Test
    void mobileLoginAcceptLanguageFallbackAndCookiePrecedence() throws Exception {
        UserAccount account = createStudent();
        assertThat(storedLocale(account)).isEqualTo("vi");

        mobileLogin(account.getUsername(), "en-US,en;q=0.9", null).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("en");

        mobileLogin(account.getUsername(), null, null).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("en");

        mobileLogin(account.getUsername(), "fr-FR,de;q=0.8", null).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("en");

        mobileLogin(account.getUsername(), "*", null).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("en");

        mobileLogin(account.getUsername(), "en", new Cookie("thi360_locale", "vi")).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("vi");

        mobileLogin(account.getUsername(), "vi", new Cookie("thi360_locale", "en")).andExpect(status().isOk());
        assertThat(storedLocale(account)).isEqualTo("en");
    }

    @Test
    void registerAndResendDeliverOtpMailInRequestedLocaleAndCookieWins() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String userEn = "mob.locen." + suffix;
        String emailEn = userEn + "@thi360.vn";
        String ip1 = SelfRegistrationSupport.freshIp();

        MvcResult startedEn = mockMvc.perform(support.request(support.body(userEn, emailEn), ip1)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andReturn();
        UUID registrationId = UUID.fromString(objectMapper
                .readTree(startedEn.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("registrationId")
                .asText());

        ReceivedMail firstMail = awaitMessagesFor(emailEn, 1).getFirst();
        assertThat(firstMail.subject()).containsIgnoringCase("verification code");
        assertThat(firstMail.text()).contains("verification code");
        assertThat(firstMail.text()).doesNotContain("Mã xác minh");

        jdbcTemplate.update(
                "UPDATE identity.pending_registration_send SET created_at = created_at - interval '61 seconds'"
                        + " WHERE email_normalized = ?",
                emailEn.toLowerCase());

        mockMvc.perform(post("/api/v1/auth/register/resend")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip1)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("registrationId", registrationId))))
                .andExpect(status().isOk());

        List<ReceivedMail> mailsAfterResend = awaitMessagesFor(emailEn, 2);
        ReceivedMail secondMail = mailsAfterResend.get(1);
        assertThat(secondMail.text()).contains("Mã xác minh");

        String userCookie = "mob.locck." + suffix;
        String emailCookie = userCookie + "@thi360.vn";
        mockMvc.perform(support.request(
                                support.body(userCookie, emailCookie),
                                SelfRegistrationSupport.freshIp(),
                                new Cookie("thi360_locale", "vi"))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        ReceivedMail cookieWinsMail = awaitMessagesFor(emailCookie, 1).getFirst();
        assertThat(cookieWinsMail.text()).contains("Mã xác minh");
        assertThat(cookieWinsMail.subject()).doesNotContain("verification code");
    }

    private org.springframework.test.web.servlet.ResultActions mobileLogin(
            String username, String acceptLanguage, Cookie localeCookie) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/mobile/login")
                .header(ClientIpResolver.X_FORWARDED_FOR, LOGIN_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "identifier", username,
                        "password", PASSWORD)));
        if (acceptLanguage != null) {
            request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        if (localeCookie != null) {
            request.cookie(localeCookie);
        }
        return mockMvc.perform(request);
    }

    private String storedLocale(UserAccount account) {
        return jdbcTemplate.queryForObject(
                "SELECT preferred_locale FROM identity.user_account WHERE id = ?",
                String.class,
                account.getId());
    }

    private UserAccount createStudent() {
        String username = "mob.loc." + UUID.randomUUID().toString().substring(0, 8);
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Mobile Locale", "User", PASSWORD, true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        return userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Mobile Locale",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));
    }

    private record ReceivedMail(String to, String subject, String text) {
    }

    private static List<ReceivedMail> awaitMessagesFor(String recipient, int expectedCount) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> receivedFor(recipient).size() >= expectedCount);
        return receivedFor(recipient);
    }

    private static List<ReceivedMail> receivedFor(String recipient) {
        return Arrays.stream(GREEN_MAIL.getReceivedMessages())
                .map(MobileLocaleIT::parse)
                .filter(mail -> mail.to().equalsIgnoreCase(recipient))
                .toList();
    }

    private static ReceivedMail parse(MimeMessage message) {
        try {
            String[] textHolder = new String[1];
            extractText(message, textHolder);
            String to = ((InternetAddress) message.getAllRecipients()[0]).getAddress();
            return new ReceivedMail(to, message.getSubject(), textHolder[0] == null ? "" : textHolder[0]);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse GreenMail message", ex);
        }
    }

    private static void extractText(Part part, String[] holder) throws Exception {
        if (part.isMimeType("text/plain")) {
            holder[0] = (String) part.getContent();
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart body = multipart.getBodyPart(i);
                extractText(body, holder);
            }
        }
    }
}
