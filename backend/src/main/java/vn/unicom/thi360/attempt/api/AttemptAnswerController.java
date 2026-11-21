package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.AnswerService;
import vn.unicom.thi360.shared.api.ApiError;

@RestController
@RequestMapping(value = "/api/v1/me/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attempts", description = "Chuẩn bị, bắt đầu và đọc trạng thái lượt thi")
public class AttemptAnswerController {

    private final AnswerService answerService;

    public AttemptAnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PutMapping("/{attemptId}/answers")
    @Operation(summary = "Lưu nháp câu trả lời theo seq, cờ đánh dấu xem lại và vị trí đang xem")
    @ApiResponse(responseCode = "200", description = "Xử lý từng thay đổi câu trả lời theo seq")
    @ApiResponse(
            responseCode = "429",
            description = "Vượt quá 20 yêu cầu trong 10 giây trên mỗi lượt thi (kèm Retry-After)",
            headers =
                    @Header(
                            name = HttpHeaders.RETRY_AFTER,
                            description = "Seconds until the rate-limit window refills",
                            schema = @Schema(implementation = Integer.class)),
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public SaveAnswersResponse saveAnswers(
            @PathVariable UUID attemptId,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId,
            @Valid @RequestBody(required = false) SaveAnswersRequest request) {
        return answerService.saveAnswers(attemptId, sessionId, request);
    }
}
