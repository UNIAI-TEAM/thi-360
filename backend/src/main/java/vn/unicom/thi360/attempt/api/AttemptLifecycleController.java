package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.AttemptLifecycleService;

@RestController
@RequestMapping(value = "/api/v1/me/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attempts", description = "Chuẩn bị, bắt đầu và đọc trạng thái lượt thi")
public class AttemptLifecycleController {

    private final AttemptLifecycleService lifecycleService;

    public AttemptLifecycleController(AttemptLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/{attemptId}/parts/{partId}/finish")
    @Operation(summary = "Hoàn thành phần hiện tại và mở phần kế tiếp của lượt thi")
    public FinishPartResponse finishPart(
            @PathVariable UUID attemptId,
            @PathVariable UUID partId,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId,
            @Valid @RequestBody(required = false) FinishPartRequest request) {
        return lifecycleService.finishPart(attemptId, partId, sessionId, request);
    }

    @PostMapping("/{attemptId}/submit")
    @Operation(summary = "Nộp bài và kết thúc lượt thi")
    public SubmitAttemptResponse submit(
            @PathVariable UUID attemptId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId,
            @Valid @RequestBody(required = false) SubmitAttemptRequest request) {
        return lifecycleService.submitAttempt(attemptId, idempotencyKey, sessionId, request);
    }
}
