package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * F6 (UNI-2686): GET /versions workingCopy.unpublishedPaths must list the same path ids as the check when a part
 * takes its options from the combination catalog (codeSource), so the publish dialog is not empty.
 */
@Transactional
class ExamUnpublishedCombinationPathsIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void qdaFullListsTheCheckPaths() throws Exception {
        String base = create("QDA", "FULL", null, null, "STANDARD");
        assertThat(unpublishedIds(base)).isEqualTo(checkIds(base)).hasSize(5);
        JsonNode versions = json(get(base + "/versions").with(asUser(Permissions.EXAM_VIEW)), 200);
        String partId = null;
        JsonNode qd01 = null;
        for (JsonNode path : versions.path("workingCopy").path("unpublishedPaths")) {
            for (JsonNode choice : path.path("choices")) {
                if ("QD01".equals(choice.path("code").asText())) {
                    qd01 = choice;
                    partId = choice.path("partId").asText();
                }
            }
        }
        assertThat(qd01).as("QD01 choice in the unpublished paths").isNotNull();
        String name = jdbc.queryForObject("""
                select name_vi from catalog.catalog_item where code_normalized = 'qd01'
                and group_id = '00000000-0000-7000-8100-000000000007'
                """, String.class);
        assertThat(partId).isNotBlank();
        assertThat(qd01.path("optionId").asText()).isEqualTo(partId + ":QD01");
        assertThat(qd01.path("kind").asText()).isEqualTo("COMBINATION");
        assertThat(qd01.path("code").asText()).isEqualTo("QD01");
        assertThat(qd01.path("name").asText()).isEqualTo(name).isNotBlank();
    }

    @Test
    void bcaFullListsTheCheckPaths() throws Exception {
        String base = create("BCA", "FULL", null, null, "STANDARD");
        assertThat(unpublishedIds(base)).isEqualTo(checkIds(base));
    }

    @Test
    void publishingOnePathLeavesTheOtherFour() throws Exception {
        String base = create("QDA", "FULL", null, null, "STANDARD");
        JsonNode check = check(base);
        List<String> all = ids(check);
        assertThat(all).hasSize(5);
        String published = all.getFirst();
        assertThat(pathResult(check, published)).as("path result of %s in %s", published, check).isEqualTo("VALID");
        JsonNode exam = json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200);
        var body = mapper.createObjectNode().put("expectedRevision", exam.path("revision").asLong());
        body.putArray("pathIds").add(published);
        json(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 200);
        assertThat(unpublishedIds(base)).isEqualTo(all.subList(1, 5));
    }

    @Test
    void everyActiveDefaultTemplateWithACodeSourcePartListsTheCheckPaths() throws Exception {
        List<Map<String, Object>> templates = jdbc.queryForList("""
                select code, kind_code, scope, part_code, subject_code, build_mode from exam.exam_template
                where active and is_default and structure::text like '%"codeSource"%'
                order by code
                """);
        assertThat(templates).as("default templates with a codeSource part").isNotEmpty();
        List<String> covered = new ArrayList<>();
        for (Map<String, Object> template : templates) {
            String code = (String) template.get("code");
            String base = create((String) template.get("kind_code"), (String) template.get("scope"),
                    (String) template.get("part_code"), (String) template.get("subject_code"),
                    (String) template.get("build_mode"));
            assertThat(json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200).path("templateCode").asText())
                    .as("template of the created exam").isEqualTo(code);
            List<String> expected = checkIds(base);
            assertThat(expected).as("check paths of %s", code).isNotEmpty();
            assertThat(unpublishedIds(base)).as("unpublished paths of %s", code).isEqualTo(expected);
            covered.add(code);
        }
        System.out.println("F6 templates with codeSource: " + covered);
    }

    @Test
    void overLimitLivePathProductListsNoPublishablePaths() throws Exception {
        String base = create("QDA", "FULL", null, null, "STANDARD");
        JsonNode before = json(get(base + "/structure").with(asUser(Permissions.EXAM_VIEW)), 200);
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) before.path("parts").deepCopy();
        // 7 x 6 stored choices x the 5 catalog combinations of the code-sourced part = 210 paths, just over the
        // default 200. The structure validator counts only stored options (42 <= 200), so the PUT is accepted.
        for (int p = 0; p < 2; p++) {
            var extra = parts.addObject().put("id", "extra" + p).put("name", "Extra " + p);
            extra.putObject("participation").put("mode", "CHOICE").put("choose", 1);
            var options = extra.putArray("options");
            for (int i = 1; i <= (p == 0 ? 7 : 6); i++) {
                options.addObject().put("id", "x" + p + "-" + i).put("kind", "BRANCH").put("code", "X" + p + i);
            }
        }
        var request = mapper.createObjectNode().put("expectedRevision", before.path("revision").asLong());
        request.set("parts", parts);
        json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base + "/structure")
                .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()), 200);
        JsonNode check = check(base);
        // The solver reports PATH_LIMIT_EXCEEDED as an uncertain OVERLAP issue and lists no paths.
        assertThat(check.toString()).contains("OVERLAP_UNCONFIRMED");
        assertThat(ids(check)).isEmpty();
        assertThat(unpublishedIds(base)).isEmpty();
    }

    private String create(String kind, String scope, String partCode, String subjectCode, String buildMode)
            throws Exception {
        var request = mapper.createObjectNode().put("kind", kind).put("scope", scope).put("buildMode", buildMode)
                .put("name", "F6 paths").put("code", "F6-" + UUID.randomUUID().toString().substring(0, 8))
                .put("durationMinutes", 60);
        if (partCode != null) request.put("partCode", partCode);
        if (subjectCode != null) request.put("subjectCode", subjectCode);
        JsonNode exam = json(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()), 201);
        return "/api/v1/admin/exams/" + exam.path("id").asText();
    }

    private JsonNode check(String base) throws Exception {
        return json(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
    }

    private List<String> checkIds(String base) throws Exception {
        return ids(check(base));
    }

    private List<String> unpublishedIds(String base) throws Exception {
        JsonNode versions = json(get(base + "/versions").with(asUser(Permissions.EXAM_VIEW)), 200);
        List<String> ids = new ArrayList<>();
        versions.path("workingCopy").path("unpublishedPaths").forEach(path -> ids.add(path.path("pathId").asText()));
        return ids.stream().sorted().toList();
    }

    private static List<String> ids(JsonNode check) {
        List<String> ids = new ArrayList<>();
        check.path("paths").forEach(path -> ids.add(path.path("pathId").asText()));
        return ids.stream().sorted().toList();
    }

    private static String pathResult(JsonNode check, String pathId) {
        for (JsonNode path : check.path("paths")) {
            if (pathId.equals(path.path("pathId").asText())) return path.path("result").asText();
        }
        return null;
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expected) throws Exception {
        return mapper.readTree(mockMvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }
}
