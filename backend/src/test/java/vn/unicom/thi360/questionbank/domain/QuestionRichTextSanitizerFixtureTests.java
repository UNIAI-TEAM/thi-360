package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer.Result;

/**
 * Bảng mẫu chung của trình soạn (UNI-2638, spec 2026-11-17 mục 3): mọi mẫu
 * {@code valid=true} qua bộ lọc không vi phạm và kết quả bằng đúng chuỗi đầu vào; mọi mẫu
 * {@code valid=false} báo đúng tập vi phạm {@code (tag, attribute)} — so như tập hợp, không
 * kể thứ tự. Đọc tệp theo đường tương đối từ {@code backend/}; ảnh dùng id cố định của bảng
 * mẫu qua resolver giả trả {@code /api/v1/admin/question-assets/<id>}.
 */
class QuestionRichTextSanitizerFixtureTests {

    private static final Path FIXTURE =
            Path.of("..", "docs", "specs", "fixtures", "rich-text-samples.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static UUID assetId;
    private static String assetPath;
    private static QuestionHtmlSanitizer sanitizer;

    private record Sample(String name, boolean valid, boolean allowImages, String html,
            Set<String> expectedViolations) {}

    static Stream<Arguments> samples() throws Exception {
        JsonNode root = MAPPER.readTree(Files.readString(FIXTURE));
        assetId = UUID.fromString(root.get("assetId").asText());
        assetPath = "/api/v1/admin/question-assets/" + assetId;
        sanitizer = new QuestionHtmlSanitizer(
                id -> id.equals(assetId) ? Optional.of(assetPath) : Optional.empty());
        return StreamSupport.stream(root.get("samples").spliterator(), false)
                .map(node -> new Sample(
                        node.get("name").asText(),
                        node.get("valid").asBoolean(),
                        node.path("allowImages").asBoolean(true),
                        node.get("html").asText(),
                        violationsOf(node)))
                .map(Arguments::of);
    }

    /** Tập {@code tag} hoặc {@code tag+attribute} của mẫu vi phạm. */
    private static Set<String> violationsOf(JsonNode sample) {
        Set<String> result = new TreeSet<>();
        JsonNode violations = sample.path("violations");
        if (violations.isArray()) {
            for (JsonNode v : violations) {
                String tag = v.get("tag").asText();
                result.add(v.hasNonNull("attribute")
                        ? tag + "|" + v.get("attribute").asText()
                        : tag);
            }
        }
        return result;
    }

    private static Set<String> reported(Result result) {
        Set<String> reported = new TreeSet<>();
        for (var v : result.violations()) {
            reported.add(v.attribute() == null ? v.tag() : v.tag() + "|" + v.attribute());
        }
        return reported;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void sampleBehavesAsFixtureSays(Sample sample) {
        Result result = sanitizer.clean(sample.html(), sample.allowImages());
        if (sample.valid()) {
            assertTrue(result.violations().isEmpty(),
                    () -> sample.name() + " reported " + result.violations());
            assertEquals(sample.html(), result.html(), sample.name());
        } else {
            assertEquals(sample.expectedViolations(), reported(result),
                    () -> sample.name() + " html: " + result.html());
        }
    }
}
