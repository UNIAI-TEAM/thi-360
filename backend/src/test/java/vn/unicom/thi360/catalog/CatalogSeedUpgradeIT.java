package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.shared.security.TestSecurity;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CombinationBranch;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"api", "worker", "test"})
@Import({CatalogSeedUpgradeIT.UpgradeConfiguration.class, TestSecurity.class})
class CatalogSeedUpgradeIT {
    private static final PostgreSQLContainer<?> UPGRADE_DB = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    private static final UUID TAG_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000008");
    private static final UUID QDA = UUID.fromString("00000000-0000-7000-8200-000000000102");

    static {
        UPGRADE_DB.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void upgradeDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", UPGRADE_DB::getJdbcUrl);
        registry.add("spring.datasource.username", UPGRADE_DB::getUsername);
        registry.add("spring.datasource.password", UPGRADE_DB::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class UpgradeConfiguration {
        @Bean
        FlywayMigrationStrategy upgradeFromBase() {
            return flyway -> {
                DataSource dataSource = flyway.getConfiguration().getDataSource();
                Flyway.configure().dataSource(dataSource).defaultSchema("shared")
                        .schemas("shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam")
                        .placeholders(flyway.getConfiguration().getPlaceholders())
                        .locations("classpath:db/migration").load().migrate();
                JdbcTemplate db = new JdbcTemplate(dataSource);
                db.execute("""
                    INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, display_order, status, is_system, depth, path) VALUES
                    ('00000000-0000-7000-8200-000000000102', '00000000-0000-7000-8100-000000000001', 'QDA', 'qda', 2, 'QDA', 1, 1, false, 1, '/00000000-0000-7000-8200-000000000102/'),
                    ('00000000-0000-7000-8200-000000000203', '00000000-0000-7000-8100-000000000002', 'ANH', 'anh', 3, 'Anh', 2, 1, false, 1, '/00000000-0000-7000-8200-000000000203/'),
                    ('ffbd8a8b-e696-54c4-b87f-fd2e3033f0fe', '00000000-0000-7000-8100-000000000007', 'QD05', 'qd05', 9, 'QD05', 17, 1, false, 1, '/ffbd8a8b-e696-54c4-b87f-fd2e3033f0fe/'),
                    ('6710590e-fa21-5bc6-9c00-3db3e7b4675f', '00000000-0000-7000-8100-000000000008', 'TEST-HIEM', 'test-hiem', 1, 'Test', 0, 1, false, 1, '/6710590e-fa21-5bc6-9c00-3db3e7b4675f/'),
                    ('11111111-1111-4111-8111-111111111111', '00000000-0000-7000-8100-000000000008', 'USER-TAG', 'user-tag', 2, 'User', 1, 1, false, 1, '/11111111-1111-4111-8111-111111111111/'),
                    ('11111111-1111-4111-8111-111111111112', '00000000-0000-7000-8100-000000000008', 'NONE', 'none', 3, 'None', 2, 1, false, 1, '/11111111-1111-4111-8111-111111111112/'),
                    ('11111111-1111-4111-8111-111111111113', '00000000-0000-7000-8100-000000000008', 'VACT-TOAN', 'vact-toan', 4, 'VACT Toan', 3, 1, false, 1, '/11111111-1111-4111-8111-111111111113/'),
                    ('11111111-1111-4111-8111-111111111114', '00000000-0000-7000-8100-00000000000a', 'VACT', 'vact', 6, 'VACT', 5, 1, false, 1, '/11111111-1111-4111-8111-111111111114/'),
                    ('11111111-1111-4111-8111-111111111141', '00000000-0000-7000-8100-000000000008', 'ANH_PHAT_HIEN_LOI', 'anh_phat_hien_loi', 10, 'Loi sai', 9, 1, false, 1, '/11111111-1111-4111-8111-111111111141/'),
                    ('11111111-1111-4111-8111-111111111142', '00000000-0000-7000-8100-000000000008', 'ANH_DIEN_DAT_TUONG_DUONG', 'anh_dien_dat_tuong_duong', 11, 'Tuong duong', 10, 1, false, 1, '/11111111-1111-4111-8111-111111111142/'),
                    ('11111111-1111-4111-8111-111111111143', '00000000-0000-7000-8100-000000000008', 'TD_SAP_XEP_DIEU_KIEN', 'td_sap_xep_dieu_kien', 12, 'Sap xep', 11, 1, false, 1, '/11111111-1111-4111-8111-111111111143/'),
                    ('11111111-1111-4111-8111-111111111144', '00000000-0000-7000-8100-000000000008', 'TD_XEP_HANG', 'td_xep_hang', 13, 'Xep hang', 12, 1, false, 1, '/11111111-1111-4111-8111-111111111144/'),
                    ('11111111-1111-4111-8111-111111111145', '00000000-0000-7000-8100-000000000008', 'TD_BANG_SO_LIEU', 'td_bang_so_lieu', 14, 'Bang so lieu', 13, 1, false, 1, '/11111111-1111-4111-8111-111111111145/'),
                    ('11111111-1111-4111-8111-111111111146', '00000000-0000-7000-8100-000000000008', 'TD_BIEU_DO', 'td_bieu_do', 15, 'Bieu do', 14, 1, false, 1, '/11111111-1111-4111-8111-111111111146/'),
                    ('00000000-0000-7000-8601-000000000041', '00000000-0000-7000-8100-000000000008', 'LEGACY-ENGLISH', 'legacy-english', 16, 'Legacy English', 15, 1, false, 1, '/00000000-0000-7000-8601-000000000041/'),
                    ('00000000-0000-7000-8601-000000000043', '00000000-0000-7000-8100-000000000008', 'LEGACY-LOGIC', 'legacy-logic', 17, 'Legacy Logic', 16, 1, false, 1, '/00000000-0000-7000-8601-000000000043/')
                    """);
                db.execute("""
                    INSERT INTO catalog.combination_version (id, item_id, version_no, exam_item_id, source_note, is_current)
                    VALUES ('4df2381e-c78c-5577-805b-818bf8a76373', 'ffbd8a8b-e696-54c4-b87f-fd2e3033f0fe', 1, '00000000-0000-7000-8200-000000000102', 'base', true)
                    """);
                db.execute("""
                    INSERT INTO catalog.combination_subject (combination_version_id, subject_item_id, position)
                    VALUES ('4df2381e-c78c-5577-805b-818bf8a76373', '00000000-0000-7000-8200-000000000203', 0)
                    """);
                flyway.migrate();
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired CatalogLookup lookup;
    @Autowired QuestionSourcePort source;
    @Autowired ObjectMapper mapper;

    @Autowired MockMvc mockMvc;
    @Autowired TestSecurity.TestSecuritySupport testSecuritySupport;
    @Test
    void upgradesOldIdsAndOccupiedTagsAndAllocatesNextApiValue() throws Exception {
        List<CatalogCombinationView> combinations = lookup.listCombinations(QDA);
        assertThat(combinations).extracting(CatalogCombinationView::itemCode)
                .containsExactly("QD01", "QD02", "QD03", "QD04", "QD05");
        var qd05 = combinations.get(4);
        assertThat(qd05.branch()).isEqualTo(CombinationBranch.ENGLISH);
        assertThat(qd05.subjects()).extracting(subject -> subject.code()).containsExactly("ANH");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.combination_version WHERE id = '4df2381e-c78c-5577-805b-818bf8a76373' AND item_id = 'ffbd8a8b-e696-54c4-b87f-fd2e3033f0fe'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT value FROM catalog.catalog_item WHERE group_id = ? AND code IN ('TEST-HIEM', 'USER-TAG') ORDER BY value", Integer.class, TAG_GROUP)).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item WHERE group_id = ? AND id::text LIKE '00000000-0000-7000-8601-%'", Integer.class, TAG_GROUP)).isEqualTo(42);
        int highWater = jdbc.queryForObject("SELECT max(value) FROM catalog.catalog_item WHERE group_id = ?", Integer.class, TAG_GROUP);
        assertThat(jdbc.queryForObject("SELECT last_value FROM catalog.catalog_group WHERE id = ?", Integer.class, TAG_GROUP)).isGreaterThanOrEqualTo(highWater);
        var result = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", TAG_GROUP)
                        .with(TestSecurity.asUser(testSecuritySupport, Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AFTER-UPGRADE\",\"nameVi\":\"After upgrade\"}"))
                .andExpect(status().isCreated()).andReturn();
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("value").asInt()).isGreaterThan(highWater);
    }

    @Test
    void overlappingExistingCatalogCodesDoNotClassifyNewSamples() {
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question q
                join questionbank.question_tag t on t.question_id = q.id
                join catalog.catalog_item tag on tag.id = t.tag_item_id
                where q.code like 'SEED-BE14-%' and tag.code in ('NONE', 'VACT-TOAN')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question q
                join catalog.catalog_item c on c.id = q.competency_item_id
                where q.code like 'SEED-BE14-VACT-TOAN-NONE-%'
                  and c.code = 'VACT-TOAN'
                """, Integer.class)).isGreaterThanOrEqualTo(12);
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question q
                join catalog.catalog_item c on c.id = q.competency_item_id
                where q.code like 'SEED-BE14-%' and c.code = 'VACT'
                """, Integer.class)).isZero();
    }

