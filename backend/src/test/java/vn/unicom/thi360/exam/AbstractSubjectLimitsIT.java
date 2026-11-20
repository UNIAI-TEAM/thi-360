package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** Shared HTTP helpers of the CR-4 limit tests: create from the real templates, edit the structure, check. */
@Transactional
abstract class AbstractSubjectLimitsIT extends AbstractIntegrationTest {
    protected static final String SET_CODE = "SECTION_SET_COUNT_RANGE";
    protected static final String SHORT_CODE = "SHORT_ANSWER_MIN";

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper mapper;

    /** Creates an exam from the default template of the choice; returns its {@code /api/v1/admin/exams/{id}} URL. */
    protected String create(String kind, String scope, String partCode, String subjectCode, String buildMode) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", kind).put("scope", scope)
                .put("name", "CR-4 " + kind).put("code", "L-" + UUID.randomUUID().toString().substring(0, 8));
        if (partCode != null) body.put("partCode", partCode);
        if (subjectCode != null) body.put("subjectCode", subjectCode);
        if (!"FULL".equals(scope)) body.put("durationMinutes", 60);
        if (buildMode != null) body.put("buildMode", buildMode);
        JsonNode created = json(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 201);
        return "/api/v1/admin/exams/" + created.path("id").asText();
    }

    protected JsonNode structure(String base) throws Exception {
        return json(get(base + "/structure").with(asUser(Permissions.EXAM_VIEW)), 200);
    }

    /** PUT of the edited structure: saving always works, the limits only block publishing. */
    protected void save(String base, JsonNode before, ObjectNode edited) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", before.path("revision").asLong());
        body.set("parts", edited.path("parts"));
        if (edited.has("examQuotas")) body.set("examQuotas", edited.path("examQuotas"));
        json(put(base + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 200);
    }

    protected JsonNode check(String base) throws Exception {
        return json(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
    }

    protected JsonNode json(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return mapper.readTree(mockMvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    protected static List<JsonNode> issues(JsonNode result) {
        List<JsonNode> all = new ArrayList<>();
        for (JsonNode list : result.path("groups").findValues("issues")) list.forEach(all::add);
        return all;
    }

    protected static List<String> codes(JsonNode result) {
        return issues(result).stream().map(issue -> issue.path("code").asText()).toList();
    }

    protected static JsonNode issue(JsonNode result, String code) {
        return issues(result).stream().filter(issue -> code.equals(issue.path("code").asText())).findFirst()
                .orElseThrow(() -> new AssertionError(code + " missing in " + codes(result)));
    }

    /** First section of the subject in the structure, wherever it is (sections, subjectPool, options). */
    protected static ObjectNode section(JsonNode structure, String subjectCode) {
        List<ObjectNode> found = new ArrayList<>();
        collect(structure.path("parts"), subjectCode, found);
        if (found.isEmpty()) throw new AssertionError("no section of " + subjectCode);
        return found.getFirst();
    }

    private static void collect(JsonNode node, String subjectCode, List<ObjectNode> found) {
        if (node.isObject()) {
            if (subjectCode.equals(node.path("subjectCode").asText()) && node.has("blocks")) found.add((ObjectNode) node);
            node.fields().forEachRemaining(entry -> collect(entry.getValue(), subjectCode, found));
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, subjectCode, found));
        }
    }

    protected static ObjectNode blockOfType(ObjectNode section, String questionType) {
        for (JsonNode block : section.path("blocks")) {
            if (questionType.equals(block.path("questionType").asText())) return (ObjectNode) block;
        }
        throw new AssertionError("no " + questionType + " block");
    }

    /**
     * Gives the set block {@code sets} sets of {@code size} questions and moves the difference to the single-choice
     * block, so the section total is unchanged. {@code sets = 0} removes the set block.
     */
    protected static void setShape(ObjectNode section, int sets, int size) {
        ObjectNode set = blockOfType(section, "QUESTION_SET");
        ObjectNode single = blockOfType(section, "SINGLE_CHOICE");
        int delta = sets * size - set.path("questionCount").asInt();
        single.put("questionCount", single.path("questionCount").asInt() - delta);
        if (sets == 0) {
            ArrayNode blocks = (ArrayNode) section.path("blocks");
            for (int i = 0; i < blocks.size(); i++) if (blocks.get(i) == set) { blocks.remove(i); break; }
        } else {
            set.put("questionCount", sets * size).put("setCount", sets).put("setSize", size);
        }
    }

    /** Turns the short-answer block into a single-choice one (the section has no short answer left). */
    protected static void dropShortAnswer(ObjectNode section) {
        blockOfType(section, "SHORT_ANSWER").put("questionType", "SINGLE_CHOICE");
    }
}
