package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * BE-16 R1-1, R1-2: the HSCA / VSAT dev seeds on a database that already holds admin-created EXAM items. The repeatable
 * seeds run again at every start, so a clash here would stop the application from booting.
 */
@Transactional
class ExamKindSeedUpgradeIT extends AbstractIntegrationTest {
    private static final String EXAM_GROUP = "00000000-0000-7000-8100-000000000001";
    private static final String SEED_HSCA = "00000000-0000-7000-8400-000000000108";
    private static final String SEED_VSAT = "00000000-0000-7000-8400-000000000109";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @Test
    void adminItemAtValue8AndHscaOnItsOwnIdKeepTheirIdsValuesAndConfiguredProfile() {
        removeSeededHscaAndVsat();
        insertExamItem(UUID.randomUUID(), "ADMIN_EXTRA", 8);
        UUID ownHsca = UUID.randomUUID();
        insertExamItem(ownHsca, "HSCA", 9);
        jdbc.update("""
                INSERT INTO exam.exam_kind_profile (exam_item_id, scopes, template_by, timing, practice_scoring, mc_rule)
                VALUES (?, '{FULL}', 'KIND', 'PER_PART', 'LINEAR', 'ALL_OR_NOTHING')""", ownHsca);

        runSeeds();
        Map<String, Integer> afterFirst = examValues();
        runSeeds();

        assertThat(examValues()).as("rerun does not reassign a value").isEqualTo(afterFirst);
        assertThat(afterFirst).containsEntry("ADMIN_EXTRA", 8).containsEntry("HSCA", 9);
        assertThat(afterFirst.get("VSAT")).as("allocated above the group's high-water mark").isGreaterThan(9);
        assertThat(afterFirst.values()).as("no duplicate value").doesNotHaveDuplicates();
        assertThat(count("SELECT count(*) FROM catalog.catalog_item WHERE group_id = ?::uuid AND code_normalized = 'hsca'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM catalog.catalog_item WHERE group_id = ?::uuid AND code_normalized = 'vsat'"))
                .isEqualTo(1);

        Map<String, Object> hsca = profile("HSCA");
        assertThat(hsca.get("exam_item_id")).isEqualTo(ownHsca);
        assertThat(hsca).as("configured profile kept").containsEntry("scopes", "FULL")
                .containsEntry("template_by", "KIND").containsEntry("practice_scoring", "LINEAR")
                .containsEntry("mc_rule", "ALL_OR_NOTHING");
        Map<String, Object> vsat = profile("VSAT");
        assertThat(vsat).containsEntry("scopes", "SUBJECT,PART").containsEntry("mc_rule", "ALL_OR_NOTHING");
        assertThat((String) vsat.get("tf_scoring")).contains("COUNT_TABLE");
        assertThat(vsat.get("exam_item_id")).as("a missing VSAT is created on the seed id").isEqualTo(UUID.fromString(SEED_VSAT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_kind_profile WHERE exam_item_id = ?::uuid",
                Integer.class, SEED_HSCA)).as("no orphan profile on the unused HSCA seed id").isZero();
    }

    @Test
    void hscaOnItsOwnIdWithoutAProfileGetsTheSeedProfileOnThatItem() {
        removeSeededHscaAndVsat();
        UUID ownHsca = UUID.randomUUID();
        insertExamItem(ownHsca, "HSCA", 8);

        runSeeds();

        Map<String, Object> hsca = profile("HSCA");
        assertThat(hsca.get("exam_item_id")).isEqualTo(ownHsca);
        assertThat(hsca).containsEntry("scopes", "SUBJECT,PART").containsEntry("mc_rule", "PARTIAL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_kind_profile WHERE exam_item_id = ?::uuid",
                Integer.class, SEED_HSCA)).isZero();
        assertThat(examValues().get("VSAT")).as("above the group high-water mark").isGreaterThan(8);
    }

    private void removeSeededHscaAndVsat() {
        jdbc.update("DELETE FROM exam.exam_kind_profile WHERE exam_item_id IN (?::uuid, ?::uuid)", SEED_HSCA, SEED_VSAT);
        jdbc.update("DELETE FROM catalog.catalog_item WHERE id IN (?::uuid, ?::uuid)", SEED_HSCA, SEED_VSAT);
    }

    private void insertExamItem(UUID id, String code, int value) {
        jdbc.update("""
                INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en,
                    display_order, status, is_system, depth, path)
                VALUES (?, ?::uuid, ?, lower(?), ?, ?, ?, 9, 1, false, 1, '/' || ?::text || '/')""",
                id, EXAM_GROUP, code, code, value, code, code, id);
        jdbc.update("UPDATE catalog.catalog_group SET last_value = GREATEST(last_value, ?) WHERE id = ?::uuid",
                value, EXAM_GROUP);
    }

    private void runSeeds() {
        var connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/devdata/R__seed_catalog_dev_data.sql"));
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/devdata/R__seed_exam_dev_data.sql"));
    }

    private Map<String, Integer> examValues() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT code, value FROM catalog.catalog_item WHERE group_id = ?::uuid", EXAM_GROUP);
        Map<String, Integer> values = new java.util.LinkedHashMap<>();
        rows.forEach(row -> values.put((String) row.get("code"), ((Number) row.get("value")).intValue()));
        return values;
    }

    private Map<String, Object> profile(String code) {
        return jdbc.queryForMap("""
                SELECT p.exam_item_id, array_to_string(p.scopes, ',') AS scopes, p.template_by, p.practice_scoring,
                       p.mc_rule, p.tf_scoring::text AS tf_scoring
                FROM exam.exam_kind_profile p JOIN catalog.catalog_item i ON i.id = p.exam_item_id
                WHERE i.group_id = ?::uuid AND i.code = ?""", EXAM_GROUP, code);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, EXAM_GROUP);
    }
}
