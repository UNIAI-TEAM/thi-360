package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.AttemptAdminService;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * Admin endpoints for attempt inspection, regrading {@code GRADING_ERROR} attempts, marking system
 * faults, and issuing compensation attempts (LT §12, F18, F20).
 */
@Tag(name = "Admin Attempts", description = "Vận hành lượt thi: tra cứu, chấm lại, đánh dấu hỏng, cấp bù")
@RestController
@RequestMapping(value = "/api/v1/admin/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminAttemptController {

    private final AttemptAdminService attemptAdminService;

    public AdminAttemptController(AttemptAdminService attemptAdminService) {
        this.attemptAdminService = Objects.requireNonNull(attemptAdminService);
    }

    @Operation(operationId = "listAdminAttempts", summary = "Danh sách lượt thi cho vận hành")
    @RequirePermission(Permissions.ATTEMPT_VIEW)
    @GetMapping
    public PageResponse<AdminAttemptSummary> listAttempts(
            @Parameter(description = "User ID (UUID) hoặc tên đăng nhập đang hoạt động")
            @RequestParam(required = false) String user,
            @Parameter(description = "ID bài thi (UUID) hoặc từ khóa tên bài thi")
            @RequestParam(required = false) String exam,
            @Parameter(description = "Trạng thái lượt thi")
            @RequestParam(required = false) AttemptState state,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return attemptAdminService.listAttempts(user, exam, state, from, to, page, size);
    }

    @Operation(operationId = "getAdminAttemptDetail", summary = "Chi tiết lượt thi, các phần, sự kiện và bài làm cuối")
    @RequirePermission(Permissions.ATTEMPT_VIEW)
    @GetMapping("/{id}")
    public AdminAttemptDetailResponse getAttemptDetail(@PathVariable UUID id) {
        return attemptAdminService.getAttemptDetail(id);
    }

    @Operation(operationId = "regradeAdminAttempt", summary = "Chấm lại lượt thi đang ở trạng thái GRADING_ERROR")
    @RequirePermission(Permissions.GRADING_MODIFY)
    @PostMapping("/{id}/regrade")
    public AdminAttemptActionResponse regrade(@PathVariable UUID id) {
        return attemptAdminService.regrade(id);
    }

    @Operation(operationId = "faultAdminAttempt", summary = "Đánh dấu lượt thi lỗi hệ thống và đóng lượt nếu đang mở")
    @RequirePermission(Permissions.ATTEMPT_COMPENSATE)
    @PostMapping(value = "/{id}/fault", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminAttemptActionResponse markFault(
            @PathVariable UUID id,
            @Valid @RequestBody FaultAttemptRequest request) {
        return attemptAdminService.markFault(id, request);
    }

    @Operation(operationId = "compensateAdminAttempt", summary = "Cấp bù 1 lượt cho lượt thi đã đánh dấu lỗi hệ thống")
    @RequirePermission(Permissions.ATTEMPT_COMPENSATE)
    @PostMapping(value = "/{id}/compensate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompensateAttemptResponse compensate(
            @PathVariable UUID id,
            @Parameter(
                    in = ParameterIn.HEADER,
                    name = "Idempotency-Key",
                    required = true,
                    description = "Khóa chống gửi lặp bắt buộc cho thao tác cấp bù lượt")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CompensateAttemptRequest request) {
        return attemptAdminService.compensate(id, idempotencyKey, request);
    }
}
