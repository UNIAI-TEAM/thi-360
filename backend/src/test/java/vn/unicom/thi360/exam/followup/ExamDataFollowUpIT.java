package vn.unicom.thi360.exam.followup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.questionbank.application.DevQuestionAssetSeeder;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/** FU: template and dev data fixed by migrations and seeds. */
@Transactional
class ExamDataFollowUpIT extends AbstractIntegrationTest {
    private static final String REQUIRED_GRADE = "$.** ? (@.gradeEnforcement == \"REQUIRED\")";
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectStorage storage;
    @Autowired private DevQuestionAssetSeeder seeder;

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

    /**
     * FU-FE1-2: the step-1 subject list is the subjects that have a template. Every template subject is an active catalog
     * subject, and an exam kind has one default standard subject template per subject, so no subject is offered twice.
     */
    @Test
    void stepOneSubjectsHaveTemplates() {
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT t.kind_code || ':' || t.subject_code FROM exam.exam_template t
                WHERE t.scope = 'SUBJECT' AND t.active AND t.subject_code IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM catalog.catalog_item s JOIN catalog.catalog_group g ON g.id = s.group_id
                                  WHERE g.code = 'SUBJECT' AND s.code = t.subject_code AND s.status = 1)
                """, String.class)).as("template subjects missing from the active catalog").isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT kind_code || ':' || subject_code || ' x' || count(*) FROM exam.exam_template
                WHERE scope = 'SUBJECT' AND active AND is_default AND build_mode = 'STANDARD' AND part_code IS NULL
                  AND subject_code IS NOT NULL
                GROUP BY kind_code, subject_code HAVING count(*) > 1
                """, String.class)).as("subjects with more than one default template").isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT subject_code) FROM exam.exam_template
                WHERE scope = 'SUBJECT' AND active AND subject_code IS NOT NULL
                """, Integer.class)).isPositive();
    }

    /** V1-04: a subject other than Toán has MAPID nodes, and every selectable (leaf) node of it finds questions. */
    @Test
    void mapidNodesHaveQuestions() {
        List<Map<String, Object>> leaves = jdbc.queryForList("""
                SELECT s.code AS subject, n.code AS node,
                       (SELECT count(*) FROM questionbank.question_knowledge k
                        JOIN questionbank.question q ON q.id = k.question_id
                        WHERE k.knowledge_item_id = n.id AND q.status = 'READY' AND q.deleted_at IS NULL) AS questions
                FROM catalog.catalog_item n
                JOIN catalog.catalog_group g ON g.id = n.group_id AND g.code = 'KNOWLEDGE'
                JOIN catalog.catalog_item_ref r ON r.item_id = (
                    SELECT split_part(trim(both '/' from n.path), '/', 1)::uuid)
                JOIN catalog.catalog_item s ON s.id = r.ref_item_id
                JOIN catalog.catalog_group sg ON sg.id = s.group_id AND sg.code = 'SUBJECT'
                WHERE s.code <> 'TOAN' AND n.depth = 3 AND n.status = 1
                """);
        assertThat(leaves).as("MAPID leaves of a subject other than Toán").isNotEmpty();
        assertThat(leaves.stream().map(row -> (String) row.get("subject")).distinct().toList())
                .as("subjects with MAPID nodes").contains("LY");
        for (Map<String, Object> leaf : leaves) {
            assertThat(((Number) leaf.get("questions")).intValue()).as("%s %s", leaf.get("subject"), leaf.get("node"))
                    .isGreaterThanOrEqualTo(1);
        }
    }

    /** FU-BE10-ASSET: the BCA attachment questions carry image files that are in the store and equal their sources. */
    @Test
    void bcaSeedAttachmentsAreStored() throws Exception {
        List<Map<String, Object>> assets = jdbc.queryForList("""
                SELECT id, object_key, file_name, mime, size, sha256 FROM questionbank.question_asset
                WHERE starts_with(object_key, 'questions/dev-seed/bca/') ORDER BY object_key
                """);
        assertThat(assets).hasSize(3);
        for (Map<String, Object> asset : assets) {
            String key = (String) asset.get("object_key");
            String fileName = (String) asset.get("file_name");
            assertThat(fileName).startsWith("bca/");
            byte[] file = new ClassPathResource("db/devdata/assets/" + fileName).getInputStream().readAllBytes();
            assertThat(asset.get("mime")).as(key).isEqualTo("image/png");
            assertThat(((Number) asset.get("size")).longValue()).as(key).isEqualTo(file.length);
            assertThat((byte[]) asset.get("sha256")).as(key).isEqualTo(MessageDigest.getInstance("SHA-256").digest(file));
            assertThat(storage.exists(key)).as(key).isTrue();
            assertThat(storage.get(key).orElseThrow()).as(key).isEqualTo(file);
        }
        for (String subject : List.of("HOA", "SINH", "DIA")) {
            UUID question = jdbc.queryForObject("SELECT id FROM questionbank.question WHERE code = ?", UUID.class,
                    "SEED-FU-BCA-ATTACH-" + subject);
            List<String> ids = jdbc.queryForList(
                    "SELECT jsonb_array_elements_text(snapshot -> 'attachmentIds') FROM questionbank.question_version "
                            + "WHERE question_id = ? AND version_no = 1", String.class, question);
            assertThat(ids).as(subject).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_asset_ref "
                    + "WHERE question_id = ? AND version_no = 1 AND asset_id = ?::uuid", Integer.class,
                    question, ids.getFirst())).as(subject).isEqualTo(1);
        }
    }

    /** An empty store (a fresh MinIO, the in-memory one of a test) is refilled completely by one run, and a second run writes nothing. */
    @Test
    void emptyStoreReseedsWithoutError() {
        List<String> keys = jdbc.queryForList(
                "SELECT object_key FROM questionbank.question_asset WHERE starts_with(object_key, 'questions/dev-seed/') "
                        + "ORDER BY object_key", String.class);
        assertThat(keys).hasSizeGreaterThanOrEqualTo(21);
        try {
            keys.forEach(storage::delete);
            assertThat(seeder.seed()).isEqualTo(keys.size());
            keys.forEach(key -> assertThat(storage.exists(key)).as(key).isTrue());
            assertThat(seeder.seed()).isZero();
        } finally {
            seeder.seed();
        }
    }
}
