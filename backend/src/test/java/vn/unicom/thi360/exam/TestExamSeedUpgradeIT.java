package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2701 AC-7: a database that already holds the wave 2B seed ({@code develop} 1774c74d, kept under
 * {@code src/test/resources/exam/seed-2b}) takes the new seed: exams nobody edited carry the new structure,
 * an admin-edited exam and its refs stay as they are, and a second run changes nothing.
 */
@Transactional
class TestExamSeedUpgradeIT extends AbstractIntegrationTest {
    private static final String CURRENT_SEED = "db/devdata/R__seed_test_exam_data.sql";
    private static final String WAVE_2B_SEED = "exam/seed-2b/R__seed_test_exam_data.sql";

    /** Base exams whose structure the rebuild changes: the 2B seed must not already equal the new one. */
    private static final List<String> REBUILT = List.of("TEST-HSA-01", "TEST-HSA-02", "TEST-HSA-03", "TEST-HSA-04",
            "TEST-QDA-01", "TEST-BCA-01", "TEST-SPT-01", "TEST-SP2E-01", "TEST-SP2E-02", "TEST-SP2E-03",
            "TEST-THPTQG-02", "TEST-THPTQG-03");

    /** Exam columns that follow the structure (an upgrade that replaces a structure must refresh them too). */
    private static final Set<String> SHAPE = Set.of("kind_item_id", "scope", "part_code", "subject_item_id",
            "template_code", "structure_year", "matrix_year", "years_confirmed", "timing", "duration_minutes",
            "max_score", "reference_max", "practice_scoring", "question_count", "has_essay", "has_english_option",
            "combination_count");

    /** Fixtures the seed rebuild moves to another base exam: their 2B columns legitimately differ, the
        structure (parts, quotas, refs) is still checked in full. */
    private static final Map<String, Set<String>> SHAPE_CHANGES = Map.of("TEST-ERR-03",
            Set.of("part_code", "template_code", "duration_minutes"));

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;

    private record Snapshot(JsonNode row, String parts, String quotas, boolean edited, List<Map<String, Object>> refs) {
        JsonNode shape() {
            ObjectNode shape = ((ObjectNode) row).deepCopy();
            shape.retain(SHAPE);
            return shape;
        }

        JsonNode shapeWithout(Set<String> columns) {
            ObjectNode shape = (ObjectNode) shape();
            columns.forEach(shape::remove);
            return shape;
        }
    }

    @Test
    void upgradesUneditedWave2BSeed() throws Exception {
        Map<String, Snapshot> current = loadWave2BSeed();

        runSeed(CURRENT_SEED);
        assertEveryExamIsCurrent(current, null, null, "after the first run");

        runSeed(CURRENT_SEED);
        assertEveryExamIsCurrent(current, null, null, "after the second run");
    }

    @Test
    void keepsAdminEditedExam() throws Exception {
        Map<String, Snapshot> current = loadWave2BSeed();
        String editedCode = "TEST-HSA-02";
        UUID editedId = jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = ?", UUID.class, editedCode);

        // An admin edits the 2B structure through the API: one group's points change.
        JsonNode stored = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", editedId)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        ArrayNode parts = (ArrayNode) stored.path("parts").deepCopy();
        ObjectNode firstBlock = (ObjectNode) parts.findValues("blocks").getFirst().get(0);
        firstBlock.put("requiredPointsPerQuestion", firstBlock.path("requiredPointsPerQuestion").asInt(1) + 3);
        ObjectNode update = mapper.createObjectNode().put("expectedRevision", stored.path("revision").asLong());
        update.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", editedId).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isOk());

        Snapshot edited = snapshot(editedCode);
        assertThat(edited.edited()).as("the API marks the structure edited").isTrue();
        assertThat(edited.parts()).as("the edit is not the new seed structure")
                .isNotEqualTo(current.get(editedCode).parts());
        assertThat(edited.refs()).as("the API rewrote the refs").isNotEmpty();

        runSeed(CURRENT_SEED);
        assertEveryExamIsCurrent(current, editedCode, edited, "after the first run");

        runSeed(CURRENT_SEED);
        assertEveryExamIsCurrent(current, editedCode, edited, "after the second run");
    }

