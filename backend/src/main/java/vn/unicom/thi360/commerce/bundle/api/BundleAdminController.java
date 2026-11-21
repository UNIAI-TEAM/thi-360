package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.bundle.application.BundleAdminService;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * Admin endpoints for managing exam bundles (TM 16.4, BS §2).
 */
@Tag(name = "Bundles", description = "Quản trị bộ đề thi")
@RestController
@RequestMapping(value = "/api/v1/admin/bundles", produces = MediaType.APPLICATION_JSON_VALUE)
public class BundleAdminController {

    private final BundleAdminService service;

    public BundleAdminController(BundleAdminService service) {
        this.service = service;
    }

    @Operation(operationId = "listBundles", summary = "Danh sách bộ đề phân trang")
    @RequirePermission(Permissions.EXAM_VIEW)
    @GetMapping
    public PageResponse<BundleSummaryResponse> list(
            @Parameter(description = "Tìm theo tên hoặc mã bộ đề") @RequestParam(required = false) String q,
            @Parameter(description = "Mã hoặc UUID kỳ thi") @RequestParam(required = false) String examKind,
            @RequestParam(required = false) BundleStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(q, examKind, status, page, size, sort);
    }

    @Operation(operationId = "listBundleCandidates",
            summary = "Danh sách bài thi đang bán (PART hoặc SUBJECT, gồm cả bài ôn) có thể đưa vào bộ đề")
    @RequirePermission(Permissions.EXAM_VIEW)
    @GetMapping("/candidates")
    public List<BundleCandidateResponse> candidates(
            @Parameter(description = "Mã kỳ thi (ví dụ SP2E) hoặc UUID danh mục kỳ thi", required = true)
            @RequestParam String examKind) {
        return service.candidates(examKind);
    }

    @Operation(operationId = "createBundle", summary = "Tạo bộ đề mới ở trạng thái nháp")
    @RequirePermission(Permissions.EXAM_MODIFY)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BundleDetailResponse create(@Valid @RequestBody CreateBundleRequest request) {
        return service.create(request);
    }

    @Operation(operationId = "getBundle", summary = "Chi tiết bộ đề và danh sách bài thi thành phần")
    @RequirePermission(Permissions.EXAM_VIEW)
    @GetMapping("/{id}")
    public BundleDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(operationId = "updateBundle", summary = "Cập nhật bộ đề (khóa lạc quan expectedRevision)")
    @RequirePermission(Permissions.EXAM_MODIFY)
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BundleDetailResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBundleRequest request) {
        return service.update(id, request);
    }

    @Operation(operationId = "publishBundle", summary = "Công bố hoặc công bố lại bộ đề")
    @RequirePermission(Permissions.EXAM_PUBLISH)
    @PostMapping("/{id}/publish")
    public BundleDetailResponse publish(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) BundleRevisionRequest request) {
        return service.publish(id, request);
    }

    @Operation(operationId = "discontinueBundle", summary = "Ngừng bán bộ đề đang công bố")
    @RequirePermission(Permissions.EXAM_PUBLISH)
    @PostMapping("/{id}/discontinue")
    public BundleDetailResponse discontinue(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) BundleRevisionRequest request) {
        return service.discontinue(id, request);
    }
}
