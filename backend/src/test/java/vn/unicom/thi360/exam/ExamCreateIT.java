package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamCreateIT extends AbstractIntegrationTest {
    @Test
    void allKindsAllowedScopes() throws Exception {
        String[][] cases = {
                {"HSA", "FULL"}, {"HSA", "PART"}, {"QDA", "FULL"}, {"QDA", "PART"},
                {"BCA", "FULL"}, {"VACT", "FULL"}, {"SPT", "SUBJECT"},
                {"SP2E", "SUBJECT"}, {"THPTQG", "SUBJECT"}
        };
        for (String[] c : cases) {
            String code = c[0] + "-" + UUID.randomUUID().toString().substring(0, 8);
            String selection = switch (c[1]) {
                case "PART" -> ",\"partCode\":\"TOAN\"";
                case "SUBJECT" -> ",\"subjectCode\":\"TOAN\"";
                default -> "";
            };
            mockMvc.perform(post("/api/v1/admin/exams")
                            .with(asUser(Permissions.EXAM_MODIFY))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kind\":\"" + c[0] + "\",\"scope\":\"" + c[1]
                                    + "\",\"name\":\"Draft\",\"code\":\"" + code + "\"" + selection + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.kind").value(c[0]))
                    .andExpect(jsonPath("$.scope").value(c[1]));
        }
    }

    @Test
    void createsHsaDraftFromLatestTemplate() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("FULL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.templateCode").value("HSA_FULL"))
                .andExpect(jsonPath("$.parts[0].sections[0].subjectCode").value("TOAN"));
    }

    @Test
    void rejectsScopeNotAllowedForHsa() throws Exception {
        String code = "HSA-S-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\",\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_SCOPE_NOT_ALLOWED"));
    }

    @Test
    void suggestsUnusedCode() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exams/suggested-code")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .param("kind", "HSA").param("scope", "FULL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("HSA-F-[0-9]{3,}")));
    }
}
