package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.PasswordResetTestSupport.Account;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.spi.UserDirectory;

/** {@code GET/PATCH /me/profile} (TM 5.5): AC-22 đến AC-27 của plan ID-2 và các ca rủi ro hồ sơ. */
class MeProfileIT extends AbstractAvatarMinioIT {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserDirectory userDirectory;
    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private MockHttpServletRequestBuilder patchProfile(Account account, String json) {
        return patch("/api/v1/me/profile")
                .with(sessionOf(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private MockHttpServletResponse patchAndGet(Account account, String json) throws Exception {
        return mockMvc.perform(patchProfile(account, json)).andReturn().getResponse();
    }

    private static String fullNameOf(int codePoints) {
        return "Đ".repeat(codePoints);
    }

    private UserAccount reload(Account account) {
        return userAccountRepository.findById(account.id()).orElseThrow();
    }

    private List<AuditEvent> auditEvents(String action, Account account) {
        return mockingDetails(auditRecorder).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("record"))
                .map(i -> (AuditEvent) i.getArgument(0))
                .filter(e -> action.equals(e.action()) && account.id().toString().equals(e.targetId()))
                .toList();
    }

    @Test
    void readsProfileUsernameReadOnly() throws Exception {
        Account account = support.eligible("profile.read");
        jdbc.update("UPDATE identity.user_account SET phone = '0912345678' WHERE id = ?", account.id());

        mockMvc.perform(get("/api/v1/me/profile").with(sessionOf(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(account.username()))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Test"))
                .andExpect(jsonPath("$.email").value(account.email()))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        // PATCH không đổi được tên đăng nhập (thân có "username" bị bỏ qua).
        MockHttpServletResponse response = patchAndGet(account,
                "{\"fullName\":\"Tên Mới\",\"username\":\"hacker.rename\"}");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("username").asText())
                .isEqualTo(account.username());
        assertThat(reload(account).getUsername()).isEqualTo(account.username());
        assertThat(reload(account).getFullName()).isEqualTo("Tên Mới");

        // Tài khoản chưa xác minh email: dải "chưa xác minh".
        Account unverified = support.create("profile.unverified",
                vn.unicom.thi360.identity.domain.UserStatus.ACTIVE, false, false);
        mockMvc.perform(get("/api/v1/me/profile").with(sessionOf(unverified)))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void profileWithoutPhoneAndAvatarSerializesExplicitNulls() throws Exception {
        Account account = support.eligible("profile.nulls");

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/me/profile").with(sessionOf(account)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // Khớp lược đồ OpenAPI: các trường nullable có mặt và là null (không bị bỏ), không phải chuỗi rỗng.
        for (String field : List.of("phone", "avatarAssetId", "avatarUrl")) {
            assertThat(body.has(field)).as("%s is present", field).isTrue();
            assertThat(body.get(field).isNull()).as("%s is null", field).isTrue();
        }
        assertThat(body.get("email").asText()).isEqualTo(account.email());
    }

    @Test
    void nameSyncDoesNotTouchOtherKeycloakFields() throws Exception {
        Account account = support.eligible("profile.kcsync");
        var users = KEYCLOAK.getKeycloakAdminClient().realm("thi-360").users();
        var before = users.get(account.id().toString()).toRepresentation();
        assertThat(before.isEnabled()).isTrue();
        assertThat(before.isEmailVerified()).isTrue();

        assertThat(patchAndGet(account, "{\"fullName\":\"Họ Tên Mới\"}").getStatus()).isEqualTo(200);

        var after = users.get(account.id().toString()).toRepresentation();
        assertThat(after.getLastName() + " " + after.getFirstName()).isEqualTo("Họ Tên Mới");
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        assertThat(after.isEmailVerified()).isEqualTo(before.isEmailVerified());
        assertThat(after.isEnabled()).isEqualTo(before.isEnabled());
        assertThat(after.getUsername()).isEqualTo(before.getUsername());
    }

    @Test
    void fullNameBoundaries() throws Exception {
        Account account = support.eligible("profile.name");

        assertThat(patchAndGet(account, "{\"fullName\":\"" + fullNameOf(100) + "\"}").getStatus())
                .as("100 characters").isEqualTo(200);
        assertThat(reload(account).getFullName()).isEqualTo(fullNameOf(100));

        for (String bad : List.of(fullNameOf(101), "", "   ")) {
            MockHttpServletResponse response = patchAndGet(account, "{\"fullName\":\"" + bad + "\"}");
            assertThat(response.getStatus()).as("rejected: '%s'", bad.length()).isEqualTo(400);
            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("fieldErrors").get(0).get("field").asText()).isEqualTo("fullName");
        }
        assertThat(reload(account).getFullName()).as("rejected input changes nothing").isEqualTo(fullNameOf(100));
    }

    @Test
    void phoneRules() throws Exception {
        Account account = support.eligible("profile.phone");
        jdbc.update("UPDATE identity.user_account SET phone = ? WHERE id = ?", "+84 (0) 912 345 678 ext.9", account.id());

        // Không gửi phone: số cũ giữ nguyên dù không theo dạng 10 chữ số.
        assertThat(patchAndGet(account, "{\"fullName\":\"Chỉ đổi tên\"}").getStatus()).isEqualTo(200);
        assertThat(reload(account).getPhone()).isEqualTo("+84 (0) 912 345 678 ext.9");

        // 10 chữ số bắt đầu bằng 0: đạt.
        assertThat(patchAndGet(account, "{\"phone\":\"0912345678\"}").getStatus()).isEqualTo(200);
        assertThat(reload(account).getPhone()).isEqualTo("0912345678");

        // 9 và 11 chữ số, sai đầu số, có chữ, có dấu cách giữa: bị từ chối, số cũ giữ nguyên.
        for (String bad : List.of("091234567", "09123456789", "1912345678", "09123456a8", "0912 345 678", "+84912345678")) {
            MockHttpServletResponse response = patchAndGet(account, "{\"phone\":\"" + bad + "\"}");
            assertThat(response.getStatus()).as(bad).isEqualTo(400);
            assertThat(objectMapper.readTree(response.getContentAsString()).get("fieldErrors").get(0).get("field").asText())
                    .isEqualTo("phone");
        }
        assertThat(reload(account).getPhone()).isEqualTo("0912345678");

        // Chuỗi rỗng xóa số (không bắt buộc).
        assertThat(patchAndGet(account, "{\"phone\":\"\"}").getStatus()).isEqualTo(200);
        assertThat(reload(account).getPhone()).isNull();
    }

    @Test
    void auditsBeforeAndAfter() throws Exception {
        Account account = support.eligible("profile.audit");
        jdbc.update("UPDATE identity.user_account SET phone = '0900000001' WHERE id = ?", account.id());

        patchAndGet(account, "{\"fullName\":\"Họ Tên Mới\",\"phone\":\"0900000002\"}");
        // Gửi lại đúng giá trị đang có: không đổi gì thì không có thêm sự kiện.
        patchAndGet(account, "{\"fullName\":\"Họ Tên Mới\",\"phone\":\"0900000002\"}");

        List<AuditEvent> profileEvents = auditEvents("USER_PROFILE_UPDATED", account);
        assertThat(profileEvents).hasSize(1);
        AuditEvent event = profileEvents.getFirst();
        assertThat(event.before()).containsEntry("fullName", "Nguyễn Văn Test").containsEntry("phone", "0900000001");
        assertThat(event.after()).containsEntry("fullName", "Họ Tên Mới").containsEntry("phone", "0900000002");
        assertThat(String.valueOf(event)).doesNotContainIgnoringCase("password");

        // Đổi rồi xóa ảnh: nhật ký có assetId trước và sau.
        String assetId = objectMapper.readTree(mockMvc.perform(
                        putAvatar(AvatarTestImages.png(32, 32), "a.png", "image/png").with(sessionOf(account)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("avatarAssetId").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/me/avatar").with(sessionOf(account)))
                .andExpect(status().isNoContent());

        AuditEvent changed = auditEvents("AVATAR_CHANGED", account).getFirst();
        assertThat(changed.before()).containsEntry("avatarAssetId", null);
        assertThat(changed.after()).containsEntry("avatarAssetId", assetId);
        AuditEvent removed = auditEvents("AVATAR_REMOVED", account).getFirst();
        assertThat(removed.before()).containsEntry("avatarAssetId", assetId);
        assertThat(removed.after()).containsEntry("avatarAssetId", null);
        verify(auditRecorder).record(argThat(e -> e != null && "AVATAR_REMOVED".equals(e.action())));
    }

    @Test
    void identityAndVerificationUnchanged() throws Exception {
        Account account = support.eligible("profile.identity");
        jdbc.update("INSERT INTO identity.user_role (user_id, role_id, assigned_at) VALUES (?, ?::uuid, now())",
                account.id(), "00000000-0000-7000-8000-000000000001");
        UserAccount before = reload(account);
        List<Map<String, Object>> rolesBefore = jdbc.queryForList(
                "SELECT role_id FROM identity.user_role WHERE user_id = ? ORDER BY role_id", account.id());

        patchAndGet(account, "{\"fullName\":\"Đổi tên không đổi định danh\",\"phone\":\"0911111111\"}");
        mockMvc.perform(putAvatar(AvatarTestImages.png(32, 32), "a.png", "image/png").with(sessionOf(account)))
                .andExpect(status().isOk());

        UserAccount after = reload(account);
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getCode()).isEqualTo(before.getCode());
        assertThat(after.getUsername()).isEqualTo(before.getUsername());
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        Instant verified = before.getEmailVerifiedAt();
        assertThat(verified).isNotNull();
        assertThat(after.getEmailVerifiedAt()).isEqualTo(verified);
        assertThat(jdbc.queryForList(
                "SELECT role_id FROM identity.user_role WHERE user_id = ? ORDER BY role_id", account.id()))
                .isEqualTo(rolesBefore);

        // Lớp học đọc họ tên và số điện thoại qua cổng UserDirectory: thấy giá trị mới ngay.
        UserDirectory.UserDetailSummary summary = userDirectory.findDetailById(account.id()).orElseThrow();
        assertThat(summary.fullName()).isEqualTo("Đổi tên không đổi định danh");
        assertThat(summary.phone()).isEqualTo("0911111111");
        // Họ tên đồng bộ sang Keycloak (như admin sửa).
        var keycloakUser = KEYCLOAK.getKeycloakAdminClient().realm("thi-360").users().get(account.id().toString())
                .toRepresentation();
        assertThat(keycloakUser.getLastName() + " " + keycloakUser.getFirstName())
                .isEqualTo("Đổi tên không đổi định danh");
    }

    @Test
    void requiresSessionAndOwnData() throws Exception {
        Account alice = support.eligible("profile.alice");
        Account bob = support.eligible("profile.bob");

        mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        // Alice sửa hồ sơ và đặt ảnh: chỉ dòng của Alice đổi; không có tham số nào để trỏ sang Bob.
        patchAndGet(alice, "{\"fullName\":\"Alice Sửa\",\"phone\":\"0933333333\"}");
        mockMvc.perform(putAvatar(AvatarTestImages.png(32, 32), "a.png", "image/png").with(sessionOf(alice)))
                .andExpect(status().isOk());

        assertThat(reload(bob).getFullName()).isEqualTo("Nguyễn Văn Test");
        assertThat(reload(bob).getPhone()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identity.user_avatar WHERE user_id = ?", Integer.class, bob.id())).isZero();
        mockMvc.perform(get("/api/v1/me/profile").with(sessionOf(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Test"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        // Tài khoản nền tảng không có hồ sơ ứng dụng.
        mockMvc.perform(get("/api/v1/me/profile").with(asPlatformAdmin())).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/me/profile").with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void existingAccountsHaveEmptyAvatar() throws Exception {
        // Tài khoản có từ trước migration (dữ liệu mẫu của DB đợt 2, không có dòng user_avatar): đọc được hồ sơ, chưa có ảnh.
        List<UUID> legacy = jdbc.queryForList(
                "SELECT a.id FROM identity.user_account a WHERE a.deleted_at IS NULL"
                        + " AND NOT EXISTS (SELECT 1 FROM identity.user_avatar v WHERE v.user_id = a.id)"
                        + " ORDER BY a.created_at LIMIT 1",
                UUID.class);
        Account account;
        if (legacy.isEmpty()) {
            // Profile test không nạp tài khoản mẫu: dựng một tài khoản không có dòng ảnh, đúng như dữ liệu cũ.
            account = support.eligible("profile.legacy");
        } else {
            UUID id = legacy.getFirst();
            String username = jdbc.queryForObject("SELECT username FROM identity.user_account WHERE id = ?", String.class, id);
            account = new Account(id, username, null, null);
        }

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/me/profile").with(sessionOf(account)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo(account.username());
        assertThat(body.has("avatarUrl") && !body.get("avatarUrl").isNull()).isFalse();
        assertThat(body.has("avatarAssetId") && !body.get("avatarAssetId").isNull()).isFalse();
    }
}
