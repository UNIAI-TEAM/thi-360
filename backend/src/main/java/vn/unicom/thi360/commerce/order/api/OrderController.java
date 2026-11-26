package vn.unicom.thi360.commerce.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.order.application.OrderService;
import vn.unicom.thi360.commerce.order.application.OrderService.CreateOrderRequest;
import vn.unicom.thi360.commerce.order.application.OrderService.CreateOrderResponse;
import vn.unicom.thi360.commerce.order.application.OrderService.OrderDetail;
import vn.unicom.thi360.commerce.order.application.OrderService.OrderSummary;
import vn.unicom.thi360.commerce.order.application.OrderService.PayOrderResponse;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * API đơn hàng của học viên (TM 8.3, E22). Mọi endpoint đòi hỏi phiên hợp lệ của tài khoản {@code ACTIVE}
 * có {@code access = EXAM}.
 */
@Tag(name = "Learner orders", description = "Tạo, xem, tiếp tục thanh toán và hủy đơn mua bài thi")
@RestController
@RequestMapping(value = "/api/v1/me/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "amountDueVnd", "state");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderService orderService;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;

    public OrderController(
            OrderService orderService,
            ClientIpResolver clientIpResolver,
            CurrentUserProvider currentUserProvider) {
        this.orderService = Objects.requireNonNull(orderService);
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
    }

    @Operation(operationId = "createMyOrder", summary = "Tạo đơn mua bài thi (hoặc dùng lại/thay thế đơn chờ)")
    @ApiResponse(responseCode = "201", description = "Tạo đơn hoặc dùng lại đơn chờ thành công",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateOrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED hoặc VALIDATION_FAILED")
    @ApiResponse(responseCode = "409", description = "ACCOUNT_CHANGED, IDEMPOTENCY_KEY_REUSED, PRICE_CHANGED hoặc PRODUCT_UNAVAILABLE")
    @ApiResponse(responseCode = "410", description = "PRODUCT_DISCONTINUED")
    @ApiResponse(responseCode = "422", description = "Lỗi voucher: VOUCHER_*")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest) {
        return orderService.createOrder(
                idempotencyKey,
                request,
                clientIpResolver.resolve(httpRequest),
                currentUserProvider.requireLoginChannel());
    }

    @Operation(operationId = "listMyOrders", summary = "Danh sách đơn hàng của học viên đang đăng nhập")
    @ApiResponse(responseCode = "200", description = "Trang danh sách đơn")
    @GetMapping
    public PageResponse<OrderSummary> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery query = PageQuery.of(page, size, sort, ALLOWED_SORT_FIELDS, DEFAULT_SORT);
        return orderService.listMyOrders(query);
    }

    @Operation(operationId = "getMyOrder", summary = "Chi tiết đơn hàng của học viên đang đăng nhập")
    @ApiResponse(responseCode = "200", description = "Chi tiết đơn",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDetail.class)))
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND")
    @GetMapping("/{code}")
    public OrderDetail detail(@PathVariable String code) {
        return orderService.getMyOrder(code);
    }

    @Operation(operationId = "payMyOrder", summary = "Tiếp tục thanh toán cho đơn đang chờ")
    @ApiResponse(responseCode = "200", description = "Tạo lần thanh toán VNPAY mới",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PayOrderResponse.class)))
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND hoặc PRODUCT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "ORDER_NOT_PENDING, ORDER_EXPIRED hoặc PRODUCT_UNAVAILABLE")
    @ApiResponse(responseCode = "410", description = "PRODUCT_DISCONTINUED")
    @PostMapping("/{code}/pay")
    public PayOrderResponse pay(
            @PathVariable String code,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return orderService.payOrder(
                code,
                idempotencyKey,
                clientIpResolver.resolve(httpRequest),
                currentUserProvider.requireLoginChannel());
    }

    @Operation(operationId = "cancelMyOrder", summary = "Hủy đơn hàng đang chờ và trả lượt voucher")
    @ApiResponse(responseCode = "200", description = "Đã hủy đơn",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDetail.class)))
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "ORDER_NOT_PENDING")
    @PostMapping("/{code}/cancel")
    public OrderDetail cancel(
            @PathVariable String code,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return orderService.cancelOrder(code, idempotencyKey);
    }
}
