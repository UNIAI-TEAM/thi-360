package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
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

    @Autowired
    private CatalogLookup catalogLookup;

    @AfterEach
    void clearCache() {
        // Khóa legacy "exam:content-reference" (trước hậu tố :v2) bị code hiện tại bỏ qua;
        // vẫn dọn để test seed payload cũ không rò rỉ sang test khác.
        redisTemplate.delete(List.of(ContentReferenceService.CACHE_KEY, "exam:content-reference"));
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

    @Test
    void warmedCacheIsRebuiltAfterCatalogMutation() throws Exception {
        // R1-4: đọc lần hai đảm bảo đã đi từ cache; đổi tên qua API quản trị làm revision
        // tăng nên bản kế tiếp phải thấy tên mới thay vì bản cache cũ.
        String leafId = "00000000-0000-7000-8200-000000001201";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$..children[?(@.code=='0D1-1-1' && @.name=='Khái niệm mệnh đề và mệnh đề chứa biến')]").value(org.hamcrest.Matchers.hasSize(1)));
        }

        long version = readItemVersion(leafId);
        patchItem(leafId, "{\"nameVi\":\"Khái niệm mệnh đề (đổi tên mẫu)\",\"version\":" + version + "}");

        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..children[?(@.code=='0D1-1-1' && @.name=='Khái niệm mệnh đề (đổi tên mẫu)')]").value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void legacyCacheKeyIsIgnoredAfterKeyVersioning() throws Exception {
        // R2-1/R2-2: cache mà instance trước-khóa-v2 để lại nằm dưới "exam:content-reference"
        // với cùng hình dạng JSON. Payload giả này mô phỏng output builder cũ (Chương nằm
        // ở gốc, nút sâu 3 là LESSON) nhưng mang revision danh mục HIỆN TẠI — code cũ sẽ phục
        // vụ nó nguyên văn, code mới phải bỏ qua và dựng lại cây đúng.
        long revision = catalogLookup.revision();
        String legacyPayload = """
                {
                  "catalogRevision": %d,
                  "subjects": [{"code":"TOAN","vi":"Toán","en":"Math"}],
                  "grades": [], "difficulties": [], "tags": [], "examKinds": [],
                  "combinations": [], "competencies": [],
                  "knowledgeTree": [{
                    "id": "00000000-0000-7000-8200-000000001001",
                    "code": "0D1",
                    "name": {"code": "0D1", "vi": "Mệnh đề", "en": "Propositions"},
                    "level": "CHAPTER",
                    "subjectCode": "TOAN",
                    "gradeCode": "G10",
                    "children": [{
                      "id": "00000000-0000-7000-8200-000000001101",
                      "code": "0D1-1",
                      "name": {"code": "0D1-1", "vi": "Mệnh đề", "en": null},
                      "level": "LESSON",
                      "subjectCode": "TOAN",
                      "gradeCode": "G10",
                      "children": [{
                        "id": "00000000-0000-7000-8200-000000001201",
                        "code": "0D1-1-1",
                        "name": {"code": "0D1-1-1", "vi": "Khái niệm mệnh đề", "en": null},
                        "level": "LESSON",
                        "subjectCode": "TOAN",
                        "gradeCode": "G10",
                        "children": []
                      }]
                    }]
                  }],
                  "taskTypes": [],
                  "questionTypes": [{"code":"ESSAY","nameVi":"Tự luận","nameEn":"Essay","active":true}]
                }
                """.formatted(revision);
        redisTemplate.opsForValue().set("exam:content-reference", legacyPayload);

        mockMvc.perform(get(URL).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeTree[?(@.code=='0D1')]").isEmpty())
                .andExpect(jsonPath("$.knowledgeTree[?(@.level=='SUBJECT' && @.code=='TOAN')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.knowledgeTree[?(@.code=='TOAN')].children[?(@.code=='0D1' && @.level=='CHAPTER')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$..children[?(@.code=='0D1-1-1' && @.level=='KNOWLEDGE')]").value(org.hamcrest.Matchers.hasSize(1)));
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
