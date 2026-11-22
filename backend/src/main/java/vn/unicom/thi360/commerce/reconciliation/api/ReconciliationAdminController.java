package vn.unicom.thi360.commerce.reconciliation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.reconciliation.application.ReconciliationService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * API tra cứu báo cáo đối soát cuối ngày mới nhất cho màn quản trị đơn hàng (TM 8.4, BS 14).
 */
@Tag(name = "Admin Reconciliation", description = "Báo cáo đối soát cuối ngày")
@RestController
@RequestMapping(value = "/api/v1/admin/reconciliation-reports", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReconciliationAdminController {

    private final ReconciliationService reconciliationService;

    public ReconciliationAdminController(ReconciliationService reconciliationService) {
        this.reconciliationService = Objects.requireNonNull(reconciliationService);
    }

    @Operation(operationId = "getLatestReconciliationReport", summary = "Lấy báo cáo đối soát mới nhất")
    @ApiResponse(responseCode = "200", description = "Trạng thái có sẵn và nội dung báo cáo mới nhất",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = LatestReconciliationReportResponse.class)))
    @RequirePermission(Permissions.ORDER_VIEW)
    @GetMapping("/latest")
    public LatestReconciliationReportResponse latest() {
        return reconciliationService.getLatestReport();
    }
}
