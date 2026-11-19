package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * AC-14. Cơ sở dữ liệu mới và chỉ {@code api,worker,e2e} (không {@code test}, không {@code dev}):
 * nếu khối {@code on-profile: e2e} không nạp {@code classpath:db/devdata} thì các script
 * {@code R__seed_*} không chạy. Các thuộc tính phía dưới thay phần mà {@code test} hoặc {@code dev}
 * thường cấp (realm, khóa, S3 bộ nhớ, tắt lịch nền).
 */
@ActiveProfiles(value = {"api", "worker", "e2e"}, inheritProfiles = false)
@TestPropertySource(properties = {
        "thi360.keycloak.realm=thi-360",
        "thi360.ctx.secret=ctx-dev-only-change-me-32-bytes!!",
        "thi360.cookie.secure=false",
        "thi360.s3.mode=memory",
        "thi360.s3.access-key=thi360",
        "thi360.s3.secret-key=thi360-dev-only",
        "thi360.file.enc-key=dGhpMzYwLWRldi1vbmx5LWFlcy0yNTYta2V5LTAwMDA=",
        "thi360.outbox.scheduling.enabled=false",
        "thi360.identity.provisioning-reconcile.scheduling.enabled=false",
        "thi360.identity.import-batch.scheduling.enabled=false",
        "thi360.questionbank.scheduling.enabled=false",
        "thi360.questionbank.assets.cleanup.scheduling.enabled=false",
        "thi360.exam.scheduling.enabled=false"
})
class E2eProfileDevDataIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) {
        String name = "e2e_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create isolated test database", e);
        }
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
        registry.add("spring.datasource.url", () -> url);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void repeatableSeedScriptsRanUnderE2eProfile() throws IOException {
        List<String> onClasspath = Arrays.stream(
                        new PathMatchingResourcePatternResolver().getResources("classpath:db/devdata/R__seed_*.sql"))
                .map(Resource::getFilename)
                .sorted()
                .toList();
        assertThat(onClasspath).as("devdata seed scripts on the classpath").isNotEmpty();

        List<String> applied = jdbc.queryForList(
                "SELECT script FROM shared.flyway_schema_history WHERE script LIKE 'R\\_\\_seed\\_%' AND success ORDER BY script",
                String.class);

        assertThat(applied).containsExactlyElementsOf(onClasspath);
        // Dữ liệu của script đã vào bảng, không chỉ dòng lịch sử.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM catalog.catalog_item WHERE code_normalized = 'hsa'", Long.class))
                .isGreaterThan(0);
    }
}
