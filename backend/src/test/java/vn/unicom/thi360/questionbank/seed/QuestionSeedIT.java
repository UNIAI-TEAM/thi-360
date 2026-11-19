package vn.unicom.thi360.questionbank.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
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
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator;
import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.domain.QuestionInput;
import vn.unicom.thi360.questionbank.domain.QuestionSearchNormalizer;
import vn.unicom.thi360.questionbank.domain.QuestionSnapshot;
import vn.unicom.thi360.questionbank.domain.QuestionUnits;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class QuestionSeedIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired QuestionAssetResolver assetResolver;

    @Test
    void hsaCellsAtLeastDouble() {
        List<Integer> cells = jdbc.queryForList("""
            select count(*)::int from questionbank.question q
            join catalog.catalog_item s on s.id = q.subject_item_id
            where q.code like 'SEED-MATH-%' and q.parent_id is null
              and q.type in ('SINGLE_CHOICE', 'SHORT_ANSWER') and s.code = 'TOAN'
              and q.status = 'READY'
            group by q.grade_item_id, q.level_item_id, q.type
            """, Integer.class);
        assertThat(cells).hasSize(24).allMatch(count -> count >= 12);
        for (String subject : List.of("LY", "HOA", "SINH", "SU", "DIA")) {
            Integer count = jdbc.queryForObject("""
                select count(*) from questionbank.question q join catalog.catalog_item s on s.id=q.subject_item_id
                where q.code like 'SEED-%' and q.status='READY' and q.parent_id is null and s.code=? and q.points=1
                """, Integer.class, subject);
            assertThat(count).as(subject).isGreaterThanOrEqualTo(34);
        }
        Integer bcaEnglish = jdbc.queryForObject("""
            select count(*) from questionbank.question q join catalog.catalog_item s on s.id=q.subject_item_id
            where q.code like 'SEED-%' and q.status='READY' and s.code='ANH' and q.points=0.75
            """, Integer.class);
        assertThat(bcaEnglish).isGreaterThanOrEqualTo(40);
    }

    @Test
    void everySeedQuestionValid() throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select q.id, q.code, q.type, q.status, q.parent_id, q.search_text, q.content_text,
                   q.current_version_no, v.snapshot::text as snapshot, r.revision as source_revision
            from questionbank.question q
            join questionbank.question_version v on v.question_id=q.id
                and v.version_no=coalesce(q.working_version_no, q.current_version_no)
            left join questionbank.question_source_revision r on r.subject_item_id=q.subject_item_id
            where q.code like 'SEED-%' order by q.code
            """);
        assertThat(rows).isNotEmpty();
        assertThat(rows).hasSize(jdbc.queryForObject(
            "select count(*) from questionbank.question where code like 'SEED-%'", Integer.class));
        assertThat(rows.stream().map(row -> row.get("type")).distinct()).hasSize(8);
        assertThat(rows.stream().map(row -> row.get("status")).distinct())
            .containsExactlyInAnyOrder("DRAFT", "READY", "RETIRED");
        QuestionAssetResolver assets = assetResolver;
        QuestionHtmlSanitizer sanitizer = new QuestionHtmlSanitizer(assets);
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("code");
            JsonNode json = mapper.readTree((String) row.get("snapshot"));
            assertOptionOrdering(json, code);
            QuestionSnapshot snapshot = mapper.treeToValue(json, QuestionSnapshot.class);
            QuestionBody body = snapshot.body();
            assertThat(body).as(code).isNotNull();
            assertCleanHtml(json, sanitizer);
            assertThat(row.get("search_text")).as(code).isEqualTo(
                QuestionSearchNormalizer.searchText(code, snapshot.content(), body));
            assertThat(row.get("content_text")).as(code).isEqualTo(
                QuestionSearchNormalizer.contentText(snapshot.content(), body));
            assertThat((Number) row.get("source_revision")).as(code).isNotNull();
            QuestionInput input = new QuestionInput(QuestionType.valueOf((String) row.get("type")), body,
                snapshot.points(), snapshot.content(), snapshot.solution(), snapshot.commonMistakes(), snapshot.attachmentIds());
            assertThat(QuestionBodyValidator.validateSave(input, assets)).as(code).isEmpty();
            if ("READY".equals(row.get("status"))) {
                assertThat(QuestionBodyValidator.validateReady(input,
                    new QuestionBodyValidator.ClassificationRequirement(snapshot.subjectItemId() != null,
                        snapshot.gradeItemId() != null, snapshot.levelItemId() != null), false, assets).errors())
                    .as(code).isEmpty();
            }
            // Child rows are not addressable directly by the question API; the parent includes them.
            if (row.get("parent_id") == null) {
                String response = mockMvc.perform(get("/api/v1/admin/questions/{id}", row.get("id"))
                    .with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
                assertThat(mapper.treeToValue(mapper.readTree(response).get("body"), QuestionBody.class))
                    .as(code).isEqualTo(body);
                if (body instanceof QuestionBody.QuestionSetBody set) {
                    var units = QuestionUnits.units((UUID) row.get("id"),
                        ((Number) row.get("current_version_no")).intValue(), body);
                    assertThat(units).hasSize(set.children().size());
                    for (int i = 0; i < set.children().size(); i++) {
                        var child = set.children().get(i);
                        assertThat(child.id()).as(code).isNotNull();
                        assertThat(child.gradeItemId() != null ? child.gradeItemId() : snapshot.gradeItemId()).as(code).isNotNull();
                        assertThat(child.levelItemId() != null ? child.levelItemId() : snapshot.levelItemId()).as(code).isNotNull();
                        assertThat(units.get(i).questionId()).isEqualTo(child.id());
                        assertThat(units.get(i).versionNo()).isEqualTo(((Number) row.get("current_version_no")).intValue());
                        assertThat(units.get(i).answerType()).isEqualTo(child.type());
                    }
                }
            }
        }
    }

    private static void assertOptionOrdering(JsonNode node, String code) {
        if (node.isObject() && node.has("shuffleOptions")) {
            assertThat(code).startsWith("SEED-2B-ORDER-");
            assertThat(node.path("type").asText()).isIn("SINGLE_CHOICE", "MULTIPLE_CHOICE");
            assertThat(node.path("shuffleOptions").isBoolean()).isTrue();
            assertThat(node.path("shuffleOptions").booleanValue()).isFalse();
        }
        node.elements().forEachRemaining(child -> assertOptionOrdering(child, code));
    }

    private static void assertCleanHtml(JsonNode node, QuestionHtmlSanitizer sanitizer) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (List.of("content", "passage", "solution", "gradingGuide", "sampleAnswer").contains(entry.getKey())
                    && entry.getValue().isTextual()) {
                    String html = entry.getValue().textValue();
                    assertThat(sanitizer.clean(html).html()).isEqualTo(html);
                    assertThat(sanitizer.clean(html).violations()).isEmpty();
                }
                assertCleanHtml(entry.getValue(), sanitizer);
            });
        } else if (node.isArray()) {
            node.forEach(child -> assertCleanHtml(child, sanitizer));
        }
    }

    @Test
    @Transactional
    void seedRerunNoDuplicates() throws Exception {
        Integer questions = jdbc.queryForObject("select count(*) from questionbank.question where code like 'SEED-%'", Integer.class);
        Integer versions = jdbc.queryForObject("""
            select count(*) from questionbank.question_version v join questionbank.question q on q.id=v.question_id
            where q.code like 'SEED-%'
            """, Integer.class);
        List<Map<String, Object>> revisions = jdbc.queryForList("select * from questionbank.question_source_revision order by subject_item_id");
        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/devdata/R__seed_questionbank_dev_data.sql"));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where code like 'SEED-%'", Integer.class)).isEqualTo(questions);
        assertThat(jdbc.queryForObject("""
            select count(*) from questionbank.question_version v join questionbank.question q on q.id=v.question_id
            where q.code like 'SEED-%'
            """, Integer.class)).isEqualTo(versions);
        assertThat(jdbc.queryForList("select * from questionbank.question_source_revision order by subject_item_id")).isEqualTo(revisions);
    }
}
