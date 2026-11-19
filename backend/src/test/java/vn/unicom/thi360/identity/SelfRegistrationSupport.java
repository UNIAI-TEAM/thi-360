package vn.unicom.thi360.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

/**
 * Dựng yêu cầu đăng ký và dọn dữ liệu cho test của ID-1 (plan: "dọn DB, Keycloak và khóa Redis ở @AfterEach theo mẫu
 * NoHardDeleteIT"). Mỗi test dùng tên đăng nhập, email có hậu tố ngẫu nhiên và IP riêng.
 */
final class SelfRegistrationSupport {

    static final String PASSWORD = "Valid@1234";
    static final String TERMS_VERSION = "2026-11";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final KeycloakIdentityClient keycloak;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final List<String> usernames = new ArrayList<>();
    private final List<String> emails = new ArrayList<>();
    private final List<String> ips = new ArrayList<>();

    SelfRegistrationSupport(
            MockMvc mockMvc,
            JdbcTemplate jdbc,
            KeycloakIdentityClient keycloak,
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.keycloak = keycloak;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Hậu tố ngẫu nhiên không có chữ số: tên đăng nhập và email xuất hiện trong thân thư, nên chữ số ở đó có thể bị
     * nhận nhầm là mã OTP 6 số khi test tách mã khỏi thư.
     */
    static String suffix() {
        String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        StringBuilder letters = new StringBuilder();
        for (char c : hex.toCharArray()) {
            letters.append(Character.isDigit(c) ? (char) ('g' + (c - '0')) : c);
        }
        return letters.toString();
    }

    /** Một IP riêng cho mỗi lần gọi để giới hạn đăng ký theo IP không lẫn giữa các test. */
    static String freshIp() {
        int a = (int) (Math.random() * 250) + 1;
        int b = (int) (Math.random() * 250) + 1;
        return "198.51." + a + "." + b;
    }

    Map<String, Object> body(String username, String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("fullName", "Nguyễn Văn An");
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("acceptTerms", true);
        body.put("termsVersion", TERMS_VERSION);
        track(username, email);
        return body;
    }

    void track(String username, String email) {
        if (username != null) {
            usernames.add(username.trim().toLowerCase());
        }
        if (email != null) {
            emails.add(email.trim().toLowerCase());
        }
    }

    ResultActions register(Map<String, Object> body, String ip, Cookie... cookies) throws Exception {
        return mockMvc.perform(request(body, ip, cookies));
    }

    MockHttpServletRequestBuilder request(Map<String, Object> body, String ip, Cookie... cookies) throws Exception {
        ips.add(ip);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post("/api/v1/auth/register")
                .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (cookies != null && cookies.length > 0) {
            builder.cookie(cookies);
        }
        return builder;
    }

    UUID accountId(String username) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM identity.user_account WHERE username_normalized = ? AND deleted_at IS NULL",
                UUID.class,
                username.trim().toLowerCase());
        return ids.isEmpty() ? null : ids.getFirst();
    }

    int liveAccountCount(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM identity.user_account WHERE username_normalized = ? AND deleted_at IS NULL",
                Integer.class,
                username.trim().toLowerCase());
        return count == null ? 0 : count;
    }

    int liveAccountCountByEmail(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM identity.user_account WHERE email_normalized = ? AND deleted_at IS NULL",
                Integer.class,
                email.trim().toLowerCase());
        return count == null ? 0 : count;
    }

    boolean keycloakHasUser(String username) {
        return keycloak.findUserIdByUsername(username.trim().toLowerCase()).isPresent();
    }

    void cleanup() {
        for (String username : usernames) {
            for (UUID id : jdbc.queryForList(
                    "SELECT id FROM identity.user_account WHERE username_normalized = ?", UUID.class, username)) {
                deleteAccountRows(id);
            }
            keycloak.findUserIdByUsername(username).ifPresent(id -> {
                try {
                    keycloak.deleteUser(id);
                } catch (RuntimeException ignored) {
                    // đã bị xóa
                }
            });
        }
        for (String email : emails) {
            jdbc.update("DELETE FROM shared.email_message WHERE lower(to_email) = ?", email);
        }
        for (String ip : ips) {
            redis.delete(RateLimitFilter.REGISTER_KEY_PREFIX + ip);
        }
        usernames.clear();
        emails.clear();
        ips.clear();
    }

    private void deleteAccountRows(UUID id) {
        try {
            keycloak.deleteUser(id);
        } catch (RuntimeException ignored) {
            // không có user Keycloak
        }
        jdbc.update("DELETE FROM identity.email_code WHERE user_id = ?", id);
        jdbc.update("DELETE FROM identity.user_role WHERE user_id = ?", id);
        jdbc.update("DELETE FROM identity.user_identity WHERE user_id = ?", id);
        jdbc.update("DELETE FROM identity.user_account WHERE id = ?", id);
        redis.delete(List.of("user-sess:" + id, "user-sess-seq:" + id, "user-rev-gen:" + id));
    }
}
