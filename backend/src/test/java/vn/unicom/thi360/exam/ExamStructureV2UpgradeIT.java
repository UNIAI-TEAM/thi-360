package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ExamStructureV2UpgradeIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LEGACY = """
            [{"id":"part","options":[
              {"kind":"COMBINATION","code":"FIRST","sections":[{"id":"section","subjectCode":"LY",
                "knowledgeScope":{"match":"ALL"},"blocks":[{"id":"kept","questionType":"SINGLE_CHOICE","questionCount":5}]}]},
              {"kind":"COMBINATION","code":"SECOND","sections":[{"id":"duplicate","subjectCode":"LY",
                "blocks":[{"id":"discarded","questionType":"SINGLE_CHOICE","questionCount":5}]}]}
            ]}]
            """;
    private static final String INVALID = """
            {"parts":[{"options":[{"kind":"SUBJECT_CHOICE","sections":[{"matrix":
              {"unit":"PERCENT","byGrade":[{"value":100000000000000000000}]} }]}]}]}
            """;

    @Test
    void upgradesPopulatedDatabaseAtomicallyAndReconcilesReferences() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("thi360").withUsername("thi360").withPassword("thi360-test")) {
            postgres.start();
            DriverManagerDataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            flyway(postgres, "202611171223").migrate();

            UUID qda = insertExam(jdbc, "QDA", LEGACY);
            UUID bca = insertExam(jdbc, "BCA", LEGACY);
            UUID hsa = insertExam(jdbc, "HSA", LEGACY);
            UUID combination = UUID.randomUUID();
            insertRef(jdbc, qda, null, "kept", "section", combination);
            insertRef(jdbc, qda, null, "discarded", "duplicate", combination);
            insertRef(jdbc, qda, 1, "discarded", "duplicate", combination);

            // This template fails after working structures were updated in the Java migration.
            jdbc.update("UPDATE exam.exam_template SET structure = CAST(? AS jsonb) WHERE code = 'BCA_TEST'", INVALID);
            assertThatThrownBy(() -> flyway(postgres, null).migrate()).isInstanceOf(RuntimeException.class);
            assertThat(document(jdbc, "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", qda))
                    .isEqualTo(JSON.readTree(LEGACY));
            assertThat(jdbc.queryForObject("SELECT state FROM exam.exam_version WHERE exam_id = ?", String.class, qda))
                    .isEqualTo("SERVING");
            jdbc.update("UPDATE exam.exam_template SET structure = CAST(? AS jsonb) WHERE code = 'BCA_TEST'", LEGACY);

            Flyway upgrade = flyway(postgres, null);
            upgrade.migrate();
            for (UUID exam : List.of(qda, bca, hsa)) {
                JsonNode working = document(jdbc, "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", exam);
                assertConverted(working);
                assertThat(document(jdbc, "SELECT parts::text FROM exam.exam_version WHERE exam_id = ?", exam)).isEqualTo(working);
                assertThat(jdbc.queryForObject("SELECT structure_hash FROM exam.exam_structure WHERE exam_id = ?", String.class, exam)).isNull();
            }
            for (String kind : List.of("QDA", "BCA", "HSA")) {
                assertConverted(document(jdbc, "SELECT structure::text FROM exam.exam_template WHERE code = ?", kind + "_TEST"));
            }
            assertThat(jdbc.queryForObject("SELECT state FROM exam.exam_version WHERE exam_id = ?", String.class, qda)).isEqualTo("RETIRED");
            assertThat(jdbc.queryForObject("SELECT state FROM exam.exam_version WHERE exam_id = ?", String.class, bca)).isEqualTo("RETIRED");
            assertThat(jdbc.queryForObject("SELECT state FROM exam.exam_version WHERE exam_id = ?", String.class, hsa)).isEqualTo("SERVING");
            List<Map<String, Object>> refs = jdbc.queryForList("SELECT block_id, version_no, option_code, combination_item_id FROM exam.exam_block_ref WHERE exam_id = ? ORDER BY version_no NULLS FIRST", qda);
            assertThat(refs).hasSize(2);
            assertThat(refs.get(0)).containsEntry("block_id", "kept").containsEntry("option_code", null).containsEntry("combination_item_id", null);
            assertThat(refs.get(1)).containsEntry("block_id", "discarded").containsEntry("version_no", 1).containsEntry("combination_item_id", combination);
            JsonNode beforeRerun = document(jdbc, "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", qda);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            assertThat(document(jdbc, "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", qda)).isEqualTo(beforeRerun);
        }
    }

    private static Flyway flyway(PostgreSQLContainer<?> postgres, String target) {
        var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam")
                .defaultSchema("shared").locations("classpath:db/migration")
                .placeholders(Map.of("auditAppRole", postgres.getUsername()));
        if (target != null) config.target(target);
        return config.load();
    }

    private static UUID insertExam(JdbcTemplate jdbc, String kind, String parts) {
        UUID id = UUID.randomUUID();
        String template = kind + "_TEST";
        jdbc.update("INSERT INTO exam.exam_template (code,kind_code,scope,timing,structure) VALUES (?,?,'FULL','SHARED',CAST(? AS jsonb))", template, kind, parts);
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,template_code) VALUES (?,?,?,?,'FULL','SHARED',?)", id, template, template.toLowerCase(), template, template);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,CAST(? AS jsonb),'old-hash')", id, parts);
        jdbc.update("INSERT INTO exam.exam_version (id,exam_id,version_no,config,parts,published_path_ids,serving_path_ids,check_run_id,state,status,published_at,published_by) VALUES (?,?,1,'{}'::jsonb,CAST(? AS jsonb),ARRAY[]::text[],ARRAY[]::text[],?,'SERVING','PUBLISHED',now(),?)", UUID.randomUUID(), id, parts, UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    private static void insertRef(JdbcTemplate jdbc, UUID exam, Integer version, String block, String section, UUID combination) {
        jdbc.update("INSERT INTO exam.exam_block_ref (id,exam_id,version_no,block_id,part_id,section_id,option_code,combination_item_id) VALUES (?,?,?,?,'part',?,'FIRST',?)", UUID.randomUUID(), exam, version, block, section, combination);
    }

    private static JsonNode document(JdbcTemplate jdbc, String query, Object id) throws Exception {
        return JSON.readTree(jdbc.queryForObject(query, String.class, id));
    }

    private static void assertConverted(JsonNode parts) {
        JsonNode part = parts.get(0);
        assertThat(part.has("options")).isFalse();
        assertThat(part.path("codeSource").path("source").asText()).isEqualTo("GLOBAL_LIST");
        assertThat(part.path("subjectPool")).hasSize(1);
        assertThat(part.path("subjectPool").get(0).path("blocks").get(0).path("id").asText()).isEqualTo("kept");
        assertThat(part.path("subjectPool").get(0).path("knowledgeScope").has("match")).isFalse();
    }
}
