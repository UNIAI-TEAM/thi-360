package vn.unicom.thi360.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
     * ({@code levelItemId}, {@code difficulty(…)}, kể cả tên phục vụ {@code mapidIds} và
     * {@code trueFalseTable}). {@code points} của câu, {@code inputHint}, {@code wordLimit} được
     * phép nên không nằm trong danh sách; {@code points} của từng mệnh đề Đúng/Sai bị cấm theo
     * đường dẫn ({@link #ATTEMPT_FORBIDDEN_SCOPED_KEYS}).
     */
    public static final Set<String> ATTEMPT_FORBIDDEN_KEYS = Set.of(
            "correct",
            "answer",
            "answers",
            "pairs",
            "scoreTable",
            "trueFalseTable",
            "solution",
            "gradingGuide",
            "rubric",
            "sampleAnswer",
            "commonMistakes",
            "knowledge",
            "knowledgeItemIds",
            "mapId",
            "mapIds",
            "mapidIds",
            "levelItemId",
            "difficulty",
            "difficultyCode");

    /**
     * Khóa cấm theo vị trí, dạng {@code mảng[].khóa}: khóa chỉ bị báo khi nó là trường trực tiếp
     * của một phần tử trong mảng tên {@code mảng}. {@code statements[].points} là điểm từng ý của
     * Đúng/Sai (LT 7.3), còn {@code points} ở cấp câu vẫn được phép.
     */
    public static final Set<String> ATTEMPT_FORBIDDEN_SCOPED_KEYS = Set.of("statements[].points");

    /**
     * TM 10.1 — phản hồi của bảng chiếu không bao giờ chứa: id hoặc nội dung câu
     * hỏi ({@code question(s)…}, {@code body}, {@code snapshot}), đáp án và lời giải
     * (toàn bộ danh sách lượt thi), nguồn câu của khối ({@code source…}), đề chứng
     * minh ({@code evidence}, {@code witness…}) và đề xem trước ({@code preview…}). Nguồn câu của
     * khối gồm mọi trường ràng buộc chọn câu của {@code QuestionBlock}: {@code knowledgeScope},
     * {@code matrix}, {@code topicQuotas}, {@code tagCodes}, {@code slots}, {@code childConditions}
     * ({@code matrix} và {@code topicQuotas} là hạn ngạch chọn câu theo MAPID/khối lớp/độ khó: bị cấm
     * ở mọi vị trí vì chi tiết sản phẩm theo TM 10.1 không có khóa nào trùng tên). Các trường còn
     * lại của khối được phép, mỗi trường kèm lý do ở {@link #STOREFRONT_ALLOWED_BLOCK_FIELDS}. Danh
     * sách này cũng chứa mọi khóa của danh sách lượt thi.
     */
    public static final Set<String> STOREFRONT_FORBIDDEN_KEYS;

    /** Khóa cấm theo vị trí của bảng chiếu: gồm {@link #ATTEMPT_FORBIDDEN_SCOPED_KEYS}. */
    public static final Set<String> STOREFRONT_FORBIDDEN_SCOPED_KEYS = ATTEMPT_FORBIDDEN_SCOPED_KEYS;

    /**
     * Trường của {@code QuestionBlock} được phép hiện ở bảng chiếu, kèm lý do. Cùng với phần nguồn
     * câu ở {@link #STOREFRONT_FORBIDDEN_KEYS} phải phủ đủ mọi trường của record: thêm trường mới vào
     * {@code QuestionBlock} thì {@code ForbiddenKeysAssertTests} đỏ cho tới khi trường đó được xếp vào
     * một trong hai nơi.
     */
    public static final Map<String, String> STOREFRONT_ALLOWED_BLOCK_FIELDS = Map.ofEntries(
            Map.entry("id", "định danh khối, không chỉ tới câu hỏi"),
            Map.entry("name", "tên phần hiển thị cho học viên"),
            Map.entry("questionType", "dạng câu: TM 10.1 hiển thị dạng câu và số câu từng dạng"),
            Map.entry("questionCount", "số câu: TM 10.1 hiển thị số câu"),
            Map.entry("setCount", "số bộ câu: một dạng số câu"),
            Map.entry("setSize", "số câu mỗi bộ: một dạng số câu"),
            Map.entry("childAnswerType", "dạng câu con của bộ câu: cùng loại với questionType"),
            Map.entry("requiredPointsPerQuestion", "điểm mỗi câu: TM 10.1 hiển thị thang điểm"),
            Map.entry("expectedScoring", "cách chấm dự kiến: TM 10.1 hiển thị cách chấm"),
            Map.entry("itemShape", "hình dạng câu (số mệnh đề, số lựa chọn): mô tả dạng câu, không chỉ nguồn"),
            Map.entry("answerFormats", "kiểu đáp án cho phép: mô tả dạng câu, không chỉ nguồn"),
            Map.entry("templateGroupCode", "mã nhóm mẫu: nhãn dựng bài, không chọn câu theo kiến thức hay thẻ"),
            Map.entry("answerTypeLocked", "cờ khóa của trình soạn, không có dữ liệu câu"),
            Map.entry("shuffleUnits", "cờ trộn, không có dữ liệu câu"),
            Map.entry("shuffleEditable", "cờ trộn, không có dữ liệu câu"),
            Map.entry("omitted", "cờ phần tạm bỏ qua: TM 10.1 ghi chú nhóm tự luận bỏ qua"),
            Map.entry("positionRange", "khoảng vị trí câu trong phần: thứ tự làm bài"));

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
                "knowledgeScope",
                "matrix",
                "topicQuotas",
                "tagCodes",
                "slots",
                "childConditions",
                "evidence",
                "witness",
                "witnessPicks",
                "preview",
                "previewId",
                "previewPaper"));
        STOREFRONT_FORBIDDEN_KEYS = Set.copyOf(storefront);
    }

    /** Chỉ nhận một tài liệu JSON trọn vẹn: không khóa trùng, không dữ liệu thừa sau gốc. */
    private static final ObjectMapper STRICT_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /**
     * Như {@link #STRICT_MAPPER} nhưng cho khóa trùng (khóa sau ghi đè khóa trước): chỉ dùng để phân
     * loại lỗi, khi tài liệu chỉ bị từ chối vì khóa trùng.
     */
    private static final ObjectMapper DUPLICATES_ALLOWED_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private ForbiddenKeysAssert() {
    }

    /** Phân tích chuỗi JSON rồi kiểm theo danh sách lượt thi (LT 7.3). */
    public static void assertNoAttemptForbiddenKeys(String json) {
        assertNoAttemptForbiddenKeys(parse(json));
    }

    /** Kiểm theo danh sách lượt thi (LT 7.3) trên nút JSON đã phân tích. */
    public static void assertNoAttemptForbiddenKeys(JsonNode node) {
        assertNoForbiddenKeys(node, ATTEMPT_FORBIDDEN_KEYS, ATTEMPT_FORBIDDEN_SCOPED_KEYS, "attempt");
    }

    /** Phân tích chuỗi JSON rồi kiểm theo danh sách bảng chiếu (TM 10.1). */
    public static void assertNoStorefrontForbiddenKeys(String json) {
        assertNoStorefrontForbiddenKeys(parse(json));
    }

    /** Kiểm theo danh sách bảng chiếu (TM 10.1) trên nút JSON đã phân tích. */
    public static void assertNoStorefrontForbiddenKeys(JsonNode node) {
        assertNoForbiddenKeys(node, STOREFRONT_FORBIDDEN_KEYS, STOREFRONT_FORBIDDEN_SCOPED_KEYS, "storefront");
    }

    /**
     * Kiểm với danh sách khóa tự chọn: dành cho phản hồi phải đạt cả hai danh
     * sách hoặc một danh sách mở rộng mà task vẫn khai ở đây.
     */
    public static void assertNoForbiddenKeys(JsonNode node, Set<String> forbiddenKeys, String listName) {
        assertNoForbiddenKeys(node, forbiddenKeys, Set.of(), listName);
    }

    /** Như trên, kèm khóa cấm theo vị trí (dạng {@code mảng[].khóa}). */
    public static void assertNoForbiddenKeys(
            JsonNode node, Set<String> forbiddenKeys, Set<String> forbiddenScopedKeys, String listName) {
        requireContainer(node);
        Set<String> violations = new TreeSet<>();
        collect(node, "$", null, forbiddenKeys, forbiddenScopedKeys, violations);
        assertThat(violations)
                .as("forbidden keys under the '%s' list (JSON paths only, values withheld)", listName)
                .isEmpty();
    }

    /**
     * {@code owningArrayKey}: tên khóa của mảng chứa trực tiếp nút này (nút là phần tử của mảng đó),
     * hoặc null nếu nút không phải phần tử của mảng có tên.
     */
    private static void collect(
            JsonNode node, String path, String owningArrayKey,
            Set<String> forbiddenKeys, Set<String> forbiddenScopedKeys, Set<String> violations) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = field.getKey();
                String childPath = path + "." + key;
                if (forbiddenKeys.contains(key)
                        || (owningArrayKey != null && forbiddenScopedKeys.contains(owningArrayKey + "[]." + key))) {
                    violations.add(childPath);
                }
                JsonNode value = field.getValue();
                if (value.isArray()) {
                    for (int i = 0; i < value.size(); i++) {
                        collect(value.get(i), childPath + "[" + i + "]", key, forbiddenKeys,
                                forbiddenScopedKeys, violations);
                    }
                } else {
                    collect(value, childPath, null, forbiddenKeys, forbiddenScopedKeys, violations);
                }
            }
        } else if (node.isArray()) {
            // Mảng gốc hoặc mảng lồng mảng: phần tử không thuộc mảng có tên.
            for (int i = 0; i < node.size(); i++) {
                collect(node.get(i), path + "[" + i + "]", null, forbiddenKeys, forbiddenScopedKeys, violations);
            }
        }
    }

    private static void requireContainer(JsonNode node) {
        if (node == null || !(node.isObject() || node.isArray())) {
            throw new AssertionError("expected a JSON object or array document to scan, got none");
        }
    }

    /**
     * Thông báo và nguyên nhân không mang nội dung phản hồi: Jackson đưa đoạn dữ liệu lỗi vào
     * thông báo, nên ngoại lệ gốc bị bỏ.
     */
    private static JsonNode parse(String json) {
        if (json == null) {
            throw new AssertionError("expected a JSON document to scan, got null");
        }
        JsonNode node;
        try {
            node = STRICT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AssertionError(onlyDuplicateKeys(json)
                    ? "duplicate object keys in the document (a later key silently overwrites an earlier one,"
                            + " hiding it from the scan); content withheld"
                    : "not one complete JSON document (malformed or trailing data); content withheld");
        }
        requireContainer(node);
        return node;
    }

    /** Tài liệu chỉ bị từ chối vì khóa trùng: bỏ kiểm khóa trùng thì phân tích được trọn vẹn. */
    private static boolean onlyDuplicateKeys(String json) {
        try {
            DUPLICATES_ALLOWED_MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
