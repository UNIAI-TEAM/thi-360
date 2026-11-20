package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.AttemptPrepareService;
import vn.unicom.thi360.attempt.application.AttemptStartService;
import vn.unicom.thi360.attempt.application.AttemptStateService;

@RestController
@RequestMapping(value = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attempts", description = "Chuẩn bị, bắt đầu và đọc trạng thái lượt thi")
public class AttemptController {

    private final AttemptPrepareService prepareService;
    private final AttemptStartService startService;
    private final AttemptStateService stateService;

    public AttemptController(
            AttemptPrepareService prepareService,
            AttemptStartService startService,
            AttemptStateService stateService) {
        this.prepareService = prepareService;
        this.startService = startService;
        this.stateService = stateService;
    }

    @GetMapping("/entitlements/{entitlementId}/prepare")
    @Operation(summary = "Chuẩn bị trước khi bắt đầu lượt thi theo quyền dùng")
    public AttemptPrepareResponse prepare(@PathVariable UUID entitlementId) {
        return prepareService.prepare(entitlementId);
    }

    @PostMapping("/attempts")
    @Operation(summary = "Bắt đầu lượt thi mới hoặc nối lại lượt đang mở (sinh đề chạy nền)")
    public ResponseEntity<StartAttemptResponse> start(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StartAttemptRequest request) {
        return startService.startAttempt(idempotencyKey, request);
    }

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "Đọc trạng thái và nội dung các phần đã mở của lượt thi")
    public AttemptStateResponse getState(@PathVariable UUID attemptId) {
        return stateService.getState(attemptId);
    }
}
