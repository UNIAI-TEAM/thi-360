package vn.unicom.thi360.questionbank.api;

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
import vn.unicom.thi360.questionbank.application.AssetTooLargeException;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Trả 413 {@code ASSET_TOO_LARGE} cho lỗi cỡ tệp của riêng endpoint
 * {@code POST /question-assets} (ảnh 5 MB / đính kèm 20 MB). Tệp vượt giới hạn multipart
 * chung (21/22 MB) vẫn do {@code GlobalExceptionHandler} trả 413 {@code UPLOAD_TOO_LARGE}.
 */
@RestControllerAdvice(assignableTypes = {AdminQuestionAssetController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QuestionAssetExceptionHandler {

    private final MessageSource messageSource;

    public QuestionAssetExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(AssetTooLargeException.class)
    public ResponseEntity<ApiError> handleAssetTooLarge(
            AssetTooLargeException ex, HttpServletRequest request) {
        var locale = LocaleConfig.normalizeSupported(LocaleConfig.resolveLocale(request));
        String message = messageSource.getMessage(
                AssetTooLargeException.CODE, null, AssetTooLargeException.CODE, locale);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(
                        AssetTooLargeException.CODE,
                        message,
                        List.of(new FieldError("file", AssetTooLargeException.CODE, message)),
                        resolveTraceId(request)));
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
