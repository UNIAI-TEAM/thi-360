package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2753 (Q11): the overview count of a FIXED part carries its printed count ({@code questionCountOnPaper}) along
 * when it grows past it. A smaller count and parts that are not FIXED keep the stored printed count.
 */
@Transactional
class ExamOverviewOnPaperIT extends AbstractIntegrationTest {
    private static final String PARTS = """
            {"parts":[
              {"id":"fixed","order":1,"name":"Fixed","questionCount":10,"questionCountOnPaper":10,
               "participation":{"mode":"ALL"},
               "sections":[{"id":"fixed-s","subjectCode":"TOAN","questionCount":10,"blocks":[]}]},
              {"id":"choice","order":2,"name":"Choice","questionCount":10,"questionCountOnPaper":10,
               "participation":{"mode":"CHOICE","choose":1},
               "options":[{"id":"choice-o","kind":"SUBJECT","code":"TOAN","sections":[
                 {"id":"choice-s","subjectCode":"TOAN","questionCount":10,"blocks":[]}],"blocks":[]}]}
            ]}""";

    /** A coded (QDA/BCA style) part is stored as ALL, but its catalog choice paths decide the printed count. */
    private static final String CODED = """
            {"parts":[
              {"id":"coded","order":1,"name":"Coded","questionCount":10,"questionCountOnPaper":10,
               "participation":{"mode":"ALL"},"codeSource":{"source":"GLOBAL_LIST"},"sections":[],
               "subjectPool":[{"id":"coded-s","subjectCode":"TOAN","questionCount":10,"blocks":[]}]}
            ]}""";

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private UUID exam() {
        return exam(PARTS);
    }

    private UUID exam(String parts) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes) "
                + "values (?, ?, ?, 'Overview on paper', 'FULL', 'SHARED', 60)", id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, parts);
        return id;
    }

    private JsonNode overview(UUID id, String partId, int count) throws Exception {
        String url = "/api/v1/admin/exams/" + id;
        JsonNode current = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String body = mapper.writeValueAsString(Map.of("expectedRevision", current.path("revision").asLong(),
                "overview", List.of(Map.of("partId", partId, "questionCount", count))));
        return mapper.readTree(mockMvc.perform(put(url + "/overview").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode part(JsonNode detail, String id) {
        for (JsonNode part : detail.path("parts")) {
            if (id.equals(part.path("id").asText())) return part;
        }
        throw new AssertionError("Missing part " + id + " in " + detail);
    }

    @Test
    void aFixedPartCountThatGrowsPastItsPrintedCountPullsThePrintedCountAlong() throws Exception {
        JsonNode detail = overview(exam(), "fixed", 12);
        assertThat(part(detail, "fixed").path("questionCount").asInt()).isEqualTo(12);
        assertThat(part(detail, "fixed").path("questionCountOnPaper").asInt()).isEqualTo(12);
    }

    @Test
    void aFixedPartCountThatShrinksBelowItsPrintedCountLeavesThePrintedCount() throws Exception {
        JsonNode detail = overview(exam(), "fixed", 8);
        assertThat(part(detail, "fixed").path("questionCount").asInt()).isEqualTo(8);
        assertThat(part(detail, "fixed").path("questionCountOnPaper").asInt()).isEqualTo(10);
    }

    @Test
    void aChoicePartKeepsItsPrintedCountAndTheOtherPartIsUntouched() throws Exception {
        JsonNode detail = overview(exam(), "choice", 12);
        assertThat(part(detail, "choice").path("questionCount").asInt()).isEqualTo(12);
        assertThat(part(detail, "choice").path("questionCountOnPaper").asInt()).isEqualTo(10);
        assertThat(part(detail, "fixed").path("questionCount").asInt()).isEqualTo(10);
        assertThat(part(detail, "fixed").path("questionCountOnPaper").asInt()).isEqualTo(10);
    }

    @Test
    void aCodedPartStoredAsAllKeepsItsPrintedCountWhenItsCountGrows() throws Exception {
        JsonNode detail = overview(exam(CODED), "coded", 12);
        assertThat(part(detail, "coded").path("questionCount").asInt()).isEqualTo(12);
        assertThat(part(detail, "coded").path("questionCountOnPaper").asInt()).isEqualTo(10);
    }
}
