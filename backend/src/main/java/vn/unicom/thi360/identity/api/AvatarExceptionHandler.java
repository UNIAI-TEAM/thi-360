package vn.unicom.thi360.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.identity.application.AvatarRejectedException;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Lỗi ảnh đại diện (TM E20, plan B2): 413 {@code AVATAR_TOO_LARGE}, 422 {@code AVATAR_INVALID_IMAGE} và
 * 422 {@code AVATAR_DIMENSIONS_EXCEEDED}. {@code GlobalExceptionHandler} không biểu diễn được 413 theo mã của riêng
 * endpoint này; mẫu theo {@code QuestionAssetExceptionHandler}. Ảnh vượt giới hạn multipart chung (21 MB) vẫn do
 * {@code GlobalExceptionHandler} trả 413 {@code UPLOAD_TOO_LARGE}.
 */
@RestControllerAdvice(assignableTypes = {AvatarController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AvatarExceptionHandler {

    private final MessageSource messageSource;

    public AvatarExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(AvatarRejectedException.class)
    public ResponseEntity<ApiError> handleRejected(AvatarRejectedException ex, HttpServletRequest request) {
        var locale = LocaleConfig.normalizeSupported(LocaleConfig.resolveLocale(request));
        String code = ex.reason().code();
        String message = messageSource.getMessage(code, null, code, locale);
        HttpStatus status = ex.reason() == AvatarRejectedException.Reason.TOO_LARGE
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, List.of(new FieldError("file", code, message)), resolveTraceId(request)));
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
