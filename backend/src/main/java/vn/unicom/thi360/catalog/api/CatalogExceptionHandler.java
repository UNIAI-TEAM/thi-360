package vn.unicom.thi360.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import vn.unicom.thi360.catalog.application.CatalogConflictException;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.GlobalExceptionHandler;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestControllerAdvice(assignableTypes = {AdminCatalogController.class, CatalogPublicController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogExceptionHandler implements HandlerExceptionResolver {

    private static final String ERR_CODE_TAKEN = "CATALOG_CODE_TAKEN";
    private static final String ERR_IMPORT_FILE_TOO_LARGE = "IMPORT_FILE_TOO_LARGE";
    private static final Locale DEFAULT_LOCALE = Locale.of("vi");
    private static final Pattern CATALOG_IMPORT_PATH =
            Pattern.compile("^/api/v1/admin/catalog/groups/[^/]+/items/import/?$");
    private static final Set<String> CODE_UNIQUE_CONSTRAINTS =
            Set.of("uq_catalog_group_code_normalized", "uq_catalog_item_group_code_normalized");

    private final MessageSource messageSource;
    private final GlobalExceptionHandler globalExceptionHandler;
    private final ObjectMapper objectMapper;

    public CatalogExceptionHandler(
            MessageSource messageSource,
            GlobalExceptionHandler globalExceptionHandler,
            ObjectMapper objectMapper) {
        this.messageSource = messageSource;
        this.globalExceptionHandler = globalExceptionHandler;
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        if (!isCatalogCodeUniqueViolation(ex)) {
            return globalExceptionHandler.handleUnexpected(ex, request);
        }
        String message = messageSource.getMessage(
                ERR_CODE_TAKEN, null, "Mã danh mục hoặc giá trị đã tồn tại", DEFAULT_LOCALE);
        String traceId = resolveTraceId(request);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(ERR_CODE_TAKEN, message, List.of(), traceId));
    }

    @ExceptionHandler(CatalogConflictException.class)
    public ResponseEntity<ApiError> handleCatalogConflict(
            CatalogConflictException ex, HttpServletRequest request) {
        String code = ex.getCode();
        String message = messageSource.getMessage(
                code, null, "Xung đột dữ liệu danh mục", DEFAULT_LOCALE);
        String traceId = resolveTraceId(request);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, ex.getFieldErrors(), traceId));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildImportFileTooLargeError(request));
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        if (!(ex instanceof MaxUploadSizeExceededException) || !isCatalogImportRequest(request)) {
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
        String traceId = resolveTraceId(request);
        return ApiError.of(
                ERR_IMPORT_FILE_TOO_LARGE,
                message,
                List.of(new FieldError("file", ERR_IMPORT_FILE_TOO_LARGE, message)),
                traceId);
    }

    private static boolean isCatalogImportRequest(HttpServletRequest request) {
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
        return CATALOG_IMPORT_PATH.matcher(uri).matches();
    }

    private static boolean isCatalogCodeUniqueViolation(Throwable throwable) {
        for (Throwable cur = throwable; cur != null; cur = cur.getCause()) {
            if (cur instanceof ConstraintViolationException cve
                    && matchesConstraint(cve.getConstraintName())) {
                return true;
            }
            if (cur instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                String msg = sqlEx.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase(Locale.ROOT);
                    for (String constraint : CODE_UNIQUE_CONSTRAINTS) {
                        if (lower.contains(constraint)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesConstraint(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return false;
        }
        String normalized = rawName.toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(dot + 1);
        }
        return CODE_UNIQUE_CONSTRAINTS.contains(normalized);
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
