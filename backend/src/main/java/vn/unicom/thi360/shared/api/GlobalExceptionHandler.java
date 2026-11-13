package vn.unicom.thi360.shared.api;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Locale DEFAULT_LOCALE = Locale.of("vi");

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        String code = ex.getCode();
        String message = resolveMessage(code, ex.getMessage(), "VALIDATION_FAILED");
        return respond(HttpStatus.BAD_REQUEST, code, message, ex.getFieldErrors(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleBindExceptions(
            BindException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    String fieldCode = fe.getCode() == null ? "INVALID" : fe.getCode().toUpperCase(Locale.ROOT);
                    String fieldMsg = fe.getDefaultMessage() == null
                            ? resolveMessage(fieldCode, null, "VALIDATION_FAILED")
                            : fe.getDefaultMessage();
                    return new FieldError(fe.getField(), fieldCode, fieldMsg);
                })
                .toList();
        String message = resolveMessage("VALIDATION_FAILED", null, "VALIDATION_FAILED");
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, fieldErrors, request);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = resolveMessage("VALIDATION_FAILED", null, "VALIDATION_FAILED");
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, List.of(), request);
    }

    @ExceptionHandler({UnauthenticatedException.class, AuthenticationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ApiError> handleUnauthenticated(Exception ex, HttpServletRequest request) {
        String code = ex instanceof UnauthenticatedException ue ? ue.getCode() : "UNAUTHENTICATED";
        String customMessage = ex instanceof UnauthenticatedException ? ex.getMessage() : null;
        String message = resolveMessage(code, customMessage, "UNAUTHENTICATED");
        return respond(HttpStatus.UNAUTHORIZED, code, message, List.of(), request);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ApiError> handleForbidden(Exception ex, HttpServletRequest request) {
        String code = ex instanceof ForbiddenException fe ? fe.getCode() : "FORBIDDEN";
        String customMessage = ex instanceof ForbiddenException ? ex.getMessage() : null;
        String message = resolveMessage(code, customMessage, "FORBIDDEN");
        return respond(HttpStatus.FORBIDDEN, code, message, List.of(), request);
    }

    @ExceptionHandler({
        NotFoundException.class,
        NoHandlerFoundException.class,
        NoResourceFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiError> handleNotFound(Exception ex, HttpServletRequest request) {
        String code = ex instanceof NotFoundException nfe ? nfe.getCode() : "NOT_FOUND";
        String customMessage = ex instanceof NotFoundException ? ex.getMessage() : null;
        String message = resolveMessage(code, customMessage, "NOT_FOUND");
        return respond(HttpStatus.NOT_FOUND, code, message, List.of(), request);
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        String code = ex.getCode();
        String message = resolveMessage(code, ex.getMessage(), "CONFLICT");
        return respond(HttpStatus.CONFLICT, code, message, List.of(), request);
    }

    @ExceptionHandler({
        VersionConflictException.class,
        OptimisticLockingFailureException.class,
        OptimisticLockException.class
    })
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public ResponseEntity<ApiError> handleVersionConflict(Exception ex, HttpServletRequest request) {
        String code = ex instanceof VersionConflictException vce ? vce.getCode() : "VERSION_CONFLICT";
        String customMessage = ex instanceof VersionConflictException ? ex.getMessage() : null;
        String message = resolveMessage(code, customMessage, "VERSION_CONFLICT");
        return respond(HttpStatus.PRECONDITION_FAILED, code, message, List.of(), request);
    }

    @ExceptionHandler(RateLimitedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ResponseEntity<ApiError> handleRateLimited(RateLimitedException ex, HttpServletRequest request) {
        String code = ex.getCode();
        String message = resolveMessage(code, ex.getMessage(), "RATE_LIMITED");
        return respond(HttpStatus.TOO_MANY_REQUESTS, code, message, List.of(), request);
    }

    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ResponseEntity<ApiError> handleExternalService(ExternalServiceException ex, HttpServletRequest request) {
        log.atError()
                .setCause(ex)
                .addKeyValue("code", ex.getCode())
                .log("External identity/provider service failure");
        String message = resolveMessage(ex.getCode(), ex.getMessage(), "INTERNAL_ERROR");
        return respond(HttpStatus.BAD_GATEWAY, ex.getCode(), message, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.atError()
                .setCause(ex)
                .addKeyValue("code", "INTERNAL_ERROR")
                .log("Unhandled exception processing HTTP request");
        String message = resolveMessage("INTERNAL_ERROR", null, "INTERNAL_ERROR");
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message, List.of(), request);
    }

    private String resolveMessage(String code, String customMessage, String fallbackCode) {
        if (customMessage != null && !customMessage.isBlank() && !customMessage.equals(code)) {
            return customMessage;
        }
        String resolvedByCode = messageSource.getMessage(code, null, null, DEFAULT_LOCALE);
        if (resolvedByCode != null && !resolvedByCode.isBlank()) {
            return resolvedByCode;
        }
        if (fallbackCode != null) {
            String resolvedFallback = messageSource.getMessage(fallbackCode, null, null, DEFAULT_LOCALE);
            if (resolvedFallback != null && !resolvedFallback.isBlank()) {
                return resolvedFallback;
            }
        }
        return "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau";
    }

    private static ResponseEntity<ApiError> respond(
            HttpStatus status,
            String code,
            String message,
            List<FieldError> fieldErrors,
            HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if ((traceId == null || traceId.isBlank()) && request != null) {
            Object attr = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            if (attr instanceof String s && !s.isBlank()) {
                traceId = s;
            }
        }
        ApiError body = ApiError.of(code, message, fieldErrors, traceId != null ? traceId : "");
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
