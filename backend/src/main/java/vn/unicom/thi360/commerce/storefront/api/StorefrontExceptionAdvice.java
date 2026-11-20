package vn.unicom.thi360.commerce.storefront.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import vn.unicom.thi360.commerce.storefront.application.ProductDiscontinuedException;
import vn.unicom.thi360.commerce.storefront.application.StorefrontErrorCodes;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * The 410 of the public product page, with the product in the body (plan B2: an advice of its own, not the shared one).
 * It goes first so the catch-all of the shared advice never answers this exception.
 */
@RestControllerAdvice(assignableTypes = StorefrontPublicController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class StorefrontExceptionAdvice {

    private final MessageSource messages;

    StorefrontExceptionAdvice(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ProductDiscontinuedException.class)
    ResponseEntity<ProductGoneResponse> discontinued(ProductDiscontinuedException ex, HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = StorefrontErrorCodes.PRODUCT_DISCONTINUED;
        String message = messages.getMessage(code, null, code, locale);
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            Object attribute = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            traceId = attribute instanceof String value ? value : "";
        }
        return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.APPLICATION_JSON)
                .body(new ProductGoneResponse(code, message, List.of(), traceId,
                        new ProductGoneResponse.Product(ex.productCode(), ex.productName())));
    }
}
