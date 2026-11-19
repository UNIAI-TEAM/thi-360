package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.spi.CombinationLookup;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2701 AC-7: a database that already holds the wave 2B seed ({@code develop} 1774c74d, kept under
 * {@code src/test/resources/exam/seed-2b}) takes the new seed. An exam nobody touched (structure, header,
 * status, versions) becomes the fresh fixture exactly, once; anything an admin edited, published or versioned
 * stays byte for byte, and a second run changes nothing.
 */
@Transactional
class TestExamSeedUpgradeIT extends AbstractIntegrationTest {
    private static final String CURRENT_SEED = "db/devdata/R__seed_test_exam_data.sql";
    private static final String WAVE_2B_SEED = "exam/seed-2b/R__seed_test_exam_data.sql";
    private static final String GLOBAL_LIST_GROUP = "00000000-0000-7000-8100-000000000007";
    private static final String KIND_HSA = "00000000-0000-7000-8200-000000000101";
    private static final String KIND_QDA = "00000000-0000-7000-8200-000000000102";
    private static final String SUBJECT_TOAN = "00000000-0000-7000-8200-000000000201";

    /** Base exams whose structure the rebuild changes: the 2B seed must not already equal the new one. */
    private static final List<String> REBUILT = List.of("TEST-HSA-01", "TEST-HSA-02", "TEST-HSA-03", "TEST-HSA-04",
            "TEST-QDA-01", "TEST-BCA-01", "TEST-SPT-01", "TEST-SP2E-01", "TEST-SP2E-02", "TEST-SP2E-03",
            "TEST-THPTQG-02", "TEST-THPTQG-03");

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;
    @Autowired CombinationLookup combinationLookup;

    @Test
    void upgradesUneditedWave2BSeed() throws Exception {
        Map<String, JsonNode> fresh = loadWave2BSeed();
        Map<String, JsonNode> wave2b = states();

        runSeed(CURRENT_SEED);
        Map<String, JsonNode> first = states();
        assertEveryExamIsUpgraded(fresh, wave2b, first, "after the first run");

        runSeed(CURRENT_SEED);
        assertThat(states()).as("the second run changes nothing: revision, marker, timestamps included")
                .isEqualTo(first);
    }

    /** An unedited exam that was checked still upgrades; its check run stays and only the status needs a recheck. */
    @Test
    void upgradesCheckedExamAndMarksItForRecheck() throws Exception {
        Map<String, JsonNode> fresh = loadWave2BSeed();
        String code = "TEST-HSA-02";
        JsonNode result = check(code);
        JsonNode checked = state(code);
        assertThat(checked.path("exam").path("check_status").asText())
                .as("a real check wrote a status (%s)", result.path("status").asText())
                .isIn("VALID", "INVALID").isEqualTo(result.path("status").asText());
        assertThat(checked.path("checkRun").isObject()).as("the check wrote its run").isTrue();
        assertThat(checked.path("exam").path("revision").asLong())
                .as("a check does not move the exam revision").isEqualTo(0);
        Map<String, JsonNode> wave2b = states();

        runSeed(CURRENT_SEED);
        Map<String, JsonNode> first = states();
        assertEveryExamIsUpgraded(fresh, wave2b, first, "after the first run");
        JsonNode upgraded = first.get(code);
        assertThat(upgraded.path("exam").path("check_status").asText()).isEqualTo("NEEDS_RECHECK");
        assertThat(upgraded.path("exam").path("revision").asLong()).as("revision + 1, once").isEqualTo(1);
        assertThat(upgraded.path("checkRun")).as("the check run is kept as it was").isEqualTo(checked.path("checkRun"));

        runSeed(CURRENT_SEED);
        assertThat(states()).as("the second run changes nothing").isEqualTo(first);
    }

    @Test
    void keepsAdminEditedExam() throws Exception {
        loadWave2BSeed();
        String editedCode = "TEST-HSA-02";
        UUID editedId = idOf(editedCode);

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

        JsonNode edited = state(editedCode);
        assertThat(edited.path("structure").path("edited").asBoolean()).as("the API marks the structure edited").isTrue();
        assertThat(edited.path("structure").path("structure_hash").asText()).doesNotStartWith("seed");
        assertThat(edited.path("refs")).as("the API rewrote the refs").isNotEmpty();

        assertKeptThroughTwoRuns(Map.of(editedCode, edited));
    }

