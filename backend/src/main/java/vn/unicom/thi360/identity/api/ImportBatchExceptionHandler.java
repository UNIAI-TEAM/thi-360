package vn.unicom.thi360.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestControllerAdvice(assignableTypes = {AdminImportBatchController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ImportBatchExceptionHandler implements HandlerExceptionResolver {

    private static final String ERR_IMPORT_FILE_TOO_LARGE = "IMPORT_FILE_TOO_LARGE";
    private static final Locale DEFAULT_LOCALE = Locale.of("vi");
    private static final Pattern IMPORT_UPLOAD_PATH =
            Pattern.compile("^/api/v1/admin/import-batches/?$");

    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public ImportBatchExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildImportFileTooLargeError(request));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        String message = messageSource.getMessage(
                "IMPORT_EMPTY_FILE",
                null,
                "Tệp Excel trống hoặc thiếu tệp tải lên",
                DEFAULT_LOCALE);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(
                        "VALIDATION_FAILED",
                        message,
                        List.of(new FieldError(ex.getRequestPartName(), "REQUIRED", message)),
                        resolveTraceId(request)));
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        if (!(ex instanceof MaxUploadSizeExceededException) || !isImportUploadRequest(request)) {
            return null;
        }
        if (!response.isCommitted()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            try {
                objectMapper.writeValue(response.getWriter(), buildImportFileTooLargeError(request));
                response.getWriter().flush();
            } catch (IOException ioEx) {
                throw new IllegalStateException("Failed to write IMPORT_FILE_TOO_LARGE response", ioEx);
            }
        }
        return new ModelAndView();
    }

    private ApiError buildImportFileTooLargeError(HttpServletRequest request) {
        String message = messageSource.getMessage(
                ERR_IMPORT_FILE_TOO_LARGE,
                null,
                "Dung lượng tệp Excel vượt quá giới hạn cho phép (tối đa 5 MB)",
                DEFAULT_LOCALE);
        return ApiError.of(
                ERR_IMPORT_FILE_TOO_LARGE,
                message,
                List.of(new FieldError("file", ERR_IMPORT_FILE_TOO_LARGE, message)),
                resolveTraceId(request));
    }

    private static boolean isImportUploadRequest(HttpServletRequest request) {
        if (request == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return IMPORT_UPLOAD_PATH.matcher(uri).matches();
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
