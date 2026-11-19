package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamPublishedTotalsIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;
    @Test
    void bcaFullPublishesHundredPointPaths() throws Exception {
        String create = "{\"kind\":\"BCA\",\"scope\":\"FULL\",\"name\":\"BCA\","
                + "\"code\":\"B-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"durationMinutes\":180}";
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String base = "/api/v1/admin/exams/" + exam.path("id").asText();
        JsonNode checked = mapper.readTree(mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var paths = mapper.createArrayNode();
        checked.path("paths").forEach(path -> {
            if ("VALID".equals(path.path("result").asText())) paths.add(path.path("pathId").asText());
        });
        assertThat(paths).as("BCA checked paths: %s", checked).isNotEmpty();
        var publish = mapper.createObjectNode().put("expectedRevision", exam.path("revision").asLong());
        publish.set("pathIds", paths);
        ExamListingFixture.fill(jdbc, UUID.fromString(exam.path("id").asText()));
        mockMvc.perform(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(publish.toString())).andExpect(status().isOk());
        JsonNode version = mapper.readTree(mockMvc.perform(get(base + "/versions/1").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode publishedPaths = version.path("parts").get(0).path("paths");
        assertThat(publishedPaths).isNotEmpty();
        for (JsonNode path : publishedPaths) {
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (JsonNode part : version.path("parts")) {
                JsonNode contribution = java.util.stream.StreamSupport.stream(part.path("paths").spliterator(), false)
                        .filter(row -> row.path("pathId").equals(path.path("pathId"))).findFirst().orElseThrow();
                assertThat(contribution.path("maxPoints").isNumber()).isTrue();
                total = total.add(contribution.path("maxPoints").decimalValue());
            }
            assertThat(total).isEqualByComparingTo("100");
        }
    }


    @Test
    void sptInformaticsPublishesBothTwentyTwoQuestionSevenPointPaths() throws Exception {
        UUID subject = jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE group_id=(SELECT id FROM catalog.catalog_group WHERE code='SUBJECT') AND code='INFORMATICS'", UUID.class);
        for (int i = 0; i < 25; i++) question(subject, "SINGLE_CHOICE", "0.25", """
                {"type":"SINGLE_CHOICE","options":[{"id":"a","content":"A","correct":true},{"id":"b","content":"B","correct":false}]}
                """);
        for (int i = 0; i < 4; i++) question(subject, "TRUE_FALSE", "1", """
                {"type":"TRUE_FALSE","scoringMethod":"COUNT_TABLE","scoreTable":[0.1,0.25,0.5,1],
                 "statements":[{"answer":true},{"answer":false},{"answer":true},{"answer":false}]}
                """);
        String create = "{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"INFORMATICS\",\"name\":\"SPT\",\"code\":\"S-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"durationMinutes\":60}";
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String base = "/api/v1/admin/exams/" + exam.path("id").asText();
        jdbc.update("UPDATE exam.exam SET timing='PER_PART', duration_minutes=NULL WHERE id=?::uuid", exam.path("id").asText());
        jdbc.update("UPDATE exam.exam_structure SET parts=jsonb_set(jsonb_set(parts,'{parts,0,durationMinutes}','10'::jsonb),'{parts,1,durationMinutes}','20'::jsonb) WHERE exam_id=?::uuid", exam.path("id").asText());
        entityManager.clear();
        JsonNode checked = mapper.readTree(mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(checked.path("status").asText()).isEqualTo("VALID");
        var paths = mapper.createArrayNode();
        checked.path("paths").forEach(path -> paths.add(path.path("pathId").asText()));
        assertThat(paths.size()).isEqualTo(2);
        var publish = mapper.createObjectNode().put("expectedRevision", exam.path("revision").asLong());
        publish.set("pathIds", paths);
        ExamListingFixture.fill(jdbc, UUID.fromString(exam.path("id").asText()));
        mockMvc.perform(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(publish.toString())).andExpect(status().isOk());
        JsonNode version = mapper.readTree(mockMvc.perform(get(base + "/versions/1").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode first = version.path("parts").get(0).path("paths");
        JsonNode second = version.path("parts").get(1).path("paths");
        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
        for (JsonNode path : first) {
            JsonNode other = java.util.stream.StreamSupport.stream(second.spliterator(), false)
                    .filter(row -> row.path("pathId").equals(path.path("pathId"))).findFirst().orElseThrow();
            assertThat(path.path("questionCount").asInt()).isEqualTo(20);
            assertThat(path.path("maxPoints").decimalValue()).isEqualByComparingTo("5");
            assertThat(path.path("durationMinutes").asInt()).isEqualTo(10);
            assertThat(other.path("questionCount").asInt()).isEqualTo(2);
            assertThat(other.path("maxPoints").decimalValue()).isEqualByComparingTo("2");
            assertThat(other.path("durationMinutes").asInt()).isEqualTo(20);
        }
    }

    private void question(UUID subject, String type, String points, String body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) VALUES (?,?,?,'READY',?,1,?::numeric)",
                id, "PUB-" + id, type, subject, points);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                id, "{\"content\":\"Test\",\"points\":" + points + ",\"body\":" + body + "}");
    }
}