    /**
     * PATCH exam info edits the header only: the structure row keeps its 2B hash and {@code edited = false},
     * so the structure alone cannot tell the admin touched the exam.
     */
    @Test
    void keepsExamWhoseHeaderWasPatched() throws Exception {
        Map<String, JsonNode> fresh = loadWave2BSeed();
        Map<String, String> bodies = new LinkedHashMap<>();
        bodies.put("TEST-BCA-01", "{\"durationMinutes\":170}");
        bodies.put("TEST-HSA-02", "{\"maxScore\":55}");
        bodies.put("TEST-HSA-03", "{\"name\":\"Tên do admin đặt\"}");
        bodies.put("TEST-HSA-04", "{\"description\":\"Mô tả do admin viết\"}");
        bodies.put("TEST-QDA-01", "{\"instructions\":\"Hướng dẫn do admin viết\"}");
        bodies.put("TEST-SP2E-02", "{\"yearsConfirmed\":false}");
        bodies.put("TEST-THPTQG-01", "{\"practiceScoring\":\"LINEAR\",\"confirmScoring\":true,\"referenceMax\":20}");
        Map<String, JsonNode> patched = new LinkedHashMap<>();
        for (var entry : bodies.entrySet()) {
            String code = entry.getKey();
            JsonNode before = state(code);
            ObjectNode body = (ObjectNode) mapper.readTree(entry.getValue());
            body.put("expectedRevision", before.path("exam").path("revision").asLong());
            mockMvc.perform(patch("/api/v1/admin/exams/{id}", idOf(code)).with(asUser(Permissions.EXAM_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isOk());
            JsonNode after = state(code);
            assertThat(after.path("exam")).as("%s: the PATCH changed the exam row", code).isNotEqualTo(before.path("exam"));
            assertThat(after.path("structure").path("structure_hash").asText())
                    .as("%s: a header edit keeps the 2B structure hash", code).isEqualTo("seed-" + code.toLowerCase(Locale.ROOT));
            assertThat(after.path("structure").path("edited").asBoolean()).as("%s: and does not mark it edited", code).isFalse();
            patched.put(code, after);
        }
        // Control: an exam nobody touched still upgrades in the same run.
        Map<String, JsonNode> wave2b = states();

        assertKeptThroughTwoRuns(patched);
        Map<String, JsonNode> after = states();
        SoftAssertions softly = new SoftAssertions();
        for (String code : wave2b.keySet()) {
            if (!patched.containsKey(code)) {
                assertUpgraded(softly, code, fresh.get(code), wave2b.get(code), after.get(code), "the untouched control");
            }
        }
        softly.assertAll();
    }

    /** Published, versioned and discontinued exams are never replaced, whichever guard protects them. */
    @Test
    void keepsPublishedAndVersionedExams() throws Exception {
        loadWave2BSeed();
        Map<String, JsonNode> kept = new LinkedHashMap<>();

        // Published through the API: status, version, version refs, serving data.
        String published = "TEST-HSA-02";
        publish(published);
        JsonNode state = state(published);
        assertThat(state.path("exam").path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(state.path("versions")).as("%s has a published version", published).hasSize(1);
        assertThat(state.path("refs").findValuesAsText("version_no"))
                .as("%s has refs of the published version", published).contains("1");
        kept.put(published, state);

        // A draft that has a prior version: the version guard alone protects it.
        String draftWithVersion = "TEST-HSA-03";
        publish(draftWithVersion);
        jdbc.update("UPDATE exam.exam SET status = 'DRAFT' WHERE code = ?", draftWithVersion);
        state = state(draftWithVersion);
        assertThat(state.path("exam").path("status").asText()).isEqualTo("DRAFT");
        assertThat(state.path("versions")).isNotEmpty();
        kept.put(draftWithVersion, state);

        // Published status without a version row: the status guard alone protects it.
        String publishedStatusOnly = "TEST-HSA-04";
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE code = ?", publishedStatusOnly);
        state = state(publishedStatusOnly);
        assertThat(state.path("versions")).isEmpty();
        kept.put(publishedStatusOnly, state);

        assertKeptThroughTwoRuns(kept);
    }

    /** The persisted count follows the current active catalog list, as the structure and the checker read it. */
    @Test
    void combinationCountFollowsCurrentCatalog() throws Exception {
        Map<String, JsonNode> fresh = loadWave2BSeed();
        addCombination("QD06", KIND_QDA, 1);
        addCombination("QD07", KIND_QDA, 2);   // inactive: not counted
        addCombination("HS01", KIND_HSA, 1);   // another exam kind: not counted
        long qdaCount = combinationLookup.listCombinations(UUID.fromString(KIND_QDA)).stream()
                .map(view -> view.itemCode()).distinct().count();
        assertThat(qdaCount).as("the catalog now lists six active QDA codes").isEqualTo(6);

        runSeed(CURRENT_SEED);

        assertThat(combinationCount("TEST-QDA-01")).as("QDA follows the catalog").isEqualTo(6).isEqualTo((int) qdaCount);
        assertThat(combinationCount("TEST-BCA-01")).as("BCA is untouched by QDA codes").isEqualTo(4)
                .isEqualTo((int) combinationLookup.listCombinations(UUID.fromString(
                        "00000000-0000-7000-8400-000000000103")).stream().map(view -> view.itemCode()).distinct().count());
        assertThat(combinationCount("TEST-HSA-01")).as("no code-source part: no combinations").isEqualTo(0);
        JsonNode structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", idOf("TEST-QDA-01"))
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(structure.path("combinations").findValuesAsText("code").stream().distinct().count())
                .as("the structure view lists the same six").isEqualTo(6);
        assertThat(fresh.get("TEST-QDA-01").path("exam").path("combination_count").asInt()).as("fresh seed").isEqualTo(5);
    }

    // --- assertions ---------------------------------------------------------------------------------------------

    /** Edited or published exams, and every other exam upgraded, across a first and a second seed run. */
    private void assertKeptThroughTwoRuns(Map<String, JsonNode> kept) throws Exception {
        runSeed(CURRENT_SEED);
        for (var entry : kept.entrySet()) {
            assertThat(state(entry.getKey())).as("%s is kept as it was after the first run", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
        runSeed(CURRENT_SEED);
        for (var entry : kept.entrySet()) {
            assertThat(state(entry.getKey())).as("%s is kept as it was after the second run", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    private void assertEveryExamIsUpgraded(Map<String, JsonNode> fresh, Map<String, JsonNode> wave2b,
            Map<String, JsonNode> actual, String when) {
        assertThat(actual.keySet()).as("exam codes " + when).isEqualTo(fresh.keySet());
        SoftAssertions softly = new SoftAssertions();
        for (String code : actual.keySet()) {
            assertUpgraded(softly, code, fresh.get(code), wave2b.get(code), actual.get(code), when);
        }
        softly.assertAll();
    }

    /**
     * An upgraded exam is the fresh fixture in every column (name and description included), carries the new
     * marker, moved one revision up, and kept its check history and status except for the recheck.
     */
    private static void assertUpgraded(SoftAssertions softly, String code, JsonNode fresh, JsonNode wave2b,
            JsonNode actual, String when) {
        ObjectNode expectedExam = (ObjectNode) scrub(fresh.path("exam"));
        expectedExam.put("revision", wave2b.path("exam").path("revision").asInt() + 1);
        if (!"UNCHECKED".equals(wave2b.path("exam").path("check_status").asText())) {
            expectedExam.put("check_status", "NEEDS_RECHECK");
            expectedExam.set("check_issue_count", wave2b.path("exam").path("check_issue_count"));
            expectedExam.set("checked_at", wave2b.path("exam").path("checked_at"));
        }
        softly.assertThat(scrub(actual.path("exam"))).as("%s exam row %s", code, when).isEqualTo(expectedExam);
        softly.assertThat(actual.path("structure").path("structure_hash").asText())
                .as("%s marker %s", code, when).isEqualTo("seed3-" + code.toLowerCase(Locale.ROOT));
        softly.assertThat(scrub(actual.path("structure"))).as("%s structure row %s", code, when)
                .isEqualTo(scrub(fresh.path("structure")));
        softly.assertThat(scrub(actual.path("refs"))).as("%s block refs %s", code, when)
                .isEqualTo(scrub(fresh.path("refs")));
        softly.assertThat(actual.path("versions")).as("%s versions %s", code, when).isEqualTo(wave2b.path("versions"));
        softly.assertThat(actual.path("checkRun")).as("%s check run %s", code, when).isEqualTo(wave2b.path("checkRun"));
    }

    /** The row without the columns that differ by clock or by who wrote it. */
    private static JsonNode scrub(JsonNode node) {
        JsonNode copy = node.deepCopy();
        removeFields(copy);
        return copy;
    }

    private static void removeFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.remove(List.of("created_at", "updated_at", "updated_by", "created_by"));
            object.elements().forEachRemaining(TestExamSeedUpgradeIT::removeFields);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(TestExamSeedUpgradeIT::removeFields);
        }
    }

    // --- database state -----------------------------------------------------------------------------------------

    /** DB as of wave 2B: the new seed (applied by Flyway) is removed and the 2B seed loaded in its place. */
    private Map<String, JsonNode> loadWave2BSeed() throws Exception {
        Map<String, JsonNode> fresh = states();
        assertThat(fresh).as("the new seed installs 14 base exams and 13 error fixtures").hasSize(27);

        jdbc.update("DELETE FROM exam.exam WHERE code LIKE 'TEST-%'");
        assertThat(states()).isEmpty();
        runSeed(WAVE_2B_SEED);

        Map<String, JsonNode> wave2b = states();
        assertThat(wave2b.keySet()).as("the 2B seed has the same exam codes").isEqualTo(fresh.keySet());
        for (String code : REBUILT) {
            assertThat(wave2b.get(code).path("structure").path("parts"))
                    .as("%s: 2B structure differs from the new one", code)
                    .isNotEqualTo(fresh.get(code).path("structure").path("parts"));
        }
        for (String code : wave2b.keySet()) {
            JsonNode structure = wave2b.get(code).path("structure");
            assertThat(structure.path("edited").asBoolean()).as("%s is unedited in the 2B DB", code).isFalse();
            assertThat(structure.path("structure_hash").asText()).as("%s 2B hash", code)
                    .isEqualTo("seed-" + code.toLowerCase(Locale.ROOT));
            assertThat(wave2b.get(code).path("exam").path("revision").asLong()).as("%s 2B revision", code).isEqualTo(0);
        }
        return fresh;
    }

    private void runSeed(String resource) {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(resource));
    }

    private Map<String, JsonNode> states() throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (String code : jdbc.queryForList("SELECT code FROM exam.exam WHERE code LIKE 'TEST-%' ORDER BY code", String.class)) {
            result.put(code, state(code));
        }
        return result;
    }

    private UUID idOf(String code) {
        return jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = ?", UUID.class, code);
    }

    private int combinationCount(String code) {
        return jdbc.queryForObject("SELECT combination_count FROM exam.exam WHERE code = ?", Integer.class, code);
    }

    /** Everything the seed may write for one exam, every column, so an untouched exam compares equal as a whole. */
    private JsonNode state(String code) throws Exception {
        UUID id = idOf(code);
        ObjectNode state = mapper.createObjectNode();
        state.set("exam", one("SELECT to_jsonb(e) FROM exam.exam e WHERE e.id = ?", id));
        state.set("structure", one("SELECT to_jsonb(s) FROM exam.exam_structure s WHERE s.exam_id = ?", id));
        state.set("refs", many("""
                SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.version_no NULLS FIRST, r.block_id), '[]'::jsonb)
                FROM exam.exam_block_ref r WHERE r.exam_id = ?""", id));
        state.set("versions", many("""
                SELECT coalesce(jsonb_agg(to_jsonb(v) ORDER BY v.version_no), '[]'::jsonb)
                FROM exam.exam_version v WHERE v.exam_id = ?""", id));
        state.set("checkRun", one("SELECT to_jsonb(c) FROM exam.exam_check_run c WHERE c.exam_id = ?", id));
        return state;
    }

    private JsonNode one(String sql, UUID id) throws Exception {
        List<String> rows = jdbc.queryForList(sql, String.class, id);
        return rows.isEmpty() ? mapper.nullNode() : mapper.readTree(rows.getFirst());
    }

    private JsonNode many(String sql, UUID id) throws Exception {
        return mapper.readTree(jdbc.queryForObject(sql, String.class, id));
    }

    // --- API helpers --------------------------------------------------------------------------------------------

    private JsonNode check(String code) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", idOf(code))
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** A real check and a real publish of every path the check found. */
    private void publish(String code) throws Exception {
        JsonNode result = check(code);
        assertThat(result.path("status").asText()).as("%s must check VALID to publish", code).isEqualTo("VALID");
        ObjectNode body = mapper.createObjectNode();
        ArrayNode pathIds = body.putArray("pathIds");
        result.path("paths").forEach(path -> pathIds.add(path.path("pathId").asText()));
        body.put("expectedRevision", jdbc.queryForObject("SELECT revision FROM exam.exam WHERE code = ?", Long.class, code));
        ExamListingFixture.fill(jdbc, idOf(code));
        mockMvc.perform(post("/api/v1/admin/exams/{id}/publish", idOf(code))
                        .with(asUser(Permissions.EXAM_PUBLISH, Permissions.EXAM_MODIFY, Permissions.EXAM_VIEW))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk());
    }

    /** One item of the SUBJECT_COMBINATION list, with its current version for {@code kind}; status 2 = inactive. */
    private void addCombination(String code, String kind, int status) {
        UUID item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en,
                    description, display_order, status, is_system, parent_id, depth, path)
                SELECT ?::uuid, g.id, ?, ?, GREATEST(g.last_value,
                    COALESCE((SELECT MAX(value) FROM catalog.catalog_item WHERE group_id = g.id), 0)) + 1,
                    ?, ?, '[TEST] combination', 90, ?, false, NULL, 1, '/' || ?::text || '/'
                FROM catalog.catalog_group g WHERE g.id = ?::uuid""",
                item, code, code.toLowerCase(Locale.ROOT), code, code, status, item, GLOBAL_LIST_GROUP);
        UUID version = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog.combination_version (id, item_id, version_no, exam_item_id, source_note, is_current)
                VALUES (?, ?, 1, ?::uuid, '[TEST]', true)""", version, item, kind);
        jdbc.update("INSERT INTO catalog.combination_subject (combination_version_id, subject_item_id, position) "
                + "VALUES (?, ?::uuid, 0)", version, SUBJECT_TOAN);
    }
}
