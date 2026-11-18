package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-21 rows 5, 6 and 11 (addendum specification 5.2): source shortages of the V-SAT English rows
 * land on the named block. Each test builds the exam through the API, retires seed questions of
 * subject ANH and runs the check endpoint (same mechanics as {@code ExamAudioChildTypeShortageIT}).
 */
@Transactional
class VsatEnglishSourceShortageIT extends AbstractIntegrationTest {
    private static final String SC4 = "{\"type\":\"SINGLE_CHOICE\",\"options\":["
            + "{\"id\":\"o0\",\"correct\":true},{\"id\":\"o1\"},{\"id\":\"o2\"},{\"id\":\"o3\"}]}";

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    /**
     * Row 5: with the mixed sets retired and only eight-child single-choice sets left, the shared
     * reading row reports CHILD_CONDITION_SHORTAGE (its children cannot fill positions 1-2).
     */
    @Test
    void onlySingleChoiceSetsReportChildConditionShortageAtTheReadingRow() throws Exception {
        retireAnhSetsWithChildren(8);
        insertEightChildSingleChoiceSet();
        String id = created(create("SUBJECT", null));
        JsonNode result = check(id);
        JsonNode issue = issueAtReadingBlock(id, result, "CHILD_CONDITION_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("available").asLong()).isZero();
        assertThat(codes(result)).doesNotContain("SET_SHORTAGE");
    }

    /**
     * Row 6: with no eight-child set at all the same row reports SET_SHORTAGE instead.
     */
    @Test
    void noEightChildSetReportsSetShortageAtTheReadingRow() throws Exception {
        retireAnhSetsWithChildren(8);
        String id = created(create("SUBJECT", null));
        JsonNode result = check(id);
        JsonNode issue = issueAtReadingBlock(id, result, "SET_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("available").asLong()).isZero();
    }

    /**
     * Row 11: the P2 block needs a six-child set; with the choice sets retired the check reports
     * the shortage on that block and does not fall back to the eight-child mixed set.
     */
    @Test
    void missingSixChildSetReportsShortageAtThePartTwoBlock() throws Exception {
        // Every six-child set of subject ANH (BE-19 choice sets, generic reading sets, tagged
        // English sets) must go: any survivor would satisfy the size at the wrong points and the
        // row would report POINTS_SHORTAGE instead of the missing-set shortage.
        retireAnhSetsWithChildren(6);
        String id = created(create("PART", "P2"));
        JsonNode result = check(id);
        JsonNode issue = findIssue(result, "SET_SHORTAGE");
        String blockId = blockId(id, "Chọn một, 1 chùm × 6 câu");
        assertThat(issue.path("blockId").asText()).isEqualTo(blockId);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    /** Retires every subject-ANH question set that has exactly {@code childCount} children. */
    private void retireAnhSetsWithChildren(int childCount) {
        int retired = jdbc.update("""
                UPDATE questionbank.question q SET status = 'RETIRED'
                FROM catalog.catalog_item s
                WHERE q.subject_item_id = s.id AND q.parent_id IS NULL AND q.deleted_at IS NULL
                  AND s.group_id = '00000000-0000-7000-8100-000000000002' AND s.code = 'ANH'
                  AND q.type = 'QUESTION_SET'
                  AND (SELECT count(*) FROM questionbank.question c
                       WHERE c.parent_id = q.id AND c.deleted_at IS NULL) = ?
                """, childCount);
        assertThat(retired).as("retired ANH sets of %d children", childCount).isPositive();
    }

    /** One eight-child set whose children are all single choice (mirrors the seed shape). */
    private void insertEightChildSingleChoiceSet() {
        UUID english = jdbc.queryForObject("""
                SELECT id FROM catalog.catalog_item
                WHERE group_id = '00000000-0000-7000-8100-000000000002' AND code = 'ANH'
                """, UUID.class);
        UUID root = UUID.randomUUID();
        var children = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            children.append(i == 0 ? "" : ",").append("{\"id\":\"").append(UUID.randomUUID())
                    .append("\",\"body\":").append(SC4).append('}');
        }
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,"
                        + "current_version_no,points) VALUES (?,?,'QUESTION_SET','READY',?,1,48)",
                root, "B21-8SC-" + root, english);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) "
                        + "VALUES (?,1,?::jsonb)", root,
                "{\"body\":{\"type\":\"QUESTION_SET\",\"children\":[" + children + "]}}");
        for (int i = 0; i < 8; i++) {
            UUID child = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,"
                            + "subject_item_id,points) VALUES (?,?,?,'READY',?,?,?,6)",
                    child, "B21-8SC-C-" + i + "-" + child, "SINGLE_CHOICE", root, i, english);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) "
                            + "VALUES (?,1,?::jsonb)", child, "{\"body\":" + SC4 + "}");
        }
    }

    /** Finds the issue on the shared-reading block of the exam's stored structure. */
    private JsonNode issueAtReadingBlock(String examId, JsonNode result, String code) throws Exception {
        String blockId = blockId(examId, "Bài đọc dùng chung – câu 8–15");
        JsonNode issue = findIssue(result, code);
        assertThat(issue.path("blockId").asText())
                .as("%s lands on the shared reading row", code).isEqualTo(blockId);
        return issue;
    }

    private String blockId(String examId, String blockName) throws Exception {
        JsonNode structure = mapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/structure", examId)
                                .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode part : structure.path("parts")) {
            for (JsonNode section : part.path("sections")) {
                for (JsonNode block : section.path("blocks")) {
                    if (blockName.equals(block.path("name").asText())) {
                        return block.path("id").asText();
                    }
                }
            }
        }
        throw new AssertionError("no block named " + blockName + " in " + structure);
    }

    private JsonNode findIssue(JsonNode result, String code) {
        return result.path("groups").findValues("issues").stream()
                .flatMap(issues -> StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> code.equals(issue.path("code").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + code + " in "
                        + result.path("groups").findValuesAsText("code")));
    }

    private java.util.List<String> codes(JsonNode result) {
        return result.path("groups").findValuesAsText("code");
    }

    private org.springframework.test.web.servlet.ResultActions create(String scope, String partCode)
            throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", "VSAT").put("scope", scope)
                .put("name", "BE21 shortage " + scope)
                .put("code", "B21S-" + UUID.randomUUID().toString().substring(0, 8))
                .put("subjectCode", "ANH");
        if (partCode != null) body.put("partCode", partCode);
        body.put("durationMinutes", "SUBJECT".equals(scope) ? 60 : 30);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private String created(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private JsonNode check(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
