package vn.unicom.thi360.shared.testapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
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

    private final MutableClock clock;

    public TestClockController(MutableClock clock) {
        this.clock = clock;
    }

    @GetMapping
    public ClockResponse now() {
        return new ClockResponse(clock.instant());
    }

    /** Đúng một trong {@code at}, {@code advanceSeconds}, {@code reset: true}; ngược lại 400. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClockResponse set(@RequestBody(required = false) ClockRequest request) {
        if (request == null) {
            throw new ValidationException("Exactly one of at, advanceSeconds, reset is required");
        }
        List<String> given = new ArrayList<>(3);
        if (request.at() != null) {
            given.add("at");
        }
        if (request.advanceSeconds() != null) {
            given.add("advanceSeconds");
        }
        if (Boolean.TRUE.equals(request.reset())) {
            given.add("reset");
        }
        if (given.size() != 1) {
            throw new ValidationException("Exactly one of at, advanceSeconds, reset: true is required");
        }
        return switch (given.getFirst()) {
            case "at" -> new ClockResponse(clock.setAt(parseAt(request.at())));
            case "advanceSeconds" -> new ClockResponse(clock.advance(parseAdvance(request.advanceSeconds())));
            default -> new ClockResponse(clock.reset());
        };
    }

    private static Instant parseAt(String at) {
        try {
            return Instant.parse(at);
        } catch (DateTimeParseException e) {
            throw new ValidationException(
                    "at must be an ISO-8601 instant, e.g. 2026-11-19T10:00:00Z",
                    List.of(new FieldError("at", "INVALID_INSTANT", "at must be an ISO-8601 instant")));
        }
    }

    private static Duration parseAdvance(long seconds) {
        if (seconds < 0) {
            throw new ValidationException(
                    "advanceSeconds must not be negative",
                    List.of(new FieldError("advanceSeconds", "NEGATIVE", "advanceSeconds must not be negative")));
        }
        return Duration.ofSeconds(seconds);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ClockRequest(String at, Long advanceSeconds, Boolean reset) {
    }

    public record ClockResponse(Instant serverNow) {
    }
}
