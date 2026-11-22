package vn.unicom.thi360.commerce.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.commerce.order.application.OrderPriceChangedException;
import vn.unicom.thi360.commerce.pricing.PriceQuote;
import vn.unicom.thi360.commerce.storefront.api.ProductGoneResponse;
import vn.unicom.thi360.commerce.storefront.application.ProductDiscontinuedException;
import vn.unicom.thi360.commerce.storefront.application.StorefrontErrorCodes;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.AppMessages;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Gắn payload {@code quote} cho 409 {@code PRICE_CHANGED} và {@code product} cho 410 {@code PRODUCT_DISCONTINUED}
 * riêng cho API đơn hàng (TM 8.3).
 */
@RestControllerAdvice(assignableTypes = OrderController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderExceptionAdvice {

    private final AppMessages messages;

    public OrderExceptionAdvice(AppMessages messages) {
        this.messages = Objects.requireNonNull(messages);
    }

    @ExceptionHandler(OrderPriceChangedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public OrderPriceChangedResponse handlePriceChanged(
            OrderPriceChangedException ex, HttpServletRequest request) {
        return new OrderPriceChangedResponse(
                "PRICE_CHANGED",
                messages.get("PRICE_CHANGED"),
                List.of(),
                resolveTraceId(request),
                ex.quote());
    }

    @ExceptionHandler(ProductDiscontinuedException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ProductGoneResponse handleDiscontinued(
            ProductDiscontinuedException ex, HttpServletRequest request) {
        return new ProductGoneResponse(
                StorefrontErrorCodes.PRODUCT_DISCONTINUED,
                messages.get(StorefrontErrorCodes.PRODUCT_DISCONTINUED),
                List.of(),
                resolveTraceId(request),
                new ProductGoneResponse.Product(ex.productCode(), ex.productName()));
    }

    private static String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            Object attr = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            if (attr instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return MDC.get(TraceIdFilter.MDC_TRACE_ID);
    }

    public record OrderPriceChangedResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FieldError> fieldErrors,
            String traceId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PriceQuote quote) {
    }
}
