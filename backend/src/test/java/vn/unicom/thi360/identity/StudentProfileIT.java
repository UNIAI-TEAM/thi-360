package vn.unicom.thi360.identity;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.PlatformAdmin;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserIdentity;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

class StudentProfileIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdPlatformAdminIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID classId : createdClassIds) {
            jdbcTemplate.update(
                    "DELETE FROM classroom.class_member_history WHERE class_id = ? OR related_class_id = ?",
                    classId,
                    classId);
            jdbcTemplate.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbcTemplate.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        createdClassIds.clear();

        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM classroom.class_member_history WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        createdUserIds.clear();

        for (UUID platformAdminId : createdPlatformAdminIds) {
            jdbcTemplate.update("DELETE FROM identity.platform_admin WHERE id = ?", platformAdminId);
        }
        createdPlatformAdminIds.clear();
    }

    @Test
    void profile_returnsAccountLoginMethodsCurrentClassesAndHistory_forSeededStudentInClass() throws Exception {
        String sfx = suffix();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant lastLogin = now.minus(2, ChronoUnit.HOURS);

        UUID studentId = Ids.newId();
        String code = "U9" + sfx;
        String username = "student.prof." + sfx;
        String email = "student.prof." + sfx + "@thi360.vn";
        UserAccount student = new UserAccount(
                studentId,
                code,
                username,
                "Nguyễn Minh Học",
                email,
                "0912345678",
                UserSource.BULK_IMPORT,
                UserStatus.ACTIVE,
                false,
                now.minus(3, ChronoUnit.DAYS),
                null);
        student.markLoggedIn(lastLogin);
        userAccountRepository.saveAndFlush(student);
        createdUserIds.add(studentId);

        Role studentRole = roleRepository.findByCodeNormalized("student").orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(studentId, studentRole.getId(), null, now.minus(3, ChronoUnit.DAYS)));

        userIdentityRepository.saveAndFlush(new UserIdentity(
                Ids.newId(),
                studentId,
                IdentityProvider.LOCAL,
                studentId.toString(),
                email,
                now.minus(3, ChronoUnit.DAYS)));
        userIdentityRepository.saveAndFlush(new UserIdentity(
                Ids.newId(),
                studentId,
                IdentityProvider.GOOGLE,
                "google-sub-" + sfx,
                email,
                now.minus(1, ChronoUnit.DAYS)));

        String classACode = "LOP-A-" + sfx;
        String classBCode = "LOP-B-" + sfx;
        UUID classAId = createClass(classACode, "Lớp A " + sfx);
        UUID classBId = createClass(classBCode, "Lớp B " + sfx);

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classAId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(1));

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members/{userId}/transfer", classAId, studentId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetClassId\":\"%s\",\"reason\":\"Chuyển lớp đầu kỳ\"}".formatted(classBId)))
                .andExpect(status().isOk());

        // Caller has USER.VIEW only (no CLASS.VIEW)
        mockMvc.perform(get("/api/v1/admin/users/{id}/profile", studentId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(studentId.toString()))
                .andExpect(jsonPath("$.account.code").value(code))
                .andExpect(jsonPath("$.account.username").value(username))
                .andExpect(jsonPath("$.account.fullName").value("Nguyễn Minh Học"))
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.account.phone").value("0912345678"))
                .andExpect(jsonPath("$.account.source").value("BULK_IMPORT"))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account.lastLoginAt").value(lastLogin.toString()))
                .andExpect(jsonPath("$.account.roles", hasSize(1)))
                .andExpect(jsonPath("$.account.roles[0].code").value("STUDENT"))
                .andExpect(jsonPath("$.loginMethods", hasSize(2)))
                .andExpect(jsonPath("$.loginMethods[0].provider").value("LOCAL"))
                .andExpect(jsonPath("$.loginMethods[0].emailAtLink").value(email))
                .andExpect(jsonPath("$.loginMethods[1].provider").value("GOOGLE"))
                .andExpect(jsonPath("$.loginMethods[1].emailAtLink").value(email))
                .andExpect(jsonPath("$.currentClasses", hasSize(1)))
                .andExpect(jsonPath("$.currentClasses[0].classId").value(classBId.toString()))
                .andExpect(jsonPath("$.currentClasses[0].classCode").value(classBCode))
                .andExpect(jsonPath("$.currentClasses[0].className").value("Lớp B " + sfx))
                .andExpect(jsonPath("$.currentClasses[0].classStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.classHistory", hasSize(3)))
                .andExpect(jsonPath("$.classHistory[0].action").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.classHistory[0].classId").value(classBId.toString()))
                .andExpect(jsonPath("$.classHistory[0].relatedClassId").value(classAId.toString()))
                .andExpect(jsonPath("$.classHistory[0].reason").value("Chuyển lớp đầu kỳ"))
                .andExpect(jsonPath("$.classHistory[1].action").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.classHistory[1].classId").value(classAId.toString()))
                .andExpect(jsonPath("$.classHistory[1].relatedClassId").value(classBId.toString()))
                .andExpect(jsonPath("$.classHistory[2].action").value("JOIN"))
                .andExpect(jsonPath("$.classHistory[2].classId").value(classAId.toString()));
    }

    @Test
    void profile_returns403_withoutUserViewPermission() throws Exception {
        UUID studentId = createBasicStudent("prof403." + suffix());

        mockMvc.perform(get("/api/v1/admin/users/{id}/profile", studentId)
                        .with(asUser(Permissions.USER_LEARNING_VIEW, Permissions.CLASS_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/profile", studentId)
                        .with(asRole("CONTENT_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/profile", studentId)
                        .with(asStudent()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void profileAndLearning_return404_forUnknownProvisioningAndPlatformAdminIds() throws Exception {
        UUID unknownId = Ids.newId();

        UserAccount provisioning = new UserAccount(
                Ids.newId(),
                "U9P" + suffix(),
                "prov." + suffix(),
                "Provisioning User",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.PROVISIONING,
                false,
                Instant.now(),
                null);
        userAccountRepository.saveAndFlush(provisioning);
        createdUserIds.add(provisioning.getId());

        UUID platformAdminId = Ids.newId();
        platformAdminRepository.saveAndFlush(new PlatformAdmin(platformAdminId, "platform." + suffix(), Instant.now()));
        createdPlatformAdminIds.add(platformAdminId);

        for (UUID missingId : List.of(unknownId, provisioning.getId(), platformAdminId)) {
            mockMvc.perform(get("/api/v1/admin/users/{id}/profile", missingId)
                            .with(asUser(Permissions.USER_VIEW)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));

            mockMvc.perform(get("/api/v1/admin/users/{id}/learning", missingId)
                            .with(asUser(Permissions.USER_LEARNING_VIEW)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void learning_returnsFixedEmptyStructure_withDataAvailableFalse() throws Exception {
        UUID studentId = createBasicStudent("learn." + suffix());

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(studentId.toString()))
                .andExpect(jsonPath("$.dataAvailable").value(false))
                .andExpect(jsonPath("$.entitlements", hasSize(0)))
                .andExpect(jsonPath("$.attempts", hasSize(0)))
                .andExpect(jsonPath("$.assessments", hasSize(0)))
                .andExpect(jsonPath("$.practice", hasSize(0)))
                .andExpect(jsonPath("$.supportRequests", hasSize(0)));

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                        .with(asRole("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataAvailable").value(false));
    }

    @Test
    void learning_returns403_withoutUserLearningViewPermission() throws Exception {
        UUID studentId = createBasicStudent("learn403." + suffix());

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                        .with(asUser(Permissions.USER_VIEW, Permissions.USER_MODIFY, Permissions.USER_ASSIGN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                        .with(asRole("CONTENT_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", studentId)
                        .with(asStudent()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void permissionsCatalog_containsUserLearningView() throws Exception {
        mockMvc.perform(get("/api/v1/admin/permissions")
                        .with(asUser(Permissions.PERMISSION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("USER.LEARNING_VIEW")));
    }

    private UUID createBasicStudent(String username) {
        UUID userId = Ids.newId();
        UserAccount account = new UserAccount(
                userId,
                "U8" + suffix(),
                username,
                "Học viên Test",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                null);
        userAccountRepository.saveAndFlush(account);
        createdUserIds.add(userId);
        return userId;
    }

    private UUID createClass(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s"
                                }
                                """.formatted(code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID classId = UUID.fromString(json.get("id").asText());
        createdClassIds.add(classId);
        return classId;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
