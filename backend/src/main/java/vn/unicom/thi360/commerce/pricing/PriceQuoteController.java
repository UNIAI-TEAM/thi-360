package vn.unicom.thi360.commerce.pricing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.entitlement.access.application.ExamLearnerGate;
import vn.unicom.thi360.commerce.storefront.api.ProductGoneResponse;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;

/**
 * Authenticated learner price-quote endpoint (TM 10.2). Gated by {@link ExamLearnerGate} (active {@code EXAM}
 * accounts only) and protected against voucher brute-forcing by {@link VoucherQuoteRateLimiter}.
 */
@Tag(name = "Price quote", description = "Báo giá mua bài thi cho học viên")
@RestController
@RequestMapping(value = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
public class PriceQuoteController {

    private final ExamLearnerGate learnerGate;
    private final PriceQuoteService priceQuoteService;
    private final VoucherQuoteRateLimiter rateLimiter;

    public PriceQuoteController(
            ExamLearnerGate learnerGate,
            PriceQuoteService priceQuoteService,
            VoucherQuoteRateLimiter rateLimiter) {
        this.learnerGate = learnerGate;
        this.priceQuoteService = priceQuoteService;
        this.rateLimiter = rateLimiter;
    }

    @Operation(
            operationId = "quoteMyPrice",
            summary = "Báo giá bài thi kèm voucher tùy chọn")
    @ApiResponse(responseCode = "200", description = "Báo giá hợp lệ",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PriceQuote.class)))
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND")
    @ApiResponse(responseCode = "410", description = "PRODUCT_DISCONTINUED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductGoneResponse.class)))
    @ApiResponse(responseCode = "422", description = "Lỗi voucher: VOUCHER_INVALID, VOUCHER_EXPIRED, VOUCHER_EXHAUSTED, VOUCHER_USER_LIMIT, VOUCHER_NOT_APPLICABLE, VOUCHER_NOT_ELIGIBLE")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED: vượt giới hạn kiểm tra mã voucher")
    @PostMapping(value = "/price-quote", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PriceQuote quote(
            @Valid @RequestBody PriceQuoteRequest request,
            HttpServletResponse response) {
        UUID userId = learnerGate.requireLearner();
        StoredProduct product = priceQuoteService.requireOnSaleProduct(request.code());
        if (StringUtils.hasText(request.voucherCode())) {
            rateLimiter.checkAndConsume(userId, response);
        }
        return priceQuoteService.quote(userId, product, request);
    }
}
