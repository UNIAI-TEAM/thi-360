package vn.unicom.thi360.exam.api;

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
import vn.unicom.thi360.exam.domain.ExamStructureInvalidException;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/** Localises the structure validator's 422 (message and every field error) from the {@code messages*} bundles. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExamStructureExceptionAdvice {
    private final MessageSource messages;

    ExamStructureExceptionAdvice(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ExamStructureInvalidException.class)
    ResponseEntity<ApiError> invalid(ExamStructureInvalidException ex, HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        List<FieldError> fieldErrors = ex.details().stream()
                .map(detail -> new FieldError(detail.field(), detail.code(), messages.getMessage(
                        detail.messageKey(), detail.args().toArray(), detail.messageKey(), locale)))
                .toList();
        String message = messages.getMessage(ex.getCode(), null, ex.getCode(), locale);
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            Object attribute = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            traceId = attribute instanceof String value ? value : "";
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(ex.getCode(), message, fieldErrors, traceId));
    }
}
