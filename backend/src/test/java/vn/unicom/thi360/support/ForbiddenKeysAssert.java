package vn.unicom.thi360.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Quét đệ quy một tài liệu JSON (mảng và đối tượng lồng nhau) và lỗi khi gặp
 * khóa bị cấm lộ. Dùng cho LT 7.3 (nội dung câu trong lúc làm bài) và TM 10.1
 * (bảng chiếu không chứa dữ liệu ẩn). Thông báo lỗi chỉ nêu đường dẫn JSON của
 * khóa vi phạm (dạng {@code $.items[2].solution}), không in giá trị.
 *
 * <p>Hai danh sách ghi ở một nơi duy nhất ở đây; task cần thêm khóa thì thêm
 * vào danh sách tương ứng (plan B6), không tự định nghĩa lại.
 */
public final class ForbiddenKeysAssert {

    /**
     * LT 7.3 — nội dung câu gửi cho học viên trong lúc làm bài không được chứa:
     * đáp án đúng ({@code correct}, {@code answer}), {@code answers}, {@code pairs},
     * điểm từng ý của Đúng/Sai ({@code scoreTable}), lời giải ({@code solution}),
     * {@code gradingGuide}, {@code rubric}, {@code sampleAnswer}, {@code commonMistakes},
     * MAPID ({@code knowledge}, {@code knowledgeItemIds}, {@code mapId(s)}) và độ khó
     * ({@code levelItemId}, {@code difficulty(…)}). {@code points}, {@code inputHint},
     * {@code wordLimit} được phép nên không nằm trong danh sách.
     */
    public static final Set<String> ATTEMPT_FORBIDDEN_KEYS = Set.of(
            "correct",
            "answer",
            "answers",
            "pairs",
            "scoreTable",
            "solution",
            "gradingGuide",
            "rubric",
            "sampleAnswer",
            "commonMistakes",
            "knowledge",
            "knowledgeItemIds",
            "mapId",
            "mapIds",
            "levelItemId",
            "difficulty",
            "difficultyCode");

    /**
     * TM 10.1 — phản hồi của bảng chiếu không bao giờ chứa: id hoặc nội dung câu
     * hỏi ({@code question(s)…}, {@code body}, {@code snapshot}), đáp án và lời giải
     * (toàn bộ danh sách lượt thi), nguồn câu của khối ({@code source…}), đề chứng
     * minh ({@code evidence}, {@code witness…}) và đề xem trước ({@code preview…}).
     */
    public static final Set<String> STOREFRONT_FORBIDDEN_KEYS;

    static {
        Set<String> storefront = new LinkedHashSet<>(ATTEMPT_FORBIDDEN_KEYS);
        storefront.addAll(Set.of(
                "question",
                "questions",
                "questionId",
                "questionIds",
                "questionVersionId",
                "questionVersionIds",
                "body",
                "snapshot",
                "source",
                "sourceCode",
                "sourceRevision",
                "evidence",
                "witness",
                "witnessPicks",
                "preview",
                "previewId",
                "previewPaper"));
        STOREFRONT_FORBIDDEN_KEYS = Set.copyOf(storefront);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ForbiddenKeysAssert() {
    }

    /** Phân tích chuỗi JSON rồi kiểm theo danh sách lượt thi (LT 7.3). */
    public static void assertNoAttemptForbiddenKeys(String json) {
        assertNoForbiddenKeys(parse(json), ATTEMPT_FORBIDDEN_KEYS, "attempt");
    }

    /** Kiểm theo danh sách lượt thi (LT 7.3) trên nút JSON đã phân tích. */
    public static void assertNoAttemptForbiddenKeys(JsonNode node) {
        assertNoForbiddenKeys(node, ATTEMPT_FORBIDDEN_KEYS, "attempt");
    }

    /** Phân tích chuỗi JSON rồi kiểm theo danh sách bảng chiếu (TM 10.1). */
    public static void assertNoStorefrontForbiddenKeys(String json) {
        assertNoForbiddenKeys(parse(json), STOREFRONT_FORBIDDEN_KEYS, "storefront");
    }

    /** Kiểm theo danh sách bảng chiếu (TM 10.1) trên nút JSON đã phân tích. */
    public static void assertNoStorefrontForbiddenKeys(JsonNode node) {
        assertNoForbiddenKeys(node, STOREFRONT_FORBIDDEN_KEYS, "storefront");
    }

    /**
     * Kiểm với danh sách khóa tự chọn: dành cho phản hồi phải đạt cả hai danh
     * sách hoặc một danh sách mở rộng mà task vẫn khai ở đây.
     */
    public static void assertNoForbiddenKeys(JsonNode node, Set<String> forbiddenKeys, String listName) {
        Set<String> violations = new TreeSet<>();
        collect(node, "$", forbiddenKeys, violations);
        assertThat(violations)
                .as("forbidden keys under the '%s' list (JSON paths only, values withheld)", listName)
                .isEmpty();
    }

    private static void collect(
            JsonNode node, String path, Set<String> forbiddenKeys, Set<String> violations) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String childPath = path + "." + field.getKey();
                if (forbiddenKeys.contains(field.getKey())) {
                    violations.add(childPath);
                }
                collect(field.getValue(), childPath, forbiddenKeys, violations);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collect(node.get(i), path + "[" + i + "]", forbiddenKeys, violations);
            }
        }
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("ForbiddenKeysAssert got malformed JSON", e);
        }
    }
}
