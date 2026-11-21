package vn.unicom.thi360.commerce.admin.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.LocaleResolver;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Chuyển {@link HandlerMethodValidationException} từ ràng buộc tham số truy vấn (như {@code @Size} trên {@code q})
 * ở {@link AdminOrderController} về mã 400 {@code VALIDATION_FAILED} nhất quán với {@code GlobalExceptionHandler}.
 */
@RestControllerAdvice(assignableTypes = AdminOrderController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminOrderExceptionAdvice {

    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public AdminOrderExceptionAdvice(MessageSource messageSource, LocaleResolver localeResolver) {
        this.messageSource = Objects.requireNonNull(messageSource);
        this.localeResolver = Objects.requireNonNull(localeResolver);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        Locale locale = request == null
                ? Locale.forLanguageTag("vi")
                : LocaleConfig.normalizeSupported(localeResolver.resolveLocale(request));
        List<FieldError> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    String paramName = result.getMethodParameter().getParameterName();
                    String field = paramName == null ? "param" : paramName;
                    return result.getResolvableErrors().stream()
                            .map(err -> new FieldError(field, "SIZE", resolveError(err, locale)));
                })
                .toList();
        String message = messageSource.getMessage(
                "VALIDATION_FAILED", null, "VALIDATION_FAILED", locale);
        return ApiError.of("VALIDATION_FAILED", message, fieldErrors, resolveTraceId(request));
    }

    private String resolveError(MessageSourceResolvable error, Locale locale) {
        String raw = error.getDefaultMessage();
        if (raw != null && raw.startsWith("{") && raw.endsWith("}") && raw.length() > 2) {
            String key = raw.substring(1, raw.length() - 1);
            return messageSource.getMessage(key, error.getArguments(), raw, locale);
        }
        return messageSource.getMessage(error, locale);
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
}
