package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.shared.security.TestSecurity;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CombinationBranch;
import vn.unicom.thi360.identity.spi.Permissions;

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
                    ('11111111-1111-4111-8111-111111111111', '00000000-0000-7000-8100-000000000008', 'USER-TAG', 'user-tag', 2, 'User', 1, 1, false, 1, '/11111111-1111-4111-8111-111111111111/')
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
    @Autowired CatalogLookup lookup;
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.catalog_item WHERE group_id = ? AND id::text LIKE '00000000-0000-7000-8601-%'", Integer.class, TAG_GROUP)).isEqualTo(40);
        int highWater = jdbc.queryForObject("SELECT max(value) FROM catalog.catalog_item WHERE group_id = ?", Integer.class, TAG_GROUP);
        assertThat(jdbc.queryForObject("SELECT last_value FROM catalog.catalog_group WHERE id = ?", Integer.class, TAG_GROUP)).isGreaterThanOrEqualTo(highWater);
        var result = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", TAG_GROUP)
                        .with(TestSecurity.asUser(testSecuritySupport, Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AFTER-UPGRADE\",\"nameVi\":\"After upgrade\"}"))
                .andExpect(status().isCreated()).andReturn();
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("value").asInt()).isGreaterThan(highWater);
    }
}
