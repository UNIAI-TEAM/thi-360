package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.AttemptChoiceService;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.PartState;

@RestController
@RequestMapping(value = "/api/v1/me/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attempts", description = "Chuẩn bị, bắt đầu và đọc trạng thái lượt thi")
public class AttemptChoiceController {

    private final AttemptChoiceService attemptChoiceService;

    public AttemptChoiceController(AttemptChoiceService attemptChoiceService) {
        this.attemptChoiceService = attemptChoiceService;
    }

    @PostMapping("/{attemptId}/parts/{partId}/choice")
    @Operation(
            operationId = "confirmPartChoice",
            summary = "Xác nhận lựa chọn phần tự chọn (phần 3 HSA, QDA) và sinh đề ở nền")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Trạng thái hiện tại khi gửi lại cùng khóa idempotency hoặc lựa chọn đã khóa giống hệt",
                content = @Content(schema = @Schema(implementation = PartChoiceResponse.class))),
        @ApiResponse(
                responseCode = "202",
                description = "Đã khóa lựa chọn và đưa việc sinh phần vào hàng chờ",
                content = @Content(schema = @Schema(implementation = PartChoiceResponse.class)))
    })
    public ResponseEntity<PartChoiceResponse> confirmChoice(
            @PathVariable UUID attemptId,
            @PathVariable UUID partId,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = "Idempotency-Key",
                            required = true,
                            description = "Khóa chống gửi lặp bắt buộc cho thao tác chọn phần 3")
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId,
            @Valid @RequestBody(required = false) ConfirmPartChoiceRequest request) {
        return attemptChoiceService.confirmChoice(
                attemptId, partId, idempotencyKey, sessionId, request);
    }

    @PostMapping("/{attemptId}/parts/{partId}/retry")
    @Operation(
            operationId = "retryPartGeneration",
            summary = "Thử lại sinh đề cho phần thi đang ở trạng thái GENERATION_FAILED")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Trạng thái hiện tại khi gửi lại cùng khóa idempotency",
                content = @Content(schema = @Schema(implementation = PartChoiceResponse.class))),
        @ApiResponse(
                responseCode = "202",
                description = "Đã đưa yêu cầu sinh lại phần vào hàng chờ",
                content = @Content(schema = @Schema(implementation = PartChoiceResponse.class)))
    })
    public ResponseEntity<PartChoiceResponse> retry(
            @PathVariable UUID attemptId,
            @PathVariable UUID partId,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = "Idempotency-Key",
                            required = true,
                            description = "Khóa chống gửi lặp bắt buộc cho thao tác thử lại sinh phần")
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestHeader(value = "X-Attempt-Session", required = false) UUID sessionId,
            @Valid @RequestBody(required = false) RetryPartRequest request) {
        return attemptChoiceService.retryGeneration(
                attemptId, partId, idempotencyKey, sessionId, request);
    }

    @Schema(description = "Yêu cầu xác nhận lựa chọn phần tự chọn sau khi bắt đầu (LT §6.1)")
    public record ConfirmPartChoiceRequest(
            @Schema(description = "Mã nhánh, phương án hoặc tổ hợp môn đã chọn", nullable = true)
                    String optionCode,
            @Schema(description = "Danh sách mã môn khi chọn SUBJECT_CHOICE (HSA Khoa học)", nullable = true)
                    @Size(max = 10, message = "{validation.attempt.choice.subjectCodes.max}")
                    List<String> subjectCodes) {}

    @Schema(description = "Yêu cầu thử lại sinh đề cho phần bị lỗi (LT §6.1)")
    public record RetryPartRequest(
            @Schema(description = "Lý do thử lại tùy chọn", nullable = true)
                    @Size(max = 200, message = "{validation.attempt.retry.reason.size}")
                    String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Trạng thái phần tự chọn sau khi xác nhận lựa chọn hoặc thử lại sinh đề")
    public record PartChoiceResponse(
            @Schema(description = "Giờ máy chủ hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
                    Instant serverNow,
            @Schema(description = "Id lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
                    UUID attemptId,
            @Schema(description = "Trạng thái lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
                    AttemptState attemptState,
            @Schema(description = "Id phần thi", requiredMode = Schema.RequiredMode.REQUIRED)
                    UUID partId,
            @Schema(description = "Mã phần thi", requiredMode = Schema.RequiredMode.REQUIRED)
                    String partCode,
            @Schema(description = "Trạng thái phần thi", requiredMode = Schema.RequiredMode.REQUIRED)
                    PartState state,
            @Schema(description = "Lựa chọn đã khóa của phần", nullable = true)
                    Map<String, Object> choice,
            @Schema(description = "Thời điểm khóa lựa chọn", nullable = true)
                    Instant choiceConfirmedAt,
            @Schema(description = "Hạn chờ chọn phần", nullable = true)
                    Instant choiceDeadlineAt,
            @Schema(description = "Thời điểm bắt đầu làm phần", nullable = true)
                    Instant startedAt,
            @Schema(description = "Hạn kết thúc phần", nullable = true)
                    Instant deadlineAt,
            @Schema(description = "Số lần sinh lỗi liên tiếp", requiredMode = Schema.RequiredMode.REQUIRED)
                    int generationTries,
            @Schema(description = "Mã lỗi sinh đề gần nhất nếu có", nullable = true)
                    String generationError) {

        public PartChoiceResponse {
            choice = choice == null ? null : Map.copyOf(choice);
        }
    }
}
