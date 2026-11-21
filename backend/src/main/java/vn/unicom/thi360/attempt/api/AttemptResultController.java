package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.AttemptHistoryService;
import vn.unicom.thi360.attempt.application.AttemptResultService;
import vn.unicom.thi360.shared.api.PageResponse;

@RestController
@RequestMapping("/api/v1/me/attempts")
@Tag(name = "My Attempts Results", description = "Kết quả, xem lại bài làm và lịch sử luyện thi của học viên (LT §10–11)")
public class AttemptResultController {

    private final AttemptResultService attemptResultService;
    private final AttemptHistoryService attemptHistoryService;

    public AttemptResultController(
            AttemptResultService attemptResultService,
            AttemptHistoryService attemptHistoryService) {
        this.attemptResultService = Objects.requireNonNull(attemptResultService);
        this.attemptHistoryService = Objects.requireNonNull(attemptHistoryService);
    }

    @GetMapping
    @Operation(summary = "Lịch sử luyện thi của học viên")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Danh sách lượt thi phân trang")})
    public PageResponse<AttemptHistoryItem> listHistory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) UUID examId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return attemptHistoryService.listHistory(q, kind, scope, examId, from, to, page, size);
    }

    @GetMapping("/{attemptId}/result")
    @Operation(summary = "Xem kết quả tổng hợp của lượt thi")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kết quả tổng hợp, thống kê và breakdown"),
        @ApiResponse(responseCode = "404", description = "Lượt không tồn tại hoặc không thuộc người dùng"),
        @ApiResponse(responseCode = "409", description = "Lượt chưa kết thúc")
    })
    public AttemptResultResponse getResult(@PathVariable UUID attemptId) {
        return attemptResultService.getResult(attemptId);
    }

    @GetMapping("/{attemptId}/review")
    @Operation(summary = "Xem lại bài làm theo quyền result_view")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Chi tiết từng câu và đáp án / lời giải"),
        @ApiResponse(responseCode = "403", description = "Gói lượt thi chỉ cho xem điểm (SCORE)"),
        @ApiResponse(responseCode = "404", description = "Lượt hoặc phần không tồn tại"),
        @ApiResponse(responseCode = "409", description = "Lượt chưa ở trạng thái xem lại được")
    })
    public AttemptReviewResponse getReview(
            @PathVariable UUID attemptId,
            @RequestParam(required = false) String part) {
        return attemptResultService.getReview(attemptId, part);
    }
}
