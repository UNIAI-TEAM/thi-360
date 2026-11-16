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
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.TraceIdFilter;
import vn.unicom.thi360.shared.web.LocaleConfig;

/**
 * Xử lý lỗi riêng của API cấp tài khoản hàng loạt. Tệp vượt giới hạn multipart chung
 * ({@code MaxUploadSizeExceededException}) do {@code GlobalExceptionHandler} trả 413;
 * giới hạn 5 MB của endpoint này do {@code ImportBatchService} kiểm sau khi đọc tệp.
 */
@RestControllerAdvice(assignableTypes = {AdminImportBatchController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ImportBatchExceptionHandler {

    private final MessageSource messageSource;

    public ImportBatchExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        String message = messageSource.getMessage(
                "IMPORT_EMPTY_FILE",
                null,
                "Tệp Excel trống hoặc thiếu tệp tải lên",
                LocaleConfig.resolveLocale(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(
                        "VALIDATION_FAILED",
                        message,
                        List.of(new FieldError(ex.getRequestPartName(), "REQUIRED", message)),
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
