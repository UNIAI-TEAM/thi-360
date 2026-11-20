package vn.unicom.thi360.exam.followup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;

/** FU: template and dev data fixed by migrations and seeds. */
@Transactional
class ExamDataFollowUpIT extends AbstractIntegrationTest {
    private static final String REQUIRED_GRADE = "$.** ? (@.gradeEnforcement == \"REQUIRED\")";
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired private JdbcTemplate jdbc;

    private void migrate(String file) throws Exception {
        jdbc.execute(new String(new ClassPathResource("db/migration/exam/" + file).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));
    }

    /** AD-90: BCA fixes grade and difficulty as "Tham khảo" (H6); other kinds keep their binding grade matrices. */
    @Test
    void bcaTemplateMatricesAreReference() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_template WHERE kind_code = 'BCA' "
                + "AND jsonb_path_exists(structure, ?::jsonpath)", Integer.class, REQUIRED_GRADE)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_template WHERE kind_code = 'BCA' "
                + "AND jsonb_path_exists(structure, '$.** ? (@.gradeEnforcement == \"REFERENCE\")')", Integer.class))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_template WHERE kind_code = 'BCA' "
                + "AND jsonb_path_exists(structure, '$.** ? (@.difficultyEnforcement == \"REQUIRED\")')", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_template WHERE kind_code <> 'BCA' "
                + "AND jsonb_path_exists(structure, ?::jsonpath)", Integer.class, REQUIRED_GRADE)).isPositive();
    }

    /** FU-BE6-3: after the migration no template still carries a part id that is not a UUID. */
    @Test
    void activeTemplatesUseUuidPartIds() {
        assertThat(jdbc.queryForList("SELECT t.code || ':' || (p->>'id') FROM exam.exam_template t, "
                + "jsonb_array_elements(t.structure->'parts') p WHERE t.active AND (p->>'id') !~ ?", String.class,
                UUID_PATTERN)).isEmpty();
    }

    /** FU-BE6-3: the backfill runs on a template with a literal part id that an exam already uses. */
    @Test
    void backfillKeepsExamReferences() throws Exception {
        String parts = "{\"parts\":[{\"id\":\"LEGACY_PART\",\"order\":1,\"name\":\"Legacy\",\"questionCount\":1,"
                + "\"sections\":[{\"id\":\"s\",\"subjectCode\":\"TOAN\",\"questionCount\":1,\"blocks\":[]}]}]}";
        jdbc.update("INSERT INTO exam.exam_template (code, kind_code, scope, timing, structure) "
                + "VALUES ('FU-LEGACY', 'HSA', 'FULL', 'PER_PART', cast(? as jsonb))", parts);
        UUID exam = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing, template_code) "
                + "VALUES (?, ?, ?, 'Legacy', 'FULL', 'PER_PART', 'FU-LEGACY')", exam, exam.toString(), exam.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, cast(? as jsonb), 'fu')",
                exam, parts);
        jdbc.update("INSERT INTO exam.exam_block_ref (id, exam_id, block_id, part_id, section_id) "
                + "VALUES (?, ?, 'b', 'LEGACY_PART', 's')", UUID.randomUUID(), exam);

        migrate("V202611200712__backfill_template_part_ids.sql");

        String expected = jdbc.queryForObject("SELECT md5('FU-LEGACY:LEGACY_PART')::uuid::text", String.class);
        assertThat(jdbc.queryForObject("SELECT structure->'parts'->0->>'id' FROM exam.exam_template WHERE code = 'FU-LEGACY'",
                String.class)).isEqualTo(expected).matches(UUID_PATTERN);
        // The rest of the template is untouched.
        assertThat(jdbc.queryForObject("SELECT structure->'parts'->0->'sections'->0->>'subjectCode' FROM exam.exam_template "
                + "WHERE code = 'FU-LEGACY'", String.class)).isEqualTo("TOAN");
        // The exam keeps its own copy and the references that point at it.
        assertThat(jdbc.queryForObject("SELECT parts->'parts'->0->>'id' FROM exam.exam_structure WHERE exam_id = ?",
                String.class, exam)).isEqualTo("LEGACY_PART");
        assertThat(jdbc.queryForObject("SELECT part_id FROM exam.exam_block_ref WHERE exam_id = ?", String.class, exam))
                .isEqualTo("LEGACY_PART");
        // Running it again changes nothing.
        migrate("V202611200712__backfill_template_part_ids.sql");
        assertThat(jdbc.queryForObject("SELECT structure->'parts'->0->>'id' FROM exam.exam_template WHERE code = 'FU-LEGACY'",
                String.class)).isEqualTo(expected);
    }

    /** Advisor item 8: review templates name their part in Vietnamese; other templates keep their names. */
    @Test
    void reviewTemplatePartsAreNamedInVietnamese() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_template t, jsonb_array_elements(t.structure->'parts') p "
                + "WHERE p->>'name' = 'Review'", Integer.class)).isZero();
        String parts = "{\"parts\":[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"name\":\"Review\"}]}";
        jdbc.update("INSERT INTO exam.exam_template (code, kind_code, scope, timing, build_mode, structure) "
                + "VALUES ('FU-REVIEW', 'HSA', 'SUBJECT', 'SHARED', 'REVIEW', cast(? as jsonb))", parts);
        jdbc.update("INSERT INTO exam.exam_template (code, kind_code, scope, timing, structure) "
                + "VALUES ('FU-STANDARD', 'HSA', 'FULL', 'PER_PART', cast(? as jsonb))", parts);
        migrate("V202611200713__review_template_part_name_vi.sql");
        assertThat(jdbc.queryForObject("SELECT structure->'parts'->0->>'name' FROM exam.exam_template WHERE code = 'FU-REVIEW'",
                String.class)).isEqualTo("Bài ôn");
        assertThat(jdbc.queryForObject("SELECT structure->'parts'->0->>'name' FROM exam.exam_template WHERE code = 'FU-STANDARD'",
                String.class)).isEqualTo("Review");
    }
}
