package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.exam.application.ExamBlockRefProjector;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.shared.security.TestSecurity;

/**
 * Proves the whole Flyway chain (versioned migrations incl. the Java structure-v2
 * migration, then the repeatable devdata seeds) produces no old-form structure on a
 * fresh database. Subclasses bind this to the `test` and `dev` profiles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurity.class)
abstract class AbstractExamStructureV2DevdataIT {
    private static final List<String> REMOVED_KEYS =
            List.of("match", "includeDescendants", "taskTypeCode", "groupOrder",
                    "shuffle", "blockShuffleRule");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("thi360").withUsername("thi360").withPassword("thi360-test");
    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerDatabase(DynamicPropertyRegistry registry) {
        String url = freshDatabaseUrl();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired JdbcTemplate jdbc;
    protected static String freshDatabaseUrl() {
        String name = "v2_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create isolated test database", e);
        }
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
    }

    @Autowired ObjectMapper mapper;
    @Autowired ExamBlockRefProjector projector;
    @Autowired DataSource dataSource;

    private record RefKey(String part, String section, String block, String option, UUID combination) {}

    @Test
    void seededSubjectPoolReferencesMatchProjector() {
        var rows = jdbc.queryForList("""
                SELECT s.exam_id, s.parts::text AS parts FROM exam.exam_structure s
                JOIN exam.exam e ON e.id = s.exam_id WHERE e.template_code IN ('QDA_FULL', 'BCA_FULL')
                """);
        assertThat(rows).isNotEmpty();
        for (var row : rows) {
            UUID examId = (UUID) row.get("exam_id");
            var document = new ExamStructureCodec(mapper).read((String) row.get("parts"));
            Set<String> poolPartIds = document.parts().stream().filter(p -> p.codeSource() != null)
                    .map(ExamPart::id).collect(Collectors.toSet());
            assertThat(poolPartIds).isNotEmpty();
            var resolver = new ExamBlockRefProjector.CodeResolver(code -> null, code -> null, code -> null);
            var expected = projector.project(examId, document.parts(), resolver).stream()
                    .filter(r -> poolPartIds.contains(r.getPartId()))
                    .map(r -> new RefKey(r.getPartId(), r.getSectionId(), r.getBlockId(),
                            r.getOptionCode(), r.getCombinationItemId())).toList();
            var actual = jdbc.query("""
                    SELECT part_id, section_id, block_id, option_code, combination_item_id
                    FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL
                    """, (rs, n) -> new RefKey(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getObject(5, UUID.class)), examId).stream()
                    .filter(r -> poolPartIds.contains(r.part())).toList();
            assertThat(actual).as("subjectPool refs for %s", examId).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    @Transactional
    void migrationRemovesObsoleteCombinationReferencesAndRetainsPoolReferences() {
        UUID examId = UUID.fromString("be11dde0-946e-5f37-846c-684f033a779c");
        int changed = jdbc.update("""
                UPDATE exam.exam_block_ref SET option_code = 'QD01',
                    combination_item_id = '00000000-0000-7000-8400-000000000709'
                WHERE exam_id = ? AND block_id = 'b-qd01-ly' AND version_no IS NULL
                """, examId);
        assertThat(changed).isEqualTo(1);
        jdbc.update("""
                INSERT INTO exam.exam_block_ref
                  (id, exam_id, version_no, block_id, part_id, option_code, section_id,
                   subject_item_id, question_type, answer_types, knowledge_item_ids, knowledge_mode,
                   knowledge_inherited, tag_item_ids, competency_item_ids, required_points, combination_item_id)
                SELECT gen_random_uuid(), exam_id, NULL, 'b-qd03-ly', part_id, 'QD03', 's-qd03-ly',
                   subject_item_id, question_type, answer_types, knowledge_item_ids, knowledge_mode,
                   knowledge_inherited, tag_item_ids, competency_item_ids, required_points, combination_item_id
                FROM exam.exam_block_ref WHERE exam_id = ? AND block_id = 'b-qd01-ly' AND version_no IS NULL
                """, examId);
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/migration/exam/V202611172354__reconcile_subject_pool_refs.sql"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ? AND block_id = 'b-qd03-ly'
                """, Integer.class, examId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ? AND block_id = 'b-qd01-ly'
                  AND option_code IS NULL AND combination_item_id IS NULL
                """, Integer.class, examId)).isEqualTo(1);
    }

    @Test
    void contextStartsAndStructuresAreV2() {
        // ApplicationContext up means every migration and every R__ seed ran clean.
        assertNoOldForm("exam.exam_structure",
                jdbc.queryForList("SELECT parts::text FROM exam.exam_structure", String.class), true);
        assertNoOldForm("exam.exam_version",
                jdbc.queryForList("SELECT parts::text FROM exam.exam_version", String.class), false);
        assertNoOldForm("exam.exam_template",
                jdbc.queryForList("SELECT structure::text FROM exam.exam_template", String.class), true);
        assertThat(jdbc.queryForList("""
                SELECT kind_code, scope, part_code, subject_code, build_mode
                FROM exam.exam_template
                GROUP BY kind_code, scope, part_code, subject_code, build_mode
                HAVING count(*) FILTER (WHERE is_default) <> 1
                """)).as("each template selection group has exactly one default").isEmpty();
    }

    private void assertNoOldForm(String table, List<String> documents, boolean requireRows) {
        if (requireRows) {
            assertThat(documents).as(table + " rows").isNotEmpty();
        }
        for (String document : documents) {
            JsonNode root = read(document);
            JsonNode parts = root.isObject() && root.has("parts") ? root.get("parts") : root;
            assertNoKey(parts, table);
            parts.findValues("kind").forEach(k ->
                    assertThat(k.asText()).as(table + " option kind").isNotEqualTo("COMBINATION"));
            for (JsonNode templateCode : parts.findValues("templateGroupCode")) {
                assertThat(templateCode.asText()).as(table + " templateGroupCode")
                        .doesNotEndWith("_REMAINDER");
            }
            parts.findValues("name").forEach(n ->
                    assertThat(n.asText()).as(table + " block name").doesNotContain("phần còn lại"));
        }
    }

    private void assertNoKey(JsonNode node, String table) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            for (String key : REMOVED_KEYS) {
                if (node.has(key)) {
                    fail(table + " still has '" + key + "' inside " + node);
                }
            }
            node.forEach(child -> assertNoKey(child, table));
        } else if (node.isArray()) {
            node.forEach(child -> assertNoKey(child, table));
        }
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Unparsable structure: " + json.substring(0, 100), e);
        }
    }
}
