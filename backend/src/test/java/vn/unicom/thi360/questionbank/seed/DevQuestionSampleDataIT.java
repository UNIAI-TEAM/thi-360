package vn.unicom.thi360.questionbank.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.questionbank.application.DevQuestionAssetSeeder;
import vn.unicom.thi360.questionbank.domain.QuestionAssetSignature;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * BE-19 (UNI-2691): the H-SCA and V-SAT sample questions, their listening audio and the safety of
 * the repeatable seed on a database that already holds other rows.
 */
class DevQuestionSampleDataIT extends AbstractIntegrationTest {

    private static final String SEED_SCRIPT = "db/devdata/R__seed_questionbank_dev_data.sql";
    private static final String ASSET_PREFIX = "questions/dev-seed/";
    private static final int AUDIO_ROOTS = 18;

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired ObjectStorage storage;
    @Autowired DevQuestionAssetSeeder seeder;

    // ---- row 3: V-SAT points -------------------------------------------------------------

    @Test
    void vsatTrueFalseIsSixPointsOnTheCountTable() throws Exception {
        List<Sample> rows = samples("SEED-VSAT-%-TF-%");
        assertThat(rows).hasSize(8 * 12);
        for (Sample row : rows) {
            assertThat(row.points()).as(row.code()).isEqualByComparingTo("6");
            JsonNode body = row.snapshot().path("body");
            assertThat(body.path("type").asText()).isEqualTo("TRUE_FALSE");
            assertThat(body.path("scoringMethod").asText()).isEqualTo("COUNT_TABLE");
            assertThat(body.path("statements")).as(row.code()).hasSize(4);
            assertThat(decimals(body.path("scoreTable"))).as(row.code())
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("6"));
        }
    }

    @Test
    void vsatMatchingIsOnePointFiveAPairAndClozeSixAPosition() throws Exception {
        List<Sample> matching = samples("SEED-VSAT-%-MATCH-%");
        assertThat(matching).hasSize(8 * 10);
        for (Sample row : matching) {
            JsonNode body = row.snapshot().path("body");
            assertThat(body.path("left")).as(row.code()).hasSize(4);
            assertThat(body.path("pairs")).as(row.code()).hasSize(4);
            body.path("pairs").forEach(pair ->
                    assertThat(pair.path("points").decimalValue()).isEqualByComparingTo("1.5"));
            assertThat(row.points()).as(row.code()).isEqualByComparingTo("6");
        }
        List<Sample> cloze = samples("SEED-VSAT-ANH-CLOZE-%");
        assertThat(cloze).hasSize(3);
        for (Sample row : cloze) {
            JsonNode blanks = row.snapshot().path("body").path("blanks");
            assertThat(blanks).as(row.code()).hasSize(5);
            blanks.forEach(blank -> assertThat(blank.path("points").decimalValue()).isEqualByComparingTo("6"));
            assertThat(row.points()).as(row.code()).isEqualByComparingTo("30");
        }
        List<Sample> shortAnswers = samples("SEED-VSAT-%-SA-%");
        assertThat(shortAnswers).hasSize(6 * 10);
        shortAnswers.forEach(row -> assertThat(row.points()).as(row.code()).isEqualByComparingTo("6"));
    }

    @Test
    void vsatSetsCarryTheSpecChildShapesAtSixPointsPerChild() throws Exception {
        for (Sample set : samples("SEED-VSAT-%-SET-%")) {
            JsonNode children = set.snapshot().path("body").path("children");
            boolean mixed = set.code().startsWith("SEED-VSAT-ANH-MIXED");
            boolean english = set.code().startsWith("SEED-VSAT-ANH-");
            assertThat(children).as(set.code()).hasSize(mixed ? 8 : english ? 6 : 3);
            for (int i = 0; i < children.size(); i++) {
                JsonNode child = children.get(i);
                assertThat(child.path("points").decimalValue()).as(set.code() + "#" + i).isEqualByComparingTo("6");
                String expectedType = mixed && i < 2 ? "TRUE_FALSE" : "SINGLE_CHOICE";
                assertThat(child.path("type").asText()).as(set.code() + "#" + i).isEqualTo(expectedType);
                assertThat(child.path("body").path("type").asText()).isEqualTo(expectedType);
                if ("TRUE_FALSE".equals(expectedType)) {
                    assertThat(child.path("body").path("statements")).hasSize(4);
                    assertThat(decimals(child.path("body").path("scoreTable")).get(3)).isEqualByComparingTo("6");
                }
            }
            assertThat(set.points()).as(set.code()).isEqualByComparingTo(
                    BigDecimal.valueOf(6L * children.size()));
        }
        // Spec 5.2: positions 1-2 of the shared reading carry 12 points, positions 3-8 carry 36.
        assertThat(samples("SEED-VSAT-ANH-MIXED-SET-%")).hasSize(3);
        assertThat(samples("SEED-VSAT-ANH-CHOICE-SET-%")).hasSize(3);
    }

    @Test
    void hscaPartTwoSetsOfEachScienceSubjectHaveFiveChildrenAtAQuarterPoint() throws Exception {
        for (String subject : List.of("TOAN", "LY", "HOA", "SINH")) {
            List<Sample> sets = samples("SEED-HSCA-" + subject + "-SET-%");
            assertThat(sets).as(subject).hasSizeGreaterThanOrEqualTo(2);
            for (Sample set : sets) {
                JsonNode children = set.snapshot().path("body").path("children");
                assertThat(children).as(set.code()).hasSize(5);
                children.forEach(child -> {
                    assertThat(child.path("type").asText()).isEqualTo("SINGLE_CHOICE");
                    assertThat(child.path("points").decimalValue()).isEqualByComparingTo("0.25");
                    assertThat(child.path("body").path("options")).hasSize(4);
                });
                assertThat(set.points()).as(set.code()).isEqualByComparingTo("1.25");
            }
        }
    }

    // ---- row 4: audio files, object storage and the seeder ---------------------------------

    @Test
    void sampleAudioIsSeededOnceAndEveryRowMatchesItsFile() throws Exception {
        List<Map<String, Object>> assets = jdbc.queryForList("""
                select id, object_key, file_name, mime, size, sha256 from questionbank.question_asset
                where starts_with(object_key, ?) order by object_key
                """, ASSET_PREFIX);
        assertThat(assets).hasSize(AUDIO_ROOTS);
        assertThat(assets.stream().map(row -> row.get("object_key")).distinct()).hasSize(AUDIO_ROOTS);
        assertThat(new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/devdata/assets/*.mp3")).hasSize(AUDIO_ROOTS);

        // A storage that starts empty, as the in-memory one does in every test context.
        assets.forEach(row -> storage.delete((String) row.get("object_key")));
        try {
            assertThat(seeder.seed()).isEqualTo(AUDIO_ROOTS);
            assertThat(seeder.seed()).isZero();
            for (Map<String, Object> row : assets) {
                String key = (String) row.get("object_key");
                byte[] file = new ClassPathResource("db/devdata/assets/" + row.get("file_name"))
                        .getInputStream().readAllBytes();
                assertThat(storage.exists(key)).as(key).isTrue();
                assertThat(storage.get(key).orElseThrow()).as(key).isEqualTo(file);
                assertThat(((Number) row.get("size")).longValue()).as(key).isEqualTo(file.length);
                assertThat((byte[]) row.get("sha256")).as(key)
                        .isEqualTo(MessageDigest.getInstance("SHA-256").digest(file));
                assertThat(row.get("mime")).as(key).isEqualTo("audio/mpeg");
                assertThat(QuestionAssetSignature.detectAttachment(file, (String) row.get("file_name"))
                        .orElseThrow().mime()).as(key).isEqualTo("audio/mpeg");
            }
        } finally {
            seeder.seed();
        }
    }

    @Test
    void audioIsAnAttachmentOfTheQuestionRootOnly() throws Exception {
        List<Sample> audioRoots = new ArrayList<>();
        audioRoots.addAll(samples("SEED-HSCA-ANH-LISTEN-SINGLE-%"));
        audioRoots.addAll(samples("SEED-HSCA-ANH-LISTEN-SET4-%"));
        audioRoots.addAll(samples("SEED-HSCA-ANH-LISTEN-SET5-%"));
        assertThat(audioRoots).hasSize(AUDIO_ROOTS);
        for (Sample root : audioRoots) {
            JsonNode ids = root.snapshot().path("attachmentIds");
            assertThat(ids).as(root.code()).hasSize(1);
            Map<String, Object> asset = jdbc.queryForMap(
                    "select mime, object_key, kind from questionbank.question_asset where id = ?",
                    UUID.fromString(ids.get(0).asText()));
            assertThat(asset.get("mime")).isEqualTo("audio/mpeg");
            assertThat(asset.get("kind")).isEqualTo("ATTACHMENT");
            assertThat(storage.exists((String) asset.get("object_key"))).as(root.code()).isTrue();
            assertThat(jdbc.queryForObject("""
                    select count(*) from questionbank.question_asset_ref
                    where asset_id = ? and question_id = ? and version_no = 1
                    """, Integer.class, UUID.fromString(ids.get(0).asText()), root.id())).isEqualTo(1);
        }
        // Children of a listening set, the reading sets and every other seeded row carry no file.
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question q
                join questionbank.question_version v on v.question_id = q.id and v.version_no = coalesce(q.current_version_no, q.working_version_no)
                where (q.code like 'SEED-HSCA-%' or q.code like 'SEED-VSAT-%')
                  and q.code not like 'SEED-HSCA-ANH-LISTEN-%'
                  and jsonb_array_length(coalesce(v.snapshot -> 'attachmentIds', '[]'::jsonb)) > 0
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question c
                join questionbank.question_version v on v.question_id = c.id and v.version_no = c.current_version_no
                where c.parent_id is not null and c.code like 'SEED-HSCA-ANH-LISTEN-%'
                  and jsonb_array_length(coalesce(v.snapshot -> 'attachmentIds', '[]'::jsonb)) > 0
                """, Integer.class)).isZero();
    }

    @Test
    @Transactional
    void theRepeatableCreatesNothingTwice() throws Exception {
        List<Integer> before = tableCounts();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_SCRIPT));
        assertThat(tableCounts()).isEqualTo(before);
    }

    // ---- populated database: no fixed value may collide with rows others created --------------

    @Test
    @Transactional
    void anAdminQuestionOnASeedCodeIsKeptAndItsSeedSetIsSkippedWhole() throws Exception {
        String code = "SEED-HSCA-ANH-LISTEN-SET4-01";
        UUID seedRoot = jdbc.queryForObject("select id from questionbank.question where code = ?", UUID.class, code);
        removeWithDependents(seedRoot);
        UUID adminId = UUID.randomUUID();
        jdbc.update("""
                insert into questionbank.question (id, code, type, status, points, content_text, search_text)
                values (?, ?, 'SINGLE_CHOICE', 'DRAFT', 1, 'admin', 'admin')
                """, adminId, code);
        Integer questionsBefore = jdbc.queryForObject("select count(*) from questionbank.question", Integer.class);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_SCRIPT));

        assertThat(jdbc.queryForList("select id, type, status from questionbank.question where code = ?", code))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("id")).isEqualTo(adminId);
                    assertThat(row.get("type")).isEqualTo("SINGLE_CHOICE");
                    assertThat(row.get("status")).isEqualTo("DRAFT");
                });
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where parent_id = ?", Integer.class,
                adminId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where parent_id = ?", Integer.class,
                seedRoot)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_asset_ref where question_id in (?, ?)",
                Integer.class, adminId, seedRoot)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question", Integer.class))
                .isEqualTo(questionsBefore);
    }

    @Test
    @Transactional
    void aRenamedSeedQuestionKeepsItsNewCodeAndIsNotInsertedAgain() throws Exception {
        UUID root = jdbc.queryForObject("select id from questionbank.question where code = 'SEED-VSAT-TOAN-TF-001'",
                UUID.class);
        UUID child = jdbc.queryForObject("select id from questionbank.question where code = 'SEED-HSCA-TOAN-SET-001-0'",
                UUID.class);
        UUID audioRoot = jdbc.queryForObject(
                "select id from questionbank.question where code = 'SEED-HSCA-ANH-LISTEN-SINGLE-01'", UUID.class);
        jdbc.update("update questionbank.question set code = 'ADMIN-RENAMED-TF' where id = ?", root);
        jdbc.update("update questionbank.question set code = 'ADMIN-RENAMED-CHILD' where id = ?", child);
        jdbc.update("update questionbank.question set code = 'ADMIN-RENAMED-AUDIO' where id = ?", audioRoot);
        List<Integer> before = tableCounts();

        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_SCRIPT));

        assertThat(tableCounts()).isEqualTo(before);
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question
                where code in ('SEED-VSAT-TOAN-TF-001', 'SEED-HSCA-TOAN-SET-001-0', 'SEED-HSCA-ANH-LISTEN-SINGLE-01')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question
                where id in (?, ?, ?) and code in ('ADMIN-RENAMED-TF', 'ADMIN-RENAMED-CHILD', 'ADMIN-RENAMED-AUDIO')
                """, Integer.class, root, child, audioRoot)).isEqualTo(3);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Sample(UUID id, String code, BigDecimal points, JsonNode snapshot) {
    }

    private List<Sample> samples(String codePattern) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select q.id, q.code, q.points, v.snapshot::text as snapshot
                from questionbank.question q
                join questionbank.question_version v on v.question_id = q.id
                    and v.version_no = coalesce(q.current_version_no, q.working_version_no)
                where q.code like ? and q.parent_id is null order by q.code
                """, codePattern);
        List<Sample> samples = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            samples.add(new Sample((UUID) row.get("id"), (String) row.get("code"), (BigDecimal) row.get("points"),
                    mapper.readTree((String) row.get("snapshot"))));
        }
        return samples;
    }

    private static List<BigDecimal> decimals(JsonNode array) {
        List<BigDecimal> values = new ArrayList<>();
        array.forEach(node -> values.add(node.decimalValue()));
        return values;
    }

    private List<Integer> tableCounts() {
        return List.of(
                jdbc.queryForObject("select count(*) from questionbank.question", Integer.class),
                jdbc.queryForObject("select count(*) from questionbank.question_version", Integer.class),
                jdbc.queryForObject("select count(*) from questionbank.question_tag", Integer.class),
                jdbc.queryForObject("select count(*) from questionbank.question_asset", Integer.class),
                jdbc.queryForObject("select count(*) from questionbank.question_asset_ref", Integer.class));
    }

    private void removeWithDependents(UUID root) {
        String ids = "(select id from questionbank.question where id = ? or parent_id = ?)";
        for (String table : List.of("question_asset_ref", "question_version", "question_tag", "question_knowledge")) {
            jdbc.update("delete from questionbank." + table + " where question_id in " + ids, root, root);
        }
        jdbc.update("delete from questionbank.question where parent_id = ?", root);
        jdbc.update("delete from questionbank.question where id = ?", root);
    }
}
