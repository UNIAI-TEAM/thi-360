package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * CR-4 / AD-75: the seeded HSA dev exam keeps passing the R-12 rules with sources, generates a paper with the science
 * sets and short answers of every selected subject (also in the section the generator trims from 17 to 16 questions),
 * and an older database gets the new structure and refs when the seed file runs again, without a key clash.
 */
@Transactional
class ExamSeedR12IT extends AbstractIntegrationTest {
    private static final UUID EXAM = UUID.fromString("00000000-0000-7000-8700-000000000001");
    private static final String SCIENCE = "00000000-0000-7000-8800-000000000070";
    private static final String HASH = "dev-hsa-structure-v4";
    private static final String FIRST_NEW_BLOCK = "00000000-0000-7000-8800-000000000080";
    private static final String LAST_NEW_BLOCK = "00000000-0000-7000-8800-000000000087";
    private static final String REMOVED_BLOCK = "00000000-0000-7000-8800-000000000044";
    private static final String HOLDER_SECTION = "00000000-0000-7000-8800-000000000040";
    private static final List<String> FILL_SUBJECTS = List.of("LY", "HOA", "SINH");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private DataSource dataSource;

    /**
     * Each selected science subject has its own mix on the generated paper: one set of three children, one short answer
     * for Lý, Hóa, Sinh and none for Sử, Địa, and the section total is 17, or 16 for the one the generator trims. The
     * seeds are many so that every selected subject is the trimmed one in some paper: the fill must survive the trim.
     */
    @Test
    void seededHsaDevExamGeneratesTheMixOfEverySelectedScienceSubject() throws Exception {
        for (String[] picks : new String[][] {{"LY", "HOA", "SINH"}, {"SU", "DIA", "LY"}, {"HOA", "SU", "DIA"}}) {
            java.util.Set<String> trimmedSubjects = new java.util.TreeSet<>();
            // Lý is always the trimmed section here and the generator drops one standalone question at random: many seeds
            // so that a section that may drop its short answer (no answer quota) is caught.
            int seeds = List.of(picks).equals(List.of("SU", "DIA", "LY")) ? 40 : 12;
            for (int seed = 1; seed <= seeds; seed++) {
                JsonNode preview = preview(picks, seed);
                assertThat(preview.path("status").asText()).as(preview.toString()).isEqualTo("GENERATED");
                int sixteen = 0;
                for (String subject : picks) {
                    int questions = 0;
                    int sets = 0;
                    int setChildren = 0;
                    int fills = 0;
                    for (JsonNode part : preview.path("paper").path("parts")) {
                        for (JsonNode block : part.path("blocks")) {
                            if (!subject.equals(block.path("subjectCode").asText())) continue;
                            for (JsonNode item : block.path("items")) {
                                if ("SET".equals(item.path("kind").asText())) {
                                    sets++;
                                    setChildren += item.path("children").size();
                                    questions += item.path("children").size();
                                } else {
                                    questions++;
                                    if ("SHORT_ANSWER".equals(item.path("question").path("type").asText())) fills++;
                                }
                            }
                        }
                    }
                    String where = String.join("+", picks) + " seed " + seed + " " + subject;
                    assertThat(sets).as("sets of " + where).isEqualTo(1);
                    assertThat(setChildren).as("set children of " + where).isEqualTo(3);
                    assertThat(fills).as("short answers of " + where).isEqualTo(FILL_SUBJECTS.contains(subject) ? 1 : 0);
                    assertThat(questions).as("questions of " + where).isIn(16, 17);
                    if (questions == 16) {
                        sixteen++;
                        trimmedSubjects.add(subject);
                    }
                }
                assertThat(sixteen).as("exactly one trimmed section in " + String.join("+", picks) + " seed " + seed).isEqualTo(1);
            }
            // The generator trims one section by the choice-group rule: with natural and social subjects mixed it is the
            // minority group (Lý, Hóa, Sinh are natural: the fill subject is the trimmed one), otherwise every subject in turn.
            java.util.Set<String> natural = new java.util.TreeSet<>();
            java.util.Set<String> social = new java.util.TreeSet<>();
            for (String subject : picks) (FILL_SUBJECTS.contains(subject) ? natural : social).add(subject);
            java.util.Set<String> expected = natural.isEmpty() || social.isEmpty() ? java.util.Set.of(picks)
                    : natural.size() == 1 ? natural : social;
            assertThat(trimmedSubjects).as("subjects trimmed to 16 in " + String.join("+", picks)).isEqualTo(expected);
        }
    }

