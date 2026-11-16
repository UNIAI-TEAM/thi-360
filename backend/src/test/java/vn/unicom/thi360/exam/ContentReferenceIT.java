package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ContentReferenceService;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-1 (UNI-2607): GET /api/v1/admin/content-reference.
 * AC-1: QUESTION.VIEW → 200; EXAM.VIEW → 200; không có quyền → 403; không đăng nhập → 401.
 * AC-3: không có giá trị QUESTION_FORMAT nào vẫn trả đủ 8 dạng.
 */
@org.springframework.transaction.annotation.Transactional
class ContentReferenceIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/content-reference";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearCache() {
        redisTemplate.delete(ContentReferenceService.CACHE_KEY);
    }

    @Test
    void questionViewGets200WithCatalogPart() throws Exception {
        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects").isArray())
                .andExpect(jsonPath("$.grades").isArray())
                .andExpect(jsonPath("$.difficulties").isArray())
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.examKinds").isArray())
                .andExpect(jsonPath("$.examKinds").isEmpty())
                .andExpect(jsonPath("$.combinations").isArray())
                .andExpect(jsonPath("$.competencies").isArray())
                .andExpect(jsonPath("$.knowledgeTree").isArray())
                .andExpect(jsonPath("$.taskTypes").isArray())
                .andExpect(jsonPath("$.questionTypes.length()").value(8));
    }

    @Test
    void examViewAlsoGets200() throws Exception {
        mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionTypes.length()").value(8));
    }

    @Test
    void userWithNeitherPermissionGets403() throws Exception {
        mockMvc.perform(get(URL).with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noQuestionFormatValuesStillReturnsAllEightTypes() throws Exception {
        // AC-3: nhóm QUESTION_FORMAT không có giá trị nào (môi trường thật, P9) thì tám dạng vẫn
        // dùng được với tên mặc định trong mã nguồn (D1). Xóa giá trị mẫu trong transaction test.
        jdbcTemplate.update(
                "DELETE FROM catalog.catalog_item WHERE group_id = '00000000-0000-7000-8100-000000000005'");

        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionTypes[?(@.active==true)]").value(org.hamcrest.Matchers.hasSize(8)))
                .andExpect(jsonPath("$.questionTypes[?(@.code=='SHORT_ANSWER' && @.name=='Trả lời ngắn')]").exists());
    }

    @Test
    void disabledQuestionFormatValueBlocksOnlyThatType() throws Exception {
        // Chỉ giá trị bị ngừng mới chặn dạng đó; dạng thiếu giá trị vẫn active với tên mặc định.
        jdbcTemplate.update(
                """
                UPDATE catalog.catalog_item
                SET status = 2
                WHERE group_id = '00000000-0000-7000-8100-000000000005'
                  AND code_normalized = 'essay'
                """);

        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionTypes.length()").value(8))
                .andExpect(jsonPath("$.questionTypes[?(@.code=='ESSAY' && @.active==false)]").exists())
                .andExpect(jsonPath("$.questionTypes[?(@.code=='MATCHING' && @.active==true)]").exists());
    }

    @Test
    void knowledgeTreeWrapsChaptersUnderSubjectWithCatalogIds() throws Exception {
        // Cây danh mục KNOWLEDGE có ba cấp (Chương/Bài/Kiến thức); client bắt đầu ở cấp
        // SUBJECT nên các chương gom dưới nút môn dựng từ liên kết SUBJECT. Bộ mẫu dev:
        // TOAN → 0D1 (Chương) → 0D1-1 (Bài) → 0D1-1-1 (Kiến thức, chọn được với id danh mục).
        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeTree[?(@.level=='SUBJECT' && @.code=='TOAN' && @.subjectCode=='TOAN' && @.gradeCode==null)]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.knowledgeTree[?(@.code=='TOAN')].children[?(@.code=='0D1' && @.level=='CHAPTER' && @.subjectCode=='TOAN' && @.gradeCode=='G10')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.knowledgeTree[?(@.code=='TOAN')].children[?(@.code=='0D1')].children[?(@.code=='0D1-1' && @.level=='LESSON' && @.subjectCode=='TOAN' && @.gradeCode=='G10')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.knowledgeTree[?(@.code=='TOAN')].children[?(@.code=='0D1')].children[?(@.code=='0D1-1')].children[?(@.code=='0D1-1-1' && @.level=='KNOWLEDGE' && @.subjectCode=='TOAN' && @.gradeCode=='G10' && @.active==true && @.id=='00000000-0000-7000-8200-000000001201')]").value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void mixedCaseQuestionFormatStillResolvesByNormalizedCode() throws Exception {
        // R1-2: mã lưu lẫn hoa/thường vẫn khớp enum theo code_normalized (định danh danh mục).
        String essayId = "00000000-0000-7000-8400-000000000508";
        long version = readItemVersion(essayId);

        patchItem(essayId, "{\"code\":\"eSsAy\",\"nameVi\":\"Bài tự luận mẫu\",\"version\":" + version + "}");
        version++;

        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionTypes[?(@.code=='ESSAY' && @.active==true && @.name=='Bài tự luận mẫu')]").value(org.hamcrest.Matchers.hasSize(1)));

        // Ngừng giá trị bắt buộc kèm lý do; mã lưu lẫn hoa/thường vẫn phải chặn đúng dạng.
        patchItem(essayId, "{\"status\":\"INACTIVE\",\"reason\":\"test ngừng dạng\",\"version\":" + version + "}");

        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionTypes.length()").value(8))
                .andExpect(jsonPath("$.questionTypes[?(@.code=='ESSAY' && @.active==false)]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.questionTypes[?(@.code=='MATCHING' && @.active==true)]").exists());
    }

    private long readItemVersion(String itemId) throws Exception {
        MvcResult item = mockMvc.perform(get("/api/v1/admin/catalog/items/" + itemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(item.getResponse().getContentAsString());
        return node.get("version").asLong();
    }

    private void patchItem(String itemId, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/catalog/items/" + itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
}
