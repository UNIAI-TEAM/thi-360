package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamCreateIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void allKindsAllowedScopes() throws Exception {
        String[][] cases = {
                {"HSA", "FULL"}, {"HSA", "PART"}, {"QDA", "FULL"},
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
    void rejectsScopeNotAllowedForVact() throws Exception {
        String code = "VACT-S-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\",\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
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

    @Test
    void vactReferenceScaleIsRetainedByCreateAndDuplicate() throws Exception {
        String code = "VACT-F-" + UUID.randomUUID().toString().substring(0, 8);
        String created = mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.referenceMax").value(1200))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();
        mockMvc.perform(get("/api/v1/admin/exams/" + id).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.referenceMax").value(1200));
        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/duplicate")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Copy\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.referenceMax").value(1200));
    }

    @Test
    void changingToKindWithoutTemplateAppliesProfileDefaults() throws Exception {
        jdbc.update("UPDATE exam.exam_template SET active=false WHERE code='VACT_FULL'");
        String url = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
        var before = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String revision = before.path("revision").asText();
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"durationMinutes\":150,\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timing").value("SHARED"))
                .andExpect(jsonPath("$.durationMinutes").value(150))
                .andExpect(jsonPath("$.referenceMax").value(1200))
                .andExpect(jsonPath("$.practiceScoring").value("LINEAR"))
                .andExpect(jsonPath("$.maxScore").isEmpty())
                .andExpect(jsonPath("$.parts").isEmpty());
    }

    @Test
    void changingToKindWithActiveTemplateAppliesItsProfile() throws Exception {
        String url = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
        var before = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String revision = before.path("revision").asText();
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("VACT"))
                .andExpect(jsonPath("$.templateCode").value("VACT_FULL"))
                .andExpect(jsonPath("$.referenceMax").value(1200))
                .andExpect(jsonPath("$.practiceScoring").value("LINEAR"));
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode").value("VACT_FULL"))
                .andExpect(jsonPath("$.referenceMax").value(1200))
                .andExpect(jsonPath("$.practiceScoring").value("LINEAR"));
    }

    @Test
    void partCodeFromTemplateCreates() throws Exception {
        String code = "HSA-P-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"PART\",\"partCode\":\"SCIENCE\","
                                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateCode").value("HSA_PART_SCIENCE"));
    }

    @Test
    void partCodeNotInTemplates422() throws Exception {
        String code = "PART-" + UUID.randomUUID().toString().substring(0, 8);
        String request = "{\"kind\":\"HSA\",\"scope\":\"PART\",\"partCode\":\"UNKNOWN\","
                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
        // 2B §10 adds QDA's Toán part template, so keep the unknown part code.
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("\"HSA\"", "\"QDA\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
        jdbc.update("UPDATE exam.exam_template SET active=false WHERE code='HSA_PART_MATH'");
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("UNKNOWN", "TOAN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
    }

    @Test
    void subjectActiveWithoutTemplateBlankStructure() throws Exception {
        jdbc.update("UPDATE exam.exam_template SET active=false WHERE kind_code='SPT' AND subject_code='TOAN'");
        String code = "SPT-S-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateCode").isEmpty())
                .andExpect(jsonPath("$.parts").isEmpty());
    }

    @Test
    void subjectUnknown422() throws Exception {
        String code = "SPT-S-" + UUID.randomUUID().toString().substring(0, 8);
        String request = "{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"UNKNOWN\","
                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
        jdbc.update("UPDATE catalog.catalog_item SET status=2 WHERE code='TOAN' AND group_id="
                + "(SELECT id FROM catalog.catalog_group WHERE code='SUBJECT')");
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request.replace("UNKNOWN", "TOAN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
    }
}
