package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamBcaTemplateCheckIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void bcaFullProvesWithReferenceWarningAndIsolatesPronunciationShortage() throws Exception {
        bank();
        ObjectNode create = mapper.createObjectNode().put("kind", "BCA").put("scope", "FULL")
                .put("buildMode", "STANDARD").put("name", "BCA acceptance")
                .put("code", "BCA-T-" + UUID.randomUUID().toString().substring(0, 8)).put("durationMinutes", 180);
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(create.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String base = "/api/v1/admin/exams/" + exam.path("id").asText();
        var load = mapper.createObjectNode().put("templateCode", "BCA_FULL").put("confirmReplace", true)
                .put("expectedRevision", exam.path("revision").asLong());
        JsonNode structure = mapper.readTree(mockMvc.perform(post(base + "/structure/template")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(load.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var update = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        update.set("parts", structure.path("parts"));
        update.set("examQuotas", mapper.readTree("""
                [{"id":"grade-reference","dimension":"GRADE","basis":"QUESTION","enforcement":"REFERENCE",
                  "buckets":[{"codes":["G10"],"percent":100}]}]
                """));
        mockMvc.perform(put(base + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(update.toString())).andExpect(status().isOk());
        var pronunciation = jdbc.queryForList("""
                select q.id from questionbank.question q
                where q.parent_id is null and q.deleted_at is null and q.status = 'READY'
                    and q.type = 'SINGLE_CHOICE' and q.points = 0.75
                    and q.subject_item_id = (select i.id from catalog.catalog_item i
                        join catalog.catalog_group g on g.id = i.group_id
                        where g.code = 'SUBJECT' and i.code = 'ANH')
                    and exists (select 1 from questionbank.question_tag t
                        where t.question_id = q.id and t.tag_item_id = (select i.id from catalog.catalog_item i
                            join catalog.catalog_group g on g.id = i.group_id
                            where g.code = 'QUESTION_TAG' and i.code = 'ANH_PHAT_AM'))
                """, UUID.class);
        for (UUID id : pronunciation) transition(id, "retire");
        JsonNode missing = check(base);
        var sourceErrors = StreamSupport.stream(missing.path("groups").spliterator(), false)
                .filter(group -> "SOURCE".equals(group.path("group").asText()))
                .flatMap(group -> StreamSupport.stream(group.path("issues").spliterator(), false))
                .filter(issue -> "ERROR".equals(issue.path("severity").asText())).toList();
        String pronunciationBlock = structure.path("parts").get(1).path("sections").get(2).path("blocks").get(0).path("id").asText();
        assertThat(sourceErrors).isNotEmpty().allSatisfy(issue ->
                assertThat(issue.path("blockId").asText()).isEqualTo(pronunciationBlock));
        for (UUID id : pronunciation) transition(id, "ready");
        JsonNode result = check(base);
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(result.path("groups").findValuesAsText("code"))
                .contains("EXAM_QUOTA_DEVIATION", "MANUAL_GRADING_REQUIRED");
        JsonNode current = mapper.readTree(mockMvc.perform(get(base + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var publish = mapper.createObjectNode().put("expectedRevision", current.path("revision").asLong());
        var paths = publish.putArray("pathIds");
        result.path("paths").forEach(path -> paths.add(path.path("pathId").asText()));
        ExamListingFixture.fill(jdbc, UUID.fromString(base.substring(base.lastIndexOf('/') + 1)));
        mockMvc.perform(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(publish.toString()))
                .andExpect(status().isOk());
    }

    private void bank() throws Exception {
        question("VAN", "G12", "ESSAY", 1, new BigDecimal("25"), "VAN_NGHI_LUAN_XA_HOI");
        singles("TOAN", "G10", 4); singles("TOAN", "G11", 7); singles("TOAN", "G12", 16);
        for (int i = 0; i < 5; i++) question("TOAN", "G12", "SHORT_ANSWER", 1, BigDecimal.ONE, null);
        question("TOAN", "G12", "QUESTION_SET", 3, BigDecimal.ONE, null);
        singles("SU", "G10", 1); singles("SU", "G11", 2); singles("SU", "G12", 4);
        question("SU", "G12", "QUESTION_SET", 3, BigDecimal.ONE, "SU_TU_LIEU_LICH_SU");
        question("ANH", "G12", "SINGLE_CHOICE", 1, new BigDecimal("0.75"), "ANH_PHAT_AM");
        question("ANH", "G12", "SINGLE_CHOICE", 1, new BigDecimal("0.75"), "ANH_TRONG_AM");
        for (int i = 0; i < 8; i++) question("ANH", "G12", "SINGLE_CHOICE", 1, new BigDecimal("0.75"), "ANH_TU_VUNG_NGU_PHAP");
        question("ANH", "G12", "QUESTION_SET", 5, new BigDecimal("0.75"), "ANH_DOC_DIEN");
        question("ANH", "G12", "QUESTION_SET", 5, new BigDecimal("0.75"), "ANH_DOC_HIEU");
        for (String subject : new String[]{"LY", "HOA", "SINH", "DIA"}) {
            int g10 = "HOA".equals(subject) ? 3 : 2;
            int g11 = "DIA".equals(subject) ? 1 : 3;
            singles(subject, "G10", g10); singles(subject, "G11", g11);
            singles(subject, "G12", 12 - g10 - g11);
            question(subject, "G12", "QUESTION_SET", 3, BigDecimal.ONE, null);
        }
    }
    private void singles(String subject, String grade, int count) throws Exception {
        for (int i = 0; i < count; i++) question(subject, grade, "SINGLE_CHOICE", 1, BigDecimal.ONE, null);
    }

    private UUID question(String subject, String grade, String type, int count, BigDecimal points, String tag) throws Exception {
        var request = mapper.createObjectNode().put("type", type).put("subjectCode", subject).put("gradeCode", grade)
                .put("difficultyCode", "NB").put("taskTypeCode", "SINGLE").put("points", points)
                .put("content", "<p>Acceptance question</p>");
        if (tag != null) request.putArray("tagCodes").add(tag);
        ObjectNode body = request.putObject("body").put("type", type);
        if ("ESSAY".equals(type)) body.put("gradingGuide", "<p>Manual grading guide</p>");
        else if ("SHORT_ANSWER".equals(type)) body.putArray("answers").add("42");
        else if ("QUESTION_SET".equals(type)) {
            body.put("passage", "<p>Passage</p>");
            var children = body.putArray("children");
            for (int i = 0; i < count; i++) {
                var child = children.addObject().put("type", "SINGLE_CHOICE").put("content", "<p>Child</p>").put("points", points);
                choices(child.putObject("body").put("type", "SINGLE_CHOICE"));
            }
        } else choices(body);
        JsonNode response = mapper.readTree(mockMvc.perform(post("/api/v1/admin/questions")
                        .with(asUser(Permissions.QUESTION_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(response.at("/summary/id").asText());
        transition(id, "ready");
        return id;
    }

    private static void choices(ObjectNode body) {
        var options = body.putArray("options");
        options.addObject().put("id", "a").put("content", "<p>A</p>").put("correct", true);
        options.addObject().put("id", "b").put("content", "<p>B</p>").put("correct", false);
    }

    private void transition(UUID id, String action) throws Exception {
        Long revision = jdbc.queryForObject("select revision from questionbank.question where id=?", Long.class, id);
        mockMvc.perform(post("/api/v1/admin/questions/{id}/" + action, id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode check(String base) throws Exception {
        return mapper.readTree(mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
