package vn.unicom.thi360.classroom.api;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
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
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.GlobalExceptionHandler;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestControllerAdvice(assignableTypes = {AdminClassController.class, AdminUserClassController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClassExceptionHandler {

    private static final String ERR_CODE_TAKEN = "CLASS_CODE_TAKEN";
    private static final String CONSTRAINT_NAME = "uq_class_room_code_active";
    private static final Locale DEFAULT_LOCALE = Locale.of("vi");

    private final MessageSource messageSource;
    private final GlobalExceptionHandler globalExceptionHandler;

    public ClassExceptionHandler(
            MessageSource messageSource, GlobalExceptionHandler globalExceptionHandler) {
        this.messageSource = messageSource;
        this.globalExceptionHandler = globalExceptionHandler;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        if (!isClassCodeUniqueViolation(ex)) {
            return globalExceptionHandler.handleUnexpected(ex, request);
        }
        String message = messageSource.getMessage(
                ERR_CODE_TAKEN, null, "Mã lớp học đã tồn tại", DEFAULT_LOCALE);
        String traceId = resolveTraceId(request);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(ERR_CODE_TAKEN, message, List.of(), traceId));
    }

    private static boolean isClassCodeUniqueViolation(Throwable throwable) {
        for (Throwable cur = throwable; cur != null; cur = cur.getCause()) {
            if (cur instanceof ConstraintViolationException cve && matchesConstraint(cve.getConstraintName())) {
                return true;
            }
            if (cur instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                String msg = sqlEx.getMessage();
                if (msg != null && msg.toLowerCase(Locale.ROOT).contains(CONSTRAINT_NAME)) {
                    return true;
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
        return CONSTRAINT_NAME.equals(normalized);
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
