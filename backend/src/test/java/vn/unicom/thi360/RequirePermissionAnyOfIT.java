package vn.unicom.thi360;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * AC-5 (UNI-2607): ba biến thể của {@code @RequirePermission} — chỉ {@code value}, chỉ
 * {@code anyOf}, và cả hai — trên endpoint thử {@code /api/v1/admin/__test/*}.
 */
@Import(TestBe1Endpoints.class)
class RequirePermissionAnyOfIT extends AbstractIntegrationTest {

    // ------------------------------------------------ chỉ value (giữ nghĩa cũ: cần đủ)

    @Test
    void valueOnly_grants200_whenUserHasThePermission_and403_whenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/__test/value-only").with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value("value-only"));

        mockMvc.perform(get("/api/v1/admin/__test/value-only").with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/__test/value-only"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------ chỉ anyOf (cần ít nhất một)

    @Test
    void anyOfOnly_grants200_whenUserHasEitherPermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/__test/anyof-only").with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value("anyof-only"));

        mockMvc.perform(get("/api/v1/admin/__test/anyof-only").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
    }

    @Test
    void anyOfOnly_403_whenUserHasNeitherPermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/__test/anyof-only").with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Người chỉ có quyền ghi (không phải quyền xem trong anyOf) vẫn bị chặn
        mockMvc.perform(get("/api/v1/admin/__test/anyof-only")
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.EXAM_PUBLISH)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------ cả hai (đủ value VÀ một trong anyOf)

    @Test
    void valueAndAnyOf_requiresAllOfValuePlusOneOfAnyOf() throws Exception {
        // Đủ: USER.VIEW + QUESTION.VIEW
        mockMvc.perform(get("/api/v1/admin/__test/value-and-anyof")
                        .with(asUser(Permissions.USER_VIEW, Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value("value-and-anyof"));

        // Đủ: USER.VIEW + EXAM.VIEW
        mockMvc.perform(get("/api/v1/admin/__test/value-and-anyof")
                        .with(asUser(Permissions.USER_VIEW, Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());

        // Thiếu value: chỉ có quyền trong anyOf
        mockMvc.perform(get("/api/v1/admin/__test/value-and-anyof").with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isForbidden());

        // Thiếu anyOf: chỉ có value
        mockMvc.perform(get("/api/v1/admin/__test/value-and-anyof").with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());

        // Không quyền nào
        mockMvc.perform(get("/api/v1/admin/__test/value-and-anyof").with(asUser()))
                .andExpect(status().isForbidden());
    }
}
