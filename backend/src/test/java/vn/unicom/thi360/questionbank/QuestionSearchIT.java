package vn.unicom.thi360.questionbank;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Danh sách câu hỏi (spec 4.5): tìm kiếm bỏ dấu trên {@code search_text}, lọc phân loại,
 * lọc MAPID mở rộng tới nút con (D23), lọc nhãn và đếm theo trạng thái cùng bộ lọc.
 */
@Transactional
class QuestionSearchIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/questions";
    private static final String TAG_CODE = "IT-TAG-A";
    private static final UUID TAG_ITEM_ID = UUID.fromString("00000000-0000-7000-8200-00000000ff01");
    private final UUID actorId = UUID.randomUUID();

    @Override
    protected org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String... permissions) {
        return asUser(actorId, "question-search-it", permissions);
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @Test
    void keywordMatchesAccentedTextCaseInsensitive() throws Exception {
        createQuestion("{\"type\":\"SINGLE_CHOICE\",\"subjectCode\":\"TOAN\",\"gradeCode\":\"G10\","
                + "\"difficultyCode\":\"NB\",\"taskTypeCode\":\"SINGLE\","
                + "\"content\":\"<p>Đạo hàm của hàm số bậc hai là gì?</p>\",\"body\":"
                + QuestionTestSupport.singleChoiceBody() + "}");
        createQuestion(QuestionTestSupport.createSingleChoiceRequest());

        // Từ khóa không dấu, chữ thường khớp nội dung có dấu.
        mockMvc.perform(get(BASE).param("createdBy", actorId.toString()).param("keyword", "dao ham")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalItems").value(1));

        // Từ khóa một ký tự chỉ so mã câu → rỗng.
        mockMvc.perform(get(BASE).param("createdBy", actorId.toString()).param("keyword", "a")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        // Khớp mã câu qua cột code.
        String code = jdbcTemplate.queryForObject(
                "SELECT code FROM questionbank.question WHERE parent_id IS NULL"
                        + " AND created_by = ? ORDER BY created_at LIMIT 1",
                String.class, actorId);
        mockMvc.perform(get(BASE).param("createdBy", actorId.toString()).param("keyword", code)
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void filtersByClassificationAndStatus() throws Exception {
        createQuestion(QuestionTestSupport.createSingleChoiceRequest());
        // Câu khác môn/khối không khớp lọc.
        mockMvc.perform(get(BASE).param("createdBy", actorId.toString()).param("subjectCode", "VAN")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get(BASE)
                        .param("createdBy", actorId.toString())
                        .param("subjectCode", "TOAN")
                        .param("gradeCode", "G10")
                        .param("difficultyCode", "NB")
                        .param("type", "SINGLE_CHOICE")
                        .param("taskTypeCode", "SINGLE")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(get(BASE).param("createdBy", actorId.toString()).param("status", "READY")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get(BASE).param("createdBy", actorId.toString()).param("status", "DRAFT")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void knowledgeFilterExpandsToDescendants() throws Exception {
        // Câu gắn MAPID lá 0D1-1-1; lọc theo chương 0D1 phải khớp (D23).
        String request = "{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p>Q?</p>\",\"body\":"
                + QuestionTestSupport.singleChoiceBody()
                + ",\"knowledge\":[{\"nodeId\":\"" + QuestionTestSupport.KNOWLEDGE_LEAF_ID
                + "\",\"primary\":true}]}";
        createQuestion(request);

        mockMvc.perform(get(BASE)
                        .param("createdBy", actorId.toString())
                        .param("knowledgeIds", QuestionTestSupport.KNOWLEDGE_CHAPTER_ID.toString())
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        // Nút chương khác không khớp.
        mockMvc.perform(get(BASE)
                        .param("createdBy", actorId.toString())
                        .param("knowledgeIds", "00000000-0000-7000-8200-000000001002")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void tagFilterMatchesLinkedItems() throws Exception {
        // Nhóm QUESTION_TAG để trống trong seed: tạo một giá trị dùng cho test.
        jdbcTemplate.update(
                "INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value,"
                        + " name_vi, name_en, display_order, status, is_system, depth, path)"
                        + " VALUES (?, ?, ?, ?, 1, 'Nhan IT', 'IT tag', 0, 1, false, 1, ?)",
                TAG_ITEM_ID, QuestionTestSupport.QUESTION_TAG_GROUP_ID, TAG_CODE,
                TAG_CODE.toLowerCase(), "/" + TAG_ITEM_ID + "/");
        createQuestion("{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p>Q?</p>\",\"body\":"
                + QuestionTestSupport.singleChoiceBody()
                + ",\"tagCodes\":[\"" + TAG_CODE + "\"]}");
        createQuestion(QuestionTestSupport.createSingleChoiceRequest());

        mockMvc.perform(get(BASE).param("createdBy", actorId.toString()).param("tagCodes", TAG_CODE)
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void statusCountsShareListFilters() throws Exception {
        createQuestion(QuestionTestSupport.createSingleChoiceRequest());
        mockMvc.perform(get(BASE + "/status-counts")
                        .param("createdBy", actorId.toString())
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").value(1))
                .andExpect(jsonPath("$.ready").value(0))
                .andExpect(jsonPath("$.retired").value(0));
        mockMvc.perform(get(BASE + "/status-counts").param("createdBy", actorId.toString()).param("subjectCode", "VAN")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").value(0));
    }

    @Test
    void authorsListsCreators() throws Exception {
        UUID author = UUID.randomUUID();
        String username = "question-it-" + author;
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, code, username, username_normalized, full_name,"
                        + " source, status, must_change_password) VALUES (?, ?, ?, ?, ?, 1, 2, false)",
                author, "U" + author.toString().substring(0, 8), username, username, "Question author");
        try {
            mockMvc.perform(post(BASE)
                            .with(asUser(author, username, Permissions.QUESTION_MODIFY))
                            .header("Idempotency-Key", "k-author-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(QuestionTestSupport.createSingleChoiceRequest()))
                    .andExpect(status().isCreated());
            mockMvc.perform(get(BASE + "/authors").with(asUser(Permissions.QUESTION_VIEW)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(author), hasSize(1)));
        } finally {
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", author);
        }
    }

    // ---------------------------------------------------------------- trợ giúp

    private UUID createQuestion(String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-search-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).at("/summary/id").asText());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
