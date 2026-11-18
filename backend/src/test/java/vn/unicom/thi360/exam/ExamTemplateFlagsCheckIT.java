package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** BE-15 against the seeded test exams: publish refusal for an unpublishable template and unmapped quotas. */
@Transactional
class ExamTemplateFlagsCheckIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void unpublishableSubjectSavesDraftFailsTheCheckAndRefusesPublish() throws Exception {
        jdbc.update("UPDATE exam.exam_template SET publishable = false WHERE code = 'THPTQG_LITERATURE_2025'");
        String url = url("TEST-THPTQG-02");

        JsonNode structure = json(get(url + "/structure").with(asUser(Permissions.EXAM_VIEW)), 200);
        ObjectNode save = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        save.set("parts", structure.path("parts"));
        save.set("examQuotas", structure.path("examQuotas"));
        json(put(url + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(save.toString()), 200);

        JsonNode result = json(post(url + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(codes(result)).contains("TEMPLATE_NOT_PUBLISHABLE");
        assertThat(result.path("paths").findValuesAsText("result")).doesNotContain("VALID");

        JsonNode exam = json(get(url).with(asUser(Permissions.EXAM_VIEW)), 200);
        String publish = "{\"pathIds\":[\"default\"],\"expectedRevision\":" + exam.path("revision").asLong() + "}";
        mockMvc.perform(post(url + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(publish))
                .andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?::uuid",
                Long.class, exam.path("id").asText())).isZero();
    }

    @Test
    void unmappedRequiredQuotaFailsCheckAndPreviewButReferenceRunsNormally() throws Exception {
        String url = url("TEST-THPTQG-01");
        JsonNode structure = json(get(url + "/structure").with(asUser(Permissions.EXAM_VIEW)), 200);
        JsonNode baseline = json(post(url + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
        assertThat(baseline.path("status").asText()).isEqualTo("VALID");

        ObjectNode section = (ObjectNode) structure.path("parts").get(0).path("sections").get(0);
        String sectionId = section.path("id").asText();
        long revision = structure.path("revision").asLong();
        for (String enforcement : List.of("REQUIRED", "REFERENCE")) {
            ObjectNode quota = mapper.createObjectNode().put("id", "pending").put("name", "Pending mapping")
                    .put("count", 1).put("basis", "QUESTION").put("enforcement", enforcement);
            quota.putArray("nodeIds");
            ObjectNode edited = structure.deepCopy();
            ((ObjectNode) edited.path("parts").get(0).path("sections").get(0)).putArray("topicQuotas").add(quota);
            ObjectNode save = mapper.createObjectNode().put("expectedRevision", revision);
            save.set("parts", edited.path("parts"));
            save.set("examQuotas", edited.path("examQuotas"));
            revision = json(put(url + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                    .contentType(MediaType.APPLICATION_JSON).content(save.toString()), 200).path("revision").asLong();

            JsonNode check = json(post(url + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
            JsonNode preview = json(post(url + "/preview").with(asUser(Permissions.EXAM_VIEW))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":7}"), 200);
            if (enforcement.equals("REQUIRED")) {
                assertThat(check.path("status").asText()).isEqualTo("INVALID");
                JsonNode issue = issue(check, "TOPIC_QUOTA_MAPPING_MISSING");
                assertThat(issue.path("location").path("sectionId").asText()).isEqualTo(sectionId);
                assertThat(preview.path("status").asText()).isEqualTo("FAILED");
                assertThat(preview.path("issues").findValuesAsText("code")).contains("TOPIC_QUOTA_MAPPING_MISSING");
            } else {
                assertThat(check.path("status").asText()).isEqualTo("VALID");
                assertThat(codes(check)).doesNotContain("TOPIC_QUOTA_MAPPING_MISSING");
                assertThat(preview.path("status").asText()).isEqualTo("GENERATED");
            }
        }
    }

    private String url(String examCode) {
        UUID id = jdbc.queryForObject("SELECT id FROM exam.exam WHERE code_normalized = ?", UUID.class,
                examCode.toLowerCase(java.util.Locale.ROOT));
        return "/api/v1/admin/exams/" + id;
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int expected)
            throws Exception {
        return mapper.readTree(mockMvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    private static List<String> codes(JsonNode result) {
        return result.path("groups").findValuesAsText("code");
    }

    private static JsonNode issue(JsonNode result, String code) {
        return StreamSupport.stream(result.path("groups").findValues("issues").spliterator(), false)
                .flatMap(list -> StreamSupport.stream(list.spliterator(), false))
                .filter(issue -> code.equals(issue.path("code").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " missing in " + codes(result)));
    }
}
