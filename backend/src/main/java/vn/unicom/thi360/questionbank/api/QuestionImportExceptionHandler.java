package vn.unicom.thi360.questionbank.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileException;
import vn.unicom.thi360.questionbank.domain.importing.ImportIssueCode;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestControllerAdvice(assignableTypes = QuestionImportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QuestionImportExceptionHandler {
    private final MessageSource messages;

    public QuestionImportExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ImportFileException.class)
    public ResponseEntity<ApiError> fileError(ImportFileException exception, HttpServletRequest request) {
        String code = exception.code().name();
        HttpStatus status = exception.code() == ImportIssueCode.IMPORT_FILE_TOO_LARGE
                ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.UNPROCESSABLE_ENTITY;
        String message = messages.getMessage("QUESTION_" + code, null, code, LocaleConfig.resolveLocale(request));
        return ResponseEntity.status(status).body(ApiError.of(code, message, List.of(), traceId(request)));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException exception, HttpServletRequest request) {
        return businessError(HttpStatus.NOT_FOUND, exception.getCode(), List.of(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException exception, HttpServletRequest request) {
        return businessError(HttpStatus.CONFLICT, exception.getCode(), exception.getFieldErrors(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> business(BusinessRuleException exception, HttpServletRequest request) {
        return businessError(HttpStatus.UNPROCESSABLE_ENTITY, exception.getCode(), exception.getFieldErrors(), request);
    }

    private ResponseEntity<ApiError> businessError(HttpStatus status, String code, List<FieldError> errors,
            HttpServletRequest request) {
        var locale = LocaleConfig.resolveLocale(request);
        String fallback = messages.getMessage(code, null, code, locale);
        String message = messages.getMessage("QUESTION_" + code, null, fallback, locale);
        return ResponseEntity.status(status).body(ApiError.of(code, message, errors, traceId(request)));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> missingPart(MissingServletRequestPartException exception, HttpServletRequest request) {
        String message = messages.getMessage("VALIDATION_FAILED", null, "VALIDATION_FAILED", LocaleConfig.resolveLocale(request));
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", message,
                List.of(new FieldError(exception.getRequestPartName(), "REQUIRED", message)), traceId(request)));
    }

    private static String traceId(HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId == null && request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE) instanceof String value) {
            traceId = value;
        }
        return traceId == null ? "" : traceId;
    }
}
