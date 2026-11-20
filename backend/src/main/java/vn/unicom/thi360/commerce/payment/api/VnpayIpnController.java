package vn.unicom.thi360.commerce.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import vn.unicom.thi360.commerce.payment.application.VnpayIpnService;
import vn.unicom.thi360.commerce.payment.gateway.VnpayIpnProcessor.IpnReply;
import vn.unicom.thi360.shared.security.ClientIpResolver;

/**
 * Webhook VNPAY IPN công khai (TM 8.4). Miễn giới hạn tần suất và luôn trả HTTP 200 JSON {@code {RspCode, Message}}.
 */
@Tag(name = "VNPAY callbacks", description = "Webhook IPN và trang quay lại VNPAY")
@RestController
@RequestMapping(value = "/api/v1/public/payments/vnpay", produces = MediaType.APPLICATION_JSON_VALUE)
public class VnpayIpnController {

    private final VnpayIpnService ipnService;
    private final ClientIpResolver clientIpResolver;

    public VnpayIpnController(VnpayIpnService ipnService, ClientIpResolver clientIpResolver) {
        this.ipnService = Objects.requireNonNull(ipnService);
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver);
    }

    @Operation(
            operationId = "handleVnpayIpn",
            summary = "Nhận thông báo thanh toán tức thì (IPN) từ VNPAY")
    @ApiResponse(responseCode = "200", description = "Phản hồi chuẩn VNPAY",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VnpayIpnResponse.class)))
    @GetMapping("/ipn")
    public VnpayIpnResponse ipn(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        IpnReply result = ipnService.process(params, clientIpResolver.resolve(request));
        return new VnpayIpnResponse(result.rspCode(), result.message());
    }

    public record VnpayIpnResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "00")
            @JsonProperty("RspCode") String rspCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Confirm Success")
            @JsonProperty("Message") String message) {
    }
}
