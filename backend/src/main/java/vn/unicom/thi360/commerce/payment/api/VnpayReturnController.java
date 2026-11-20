package vn.unicom.thi360.commerce.payment.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.payment.application.VnpayReturnService;
import vn.unicom.thi360.commerce.payment.application.VnpayReturnService.VnpayReturnView;
import vn.unicom.thi360.shared.security.ClientIpResolver;

/**
 * API công khai kiểm chứng tham số URL quay lại từ VNPAY (TM 8.4). Không tiết lộ dữ liệu người mua.
 */
@Tag(name = "VNPAY callbacks", description = "Webhook IPN và trang quay lại VNPAY")
@RestController
@RequestMapping(value = "/api/v1/public/payments/vnpay", produces = MediaType.APPLICATION_JSON_VALUE)
public class VnpayReturnController {

    private final VnpayReturnService returnService;
    private final ClientIpResolver clientIpResolver;

    public VnpayReturnController(VnpayReturnService returnService, ClientIpResolver clientIpResolver) {
        this.returnService = Objects.requireNonNull(returnService);
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver);
    }

    @Operation(
            operationId = "verifyVnpayReturn",
            summary = "Kiểm chữ ký tham số VNPAY ở trang kết quả (không đổi trạng thái)")
    @ApiResponse(responseCode = "200", description = "Chữ ký hợp lệ",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VnpayReturnResponse.class)))
    @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND")
    @ApiResponse(responseCode = "422", description = "VNPAY_SIGNATURE_INVALID")
    @GetMapping("/return")
    public VnpayReturnResponse verifyReturn(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        VnpayReturnView view = returnService.inspectReturn(params, clientIpResolver.resolve(request));
        return new VnpayReturnResponse(view.orderCode(), view.paymentState());
    }

    public record VnpayReturnResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String paymentState) {
    }
}
