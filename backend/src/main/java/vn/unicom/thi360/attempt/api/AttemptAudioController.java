package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.api.AttemptAudioExceptionAdvice.AudioPlayInProgressResponse;
import vn.unicom.thi360.attempt.api.AttemptAudioExceptionAdvice.AudioSeekRejectedResponse;
import vn.unicom.thi360.attempt.application.AttemptAudioService;
import vn.unicom.thi360.attempt.domain.AudioEndReason;
import vn.unicom.thi360.attempt.domain.AudioPlayState;

@RestController
@RequestMapping(
        value = "/api/v1/me/attempts/{attemptId}/audio/{assetId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attempts", description = "Chuẩn bị, bắt đầu và đọc trạng thái lượt thi")
public class AttemptAudioController {

    private final AttemptAudioService attemptAudioService;

    public AttemptAudioController(AttemptAudioService attemptAudioService) {
        this.attemptAudioService = attemptAudioService;
    }

    @PostMapping("/plays")
    @Operation(summary = "Mở lần nghe mới cho tệp audio hoặc trả lại kết quả khi gửi lặp requestId")
    @ApiResponse(responseCode = "200", description = "Trạng thái lần nghe sau khi mở hoặc gửi lại")
    @ApiResponse(
            responseCode = "409",
            description =
                    "AUDIO_PLAY_IN_PROGRESS (kèm playId), AUDIO_PLAYS_EXHAUSTED, PART_CLOSED, TIME_UP hoặc ATTEMPT_SESSION_REVOKED",
            content = @Content(schema = @Schema(implementation = AudioPlayInProgressResponse.class)))
    public AttemptAudioPlayResponse openAudioPlay(
            @PathVariable UUID attemptId,
            @PathVariable UUID assetId,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId,
            @Valid @RequestBody StartAudioPlayRequest request) {
        return attemptAudioService.openPlay(attemptId, assetId, sessionId, request.requestId());
    }

    @PostMapping("/plays/{playId}/resume")
    @Operation(summary = "Phát tiếp lần nghe đang dở từ vị trí đã lưu và gia hạn lease")
    @ApiResponse(responseCode = "200", description = "Trạng thái lần nghe sau khi phát tiếp")
    public AttemptAudioPlayResponse resumeAudioPlay(
            @PathVariable UUID attemptId,
            @PathVariable UUID assetId,
            @PathVariable UUID playId,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId) {
        return attemptAudioService.resumePlay(attemptId, assetId, playId, sessionId);
    }

    @PutMapping("/plays/{playId}/progress")
    @Operation(summary = "Cập nhật tiến độ phát audio và kiểm tra giới hạn chống tua")
    @ApiResponse(responseCode = "200", description = "Trạng thái lần nghe sau khi cập nhật tiến độ")
    @ApiResponse(
            responseCode = "409",
            description =
                    "AUDIO_SEEK_REJECTED (kèm positionMs đã lưu), AUDIO_PLAY_STALE, PART_CLOSED, TIME_UP hoặc ATTEMPT_SESSION_REVOKED",
            content = @Content(schema = @Schema(implementation = AudioSeekRejectedResponse.class)))
    public AttemptAudioPlayResponse updateAudioProgress(
            @PathVariable UUID attemptId,
            @PathVariable UUID assetId,
            @PathVariable UUID playId,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId,
            @Valid @RequestBody AudioProgressRequest request) {
        return attemptAudioService.recordProgress(
                attemptId, assetId, playId, sessionId, request.positionMs());
    }

    @PostMapping("/plays/{playId}/end")
    @Operation(summary = "Kết thúc lần nghe audio đang phát")
    @ApiResponse(responseCode = "200", description = "Trạng thái kết thúc của lần nghe audio")
    public AttemptAudioPlayResponse endAudioPlay(
            @PathVariable UUID attemptId,
            @PathVariable UUID assetId,
            @PathVariable UUID playId,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId) {
        return attemptAudioService.endPlay(attemptId, assetId, playId, sessionId);
    }

    @Schema(description = "Yêu cầu mở lần nghe mới cho tệp audio (BS §17.2)")
    public record StartAudioPlayRequest(
            @Schema(
                            description = "Khóa chống gửi lặp do trình duyệt tạo cho mỗi lần bấm Nghe",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank(message = "{validation.attempt.audio.requestId.notBlank}")
                    @Size(max = 128, message = "{validation.attempt.audio.requestId.size}")
                    String requestId) {}

    @Schema(description = "Yêu cầu cập nhật vị trí phát hiện tại của audio (BS §17.2)")
    public record AudioProgressRequest(
            @Schema(
                            description = "Vị trí phát hiện tại tính bằng mili giây",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull(message = "{validation.attempt.audio.positionMs.notNull}")
                    Long positionMs) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Trạng thái lần nghe của một tệp audio trong lượt thi (BS §17.2)")
    public record AttemptAudioPlayResponse(
            @Schema(description = "Id của lần nghe", nullable = true) UUID playId,
            @Schema(
                            description = "Số lần nghe đã dùng",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    int playsUsed,
            @Schema(
                            description = "Số lần nghe còn lại",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    int playsLeft,
            @Schema(
                            description = "Vị trí phát đã lưu tính bằng mili giây",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    long positionMs,
            @Schema(
                            description = "Thời lượng tệp audio do máy chủ đo tính bằng mili giây",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    long durationMs,
            @Schema(
                            description = "Trạng thái phát audio",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    AudioPlayState state,
            @Schema(description = "Lý do kết thúc khi state là ENDED", nullable = true)
                    AudioEndReason endReason) {}
}
