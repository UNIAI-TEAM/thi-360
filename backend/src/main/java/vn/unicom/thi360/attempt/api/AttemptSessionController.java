package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import vn.unicom.thi360.attempt.application.EditSessionService;

@RestController
@RequestMapping(value = "/api/v1/me/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attempts", description = "Chuẩn bị, bắt đầu và đọc trạng thái lượt thi")
public class AttemptSessionController {

    private final EditSessionService editSessionService;

    public AttemptSessionController(EditSessionService editSessionService) {
        this.editSessionService = editSessionService;
    }

    @PostMapping("/{attemptId}/session")
    @Operation(summary = "Nhận, khôi phục hoặc tiếp quản phiên sửa bài của lượt thi")
    @ApiResponse(responseCode = "200", description = "Nhận phiên sửa thành công")
    @ApiResponse(
            responseCode = "409",
            description = "ATTEMPT_OPEN_ELSEWHERE (kèm lastSeenAt), TIME_UP hoặc ATTEMPT_NOT_OPEN",
            content = @Content(schema = @Schema(implementation = AttemptSessionConflictResponse.class)))
    public ClaimSessionResponse claimSession(
            @PathVariable UUID attemptId,
            @Valid @RequestBody(required = false) ClaimSessionRequest request) {
        return editSessionService.claimSession(attemptId, request);
    }

    @PostMapping("/{attemptId}/heartbeat")
    @Operation(summary = "Cập nhật thời điểm hoạt động của phiên sửa bài")
    public HeartbeatResponse heartbeat(
            @PathVariable UUID attemptId,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId) {
        return editSessionService.heartbeat(attemptId, sessionId);
    }
}
