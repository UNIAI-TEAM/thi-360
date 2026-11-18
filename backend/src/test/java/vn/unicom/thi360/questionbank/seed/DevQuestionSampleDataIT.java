package vn.unicom.thi360.questionbank.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
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
    void vsatGeographySetsCarryOneSlotLabelOnTheRootOnly() throws Exception {
        List<Sample> sets = samples("SEED-VSAT-DIA-SET-%");
        assertThat(sets).hasSize(4);
        for (Sample set : sets) {
            // The version-1 snapshot agrees with question_tag: one label on the root.
            assertThat(set.snapshot().path("tagItemIds")).as(set.code()).hasSize(1);
        }
        assertDiaSetTagging();
    }

    @Test
    @Transactional
    void anOldHeadDatabaseConvergesOnlyWhereTheSeedStateIsUntouched() throws Exception {
        installOldHead();
        UUID bang = tagId("DIA_BANG_SO_LIEU");
        UUID bieu = tagId("DIA_BIEU_DO");
        UUID adminTag = tagId("DIA_CAU_KY_NANG");
        // 003: an admin published version 2 with different tags; 004: published again, same labels.
        publishVersionTwo("SEED-VSAT-DIA-SET-003", adminTag);
        publishVersionTwo("SEED-VSAT-DIA-SET-004", bang, bieu);
        // An unrelated root that happens to hold both labels, and one with its own tag.
        setTags(id("SEED-VSAT-TOAN-SET-001"), "DIA_BANG_SO_LIEU", "DIA_BIEU_DO");
        List<Map<String, Object>> untouchedBefore = state("SEED-VSAT-DIA-SET-003%", "SEED-VSAT-DIA-SET-004%",
                "SEED-VSAT-TOAN-SET-001%", "SEED-VSAT-VAN-POETRY-SET-001%");
        assertThat(tags("SEED-VSAT-DIA-SET-001")).containsExactlyInAnyOrder(bang, bieu);
        assertThat(snapshotTags("SEED-VSAT-DIA-SET-002", 1)).containsExactlyInAnyOrder(bang, bieu);

        runSeed();

        // 001 -> DIA_BIEU_DO, 002 -> DIA_BANG_SO_LIEU in both representations, children bare.
        assertThat(tags("SEED-VSAT-DIA-SET-001")).containsExactly(bieu);
        assertThat(snapshotTags("SEED-VSAT-DIA-SET-001", 1)).containsExactly(bieu);
        assertThat(tags("SEED-VSAT-DIA-SET-002")).containsExactly(bang);
        assertThat(snapshotTags("SEED-VSAT-DIA-SET-002", 1)).containsExactly(bang);
        for (String root : List.of("SEED-VSAT-DIA-SET-001", "SEED-VSAT-DIA-SET-002")) {
            List<UUID> children = jdbc.queryForList(
                    "select id from questionbank.question where parent_id = ? order by position", UUID.class, id(root));
            assertThat(children).as(root).hasSize(3);
            for (UUID child : children) {
                assertThat(jdbc.queryForObject("select count(*) from questionbank.question_tag where question_id = ?",
                        Integer.class, child)).as(root).isZero();
                assertThat(jdbc.queryForObject("""
                        select v.snapshot -> 'tagItemIds' from questionbank.question_version v
                        where v.question_id = ? and v.version_no = 1
                        """, String.class, child)).as(root).isEqualTo("[]");
            }
        }
        // Edited, published and unrelated rows keep every tag and every version byte for byte.
        assertThat(state("SEED-VSAT-DIA-SET-003%", "SEED-VSAT-DIA-SET-004%", "SEED-VSAT-TOAN-SET-001%",
                "SEED-VSAT-VAN-POETRY-SET-001%")).isEqualTo(untouchedBefore);
        assertThat(tags("SEED-VSAT-DIA-SET-003")).containsExactly(adminTag);
        assertThat(snapshotTags("SEED-VSAT-DIA-SET-003", 1)).containsExactlyInAnyOrder(bang, bieu);
        assertThat(tags("SEED-VSAT-DIA-SET-004")).containsExactlyInAnyOrder(bang, bieu);
        assertThat(tags("SEED-VSAT-TOAN-SET-001")).containsExactlyInAnyOrder(bang, bieu);

        // A second run finds nothing left to correct.
        List<Map<String, Object>> afterFirst = state("SEED-VSAT-DIA-SET-%", "SEED-VSAT-TOAN-SET-001%");
        runSeed();
        assertThat(state("SEED-VSAT-DIA-SET-%", "SEED-VSAT-TOAN-SET-001%")).isEqualTo(afterFirst);
    }

    @Test
    @Transactional
    void aCorrectedOldHeadRaisesTheSubjectSourceRevisionOnceOnly() throws Exception {
        installOldHead();
        long before = totalRevision();
        long subjectBefore = subjectRevision("SEED-VSAT-DIA-SET-001");

        runSeed();

        // The four READY roots left a root-tag filter: their subject moves by four, no other subject does.
        assertThat(subjectRevision("SEED-VSAT-DIA-SET-001")).isEqualTo(subjectBefore + 4);
        assertThat(totalRevision()).isEqualTo(before + 4);

        long afterFirst = totalRevision();
        runSeed();
        assertThat(totalRevision()).isEqualTo(afterFirst);
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

    private void assertDiaSetTagging() {
        Map<String, List<String>> byTag = jdbc.query("""
                select i.code as tag, q.code as question
                from questionbank.question_tag qt
                join catalog.catalog_item i on i.id = qt.tag_item_id
                join questionbank.question q on q.id = qt.question_id
                where i.code in ('DIA_BANG_SO_LIEU', 'DIA_BIEU_DO')
                  and q.code like 'SEED-VSAT-DIA-SET-%'
                """, rs -> {
            Map<String, List<String>> map = new HashMap<>();
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("tag"), k -> new ArrayList<>())
                        .add(rs.getString("question"));
            }
            return map;
        });
        assertThat(byTag.getOrDefault("DIA_BANG_SO_LIEU", List.of()))
                .containsExactlyInAnyOrder("SEED-VSAT-DIA-SET-002", "SEED-VSAT-DIA-SET-004");
        assertThat(byTag.getOrDefault("DIA_BIEU_DO", List.of()))
                .containsExactlyInAnyOrder("SEED-VSAT-DIA-SET-001", "SEED-VSAT-DIA-SET-003");
        assertThat(jdbc.queryForObject("""
                select count(*) from questionbank.question_tag qt
                join catalog.catalog_item i on i.id = qt.tag_item_id
                join questionbank.question c on c.id = qt.question_id
                where i.code in ('DIA_BANG_SO_LIEU', 'DIA_BIEU_DO')
                  and c.parent_id is not null
                  and c.code like 'SEED-VSAT-DIA-SET-%'
                """, Integer.class)).isZero();
    }

    private static final String TAG_GROUP = "00000000-0000-7000-8100-000000000008";
    private static final List<String> DIA_ROOTS = List.of(
            "SEED-VSAT-DIA-SET-001", "SEED-VSAT-DIA-SET-002", "SEED-VSAT-DIA-SET-003", "SEED-VSAT-DIA-SET-004");

    private void runSeed() throws Exception {
        // The script keeps most temp tables until commit; a second run in this transaction needs them gone.
        jdbc.execute("drop table if exists seed_question_rows, seed_2b_tags, seed_2b_question_tags, seed_2b_knowledge, "
                + "seed_inserted_questions, seed_inserted_children");
        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_SCRIPT));
    }

    private UUID id(String code) {
        return jdbc.queryForObject("select id from questionbank.question where code = ?", UUID.class, code);
    }

    private UUID tagId(String code) {
        return jdbc.queryForObject("select id from catalog.catalog_item where group_id = ?::uuid and code = ?",
                UUID.class, TAG_GROUP, code);
    }

    private List<UUID> tags(String code) {
        return jdbc.queryForList("select tag_item_id from questionbank.question_tag where question_id = ? order by 1",
                UUID.class, id(code));
    }

    private List<UUID> snapshotTags(String code, int versionNo) {
        return jdbc.queryForList("""
                select e::uuid from questionbank.question_version v,
                     jsonb_array_elements_text(v.snapshot -> 'tagItemIds') e
                where v.question_id = ? and v.version_no = ? order by 1
                """, UUID.class, id(code), versionNo);
    }

    /** Sets the tags of a question in both representations: question_tag and the version-1 snapshot. */
    private void setTags(UUID question, String... codes) {
        String in = codes.length == 0 ? "null" : String.join(",", java.util.Collections.nCopies(codes.length, "?"));
        jdbc.update("delete from questionbank.question_tag where question_id = ?", question);
        List<Object> insert = new ArrayList<>(List.of(question));
        insert.addAll(List.of(codes));
        jdbc.update("""
                insert into questionbank.question_tag (question_id, tag_item_id)
                select ?, id from catalog.catalog_item
                where group_id = '%s' and code in (%s)
                """.formatted(TAG_GROUP, in), insert.toArray());
        List<Object> snapshot = new ArrayList<>(List.of((Object[]) codes));
        snapshot.add(question);
        jdbc.update("""
                update questionbank.question_version
                set snapshot = jsonb_set(snapshot, '{tagItemIds}', coalesce(
                    (select jsonb_agg(id order by id) from catalog.catalog_item
                     where group_id = '%s' and code in (%s)), '[]'::jsonb))
                where question_id = ? and version_no = 1
                """.formatted(TAG_GROUP, in), snapshot.toArray());
    }

    /** The database as 6ef80282 left it: roots with both labels, children with one each, in both representations. */
    private void installOldHead() {
        for (String root : DIA_ROOTS) {
            setTags(id(root), "DIA_BANG_SO_LIEU", "DIA_BIEU_DO");
            List<UUID> children = jdbc.queryForList(
                    "select id from questionbank.question where parent_id = ? order by position", UUID.class, id(root));
            assertThat(children).as(root).hasSize(3);
            setTags(children.get(0), "DIA_BANG_SO_LIEU");
            setTags(children.get(1), "DIA_BIEU_DO");
            setTags(children.get(2));
        }
    }

    /** An admin publishes version 2 of the root with the given tags; the projection follows the serving version. */
    private void publishVersionTwo(String code, UUID... tagIds) {
        UUID question = id(code);
        String json = "[" + java.util.Arrays.stream(tagIds).sorted().map(tag -> "\"" + tag + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
        jdbc.update("""
                insert into questionbank.question_version (question_id, version_no, snapshot, ready_at)
                select question_id, 2, jsonb_set(jsonb_set(snapshot, '{tagItemIds}', ?::jsonb), '{content}',
                                                 '"<p>edited by an admin</p>"'::jsonb), now()
                from questionbank.question_version where question_id = ? and version_no = 1
                """, json, question);
        jdbc.update("update questionbank.question set current_version_no = 2 where id = ?", question);
        jdbc.update("delete from questionbank.question_tag where question_id = ?", question);
        for (UUID tag : tagIds) {
            jdbc.update("insert into questionbank.question_tag (question_id, tag_item_id) values (?, ?)", question, tag);
        }
    }

    /** Every row, tag and version of the questions whose code matches one of the patterns. */
    private List<Map<String, Object>> state(String... codePatterns) {
        String where = String.join(" or ", java.util.Collections.nCopies(codePatterns.length, "q.code like ?"));
        return jdbc.queryForList("""
                select q.code, q.status, q.parent_id, q.current_version_no, q.working_version_no,
                       (select string_agg(t.tag_item_id::text, ',' order by t.tag_item_id)
                        from questionbank.question_tag t where t.question_id = q.id) as tags,
                       (select string_agg(v.version_no || ':' || v.snapshot::text, '|' order by v.version_no)
                        from questionbank.question_version v where v.question_id = q.id) as versions
                from questionbank.question q where %s order by q.code
                """.formatted(where), (Object[]) codePatterns);
    }

    private long totalRevision() {
        return jdbc.queryForObject("select coalesce(sum(revision), 0) from questionbank.question_source_revision",
                Long.class);
    }

    private long subjectRevision(String rootCode) {
        return jdbc.queryForObject("""
                select coalesce((select revision from questionbank.question_source_revision
                                 where subject_item_id = (select subject_item_id from questionbank.question where code = ?)), 0)
                """, Long.class, rootCode);
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
