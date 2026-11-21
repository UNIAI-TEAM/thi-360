package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.PreviewGradingService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

/** API quản trị làm thử đề xem trước: gia hạn TTL, phát đề không đáp án và chấm bài thử (AC-17..AC-24). */
@Tag(
        name = "Admin Preview Trial",
        description = "Gia hạn, đọc đề làm thử không đáp án và chấm bài thử từ bản xem trước")
@RestController
@RequestMapping(
        value = "/api/v1/admin/exams/{id}/preview/{previewId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminPreviewGradingController {

    private final PreviewGradingService previewGradingService;

    public AdminPreviewGradingController(PreviewGradingService previewGradingService) {
        this.previewGradingService = previewGradingService;
    }

    @PostMapping("/trial")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(
            operationId = "extendAdminPreviewForTrial",
            summary = "Gia hạn TTL phiên xem trước theo công thức max(hiện tại, now + thời lượng + 30p)")
    public PreviewTrialResponse extendForTrial(
            @PathVariable("id") UUID examId, @PathVariable("previewId") UUID previewId) {
        return previewGradingService.extendForTrial(examId, previewId);
    }

    @GetMapping("/paper")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(
            operationId = "getAdminPreviewPaper",
            summary = "Đọc đề làm thử từ bản xem trước (đã lọc đáp án, giữ thứ tự câu/đáp án/âm thanh/đoạn)")
    public PreviewTrialPaperResponse getPaper(
            @PathVariable("id") UUID examId, @PathVariable("previewId") UUID previewId) {
        return previewGradingService.getPaper(examId, previewId);
    }

    @PostMapping(value = "/grade", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(
            operationId = "gradeAdminPreview",
            summary = "Chấm bài làm tạm trên bản xem trước bằng ItemGrader/ScoreAggregator, không lưu DB")
    public PreviewGradeResponse grade(
            @PathVariable("id") UUID examId,
            @PathVariable("previewId") UUID previewId,
            @Valid @RequestBody PreviewGradeRequest request) {
        return previewGradingService.grade(examId, previewId, request);
    }
}