    @Test
    void occupiedD9TagsStillTagVactSamples() {
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question
                where code like 'SEED-BE14-VACT-NN-TA-ANH_PHAT_HIEN_LOI-%'
                   or code like 'SEED-BE14-VACT-NN-TA-ANH_DIEN_DAT_TUONG_DUONG-%'
                """, Integer.class)).isEqualTo(10);
        UUID english = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'ANH'
                """, UUID.class);
        for (var entry : Map.of(
                "ANH_PHAT_HIEN_LOI", "11111111-1111-4111-8111-111111111141",
                "ANH_DIEN_DAT_TUONG_DUONG", "11111111-1111-4111-8111-111111111142").entrySet()) {
            var filter = new QuestionSourcePort.Filter(english, List.of(), List.of(),
                    QuestionType.SINGLE_CHOICE, null, null, List.of(entry.getKey()), List.of("VACT-NN-TA"),
                    new BigDecimal("10"), null, List.of(), List.of());
            assertThat(source.count(filter).total()).as(entry.getKey()).isGreaterThanOrEqualTo(5);
            assertThat(jdbc.queryForObject("""
                    select count(*) from questionbank.question_tag t
                    join questionbank.question q on q.id = t.question_id
                    where t.tag_item_id = ?::uuid and q.code like 'SEED-BE14-%'
                    """, Integer.class, entry.getValue())).as(entry.getKey()).isEqualTo(5);
            assertThat(jdbc.queryForObject("""
                    select count(*) from questionbank.question_version v
                    join questionbank.question q on q.id = v.question_id
                    where v.version_no = 1 and q.code like 'SEED-BE14-VACT-NN-TA-%'
                      and v.snapshot->'tagItemIds' @> ?::jsonb
                    """, Integer.class, "[\"" + entry.getValue() + "\"]")).as(entry.getKey()).isEqualTo(5);
        }
    }

    @Test
    @Transactional
    void occupiedLogicTagsResolveForSetsAndQuestionSeedRerunsAlone() {
        UUID subject = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'SCIENTIFIC_THINKING'
                """, UUID.class);
        for (var entry : Map.of(
                "TD_SAP_XEP_DIEU_KIEN", "11111111-1111-4111-8111-111111111143",
                "TD_XEP_HANG", "11111111-1111-4111-8111-111111111144",
                "TD_BANG_SO_LIEU", "11111111-1111-4111-8111-111111111145",
                "TD_BIEU_DO", "11111111-1111-4111-8111-111111111146").entrySet()) {
            var filter = new QuestionSourcePort.Filter(subject, List.of(), List.of(),
                    QuestionType.QUESTION_SET, null, QuestionType.SINGLE_CHOICE,
                    List.of(entry.getKey()), List.of("VACT-TD-LOGIC"),
                    new BigDecimal("10"), null, List.of(), List.of());
            var sets = source.findCandidates(filter, 1000).stream()
                    .filter(candidate -> candidate.code().startsWith("SEED-BE14-VACT-TD-LOGIC-"))
                    .filter(candidate -> candidate.units().size() == 3).toList();
            assertThat(sets).as(entry.getKey()).hasSize(1);
            UUID root = sets.get(0).questionId();
            assertThat(jdbc.queryForObject("""
                    select count(*) from questionbank.question q
                    join questionbank.question_tag t on t.question_id = q.id
                    join questionbank.question_version v on v.question_id = q.id and v.version_no = 1
                    where (q.id = ? or q.parent_id = ?) and t.tag_item_id = ?::uuid
                      and v.snapshot->'tagItemIds' @> ?::jsonb
                    """, Integer.class, root, root, entry.getValue(), "[\"" + entry.getValue() + "\"]"))
                    .as(entry.getKey() + " root and children").isEqualTo(4);
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question q
                join questionbank.question_tag t on t.question_id = q.id
                join catalog.catalog_item tag on tag.id = t.tag_item_id
                where q.code like 'SEED-BE14-%' and tag.code in ('LEGACY-ENGLISH', 'LEGACY-LOGIC')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select to_regclass('catalog.seed_tag_slot')", String.class)).isNull();

        int before = jdbc.queryForObject("select count(*) from questionbank.question where code like 'SEED-BE14-%'", Integer.class);
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/devdata/R__seed_questionbank_dev_data.sql"));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where code like 'SEED-BE14-%'", Integer.class))
                .isEqualTo(before);
    }
}
