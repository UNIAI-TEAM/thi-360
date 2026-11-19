package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
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
 * UNI-2753 (AD-32): a section count of an option changed through the overview clears the option's expert
 * confirmation, so the check reports OPTION_UNCONFIRMED; options whose counts stay as they were keep it.
 */
@Transactional
class ExamOverviewConfirmationIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final String PARTS = """
            {"parts":[
              {"id":"p","order":1,"name":"Choice","questionCount":5,"questionCountOnPaper":5,
               "participation":{"mode":"CHOICE","choose":1},"sections":[],
               "options":[
                 {"id":"o1","kind":"SUBJECT","code":"%1$s","expertConfirmed":true,"sections":[
                   {"id":"s1","subjectCode":"%1$s","questionCount":5,
                    "blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":5}]}],"blocks":[]},
                 {"id":"o2","kind":"SUBJECT","code":"%2$s","expertConfirmed":true,"sections":[
                   {"id":"s2","subjectCode":"%2$s","questionCount":5,
                    "blocks":[{"id":"b2","questionType":"SINGLE_CHOICE","questionCount":5}]}],"blocks":[]}]}
            ]}""";

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private String subject() {
        UUID id = UUID.randomUUID();
        String code = "CONFIRM_" + id.toString().replace("-", "");
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Confirm subject', 'Confirm subject', 0, 1, false, 1, ?)
                """, id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return code;
    }

    private UUID exam() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes) "
                + "values (?, ?, ?, 'Overview confirmation', 'FULL', 'SHARED', 60)", id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, PARTS.formatted(subject(), subject()));
        return id;
    }

    private void overview(UUID id, List<Map<String, Object>> rows) throws Exception {
        String url = "/api/v1/admin/exams/" + id;
        JsonNode current = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String body = mapper.writeValueAsString(Map.of("expectedRevision", current.path("revision").asLong(),
                "overview", rows));
        mockMvc.perform(put(url + "/overview").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    private List<String> checkCodes(UUID id) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).findValuesAsText("code");
    }

    private Boolean confirmed(UUID id, String optionId) throws Exception {
        JsonNode parts = mapper.readTree(jdbc.queryForObject(
                "select parts::text from exam.exam_structure where exam_id=?", String.class, id));
        for (JsonNode option : parts.path("parts").get(0).path("options")) {
            if (optionId.equals(option.path("id").asText())) return option.path("expertConfirmed").asBoolean();
        }
        throw new AssertionError("Missing option " + optionId);
    }

    @Test
    void changingAnOptionSectionCountThroughTheOverviewClearsItsConfirmationAndTheCheckAsksAgain() throws Exception {
        UUID id = exam();
        assertThat(checkCodes(id)).doesNotContain("OPTION_UNCONFIRMED");
        overview(id, List.of(Map.of("partId", "p", "sectionId", "s1", "questionCount", 6)));
        assertThat(confirmed(id, "o1")).isFalse();
        assertThat(confirmed(id, "o2")).isTrue();
        assertThat(checkCodes(id)).contains("OPTION_UNCONFIRMED");
    }

    @Test
    void anOverviewChangeThatLeavesTheOptionCountsAsTheyWereKeepsItConfirmed() throws Exception {
        UUID id = exam();
        overview(id, List.of(Map.of("partId", "p", "questionCount", 6),
                Map.of("partId", "p", "sectionId", "s1", "questionCount", 5)));
        assertThat(confirmed(id, "o1")).isTrue();
        assertThat(confirmed(id, "o2")).isTrue();
        assertThat(checkCodes(id)).doesNotContain("OPTION_UNCONFIRMED");
    }
}
