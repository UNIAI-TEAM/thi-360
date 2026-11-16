package vn.unicom.thi360.questionbank;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fixture chung của các IT ngân hàng câu hỏi: id giá trị danh mục đã seed (dev data),
 * JSON thân câu theo dạng và dọn dữ liệu câu sau mỗi test.
 */
final class QuestionTestSupport {

    private QuestionTestSupport() {}

    // Giá trị danh mục đã seed trong db/devdata (id cố định).
    static final String SUBJECT_TOAN = "TOAN";
    static final UUID SUBJECT_TOAN_ID = UUID.fromString("00000000-0000-7000-8200-000000000201");
    static final String GRADE_G10 = "G10";
    static final UUID GRADE_G10_ID = UUID.fromString("00000000-0000-7000-8200-000000000301");
    static final String LEVEL_NB = "NB";
    static final String TASK_TYPE_SINGLE = "SINGLE";
    static final String KNOWLEDGE_LEAF = "0D1-1-1";
    static final UUID KNOWLEDGE_LEAF_ID = UUID.fromString("00000000-0000-7000-8200-000000001201");
    static final String KNOWLEDGE_CHAPTER = "0D1";
    static final UUID KNOWLEDGE_CHAPTER_ID = UUID.fromString("00000000-0000-7000-8200-000000001001");
    static final UUID QUESTION_TAG_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000008");

    /** Thân câu một đáp án tối thiểu hợp lệ (type trong body = SINGLE_CHOICE). */
    static String singleChoiceBody() {
        return """
                {"type":"SINGLE_CHOICE","options":[
                  {"id":"o1","content":"<p>A</p>","correct":true},
                  {"id":"o2","content":"<p>B</p>","correct":false}]}""";
    }

    /** Thân câu trả lời ngắn tối thiểu hợp lệ. */
    static String shortAnswerBody() {
        return "{\"type\":\"SHORT_ANSWER\",\"answers\":[\"42\"],\"inputHint\":\"<p>So</p>\"}";
    }

    /** Bộ câu hỏi hai câu con (một trắc nghiệm, một trả lời ngắn). */
    static String questionSetBody() {
        return """
                {"type":"QUESTION_SET","passage":"<p>Doc doan van sau.</p>","children":[
                  {"type":"SINGLE_CHOICE","content":"<p>Cau con 1?</p>",
                   "body":%s},
                  {"type":"SHORT_ANSWER","content":"<p>Cau con 2?</p>",
                   "body":%s,"points":2}]}"""
                .formatted(singleChoiceBody(), shortAnswerBody());
    }

    /** Bản ghi tạo câu một đáp án ở mức lưu tối thiểu. */
    static String createSingleChoiceRequest() {
        return """
                {"type":"SINGLE_CHOICE","subjectCode":"%s","gradeCode":"%s",
                 "difficultyCode":"%s","taskTypeCode":"%s",
                 "content":"<p>Noi dung cau hoi?</p>","body":%s}"""
                .formatted(SUBJECT_TOAN, GRADE_G10, LEVEL_NB, TASK_TYPE_SINGLE, singleChoiceBody());
    }

    /** Bản ghi tạo bộ câu hỏi hai câu con. */
    static String createQuestionSetRequest() {
        return """
                {"type":"QUESTION_SET","subjectCode":"%s","gradeCode":"%s",
                 "difficultyCode":"%s","taskTypeCode":"%s","body":%s}"""
                .formatted(SUBJECT_TOAN, GRADE_G10, LEVEL_NB, TASK_TYPE_SINGLE, questionSetBody());
    }

    /** Xóa mọi dữ liệu câu hỏi (kể cả câu con) để test độc lập. */
    static void deleteAllQuestions(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM questionbank.question_usage_ledger");
        jdbc.update("DELETE FROM questionbank.question_knowledge");
        jdbc.update("DELETE FROM questionbank.question_tag");
        jdbc.update("DELETE FROM questionbank.question_version");
        jdbc.update("DELETE FROM questionbank.question WHERE parent_id IS NOT NULL");
        jdbc.update("DELETE FROM questionbank.question");
        jdbc.update("DELETE FROM questionbank.question_source_revision");
    }

    /** Mã câu đầu tiên của một dạng (truy vấn nhanh cho assert). */
    static List<String> codesByStatus(JdbcTemplate jdbc, String status) {
        return jdbc.queryForList(
                "SELECT code FROM questionbank.question WHERE status = ? AND parent_id IS NULL"
                        + " AND deleted_at IS NULL ORDER BY created_at",
                String.class, status);
    }
}
