package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.attempt.application.GradingClaimedException;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.PreviewGoneException;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Controller-scoped exception mapper for AT-5 admin endpoints:
 * - {@link GradingClaimedException} -> 409 {@code GRADING_CLAIMED} with holder details
 * - {@link PreviewGoneException} -> 410 {@code PREVIEW_EXPIRED}
 * - Malformed polymorphic {@link AnswerValue} JSON -> 422 {@code INVALID_ANSWER_FORMAT} (AC-19)
 */
@RestControllerAdvice(assignableTypes = {AdminGradingController.class, AdminAttemptController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAttemptExceptionAdvice {

    private final MessageSource messageSource;

    public AdminAttemptExceptionAdvice(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(GradingClaimedException.class)
    public ResponseEntity<GradingClaimedResponse> handleGradingClaimed(
            GradingClaimedException ex, HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = "GRADING_CLAIMED";
        String message = messageSource.getMessage(code, null, code, locale);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GradingClaimedResponse(
                        code,
                        message,
                        List.of(),
                        resolveTraceId(request),
                        ex.getClaimedById(),
                        ex.getClaimedByName(),
                        ex.getClaimedAt(),
                        ex.getLastActivityAt()));
    }

    @ExceptionHandler(PreviewGoneException.class)
    public ResponseEntity<ApiError> handlePreviewGone(HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = "PREVIEW_EXPIRED";
        String message = messageSource.getMessage(code, null, code, locale);
        return ResponseEntity.status(HttpStatus.GONE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, List.of(), resolveTraceId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (isInvalidAnswerValueCause(ex)) {
            Locale locale = LocaleConfig.resolveLocale(request);
            String code = "INVALID_ANSWER_FORMAT";
            String message = messageSource.getMessage(code, null, code, locale);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiError.of(code, message, List.of(), resolveTraceId(request)));
        }
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = "VALIDATION_FAILED";
        String message = messageSource.getMessage(code, null, code, locale);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, List.of(), resolveTraceId(request)));
    }

    private static boolean isInvalidAnswerValueCause(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof InvalidTypeIdException || cur instanceof InvalidFormatException) {
                return true;
            }
            if (cur instanceof MismatchedInputException mie) {
                if (mie.getTargetType() != null && AnswerValue.class.isAssignableFrom(mie.getTargetType())) {
                    return true;
                }
                if (mie.getPath() != null && mie.getPath().stream().anyMatch(ref -> "answers".equals(ref.getFieldName()))) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
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
