package vn.unicom.thi360.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.unicom.thi360.shared.security.TestSecurity.asStudent;
import static vn.unicom.thi360.shared.security.TestSecurity.asUser;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RolePermission;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
 import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;

class LocaleErrorLocalizationIT extends AbstractIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void notFoundError_resolvesLocaleFromAcceptLanguage_withStableCode() throws Exception {
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        mockMvc.perform(get("/api/v1/admin/users/{id}", missingId)
                        .with(asUser(Permissions.USER_VIEW))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Requested resource was not found"));

        mockMvc.perform(get("/api/v1/admin/users/{id}", missingId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy dữ liệu yêu cầu"));

        mockMvc.perform(get("/api/v1/admin/users/{id}", missingId)
                        .with(asUser(Permissions.USER_VIEW))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy dữ liệu yêu cầu"));
    }

    @Test
    void securityErrorWriter_localizes403FromAccessDeniedAndAdminOriginFilter() throws Exception {
        // 403 FORBIDDEN written by Spring Security AccessDeniedHandler -> SecurityErrorWriter
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(asStudent())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(asStudent()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Không có quyền thực hiện thao tác này"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(asStudent())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Không có quyền thực hiện thao tác này"));

        // 403 ORIGIN_INVALID written by AdminOriginFilter -> SecurityErrorWriter
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_INVALID"))
                .andExpect(jsonPath("$.message").value("Request origin is not allowed"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_INVALID"))
                .andExpect(jsonPath("$.message").value("Nguồn gốc yêu cầu không được phép"));
    }

    @Test
    void fieldErrors_fromBeanValidationAndServices_areLocalized() throws Exception {
        // Bean validation (@NotBlank on CreateUserRequest.fullName)
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid input data"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NOTBLANK"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Full name must not be blank"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NOTBLANK"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Họ và tên không được để trống"));

        // Programmatic service FieldError (AppearanceService)
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"palette\":{\"presetId\":\"thi360-green\"},\"mode\":\"sepia\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid input data"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("mode"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("APPEARANCE_MODE_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Appearance mode must be light or dark"));

        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"palette\":{\"presetId\":\"thi360-green\"},\"mode\":\"sepia\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("mode"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("APPEARANCE_MODE_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Chế độ giao diện phải là light hoặc dark"));

        // PageQuery static FieldError
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "-1")
                        .with(asUser(Permissions.USER_VIEW))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Page index must not be less than 0"));
    }

    @Test
    void passwordPolicy_validAccountInputWithMissingUppercase_isLocalizedInEnNoneAndUnsupported() throws Exception {
        String payload = """
                {
                  "username": "policy.user.01",
                  "password": "abcd1234!",
                  "fullName": "Policy Test User"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"))
                .andExpect(jsonPath("$.message").value("Password does not meet security requirements"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("PASSWORD_UPPERCASE"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Password must contain at least one uppercase letter"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"))
                .andExpect(jsonPath("$.message").value("Mật khẩu không đạt yêu cầu bảo mật"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("PASSWORD_UPPERCASE"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Mật khẩu phải có ít nhất một chữ hoa"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"))
                .andExpect(jsonPath("$.message").value("Mật khẩu không đạt yêu cầu bảo mật"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("PASSWORD_UPPERCASE"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Mật khẩu phải có ít nhất một chữ hoa"));
    }

    @Test
    void permissionsMetadataAndUsersExport_areLocalizedByAcceptLanguage() throws Exception {
        UUID userViewId = permissionRepository.findByCode(Permissions.USER_VIEW).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/admin/permissions")
                        .param("group", "USER")
                        .param("q", "USER.VIEW")
                        .with(asUser(Permissions.PERMISSION_VIEW))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USER.VIEW"))
                .andExpect(jsonPath("$[0].groupName").value("Accounts"))
                .andExpect(jsonPath("$[0].name").value("View accounts"));

        mockMvc.perform(get("/api/v1/admin/permissions")
                        .param("group", "USER")
                        .param("q", "USER.VIEW")
                        .with(asUser(Permissions.PERMISSION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USER.VIEW"))
                .andExpect(jsonPath("$[0].groupName").value("Tài khoản"))
                .andExpect(jsonPath("$[0].name").value("Xem tài khoản"));

        UUID userId = Ids.newId();
        UUID customRoleId = Ids.newId();
        String suffix = userId.toString().substring(0, 8);
        String username = "export.locale." + suffix;
        Instant now = Instant.now();
        Role systemAdminRole = roleRepository.findByCodeNormalized("system_admin").orElseThrow();
        Role customRole = roleRepository.saveAndFlush(new Role(
                customRoleId,
                "CUSTOM_EXPORT_" + suffix,
                "Giáo viên chấm thi",
                null,
                Access.MANAGE,
                false,
                RoleStatus.ACTIVE,
                now,
                null));
        RolePermission customRolePermission = rolePermissionRepository.saveAndFlush(
                new RolePermission(customRoleId, userViewId, null, now));
        userAccountRepository.saveAndFlush(new UserAccount(
                userId,
                "UEXP" + suffix,
                username,
                "Export Locale User",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                now,
                null));
        UserRole sysUserRole = userRoleRepository.saveAndFlush(new UserRole(userId, systemAdminRole.getId(), null, now));
        UserRole customUserRole = userRoleRepository.saveAndFlush(new UserRole(userId, customRole.getId(), null, now));

        try {
            MvcResult enExport = mockMvc.perform(get("/api/v1/admin/permissions/{id}/users/export", userViewId)
                            .param("q", username)
                            .with(asUser(Permissions.PERMISSION_VIEW, Permissions.USER_VIEW))
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andExpect(status().isOk())
                    .andReturn();
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(enExport.getResponse().getContentAsByteArray()))) {
                var sheet = wb.getSheetAt(0);
                var header = sheet.getRow(0);
                assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Username");
                assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Permission sources");
                var row = sheet.getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo(username);
                assertThat(row.getCell(3).getStringCellValue()).isEqualTo("Active");
                assertThat(row.getCell(4).getStringCellValue())
                        .isEqualTo("Role: System Admin; Role: Giáo viên chấm thi");
            }

            MvcResult viExport = mockMvc.perform(get("/api/v1/admin/permissions/{id}/users/export", userViewId)
                            .param("q", username)
                            .with(asUser(Permissions.PERMISSION_VIEW, Permissions.USER_VIEW)))
                    .andExpect(status().isOk())
                    .andReturn();
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(viExport.getResponse().getContentAsByteArray()))) {
                var sheet = wb.getSheetAt(0);
                var header = sheet.getRow(0);
                assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Tên đăng nhập");
                assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Nguồn quyền");
                var row = sheet.getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo(username);
                assertThat(row.getCell(3).getStringCellValue()).isEqualTo("Hoạt động");
                assertThat(row.getCell(4).getStringCellValue())
                        .isEqualTo("Vai trò: Quản trị hệ thống; Vai trò: Giáo viên chấm thi");
            }
        } finally {
            userRoleRepository.deleteAll(List.of(sysUserRole, customUserRole));
            userAccountRepository.deleteById(userId);
            rolePermissionRepository.delete(customRolePermission);
            roleRepository.deleteById(customRoleId);
        }
    }
}
