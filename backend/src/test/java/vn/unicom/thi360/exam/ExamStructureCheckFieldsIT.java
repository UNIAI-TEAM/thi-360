package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamStructureCheckFieldsIT extends AbstractIntegrationTest {
    private static final String URL = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001/structure";
    private static final UUID TAG_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000008");
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void fractionalQuotaIsRejectedWhenSaving() throws Exception {
        JsonNode old = mapper.readTree(mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = old.path("parts").deepCopy();
        ObjectNode quota = (ObjectNode) parts.get(0).path("sections").get(0)
                .path("answerQuotas").path("items").get(0);
        quota.put("value", new java.math.BigDecimal("1.5"));
        ObjectNode request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", old.path("revision").asLong());
        mockMvc.perform(put(URL).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
    }

    @Test
    void legacyStructureDefaultsAndNewCheckFieldsRoundTrip() throws Exception {
        UUID tag = UUID.randomUUID();
        String code = "CHECK-" + tag.toString().substring(0, 8);
        jdbc.update("INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, "
                        + "name_vi, display_order, status, is_system, depth, path) "
                        + "VALUES (?, ?, ?, ?, 1, 'Check tag', 0, 1, false, 1, ?)",
                tag, TAG_GROUP, code, code.toLowerCase(Locale.ROOT), "/" + tag + "/");

        JsonNode old = mapper.readTree(mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode oldBlock = old.path("parts").get(0).path("sections").get(0).path("blocks").get(0);
        assertThat(oldBlock.path("tagCodes").isArray()).isTrue();
        assertThat(oldBlock.path("tagCodes")).isEmpty();
        assertThat(oldBlock.path("expectedScoring").isMissingNode()).isTrue();

        var parts = old.path("parts").deepCopy();
        ObjectNode block = (ObjectNode) parts.get(0).path("sections").get(0).path("blocks").get(0);
        block.putArray("tagCodes").add(code);
        block.putObject("expectedScoring").put("multipleChoiceRule", "ALL_OR_NOTHING");
        ((ObjectNode) parts.get(2).path("options").get(0)).put("expertConfirmed", false);
        ObjectNode request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", old.path("revision").asLong());
        mockMvc.perform(put(URL).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        JsonNode saved = mapper.readTree(mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode savedBlock = saved.path("parts").get(0).path("sections").get(0).path("blocks").get(0);
        assertThat(savedBlock.path("tagCodes").get(0).asText()).isEqualTo(code);
        assertThat(savedBlock.path("expectedScoring").path("multipleChoiceRule").asText()).isEqualTo("ALL_OR_NOTHING");
        assertThat(saved.path("parts").get(2).path("options").get(0).path("expertConfirmed").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT ? = ANY(tag_item_ids) FROM exam.exam_block_ref "
                        + "WHERE exam_id = ? AND block_id = ? AND version_no IS NULL", Boolean.class,
                tag, UUID.fromString("00000000-0000-7000-8700-000000000001"), savedBlock.path("id").asText()))
                .isTrue();
    }
}
