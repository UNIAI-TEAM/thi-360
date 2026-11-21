package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.EssayGradingService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * Admin endpoints for anonymous manual essay grading queue, claim/takeover, draft saves,
 * finalization, and reopening (LT §9.2, F17, BS §11).
 */
@Tag(name = "Admin Grading", description = "Hàng chờ chấm tự luận ẩn danh cho người chấm")
@RestController
@RequestMapping(value = "/api/v1/admin/grading/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminGradingController {

    private final EssayGradingService essayGradingService;

    public AdminGradingController(EssayGradingService essayGradingService) {
        this.essayGradingService = Objects.requireNonNull(essayGradingService);
    }

    @Operation(operationId = "listGradingItems", summary = "Danh sách hàng chờ chấm tự luận ẩn danh")
    @RequirePermission(Permissions.GRADING_VIEW)
    @GetMapping
    public PageResponse<GradingQueueItemView> listItems(
            @Parameter(description = "Trạng thái hàng chờ (WAITING, IN_PROGRESS, DONE)")
            @RequestParam(required = false) GradingQueueState state,
            @Parameter(description = "ID bài thi (UUID) hoặc từ khóa tên bài thi")
            @RequestParam(required = false) String exam,
            @Parameter(description = "Mã kỳ thi (vd: BCA, SP2E)")
            @RequestParam(required = false) String kind,
            @Parameter(description = "Lọc ngày/giờ nộp từ (ISO-8601 hoặc yyyy-MM-dd)")
            @RequestParam(required = false) String from,
            @Parameter(description = "Lọc ngày/giờ nộp đến (ISO-8601 hoặc yyyy-MM-dd)")
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return essayGradingService.listItems(state, exam, kind, from, to, page, size);
    }

    @Operation(operationId = "getGradingItemCounts", summary = "Số đếm hàng chờ chấm theo trạng thái (BS §11)")
    @RequirePermission(Permissions.GRADING_VIEW)
    @GetMapping("/counts")
    public GradingQueueCountsResponse getCounts(
            @RequestParam(required = false) String exam,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return essayGradingService.counts(exam, kind, from, to);
    }

    @Operation(operationId = "getGradingItemDetail", summary = "Chi tiết câu tự luận theo đúng phiên bản đã phát (ẩn danh)")
    @RequirePermission(Permissions.GRADING_VIEW)
    @GetMapping("/{itemId}")
    public GradingItemDetailResponse getItemDetail(@PathVariable UUID itemId) {
        return essayGradingService.getItemDetail(itemId);
    }

    @Operation(operationId = "claimGradingItem", summary = "Nhận câu tự luận để chấm hoặc tiếp quản sau 30 phút không hoạt động")
    @ApiResponse(responseCode = "200", description = "Đã nhận câu chấm",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = EssayGradeView.class)))
    @ApiResponse(responseCode = "409", description = "GRADING_CLAIMED hoặc ESSAY_ALREADY_FINALIZED",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = GradingClaimedResponse.class)))
    @RequirePermission(Permissions.GRADING_MODIFY)
    @PostMapping("/{itemId}/claim")
    public EssayGradeView claim(@PathVariable UUID itemId) {
        return essayGradingService.claim(itemId);
    }

    @Operation(operationId = "saveEssayDraft", summary = "Lưu nháp điểm chấm tự luận")
    @ApiResponse(responseCode = "200", description = "Đã lưu nháp",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = EssayGradeView.class)))
    @ApiResponse(responseCode = "409", description = "GRADING_CLAIMED, GRADING_NOT_CLAIMED hoặc ESSAY_ALREADY_FINALIZED",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = GradingClaimedResponse.class)))
    @ApiResponse(responseCode = "412", description = "VERSION_CONFLICT")
    @RequirePermission(Permissions.GRADING_MODIFY)
    @PutMapping(value = "/{itemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EssayGradeView saveDraft(
            @PathVariable UUID itemId,
            @Valid @RequestBody SaveEssayDraftRequest request) {
        return essayGradingService.saveDraft(itemId, request);
    }

    @Operation(operationId = "finalizeEssayGrade", summary = "Hoàn tất chấm câu tự luận và tính lại điểm lượt thi")
    @ApiResponse(responseCode = "200", description = "Đã hoàn tất chấm",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = FinalizeEssayResponse.class)))
    @ApiResponse(responseCode = "409", description = "GRADING_CLAIMED, GRADING_NOT_CLAIMED hoặc ESSAY_ALREADY_FINALIZED",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = GradingClaimedResponse.class)))
    @ApiResponse(responseCode = "412", description = "VERSION_CONFLICT")
    @ApiResponse(responseCode = "422", description = "ESSAY_POINTS_OUT_OF_RANGE, ESSAY_CRITERIA_INVALID hoặc ESSAY_CRITERIA_SUM_MISMATCH")
    @RequirePermission(Permissions.GRADING_MODIFY)
    @PostMapping("/{itemId}/finalize")
    public FinalizeEssayResponse finalizeGrade(
            @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) FinalizeEssayRequest request) {
        return essayGradingService.finalizeGrade(itemId, request);
    }

    @Operation(operationId = "reopenEssayGrade", summary = "Mở lại bản chấm tự luận đã hoàn tất để chấm lại")
    @RequirePermission(Permissions.GRADING_MODIFY)
    @PostMapping(value = "/{itemId}/reopen", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EssayGradeView reopen(
            @PathVariable UUID itemId,
            @Valid @RequestBody ReopenEssayRequest request) {
        return essayGradingService.reopen(itemId, request);
    }
}
