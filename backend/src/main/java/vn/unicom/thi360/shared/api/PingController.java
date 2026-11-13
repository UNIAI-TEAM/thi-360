package vn.unicom.thi360.shared.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "System", description = "Kiểm tra trạng thái hệ thống")
public class PingController {

    private final Clock clock;

    public PingController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/ping")
    @Operation(summary = "Kiểm tra kết nối API")
    public PingResponse ping() {
        return new PingResponse("ok", Instant.now(clock));
    }

    @Schema(description = "Phản hồi kiểm tra kết nối")
    public record PingResponse(
            @Schema(description = "Trạng thái dịch vụ", example = "ok")
            String status,
            @Schema(description = "Thời gian máy chủ (UTC)", example = "2026-11-13T10:00:00Z")
            Instant time
    ) {
    }
}
