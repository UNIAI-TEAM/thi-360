package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** BE-16 rows 1, 9, 10: template flags, the H41 constraint, and the HSCA / VSAT dev catalog. */
@Transactional
class ExamTemplateExtendedContractIT extends AbstractIntegrationTest {
    private static final List<String> OLD_KINDS = List.of("HSA", "QDA", "BCA", "VACT", "SPT", "SP2E", "THPTQG");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void existingTemplatesExposeBothNewFlagsAsFalse() throws Exception {
        var templates = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        int checked = 0;
        for (var template : templates) {
            if (!OLD_KINDS.contains(template.path("kindCode").asText())) continue;
            String code = template.path("code").asText();
            assertThat(template.path("fixedBlockCounts").isBoolean()).as(code).isTrue();
            assertThat(template.path("omittedLocked").isBoolean()).as(code).isTrue();
            assertThat(template.path("fixedBlockCounts").asBoolean()).as(code + " fixedBlockCounts").isFalse();
            assertThat(template.path("omittedLocked").asBoolean()).as(code + " omittedLocked").isFalse();
            checked++;
        }
        assertThat(checked).isGreaterThan(10);
    }

    @Test
    void flagColumnsDefaultToFalseForRowsInsertedWithoutThem() {
        insertTemplate("FLAG_DEFAULTS", "PART", "P2", "ANH");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT fixed_block_counts, omitted_locked FROM exam.exam_template WHERE code = 'FLAG_DEFAULTS'");
        assertThat(row).containsEntry("fixed_block_counts", false).containsEntry("omitted_locked", false);
    }

    @Test
    void partTemplateMayCarryASubjectCode() {
        insertTemplate("PART_WITH_SUBJECT", "PART", "P2", "ANH");
        assertThat(jdbc.queryForObject(
                "SELECT subject_code FROM exam.exam_template WHERE code = 'PART_WITH_SUBJECT'", String.class))
                .isEqualTo("ANH");
    }

    @Test
    void fullTemplateWithASubjectCodeIsStillRejected() {
        assertThatThrownBy(() -> insertTemplate("FULL_WITH_SUBJECT", "FULL", null, "ANH"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_exam_template_scope_fields");
    }

    @Test
    void fullTemplateWithAPartCodeIsStillRejected() {
        assertThatThrownBy(() -> insertTemplate("FULL_WITH_PART", "FULL", "P2", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_exam_template_scope_fields");
    }

    @Test
    void subjectTemplateStillNeedsASubjectCodeUnlessItIsAReviewSkeleton() {
        assertThatThrownBy(() -> insertTemplate("SUBJECT_WITHOUT_SUBJECT", "SUBJECT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_exam_template_scope_fields");
    }

    @Test
    void devCatalogHasHscaAndVsatWithTheH36Profiles() {
        List<String> kinds = jdbc.queryForList("""
                SELECT i.code FROM catalog.catalog_item i
                JOIN catalog.catalog_group g ON g.id = i.group_id
                WHERE g.code = 'EXAM' AND i.status = 1""", String.class);
        assertThat(kinds).contains("HSCA", "VSAT");

        for (String kind : List.of("HSCA", "VSAT")) {
            Map<String, Object> profile = jdbc.queryForMap("""
                    SELECT array_to_string(p.scopes, ',') AS scopes, p.template_by, p.timing, p.max_score,
                           p.reference_max, p.practice_scoring,
                           p.mc_rule, p.tf_scoring::text AS tf_scoring
                    FROM exam.exam_kind_profile p JOIN catalog.catalog_item i ON i.id = p.exam_item_id
                    WHERE i.code = ?""", kind);
            assertThat(java.util.Arrays.asList(((String) profile.get("scopes")).split(",")))
                    .as(kind).containsExactlyInAnyOrder("SUBJECT", "PART");
            assertThat(profile).as(kind).containsEntry("template_by", "SUBJECT")
                    .containsEntry("timing", "SHARED")
                    .containsEntry("practice_scoring", "RAW")
                    .containsEntry("max_score", null)
                    .containsEntry("reference_max", null);
        }
        assertThat(jdbc.queryForObject("""
                SELECT p.mc_rule FROM exam.exam_kind_profile p JOIN catalog.catalog_item i ON i.id = p.exam_item_id
                WHERE i.code = 'HSCA'""", String.class)).isEqualTo("PARTIAL");
        var vsat = jdbc.queryForObject("""
                SELECT p.tf_scoring::text FROM exam.exam_kind_profile p JOIN catalog.catalog_item i ON i.id = p.exam_item_id
                WHERE i.code = 'VSAT'""", String.class);
        assertThat(vsat).contains("COUNT_TABLE").contains("[1, 2, 3, 6]");
    }

    @Test
    void devCatalogHasTheTwoGeographyLabels() {
        List<String> tags = jdbc.queryForList("""
                SELECT i.code FROM catalog.catalog_item i
                JOIN catalog.catalog_group g ON g.id = i.group_id
                WHERE g.code = 'QUESTION_TAG' AND i.code IN ('DIA_BANG_SO_LIEU', 'DIA_BIEU_DO') AND i.status = 1""",
                String.class);
        assertThat(tags).containsExactlyInAnyOrder("DIA_BANG_SO_LIEU", "DIA_BIEU_DO");
    }

    private void insertTemplate(String code, String scope, String partCode, String subjectCode) {
        jdbc.update("""
                INSERT INTO exam.exam_template (code, kind_code, scope, part_code, subject_code, timing, structure,
                    build_mode, is_default, active)
                VALUES (?, 'VSAT', ?, ?, ?, 'PER_PART', '{"parts":[]}'::jsonb, 'STANDARD', false, true)""",
                code, scope, partCode, subjectCode);
    }
}
