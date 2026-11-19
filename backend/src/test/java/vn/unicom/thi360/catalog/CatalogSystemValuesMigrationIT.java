package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.shared.security.TestSecurity;

/**
 * UNI-2759 (AD-34, AD-35): V202611191730 on a database that holds the cases the matching rule names.
 * Flyway migrates up to the version before it, the prepared rows go in, then the migration and the
 * devdata seeds run as they do on a real upgrade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"api", "worker", "test"})
@Import({CatalogSystemValuesMigrationIT.PreparedRows.class, TestSecurity.class})
class CatalogSystemValuesMigrationIT {

    private static final String MIGRATION_SCRIPT = "db/migration/catalog/V202611191730__catalog_system_values.sql";
    private static final String CATALOG_SEED = "db/devdata/R__seed_catalog_dev_data.sql";

    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    private static final String EXAM = "00000000-0000-7000-8100-000000000001";
    private static final String SUBJECT = "00000000-0000-7000-8100-000000000002";
    private static final String GRADE = "00000000-0000-7000-8100-000000000003";
    private static final String LEVEL = "00000000-0000-7000-8100-000000000004";
    private static final String FORMAT = "00000000-0000-7000-8100-000000000005";
    private static final String TASK = "00000000-0000-7000-8100-000000000006";
    private static final String KNOWLEDGE = "00000000-0000-7000-8100-000000000009";

    // (a) same code under another id (admin deleted and recreated it) and a name an admin chose.
    private static final String SU_OTHER_ID = "11111111-1111-4111-8111-000000000001";
    // (a) the common dev case: the seeded row itself, renamed by an admin.
    private static final String NB_ID = "00000000-0000-7000-8200-000000000401";
    // (b) canonical id under a renamed code: unused, used by a tree link, used by a question.
    private static final String G10_ID = "00000000-0000-7000-8200-000000000301";
    private static final String G11_ID = "00000000-0000-7000-8200-000000000302";
    private static final String G12_ID = "00000000-0000-7000-8200-000000000303";
    // (c) an admin value that holds GRADE_MIXED's listed value 4.
    private static final String CUSTOM_GRADE_ID = "11111111-1111-4111-8111-000000000002";
    private static final String KNOWLEDGE_ROOT_ID = "11111111-1111-4111-8111-000000000003";
    private static final String QUESTION_ID = "11111111-1111-4111-8111-000000000004";

    // The 52 listed values by group; G11 and G12 stay under the codes admins gave them (in use).
    private static final Map<String, List<String>> LISTED_CODES = Map.of(
            EXAM, List.of("HSA", "QDA", "BCA", "VACT", "SPT", "SP2E", "THPTQG", "HSCA", "VSAT"),
            SUBJECT, List.of("TOAN", "VAN", "ANH", "LY", "HOA", "SINH", "SU", "DIA", "TIN", "GDKTPL", "CNCN", "CNNN",
                    "VIETNAMESE", "ECON_LAW", "INFORMATICS", "TECH_INDUSTRY", "TECH_AGRICULTURE", "SCIENTIFIC_THINKING"),
            GRADE, List.of("G10", "GRADE_MIXED"),
            LEVEL, List.of("NB", "TH", "VD", "VDC"),
            FORMAT, List.of("SINGLE_CHOICE", "SHORT_ANSWER", "MULTIPLE_CHOICE", "CLOZE", "QUESTION_SET", "TRUE_FALSE",
                    "MATCHING", "ESSAY"),
            TASK, List.of("SINGLE", "CLUSTER", "SYNONYM", "ANTONYM", "DIALOGUE_COMPLETION", "READING_COMPREHENSION",
                    "WORD_USAGE_ERROR", "PASSAGE_READING", "SENTENCE_COMPLETION"));

    static {
        DB.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class PreparedRows {
        @Bean
        FlywayMigrationStrategy migrateInTwoSteps() {
            return flyway -> {
                DataSource dataSource = flyway.getConfiguration().getDataSource();
                CatalogUpgradeSupport.migrateUpToBeforeSystemValues(dataSource, flyway.getConfiguration().getPlaceholders());
                JdbcTemplate db = new JdbcTemplate(dataSource);
                db.execute(
                        "INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi,"
                                + " display_order, status, is_system, depth, path) VALUES\n"
                                + row(SU_OTHER_ID, SUBJECT, "SU", 7, "Sử do admin đặt tên", 6) + ",\n"
                                + row(NB_ID, LEVEL, "NB", 1, "Nhận biết (admin đã sửa)", 0) + ",\n"
                                + row(G10_ID, GRADE, "LOP10", 1, "Lớp mười", 0) + ",\n"
                                + row(G11_ID, GRADE, "LOP11", 2, "Lớp mười một", 1) + ",\n"
                                + row(G12_ID, GRADE, "LOP12", 3, "Lớp mười hai", 2) + ",\n"
                                + row(CUSTOM_GRADE_ID, GRADE, "CUSTOM_GRADE", 4, "Khối admin tự tạo", 3) + ",\n"
                                + row(KNOWLEDGE_ROOT_ID, KNOWLEDGE, "KN_MIG", 900, "Gốc kiến thức", 0));
                db.update("INSERT INTO catalog.catalog_item_ref (item_id, ref_item_id) VALUES (?::uuid, ?::uuid)",
                        KNOWLEDGE_ROOT_ID, G11_ID);
                db.update("INSERT INTO questionbank.question (id, code, type, grade_item_id)"
                        + " VALUES (?::uuid, 'MIG-USED-G12', 'SINGLE_CHOICE', ?::uuid)", QUESTION_ID, G12_ID);
                flyway.migrate();
            };
        }

        private static String row(String id, String group, String code, int value, String nameVi, int order) {
            return "('" + id + "', '" + group + "', '" + code + "', '" + code.toLowerCase() + "', " + value + ", '"
                    + nameVi.replace("'", "''") + "', " + order + ", 1, false, 1, '/" + id + "/')";
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void everyListedValueIsSystemWithItsCanonicalCode() {
        int checked = 0;
        for (Map.Entry<String, List<String>> group : LISTED_CODES.entrySet()) {
            for (String code : group.getValue()) {
                Boolean system = jdbc.queryForObject(
                        "SELECT is_system FROM catalog.catalog_item WHERE group_id = ?::uuid AND code_normalized = ?",
                        Boolean.class, group.getKey(), code.toLowerCase());
                assertThat(system).as(code).isTrue();
                checked++;
            }
        }
        // G11 and G12 are system under their current codes: 50 by code + 2 by id = the 52 listed values.
        assertThat(checked).isEqualTo(50);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item WHERE id IN (?::uuid, ?::uuid) AND is_system",
                Integer.class, G11_ID, G12_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item WHERE is_system", Integer.class))
                .isEqualTo(52);
        Map<String, Integer> perGroup = Map.of(EXAM, 9, SUBJECT, 18, GRADE, 4, LEVEL, 4, FORMAT, 8, TASK, 9);
        perGroup.forEach((group, expected) -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item WHERE group_id = ?::uuid AND is_system",
                Integer.class, group)).as(group).isEqualTo(expected));
    }

    @Test
    void sameCodeKeepsIdNameAndValueAndOnlyGainsTheFlag() {
        Map<String, Object> su = jdbc.queryForMap(
                "SELECT id::text AS id, name_vi, value, is_system FROM catalog.catalog_item"
                        + " WHERE group_id = ?::uuid AND code_normalized = 'su'", SUBJECT);
        assertThat(su).containsEntry("id", SU_OTHER_ID).containsEntry("name_vi", "Sử do admin đặt tên")
                .containsEntry("value", 7).containsEntry("is_system", true);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item WHERE id = '00000000-0000-7000-8200-000000000207'::uuid",
                Integer.class)).isZero();

        Map<String, Object> nb = jdbc.queryForMap(
                "SELECT code, name_vi, is_system FROM catalog.catalog_item WHERE id = ?::uuid", NB_ID);
        assertThat(nb).containsEntry("code", "NB").containsEntry("name_vi", "Nhận biết (admin đã sửa)")
                .containsEntry("is_system", true);
    }

    @Test
    void renamedCodeWithoutUsageIsRestoredAndKeepsItsName() {
        Map<String, Object> g10 = jdbc.queryForMap(
                "SELECT code, code_normalized, name_vi, value, is_system FROM catalog.catalog_item WHERE id = ?::uuid",
                G10_ID);
        assertThat(g10).containsEntry("code", "G10").containsEntry("code_normalized", "g10")
                .containsEntry("name_vi", "Lớp mười").containsEntry("value", 1).containsEntry("is_system", true);
    }

    @Test
    void renamedCodeInUseIsKeptAndMarkedSystem() {
        for (String id : List.of(G11_ID, G12_ID)) {
            Map<String, Object> grade = jdbc.queryForMap(
                    "SELECT code, is_system FROM catalog.catalog_item WHERE id = ?::uuid", id);
            assertThat(grade).as(id).containsEntry("is_system", true);
        }
        assertThat(jdbc.queryForObject("SELECT code FROM catalog.catalog_item WHERE id = ?::uuid", String.class, G11_ID))
                .as("used by a tree link").isEqualTo("LOP11");
        assertThat(jdbc.queryForObject("SELECT code FROM catalog.catalog_item WHERE id = ?::uuid", String.class, G12_ID))
                .as("used by a question").isEqualTo("LOP12");
    }

    @Test
    void listedValueThatAnAdminValueHoldsMovesToMaxPlusOne() {
        assertThat(jdbc.queryForObject(
                "SELECT value FROM catalog.catalog_item WHERE id = ?::uuid", Integer.class, CUSTOM_GRADE_ID)).isEqualTo(4);
        Map<String, Object> mixed = jdbc.queryForMap(
                "SELECT id::text AS id, value, is_system, name_vi FROM catalog.catalog_item"
                        + " WHERE group_id = ?::uuid AND code_normalized = 'grade_mixed'", GRADE);
        assertThat(mixed).containsEntry("id", "00000000-0000-7000-8400-000000000304")
                .containsEntry("value", 5).containsEntry("is_system", true).containsEntry("name_vi", "Liên lớp");
        assertThat(jdbc.queryForObject(
                "SELECT is_system FROM catalog.catalog_item WHERE id = ?::uuid", Boolean.class, CUSTOM_GRADE_ID)).isFalse();
    }

    @Test
    void insertedValuesKeepTheirListedIdsAndValuesAndTheGroupCountersCoverThem() {
        assertThat(jdbc.queryForObject(
                "SELECT value FROM catalog.catalog_item WHERE id = '00000000-0000-7000-8400-000000000109'::uuid",
                Integer.class)).as("EXAM VSAT").isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT value FROM catalog.catalog_item WHERE id = '5b100b3c-4336-51b7-8277-3cc4223367ae'::uuid",
                Integer.class)).as("TASK_TYPE SENTENCE_COMPLETION").isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item i JOIN catalog.catalog_group g ON g.id = i.group_id"
                        + " WHERE i.is_system AND g.last_value < i.value", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT last_value FROM catalog.catalog_group WHERE id = ?::uuid", Integer.class, GRADE)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void devdataSeedRerunKeepsTheFlagsCodesAndNamesAndAddsNothing() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item", Integer.class);
        String stateBefore = state();

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource(CATALOG_SEED), StandardCharsets.UTF_8));
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item WHERE is_system", Integer.class))
                .isEqualTo(52);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item", Integer.class)).isEqualTo(before);
        assertThat(state()).isEqualTo(stateBefore);
    }

    @Test
    void runningTheMigrationAgainChangesNothing() throws Exception {
        String stateBefore = state();
        int before = jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item", Integer.class);

        jdbc.execute(new String(new ClassPathResource(MIGRATION_SCRIPT).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item", Integer.class)).isEqualTo(before);
        assertThat(state()).isEqualTo(stateBefore);
    }

    /** id, code, name, value and flag of every system value, in a stable order. */
    private String state() {
        return String.join("\n", jdbc.query(
                "SELECT id || '|' || code || '|' || name_vi || '|' || coalesce(name_en, '') || '|' || value || '|' || is_system"
                        + " FROM catalog.catalog_item WHERE is_system ORDER BY group_id, id",
                (rs, n) -> rs.getString(1)));
    }
}
