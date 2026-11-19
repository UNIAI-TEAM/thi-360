package vn.unicom.thi360.exam.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.PreviewGoneException;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestControllerAdvice(assignableTypes = ExamPreviewController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class PreviewExceptionAdvice {
    private final MessageSource messages;

    PreviewExceptionAdvice(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(PreviewGoneException.class)
    ResponseEntity<ApiError> expired(HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            Object attribute = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            traceId = attribute instanceof String value ? value : "";
        }
        String message = messages.getMessage("PREVIEW_EXPIRED", null, LocaleConfig.resolveLocale(request));
        return ResponseEntity.status(HttpStatus.GONE).body(ApiError.of("PREVIEW_EXPIRED", message, traceId));
    }
}
