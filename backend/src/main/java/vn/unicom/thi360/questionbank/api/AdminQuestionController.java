package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.application.QuestionQueryService;
import vn.unicom.thi360.questionbank.application.QuestionService;
import vn.unicom.thi360.questionbank.domain.QuestionStatus;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * API quản trị ngân hàng câu hỏi (spec 4.5): danh sách/bộ đếm/tác giả, chi tiết, điều kiện
 * Sẵn sàng, các lệnh ghi có idempotency và nhật ký {@code QUESTION.*}. Câu con của bộ không
 * nhận lệnh trực tiếp — sửa qua {@code PUT /questions/{id}} của bộ (404 khi gọi với id câu
 * con).
 */
@Tag(name = "Admin Question Bank", description = "Ngân hàng câu hỏi: lưu, trạng thái, tìm kiếm")
@RestController
@RequestMapping("/api/v1/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;
    private final QuestionQueryService queryService;

    public AdminQuestionController(
            QuestionService questionService, QuestionQueryService queryService) {
        this.questionService = questionService;
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Danh sách câu hỏi độc lập, phân trang",
            description = "Từ khóa bỏ dấu trên search_text (dưới 2 ký tự chỉ so mã); lọc MAPID "
                    + "khớp cả nút con (D23). Sort: createdAt, updatedAt, code, points, type, status.")
    public PageResponse<QuestionSummary> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) String gradeCode,
            @RequestParam(required = false) String difficultyCode,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String taskTypeCode,
            @RequestParam(required = false) String competencyCode,
            @RequestParam(required = false) List<UUID> knowledgeIds,
            @RequestParam(required = false) List<String> tagCodes,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) Boolean hasSolution,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return queryService.list(keyword, subjectCode, gradeCode, difficultyCode, type,
                taskTypeCode, competencyCode, knowledgeIds, tagCodes, status, createdBy,
                createdFrom, createdTo, hasSolution, page, size, sort);
    }

    @GetMapping("/status-counts")
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Số câu theo trạng thái với cùng bộ lọc danh sách")
    public QuestionStatusCounts statusCounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) String gradeCode,
            @RequestParam(required = false) String difficultyCode,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String taskTypeCode,
            @RequestParam(required = false) String competencyCode,
            @RequestParam(required = false) List<UUID> knowledgeIds,
            @RequestParam(required = false) List<String> tagCodes,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) Boolean hasSolution) {
        return queryService.statusCounts(keyword, subjectCode, gradeCode, difficultyCode, type,
                taskTypeCode, competencyCode, knowledgeIds, tagCodes, createdBy,
                createdFrom, createdTo, hasSolution);
    }

    @GetMapping("/authors")
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Người tạo đang có câu (mục lọc \"Người tạo\")")
    public List<PersonRef> authors() {
        return queryService.authors();
    }

    @GetMapping("/{id}")
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Chi tiết câu hỏi (phiên bản đang làm việc hoặc đang phục vụ)")
    public QuestionDetail detail(@PathVariable UUID id) {
        return queryService.detail(id);
    }

    @GetMapping("/{id}/readiness")
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Điều kiện Sẵn sàng của câu (lỗi chặn + cảnh báo)")
    public QuestionReadiness readiness(@PathVariable UUID id) {
        return queryService.readiness(id);
    }

    @GetMapping("/{id}/versions")
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Lịch sử phiên bản của câu, mới nhất trước")
    public QuestionVersionList versions(@PathVariable UUID id) {
        return queryService.versions(id);
    }

    @GetMapping("/{id}/usage")
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Nơi đang dùng câu: khối bài thi lấy câu làm nguồn + sổ đã dùng")
    public QuestionUsage usage(@PathVariable UUID id) {
        return queryService.usage(id);
    }

    @GetMapping("/{id}/retire-impact")
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Ảnh hưởng khi ngừng câu (cùng dữ liệu nơi đang dùng)")
    public QuestionRetireImpact retireImpact(@PathVariable UUID id) {
        return queryService.retireImpact(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Tạo câu hỏi mới ở trạng thái Nháp (Idempotency-Key bắt buộc)")
    public QuestionDetail create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SaveQuestionRequest request) {
        return questionService.create(idempotencyKey, request);
    }

    @PutMapping("/{id}")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Sửa câu hỏi (expectedRevision là khóa lạc quan, D13)",
            description = "Câu đang Sẵn sàng hoặc đã Ngừng lưu sang phiên bản làm việc mới và "
                    + "về Nháp; câu Nháp ghi đè bản làm việc.")
    public UpdateQuestionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionRequest request) {
        return questionService.update(id, request);
    }

    @PostMapping("/{id}/ready")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Đưa câu lên Sẵn sàng (đóng bản làm việc)")
    public QuestionSummary ready(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionStatusRequest request) {
        return questionService.markReady(id, request.expectedRevision());
    }

    @PostMapping("/{id}/retire")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Ngừng câu Sẵn sàng (bỏ bản Nháp đang sửa)")
    public QuestionSummary retire(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionStatusRequest request) {
        return questionService.retire(id, request.expectedRevision());
    }

    @PostMapping("/bulk/ready")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Đưa hàng loạt lên Sẵn sàng; câu lỗi bị bỏ qua kèm lý do (Idempotency-Key bắt buộc)")
    public BulkQuestionStatusResult bulkReady(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BulkQuestionStatusRequest request) {
        return questionService.bulkReady(idempotencyKey, request);
    }

    @PostMapping("/bulk/retire")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Ngừng hàng loạt; câu lỗi bị bỏ qua kèm lý do (Idempotency-Key bắt buộc)")
    public BulkQuestionStatusResult bulkRetire(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BulkQuestionStatusRequest request) {
        return questionService.bulkRetire(idempotencyKey, request);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Nhân đôi câu (bộ câu hỏi nhân đôi cả câu con) — Idempotency-Key bắt buộc")
    public QuestionDetail duplicate(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return questionService.duplicate(id, idempotencyKey);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Xóa mềm câu; câu đã dùng bị chặn 409 (Idempotency-Key bắt buộc)")
    public void delete(
            @PathVariable UUID id,
            @RequestParam Long expectedRevision,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        questionService.delete(id, idempotencyKey, expectedRevision);
    }

    @PostMapping("/{id}/versions/{versionNo}/restore")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Khôi phục một phiên bản cũ vào bản làm việc (xử lý như một lần lưu)")
    public UpdateQuestionResponse restoreVersion(
            @PathVariable UUID id,
            @PathVariable int versionNo,
            @Valid @RequestBody RestoreQuestionVersionRequest request) {
        return questionService.restoreVersion(id, versionNo, request.expectedRevision());
    }
}
