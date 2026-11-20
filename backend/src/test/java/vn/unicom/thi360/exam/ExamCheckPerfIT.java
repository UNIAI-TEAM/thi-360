package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;

@Tag("perf")
@Transactional
class ExamCheckPerfIT extends AbstractIntegrationTest {
    private static final UUID SEEDED_HSA = UUID.fromString("00000000-0000-7000-8700-000000000001");
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;

    @Test
    void seededHsaSyncCheckFinishesWithinThirtySeconds() throws Exception {
        UUID examId = SEEDED_HSA;
        long start = System.nanoTime();
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeLimitSeconds").value(30))
                .andExpect(jsonPath("$.groups.length()").value(8));
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("PERF ExamCheck seeded HSA ms=" + elapsed);
        assertThat(elapsed).isLessThan(30_000);
    }

    /**
     * UNI-2758 AC-13: the seeded HSA exam with six MAPID rules on its first subject (a subject PRESENT, an exact
     * count, group minimums, an ONLY_IN_GROUP and a SOURCE) is still decided inside the check deadline: the result
     * is a proof (VALID or INVALID), never UNCONFIRMED.
     */
    @Test
    void seededHsaWithSixMapidRulesIsDecidedWithinTheDeadline() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", SEEDED_HSA)
                .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ArrayNode parts = (ArrayNode) before.path("parts").deepCopy();
        ObjectNode section = firstSectionWithTwoBlocks(parts);
        String first = section.path("blocks").get(0).path("id").asText();
        String second = section.path("blocks").get(1).path("id").asText();
        List<String> nodes = disjointNodes(3);
        section.put("mapidMode", "MATRIX");
        ArrayNode rules = section.putArray("mapidRules");
        rules.add(rule("present", "PRESENT", null, null, null, nodes.get(0)));
        rules.add(rule("exact", "COUNT", null, "EXACT", 2, nodes.get(1)));
        rules.add(rule("first", "COUNT", first, "AT_LEAST", 1, nodes.get(0)));
        rules.add(rule("second", "COUNT", second, "AT_LEAST", 1, nodes.get(2)));
        rules.add(rule("only", "ONLY_IN_GROUP", first, null, null, nodes.get(1)));
        rules.add(rule("source", "SOURCE", second, null, null, nodes.get(0), nodes.get(1), nodes.get(2)));
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", before.path("revision").asLong());
        request.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", SEEDED_HSA).with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isOk());
        long start = System.nanoTime();
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + SEEDED_HSA + "/check")
                .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("PERF ExamCheck seeded HSA six MAPID rules ms=" + elapsed + " status=" + result.path("status").asText());
        assertThat(elapsed).isLessThan(30_000);
        assertThat(result.path("status").asText()).isIn("VALID", "INVALID");
    }

    private static ObjectNode firstSectionWithTwoBlocks(ArrayNode parts) {
        for (JsonNode part : parts) for (JsonNode section : part.path("sections")) {
            if (section.path("blocks").size() >= 2) return (ObjectNode) section;
        }
        throw new AssertionError("seeded HSA exam has no section with two groups");
    }

    private ObjectNode rule(String id, String type, String groupId, String comparator, Integer count, String... nodeIds) {
        ObjectNode rule = mapper.createObjectNode().put("id", id).put("type", type);
        if (groupId != null) rule.put("groupId", groupId);
        if (comparator != null) rule.put("comparator", comparator);
        if (count != null) rule.put("count", count);
        ArrayNode nodes = rule.putArray("nodeIds");
        for (String node : nodeIds) nodes.add(node);
        return rule;
    }

    private List<String> disjointNodes(int wanted) {
        List<String> ids = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (CatalogItemView item : catalog.listActiveGroupItems("KNOWLEDGE")) {
            if (paths.stream().noneMatch(path -> path.startsWith(item.path()) || item.path().startsWith(path))) {
                ids.add(item.id().toString());
                paths.add(item.path());
            }
            if (ids.size() == wanted) break;
        }
        assertThat(ids).hasSize(wanted);
        return ids;
    }
}
