package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import vn.unicom.thi360.questionbank.spi.AnswerFormat;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class SampleQuestionCoverageIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired QuestionSourcePort source;
    @Autowired DataSource dataSource;

    @Test
    void everyTemplateBlockHasEnoughMatchingSource() throws Exception {
        var templates = jdbc.queryForList("select code, structure::text as structure from exam.exam_template order by code");
        assertThat(templates).isNotEmpty();
        List<String> failures = new ArrayList<>();
        int[] checked = {0};
        for (var template : templates) {
            String code = (String) template.get("code");
            inspect(mapper.readTree((String) template.get("structure")), null, code, failures, checked);
        }
        assertThat(checked[0]).as("template blocks checked").isPositive();
        assertThat(failures).as("template source shortages").isEmpty();
    }

    @Test
    void sampleFormatsAndDistinctLiteraturePassagesAreAvailable() throws Exception {
        var passages = jdbc.queryForList("""
                select v.snapshot::text from questionbank.question q
                join questionbank.question_version v on v.question_id=q.id and v.version_no=q.current_version_no
                where q.parent_id is null and q.code like 'SEED-2B-LITERATURE-literature_sp2e-%'
                order by q.code
                """, String.class);
        assertThat(passages).hasSize(4);
        Set<String> texts = new HashSet<>();
        List<Integer> lengths = new ArrayList<>();
        for (String passage : passages) {
            JsonNode body = mapper.readTree(passage).path("body");
            texts.add(body.path("passage").asText());
            lengths.add(body.path("children").size());
        }
        assertThat(texts).hasSize(4);
        assertThat(lengths).containsExactlyInAnyOrder(6, 7, 8, 9);
        UUID chemistry = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'HOA'
                """, UUID.class);
        for (AnswerFormat format : List.of(AnswerFormat.INTEGER, AnswerFormat.DECIMAL, AnswerFormat.CHEM_FORMULA)) {
            var filter = new QuestionSourcePort.Filter(chemistry, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                    QuestionType.SHORT_ANSWER, null, null, List.of(), List.of(), null,
                    null, List.of(format), List.of());
            assertThat(source.findCandidates(filter, 100)).as(format.name()).isNotEmpty();
        }
        for (var shape : List.of(new QuestionSourcePort.ItemShape(null, 4, 6),
                                 new QuestionSourcePort.ItemShape(null, 3, 5))) {
            var filter = new QuestionSourcePort.Filter(chemistry, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                    QuestionType.MATCHING, null, null, List.of(), List.of(), null, shape, List.of(), List.of());
            assertThat(source.findCandidates(filter, 100)).as(shape.toString()).isNotEmpty();
        }
        UUID math = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'TOAN'
                """, UUID.class);
        List<UUID> mapids = jdbc.queryForList("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000009'
                  and code in ('0D1-1-1', '0D1-1-2') order by code
                """, UUID.class);
        assertThat(mapids).hasSize(2);
        var positioned = new QuestionSourcePort.Filter(math, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.QUESTION_SET, null, QuestionType.SINGLE_CHOICE, List.of(), List.of(), null, null,
                List.of(), List.of(new QuestionSourcePort.ChildCondition(1, 1, List.of(mapids.get(0))),
                                   new QuestionSourcePort.ChildCondition(2, 2, List.of(mapids.get(1)))));
        assertThat(source.findCandidates(positioned, 100)).anyMatch(c -> c.code().startsWith("SEED-2B-MAPID-"));
        assertThat(jdbc.queryForList("""
                select v.snapshot #>> '{body,shuffleOptions}'
                from questionbank.question q join questionbank.question_version v
                  on v.question_id=q.id and v.version_no=q.current_version_no
                where q.code in ('SEED-2B-ORDER-SINGLE_CHOICE', 'SEED-2B-ORDER-MULTIPLE_CHOICE')
                """, String.class)).containsExactlyInAnyOrder("false", "false");
    }

    @Test
    void technologyAndLegacyScienceHaveExactPointSources() {
        for (String code : List.of("TECH_INDUSTRY", "TECH_AGRICULTURE", "LY", "HOA", "SINH")) {
            UUID subject = jdbc.queryForObject("""
                    select id from catalog.catalog_item
                    where group_id = '00000000-0000-7000-8100-000000000002' and code = ?
                    """, UUID.class, code);
            boolean technology = code.startsWith("TECH_");
            var filter = new QuestionSourcePort.Filter(subject, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                    technology ? QuestionType.SHORT_ANSWER : QuestionType.TRUE_FALSE, null, null, List.of(), List.of(),
                    new BigDecimal(technology ? "0.25" : "0.8"),
                    technology ? null : new QuestionSourcePort.ItemShape(4, null, null), List.of(), List.of());
            assertThat(source.findCandidates(filter, 100)).as(code).hasSizeGreaterThanOrEqualTo(technology ? 4 : 5);
        }
    }

    @Test
    @Transactional
    void taggedSamplesAreQueryableWhenCatalogCodesExist() {
        List<String> codes = List.of("ANH_PHAT_AM", "ANH_TRONG_AM", "ANH_DONG_NGHIA", "ANH_TRAI_NGHIA",
                "VAN_THO", "ANH_WORD_FORMATION", "DIA_CAU_KY_NANG", "TOAN_DU_LIEU_TONG_HOP",
                "VAN_KHOA_HOC", "VAN_THONG_TIN");
        for (String code : codes) {
            jdbc.update("""
                    insert into catalog.catalog_item
                        (id, group_id, code, code_normalized, value, name_vi, path)
                    select md5('be10-test-' || ?)::uuid, g.id, ?, lower(?),
                           (select coalesce(max(value), 0) + 1 from catalog.catalog_item where group_id=g.id),
                           ?, '/' || md5('be10-test-' || ?)::uuid::text || '/'
                    from catalog.catalog_group g where g.code = 'QUESTION_TAG'
                    on conflict (group_id, code_normalized) do nothing
                    """, code, code, code, code, code);
        }
        var connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/devdata/R__seed_questionbank_dev_data.sql"));
        UUID english = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'ANH'
                """, UUID.class);
        for (String code : codes.subList(0, 4)) {
            var filter = new QuestionSourcePort.Filter(english, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                    QuestionType.SINGLE_CHOICE, null, null, List.of(code), List.of(), null, null, List.of(), List.of());
            assertThat(source.findCandidates(filter, 100)).as(code)
                    .anyMatch(c -> c.code().startsWith("SEED-2B-TAGGED-"));
        }
        var wordFormation = new QuestionSourcePort.Filter(english, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.CLOZE, null, null, List.of("ANH_WORD_FORMATION"), List.of(), null, null, List.of(), List.of());
        assertThat(source.findCandidates(wordFormation, 100)).anyMatch(c -> c.units().size() == 4);
        UUID literature = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'VAN'
                """, UUID.class);
        var poetry = new QuestionSourcePort.Filter(literature, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.QUESTION_SET, null, null, List.of("VAN_THO"), List.of(), null, null, List.of(), List.of());
        assertThat(source.findCandidates(poetry, 100).stream().filter(c -> c.units().size() == 5)).hasSizeGreaterThanOrEqualTo(3);
        Set<String> sptPassages = new HashSet<>();
        for (String tag : List.of("VAN_KHOA_HOC", "VAN_THONG_TIN")) {
            var spt = new QuestionSourcePort.Filter(literature, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                    QuestionType.QUESTION_SET, null, QuestionType.SINGLE_CHOICE, List.of(tag), List.of(),
                    new BigDecimal("0.3"), null, List.of(), List.of());
            var candidates = source.findCandidates(spt, 100);
            assertThat(candidates).hasSizeGreaterThanOrEqualTo(3);
            for (Candidate candidate : candidates) {
                String passage = jdbc.queryForObject("""
                        select v.snapshot #>> '{body,passage}' from questionbank.question q
                        join questionbank.question_version v on v.question_id=q.id and v.version_no=q.current_version_no
                        where q.id = ?
                        """, String.class, candidate.questionId());
                assertThat(sptPassages.add(passage)).as(candidate.code()).isTrue();
            }
        }
        UUID geography = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'DIA'
                """, UUID.class);
        var geoSkill = new QuestionSourcePort.Filter(geography, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.TRUE_FALSE, null, null, List.of("DIA_CAU_KY_NANG"), List.of(), null,
                new QuestionSourcePort.ItemShape(4, null, null), List.of(), List.of());
        assertThat(source.findCandidates(geoSkill, 100)).isNotEmpty();
        UUID math = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = 'TOAN'
                """, UUID.class);
        var synthesis = new QuestionSourcePort.Filter(math, List.of(), QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.SINGLE_CHOICE, null, null, List.of("TOAN_DU_LIEU_TONG_HOP"), List.of(), null, null,
                List.of(), List.of());
        assertThat(source.findCandidates(synthesis, 100)).isNotEmpty();
        UUID edited = jdbc.queryForObject("""
                select q.id from questionbank.question q
                join questionbank.question_tag t on t.question_id = q.id
                join catalog.catalog_item tag on tag.id = t.tag_item_id
                where q.code like 'SEED-2B-TAGGED-english_pronunciation-%' and tag.code = 'ANH_PHAT_AM'
                order by q.code limit 1
                """, UUID.class);
        jdbc.update("delete from questionbank.question_tag where question_id = ?", edited);
        jdbc.update("""
                insert into questionbank.question_version (question_id, version_no, snapshot, ready_at)
                select question_id, 2, jsonb_set(snapshot, '{tagItemIds}', '[]'::jsonb), now()
                from questionbank.question_version where question_id = ? and version_no = 1
                """, edited);
        jdbc.update("update questionbank.question set current_version_no = 2, revision = revision + 1 where id = ?", edited);
        jdbc.update("""
                update questionbank.question_source_revision set revision = revision + 1
                where subject_item_id = (select subject_item_id from questionbank.question where id = ?)
                """, edited);
        var versions = jdbc.queryForList("""
                select version_no, snapshot::text from questionbank.question_version
                where question_id = ? order by version_no
                """, edited);
        var question = jdbc.queryForMap("select current_version_no, revision from questionbank.question where id = ?", edited);
        var revisions = jdbc.queryForList("select * from questionbank.question_source_revision order by subject_item_id");
        jdbc.execute("drop table seed_question_rows, seed_2b_tags, seed_2b_question_tags, seed_2b_knowledge, "
                + "seed_inserted_questions, seed_inserted_children");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/devdata/R__seed_questionbank_dev_data.sql"));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_tag where question_id = ?",
                Integer.class, edited)).isZero();
        assertThat(jdbc.queryForList("""
                select version_no, snapshot::text from questionbank.question_version
                where question_id = ? order by version_no
                """, edited)).isEqualTo(versions);
        assertThat(jdbc.queryForMap("select current_version_no, revision from questionbank.question where id = ?", edited))
                .isEqualTo(question);
        assertThat(jdbc.queryForList("select * from questionbank.question_source_revision order by subject_item_id"))
                .isEqualTo(revisions);
    }

    @Test
    @Transactional
    void incompleteMapidPrerequisitesEmitNoPartialSet() {
        String sample = "select id from questionbank.question where code ~ '^SEED-2B-MAPID-[1-3](-[0-2])?$'";
        jdbc.update("delete from questionbank.question_knowledge where question_id in (" + sample + ")");
        jdbc.update("delete from questionbank.question_tag where question_id in (" + sample + ")");
        jdbc.update("delete from questionbank.question_version where question_id in (" + sample + ")");
        jdbc.update("delete from questionbank.question where parent_id in (" + sample + ")");
        jdbc.update("delete from questionbank.question where id in (" + sample + ")");
        jdbc.update("""
                update catalog.catalog_item set code = 'BE10-MISSING-MAPID', code_normalized = 'be10-missing-mapid'
                where group_id = '00000000-0000-7000-8100-000000000009' and code = '0D1-1-3'
                """);
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/devdata/R__seed_questionbank_dev_data.sql"));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where id in (" + sample + ")",
                Integer.class)).isZero();
    }

    @Test
    void checkerRejectsEmptyRemaindersAndInheritedFilters() throws Exception {
        List<String> failures = new ArrayList<>();
        int[] checked = {0};
        JsonNode choice = mapper.readTree("""
                {"kind":"SUBJECT_CHOICE","countsByPick":[17,17,16],"sections":[{
                  "subjectCode":"LY","blocks":[
                    {"name":"fixed","questionType":"SINGLE_CHOICE","questionCount":6},
                    {"name":"remainder","questionType":"SINGLE_CHOICE","questionCount":null,
                     "requiredPointsPerQuestion":987}]}]}
                """);
        inspect(choice, null, "choice", failures, checked);
        assertThat(failures).anyMatch(s -> s.contains("remainder") && s.contains("11 units"));
        failures.clear();
        JsonNode ordinary = mapper.readTree("""
                {"subjectCode":"LY","questionCount":17,"blocks":[
                  {"name":"fixed","questionType":"SINGLE_CHOICE","questionCount":6},
                  {"name":"remainder","questionType":"SINGLE_CHOICE","questionCount":null}]}
                """);
        inspect(ordinary, null, "ordinary", failures, checked);
        assertThat(failures).isEmpty();
        JsonNode scoped = mapper.readTree("""
                {"subjectCode":"LY","questionCount":1,
                 "knowledgeScope":{"nodeIds":["00000000-0000-0000-0000-000000000001"]},
                 "blocks":[{"name":"scoped","questionType":"SINGLE_CHOICE","questionCount":1}]}
                """);
        inspect(scoped, null, "scoped", failures, checked);
        assertThat(failures).anyMatch(s -> s.contains("scoped") && s.contains("0 units"));
        failures.clear();
        JsonNode quota = mapper.readTree("""
                {"subjectCode":"LY","questionCount":1,
                 "answerQuotas":{"items":[{"answerType":"ESSAY","value":1}]},
                 "blocks":[{"name":"quota","questionType":null,"questionCount":1,
                            "requiredPointsPerQuestion":0.25}]}
                """);
        inspect(quota, null, "quota", failures, checked);
        assertThat(failures).anyMatch(s -> s.contains("quota") && s.contains("0 units"));
        failures.clear();
        JsonNode slot = mapper.readTree("""
                {"subjectCode":"LY","questionCount":3,
                 "knowledgeScope":{"nodeIds":["00000000-0000-0000-0000-000000000001"]},
                 "blocks":[{"name":"slot","questionType":"QUESTION_SET","setCount":1,"setSize":3,
                            "slots":[{}]}]}
                """);
        inspect(slot, null, "slot", failures, checked);
        assertThat(failures).anyMatch(s -> s.contains("slots lack distinct"));
    }

    private void inspect(JsonNode node, String subject, String template, List<String> failures, int[] checked) {
        inspect(node, subject, null, template, failures, checked);
    }

    private void inspect(JsonNode node, String subject, Integer choiceCount, String template,
                         List<String> failures, int[] checked) {
        if (node.isArray()) {
            node.forEach(child -> inspect(child, subject, choiceCount, template, failures, checked));
        } else if (node.isObject()) {
            String currentSubject = text(node, "subjectCode", subject);
            Integer currentChoiceCount = choiceCount;
            if ("SUBJECT_CHOICE".equals(text(node, "kind", null))) {
                currentChoiceCount = null;
                for (JsonNode count : node.path("countsByPick")) {
                    currentChoiceCount = currentChoiceCount == null ? count.asInt()
                            : Math.max(currentChoiceCount, count.asInt());
                }
            }
            JsonNode blocks = node.path("blocks");
            if (blocks.isArray()) {
                int fixed = 0;
                for (JsonNode block : blocks) {
                    if (block.path("omitted").asBoolean(false)) continue;
                    String type = text(block, "questionType", null);
                    boolean set = "QUESTION_SET".equals(type) || "CLOZE".equals(type);
                    if (set) fixed += block.path("setCount").asInt(0) * block.path("setSize").asInt(0);
                    else if (block.hasNonNull("questionCount")) fixed += block.path("questionCount").asInt();
                }
                Integer count = currentChoiceCount != null ? currentChoiceCount : integer(node, "questionCount");
                for (JsonNode block : blocks) {
                    if (block.path("omitted").asBoolean(false)) continue;
                    checked[0]++;
                    String type = text(block, "questionType", null);
                    boolean set = "QUESTION_SET".equals(type) || "CLOZE".equals(type);
                    Integer units = set ? block.path("setCount").asInt(0) * block.path("setSize").asInt(0)
                            : integer(block, "questionCount");
                    if (units == null && count == null) {
                        failures.add(template + " / " + currentSubject + ": unresolved remainder");
                    } else {
                        int needed = units == null ? count - fixed : units;
                        if (needed < 0) failures.add(template + " / " + currentSubject + ": negative remainder");
                        else checkBlock(template, currentSubject, node, block, needed, failures);
                    }
                }
            }
            final Integer inheritedChoiceCount = currentChoiceCount;
            node.fields().forEachRemaining(field -> {
                if (!field.getKey().equals("blocks")) {
                    inspect(field.getValue(), currentSubject, inheritedChoiceCount, template, failures, checked);
                }
            });
        }
    }

    private void checkBlock(String template, String subject, JsonNode section, JsonNode block,
                            int neededUnits, List<String> failures) {
        String label = template + " / " + subject + " / " + text(block, "name", "unnamed");
        if (subject == null) {
            failures.add(label + ": missing subject");
            return;
        }
        var subjectIds = jdbc.queryForList("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = ? and status = 1
                """, UUID.class, subject);
        if (subjectIds.size() != 1) {
            failures.add(label + ": subject not found");
            return;
        }
        JsonNode scope = block.path("knowledgeScope").isObject()
                ? block.path("knowledgeScope") : section.path("knowledgeScope");
        QuestionSourcePort.Filter filter = filter(subjectIds.get(0), section, block, strings(block.path("tagCodes")), scope);
        List<Candidate> candidates = source.findCandidates(filter, 100_000);
        int setSize = block.path("setSize").asInt(0);
        if (setSize > 0) candidates = candidates.stream().filter(c -> c.units().size() == setSize).toList();
        int neededSets = block.path("setCount").asInt(0);
        long units = candidates.stream().mapToLong(c -> c.units().size()).sum();
        if (candidates.size() < neededSets || units < neededUnits) {
            failures.add(label + ": need " + neededSets + " sets / " + neededUnits + " units, found "
                    + candidates.size() + " candidates / " + units + " units");
        }
        if (block.path("slots").isArray() && !block.path("slots").isEmpty()) {
            Set<UUID> eligible = new HashSet<>();
            candidates.forEach(c -> eligible.add(c.questionId()));
            List<Set<UUID>> slots = new ArrayList<>();
            for (JsonNode slot : block.path("slots")) {
                Set<UUID> matching = new HashSet<>();
                JsonNode slotScope = slot.path("knowledgeScope");
                source.findCandidates(filter(subjectIds.get(0), section, block, strings(slot.path("tagCodes")),
                                slotScope.isObject() ? slotScope : scope), 100_000)
                        .stream().filter(c -> eligible.contains(c.questionId()))
                        .forEach(c -> matching.add(c.questionId()));
                slots.add(matching);
            }
            if (!assignSlots(slots, 0, new HashSet<>())) failures.add(label + ": slots lack distinct matching sets");
        }
    }

    private QuestionSourcePort.Filter filter(UUID subject, JsonNode section, JsonNode block,
                                             List<String> tags, JsonNode scope) {
        JsonNode shape = block.path("itemShape");
        QuestionSourcePort.ItemShape itemShape = shape.isObject()
                ? new QuestionSourcePort.ItemShape(integer(shape, "statements"), integer(shape, "leftItems"),
                        integer(shape, "rightItems")) : null;
        List<AnswerFormat> formats = strings(block.path("answerFormats")).stream().map(AnswerFormat::valueOf).toList();
        List<QuestionSourcePort.ChildCondition> children = new ArrayList<>();
        for (JsonNode condition : block.path("childConditions")) {
            children.add(new QuestionSourcePort.ChildCondition(condition.path("from").asInt(), condition.path("to").asInt(),
                    strings(condition.path("nodeIds")).stream().map(UUID::fromString).toList()));
        }
        List<List<UUID>> knowledge = new ArrayList<>();
        for (String id : strings(scope.path("nodeIds"))) {
            knowledge.add(jdbc.queryForList("select id from catalog.catalog_item where path like ?", UUID.class,
                    "%/" + id + "/%"));
        }
        String type = text(block, "questionType", null);
        String childType = text(block, "childAnswerType", null);
        BigDecimal points = block.hasNonNull("requiredPointsPerQuestion")
                ? block.get("requiredPointsPerQuestion").decimalValue() : null;
        List<QuestionType> answerTypes = new ArrayList<>();
        if (type == null) {
            for (JsonNode quota : section.path("answerQuotas").path("items")) {
                if (quota.hasNonNull("answerType")) answerTypes.add(QuestionType.valueOf(quota.get("answerType").asText()));
            }
        }
        QuestionSourcePort.Match match = "ALL".equals(text(scope, "match", null))
                ? QuestionSourcePort.Match.ALL : QuestionSourcePort.Match.ANY;
        return new QuestionSourcePort.Filter(subject, knowledge, match, answerTypes,
                type == null ? null : QuestionType.valueOf(type), text(block, "taskTypeCode", null),
                childType == null ? null : QuestionType.valueOf(childType), tags,
                strings(scope.path("competencyCodes")), points, itemShape, formats, children);
    }

    private static boolean assignSlots(List<Set<UUID>> slots, int index, Set<UUID> used) {
        if (index == slots.size()) return true;
        for (UUID id : slots.get(index)) {
            if (used.add(id)) {
                if (assignSlots(slots, index + 1, used)) return true;
                used.remove(id);
            }
        }
        return false;
    }

    private static List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) node.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }
}
