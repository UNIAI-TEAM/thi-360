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

class LocaleErrorLocalizationIT extends AbstractIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

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

        MvcResult enExport = mockMvc.perform(get("/api/v1/admin/permissions/{id}/users/export", userViewId)
                        .with(asUser(Permissions.PERMISSION_VIEW, Permissions.USER_VIEW))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andReturn();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(enExport.getResponse().getContentAsByteArray()))) {
            var header = wb.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Username");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Permission sources");
        }

        MvcResult viExport = mockMvc.perform(get("/api/v1/admin/permissions/{id}/users/export", userViewId)
                        .with(asUser(Permissions.PERMISSION_VIEW, Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andReturn();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(viExport.getResponse().getContentAsByteArray()))) {
            var header = wb.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Tên đăng nhập");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Nguồn quyền");
        }
    }
}
