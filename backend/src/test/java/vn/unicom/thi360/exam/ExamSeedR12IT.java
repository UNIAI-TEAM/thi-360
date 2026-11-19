package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * CR-4 / AD-75: the seeded HSA dev exam keeps passing the R-12 rules with sources, generates a paper with its
 * science sets and short answers, and an older database gets the new structure when the seed file runs again.
 */
@Transactional
class ExamSeedR12IT extends AbstractIntegrationTest {
    private static final UUID EXAM = UUID.fromString("00000000-0000-7000-8700-000000000001");
    private static final String SCIENCE = "00000000-0000-7000-8800-000000000070";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private DataSource dataSource;

    @Test
    void seededHsaDevExamGeneratesSciencePapersWithSetAndShortAnswer() throws Exception {
        for (String[] picks : new String[][] {{"LY", "HOA", "SINH"}, {"SU", "DIA", "LY"}}) {
            JsonNode preview = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + EXAM + "/preview")
                            .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"" + SCIENCE + "\",\"subjectCodes\":[\""
                                    + String.join("\",\"", picks) + "\"]},\"seed\":7}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(preview.path("status").asText()).as(preview.toString()).isEqualTo("GENERATED");
            String paper = preview.path("paper").toString();
            assertThat(paper).contains("QUESTION_SET");
            if (!List.of(picks).equals(List.of("SU", "DIA", "LY"))) assertThat(paper).contains("SHORT_ANSWER");
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

    /** The seed file is a repeatable migration: running it on a database that holds the v2 seed upgrades that row only. */
    @Test
    void rerunUpgradesTheOldSeedAndKeepsAnEditedStructure() throws Exception {
        String upgraded = jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM);
        long blockRefs = jdbc.queryForObject("SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ?", Long.class, EXAM);
        assertThat(jdbc.queryForObject("SELECT structure_hash FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM))
                .isEqualTo("dev-hsa-structure-v3");

        // An older database: the v2 hash and a structure without the science sets and short answers.
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = 'dev-hsa-structure-v2', "
                + "parts = jsonb_set(parts, '{parts,2,options,0,sections}', '[]'::jsonb) WHERE exam_id = ?", EXAM);
        jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ? AND block_id::text >= '00000000-0000-7000-8800-000000000080' "
                + "AND block_id::text <= '00000000-0000-7000-8800-000000000087'", EXAM);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ?", Long.class, EXAM)).isLessThan(blockRefs);
        runSeed();
        assertThat(jdbc.queryForObject("SELECT structure_hash FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM))
                .isEqualTo("dev-hsa-structure-v3");
        assertThat(mapper.readTree(jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM)))
                .isEqualTo(mapper.readTree(upgraded));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ?", Long.class, EXAM)).isEqualTo(blockRefs);

        // Running it again changes nothing.
        runSeed();
        assertThat(mapper.readTree(jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM)))
                .isEqualTo(mapper.readTree(upgraded));

        // A structure that someone edited (other hash) is not overwritten.
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = 'edited-by-hand', parts = jsonb_set(parts, '{parts,0,name}', '\"Edited\"') WHERE exam_id = ?", EXAM);
        runSeed();
        assertThat(jdbc.queryForObject("SELECT parts #>> '{parts,0,name}' FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM))
                .isEqualTo("Edited");
        assertThat(jdbc.queryForObject("SELECT structure_hash FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM))
                .isEqualTo("edited-by-hand");
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
