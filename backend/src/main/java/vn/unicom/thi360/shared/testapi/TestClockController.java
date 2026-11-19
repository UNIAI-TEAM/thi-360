package vn.unicom.thi360.shared.testapi;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;

/**
 * Điều khiển {@link MutableClock} cho E2E (plan B5, B6). Chỉ có ở profile {@code e2e}, không có
 * trong OpenAPI. Gọi không cần đăng nhập.
 */
@TestApiController
@Hidden
@RequestMapping(value = TestApiPaths.PREFIX + "/clock", produces = MediaType.APPLICATION_JSON_VALUE)
public class TestClockController {

    private static final String ONE_OF = "Exactly one of at, advanceSeconds, reset: true is required";

    /** Một tài liệu JSON trọn vẹn: khóa trùng và dữ liệu thừa sau gốc là lỗi, không bị bỏ qua. */
    private static final ObjectMapper STRICT_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final MutableClock clock;

    public TestClockController(MutableClock clock) {
        this.clock = clock;
    }

    @GetMapping
    public ClockResponse now() {
        return new ClockResponse(clock.instant());
    }

    /**
     * Đúng một trường trong {@code at} (chuỗi ISO-8601), {@code advanceSeconds} (số nguyên không
     * âm), {@code reset} (đúng {@code true}); mọi thân khác là 400 và không đổi đồng hồ: rỗng, không
     * phải đối tượng, thêm trường (kể cả null, false, trường lạ), khóa trùng, sai kiểu (số thập
     * phân, chuỗi thay số, chuỗi "true"), giờ kết quả ngoài {@link MutableClock#SUPPORTED_MIN}..
     * {@link MutableClock#SUPPORTED_MAX}. Thân được đọc thô vì các mặc định của Jackson (bỏ qua trường
     * lạ, cắt phần thập phân, ép chuỗi thành boolean) làm một thân sai vẫn đổi đồng hồ; không đổi
     * cấu hình mapper chung của các API khác.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClockResponse set(@RequestBody(required = false) byte[] body) {
        JsonNode request = parse(body);
        if (!request.isObject() || request.size() != 1) {
            throw new ValidationException(ONE_OF);
        }
        Map.Entry<String, JsonNode> field = request.properties().iterator().next();
        String name = field.getKey();
        JsonNode value = field.getValue();
        try {
            return switch (name) {
                case "at" -> new ClockResponse(clock.setAt(parseAt(value)));
                case "advanceSeconds" -> new ClockResponse(clock.advance(parseAdvance(value)));
                case "reset" -> {
                    if (!value.isBoolean() || !value.booleanValue()) {
                        throw invalidField("reset", "INVALID_RESET", "reset must be the boolean true");
                    }
                    yield new ClockResponse(clock.reset());
                }
                default -> throw new ValidationException(ONE_OF);
            };
        } catch (MutableClock.OutOfRangeException e) {
            throw invalidField(name, "OUT_OF_RANGE", e.getMessage());
        }
    }

    private static JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new ValidationException(ONE_OF);
        }
        try {
            JsonNode node = STRICT_MAPPER.readTree(body);
            if (node == null || node.isMissingNode()) {
                throw new ValidationException(ONE_OF);
            }
            return node;
        } catch (IOException e) {
            throw new ValidationException("Request body must be one JSON object without duplicate keys");
        }
    }

    private static Instant parseAt(JsonNode value) {
        if (!value.isTextual()) {
            throw invalidField("at", "INVALID_INSTANT", "at must be an ISO-8601 instant string");
        }
        try {
            return Instant.parse(value.textValue());
        } catch (DateTimeParseException e) {
            throw invalidField("at", "INVALID_INSTANT",
                    "at must be an ISO-8601 instant, e.g. 2026-11-19T10:00:00Z");
        }
    }

    private static Duration parseAdvance(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalidField("advanceSeconds", "INVALID_SECONDS", "advanceSeconds must be an integer number");
        }
        long seconds = value.longValue();
        if (seconds < 0) {
            throw invalidField("advanceSeconds", "NEGATIVE", "advanceSeconds must not be negative");
        }
        return Duration.ofSeconds(seconds);
    }

    private static ValidationException invalidField(String field, String code, String message) {
        return new ValidationException(message, List.of(new FieldError(field, code, message)));
    }

    public record ClockResponse(Instant serverNow) {
    }
}