    @Test
    void everySeededHsaQdaStandardExamKeepsTheR12Rules() throws Exception {
        List<Map<String, Object>> exams = jdbc.queryForList("SELECT e.id, e.code, k.code AS kind FROM exam.exam e "
                + "JOIN catalog.catalog_item k ON k.id = e.kind_item_id WHERE k.code IN ('HSA', 'QDA') "
                + "AND e.build_mode <> 'REVIEW' ORDER BY e.code");
        assertThat(exams).isNotEmpty();
        List<String> offenders = new ArrayList<>();
        for (Map<String, Object> exam : exams) {
            JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam.get("id") + "/check")
                            .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            List<String> codes = new ArrayList<>();
            for (JsonNode list : result.path("groups").findValues("issues")) list.forEach(i -> codes.add(i.path("code").asText()));
            System.out.println("R12-SEED " + exam.get("kind") + " " + exam.get("code") + " status=" + result.path("status").asText()
                    + " r12=" + codes.stream().filter(c -> c.equals("SECTION_SET_COUNT_RANGE") || c.equals("SHORT_ANSWER_MIN")).toList());
            if (codes.contains("SECTION_SET_COUNT_RANGE") || codes.contains("SHORT_ANSWER_MIN")) offenders.add((String) exam.get("code"));
        }
        assertThat(offenders).as("seeded HSA/QDA standard exams that break R-12").isEmpty();
    }

    /** The seed file is a repeatable migration: running it on a database that holds an older seed upgrades that row only. */
    @Test
    void rerunUpgradesTheOldSeedAndKeepsAnEditedStructure() throws Exception {
        String upgraded = parts();
        long blockRefs = refs();
        assertThat(hash()).isEqualTo(HASH);

        // An older database (v2, then v3): the old hash and a structure without the science sets, short answers and quotas.
        for (String oldHash : new String[] {"dev-hsa-structure-v2", "dev-hsa-structure-v3"}) {
            jdbc.update("UPDATE exam.exam_structure SET structure_hash = ?, "
                    + "parts = jsonb_set(parts, '{parts,2,options,0,sections}', '[]'::jsonb) WHERE exam_id = ?", oldHash, EXAM);
            deleteNewRefs();
            assertThat(refs()).isLessThan(blockRefs);
            runSeed();
            assertThat(hash()).isEqualTo(HASH);
            assertThat(mapper.readTree(parts())).isEqualTo(mapper.readTree(upgraded));
            assertThat(refs()).isEqualTo(blockRefs);
        }

        // Running it again changes nothing.
        runSeed();
        assertThat(mapper.readTree(parts())).isEqualTo(mapper.readTree(upgraded));

        // A structure that someone edited (other hash) is not overwritten.
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = 'edited-by-hand', parts = jsonb_set(parts, '{parts,0,name}', '\"Edited\"') WHERE exam_id = ?", EXAM);
        runSeed();
        assertThat(jdbc.queryForObject("SELECT parts #>> '{parts,0,name}' FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM))
                .isEqualTo("Edited");
        assertThat(hash()).isEqualTo("edited-by-hand");
    }

    /**
     * R1-9: on a database whose v2 structure was edited by hand, the upgrade of the structure is skipped, so the refs of
     * the new science blocks must not appear for blocks the structure does not hold; and once the structure holds them
     * and the projector wrote its own ref (a fresh id) for one, the seed adds the others and never clashes on the block.
     */
    @Test
    void rerunOnAnEditedDatabaseOnlyAddsRefsOfBlocksItStillHolds() throws Exception {
        String upgraded = parts();
        long blockRefs = refs();

        // Edited v2 database without the new blocks: no ref for them, nothing else changes.
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = 'edited-by-hand', "
                + "parts = jsonb_set(parts, '{parts,2,options,0,sections}', '[]'::jsonb) WHERE exam_id = ?", EXAM);
        deleteNewRefs();
        String edited = parts();
        long before = refs();
        runSeed();
        assertThat(newRefs()).isZero();
        assertThat(refs()).isEqualTo(before);
        assertThat(mapper.readTree(parts())).isEqualTo(mapper.readTree(edited));
        assertThat(hash()).isEqualTo("edited-by-hand");

        // The edited database saved a structure that holds the new blocks; the projector wrote a ref with its own id.
        jdbc.update("UPDATE exam.exam_structure SET parts = ?::jsonb WHERE exam_id = ?", upgraded, EXAM);
        runSeed();
        assertThat(newRefs()).isEqualTo(8);
        UUID projected = UUID.randomUUID();
        assertThat(jdbc.update("UPDATE exam.exam_block_ref SET id = ? WHERE exam_id = ? AND block_id = ?", projected, EXAM, FIRST_NEW_BLOCK))
                .isEqualTo(1);
        runSeed();
        assertThat(newRefs()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT id FROM exam.exam_block_ref WHERE exam_id = ? AND block_id = ?", UUID.class, EXAM, FIRST_NEW_BLOCK))
                .isEqualTo(projected);
        assertThat(refs()).isEqualTo(blockRefs);
        assertThat(hash()).isEqualTo("edited-by-hand");
    }

    /**
     * R1-9: a normal save through the structure API rewrites EVERY draft ref with a fresh id, the seeded originals
     * included. When the seed file runs again (its checksum changed with a release) it must neither clash on the natural
     * key of a ref the projector already wrote nor bring back the ref of a block the edit removed.
     */
    @Test
    void rerunAfterANormalSaveThatRewroteEveryDraftRefKeepsTheEditedRefs() throws Exception {
        String url = "/api/v1/admin/exams/" + EXAM + "/structure";
        JsonNode current = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode edited = current.deepCopy();
        assertThat(removeBlock(edited.path("parts"), REMOVED_BLOCK)).as("the block is in the seeded structure").isTrue();
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", current.path("revision").asLong());
        body.set("parts", edited.path("parts"));
        if (edited.has("examQuotas")) body.set("examQuotas", edited.path("examQuotas"));
        mockMvc.perform(put(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isOk());

        List<UUID> rewritten = jdbc.queryForList("SELECT id FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL",
                UUID.class, EXAM);
        assertThat(rewritten).as("draft refs after the save").isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ? AND id::text LIKE '00000000-0000-7000-8900-%'",
                Long.class, EXAM)).as("no seeded ref id survives the save").isZero();
        assertThat(blockRefCount(REMOVED_BLOCK)).as("the edit removed that block's ref").isZero();
        String savedHash = hash();
        String savedParts = parts();

        runSeed();

        assertThat(jdbc.queryForList("SELECT id FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL", UUID.class, EXAM))
                .as("the projector's refs survive, none is added").containsExactlyInAnyOrderElementsOf(rewritten);
        assertThat(blockRefCount(REMOVED_BLOCK)).as("the ref of the removed block is not brought back").isZero();
        assertThat(hash()).isEqualTo(savedHash);
        assertThat(parts()).isEqualTo(savedParts);
    }

    /**
     * R3-3: a normal save may remove a seeded block and give its freed id to another node (here the section that held
     * it). Only a member of a blocks[] array is a block, so the rerun of the seed must not write the removed block's
     * ref again because the id still occurs elsewhere in the structure.
     */
    @Test
    void rerunDoesNotBringBackTheRefOfARemovedBlockWhoseIdNowNamesAnotherNode() throws Exception {
        String url = "/api/v1/admin/exams/" + EXAM + "/structure";
        JsonNode current = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode edited = current.deepCopy();
        assertThat(removeBlock(edited.path("parts"), REMOVED_BLOCK)).as("the block is in the seeded structure").isTrue();
        assertThat(renameNode(edited.path("parts"), HOLDER_SECTION, REMOVED_BLOCK)).as("the section that held it").isTrue();
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", current.path("revision").asLong());
        body.set("parts", edited.path("parts"));
        if (edited.has("examQuotas")) body.set("examQuotas", edited.path("examQuotas"));
        mockMvc.perform(put(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isOk());

        List<UUID> rewritten = jdbc.queryForList("SELECT id FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL",
                UUID.class, EXAM);
        assertThat(blockRefCount(REMOVED_BLOCK)).as("the edit removed that block's ref").isZero();
        String savedParts = parts();
        assertThat(savedParts).as("the freed id now names the section").contains("\"" + REMOVED_BLOCK + "\"");

        runSeed();

        assertThat(blockRefCount(REMOVED_BLOCK)).as("the ref of the removed block is not brought back").isZero();
        assertThat(jdbc.queryForList("SELECT id FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL", UUID.class, EXAM))
                .as("the projector's refs survive, none is added").containsExactlyInAnyOrderElementsOf(rewritten);
        assertThat(parts()).isEqualTo(savedParts);
    }

    /** Gives the first object whose id is {@code from} the id {@code to}. */
    private static boolean renameNode(JsonNode node, String from, String to) {
        if (node.isObject() && from.equals(node.path("id").asText())) {
            ((ObjectNode) node).put("id", to);
            return true;
        }
        for (JsonNode child : node) if (renameNode(child, from, to)) return true;
        return false;
    }

    private static boolean removeBlock(JsonNode node, String blockId) {
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i).isObject() && blockId.equals(array.get(i).path("id").asText())) {
                    array.remove(i);
                    return true;
                }
            }
        }
        for (JsonNode child : node) if (removeBlock(child, blockId)) return true;
        return false;
    }

    private long blockRefCount(String blockId) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ? AND block_id = ?", Long.class, EXAM, blockId);
    }

    private JsonNode preview(String[] picks, int seed) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + EXAM + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"" + SCIENCE + "\",\"subjectCodes\":[\""
                                + String.join("\",\"", picks) + "\"]},\"seed\":" + seed + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String parts() {
        return jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM);
    }

    private String hash() {
        return jdbc.queryForObject("SELECT structure_hash FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM);
    }

    private long refs() {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ?", Long.class, EXAM);
    }

    private long newRefs() {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ? AND block_id >= ? AND block_id <= ?",
                Long.class, EXAM, FIRST_NEW_BLOCK, LAST_NEW_BLOCK);
    }

    private void deleteNewRefs() {
        jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ? AND block_id >= ? AND block_id <= ?",
                EXAM, FIRST_NEW_BLOCK, LAST_NEW_BLOCK);
    }

    private void runSeed() throws Exception {
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.support.EncodedResource(
                    new ClassPathResource("db/devdata/R__seed_exam_dev_data.sql"), "UTF-8"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
