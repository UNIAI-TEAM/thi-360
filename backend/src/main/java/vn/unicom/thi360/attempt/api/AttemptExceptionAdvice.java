package vn.unicom.thi360.attempt.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.attempt.application.AttemptOpenElsewhereException;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Maps attempt-specific exceptions that require extra response fields or headers (409
 * {@code ATTEMPT_OPEN_ELSEWHERE} with {@code lastSeenAt}; 429 {@code RATE_LIMITED} with
 * {@code Retry-After}) without altering {@code GlobalExceptionHandler} (plan B2).
 */
@RestControllerAdvice(assignableTypes = {AttemptSessionController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttemptExceptionAdvice {

    private final MessageSource messageSource;

    public AttemptExceptionAdvice(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(AttemptOpenElsewhereException.class)
    public ResponseEntity<AttemptSessionConflictResponse> handleOpenElsewhere(
            AttemptOpenElsewhereException ex, HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = "ATTEMPT_OPEN_ELSEWHERE";
        String message = messageSource.getMessage(code, null, code, locale);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        new AttemptSessionConflictResponse(
                                code, message, List.of(), resolveTraceId(request), ex.getLastSeenAt()));
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if ((traceId == null || traceId.isBlank()) && request != null) {
            Object attr = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            if (attr instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return traceId != null ? traceId : "";
    }
}
