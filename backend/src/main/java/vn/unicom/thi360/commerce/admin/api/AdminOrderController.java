package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.admin.application.AdminOrderQueryService;
import vn.unicom.thi360.commerce.admin.application.LateSuccessReviewService;
import vn.unicom.thi360.commerce.admin.application.OrderRecoverService;
import vn.unicom.thi360.commerce.admin.domain.AdminPaymentStatus;
import vn.unicom.thi360.commerce.admin.domain.AdminRefundStatus;
import vn.unicom.thi360.commerce.admin.domain.OrderAttentionType;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository.OrderFilter;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * API quản trị đơn hàng, dải "Cần xử lý", người mua theo bài thi, tiếp tục cấp quyền và xử lý tiền về muộn
 * (TM 9.1–9.3, 9.5, BS §14).
 */
@Tag(name = "Admin Orders", description = "Quản trị đơn hàng, người mua, tiếp tục cấp quyền và đối soát tiền về muộn")
@RestController
@RequestMapping(value = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminOrderController {

    private static final Set<String> ORDER_SORT_FIELDS = Set.of(
            "createdAt", "paidAt", "fulfilledAt", "amountDueVnd", "code", "state");
    private static final Sort DEFAULT_ORDER_SORT = Sort.by(
            Sort.Order.desc("createdAt"));

    private static final Set<String> BUYER_SORT_FIELDS = Set.of(
            "lastPaidAt", "userId", "purchaseCount", "username");
    private static final Sort DEFAULT_BUYER_SORT = Sort.by(
            Sort.Order.desc("lastPaidAt"), Sort.Order.asc("userId"));

    private final AdminOrderQueryService queryService;
    private final OrderRecoverService recoverService;
    private final LateSuccessReviewService lateSuccessReviewService;

    public AdminOrderController(
            AdminOrderQueryService queryService,
            OrderRecoverService recoverService,
            LateSuccessReviewService lateSuccessReviewService) {
        this.queryService = Objects.requireNonNull(queryService);
        this.recoverService = Objects.requireNonNull(recoverService);
        this.lateSuccessReviewService = Objects.requireNonNull(lateSuccessReviewService);
    }

    @Operation(operationId = "listAdminOrders", summary = "Danh sách đơn hàng quản trị và số đếm dải Cần xử lý")
    @ApiResponse(responseCode = "200", description = "Danh sách đơn hàng phân trang",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminOrderListResponse.class)))
    @RequirePermission(Permissions.ORDER_VIEW)
    @GetMapping("/orders")
    public AdminOrderListResponse listOrders(
            @Parameter(description = "Tìm theo mã đơn, tên đăng nhập, txnRef hoặc mã giao dịch VNPAY")
            @RequestParam(required = false) @Size(max = 200, message = "{validation.order.q.size}") String q,
            @Parameter(description = "Lọc theo bài thi")
            @RequestParam(required = false) UUID examId,
            @Parameter(description = "Từ thời điểm tạo (ISO-8601 UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Đến thời điểm tạo (ISO-8601 UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Lọc theo trạng thái thanh toán")
            @RequestParam(required = false) AdminPaymentStatus paymentStatus,
            @Parameter(description = "Lọc theo trạng thái hoàn tiền")
            @RequestParam(required = false) AdminRefundStatus refundStatus,
            @Parameter(description = "Lọc theo hình thức thanh toán")
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @Parameter(description = "Lọc theo nhóm Cần xử lý")
            @RequestParam(required = false) OrderAttentionType attention,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(page, size, sort, ORDER_SORT_FIELDS, DEFAULT_ORDER_SORT);
        OrderFilter filter = new OrderFilter(
                q, examId, from, to, paymentStatus, refundStatus, paymentMethod, attention);
        return queryService.listOrders(filter, pageQuery);
    }

    @Operation(operationId = "getAdminOrderAttentionSummary", summary = "Số lượng đơn theo 6 nhóm Cần xử lý")
    @ApiResponse(responseCode = "200", description = "Số đếm dải Cần xử lý",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = OrderAttentionSummary.class)))
    @RequirePermission(Permissions.ORDER_VIEW)
    @GetMapping("/orders/attention")
    public OrderAttentionSummary getAttentionSummary() {
        return queryService.getAttentionSummary();
    }

    @Operation(operationId = "getAdminOrderDetail", summary = "Chi tiết đơn hàng cho quản trị viên")
    @ApiResponse(responseCode = "200", description = "Chi tiết đơn hàng",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminOrderDetailResponse.class)))
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND")
    @RequirePermission(Permissions.ORDER_VIEW)
    @GetMapping("/orders/{code}")
    public AdminOrderDetailResponse getOrderDetail(@PathVariable String code) {
        return queryService.getOrderDetail(code);
    }

    @Operation(operationId = "listExamBuyers", summary = "Danh sách tài khoản đã mua theo bài thi")
    @ApiResponse(responseCode = "200", description = "Danh sách người mua phân trang theo tài khoản",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExamBuyersResponse.class)))
    @RequirePermission(Permissions.ORDER_VIEW)
    @GetMapping("/exams/{id}/buyers")
    public ExamBuyersResponse listExamBuyers(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(page, size, sort, BUYER_SORT_FIELDS, DEFAULT_BUYER_SORT);
        return queryService.listExamBuyers(id, pageQuery);
    }

    @Operation(operationId = "recoverOrderFulfillment", summary = "Tiếp tục cấp quyền thủ công cho đơn đã thanh toán")
    @ApiResponse(responseCode = "200", description = "Đã cấp quyền thành công",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminOrderDetailResponse.class)))
    @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED hoặc VALIDATION_FAILED")
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "ORDER_RECOVERY_NOT_ALLOWED hoặc IDEMPOTENCY_KEY_REUSED")
    @RequirePermission(Permissions.ORDER_RECOVER)
    @PostMapping(value = "/orders/{code}/fulfill", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminOrderDetailResponse recoverFulfillment(
            @PathVariable String code,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RecoverOrderRequest request) {
        return recoverService.recoverOrder(code, idempotencyKey, request);
    }

    @Operation(operationId = "reviewLateSuccessPayment", summary = "Đối soát tiền về muộn trên lần thanh toán đã ghi thất bại")
    @ApiResponse(responseCode = "200", description = "Đã xử lý quyết định đối soát",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminOrderDetailResponse.class)))
    @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED hoặc VALIDATION_FAILED")
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND hoặc ORDER_PAYMENT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "LATE_SUCCESS_ALREADY_RESOLVED hoặc IDEMPOTENCY_KEY_REUSED")
    @RequirePermission(Permissions.ORDER_RECOVER)
    @PostMapping(value = "/orders/{code}/payments/{paymentId}/late-success",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminOrderDetailResponse reviewLateSuccess(
            @PathVariable String code,
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody LateSuccessReviewRequest request) {
        return lateSuccessReviewService.reviewLateSuccess(code, paymentId, idempotencyKey, request);
    }
}
