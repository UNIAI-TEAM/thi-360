package vn.unicom.thi360.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditQuery;
import vn.unicom.thi360.audit.spi.AuditQueryCriteria;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

@RestController
@RequestMapping(value = "/api/v1/admin/audit-events", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Audit", description = "Tra cứu nhật ký thao tác")
public class AuditController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("occurredAt", "action", "module");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "occurredAt");

    private final AuditQuery auditQuery;

    public AuditController(AuditQuery auditQuery) {
        this.auditQuery = auditQuery;
    }

    @GetMapping
    @RequirePermission("AUDIT.VIEW")
    @Operation(summary = "Tra cứu danh sách sự kiện nhật ký thao tác")
    public PageResponse<AuditEventResponse> listAuditEvents(
            @Parameter(description = "Loại đối tượng")
            @RequestParam(required = false) String targetType,
            @Parameter(description = "ID đối tượng")
            @RequestParam(required = false) String targetId,
            @Parameter(description = "ID người thực hiện")
            @RequestParam(required = false) UUID actorId,
            @Parameter(description = "Mã hành động")
            @RequestParam(required = false) String action,
            @Parameter(description = "Từ thời điểm (ISO-8601 UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Đến thời điểm (ISO-8601 UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Chỉ số trang (bắt đầu từ 0)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Kích thước trang (tối đa 100)")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sắp xếp theo trường,vd: occurredAt,desc")
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(page, size, sort, ALLOWED_SORT_FIELDS, DEFAULT_SORT);
        AuditQueryCriteria criteria = new AuditQueryCriteria(targetType, targetId, actorId, action, from, to);
        PageResponse<AuditEventView> result = auditQuery.query(criteria, pageQuery);
        return new PageResponse<>(
                result.items().stream().map(AuditEventResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalItems(),
                result.totalPages());
    }
}