    /** DB as of wave 2B: the new seed (applied by Flyway) is removed and the 2B seed loaded in its place. */
    private Map<String, Snapshot> loadWave2BSeed() throws Exception {
        Map<String, Snapshot> current = snapshots();
        assertThat(current).as("the new seed installs 14 base exams and 13 error fixtures").hasSize(27);

        jdbc.update("DELETE FROM exam.exam WHERE code LIKE 'TEST-%'");
        assertThat(snapshots()).isEmpty();
        runSeed(WAVE_2B_SEED);

        Map<String, Snapshot> wave2b = snapshots();
        assertThat(wave2b.keySet()).as("the 2B seed has the same exam codes").isEqualTo(current.keySet());
        for (String code : REBUILT) {
            assertThat(wave2b.get(code).parts()).as("%s: 2B structure differs from the new one", code)
                    .isNotEqualTo(current.get(code).parts());
        }
        for (String code : wave2b.keySet()) {
            assertThat(wave2b.get(code).edited()).as("%s is unedited in the 2B DB", code).isFalse();
            assertThat(jdbc.queryForObject("""
                    SELECT s.structure_hash FROM exam.exam_structure s JOIN exam.exam e ON e.id = s.exam_id
                    WHERE e.code = ?""", String.class, code)).as("%s 2B hash", code).startsWith("seed-");
        }
        return current;
    }

    private void assertEveryExamIsCurrent(Map<String, Snapshot> current, String editedCode, Snapshot edited, String when)
            throws Exception {
        Map<String, Snapshot> actual = snapshots();
        assertThat(actual.keySet()).as("exam codes " + when).isEqualTo(current.keySet());
        for (var entry : actual.entrySet()) {
            String code = entry.getKey();
            Snapshot now = entry.getValue();
            if (code.equals(editedCode)) {
                assertThat(now.row()).as("%s row (the admin's exam) %s", code, when).isEqualTo(edited.row());
                assertThat(now.parts()).as("%s structure (the admin's exam) %s", code, when).isEqualTo(edited.parts());
                assertThat(now.quotas()).as("%s quotas (the admin's exam) %s", code, when).isEqualTo(edited.quotas());
                assertThat(now.edited()).as("%s still edited %s", code, when).isTrue();
                assertThat(now.refs()).as("%s refs (the admin's exam, ids included) %s", code, when).isEqualTo(edited.refs());
            } else {
                Snapshot expected = current.get(code);
                Set<String> changed = SHAPE_CHANGES.getOrDefault(code, Set.of());
                assertThat(now.parts()).as("%s carries the new structure %s", code, when).isEqualTo(expected.parts());
                assertThat(now.quotas()).as("%s quotas %s", code, when).isEqualTo(expected.quotas());
                assertThat(now.shapeWithout(changed))
                        .as("%s exam columns follow the new structure %s", code, when)
                        .isEqualTo(expected.shapeWithout(changed));
                assertThat(now.refs()).as("%s block refs %s", code, when).isEqualTo(expected.refs());
                assertThat(now.edited()).as("%s is not marked edited by the upgrade %s", code, when).isFalse();
            }
        }
    }

    private void runSeed(String resource) {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(resource));
    }

    private Map<String, Snapshot> snapshots() throws Exception {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        for (String code : jdbc.queryForList("SELECT code FROM exam.exam WHERE code LIKE 'TEST-%' ORDER BY code", String.class)) {
            result.put(code, snapshot(code));
        }
        return result;
    }

    private Snapshot snapshot(String code) throws Exception {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT e.id, (to_jsonb(e) - 'created_at' - 'updated_at')::text AS row,
                       s.parts::text AS parts, s.exam_quotas::text AS quotas, s.edited
                FROM exam.exam e JOIN exam.exam_structure s ON s.exam_id = e.id WHERE e.code = ?
                """, code);
        List<Map<String, Object>> refs = jdbc.queryForList("""
                SELECT id::text AS id, block_id, part_id, option_code, section_id, subject_item_id::text AS subject,
                       question_type, answer_types::text AS answer_types, knowledge_item_ids::text AS knowledge,
                       knowledge_mode, knowledge_inherited, tag_item_ids::text AS tags,
                       competency_item_ids::text AS competencies, required_points::text AS points,
                       combination_item_id::text AS combination
                FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL ORDER BY block_id
                """, row.get("id"));
        return new Snapshot(mapper.readTree((String) row.get("row")), mapper.readTree((String) row.get("parts")).toString(),
                mapper.readTree((String) row.get("quotas")).toString(), (Boolean) row.get("edited"), refs);
    }
}
