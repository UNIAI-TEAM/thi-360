package vn.unicom.thi360.commerce.refund.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.refund.application.RefundService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * API quản trị hoàn tiền đơn hàng qua VNPAY API, ghi nhận thủ công, tra cứu và đánh dấu thất bại (TM 9.4).
 */
@Tag(name = "Admin Refunds", description = "Quản trị hoàn tiền đơn hàng")
@RestController
@RequestMapping(value = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class RefundAdminController {

    private final RefundService refundService;

    public RefundAdminController(RefundService refundService) {
        this.refundService = Objects.requireNonNull(refundService, "refundService must not be null");
    }

    @Operation(operationId = "createOrderPaymentRefund", summary = "Tạo yêu cầu hoàn tiền qua API VNPAY hoặc ghi nhận thủ công")
    @ApiResponse(responseCode = "201", description = "Đã tạo hoặc phát lại yêu cầu hoàn tiền",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RefundResponse.class)))
    @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED hoặc VALIDATION_FAILED")
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND hoặc ORDER_PAYMENT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "REFUND_PAYMENT_NOT_SUCCEEDED, REFUND_ACTIVE_EXISTS, REFUND_AMOUNT_EXCEEDED, REFUND_MANUAL_REQUIRES_FAILED_API hoặc IDEMPOTENCY_KEY_REUSED")
    @ApiResponse(responseCode = "422", description = "REFUND_SYSTEM_ERROR_NOT_VERIFIED hoặc REFUND_EXTERNAL_REF_REQUIRED")
    @RequirePermission(Permissions.ORDER_REFUND)
    @PostMapping(value = "/orders/{code}/payments/{paymentId}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(
            @PathVariable String code,
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {
        return refundService.createRefund(code, paymentId, idempotencyKey, request);
    }

    @Operation(operationId = "getRefund", summary = "Xem chi tiết một yêu cầu hoàn tiền")
    @ApiResponse(responseCode = "200", description = "Chi tiết yêu cầu hoàn tiền",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RefundResponse.class)))
    @ApiResponse(responseCode = "404", description = "REFUND_NOT_FOUND")
    @RequirePermission(Permissions.ORDER_VIEW)
    @GetMapping("/refunds/{id}")
    public RefundResponse get(@PathVariable UUID id) {
        return refundService.getRefund(id);
    }

    @Operation(operationId = "queryRefund", summary = "Tra cứu kết quả hoàn tiền từ cổng VNPAY (querydr)")
    @ApiResponse(responseCode = "200", description = "Trạng thái hoàn tiền sau tra cứu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RefundResponse.class)))
    @ApiResponse(responseCode = "404", description = "REFUND_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "REFUND_QUERY_NOT_ALLOWED")
    @RequirePermission(Permissions.ORDER_REFUND)
    @PostMapping("/refunds/{id}/query")
    public RefundResponse query(@PathVariable UUID id) {
        return refundService.queryRefund(id);
    }

    @Operation(operationId = "markRefundFailed", summary = "Đánh dấu giao dịch hoàn tiền UNKNOWN là FAILED kèm chứng từ")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật trạng thái FAILED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RefundResponse.class)))
    @ApiResponse(responseCode = "404", description = "REFUND_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "REFUND_NOT_UNKNOWN")
    @RequirePermission(Permissions.ORDER_REFUND)
    @PostMapping(value = "/refunds/{id}/mark-failed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RefundResponse markFailed(
            @PathVariable UUID id,
            @Valid @RequestBody MarkRefundFailedRequest request) {
        return refundService.markFailed(id, request);
    }
}
