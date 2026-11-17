package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.exam.application.ExamKindService;
import vn.unicom.thi360.exam.application.ExamService;
import vn.unicom.thi360.exam.application.ExamTemplateService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;
import vn.unicom.thi360.shared.api.NotImplementedException;

@Tag(name = "Exam", description = "Bài thi, cấu trúc và mẫu")
@RestController
@RequestMapping("/api/v1/admin")
public class ExamAdminController {
    private static final Set<String> SORT_FIELDS = Set.of("code", "name", "status", "question_count",
            "duration_minutes", "max_score", "created_at", "updated_at");
    private final ExamService exams;
    private final ExamKindService kinds;
    private final ExamTemplateService templates;

    public ExamAdminController(ExamService exams, ExamKindService kinds, ExamTemplateService templates) {
        this.exams = exams;
        this.kinds = kinds;
        this.templates = templates;
    }

    @GetMapping("/exam-kinds")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Danh sách kỳ thi và hồ sơ")
    public List<ExamKindOption> listKinds() {
        return kinds.listExamKinds();
    }

    @PutMapping("/exam-kinds/{itemId}/profile")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Thay hồ sơ kỳ thi")
    public ExamKindOption updateKind(@PathVariable UUID itemId,
            @Valid @RequestBody UpdateExamKindProfileRequest request) {
        return kinds.updateProfile(itemId, request);
    }

    @GetMapping("/exam-templates")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Danh sách mẫu cấu trúc")
    public List<ExamTemplateResponse> listTemplates(@RequestParam(required = false) String kind,
            @RequestParam(required = false) String subjectCode) {
        return templates.listExamTemplates().stream()
                .filter(t -> kind == null || kind.equalsIgnoreCase(t.kindCode()))
                .filter(t -> subjectCode == null || subjectCode.equalsIgnoreCase(t.subjectCode()))
                .toList();
    }

    @GetMapping("/exams/suggested-code")
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(summary = "Gợi ý mã bài thi")
    public SuggestExamCodeResponse suggestCode(@RequestParam String kind, @RequestParam String scope,
            @RequestParam(required = false) String partCode,
            @RequestParam(required = false) String subjectCode) {
        return exams.suggestExamCode(kind, scope, partCode, subjectCode);
    }

    @GetMapping("/exams")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Danh sách bài thi")
    public PageResponse<ExamSummaryResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String kind, @RequestParam(required = false) String subject,
            @RequestParam(required = false) String scope, @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean hasEssay,
            @RequestParam(required = false) Boolean hasEnglishOption,
            @RequestParam(required = false) String checkStatus,
            @RequestParam(required = false) Boolean hasUnpublishedChanges,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return exams.listExams(q, kind, subject, scope, status, hasEssay, hasEnglishOption,
                checkStatus, hasUnpublishedChanges, PageQuery.of(page, size, sort, SORT_FIELDS, Sort.unsorted()));
    }

    @GetMapping("/exams/{id}")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Chi tiết bài thi")
    public ExamDetailResponse detail(@PathVariable UUID id) {
        return exams.getExamDetail(id);
    }

    @PostMapping("/exams")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(summary = "Tạo bài thi nháp")
    public ExamDetailResponse create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateExamRequest request) {
        if (request.overview() != null || (request.buildMode() != null
                && !"STANDARD".equals(request.buildMode()))) {
            throw new NotImplementedException();
        }
        return exams.createExam(key, request);
    }

    @PatchMapping("/exams/{id}")
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(summary = "Sửa thông tin bài thi")
    public ExamDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateExamInfoRequest request) {
        return exams.updateExamInfo(id, request);
    }

    @GetMapping("/exams/{id}/structure")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Đọc cấu trúc bản làm việc")
    public ExamStructureResponse structure(@PathVariable UUID id) {
        return exams.getExamStructure(id);
    }

    @GetMapping("/exams/{id}/structure/summary")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(summary = "Tổng hợp cấu trúc bài thi")
    public ExamStructureSummaryResponse structureSummary(@PathVariable UUID id) {
        throw new NotImplementedException();
    }

    @PutMapping("/exams/{id}/overview")
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(summary = "Cập nhật tổng quan bài thi")
    public ExamDetailResponse updateOverview(@PathVariable UUID id,
            @Valid @RequestBody ExamOverviewRequest request) {
        throw new NotImplementedException();
    }

    @PutMapping("/exams/{id}/structure")
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(summary = "Lưu cấu trúc bản làm việc")
    public ExamDetailResponse updateStructure(@PathVariable UUID id,
            @Valid @RequestBody UpdateExamStructureRequest request) {
        exams.updateStructure(id, request);
        return exams.getExamDetail(id);
    }

    @PostMapping("/exams/{id}/structure/template")
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(summary = "Nạp mẫu cấu trúc")
    @ApiResponse(responseCode = "409", description = "EXAM_STRUCTURE_REPLACE_CONFIRM or EXAM_OVERVIEW_CONFIRM",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ExamDetailResponse loadTemplate(@PathVariable UUID id,
            @Valid @RequestBody LoadExamTemplateRequest request) {
        if (request.overviewPolicy() != null) {
            throw new NotImplementedException();
        }
        exams.loadTemplate(id, request);
        return exams.getExamDetail(id);
    }

    @PostMapping("/exams/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(summary = "Nhân bản bài thi")
    public ExamDetailResponse duplicate(@PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DuplicateExamRequest request) {
        return exams.duplicateExam(key, id, request);
    }
}
