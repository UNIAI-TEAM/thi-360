package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;

class ExamTemplateIntegrityIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void exactlyOneDefaultForEachTemplateKey() {
        var invalid = jdbc.queryForList("""
                select kind_code, scope, part_code, subject_code, build_mode,
                       count(*) filter (where is_default) as defaults
                from exam.exam_template
                group by kind_code, scope, part_code, subject_code, build_mode
                having count(*) filter (where is_default) <> 1
                """);
        assertThat(jdbc.queryForObject("select count(*) from exam.exam_template", Integer.class))
                .isPositive();
        assertThat(invalid).as("template keys without exactly one default").isEmpty();
    }

    @Test
    void allTemplateTagsExistAndRemovedTaskTypeIsAbsent() throws Exception {
        Set<String> tags = new HashSet<>(jdbc.queryForList("""
                select i.code from catalog.catalog_item i
                join catalog.catalog_group g on g.id = i.group_id
                where g.code = 'QUESTION_TAG'
                """, String.class));
        assertThat(tags).as("seeded question tags").isNotEmpty();
        var templates = jdbc.queryForList("select code, structure::text as document from exam.exam_template");
        assertThat(templates).isNotEmpty();
        for (var template : templates) {
            inspect(mapper.readTree((String) template.get("document")), (String) template.get("code"), tags);
        }
    }

    @Test
    void partAndSectionIdsArePresentAndUniqueWithinEachTemplate() throws Exception {
        var templates = jdbc.queryForList("select code, structure::text as document from exam.exam_template");
        assertThat(templates).isNotEmpty();
        for (var template : templates) {
            String code = (String) template.get("code");
            JsonNode document = mapper.readTree((String) template.get("document"));
            assertThat(document.path("parts").isArray()).as("%s parts", code).isTrue();
            assertIds(document, code, new HashSet<>());
        }
    }

    private void assertIds(JsonNode node, String code, Set<String> ids) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if (Set.of("parts", "sections", "subjectPool").contains(field.getKey())
                        && field.getValue().isArray()) {
                    for (JsonNode child : field.getValue()) {
                        assertThat(child.path("id").isTextual()).as("%s %s id", code, field.getKey()).isTrue();
                        String id = child.path("id").asText();
                        assertThat(id).as("%s %s id", code, field.getKey()).isNotBlank();
                        assertThat(ids.add(id)).as("%s duplicate part/section id %s", code, id).isTrue();
                    }
                }
                assertIds(field.getValue(), code, ids);
            });
        } else if (node.isArray()) {
            node.forEach(child -> assertIds(child, code, ids));
        }
    }

    private void inspect(JsonNode node, String code, Set<String> tags) {
        if (node.isObject()) {
            assertThat(node.has("taskTypeCode")).as("%s contains removed taskTypeCode", code).isFalse();
            JsonNode referenced = node.get("tagCodes");
            if (referenced != null && !referenced.isNull()) {
                assertThat(referenced.isArray()).as("%s tagCodes", code).isTrue();
                referenced.forEach(tag -> assertThat(tags).as("%s tag %s", code, tag.asText())
                        .contains(tag.asText()));
            }
            node.elements().forEachRemaining(child -> inspect(child, code, tags));
        } else if (node.isArray()) {
            node.forEach(child -> inspect(child, code, tags));
        }
    }
}
